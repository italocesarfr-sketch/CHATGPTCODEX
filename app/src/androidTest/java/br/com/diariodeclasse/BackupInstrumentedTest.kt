package br.com.diariodeclasse

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.*

@RunWith(AndroidJUnit4::class)
class BackupInstrumentedTest {
    private lateinit var context: Context
    private lateinit var db: DiaryDatabase
    private lateinit var manager: BackupManager
    private lateinit var folder: File
    private val name="backup-integration-test.db"
    private fun fixture(): BackupSnapshot = InstrumentationRegistry.getInstrumentation().context.assets.open("baseline-v1.json").bufferedReader().use { BackupCodec.json.decodeFromString(it.readText()) }
    @Before fun setup() {
        context=ApplicationProvider.getApplicationContext();context.deleteDatabase(name)
        folder=File(context.cacheDir,"backup-test-${java.util.UUID.randomUUID()}")
        db=Room.databaseBuilder(context,DiaryDatabase::class.java,name).build()
        manager=BackupManager(db,FileSafetyBackupStore(folder),2,null)
    }
    @After fun cleanup() { db.close();context.deleteDatabase(name);folder.deleteRecursively() }
    private suspend fun loadFixture(): BackupSnapshot {
        val f=fixture().canonical()
        val prepared=manager.prepare(BackupCodec.encode(f,1,null).inputStream())
        manager.restore(prepared,"RESTAURAR")
        return f
    }
    @Test fun fullRoundTripWithRecoveryCopyAndReopen() = runBlocking {
        val expected=loadFixture()
        val backup=manager.backupBytes()
        val repo=DiaryRepository(db)
        repo.saveGrade("atividade-1","alfa",8.0)
        val changed=manager.snapshot()
        manager.restore(manager.prepare(backup.inputStream()),"RESTAURAR")
        assertEquals(expected,manager.snapshot())
        val previous=ByteArrayOutputStream();manager.writeLatestSafetyCopy(previous)
        assertEquals(changed,BackupCodec.decode(previous.toByteArray()).snapshot)
        db.close();db=Room.databaseBuilder(context,DiaryDatabase::class.java,name).build()
        manager=BackupManager(db,FileSafetyBackupStore(folder),2,null)
        assertEquals(expected,manager.snapshot())
    }
    @Test fun previewCancellationAndMissingConfirmationDoNotChangeAnything() = runBlocking {
        val expected=loadFixture()
        val prepared=manager.prepare(manager.backupBytes().inputStream())
        assertEquals(expected,manager.snapshot()) // apenas preparar, sem confirmar
        try { manager.restore(prepared,"");fail() } catch (_: BackupFailure) { }
        assertEquals(expected,manager.snapshot())
    }
    @Test fun corruptionAndChangedLiveDataBlockRestore() = runBlocking {
        loadFixture();val bytes=manager.backupBytes()
        val prepared=manager.prepare(bytes.inputStream())
        DiaryRepository(db).saveGrade("atividade-1","alfa",7.0)
        val changed=manager.snapshot()
        try { manager.restore(prepared,"RESTAURAR");fail() } catch (_: BackupFailure) { }
        assertEquals(changed,manager.snapshot())
        try { manager.prepare("invalido".byteInputStream());fail() } catch (_: BackupFailure) { }
        assertEquals(changed,manager.snapshot())
    }
    @Test fun failureDuringInsertRollsBackDeletesAndAllOtherInserts() = runBlocking {
        val before=loadFixture()
        val modified=before.copy(classes=before.classes.map { it.copy(name="Restaurar teste") })
        val prepared=manager.prepare(BackupCodec.encode(modified,2,null).inputStream())
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_test_restore BEFORE INSERT ON classes WHEN NEW.name = 'Restaurar teste' BEGIN SELECT RAISE(ABORT, 'test-only'); END")
        try { manager.restore(prepared,"RESTAURAR");fail() } catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals(before,manager.snapshot())
        assertEquals(before,BackupCodec.decode(FileSafetyBackupStore(folder).latest()!!.readBytes()).snapshot)
    }
    @Test fun inabilityToSaveSafetyCopyNeverModifiesDatabase() = runBlocking {
        val before=loadFixture()
        val blocked=BackupManager(db,object:SafetyBackupStore {
            override fun save(bytes:ByteArray):File = throw IOException("disco simulado")
            override fun latest():File?=null
        },2,null)
        val prepared=blocked.prepare(manager.backupBytes().inputStream())
        try { blocked.restore(prepared,"RESTAURAR");fail() } catch (_: IOException) { }
        assertEquals(before,manager.snapshot())
    }
    @Test fun failedExportDoesNotModifySourceDatabase() = runBlocking {
        val before=loadFixture()
        try { manager.writeBackup(object:OutputStream() { override fun write(value:Int) { throw IOException("destino simulado") } });fail() } catch (_:IOException) { }
        assertEquals(before,manager.snapshot())
    }
    @Test fun newestSafetyCopyIsIndependentOfFilesystemTimestampResolution() = runBlocking {
        loadFixture();val original=manager.backupBytes()
        DiaryRepository(db).saveGrade("atividade-1","alfa",6.0)
        val changed=manager.snapshot()
        manager.restore(manager.prepare(original.inputStream()),"RESTAURAR")
        folder.listFiles()?.forEach { if(it.extension=="json") assertTrue(it.setLastModified(0L)) }
        val selected=FileSafetyBackupStore(folder).latest()!!
        assertEquals(changed,BackupCodec.decode(selected.readBytes()).snapshot)
    }
    @Test fun baselineSchemaOpensWithoutMigrationAndRetainsIdentity() = runBlocking {
        loadFixture()
        db.openHelper.writableDatabase.query("PRAGMA user_version").use { assertTrue(it.moveToFirst());assertEquals(8,it.getInt(0)) }
        db.openHelper.writableDatabase.query("SELECT identity_hash FROM room_master_table WHERE id = 42").use { assertTrue(it.moveToFirst());assertEquals(BackupCodec.IDENTITY,it.getString(0)) }
    }
}
