package br.com.diariodeclasse

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.test.platform.app.InstrumentationRegistry
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.ui.*
import br.com.diariodeclasse.ui.theme.NoctuaTheme
import org.junit.*
import org.junit.Assert.*
import java.io.File

class StudentDeletionLayoutInstrumentedTest {
    @get:Rule val compose=createComposeRule()
    private var scale by mutableStateOf(1f);private var width by mutableStateOf(288)
    private var showing by mutableStateOf(false);private var saved=0;private var deleted=0
    private val school=SchoolClass("delete-layout",name="1º H fictício",subject="História",year=2026)
    private val student=Student("delete-layout-person","delete-layout-identity",school.id,"Estudante Fictício de Nome Extenso para Conferência de Exclusão Individual",1,"2026-01-01")
    @Before fun setup(){compose.setContent {
        val density=LocalDensity.current.density
        NoctuaTheme {
            CompositionLocalProvider(LocalDensity provides Density(density,scale)) {
                Surface(Modifier.widthIn(max=width.dp).heightIn(max=520.dp).fillMaxSize()) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        StudentFormActions(true,"Salvar aluno",{saved++},{showing=true})
                    }
                }
            }
            // O popup usa a densidade real da janela, verificada nos testes com font_scale.
            if(showing) StudentDeletionDialog(StudentDeletionPreview(student,school,linkedMapOf("Atividades com notas lançadas" to 3,"Registros de frequência" to 12,"Recomposições com nota" to 1,"Observações desta matrícula" to 1),mapOf("P" to 7,"F" to 3,"FJ" to 1,"NR" to 1),true,null,"layout-fixture"),false,null,{showing=false},{deleted++;showing=false})
        }
    }}
    private fun tag(s:String)=compose.onNodeWithTag(s)
    private fun shot(name:String){val app=ApplicationProvider.getApplicationContext<DiaryApplication>();val dir=File(app.filesDir,"student-deletion-screenshots");dir.mkdirs();val root=if(showing)tag("student-delete-dialog")else compose.onRoot();File(dir,"$name.png").outputStream().use{root.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}}
    @Test fun buttonsReflowWithoutClippedTextAndKeepIndependentActions() {
        for((w,f) in listOf(288 to 1f,248 to 1f,248 to 1.6f,320 to 1.3f)) {
            compose.runOnIdle{width=w;scale=f}
            val save=tag("student-save");val remove=tag("student-delete")
            save.performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp);remove.performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            val a=save.getUnclippedBoundsInRoot();val b=remove.getUnclippedBoundsInRoot()
            assertTrue("Botões sobrepostos em $w/$f",a.right<=b.left||a.bottom<=b.top)
            if(w>=280&&f<=1.2f){assertEquals(a.top.value,b.top.value,1f);assertTrue(a.left<b.left)}else assertTrue(a.bottom<=b.top)
            for(id in listOf("student-save-label","student-delete-label")) {
                val layouts=mutableListOf<TextLayoutResult>();tag(id).performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(layouts)}
                val t=layouts.single();assertFalse(t.didOverflowHeight);assertFalse(t.multiParagraph.didExceedMaxLines)
                val bounds=tag(id).fetchSemanticsNode().boundsInRoot
                for(line in 0 until t.lineCount)assertFalse(t.isLineEllipsized(line))
                val glyphs=t.layoutInput.text.indices.map{t.getBoundingBox(it)}
                assertTrue("Texto cortado em $w/$f",glyphs.maxOf{it.right}-glyphs.minOf{it.left}<=bounds.width+0.5f)
            }
            shot("03-acoes-$w-fonte-$f")
        }
        tag("student-save").performClick();assertEquals(1,saved);assertEquals(0,deleted)
        tag("student-delete").performClick();tag("student-delete-cancel").performClick();assertEquals(0,deleted)
    }
    @Test fun confirmationPreservesLongNamesDiagnosisAndReachableActions() {
        compose.runOnIdle{showing=true}
        tag("student-delete-confirm").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        tag("student-delete-cancel").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        val name=compose.onNode(hasText(student.name) and hasAnyAncestor(hasTestTag("student-delete-diagnosis")))
        name.performScrollTo().assertIsDisplayed()
        val expected=InstrumentationRegistry.getArguments().getString("expectedSystemFontScale")?.toFloat()
        val layouts=mutableListOf<TextLayoutResult>();name.performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(layouts)}
        if(expected!=null)assertEquals("Escala real da janela de confirmação",expected,layouts.single().layoutInput.density.fontScale,0.05f)
        shot("04-popup-fonte-${expected?:1f}")
        compose.onNodeWithText("Deseja realmente excluir?").performScrollTo().assertIsDisplayed()
        tag("student-delete-cancel").performClick();assertEquals(0,deleted)
        compose.runOnIdle{showing=true};tag("student-delete-confirm").performClick();assertEquals(1,deleted)
    }
}
