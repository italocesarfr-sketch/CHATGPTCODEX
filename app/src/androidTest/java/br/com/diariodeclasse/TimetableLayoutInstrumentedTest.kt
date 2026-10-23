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
import br.com.diariodeclasse.ui.*
import br.com.diariodeclasse.ui.theme.NoctuaTheme
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.time.LocalDate

class TimetableLayoutInstrumentedTest {
    @get:Rule val compose=createComposeRule()
    private val app=ApplicationProvider.getApplicationContext<DiaryApplication>()
    private lateinit var vm:DiaryViewModel
    private val store=androidx.lifecycle.ViewModelStore()
    private var scale by mutableFloatStateOf(1f)
    private var mode by mutableStateOf("Horários")
    @Before fun setup(){
        vm=DiaryViewModel(app);store.put("weekly-layout",vm)
        compose.setContent {
            val density=LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density,scale)) {
                NoctuaTheme { Surface(Modifier.width(320.dp).height(560.dp)) {
                    ClassJournalArea(vm,DiarySnapshot(loaded=true),null,1,{}, {},mode,{mode=it},null,{}, {},{it()},year=2026)
                } }
            }
        }
    }
    @After fun clean(){compose.runOnIdle{store.clear()}}
    @Test fun weeklyAreaWorksWithoutAnySelectedClassAndKeepsAccessibleControlsAtLargeFonts(){
        for(font in listOf(1f,1.3f,1.6f)) {
            compose.runOnIdle{scale=font;mode="Horários"}
            compose.onNodeWithTag("weekly-timetable").assertExists()
            if(font==1f) compose.onNodeWithTag("weekday-${LocalDate.now().dayOfWeek.value}").assertIsSelected()
            compose.onNodeWithTag("class-mode-Horários").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            compose.onNodeWithTag("class-mode-Chamada").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            compose.onNodeWithTag("class-mode-Diário").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            for(d in 1..7) compose.onNodeWithTag("weekday-$d").performScrollTo().performClick().assertIsSelected().assertHeightIsAtLeast(48.dp)
            val root=compose.onRoot().fetchSemanticsNode().boundsInRoot
            for(s in listOf("class-mode-Horários","class-mode-Chamada","class-mode-Diário")) {
                val b=compose.onNodeWithTag(s).fetchSemanticsNode().boundsInRoot
                assertTrue(b.left>=root.left&&b.right<=root.right)
                val label=compose.onNodeWithTag(s.replace("class-mode-","class-mode-label-"),useUnmergedTree=true)
                val layouts=mutableListOf<TextLayoutResult>()
                label.performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(layouts)}
                val text=layouts.single();val bounds=label.fetchSemanticsNode().boundsInRoot
                assertEquals("Palavra quebrada em fonte $font",1,text.lineCount)
                assertFalse(text.isLineEllipsized(0));assertFalse(text.multiParagraph.didExceedMaxLines)
                assertEquals(text.layoutInput.text.length,text.getLineEnd(0,true))
                val boxes=(0 until text.layoutInput.text.length).map{text.getBoundingBox(it)}
                assertTrue("Rótulo cortado em fonte $font",boxes.maxOf{it.right}-boxes.minOf{it.left}<=bounds.width+0.5f)
                assertTrue(bounds.left>=b.left-0.5f && bounds.right<=b.right+0.5f)
            }
            val dir=File(app.filesDir,"weekly-screenshots");dir.mkdirs()
            File(dir,"05-sem-turma-fonte-$font.png").outputStream().use{compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
            compose.onNodeWithTag("class-mode-Diário").performClick();compose.onNodeWithText("Selecione uma turma para consultar o diário.").assertExists()
            compose.onNodeWithTag("class-mode-Horários").performClick()
        }
    }
}
