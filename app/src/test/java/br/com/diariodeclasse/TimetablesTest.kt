package br.com.diariodeclasse

import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.backup.*
import kotlinx.serialization.encodeToString
import org.junit.Test
import org.junit.Assert.*

class TimetablesTest {
    private val c=SchoolClass(id="schedule-c",name="Turma Fictícia A",subject="História",year=2026)
    private val h=c.copy(id="schedule-h",name="Turma Fictícia B")
    private fun slot(day:Int=4,n:Int=1,school:SchoolClass=c)=WeeklySlot(id="${school.id}-$day-$n",classId=school.id,weekday=day,ordinal=n,className=school.name,subject=school.subject,shift=school.shift)
    private fun data()=DiarySnapshot(classes=listOf(c,h))
    private fun fail(block:()->Unit){try{block();fail("Deveria recusar")}catch(_:IllegalArgumentException){}}
    @Test fun oneAndDoublePeriodsSortedWithoutCreatingLessons() {
        val d=data();val p=Timetables.preview(d,2026,"2026-01-01",listOf(slot(n=2),slot()))
        val saved=d.copy(timetables=listOf(p.proposed))
        assertEquals(listOf(1,2),Timetables.planned(saved,c.id,"2026-08-06").map{it.ordinal})
        assertTrue(Timetables.planned(saved,c.id,"2026-08-07").isEmpty());assertEquals(d.lessons,saved.lessons);assertEquals(d.marks,saved.marks)
    }
    @Test fun multipleClassesEveryWeekdayAndConflict() {
        val slots=(1..7).flatMap{listOf(slot(it,1),slot(it,2,h))}
        val p=Timetables.preview(data(),2026,"2026-01-01",slots)
        assertEquals(14,p.proposed.slots.size)
        fail{Timetables.preview(data(),2026,"2026-01-01",listOf(slot(),slot(school=h)))}
    }
    @Test fun futureValidityPreservesEarlierDaysAndYearBoundaries() {
        val p=Timetables.preview(data(),2026,"2026-01-01",listOf(slot(),slot(n=2)))
        val d=data().copy(timetables=listOf(p.proposed))
        val q=Timetables.preview(d,2026,"2026-11-01",listOf(slot(day=3)))
        val after=d.copy(timetables=d.timetables+q.proposed)
        assertEquals(2,Timetables.planned(after,c.id,"2026-10-29").size)
        assertEquals(0,Timetables.planned(after,c.id,"2026-11-05").size)
        assertEquals(1,Timetables.planned(after,c.id,"2026-11-04").size)
        assertNull(Timetables.current(after.timetables,"2027-01-01"))
    }
    @Test fun correctionAddsRevisionAndReportsOnlyActuallyAffectedDates() {
        val p=Timetables.preview(data(),2026,"2026-01-01",listOf(slot()))
        val d=data().copy(timetables=listOf(p.proposed),lessons=listOf(Lesson("l",c.id,"2026-08-06",1,"CANCELADA")),lessonRecords=listOf(LessonRecord("r",c.id,"2026-08-07","Fictício","Conteúdo")))
        val q=Timetables.preview(d,2026,"2026-01-01",listOf(slot(day=5)))
        assertEquals(2,q.proposed.revision);assertEquals(2,q.affected.size)
        val after=d.copy(timetables=d.timetables+q.proposed)
        assertEquals(d.lessons,after.lessons);assertEquals(d.lessonRecords,after.lessonRecords)
        assertEquals(q.proposed,Timetables.current(after.timetables,"2026-08-06"))
    }
    @Test fun removingConfigurationPreservesAllRevisionsAndAcademicData() {
        val p=Timetables.preview(data(),2026,"2026-01-01",listOf(slot()))
        val d=data().copy(timetables=listOf(p.proposed))
        val q=Timetables.preview(d,2026,"2026-09-01",emptyList())
        assertTrue(q.proposed.slots.isEmpty());assertEquals(listOf(p.proposed),d.timetables)
    }
    @Test fun archivedClassRetainsHistoryButCannotGainPeriods() {
        val p=Timetables.preview(data(),2026,"2026-01-01",listOf(slot()))
        val d=data().copy(timetables=listOf(p.proposed),classArchives=listOf(ClassArchive(c.id)))
        assertEquals(1,Timetables.planned(d,c.id,"2026-08-06").size)
        fail{Timetables.preview(d,2026,"2026-09-01",listOf(slot(),slot(n=2)))}
        assertTrue(Timetables.preview(d,2026,"2026-09-01",emptyList()).proposed.slots.isEmpty())
    }
    @Test fun backupElevenRoundTripsAndLegacyTenLoadsWithoutSchedule() {
        val p=Timetables.preview(data(),2026,"2026-01-01",listOf(slot()))
        val b=ClassLifecycle.snapshot(data().copy(timetables=listOf(p.proposed)))
        val decoded=BackupCodec.decode(BackupCodec.encode(b,29,null));assertEquals(11,decoded.envelope.formatVersion);assertEquals(b,decoded.snapshot)
        val payload=BackupCodec.json.encodeToString(b.copy(timetables=emptyList())).replace(",\"timetables\":[]","")
        val old=BackupEnvelope("diario-de-classe-backup",10,"br.com.diariodeclasse",8,BackupCodec.V8_IDENTITY,"2026-10-22T00:00:00Z",28,null,BackupCodec.hash(payload.toByteArray()),payload)
        assertTrue(BackupCodec.decode(BackupCodec.json.encodeToString(old).toByteArray()).snapshot.timetables.isEmpty())
    }
    @Test fun invalidPeriodsDuplicateIdentityAndWrongYearAreRejected() {
        fail{Timetables.preview(data(),2026,"2027-01-01",listOf(slot()))}
        fail{Timetables.preview(data(),2026,"2026-01-01",listOf(slot(n=0)))}
        fail{Timetables.preview(data(),2026,"2026-01-01",listOf(slot(),slot(n=2).copy(id=slot().id)))}
    }
}
