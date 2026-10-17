package br.com.diariodeclasse

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.domain.CsvRoster
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class StageTwoDataInstrumentedTest {
    private lateinit var context: Context
    private lateinit var db: DiaryDatabase
    private lateinit var repo: DiaryRepository
    private lateinit var folder: File
    private lateinit var prefs: LocalSelectionStore
    private lateinit var manager: BackupManager
    private val c=SchoolClass(id="s2-class",name="Turma fictícia",subject="História",year=2026)
    @Before fun setup() = runBlocking {
        context=ApplicationProvider.getApplicationContext()
        context.deleteDatabase("stage-two-test.db")
        db=Room.databaseBuilder(context,DiaryDatabase::class.java,"stage-two-test.db").build()
        repo=DiaryRepository(db)
        folder=File(context.cacheDir,"stage-two-safety")
        prefs=LocalSelectionStore(context,"stage-two-prefs");prefs.write(null)
        manager=BackupManager(db,FileSafetyBackupStore(folder),3,null,prefs)
        repo.saveClass(c,"2026-01-01")
    }
    @After fun cleanup() { db.close();context.deleteDatabase("stage-two-test.db");prefs.write(null);folder.deleteRecursively() }
    @Test fun noWeeklyScheduleAndRetroactiveDateInSameYear() = runBlocking {
        assertTrue(db.dao().day(c.id,"2026-03-10").isEmpty())
        repo.extraLesson(c.id,"2026-03-10","Teste fictício")
        assertEquals(4,db.dao().periods(c.id).size)
        assertEquals("2026-01-01",db.dao().periods(c.id).first().start)
        assertEquals(1,db.dao().day(c.id,"2026-03-10").size)
    }
    @Test fun metadataEditPreservesScheduleAndEveryLesson() = runBlocking {
        val old=c.copy(schedule="0,0,2,1,0,0,0",duration=45)
        repo.saveClass(old,"2026-01-01")
        db.dao().save(Lesson("legacy-s2",c.id,"2026-03-04",1))
        val first=db.dao().day(c.id,"2026-03-04").first()
        repo.startLesson(first.id)
        val before=manager.snapshot()
        repo.saveClass(old.copy(name="Turma fictícia editada",subject="Geografia"),"2026-01-01")
        assertEquals(before.lessons,manager.snapshot().lessons)
        assertEquals(old.schedule,db.dao().schoolClass(c.id)!!.schedule)
        assertEquals(45,db.dao().schoolClass(c.id)!!.duration)
    }
    @Test fun textualImportKeepsNumbersAndHasNoEnrollmentBeforeConfirmation() = runBlocking {
        repo.appendDrafts(c.id,CsvRoster.parse("Número;Nome\n18;Estudante Fictício Beta\n3;Estudante Fictício Alfa"))
        assertTrue(db.dao().roster(c.id).isEmpty())
        val drafts=db.dao().draftRows(c.id)
        assertEquals(listOf(18,3),drafts.map { it.number })
        drafts.forEach { db.dao().save(it.copy(reviewed=true)) }
        repo.confirmImport(c.id,"2026-02-01")
        assertEquals(listOf(3,18),db.dao().roster(c.id).map { it.number })
    }
    @Test fun duplicateConflictRollsBackAndExplicitHomonymRequiresDistinctNumber() = runBlocking {
        repo.saveStudent(Student(classId=c.id,name="Estudante Fictício Alfa",number=1,joined="2026-01-01"))
        repo.appendDrafts(c.id,CsvRoster.parse("2 Estudante Fictício Beta\n3 Estudante Fictício Alfa"))
        db.dao().draftRows(c.id).forEach { db.dao().save(it.copy(reviewed=true)) }
        try { repo.confirmImport(c.id,"2026-02-01");fail() } catch (_:IllegalArgumentException) { }
        assertEquals(1,db.dao().roster(c.id).size)
        assertEquals(2,db.dao().draftRows(c.id).size)
        db.dao().draftRows(c.id).forEach { db.dao().save(it.copy(allowHomonym=true)) }
        assertEquals(2,repo.confirmImport(c.id,"2026-02-01"))
    }
    @Test fun reorderingAndTransferKeepMembershipHistoryAndPersonId() = runBlocking {
        val a=Student(classId=c.id,name="Estudante Fictício Alfa",number=2,joined="2026-01-01")
        val b=a.copy(id=newId(),personId=newId(),name="Estudante Fictício Beta",number=9)
        repo.saveStudent(a);repo.saveStudent(b)
        repo.extraLesson(c.id,"2026-03-10","Teste")
        val lesson=db.dao().day(c.id,"2026-03-10").single()
        repo.startLesson(lesson.id);repo.mark(lesson.id,a.id,"F")
        val activity=Activity(classId=c.id,name="Atividade fictícia",date="2026-03-10",term=1,maximum=10.0)
        repo.saveActivity(activity);repo.saveGrade(activity.id,a.id,0.0)
        repo.moveStudent(a.id,1)
        assertEquals(9,db.dao().student(a.id)!!.number)
        val target=c.copy(id="s2-destination")
        repo.saveClass(target,"2026-01-01");repo.transfer(a.id,target.id,"2026-04-01")
        assertEquals("F",db.dao().attendance(lesson.id).single().mark)
        assertEquals(0.0,db.dao().activityGrades(activity.id).single().value!!,0.0)
        assertEquals(a.personId,db.dao().roster(target.id).single().personId)
        assertEquals("Transferido",db.dao().student(a.id)!!.status)
    }
    @Test fun selectionSurvivesNewStoreAndBackupRestoresItAlongWithLegacyDraft() = runBlocking {
        val legacy=ImportDraft(classId=c.id,name="",number=0,position=0,page=2,reason="Rascunho legado")
        db.dao().save(legacy)
        prefs.write(AppSelection(2026,c.id))
        assertEquals(AppSelection(2026,c.id),LocalSelectionStore(context,"stage-two-prefs").read())
        val before=manager.snapshot();val bytes=manager.backupBytes()
        prefs.write(AppSelection(2025,null))
        manager.restore(manager.prepare(bytes.inputStream()),"RESTAURAR")
        assertEquals(before,manager.snapshot())
        assertEquals(legacy,db.dao().draftRows(c.id).single())
        val old=before.copy(selection=null)
        manager.restore(manager.prepare(BackupCodec.encode(old,2,null).inputStream()),"RESTAURAR")
        assertNull(prefs.read())
        assertEquals(old,manager.snapshot())
    }
    @Test fun visualPreferenceFailureDoesNotLoseRestoredSchoolDataAndIsReported() = runBlocking {
        val bytes=manager.backupBytes()
        val blocked=BackupManager(db,FileSafetyBackupStore(folder),3,null,object:SelectionStore {
            override fun read():AppSelection?=null
            override fun write(value:AppSelection?) { throw java.io.IOException("simulada") }
        })
        val result=blocked.restore(blocked.prepare(bytes.inputStream()),"RESTAURAR")
        assertTrue(result.warning.isNotBlank())
        assertEquals(c,db.dao().schoolClass(c.id))
    }
}
