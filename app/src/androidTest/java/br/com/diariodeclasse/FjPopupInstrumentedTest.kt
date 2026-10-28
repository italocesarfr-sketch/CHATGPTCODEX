package br.com.diariodeclasse

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
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

class FjPopupInstrumentedTest {
    @get:Rule val compose=createComposeRule()
    private fun shot(name:String) {
        val dir=File(ApplicationProvider.getApplicationContext<DiaryApplication>().filesDir,"fj-flow-screenshots");dir.mkdirs()
        val node=if(compose.onAllNodes(isPopup()).fetchSemanticsNodes().isNotEmpty())compose.onNode(isPopup())else compose.onRoot()
        File(dir,"$name.png").outputStream().use{node.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun firstOpeningSurvivesVisibleColumnWindowShiftWithoutChangingTheOccurrence() {
        val c=SchoolClass(id="fj-window-c",name="Turma Fictícia",subject="História",year=2026)
        val s=Student(id="fj-window-s",classId=c.id,name="Estudante Fictício",number=1,joined="2026-01-01")
        val lessons=(1..40).map{Lesson("fj-window-$it",c.id,"2026-08-28",it,"REALIZADA")}
        val target=lessons[2];val data=DiarySnapshot(classes=listOf(c),students=listOf(s),lessons=lessons,marks=listOf(Attendance(target.id,s.id,"F")),loaded=true)
        var pixels=1f;var writes=0
        compose.setContent { NoctuaTheme { Surface(Modifier.width(320.dp).height(560.dp)) {
            val density=LocalDensity.current.density;SideEffect{pixels=density}
            FrequencyGrid(data,c,lessons,null,true,mark={_,_,_->writes++},options={},openDay={})
        } } }
        fun scroll(dp:Float){compose.onAllNodesWithTag("frequency-horizontal").onLast().performSemanticsAction(SemanticsActions.ScrollBy){it(dp*pixels,0f)};compose.waitForIdle()}
        scroll(270f)
        val indicator=compose.onNodeWithTag("cell-menu-${target.id}-${s.id}")
        indicator.assertIsDisplayed().performClick();compose.onNode(isPopup()).assertExists();shot("01-primeira-abertura")
        // 270 → 320 dp muda a janela de composição (primeira coluna 0 → 1).
        // A terceira ocorrência continua visível, mas ocupava outro slot sem key.
        scroll(50f);indicator.assertIsDisplayed();shot("02-apos-mudanca-da-janela")
        compose.onNode(isPopup()).assertExists()
        assertEquals(0,writes);assertEquals("F",data.marks.single().mark)
    }
}
