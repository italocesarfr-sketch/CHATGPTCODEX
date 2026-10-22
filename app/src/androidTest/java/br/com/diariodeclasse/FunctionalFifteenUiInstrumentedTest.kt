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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
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
    private val destination=c.copy(id="ui-correction-target-${newId()}",name="Turma Fictícia Destino")
    private var screen by mutableStateOf("student")
    private var closed by mutableStateOf(false)
    private var font by mutableStateOf(1f)
    @Before fun setup()=runBlocking {
        app=ApplicationProvider.getApplicationContext();vm=DiaryViewModel(app);store.put("fifteen",vm)
        app.repository.saveClass(c,"2026-01-01");app.repository.saveStudent(s);app.repository.dao.save(l)
        app.repository.mark(l.id,s.id,"FJ")
        app.repository.confirmRelocation(app.repository.previewRelocation(s.id,null,"foi pro noturno","2026-10-25"))
        withTimeout(10000) { vm.data.first { it.loaded && it.relocations.any { link -> link.sourceStudentId == s.id } } }
        compose.setContent {
            val d by vm.data.collectAsState();val density=LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density,font)) {
                NoctuaTheme {Surface(Modifier.widthIn(max=360.dp).fillMaxSize()) {
                    when {
                        closed -> androidx.compose.material3.Text("Concluído")
                        screen=="student" -> if(d.loaded)StudentForm(vm,c,d.students.first{it.id==s.id}){closed=true}
                        screen=="day" -> DayAttendanceScreen(vm,d,c,l.date,{}, {})
                        screen=="export" -> ExportScreen(d,c,4){}
                        screen=="plans" -> AttendanceExportScreen(d,c,4,{},true,plans=true){}
                    }
                } }
            }
        }
        compose.waitUntil(10000){vm.data.value.loaded}
    }
    @After fun cleanup()=runBlocking {
        store.clear();if(app.repository.dao.schoolClass(destination.id)!=null) {val extra=app.repository.previewClassDeletion(destination.id);app.repository.deleteClass(extra,extra.confirmation)};val preview=app.repository.previewClassDeletion(c.id);app.repository.deleteClass(preview,preview.confirmation)
    }
    private fun click(tag:String){compose.onNodeWithTag(tag).performScrollTo().performClick()}
    private fun pressBack() {
        val automation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        val now=android.os.SystemClock.uptimeMillis()
        automation.injectInputEvent(android.view.KeyEvent(now,now,android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.KEYCODE_BACK,0),true)
        automation.injectInputEvent(android.view.KeyEvent(now,now,android.view.KeyEvent.ACTION_UP,android.view.KeyEvent.KEYCODE_BACK,0),true)
        compose.waitForIdle()
    }
    private fun shot(name:String, dialogTitle:String?=null){
        val folder=File(app.filesDir,"functional15-screenshots").apply{mkdirs()}
        val root=if(dialogTitle!=null) compose.onNode(isDialog() and hasAnyDescendant(hasText(dialogTitle))) else if(compose.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty())compose.onNode(isDialog()) else compose.onRoot()
        File(folder,"$name.png").outputStream().use{root.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun correctionShowsAffectedFJAndRequiresExplicitConfirmation(){
        val before=runBlocking{app.backupManager.snapshot()}
        click("correct-relocation")
        compose.onNodeWithTag("correction-date").performScrollTo().performTextReplacement("17/10/2026")
        compose.onNodeWithText("Corrigir").assertExists()
        click("preview-correction")
        compose.waitUntil(10000){compose.onAllNodesWithTag("remove-correction-${l.id}").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("20/10/2026 • 1º horário • FJ").performScrollTo().assertIsDisplayed()
        shot("01-previa-correção")
        click("remove-correction-${l.id}")
        compose.onNodeWithText("Conferir correção").assertDoesNotExist()
        compose.waitUntil(10000){compose.onNodeWithTag("save-correction").fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled).not()}
        compose.onNodeWithText("Editar data e destino").assertDoesNotExist()
        compose.onNodeWithText("Destino: foi pro noturno").assertExists()
        click("save-correction");compose.onNodeWithText("Confirmar correção?").assertExists()
        compose.onNodeWithText("Retirar: 1 FJ.",substring=true).assertExists()
        compose.onNodeWithText("Cancelar",useUnmergedTree=true).performClick();assertEquals(before,runBlocking{app.backupManager.snapshot()})
        click("save-correction");compose.onNodeWithText("Confirmar",useUnmergedTree=true).performClick()
        compose.waitUntil(10000){closed}
        val after=runBlocking{app.backupManager.snapshot()};assertTrue(after.marks.none{it.lessonId==l.id&&it.studentId==s.id});assertEquals("Remanejado",after.students.first{it.id==s.id}.status)
    }

    private fun waitReviewReady() {
        compose.waitUntil(10000) { compose.onAllNodesWithTag("correction-checking").fetchSemanticsNodes().isEmpty() &&
            compose.onAllNodesWithTag("save-correction").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun backKeepsDateDestinationAndExplicitSelectionWithLargeFont() {
        compose.runOnIdle {font=1.3f}
        val before=runBlocking{app.backupManager.snapshot()}
        click("correct-relocation")
        compose.onNodeWithTag("correction-date").performScrollTo().performTextReplacement("17/10/2026")
        compose.onNodeWithTag("correction-target").performScrollTo().performTextReplacement("Destino externo corrigido")
        click("preview-correction");waitReviewReady()
        compose.onNodeWithText("20/10/2026 • 1º horário • FJ").performScrollTo().assertIsDisplayed()
        click("remove-correction-${l.id}");waitReviewReady()
        compose.onNodeWithTag("save-correction").performScrollTo().assertIsEnabled()
        click("save-correction")
        compose.onNodeWithText("Confirmar",useUnmergedTree=true).assertIsDisplayed()
        compose.onNodeWithText("Cancelar",useUnmergedTree=true).assertIsDisplayed().performClick()
        compose.onNodeWithText("Voltar").performScrollTo().performClick()
        compose.onNodeWithTag("correction-date").assertTextContains("17/10/2026")
        compose.onNodeWithTag("correction-target").assertTextContains("Destino externo corrigido")
        click("preview-correction");waitReviewReady()
        compose.onNodeWithTag("remove-correction-${l.id}").performScrollTo().assertIsOn()
        compose.onNodeWithTag("save-correction").performScrollTo().assertIsEnabled()
        assertEquals(before,runBlocking{app.backupManager.snapshot()})
        shot("04-previa-fonte-ampliada")
        compose.onNodeWithText("Voltar").performScrollTo().performClick()
        runBlocking{app.repository.dao.save(Attendance(l.id,s.id,"F"))}
        compose.waitUntil(10000){vm.data.value.marks.any{it.lessonId==l.id&&it.studentId==s.id&&it.mark=="F"}}
        click("preview-correction");waitReviewReady()
        compose.onNodeWithTag("remove-correction-${l.id}").performScrollTo().assertIsOff()
        compose.onNodeWithTag("save-correction").performScrollTo().assertIsNotEnabled()
        assertEquals("F",runBlocking{app.repository.dao.attendance(l.id).first{it.studentId==s.id}.mark})
    }
    @Test fun registeredDestinationPreviewsConversionAndExternalDraftIsRetained() {
        runBlocking{app.repository.saveClass(destination,"2026-01-01")}
        compose.waitUntil(10000){vm.data.value.classes.any{it.id==destination.id}}
        val before=runBlocking{app.backupManager.snapshot()}
        click("correct-relocation")
        compose.onNodeWithTag("correction-target").performScrollTo().performTextReplacement("Texto externo não confirmado")
        click("correction-internal");click("correction-class");compose.onNodeWithTag("correction-class-${destination.id}").performClick()
        compose.onNodeWithText("${destination.name} • ${destination.subject} • ${destination.year} • ${destination.shift}").assertExists()
        compose.onNodeWithTag("preview-correction").performScrollTo().assertIsEnabled()
        click("preview-correction");waitReviewReady()
        compose.onNodeWithText("Criar matrícula na turma de destino.").performScrollTo().assertIsDisplayed()
        click("save-correction");compose.onNodeWithText("Uma matrícula ativa será criada no destino",substring=true).assertExists()
        compose.onNodeWithText("Cancelar",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Voltar").performScrollTo().performClick()
        click("correction-external")
        compose.onNodeWithTag("correction-target").assertTextContains("Texto externo não confirmado")
        assertEquals(before,runBlocking{app.backupManager.snapshot()})
    }
    @Test fun leavingReviewReturnsToEditAndDiscardHasOneConfirmationWithoutWrites() {
        val before=runBlocking{app.backupManager.snapshot()}
        click("correct-relocation")
        compose.onNodeWithTag("correction-date").performScrollTo().performTextReplacement("17/10/2026")
        click("preview-correction");waitReviewReady()
        pressBack()
        compose.onNodeWithTag("correction-date").assertExists()
        compose.onNodeWithText("Voltar").performScrollTo().performClick()
        compose.onNodeWithText("Descartar a correção não confirmada?").assertExists()
        pressBack()
        compose.onNodeWithTag("correction-date").assertTextContains("17/10/2026")
        compose.onNodeWithText("Voltar").performScrollTo().performClick()
        compose.onNodeWithText("Confirmar",useUnmergedTree=true).performClick()
        compose.waitUntil(10000){closed}
        assertEquals(before,runBlocking{app.backupManager.snapshot()})
    }
    @Test fun concurrentMarkChangeRefreshesReviewClearsSelectionAndClosesStaleConfirmation() {
        click("correct-relocation")
        compose.onNodeWithTag("correction-date").performScrollTo().performTextReplacement("17/10/2026")
        click("preview-correction");waitReviewReady();click("remove-correction-${l.id}");waitReviewReady()
        click("save-correction");compose.onNodeWithText("Confirmar correção?").assertExists()
        runBlocking{app.repository.dao.save(Attendance(l.id,s.id,"P"))}
        val changed=runBlocking{app.backupManager.snapshot()}
        compose.waitUntil(10000){compose.onAllNodesWithText("Confirmar correção?").fetchSemanticsNodes().isEmpty()}
        waitReviewReady()
        compose.onNodeWithText("Os registros mudaram.",substring=true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("remove-correction-${l.id}").performScrollTo().assertIsOff()
        compose.onNodeWithText("20/10/2026 • 1º horário • P").assertExists()
        compose.onNodeWithTag("save-correction").performScrollTo().assertIsNotEnabled()
        assertEquals(changed,runBlocking{app.backupManager.snapshot()})
    }
    @Test fun destinationOnlyCorrectionUsesOneFinalConfirmationAndKeepsAttendance() {
        val before=runBlocking{app.backupManager.snapshot()}
        click("correct-relocation")
        compose.onNodeWithTag("correction-target").performScrollTo().performTextReplacement("Noturno corrigido")
        click("preview-correction");waitReviewReady()
        compose.onNodeWithText("Destino: foi pro noturno → Noturno corrigido").assertExists()
        click("save-correction");compose.onNodeWithText("Nenhuma marcação será retirada.",substring=true).assertExists()
        shot("05-confirmacao-unica","Confirmar correção?")
        compose.onNodeWithText("Confirmar",useUnmergedTree=true).performClick()
        compose.waitUntil(10000){closed}
        val after=runBlocking{app.backupManager.snapshot()}
        assertEquals(before.marks,after.marks);assertEquals(before.students,after.students)
        assertEquals("Noturno corrigido",after.relocations.first{it.sourceStudentId==s.id}.externalTarget)
        assertEquals(1,after.relocations.first{it.sourceStudentId==s.id}.corrections.size)
    }
    @Test fun internalConversionKeepsAuditAndWithdrawsOnlySelectedFJAfterOneConfirmation() {
        runBlocking {
            app.repository.saveClass(destination,"2026-01-01")
            app.repository.confirmRelocationCorrection(app.repository.previewRelocationCorrection(s.id,"2026-10-23","Destino externo revisado"))
        }
        compose.waitUntil(10000){vm.data.value.relocations.any{it.sourceStudentId==s.id&&it.effectiveDate=="2026-10-23"}&&vm.data.value.classes.any{it.id==destination.id}}
        val before=runBlocking{app.backupManager.snapshot()};compose.runOnIdle{font=1.3f}
        click("correct-relocation")
        compose.onNodeWithTag("correction-date").performScrollTo().performTextReplacement("17/10/2026")
        click("correction-internal");click("correction-class");compose.onNodeWithTag("correction-class-${destination.id}").performClick()
        click("preview-correction");waitReviewReady()
        compose.onNodeWithText("Destino anterior: Destino externo revisado").assertExists()
        compose.onNodeWithText("Novo destino: ${destination.name}",substring=true).assertExists()
        compose.onNodeWithText("Criar matrícula na turma de destino.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("save-correction").performScrollTo().assertIsNotEnabled()
        click("remove-correction-${l.id}");waitReviewReady()
        shot("06-previa-conversao")
        click("save-correction");compose.onNodeWithText("Confirmar",useUnmergedTree=true).assertIsDisplayed()
        compose.onNodeWithText("Cancelar",useUnmergedTree=true).performClick()
        assertEquals(before,runBlocking{app.backupManager.snapshot()})
        click("save-correction");shot("07-confirmar-conversao","Confirmar correção?")
        compose.onNodeWithText("Confirmar",useUnmergedTree=true).performClick();compose.waitUntil(10000){closed}
        val after=runBlocking{app.backupManager.snapshot()};val link=after.relocations.first{it.sourceStudentId==s.id}
        assertNull(link.externalTarget);assertNotNull(link.targetStudentId)
        val enrolled=after.students.first{it.id==link.targetStudentId}
        assertEquals(s.personId,enrolled.personId);assertEquals(destination.id,enrolled.classId);assertEquals("2026-10-17",enrolled.joined);assertEquals("Ativo",enrolled.status)
        assertEquals(before.relocations.first{it.sourceStudentId==s.id}.corrections,link.corrections.dropLast(1))
        assertEquals("CONVERSAO_INTERNA",link.corrections.last().operation)
        assertEquals("FJ",link.corrections.last().removed.single().attendance.mark)
        assertTrue(after.marks.none{it.studentId==s.id&&it.lessonId==l.id});assertTrue(after.marks.none{it.studentId==enrolled.id})
    }
    @Test fun conversionCanLinkExistingEnrollmentWithoutCreatingAnotherStudent() {
        val target=s.copy(id="ui-existing-${newId()}",classId=destination.id,joined="2026-10-25")
        runBlocking{app.repository.saveClass(destination,"2026-01-01");app.repository.saveStudent(target)}
        compose.waitUntil(10000){vm.data.value.students.any{it.id==target.id}}
        val before=runBlocking{app.backupManager.snapshot()}
        click("correct-relocation");click("correction-internal");click("correction-class")
        compose.onNodeWithTag("correction-class-${destination.id}").performClick()
        click("preview-correction");waitReviewReady()
        compose.onNodeWithText("Vincular matrícula já cadastrada.").performScrollTo().assertIsDisplayed()
        click("save-correction");compose.onNodeWithText("Confirmar",useUnmergedTree=true).performClick()
        compose.waitUntil(10000){closed}
        val after=runBlocking{app.backupManager.snapshot()}
        assertEquals(before.students,after.students);assertEquals(before.marks,after.marks)
        assertEquals(target.id,after.relocations.first{it.sourceStudentId==s.id}.targetStudentId)
        assertEquals(1,after.students.count{it.personId==s.personId&&it.classId==destination.id})
    }
    @Test fun nrCleanupDiagnosesHourCancelsAndRemovesOnlyAfterConfirmation(){
        runBlocking{app.repository.dao.save(Attendance(l.id,s.id,"NR"))};compose.runOnIdle{screen="day";font=1.3f}
        compose.onNodeWithText("Horários: 1 • Alterar").performClick();click("clear-day-hours")
        compose.waitUntil(10000){compose.onAllNodesWithTag("confirm-clear-day").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("1º horário: 1 NR").assertExists();shot("02-diagnostico-nr")
        compose.onNodeWithText("Cancelar",useUnmergedTree=true).performClick();assertEquals(1,runBlocking{app.repository.dao.day(c.id,l.date).size});assertEquals("NR",runBlocking{app.repository.dao.attendance(l.id).single().mark})
        click("clear-day-hours");compose.waitUntil(10000){compose.onAllNodesWithTag("confirm-clear-day").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("confirm-clear-day").performClick()
        compose.waitUntil(10000){runBlocking{app.repository.dao.day(c.id,l.date).isEmpty()}}
        assertTrue(runBlocking{app.repository.dao.attendance(l.id).isEmpty()})
    }
    @Test fun independentPlanExportNavigationPeriodAndEmptyState(){
        runBlocking{app.repository.saveLessonRecord(LessonRecord("ui-fifteen-plan-${newId()}",c.id,"2026-10-17","Título fictício","Descrição exata\nCom acentuação."))}
        compose.runOnIdle{screen="export"}
        click("export-type");compose.onNodeWithTag("export-type-PLANS").performClick()
        compose.onNodeWithTag("plans-export-screen").assertExists()
        click("export-show-preview")
        compose.onNodeWithText("Descrição exata\nCom acentuação.").performScrollTo().assertExists();shot("03-planos-xlsx")
        compose.onNodeWithTag("export-preview-close").performClick()
        click("export-options")
        compose.onNodeWithText("Bimestre selecionado ▾").performScrollTo().performClick()
        compose.onNode(hasText("Intervalo de datas") and hasAnyAncestor(isPopup()),useUnmergedTree=true).performClick()
        compose.onNodeWithTag("attendance-export-start").performTextReplacement("01/01/2026")
        compose.onNodeWithTag("attendance-export-end").performTextReplacement("31/03/2026")
        compose.onNodeWithTag("export-settings-done").assertIsDisplayed().performClick()
        compose.onNodeWithText("Nenhum plano de aula neste período.").performScrollTo().assertExists()
        compose.onNodeWithTag("export-file").assertIsNotEnabled()

    }
}
