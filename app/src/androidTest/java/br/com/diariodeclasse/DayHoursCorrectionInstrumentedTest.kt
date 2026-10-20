package br.com.diariodeclasse

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

class DayHoursCorrectionInstrumentedTest {
    private lateinit var db: DiaryDatabase
    private lateinit var repo: DiaryRepository
    private lateinit var context: Context
    private val c=SchoolClass("hours-fix",name="Turma Fictícia Horários",subject="História",year=2026)
    private val s=Student("hours-student","hours-person",c.id,"Estudante Fictício",1,"2026-01-01")
    private val day="2026-10-19"
    @Before fun setup()=runBlocking {
        context=ApplicationProvider.getApplicationContext();context.deleteDatabase("hours-fix-test.db")
        reopen();repo.saveClass(c,"2026-01-01");repo.saveStudent(s)
    }
    private fun reopen(){db=Room.databaseBuilder(context,DiaryDatabase::class.java,"hours-fix-test.db").build();repo=DiaryRepository(db)}
    private fun manager()=BackupManager(db,FileSafetyBackupStore(File(context.cacheDir,"hours-fix-safety")),23,null)
    @After fun clean(){db.close();context.deleteDatabase("hours-fix-test.db");File(context.cacheDir,"hours-fix-safety").deleteRecursively()}
    @Test fun zeroThenOneThenTwoReusesIdsAndMarksAfterRestart()=runBlocking {
        repo.setDayHours(c.id,day,2);val original=repo.dao.day(c.id,day)
        repo.mark(original[0].id,s.id,"P");repo.mark(original[1].id,s.id,"FJ")
        repo.setDayHours(c.id,day,0,true);val cancelled=manager().snapshot()
        try {repo.setDayHours(c.id,day,2);fail("Reativação requer confirmação")}catch(_:IllegalArgumentException){}
        assertEquals(cancelled,manager().snapshot())
        repo.setDayHours(c.id,day,1,confirmReactivation=true)
        assertEquals(listOf("REALIZADA","CANCELADA"),repo.dao.day(c.id,day).map{it.state})
        repo.setDayHours(c.id,day,2,confirmReactivation=true)
        assertEquals(original,repo.dao.day(c.id,day));assertEquals(cancelled.marks,manager().snapshot().marks)
        db.close();reopen();assertEquals(original,repo.dao.day(c.id,day));assertEquals(cancelled.marks,manager().snapshot().marks)
    }
    @Test fun mixedCancelledAndNewCreatesOnlyMissingWithoutManufacturingOldPresence()=runBlocking {
        repo.saveClass(c.copy(startPresent=true),"2026-01-01")
        repo.dao.save(Lesson("legacy-arbitrary-id",c.id,day,1,"CANCELADA"))
        repo.setDayHours(c.id,day,3,confirmReactivation=true)
        val rows=repo.dao.day(c.id,day);assertEquals(3,rows.size);assertEquals("legacy-arbitrary-id",rows.first().id)
        assertTrue(repo.dao.attendance(rows.first().id).isEmpty());assertEquals(2,manager().snapshot().marks.size)
        repo.setDayHours(c.id,day,0,true);repo.setDayHours(c.id,day,1,confirmReactivation=true)
        assertEquals(1,repo.dao.day(c.id,day).count{it.state=="REALIZADA"})
    }
    @Test fun clearEmptyDayIsScopedPersistsAndRoundTripsBackup()=runBlocking {
        repo.setDayHours(c.id,day,2);repo.setDayHours(c.id,"2026-10-20",1)
        val other=c.copy(id="hours-other");repo.saveClass(other,"2026-01-01");repo.setDayHours(other.id,day,1)
        repo.setDayHours(c.id,day,0,true)
        val before=manager().snapshot();val backup=manager().backupBytes();val checked=repo.checkEmptyDay(c.id,day)
        repo.clearEmptyDay(c.id,day,checked)
        val expected=before.copy(lessons=before.lessons.filterNot{it.id in checked.map{l->l.id}})
        assertEquals(expected,manager().snapshot());assertTrue(repo.dao.day(c.id,day).isEmpty())
        db.close();reopen();assertEquals(expected,manager().snapshot())
        db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}
        manager().restore(manager().prepare(backup.inputStream()),"RESTAURAR");assertEquals(before,manager().snapshot())
    }
    @Test fun anyMarkIncludingExplicitNrBlocksClearingAndPreservesEverything()=runBlocking {
        repo.setDayHours(c.id,day,2);val lessons=repo.dao.day(c.id,day)
        for(mark in listOf("P","F","FJ","NR")) {
            repo.mark(lessons[1].id,s.id,mark);val before=manager().snapshot()
            try{repo.clearEmptyDay(c.id,day,lessons);fail()}catch(_:IllegalArgumentException){}
            assertEquals(before,manager().snapshot())
        }
    }
    @Test fun notesDiaryActivitiesAndRecompositionsBlockEvenWithoutMarks()=runBlocking {
        repo.setDayHours(c.id,day,1);val lesson=repo.dao.day(c.id,day).single()
        repo.lessonState(lesson.id,"CANCELADA","Observação fictícia")
        try{repo.checkEmptyDay(c.id,day);fail()}catch(_:IllegalArgumentException){}
        repo.lessonState(lesson.id,"CANCELADA","")
        val record=LessonRecord("hours-diary",c.id,day,"Título fictício","Descrição fictícia")
        repo.saveLessonRecord(record);val before=manager().snapshot()
        try{repo.clearEmptyDay(c.id,day,listOf(lesson));fail()}catch(_:IllegalArgumentException){}
        assertEquals(before,manager().snapshot());repo.dao.removeRecord(record.id)
        repo.saveActivity(Activity("hours-activity",c.id,"Atividade fictícia",date=day,term=4,maximum=10.0))
        try{repo.checkEmptyDay(c.id,day);fail()}catch(_:IllegalArgumentException){}
        repo.dao.deleteActivity("hours-activity");repo.saveRecomposition(Recomposition(s.id,3,0.0,day))
        try{repo.checkEmptyDay(c.id,day);fail()}catch(_:IllegalArgumentException){}
    }
    @Test fun staleConfirmationAndFailureDuringDeletionRollBackWholeDay()=runBlocking {
        repo.setDayHours(c.id,day,2);val checked=repo.checkEmptyDay(c.id,day)
        repo.setDayHours(c.id,day,3);val before=manager().snapshot()
        try{repo.clearEmptyDay(c.id,day,checked);fail()}catch(_:IllegalArgumentException){}
        assertEquals(before,manager().snapshot())
        val current=repo.checkEmptyDay(c.id,day)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER hours_fail BEFORE DELETE ON lessons WHEN OLD.ordinal=2 BEGIN SELECT RAISE(ABORT,'fictitious failure'); END")
        try{repo.clearEmptyDay(c.id,day,current);fail()}catch(_:android.database.sqlite.SQLiteException){}
        assertEquals(before,manager().snapshot())
    }
    @Test fun reactivationFailureRollsBackCancellationAndEarlierReactivation()=runBlocking {
        repo.setDayHours(c.id,day,2);repo.setDayHours(c.id,day,0,true)
        val before=manager().snapshot()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER hours_fail BEFORE UPDATE ON lessons WHEN OLD.ordinal=2 BEGIN SELECT RAISE(ABORT,'fictitious failure'); END")
        try{repo.setDayHours(c.id,day,2,confirmReactivation=true);fail()}catch(_:android.database.sqlite.SQLiteException){}
        assertEquals(before,manager().snapshot())
    }
}
