package br.com.diariodeclasse

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
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

    private val school=SchoolClass(id="fj-mock-c",name="Turma Fictícia",subject="História",year=2026)
    private var shown by mutableStateOf(DiarySnapshot(loaded=true))
    private var pixels=1f
    private var writes=0
    private var menuColor=Color.Black
    private fun grid(students:Int=1,hours:Int=1,mark:String?="F") {
        val roster=(1..students).map{Student("fj-mock-s$it","fj-mock-p$it",school.id,"Estudante Fictício $it",it,"2026-01-01")}
        val lessons=(1..hours).map{Lesson("fj-mock-l$it",school.id,"2026-08-28",it,"REALIZADA")}
        shown=DiarySnapshot(classes=listOf(school),students=roster,lessons=lessons,
            marks=if(mark==null)emptyList()else roster.flatMap{s->lessons.map{Attendance(it.id,s.id,mark)}},loaded=true)
        fun save(value:Attendance) { writes++;shown=shown.copy(marks=shown.marks.filterNot{it.lessonId==value.lessonId&&it.studentId==value.studentId}+value) }
        compose.setContent { NoctuaTheme {
            val density=LocalDensity.current.density;val solid=MaterialTheme.colorScheme.surfaceContainer
            SideEffect {pixels=density;menuColor=solid}
            Surface(Modifier.width(320.dp).height(560.dp)) {
                FrequencyGrid(shown,school,shown.lessons,null,true,mark={l,s,value->save(Attendance(l.id,s.id,value))},options={},openDay={},
                    justification={expected,reason->check(shown.marks.any{it==expected});save(AbsenceJustifications.apply(expected,reason))})
            }
        } }
    }
    private fun tag(value:String)=compose.onNodeWithTag(value)
    private fun back(){androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);compose.waitForIdle()}
    private fun scroll(dp:Float){compose.onAllNodesWithTag("frequency-horizontal").onLast().performSemanticsAction(SemanticsActions.ScrollBy){it(dp*pixels,0f)};compose.waitForIdle()}
    @Test fun firstTouchRecordRefreshAndDismissKeepTheSameAbsenceAndSolidSurface() {
        grid(mark=null)
        tag("cell-menu-fj-mock-l1-fj-mock-s1").performClick()
        compose.onNode(isPopup()).assertExists();assertEquals(0,writes)
        compose.runOnIdle { shown=shown.copy(marks=listOf(Attendance("fj-mock-l1","fj-mock-s1","F")),lessons=shown.lessons.map{it.copy(note="Observação fictícia")}) }
        tag("justification-option-Atestado médico").assertExists()
        compose.mainClock.advanceTimeBy(2000);compose.waitForIdle();compose.onNode(isPopup()).assertExists()
        val image=tag("attendance-menu").captureToImage().toPixelMap();val pixel=image[image.width/2,4]
        assertEquals(1f,pixel.alpha,0.001f)
        assertEquals(menuColor.red,pixel.red,0.03f);assertEquals(menuColor.green,pixel.green,0.03f);assertEquals(menuColor.blue,pixel.blue,0.03f)
        shot("03-primeiro-toque-superficie-solida")
        back();compose.onNode(isPopup()).assertDoesNotExist();assertEquals("F",shown.marks.single().mark);assertEquals(0,writes)
        tag("cell-menu-fj-mock-l1-fj-mock-s1").performClick()
        // Um toque enviado ao root Compose é relativo à janela daquele root;
        // (4,4) pode cair dentro do popup ancorado no topo. Injeta um toque
        // de tela abaixo da janela, como o gesto real de dispensa no Android.
        val instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val screenshot=instrumentation.uiAutomation.takeScreenshot()
        val menuBounds=android.graphics.Rect()
        instrumentation.uiAutomation.rootInActiveWindow?.getBoundsInScreen(menuBounds)
        val x=if(menuBounds.left>8)4f else screenshot.width-4f
        val y=screenshot.height/2f
        val dir=File(ApplicationProvider.getApplicationContext<DiaryApplication>().filesDir,"fj-flow-screenshots");dir.mkdirs()
        File(dir,"04-popup-tela-inteira.png").outputStream().use{screenshot.compress(Bitmap.CompressFormat.PNG,100,it)}
        screenshot.recycle()
        android.util.Log.i("FjPopupTest","Popup acessível: $menuBounds; toque fora: $x,$y")
        val down=android.os.SystemClock.uptimeMillis()
        for(action in listOf(android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP)) {
            val event=android.view.MotionEvent.obtain(down,android.os.SystemClock.uptimeMillis(),action,x,y,0)
            event.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
            instrumentation.sendPointerSync(event);event.recycle()
        }
        instrumentation.waitForIdleSync()
        compose.waitUntil(5000){compose.onAllNodes(isPopup()).fetchSemanticsNodes().isEmpty()}
        compose.onNode(isPopup()).assertDoesNotExist();assertEquals(0,writes)
    }
    @Test fun largeButtonsAndSixReasonsUseOneIndicatorAndExplicitRemoval() {
        grid(mark=null)
        val f=tag("cell-fj-mock-l1-fj-mock-s1-F");val p=tag("cell-fj-mock-l1-fj-mock-s1-P")
        f.performClick();f.performClick();compose.onNode(isPopup()).assertDoesNotExist();assertEquals("F",shown.marks.single().mark)
        tag("cell-menu-fj-mock-l1-fj-mock-s1").assertTextEquals("F ▾").performClick()
        tag("justification-remove").assertDoesNotExist();compose.onNodeWithText("Sem motivo").assertDoesNotExist();compose.onNodeWithText("FJ — falta justificada").assertDoesNotExist()
        back();assertEquals("F",shown.marks.single().mark)
        for(reason in JustificationReasons.options) {
            tag("cell-menu-fj-mock-l1-fj-mock-s1").performClick();tag("justification-option-$reason").performScrollTo().performClick()
            compose.onNode(isPopup()).assertDoesNotExist();assertEquals(Attendance("fj-mock-l1","fj-mock-s1","FJ",reason),shown.marks.single())
            tag("cell-menu-fj-mock-l1-fj-mock-s1").assertTextEquals("FJ ▾")
        }
        tag("cell-menu-fj-mock-l1-fj-mock-s1").performClick();tag("justification-remove").performScrollTo().performClick()
        assertEquals("F",shown.marks.single().mark);assertNull(shown.marks.single().reason)
        tag("cell-menu-fj-mock-l1-fj-mock-s1").performClick();tag("justification-option-Outro").performClick();f.performClick()
        assertEquals(Attendance("fj-mock-l1","fj-mock-s1","F"),shown.marks.single());compose.onNode(isPopup()).assertDoesNotExist()
        tag("cell-menu-fj-mock-l1-fj-mock-s1").performClick();tag("justification-option-Atestado médico").performClick();p.performClick();p.performClick()
        assertEquals(Attendance("fj-mock-l1","fj-mock-s1","P"),shown.marks.single());compose.onNode(isPopup()).assertDoesNotExist()
    }
    @Test fun studentsAndHoursKeepTheirOwnReasonsAfterBothScrollDirections() {
        grid(students=60,hours=20)
        val target=Attendance("fj-mock-l3","fj-mock-s60","F")
        tag("frequency-students").performScrollToIndex(59);scroll(270f)
        val indicator=tag("cell-menu-${target.lessonId}-${target.studentId}")
        indicator.assertIsDisplayed().performClick();scroll(50f);compose.onNode(isPopup()).assertExists()
        tag("justification-option-Convocação oficial").performClick()
        assertEquals(target.copy(mark="FJ",reason="Convocação oficial"),shown.marks.single{it.lessonId==target.lessonId&&it.studentId==target.studentId})
        assertTrue(shown.marks.filterNot{it.lessonId==target.lessonId&&it.studentId==target.studentId}.all{it.mark=="F"&&it.reason==null})
        indicator.performClick();tag("frequency-students").performScrollToIndex(0);compose.waitForIdle()
        compose.onNode(isPopup()).assertDoesNotExist()
        tag("frequency-students").performScrollToIndex(59);compose.onNode(isPopup()).assertDoesNotExist();assertEquals(1,writes)
    }
    @Test fun presenceNrAndLegacyFjKeepTheirExistingDataWhenTheIndicatorIsOnlyConsulted() {
        grid(hours=3,mark=null)
        compose.runOnIdle { shown=shown.copy(marks=listOf(Attendance("fj-mock-l1","fj-mock-s1","P"),Attendance("fj-mock-l3","fj-mock-s1","FJ"))) }
        tag("cell-menu-fj-mock-l1-fj-mock-s1").performClick()
        compose.onNodeWithText("FJ — falta justificada").assertExists();tag("justification-option-Outro").assertDoesNotExist();back();assertEquals(0,writes)
        scroll(152f);tag("cell-menu-fj-mock-l2-fj-mock-s1").performClick();tag("justification-option-Outro").assertDoesNotExist();back();assertEquals(0,writes)
        scroll(118f);tag("cell-menu-fj-mock-l3-fj-mock-s1").performClick();tag("justification-remove").performScrollTo().performClick()
        assertEquals(Attendance("fj-mock-l3","fj-mock-s1","F"),shown.marks.single{it.lessonId=="fj-mock-l3"})
        assertEquals("P",shown.marks.single{it.lessonId=="fj-mock-l1"}.mark);assertFalse(shown.marks.any{it.lessonId=="fj-mock-l2"})
    }
}
