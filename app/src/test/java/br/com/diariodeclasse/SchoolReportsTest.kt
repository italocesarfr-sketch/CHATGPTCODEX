package br.com.diariodeclasse

import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.reports.*
import org.junit.Assert.*
import org.junit.Test

class SchoolReportsTest {
    private val c = SchoolClass(id="report-class", name="Turma Fictícia", subject="História", year=2026)
    private val a = Student(id="a", personId="pa", classId=c.id, name="Estudante Fictício Alfa", number=2, joined="2026-01-01")
    private val b = a.copy(id="b", personId="pb", name="Estudante Fictício Beta", number=1, joined="2026-07-01")
    private val t = a.copy(id="t", personId="pt", number=3, left="2026-06-30", status="Transferido")
    private val periods = (1..4).map { val start=java.time.LocalDate.of(2026,(it-1)*3+1,1); Term(c.id,it,start.toString(),start.plusMonths(3).minusDays(1).toString()) }
    private val activities = listOf(Activity("q1",c.id,"Prova fictícia",date="2026-02-10",term=1,maximum=10.0),
        Activity("q2",c.id,"Trabalho fictício",date="2026-05-10",term=2,maximum=10.0),
        Activity("q3",c.id,"Seminário fictício",date="2026-08-10",term=3,maximum=2.0))
    private val data = DiarySnapshot(classes=listOf(c),students=listOf(t,a,b),terms=periods,
        activities=activities,grades=listOf(Grade("q1",a.id,0.0),Grade("q1",t.id,8.0),Grade("q2",t.id,0.0),Grade("q3",a.id,1.5)),
        lessons=listOf(Lesson("l1",c.id,"2026-02-10",1,"REALIZADA"),Lesson("l2",c.id,"2026-02-10",2,"REALIZADA"),
            Lesson("cancel",c.id,"2026-02-11",1,"CANCELADA"),Lesson("planned",c.id,"2026-02-12",1,"PREVISTA"),Lesson("l3",c.id,"2026-08-10",1,"REALIZADA")),
        marks=listOf(Attendance("l1",a.id,"P"),Attendance("l2",a.id,"F"),Attendance("l1",t.id,"FJ"),Attendance("l2",t.id,"P"),
            Attendance("cancel",a.id,"F"),Attendance("planned",a.id,"F"),Attendance("l1",b.id,"F"),Attendance("l3",t.id,"F"),Attendance("l3",b.id,"F"),Attendance("l3",a.id,"NR")),loaded=true)
    @Test fun zeroPendingPartialAndFourTermsAreDistinct() {
        val rows=SchoolReports(data,c).year(a).terms
        assertEquals(listOf(1,2,3,4),rows.map { it.number })
        assertEquals(0.0,rows[0].total!!,0.0); assertEquals(GradeState.COMPLETE,rows[0].state)
        assertNull(rows[1].total);assertEquals(1,rows[1].grades.pending);assertEquals(GradeState.NO_SCORES,rows[1].state)
        assertEquals(1.5,rows[2].total!!,0.0);assertEquals(GradeState.PARTIAL,rows[2].state)
        assertNull(rows[3].total);assertEquals(GradeState.NO_ACTIVITIES,rows[3].state)
    }
    @Test fun frequencyRespectsDoubleLessonsCancellationNrAndEnrollment() {
        val reports=SchoolReports(data,c)
        val f=reports.student(a,1).frequency!!
        assertEquals(2,f.performed);assertEquals(1,f.present);assertEquals(1,f.absent);assertEquals(50.0,f.percent!!,0.0)
        assertEquals(0,reports.student(b,1).frequency!!.performed)
        assertEquals(GradeState.OUTSIDE_ENROLLMENT,reports.student(b,1).state)
        assertEquals(0,reports.student(t,3).frequency!!.performed)
        assertEquals(GradeState.OUTSIDE_ENROLLMENT,reports.student(t,3).state)
        assertEquals(0,reports.student(a,3).frequency!!.valid);assertNull(reports.student(a,3).frequency!!.percent)
    }
    @Test fun justifiedPolicyAndRankingUseEffectiveAbsencesWithStableTies() {
        val reports=SchoolReports(data,c)
        assertEquals(listOf("a","t"),reports.mostAbsent(1).map { it.student.id })
        assertEquals(1,reports.student(t,1).frequency!!.justified)
        assertEquals(50.0,reports.student(t,1).frequency!!.percent!!,0.0)
        val aboned=SchoolReports(data,c.copy(countJustified=false))
        assertEquals(100.0,aboned.student(t,1).frequency!!.percent!!,0.0)
        assertEquals(listOf("a"),aboned.mostAbsent(1).map { it.student.id })
        val row=reports.student(a,1)
        val tied=listOf(row.copy(student=a.copy(id="z")),row.copy(student=a.copy(id="x")))
        assertEquals(listOf("x","z"),SchoolReports.rank(tied.reversed(),true).map { it.student.id })
    }
    @Test fun classFrequencyUsesWeightedCountsNotAveragePercentages() {
        val extra=Lesson("extra",c.id,"2026-02-13",1,"REALIZADA")
        val changed=data.copy(lessons=data.lessons+extra,marks=data.marks+Attendance(extra.id,a.id,"P"))
        val report=SchoolReports(changed,c).term(1)
        assertEquals(3,report.performedLessons)
        assertEquals(6,report.frequency!!.performed)
        assertEquals(5,report.frequency.valid)
        assertEquals(60.0,report.frequency.percent!!,0.0)
    }
    @Test fun missingTermNeverFallsBackToWholeYear() {
        val reports=SchoolReports(data.copy(terms=emptyList()),c)
        assertNull(reports.student(a,1).frequency);assertNull(reports.term(1).frequency)
        assertEquals(3,reports.year(a).frequency.performed)
    }
    @Test fun annualFrequencyIncludesDaysOutsideBimestersAndNoAnnualGradeIsInvented() {
        val changed=data.copy(terms=periods.map { if(it.number==3) it.copy(start="2026-07-10") else it },
            lessons=data.lessons+Lesson("outside",c.id,"2026-07-05",1,"REALIZADA"),marks=data.marks+Attendance("outside",a.id,"P"))
        val report=SchoolReports(changed,c)
        assertEquals(4,report.year(a).frequency.performed)
        assertEquals(3,report.year(a).terms.sumOf { it.frequency!!.performed })
        assertEquals(4,report.year(a).terms.size)
    }
    @Test fun calculationsAreSharedAndReadOnlyAndOtherClassesAreIsolated() {
        val other=c.copy(id="other")
        val source=data.copy(classes=listOf(c,other),activities=data.activities+activities[0].copy(id="other-activity",classId=other.id))
        val report=SchoolReports(source,c)
        for(n in 1..4) for(s in source.students) {
            val expected=source.result(s,n,c.scale); val actual=report.student(s,n)
            assertEquals(expected.total,actual.grades.total,0.0);assertEquals(expected.pending,actual.grades.pending);assertEquals(expected.partial,actual.grades.partial)
            assertEquals(source.frequency(s,periods[n-1],c.countJustified).absent,actual.frequency!!.absent)
        }
        report.annual();report.mostAbsent(1)
        assertEquals(activities,source.activities.filter {it.classId==c.id});assertEquals(data.marks,source.marks)
    }
}
