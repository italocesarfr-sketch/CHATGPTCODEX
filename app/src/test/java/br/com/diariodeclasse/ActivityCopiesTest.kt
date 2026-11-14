package br.com.diariodeclasse

import br.com.diariodeclasse.data.*
import org.junit.Test
import org.junit.Assert.*

class ActivityCopiesTest {
    private val c=SchoolClass("a",name="1º A",subject="História",year=2026)
    private val b=c.copy(id="b",name="2º B")
    private val future=c.copy(id="future",name="2º Futuro",year=2027)
    private val activity=Activity("original",c.id,"Produção",type="Produção textual",date="2026-08-10",term=3,maximum=7.5)
    private fun data()=DiarySnapshot(classes=listOf(c,b,future,c.copy(id="wrong",subject="Geografia")),terms=listOf(c,b,future).map{Term(it.id,3,"${it.year}-07-01","${it.year}-09-30")})
    private fun rejects(block:()->Unit){try{block();fail("Expected validation failure")}catch(_:IllegalArgumentException){}}
    @Test fun currentIsDefaultAndAllIncludesSameCalendarYearOnly() {
        assertEquals(listOf(activity),ActivityCopies.plan(data(),c.id,activity,ActivityScope.CURRENT,emptySet()).activities)
        val all=ActivityCopies.plan(data(),c.id,activity,ActivityScope.ALL,emptySet())
        assertEquals(setOf(c.id,b.id),all.activities.map{it.classId}.toSet());assertEquals(2,all.activities.map{it.id}.toSet().size)
        assertTrue(all.activities.all{it.maximum==7.5&&it.type=="Produção textual"})
        assertEquals(all,ActivityCopies.plan(data(),c.id,activity,ActivityScope.ALL,emptySet()))
    }
    @Test fun manualOtherYearRequiresExplicitDateAndPreservesIndependentId() {
        rejects{ActivityCopies.plan(data(),c.id,activity,ActivityScope.CUSTOM,setOf(future.id))}
        val result=ActivityCopies.plan(data(),c.id,activity,ActivityScope.CUSTOM,setOf(future.id),mapOf(future.id to "2027-08-20"))
        assertEquals("2027-08-20",result.activities.single().date);assertNotEquals(activity.id,result.activities.single().id)
        rejects{ActivityCopies.plan(data(),c.id,activity,ActivityScope.CUSTOM,setOf(future.id),mapOf(future.id to "2026-08-20"))}
    }
    @Test fun invalidTargetDoesNotSilentlyShrinkAllSelection() {
        val changed=data().copy(terms=data().terms.map{if(it.classId==b.id)it.copy(start="2026-08-20")else it})
        rejects{ActivityCopies.plan(changed,c.id,activity,ActivityScope.ALL,emptySet())}
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
