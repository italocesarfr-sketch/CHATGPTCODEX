package br.com.diariodeclasse

import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.reports.*
import br.com.diariodeclasse.export.*
import org.junit.Test
import org.junit.Assert.*

class ReportDisplayTest {
    private val school=SchoolClass(id="display-class",name="Turma Fictícia",subject="História",year=2026)
    private fun data():DiarySnapshot {
        val students=listOf(Student(id="zero",classId=school.id,name="Áurea Fictícia",number=7,joined="2026-01-01"),Student(id="pending",classId=school.id,name="Bruna Fictícia",number=2,joined="2026-01-01"))
        val activity=Activity(id="exam",classId=school.id,name="Atividade Fictícia",date="2026-02-10",term=1,maximum=10.0)
        return DiarySnapshot(classes=listOf(school),students=students,terms=listOf(Term(school.id,1,"2026-01-01","2026-03-31")),activities=listOf(activity),grades=listOf(Grade(activity.id,"zero",0.0)),lessons=listOf(Lesson("lesson",school.id,"2026-02-10",1,"REALIZADA")),marks=listOf(Attendance("lesson","zero","FJ"),Attendance("lesson","pending","F")))
    }
    @Test fun eachViewOffersOnlyItsCompatibleCriteria() {
        assertEquals(listOf("Geral","Notas","Frequência"),ReportDisplay.views)
        assertEquals(StudentOrder.entries.toSet(),ReportDisplay.orders("Geral").toSet())
        assertEquals(listOf(StudentOrder.CALL,StudentOrder.ALPHABETICAL,StudentOrder.HIGHEST_GRADE,StudentOrder.LOWEST_GRADE),ReportDisplay.orders("Notas"))
        assertEquals(listOf(StudentOrder.CALL,StudentOrder.ALPHABETICAL,StudentOrder.ABSENCES,StudentOrder.LEAST_ABSENCES),ReportDisplay.orders("Frequência"))
    }
    @Test fun switchingViewPreservesCompatibleOrderAndOtherwiseUsesRealCallNumber() {
        for(view in ReportDisplay.views) for(order in StudentOrder.entries)
            assertEquals(if(order in ReportDisplay.orders(view))order else StudentOrder.CALL,ReportDisplay.compatible(view,order))
        val rows=SchoolReports(data(),school).term(1).students
        assertEquals(listOf(2,7),SchoolReports.ordered(rows,ReportDisplay.compatible("Frequência",StudentOrder.HIGHEST_GRADE),true).map{it.student.number})
    }
    @Test fun zeroPendingAndAbsencesUseExistingSchoolRulesWithoutChangingExportLayout() {
        val d=data();val before=ExportProjection.build(d,school,ExportOptions(term=1))
        val rows=SchoolReports(d,school).term(1).students
        assertEquals(listOf("zero","pending"),SchoolReports.ordered(rows,StudentOrder.LOWEST_GRADE,true).map{it.student.id})
        assertEquals(0.0,rows.first{it.student.id=="zero"}.total!!,0.0);assertNull(rows.first{it.student.id=="pending"}.total)
        assertEquals(listOf(2,7),SchoolReports.ordered(rows,StudentOrder.ABSENCES,true).map{it.student.number})
        assertEquals("zero",SchoolReports.ordered(rows,StudentOrder.LEAST_ABSENCES,false).first().student.id)
        for(view in ReportDisplay.views) for(order in ReportDisplay.orders(view)) SchoolReports.ordered(rows,order,true)
        assertEquals(before,ExportProjection.build(d,school,ExportOptions(term=1)))
    }
}
