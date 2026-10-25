package br.com.diariodeclasse

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import br.com.diariodeclasse.backup.diarySnapshot
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.ui.*
import br.com.diariodeclasse.ui.theme.NoctuaTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

class RosterLayoutInstrumentedTest {
    @get:Rule val compose=createComposeRule()
    private lateinit var app:DiaryApplication
    private lateinit var vm:DiaryViewModel
    private lateinit var data:DiarySnapshot
    private val store=androidx.lifecycle.ViewModelStore()
    private val school=SchoolClass("roster-layout-0181",name="Turma Fictícia Layout",subject="História",year=2026)
    private val students=(1..100).map{number->Student("roster-layout-$number","roster-person-$number",school.id,
        "Estudante Fictício ${(101-number).toString().padStart(3,'0')}",number,"2026-01-01")}
    private var width by mutableStateOf(320)
    private var scale by mutableStateOf(1f)
    @Before fun setup()=runBlocking {
        app=ApplicationProvider.getApplicationContext();app.repository.saveClass(school,"2026-01-01")
        app.database.backupDao().insertStudents(students)
        vm=DiaryViewModel(app);store.put("roster-layout",vm)
        data=app.backupManager.snapshot().diarySnapshot()
        compose.setContent {
            val density=LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density,scale)) {NoctuaTheme {
                Surface(Modifier.widthIn(max=width.dp).heightIn(max=560.dp).fillMaxSize()) {RosterScreen(vm,data,school){}}
            }}
        }
    }
    @After fun cleanup()=runBlocking {
        compose.runOnIdle{store.clear()};app.commands.awaitPendingWrites()
        val p=app.repository.previewClassDeletion(school.id);app.repository.deleteClass(p,p.confirmation)
    }
    private fun tag(value:String)=compose.onNodeWithTag(value)
    private fun shot(name:String) {
        val dir=File(app.filesDir,"roster-layout-screenshots");dir.mkdirs()
        File(dir,"$name.png").outputStream().use{compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun headerActionsOrderingAndLongListKeepSpacingAlignmentAndLegibleTargets() {
        val before=runBlocking{app.backupManager.snapshot()}
        for((w,f) in listOf(320 to 1f,320 to 1.6f,360 to 1.3f)) {
            compose.runOnIdle{width=w;scale=f};tag("roster-list").performScrollToIndex(0)
            tag("roster-add").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(50.dp)
            val heading=tag("roster-heading").getUnclippedBoundsInRoot();val add=tag("roster-add").getUnclippedBoundsInRoot()
            val imports=tag("roster-import-actions").getUnclippedBoundsInRoot();val order=tag("roster-organization").getUnclippedBoundsInRoot()
            assertTrue("Cabeçalho e ação principal",add.top-heading.bottom>=23.dp)
            assertTrue("Ação principal e importação",imports.top-add.bottom>=15.dp)
            assertTrue("Importação e organização",order.top-imports.bottom>=15.dp)
            for(bounds in listOf(add,imports,order)) {
                assertEquals("Margem esquerda",heading.left.value,bounds.left.value,1f)
                assertEquals("Margem direita",heading.right.value,bounds.right.value,1f)
            }
            tag("roster-paste").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(50.dp)
            tag("roster-csv").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(50.dp)
            val a=tag("roster-paste").getUnclippedBoundsInRoot();val b=tag("roster-csv").getUnclippedBoundsInRoot()
            assertTrue("Intervalo entre ações secundárias",b.left-a.right>=11.dp)
            assertEquals(a.top.value,b.top.value,1f);assertEquals((a.bottom-a.top).value,(b.bottom-b.top).value,1f)
            for(id in listOf("roster-paste-label","roster-csv-label")) {
                val node=compose.onNodeWithTag(id,useUnmergedTree=true);val layouts=mutableListOf<TextLayoutResult>()
                node.performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(layouts)}
                val text=layouts.single();assertFalse(text.didOverflowHeight);assertFalse(text.multiParagraph.didExceedMaxLines)
                assertFalse((0 until text.lineCount).any{text.isLineEllipsized(it)})
                val bounds=node.fetchSemanticsNode().boundsInRoot;val glyphs=text.layoutInput.text.indices.map{text.getBoundingBox(it)}
                assertTrue("Texto não cortado em $w/$f",glyphs.maxOf{it.right}-glyphs.minOf{it.left}<=bounds.width+0.5f)
            }
            shot("01-acoes-$w-fonte-$f")
            compose.onNodeWithText("Ordem alfabética ▾").performScrollTo().assertIsDisplayed()
            val label=compose.onNodeWithText("Organização da lista",useUnmergedTree=true).getUnclippedBoundsInRoot()
            val selector=compose.onNodeWithText("Ordem alfabética ▾").getUnclippedBoundsInRoot()
            assertTrue("Rótulo separado do seletor",selector.top-label.bottom>=7.dp)
            shot("02-organizacao-$w-fonte-$f")
            tag("roster-list").performScrollToNode(hasTestTag("roster-student-${students.first().id}"))
            val card=tag("roster-student-${students.first().id}");card.assertIsDisplayed()
            assertEquals("Cards alinhados ao cabeçalho",heading.left.value,card.getUnclippedBoundsInRoot().left.value,1f)
            shot("03-lista-longa-$w-fonte-$f")
        }
        assertEquals(before,runBlocking{app.backupManager.snapshot()})
    }
    @Test fun addPasteAndOrderingKeepTheirExistingFlowsWithoutChangingAnySchoolData() {
        val before=runBlocking{app.backupManager.snapshot()}
        tag("roster-add").performScrollTo().performClick()
        compose.onNodeWithText("Cadastrar aluno").assertExists();tag("student-delete").assertDoesNotExist()
        compose.onNodeWithText("Voltar").performScrollTo().performClick()
        tag("roster-paste").performScrollTo().performClick()
        compose.onNodeWithText("Colar lista de alunos").assertExists()
        compose.onNodeWithText("Preparar conferência").assertExists()
        compose.onNodeWithText("Voltar").performScrollTo().performClick()
        tag("roster-csv").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("Ordem alfabética ▾").performScrollTo().performClick()
        compose.onNode(hasText("Ordem da chamada") and hasAnyAncestor(isPopup()),useUnmergedTree=true).performScrollTo().performClick()
        compose.onNodeWithText("Ordem da chamada ▾").assertExists()
        tag("roster-list").performScrollToIndex(0)
        tag("roster-list").performScrollToNode(hasTestTag("roster-student-${students.first().id}"))
        tag("roster-student-${students.first().id}").assertIsDisplayed()
        assertEquals(before,runBlocking{app.backupManager.snapshot()})
    }
}
