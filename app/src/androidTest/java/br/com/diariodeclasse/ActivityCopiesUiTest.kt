package br.com.diariodeclasse

import android.content.Context
import android.graphics.Bitmap
import android.view.inputmethod.InputMethodManager
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import br.com.diariodeclasse.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

class ActivityCopiesUiTest {
    @get:Rule(order=0) val compose=createAndroidComposeRule<MainActivity>()
    @get:Rule(order=1) val entry=enterNoctuaRule(compose)
    private lateinit var app:DiaryApplication
    private var previous:AppSelection?=null
    private val c=SchoolClass("copies-ui-a",name="1º A Fictícia Cópias",subject="História",year=2026)
    private val b=c.copy(id="copies-ui-b",name="2º B Fictícia Cópias")
    private val a=Activity("copies-ui-main",c.id,"Produção Fictícia",type="Produção textual",date="2026-08-10",term=3,maximum=7.5)
    private val students=listOf(Student("copies-ui-z","copies-person-z",c.id,"Zélia Fictícia",1,"2026-01-01"),Student("copies-ui-a","copies-person-a",c.id,"Álvaro Fictício",2,"2026-01-01"))
    @Before fun setup():Unit=runBlocking{
        app=compose.activity.application as DiaryApplication;app.commands.awaitPendingWrites();previous=app.selectionStore.read()
        for(school in listOf(c,b))app.repository.saveClass(school,"2026-01-01")
        for(s in students)app.repository.saveStudent(s)
        app.repository.saveActivity(a);app.repository.saveGrade(a.id,students[0].id,0.0)
        app.selectionStore.write(AppSelection(2026,c.id));compose.waitUntil(10000){compose.onAllNodesWithText(c.name).fetchSemanticsNodes().isNotEmpty()};waitTag("nav-2");compose.onNodeWithTag("nav-2").performClick();waitTag("activities-list")
        compose.onNodeWithText("3º bim.").performClick()
        compose.waitUntil(10000){compose.onAllNodes(hasText("3º bim.") and isSelected()).fetchSemanticsNodes().isNotEmpty()}
        // Large system fonts place the card below the fold; request its lazy composition.
        compose.onNodeWithTag("activities-list").performScrollToNode(hasTestTag("activity-grades-${a.id}"))
        compose.waitUntil(10000){compose.onAllNodesWithTag("activity-grades-${a.id}").fetchSemanticsNodes().isNotEmpty()}
    }
    @After fun cleanup():Unit=runBlocking{
        app.commands.awaitPendingWrites()
        for(id in listOf(c.id,b.id)){val p=app.repository.previewClassDeletion(id);app.repository.deleteClass(p,p.confirmation)}
        app.selectionStore.write(previous)
    }
    private fun waitTag(tag:String){compose.waitUntil(10000){compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()}}
    private fun hideKeyboard(){compose.runOnIdle{(app.getSystemService(Context.INPUT_METHOD_SERVICE)as InputMethodManager).hideSoftInputFromWindow(compose.activity.window.decorView.windowToken,0)}}
    private fun shot(name:String){hideKeyboard();compose.waitForIdle();val f=File(app.filesDir,"activity-copies-screenshots");f.mkdirs();val screenshot=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot();checkNotNull(screenshot);File(f,"$name.png").outputStream().use{screenshot.compress(Bitmap.CompressFormat.PNG,100,it)};screenshot.recycle()}
    private fun listAction(tag:String){compose.onNodeWithTag("activities-list").performScrollToNode(hasTestTag(tag));compose.onNodeWithTag(tag).performClick()}
    private fun search(q:String){compose.onNodeWithTag("grades-list").performScrollToIndex(1);compose.onNodeWithTag("grade-search").performTextReplacement(q);hideKeyboard()}
    @Test fun gradeSearchSortAndInvalidDraftSurviveFilteringAndRecreation(){
        listAction("activity-grades-${a.id}");waitTag("grades-list")
        search("ALVARO");compose.onNodeWithTag("grades-list").performScrollToNode(hasTestTag("grade-value-${students[1].id}"))
        compose.onNodeWithTag("grade-value-${students[1].id}").performTextReplacement("8") // Invalid, must survive hiding this pupil.
        hideKeyboard();search("1")
        compose.onNodeWithTag("grades-list").performScrollToNode(hasTestTag("grade-value-${students[0].id}"))
        compose.onNodeWithTag("grade-value-${students[0].id}").assertTextContains("0,0")
        search("alvaro");compose.onNodeWithTag("grades-list").performScrollToNode(hasTestTag("grade-value-${students[1].id}"))
        compose.onNodeWithTag("grade-value-${students[1].id}").assertTextContains("8")
        compose.activityRule.scenario.recreate();waitTag("grades-list")
        compose.onNodeWithTag("grades-list").performScrollToNode(hasTestTag("grade-value-${students[1].id}"));compose.onNodeWithTag("grade-value-${students[1].id}").assertTextContains("8")
        compose.onNodeWithTag("grade-value-${students[1].id}").performTextReplacement("7,5");hideKeyboard()
        compose.waitUntil(10000){runBlocking{app.repository.dao.activityGrades(a.id).any{it.studentId==students[1].id&&it.value==7.5}}}
        search("");compose.onNodeWithText("Nº da chamada ▾").performScrollTo().performClick();compose.onNodeWithText("Nome A–Z").performClick()
        search("FICTICIO");shot("01-busca-notas")
        assertEquals(0.0,runBlocking{app.repository.dao.activityGrades(a.id).single{it.studentId==students[0].id}.value!!},0.0)
    }
    @Test fun manualSelectionIsExplicitAndCreatesIndependentCopies(){
        compose.onNodeWithTag("activities-list").performScrollToIndex(0);compose.onNodeWithText("+ Criar atividade").performClick()
        compose.onNodeWithText("Nome da atividade").performTextReplacement("Cópia Fictícia UI")
        compose.onNodeWithText("Data (dd/mm/aaaa)").performScrollTo().performTextReplacement("10/08/2026");hideKeyboard()
        compose.onNodeWithText("Turma atual ▾").performScrollTo().performClick();compose.onNodeWithText("Selecionar turmas").performClick()
        compose.onNodeWithTag("activity-select-all").performScrollTo().performClick()
        compose.onNodeWithTag("activity-deselect-all").performScrollTo().performClick()
        compose.onNodeWithTag("activity-target-summary").performScrollTo().assertTextEquals("0 turmas: ")
        for(id in listOf(c.id,b.id))compose.onNodeWithTag("activity-target-$id").performScrollTo().performClick()
        compose.onNodeWithTag("activity-target-summary").performScrollTo().assertTextEquals("2 turmas: ${c.name}, ${b.name}")
        shot("02-selecao-de-turmas")
        compose.onNodeWithText("Salvar e lançar notas").performScrollTo().performClick();waitTag("grades-list")
        val copies=runBlocking{app.repository.readSnapshot().activities.filter{it.name=="Cópia Fictícia UI"}}
        assertEquals(setOf(c.id,b.id),copies.map{it.classId}.toSet());assertEquals(2,copies.map{it.id}.toSet().size)
        for(copy in copies)assertTrue(runBlocking{app.repository.dao.activityGrades(copy.id).isEmpty()})
    }
    @Test fun selectingOnlyAnotherClassOpensItsOwnGrades(){
        compose.onNodeWithTag("activities-list").performScrollToIndex(0);compose.onNodeWithText("+ Criar atividade").performClick()
        compose.onNodeWithText("Nome da atividade").performTextReplacement("Outra Turma Fictícia")
        compose.onNodeWithText("Data (dd/mm/aaaa)").performScrollTo().performTextReplacement("10/08/2026");hideKeyboard()
        compose.onNodeWithText("Turma atual ▾").performScrollTo().performClick();compose.onNodeWithText("Selecionar turmas").performClick()
        compose.onNodeWithTag("activity-deselect-all").performScrollTo().performClick()
        compose.onNodeWithTag("activity-target-${b.id}").performScrollTo().performClick()
        compose.onNodeWithTag("activity-target-summary").performScrollTo().assertTextEquals("1 turma: ${b.name}")
        compose.onNodeWithText("Salvar e lançar notas").performScrollTo().performClick();waitTag("grades-list")
        compose.waitUntil(10000){compose.onAllNodesWithText(b.name).fetchSemanticsNodes().isNotEmpty()}
        val copies=runBlocking{app.repository.readSnapshot().activities.filter{it.name=="Outra Turma Fictícia"}}
        assertEquals(listOf(b.id),copies.map{it.classId})
    }

}
