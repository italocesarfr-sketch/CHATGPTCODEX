package br.com.diariodeclasse.backup

import android.util.AtomicFile
import androidx.room.withTransaction
import br.com.diariodeclasse.data.DiaryDatabase
import br.com.diariodeclasse.data.SelectionStore
import br.com.diariodeclasse.data.resolveSelection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

interface SafetyBackupStore {
    fun save(bytes: ByteArray): File
    fun latest(): File?
}
class FileSafetyBackupStore(private val folder: File) : SafetyBackupStore {
    override fun save(bytes: ByteArray): File {
        check(folder.isDirectory || folder.mkdirs()) { "Cópia de segurança indisponível." }
        val file = File(folder, "antes-restauracao-${System.currentTimeMillis()}-${UUID.randomUUID()}.json")
        val atomic = AtomicFile(file)
        val output = atomic.startWrite()
        try { output.write(bytes); atomic.finishWrite(output) }
        catch (error: Exception) { atomic.failWrite(output); throw error }
        check(file.isFile && file.length() == bytes.size.toLong())
        check(file.inputStream().use { BackupCodec.read(it) }.contentEquals(bytes)) { "Cópia de segurança não confere." }
        // mtime pode ter resolução de segundos. O ponteiro atômico não depende do relógio.
        val pointer = AtomicFile(File(folder, "latest-copy.txt"))
        val index = pointer.startWrite()
        try { index.write(file.name.toByteArray(Charsets.UTF_8)); pointer.finishWrite(index) }
        catch (error: Exception) { pointer.failWrite(index); throw error }
        return file
    }
    override fun latest(): File? = runCatching {
        val name = AtomicFile(File(folder, "latest-copy.txt")).readFully().toString(Charsets.UTF_8)
        require(name.startsWith("antes-restauracao-") && name.endsWith(".json") && !name.contains('/') && !name.contains('\\'))
        File(folder, name).takeIf { it.isFile }
    }.getOrNull()
}
class PreparedRestore internal constructor(internal val decoded: DecodedBackup, internal val currentFingerprint: String) {
    val summary: String get() = decoded.snapshot.summary()
    val createdAt: String get() = decoded.envelope.createdAt
}
data class RestoreResult(val safetyCopy: File, val warning: String = "")

/** O banco aberto não é copiado, apagado nem trocado. Somente dados são restaurados numa transação. */
class BackupManager(
    private val db: DiaryDatabase, private val safety: SafetyBackupStore,
    private val appVersionCode: Int, private val certificate: String?, private val selection: SelectionStore? = null
) {
    private val dao = db.backupDao()
    private val _generation = MutableStateFlow(0L)
    val generation = _generation.asStateFlow()
    var restoreNotice: String = ""
        private set
    private fun verifySchema() {
        db.openHelper.writableDatabase.query("PRAGMA user_version").use {
            check(it.moveToFirst() && it.getInt(0) == 2) { "Versão do banco incompatível." }
        }
        db.openHelper.writableDatabase.query("SELECT identity_hash FROM room_master_table WHERE id = 42").use {
            check(it.moveToFirst() && it.getString(0) == BackupCodec.IDENTITY) { "Esquema do banco incompatível." }
        }
    }
    private suspend fun readRows(): BackupSnapshot = BackupSnapshot(dao.classes(), dao.students(), dao.terms(), dao.lessons(),
        dao.marks(), dao.activities(), dao.grades(), dao.drafts(), recompositions = dao.recompositions())
    private fun withContext(rows: BackupSnapshot): BackupSnapshot = rows.copy(selection = selection?.read()?.let { resolveSelection(it, rows.classes) })
    suspend fun snapshot(): BackupSnapshot = db.withTransaction { verifySchema(); withContext(readRows()) }
    suspend fun backupBytes(): ByteArray = BackupCodec.encode(snapshot(), appVersionCode, certificate)
    suspend fun writeBackup(output: OutputStream) { val bytes = backupBytes(); output.write(bytes); output.flush() }
    suspend fun prepare(input: InputStream): PreparedRestore {
        val decoded = BackupCodec.decode(BackupCodec.read(input))
        return PreparedRestore(decoded, BackupCodec.fingerprint(snapshot()))
    }
    fun hasSafetyCopy(): Boolean = safety.latest() != null
    fun writeLatestSafetyCopy(output: OutputStream) {
        val file = safety.latest() ?: throw BackupFailure("Ainda não há cópia anterior a uma restauração.")
        // Verifica a cópia privada antes de oferecê-la ao seletor do professor.
        val bytes = file.inputStream().use { BackupCodec.read(it) }; BackupCodec.decode(bytes)
        output.write(bytes); output.flush()
    }
    suspend fun restore(prepared: PreparedRestore, confirmation: String): RestoreResult {
        if (confirmation != "RESTAURAR") throw BackupFailure("Digite RESTAURAR para confirmar a substituição.")
        val incoming = prepared.decoded.snapshot
        BackupCodec.validate(incoming)
        val result = db.withTransaction {
            verifySchema()
            val current = withContext(readRows())
            if (BackupCodec.fingerprint(current) != prepared.currentFingerprint) throw BackupFailure("Os dados mudaram após a prévia. Selecione novamente o backup antes de confirmar.")
            // Se esta escrita falhar, nenhuma tabela é modificada.
            val copy = safety.save(BackupCodec.encode(current, appVersionCode, certificate))
            val safeguarded = copy.inputStream().use { BackupCodec.decode(BackupCodec.read(it)).snapshot }
            check(BackupCodec.fingerprint(safeguarded) == BackupCodec.fingerprint(current)) { "Cópia de segurança não validada." }
            // Chaves estrangeiras continuam ligadas; qualquer erro causa rollback integral.
            dao.removeRecompositions(); dao.removeMarks(); dao.removeGrades(); dao.removeDrafts(); dao.removeActivities()
            dao.removeLessons(); dao.removeTerms(); dao.removeStudents(); dao.removeClasses()
            dao.insertClasses(incoming.classes); dao.insertStudents(incoming.students); dao.insertTerms(incoming.terms)
            dao.insertLessons(incoming.lessons); dao.insertActivities(incoming.activities)
            dao.insertMarks(incoming.marks); dao.insertGrades(incoming.grades); dao.insertDrafts(incoming.drafts); dao.insertRecompositions(incoming.recompositions)
            db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use {
                check(!it.moveToFirst()) { "Vínculos inconsistentes." }
            }
            check(readRows() == incoming.copy(selection = null)) { "Verificação de restauração falhou." }
            RestoreResult(copy)
        }
        // O contexto visual é aplicado APÓS o commit escolar. Falhar aqui nunca desfaz
        // nem oculta a restauração dos registros; a UI resolve IDs ausentes com segurança.
        val warning = runCatching { selection?.write(incoming.selection) }.exceptionOrNull()?.let {
            "Não foi possível memorizar a turma restaurada. Selecione novamente a turma e o ano."
        }.orEmpty()
        restoreNotice = "Backup restaurado. Os dados anteriores estão em uma cópia de segurança. $warning"
        _generation.value += 1
        return result.copy(warning = warning)
    }
}
