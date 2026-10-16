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

@RunWith(AndroidJUnit4::class)
class ActivityChangesUiInstrumentedTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private lateinit var app:DiaryApplication
    private var previous:AppSelection?=null
    private val c=SchoolClass(id="ui-s4-class",name="Turma Fictícia Atividades",subject="História",year=2026)
    private val a=Activity(id="ui-s4-main",classId=c.id,name="Avaliação Fictícia",date="2026-08-06",term=3,maximum=10.0)
    private val empty=a.copy(id="ui-s4-empty",name="Atividade Fictícia sem Notas")
    private val students=listOf("Alfa","Beta","Gama","Delta").mapIndexed {i,n->Student(id="ui-s4-student-$i",classId=c.id,name="Estudante Fictício $n",number=i+1,joined="2026-01-01")}
    @Before fun setup()=runBlocking {
        app=compose.activity.application as DiaryApplication;app.commands.awaitPendingWrites();previous=app.selectionStore.read()
        app.repository.saveClass(c,"2026-01-01");students.forEach {app.repository.saveStudent(it)}
        app.repository.saveActivity(a);app.repository.saveActivity(empty)
        app.repository.saveGrade(a.id,students[0].id,8.0);app.repository.saveGrade(a.id,students[1].id,0.0);app.repository.saveGrade(a.id,students[2].id,null)
        app.selectionStore.write(AppSelection(2026,c.id))
        waitText(c.name);compose.onNodeWithTag("nav-2").performClick();waitText("+ Criar atividade")
        compose.onNodeWithText("3º bim.").performClick();waitText(a.name)
    }
    @After fun cleanup()=runBlocking {
        app.commands.awaitPendingWrites()
        val sql=app.database.openHelper.writableDatabase
        app.database.runInTransaction {
            sql.execSQL("DELETE FROM marks WHERE lessonId IN (SELECT id FROM lessons WHERE classId=?)",arrayOf(c.id))
            sql.execSQL("DELETE FROM grades WHERE activityId IN (SELECT id FROM activities WHERE classId=?)",arrayOf(c.id))
            for(t in listOf("import_drafts","activities","lessons","terms","students"))sql.execSQL("DELETE FROM $t WHERE classId=?",arrayOf(c.id))
            sql.execSQL("DELETE FROM classes WHERE id=?",arrayOf(c.id))
        }
        app.selectionStore.write(previous)
    }
    private fun waitText(text:String){compose.waitUntil(10000){compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
    private fun clickList(tag:String){waitText("+ Criar atividade");compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag(tag));compose.onNodeWithTag(tag).performClick()}
    private fun editMaximum(value:String){
        clickList("activity-edit-${a.id}");waitText("Editar atividade")
        compose.onNodeWithText("Pontuação máxima").performScrollTo().performTextReplacement(value)
        compose.onNodeWithText("Salvar alterações").performScrollTo().performClick();waitText("Confirmar alteração da pontuação")
    }
    private fun shot(name:String){val folder=File(app.filesDir,"etapa4a-screenshots");folder.mkdirs();File(folder,"$name.png").outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}}
    @Test fun editEveryFieldFromListWithoutOpeningGrades()=runBlocking {
        val before=app.repository.activitySnapshot(a.id)
        shot("01-acoes-na-lista");clickList("activity-edit-${a.id}");waitText("Editar atividade")
        compose.onNodeWithText("Nome da atividade").performTextReplacement("Avaliação Fictícia Editada")
        compose.onNodeWithText("Prova ▾").performScrollTo().performClick();compose.onNodeWithText("Seminário").performScrollTo().performClick()
        compose.onNodeWithText("Data (dd/mm/aaaa)").performScrollTo().performTextReplacement("20/10/2026")
        compose.onNodeWithText("3 ▾").performScrollTo().performClick();compose.onNodeWithText("4").performClick()
        compose.onNodeWithText("Descrição").performScrollTo().performTextReplacement("Descrição fictícia")
        compose.onNodeWithText("Observações").performScrollTo().performTextReplacement("Observação fictícia")
        compose.onNodeWithText("Salvar alterações").performScrollTo().performClick();waitText("Avaliação Fictícia Editada")
        val updated=app.repository.dao.activity(a.id)!!
        assertEquals(a.copy(name="Avaliação Fictícia Editada",type="Seminário",date="2026-10-20",term=4,description="Descrição fictícia",notes="Observação fictícia"),updated)
        assertEquals(before.grades,app.repository.activitySnapshot(a.id).grades)
        compose.onAllNodesWithText("Nota (0 a 10,00)").assertCountEquals(0)
    }
    @Test fun deletingEmptyAndGradedActivitiesRequiresExplicitConfirmation()=runBlocking {
        val before=app.backupManager.snapshot()
        clickList("activity-delete-${a.id}");waitText("2 notas já lançadas")
        compose.onNodeWithText("Cancelar").performScrollTo().performClick();waitText("+ Criar atividade")
        assertEquals(before,app.backupManager.snapshot())
        clickList("activity-delete-${empty.id}");waitText("0 notas já lançadas")
        compose.onNodeWithTag("confirm-delete-activity").performScrollTo().performClick()
        compose.waitUntil(5000){runBlocking{app.repository.dao.activity(empty.id)==null}}
        clickList("activity-delete-${a.id}");waitText("2 notas já lançadas")
        compose.onNodeWithText("A atividade e TODAS as notas vinculadas",substring=true).assertExists()
        shot("02-exclusao-com-contagem")
        compose.onNodeWithTag("confirm-delete-activity").performScrollTo().performClick()
        compose.waitUntil(5000){runBlocking{app.repository.dao.activity(a.id)==null}}
        assertTrue(app.repository.dao.activityGrades(a.id).isEmpty())
    }
    @Test fun proportionalPreviewCancellationAndLiveReports()=runBlocking {
        val before=app.backupManager.snapshot()
        editMaximum("1")
        compose.onNodeWithText("8,0 → 0,8",substring=true).assertExists()
        compose.onNodeWithTag("keep-grades").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Cancelar").performScrollTo().performClick();waitText("Salvar alterações")
        assertEquals(before,app.backupManager.snapshot())
        compose.onNodeWithText("Salvar alterações").performScrollTo().performClick();waitText("Confirmar alteração da pontuação")
        shot("03-previa-proporcional")
        compose.onNodeWithTag("recalculate-grades").performScrollTo().performClick();waitText("+ Criar atividade")
        compose.waitUntil(5000){runBlocking{app.repository.dao.activityGrades(a.id).any {it.studentId==students[0].id&&it.value==0.8}}}
        assertEquals(0.0,app.repository.dao.activityGrades(a.id).single {it.studentId==students[1].id}.value!!,0.0)
        assertNull(app.repository.dao.activityGrades(a.id).single {it.studentId==students[2].id}.value)
        compose.onNodeWithTag("nav-3").performClick();waitText("Boletim do 3º bimestre")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("1. ${students[0].name}"))
        compose.onNodeWithText("1. ${students[0].name}").performClick()
        compose.waitUntil(5000){compose.onAllNodesWithText("Nota 0,80 / 10,00",substring=true).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("nav-2").performClick();waitText("+ Criar atividade")
        clickList("activity-grades-${a.id}");waitText("Editar atividade")
        compose.onAllNodesWithText("Nota (0 a 1,00)").onFirst().assertTextContains("0,8")
        compose.onNodeWithText("Editar atividade").performClick();waitText("Salvar alterações")
        compose.onNodeWithText("Pontuação máxima").performScrollTo().performTextReplacement("2")
        compose.onNodeWithText("Salvar alterações").performScrollTo().performClick();waitText("Confirmar alteração da pontuação")
        compose.onNodeWithTag("recalculate-grades").performScrollTo().performClick();waitText("+ Criar atividade")
        clickList("activity-grades-${a.id}");waitText("Editar atividade")
        compose.onAllNodesWithText("Nota (0 a 2,00)").onFirst().assertTextContains("1,6")
        compose.activityRule.scenario.recreate();waitText("Editar atividade")
        compose.onAllNodesWithText("Nota (0 a 2,00)").onFirst().assertTextContains("1,6")
        Unit
    }
    @Test fun keepExistingValuesIsAvailableWhenCompatible()=runBlocking {
        val before=app.repository.activitySnapshot(a.id)
        editMaximum("20");compose.onNodeWithTag("keep-grades").performScrollTo().assertIsEnabled().performClick();waitText("+ Criar atividade")
        assertEquals(20.0,app.repository.dao.activity(a.id)!!.maximum,0.0)
        assertEquals(before.grades,app.repository.activitySnapshot(a.id).grades)
    }
    @Test fun activityWithoutLaunchedGradesChangesMaximumWithoutConversion()=runBlocking {
        clickList("activity-edit-${empty.id}");waitText("Editar atividade")
        compose.onNodeWithText("Pontuação máxima").performScrollTo().performTextReplacement("5")
        compose.onNodeWithText("Salvar alterações").performScrollTo().performClick();waitText("+ Criar atividade")
        assertEquals(5.0,app.repository.dao.activity(empty.id)!!.maximum,0.0)
        assertTrue(app.repository.dao.activityGrades(empty.id).isEmpty())
    }
    @Test fun metadataEditAndGradeReopenDoNotRoundStoredPrecision()=runBlocking {
        val precise=a.copy(maximum=10.1234567891234)
        app.repository.dao.save(precise);app.repository.saveGrade(a.id,students[0].id,8.123456789123456)
        compose.waitUntil(5000){compose.onAllNodesWithText("Prova • 06/08/2026 • 10,12 pontos").fetchSemanticsNodes().isNotEmpty()}
        clickList("activity-edit-${a.id}");waitText("Editar atividade")
        compose.onNodeWithText("Pontuação máxima").assertTextContains("10,1234567891234")
        compose.onNodeWithText("Nome da atividade").performScrollTo().performTextReplacement("Atividade Fictícia Precisa")
        compose.onNodeWithText("Salvar alterações").performScrollTo().performClick();waitText("Atividade Fictícia Precisa")
        assertEquals(precise.maximum,app.repository.dao.activity(a.id)!!.maximum,0.0)
        clickList("activity-grades-${a.id}");waitText("Editar atividade")
        compose.onAllNodesWithText("Nota (0 a 10,12)").onFirst().assertTextContains(8.123456789123456.toString().replace('.',','))
        assertEquals(8.123456789123456,app.repository.dao.activityGrades(a.id).single {it.studentId==students[0].id}.value!!,0.0)
        Unit
    }
}
