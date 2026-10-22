package br.com.diariodeclasse

import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.export.*
import br.com.diariodeclasse.reports.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.Assert.*
import java.io.File

class ExportPresentationTest {
    private val c = SchoolClass("minimal-class", name="Turma Fictícia", subject="História", year=2026)
    private val s = Student("minimal-student","minimal-person",c.id,"Estudante Fictícia Áurea",1,"2026-01-01")
    private val activity = Activity("minimal-activity",c.id,"Atividade Fictícia",date="2026-08-10",term=3,maximum=10.0)
    private fun data() = DiarySnapshot(classes=listOf(c),students=listOf(s),
        terms=(1..4).map { n -> Term(c.id,n,"2026-${listOf("01","04","07","10")[n-1]}-01","2026-${listOf("03-31","06-30","09-30","12-31")[n-1]}") },
        activities=listOf(activity), grades=listOf(Grade(activity.id,s.id,0.0)),
        lessons=listOf(Lesson("minimal-lesson",c.id,"2026-08-10",1,"REALIZADA")),marks=listOf(Attendance("minimal-lesson",s.id,"FJ")),
        lessonRecords=listOf(LessonRecord("minimal-plan",c.id,"2026-08-10","Título fictício","Descrição exata\nAção e reflexão.")))
    @Test fun allSixDefaultsAreIdenticalToPreviouslyApprovedProjections() {
        val d=data(); val p=SpreadsheetPreferences()
        SpreadsheetKind.entries.forEach { kind ->
            val expected=when(kind) {
                SpreadsheetKind.NOTES,SpreadsheetKind.FREQUENCY,SpreadsheetKind.CONSOLIDATED -> ExportProjection.build(d,c,ExportOptions(ExportModel.valueOf(kind.name),3))
                SpreadsheetKind.ATTENDANCE -> AttendanceExportProjection.build(d,c,"2026-07-01","2026-09-30")
                SpreadsheetKind.PLANS -> LessonPlanExportProjection.build(d,c,"2026-07-01","2026-09-30")
                SpreadsheetKind.ANNUAL -> AnnualExportProjection.build(d,c)
            }
            assertEquals(kind.name,expected,ExportPresentation.build(d,c,3,kind,p))
        }
    }
    @Test fun headersNumbersAnnualAndEveryRegularModelRetainExactParameters() {
        val d=data()
        for(kind in SpreadsheetKind.entries.filter{it.regular}) for(headers in listOf(true,false)) for(numbers in listOf(true,false)) for(annual in listOf(true,false)) {
            val p=SpreadsheetPreferences(headers=headers,callNumber=numbers,allTerms=annual)
            assertEquals(ExportProjection.build(d,c,ExportOptions(ExportModel.valueOf(kind.name),if(annual)null else 3,headers,numbers)),ExportPresentation.build(d,c,3,kind,p))
        }
    }
    @Test fun headerAndNumberOptionsForAttendanceAndAnnualRemoveOnlyRequestedPresentation() {
        for(kind in listOf(SpreadsheetKind.ATTENDANCE,SpreadsheetKind.ANNUAL)) {
            val full=ExportPresentation.build(data(),c,3,kind,SpreadsheetPreferences())
            val reduced=ExportPresentation.build(data(),c,3,kind,SpreadsheetPreferences(headers=false,callNumber=false))
            assertEquals(full.columns.filterNot{it.key=="number"},reduced.columns)
            assertEquals(full.records.map{it.cells.drop(1)},reduced.rows())
        }
    }
    @Test fun inheritedTermChangesTheProjectionWithoutAnySecondPeriodState() {
        val p=SpreadsheetPreferences()
        assertTrue(ExportPresentation.build(data(),c,3,SpreadsheetKind.NOTES,p).columns.any{it.key=="3:current"})
        assertTrue(ExportPresentation.build(data(),c,4,SpreadsheetKind.NOTES,p).columns.any{it.key=="4:current"})
        assertFalse(ExportPresentation.build(data(),c,4,SpreadsheetKind.NOTES,p).columns.any{it.key=="3:current"})
    }
    @Test fun allCopyModesPreserveZeroPendingAndExplicitBlankRows() {
        val d=data().copy(students=listOf(s,s.copy(id="pending",personId="pending-person",number=2)),exportRows=listOf(ExportLayout.studentRow(s,0),ExportRow("blank",c.id,null,1)))
        val table=ExportPresentation.build(d,c,3,SpreadsheetKind.NOTES,SpreadsheetPreferences())
        assertEquals(SpreadsheetWriter.clipboard(table),ExportPresentation.copy(table,SpreadsheetPreferences()))
        assertEquals(SpreadsheetWriter.clipboard(table,true),ExportPresentation.copy(table,SpreadsheetPreferences(copyMode="Somente notas vigentes")))
        assertEquals(SpreadsheetWriter.clipboard(table,true,true),ExportPresentation.copy(table,SpreadsheetPreferences(copyMode="Todas as notas")))
        assertEquals(0.0,table.records.first().cells[table.columns.indexOfFirst{it.key=="3:current"}].number!!,0.0)
        assertTrue(table.records.any{it.enrollmentId==null&&it.cells.all{it==ExportCell.EMPTY}})
        assertNull(table.records.last().cells[table.columns.indexOfFirst{it.key=="3:current"}].number)
    }
    @Test fun serializedPreferencesRemainSeparateBetweenModelsAndRecreation() {
        val saved=mapOf("NOTES" to SpreadsheetPreferences(format=ExportFormat.CSV,headers=false),"PLANS" to SpreadsheetPreferences(customRange=true,start="2026-08-01",end="2026-08-31"))
        val restored=Json.decodeFromString<Map<String,SpreadsheetPreferences>>(Json.encodeToString(saved))
        assertEquals(saved,restored);assertEquals(SpreadsheetPreferences(),restored["FREQUENCY"]?:SpreadsheetPreferences())
    }
    @Test fun customPlanRangeDoesNotConsultAttendanceOrOtherClasses() {
        val d=data(); val p=SpreadsheetPreferences(customRange=true,start="2026-08-01",end="2026-08-31")
        val t=ExportPresentation.build(d.copy(lessons=emptyList(),marks=emptyList()),c,3,SpreadsheetKind.PLANS,p)
        assertEquals(listOf("date","title","description"),t.columns.map{it.key});assertEquals(1,t.records.size)
        assertEquals("Descrição exata\nAção e reflexão.",t.records.single().cells.last().text)
        assertTrue(ExportPresentation.build(d,c,3,SpreadsheetKind.PLANS,p.copy(start="2026-01-01",end="2026-01-31")).records.isEmpty())
        assertThrows(IllegalArgumentException::class.java){ExportPresentation.build(d,c,3,SpreadsheetKind.PLANS,p.copy(start="2025-01-01"))}
    }
    @Test fun csvAndTsvFilesUseSameMatrixAsPreviewAndClipboard() {
        val table=ExportPresentation.build(data(),c,3,SpreadsheetKind.NOTES,SpreadsheetPreferences())
        val folder=File("build/minimal-export-temporary").also{it.mkdirs()}
        try {
            for(format in listOf(ExportFormat.CSV,ExportFormat.TSV)) {
                val file=ExportFiles.prepare(folder,table,format)
                if(format==ExportFormat.TSV)assertEquals(SpreadsheetWriter.clipboard(table),file.readText())
                else assertEquals("\uFEFF"+SpreadsheetWriter.delimited(table.rows(),';'),file.readText())
                assertEquals(format.extension,file.extension);file.delete()
            }
        }finally{folder.deleteRecursively()}
    }
    @Test fun alphabeticDisplayOrderNeverChangesOfficialCallOrTheExportLayout() {
        val d=data().copy(students=listOf(s.copy(name="Z Fictício"),s.copy(id="second",personId="second-person",number=2,name="A Fictício")))
        val before=ExportPresentation.build(d,c,3,SpreadsheetKind.NOTES,SpreadsheetPreferences())
        val rows=SchoolReports(d,c).term(3).students
        assertEquals(listOf(2,1),SchoolReports.ordered(rows,StudentOrder.ALPHABETICAL,true).map{it.student.number})
        assertEquals(listOf(1,2),SchoolReports.ordered(rows,StudentOrder.CALL,true).map{it.student.number})
        assertEquals(before,ExportPresentation.build(d,c,3,SpreadsheetKind.NOTES,SpreadsheetPreferences()))
    }
}
