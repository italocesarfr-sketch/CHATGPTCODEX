package br.com.diariodeclasse

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.diariodeclasse.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryInstrumentedTest {
    private lateinit var db: DiaryDatabase
    private lateinit var repository: DiaryRepository
    private lateinit var context: Context
    private val dbName = "integration-test.db"
    private val c = SchoolClass(id = "test-class", name = "Turma fictícia", subject = "História", year = 2026, schedule = "0,0,2,1,0,0,0")
    @Before fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
        db = Room.databaseBuilder(context, DiaryDatabase::class.java, dbName).build()
        repository = DiaryRepository(db)
        repository.saveClass(c, "2026-01-01")
    }
    @After fun cleanup() { db.close(); context.deleteDatabase(dbName) }
    @Test fun draftRequiresReviewAndPersistsAcrossReopen() = runBlocking {
        val draft = ImportDraft(id = "draft-1", classId = c.id, name = "Estudante Fictício Alfa", number = 1, position = 0, page = 1)
        db.dao().save(draft)
        try { repository.confirmImport(c.id, "2026-02-01"); fail("Exige revisão") } catch (_: IllegalArgumentException) { }
        assertTrue(db.dao().roster(c.id).isEmpty())
        db.close()
        db = Room.databaseBuilder(context, DiaryDatabase::class.java, dbName).build()
        repository = DiaryRepository(db)
        assertEquals(1, db.dao().draftRows(c.id).size)
        db.dao().save(draft.copy(reviewed = true))
        assertEquals(1, repository.confirmImport(c.id, "2026-02-01"))
        assertTrue(db.dao().draftRows(c.id).isEmpty())
        assertEquals("Estudante Fictício Alfa", db.dao().roster(c.id).single().name)
    }
    @Test fun multiplePagesConfirmAtomicallyAndDuplicateRollsBack() = runBlocking {
        val a = ImportDraft(id = "a", classId = c.id, name = "Estudante Fictício Alfa", number = 1, position = 0, page = 1, reviewed = true)
        val b = a.copy(id = "b", name = "Estudante Fictício Beta", number = 2, position = 1, page = 2)
        db.dao().save(a); db.dao().save(b)
        assertEquals(2, repository.confirmImport(c.id, "2026-02-01"))
        db.dao().save(a.copy(id = "c", name = "Estudante Fictício Gama", number = 3))
        db.dao().save(b.copy(id = "d", number = 4))
        try { repository.confirmImport(c.id, "2026-02-01"); fail("Deve rejeitar duplicidade") } catch (_: IllegalArgumentException) { }
        assertEquals(2, db.dao().roster(c.id).size)
        assertEquals(2, db.dao().draftRows(c.id).size)
    }
    @Test fun importedStudentParticipatesInIndependentLessonsAndGrades() = runBlocking {
        db.dao().save(ImportDraft(classId = c.id, name = "Estudante Fictício Alfa", number = 1, position = 0, page = 1, reviewed = true))
        repository.confirmImport(c.id, "2026-02-01")
        val student = db.dao().roster(c.id).single()
        val lessons = db.dao().day(c.id, "2026-02-04")
        assertEquals(2, lessons.size)
        lessons.forEach { repository.startLesson(it.id) }
        repository.mark(lessons[0].id, student.id, "P")
        repository.copyFirst(lessons[1].id)
        repository.mark(lessons[1].id, student.id, "F")
        val activity = Activity(classId = c.id, name = "Atividade fictícia", date = "2026-02-04", term = 1, maximum = 10.0)
        repository.saveActivity(activity)
        repository.saveGrade(activity.id, student.id, 7.5)
        assertEquals("P", db.dao().attendance(lessons[0].id).single().mark)
        assertEquals("F", db.dao().attendance(lessons[1].id).single().mark)
        assertEquals(7.5, db.dao().activityGrades(activity.id).single().value!!, 0.001)
        val snapshot = repository.snapshot.first { it.grades.isNotEmpty() && it.marks.size == 2 }
        assertEquals(1, snapshot.frequency(student, null, true).absent)
        assertEquals(7.5, snapshot.result(student, 1, 10.0).total, 0.001)
        assertEquals(0.0, snapshot.result(student, 2, 10.0).total, 0.001)
        assertEquals(7.5, snapshot.result(student, 1, 10.0).total, 0.001)
        repository.lessonState(lessons[1].id, "CANCELADA", "Feriado")
        val cancelled = repository.snapshot.first { it.lessons.any { l -> l.id == lessons[1].id && l.state == "CANCELADA" } }
        assertEquals(0, cancelled.frequency(student, null, true).absent)
    }
    @Test fun scheduleChangesPreserveRecordedLessons() = runBlocking {
        val first = db.dao().day(c.id, "2026-02-04").first()
        repository.startLesson(first.id)
        repository.saveClass(c.copy(schedule = "1,0,0,0,0,0,0"), "2026-02-01")
        assertEquals("REALIZADA", db.dao().lesson(first.id)?.state)
        assertEquals(1, db.dao().day(c.id, "2026-02-04").size)
        assertEquals(1, db.dao().day(c.id, "2026-02-09").size)
    }
    @Test fun duplicateNumbersFailWithoutPartialEnrollment() = runBlocking {
        db.dao().save(ImportDraft(classId = c.id, name = "Estudante Fictício Alfa", number = 1, position = 0, page = 1, reviewed = true))
        db.dao().save(ImportDraft(classId = c.id, name = "Estudante Fictício Beta", number = 1, position = 1, page = 1, reviewed = true))
        try { repository.confirmImport(c.id, "2026-02-01"); fail("Deve rejeitar números duplicados") } catch (_: IllegalArgumentException) { }
        assertTrue(db.dao().roster(c.id).isEmpty())
    }
    @Test fun copyingAnUnregisteredStudentClearsOldTargetMarkToNr() = runBlocking {
        val student = Student(classId = c.id, name = "Estudante Fictício Alfa", number = 1, joined = "2026-02-01")
        repository.saveStudent(student)
        val lessons = db.dao().day(c.id, "2026-02-04")
        lessons.forEach { repository.startLesson(it.id) }
        repository.mark(lessons[1].id, student.id, "F")
        repository.copyFirst(lessons[1].id)
        assertEquals("NR", db.dao().attendance(lessons[1].id).single().mark)
    }
    @Test fun lateEnrollmentCannotReceiveHistoricalMark() = runBlocking {
        val s = Student(classId = c.id, name = "Estudante Fictício Tardio", number = 1, joined = "2026-03-01")
        repository.saveStudent(s)
        val lesson = db.dao().day(c.id, "2026-02-04").first()
        repository.startLesson(lesson.id)
        try { repository.mark(lesson.id, s.id, "F"); fail("Matrícula ainda não iniciada") } catch (_: IllegalArgumentException) { }
        assertTrue(db.dao().attendance(lesson.id).isEmpty())
    }
}
