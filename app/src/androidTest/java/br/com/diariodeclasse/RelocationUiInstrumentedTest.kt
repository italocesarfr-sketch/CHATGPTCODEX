package br.com.diariodeclasse

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.ui.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

class RelocationUiInstrumentedTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private lateinit var app:DiaryApplication
    private var previous:AppSelection?=null
    private val g=SchoolClass(id="ui-rel-g",name="1º G Fictício Remanejamento",subject="História",year=2026)
    private val h=g.copy(id="ui-rel-h",name="1º H Fictício Remanejamento")
    private val a=Student(id="ui-rel-a",personId="ui-rel-person",classId=g.id,name="Estudante Fictício Remanejamento",number=1,joined="2026-01-01")
    private val old=Activity("ui-rel-old",g.id,"Trabalho Fictício Anterior",date="2026-08-10",term=3,maximum=5.0)
    private val next=old.copy(id="ui-rel-next",classId=h.id,date="2026-09-10",name="Trabalho Fictício Posterior")
    @Before fun setup() {runBlocking {
        app=compose.activity.application as DiaryApplication;previous=app.selectionStore.read()
        app.repository.saveClass(g,"2026-01-01");app.repository.saveClass(h,"2026-01-01");app.repository.saveStudent(a)
        app.repository.saveActivity(old);app.repository.saveActivity(next);app.repository.saveGrade(old.id,a.id,2.0)
        app.repository.setDayHours(g.id,"2026-08-10",2);app.repository.mark("${g.id}:2026-08-10:1",a.id,"P");app.repository.mark("${g.id}:2026-08-10:2",a.id,"F")
        app.selectionStore.write(AppSelection(2026,g.id))
    };waitText(g.name)}
    @After fun cleanup() {runBlocking {
        app.database.runInTransaction {
            val sql=app.database.openHelper.writableDatabase
            for(id in listOf(g.id,h.id)) {
                sql.execSQL("DELETE FROM relocations WHERE sourceStudentId IN (SELECT id FROM students WHERE classId=?) OR targetStudentId IN (SELECT id FROM students WHERE classId=?)",arrayOf(id,id))
                sql.execSQL("DELETE FROM recompositions WHERE studentId IN (SELECT id FROM students WHERE classId=?)",arrayOf(id))
                sql.execSQL("DELETE FROM marks WHERE lessonId IN (SELECT id FROM lessons WHERE classId=?)",arrayOf(id))
                sql.execSQL("DELETE FROM grades WHERE activityId IN (SELECT id FROM activities WHERE classId=?)",arrayOf(id))
                for(table in listOf("import_drafts","activities","lessons","terms","students"))sql.execSQL("DELETE FROM $table WHERE classId=?",arrayOf(id))
                sql.execSQL("DELETE FROM classes WHERE id=?",arrayOf(id))
            }
        };app.selectionStore.write(previous)
    }}
    private fun waitText(text:String) {compose.waitUntil(10000){compose.onAllNodesWithText(text,substring=true).fetchSemanticsNodes().isNotEmpty()}}
    private fun begin(external:Boolean=false) {
        compose.onNodeWithText("Gerenciar alunos").performScrollTo().performClick();waitText("+ Adicionar aluno")
        editStudent();configureMove(external)
    }
    private fun editStudent() {
        compose.onNodeWithText("Editar").performScrollTo().performClick();waitText("Editar aluno")
    }
    private fun configureMove(external:Boolean=false,day:String="01/09/2026") {
        compose.onNodeWithText("Ativo ▾").performScrollTo().performClick();compose.onNodeWithText("Remanejado",useUnmergedTree=true).performClick()
        compose.onNodeWithTag("relocation-date").performScrollTo().performTextReplacement(day)
        if(external) {
            compose.onNodeWithTag("relocation-external").performScrollTo().performClick()
            compose.onNodeWithTag("relocation-external-name").performScrollTo().performTextInput("Destino Externo Fictício")
        } else compose.onNodeWithTag("relocation-target-${h.id}").performScrollTo().performClick()
    }
    private fun preview() {compose.onNodeWithTag("student-save").performScrollTo().performClick();compose.waitUntil(10000){compose.onAllNodesWithTag("relocation-confirm").fetchSemanticsNodes().isNotEmpty()}}
    private fun confirm() {compose.onNodeWithTag("relocation-confirm").performScrollTo().performClick();compose.waitUntil(10000){runBlocking{app.database.backupDao().relocations().any {it.sourceStudentId==a.id}}}}
    private fun saveStudent() {
        compose.onNodeWithTag("student-save").performScrollTo().performClick()
        compose.waitUntil(10000){compose.onAllNodesWithTag("student-save").fetchSemanticsNodes().isEmpty()}
    }
    private fun shot(name:String) {
        val folder=File(app.filesDir,"etapa4c-screenshots");folder.mkdirs()
        val root=if(compose.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty()) compose.onNode(isDialog()) else compose.onRoot()
        File(folder,"$name.png").outputStream().use {root.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun selectingDestinationAndCancellingPreviewNeverChangesAnyData() {
        val before=runBlocking{app.backupManager.snapshot()};begin();assertEquals(before,runBlocking{app.backupManager.snapshot()})
        preview();assertEquals(before,runBlocking{app.backupManager.snapshot()});shot("01-previa")
        compose.onNodeWithTag("relocation-cancel").performScrollTo().performClick()
        compose.onNodeWithTag("relocation-date").performScrollTo().assertTextContains("01/09/2026")
        assertEquals(before,runBlocking{app.backupManager.snapshot()})
        compose.onNodeWithText("Voltar").performScrollTo().performClick();waitText("Descartar alterações não salvas?")
        compose.onNodeWithText("Cancelar").performClick();compose.onNodeWithTag("student-save").assertExists()
    }
    @Test fun transferredThenActiveThenRecreatedActivityCanConfirmRelocation() {
        compose.onNodeWithText("Gerenciar alunos").performScrollTo().performClick();waitText("+ Adicionar aluno");editStudent()
        compose.onNodeWithText("Ativo ▾").performScrollTo().performClick();compose.onNodeWithText("Transferido",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Saída (opcional, dd/mm/aaaa)").performScrollTo().performTextReplacement("16/10/2026")
        saveStudent()
        assertEquals("Transferido",runBlocking{app.repository.dao.student(a.id)!!.status})
        editStudent();compose.onNodeWithText("Transferido ▾").performScrollTo().performClick();compose.onNodeWithText("Ativo",useUnmergedTree=true).performClick()
        waitText("Ao salvar como Ativo, a saída anterior será retirada")
        assertEquals("2026-10-16",runBlocking{app.repository.dao.student(a.id)!!.left})
        saveStudent();assertEquals(a,runBlocking{app.repository.dao.student(a.id)})
        compose.activityRule.scenario.recreate();waitText("+ Adicionar aluno")
        editStudent();configureMove(day="17/10/2026");preview();confirm()
        val after=runBlocking{app.backupManager.snapshot()}
        assertEquals(1,after.relocations.count {it.sourceStudentId==a.id})
        assertEquals(2,after.students.count {it.personId==a.personId})
        assertEquals(1,after.grades.count {it.studentId==a.id});assertEquals(2,after.marks.count {it.studentId==a.id})
    }
    @Test fun legacyActiveExitCanBeRepairedOnlyAfterExplicitConfirmation() {
        val stale=a.copy(left="2026-10-16")
        runBlocking {app.repository.dao.save(stale)}
        val vm=androidx.lifecycle.ViewModelProvider(compose.activity)[DiaryViewModel::class.java]
        compose.waitUntil(10000){vm.data.value.students.any {it.id==a.id&&it.left==stale.left}}
        compose.onNodeWithText("Gerenciar alunos").performScrollTo().performClick();waitText("+ Adicionar aluno");editStudent()
        waitText("Esta matrícula está Ativa, mas possui saída")
        val before=runBlocking{app.backupManager.snapshot()}
        compose.onNodeWithTag("repair-reactivation").performScrollTo().performClick();waitText("Reativar esta matrícula?")
        compose.onNodeWithText("Cancelar").performClick();assertEquals(before,runBlocking{app.backupManager.snapshot()})
        compose.onNodeWithTag("repair-reactivation").performScrollTo().performClick();compose.onNodeWithText("Confirmar",useUnmergedTree=true).performClick()
        compose.waitUntil(10000){compose.onAllNodesWithTag("student-save").fetchSemanticsNodes().isEmpty()}
        assertEquals(a,runBlocking{app.repository.dao.student(a.id)})
        editStudent();configureMove(day="17/10/2026");preview();confirm()
        val after=runBlocking{app.backupManager.snapshot()}
        assertEquals(before.grades,after.grades);assertEquals(before.marks,after.marks)
        assertEquals(1,after.relocations.count {it.sourceStudentId==a.id})
    }
    @Test fun confirmedMoveHidesSourceAndRefreshesDestinationReportsAcrossRecreation() {
        begin();preview();confirm();compose.waitUntil(10000){compose.onAllNodesWithTag("relocation-confirm").fetchSemanticsNodes().isEmpty()}
        compose.onAllNodesWithText("1. ${a.name}").assertCountEquals(0)
        compose.onNodeWithTag("show-relocated").performScrollTo().performClick()
        compose.onNodeWithTag("roster-list").performScrollToNode(hasTestTag("roster-student-${a.id}"))
        waitText("Remanejado para ${h.name}");shot("02-origem-historico")
        compose.onNodeWithTag("nav-3").performClick();waitText("Boletim do 4º bimestre")
        compose.onNodeWithText("3º bim.").performClick();waitText("Boletim do 3º bimestre")
        compose.onAllNodesWithTag("report-row-${a.id}").assertCountEquals(0)
        val reportRoot=compose.onNodeWithTag("reports-list").fetchSemanticsNode().id
        compose.onNodeWithTag("class-selector").performClick();compose.onNodeWithText(h.name,useUnmergedTree=true).performClick()
        compose.waitUntil(10000){compose.onAllNodesWithTag("reports-list").fetchSemanticsNodes().any {it.id!=reportRoot}}
        val target=runBlocking{app.repository.dao.roster(h.id).single()}
        compose.onNodeWithTag("reports-list").performScrollToNode(hasTestTag("report-row-${target.id}"))
        fun value()=compose.onNodeWithTag("report-row-${target.id}-current",useUnmergedTree=true).onChild()
        value().assertTextEquals("2,00")
        runBlocking{app.repository.saveGrade(next.id,target.id,3.0)}
        compose.waitUntil(10000){value().fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].any {it.text=="5,00"}}
        compose.onNodeWithText("${target.number}. ${target.name}").performClick();waitText("Histórico consolidado:")
        compose.onNodeWithTag("individual-report").performScrollToNode(hasTestTag("individual-activity-${old.id}"))
        compose.onNodeWithTag("individual-activity-${old.id}").assertTextContains(g.name,substring=true)
        compose.onNodeWithTag("individual-report").performScrollToNode(hasTestTag("history-attendance-${g.id}:2026-08-10:2"))
        compose.onNodeWithTag("history-attendance-${g.id}:2026-08-10:2").assertTextContains("F • REALIZADA",substring=true);shot("03-historico-destino")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("individual-report").performScrollToIndex(0);waitText("Nota 5,00 / 10,00")
        assertEquals(a.personId,target.personId)
    }
    @Test fun externalDestinationRequiresConfirmationAndCreatesNoFakeClassOrStudent() {
        val before=runBlocking{app.backupManager.snapshot()};begin(true);preview()
        assertEquals(before,runBlocking{app.backupManager.snapshot()})
        waitText("Destino externo: sem criar turma");confirm()
        val after=runBlocking{app.backupManager.snapshot()}
        assertEquals(before.classes,after.classes);assertEquals(before.students.size,after.students.size)
        assertEquals(before.grades,after.grades);assertEquals(before.marks,after.marks)
        assertEquals("Destino Externo Fictício",after.relocations.single {it.sourceStudentId==a.id}.externalTarget)
    }
    @Test fun showingSourceAttendanceOrGradesNeverEnablesHistoricalWrites() {
        val p=runBlocking{app.repository.previewRelocation(a.id,h.id,null,"2026-09-01").also {app.repository.confirmRelocation(it)}}
        val initial=runBlocking{app.repository.snapshot.first{it.loaded}}
        val page=mutableStateOf("frequency")
        val vm=androidx.lifecycle.ViewModelProvider(compose.activity)[DiaryViewModel::class.java]
        compose.activity.runOnUiThread {compose.activity.setContent {MaterialTheme {
            val d by app.repository.snapshot.collectAsState(initial=initial)
            if(page.value=="frequency") FrequencyGrid(d,g,d.lessons.filter {it.classId==g.id},d.terms.first {it.classId==g.id&&it.number==3},true,mark={l,s,m->vm.act{app.repository.mark(l.id,s.id,m)}},options={},openDay={})
            else GradeScreen(vm,d,old,edit={},deleted={})
        }}}
        compose.onAllNodesWithTag("cell-${g.id}:2026-08-10:1-${a.id}-P").assertCountEquals(0)
        compose.onNodeWithTag("show-relocated").performClick()
        compose.onNodeWithTag("cell-${g.id}:2026-08-10:1-${a.id}-P").assertIsNotEnabled()
        compose.onNodeWithTag("cell-${g.id}:2026-08-10:1-${a.id}-F").assertIsNotEnabled()
        compose.runOnIdle {page.value="grades"}
        compose.onAllNodesWithText(a.name,substring=true).assertCountEquals(0)
        compose.onNodeWithTag("show-relocated").performScrollTo().performClick()
        waitText("Histórico somente leitura: 2,00")
        compose.onAllNodesWithText("Nota (0 a 5,00)").assertCountEquals(0)
        assertEquals(1,runBlocking{app.database.backupDao().relocations().count {it.sourceStudentId==p.source.id}})
    }
}
