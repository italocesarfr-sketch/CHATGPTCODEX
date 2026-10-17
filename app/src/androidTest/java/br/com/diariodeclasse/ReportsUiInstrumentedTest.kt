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

class ReportsUiInstrumentedTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private lateinit var app: DiaryApplication
    private var previous: AppSelection?=null
    private val c=SchoolClass(id="ui-reports-c",name="Turma Fictícia Relatórios",subject="História",year=2026)
    private val other=c.copy(id="ui-reports-other",name="Outra Turma Fictícia")
    private val a=Student(id="ui-reports-a",classId=c.id,name="Estudante Fictício Alfa",number=1,joined="2026-01-01")
    private val late=a.copy(id="ui-reports-late",personId="ui-report-person-late",name="Estudante Fictício Tardio",number=2,joined="2026-09-01")
    private val transferred=a.copy(id="ui-reports-t",personId="ui-report-person-t",name="Estudante Fictício Transferido",number=3,left="2026-06-30",status="Transferido")
    private val pending=a.copy(id="ui-reports-p",personId="ui-report-person-p",name="Estudante Fictício Pendente",number=4)
    private val q1=Activity("ui-reports-q1",c.id,"Prova Fictícia B1",date="2026-02-10",term=1,maximum=10.0)
    private val q3=q1.copy(id="ui-reports-q3",name="Trabalho Fictício B3",date="2026-08-10",term=3,maximum=2.0)
    private val l1="${c.id}:2026-02-10:1"
    private val l2="${c.id}:2026-02-10:2"
    @Before fun setup() { runBlocking {
        app=compose.activity.application as DiaryApplication;previous=app.selectionStore.read()
        app.repository.saveClass(c,"2026-01-01");app.repository.saveClass(other,"2026-01-01")
        listOf(a,late,transferred,pending).forEach {app.repository.saveStudent(it)}
        app.repository.saveActivity(q1);app.repository.saveActivity(q3)
        app.repository.saveGrade(q1.id,a.id,0.0);app.repository.saveGrade(q1.id,transferred.id,8.0);app.repository.saveGrade(q3.id,a.id,1.5)
        app.repository.setDayHours(c.id,"2026-02-10",2)
        app.repository.mark(l1,a.id,"P");app.repository.mark(l2,a.id,"F")
        app.repository.mark(l1,transferred.id,"FJ");app.repository.mark(l2,transferred.id,"P")
        app.selectionStore.write(AppSelection(2026,c.id))
    };compose.onNodeWithTag("nav-3").performClick();waitText("Boletim do 4º bimestre")
        compose.onNodeWithText("1º bim.").performClick();waitText("Boletim do 1º bimestre")
    }
    @After fun cleanup() { runBlocking {
        app.database.runInTransaction {
            val sql=app.database.openHelper.writableDatabase
            for(id in listOf(c.id,other.id)) {
                sql.execSQL("DELETE FROM marks WHERE lessonId IN (SELECT id FROM lessons WHERE classId=?)",arrayOf(id))
                sql.execSQL("DELETE FROM grades WHERE activityId IN (SELECT id FROM activities WHERE classId=?)",arrayOf(id))
                for(table in listOf("import_drafts","activities","lessons","terms","students")) sql.execSQL("DELETE FROM $table WHERE classId=?",arrayOf(id))
                sql.execSQL("DELETE FROM classes WHERE id=?",arrayOf(id))
            }
        };app.selectionStore.write(previous)
    } }
    private fun waitText(text:String) { compose.waitUntil(10000) {compose.onAllNodesWithText(text,substring=true).fetchSemanticsNodes().isNotEmpty()} }
    private fun mode(value:String,current:String="Bimestral") {
        compose.onNodeWithTag("reports-list").performScrollToIndex(0)
        compose.onNodeWithText("$current ▾").performClick();compose.onNodeWithText(value,useUnmergedTree=true).performClick()
    }
    private fun row(student:Student) { compose.onNodeWithTag("reports-list").performScrollToNode(hasTestTag("report-row-${student.id}")) }
    private fun selectClass(school:SchoolClass) {
        val previousRoot=compose.onNodeWithTag("reports-list").fetchSemanticsNode().id
        compose.onNodeWithTag("class-selector").performClick();compose.onNodeWithText(school.name,useUnmergedTree=true).performClick()
        compose.waitUntil(10000) {compose.onAllNodesWithTag("reports-list").fetchSemanticsNodes().any {it.id!=previousRoot}}
        compose.onNodeWithTag("reports-list").performScrollToIndex(0)
    }
    private fun cell(student:Student,index:Int)=compose.onNodeWithTag("report-row-${student.id}-cell-$index",useUnmergedTree=true)
    private fun shot(name:String) {
        val folder=File(app.filesDir,"etapa4b1-screenshots");folder.mkdirs()
        File(folder,"$name.png").outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun bimestralDistinguishesZeroPendingOutsideEnrollmentAndIndividualIsReadOnly() {
        val before=runBlocking {app.backupManager.snapshot()}
        row(a);cell(a,0).assertTextEquals("0,00");shot("01-bimestral")
        row(pending);cell(pending,0).assertTextEquals("Pendente");cell(pending,1).assertTextEquals("Não lançado")
        row(late);cell(late,0).assertTextEquals("Fora da matrícula")
        row(a);compose.onNodeWithText("1. ${a.name}").performClick();waitText("Nota 0,00 / 10,00")
        compose.onAllNodesWithText("Editar cadastro e observações").assertCountEquals(0);shot("04-individual")
        compose.onNodeWithTag("individual-report").performScrollToIndex(0)
        compose.onNodeWithText("3º bim.").performClick();waitText("Nota 1,50 / 10,00")
        compose.onNodeWithTag("individual-report").performScrollToNode(hasTestTag("individual-term-4"))
        compose.onNodeWithTag("individual-term-4").assertTextContains("Sem atividades",substring=true)
        compose.onNodeWithText("‹ Voltar").performClick();compose.waitUntil(10000) {compose.onAllNodesWithTag("reports-list").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("reports-list").performScrollToIndex(0);waitText("Boletim do 3º bimestre")
        assertEquals(before,runBlocking {app.backupManager.snapshot()})
    }
    @Test fun frequencyAndGradesRefreshWhileReportsRemainOpen() {
        mode("Frequência");row(a);cell(a,2).assertTextEquals("1");cell(a,0).assertTextEquals("2");shot("02-frequencia")
        runBlocking {app.repository.mark(l2,a.id,"P")}
        compose.waitUntil(10000) {cell(a,2).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].any {it.text=="0"}}
        cell(a,1).assertTextEquals("2")
        mode("Bimestral","Frequência");row(a)
        runBlocking {app.repository.saveGrade(q1.id,a.id,6.0)}
        compose.waitUntil(10000) {cell(a,0).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].any {it.text=="6,00"}}
        cell(a,1).assertTextEquals("6,00")
    }
    @Test fun absenteeRankingFollowsJustificationPolicyAndDoesNotCountNr() {
        mode("Mais faltosos");row(a);cell(a,4).assertTextEquals("1")
        row(transferred);cell(transferred,4).assertTextEquals("1")
        compose.onAllNodesWithText("${late.number}. ${late.name}").assertCountEquals(0)
        runBlocking {app.repository.saveClass(c.copy(countJustified=false),"2026-01-01")}
        compose.waitUntil(10000) {compose.onAllNodesWithTag("report-row-${transferred.id}").fetchSemanticsNodes().isEmpty()}
        row(a);runBlocking {app.repository.mark(l2,a.id,"P")}
        waitText("Nenhum aluno com faltas efetivas no período.")
    }
    @Test fun annualShowsFourTermsNoDataAndGlobalClassIsolation() {
        val before=runBlocking {app.backupManager.snapshot()}
        mode("Resumo anual");row(a)
        cell(a,0).assertTextContains("0,00",substring=true);cell(a,1).assertTextEquals("Sem dados")
        cell(a,2).assertTextContains("Parcial",substring=true);cell(a,3).assertTextEquals("Sem dados");shot("03-anual")
        selectClass(other)
        compose.onNodeWithTag("reports-list").performScrollToNode(hasText("Nenhum aluno cadastrado nesta turma."))
        waitText("Nenhum aluno cadastrado nesta turma.")
        selectClass(c)
        compose.waitUntil(10000) {compose.onAllNodesWithTag("reports-list").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("reports-list").performScrollToIndex(0);waitText("Resumo anual de 2026")
        compose.activityRule.scenario.recreate();waitText("Resumo anual de 2026")
        assertEquals(before,runBlocking {app.backupManager.snapshot()})
    }
}
