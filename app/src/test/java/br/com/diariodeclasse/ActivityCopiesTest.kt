package br.com.diariodeclasse

import br.com.diariodeclasse.data.*
import org.junit.Test
import org.junit.Assert.*

class ActivityCopiesTest {
    private val c=SchoolClass("a",name="1º K",subject="História",year=2026)
    private val b=c.copy(id="b",name="2º B")
    private val third=c.copy(id="third",name="3º C")
    private val future=c.copy(id="future",name="2º Futuro",year=2027)
    private val activity=Activity("original",c.id,"Produção",type="Produção textual",date="2026-08-10",term=3,maximum=7.5)
    private fun data()=DiarySnapshot(classes=listOf(c,b,third,future,c.copy(id="wrong",subject="Geografia")),terms=listOf(c,b,third,future).map{Term(it.id,3,"${it.year}-07-01","${it.year}-09-30")})
    private fun rejects(block:()->Unit){try{block();fail("Expected validation failure")}catch(_:IllegalArgumentException){}}
    @Test fun currentIsDefaultAndRetainsTheOriginalActivity() {
        assertEquals(listOf(activity),ActivityCopies.plan(data(),c.id,activity,ActivityScope.CURRENT,emptySet()).activities)
        assertEquals(listOf(ActivityScope.CURRENT,ActivityScope.CUSTOM),ActivityCopies.creationScopes)
    }
    @Test fun automaticScopeCannotInferSeriesFromNamesOrStage() {
        val sameNamedSeries=c.copy(id="same-series",name="1º H")
        val fixture=data().copy(classes=data().classes+sameNamedSeries,terms=data().terms+Term(sameNamedSeries.id,3,"2026-07-01","2026-09-30"))
        rejects{ActivityCopies.plan(fixture,c.id,activity,ActivityScope.ALL,emptySet())}
        rejects{ActivityCopies.plan(fixture,c.id,activity,ActivityScope.ALL,setOf(c.id,sameNamedSeries.id))}
        rejects{ActivityCopies.plan(fixture.copy(classes=fixture.classes.map{it.copy(name="Turma sem série identificada")}),c.id,activity,ActivityScope.ALL,emptySet())}
    }
    @Test fun manualCanCombineFirstSecondAndThirdGradesInTheSameSchoolYear() {
        val selected=setOf(c.id,b.id,third.id)
        val plan=ActivityCopies.plan(data(),c.id,activity,ActivityScope.CUSTOM,selected)
        assertEquals(selected,plan.activities.map{it.classId}.toSet());assertEquals(3,plan.activities.map{it.id}.toSet().size)
        assertTrue(plan.activities.all{it.maximum==7.5&&it.type=="Produção textual"&&it.date==activity.date})
        assertEquals(plan,ActivityCopies.plan(data(),c.id,activity,ActivityScope.CUSTOM,selected))
    }
    @Test fun manualOtherSchoolYearIsRejectedEvenWithItsOwnDate() {
        rejects{ActivityCopies.plan(data(),c.id,activity,ActivityScope.CUSTOM,setOf(c.id,future.id))}
        rejects{ActivityCopies.plan(data(),c.id,activity,ActivityScope.CUSTOM,setOf(c.id,future.id),mapOf(future.id to "2027-08-20"))}
        rejects{ActivityCopies.plan(data(),c.id,activity,ActivityScope.CUSTOM,setOf(future.id))}
    }
    @Test fun candidatesExcludeOtherSchoolYearsButIncludeDifferentSchoolGrades() {
        assertEquals(setOf(c.id,b.id,third.id),ActivityCopies.candidates(data(),c).map{it.id}.toSet())
        assertFalse(ActivityCopies.compatible(c,future));assertTrue(ActivityCopies.compatible(c,third))
    }
    @Test fun invalidTargetDoesNotSilentlyShrinkManualSelection() {
        val changed=data().copy(terms=data().terms.map{if(it.classId==b.id)it.copy(start="2026-08-20")else it})
        rejects{ActivityCopies.plan(changed,c.id,activity,ActivityScope.CUSTOM,setOf(c.id,b.id))}
    }
    @Test fun manualRejectsEmptyIncompatibleArchivedAndMissingClass() {
        for(ids in listOf(emptySet(),setOf("wrong"),setOf("missing"))) rejects{ActivityCopies.plan(data(),c.id,activity,ActivityScope.CUSTOM,ids)}
        val archived=data().copy(classArchives=listOf(ClassArchive(b.id)))
        rejects{ActivityCopies.plan(archived,c.id,activity,ActivityScope.CUSTOM,setOf(b.id))}
    }
    @Test fun invalidDatesMaximumAndTermReject() {
        for(a in listOf(activity.copy(date="2027-08-10"),activity.copy(maximum=Double.NaN),activity.copy(term=5),activity.copy(date="2026-10-01"))) rejects{ActivityCopies.plan(data(),c.id,a,ActivityScope.CURRENT,emptySet())}
    }
    @Test fun duplicateComparisonNormalizesNameButNeverMergesClassIds() {
        assertTrue(ActivityCopies.duplicate(activity,activity.copy(id="other",name="PRODUCAO",maximum=10.0)))
        assertFalse(ActivityCopies.duplicate(activity,activity.copy(classId=b.id)))
    }
    private val roster=listOf(Student("z","pz",c.id,"Zélia Fictícia",1,"2026-01-01"),Student("a","pa",c.id,"ÁLVARO Fictício",3,"2026-01-01"),Student("b","pb",c.id,"Beatriz Fictícia 31",2,"2026-01-01"))
    @Test fun searchAccentsCaseAndExactRollNumber() {
        assertEquals(listOf("a"),GradeRoster.select(roster,"alvaro",GradeRosterOrder.NUMBER).map{it.id})
        assertEquals(listOf("z"),GradeRoster.select(roster,"ZELIA",GradeRosterOrder.NUMBER).map{it.id})
        assertEquals(listOf("a"),GradeRoster.select(roster,"3",GradeRosterOrder.NUMBER).map{it.id})
        assertTrue(GradeRoster.select(roster,"31",GradeRosterOrder.NUMBER).isEmpty())
    }
    @Test fun sortingIsDeterministicAndDoesNotMutateInput() {
        val before=roster.toList()
        assertEquals(listOf("z","b","a"),GradeRoster.select(roster,"",GradeRosterOrder.NUMBER).map{it.id})
        assertEquals(listOf("a","b","z"),GradeRoster.select(roster,"",GradeRosterOrder.NAME).map{it.id})
        assertEquals(before,roster)
        assertEquals(listOf("a","b","z"),GradeRoster.select(roster,"FICT",GradeRosterOrder.NAME).map{it.id})
    }
}
