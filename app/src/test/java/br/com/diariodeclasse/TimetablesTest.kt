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
        assertEquals(2,Timetables.preview(data(),2026,"2026-01-01",listOf(slot(),slot(school=h.copy(shift="Noturno")))) .proposed.slots.size)
        assertTrue(Timetables.shiftOrder("Matutino")<Timetables.shiftOrder("Vespertino"))
        assertTrue(Timetables.shiftOrder("Vespertino")<Timetables.shiftOrder("Noturno"))
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
    @Test fun firstRegistrationInOctoberOrDecemberCoversTheEntireSchoolYear() {
        for(now in listOf("2026-10-23","2026-12-20")) {
            val start=Timetables.effective(2026,TimetableScope.YEAR,java.time.LocalDate.parse(now))
            assertEquals("2026-01-01",start)
            val p=Timetables.annualPreview(data(),2026,listOf(slot()))
            val after=data().copy(timetables=p.additions)
            for(month in 1..12) {
                val date=java.time.LocalDate.of(2026,month,1).with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.THURSDAY))
                assertEquals(1,Timetables.planned(after,c.id,date.toString()).size)
            }
            assertNull(Timetables.current(after.timetables,"2027-01-07"))
        }
    }
    @Test fun annualChangeOverridesEveryFutureBoundaryWithoutChangingAnyHistoricalRevision() {
        val old=Timetables.preview(data(),2026,"2026-10-01",listOf(slot())).proposed
        var d=data().copy(timetables=listOf(old))
        val future=Timetables.preview(d,2026,"2026-11-01",listOf(slot(day=5))).proposed
        d=d.copy(timetables=d.timetables+future)
        val later=Timetables.preview(d,2026,"2026-12-01",listOf(slot(day=6))).proposed
        d=d.copy(timetables=d.timetables+later)
        val p=Timetables.annualPreview(d,2026,listOf(slot(day=3)))
        val after=d.copy(timetables=d.timetables+p.additions)
        assertEquals(listOf("2026-01-01","2026-10-01","2026-11-01","2026-12-01"),p.additions.map{it.effective})
        assertTrue(after.timetables.containsAll(d.timetables));assertEquals(7,after.timetables.size)
        for(day in listOf("2026-01-07","2026-08-05","2026-10-28","2026-11-04","2026-12-30"))
            assertEquals(listOf(1),Timetables.planned(after,c.id,day).map{it.ordinal})
        assertTrue(Timetables.planned(after,c.id,"2026-12-04").isEmpty())
        val again=Timetables.annualPreview(after,2026,listOf(slot(day=2)))
        assertEquals(p.additions.map{it.revision+1},again.additions.map{it.revision})
        val final=after.copy(timetables=after.timetables+again.additions)
        assertEquals(1,Timetables.planned(final,c.id,"2026-12-29").size)
        val restored=BackupCodec.decode(BackupCodec.encode(ClassLifecycle.snapshot(final),30,null)).snapshot.diarySnapshot()
        assertEquals(final.timetables.sortedBy{it.id},restored.timetables.sortedBy{it.id})
        assertEquals(1,Timetables.planned(restored,c.id,"2026-08-04").size)
    }
    @Test fun todayScopeKeepsEarlierPredictionsAndRejectsADifferentSchoolYear() {
        val day=java.time.LocalDate.parse("2026-11-10")
        assertEquals("2026-11-10",Timetables.effective(2026,TimetableScope.TODAY,day))
        fail{Timetables.effective(2025,TimetableScope.TODAY,day)}
        fail{Timetables.effective(2027,TimetableScope.TODAY,day)}
        val annual=Timetables.annualPreview(data(),2026,listOf(slot()))
        val d=data().copy(timetables=annual.additions)
        val q=Timetables.preview(d,2026,Timetables.effective(2026,TimetableScope.TODAY,day),listOf(slot(day=3)))
        val after=d.copy(timetables=d.timetables+q.additions)
        assertEquals(1,Timetables.planned(after,c.id,"2026-11-05").size)
        assertEquals(0,Timetables.planned(after,c.id,"2026-11-12").size)
        assertEquals(1,Timetables.planned(after,c.id,"2026-11-11").size)
    }
    @Test fun annualPreviewFindsAffectedActualDatesAcrossAllFutureBoundariesAndConflicts() {
        val old=Timetables.preview(data(),2026,"2026-10-01",listOf(slot())).proposed
        val future=Timetables.preview(data().copy(timetables=listOf(old)),2026,"2026-11-01",listOf(slot(day=5))).proposed
        val d=data().copy(timetables=listOf(old,future),lessons=listOf(Lesson("actual",c.id,"2026-08-05",1,"REALIZADA")),lessonRecords=listOf(LessonRecord("journal",c.id,"2026-12-02","Fictício","Preservado")))
        val p=Timetables.annualPreview(d,2026,listOf(slot(day=3)))
        assertEquals(2,p.affected.size);assertEquals("2026-12-31",p.through)
        fail{Timetables.annualPreview(d,2026,listOf(slot(),slot(school=h)))}
        val otherYear=h.copy(year=2027)
        fail{Timetables.annualPreview(data().copy(classes=listOf(otherYear)),2026,listOf(slot(school=otherYear)))}
    }

    @Test fun sixNewPeriodsPerShiftCoexistButSameShiftConflictsAndNewSeventhIsRejected() {
        val morning=c.copy(shift="Matutino");val afternoon=h.copy(shift="Vespertino");val night=h.copy(id="night",shift="Noturno")
        val d=data().copy(classes=listOf(morning,afternoon,night))
        val slots=listOf(morning,afternoon,night).flatMap{school->(1..6).map{slot(n=it,school=school)}}
        assertEquals(18,Timetables.annualPreview(d,2026,slots).proposed.slots.size)
        fail{Timetables.annualPreview(d,2026,listOf(slot(n=2),slot(n=2,school=h)))}
        fail{Timetables.annualPreview(d,2026,listOf(slot(n=7)))}
        fail{Timetables.preview(d,2026,"2026-10-01",listOf(slot(n=7)))}
    }
    @Test fun legacyTwelvePeriodsRemainReadableRestorableAndCanBeRetainedButNotDuplicated() {
        val legacy=WeeklyTimetable(id="legacy",year=2026,effective="2026-01-01",revision=1,slots=(1..12).map{slot(n=it)})
        val d=data().copy(timetables=listOf(legacy))
        Timetables.validate(d.timetables)
        val p=Timetables.annualPreview(d,2026,legacy.slots)
        assertEquals(12,p.proposed.slots.size)
        fail{Timetables.annualPreview(d,2026,legacy.slots.filter{it.ordinal<=6}+slot(n=7).copy(id="new-seven"))}
        val kept=d.copy(timetables=d.timetables+p.additions)
        val b=BackupCodec.decode(BackupCodec.encode(ClassLifecycle.snapshot(kept),31,null)).snapshot.diarySnapshot()
        assertEquals((1..12).toList(),Timetables.planned(b,c.id,"2026-08-06").map{it.ordinal})
        val corrected=Timetables.annualPreview(b,2026,legacy.slots.filter{it.ordinal<=6})
        assertEquals(6,corrected.proposed.slots.size);assertTrue((b.timetables+corrected.additions).contains(legacy))
    }

    @Test fun regularWeekdaySelectionHasFiveDaysAndWeekendRestorationUsesMonday() {
        assertEquals((1..5).toList(),br.com.diariodeclasse.ui.regularTimetableDays.toList())
        for(day in 1..5)assertEquals(day,br.com.diariodeclasse.ui.regularTimetableDay(day))
        for(day in listOf(6,7,0,8))assertEquals(1,br.com.diariodeclasse.ui.regularTimetableDay(day))
    }

}
