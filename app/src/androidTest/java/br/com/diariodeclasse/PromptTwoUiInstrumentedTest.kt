package br.com.diariodeclasse

import android.content.Context
import android.graphics.Bitmap
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelProvider
import br.com.diariodeclasse.ui.DiaryViewModel
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.domain.CsvRoster
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

class PromptTwoUiInstrumentedTest {
    @get:Rule(order=0) val compose=createAndroidComposeRule<MainActivity>()
    @get:Rule(order=1) val entry=enterNoctuaRule(compose)
    private lateinit var app:DiaryApplication
    private var previous:AppSelection?=null
    private val c=SchoolClass("p2-ui-c",name="Turma Fictícia Atual",subject="História",year=2026)
    private val h=c.copy(id="p2-ui-h",name="Turma Fictícia Anterior",year=2025)
    private val a=Student("p2-ui-a","p2-ui-pa",c.id,"Estudante Fictícia Áurea",1,"2026-01-01")
    private val b=a.copy(id="p2-ui-b",personId="p2-ui-pb",classId=h.id,joined="2025-01-01")
    private val day="2026-08-28"
    @Before fun setup()=runBlocking {
        app=compose.activity.application as DiaryApplication;previous=app.selectionStore.read();app.commands.awaitPendingWrites()
        while(app.commands.messages.tryReceive().isSuccess){}
        app.repository.saveClass(c,"2026-01-01");app.repository.saveClass(h,"2025-01-01");app.repository.saveStudent(a);app.repository.saveStudent(b)
        val q=Activity("p2-ui-q",c.id,"Prova Fictícia Atual",date=day,term=3,maximum=10.0)
        val other=q.copy(id="p2-ui-qh",classId=h.id,name="Prova Fictícia Anterior",date="2025-08-28")
        app.repository.saveActivity(q);app.repository.saveActivity(other);app.repository.saveGrade(q.id,a.id,0.0);app.repository.saveGrade(other.id,b.id,7.5)
        app.repository.setDayHours(c.id,day,2);app.repository.setDayHours(c.id,"2026-08-29",1)
        app.repository.mark("${c.id}:2026-08-29:1",a.id,"FJ")
        app.selectionStore.write(AppSelection(2026,c.id));waitTag("class-selector")
        compose.waitUntil(10000){compose.onAllNodesWithText(c.name).fetchSemanticsNodes().isNotEmpty()}
        val vm=ViewModelProvider(compose.activity.academicOwner)[DiaryViewModel::class.java]
        compose.waitUntil(10000){vm.data.value.lessons.count{it.classId==c.id}==3&&vm.data.value.grades.any{it.activityId==other.id&&it.value==7.5}&&vm.data.value.marks.any{it.studentId==a.id&&it.mark=="FJ"}}
    }
    @After fun clean()=runBlocking {
        app.commands.awaitPendingWrites()
        for(id in listOf(c.id,h.id))app.repository.dao.schoolClass(id)?.let{val p=app.repository.previewClassDeletion(id);app.repository.deleteClass(p,p.confirmation)}
        app.selectionStore.write(previous);while(app.commands.messages.tryReceive().isSuccess){}
    }
    private fun tag(value:String)=compose.onNodeWithTag(value)
    private fun waitTag(value:String){compose.waitUntil(10000){compose.onAllNodesWithTag(value).fetchSemanticsNodes().isNotEmpty()}}
    private fun waitText(value:String){compose.waitUntil(10000){compose.onAllNodesWithText(value,substring=true).fetchSemanticsNodes().isNotEmpty()}}
    private fun hideKeyboard(){compose.runOnIdle{(app.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(compose.activity.window.decorView.windowToken,0)}}
    private fun shot(name:String){compose.waitForIdle();val f=File(app.filesDir,"prompt2-screenshots");f.mkdirs();File(f,"$name.png").outputStream().use{compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}}
    private fun report(){tag("nav-3").performClick();compose.onNodeWithText("3º bim.").performClick();waitTag("reports-list")}
    private fun search(value:String){tag("reports-list").performScrollToIndex(0);tag("report-search").performTextReplacement(value);tag("report-search").performImeAction()}
    private fun assertGrade(student:Student,value:String){tag("reports-list").performScrollToNode(hasTestTag("report-row-${student.id}"));compose.onNodeWithTag("report-row-${student.id}-current",useUnmergedTree=true).onChild().assertTextEquals(value)}
    @Test fun selectAllTriStateAndIndividualReviewPersistWithoutEnrollingAnyStudent() {
        runBlocking {
            app.repository.appendDrafts(c.id,listOf(CsvRoster.Row("Estudante Fictício Beta",2),CsvRoster.Row("Estudante Fictício Gama",3)))
            app.repository.appendDrafts(h.id,listOf(CsvRoster.Row("Estudante Fictício Outra Importação",2)))
        }
        val before=runBlocking{app.backupManager.snapshot()};val rows=before.drafts.filter{it.classId==c.id}
        compose.onNodeWithText("Gerenciar alunos").performScrollTo().performClick();compose.onNodeWithTag("import-review-open").performScrollTo().performClick();waitTag("import-select-all")
        tag("import-select-all").performScrollTo().performClick()
        compose.waitUntil(10000){runBlocking{app.repository.dao.draftRows(c.id).all{it.reviewed}}}
        tag("import-select-all").assertIsOn()
        assertEquals(before.students,runBlocking{app.backupManager.snapshot()}.students)
        compose.onAllNodesWithText("Cadastrar os alunos conferidos?").assertCountEquals(0)
        tag("import-review-list").performScrollToNode(hasTestTag("import-reviewed-${rows[0].id}"))
        tag("import-reviewed-${rows[0].id}").performClick()
        compose.waitUntil(10000){runBlocking{app.repository.dao.draftRows(c.id).count{it.reviewed}==1}}
        tag("import-review-list").performScrollToNode(hasTestTag("import-select-all"))
        tag("import-select-all").assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState,ToggleableState.Indeterminate));shot("01-conferencia-parcial")
        tag("import-select-all").performScrollTo().performClick();compose.waitUntil(10000){runBlocking{app.repository.dao.draftRows(c.id).all{it.reviewed}}}
        tag("import-select-all").performScrollTo().performClick();compose.waitUntil(10000){runBlocking{app.repository.dao.draftRows(c.id).none{it.reviewed}}}
        assertEquals(before,runBlocking{app.backupManager.snapshot()})
        compose.activityRule.scenario.recreate();waitTag("import-select-all");tag("import-select-all").performScrollTo().assertIsOff()
    }
    @Test fun globalSearchShowsBothSchoolsGradesAndClearingRestoresSelectedClassWithoutWriting() {
        val before=runBlocking{app.backupManager.snapshot()};report();search("FICTICIA aurea")
        assertGrade(a,"0,00")
        compose.onNode(hasText(c.name,substring=true) and hasAnyAncestor(hasTestTag("report-row-${a.id}-name")),useUnmergedTree=true).assertExists()
        assertGrade(b,"7,50")
        compose.onNode(hasText(h.name,substring=true) and hasAnyAncestor(hasTestTag("report-row-${b.id}-name")),useUnmergedTree=true).assertExists()
        shot("02-busca-global")
        tag("reports-list").performScrollToIndex(0);tag("report-search-clear").performClick()
        waitText("Boletim 3º Bim");assertGrade(a,"0,00")
        compose.onAllNodesWithTag("report-row-${b.id}").assertCountEquals(0)
        assertEquals(before,runBlocking{app.backupManager.snapshot()})
    }
    @Test fun globalSearchAlsoWorksWithNoClassSelectedInTheCurrentYear() {
        runBlocking{app.selectionStore.write(AppSelection(2027,null))};waitText("2027 ▾");report();search("aurea")
        assertGrade(a,"0,00");assertGrade(b,"7,50")
        assertEquals(AppSelection(2027,null),app.selectionStore.read())
        tag("reports-list").performScrollToIndex(0);tag("report-search-clear").performClick()
        waitText("Selecione uma turma ou pesquise um aluno em todas as turmas.")
    }
    private fun openDay() {
        tag("nav-1").performClick();waitTag("attendance-calendar")
        val now=LocalDate.now();compose.onNodeWithText("${now.monthValue} • ${now.month.getDisplayName(TextStyle.FULL,Locale.forLanguageTag("pt-BR"))} ▾").performClick()
        compose.onAllNodesWithText("8 • agosto").onLast().performScrollTo().performClick()
        val calendar=tag("attendance-calendar")
        calendar.performScrollToNode(hasTestTag("date-$day"))
        // O grid inteiro é um único item lazy. Para a última semana, trazer o item
        // da legenda à vista também desloca verticalmente o grid, não só seu scroll horizontal.
        calendar.performScrollToIndex(2)
        val date=tag("date-$day");date.assertIsDisplayed()
        val viewport=calendar.fetchSemanticsNode().boundsInRoot;val bounds=date.fetchSemanticsNode().boundsInRoot
        assertTrue("A data deve caber verticalmente na área tocável do calendário",bounds.top>=viewport.top&&bounds.bottom<=viewport.bottom)
        shot("00-calendario-final-do-mes")
        date.performClick()
        try{waitTag("attendance-day-$day");waitTag("daily-frequency")}catch(e:Exception){
            shot("falha-abertura-chamada")
            throw AssertionError("Abertura da data: "+compose.onRoot().printToString(),e)
        }
    }
    @Test fun largePAndFRecordDirectlyAndExplicitNrSurvivesRecreation() {
        val id="${c.id}:$day:1";val before=runBlocking{app.backupManager.snapshot()};openDay()
        fun waitMark(value:String){compose.waitUntil(10000){runBlocking{app.repository.dao.attendance(id).any{it.studentId==a.id&&it.mark==value}}}}
        for(value in listOf("P","F")) {
            tag("cell-$id-${a.id}-$value").performScrollTo().performClick();waitMark(value)
            tag("cell-$id-${a.id}-$value").performClick();waitMark(value)
            tag("cell-menu-$id-${a.id}").performClick();tag("attendance-unregister").performClick();waitMark("NR")
        }
        tag("cell-menu-$id-${a.id}").performScrollTo().performClick();compose.onNodeWithText("FJ — falta justificada").performClick();waitMark("FJ")
        tag("cell-menu-$id-${a.id}").performClick();tag("justification-remove").performClick();waitMark("F")
        tag("cell-menu-$id-${a.id}").performClick();tag("attendance-unregister").performClick();waitMark("NR")
        compose.activityRule.scenario.recreate();waitTag("daily-frequency")
        tag("cell-$id-${a.id}-P").assertIsNotSelected();tag("cell-$id-${a.id}-F").assertIsNotSelected()
        tag("frequency-total-${a.id}").performScrollTo().assertTextEquals("0 / 1");shot("03-chamada-sem-marcacao")
        val after=runBlocking{app.backupManager.snapshot()}
        assertEquals(before,after.copy(marks=after.marks.filterNot{it.lessonId==id&&it.studentId==a.id}))
    }
    private fun chooseExport(current:String,value:String) {
        compose.onNodeWithText("$current ▾").performScrollTo().performClick()
        compose.onNode(hasText(value) and hasAnyAncestor(isPopup()),useUnmergedTree=true).performClick()
    }
    private fun shareExport() {
        tag("export-options").performScrollTo().performClick();chooseExport("Salvar arquivo","Compartilhar")
        tag("export-settings-done").assertIsDisplayed().performClick();tag("export-file").performClick()
    }
    private fun backFromExternalWindow() {
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        compose.waitUntil(10000){automation.rootInActiveWindow?.packageName?.toString()?.let{it!=app.packageName}==true}
        automation.executeShellCommand("input keyevent KEYCODE_BACK").close()
        waitTag("attendance-export-screen")
    }
    @Test fun attendanceXlsxPreviewPickerCancelAndShareLeaveAllSchoolDataUnchanged() {
        runBlocking{app.repository.mark("${c.id}:$day:1",a.id,"P");app.repository.mark("${c.id}:$day:2",a.id,"F")}
        val before=runBlocking{app.backupManager.snapshot()};report();tag("open-export").performScrollTo().performClick();tag("export-type").performScrollTo().performClick();tag("export-type-ATTENDANCE").performClick();waitTag("attendance-export-screen")
        tag("export-options").performScrollTo().performClick()
        chooseExport("3º bimestre","Intervalo de datas")
        tag("attendance-export-start").performTextReplacement("28/08/2026");tag("attendance-export-end").performTextReplacement("29/08/2026");hideKeyboard();tag("export-settings-done").assertIsDisplayed().performClick()
        shot("04-exportar-chamada")
        tag("export-file").assertIsDisplayed().performClick();backFromExternalWindow();waitText("Exportação cancelada.")
        compose.onAllNodesWithText("Chamada exportada.").assertCountEquals(0)
        shareExport();backFromExternalWindow()
        assertEquals(before,runBlocking{app.backupManager.snapshot()})
    }
    @Test fun annualXlsxPreviewSaveCancellationAndShareAreReadOnly() {
        val before=runBlocking{app.backupManager.snapshot()}
        report();compose.onNodeWithContentDescription("Buscar aluno").assertExists()
        tag("open-export").performScrollTo().performClick();tag("export-type").performScrollTo().performClick();tag("export-type-ANNUAL").performClick();waitTag("annual-export-screen")
        tag("attendance-export-start").assertDoesNotExist();tag("attendance-export-end").assertDoesNotExist()
        tag("export-show-preview").performClick();tag("export-preview-0").assertIsDisplayed();tag("export-preview-close").performClick()
        fun back() {
            val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
            compose.waitUntil(10000){automation.rootInActiveWindow?.packageName?.toString()?.let{it!=app.packageName}==true}
            automation.executeShellCommand("input keyevent KEYCODE_BACK").close();waitTag("annual-export-screen")
        }
        tag("export-file").assertIsDisplayed().performClick();back();waitText("Exportação cancelada.")
        compose.onAllNodesWithText("Relatório anual exportado.").assertCountEquals(0)
        shareExport();back()
        assertEquals(before,runBlocking{app.backupManager.snapshot()})
    }

}
