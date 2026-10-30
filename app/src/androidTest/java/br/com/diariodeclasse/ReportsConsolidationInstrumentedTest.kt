package br.com.diariodeclasse

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.reports.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.decodeFromString
import org.junit.*
import org.junit.Assert.*
import java.io.File

class ReportsConsolidationInstrumentedTest {
    private val context:Context get()=ApplicationProvider.getApplicationContext()
    private val name="reports-consolidation-exclusive.db"
    private lateinit var db:DiaryDatabase
    private lateinit var repo:DiaryRepository
    private lateinit var manager:BackupManager
    private lateinit var folder:File
    private fun open() {
        db=Room.databaseBuilder(context,DiaryDatabase::class.java,name).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,MIGRATION_7_8,MIGRATION_8_9,MIGRATION_9_10,MIGRATION_10_11).build()
        repo=DiaryRepository(db);manager=BackupManager(db,FileSafetyBackupStore(folder),10,null)
    }
    @Before fun setup() {runBlocking {
        context.deleteDatabase(name);folder=File(context.cacheDir,"reports-final-${java.util.UUID.randomUUID()}");open()
        val fixture=InstrumentationRegistry.getInstrumentation().context.assets.open("baseline-v1.json").bufferedReader().use {BackupCodec.json.decodeFromString<BackupSnapshot>(it.readText())}
        manager.restore(manager.prepare(BackupCodec.encode(fixture,10,null).inputStream()),"RESTAURAR")
    }}
    @After fun cleanup() {db.close();context.deleteDatabase(name);folder.deleteRecursively()}
    private suspend fun current(predicate:(DiarySnapshot)->Boolean={true})=withTimeout(10000) {repo.snapshot.first {it.loaded&&predicate(it)}}
    @Test fun everyObservedSnapshotIsConsistentDuringTransactionalProportionalEdits() {runBlocking {
        val a=Activity("atomic-report","classe-alfa","Prova Fictícia Atômica",date="2026-08-10",term=3,maximum=10.0)
        repo.saveActivity(a);repo.saveGrade(a.id,"alfa",8.0)
        repo.saveRecomposition(Recomposition("alfa",3,5.0,"2026-11-20"))
        val started=CompletableDeferred<Unit>();val finished=CompletableDeferred<Unit>();var observations=0
        val watch=launch {
            repo.snapshot.collect { d ->
                val activity=d.activities.single {it.id==a.id};val grade=d.grades.single {it.activityId==a.id&&it.studentId=="alfa"}
                assertEquals(0.8,grade.value!!/activity.maximum,1e-10)
                val c=d.classes.first {it.id=="classe-alfa"};val s=d.students.first {it.id=="alfa"}
                val row=SchoolReports(d,c).student(s,3)
                assertTrue(row.total!!>=5.0);assertEquals(5.0,row.recomposition!!.value!!,0.0)
                observations++;started.complete(Unit)
                if(activity.notes=="final")finished.complete(Unit)
            }
        }
        try {
            withTimeout(10000){started.await()}
            for(n in 1..24) {
                val old=repo.dao.activity(a.id)!!
                val proposed=old.copy(maximum=listOf(1.0,5.0,2.0,10.0)[n%4],notes=if(n==24)"final" else "edição-$n")
                repo.applyActivityEdit(repo.previewActivityEdit(proposed),GradeChangeMode.PROPORTIONAL)
            }
            withTimeout(10000){finished.await()};assertTrue(observations>=2)
        } finally {watch.cancelAndJoin()}
    }}
    @Test fun annualRecompositionAndAttendanceSurviveRestartBackupAndRestore() {runBlocking {
        repo.saveRecomposition(Recomposition("alfa",1,0.0,"2026-11-20"))
        repo.saveRecomposition(Recomposition("alfa",3,6.0,"2026-11-20"))
        repo.saveActivity(Activity("final-b2","classe-alfa","Recuperação Comum Fictícia",date="2026-05-10",term=2,maximum=10.0))
        repo.saveGrade("final-b2","alfa",9.5)
        val expected=manager.snapshot();val backup=manager.backupBytes()
        db.close();open()
        assertEquals(expected,manager.snapshot())
        var d=current();val c=d.classes.first {it.id=="classe-alfa"};val s=d.students.first {it.id=="alfa"}
        var report=SchoolReports(d,c)
        assertEquals(listOf(0.0,9.5,6.0,null),report.year(s).terms.map {it.total})
        assertEquals(1,report.student(s,1).attendance!!.effectiveAbsences)
        repo.mark("aula-2","alfa","P");repo.saveGrade("atividade-2","alfa",2.0)
        manager.restore(manager.prepare(backup.inputStream()),"RESTAURAR")
        d=current {it.marks.any {m->m.lessonId=="aula-2"&&m.studentId=="alfa"&&m.mark=="F"}}
        report=SchoolReports(d,c)
        assertEquals(1.5,report.student(s,3).original!!,0.0);assertEquals(6.0,report.student(s,3).total!!,0.0)
        assertEquals(expected,manager.snapshot())
        val before=manager.snapshot();report.year();report.term(2);report.mostAbsent(1);assertEquals(before,manager.snapshot())
    }}
    @Test fun editsMoveOrDeleteActivityWithoutMovingRecompositionOrInventingGrades() {runBlocking {
        val a=repo.dao.activity("atividade-2")!!
        repo.saveRecomposition(Recomposition("alfa",3,5.0,"2026-11-20"))
        repo.applyActivityEdit(repo.previewActivityEdit(a.copy(term=4,date="2026-11-10")),GradeChangeMode.KEEP)
        var d=current {it.activities.any {v->v.id==a.id&&v.term==4}}
        val c=d.classes.first {it.id=="classe-alfa"};val s=d.students.first {it.id=="alfa"}
        var reports=SchoolReports(d,c)
        assertNull(reports.student(s,3).original);assertNull(reports.student(s,3).total)
        assertEquals(5.0,reports.student(s,3).recomposition!!.value!!,0.0)
        assertEquals(1.5,reports.student(s,4).total!!,0.0)
        repo.deleteActivity(repo.activitySnapshot(a.id));d=current {it.activities.none {v->v.id==a.id}}
        reports=SchoolReports(d,c);assertNull(reports.student(s,4).total)
        assertEquals(1,manager.snapshot().recompositions.size)
    }}
}
