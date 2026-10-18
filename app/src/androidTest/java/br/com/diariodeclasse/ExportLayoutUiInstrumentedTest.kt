package br.com.diariodeclasse

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.ui.DiaryViewModel
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

class ExportLayoutUiInstrumentedTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private lateinit var app:DiaryApplication
    private var previous:AppSelection?=null
    private val c=SchoolClass(id="ui-exp-c",name="Turma Fictícia Disposição",subject="História",year=2026)
    private val h=c.copy(id="ui-exp-h",name="Destino Fictício Disposição")
    private val a=Student("ui-exp-a","ui-exp-person-a",c.id,"Estudante Fictício Alfa Exportação",1,"2026-01-01")
    private val b=a.copy(id="ui-exp-b",personId="ui-exp-person-b",number=2,name="Estudante Fictício Beta Exportação")
    private val activity=Activity("ui-exp-activity",c.id,"Avaliação Fictícia",date="2026-08-10",term=3,maximum=10.0)
    @Before fun setup()=runBlocking {
        app=compose.activity.application as DiaryApplication;previous=app.selectionStore.read()
        app.repository.saveClass(c,"2026-01-01");app.repository.saveClass(h,"2026-01-01")
        app.repository.saveStudent(a);app.repository.saveStudent(b)
        app.repository.saveActivity(activity);app.repository.saveGrade(activity.id,a.id,0.0)
        app.repository.setDayHours(c.id,"2026-08-10",2);app.repository.mark("${c.id}:2026-08-10:1",a.id,"P");app.repository.mark("${c.id}:2026-08-10:2",a.id,"F")
        app.selectionStore.write(AppSelection(2026,c.id))
        waitText(c.name)
    }
    @After fun cleanup()=runBlocking {
        app.database.runInTransaction {
            val sql=app.database.openHelper.writableDatabase
            for(id in listOf(c.id,h.id)) {
                sql.execSQL("DELETE FROM export_rows WHERE classId=?",arrayOf(id))
                sql.execSQL("DELETE FROM relocations WHERE sourceStudentId IN (SELECT id FROM students WHERE classId=?) OR targetStudentId IN (SELECT id FROM students WHERE classId=?)",arrayOf(id,id))
                sql.execSQL("DELETE FROM recompositions WHERE studentId IN (SELECT id FROM students WHERE classId=?)",arrayOf(id))
                sql.execSQL("DELETE FROM marks WHERE lessonId IN (SELECT id FROM lessons WHERE classId=?)",arrayOf(id))
                sql.execSQL("DELETE FROM grades WHERE activityId IN (SELECT id FROM activities WHERE classId=?)",arrayOf(id))
                for(table in listOf("import_drafts","activities","lessons","terms","students"))sql.execSQL("DELETE FROM $table WHERE classId=?",arrayOf(id))
                sql.execSQL("DELETE FROM classes WHERE id=?",arrayOf(id))
            }
        };app.selectionStore.write(previous)
    }
    private fun waitText(value:String) {compose.waitUntil(10000){compose.onAllNodesWithText(value,substring=true).fetchSemanticsNodes().isNotEmpty()}}
    private fun waitTag(value:String) {compose.waitUntil(10000){compose.onAllNodesWithTag(value).fetchSemanticsNodes().isNotEmpty()}}
    private fun reports() {compose.onNodeWithTag("nav-3").performClick();waitTag("export-organize")}
    private fun top(root:String="export-layout-list") {compose.onNodeWithTag(root).performScrollToIndex(0)}
    private fun hideMode() {compose.onNodeWithTag("export-organize").performClick();waitTag("export-layout-list");compose.onNodeWithTag("export-hide").performClick();waitTag("export-conclude")}
    private fun checkbox(id:String):SemanticsNodeInteraction {
        compose.onNodeWithTag("export-layout-list").performScrollToNode(hasTestTag("export-hide-toggle-$id"))
        return compose.onNodeWithTag("export-hide-toggle-$id")
    }
    private fun toggle(id:String) { checkbox(id).performClick() }
    private fun conclude(root:String="export-layout-list") {
        top(root);compose.onNodeWithTag("export-conclude").performClick();waitTag("export-organize")
        val persisted=runBlocking{app.repository.dao.exportRows(c.id)}
        val vm=ViewModelProvider(compose.activity)[DiaryViewModel::class.java]
        compose.waitUntil(10000){vm.data.value.exportRows.filter{it.classId==c.id}==persisted}
    }
    private fun organize() {compose.onNodeWithTag("export-organize").performClick();waitTag("export-layout-list")}
    private fun shot(name:String) {
        compose.waitForIdle()
        val folder=File(app.filesDir,"etapa51-screenshots");folder.mkdirs()
        File(folder,"$name.png").outputStream().use{compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun multipleSelectionSurvivesScrollingAndRecreationThenUnhidesWithoutChangingSchoolData() {
        val people=(3..22).map{n->a.copy(id="ui-exp-$n",personId="ui-exp-person-$n",number=n,name="Estudante Fictício Exportação ${n.toString().padStart(2,'0')}")}
        runBlocking{people.forEach{app.repository.saveStudent(it)}}
        val vm=ViewModelProvider(compose.activity)[DiaryViewModel::class.java]
        compose.waitUntil(10000){vm.data.value.roster(c.id).size==22}
        val before=runBlocking{app.backupManager.snapshot()};reports();hideMode();toggle(a.id);toggle(people.last().id)
        compose.activityRule.scenario.recreate();waitTag("export-layout-list")
        compose.onNodeWithTag("export-layout-list").performScrollToNode(hasTestTag("export-hide-toggle-${a.id}"));checkbox(a.id).assertIsOn()
        compose.onNodeWithTag("export-layout-list").performScrollToNode(hasTestTag("export-hide-toggle-${people.last().id}"));compose.onNodeWithTag("export-hide-toggle-${people.last().id}").assertIsOn()
        top();shot("01-selecao");conclude()
        val after=runBlocking{app.backupManager.snapshot()};assertEquals(before,after.copy(exportRows=emptyList()))
        assertEquals(2,after.exportRows.count{it.classId==c.id&&it.manualHidden})
        hideMode();checkbox(a.id).assertIsOn();toggle(a.id);conclude()
        assertFalse(runBlocking{app.repository.dao.exportRows(c.id).first{it.studentId==a.id}.manualHidden})
    }
    @Test fun cancelAndNavigationGuardPreserveIntentUntilExplicitDiscard() {
        val before=runBlocking{app.backupManager.snapshot()};reports();hideMode();toggle(a.id)
        compose.onNodeWithTag("class-selector").performClick();compose.onNodeWithText(h.name,useUnmergedTree=true).performClick();waitText("Descartar alterações de exportação?")
        compose.onNode(hasText("Cancelar") and hasAnyAncestor(isDialog())).performClick();checkbox(a.id).assertIsOn()
        compose.activityRule.scenario.onActivity{it.onBackPressedDispatcher.onBackPressed()};waitText("Descartar alterações de exportação?")
        compose.onNode(hasText("Cancelar") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithText("Backup").performClick();waitText("Descartar alterações de exportação?");compose.onNode(hasText("Cancelar") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithTag("nav-3").performClick();waitText("Descartar alterações de exportação?");compose.onNodeWithText("Confirmar").performClick();waitTag("reports-list")
        assertEquals(before,runBlocking{app.backupManager.snapshot()})
        compose.onNodeWithTag("nav-0").performClick();reports();hideMode();checkbox(a.id).assertIsOff()
        toggle(a.id);top();compose.onNodeWithTag("export-cancel").performClick();waitTag("export-organize")
        assertEquals(before,runBlocking{app.backupManager.snapshot()})
        compose.onNodeWithTag("nav-0").performClick();compose.onNodeWithText("Gerenciar alunos").performScrollTo().performClick();waitTag("roster-list")
        compose.onAllNodesWithText("Editar").onFirst().performScrollTo().performClick();waitText("Editar aluno")
    }
    @Test fun independentOrderBlankPreviewAndRenamePersistWithoutRenumberingRoster() {
        val before=runBlocking{app.backupManager.snapshot()};reports();organize()
        compose.onNodeWithTag("export-layout-list").performScrollToNode(hasTestTag("export-up-student:${b.id}"));compose.onNodeWithTag("export-up-student:${b.id}").performClick()
        compose.onNodeWithTag("export-blank-after-student:${b.id}").performScrollTo().performClick()
        compose.onNodeWithTag("export-layout-list").performScrollToNode(hasTestTag("export-order-hide-${a.id}"));compose.onNodeWithTag("export-order-hide-${a.id}").performClick()
        top("export-layout-list");compose.onNodeWithTag("export-preview").performClick();waitText("Prévia da sequência • 2 linhas")
        compose.onNodeWithTag("export-layout-list").performScrollToNode(hasTestTag("export-excluded-${a.id}"));shot("02-previa")
        conclude("export-layout-list")
        val after=runBlocking{app.backupManager.snapshot()};assertEquals(before,after.copy(exportRows=emptyList()))
        assertEquals(listOf(b.id,null,a.id),runBlocking{app.repository.dao.exportRows(c.id)}.map{it.studentId})
        runBlocking{app.repository.saveStudent(a.copy(name="Estudante Fictício Renomeado"));app.repository.saveStudent(a.copy(id="ui-exp-new",personId="ui-exp-new-person",number=3,name="AAAA Estudante Fictício Novo"))}
        val vm=ViewModelProvider(compose.activity)[DiaryViewModel::class.java]
        compose.waitUntil(10000){vm.data.value.roster(c.id).size==3}
        organize();val rows=runBlocking{app.repository.dao.exportRows(c.id)};assertEquals(listOf(b.id,null,a.id),rows.map{it.studentId})
        compose.activityRule.scenario.recreate();waitTag("export-layout-list")
        top("export-layout-list");compose.onNodeWithTag("export-preview").performClick();waitText("Prévia da sequência • 3 linhas")
        assertEquals(listOf(1,2,3),runBlocking{app.repository.dao.roster(c.id)}.map{it.number})
    }
    @Test fun automaticallyExcludedSourceIsDistinctFromManualCheckboxAndDestinationStartsIncluded() {
        val p=runBlocking{app.repository.previewRelocation(a.id,h.id,null,"2026-09-01").also{app.repository.confirmRelocation(it)}}
        val vm=ViewModelProvider(compose.activity)[DiaryViewModel::class.java]
        compose.waitUntil(10000){vm.data.value.relocations.any{it.sourceStudentId==a.id}}
        reports();compose.onNodeWithTag("show-relocated").performScrollTo().performClick();hideMode()
        compose.onNodeWithTag("export-layout-list").performScrollToNode(hasTestTag("export-hide-toggle-${a.id}"));checkbox(a.id).assertIsOff()
        compose.onNodeWithTag("export-auto-${a.id}",useUnmergedTree=true).assertTextContains("Exclusão automática",substring=true);shot("03-remanejado")
        top();compose.onNodeWithTag("export-cancel").performClick()
        compose.onNodeWithTag("class-selector").performClick();compose.onNodeWithText(h.name,useUnmergedTree=true).performClick()
        compose.waitUntil(10000){vm.selection.value?.classId==h.id && compose.onAllNodesWithTag("export-organize").fetchSemanticsNodes().isNotEmpty()}
        reports();hideMode();compose.onNodeWithTag("export-layout-list").performScrollToNode(hasTestTag("export-hide-toggle-${p.target!!.id}"));compose.onNodeWithTag("export-hide-toggle-${p.target!!.id}").assertIsOff()
        assertEquals("Ativo",runBlocking{app.repository.dao.student(p.target!!.id)!!.status})
    }
    @Test fun cancellingBlankInsertionAndRemovalDoesNotWriteAnyPreference() {
        val before=runBlocking{app.backupManager.snapshot()};reports();organize()
        compose.onNodeWithTag("export-add-blank").performScrollTo().performClick()
        compose.onNodeWithTag("export-layout-list").performScrollToNode(hasText("Remover linha vazia"));compose.onNodeWithText("Remover linha vazia").performClick()
        top("export-layout-list");compose.onNodeWithTag("export-add-blank").performScrollTo().performClick()
        top("export-layout-list");compose.onNodeWithTag("export-cancel").performClick();waitTag("export-organize")
        assertEquals(before,runBlocking{app.backupManager.snapshot()})
    }
}
