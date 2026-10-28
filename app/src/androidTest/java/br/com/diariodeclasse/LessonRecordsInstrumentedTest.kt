package br.com.diariodeclasse

import android.content.Context
import android.content.ContentValues
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.export.*
import br.com.diariodeclasse.reports.SchoolReports
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.io.File

class LessonRecordsInstrumentedTest {
    @get:Rule val helper=MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),DiaryDatabase::class.java.canonicalName!!,FrameworkSQLiteOpenHelperFactory())
    private val context:Context get()=ApplicationProvider.getApplicationContext()
    private val name="lesson-records-exclusive.db"
    private lateinit var db:DiaryDatabase;private lateinit var repo:DiaryRepository;private lateinit var manager:BackupManager;private lateinit var folder:File
    private val note=LessonRecord("note","classe-alfa","2026-08-12","Título Fictício áçã","Descrição livre\nCom \"aspas\"\t e horários independentes.")
    private fun open(){db=Room.databaseBuilder(context,DiaryDatabase::class.java,name).addMigrations(MIGRATION_1_2,MIGRATION_2_3,MIGRATION_3_4,MIGRATION_4_5,MIGRATION_5_6, MIGRATION_6_7,MIGRATION_7_8,MIGRATION_8_9,MIGRATION_9_10).build();repo=DiaryRepository(db);manager=BackupManager(db,FileSafetyBackupStore(folder),15,null)}
    private suspend fun data()=repo.snapshot.first{it.loaded}
    @Before fun setup()=runBlocking {
        context.deleteDatabase(name);folder=File(context.cacheDir,"lesson-records-${newId()}");open()
        val raw=InstrumentationRegistry.getInstrumentation().context.assets.open("baseline-v1.json").bufferedReader().use{it.readText()}
        val fixture=BackupCodec.json.decodeFromString<BackupSnapshot>(raw)
        manager.restore(manager.prepare(BackupCodec.encode(fixture,15,null).inputStream()),"RESTAURAR")
        repo.saveRecomposition(Recomposition("alfa",3,6.0,"2026-11-20"))
        repo.saveExportLayout("classe-alfa",emptyList(),ExportLayout.normalize(listOf(ExportLayout.blank("classe-alfa"))+ExportLayout.rows(data(),"classe-alfa").map{it.copy(manualHidden=it.studentId=="alfa")}))
        repo.archiveClass("classe-beta",true)
    }
    @After fun cleanup(){db.close();context.deleteDatabase(name);folder.deleteRecursively()}
    @Test fun createEditMoveDuplicateAndDeleteNeverWriteSchoolData()=runBlocking {
        val before=manager.snapshot();val d=data();val c=d.classes.first{it.id==note.classId}
        val exports=ExportModel.entries.map{ExportProjection.build(d,c,ExportOptions(it,null))}
        repo.saveLessonRecord(note);val second=note.copy(id="second");repo.saveLessonRecord(second)
        assertEquals(2,LessonRecords.list(data().lessonRecords,note.classId,note.date,note.date).size)
        val edited=note.copy(title="Título editado",date="2026-08-13",classId="classe-beta")
        repo.saveLessonRecord(edited,note);assertEquals(edited,repo.dao.lessonRecord(note.id))
        assertEquals(before,manager.snapshot().copy(lessonRecords=emptyList()))
        assertEquals(exports,ExportModel.entries.map{ExportProjection.build(data(),c,ExportOptions(it,null))})
        assertEquals(6.0,SchoolReports(data(),c).student(d.students.first{it.id=="alfa"},3).total!!,0.0)
        repo.deleteLessonRecord(second);repo.deleteLessonRecord(edited);assertEquals(before,manager.snapshot())
    }
    @Test fun restartKeepsIdsTextDateAndClass()=runBlocking {
        repo.saveLessonRecord(note);val before=manager.snapshot();db.close();open()
        assertEquals(before,manager.snapshot());assertEquals(note,repo.dao.lessonRecord(note.id))
    }
    @Test fun backupRestoreNewAndRealFormatSixPreserveAllOldTables()=runBlocking {
        val old=manager.snapshot();val payload=JSONObject(BackupCodec.json.encodeToString(old)).apply{remove("lessonRecords")}.toString()
        val envelope=BackupEnvelope("diario-de-classe-backup",6,"br.com.diariodeclasse",5,BackupCodec.V5_IDENTITY,"2026-10-18T00:00:00Z",14,null,BackupCodec.hash(payload.toByteArray()),payload)
        val oldBytes=BackupCodec.json.encodeToString(envelope).toByteArray()
        repo.saveLessonRecord(note);val noted=manager.snapshot();val bytes=manager.backupBytes()
        repo.deleteLessonRecord(note);manager.restore(manager.prepare(bytes.inputStream()),"RESTAURAR");assertEquals(noted,manager.snapshot())
        manager.restore(manager.prepare(oldBytes.inputStream()),"RESTAURAR");assertEquals(old,manager.snapshot())
        manager.restore(manager.prepare(bytes.inputStream()),"RESTAURAR");assertEquals(noted,manager.snapshot());assertTrue(manager.hasSafetyCopy())
    }
    @Test fun staleEditsAndDeletesCannotOverwriteConfirmedChanges()=runBlocking {
        repo.saveLessonRecord(note);val edited=note.copy(description="Texto confirmado");repo.saveLessonRecord(edited,note)
        val before=manager.snapshot()
        try{repo.saveLessonRecord(note.copy(title="Edição antiga"),note);fail()}catch(_:IllegalArgumentException){}
        try{repo.deleteLessonRecord(note);fail()}catch(_:IllegalArgumentException){}
        try{repo.saveLessonRecord(note);fail()}catch(_:IllegalArgumentException){}
        assertEquals(before,manager.snapshot())
    }
    @Test fun invalidDateEmptyTextAndMissingClassLeaveNoPartialWrites()=runBlocking {
        val before=manager.snapshot()
        for(value in listOf(note.copy(date="2026-02-31"),note.copy(classId="missing"),note.copy(title="",description=" "))){
            try{repo.saveLessonRecord(value);fail()}catch(_:Exception){}
        };assertEquals(before,manager.snapshot())
    }
    @Test fun failedRestoreRollsBackRecordsAndEverySchoolTable()=runBlocking {
        repo.saveLessonRecord(note);val bytes=manager.backupBytes();repo.saveLessonRecord(note.copy(title="Antes da falha"),note)
        val before=manager.snapshot()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_record_restore BEFORE INSERT ON lesson_records BEGIN SELECT RAISE(ABORT,'test-only'); END")
        try{manager.restore(manager.prepare(bytes.inputStream()),"RESTAURAR");fail()}catch(_:android.database.sqlite.SQLiteException){}
        assertEquals(before,manager.snapshot());db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_record_restore")
    }
    @Test fun archiveKeepsTextAndConfirmedClassDeletionRemovesOnlyItsRecordsWithRollback()=runBlocking {
        val c=SchoolClass(id="exclusive",name="Turma Fictícia Exclusiva",subject="História",year=2026)
        repo.saveClass(c,"2026-01-01");repo.saveLessonRecord(note);val own=note.copy(id="own",classId=c.id);repo.saveLessonRecord(own)
        repo.archiveClass(c.id,true);val before=manager.snapshot();assertEquals(own,repo.dao.lessonRecord(own.id))
        val preview=repo.previewClassDeletion(c.id);assertEquals(1,preview.counts["Registros de aula"])
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_record_class_delete BEFORE DELETE ON classes WHEN OLD.id='exclusive' BEGIN SELECT RAISE(ABORT,'test-only'); END")
        try{repo.deleteClass(preview,preview.confirmation);fail()}catch(_:android.database.sqlite.SQLiteException){}
        assertEquals(before,manager.snapshot());db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_record_class_delete")
        repo.deleteClass(preview,preview.confirmation);assertNull(repo.dao.lessonRecord(own.id));assertEquals(note,repo.dao.lessonRecord(note.id))
        assertEquals(ClassLifecycle.snapshot(ClassLifecycle.without(dataFrom(before),c.id)).canonical(),manager.snapshot())
    }
    private fun dataFrom(b:BackupSnapshot)=DiarySnapshot(classes=b.classes,students=b.students,terms=b.terms,lessons=b.lessons,marks=b.marks,activities=b.activities,grades=b.grades,drafts=b.drafts,recompositions=b.recompositions,relocations=b.relocations,exportRows=b.exportRows,classArchives=b.classArchives,lessonRecords=b.lessonRecords)
    @Test fun migrationFromDistributedV5KeepsAllTwelveTablesAndAddsEmptyRecords()=runBlocking {
        val expected=manager.snapshot();db.close();context.deleteDatabase(name)
        val json=JSONObject(BackupCodec.json.encodeToString(expected))
        helper.createDatabase(name,5).use{sql->for(field in listOf("classes","students","terms","lessons","activities","marks","grades","drafts","recompositions","relocations","exportRows","classArchives")){
            val rows=json.getJSONArray(field);for(i in 0 until rows.length()){
                val row=rows.getJSONObject(i);if(field=="marks") { assertTrue(row.isNull("reason")); row.remove("reason") };val values=ContentValues();row.keys().forEach{key->when(val v=row.get(key)){JSONObject.NULL->values.putNull(key);is Boolean->values.put(key,if(v)1 else 0);else->values.put(key,v.toString())}}
                sql.insert(when(field){"drafts"->"import_drafts";"exportRows"->"export_rows";"classArchives"->"class_archives";else->field},0,values)
            }
        }}
        helper.runMigrationsAndValidate(name,6,true,MIGRATION_5_6).close();open();assertEquals(expected,manager.snapshot());assertTrue(data().lessonRecords.isEmpty())
    }
    @Test fun confirmedChangesEmitToSnapshotAutomatically()=runBlocking {
        val observed=async(start=CoroutineStart.UNDISPATCHED){withTimeout(10000){repo.snapshot.first{it.lessonRecords.any{r->r.id==note.id}}}}
        repo.saveLessonRecord(note);assertEquals(note,observed.await().lessonRecords.single())
        val changed=async(start=CoroutineStart.UNDISPATCHED){withTimeout(10000){repo.snapshot.first{it.lessonRecords.any{r->r.title=="Alterado"}}}}
        repo.saveLessonRecord(note.copy(title="Alterado"),note);assertEquals("Alterado",changed.await().lessonRecords.single().title)
    }
}
