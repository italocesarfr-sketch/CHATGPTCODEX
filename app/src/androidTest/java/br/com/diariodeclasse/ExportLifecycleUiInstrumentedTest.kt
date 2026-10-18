package br.com.diariodeclasse

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.export.*
import br.com.diariodeclasse.ui.DiaryViewModel
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

class ExportLifecycleUiInstrumentedTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private lateinit var app:DiaryApplication
    private var previous:AppSelection?=null
    private val c=SchoolClass(id="ui52-c",name="Turma Fictícia Exportação",subject="História",year=2026)
    private val h=c.copy(id="ui52-h",name="Turma Fictícia Preservada")
    private val a=Student("ui52-a","ui52-person-a",c.id,"Estudante Fictício Áurea",1,"2026-01-01")
    private val b=a.copy(id="ui52-b",personId="ui52-person-b",number=2,name="Estudante Fictício Oculto")
    @Before fun setup()=runBlocking {
        app=compose.activity.application as DiaryApplication;previous=app.selectionStore.read()
        app.repository.saveClass(c,"2026-01-01");app.repository.saveClass(h,"2026-01-01");app.repository.saveStudent(a);app.repository.saveStudent(b)
        val activity=Activity("ui52-activity",c.id,"Avaliação Fictícia B3",date="2026-08-10",term=3,maximum=10.0)
        app.repository.saveActivity(activity);app.repository.saveGrade(activity.id,a.id,0.0);app.repository.saveRecomposition(Recomposition(a.id,3,5.0,"2026-08-20"))
        app.repository.setDayHours(c.id,"2026-08-10",2);app.repository.mark("${c.id}:2026-08-10:1",a.id,"P");app.repository.mark("${c.id}:2026-08-10:2",a.id,"FJ")
        app.repository.saveExportLayout(c.id,emptyList(),listOf(ExportLayout.studentRow(a,0),ExportLayout.studentRow(b,1).copy(manualHidden=true),ExportRow("blank:ui52",c.id,null,2)))
        app.selectionStore.write(AppSelection(2026,c.id));waitTag("class-selector")
        val vm=ViewModelProvider(compose.activity)[DiaryViewModel::class.java]
        compose.waitUntil(10000){vm.selection.value?.classId==c.id&&vm.data.value.exportRows.any{it.classId==c.id}}
    }
    @After fun cleanup()=runBlocking {
        app.database.runInTransaction {
            val sql=app.database.openHelper.writableDatabase
            sql.execSQL("DROP TRIGGER IF EXISTS fail_ui_class_delete")
            for(id in listOf(c.id,h.id)){
                sql.execSQL("DELETE FROM preserved_histories WHERE classId=?",arrayOf(id))
                sql.execSQL("DELETE FROM class_archives WHERE classId=?",arrayOf(id));sql.execSQL("DELETE FROM export_rows WHERE classId=?",arrayOf(id))
                sql.execSQL("DELETE FROM relocations WHERE sourceStudentId IN (SELECT id FROM students WHERE classId=?) OR targetStudentId IN (SELECT id FROM students WHERE classId=?)",arrayOf(id,id))
                sql.execSQL("DELETE FROM recompositions WHERE studentId IN (SELECT id FROM students WHERE classId=?)",arrayOf(id))
                sql.execSQL("DELETE FROM marks WHERE lessonId IN (SELECT id FROM lessons WHERE classId=?)",arrayOf(id));sql.execSQL("DELETE FROM grades WHERE activityId IN (SELECT id FROM activities WHERE classId=?)",arrayOf(id))
                for(t in listOf("import_drafts","activities","lessons","terms","students"))sql.execSQL("DELETE FROM $t WHERE classId=?",arrayOf(id))
                sql.execSQL("DELETE FROM classes WHERE id=?",arrayOf(id))
            }
        };app.selectionStore.write(previous)
    }
    private fun waitTag(tag:String){compose.waitUntil(10000){compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()}}
    private fun waitText(text:String){compose.waitUntil(10000){compose.onAllNodesWithText(text,substring=true).fetchSemanticsNodes().isNotEmpty()}}
    private fun manage(){compose.onNodeWithTag("edit-class").performScrollTo().performClick();waitTag("archive-class")}
    private fun openExport(){compose.onNodeWithTag("nav-3").performClick();compose.onNodeWithText("3º bim.").performClick();compose.onNodeWithTag("open-export").performClick();waitTag("export-screen")}
    private fun shot(name:String){compose.waitForIdle();val folder=File(app.filesDir,"etapa52-screenshots");folder.mkdirs();val root=when {compose.onAllNodesWithTag("delete-class-dialog").fetchSemanticsNodes().isNotEmpty()->compose.onNodeWithTag("delete-class-dialog");compose.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty()->compose.onNode(isDialog());else->compose.onRoot()};File(folder,"$name.png").outputStream().use{root.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}}
    private fun choose(current:String,value:String) {
        compose.onNodeWithText("$current ▾").performScrollTo().performClick()
        compose.waitUntil(10000){compose.onAllNodes(isPopup()).fetchSemanticsNodes().isNotEmpty()}
        compose.onNode(hasText(value) and hasAnyAncestor(isPopup()),useUnmergedTree=true).performClick()
    }
    private fun clip():String {var value="";compose.runOnIdle{value=(app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip!!.getItemAt(0).text.toString()};return value}
    @Test fun exportPreviewAndBothClipboardModesUseExactSharedProjectionAndDoNotWriteRoom() {
        val before=runBlocking{app.backupManager.snapshot()};openExport()
        compose.onNodeWithTag("export-notes-preview").performScrollTo().performClick();waitText("Sem nomes ou cabeçalhos.");shot("01-notas-previa")
        compose.onNodeWithTag("export-copy").performScrollTo().performClick()
        val vm=ViewModelProvider(compose.activity)[DiaryViewModel::class.java]
        val table=ExportProjection.build(vm.data.value,c,ExportOptions(term=3))
        assertEquals(SpreadsheetWriter.clipboard(table),clip());assertFalse(clip().contains(b.name));assertTrue(clip().contains(a.name))
        compose.onNodeWithTag("export-copy-grades").performScrollTo().performClick();assertEquals(SpreadsheetWriter.clipboard(table,true),clip());assertFalse(clip().contains(a.name))
        waitText("Somente notas copiadas.")
        compose.waitUntil(12000){compose.onAllNodesWithText("Somente notas copiadas.").fetchSemanticsNodes().isEmpty()}
        assertEquals(before,runBlocking{app.backupManager.snapshot()})
    }
    @Test fun groupedOptionsAnnualAndCsvRefreshPreviewAcrossRecreation() {
        val before=runBlocking{app.backupManager.snapshot()};openExport();compose.onNodeWithTag("export-options").performScrollTo().performClick()
        choose("3º bimestre","Resumo anual")
        choose("Notas","Consolidado")
        choose("XLSX","CSV")
        compose.onNodeWithTag("export-options").performScrollTo().performClick()
        waitText("Consolidado • Resumo anual • CSV");shot("02-consolidado")
        compose.activityRule.scenario.recreate();waitText("Consolidado • Resumo anual • CSV")
        assertEquals(before,runBlocking{app.backupManager.snapshot()})
    }
    @Test fun realAndroidDocumentPickerCancellationHasNoSuccessAndDoesNotChangeDatabase() {
        val before=runBlocking{app.backupManager.snapshot()};openExport();compose.onNodeWithTag("export-file").performScrollTo().performClick()
        // UI do seletor é externa ao Compose. Aguarda seu pacote, sem sleeps arbitrários.
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        compose.waitUntil(10000){val root=automation.rootInActiveWindow;root?.packageName?.toString()?.let{it!="br.com.diariodeclasse"}==true}
        automation.executeShellCommand("input keyevent KEYCODE_BACK").close()
        waitTag("export-screen");waitText("Exportação cancelada.")
        compose.onAllNodesWithText("Arquivo exportado.").assertCountEquals(0)
        assertEquals(before,runBlocking{app.backupManager.snapshot()})
    }
    private fun openArchived() {
        compose.onNodeWithTag("open-archives").performScrollTo().performClick()
        waitTag("archived-${c.id}");compose.onNodeWithTag("archived-${c.id}").performClick();waitTag("archive-class")
    }
    private fun waitFinished() {
        compose.waitUntil(10000){!ViewModelProvider(compose.activity)[DiaryViewModel::class.java].saving.value && compose.onAllNodesWithTag("archive-class").fetchSemanticsNodes().isEmpty()}
    }
    private fun confirmDeletion() {
        compose.waitUntil(10000){isEnabled().matches(compose.onNodeWithTag("delete-class-confirm").fetchSemanticsNode())}
        compose.onNodeWithTag("delete-class-confirm").performClick()
    }
    @Test fun archiveRemovesClassFromMainSelectorAllowsReadOnlyHistoryAndCanReactivateAfterRecreation() {
        val before=runBlocking{app.backupManager.snapshot()};manage()
        compose.onNodeWithTag("archive-class").performScrollTo().performClick()
        compose.onNodeWithText("Cancelar").performClick()
        assertEquals(before,runBlocking{app.backupManager.snapshot()})
        compose.onNodeWithTag("archive-class").performScrollTo().performClick();compose.onNodeWithText("Confirmar").performClick();waitFinished()
        assertEquals(before.copy(selection=AppSelection(2026,h.id),classArchives=listOf(ClassArchive(c.id))),runBlocking{app.backupManager.snapshot()})
        assertNotEquals(c.id,app.selectionStore.read()?.classId)
        compose.onNodeWithTag("class-selector").performClick()
        compose.onNode(hasText(c.name) and hasAnyAncestor(isPopup()),useUnmergedTree=true).assertDoesNotExist()
        compose.onNode(hasText(h.name) and hasAnyAncestor(isPopup()),useUnmergedTree=true).performClick()
        openArchived()
        compose.onNodeWithTag("archived-reports").performScrollTo().performClick();waitText("Turma arquivada • ${c.name}");shot("03-arquivada")
        compose.activityRule.scenario.recreate();waitText("Turma arquivada • ${c.name}")
        compose.onNodeWithText("‹ Voltar").performClick();waitTag("archive-class")
        compose.onNodeWithTag("archive-class").performScrollTo().performClick();compose.onNodeWithText("Confirmar").performClick();waitFinished()
        assertEquals(before.copy(selection=AppSelection(2026,h.id)),runBlocking{app.backupManager.snapshot()})
        compose.onNodeWithTag("class-selector").performClick()
        compose.onNode(hasText(c.name) and hasAnyAncestor(isPopup()),useUnmergedTree=true).performClick()
        manage();compose.onNodeWithTag("archive-class").performScrollTo().performClick();compose.onNodeWithText("Confirmar").performClick();waitFinished()
        openArchived();compose.onNodeWithText("Arquivada • registros escolares preservados").performScrollTo().assertIsDisplayed()
    }
    @Test fun editIsUnifiedAndSimpleDeletionCancelsWithoutWritingOrTypingAndPreservesOtherClass() {
        val before=runBlocking{app.backupManager.snapshot()}
        compose.onNodeWithTag("manage-class").assertDoesNotExist()
        compose.onNodeWithText("Editar Turma").assertIsDisplayed()
        compose.onNodeWithText("Editar Turma Existente").assertDoesNotExist()
        manage();compose.onNodeWithText("Nome da turma").assertIsDisplayed();shot("05-editar-turma")
        compose.onNodeWithTag("archive-class").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("delete-class-preview").assertIsDisplayed();shot("06-editar-acoes")
        compose.onNodeWithTag("delete-class-preview").performClick();waitText("Excluir turma?")
        compose.onNodeWithText("Deseja realmente excluir a turma ${c.name}?\n\nO procedimento é irreversível.").assertIsDisplayed()
        compose.onNodeWithTag("delete-class-confirmation").assertDoesNotExist()
        compose.onNodeWithText("Digite exatamente:",substring=true).assertDoesNotExist()
        compose.waitUntil(10000){isEnabled().matches(compose.onNodeWithTag("delete-class-cancel").fetchSemanticsNode())};shot("04-excluir-simples")
        compose.onNodeWithText("Não").performClick();assertEquals(before,runBlocking{app.backupManager.snapshot()})
        compose.onNodeWithTag("delete-class-preview").performScrollTo().performClick();waitText("Excluir turma?")
        confirmDeletion()
        compose.waitUntil(10000){runBlocking{app.repository.dao.schoolClass(c.id)==null}}
        waitFinished();assertNotEquals(c.id,app.selectionStore.read()?.classId)
        assertEquals(before.classes.first{it.id==h.id},runBlocking{app.repository.dao.schoolClass(h.id)})
        compose.onNodeWithTag("edit-class").assertIsDisplayed()
    }
    @Test fun deletingRelocatedOriginRequiresConfirmationAndDestinationReportsSurviveRecreation() {
        val move=runBlocking {app.repository.previewRelocation(a.id,h.id,null,"2026-09-01").also {app.repository.confirmRelocation(it)}}
        val target=move.target!!;val before=runBlocking {app.backupManager.snapshot()}
        manage();compose.onNodeWithTag("delete-class-preview").performScrollTo().performClick()
        waitText("Excluir turma?")
        compose.waitUntil(10000){isEnabled().matches(compose.onNodeWithTag("delete-class-cancel").fetchSemanticsNode())}
        compose.onNodeWithText("Não").performClick()
        assertEquals(before,runBlocking {app.backupManager.snapshot()})
        compose.onNodeWithTag("delete-class-preview").performScrollTo().performClick()
        confirmDeletion()
        compose.waitUntil(10000) {runBlocking {app.repository.dao.schoolClass(c.id)==null}}
        waitFinished()
        // A Snackbar temporária fica sobre o conteúdo inferior: aguardar seu fim
        // antes de testar o toque real na linha do boletim.
        compose.waitUntil(12000){compose.onAllNodesWithText("Turma excluída.").fetchSemanticsNodes().isEmpty()}
        waitTag("class-selector");compose.onNodeWithTag("nav-3").performClick();compose.onNodeWithText("3º bim.").performClick()
        val list=compose.onNodeWithTag("reports-list")
        list.performScrollToNode(hasTestTag("report-row-${target.id}"))
        compose.onNodeWithTag("report-row-${target.id}-original",useUnmergedTree=true).onChild().assertTextEquals("0,00")
        compose.onNodeWithTag("report-row-${target.id}-current",useUnmergedTree=true).onChild().assertTextEquals("5,00")
        // O nó mesclado com esse texto é a linha inteira, cujo centro pode ficar
        // fora do viewport quando há várias células históricas altas. Tocar no
        // texto real, na coluna fixa, reproduz o gesto do professor.
        compose.onNodeWithTag("report-row-${target.id}").assertHasClickAction()
        val name=compose.onNodeWithText("${target.number}. ${target.name}",useUnmergedTree=true)
        name.performScrollTo().assertIsDisplayed()
        println("Fictitious history tap: viewport=${list.fetchSemanticsNode().boundsInRoot}; row=${compose.onNodeWithTag("report-row-${target.id}").fetchSemanticsNode().boundsInRoot}; name=${name.fetchSemanticsNode().boundsInRoot}")
        shot("07-historico-no-destino")
        name.performClick()
        try {waitTag("individual-report")} catch(e:Throwable) {
            shot("09-diagnostico-toque-historico")
            throw AssertionError("Não abriu o perfil após toque no nome visível. ${compose.onRoot().printToString(maxDepth=5)}",e)
        }
        shot("08-perfil-historico")
        compose.onNodeWithTag("individual-report").performScrollToNode(hasTestTag("history-enrollment-${a.id}"))
        compose.onNodeWithTag("history-enrollment-${a.id}").assertTextContains("${c.name} (turma excluída)",substring=true)
        compose.activityRule.scenario.recreate();waitTag("individual-report")
        assertEquals(before.students.first {it.id==target.id},runBlocking {app.repository.dao.student(target.id)})
        assertEquals(before.grades.filter {it.studentId==a.id},runBlocking {app.database.backupDao().histories().single().content.grades})
    }
    @Test fun failedDeletionShowsShortErrorKeepsDialogAndRollsBackThenCanRetry() {
        val before=runBlocking{app.backupManager.snapshot()}
        app.database.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_ui_class_delete BEFORE DELETE ON classes WHEN OLD.id='${c.id}' BEGIN SELECT RAISE(ABORT, 'fictitious failure'); END")
        try {
            manage();compose.onNodeWithTag("delete-class-preview").performScrollTo().performClick();confirmDeletion()
            waitText("Não foi possível excluir a turma. Feche e tente novamente.")
            compose.onNodeWithTag("delete-class-dialog").assertExists()
            compose.onAllNodesWithText("Turma excluída.").assertCountEquals(0)
            assertEquals(before,runBlocking{app.backupManager.snapshot()})
            assertEquals(c.id,app.selectionStore.read()?.classId)
        } finally {app.database.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS fail_ui_class_delete")}
        compose.onNodeWithText("Não").performClick()
        compose.onNodeWithTag("delete-class-preview").performScrollTo().performClick();confirmDeletion();waitFinished()
        assertNull(runBlocking{app.repository.dao.schoolClass(c.id)})
    }
}
