package br.com.diariodeclasse

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import br.com.diariodeclasse.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

class RecompositionUiInstrumentedTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private lateinit var app:DiaryApplication
    private var previous:AppSelection?=null
    private val c=SchoolClass(id="ui-recmp-c",name="Turma Fictícia Recomposição",subject="História",year=2026)
    private val a=Student(id="ui-recmp-a",classId=c.id,name="Estudante Fictício Alfa",number=1,joined="2026-01-01")
    private val pending=a.copy(id="ui-recmp-p",number=2,name="Estudante Fictício Pendente")
    private val zero=a.copy(id="ui-recmp-z",number=3,name="Estudante Fictício Zero")
    private val q=Activity("ui-recmp-q",c.id,"Prova Fictícia",date="2026-08-10",term=3,maximum=10.0)
    @Before fun setup() {runBlocking {
        app=compose.activity.application as DiaryApplication;previous=app.selectionStore.read()
        app.repository.saveClass(c,"2026-01-01")
        listOf(a,pending,zero).forEach {app.repository.saveStudent(it)}
        app.repository.saveActivity(q);app.repository.saveGrade(q.id,a.id,3.0);app.repository.saveGrade(q.id,zero.id,0.0)
        app.repository.saveActivity(q.copy(id=q.id+"4",date="2026-11-10",term=4));app.repository.saveGrade(q.id+"4",a.id,4.5)
        app.selectionStore.write(AppSelection(2026,c.id))
    }
        compose.onNodeWithTag("nav-2").performClick()
        compose.onNodeWithTag("activities-list").performScrollToNode(hasTestTag("open-recomposition"))
        compose.onNodeWithTag("open-recomposition").performClick();waitText("Bimestre de origem: 4º")
        compose.onNodeWithText("3º bim.").performClick();waitText("Bimestre de origem: 3º")
    }
    @After fun cleanup() {runBlocking {
        app.database.runInTransaction {
            val sql=app.database.openHelper.writableDatabase
            sql.execSQL("DELETE FROM recompositions WHERE studentId IN (SELECT id FROM students WHERE classId=?)",arrayOf(c.id))
            sql.execSQL("DELETE FROM grades WHERE activityId IN (SELECT id FROM activities WHERE classId=?)",arrayOf(c.id))
            for(t in listOf("activities","terms","students"))sql.execSQL("DELETE FROM $t WHERE classId=?",arrayOf(c.id))
            sql.execSQL("DELETE FROM classes WHERE id=?",arrayOf(c.id))
        };app.selectionStore.write(previous)
    }}
    private fun waitText(text:String) {compose.waitUntil(10000) {compose.onAllNodesWithText(text,substring=true).fetchSemanticsNodes().isNotEmpty()}}
    private fun row(s:Student) {compose.onNodeWithTag("recomposition-list").performScrollToNode(hasTestTag("recomposition-row-${s.id}"))}
    private fun edit(s:Student) {row(s);compose.onNodeWithTag("recomposition-edit-${s.id}").performClick()}
    private fun all() {
        compose.onNodeWithTag("recomposition-list").performScrollToIndex(0)
        compose.onNodeWithText("Abaixo de 5,00 ▾").performClick();compose.onNodeWithText("Todos os alunos",useUnmergedTree=true).performClick()
    }
    private fun setScore(value:String) {compose.onNodeWithTag("recomposition-score").performTextReplacement(value)}
    private fun save() {compose.onNodeWithTag("recomposition-save").performScrollTo().performClick();compose.waitUntil(10000) {compose.onAllNodesWithTag("recomposition-save").fetchSemanticsNodes().isEmpty()}}
    private fun shot(name:String) {
        val folder=File(app.filesDir,"etapa4b2-screenshots");folder.mkdirs()
        File(folder,"$name.png").outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun defaultExcludesPendingAndAutomaticallyRemovesStudentReachingFive() {
        row(a);compose.onNodeWithTag("recomposition-values-${a.id}").assertTextContains("Vigente: 3,00",substring=true)
        compose.onAllNodesWithTag("recomposition-row-${pending.id}").assertCountEquals(0)
        shot("01-filtro")
        edit(a);setScore("5,00")
        compose.onNodeWithTag("recomposition-date").performTextReplacement("20/11/2026")
        shot("02-previa");save()
        compose.waitUntil(10000) {compose.onAllNodesWithTag("recomposition-row-${a.id}").fetchSemanticsNodes().isEmpty()}
        all();row(a);compose.onNodeWithTag("recomposition-values-${a.id}").assertTextContains("Vigente: 5,00",substring=true)
        compose.onNodeWithTag("recomposition-values-${a.id}").assertTextContains("Original: 3,00",substring=true)
        row(pending);compose.onNodeWithTag("recomposition-values-${pending.id}").assertTextContains("Vigente: Pendente",substring=true)
        row(a);shot("03-todos")
        compose.activityRule.scenario.recreate();waitText("Bimestre de origem: 3º")
        row(a);compose.onNodeWithTag("recomposition-values-${a.id}").assertTextContains("Recomposição: 5,00",substring=true)
        val rows=runBlocking {app.backupManager.snapshot()};assertEquals(1,rows.recompositions.size);assertEquals(3.0,rows.grades.first {it.activityId==q.id&&it.studentId==a.id}.value!!,0.0)
    }
    @Test fun dirtyDismissRequiresConfirmationAndCancelKeepsDraftAcrossRecreation() {
        val before=runBlocking {app.backupManager.snapshot()}
        edit(a);setScore("8,75")
        compose.activityRule.scenario.recreate();compose.onNodeWithTag("recomposition-score").assertTextContains("8,75",substring=true)
        compose.onNodeWithText("Voltar",useUnmergedTree=true).performScrollTo().performClick();waitText("Descartar alterações não salvas?")
        compose.onNodeWithText("Cancelar",useUnmergedTree=true).performClick()
        compose.onNodeWithTag("recomposition-score").assertTextContains("8,75",substring=true)
        compose.onNodeWithText("Voltar",useUnmergedTree=true).performScrollTo().performClick()
        compose.onNodeWithText("Confirmar",useUnmergedTree=true).performClick()
        assertEquals(before,runBlocking {app.backupManager.snapshot()})
        all();compose.onNodeWithText("‹ Voltar").performClick()
        compose.onNodeWithTag("nav-3").performClick();waitText("Boletim do 3º bimestre")
        assertEquals(before,runBlocking {app.backupManager.snapshot()})
    }
    @Test fun zeroUnperformedEditAndConfirmedDeletionPreserveActivityGrades() {
        edit(zero);setScore("0");save()
        row(zero);compose.onNodeWithTag("recomposition-values-${zero.id}").assertTextContains("Recomposição: 0,00",substring=true)
        edit(zero);setScore("");save()
        row(zero);compose.onNodeWithTag("recomposition-values-${zero.id}").assertTextContains("Recomposição: Não realizada",substring=true)
        edit(zero);setScore("4");save()
        edit(zero);compose.onNodeWithTag("recomposition-delete").performScrollTo().performClick()
        compose.onNodeWithText("Cancelar",useUnmergedTree=true).performClick()
        assertEquals(4.0,runBlocking {app.repository.dao.recomposition(zero.id,3)}!!.value!!,0.0)
        compose.onNodeWithTag("recomposition-delete").performScrollTo().performClick()
        compose.onNodeWithText("Confirmar",useUnmergedTree=true).performClick()
        compose.waitUntil(10000) {compose.onAllNodesWithTag("recomposition-save").fetchSemanticsNodes().isEmpty()}
        assertNull(runBlocking {app.repository.dao.recomposition(zero.id,3)})
        assertEquals(0.0,runBlocking {app.repository.dao.activityGrades(q.id)}.first {it.studentId==zero.id}.value!!,0.0)
    }
    @Test fun reportsRefreshWithoutAddingRecompositionToActivitiesOrFourthTerm() {
        compose.onNodeWithText("‹ Voltar").performClick()
        compose.waitUntil(10000) {compose.onAllNodesWithTag("activities-list").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("nav-3").performClick();waitText("Boletim do 3º bimestre")
        compose.onNodeWithTag("reports-list").performScrollToNode(hasTestTag("report-row-${a.id}"))
        val total="report-row-${a.id}-cell-1"
        compose.onNodeWithTag(total,useUnmergedTree=true).assertTextEquals("3,00")
        runBlocking {app.repository.saveRecomposition(Recomposition(a.id,3,6.0,"2026-11-20"))}
        compose.waitUntil(10000) {compose.onAllNodesWithText("6,00",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag(total,useUnmergedTree=true).assertTextEquals("6,00")
        compose.onNodeWithText("1. ${a.name}").performClick();waitText("Original das atividades: 3,00")
        waitText("Recomposição: 6,00");waitText("Resultado vigente: 6,00");shot("04-individual")
        compose.onNodeWithTag("individual-report").performScrollToIndex(0)
        compose.onNodeWithText("4º bim.").performClick();waitText("Nota 4,50 / 10,00")
        compose.onNodeWithText("‹ Voltar").performClick();waitText("Boletim do 4º bimestre")
        compose.onNodeWithTag("reports-list").performScrollToIndex(0)
        compose.onNodeWithText("Bimestral ▾").performClick();compose.onNodeWithText("Resumo anual",useUnmergedTree=true).performClick()
        compose.onNodeWithTag("reports-list").performScrollToNode(hasTestTag("report-row-${a.id}"))
        compose.onNodeWithTag("report-row-${a.id}-cell-2",useUnmergedTree=true).assertTextContains("Resultado vigente: 6,00",substring=true)
        compose.onNodeWithTag("report-row-${a.id}-cell-3",useUnmergedTree=true).assertTextContains("4,50",substring=true)
    }
}
