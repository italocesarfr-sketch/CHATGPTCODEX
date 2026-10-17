package br.com.diariodeclasse

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.reports.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.decodeFromString
import org.junit.*
import org.junit.Assert.*
import java.io.File

class ReportsInstrumentedTest {
    private lateinit var context: Context
    private lateinit var db: DiaryDatabase
    private lateinit var repo: DiaryRepository
    private lateinit var manager: BackupManager
    private lateinit var folder: File
    private val name="reports-exclusive-test.db"
    @Before fun setup() { runBlocking {
        context=ApplicationProvider.getApplicationContext();context.deleteDatabase(name)
        db=Room.databaseBuilder(context,DiaryDatabase::class.java,name).build();repo=DiaryRepository(db)
        folder=File(context.cacheDir,"reports-${java.util.UUID.randomUUID()}")
        manager=BackupManager(db,FileSafetyBackupStore(folder),8,null)
        val fixture=InstrumentationRegistry.getInstrumentation().context.assets.open("baseline-v1.json").bufferedReader().use { BackupCodec.json.decodeFromString<BackupSnapshot>(it.readText()) }
        manager.restore(manager.prepare(BackupCodec.encode(fixture,1,null).inputStream()),"RESTAURAR")
    } }
    @After fun cleanup() { db.close();context.deleteDatabase(name);folder.deleteRecursively() }
    private suspend fun current(predicate: (DiarySnapshot)->Boolean = {true}) = withTimeout(10000) { repo.snapshot.first { it.loaded && predicate(it) } }
    @Test fun reportsReadAllLegacyTablesWithoutWritingAndRespectPartialMembership() { runBlocking {
        val before=manager.snapshot();val data=current();val c=data.classes.first {it.id=="classe-alfa"}
        val reports=SchoolReports(data,c)
        val alfa=data.students.first {it.id=="alfa"}; val beta=data.students.first {it.id=="beta-origem"};val late=data.students.first {it.id=="gama"}
        assertEquals(0.0,reports.student(alfa,1).total!!,0.0)
        assertNull(reports.student(beta,1).total)
        assertEquals(1,reports.student(alfa,1).frequency!!.absent)
        assertEquals(1,reports.student(beta,1).frequency!!.justified)
        assertEquals(0,reports.student(late,1).frequency!!.absent)
        assertEquals(0,reports.student(beta,3).frequency!!.performed)
        reports.annual();reports.mostAbsent(1);reports.term(4)
        assertEquals(before,manager.snapshot())
    } }
    @Test fun roomFlowsRefreshReportsAfterGradesAttendanceCancellationAndRecoveryOfBackup() { runBlocking {
        val before=manager.backupBytes()
        repo.saveGrade("atividade-1","alfa",6.0)
        var data=current {it.grades.any {g->g.activityId=="atividade-1"&&g.studentId=="alfa"&&g.value==6.0}}
        var c=data.classes.first {it.id=="classe-alfa"};var a=data.students.first {it.id=="alfa"}
        assertEquals(6.0,SchoolReports(data,c).student(a,1).total!!,0.0)
        repo.mark("aula-2","alfa","P")
        data=current {it.marks.any {m->m.lessonId=="aula-2"&&m.studentId=="alfa"&&m.mark=="P"}}
        assertEquals(0,SchoolReports(data,c).student(a,1).frequency!!.absent)
        repo.lessonState("aula-1","CANCELADA")
        data=current {it.lessons.any {l->l.id=="aula-1"&&l.state=="CANCELADA"}}
        assertEquals(1,SchoolReports(data,c).student(a,1).frequency!!.performed)
        manager.restore(manager.prepare(before.inputStream()),"RESTAURAR")
        data=current {it.grades.any {g->g.activityId=="atividade-1"&&g.studentId=="alfa"&&g.value==0.0}&&it.lessons.any {l->l.id=="aula-1"&&l.state=="REALIZADA"}&&it.marks.any {m->m.lessonId=="aula-2"&&m.studentId=="alfa"&&m.mark=="F"}}
        assertEquals(0.0,SchoolReports(data,c).student(a,1).total!!,0.0)
        assertEquals(1,SchoolReports(data,c).student(a,1).frequency!!.absent)
    } }
    @Test fun fourTermsPersistAfterReopenAndNewReportsKeepBackupCompatible() { runBlocking {
        val c=repo.dao.schoolClass("classe-alfa")!!
        repo.saveActivity(Activity("report-q2",c.id,"Avaliação Fictícia B2",date="2026-05-10",term=2,maximum=10.0));repo.saveGrade("report-q2","alfa",5.0)
        repo.saveActivity(Activity("report-q4",c.id,"Avaliação Fictícia B4",date="2026-11-10",term=4,maximum=10.0));repo.saveGrade("report-q4","alfa",null)
        val expected=manager.snapshot()
        db.close();db=Room.databaseBuilder(context,DiaryDatabase::class.java,name).build();repo=DiaryRepository(db);manager=BackupManager(db,FileSafetyBackupStore(folder),8,null)
        val data=current {it.activities.any {a->a.id=="report-q4"}}
        val annual=SchoolReports(data,c).year(data.students.first {it.id=="alfa"})
        assertEquals(listOf(0.0,5.0,1.5,null),annual.terms.map {it.total})
        assertEquals(GradeState.NO_SCORES,annual.terms[3].state)
        assertEquals(expected,BackupCodec.decode(manager.backupBytes()).snapshot)
        assertEquals(expected,manager.snapshot())
    } }
}
