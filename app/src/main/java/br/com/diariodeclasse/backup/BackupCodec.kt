package br.com.diariodeclasse.backup

import br.com.diariodeclasse.data.*
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate

@Serializable
data class BackupSnapshot(
    val classes: List<SchoolClass>, val students: List<Student>, val terms: List<Term>,
    val lessons: List<Lesson>, val marks: List<Attendance>, val activities: List<Activity>,
    val grades: List<Grade>, val drafts: List<ImportDraft>, val selection: AppSelection? = null
) {
    fun canonical(): BackupSnapshot = copy(
        classes = classes.sortedBy { it.id }, students = students.sortedBy { it.id },
        terms = terms.sortedWith(compareBy({ it.classId }, { it.number })), lessons = lessons.sortedBy { it.id },
        marks = marks.sortedWith(compareBy({ it.lessonId }, { it.studentId })), activities = activities.sortedBy { it.id },
        grades = grades.sortedWith(compareBy({ it.activityId }, { it.studentId })), drafts = drafts.sortedBy { it.id })
    fun summary(): String = "${classes.size} turmas • ${students.size} matrículas\n${terms.size} bimestres • ${lessons.size} aulas\n${marks.size} frequências • ${activities.size} atividades\n${grades.size} notas • ${drafts.size} linhas de rascunho"
}
@Serializable
data class BackupEnvelope(
    val format: String, val formatVersion: Int,
    val applicationId: String, val databaseVersion: Int,
    val schemaIdentityHash: String,
    val createdAt: String, val appVersionCode: Int, val sourceCertificateSha256: String?,
    val payloadSha256: String, val payload: String
)
data class DecodedBackup(val envelope: BackupEnvelope, val snapshot: BackupSnapshot)
class BackupFailure(message: String) : IllegalArgumentException(message)

/** Formato JSON local, completo e estrito. O checksum detecta corrupção, não autentica o autor. */
object BackupCodec {
    const val IDENTITY = "99b868cf68cf5dffb82edf267ddc78be"
    const val MAX_BYTES = 32 * 1024 * 1024
    val json = Json { encodeDefaults = true; ignoreUnknownKeys = false; isLenient = false; coerceInputValues = false }
    fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    fun fingerprint(value: BackupSnapshot): String = hash(json.encodeToString(value.canonical()).toByteArray(Charsets.UTF_8))
    fun encode(value: BackupSnapshot, version: Int, certificate: String?): ByteArray {
        validate(value)
        val payload = json.encodeToString(value.canonical())
        val envelope = BackupEnvelope(format = "diario-de-classe-backup", formatVersion = 2, applicationId = "br.com.diariodeclasse", databaseVersion = 1, schemaIdentityHash = IDENTITY, createdAt = Instant.now().toString(), appVersionCode = version,
            sourceCertificateSha256 = certificate, payloadSha256 = hash(payload.toByteArray(Charsets.UTF_8)), payload = payload)
        val bytes = json.encodeToString(envelope).toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_BYTES) throw BackupFailure("O backup excede o limite seguro de 32 MB.")
        return bytes
    }
    fun read(input: InputStream): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val size = input.read(buffer); if (size < 0) break
            if (output.size() + size > MAX_BYTES) throw BackupFailure("Arquivo excede o limite de 32 MB.")
            output.write(buffer, 0, size)
        }
        return output.toByteArray()
    }
    fun decode(bytes: ByteArray): DecodedBackup {
        if (bytes.isEmpty() || bytes.size > MAX_BYTES) throw BackupFailure("Arquivo vazio ou muito grande.")
        try {
            val envelope = json.decodeFromString<BackupEnvelope>(bytes.toString(Charsets.UTF_8))
            if (envelope.format != "diario-de-classe-backup" || envelope.applicationId != "br.com.diariodeclasse") throw BackupFailure("Este arquivo não é um backup do Diário de Classe.")
            if (envelope.formatVersion !in 1..2 || envelope.databaseVersion != 1 || envelope.schemaIdentityHash != IDENTITY) throw BackupFailure("Versão de backup ou banco incompatível. Nenhum dado foi alterado.")
            Instant.parse(envelope.createdAt)
            if (hash(envelope.payload.toByteArray(Charsets.UTF_8)) != envelope.payloadSha256) throw BackupFailure("Falha de integridade: arquivo corrompido ou modificado.")
            val snapshot = json.decodeFromString<BackupSnapshot>(envelope.payload).canonical()
            validate(snapshot)
            return DecodedBackup(envelope, snapshot)
        } catch (error: BackupFailure) { throw error }
        catch (_: Exception) { throw BackupFailure("Backup inválido, incompleto ou com campos não reconhecidos.") }
    }
    fun validate(s: BackupSnapshot) {
        fun check(value: Boolean) { if (!value) throw BackupFailure("Backup contém valores, duplicidades ou vínculos inválidos.") }
        fun validDate(value: String) { check(runCatching { LocalDate.parse(value).toString() == value }.getOrDefault(false)) }
        fun unique(ids: List<String>) { check(ids.all { it.isNotBlank() } && ids.toSet().size == ids.size) }
        unique(s.classes.map { it.id }); unique(s.students.map { it.id }); unique(s.lessons.map { it.id })
        unique(s.activities.map { it.id }); unique(s.drafts.map { it.id })
        check(s.terms.map { it.classId to it.number }.toSet().size == s.terms.size)
        check(s.lessons.map { Triple(it.classId, it.date, it.ordinal) }.toSet().size == s.lessons.size)
        check(s.marks.map { it.lessonId to it.studentId }.toSet().size == s.marks.size)
        check(s.grades.map { it.activityId to it.studentId }.toSet().size == s.grades.size)
        val classes = s.classes.associateBy { it.id }; val students = s.students.associateBy { it.id }
        val lessons = s.lessons.associateBy { it.id }; val activities = s.activities.associateBy { it.id }
        s.selection?.let { selection ->
            check(selection.year in 2000..2100)
            check(selection.classId == null || classes[selection.classId]?.year == selection.year)
        }
        s.classes.forEach {
            check(it.name.isNotBlank() && it.subject.isNotBlank() && it.year in 2000..2100 && it.duration in 1..240)
            check(it.scale.isFinite() && it.scale > 0 && it.low.isFinite() && it.high.isFinite() && it.low >= 0 && it.low < it.high && it.high <= it.scale)
            val counts = it.schedule.split(',').map { n -> n.toIntOrNull() }
            check(counts.size == 7 && counts.all { n -> n != null && n in 0..12 })
        }
        s.students.forEach {
            check(it.classId in classes && it.personId.isNotBlank() && it.number > 0 && it.name.trim().length >= 2)
            validDate(it.joined); it.left?.let { d -> validDate(d); check(d >= it.joined) }
            check(it.status in listOf("Ativo", "Transferido", "Remanejado"))
        }
        s.terms.forEach { check(it.classId in classes && it.number in 1..4); validDate(it.start); validDate(it.end); check(it.start <= it.end) }
        s.lessons.forEach { check(it.classId in classes && it.ordinal > 0 && it.state in listOf("PREVISTA", "REALIZADA", "CANCELADA")); validDate(it.date) }
        s.activities.forEach { check(it.classId in classes && it.term in 1..4 && it.maximum.isFinite() && it.maximum > 0 && it.name.isNotBlank()); validDate(it.date) }
        s.marks.forEach {
            val lesson = lessons[it.lessonId]; val student = students[it.studentId]
            check(lesson != null && student != null && lesson.classId == student.classId && it.mark in listOf("P", "F", "FJ", "NR"))
        }
        s.grades.forEach {
            val activity = activities[it.activityId]; val student = students[it.studentId]
            check(activity != null && student != null && activity.classId == student.classId)
            check(it.value == null || it.value.isFinite() && it.value >= 0 && it.value <= activity!!.maximum)
        }
        s.drafts.forEach { check(it.classId in classes && it.position >= 0 && it.page >= 0) }
        // Não rejeitar registros fora do ingresso/bimestre atual: datas podem ter sido editadas
        // após a chamada/nota. O backup deve preservar esse histórico, sem corrigi-lo sozinho.
    }
}
