package br.com.diariodeclasse

import android.content.Context
import android.graphics.Bitmap
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelProvider
import br.com.diariodeclasse.ui.DiaryViewModel
import br.com.diariodeclasse.backup.BackupViewModel
import br.com.diariodeclasse.backup.BackupCodec
import java.io.ByteArrayOutputStream
import java.io.IOException
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.domain.CsvRoster
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

class PostTwelveUiInstrumentedTest {
    @get:Rule(order=0) val compose=createAndroidComposeRule<MainActivity>()
    @get:Rule(order=1) val entry=enterNoctuaRule(compose)
    private lateinit var app:DiaryApplication
    private var previous:AppSelection?=null
    private val c=SchoolClass("ux12-ui-c",name="Turma Fictícia Atual",subject="História",year=2026)
    private val h=c.copy(id="ux12-ui-h",name="Turma Fictícia Anterior",year=2025)
    private val a=Student("ux12-ui-a","ux12-ui-pa",c.id,"Estudante Fictícia Áurea",1,"2026-01-01")
    private val b=a.copy(id="ux12-ui-b",personId="ux12-ui-pb",classId=h.id,joined="2025-01-01")
    private val day="2026-08-28"
    @Before fun setup()=runBlocking {
        app=compose.activity.application as DiaryApplication;previous=app.selectionStore.read();app.commands.awaitPendingWrites()
        while(app.commands.messages.tryReceive().isSuccess){}
        app.repository.saveClass(c,"2026-01-01");app.repository.saveClass(h,"2025-01-01");app.repository.saveStudent(a);app.repository.saveStudent(b)
        val q=Activity("ux12-ui-q",c.id,"Prova Fictícia Atual",date=day,term=3,maximum=10.0)
        val other=q.copy(id="ux12-ui-qh",classId=h.id,name="Prova Fictícia Anterior",date="2025-08-28")
        app.repository.saveActivity(q);app.repository.saveActivity(other);app.repository.saveGrade(q.id,a.id,0.0);app.repository.saveGrade(other.id,b.id,7.5)
        app.repository.setDayHours(c.id,day,2);app.repository.setDayHours(c.id,"2026-08-29",1)
        app.repository.mark("${c.id}:2026-08-29:1",a.id,"FJ")
        app.selectionStore.write(AppSelection(2026,c.id));waitTag("class-selector")
        compose.waitUntil(10000){compose.onAllNodesWithText(c.name).fetchSemanticsNodes().isNotEmpty()}
        val vm=ViewModelProvider(compose.activity.academicOwner)[DiaryViewModel::class.java]
        compose.waitUntil(10000){vm.data.value.lessons.count{it.classId==c.id}==3&&vm.data.value.grades.any{it.activityId==other.id&&it.value==7.5}&&vm.data.value.marks.any{it.studentId==a.id&&it.mark=="FJ"}}
    }
    @After fun clean()=runBlocking {
        app.commands.awaitPendingWrites()
        for(id in listOf(c.id,h.id))app.repository.dao.schoolClass(id)?.let{val p=app.repository.previewClassDeletion(id);app.repository.deleteClass(p,p.confirmation)}
        app.selectionStore.write(previous);while(app.commands.messages.tryReceive().isSuccess){}
    }
    private fun tag(value:String)=compose.onNodeWithTag(value)
    private fun waitTag(value:String){compose.waitUntil(10000){compose.onAllNodesWithTag(value).fetchSemanticsNodes().isNotEmpty()}}
    private fun waitText(value:String){compose.waitUntil(10000){compose.onAllNodesWithText(value,substring=true).fetchSemanticsNodes().isNotEmpty()}}
    private fun hideKeyboard(){compose.runOnIdle{(app.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(compose.activity.window.decorView.windowToken,0)}}
    private fun shot(name:String){
        compose.waitForIdle();val f=File(app.filesDir,"ux12-screenshots");f.mkdirs()
        val dialogs=compose.onAllNodes(isDialog())
        val root=if(dialogs.fetchSemanticsNodes().isNotEmpty())dialogs.onLast()else compose.onRoot()
        File(f,"$name.png").outputStream().use{root.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    private fun report(){tag("nav-3").performClick();compose.onNodeWithText("3º bim.").performClick();waitTag("reports-list")}
    private fun search(value:String){tag("reports-list").performScrollToIndex(0);tag("report-search").performTextReplacement(value);tag("report-search").performImeAction()}
    private fun assertGrade(student:Student,value:String){tag("reports-list").performScrollToNode(hasTestTag("report-row-${student.id}"));compose.onNodeWithTag("report-row-${student.id}-current",useUnmergedTree=true).onChild().assertTextEquals(value)}
    private fun pressBack() {
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        val now=android.os.SystemClock.uptimeMillis()
        automation.injectInputEvent(android.view.KeyEvent(now,now,android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.KEYCODE_BACK,0),true)
        automation.injectInputEvent(android.view.KeyEvent(now,now,android.view.KeyEvent.ACTION_UP,android.view.KeyEvent.KEYCODE_BACK,0),true)
        compose.waitForIdle()
    }
    private fun openImport() {
        compose.onNodeWithText("Gerenciar alunos").performScrollTo().performClick()
        tag("import-review-open").performScrollTo().performClick();waitText("Conferir importação")
    }
    private fun importData()=runBlocking {
        app.repository.appendDrafts(c.id,listOf(CsvRoster.Row("Estudante Fictício Beta",2),CsvRoster.Row("Estudante Fictício Gama",3)))
        app.repository.appendDrafts(h.id,listOf(CsvRoster.Row("Estudante Fictício Outra Importação",2)))
    }
    @Test fun importBackAsksKeepAndroidDismissCancelsAndNoDiscardsOnlyThisImport() {
        importData();val before=runBlocking{app.backupManager.snapshot()};openImport()
        compose.onNodeWithText("2 alunos").assertExists()
        tag("import-back").performScrollTo().performClick();waitText("Deseja manter o rascunho?")
        pressBack();compose.onNodeWithText("Deseja manter o rascunho?").assertDoesNotExist()
        assertEquals(before,runBlocking{app.backupManager.snapshot()})
        tag("import-back").performClick();tag("import-keep").performClick();waitTag("roster-list")
        assertEquals(before,runBlocking{app.backupManager.snapshot()})
        tag("import-review-open").performScrollTo().performClick();waitTag("import-select-all")
        tag("import-select-all").performScrollTo().performClick()
        compose.waitUntil(10000){runBlocking{app.repository.dao.draftRows(c.id).all{it.reviewed}}}
        tag("import-select-all").performClick()
        compose.waitUntil(10000){runBlocking{app.repository.dao.draftRows(c.id).none{it.reviewed}}}
        shot("01-importacao-limpa")
        tag("nav-3").performClick();waitText("Deseja manter o rascunho?");shot("02-decidir-importacao")
        tag("import-discard").performClick();waitTag("reports-list")
        assertEquals(before.copy(drafts=before.drafts.filterNot{it.classId==c.id}),runBlocking{app.backupManager.snapshot()})
    }
    @Test fun headerBackAndClassChangesCannotBypassTheImportDecision() {
        importData();val before=runBlocking{app.backupManager.snapshot()};openImport()
        compose.onNodeWithText("‹ Voltar").performClick();waitText("Deseja manter o rascunho?")
        pressBack();waitTag("import-review-list")
        pressBack();waitText("Deseja manter o rascunho?");tag("import-keep").performClick();waitTag("roster-list")
        tag("import-review-open").performScrollTo().performClick();waitTag("import-review-list")
        tag("class-selector").performClick()
        // Trocar ano também usa a mesma proteção, antes de mudar a seleção global.
        pressBack()
        tag("year-selector").performClick();compose.onNode(hasText("Ano letivo 2025") and hasAnyAncestor(isPopup()),useUnmergedTree=true).performClick()
        waitText("Deseja manter o rascunho?")
        assertEquals(AppSelection(2026,c.id),app.selectionStore.read())
        pressBack();assertEquals(before,runBlocking{app.backupManager.snapshot()})
        tag("import-back").performScrollTo().performClick();tag("import-keep").performClick()
    }
    private fun chooseReport(value:String,current:String) {
        tag("reports-list").performScrollToIndex(0)
        tag("report-mode-$value").performScrollTo().performClick()
    }
    @Test fun threeReportViewsAndGlobalSearchKeepGradesAndRestoreNormalFilters() {
        val before=runBlocking{app.backupManager.snapshot()};report()
        chooseReport("Frequência","Geral")
        compose.onNodeWithTag("report-sort").assertExists()
        search("ficticia aurea")
        tag("report-mode-Frequência").assertIsSelected();tag("report-sort").assertExists()
        for(student in listOf(a,b)) {
            tag("reports-list").performScrollToNode(hasTestTag("report-row-${student.id}"))
            compose.onNodeWithTag("report-row-${student.id}-present",useUnmergedTree=true).assertExists()
            compose.onNodeWithTag("report-row-${student.id}-current",useUnmergedTree=true).assertDoesNotExist()
        }
        shot("03-busca-com-visualizacao-frequencia")
        tag("reports-list").performScrollToIndex(0);tag("report-search-clear").performClick()
        compose.onNodeWithContentDescription("Ordenar alunos: Nº da chamada").assertExists();compose.onNodeWithTag("report-sort").assertExists()
        chooseReport("Notas","Frequência");assertGrade(a,"0,00")
        compose.onAllNodesWithTag("report-row-${a.id}-performed",useUnmergedTree=true).assertCountEquals(0)
        chooseReport("Geral","Notas");assertGrade(a,"0,00")
        compose.onNodeWithTag("report-row-${a.id}-performed",useUnmergedTree=true).assertExists()
        assertEquals(before,runBlocking{app.backupManager.snapshot()})
    }
    private fun openBackup():BackupViewModel {
        compose.onNodeWithText("Backup").performClick();waitText("Backup e restauração")
        compose.onAllNodesWithText("SHA-256",substring=true).assertCountEquals(0)
        compose.onAllNodesWithText("br.com.diariodeclasse",substring=true).assertCountEquals(0)
        return ViewModelProvider(compose.activity.academicOwner)[BackupViewModel::class.java]
    }
    @Test fun backupSuccessIsVisibleOnlyAfterCloseAndWriteFlushCloseFailuresNeverSucceed() {
        val before=runBlocking{app.backupManager.snapshot()};val vm=openBackup()
        var closed=false;val stream=object:ByteArrayOutputStream(){override fun close(){assertFalse(vm.state.value.exportSucceeded);closed=true;super.close()}}
        compose.runOnIdle{vm.exportTo({stream},password="Backup-Ficticio-2026".toCharArray())}
        waitTag("backup-success");tag("backup-success").assertIsDisplayed();assertTrue(closed)
        assertEquals(before,BackupCodec.decode(br.com.diariodeclasse.auth.AccountBackup.open(stream.toByteArray(),fixtureUser.id,"Backup-Ficticio-2026".toCharArray())).snapshot);shot("04-backup-confirmado")
        compose.onNodeWithText("OK").performClick()
        for(stage in listOf("write","flush","close")) {
            val failing=object:java.io.OutputStream(){
                override fun write(b:Int){if(stage=="write")throw IOException("fixture write")}
                override fun flush(){if(stage=="flush")throw IOException("fixture flush")}
                override fun close(){if(stage=="close")throw IOException("fixture close")}
            }
            compose.runOnIdle{vm.exportTo({failing},password="Backup-Ficticio-2026".toCharArray())}
            compose.waitUntil(10000){!vm.state.value.busy&&vm.state.value.message.startsWith("Não foi possível salvar o backup.")}
            tag("backup-success").assertDoesNotExist();assertFalse(vm.state.value.exportSucceeded)
            assertEquals(before,runBlocking{app.backupManager.snapshot()})
        }
    }
    @Test fun cancellingBackupPickerShowsNoSuccessAndChangesNoRecords() {
        val before=runBlocking{app.backupManager.snapshot()};val vm=openBackup()
        compose.onNodeWithTag("backup-password").performScrollTo().performTextInput("Backup-Ficticio-2026")
        compose.onNodeWithText("Criar backup completo").performScrollTo().performClick()
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        compose.waitUntil(10000){automation.rootInActiveWindow?.packageName?.toString()?.let{it!=app.packageName}==true}
        automation.executeShellCommand("input keyevent KEYCODE_BACK").close()
        compose.waitUntil(10000){vm.state.value.message=="Operação cancelada."}
        tag("backup-success").assertDoesNotExist();assertEquals(before,runBlocking{app.backupManager.snapshot()})
    }
}
