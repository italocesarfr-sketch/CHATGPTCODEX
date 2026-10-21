package br.com.diariodeclasse

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.ui.*
import br.com.diariodeclasse.ui.theme.NoctuaTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

class FunctionalFifteenUiInstrumentedTest {
    @get:Rule val compose=createComposeRule()
    private lateinit var app:DiaryApplication
    private lateinit var vm:DiaryViewModel
    private val store=androidx.lifecycle.ViewModelStore()
    private val c=SchoolClass("ui-fifteen-${newId()}",name="Turma Fictícia Quinze",subject="História",year=2026)
    private val s=Student("ui-fifteen-student-${newId()}","ui-fifteen-person",c.id,"Estudante Fictícia Quinze",1,"2026-01-01")
    private val l=Lesson("ui-fifteen-lesson-${newId()}",c.id,"2026-10-20",1,"REALIZADA")
    private var screen by mutableStateOf("student")
    private var closed by mutableStateOf(false)
    private var font by mutableStateOf(1f)
    @Before fun setup()=runBlocking {
        app=ApplicationProvider.getApplicationContext();vm=DiaryViewModel(app);store.put("fifteen",vm)
        app.repository.saveClass(c,"2026-01-01");app.repository.saveStudent(s);app.repository.dao.save(l)
        app.repository.mark(l.id,s.id,"FJ")
        app.repository.confirmRelocation(app.repository.previewRelocation(s.id,null,"foi pro noturno","2026-10-25"))
        compose.setContent {
            val d by vm.data.collectAsState();val density=LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density,font)) {
                NoctuaTheme {Surface(Modifier.widthIn(max=360.dp).fillMaxSize()) {
                    when {
                        closed -> androidx.compose.material3.Text("Concluído")
                        screen=="student" -> if(d.loaded)StudentForm(vm,c,d.students.first{it.id==s.id}){closed=true}
                        screen=="day" -> DayAttendanceScreen(vm,d,c,l.date,{}, {})
                        screen=="export" -> ExportScreen(d,c,4,plans={screen="plans"}){}
                        screen=="plans" -> AttendanceExportScreen(d,c,4,{},true,plans=true){}
                    }
                } }
            }
        }
        compose.waitUntil(10000){vm.data.value.loaded}
    }
    @After fun cleanup()=runBlocking {
        store.clear();val preview=app.repository.previewClassDeletion(c.id);app.repository.deleteClass(preview,preview.confirmation)
    }
    private fun click(tag:String){compose.onNodeWithTag(tag).performScrollTo().performClick()}
    private fun shot(name:String){
        val folder=File(app.filesDir,"functional15-screenshots").apply{mkdirs()}
        val root=if(compose.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty())compose.onNode(isDialog()) else compose.onRoot()
        File(folder,"$name.png").outputStream().use{root.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun correctionShowsAffectedFJAndRequiresExplicitConfirmation(){
        val before=runBlocking{app.backupManager.snapshot()}
        click("correct-relocation")
        compose.onNodeWithTag("correction-date").performScrollTo().performTextReplacement("17/10/2026")
        click("preview-correction")
        compose.waitUntil(10000){compose.onAllNodesWithTag("remove-correction-${l.id}").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("20/10/2026 • 1º horário • FJ").performScrollTo().assertExists()
        shot("01-previa-correção")
        click("remove-correction-${l.id}");click("preview-correction")
        compose.waitUntil(10000){compose.onNodeWithTag("save-correction").fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled).not()}
        click("save-correction");compose.onNodeWithText("Confirmar correção administrativa?").assertExists()
        compose.onNodeWithText("Cancelar",useUnmergedTree=true).performClick();assertEquals(before,runBlocking{app.backupManager.snapshot()})
        click("save-correction");compose.onNodeWithText("Confirmar",useUnmergedTree=true).performClick()
        compose.waitUntil(10000){closed}
        val after=runBlocking{app.backupManager.snapshot()};assertTrue(after.marks.none{it.lessonId==l.id&&it.studentId==s.id});assertEquals("Remanejado",after.students.first{it.id==s.id}.status)
    }
    @Test fun nrCleanupDiagnosesHourCancelsAndRemovesOnlyAfterConfirmation(){
        runBlocking{app.repository.dao.save(Attendance(l.id,s.id,"NR"))};compose.runOnIdle{screen="day";font=1.3f}
        compose.onNodeWithText("Horários: 1 • Alterar").performScrollTo().performClick();click("clear-day-hours")
        compose.waitUntil(10000){compose.onAllNodesWithTag("confirm-clear-day").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("1º horário: 1 NR").assertExists();shot("02-diagnostico-nr")
        compose.onNodeWithText("Cancelar",useUnmergedTree=true).performClick();assertEquals(1,runBlocking{app.repository.dao.day(c.id,l.date).size})
        click("clear-day-hours");compose.waitUntil(10000){compose.onAllNodesWithTag("confirm-clear-day").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("confirm-clear-day").performClick()
        compose.waitUntil(10000){runBlocking{app.repository.dao.day(c.id,l.date).isEmpty()}}
        assertTrue(runBlocking{app.repository.dao.attendance(l.id).isEmpty()})
    }
    @Test fun independentPlanExportNavigationPeriodAndEmptyState(){
        runBlocking{app.repository.saveLessonRecord(LessonRecord("ui-fifteen-plan-${newId()}",c.id,"2026-10-17","Título fictício","Descrição exata\nCom acentuação."))}
        compose.runOnIdle{screen="export"}
        click("export-other");compose.onNodeWithTag("open-plans-export").performClick()
        compose.onNodeWithTag("plans-export-screen").assertExists()
        compose.onNodeWithText("Descrição exata\nCom acentuação.").performScrollTo().assertExists();shot("03-planos-xlsx")
        compose.onNodeWithText("4 ▾").performScrollTo().performClick();compose.onNodeWithText("1",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Nenhum plano de aula neste período.").performScrollTo().assertExists()
        compose.onNodeWithTag("attendance-export-file").performScrollTo().assertIsNotEnabled()
    }
}
