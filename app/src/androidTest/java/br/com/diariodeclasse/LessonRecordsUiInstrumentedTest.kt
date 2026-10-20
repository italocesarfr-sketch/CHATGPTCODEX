package br.com.diariodeclasse

import android.content.ClipboardManager
import android.content.Context
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

class LessonRecordsUiInstrumentedTest {
    @get:Rule(order=0) val compose=createAndroidComposeRule<MainActivity>()
    @get:Rule(order=1) val entry=enterNoctuaRule(compose)
    private lateinit var app:DiaryApplication
    private var previous:AppSelection?=null
    private val c=SchoolClass(id="ui53-c",name="Turma Fictícia Registros",subject="História",year=2026)
    private val h=c.copy(id="ui53-h",name="Turma Fictícia Destino")
    private val note=LessonRecord("ui53-record",c.id,"2026-08-12","Título fictício Áurea","Descrição livre\nAcentos, aspas \" e texto para o EducaDF.")
    private val vm get()=ViewModelProvider(compose.activity)[DiaryViewModel::class.java]
    @Before fun setup()=runBlocking {
        app=compose.activity.application as DiaryApplication;previous=app.selectionStore.read()
        app.repository.saveClass(c,"2026-01-01");app.repository.saveClass(h,"2026-01-01");app.selectionStore.write(AppSelection(2026,c.id))
        compose.waitUntil(10000){vm.data.value.loaded&&vm.selection.value?.classId==c.id}
    }
    @After fun cleanup()=runBlocking {
        app.commands.awaitPendingWrites()
        for(id in listOf(c.id,h.id)) app.repository.dao.schoolClass(id)?.let{val p=app.repository.previewClassDeletion(id);app.repository.deleteClass(p,p.confirmation)}
        app.selectionStore.write(previous)
    }
    private fun waitTag(tag:String){compose.waitUntil(10000){compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()}}
    private fun open(){compose.onNodeWithTag("nav-1").performClick();compose.onNodeWithTag("class-mode-Diário").performClick();compose.onNodeWithTag("journal-calendar").performScrollToNode(hasTestTag("journal-all-records"));compose.onNodeWithTag("journal-all-records").performClick();waitTag("lesson-records-list")}
    private fun top(){compose.onNodeWithTag("lesson-records-list").performScrollToIndex(0)}
    private fun scrollRecord(id:String){
        val row=records().first{it.id==id}
        val index=LessonRecords.list(records(),row.classId,null,null).indexOfFirst{it.id==id}+1
        compose.onNodeWithTag("lesson-records-list").performScrollToIndex(index)
    }
    private fun records()=runBlocking{app.database.backupDao().lessonRecords()}.filter{it.classId in listOf(c.id,h.id)}
    private fun seed(){runBlocking{app.repository.saveLessonRecord(note)};compose.waitUntil(10000){vm.data.value.lessonRecords.any{it.id==note.id}}}
    private fun dialogText(text:String)=compose.onNode(hasText(text) and hasAnyAncestor(isDialog()),useUnmergedTree=true)
    private fun shot(name:String){compose.waitForIdle();val folder=File(app.filesDir,"etapa53-screenshots");folder.mkdirs();val node=if(compose.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty())compose.onNode(isDialog())else compose.onRoot();File(folder,"$name.png").outputStream().use{node.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}}
    private fun clip():String {var value="";compose.runOnIdle{value=(app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip!!.getItemAt(0).text.toString()};return value}
    private fun save(){compose.onNodeWithTag("save-lesson-record").performScrollTo().performClick();compose.waitUntil(10000){compose.onAllNodesWithTag("record-title").fetchSemanticsNodes().isEmpty()};compose.waitUntil(10000){compose.onAllNodesWithText("Registro de aula salvo.").fetchSemanticsNodes().isNotEmpty()};compose.waitUntil(12000){compose.onAllNodesWithText("Registro de aula salvo.").fetchSemanticsNodes().isEmpty()}}
    @Test fun createEditAndRecreateDraftThenReopenSavedRecordWithoutAttendance() {
        open();compose.onNodeWithTag("new-lesson-record").performClick()
        compose.onNodeWithTag("record-date").performTextReplacement("12/08/2026")
        compose.onNodeWithTag("record-title").performScrollTo().performTextInput(note.title)
        compose.onNodeWithTag("record-description").performScrollTo().performTextInput(note.description)
        compose.activityRule.scenario.recreate();waitTag("record-title");compose.onNodeWithTag("record-title").assertTextContains(note.title)
        shot("01-editor-facultativo");save()
        compose.waitUntil(10000){records().size==1};val id=records().single().id
        compose.onNodeWithTag("edit-record-$id").performScrollTo().performClick();compose.onNodeWithTag("record-title").performScrollTo().performTextReplacement("Título corrigido");save()
        assertEquals("Título corrigido",records().single().title)
        compose.activityRule.scenario.recreate();waitTag("record-$id");compose.onNodeWithText("Título corrigido").assertExists();shot("02-lista-registros")
        assertTrue(runBlocking{app.database.backupDao().lessons()}.none{it.classId==c.id})
    }
    @Test fun duplicateSameDayAndAnotherClassOnlyPersistAfterExplicitSave() {
        seed();open();compose.onNodeWithTag("duplicate-record-${note.id}").performScrollTo().performClick();assertEquals(1,records().size);save()
        compose.waitUntil(10000){records().size==2};assertTrue(records().all{it.date==note.date&&it.description==note.description})
        scrollRecord(note.id);compose.onNodeWithTag("duplicate-record-${note.id}").performScrollTo().performClick()
        compose.onNodeWithTag("record-class").performScrollTo().performClick()
        compose.onNode(hasText("${h.name} • ${h.subject} • ${h.year}") and hasAnyAncestor(isPopup()),useUnmergedTree=true).performClick()
        compose.onNodeWithTag("record-date").performTextReplacement("13/08/2026");save()
        compose.waitUntil(10000){records().size==3};assertEquals("2026-08-13",records().single{it.classId==h.id}.date)
        assertEquals(note,records().first{it.id==note.id})
    }
    @Test fun deletionCancelKeepsTextAndExplicitConfirmationDeletesOnlyRecord() {
        seed();open();val before=runBlocking{app.backupManager.snapshot()}
        compose.onNodeWithTag("delete-record-${note.id}").performScrollTo().performClick();dialogText("Cancelar").performClick()
        assertEquals(before,runBlocking{app.backupManager.snapshot()})
        compose.onNodeWithTag("delete-record-${note.id}").performScrollTo().performClick();shot("03-exclusao-confirmada");dialogText("Confirmar").performClick()
        compose.waitUntil(10000){records().isEmpty()}
        assertEquals(before.copy(lessonRecords=before.lessonRecords.filterNot{it.id==note.id}),runBlocking{app.backupManager.snapshot()})
    }
    @Test fun copyTitleAndDescriptionExactlyWithoutChangingDatabase() {
        seed();open();val before=runBlocking{app.backupManager.snapshot()}
        compose.onNodeWithTag("copy-title-${note.id}").performScrollTo().performClick();assertEquals(note.title,clip())
        compose.onNodeWithTag("copy-description-${note.id}").performScrollTo().performClick();assertEquals(note.description,clip())
        compose.waitUntil(10000){compose.onAllNodesWithText("Descrição copiada.").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Descrição copiada.").assertExists()
        assertEquals(before,runBlocking{app.backupManager.snapshot()})
    }
    @Test fun classAndDateFiltersDoNotWriteAndDirtyDismissRequiresDecision() {
        seed();runBlocking{app.repository.saveLessonRecord(note.copy(id="ui53-other",classId=h.id))};open()
        compose.onNodeWithTag("record-filters").performClick();compose.onNodeWithTag("records-from").performScrollTo().performTextReplacement("13/08/2026")
        compose.onNodeWithTag("lesson-records-list").performScrollToIndex(1)
        compose.onNodeWithText("Nenhum registro neste período.",substring=true).assertExists()
        compose.onAllNodesWithTag("record-${note.id}").assertCountEquals(0)
        top();compose.onNodeWithTag("records-from").performTextReplacement("12/08/2026");compose.onNodeWithTag("records-until").performScrollTo().performTextReplacement("12/08/2026")
        scrollRecord(note.id);waitTag("record-${note.id}");compose.onAllNodesWithTag("record-ui53-other").assertCountEquals(0)
        top();compose.onNodeWithTag("new-lesson-record").performScrollTo().performClick();compose.onNodeWithTag("record-title").performScrollTo().performTextInput("Texto ainda não salvo")
        dialogText("Voltar").performScrollTo().performClick();dialogText("Cancelar").performClick();compose.onNodeWithTag("record-title").assertTextContains("Texto ainda não salvo")
        dialogText("Voltar").performScrollTo().performClick();dialogText("Confirmar").performClick();assertEquals(2,records().size)
        compose.onNodeWithTag("class-selector").performClick();compose.onNode(hasText(h.name) and hasAnyAncestor(isPopup()),useUnmergedTree=true).performClick()
        waitTag("journal-calendar");compose.onNodeWithTag("journal-calendar").performScrollToNode(hasTestTag("journal-all-records"));compose.onNodeWithTag("journal-all-records").performClick();waitTag("record-ui53-other");compose.onAllNodesWithTag("record-${note.id}").assertCountEquals(0)
    }
    @Test fun archivedClassKeepsReadOnlyRecordsAndCancelledDuplicateDoesNotCreateAnything() {
        seed();runBlocking{app.repository.archiveClass(c.id,true)}
        compose.onNodeWithTag("nav-0").performClick();compose.onNodeWithTag("open-archives").performScrollTo().performClick();waitTag("archived-${c.id}")
        compose.onNodeWithTag("archived-${c.id}").performClick();compose.onNodeWithTag("archived-records").performScrollTo().performClick();waitTag("record-${note.id}")
        compose.onAllNodesWithTag("edit-record-${note.id}").assertCountEquals(0);compose.onAllNodesWithTag("delete-record-${note.id}").assertCountEquals(0)
        compose.onNodeWithTag("duplicate-record-${note.id}").performScrollTo().performClick();dialogText("Voltar").performScrollTo().performClick();dialogText("Confirmar").performClick()
        assertEquals(listOf(note),records());assertTrue(runBlocking{app.database.backupDao().classArchives()}.any{it.classId==c.id})
    }

}
