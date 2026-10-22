package br.com.diariodeclasse

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.ui.*
import br.com.diariodeclasse.ui.theme.NoctuaTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.time.LocalDate
import java.time.format.TextStyle

class StageSixUiInstrumentedTest {
    @get:Rule(order=0) val compose=createAndroidComposeRule<MainActivity>()
    @get:Rule(order=1) val entry=enterNoctuaRule(compose)
    private lateinit var app:DiaryApplication
    private var previous:AppSelection?=null
    private val c=SchoolClass(id="ui6-c",name="1º X Fictício",subject="História",year=2026)
    private val h=c.copy(id="ui6-h",name="1º H Fictício")
    private val student=Student("ui6-s","ui6-p",c.id,"Estudante Fictício Nocturno",1,"2026-01-01")
    private val note=LessonRecord("ui6-r",c.id,"2026-08-06","Aula fictícia","Descrição livre para copiar.")
    private val vm get()=ViewModelProvider(compose.activity)[DiaryViewModel::class.java]
    @Before fun setup()=runBlocking {
        app=compose.activity.application as DiaryApplication;previous=app.selectionStore.read()
        app.repository.saveClass(c,"2026-01-01");app.repository.saveClass(h,"2026-01-01");app.repository.saveStudent(student)
        app.selectionStore.write(AppSelection(2026,c.id));compose.waitUntil(10000){vm.selection.value?.classId==c.id&&vm.data.value.students.any{it.id==student.id}}
    }
    @After fun cleanup()=runBlocking {
        app.commands.awaitPendingWrites()
        for(id in listOf(c.id,h.id))app.repository.dao.schoolClass(id)?.let{val p=app.repository.previewClassDeletion(id);app.repository.deleteClass(p,p.confirmation)}
        app.selectionStore.write(previous)
    }
    private fun tag(tag:String)=compose.onNodeWithTag(tag)
    private fun waitTag(tag:String){compose.waitUntil(10000){compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()}}
    private fun openDiary(){tag("nav-1").performClick();waitTag("class-mode-Diário");tag("class-mode-Diário").performClick();waitTag("journal-calendar")}
    private fun august(calendar:String){
        val month=LocalDate.now().monthValue;val name=LocalDate.now().month.getDisplayName(TextStyle.FULL,brLocale)
        compose.onNodeWithText("$month • $name ▾").performClick();compose.onNode(hasText("8 • agosto") and hasAnyAncestor(isPopup()),useUnmergedTree=true).performScrollTo().performClick()
        tag(calendar).performScrollToNode(hasTestTag("date-${note.date}"));tag("date-${note.date}").performScrollTo()
    }
    private fun shot(name:String){compose.waitForIdle();val dir=File(app.filesDir,"etapa6-screenshots");dir.mkdirs();File(dir,"$name.png").outputStream().use{compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}}
    @Test fun officialOwlIsVisibleWithoutTintOrDistortionAcrossAllFourPages() {
        val before=runBlocking {app.backupManager.snapshot()}
        for(page in 0..3) {
            tag("nav-$page").performClick()
            tag("noctua-logo").assertIsDisplayed().assertWidthIsEqualTo(32.dp).assertHeightIsEqualTo(32.dp)
                .assertContentDescriptionEquals("Logo do Noctua: coruja em forma de N")
        }
        val image=tag("noctua-logo").captureToImage().asAndroidBitmap()
        var blue=0;var gold=0
        for(y in 0 until image.height) for(x in 0 until image.width) {
            val color=image.getPixel(x,y);val r=android.graphics.Color.red(color);val g=android.graphics.Color.green(color);val b=android.graphics.Color.blue(color)
            if(b>r+20) blue++
            if(r>180 && g>100 && b<120) gold++
        }
        assertTrue("O fundo azul original não pode ser tingido pelo tema",blue>image.width*image.height/4)
        assertTrue("Os olhos dourados originais devem continuar visíveis",gold>0)
        val folder=File(app.filesDir,"brand-screenshots");folder.mkdirs()
        File(folder,"05-cabecalho-noctua.png").outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
        assertEquals(before,runBlocking {app.backupManager.snapshot()})
    }
    @Test fun initialAttendanceKeepsCalendarTermModeAndContextWhenSwitchingToJournal() {
        val before=runBlocking{app.backupManager.snapshot()}
        tag("nav-1").performClick();tag("class-mode-Chamada").assertIsSelected();tag("frequency-mode-Calendário").assertExists();tag("frequency-mode-Bimestre").performClick()
        tag("term-frequency").assertExists();tag("class-mode-Diário").performClick();waitTag("journal-calendar");tag("class-mode-Chamada").performClick();waitTag("term-frequency")
        assertEquals(AppSelection(2026,c.id),app.selectionStore.read());assertEquals(before,runBlocking{app.backupManager.snapshot()})
        shot("01-chamada-bimestre")
    }
    @Test fun selectedJournalDatePrefillsEditorPersistsAndMarksCalendarWithoutCreatingAttendance() {
        val before=runBlocking{app.backupManager.snapshot()};openDiary();august("journal-calendar");tag("date-${note.date}").performClick()
        waitTag("new-lesson-record");tag("new-lesson-record").performClick();tag("record-date").assertTextContains("06/08/2026");tag("record-class").assertTextContains(c.name,substring=true)
        tag("record-title").performScrollTo().performTextInput(note.title);tag("record-description").performScrollTo().performTextInput(note.description);tag("save-lesson-record").performScrollTo().performClick()
        compose.waitUntil(10000){runBlocking{app.database.backupDao().lessonRecords().size==1}};compose.waitUntil(12000){compose.onAllNodesWithText("Registro de aula salvo.").fetchSemanticsNodes().isEmpty()}
        tag("lesson-records-list").performScrollToIndex(0);tag("journal-back").performClick();waitTag("journal-calendar")
        tag("journal-calendar").performScrollToNode(hasTestTag("date-${note.date}"));tag("date-${note.date}").assertContentDescriptionContains("1 registro de aula",substring=true);shot("02-diario-calendario")
        val after=runBlocking{app.backupManager.snapshot()};assertEquals(before,after.copy(lessonRecords=emptyList()))
        compose.activityRule.scenario.recreate();waitTag("journal-calendar");tag("date-${note.date}").performScrollTo().assertContentDescriptionContains("1 registro de aula",substring=true)
    }
    @Test fun multipleJournalRecordsAndAttendanceMarkersStayIndependentAndReadOnly() {
        runBlocking{app.repository.saveLessonRecord(note);app.repository.saveLessonRecord(note.copy(id="ui6-second"));app.repository.setDayHours(c.id,note.date,2);app.repository.mark("${c.id}:${note.date}:1",student.id,"P");app.repository.mark("${c.id}:${note.date}:2",student.id,"FJ")}
        val before=runBlocking{app.backupManager.snapshot()};openDiary();august("journal-calendar")
        tag("date-${note.date}").assertContentDescriptionContains("2 registros de aula",substring=true)
        tag("date-${note.date}").performClick();tag("new-lesson-record").assertTextContains("Adicionar outro registro")
        tag("lesson-records-list").performScrollToIndex(1)
        tag("view-record-${note.id}").performScrollTo().performClick();compose.onNode(hasText(note.description) and hasAnyAncestor(isDialog()),useUnmergedTree=true).assertExists()
        compose.onNode(hasText("Voltar") and hasAnyAncestor(isDialog()),useUnmergedTree=true).performScrollTo().performClick()
        tag("class-mode-Chamada").performClick();waitTag("attendance-calendar")
        tag("date-${note.date}").performScrollTo().assertContentDescriptionContains("chamada preenchida",substring=true)
        assertEquals(before,runBlocking{app.backupManager.snapshot()})
    }
    @Test fun classSwitchDoesNotShowPreviousJournalAndKeepsSelectedYear() {
        runBlocking{app.repository.saveLessonRecord(note)};openDiary();august("journal-calendar");tag("date-${note.date}").assertContentDescriptionContains("1 registro de aula",substring=true)
        tag("class-selector").performClick();compose.onNode(hasText(h.name) and hasAnyAncestor(isPopup()),useUnmergedTree=true).performClick()
        compose.waitUntil(10000){app.selectionStore.read()?.classId==h.id};waitTag("journal-calendar");august("journal-calendar")
        tag("date-${note.date}").assertContentDescriptionContains("sem registro de aula",substring=true);assertEquals(2026,app.selectionStore.read()!!.year)
    }
    @Test fun reportsOrganizeThenExportPersistsHiddenRowsAndLeavesOfficialRosterUntouched() {
        val before=runBlocking{app.backupManager.snapshot()};tag("nav-3").performClick();tag("export-organize").performClick();waitTag("export-layout-list")
        tag("export-layout-list").performScrollToNode(hasTestTag("export-order-hide-${student.id}"));tag("export-order-hide-${student.id}").performClick()
        tag("export-layout-list").performScrollToIndex(0);tag("export-add-blank").performScrollTo().performClick();shot("03-organizar-exportacao")
        tag("export-conclude-and-export").performScrollTo().performClick();waitTag("export-screen")
        compose.waitUntil(10000){runBlocking{app.repository.dao.exportRows(c.id).size==2}}
        val after=runBlocking{app.backupManager.snapshot()};assertEquals(before,after.copy(exportRows=emptyList()));assertTrue(after.exportRows.first{it.studentId==student.id}.manualHidden);assertTrue(after.exportRows.any{it.studentId==null})
        compose.activityRule.scenario.recreate();waitTag("export-screen");shot("04-exportacao")
        tag("nav-0").performClick();compose.onNodeWithText("Gerenciar alunos").performScrollTo().performClick();tag("roster-list").performScrollToNode(hasTestTag("roster-student-${student.id}"));tag("roster-student-${student.id}").assertExists();compose.onAllNodesWithTag("export-organize").assertCountEquals(0)
    }
    @Test fun smallPhoneAndLargeFontKeepFourNavigationLabelsAndAccessibleModeTargets() {
        val model=vm
        compose.activityRule.scenario.onActivity{activity->activity.setContent{
            val original=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(original.density,1.3f)) {
                NoctuaTheme {Box(Modifier.requiredWidth(320.dp).height(600.dp).testTag("compact-host")){DiaryApp(model)}}
            }
        }}
        waitTag("nav-1");tag("nav-1").performClick();tag("class-mode-Chamada").assertHeightIsAtLeast(48.dp);tag("class-mode-Diário").assertHeightIsAtLeast(48.dp)
        for(i in 0..3)tag("nav-$i").assertIsDisplayed()
        for(label in listOf("Cadastrar\nTurma","Horários\nChamada\nDiário","Atividades\ne Notas","Relatórios")) {
            val layouts=mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label,useUnmergedTree=true).performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(layouts)}
            assertTrue(layouts.isNotEmpty());assertFalse("Rótulo truncado: $label",layouts.first().hasVisualOverflow)
        }
        shot("05-celular-320-fonte-ampliada")
        tag("class-mode-Diário").performClick();waitTag("journal-calendar");shot("06-diario-compacto")
        tag("class-mode-Chamada").performClick();waitTag("attendance-calendar")
        val compactDay="2026-${LocalDate.now().monthValue.toString().padStart(2,'0')}-06"
        tag("attendance-calendar").performScrollToNode(hasTestTag("date-$compactDay"));tag("date-$compactDay").performScrollTo().performClick()
        compose.onNodeWithText("Registrar horários").performScrollTo().assertIsDisplayed().performClick()
        compose.waitUntil(10000){runBlocking{app.repository.dao.day(c.id,compactDay).size==1}}

    }
}
