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
            for(id in listOf(c.id,h.id)){
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
    private fun manage(){compose.onNodeWithTag("manage-class").performScrollTo().performClick();waitTag("class-lifecycle")}
    private fun openExport(){compose.onNodeWithTag("nav-3").performClick();compose.onNodeWithText("3º bim.").performClick();compose.onNodeWithTag("open-export").performClick();waitTag("export-screen")}
    private fun shot(name:String){compose.waitForIdle();val folder=File(app.filesDir,"etapa52-screenshots");folder.mkdirs();val root=if(compose.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty())compose.onNode(isDialog())else compose.onRoot();File(folder,"$name.png").outputStream().use{root.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}}
    private fun clip():String {var value="";compose.runOnIdle{value=(app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip!!.getItemAt(0).text.toString()};return value}
    @Test fun exportPreviewAndBothClipboardModesUseExactSharedProjectionAndDoNotWriteRoom() {
        val before=runBlocking{app.backupManager.snapshot()};openExport()
        compose.onNodeWithTag("export-copy").performScrollTo().performClick()
        val vm=ViewModelProvider(compose.activity)[DiaryViewModel::class.java]
        val table=ExportProjection.build(vm.data.value,c,ExportOptions(term=3))
        assertEquals(SpreadsheetWriter.clipboard(table),clip());assertFalse(clip().contains(b.name));assertTrue(clip().contains(a.name))
        compose.onNodeWithTag("export-copy-grades").performScrollTo().performClick();assertEquals(SpreadsheetWriter.clipboard(table,true),clip());assertFalse(clip().contains(a.name))
        compose.onNodeWithTag("export-notes-preview").performScrollTo().performClick();waitText("Sem nomes ou cabeçalhos.");shot("01-notas-previa")
        assertEquals(before,runBlocking{app.backupManager.snapshot()})
    }
    @Test fun groupedOptionsAnnualAndCsvRefreshPreviewAcrossRecreation() {
        val before=runBlocking{app.backupManager.snapshot()};openExport();compose.onNodeWithTag("export-options").performScrollTo().performClick()
        compose.onNodeWithText("3º bimestre ▾").performScrollTo().performClick();compose.onNodeWithText("Resumo anual",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Notas ▾").performScrollTo().performClick();compose.onNodeWithText("Consolidado",useUnmergedTree=true).performClick()
        compose.onNodeWithText("XLSX ▾").performScrollTo().performClick();compose.onNodeWithText("CSV",useUnmergedTree=true).performClick()
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
    @Test fun archiveRemovesClassFromMainSelectorAllowsReadOnlyHistoryAndCanReactivateAfterRecreation() {
        val before=runBlocking{app.backupManager.snapshot()};manage();compose.onNodeWithTag("archive-class").performClick();compose.onNodeWithText("Confirmar").performClick();waitText("Arquivada • registros escolares preservados")
        assertEquals(before,runBlocking{app.backupManager.snapshot()}.copy(classArchives=emptyList()))
        compose.onNodeWithTag("archived-reports").performScrollTo().performClick();waitText("Turma arquivada • ${c.name}");shot("03-arquivada")
        compose.activityRule.scenario.recreate();waitText("Turma arquivada • ${c.name}")
        compose.onNodeWithText("‹ Voltar").performClick();waitTag("class-lifecycle")
        compose.onNodeWithTag("archive-class").performClick();compose.onNodeWithText("Confirmar").performClick();waitText("Ativa")
        assertEquals(before,runBlocking{app.backupManager.snapshot()})
        compose.onNodeWithText("‹ Voltar").performClick();waitTag("class-selector")
        // Arquivar novamente e consultar pela rota específica, sem reinserir no seletor ativo.
        manage();compose.onNodeWithTag("archive-class").performClick();compose.onNodeWithText("Confirmar").performClick();waitText("Arquivada •")
        compose.onNodeWithTag("nav-0").performClick();waitTag("class-selector");compose.onNodeWithTag("class-selector").performClick()
        compose.onAllNodesWithText(c.name,useUnmergedTree=true).assertCountEquals(0)
        compose.activityRule.scenario.onActivity{it.onBackPressedDispatcher.onBackPressed()}
        compose.onNodeWithTag("open-archives").performScrollTo().performClick();waitTag("archived-${c.id}");compose.onNodeWithTag("archived-${c.id}").performClick();waitText("Arquivada •")
    }
    @Test fun deletionRequiresExactIdentificationCancelDoesNothingAndOtherClassSurvives() {
        val before=runBlocking{app.backupManager.snapshot()};manage();compose.onNodeWithTag("delete-class-preview").performScrollTo().performClick();waitText("Excluir turma permanentemente")
        compose.onNodeWithTag("delete-class-confirm").performScrollTo().assertIsNotEnabled();shot("04-excluir-previa")
        compose.onNodeWithText("Cancelar exclusão").performScrollTo().performClick();assertEquals(before,runBlocking{app.backupManager.snapshot()})
        compose.onNodeWithTag("delete-class-preview").performScrollTo().performClick();waitText("Excluir turma permanentemente")
        compose.onNodeWithTag("delete-class-confirmation").performScrollTo().performTextInput("EXCLUIR ${c.name} (${c.year})")
        compose.onNodeWithTag("delete-class-confirm").performScrollTo().performClick();waitTag("class-selector")
        compose.waitUntil(10000){runBlocking{app.repository.dao.schoolClass(c.id)==null}}
        assertEquals(before.classes.first{it.id==h.id},runBlocking{app.repository.dao.schoolClass(h.id)})
    }
}
