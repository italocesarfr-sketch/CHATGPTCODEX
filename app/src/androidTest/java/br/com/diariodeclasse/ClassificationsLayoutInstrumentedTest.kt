package br.com.diariodeclasse

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.ui.FrequencyGrid
import br.com.diariodeclasse.ui.theme.NoctuaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class ClassificationsLayoutInstrumentedTest {
    @get:Rule val compose=createComposeRule()
    @Test fun optionalReasonStaysBelowButtonsAndPopupFitsCompactScreenAtLargeFonts() {
        val c=SchoolClass(id="reason-layout-c",name="Turma Fictícia",subject="História",year=2026)
        val s=Student(id="reason-layout-s",classId=c.id,name="Estudante Fictícia de Nome Comprido",number=1,joined="2026-01-01")
        val lesson=Lesson("reason-layout-l",c.id,"2026-08-28",1,"REALIZADA")
        var scale by mutableFloatStateOf(1f)
        var record by mutableStateOf(Attendance(lesson.id,s.id,"FJ","Atividade escolar ou esportiva"))
        compose.setContent {
            val density=LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density,scale)) {
                NoctuaTheme { Surface(Modifier.width(320.dp).height(560.dp)) {
                    FrequencyGrid(DiarySnapshot(classes=listOf(c),students=listOf(s),lessons=listOf(lesson),marks=listOf(record),loaded=true),c,listOf(lesson),null,true,
                        mark={_,_,_->},options={},openDay={},justification={current,value->record=current.copy(reason=value)})
                } }
            }
        }
        for(font in listOf(1f,1.3f,1.6f)) {
            compose.runOnIdle{scale=font;record=record.copy(reason="Atividade escolar ou esportiva")}
            val p=compose.onNodeWithTag("cell-${lesson.id}-${s.id}-P");val f=compose.onNodeWithTag("cell-${lesson.id}-${s.id}-F")
            val reason=compose.onNodeWithTag("reason-${lesson.id}-${s.id}")
            for(node in listOf(p,f,reason))node.assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            assertTrue(reason.fetchSemanticsNode().boundsInRoot.top>=p.fetchSemanticsNode().boundsInRoot.bottom)
            assertTrue(f.fetchSemanticsNode().boundsInRoot.left>=p.fetchSemanticsNode().boundsInRoot.right)
            val root=compose.onRoot().fetchSemanticsNode().boundsInRoot;val bounds=reason.fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.right<=root.right&&bounds.bottom<=root.bottom)
            val dir=File(ApplicationProvider.getApplicationContext<DiaryApplication>().filesDir,"classification-screenshots");dir.mkdirs()
            File(dir,"03-fj-320-fonte-$font.png").outputStream().use{compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
            reason.performClick()
            compose.onNodeWithTag("justification-option-Atividade escolar ou esportiva").performScrollTo().assertIsDisplayed()
            val text=compose.onNodeWithText("Atividade escolar ou esportiva",useUnmergedTree=true)
            val layouts=mutableListOf<TextLayoutResult>();text.performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(layouts)}
            assertFalse(layouts.single().multiParagraph.didExceedMaxLines)
            compose.onNodeWithTag("justification-option-none").performScrollTo().performClick()
            compose.runOnIdle{assertNull(record.reason);assertEquals("FJ",record.mark)}
        }
    }
}
