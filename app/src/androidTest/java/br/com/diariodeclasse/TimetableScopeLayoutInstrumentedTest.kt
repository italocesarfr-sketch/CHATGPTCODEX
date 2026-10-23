package br.com.diariodeclasse

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.assertEquals
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.ui.*
import br.com.diariodeclasse.ui.theme.NoctuaTheme
import org.junit.*
import java.io.File

/** Apenas leitura da massa fictícia; não chama Salvar nem modifica o banco. */
class TimetableScopeLayoutInstrumentedTest {
    @get:Rule val compose=createComposeRule()
    private val app=ApplicationProvider.getApplicationContext<DiaryApplication>()
    private val store=androidx.lifecycle.ViewModelStore()
    private var scale by mutableFloatStateOf(1.3f)
    private var export by mutableStateOf(false)
    private val school=SchoolClass(id="scope-layout",name="Turma Fictícia",subject="História",year=2026)
    private val slot=WeeklySlot(classId=school.id,weekday=4,ordinal=1,className=school.name,subject=school.subject,shift=school.shift)
    private val data=DiarySnapshot(loaded=true,classes=listOf(school),timetables=listOf(WeeklyTimetable(year=2026,effective="2026-01-01",revision=1,slots=listOf(slot))),terms=(1..4).map{Term(school.id,it,"2026-${listOf("01","04","07","10")[it-1]}-01","2026-${listOf("03-31","06-30","09-30","12-31")[it-1]}")})
    @Before fun setup(){
        val vm=DiaryViewModel(app);store.put("scope-layout",vm)
        compose.setContent {
            val density=LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density,scale)) {
                NoctuaTheme { Surface(Modifier.width(320.dp).height(560.dp)) {
                    if(export) ExportScreen(data,school,3){} else TimetableScreen(vm,data,2026,{}, {})
                } }
            }
        }
    }
    @After fun clean(){compose.runOnIdle{store.clear()}}
    private val systemFont get()=InstrumentationRegistry.getArguments().getString("expectedSystemFontScale")
    private fun checkDialogFont(label:String) {
        val expected=systemFont?.toFloat() ?: return
        val layouts=mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(label,useUnmergedTree=true).performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(layouts)}
        assertEquals("O diálogo precisa usar a escala real do Android",expected,layouts.single().layoutInput.density.fontScale,0.01f)
    }
    private fun shot(name:String){
        val dir=File(app.filesDir,"weekly-screenshots");dir.mkdirs()
        val root=if(compose.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty())compose.onNode(isDialog())else compose.onRoot()
        File(dir,"${systemFont?.let{"system-$it-"}.orEmpty()}$name.png").outputStream().use{root.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun scopeAndSaveRemainReachableOnCompactPhoneAtLargeFont(){
        compose.onNodeWithTag("add-weekly-hours").performScrollTo().performClick()
        for(font in listOf(1.3f,1.6f)) {
            compose.runOnIdle{scale=font}
            for(option in TimetableScope.entries) compose.onNodeWithTag("timetable-scope-${option.name}").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick().assertIsSelected()
            checkDialogFont("Como deseja aplicar a alteração?")
            shot("11-alcance-fonte-$font")
            compose.onNodeWithTag("review-weekly-hours").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            compose.onNodeWithTag("timetable-effective").assertDoesNotExist()
        }
    }
    @Test fun termChoicesAndSheetFooterRemainReachableAtLargeFont(){
        compose.runOnIdle{export=true;scale=1.6f}
        compose.onNodeWithTag("export-options").performScrollTo().performClick()
        compose.onNodeWithText("3º bimestre ▾").performScrollTo().performClick()
        compose.onNode(hasText("2º bimestre") and hasAnyAncestor(isPopup()),useUnmergedTree=true).performScrollTo().performClick()
        compose.onNodeWithText("2º bimestre ▾").performScrollTo().assertIsDisplayed()
        checkDialogFont("Ajustes da exportação")
        shot("12-abrangencia-fonte-1.6")
        compose.onNodeWithTag("export-settings-done").assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithText("${school.name} • ${school.subject} • 2026 • 2º bimestre").assertExists()
    }
}
