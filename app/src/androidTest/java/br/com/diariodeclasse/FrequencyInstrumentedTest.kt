package br.com.diariodeclasse

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.ui.defaultEnrollment
import br.com.diariodeclasse.domain.CsvRoster
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FrequencyInstrumentedTest {
    private lateinit var db:DiaryDatabase
    private lateinit var repo:DiaryRepository
    private lateinit var context:Context
    private val c=SchoolClass(id="frequency-test",name="Turma Fictícia Frequência",subject="História",year=2026,schedule="0,0,2,1,0,0,0")
    private val s=Student(id="frequency-student",classId=c.id,name="Estudante Fictício Alfa",number=1,joined="2026-02-01")
    @Before fun setup()=runBlocking {
        context=ApplicationProvider.getApplicationContext();context.deleteDatabase("frequency-test.db")
        db=Room.databaseBuilder(context,DiaryDatabase::class.java,"frequency-test.db").build();repo=DiaryRepository(db)
        repo.saveClass(c,"2026-01-01");repo.saveStudent(s)
    }
    @After fun cleanup(){db.close();context.deleteDatabase("frequency-test.db")}
    private suspend fun snapshot()=BackupManager(db,FileSafetyBackupStore(File(context.cacheDir,"frequency-safety")),4,null).snapshot()
    @Test fun noWeeklyGenerationAndNavigatingQueriesDoNotChangeRows()=runBlocking {
        val before=snapshot()
        assertTrue(before.lessons.isEmpty())
        repo.dao.day(c.id,"2026-08-06");repo.dao.periods(c.id)
        repo.saveTerms(c.id,repo.dao.periods(c.id));repo.saveClass(c.copy(schedule="1,1,1,1,1,0,0"),"2026-01-01")
        assertEquals(before.copy(classes=listOf(c.copy(schedule="1,1,1,1,1,0,0"))),snapshot())
    }
    @Test fun retroactiveMultipleHoursIdempotentAndIndependent()=runBlocking {
        repo.setDayHours(c.id,"2026-08-06",3)
        val first=repo.dao.day(c.id,"2026-08-06")
        repo.setDayHours(c.id,"2026-08-06",3)
        assertEquals(first,repo.dao.day(c.id,"2026-08-06"))
        repo.mark(first[0].id,s.id,"P");repo.mark(first[1].id,s.id,"F")
        val data=repo.snapshot.first { it.marks.size==2 }
        val period=data.terms.first { it.classId==c.id && it.number==3 }
        val f=data.frequency(s,period,true)
        assertEquals(1,f.absent);assertEquals(1,f.present);assertEquals(2,f.valid);assertEquals(3,f.performed);assertEquals(50.0,f.percent!!,0.0)
        assertEquals(0,data.frequency(s,data.terms.first { it.number==1 },true).absent)
    }
    @Test fun reductionRequiresExplicitCancellationPreservesMarksAndReactivation()=runBlocking {
        repo.setDayHours(c.id,"2026-03-10",2);val lessons=repo.dao.day(c.id,"2026-03-10")
        lessons.forEach { repo.mark(it.id,s.id,"F") }
        val before=snapshot()
        try {repo.setDayHours(c.id,"2026-03-10",1);fail()}catch(_:IllegalArgumentException){}
        assertEquals(before,snapshot())
        repo.setDayHours(c.id,"2026-03-10",1,true)
        assertEquals("CANCELADA",repo.dao.lesson(lessons[1].id)!!.state)
        assertEquals(before.marks,snapshot().marks)
        assertEquals(1,repo.snapshot.first {it.lessons.any {l->l.state=="CANCELADA"}}.frequency(s,null,true).absent)
        repo.setDayHours(c.id,"2026-03-10",2)
        assertEquals("CANCELADA",repo.dao.lesson(lessons[1].id)!!.state)
        repo.lessonState(lessons[1].id,"REALIZADA")
        assertEquals(2,repo.snapshot.first {it.lessons.all {l->l.state=="REALIZADA"}}.frequency(s,null,true).absent)
        repo.setDayHours(c.id,"2026-03-10",0,true)
        assertEquals(2,repo.dao.day(c.id,"2026-03-10").size)
    }
    @Test fun legacyIdsFjAndNotesSurviveQuantityAndBackup()=runBlocking {
        val old=Lesson("arbitrary-legacy-id",c.id,"2026-03-10",1,"REALIZADA","Observação fictícia")
        repo.dao.save(old);repo.dao.save(Attendance(old.id,s.id,"FJ"))
        repo.setDayHours(c.id,old.date,2)
        assertEquals(old,repo.dao.lesson(old.id));assertEquals("FJ",repo.dao.attendance(old.id).single().mark)
        val manager=BackupManager(db,FileSafetyBackupStore(File(context.cacheDir,"frequency-safety")),4,null)
        val before=manager.snapshot();val bytes=manager.backupBytes()
        repo.mark(old.id,s.id,"F");manager.restore(manager.prepare(bytes.inputStream()),"RESTAURAR")
        assertEquals(before,manager.snapshot())
    }
    @Test fun enrollmentBoundariesFjNrAndCopyAreConsistent()=runBlocking {
        repo.setDayHours(c.id,"2026-01-20",1)
        val historical=repo.dao.day(c.id,"2026-01-20").single()
        try{repo.mark(historical.id,s.id,"F");fail()}catch(_:IllegalArgumentException){}
        repo.setDayHours(c.id,"2026-02-01",2)
        val lessons=repo.dao.day(c.id,"2026-02-01")
        repo.mark(lessons[0].id,s.id,"FJ");repo.mark(lessons[1].id,s.id,"F")
        var data=repo.snapshot.first{it.marks.size==2}
        assertEquals(1,data.frequency(s,null,true).absent);assertEquals(1,data.frequency(s,null,true).justified)
        assertEquals(50.0,data.frequency(s,null,false).percent!!,0.0)
        repo.mark(lessons[0].id,s.id,"NR");repo.copyFirst(lessons[1].id)
        data=repo.snapshot.first {it.marks.all {m->m.mark=="NR"}}
        assertEquals(0,data.frequency(s,null,true).absent);assertNull(data.frequency(s,null,true).percent)
    }
    @Test fun changingTermsReclassifiesWithoutChangingAnyLessonOrMark()=runBlocking {
        repo.setDayHours(c.id,"2026-03-10",2)
        repo.dao.day(c.id,"2026-03-10").forEach {repo.mark(it.id,s.id,"F")}
        val before=snapshot();val periods=repo.dao.periods(c.id)
        repo.saveTerms(c.id,periods.map { when(it.number){1->it.copy(end="2026-02-28");2->it.copy(start="2026-03-01");else->it} })
        assertEquals(before.lessons,snapshot().lessons);assertEquals(before.marks,snapshot().marks)
        val data=repo.snapshot.first{it.terms.any {t->t.number==2&&t.start=="2026-03-01"}}
        assertEquals(0,data.frequency(s,data.terms.first {it.number==1},true).absent)
        assertEquals(2,data.frequency(s,data.terms.first {it.number==2},true).absent)
    }
    @Test fun yearEdgesLeapYearAndOutsideTermsAreValid()=runBlocking {
        repo.setDayHours(c.id,"2026-01-01",1);repo.setDayHours(c.id,"2026-12-31",12)
        val leap=c.copy(id="leap",year=2028);repo.saveClass(leap,"2028-01-01");repo.setDayHours(leap.id,"2028-02-29",2)
        assertEquals(2,repo.dao.day(leap.id,"2028-02-29").size)
        try {repo.setDayHours(c.id,"2025-12-31",1);fail()}catch(_:IllegalArgumentException){}
        try {repo.setDayHours(c.id,"2026-03-10",13);fail()}catch(_:IllegalArgumentException){}
        val periods=repo.dao.periods(c.id);repo.saveTerms(c.id,periods.map {if(it.number==1)it.copy(start="2026-02-15")else it})
        repo.setDayHours(c.id,"2026-02-01",1);repo.mark(repo.dao.day(c.id,"2026-02-01").single().id,s.id,"F")
        val data=repo.snapshot.first {it.marks.isNotEmpty()}
        assertEquals(1,data.frequency(s,null,true).absent)
        assertEquals(0,data.frequency(s,data.terms.first {it.number==1},true).absent)
    }
    @Test fun reopenDatabaseRetainsLastCommittedCell()=runBlocking {
        repo.setDayHours(c.id,"2026-08-06",1);val id=repo.dao.day(c.id,"2026-08-06").single().id
        repo.mark(id,s.id,"F");db.close()
        db=Room.databaseBuilder(context,DiaryDatabase::class.java,"frequency-test.db").build();repo=DiaryRepository(db)
        assertEquals("F",repo.dao.attendance(id).single().mark)
    }
    @Test fun octoberRegistrationAllowsAugustWhileExplicitEnrollmentDatesStayIntact()=runBlocking {
        repo.saveClass(c,"2026-10-16")
        val regular=s.copy(id="october-regular",number=2,name="Estudante Fictício Regular",joined=defaultEnrollment(c.year))
        val late=s.copy(id="october-late",number=3,name="Estudante Fictício Ingressante",joined="2026-09-01",left="2026-10-01")
        val departed=s.copy(id="october-departed",number=4,name="Estudante Fictício Transferido",joined="2026-01-01",left="2026-07-31",status="Transferido")
        listOf(regular,late,departed).forEach {repo.saveStudent(it)}
        repo.appendDrafts(c.id,CsvRoster.parse("numero;nome\n5;Estudante Fictício Importado"))
        repo.dao.draftRows(c.id).forEach {repo.dao.save(it.copy(reviewed=true))}
        repo.confirmImport(c.id,defaultEnrollment(c.year))
        val imported=repo.dao.roster(c.id).single {it.number==5}
        assertEquals("2026-01-01",regular.joined);assertEquals(regular.joined,imported.joined)
        assertTrue(snapshot().lessons.isEmpty());assertTrue(snapshot().marks.isEmpty())
        repo.setDayHours(c.id,"2026-08-06",2);repo.allPresentDay(c.id,"2026-08-06")
        val lessons=repo.dao.day(c.id,"2026-08-06")
        assertEquals(6,snapshot().marks.size) // três matrículas elegíveis, dois horários
        assertTrue(snapshot().marks.none {it.studentId in listOf(late.id,departed.id)})
        for(student in listOf(late,departed)) {
            try {repo.mark(lessons[0].id,student.id,"F");fail()}catch(_:IllegalArgumentException){}
        }
        repo.mark(lessons[1].id,regular.id,"F")
        val data=repo.snapshot.first {it.marks.any {m->m.studentId==regular.id&&m.mark=="F"}}
        val term=data.terms.single {it.classId==c.id&&it.number==3}
        assertEquals(1,data.frequency(regular,term,true).absent)
        assertEquals(1,data.frequency(regular,term,true).present)
        assertEquals(0,data.frequency(late,term,true).absent)
        assertTrue(repo.dao.day(c.id,"2026-08-07").isEmpty())
        repo.saveClass(c.copy(name="Turma Fictícia Editada"),"2026-10-16")
        db.close();db=Room.databaseBuilder(context,DiaryDatabase::class.java,"frequency-test.db").build();repo=DiaryRepository(db)
        assertEquals(s,repo.dao.student(s.id));assertEquals(late,repo.dao.student(late.id));assertEquals(departed,repo.dao.student(departed.id))
        assertEquals(regular,repo.dao.student(regular.id));assertEquals("F",repo.dao.attendance(lessons[1].id).single {it.studentId==regular.id}.mark)
    }
}
