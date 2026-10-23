package br.com.diariodeclasse

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.time.LocalDate
import java.time.format.TextStyle

class TimetableUiInstrumentedTest {
    @get:Rule(order=0) val compose=createAndroidComposeRule<MainActivity>()
    @get:Rule(order=1) val entry=enterNoctuaRule(compose)
    private lateinit var app:DiaryApplication
    private var previous:AppSelection?=null
    private var previousRows=emptyList<WeeklyTimetable>()
    private val c=SchoolClass(id="weekly-ui-c",name="Turma Fictícia Horários",subject="História",year=2026)
    private val h=c.copy(id="weekly-ui-h",name="Turma Fictícia B")
    private val day="2026-08-06"
    private val vm get()=ViewModelProvider(compose.activity)[DiaryViewModel::class.java]
    private fun tag(s:String)=compose.onNodeWithTag(s)
    private fun waitTag(s:String){compose.waitUntil(10000){compose.onAllNodesWithTag(s).fetchSemanticsNodes().isNotEmpty()}}
    @Before fun setup()=runBlocking {
        app=compose.activity.application as DiaryApplication;previous=app.selectionStore.read();previousRows=app.database.backupDao().timetables()
        app.repository.saveClass(c,"2026-01-01");app.repository.saveClass(h,"2026-01-01")
        app.selectionStore.write(AppSelection(2026,c.id));compose.waitUntil(10000){vm.data.value.classes.any{it.id==c.id}&&vm.selection.value?.classId==c.id}
    }
    @After fun cleanup()=runBlocking {
        app.commands.awaitPendingWrites()
        app.database.backupDao().removeTimetables();app.database.backupDao().insertTimetables(previousRows)
        for(id in listOf(c.id,h.id)){val p=app.repository.previewClassDeletion(id);app.repository.deleteClass(p,p.confirmation)}
        app.selectionStore.write(previous)
    }
    private fun weekly(){tag("nav-1").performClick();waitTag("class-mode-Horários");tag("class-mode-Horários").performClick();waitTag("weekly-timetable")}
    private fun seed()=runBlocking {
        val slots=(1..2).map{WeeklySlot(classId=c.id,weekday=4,ordinal=it,className=c.name,subject=c.subject,shift=c.shift)}
        app.repository.confirmTimetable(app.repository.previewTimetable(2026,"2026-01-01",slots))
        compose.waitUntil(10000){vm.data.value.timetables.any{it.slots.any{s->s.classId==c.id}}}
    }
    private fun august(calendar:String){
        val now=LocalDate.now();compose.onNodeWithText("${now.monthValue} • ${now.month.getDisplayName(TextStyle.FULL,brLocale)} ▾").performClick()
        compose.onNode(hasText("8 • agosto") and hasAnyAncestor(isPopup()),useUnmergedTree=true).performScrollTo().performClick()
        tag(calendar).performScrollToNode(hasTestTag("date-$day"));tag("date-$day").performScrollTo()
    }
    private fun shot(name:String){val dir=File(app.filesDir,"weekly-screenshots");dir.mkdirs();val root=if(compose.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty())compose.onNode(isDialog())else compose.onRoot();File(dir,"$name.png").outputStream().use{root.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}}
    @Test fun timetableCanBeOpenedWithNoClassInTheSelectedYearAndDoesNotInsertAnything() {
        runBlocking{app.selectionStore.write(AppSelection(2099,null))}
        compose.waitUntil(10000){vm.selection.value?.year==2099}
        val before=runBlocking{app.backupManager.snapshot()}
        weekly();tag("add-weekly-hours").assertIsNotEnabled()
        tag("weekday-${LocalDate.now().dayOfWeek.value}").assertIsSelected()
        assertEquals(before,runBlocking{app.backupManager.snapshot()})
    }
    @Test fun todayAndEveryWeekdayAreAccessibleAndWeeklyShortcutOpensAttendance() {
        seed();weekly();tag("weekday-${LocalDate.now().dayOfWeek.value}").assertIsSelected()
        for(d in 1..7){tag("weekday-$d").performClick().assertIsSelected()}
        tag("weekday-4").performClick();tag("weekly-timetable").performScrollToNode(hasTestTag("weekly-class-${c.id}"));tag("weekly-class-${c.id}").assertExists();shot("01-grade-semanal")
        tag("open-weekly-${c.id}").performScrollTo().performClick();waitTag("attendance-calendar")
        assertEquals(c.id,app.selectionStore.read()?.classId)
    }
    @Test fun registerDoubleHoursThroughEditorAndCancelAnotherDraftWithoutPersisting() {
        weekly();tag("weekday-4").performClick();tag("add-weekly-hours").performClick()
        tag("weekly-period-1").performScrollTo().performClick();tag("weekly-period-2").performScrollTo().performClick()
        tag("timetable-effective").assertDoesNotExist();tag("timetable-scope-YEAR").assertDoesNotExist()
        shot("02-editar-grade")
        tag("review-weekly-hours").performScrollTo().performClick()
        compose.waitUntil(10000){runBlocking{app.database.backupDao().timetables().any{it.slots.count{s->s.classId==c.id}==2}}}
        val saved=runBlocking{app.backupManager.snapshot()};assertEquals("2026-01-01",saved.timetables.single().effective);compose.activityRule.scenario.recreate();waitTag("weekly-timetable")
        tag("add-weekly-hours").performClick();tag("weekly-period-3").performScrollTo().performClick()
        compose.onNode(hasText("Voltar") and hasAnyAncestor(isDialog()),useUnmergedTree=true).performScrollTo().performClick()
        compose.onNodeWithText("Descartar edição da grade?").assertExists();compose.onNodeWithText("Confirmar").performClick()
        assertEquals(saved,runBlocking{app.backupManager.snapshot()})
    }
    @Test fun sharedCalendarsSuggestTwoHoursWithoutWritingUntilExplicitConfirmation() {
        seed();val before=runBlocking{app.backupManager.snapshot()}
        tag("nav-1").performClick();august("attendance-calendar")
        tag("date-$day").assertContentDescriptionContains("2 aulas previstas",substring=true);shot("03-chamada-prevista")
        tag("date-$day").performClick();tag("hours-choice-2").assertIsSelected();tag("planned-hours").assertTextEquals("2 aulas previstas para esta turma.")
        assertEquals(before,runBlocking{app.backupManager.snapshot()})
        tag("class-mode-Diário").performClick();waitTag("journal-calendar")
        tag("journal-calendar").performScrollToNode(hasTestTag("date-$day"));tag("date-$day").assertContentDescriptionContains("2 aulas previstas",substring=true)
        shot("04-diario-previsto");tag("date-$day").performClick();tag("new-lesson-record").assertExists()
        assertEquals(before,runBlocking{app.backupManager.snapshot()})
        tag("class-mode-Chamada").performClick();waitTag("hours-choice-2");compose.onNodeWithText("Registrar horários").performScrollTo().performClick()
        compose.waitUntil(10000){runBlocking{app.repository.dao.day(c.id,day).size==2}}
        assertEquals(2,runBlocking{app.repository.dao.day(c.id,day).size});assertTrue(runBlocking{app.database.backupDao().marks().isEmpty()})
    }
    @Test fun journalContentRemainsIndependentAndCancelledAttendanceIsNotRecreated() {
        seed();runBlocking{app.repository.setDayHours(c.id,day,1);app.repository.setDayHours(c.id,day,0,true);app.repository.saveLessonRecord(LessonRecord(classId=c.id,date=day,title="Conteúdo Fictício",description="Um registro para duas aulas previstas."))}
        val before=runBlocking{app.backupManager.snapshot()}
        tag("nav-1").performClick();august("attendance-calendar")
        tag("date-$day").assertContentDescriptionContains("horários cancelados",substring=true)
        tag("date-$day").performClick();compose.onNodeWithText("Horários: 0 • Alterar").assertExists()
        tag("class-mode-Diário").performClick();waitTag("journal-calendar");tag("journal-calendar").performScrollToNode(hasTestTag("date-$day"))
        tag("date-$day").assertContentDescriptionContains("1 registro de aula",substring=true).assertContentDescriptionContains("2 aulas previstas",substring=true)
        assertEquals(before,runBlocking{app.backupManager.snapshot()})
    }
    @Test fun switchingAwayFromEditorRequiresDiscardAndKeepsConfirmedScheduleIntact() {
        weekly();val before=runBlocking{app.backupManager.snapshot()}
        tag("add-weekly-hours").performClick();tag("weekly-period-1").performScrollTo().performClick()
        // Back dismisses the editor through its own guard; cancelling keeps the draft.
        compose.onNode(hasText("Voltar") and hasAnyAncestor(isDialog()),useUnmergedTree=true).performScrollTo().performClick()
        compose.onNodeWithText("Cancelar").performClick();tag("weekly-period-1").assertIsSelected()
        assertEquals(before,runBlocking{app.backupManager.snapshot()})
        compose.onNode(hasText("Voltar") and hasAnyAncestor(isDialog()),useUnmergedTree=true).performScrollTo().performClick();compose.onNodeWithText("Confirmar").performClick()
        tag("class-mode-Chamada").performClick();waitTag("attendance-calendar");assertEquals(before,runBlocking{app.backupManager.snapshot()})
    }
    @Test fun annualEditOverridesFutureRevisionsWithCompactConfirmationAndKeepsAcademicData() {
        seed()
        runBlocking{app.repository.confirmTimetable(app.repository.previewTimetable(2026,"2026-11-01",emptyList()))}
        val before=runBlocking{app.backupManager.snapshot()}
        weekly();tag("weekday-4").performClick();tag("add-weekly-hours").performClick()
        tag("weekly-period-3").performScrollTo().performClick()
        tag("timetable-effective").assertDoesNotExist()
        tag("timetable-scope-TODAY").assertExists();tag("timetable-scope-YEAR").performScrollTo().performClick()
        shot("09-alcance-anual")
        tag("review-weekly-hours").performScrollTo().performClick()
        compose.onNodeWithText("Esta grade substituirá também as previsões posteriores de 2026 (1 vigência).").assertExists()
        shot("10-confirmacao-anual")
        tag("save-weekly-hours").performScrollTo().performClick()
        compose.waitUntil(10000){runBlocking{app.database.backupDao().timetables().size==4}}
        val after=runBlocking{app.backupManager.snapshot()}
        assertEquals(before,after.copy(timetables=before.timetables));assertTrue(after.timetables.containsAll(before.timetables))
        assertEquals(3,Timetables.planned(vm.data.value,c.id,"2026-12-03").size)
        compose.activityRule.scenario.recreate();waitTag("weekly-timetable")
        assertEquals(after,runBlocking{app.backupManager.snapshot()})
    }
    @Test fun todayScopeShowsEffectiveDateAndSavesWithoutAnotherAdministrativeStep() {
        seed();weekly();tag("weekday-4").performClick();tag("add-weekly-hours").performClick()
        tag("timetable-scope-TODAY").performScrollTo().performClick()
        compose.onNodeWithText("Desde ${shortDate(today())}").assertExists()
        val before=runBlocking{app.backupManager.snapshot()}
        tag("weekly-period-3").performScrollTo().performClick()
        tag("review-weekly-hours").performScrollTo().performClick()
        compose.waitUntil(10000){runBlocking{app.database.backupDao().timetables().size==2}}
        val after=runBlocking{app.backupManager.snapshot()}
        assertEquals(before,after.copy(timetables=before.timetables))
        assertEquals(today(),after.timetables.first{it.id !in before.timetables.map{v->v.id}}.effective)
    }

}
