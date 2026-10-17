package br.com.diariodeclasse

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ActivityChangesInstrumentedTest {
    private lateinit var context:Context
    private lateinit var db:DiaryDatabase
    private lateinit var repo:DiaryRepository
    private val c=SchoolClass(id="grade-test-class",name="Turma Fictícia Notas",subject="História",year=2026)
    private val a=Activity(id="grade-test-a",classId=c.id,name="Avaliação Fictícia",date="2026-08-06",term=3,maximum=10.0)
    private val other=a.copy(id="grade-test-other",name="Outra Atividade Fictícia",maximum=5.0)
    private val students=(1..4).map {Student(id="grade-student-$it",personId="grade-person-$it",classId=c.id,name="Estudante Fictício $it",number=it,joined="2026-01-01")}
    @Before fun setup()=runBlocking {
        context=ApplicationProvider.getApplicationContext();context.deleteDatabase("grade-change-test.db")
        db=Room.databaseBuilder(context,DiaryDatabase::class.java,"grade-change-test.db").build();repo=DiaryRepository(db)
        repo.saveClass(c,"2026-01-01");students.forEach {repo.saveStudent(it)}
        repo.saveActivity(a);repo.saveActivity(other)
        repo.saveGrade(a.id,students[0].id,8.0);repo.saveGrade(a.id,students[1].id,0.0);repo.saveGrade(a.id,students[2].id,null)
        repo.saveGrade(other.id,students[0].id,4.0)
    }
    @After fun cleanup(){db.close();context.deleteDatabase("grade-change-test.db")}
    private suspend fun snapshot()=BackupManager(db,FileSafetyBackupStore(File(context.cacheDir,"grade-change-safety")),6,null).snapshot()
    private suspend fun scale(old:Double,next:Double,value:Double,result:Double) {
        repo.dao.save(a.copy(maximum=old));repo.dao.save(Grade(a.id,students[0].id,value))
        val before=snapshot();val current=repo.dao.activity(a.id)!!
        val plan=repo.previewActivityEdit(current.copy(maximum=next),current)
        assertTrue(plan.needsChoice);assertEquals(2,plan.before.launched)
        repo.applyActivityEdit(plan,GradeChangeMode.PROPORTIONAL)
        assertEquals(next,repo.dao.activity(a.id)!!.maximum,0.0)
        val grades=repo.dao.activityGrades(a.id)
        assertEquals(result,grades.single {it.studentId==students[0].id}.value!!,1e-14)
        assertEquals(0.0,grades.single {it.studentId==students[1].id}.value!!,0.0)
        assertNull(grades.single {it.studentId==students[2].id}.value)
        assertTrue(grades.none {it.studentId==students[3].id})
        val after=snapshot()
        assertEquals(before.grades.filter {it.activityId!=a.id},after.grades.filter {it.activityId!=a.id})
        assertEquals(before.activities.filter {it.id!=a.id},after.activities.filter {it.id!=a.id})
        assertEquals(before.grades.map {it.activityId to it.studentId}.toSet(),after.grades.map {it.activityId to it.studentId}.toSet())
    }
    @Test fun reduceTenToOneUpdatesTotalsAndSurvivesReopen()=runBlocking {
        scale(10.0,1.0,8.0,0.8)
        val data=repo.snapshot.first {it.activities.any {a->a.id==this@ActivityChangesInstrumentedTest.a.id&&a.maximum==1.0}&&it.grades.any {g->g.activityId==a.id&&g.value==0.8}}
        assertEquals(4.8,data.result(students[0],3,10.0).total,1e-14)
        db.close();db=Room.databaseBuilder(context,DiaryDatabase::class.java,"grade-change-test.db").build();repo=DiaryRepository(db)
        assertEquals(0.8,repo.dao.activityGrades(a.id).single {it.studentId==students[0].id}.value!!,0.0)
        assertEquals(1.0,repo.dao.activity(a.id)!!.maximum,0.0)
        val manager=BackupManager(db,FileSafetyBackupStore(File(context.cacheDir,"grade-change-safety")),6,null)
        val before=manager.snapshot();val backup=manager.backupBytes()
        repo.saveGrade(a.id,students[0].id,0.5);manager.restore(manager.prepare(backup.inputStream()),"RESTAURAR")
        assertEquals(before,manager.snapshot())
    }
    @Test fun reduceFiveToTwo()=runBlocking {scale(5.0,2.0,4.0,1.6)}
    @Test fun increaseTwoToFive()=runBlocking {scale(2.0,5.0,1.5,3.75)}
    @Test fun keepingCompatibleValuesOnIncreaseAndReduction()=runBlocking {
        for(max in listOf(20.0,9.0)) {
            val before=repo.activitySnapshot(a.id)
            val plan=repo.previewActivityEdit(before.activity.copy(maximum=max))
            assertTrue(plan.canKeep);assertTrue(plan.needsChoice)
            repo.applyActivityEdit(plan,GradeChangeMode.KEEP)
            assertEquals(before.grades,repo.activitySnapshot(a.id).grades)
        }
    }
    @Test fun incompatibleKeepAndUnconfirmedMaximumChangeAreBlocked()=runBlocking {
        val before=snapshot();val plan=repo.previewActivityEdit(a.copy(maximum=1.0))
        assertFalse(plan.canKeep)
        try {repo.applyActivityEdit(plan,GradeChangeMode.KEEP);fail()}catch(_:IllegalArgumentException){}
        try {repo.saveActivity(a.copy(maximum=20.0));fail()}catch(_:IllegalArgumentException){}
        assertEquals(before,snapshot())
    }
    @Test fun previewCancellationAndStaleConfirmationNeverOverwriteChanges()=runBlocking {
        val before=snapshot();val plan=repo.previewActivityEdit(a.copy(maximum=1.0))
        assertEquals(before,snapshot()) // cancelar é descartar esta prévia, sem nenhuma escrita
        repo.saveGrade(a.id,students[0].id,7.0);val edited=snapshot()
        try {repo.applyActivityEdit(plan,GradeChangeMode.PROPORTIONAL);fail()}catch(_:IllegalArgumentException){}
        try {repo.deleteActivity(plan.before);fail()}catch(_:IllegalArgumentException){}
        assertEquals(edited,snapshot())
    }
    @Test fun failureAfterPartialGradeWritesRollsBackMaximumAndEveryGrade()=runBlocking {
        val before=snapshot();val plan=repo.previewActivityEdit(a.copy(maximum=1.0))
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_grade_change BEFORE UPDATE ON grades WHEN NEW.activityId='grade-test-a' AND NEW.studentId='grade-student-2' BEGIN SELECT RAISE(ABORT, 'falha simulada'); END")
        try {repo.applyActivityEdit(plan,GradeChangeMode.PROPORTIONAL);fail("Exige rollback")}catch(_:android.database.sqlite.SQLiteException){}
        assertEquals(before,snapshot())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_grade_change")
    }
    @Test fun deletionRemovesOnlyConfirmedActivityIncludingPendingAndZero()=runBlocking {
        val target=repo.activitySnapshot(a.id);assertEquals(2,target.launched)
        val preserved=repo.activitySnapshot(other.id)
        repo.deleteActivity(target)
        assertNull(repo.dao.activity(a.id));assertTrue(repo.dao.activityGrades(a.id).isEmpty())
        assertEquals(preserved,repo.activitySnapshot(other.id))
        val empty=a.copy(id="grade-empty");repo.saveActivity(empty)
        assertEquals(0,repo.activitySnapshot(empty.id).launched);repo.deleteActivity(repo.activitySnapshot(empty.id))
        assertNull(repo.dao.activity(empty.id))
    }
    @Test fun failedCascadeDeletionPreservesActivityAndAllGrades()=runBlocking {
        val before=snapshot();val target=repo.activitySnapshot(a.id)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_grade_delete BEFORE DELETE ON grades WHEN OLD.activityId='grade-test-a' BEGIN SELECT RAISE(ABORT, 'falha simulada'); END")
        try {repo.deleteActivity(target);fail("Exige rollback")}catch(_:android.database.sqlite.SQLiteException){}
        assertEquals(before,snapshot());db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_grade_delete")
    }
    @Test fun editingEveryMetadataFieldKeepsActivityAndGradeIdentifiers()=runBlocking {
        val before=repo.activitySnapshot(a.id)
        val updated=a.copy(name="Atividade Fictícia Editada",type="Seminário",date="2026-10-20",term=4,description="Descrição fictícia",notes="Observação fictícia")
        repo.applyActivityEdit(repo.previewActivityEdit(updated,a),GradeChangeMode.KEEP)
        assertEquals(updated,repo.dao.activity(a.id));assertEquals(before.grades,repo.activitySnapshot(a.id).grades)
    }
}
