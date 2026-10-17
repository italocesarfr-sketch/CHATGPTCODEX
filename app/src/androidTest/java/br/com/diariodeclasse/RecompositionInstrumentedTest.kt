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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.io.File

class RecompositionInstrumentedTest {
    @get:Rule val helper=MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),DiaryDatabase::class.java.canonicalName!!,FrameworkSQLiteOpenHelperFactory())
    private val context:Context get()=ApplicationProvider.getApplicationContext()
    private val name="recomposition-exclusive.db"
    private lateinit var db:DiaryDatabase
    private lateinit var repo:DiaryRepository
    private lateinit var manager:BackupManager
    private lateinit var folder:File
    private fun fixtureText()=InstrumentationRegistry.getInstrumentation().context.assets.open("baseline-v1.json").bufferedReader().use {it.readText()}
    @Before fun setup() { runBlocking {
        context.deleteDatabase(name);folder=File(context.cacheDir,"recmp-${java.util.UUID.randomUUID()}")
        open()
        val f=BackupCodec.json.decodeFromString<BackupSnapshot>(fixtureText())
        manager.restore(manager.prepare(BackupCodec.encode(f,9,null).inputStream()),"RESTAURAR")
    } }
    private fun open() {
        db=Room.databaseBuilder(context,DiaryDatabase::class.java,name).addMigrations(MIGRATION_1_2).build()
        repo=DiaryRepository(db);manager=BackupManager(db,FileSafetyBackupStore(folder),9,null)
    }
    @After fun cleanup() {db.close();context.deleteDatabase(name);folder.deleteRecursively()}
    private suspend fun current(predicate:(DiarySnapshot)->Boolean={true})=withTimeout(10000) {repo.snapshot.first {it.loaded&&predicate(it)}}
    private fun report(data:DiarySnapshot,term:Int)=SchoolReports(data,data.classes.first {it.id=="classe-alfa"}).student(data.students.first {it.id=="alfa"},term)
    @Test fun higherLowerEqualEditDeleteAndOriginalCorrectionsNeverAccumulate() {runBlocking {
        repo.saveGrade("atividade-1","alfa",3.0)
        var r=Recomposition("alfa",1,5.0,"2026-11-20");repo.saveRecomposition(r)
        var data=current {it.recompositions.contains(r)&&it.grades.any {g->g.studentId=="alfa"&&g.activityId=="atividade-1"&&g.value==3.0}}
        assertEquals(3.0,report(data,1).original!!,0.0);assertEquals(5.0,report(data,1).total!!,0.0)
        val low=r.copy(value=2.0);repo.saveRecomposition(low,r);r=low
        data=current {it.recompositions.contains(r)};assertEquals(3.0,report(data,1).total!!,0.0)
        val equal=r.copy(value=3.0);repo.saveRecomposition(equal,r);r=equal
        data=current {it.recompositions.contains(r)};assertEquals(3.0,report(data,1).total!!,0.0)
        repo.saveGrade("atividade-1","alfa",7.0)
        data=current {it.grades.any {g->g.studentId=="alfa"&&g.activityId=="atividade-1"&&g.value==7.0}}
        assertEquals(7.0,report(data,1).total!!,0.0)
        repo.deleteRecomposition(r);data=current {it.recompositions.isEmpty()}
        assertEquals(7.0,report(data,1).total!!,0.0)
    }}
    @Test fun zeroNullUnknownAndThirdOriginDuringFourthRemainDistinctAfterReopen() {runBlocking {
        val rows=listOf(Recomposition("alfa",1,0.0,"2026-11-20"),Recomposition("alfa",2,null,"2026-11-20"),Recomposition("alfa",3,5.0,"2026-11-20"))
        rows.forEach {repo.saveRecomposition(it)}
        val before=manager.snapshot();db.close();open()
        assertEquals(before,manager.snapshot())
        val data=current {it.recompositions.size==3}
        assertEquals(0.0,report(data,1).total!!,0.0);assertNull(report(data,2).total)
        assertEquals(1.5,report(data,3).original!!,0.0);assertEquals(5.0,report(data,3).total!!,0.0)
        assertNull(report(data,4).total)
        assertEquals(before.grades,manager.snapshot().grades);assertEquals(before.marks,manager.snapshot().marks)
    }}
    @Test fun backupsIncludeAllRecompositionsAndRestoreOlderFormatsWithSafetyCopy() {runBlocking {
        repo.saveRecomposition(Recomposition("alfa",3,5.5,"2026-11-20"))
        repo.saveRecomposition(Recomposition("alfa",1,0.0,"2026-11-20"))
        repo.saveRecomposition(Recomposition("gama",3,null,"2026-11-20"))
        val expected=manager.snapshot();val bytes=manager.backupBytes()
        repo.deleteRecomposition(expected.recompositions.first())
        manager.restore(manager.prepare(bytes.inputStream()),"RESTAURAR")
        assertEquals(expected,manager.snapshot());assertEquals(expected,BackupCodec.decode(bytes).snapshot)
        val payload=fixtureText()
        for(format in 1..2) {
            val envelope=BackupEnvelope("diario-de-classe-backup",format,"br.com.diariodeclasse",1,BackupCodec.LEGACY_IDENTITY,"2026-10-17T00:00:00Z",8,null,BackupCodec.hash(payload.toByteArray()),payload)
            manager.restore(manager.prepare(BackupCodec.json.encodeToString(envelope).byteInputStream()),"RESTAURAR")
            assertTrue(manager.snapshot().recompositions.isEmpty())
            assertEquals(BackupCodec.json.decodeFromString<BackupSnapshot>(payload).canonical(),manager.snapshot())
            if(format==1) assertEquals(expected,BackupCodec.decode(FileSafetyBackupStore(folder).latest()!!.readBytes()).snapshot)
            manager.restore(manager.prepare(bytes.inputStream()),"RESTAURAR")
        }
    }}
    @Test fun failedWriteAndFailedRestoreLeaveEveryOriginalRowIntact() {runBlocking {
        val r=Recomposition("alfa",1,5.0,"2026-11-20");repo.saveRecomposition(r)
        val before=manager.snapshot()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_recmp BEFORE UPDATE ON recompositions BEGIN SELECT RAISE(ABORT,'test-only'); END")
        try {repo.saveRecomposition(r.copy(value=8.0),r);fail()}catch(_:android.database.sqlite.SQLiteException){}
        assertEquals(before,manager.snapshot())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_recmp")
        val changed=before.copy(recompositions=listOf(r.copy(value=9.0)),classes=before.classes.map {it.copy(name=it.name+" teste")})
        val prepared=manager.prepare(BackupCodec.encode(changed,9,null).inputStream())
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_recmp BEFORE INSERT ON recompositions BEGIN SELECT RAISE(ABORT,'test-only'); END")
        try {manager.restore(prepared,"RESTAURAR");fail()}catch(_:android.database.sqlite.SQLiteException){}
        assertEquals(before,manager.snapshot())
    }}
    @Test fun validatesBoundsAndRejectsStaleEditsWithoutAffectingOtherTables() {runBlocking {
        val before=manager.snapshot();val r=Recomposition("alfa",1,5.0,"2026-11-20")
        for(bad in listOf(r.copy(value=10.01),r.copy(value=Double.NaN),r.copy(term=5),r.copy(studentId="ausente"),r.copy(date="invalida"))) {
            try {repo.saveRecomposition(bad);fail()}catch(_:Exception){}
        }
        assertEquals(before,manager.snapshot())
        repo.saveRecomposition(r)
        try {repo.saveRecomposition(r.copy(value=9.0));fail()}catch(_:Exception){}
        assertEquals(listOf(r),manager.snapshot().recompositions)
        assertEquals(before,manager.snapshot().copy(recompositions=emptyList()))
    }}
    @Test fun migrationFromDistributedV1PreservesAllEightTablesAndAddsOnlyEmptyTable() {runBlocking {
        db.close();context.deleteDatabase(name)
        val text=fixtureText();val expected=BackupCodec.json.decodeFromString<BackupSnapshot>(text).canonical()
        val fixture=JSONObject(text)
        helper.createDatabase(name,1).use { sql ->
            for(field in listOf("classes","students","terms","lessons","activities","marks","grades","drafts")) {
                val rows=fixture.getJSONArray(field)
                for(i in 0 until rows.length()) {
                    val row=rows.getJSONObject(i);val values=ContentValues()
                    row.keys().forEach { key -> when(val v=row.get(key)) {
                        JSONObject.NULL -> values.putNull(key)
                        is Boolean -> values.put(key,if(v)1 else 0)
                        is Number -> values.put(key,v.toString())
                        else -> values.put(key,v.toString())
                    }}
                    sql.insert(if(field=="drafts") "import_drafts" else field,0,values)
                }
            }
        }
        helper.runMigrationsAndValidate(name,2,true,MIGRATION_1_2).close();open()
        assertEquals(expected,manager.snapshot());assertTrue(manager.snapshot().recompositions.isEmpty())
        repo.saveRecomposition(Recomposition("alfa",3,5.0,"2026-11-20"))
        assertEquals(5.0,report(current {it.recompositions.isNotEmpty()},3).total!!,0.0)
        db.close();open();assertEquals(1,manager.snapshot().recompositions.size)
    }}
}
