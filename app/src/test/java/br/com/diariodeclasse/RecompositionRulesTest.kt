package br.com.diariodeclasse

import br.com.diariodeclasse.domain.RecompositionRules as Rules
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.reports.*
import org.junit.Assert.*
import org.junit.Test

class RecompositionRulesTest {
    private val c=SchoolClass(id="c",name="Turma Fictícia",subject="História",year=2026)
    private val s=Student(id="s",classId=c.id,name="Estudante Fictício",number=1,joined="2026-01-01")
    @Test fun greaterLowerEqualAndCeiling() {
        assertEquals(5.0,Rules.effective(3.0,5.0)!!,0.0)
        assertEquals(4.5,Rules.effective(4.5,3.0)!!,0.0)
        assertEquals(4.5,Rules.effective(4.5,4.5)!!,0.0)
        assertEquals(10.0,Rules.effective(10.0,10.0)!!,0.0)
    }
    @Test fun zeroUnperformedUnknownAndDefaultFilter() {
        assertEquals(0.0,Rules.effective(0.0,0.0)!!,0.0)
        assertEquals(3.0,Rules.effective(3.0,null)!!,0.0)
        assertNull(Rules.effective(null,null));assertNull(Rules.effective(null,8.0))
        assertTrue(Rules.belowFive(0.0));assertTrue(Rules.belowFive(4.99))
        assertFalse(Rules.belowFive(5.0));assertFalse(Rules.belowFive(null))
        listOf(-1.0,10.01,Double.NaN,Double.POSITIVE_INFINITY).forEach {
            try {Rules.effective(3.0,it);fail()} catch(_:IllegalArgumentException) {}
        }
    }
    @Test fun projectionsUseOriginAndKeepOriginalActivitiesAndFrequency() {
        val a=Activity("a",c.id,"Prova Fictícia",date="2026-08-10",term=3,maximum=10.0)
        val fourth=a.copy(id="fourth",date="2026-11-10",term=4)
        val data=DiarySnapshot(classes=listOf(c),students=listOf(s),activities=listOf(a,fourth),grades=listOf(Grade(a.id,s.id,3.0),Grade(fourth.id,s.id,4.5)))
        val r=Recomposition(s.id,3,5.0,"2026-11-20")
        fun report(d:DiarySnapshot)=SchoolReports(d,c)
        val changed=data.copy(recompositions=listOf(r))
        assertEquals(3.0,report(changed).student(s,3).original!!,0.0)
        assertEquals(5.0,report(changed).student(s,3).total!!,0.0)
        assertEquals(4.5,report(changed).student(s,4).total!!,0.0)
        assertEquals(listOf(null,null,5.0,4.5),report(changed).year(s).terms.map {it.total})
        assertEquals(3.0,report(changed.copy(recompositions=listOf(r.copy(value=2.0)))).student(s,3).total!!,0.0)
        assertEquals(3.0,report(changed.copy(recompositions=emptyList())).student(s,3).total!!,0.0)
        assertEquals(data.grades,changed.grades)
        assertEquals(data.frequency(s,null,true).absent,changed.frequency(s,null,true).absent)
        assertNull(report(data.copy(grades=emptyList(),recompositions=listOf(r))).student(s,3).total)
    }
    @Test fun ordinaryActivitiesRetainTheirScaleAndAreNotLimitedToSevenPointFive() {
        assertEquals(9.5,Rules.effective(9.5,null)!!,0.0)
        assertEquals(20.0,Rules.effective(20.0,null)!!,0.0)
        assertEquals(10.0,Rules.effective(20.0,8.0)!!,0.0)
    }
}
