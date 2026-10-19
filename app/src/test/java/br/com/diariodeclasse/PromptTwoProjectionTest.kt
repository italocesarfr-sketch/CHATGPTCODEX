package br.com.diariodeclasse

import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.export.*
import br.com.diariodeclasse.reports.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PromptTwoProjectionTest {
    private val c=SchoolClass("p2-c",name="Turma Fictícia A",subject="História",year=2026)
    private val h=c.copy(id="p2-h",name="Turma Fictícia B")
    private val a=Student("p2-a","p2-pa",c.id,"Estudante Fictícia Áurea",1,"2026-01-01")
    private val b=a.copy(id="p2-b",personId="p2-pb",name="Estudante Fictícia Tardia",number=2,joined="2026-09-01")
    private val t=a.copy(id="p2-t",personId="p2-pt",name="Estudante Fictícia Transferida",number=3,status="Transferido",left="2026-08-28")
    private val hidden=a.copy(id="p2-hidden",personId="p2-ph",number=4)
    private fun data():DiarySnapshot {
        val l1=Lesson("p2-1",c.id,"2026-08-28",1,"REALIZADA")
        val l2=l1.copy(id="p2-2",ordinal=2)
        val l3=l1.copy(id="p2-3",date="2026-08-29")
        return DiarySnapshot(classes=listOf(c),students=listOf(hidden,t,b,a),lessons=listOf(l3,l2,l1,l1.copy(id="cancel",date="2026-08-30",state="CANCELADA"),l1.copy(id="future",date="2026-08-31",state="PREVISTA")),
            marks=listOf(Attendance(l1.id,a.id,"P"),Attendance(l2.id,a.id,"F"),Attendance(l3.id,a.id,"FJ"),Attendance(l1.id,t.id,"F"),Attendance(l2.id,t.id,"NR"),Attendance(l3.id,t.id,"P")),
            exportRows=listOf(ExportLayout.studentRow(hidden,0).copy(manualHidden=true),ExportLayout.studentRow(t,1),ExportRow("blank:p2",c.id,null,2),ExportLayout.studentRow(a,3),ExportLayout.studentRow(b,4)))
    }
    @Test fun attendanceUsesOfficialOrderIndependentHoursAndBlankNrOrIneligibleCells() {
        val source=data();val before=ClassLifecycle.fingerprint(source)
        val table=AttendanceExportProjection.build(source,c,"2026-08-28","2026-08-31")
        assertEquals(listOf("number","name","lesson:p2-1","lesson:p2-2","lesson:p2-3"),table.columns.map{it.key})
        assertEquals(listOf(a.id,b.id,t.id),table.records.map{it.enrollmentId})
        assertEquals(listOf("P","F","FJ"),table.records[0].cells.drop(2).map{it.text})
        assertTrue(table.records[1].cells.drop(2).all{it==ExportCell.EMPTY})
        assertEquals(listOf("F",null,null),table.records[2].cells.drop(2).map{it.text})
        assertEquals(before,ClassLifecycle.fingerprint(source))
    }
    @Test fun invalidIntervalsDoNotProduceAWorkbook() {
        for(pair in listOf("2026-09-01" to "2026-08-01","2025-01-01" to "2026-08-01")) {
            try{AttendanceExportProjection.build(data(),c,pair.first,pair.second);fail()}catch(_:IllegalArgumentException){}
        }
    }
    @Test fun globalSearchIgnoresAccentCaseAndKeepsHomonymsAndSchoolScoresSeparate() {
        val other=a.copy(id="p2-other",classId=h.id,personId="p2-other-person")
        val moved=a.copy(id="p2-moved",name="Estudante Fictícia Áurea Histórica",status="Remanejado")
        val q=Activity("q",c.id,"Avaliação Fictícia",date="2026-08-28",term=3,maximum=10.0)
        val qh=q.copy(id="qh",classId=h.id)
        val d=DiarySnapshot(classes=listOf(c,h),students=listOf(a,other,moved),activities=listOf(q,qh),grades=listOf(Grade(q.id,a.id,0.0),Grade(qh.id,other.id,7.5)),classArchives=listOf(ClassArchive(h.id)))
        val matches=GlobalStudentSearch.find(d,"FICTICIA aurea")
        assertEquals(setOf(a.id,other.id),matches.map{it.student.id}.toSet())
        assertEquals(mapOf(a.id to 0.0,other.id to 7.5),matches.associate{it.student.id to SchoolReports(d,it.school).student(it.student,3).total})
        assertEquals(3,GlobalStudentSearch.find(d,"áUREA",true).size)
        assertTrue(GlobalStudentSearch.find(d,"").isEmpty());assertTrue(GlobalStudentSearch.find(d,"Nome Fictício Inexistente").isEmpty())
    }
    @Test fun attendanceAndSearchKeepHistoricalOriginsAfterSuccessiveClassDeletion() {
        val before=BackupCodec.json.decodeFromString<BackupSnapshot>(requireNotNull(javaClass.classLoader?.getResourceAsStream("history-deletion.json")).bufferedReader().use{it.readText()}).diarySnapshot()
        val after=ClassLifecycle.without(ClassLifecycle.without(before,"history-x"),"history-g")
        val c=before.classes.first{it.id=="history-h"}
        val first=AttendanceExportProjection.build(before,c,"2026-01-01","2026-12-31")
        assertEquals(first,AttendanceExportProjection.build(after,c,"2026-01-01","2026-12-31"))
        assertTrue(first.columns.any{it.label.contains("(origem)")})
        assertEquals(first.columns.size,first.columns.map{it.key}.distinct().size)
        val student=after.students.first{it.id=="hh"}
        assertEquals(listOf("hh"),GlobalStudentSearch.find(after,student.name).map{it.student.id})
    }
    @Test fun generateExactAttendanceWorkbookForIndependentReader() {
        val table=AttendanceExportProjection.build(data(),c,"2026-08-28","2026-08-31")
        val folder=File("build/attendance-export-validation");folder.mkdirs();val name="chamada-horarios"
        File(folder,"$name.xlsx").outputStream().use{SpreadsheetWriter.xlsx(table,it)}
        File(folder,"$name.csv").outputStream().use{SpreadsheetWriter.csv(table,it)}
        File(folder,"$name.tsv").writeText(SpreadsheetWriter.clipboard(table))
        File(folder,"$name-notes.tsv").writeText(SpreadsheetWriter.clipboard(table,true))
        File(folder,"$name-all-notes.tsv").writeText(SpreadsheetWriter.clipboard(table,true,true))
        val rows=listOf(
            listOf("Noctua • Turma Fictícia A • História • 2026 • Chamada • 2026-08-28 a 2026-08-31",null,null,null,null),
            listOf("Nº da chamada","Nome completo","28/08 • 1º horário","28/08 • 2º horário","29/08 • 1º horário"),
            listOf(1,a.name,"P","F","FJ"),listOf(2,b.name,null,null,null),listOf(3,t.name,"F",null,null))
        val expected=buildJsonArray{add(buildJsonObject{
            put("name",name);put("columns",5);put("rows",buildJsonArray{rows.forEach{row->add(buildJsonArray{row.forEach{v->add(when(v){null->JsonNull;is Number->JsonPrimitive(v);else->JsonPrimitive(v.toString())})}})}})
            put("notes",JsonArray(emptyList()));put("allNotes",JsonArray(emptyList()))
        })};File(folder,"expected.json").writeText(expected.toString())
    }
}
