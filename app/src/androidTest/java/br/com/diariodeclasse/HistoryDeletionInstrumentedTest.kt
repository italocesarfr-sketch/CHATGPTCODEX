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
import br.com.diariodeclasse.export.*
import br.com.diariodeclasse.reports.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.io.File

class HistoryDeletionInstrumentedTest {
    @get:Rule val helper=MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),DiaryDatabase::class.java.canonicalName!!,FrameworkSQLiteOpenHelperFactory())
    private val context:Context get()=ApplicationProvider.getApplicationContext()
    private val name="history-deletion-test.db"
    private lateinit var db:DiaryDatabase
    private lateinit var repo:DiaryRepository
    private lateinit var manager:BackupManager
    private lateinit var folder:File
    private lateinit var original:BackupSnapshot
    private fun open() {
        db=Room.databaseBuilder(context,DiaryDatabase::class.java,name).addMigrations(MIGRATION_1_2,MIGRATION_2_3,MIGRATION_3_4,MIGRATION_4_5,MIGRATION_5_6,MIGRATION_6_7,MIGRATION_7_8).build()
        repo=DiaryRepository(db);manager=BackupManager(db,FileSafetyBackupStore(folder),18,null)
    }
    private suspend fun data()=repo.snapshot.first {it.loaded}
    private suspend fun remove(id:String) {val p=repo.previewClassDeletion(id);assertNull(p.blockedReason);repo.deleteClass(p,p.confirmation)}
    private suspend fun restore(s:BackupSnapshot) {val bytes=BackupCodec.encode(s,18,null);manager.restore(manager.prepare(bytes.inputStream()),"RESTAURAR")}
    private fun report(d:DiarySnapshot)=SchoolReports(d,d.classes.first {it.id=="history-h"}).student(d.students.first {it.id=="hh"},3)
    @Before fun setup()=runBlocking {
        context.deleteDatabase(name);folder=File(context.cacheDir,"history-delete-${newId()}");open()
        original=BackupCodec.json.decodeFromString<BackupSnapshot>(InstrumentationRegistry.getInstrumentation().context.assets.open("history-deletion.json").bufferedReader().use {it.readText()}).canonical()
        restore(original)
    }
    @After fun cleanup() {db.close();context.deleteDatabase(name);folder.deleteRecursively()}
    @Test fun deletingOriginActuallyRemovesClassAndPreservesNumbersNotesAndAttendance()=runBlocking {
        val old=report(data());val p=repo.previewClassDeletion("history-x")
        assertEquals(1,p.counts["Históricos de matrículas preservados"]);assertEquals(3,p.counts["Notas preservadas no histórico"])
        repo.deleteClass(p,p.confirmation)
        assertNull(repo.dao.schoolClass("history-x"));assertNull(repo.dao.student("hx"));assertNull(repo.dao.activity("hx-score"))
        val d=data();val row=report(d)
        assertEquals(old.original,row.original);assertEquals(old.total,row.total);assertEquals(old.recomposition,row.recomposition);assertEquals(old.attendance,row.attendance)
        assertEquals(original.students.first {it.id=="hx"},d.histories.single().content.students.single())
        assertEquals(original.students.filter {it.classId!="history-x"},manager.snapshot().students)
        assertEquals(original.marks.filter {it.studentId=="hx"},d.histories.single().content.marks)
        db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use {assertFalse(it.moveToFirst())}
    }
    @Test fun successiveOrIntermediateFirstDeletionKeepsExactExportAndDoesNotDuplicateAnything()=runBlocking {
        val d=data();val c=d.classes.first {it.id=="history-h"}
        val tables=ExportModel.entries.associateWith {ExportProjection.build(d,c,ExportOptions(it,null))}
        remove("history-x");remove("history-g");val expected=manager.snapshot()
        for((model,table) in tables) assertEquals(table,ExportProjection.build(data(),c,ExportOptions(model,null)))
        restore(original);remove("history-g");remove("history-x")
        assertEquals(expected,manager.snapshot());assertEquals(2,data().academic().relocations.size)
        assertEquals(listOf("hx","hg","hh"),data().lineage(repo.dao.student("hh")!!).map {it.id})
        assertEquals(5,data().academic().grades.size);assertEquals(8,report(data()).attendance!!.performed)
    }
    @Test fun errorsWritingHistoryOrDeletingAfterItsWriteRollbackTheWholeDatabase()=runBlocking {
        for((name,sql) in listOf("history_insert" to "CREATE TRIGGER history_insert BEFORE INSERT ON preserved_histories BEGIN SELECT RAISE(ABORT,'test-only'); END",
            "history_delete" to "CREATE TRIGGER history_delete BEFORE DELETE ON classes WHEN OLD.id='history-x' BEGIN SELECT RAISE(ABORT,'test-only'); END")) {
            db.openHelper.writableDatabase.execSQL(sql)
            val before=manager.snapshot();val p=repo.previewClassDeletion("history-x")
            try {repo.deleteClass(p,p.confirmation);fail("Falha simulada deveria cancelar a transação")}catch(_:android.database.sqlite.SQLiteException){}
            assertEquals(before,manager.snapshot());assertTrue(data().histories.isEmpty())
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER $name")
        }
    }
    @Test fun cancelAndStaleConfirmationDoNotDetachOrDeleteAnyHistory()=runBlocking {
        val before=manager.snapshot();val p=repo.previewClassDeletion("history-x")
        assertEquals(before,manager.snapshot())
        try {repo.deleteClass(p,"CANCELAR");fail()}catch(_:IllegalArgumentException){}
        assertEquals(before,manager.snapshot())
        repo.saveGrade("hh-score","hh",1.5);val changed=manager.snapshot()
        try {repo.deleteClass(p,p.confirmation);fail()}catch(_:IllegalArgumentException){}
        assertEquals(changed,manager.snapshot());assertTrue(data().histories.isEmpty())
    }
    @Test fun backupRestoreDatabaseReopenAndRestoreFailureKeepAllDetachedHistory()=runBlocking {
        remove("history-x");remove("history-g");val expected=manager.snapshot();val bytes=manager.backupBytes()
        db.close();open();assertEquals(expected,manager.snapshot());assertEquals(6.0,report(data()).total!!,0.0)
        restore(original)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_history_restore BEFORE INSERT ON preserved_histories BEGIN SELECT RAISE(ABORT,'test-only'); END")
        val before=manager.snapshot();try {manager.restore(manager.prepare(bytes.inputStream()),"RESTAURAR");fail()}catch(_:android.database.sqlite.SQLiteException){}
        assertEquals(before,manager.snapshot());db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_history_restore")
        manager.restore(manager.prepare(bytes.inputStream()),"RESTAURAR");assertEquals(expected,manager.snapshot())
        db.close();open();assertEquals(expected,manager.snapshot())
    }
    @Test fun inheritedRecompositionStillEditsAndDeletesInPlaceWithoutTouchingOriginalGrades()=runBlocking {
        remove("history-x");val grades=data().academic().grades
        val old=report(data()).recomposition!!
        repo.saveRecomposition(old.copy(value=7.5),old,"hh")
        assertEquals(7.5,report(data()).total!!,0.0);assertEquals("hx",report(data()).recomposition!!.studentId)
        repo.deleteRecomposition(old.copy(value=7.5),"hh")
        assertEquals(4.5,report(data()).total!!,0.0);assertNull(report(data()).recomposition)
        repo.saveRecomposition(Recomposition("hh",3,0.0,"2026-11-01"),contextStudentId="hh")
        assertEquals(4.5,report(data()).total!!,0.0);assertEquals(grades,data().academic().grades)
    }
    @Test fun aFurtherRelocationStillSeesDeletedAncestorsAndEnforcesSharedCriteria()=runBlocking {
        remove("history-x");remove("history-g")
        val c=repo.dao.schoolClass("history-h")!!;val next=c.copy(id="history-next",name="Destino Fictício Posterior")
        repo.saveClass(next,"2026-01-01")
        try {repo.saveClass(c.copy(countJustified=false),"2026-01-01");fail()}catch(_:IllegalArgumentException){}
        val p=repo.previewRelocation("hh",next.id,null,"2026-10-01");repo.confirmRelocation(p)
        val chain=data().lineage(p.target!!);assertEquals(listOf("hx","hg","hh",p.target!!.id),chain.map {it.id})
        assertEquals(6.0,SchoolReports(data(),next).student(p.target!!,3).total!!,0.0)
        val enrolled=repo.dao.student(p.target!!.id)!!
        try {repo.saveStudent(enrolled.copy(joined="2026-01-01"));fail()}catch(_:IllegalArgumentException){}
    }
    @Test fun deletingLastConnectedClassCleansOnlyUnneededHistoryAndPreservesUnrelatedRows()=runBlocking {
        val unrelated=original.students.first {it.id=="unrelated"}
        remove("history-g");remove("history-x");remove("history-h")
        assertTrue(manager.snapshot().histories.isEmpty());assertEquals(unrelated,repo.dao.student(unrelated.id))
        assertEquals(listOf("history-other"),manager.snapshot().classes.map {it.id})
    }
    @Test fun distributedSchemaSixMigratesAdditivelyWithoutChangingAnyOfItsThirteenTables()=runBlocking {
        val expected=manager.snapshot();db.close();context.deleteDatabase(name)
        val json=JSONObject(BackupCodec.json.encodeToString(expected))
        helper.createDatabase(name,6).use {sql ->
            for(field in listOf("classes","students","terms","lessons","activities","marks","grades","drafts","recompositions","relocations","exportRows","classArchives","lessonRecords")) {
                val rows=json.getJSONArray(field)
                for(i in 0 until rows.length()) {
                    val row=rows.getJSONObject(i)
                    // A fixture é do esquema antigo: a auditoria só passa a existir na v8.
                    if(field=="relocations") { assertEquals(0,row.getJSONArray("corrections").length());row.remove("corrections") }
                    val values=ContentValues()
                    row.keys().forEach {key -> when(val v=row.get(key)) {JSONObject.NULL->values.putNull(key);is Boolean->values.put(key,if(v)1 else 0);else->values.put(key,v.toString())}}
                    sql.insert(when(field) {"drafts"->"import_drafts";"exportRows"->"export_rows";"classArchives"->"class_archives";"lessonRecords"->"lesson_records";else->field},0,values)
                }
            }
        }
        helper.runMigrationsAndValidate(name,7,true,MIGRATION_6_7).close();open()
        assertEquals(expected,manager.snapshot());assertTrue(data().histories.isEmpty())
        remove("history-x");assertEquals(6.0,report(data()).total!!,0.0)
    }
}
