package br.com.diariodeclasse

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.time.LocalDate
import java.time.format.TextStyle

class ClassificationsUiInstrumentedTest {
    @get:Rule(order=0) val compose=createAndroidComposeRule<MainActivity>()
    @get:Rule(order=1) val entry=enterNoctuaRule(compose)
    private lateinit var app:DiaryApplication
    private lateinit var vm:DiaryViewModel
    private var previous:AppSelection?=null
    private val c=SchoolClass(id="classification-ui-class",name="Turma Fictícia Classificações",subject="História",year=2026)
    private val s=Student(id="classification-ui-student",classId=c.id,name="Estudante Fictícia Áurea",number=1,joined="2026-01-01")
    private val day="2026-08-28";private val lesson="${c.id}:$day:1"
    @Before fun setup()=runBlocking {
        app=compose.activity.application as DiaryApplication;previous=app.selectionStore.read();app.commands.awaitPendingWrites()
        app.repository.saveClass(c,"2026-01-01");app.repository.saveStudent(s);app.repository.setDayHours(c.id,day,2)
        app.selectionStore.write(AppSelection(2026,c.id));vm=ViewModelProvider(compose.activity)[DiaryViewModel::class.java]
        compose.waitUntil(10000){vm.selection.value?.classId==c.id&&vm.data.value.lessons.count{it.classId==c.id}==2}
    }
    @After fun cleanup()=runBlocking {
        app.commands.awaitPendingWrites();val p=app.repository.previewClassDeletion(c.id);app.repository.deleteClass(p,p.confirmation)
        app.selectionStore.write(previous)
    }
    private fun tag(t:String)=compose.onNodeWithTag(t)
    private fun waitTag(t:String){compose.waitUntil(10000){compose.onAllNodesWithTag(t).fetchSemanticsNodes().isNotEmpty()}}
    private fun reason(value:String?) {
        tag("reason-$lesson-${s.id}").performScrollTo().performClick()
        tag("justification-option-${value ?: "none"}").performScrollTo().performClick()
        compose.waitUntil(10000){vm.data.value.marks.any{it.lessonId==lesson&&it.studentId==s.id&&it.mark=="FJ"&&it.reason==value}}
    }
    private fun shot(name:String){val d=File(app.filesDir,"classification-screenshots");d.mkdirs();val node=if(compose.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty())compose.onNode(isDialog())else compose.onRoot();File(d,"$name.png").outputStream().use{node.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}}
    @Test fun productionTextCanBeCreatedEditedAndReopenedWithoutChangingExistingActivities() {
        val old=runBlocking{app.backupManager.snapshot()}.activities
        tag("nav-2").performClick();compose.onNodeWithText("3º bim.").performClick()
        compose.onNodeWithText("+ Criar atividade").performScrollTo().performClick()
        compose.onNodeWithText("Nome da atividade").performTextReplacement("Produção Fictícia da Interface")
        compose.onNodeWithText("Prova ▾").performScrollTo().performClick()
        compose.onNode(hasText("Produção textual") and hasAnyAncestor(isPopup()),useUnmergedTree=true).performScrollTo().performClick()
        shot("01-producao-textual")
        compose.onNodeWithText("Salvar e lançar notas").performScrollTo().performClick()
        compose.waitUntil(10000){vm.data.value.activities.any{it.classId==c.id&&it.type=="Produção textual"}}
        compose.onNodeWithText("Editar atividade").performScrollTo().performClick()
        compose.onNodeWithText("Produção textual ▾").assertExists()
        compose.onNodeWithText("Nome da atividade").performTextReplacement("Produção Fictícia Editada")
        compose.onNodeWithText("Salvar alterações").performScrollTo().performClick()
        compose.waitUntil(10000){vm.data.value.activities.any{it.classId==c.id&&it.name=="Produção Fictícia Editada"}}
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Editar atividade").performScrollTo().performClick()
        compose.onNodeWithText("Produção textual ▾").assertExists()
        assertEquals(old,runBlocking{app.backupManager.snapshot()}.activities.filter{it.classId!=c.id})
    }
    @Test fun justifiedReasonAutosavesCanBeChangedClearedAndRemovedBySecondTap() {
        val before=runBlocking{app.backupManager.snapshot()}
        tag("nav-1").performClick();tag("class-mode-Chamada").performClick();waitTag("attendance-calendar")
        val now=LocalDate.now();compose.onNodeWithText("${now.monthValue} • ${now.month.getDisplayName(TextStyle.FULL,brLocale)} ▾").performClick()
        compose.onAllNodesWithText("8 • agosto").onLast().performScrollTo().performClick()
        tag("attendance-calendar").performScrollToNode(hasTestTag("date-$day"));tag("attendance-calendar").performScrollToIndex(2)
        tag("date-$day").performClick();waitTag("daily-frequency")
        tag("cell-menu-$lesson-${s.id}").performScrollTo().performClick();compose.onNodeWithText("FJ — falta justificada").performClick()
        compose.waitUntil(10000){vm.data.value.marks.any{it.lessonId==lesson&&it.studentId==s.id&&it.mark=="FJ"}}
        assertNull(runBlocking{app.repository.dao.attendance(lesson).single().reason})
        for(value in JustificationReasons.options) reason(value)
        shot("02-fj-com-motivo")
        compose.activityRule.scenario.recreate();waitTag("daily-frequency")
        tag("reason-$lesson-${s.id}").assertExists();assertEquals("Outro",runBlocking{app.repository.dao.attendance(lesson).single().reason})
        reason(null);reason("Atestado médico")
        tag("cell-$lesson-${s.id}-P").performScrollTo().performClick()
        compose.waitUntil(10000){runBlocking{app.repository.dao.attendance(lesson).single().let{it.mark=="P"&&it.reason==null}}}
        tag("reason-$lesson-${s.id}").assertDoesNotExist()
        tag("cell-menu-$lesson-${s.id}").performClick();compose.onNodeWithText("FJ — falta justificada").performClick()
        waitTag("reason-$lesson-${s.id}");reason("Convocação oficial")
        tag("cell-menu-$lesson-${s.id}").performScrollTo().performClick();compose.onNodeWithText("FJ — falta justificada").performClick()
        compose.waitUntil(10000){runBlocking{app.repository.dao.attendance(lesson).single().let{it.mark=="NR"&&it.reason==null}}}
        compose.activityRule.scenario.recreate();waitTag("daily-frequency");tag("reason-$lesson-${s.id}").assertDoesNotExist()
        val after=runBlocking{app.backupManager.snapshot()};assertEquals(before.copy(marks=after.marks),after)
    }
}
