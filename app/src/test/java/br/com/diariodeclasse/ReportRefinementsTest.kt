package br.com.diariodeclasse

import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.export.*
import br.com.diariodeclasse.reports.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ReportRefinementsTest {
    private val c=SchoolClass("annual-c",name="Turma Fictícia Anual",subject="História",year=2026)
    private val a=Student("annual-a","annual-pa",c.id,"Estudante Fictícia Áurea",1,"2026-01-01")
    private val b=a.copy(id="annual-b",personId="annual-pb",number=2,joined="2026-08-01")
    private val p=a.copy(id="annual-p",personId="annual-pp",number=3)
    private fun data():DiarySnapshot {
        val lessons=listOf("2026-02-10","2026-05-10","2026-08-10","2026-11-10").mapIndexed{i,date->Lesson("annual-$i",c.id,date,1,"REALIZADA")}
        val terms=listOf(Term(c.id,1,"2026-01-01","2026-03-31"),Term(c.id,2,"2026-04-01","2026-06-30"),Term(c.id,3,"2026-07-01","2026-09-30"),Term(c.id,4,"2026-10-01","2026-12-31"))
        return DiarySnapshot(classes=listOf(c),students=listOf(p,b,a),terms=terms,lessons=lessons+lessons[0].copy(id="double",ordinal=2)+lessons[3].copy(id="cancelled",state="CANCELADA"),
            marks=listOf(Attendance("annual-0",a.id,"P"),Attendance("double",a.id,"F"),Attendance("annual-1",a.id,"FJ"),Attendance("annual-2",a.id,"NR"),Attendance("annual-3",a.id,"P"),Attendance("cancelled",a.id,"F"),Attendance("annual-2",b.id,"P")),
            activities=listOf(Activity("annual-activity",c.id,"Prova fictícia",date="2026-02-10",term=1,maximum=10.0)),grades=listOf(Grade("annual-activity",a.id,0.0),Grade("annual-activity",p.id,8.0)),
            exportRows=listOf(ExportLayout.studentRow(b,0),ExportRow("annual-blank",c.id,null,1),ExportLayout.studentRow(p,2).copy(manualHidden=true),ExportLayout.studentRow(a,3)))
    }
    @Test fun updateProbeCanStillCallTheLegacyKotlinDefaultBridgeOnVersion0121() {
        val bridge=DiaryRepository::class.java.declaredMethods.single { it.name=="setDayHours\$default" && it.parameterTypes.size==8 }
        assertEquals(listOf(DiaryRepository::class.java.name,"java.lang.String","java.lang.String","int","boolean","kotlin.coroutines.Continuation","int","java.lang.Object"),bridge.parameterTypes.map{it.name})
    }
    @Test fun allFiveOrdersAreStableAndUseExistingEffectiveAbsencesAndGrades() {
        val rows=SchoolReports(data(),c).term(1).students.reversed()
        fun order(o:StudentOrder)=SchoolReports.ordered(rows,o,true).map{it.student.id}
        assertEquals(listOf(a.id,b.id,p.id),order(StudentOrder.CALL))
        assertEquals(listOf(a.id,b.id,p.id),order(StudentOrder.ABSENCES))
        assertEquals(listOf(b.id,p.id,a.id),order(StudentOrder.LEAST_ABSENCES))
        assertEquals(listOf(a.id,p.id,b.id),order(StudentOrder.LOWEST_GRADE))
        assertEquals(listOf(p.id,a.id,b.id),order(StudentOrder.HIGHEST_GRADE))
        val b2=SchoolReports(data(),c).term(2).students
        assertEquals(a.id,SchoolReports.ordered(b2,StudentOrder.LEAST_ABSENCES,false).first().student.id)
        assertEquals(b.id,SchoolReports.ordered(b2,StudentOrder.LEAST_ABSENCES,true).first().student.id)
    }
    @Test fun annualReusesFrequencyProjectionHasFourTermsAndNoGradeMean() {
        val d=data();val before=ClassLifecycle.fingerprint(d);val t=AnnualExportProjection.build(d,c)
        assertEquals(listOf(a.id,b.id),t.records.map{it.enrollmentId});assertEquals(45,t.columns.size)
        fun value(key:String,id:String=a.id)=t.records.single{it.enrollmentId==id}.cells[t.columns.indexOfFirst{it.key==key}].number
        assertEquals(1.0,value("1º bim.absent"));assertEquals(1.0,value("2º bim.justified"));assertEquals(1.0,value("3º bim.nr"))
        assertEquals(50.0,value("Anopercent"));assertEquals(2.0,value("Anoeffective"));assertNull(value("1º bim.percent",b.id))
        assertFalse(t.columns.any{it.grade});assertEquals(before,ClassLifecycle.fingerprint(d))
        val source=ExportProjection.build(d,c,ExportOptions(ExportModel.FREQUENCY,null))
        for(row in t.records) {
            val original=source.records.single{it.enrollmentId==row.enrollmentId}
            assertEquals(original.cells,row.cells.take(2)+row.cells.drop(4))
        }
    }
    @Test fun successiveRelocationsAndDeletedOriginsKeepExactAnnualFrequency() {
        val d=BackupCodec.json.decodeFromString<BackupSnapshot>(javaClass.classLoader!!.getResourceAsStream("history-deletion.json")!!.bufferedReader().readText()).diarySnapshot()
        val c=d.classes.first{it.id=="history-h"};val before=AnnualExportProjection.build(d,c)
        val deleted=ClassLifecycle.without(ClassLifecycle.without(d,"history-x"),"history-g")
        assertEquals(before,AnnualExportProjection.build(deleted,c))
    }
    @Test fun emptyDayValidationProtectsAllKindsOfLinkedHistory() {
        val d=data();val day="2026-10-19";val l=Lesson("empty",c.id,day,1,"CANCELADA")
        val base=d.copy(lessons=d.lessons+l)
        assertEquals(listOf(l),EmptyDayHours.validate(base,c.id,day))
        for(blocked in listOf(base.copy(marks=d.marks+Attendance(l.id,a.id,"NR")),base.copy(lessons=d.lessons+l.copy(note="Observação")),
            base.copy(lessonRecords=listOf(LessonRecord("diary",c.id,day,"Título","Texto"))),base.copy(activities=d.activities+d.activities.first().copy(id="dated",date=day)),
            base.copy(recompositions=listOf(Recomposition(a.id,3,0.0,day))))) {
            val before=ClassLifecycle.fingerprint(blocked)
            try{EmptyDayHours.validate(blocked,c.id,day);fail()}catch(_:IllegalArgumentException){}
            assertEquals(before,ClassLifecycle.fingerprint(blocked))
        }
    }
    @Test fun generateAnnualWorkbookForIndependentReader() {
        val t=AnnualExportProjection.build(data(),c)
        val folder=File("build/annual-export-validation");folder.mkdirs();val name="relatorio-anual"
        File(folder,"$name.xlsx").outputStream().use{SpreadsheetWriter.xlsx(t,it)}
        File(folder,"$name.csv").outputStream().use{SpreadsheetWriter.csv(t,it)}
        File(folder,"$name.tsv").writeText(SpreadsheetWriter.clipboard(t))
        File(folder,"$name-notes.tsv").writeText("");File(folder,"$name-all-notes.tsv").writeText("")
        File(folder,"expected.json").writeText(buildJsonArray{add(buildJsonObject{
            put("name",name);put("columns",t.columns.size)
            put("rows",buildJsonArray{t.rows().forEach{row->add(buildJsonArray{row.forEach{cell->add(cell.number?.let{JsonPrimitive(it)}?:cell.text?.let{JsonPrimitive(it)}?:JsonNull)}})}})
            put("notes",JsonArray(emptyList()));put("allNotes",JsonArray(emptyList()))
        })}.toString())
    }
}
