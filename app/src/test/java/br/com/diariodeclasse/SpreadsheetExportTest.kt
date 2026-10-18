package br.com.diariodeclasse

import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.export.*
import br.com.diariodeclasse.reports.SchoolReports
import kotlinx.serialization.json.*
import org.junit.Test
import org.junit.Assert.*
import java.io.*
import java.util.zip.ZipInputStream

class SpreadsheetExportTest {
    private val g=SchoolClass(id="export-origin",name="Origem Fictícia",subject="História",year=2026)
    private val c=g.copy(id="export-destination",name="Destino Fictício")
    private val a=Student("export-a","person-a",c.id,"Estudante Fictício Áurea; \"β\"\nLinha\tTeste",1,"2026-01-01")
    private val b=a.copy(id="export-b",personId="person-b",number=2)
    private val old=a.copy(id="export-source",personId="moved-person",classId=g.id,name="Estudante Fictício Remanejado",left="2026-08-31",status="Remanejado")
    private val moved=old.copy(id="export-target",classId=c.id,number=3,joined="2026-09-01",left=null,status="Ativo")
    private val late=a.copy(id="export-late",personId="late-person",number=4,joined="2026-10-01",name="Estudante Fictício Tardio")
    private val q1=Activity("export-q1",c.id,"Prova Fictícia B1",date="2026-02-10",term=1,maximum=10.0)
    private val q3=q1.copy(id="export-q3",name="Avaliação Fictícia B3",date="2026-09-10",term=3,maximum=3.0)
    private val origin=q3.copy(id="export-old",classId=g.id,name="Atividade Anterior",date="2026-08-10",maximum=2.0)
    private fun data():DiarySnapshot {
        val terms=listOf(g,c).flatMap{cl->(1..4).map{n->val start=java.time.LocalDate.of(2026,3*(n-1)+1,1);Term(cl.id,n,start.toString(),start.plusMonths(3).minusDays(1).toString())}}
        val rows=listOf(ExportLayout.studentRow(moved,0),ExportLayout.blank(c.id),ExportLayout.studentRow(a,2),ExportLayout.studentRow(b,3).copy(manualHidden=true),ExportLayout.studentRow(late,4),ExportLayout.blank(c.id))
        return DiarySnapshot(classes=listOf(g,c),students=listOf(old,a,b,moved,late),terms=terms,activities=listOf(q1,q3,origin),
            grades=listOf(Grade(q1.id,a.id,0.0),Grade(q3.id,a.id,0.0),Grade(q3.id,moved.id,1.0),Grade(origin.id,old.id,1.5)),
            lessons=listOf(Lesson("old1",g.id,"2026-08-10",1,"REALIZADA"),Lesson("old2",g.id,"2026-08-10",2,"REALIZADA"),Lesson("next1",c.id,"2026-09-10",1,"REALIZADA"),Lesson("next2",c.id,"2026-09-10",2,"REALIZADA"),Lesson("cancel",c.id,"2026-09-10",3,"CANCELADA")),
            marks=listOf(Attendance("old1",old.id,"P"),Attendance("old2",old.id,"F"),Attendance("next1",moved.id,"P"),Attendance("next2",moved.id,"FJ"),Attendance("cancel",moved.id,"F")),
            recompositions=listOf(Recomposition(old.id,3,5.0,"2026-08-20")),relocations=listOf(Relocation(old.id,moved.id,"2026-09-01")),exportRows=ExportLayout.normalize(rows))
    }
    private fun value(t:ExportTable,id:String,key:String)=t.records.first{it.enrollmentId==id}.cells[t.columns.indexOfFirst{it.key==key}]
    @Test fun numericZeroPendingAndSubstitutionAreExactlyTheReportValues() {
        val d=data();val t=ExportProjection.build(d,c,ExportOptions(term=3));val r=SchoolReports(d,c).student(moved,3)
        assertEquals(r.original,value(t,moved.id,"3:original").number);assertEquals(r.total,value(t,moved.id,"3:current").number)
        assertEquals(5.0,value(t,moved.id,"3:recomposition").number!!,0.0);assertEquals(0.0,value(t,a.id,"activity:${q3.id}").number!!,0.0)
        assertNull(value(t,late.id,"activity:${q3.id}").number);assertTrue(value(t,late.id,"3:detail").text!!.contains("Não aplicável"))
        assertNull(value(t,a.id,"3:recomposition").number)
    }
    @Test fun everyModelUsesSameOrderVisibilityAndExplicitBlanksWithoutSchoolWrites() {
        val d=data();val before=ClassLifecycle.fingerprint(d)
        ExportModel.entries.forEach{model->val t=ExportProjection.build(d,c,ExportOptions(model,3));assertEquals(listOf(moved.id,null,a.id,late.id,null),t.records.map{it.enrollmentId})}
        val source=ExportProjection.build(d,g,ExportOptions(term=3));assertTrue(source.records.isEmpty())
        assertEquals(before,ClassLifecycle.fingerprint(d));assertEquals(listOf(1,1,2,3,4),d.students.sortedBy{it.number}.map{it.number})
    }
    @Test fun annualHasFourPeriodsAndActualAnnualAttendanceWithoutAnnualGradeAverage() {
        val d=data();val t=ExportProjection.build(d,c,ExportOptions(ExportModel.CONSOLIDATED,null))
        (1..4).forEach{n->assertTrue(t.columns.any{it.key=="$n:current"})};assertFalse(t.columns.any{it.key.contains("average")})
        val annual=SchoolReports(d,c).year(moved).attendance
        assertEquals(annual.percent,value(t,moved.id,"Anopercent").number);assertEquals(annual.performed.toDouble(),value(t,moved.id,"Anoperformed").number)
        assertEquals(4,annual.performed);assertEquals(1,annual.absent);assertEquals(1,annual.justified)
        assertTrue(value(t,a.id,"2:status").text!!.contains("Sem dados"));assertNull(value(t,a.id,"2:current").number)
    }
    @Test fun noHeadersNoNumberAndOnlyGradesContainNoStudentIdentitiesOrStatus() {
        val t=ExportProjection.build(data(),c,ExportOptions(term=3,headers=false,callNumber=false))
        assertFalse(t.columns.any{it.key=="number"});assertEquals(t.records.map{it.cells},t.rows())
        val copied=SpreadsheetWriter.clipboard(t,true)
        assertFalse(copied.contains("Estudante"));assertFalse(copied.contains("Original"));assertTrue(copied.contains("0"))
        assertEquals(1,t.rows(true).first().size);assertTrue(t.rows(true,true).first().size>1)
        assertEquals(t.records.size,copied.split("\r\n").size-1)
        assertTrue(SpreadsheetWriter.clipboard(ExportProjection.build(data(),c,ExportOptions(ExportModel.FREQUENCY,3)),true).isEmpty())
    }
    @Test fun justifiedPolicyCancelledLessonsAndPartialEnrollmentUseExistingRules() {
        val d=data();val t=ExportProjection.build(d,c,ExportOptions(ExportModel.FREQUENCY,3))
        assertEquals(2.0,value(t,moved.id,"3º bim.effective").number!!,0.0);assertEquals(4.0,value(t,moved.id,"3º bim.performed").number!!,0.0)
        assertEquals(0.0,value(t,late.id,"3º bim.performed").number!!,0.0);assertNull(value(t,late.id,"3º bim.percent").number)
        val changed=c.copy(countJustified=false);val without=ExportProjection.build(d.copy(classes=listOf(g,changed)),changed,ExportOptions(ExportModel.FREQUENCY,3))
        assertEquals(1.0,value(without,moved.id,"3º bim.effective").number!!,0.0)
        assertEquals(SchoolReports(d,c).student(moved,3).attendance!!.percent,value(t,moved.id,"3º bim.percent").number)
    }
    @Test fun archiveDoesNotChangeExportOrSchoolResultsAndHasNoGradeSum() {
        val d=data();val archived=d.copy(classArchives=listOf(ClassArchive(c.id)))
        assertEquals(ExportProjection.build(d,c,ExportOptions()),ExportProjection.build(archived,c,ExportOptions()))
        assertEquals(ClassLifecycle.snapshot(d),ClassLifecycle.snapshot(archived).copy(classArchives=emptyList()))
    }
    @Test fun csvBomDelimitersQuotesAndTsvMultilineEscapeArePreserved() {
        val t=ExportProjection.build(data(),c,ExportOptions(term=3))
        val bytes=ByteArrayOutputStream().also{SpreadsheetWriter.csv(t,it)}.toByteArray()
        assertArrayEquals(byteArrayOf(0xEF.toByte(),0xBB.toByte(),0xBF.toByte()),bytes.take(3).toByteArray())
        assertTrue(bytes.toString(Charsets.UTF_8).contains("\"\"β\"\""));assertTrue(SpreadsheetWriter.clipboard(t).contains("Linha\tTeste\""))
    }
    @Test fun deliverCancellationFailuresAndCloseErrorsNeverSucceedOrTouchSchoolData() {
        val folder=kotlin.io.path.createTempDirectory("export-ficticio-").toFile();val t=ExportProjection.build(data(),c,ExportOptions(term=3));var opened=false
        val first=ExportFiles.prepare(folder,t,ExportFormat.XLSX);assertFalse(ExportFiles.deliver(first,false){opened=true;ByteArrayOutputStream()});assertFalse(opened);assertFalse(first.exists())
        for(mode in 0..2) {
            val file=ExportFiles.prepare(folder,t,ExportFormat.CSV)
            try{ExportFiles.deliver(file,true){when(mode){0->null;else->object:OutputStream(){override fun write(b:Int){if(mode==1)throw IOException("Fictícia")};override fun close(){if(mode==2)throw IOException("Fictícia")}}}};fail("Gravação deveria falhar")}catch(_:IOException){}
            assertFalse(file.exists())
        }
        val file=ExportFiles.prepare(folder,t,ExportFormat.XLSX);val out=ByteArrayOutputStream();assertTrue(ExportFiles.deliver(file,true){out});assertTrue(out.size()>100);folder.deleteRecursively()
    }
    @Test fun xlsxHasOoxmlPartsAndInlineTextAndUnprotectedNumericCells() {
        val table=ExportProjection.build(data(),c,ExportOptions(term=3));val bytes=ByteArrayOutputStream().also{SpreadsheetWriter.xlsx(table,it)}.toByteArray()
        val parts=mutableMapOf<String,String>();ZipInputStream(bytes.inputStream()).use{z->while(true){val entry=z.nextEntry?:break;parts[entry.name]=z.readBytes().toString(Charsets.UTF_8)}}
        assertTrue(parts.keys.containsAll(listOf("[Content_Types].xml","xl/workbook.xml","xl/styles.xml","xl/worksheets/sheet1.xml")))
        val sheet=parts.getValue("xl/worksheets/sheet1.xml");assertTrue(sheet.contains("<v>0</v>"));assertFalse(sheet.contains("sheetProtection"));assertFalse(sheet.contains("<f>"));assertTrue(sheet.contains("inlineStr"))
    }
    @Test fun invalidXmlControlCharactersAreRejectedInsteadOfSilentlyChangingNames() {
        val table=ExportProjection.build(data().copy(students=data().students.map{if(it.id==a.id)it.copy(name="Fictício\u0000Teste")else it}),c,ExportOptions(term=3))
        try{SpreadsheetWriter.xlsx(table,ByteArrayOutputStream());fail()}catch(_:IllegalArgumentException){}
    }
    @Test fun generateIndependentReaderFixturesForAllModelsPeriodsAndHeaderVariants() {
        val folder=File("build/exports-validation");folder.mkdirs()
        val cases=buildJsonArray {
            ExportModel.entries.forEach{model->listOf(1,2,3,4,null).forEach{term->listOf(true,false).forEach{headers->
                val options=ExportOptions(model,term,headers,headers);val table=ExportProjection.build(data(),c,options)
                val name="${model.name}-${term?:0}-$headers"
                File(folder,"$name.xlsx").outputStream().use{SpreadsheetWriter.xlsx(table,it)}
                File(folder,"$name.csv").outputStream().use{SpreadsheetWriter.csv(table,it)}
                File(folder,"$name.tsv").writeText(SpreadsheetWriter.clipboard(table))
                File(folder,"$name-notes.tsv").writeText(SpreadsheetWriter.clipboard(table,true));File(folder,"$name-all-notes.tsv").writeText(SpreadsheetWriter.clipboard(table,true,true))
                fun matrix(rows:List<List<ExportCell>>)=buildJsonArray{rows.forEach{row->add(buildJsonArray{row.forEach{cell->add(cell.number?.let(::JsonPrimitive)?:cell.text?.takeIf{it.isNotEmpty()}?.let(::JsonPrimitive)?:JsonNull)}})}}
                add(buildJsonObject{put("name",name);put("columns",table.columns.size);put("rows",matrix(table.rows()));put("notes",matrix(table.rows(true)));put("allNotes",matrix(table.rows(true,true)));put("enrollments",buildJsonArray{table.records.forEach{add(it.enrollmentId?.let(::JsonPrimitive)?:JsonNull)}})})
            }}}
        };File(folder,"expected.json").writeText(cases.toString())
        assertEquals(30,cases.size)
    }
}
