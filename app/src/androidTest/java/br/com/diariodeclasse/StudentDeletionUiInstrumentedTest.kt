package br.com.diariodeclasse

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import br.com.diariodeclasse.backup.diarySnapshot
import br.com.diariodeclasse.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

class StudentDeletionUiInstrumentedTest {
    @get:Rule(order=0) val compose=createAndroidComposeRule<MainActivity>()
    @get:Rule(order=1) val entry=enterNoctuaRule(compose)
    private lateinit var app:DiaryApplication
    private var previous:AppSelection?=null
    private val g=SchoolClass("delete-ui-g",name="1º G fictício Exclusão",subject="História",year=2026)
    private val h=g.copy(id="delete-ui-h",name="1º H fictício Exclusão")
    private val kept=Student("delete-ui-kept","delete-ui-person-kept",g.id,"Estudante Fictício Homônimo Completo",1,"2026-01-01")
    private val wrong=kept.copy(id="delete-ui-wrong",personId="delete-ui-person-wrong",classId=h.id)
    @Before fun setup() {
        app=compose.activity.application as DiaryApplication
        runBlocking {
            previous=app.selectionStore.read()
            for((c,s) in listOf(g to kept,h to wrong)) {
                app.repository.saveClass(c,"2026-01-01");app.repository.saveStudent(s)
                app.repository.saveActivity(Activity("activity-${c.id}",c.id,"Atividade fictícia",date="2026-08-10",term=3,maximum=10.0))
                app.repository.saveGrade("activity-${c.id}",s.id,0.0)
                app.repository.setDayHours(c.id,"2026-08-10",1);app.repository.mark("${c.id}:2026-08-10:1",s.id,"FJ")
                app.repository.saveRecomposition(Recomposition(s.id,3,5.0,"2026-08-20"))
            }
            app.selectionStore.write(AppSelection(2026,h.id))
        }
        compose.onNodeWithTag("nav-0").performClick()
        compose.onNodeWithText("Gerenciar alunos").performScrollTo().performClick()
        compose.waitUntil(10000){compose.onAllNodesWithTag("roster-list").fetchSemanticsNodes().isNotEmpty()}
    }
    @After fun cleanup()=runBlocking {
        app.commands.awaitPendingWrites()
        for(id in listOf(h.id,g.id))if(app.repository.dao.schoolClass(id)!=null){val p=app.repository.previewClassDeletion(id);app.repository.deleteClass(p,p.confirmation)}
        app.selectionStore.write(previous)
    }
    private fun tag(s:String)=compose.onNodeWithTag(s)
    private fun edit(){tag("roster-list").performScrollToNode(hasTestTag("roster-student-${wrong.id}"));compose.onNode(hasText("Editar") and hasAnyAncestor(hasTestTag("roster-student-${wrong.id}"))).performClick()}
    private fun preview(){tag("student-delete").performScrollTo().performClick();compose.waitUntil(10000){compose.onAllNodesWithTag("student-delete-count-Registros de frequência").fetchSemanticsNodes().isNotEmpty()}}
    private fun shot(name:String){val dir=File(app.filesDir,"student-deletion-screenshots");dir.mkdirs();val root=if(compose.onAllNodesWithTag("student-delete-dialog").fetchSemanticsNodes().isNotEmpty())tag("student-delete-dialog")else compose.onNode(isDialog());File(dir,"$name.png").outputStream().use{root.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}}
    @Test fun newStudentHasNoDeleteAndCancelOrAndroidBackPreservesEveryRow() {
        val before=runBlocking{app.backupManager.snapshot()}
        compose.onNodeWithText("+ Adicionar aluno").performScrollTo().performClick();tag("student-delete").assertDoesNotExist()
        compose.onNodeWithText("Voltar").performScrollTo().performClick()
        edit();tag("student-save").performScrollTo().assertIsDisplayed();tag("student-delete").assertExists();shot("01-editar-aluno")
        preview();compose.onNode(hasText(wrong.name) and hasAnyAncestor(hasTestTag("student-delete-diagnosis"))).assertExists();compose.onNodeWithText("${h.name} • História • 2026").assertExists()
        tag("student-delete-count-Atividades com notas lançadas").assertTextEquals("Atividades com notas lançadas: 1")
        tag("student-delete-count-Registros de frequência").assertTextEquals("Registros de frequência: 1")
        tag("student-delete-count-Recomposições com nota").assertTextEquals("Recomposições com nota: 1")
        shot("02-diagnostico-e-confirmacao");tag("student-delete-cancel").performClick();tag("student-delete-dialog").assertDoesNotExist()
        assertEquals(before,runBlocking{app.backupManager.snapshot()})
        preview()
        val automation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        val now=android.os.SystemClock.uptimeMillis()
        automation.injectInputEvent(android.view.KeyEvent(now,now,android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.KEYCODE_BACK,0),true)
        automation.injectInputEvent(android.view.KeyEvent(now,now,android.view.KeyEvent.ACTION_UP,android.view.KeyEvent.KEYCODE_BACK,0),true)
        compose.waitUntil(10000){compose.onAllNodesWithTag("student-delete-dialog").fetchSemanticsNodes().isEmpty()}
        assertEquals(before,runBlocking{app.backupManager.snapshot()})
    }
    @Test fun confirmedDeleteRefreshesRosterAndReportsAndKeepsIndependentHomonymAfterRecreation() {
        val before=runBlocking{app.backupManager.snapshot()};edit();preview();tag("student-delete-confirm").performClick()
        compose.waitUntil(10000){runBlocking{app.repository.dao.student(wrong.id)==null}}
        compose.waitUntil(10000){compose.onAllNodesWithTag("student-delete-dialog").fetchSemanticsNodes().isEmpty()}
        compose.waitUntil(10000){compose.onAllNodesWithText("Aluno excluído desta turma.").fetchSemanticsNodes().isNotEmpty()}
        tag("student-delete-dialog").assertDoesNotExist();tag("roster-student-${wrong.id}").assertDoesNotExist()
        assertEquals(ClassLifecycle.fingerprint(StudentDeletion.without(before.diarySnapshot(),wrong.id)),ClassLifecycle.fingerprint(runBlocking{app.backupManager.snapshot().diarySnapshot()}))
        compose.activityRule.scenario.recreate();tag("roster-list").assertExists();tag("roster-student-${wrong.id}").assertDoesNotExist()
        tag("class-selector").performClick();compose.onNodeWithText(g.name,useUnmergedTree=true).performClick()
        tag("roster-list").performScrollToNode(hasTestTag("roster-student-${kept.id}"));tag("roster-student-${kept.id}").assertIsDisplayed()
        tag("nav-3").performClick();compose.onNodeWithText("3º bim.").performClick()
        tag("reports-list").performScrollToNode(hasTestTag("report-row-${kept.id}"))
        compose.onNodeWithTag("report-row-${kept.id}-current",useUnmergedTree=true).onChild().assertTextEquals("5,00")
        assertEquals(kept,runBlocking{app.repository.dao.student(kept.id)})
    }
    @Test fun sharedExternalMoveExplainsBlockAndNeverOffersAnEnabledConfirmation() {
        runBlocking{app.repository.confirmRelocation(app.repository.previewRelocation(wrong.id,null,"Destino externo fictício","2026-09-01"))}
        compose.waitUntil(10000){compose.onAllNodesWithTag("show-relocated").fetchSemanticsNodes().isNotEmpty()}
        tag("show-relocated").performScrollTo().performClick();edit();preview()
        tag("student-delete-confirm").assertIsNotEnabled()
        compose.onNodeWithText("Esta matrícula participa",substring=true).performScrollTo().assertIsDisplayed()
        val before=runBlocking{app.backupManager.snapshot()};tag("student-delete-cancel").performClick()
        assertEquals(before,runBlocking{app.backupManager.snapshot()})
    }
    @Test fun deleteFromStudentProfileReturnsToExistingRosterInsteadOfAnEmptyProfile() {
        tag("roster-list").performScrollToNode(hasTestTag("roster-student-${wrong.id}"));tag("roster-student-${wrong.id}").performClick()
        compose.onNodeWithText("Editar cadastro e observações").performClick();preview();tag("student-delete-confirm").performClick()
        compose.waitUntil(10000){compose.onAllNodesWithTag("roster-list").fetchSemanticsNodes().isNotEmpty()}
        tag("roster-student-${wrong.id}").assertDoesNotExist();assertEquals(kept,runBlocking{app.repository.dao.student(kept.id)})
    }
}
