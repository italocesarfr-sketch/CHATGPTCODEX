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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.io.*

class ClassLifecycleInstrumentedTest {
    @get:Rule val helper=MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),DiaryDatabase::class.java.canonicalName!!,FrameworkSQLiteOpenHelperFactory())
    private val context:Context get()=ApplicationProvider.getApplicationContext()
    private val name="lifecycle-exclusive.db"
    private lateinit var db:DiaryDatabase;private lateinit var repo:DiaryRepository;private lateinit var manager:BackupManager;private lateinit var folder:File
    private val c=SchoolClass(id="life-c",name="Turma Fictícia Excluir",subject="História",year=2026)
    private val h=c.copy(id="life-h",name="Turma Fictícia Preservada")
    private val a=Student("life-a","life-person-a",c.id,"Estudante Fictício Alfa Áurea\r\nLinha",1,"2026-01-01",notes="Observação fictícia preservada")
    private val b=a.copy(id="life-b",personId="life-person-b",classId=h.id,name="Estudante Fictício Beta")
    private fun open(){db=Room.databaseBuilder(context,DiaryDatabase::class.java,name).addMigrations(MIGRATION_1_2,MIGRATION_2_3,MIGRATION_3_4,MIGRATION_4_5, MIGRATION_5_6).build();repo=DiaryRepository(db);manager=BackupManager(db,FileSafetyBackupStore(folder),14,null)}
    private suspend fun data()=repo.snapshot.first{it.loaded}
    @Before fun setup()=runBlocking {
        context.deleteDatabase(name);folder=File(context.cacheDir,"life-${newId()}");open()
        for((cl,s) in listOf(c to a,h to b)){
            repo.saveClass(cl,"2026-01-01");repo.saveStudent(s)
            repo.saveActivity(Activity("activity-${cl.id}",cl.id,"Atividade Fictícia",date="2026-08-10",term=3,maximum=10.0));repo.saveGrade("activity-${cl.id}",s.id,0.0)
            repo.setDayHours(cl.id,"2026-08-10",2);repo.mark("${cl.id}:2026-08-10:1",s.id,"P");repo.mark("${cl.id}:2026-08-10:2",s.id,"FJ")
            repo.saveRecomposition(Recomposition(s.id,3,5.0,"2026-08-20"))
            repo.saveExportLayout(cl.id,emptyList(),listOf(ExportLayout.studentRow(s,0),ExportRow("blank:${cl.id}",cl.id,null,1)))
        }
    }
    @After fun cleanup(){db.close();context.deleteDatabase(name);folder.deleteRecursively()}
    @Test fun archiveReopenReactivatePreservesAllSchoolRowsPreferencesAndExports()=runBlocking {
        val before=manager.snapshot();val table=ExportProjection.build(data(),c,ExportOptions(term=3))
        repo.archiveClass(c.id,true);repo.archiveClass(c.id,true)
        assertEquals(before,manager.snapshot().copy(classArchives=emptyList()));assertEquals(listOf(ClassArchive(c.id)),manager.snapshot().classArchives)
        db.close();open();assertEquals(listOf(ClassArchive(c.id)),manager.snapshot().classArchives)
        assertEquals(table,ExportProjection.build(data(),c,ExportOptions(term=3)))
        repo.archiveClass(c.id,false);assertEquals(before,manager.snapshot())
    }
    @Test fun backupsIncludeArchivesAndReadRealPreviousFormatWithoutLosingExportRows()=runBlocking {
        val before=manager.snapshot();val oldPayload=JSONObject(BackupCodec.json.encodeToString(before)).apply{remove("classArchives")}.toString()
        val old=BackupEnvelope("diario-de-classe-backup",5,"br.com.diariodeclasse",4,BackupCodec.V4_IDENTITY,"2026-10-18T00:00:00Z",13,null,BackupCodec.hash(oldPayload.toByteArray()),oldPayload)
        val bytesOld=BackupCodec.json.encodeToString(old).toByteArray()
        repo.archiveClass(c.id,true);val archived=manager.snapshot();val bytes=manager.backupBytes()
        repo.archiveClass(c.id,false);manager.restore(manager.prepare(bytes.inputStream()),"RESTAURAR");assertEquals(archived,manager.snapshot())
        manager.restore(manager.prepare(bytesOld.inputStream()),"RESTAURAR");assertEquals(before,manager.snapshot());assertTrue(manager.hasSafetyCopy())
        manager.restore(manager.prepare(bytes.inputStream()),"RESTAURAR");assertEquals(archived,manager.snapshot())
    }
    @Test fun previewCancelWrongConfirmationAndSuccessfulDeletePreserveOtherClassExactly()=runBlocking {
        val before=manager.snapshot();val d=data();val preview=repo.previewClassDeletion(c.id)
        assertEquals(1,preview.counts["Matrículas"]);assertEquals(2,preview.counts["Horários de aula"]);assertEquals(2,preview.counts["Chamadas registradas"]);assertNull(preview.blockedReason)
        assertEquals(before,manager.snapshot())
        try{repo.deleteClass(preview,"EXCLUIR");fail()}catch(_:IllegalArgumentException){}
        assertEquals(before,manager.snapshot());repo.deleteClass(preview,preview.confirmation)
        assertEquals(ClassLifecycle.snapshot(ClassLifecycle.without(d,c.id)).canonical(),manager.snapshot())
        assertEquals(before.students.first{it.id==b.id},repo.dao.student(b.id));assertEquals(before.exportRows.filter{it.classId==h.id},repo.dao.exportRows(h.id))
    }
    @Test fun linkedInternalExternalAndSharedIdentityAlwaysBlockDeletion()=runBlocking {
        repo.confirmRelocation(repo.previewRelocation(a.id,h.id,null,"2026-09-01"));val before=manager.snapshot()
        for(id in listOf(c.id,h.id)){
            val preview=repo.previewClassDeletion(id);assertNotNull(preview.blockedReason)
            try{repo.deleteClass(preview,preview.confirmation);fail()}catch(_:IllegalArgumentException){}
        };assertEquals(before,manager.snapshot())
    }
    @Test fun externalAndLegacySharedPersonAreBlockedWithoutInventingNewClass()=runBlocking {
        repo.dao.save(b.copy(personId=a.personId));assertNotNull(repo.previewClassDeletion(c.id).blockedReason)
        repo.dao.save(b);repo.confirmRelocation(repo.previewRelocation(a.id,null,"Destino Externo Fictício","2026-09-01"))
        val before=manager.snapshot();val p=repo.previewClassDeletion(c.id);assertNotNull(p.blockedReason)
        try{repo.deleteClass(p,p.confirmation);fail()}catch(_:IllegalArgumentException){}
        assertEquals(before,manager.snapshot());assertEquals(2,before.classes.size)
    }
    @Test fun crossClassGradeAndAttendanceAreNeverDeletedByCascade()=runBlocking {
        repo.dao.save(Grade("activity-${c.id}",b.id,1.0));repo.dao.save(Attendance("${c.id}:2026-08-10:1",b.id,"P"))
        val before=manager.snapshot();val p=repo.previewClassDeletion(c.id);assertTrue(p.blockedReason!!.contains("outra turma"))
        try{repo.deleteClass(p,p.confirmation);fail()}catch(_:IllegalArgumentException){}
        assertEquals(before,manager.snapshot())
    }
    @Test fun simulatedFailureAfterChildrenWereRemovedRollsBackEveryTableAndStalePreviewIsRefused()=runBlocking {
        repo.archiveClass(c.id,true);val p=repo.previewClassDeletion(c.id);val before=manager.snapshot()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_class_delete BEFORE DELETE ON classes WHEN OLD.id='life-c' BEGIN SELECT RAISE(ABORT,'test-only'); END")
        try{repo.deleteClass(p,p.confirmation);fail()}catch(_:android.database.sqlite.SQLiteException){}
        assertEquals(before,manager.snapshot());db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_class_delete")
        repo.saveGrade("activity-${c.id}",a.id,2.0);val changed=manager.snapshot()
        try{repo.deleteClass(p,p.confirmation);fail()}catch(_:IllegalArgumentException){}
        assertEquals(changed,manager.snapshot())
    }
    @Test fun exportAllFormatsIsReadOnlyAndWriteFailuresNeverChangeRoom()=runBlocking {
        val before=manager.snapshot();val d=data()
        val exports=File(context.filesDir,"etapa52-planilhas");exports.mkdirs()
        val expected=buildJsonArray {
            for(model in ExportModel.entries){
                val t=ExportProjection.build(d,c,ExportOptions(model,null));val key="ANDROID-${model.name}"
                File(exports,"$key.xlsx").outputStream().use{SpreadsheetWriter.xlsx(t,it)};File(exports,"$key.csv").outputStream().use{SpreadsheetWriter.csv(t,it)}
                File(exports,"$key.tsv").writeText(SpreadsheetWriter.clipboard(t));File(exports,"$key-notes.tsv").writeText(SpreadsheetWriter.clipboard(t,true));File(exports,"$key-all-notes.tsv").writeText(SpreadsheetWriter.clipboard(t,true,true))
                fun matrix(rows:List<List<ExportCell>>)=buildJsonArray{rows.forEach{row->add(buildJsonArray{row.forEach{v->add(v.number?.let(::JsonPrimitive)?:v.text?.takeIf{it.isNotEmpty()}?.let(::JsonPrimitive)?:JsonNull)}})}}
                add(buildJsonObject{put("name",key);put("columns",t.columns.size);put("rows",matrix(t.rows()));put("notes",matrix(t.rows(true)));put("allNotes",matrix(t.rows(true,true)))})
            }
        };File(exports,"expected.json").writeText(expected.toString())
        assertEquals(before,manager.snapshot())
        repo.archiveClass(c.id,true);val bytes=manager.backupBytes();val archived=manager.snapshot()
        repo.archiveClass(c.id,false)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_restore_archive BEFORE INSERT ON class_archives BEGIN SELECT RAISE(ABORT,'test-only'); END")
        val current=manager.snapshot();try{manager.restore(manager.prepare(bytes.inputStream()),"RESTAURAR");fail()}catch(_:android.database.sqlite.SQLiteException){}
        assertEquals(current,manager.snapshot());db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_restore_archive")
        manager.restore(manager.prepare(bytes.inputStream()),"RESTAURAR");assertEquals(archived,manager.snapshot())
    }
    @Test fun archivedOriginStillContributesHistoryWhileArchivedTargetRejectsNewMovement()=runBlocking {
        val preview=repo.previewRelocation(a.id,h.id,null,"2026-09-01")
        repo.archiveClass(h.id,true)
        try{repo.confirmRelocation(preview);fail()}catch(_:IllegalArgumentException){}
        try{repo.previewRelocation(a.id,h.id,null,"2026-09-01");fail()}catch(_:IllegalArgumentException){}
        repo.archiveClass(h.id,false)
        val move=repo.previewRelocation(a.id,h.id,null,"2026-09-01");repo.confirmRelocation(move)
        val before=ExportProjection.build(data(),h,ExportOptions(ExportModel.CONSOLIDATED,null))
        repo.archiveClass(c.id,true)
        assertEquals(before,ExportProjection.build(data(),h,ExportOptions(ExportModel.CONSOLIDATED,null)))
        assertEquals(2,data().lineage(move.target!!).size)
    }
    @Test fun migrationFromDistributedV4PreservesElevenTablesAndStartsWithNoArchivedClasses()=runBlocking {
        val expected=manager.snapshot();db.close();context.deleteDatabase(name)
        val json=JSONObject(BackupCodec.json.encodeToString(expected))
        helper.createDatabase(name,4).use{sql->for(field in listOf("classes","students","terms","lessons","activities","marks","grades","drafts","recompositions","relocations","exportRows")){
            val rows=json.getJSONArray(field);for(i in 0 until rows.length()){
                val row=rows.getJSONObject(i);val values=ContentValues();row.keys().forEach{key->when(val v=row.get(key)){JSONObject.NULL->values.putNull(key);is Boolean->values.put(key,if(v)1 else 0);else->values.put(key,v.toString())}}
                sql.insert(when(field){"drafts"->"import_drafts";"exportRows"->"export_rows";else->field},0,values)
            }
        }}
        helper.runMigrationsAndValidate(name,5,true,MIGRATION_4_5).close();open();assertEquals(expected,manager.snapshot())
    }
}
