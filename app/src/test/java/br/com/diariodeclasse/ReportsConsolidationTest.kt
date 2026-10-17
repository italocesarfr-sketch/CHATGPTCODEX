package br.com.diariodeclasse

import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.reports.*
import org.junit.Assert.*
import org.junit.Test

class ReportsConsolidationTest {
    private val c=SchoolClass(id="c",name="Turma Fictícia",subject="História",year=2026)
    private val s=Student(id="s",personId="p",classId=c.id,name="Estudante Fictício Alfa",number=1,joined="2026-01-01")
    private val late=s.copy(id="late",number=2,joined="2026-08-15",name="Estudante Fictício Tardio")
    private val moved=s.copy(id="moved",number=3,left="2026-05-31",status="Remanejado",name="Estudante Fictício Remanejado")
    private val periods=(1..4).map { n -> val start=java.time.LocalDate.of(2026,(n-1)*3+1,1);Term(c.id,n,start.toString(),start.plusMonths(3).minusDays(1).toString()) }
    private val activities=(1..3).map { n -> Activity("a$n",c.id,"Prova Fictícia $n",date="2026-${listOf("02","05","08")[n-1]}-10",term=n,maximum=if(n==2)5.0 else 10.0) }
    private val data=DiarySnapshot(classes=listOf(c),students=listOf(s,late,moved),terms=periods,activities=activities,
        grades=listOf(Grade("a1",s.id,0.0),Grade("a2",s.id,3.0),Grade("a1",moved.id,4.0)),
        lessons=listOf(Lesson("l1",c.id,"2026-02-10",1,"REALIZADA"),Lesson("l2",c.id,"2026-02-10",2,"REALIZADA"),
            Lesson("cancel",c.id,"2026-02-11",1,"CANCELADA"),Lesson("nr",c.id,"2026-08-20",1,"REALIZADA")),
        marks=listOf(Attendance("l1",s.id,"P"),Attendance("l2",s.id,"FJ"),Attendance("cancel",s.id,"F"),
            Attendance("l1",moved.id,"F"),Attendance("l2",moved.id,"F"),Attendance("nr",late.id,"NR")))
    @Test fun fourBimestersKeepCompletePartialPendingAndNoData() {
        val rows=SchoolReports(data,c).year(s).terms
        assertEquals(listOf(GradeState.COMPLETE,GradeState.PARTIAL,GradeState.NO_SCORES,GradeState.NO_ACTIVITIES),rows.map {it.state})
        assertEquals(listOf(0.0,3.0,null,null),rows.map {it.original})
        assertEquals(listOf(true,true,true,false),rows.map {it.hasData})
        assertEquals(1,rows[2].grades.pending)
    }
    @Test fun membershipAndActivityStatesDistinguishZeroPendingAndNotApplicable() {
        val report=SchoolReports(data,c)
        assertEquals(ActivityScoreState.RECORDED,report.student(s,1).activities.single().state)
        assertEquals(0.0,report.student(s,1).activities.single().applicableScore!!,0.0)
        assertEquals(ActivityScoreState.PENDING,report.student(s,3).activities.single().state)
        val lateRow=report.student(late,3)
        assertEquals(EnrollmentCoverage.PARTIAL,lateRow.enrollment)
        assertEquals(ActivityScoreState.NOT_APPLICABLE,lateRow.activities.single().state)
        assertNull(lateRow.activities.single().applicableScore)
        assertEquals(EnrollmentCoverage.OUTSIDE,report.student(late,1).enrollment)
        assertEquals(EnrollmentCoverage.PARTIAL,report.student(moved,2).enrollment)
        assertEquals("Remanejado",report.student(moved,2).student.status)
    }
    @Test fun recompositionVariantsAndActivityCorrectionsAlwaysUseOriginal() {
        val original=data.copy(grades=listOf(Grade("a1",s.id,4.0)))
        for(score in listOf(null,0.0,3.0,4.0,6.0)) {
            val r=Recomposition(s.id,1,score,"2026-11-20")
            val changed=original.copy(recompositions=listOf(r))
            val row=SchoolReports(changed,c).student(s,1)
            assertEquals(4.0,row.original!!,0.0)
            assertEquals(if(score==null)4.0 else maxOf(4.0,score),row.total!!,0.0)
            assertEquals(8.0,SchoolReports(changed.copy(grades=listOf(Grade("a1",s.id,8.0))),c).student(s,1).total!!,0.0)
            val absent=SchoolReports(changed.copy(activities=emptyList()),c).student(s,1)
            assertNull(absent.total);assertEquals(r,absent.recomposition)
            assertNull(SchoolReports(changed,c).student(s,3).total)
        }
    }
    @Test fun yearlyFrequencyUsesAllEligibleLessonsAndConfiguredJustificationPolicy() {
        val changed=data.copy(terms=periods.map {if(it.number==3)it.copy(start="2026-07-10") else it},
            lessons=data.lessons+Lesson("outside",c.id,"2026-07-05",1,"REALIZADA"),marks=data.marks+Attendance("outside",s.id,"P"))
        for(policy in listOf(true,false)) {
            val reports=SchoolReports(changed,c.copy(countJustified=policy));val annual=reports.year(s)
            assertEquals(4,annual.attendance.performed);assertEquals(3,annual.attendance.valid)
            assertEquals(1,annual.attendance.unregistered)
            assertEquals(if(policy)1 else 0,annual.attendance.effectiveAbsences)
            assertEquals(if(policy)200.0/3 else 100.0,annual.attendance.percent!!,1e-9)
            assertEquals(3,annual.terms.sumOf {it.attendance!!.performed})
            val classroom=reports.year()
            assertEquals(4,classroom.performedLessons)
            assertEquals(classroom.students.sumOf {it.attendance.effectiveAbsences},classroom.frequency.effectiveAbsences)
            assertEquals(classroom.students.sumOf {it.attendance.valid},classroom.frequency.valid)
        }
    }
    @Test fun rankingAndGradeOrderingRemainStableAndNullIsNotZero() {
        val report=SchoolReports(data,c).term(1)
        assertEquals(listOf(moved.id,s.id),SchoolReports.rank(report.students,true).map {it.student.id})
        assertEquals(listOf(moved.id),SchoolReports.rank(report.students,false).map {it.student.id})
        assertEquals(listOf(s.id,moved.id,late.id),SchoolReports.ordered(report.students,StudentOrder.LOWEST_GRADE,true).map {it.student.id})
        assertEquals(listOf(moved.id,s.id,late.id),SchoolReports.ordered(report.students,StudentOrder.HIGHEST_GRADE,true).map {it.student.id})
        val tied=report.students.first().let {listOf(it.copy(student=s.copy(id="z")),it.copy(student=s.copy(id="a")))}
        assertEquals(listOf("a","z"),SchoolReports.ordered(tied.reversed(),StudentOrder.ABSENCES,true).map {it.student.id})
        assertEquals(listOf(s,late,moved),data.students)
    }
    @Test fun differentClassAndYearCannotContributeToCurrentProjection() {
        val other=c.copy(id="other",year=2025)
        val otherStudent=s.copy(id="other-s",classId=other.id)
        val changed=data.copy(classes=listOf(c,other),students=data.students+otherStudent,
            activities=data.activities+activities[0].copy(id="other-a",classId=other.id,date="2025-02-10"),
            grades=data.grades+Grade("other-a",otherStudent.id,10.0),recompositions=listOf(Recomposition(otherStudent.id,1,10.0,"2025-11-20")))
        val report=SchoolReports(changed,c)
        assertEquals(0.0,report.student(s,1).total!!,0.0);assertEquals(3,report.year().students.size)
        assertEquals(c,report.year().school)
        assertNull(SchoolReports(changed.copy(terms=emptyList()),c).student(s,1).attendance)
    }
}
