package br.com.diariodeclasse

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.diariodeclasse.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class FrequencyUiInstrumentedTest {
    @get:Rule(order=0) val compose=createAndroidComposeRule<MainActivity>()
    @get:Rule(order=1) val entry=enterNoctuaRule(compose)
    private lateinit var app:DiaryApplication
    private var previous:AppSelection?=null
    private val c=SchoolClass(id="freq-ui-a",name="Turma Fictícia Chamada",subject="História",year=2026)
    private val other=c.copy(id="freq-ui-b",name="Turma Fictícia Destino")
    private val a=Student(id="freq-ui-student",classId=c.id,name="Estudante Fictício Alfa",number=1,joined="2026-02-01")
    private val late=a.copy(id="freq-ui-late",name="Estudante Fictício Tardio",number=2,joined="2026-10-01")
    private val day="2026-08-06"
    @Before fun setup()=runBlocking {
        app=compose.activity.application as DiaryApplication;previous=app.selectionStore.read()
        app.commands.awaitPendingWrites()
        app.repository.saveClass(c,"2026-10-16");app.repository.saveClass(other,"2026-01-01")
        app.repository.saveStudent(a);app.repository.saveStudent(late);app.selectionStore.write(AppSelection(2026,c.id))
        compose.waitUntil(10000){compose.onAllNodesWithText(c.name).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("nav-1").performClick();waitText("Mês do ano letivo 2026")
    }
    @After fun cleanup()=runBlocking {
        app.commands.awaitPendingWrites()
        val sql=app.database.openHelper.writableDatabase
        app.database.runInTransaction {
            for(id in listOf(c.id,other.id)) {
                sql.execSQL("DELETE FROM marks WHERE lessonId IN (SELECT id FROM lessons WHERE classId=?)",arrayOf(id))
                sql.execSQL("DELETE FROM grades WHERE activityId IN (SELECT id FROM activities WHERE classId=?)",arrayOf(id))
                for(t in listOf("import_drafts","activities","lessons","terms","students"))sql.execSQL("DELETE FROM $t WHERE classId=?",arrayOf(id))
                sql.execSQL("DELETE FROM classes WHERE id=?",arrayOf(id))
            }
        }
        app.selectionStore.write(previous)
    }
    private fun waitText(value:String){compose.waitUntil(10000){compose.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty()}}
    private fun waitTag(value:String){compose.waitUntil(10000){compose.onAllNodesWithTag(value).fetchSemanticsNodes().isNotEmpty()}}
    private fun openAugust(){
        val now=LocalDate.now()
        compose.onNodeWithText("${now.monthValue} • ${now.month.getDisplayName(TextStyle.FULL,Locale.forLanguageTag("pt-BR"))} ▾").performClick()
        compose.onAllNodesWithText("8 • agosto").onLast().performScrollTo().performClick()
        compose.onNodeWithTag("attendance-calendar").performScrollToNode(hasTestTag("date-$day"))
        compose.onNodeWithTag("date-$day").performScrollTo().performClick();waitTag("attendance-day-$day")
    }
    private fun shot(name:String){val folder=File(app.filesDir,"etapa3-screenshots");folder.mkdirs();File(folder,"$name.png").outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}}
    @Test fun retroactiveTwoHoursIndependentCellsFixedNamesAndClassSwitch()=runBlocking {
        openAugust();compose.onNodeWithTag("hours-choice-2").performClick();compose.onNodeWithText("Registrar horários").performScrollTo().performClick()
        waitTag("daily-frequency");val lessons=app.repository.dao.day(c.id,day)
        compose.onNodeWithTag("cell-${lessons[0].id}-${a.id}-P").performClick()
        val left=compose.onNodeWithTag("student-name-${a.id}").fetchSemanticsNode().boundsInRoot.left
        compose.onNodeWithTag("cell-${lessons[1].id}-${a.id}-F").performScrollTo().performClick()
        compose.waitUntil(5000){runBlocking{app.repository.dao.attendance(lessons[1].id).any {it.studentId==a.id&&it.mark=="F"}}}
        assertEquals(left,compose.onNodeWithTag("student-name-${a.id}").fetchSemanticsNode().boundsInRoot.left,0.1f)
        assertTrue(app.repository.dao.attendance(lessons[1].id).none {it.studentId==late.id})
        waitText("Salvo no aparelho");shot("01-chamada-dupla")
        compose.activityRule.scenario.recreate();waitTag("daily-frequency")
        compose.onNodeWithTag("class-selector").performClick();compose.onNodeWithText(other.name).performClick();waitText("Mês do ano letivo 2026")
        assertTrue(app.database.backupDao().lessons().none {it.classId==other.id})
        compose.onNodeWithTag("class-selector").performClick();compose.onNodeWithText(c.name).performClick();waitTag("daily-frequency")
        assertEquals(lessons,app.repository.dao.day(c.id,day))
        assertEquals("P",app.repository.dao.attendance(lessons[0].id).single().mark)
        assertEquals("F",app.repository.dao.attendance(lessons[1].id).single().mark)
    }
    @Test fun allPresentThenJustifyAndCancelPreservesOldMark()=runBlocking {
        app.repository.setDayHours(c.id,day,2);openAugust();waitTag("daily-frequency")
        compose.onNodeWithText("Marcar todos como presentes").performClick();compose.onNodeWithText("Confirmar").performClick()
        compose.waitUntil(5000){runBlocking{app.database.backupDao().marks().count {it.studentId==a.id}==2}}
        val first=app.repository.dao.day(c.id,day).first()
        compose.onNodeWithTag("cell-menu-${first.id}-${a.id}").performScrollTo().performClick()
        compose.onNodeWithText("FJ — falta justificada").performClick()
        compose.waitUntil(5000){runBlocking{app.repository.dao.attendance(first.id).single().mark=="FJ"}}
        compose.onNodeWithTag("hour-options-1").performScrollTo().performClick()
        compose.onNodeWithText("Cancelar horário").performScrollTo().performClick()
        compose.onNodeWithText("Confirmar cancelamento").performScrollTo().performClick()
        compose.waitUntil(5000){runBlocking{app.repository.dao.lesson(first.id)!!.state=="CANCELADA"}}
        assertEquals("FJ",app.repository.dao.attendance(first.id).single().mark)
        shot("02-cancelamento-preservado")
    }
    @Test fun termGridChronologicalAndCalendarNavigationDoNotMutateSchoolData()=runBlocking {
        app.repository.setDayHours(c.id,"2026-08-05",1);app.repository.setDayHours(c.id,day,2)
        val earlier=app.repository.dao.day(c.id,"2026-08-05").single();val later=app.repository.dao.day(c.id,day)
        app.repository.mark(earlier.id,a.id,"FJ");app.repository.mark(later[1].id,a.id,"F")
        val before=app.backupManager.snapshot().copy(selection=null)
        compose.onNodeWithTag("frequency-mode-Bimestre").performClick();compose.onNodeWithText("3º bim.").performClick();waitTag("term-frequency")
        compose.onNodeWithText("05/08\n1º").assertExists();compose.onNodeWithText("06/08\n1º").assertExists()
        val left=compose.onNodeWithTag("student-name-${a.id}").fetchSemanticsNode().boundsInRoot.left
        compose.onNodeWithTag("frequency-total-${a.id}").performScrollTo().assertTextEquals("1 / 1")
        assertEquals(left,compose.onNodeWithTag("student-name-${a.id}").fetchSemanticsNode().boundsInRoot.left,0.1f)
        shot("03-frequencia-bimestral")
        assertEquals(before,app.backupManager.snapshot().copy(selection=null))
    }
    @Test fun defaultRegistrationAllowsRetroactiveButtonsCollectivePresenceAndLiveReports()=runBlocking {
        val name="Estudante Fictício Regular"
        compose.onNodeWithTag("nav-0").performClick()
        compose.onNodeWithText("Gerenciar alunos").performScrollTo().performClick();waitText("+ Adicionar aluno")
        compose.onNodeWithText("+ Adicionar aluno").performClick()
        compose.onNodeWithText("Nome completo").performTextInput(name)
        compose.onNodeWithText("Ingresso (dd/mm/aaaa)").assertTextContains("01/01/2026")
        compose.onNodeWithText("Salvar aluno").performScrollTo().performClick()
        compose.waitUntil(5000){runBlocking{app.repository.dao.roster(c.id).any {it.name==name}}}
        val regular=app.repository.dao.roster(c.id).single {it.name==name}
        assertEquals("2026-01-01",regular.joined)
        assertTrue(app.database.backupDao().lessons().isEmpty());assertTrue(app.database.backupDao().marks().isEmpty())
        compose.onNodeWithTag("nav-1").performClick();waitText("Mês do ano letivo 2026");openAugust()
        compose.onNodeWithTag("hours-choice-2").performClick();compose.onNodeWithText("Registrar horários").performScrollTo().performClick();waitTag("daily-frequency")
        val lessons=app.repository.dao.day(c.id,day)
        compose.onNodeWithText("Marcar todos como presentes").performClick();compose.onNodeWithText("Confirmar").performClick()
        compose.waitUntil(5000){runBlocking{app.database.backupDao().marks().count {it.studentId==regular.id}==2}}
        compose.onNodeWithTag("frequency-students").performScrollToNode(hasTestTag("student-name-${late.id}"))
        compose.onNodeWithTag("cell-${lessons[0].id}-${late.id}-F").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("frequency-students").performScrollToNode(hasTestTag("student-name-${regular.id}"))
        compose.onNodeWithTag("cell-${lessons[0].id}-${regular.id}-P").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("cell-${lessons[1].id}-${regular.id}-F").performScrollTo().performClick()
        compose.waitUntil(5000){runBlocking{app.repository.dao.attendance(lessons[1].id).any {it.studentId==regular.id&&it.mark=="F"}}}
        assertTrue(app.database.backupDao().marks().none {it.studentId==late.id})
        assertEquals(late,app.repository.dao.student(late.id));waitText("Salvo no aparelho")
        shot("04-matricula-retroativa")
        fun openProfileReport(){
            compose.onNodeWithTag("nav-3").performClick();waitText("Boletim 3º Bim")
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("${regular.number}. $name"))
            compose.onNodeWithText("${regular.number}. $name").performClick()
        }
        openProfileReport();compose.waitUntil(5000){compose.onAllNodesWithText("Faltas: 1 • justificadas: 0",substring=true).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Presenças: 1 • frequência: 50,00%",substring=true).assertExists()
        compose.onNodeWithTag("nav-1").performClick();waitTag("daily-frequency")
        compose.onNodeWithTag("frequency-students").performScrollToNode(hasTestTag("student-name-${regular.id}"))
        compose.onNodeWithTag("cell-${lessons[1].id}-${regular.id}-P").performScrollTo().performClick()
        compose.waitUntil(5000){runBlocking{app.repository.dao.attendance(lessons[1].id).any {it.studentId==regular.id&&it.mark=="P"}}}
        waitText("Salvo no aparelho");compose.activityRule.scenario.recreate();waitTag("daily-frequency")
        assertEquals(lessons,app.repository.dao.day(c.id,day))
        assertEquals("P",app.repository.dao.attendance(lessons[1].id).single {it.studentId==regular.id}.mark)
        openProfileReport();compose.waitUntil(5000){compose.onAllNodesWithText("Faltas: 0 • justificadas: 0",substring=true).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Presenças: 2 • frequência: 100,00%",substring=true).assertExists()
        Unit
    }
    private fun changeHours(n:Int) {
        compose.onNodeWithText("Horários:",substring=true).performClick()
        compose.onNodeWithText("Horários ministrados (0 a 12)").performTextReplacement(n.toString())
        compose.onNodeWithText("Aplicar horários").performScrollTo().performClick()
    }
    @Test fun cancelledHoursReactivateOnlyAfterConfirmationAndKeepBothMarks()=runBlocking {
        app.repository.setDayHours(c.id,day,2)
        val lessons=app.repository.dao.day(c.id,day)
        app.repository.mark(lessons[0].id,a.id,"P");app.repository.mark(lessons[1].id,a.id,"FJ")
        openAugust();waitTag("daily-frequency")
        changeHours(0);waitText("Cancelar horários excedentes?")
        compose.onNodeWithText("Confirmar").performClick();waitText("Horários: 0 • Alterar")
        val cancelled=app.backupManager.snapshot()
        changeHours(2);waitText("Reativar 2 horários cancelados?")
        compose.onNodeWithText("Não").performClick()
        assertEquals(cancelled,app.backupManager.snapshot())
        compose.onNodeWithText("Horários ministrados (0 a 12)").performTextReplacement("1")
        compose.onNodeWithText("Aplicar horários").performScrollTo().performClick()
        compose.onNodeWithText("Reativar").performClick();waitText("Horários: 1 • Alterar")
        changeHours(2);waitText("Reativar 1 horário cancelado?")
        compose.onNodeWithText("Reativar").performClick();waitText("Horários: 2 • Alterar")
        assertEquals(lessons,app.repository.dao.day(c.id,day));assertEquals(cancelled.marks,app.backupManager.snapshot().marks)
        compose.activityRule.scenario.recreate();waitText("Horários: 2 • Alterar")
        compose.onNodeWithText("Horários:",substring=true).performClick()
        compose.onNodeWithTag("clear-day-hours").performScrollTo().performClick()
        waitText("Horários preservados")
        compose.onNodeWithText("1º horário: 1 P").assertExists()
        compose.onNodeWithText("2º horário: 1 FJ").assertExists()
        compose.onNodeWithTag("confirm-clear-day").assertDoesNotExist()
        assertNull(app.commands.attendanceError.value)
        assertNull(app.commands.error.value)
        assertEquals(cancelled.marks,app.backupManager.snapshot().marks)
    }
    @Test fun emptyDayCleaningCancelsSafelyAndRemovesNrAndCancelledCalendarMarkers()=runBlocking {
        app.repository.setDayHours(c.id,day,2);app.repository.setDayHours(other.id,day,1)
        openAugust();waitTag("daily-frequency")
        val before=app.backupManager.snapshot()
        compose.onNodeWithText("Horários:",substring=true).performClick()
        compose.onNodeWithTag("clear-day-hours").performScrollTo().performClick();waitText("Remover os horários desta data?")
        compose.onNodeWithText("Cancelar").performClick();assertEquals(before,app.backupManager.snapshot())
        compose.onNodeWithText("Voltar").performScrollTo().performClick()
        changeHours(0);compose.onNodeWithText("Confirmar").performClick();waitText("Horários: 0 • Alterar")
        compose.onNodeWithText("Horários:",substring=true).performClick()
        compose.onNodeWithTag("clear-day-hours").performScrollTo().performClick();waitText("Remover os horários desta data?")
        compose.onNodeWithText("Remover").performClick();waitText("Quantos horários foram ministrados?")
        val after=app.backupManager.snapshot()
        assertEquals(before.copy(lessons=before.lessons.filterNot{it.classId==c.id&&it.date==day}),after)
        compose.onNodeWithText("‹ Calendário").performClick();waitTag("attendance-calendar")
        compose.onNodeWithTag("attendance-calendar").performScrollToNode(hasTestTag("date-$day"))
        compose.onNodeWithTag("date-$day").assertContentDescriptionEquals("06/08/2026, sem aulas")
        compose.activityRule.scenario.recreate();waitTag("attendance-calendar")
        assertTrue(app.repository.dao.day(c.id,day).isEmpty());assertEquals(1,app.repository.dao.day(other.id,day).size)
    }

}
