package br.com.diariodeclasse

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
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
    };compose.onNodeWithTag("nav-3").performClick();waitText("Boletim 4º Bim")
        compose.onNodeWithText("1º bim.").performClick();waitText("Boletim 1º Bim")
    }
    @After fun cleanup() { runBlocking {
        app.database.runInTransaction {
            val sql=app.database.openHelper.writableDatabase
            for(id in listOf(c.id,other.id,"ui-reports-previous")) {
                sql.execSQL("DELETE FROM recompositions WHERE studentId IN (SELECT id FROM students WHERE classId=?)",arrayOf(id))
                sql.execSQL("DELETE FROM marks WHERE lessonId IN (SELECT id FROM lessons WHERE classId=?)",arrayOf(id))
                sql.execSQL("DELETE FROM grades WHERE activityId IN (SELECT id FROM activities WHERE classId=?)",arrayOf(id))
                for(table in listOf("import_drafts","activities","lessons","terms","students")) sql.execSQL("DELETE FROM $table WHERE classId=?",arrayOf(id))
                sql.execSQL("DELETE FROM classes WHERE id=?",arrayOf(id))
            }
        };app.selectionStore.write(previous)
    } }
    private fun waitText(text:String) { compose.waitUntil(10000) {compose.onAllNodesWithText(text,substring=true).fetchSemanticsNodes().isNotEmpty()} }
    private fun mode(value:String,current:String="Geral") {
        compose.onNodeWithTag("reports-list").performScrollToIndex(0)
        compose.onNodeWithText("${if(current=="Bimestral")"Geral"else current} ▾").performScrollTo().performClick()
        compose.onNode(hasText(if(value=="Bimestral")"Geral"else value) and hasAnyAncestor(isPopup()),useUnmergedTree=true).performClick()
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
        val folder=File(app.filesDir,"etapa4b3-screenshots");folder.mkdirs()
        val root=if(compose.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty()) compose.onNode(isDialog()) else compose.onRoot()
        File(folder,"$name.png").outputStream().use {root.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun explanatoryHelpStaysOffTheTableAndClosesWithoutChangingDataOrFilters() {
        val before=runBlocking {app.backupManager.snapshot()}
        compose.onNodeWithTag("export-organize").assertIsDisplayed()
        compose.onNodeWithTag("open-export").assertIsDisplayed()
        compose.onNodeWithTag("report-information").assertDoesNotExist()
        compose.onNodeWithText("Atividades: escala",substring=true).assertDoesNotExist()
        compose.onNodeWithText("Deslize as colunas",substring=true).assertDoesNotExist()
        compose.onNodeWithText("Faltas efetivas: F + FJ",substring=true).assertDoesNotExist()
        val help=compose.onNodeWithContentDescription("Informações do relatório")
        help.assertWidthIsAtLeast(48.dp)
        help.assertHeightIsAtLeast(48.dp)
        shot("06-relatorios-sem-blocos-auxiliares")
        help.performScrollTo().performClick()
        compose.onNodeWithTag("report-information").assertIsDisplayed()
        compose.onNodeWithText("Atividades: escala 0 a 10,00. Recomposição: 0 a 10. Pendências e pontuação disponível abaixo da escala mantêm o original parcial.").assertExists()
        compose.onNodeWithText("Deslize as colunas",substring=true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Faltas efetivas: F + FJ",substring=true).performScrollTo().assertIsDisplayed()
        shot("07-informacoes-do-relatorio")
        compose.onNodeWithText("Fechar").performClick()
        compose.onNodeWithTag("report-information").assertDoesNotExist()
        compose.onNodeWithTag("report-sort").performScrollTo().performClick()
        compose.onNodeWithText("Maior nota",useUnmergedTree=true).performClick()
        row(a);cell(a,0).assertTextEquals("0,00")
        compose.onNodeWithTag("reports-list").performScrollToIndex(0)
        compose.onNodeWithContentDescription("Ordenar: Maior nota").performScrollTo().assertIsDisplayed()
        help.performScrollTo().performClick();compose.onNodeWithText("Fechar").performClick()
        compose.onNodeWithContentDescription("Ordenar: Maior nota").performScrollTo().assertIsDisplayed()
        assertEquals(before,runBlocking {app.backupManager.snapshot()})
    }
    @Test fun helpPreservesCurrentClassJustificationPolicy() {
        mode("Frequência")
        compose.onNodeWithContentDescription("Informações do relatório").performClick()
        compose.onNodeWithText("Aulas elegíveis variam conforme a matrícula.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Fechar").performClick()
        runBlocking {app.repository.saveClass(c.copy(countJustified=false),"2026-01-01")}
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Informações do relatório").performClick()
        waitText("Faltas efetivas: somente F.")
        compose.onNodeWithText("Faltas efetivas: somente F.",substring=true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Fechar").performClick()
        row(a);cell(a,4).assertTextEquals("1")
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
        compose.waitForIdle()
        // Fixture B3: cabeçalho, uma atividade, cabeçalho anual e quatro bimestres.
        // Evita a busca recursiva de semântica durante a mudança de período (bug do harness Compose).
        compose.onNodeWithTag("individual-report").performScrollToIndex(6)
        compose.onNodeWithTag("individual-term-4").assertTextContains("Sem dados",substring=true)
        compose.onNodeWithText("‹ Voltar").performClick();compose.waitUntil(10000) {compose.onAllNodesWithTag("reports-list").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("reports-list").performScrollToIndex(0);waitText("Boletim 3º Bim")
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
    @Test fun sortingUsesEffectiveAbsencesAndRestoresOfficialCallOnSecondChoice() {
        val before=runBlocking {app.backupManager.snapshot()}
        compose.onAllNodesWithText("Consultas").assertCountEquals(0)
        for(label in listOf("Mais faltas","Menos faltas","Maior nota","Menor nota","Chamada")) {
            compose.onNodeWithTag("reports-list").performScrollToIndex(0)
            compose.onNodeWithTag("report-sort").performScrollTo().performClick()
            compose.onNodeWithText(label,useUnmergedTree=true).performClick()
            compose.onNodeWithContentDescription("Ordenar: $label").assertExists()
            if(label!="Chamada") {
                compose.onNodeWithTag("report-sort").performClick()
                compose.onNodeWithText(label,useUnmergedTree=true).performClick()
                compose.onNodeWithContentDescription("Ordenar: Chamada").assertExists()
            }
        }
        mode("Frequência");row(a);cell(a,4).assertTextEquals("1")
        row(transferred);cell(transferred,4).assertTextEquals("1")
        runBlocking {app.repository.saveClass(c.copy(countJustified=false),"2026-01-01")}
        compose.waitUntil(10000) {cell(transferred,4).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].any {it.text=="0"}}
        row(a);runBlocking {app.repository.mark(l2,a.id,"P")}
        compose.waitUntil(10000) {cell(a,4).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].any {it.text=="0"}}
        assertEquals(before.grades,runBlocking {app.backupManager.snapshot()}.grades)
    }
    @Test fun annualRemainsAccessibleInIndividualAndClassIsolationPersists() {
        val before=runBlocking {app.backupManager.snapshot()}
        row(a);compose.onNodeWithText("1. ${a.name}").performClick();waitText("Nota 0,00 / 10,00")
        for(n in 1..4) {
            compose.onNodeWithTag("individual-report").performScrollToNode(hasTestTag("individual-term-$n"))
            compose.onNodeWithTag("individual-term-$n").assertTextContains(if(n in listOf(2,4))"Sem dados"else if(n==3)"Parcial"else"0,00",substring=true)
        }
        shot("03-anual")
        compose.onNodeWithText("‹ Voltar").performClick();waitText("Boletim 1º Bim")
        selectClass(other);waitText("Nenhum aluno cadastrado nesta turma.")
        selectClass(c);waitText("Boletim 1º Bim")
        compose.activityRule.scenario.recreate();waitText("Boletim 1º Bim")
        assertEquals(before,runBlocking {app.backupManager.snapshot()})
    }
    private fun field(student:Student,key:String)=compose.onNodeWithTag("report-row-${student.id}-$key",useUnmergedTree=true).onChild()
    @Test fun consolidatedBimesterExposesOriginalRecompositionPresenceAndTransferStatus() {
        runBlocking {app.repository.saveRecomposition(Recomposition(a.id,1,5.0,"2026-11-20"))}
        row(a)
        compose.waitUntil(10000) {field(a,"current").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].any {it.text=="5,00"}}
        field(a,"original").assertTextEquals("0,00");field(a,"recomposition").assertTextEquals("5,00")
        field(a,"present").assertTextEquals("1");field(a,"absent").assertTextEquals("1");field(a,"justified").assertTextEquals("0")
        field(a,"effective-absences").assertTextEquals("1");field(a,"percent").assertTextEquals("50,00%")
        row(transferred);compose.onNodeWithTag("report-row-${transferred.id}-name",useUnmergedTree=true).onChildren().filter(hasText("Transferido")).assertCountEquals(1)
        field(transferred,"original").assertTextEquals("8,00");field(transferred,"justified").assertTextEquals("1")
        row(late);field(late,"activity-${q1.id}").assertTextEquals("Fora da matrícula")
        row(pending);field(pending,"current").assertTextEquals("Não lançado");field(pending,"activity-${q1.id}").assertTextEquals("Pendente")
    }
    @Test fun annualAndIndividualDistinguishAllResultsAndEffectiveAbsences() {
        val q2=q1.copy(id="ui-reports-q2-final",date="2026-05-10",term=2)
        val q4=q1.copy(id="ui-reports-q4-final",date="2026-11-10",term=4)
        runBlocking {
            app.repository.saveActivity(q2);app.repository.saveGrade(q2.id,a.id,3.0)
            app.repository.saveActivity(q4);app.repository.saveRecomposition(Recomposition(a.id,1,0.0,"2026-11-20"))
            app.repository.saveRecomposition(Recomposition(a.id,2,2.0,"2026-11-20"))
            app.repository.saveRecomposition(Recomposition(a.id,3,6.0,"2026-11-20"))
        }
        row(a);compose.onNodeWithText("1. ${a.name}").performClick();waitText("Original das atividades: 0,00")
        for((n,total) in listOf(1 to "0,00",2 to "3,00",3 to "6,00",4 to "Não lançado")) {
            compose.onNodeWithTag("individual-report").performScrollToNode(hasTestTag("individual-term-$n"))
            compose.onNodeWithTag("individual-term-$n").assertTextContains(total,substring=true)
        }
        compose.onNodeWithTag("individual-report").performScrollToNode(hasTestTag("individual-term-3"))
        compose.onNodeWithTag("individual-term-3").assertTextContains("Parcial",substring=true)
        compose.onNodeWithTag("individual-report").performScrollToIndex(0)
        compose.onNodeWithText("3º bim.").performClick()
        // O resumo anual também contém 6,00 antes de terminar a troca de período.
        // Aguardar o cabeçalho corrente evita consultar a lista enquanto ela é recomposta.
        waitText("Nota 6,00 / 10,00");compose.waitForIdle()
        // Fixture B3: cabeçalho, uma atividade, cabeçalho anual e quatro bimestres.
        // Evita a busca recursiva de semântica durante a mudança de período (bug do harness Compose).
        compose.onNodeWithTag("individual-report").performScrollToIndex(6)
        compose.onNodeWithTag("individual-term-4").assertTextContains("Recomposição: Não registrada",substring=true)
    }
    @Test fun longNamesRemainFixedAndConfirmedUpdatesPreserveReadingPosition() {
        val people=(5..24).map {n -> a.copy(id="ui-reports-long-$n",number=n,name="Estudante Fictício de Nome Extenso para Conferência da Leitura Número $n")}
        runBlocking {people.forEach {app.repository.saveStudent(it)}}
        val selected=people[10]
        row(selected)
        val fixed=compose.onNodeWithTag("report-row-${selected.id}-name",useUnmergedTree=true)
        val left=fixed.fetchSemanticsNode().boundsInRoot.left
        val values=compose.onNodeWithTag("report-row-${selected.id}-values",useUnmergedTree=true)
        values.performTouchInput {swipeLeft()}
        assertEquals(left,fixed.fetchSemanticsNode().boundsInRoot.left,0.1f)
        val horizontal=values.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.HorizontalScrollAxisRange].value()
        assertTrue(horizontal>0)
        val list=compose.onNodeWithTag("reports-list")
        val vertical=list.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()
        runBlocking {app.repository.saveGrade(q1.id,selected.id,6.0)}
        compose.waitUntil(10000) {field(selected,"current").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].any {it.text=="6,00"}}
        assertEquals(vertical,list.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value(),0.1f)
        assertEquals(horizontal,values.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.HorizontalScrollAxisRange].value(),0.1f)
        assertEquals(left,fixed.fetchSemanticsNode().boundsInRoot.left,0.1f)
        val before=runBlocking {app.backupManager.snapshot()};shot("05-nomes-extensos")
        assertEquals(before,runBlocking {app.backupManager.snapshot()})
    }
    @Test fun rapidYearClassAndTermChangesNeverShowPreviousClassRows() {
        val previousYear=c.copy(id="ui-reports-previous",name="Turma Fictícia 2025",year=2025)
        val otherStudent=a.copy(id="ui-reports-previous-student",classId=previousYear.id,name="Estudante Fictício Ano Anterior",joined="2025-01-01")
        val activity=q1.copy(id="ui-reports-previous-q",classId=previousYear.id,date="2025-02-10")
        runBlocking {
            app.repository.saveClass(previousYear,"2025-01-01");app.repository.saveStudent(otherStudent)
            app.repository.saveActivity(activity);app.repository.saveGrade(activity.id,otherStudent.id,9.0)
            repeat(15) {n -> app.selectionStore.write(if(n%2==0) AppSelection(2025,previousYear.id) else AppSelection(2026,c.id))}
        }
        compose.waitUntil(10000) {compose.onAllNodesWithText("2025 ▾").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("reports-list").performScrollToIndex(0)
        row(otherStudent);field(otherStudent,"current").assertTextEquals("9,00")
        compose.onAllNodesWithTag("report-row-${a.id}").assertCountEquals(0)
        for(n in listOf(3,2,1)) {compose.onNodeWithText("${n}º bim.").performClick();waitText("Boletim ${n}º Bim")}
        row(otherStudent);field(otherStudent,"current").assertTextEquals("9,00")
        compose.onNodeWithTag("year-selector").performClick();compose.onNodeWithText("Ano letivo 2026",useUnmergedTree=true).performClick()
        compose.waitUntil(10000) {compose.onAllNodesWithText("2026 ▾").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("class-selector").performClick();compose.onNodeWithText(c.name,useUnmergedTree=true).performClick()
        compose.waitUntil(10000) {compose.onAllNodesWithTag("report-row-${otherStudent.id}").fetchSemanticsNodes().isEmpty()}
        row(a);field(a,"current").assertTextEquals("0,00")
    }

}
