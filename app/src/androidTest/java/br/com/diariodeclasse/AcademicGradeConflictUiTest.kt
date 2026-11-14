package br.com.diariodeclasse

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import br.com.diariodeclasse.sync.academic.*
import br.com.diariodeclasse.ui.theme.NoctuaTheme
import org.junit.*
import org.junit.Assert.*
import java.io.File

class AcademicGradeConflictUiTest {
    @get:Rule val compose=createComposeRule()
    private val value=NumericGradeConflict("Estudante Fictícia Maria da Silva","1º A Fictícia • História • 2026","Produção textual fictícia",6.0,8.0)
    private fun show(error:String="",choose:(AcademicChoice)->Unit={},close:()->Unit={}) {
        compose.setContent{NoctuaTheme{NumericGradeConflictDialog(value,false,error,{},close,choose)}}
    }
    @Test fun bothValuesAndExplicitHigherChoiceAreAccessibleWithoutAutomaticDecision() {
        var chosen:AcademicChoice?=null;show(choose={chosen=it})
        compose.onNodeWithText(value.student).assertIsDisplayed()
        compose.onNodeWithText(value.classroom).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(value.activity).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Neste aparelho: 6").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Na nuvem: 8").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Sim, manter 8").assertIsDisplayed();compose.onNodeWithText("Não, manter 6").assertIsDisplayed()
        assertNull(chosen);shot("numeric-conflict")
        compose.onNodeWithTag("grade-keep-KEEP_REMOTE").performClick()
        compose.runOnIdle{assertEquals(AcademicChoice.KEEP_REMOTE,chosen)}
    }
    @Test fun lowerGradeRequiresItsOwnExplicitButton() {
        var chosen:AcademicChoice?=null;show(choose={chosen=it})
        compose.onNodeWithText("Não, manter 6").assertIsDisplayed().performClick()
        compose.runOnIdle{assertEquals(AcademicChoice.KEEP_LOCAL,chosen)}
    }
    @Test fun dismissDoesNotMakeAChoice() {
        var chosen:AcademicChoice?=null;var dismissed=false;show(choose={chosen=it},close={dismissed=true})
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);compose.runOnIdle{assertTrue(dismissed);assertNull(chosen)}
    }
    @Test fun stalePreviewDisablesBothChoicesAndAllowsRefresh() {
        show("As versões mudaram. Atualize a prévia antes de decidir novamente.")
        compose.onNodeWithTag("grade-keep-KEEP_LOCAL").assertIsNotEnabled()
        compose.onNodeWithTag("grade-keep-KEEP_REMOTE").assertIsNotEnabled()
        compose.onNodeWithTag("grade-conflict-refresh").performScrollTo().assertIsDisplayed().assertIsEnabled();shot("stale-preview")
    }
    private fun shot(name:String) {
        compose.waitForIdle()
        val ctx=InstrumentationRegistry.getInstrumentation().targetContext
        val dir=File(ctx.filesDir,"grade-conflict-screenshots").apply{mkdirs()}
        val scale=ctx.resources.configuration.fontScale
        requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()).let{b->
            File(dir,"$name-font-$scale.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)}
        }
    }
}
