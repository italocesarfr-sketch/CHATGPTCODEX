package br.com.diariodeclasse

import android.content.ContentValues
import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.reports.SchoolReports
import br.com.diariodeclasse.ui.ExportEditSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.io.File

class ExportLayoutInstrumentedTest {
    @get:Rule val helper=MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),DiaryDatabase::class.java.canonicalName!!,FrameworkSQLiteOpenHelperFactory())
    private val context:Context get()=ApplicationProvider.getApplicationContext()
    private val name="export-layout-exclusive.db"
    private lateinit var db:DiaryDatabase
    private lateinit var repo:DiaryRepository
    private lateinit var manager:BackupManager
    private lateinit var folder:File
    private val c=SchoolClass(id="export-room-c",name="Turma Fictícia Exportação",subject="História",year=2026)
    private val d=c.copy(id="export-room-d",name="Destino Fictício Exportação")
    private val a=Student("export-room-a","export-person-a",c.id,"Estudante Fictício Homônimo",10,"2026-01-01",notes="Observação fictícia preservada")
    private val b=a.copy(id="export-room-b",personId="export-person-b",number=20)
    private val activity=Activity("export-room-activity",c.id,"Avaliação Fictícia",date="2026-08-10",term=3,maximum=10.0)
    private fun open() {
        db=Room.databaseBuilder(context,DiaryDatabase::class.java,name).addMigrations(MIGRATION_1_2,MIGRATION_2_3,MIGRATION_3_4,MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,MIGRATION_7_8,MIGRATION_8_9).build()
        repo=DiaryRepository(db);manager=BackupManager(db,FileSafetyBackupStore(folder),13,null)
    }
    private suspend fun data()=repo.snapshot.first{it.loaded}
    private suspend fun save(session:ExportEditSession)=repo.saveExportLayout(session.classId,session.expected,session.rows)
    private fun school(s:BackupSnapshot)=s.copy(exportRows=emptyList())
    @Before fun setup()=runBlocking {
        context.deleteDatabase(name);folder=File(context.cacheDir,"export-test-${java.util.UUID.randomUUID()}");open()
        repo.saveClass(c,"2026-01-01");repo.saveClass(d,"2026-01-01");repo.saveStudent(a);repo.saveStudent(b)
        repo.saveActivity(activity);repo.saveGrade(activity.id,a.id,0.0);repo.saveGrade(activity.id,b.id,4.0)
        repo.setDayHours(c.id,"2026-08-10",2)
        repo.mark("${c.id}:2026-08-10:1",a.id,"P");repo.mark("${c.id}:2026-08-10:2",a.id,"FJ")
        repo.saveRecomposition(Recomposition(a.id,3,5.0,"2026-08-20"))
    }
    @After fun cleanup() {db.close();context.deleteDatabase(name);folder.deleteRecursively()}
    @Test fun hidingMultipleAndUnhidingWritesOnlyPreferencesAndDoesNotDisableSchoolOperations()=runBlocking {
        val before=manager.snapshot();val draft=ExportEditSession.start(data(),c.id,"HIDE").hide(a.id,true).hide(b.id,true)
        assertEquals(before,manager.snapshot()) // Cancelar/navegar não grava o rascunho.
        save(draft);val hidden=manager.snapshot();assertEquals(before,school(hidden))
        assertEquals(2,hidden.exportRows.count{it.manualHidden});assertEquals(listOf(a,b),repo.dao.roster(c.id))
        repo.saveGrade(activity.id,a.id,2.0);repo.mark("${c.id}:2026-08-10:2",a.id,"F")
        val report=SchoolReports(data(),c).student(a,3)
        assertEquals(2.0,report.original!!,0.0);assertEquals(5.0,report.total!!,0.0);assertEquals(1,report.attendance!!.absent)
        val unhidden=ExportEditSession.start(data(),c.id,"HIDE").hide(a.id,false);save(unhidden)
        assertFalse(repo.dao.exportRows(c.id).first{it.studentId==a.id}.manualHidden)
        assertTrue(repo.dao.exportRows(c.id).first{it.studentId==b.id}.manualHidden)
    }
    @Test fun reorderedRowsHomonymsRenamingOfficialReorderAndNewStudentsKeepIndependentPositions()=runBlocking {
        val session=ExportEditSession.start(data(),c.id,"ORDER").move("student:${b.id}",-1).addBlank("student:${b.id}")
        save(session);val rows=repo.dao.exportRows(c.id)
        repo.saveStudent(a.copy(name="Estudante Fictício Renomeado"));repo.moveStudent(a.id,1)
        assertEquals(rows,repo.dao.exportRows(c.id));assertEquals(20,repo.dao.student(a.id)!!.number)
        val added=a.copy(id="export-room-new",personId="new-person",number=1,name="AAAA Estudante Fictício Novo")
        repo.saveStudent(added)
        assertEquals(listOf(b.id,null,a.id,added.id),ExportLayout.rows(data(),c.id).map{it.studentId})
        assertEquals(rows,repo.dao.exportRows(c.id))
        // Cadastro posterior ao começo de uma edição é acrescentado no final, sem perder a edição.
        val editing=ExportEditSession.start(data(),c.id,"HIDE").hide(b.id,true)
        val later=added.copy(id="export-room-later",personId="later-person",number=2,name="Estudante Fictício Posterior")
        repo.saveStudent(later);save(editing)
        assertEquals(listOf(b.id,null,a.id,added.id,later.id),repo.dao.exportRows(c.id).map{it.studentId})
        assertEquals(rows.map{it.id},repo.dao.exportRows(c.id).take(3).map{it.id})
    }
    @Test fun relocationRetainsManualSourcePreferenceAndIncludesNewDestinationByDefault()=runBlocking {
        save(ExportEditSession.start(data(),c.id,"HIDE").hide(a.id,true))
        val before=manager.snapshot();val preview=repo.previewRelocation(a.id,d.id,null,"2026-09-01");repo.confirmRelocation(preview)
        val snapshot=data();val origin=ExportLayout.project(snapshot,ExportLayout.rows(snapshot,c.id)).first{it.student!!.id==a.id}
        assertTrue(origin.automaticallyExcluded);assertTrue(origin.row.manualHidden)
        val target=ExportLayout.project(snapshot,ExportLayout.rows(snapshot,d.id)).single()
        assertTrue(target.included);assertFalse(target.row.manualHidden)
        val after=manager.snapshot();assertEquals(before.exportRows,after.exportRows)
        assertEquals(before.grades,after.grades);assertEquals(before.marks,after.marks);assertEquals(before.recompositions,after.recompositions)
        assertEquals(5.0,SchoolReports(snapshot,d).student(preview.target!!,3).total!!,0.0)
        assertEquals(1,snapshot.relocations.size)
    }
    @Test fun configurationBackupRestoreAndDatabaseReopenPreserveAllRowsAndOldBackupClearsOnlyConfirmedConfiguration()=runBlocking {
        val before=manager.snapshot();val old=legacyV3Backup(before)
        save(ExportEditSession.start(data(),c.id,"ORDER").hide(a.id,true).addBlank("student:${a.id}").move("student:${b.id}",-1))
        val configured=manager.snapshot();val bytes=manager.backupBytes()
        db.close();open();assertEquals(configured,manager.snapshot())
        save(ExportEditSession.start(data(),c.id,"HIDE").hide(a.id,false))
        manager.restore(manager.prepare(bytes.inputStream()),"RESTAURAR");assertEquals(configured,manager.snapshot())
        manager.restore(manager.prepare(old.inputStream()),"RESTAURAR");assertEquals(before,manager.snapshot())
        assertTrue(manager.hasSafetyCopy());assertEquals(configured,BackupCodec.decode(FileSafetyBackupStore(folder).latest()!!.readBytes()).snapshot)
        manager.restore(manager.prepare(bytes.inputStream()),"RESTAURAR");assertEquals(configured,manager.snapshot())
    }
    private fun legacyV3Backup(value:BackupSnapshot):ByteArray {
        val payload=JSONObject(BackupCodec.json.encodeToString(value)).apply{remove("exportRows")}.toString()
        val envelope=BackupEnvelope("diario-de-classe-backup",4,"br.com.diariodeclasse",3,BackupCodec.V3_IDENTITY,"2026-10-17T00:00:00Z",12,null,BackupCodec.hash(payload.toByteArray()),payload)
        return BackupCodec.json.encodeToString(envelope).toByteArray()
    }
    @Test fun transactionFailureStaleEditorAndForeignStudentCannotOverwritePreferences()=runBlocking {
        save(ExportEditSession.start(data(),c.id,"HIDE"));val baseline=manager.snapshot()
        val first=ExportEditSession.start(data(),c.id,"HIDE");val second=first.hide(b.id,true)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_export BEFORE INSERT ON export_rows WHEN NEW.manualHidden=1 BEGIN SELECT RAISE(ABORT,'test-only'); END")
        try {save(second);fail()}catch(_:android.database.sqlite.SQLiteException){}
        assertEquals(baseline,manager.snapshot());db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_export")
        save(first.hide(a.id,true));val changed=manager.snapshot()
        try {save(second);fail()}catch(_:IllegalArgumentException){}
        assertEquals(changed,manager.snapshot())
        val foreign=a.copy(id="export-foreign",personId="foreign",classId=d.id);repo.saveStudent(foreign)
        val current=manager.snapshot()
        try {repo.saveExportLayout(c.id,repo.dao.exportRows(c.id),listOf(ExportLayout.studentRow(foreign,0).copy(classId=c.id)));fail()}catch(_:IllegalArgumentException){}
        assertEquals(current,manager.snapshot())
    }
    @Test fun migrationFromDistributedV3PreservesEverySchoolTableAndMovement()=runBlocking {
        db.close();context.deleteDatabase(name)
        val raw=InstrumentationRegistry.getInstrumentation().context.assets.open("baseline-v1.json").bufferedReader().use{it.readText()}
        val old=BackupCodec.json.decodeFromString<BackupSnapshot>(raw)
        val expected=old.copy(students=old.students.map{if(it.id=="beta-origem")it.copy(status="Remanejado")else it},
            recompositions=listOf(Recomposition("alfa",3,5.0,"2026-11-20")),relocations=listOf(Relocation("beta-origem","beta-destino","2026-06-01"))).canonical()
        val json=JSONObject(BackupCodec.json.encodeToString(expected))
        helper.createDatabase(name,3).use { sql ->
            for(field in listOf("classes","students","terms","lessons","activities","marks","grades","drafts","recompositions","relocations")) {
                val rows=json.getJSONArray(field)
                for(i in 0 until rows.length()) {
                    val row=rows.getJSONObject(i)
                    // A fixture é do esquema antigo: a auditoria só passa a existir na v8.
                    if(field=="relocations") { assertEquals(0,row.getJSONArray("corrections").length());row.remove("corrections") }
                    val values=ContentValues()
                    row.keys().forEach{key->when(val v=row.get(key)){JSONObject.NULL->values.putNull(key);is Boolean->values.put(key,if(v)1 else 0);else->values.put(key,v.toString())}}
                    sql.insert(if(field=="drafts")"import_drafts"else field,0,values)
                }
            }
        }
        helper.runMigrationsAndValidate(name,4,true,MIGRATION_3_4).close();open()
        assertEquals(expected,manager.snapshot());assertTrue(repo.dao.exportRows("classe-alfa").isEmpty())
        save(ExportEditSession.start(data(),"classe-alfa","HIDE").hide("alfa",true))
        assertEquals(expected,school(manager.snapshot()))
    }
}
