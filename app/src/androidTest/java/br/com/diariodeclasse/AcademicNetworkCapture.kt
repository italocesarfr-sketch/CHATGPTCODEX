package br.com.diariodeclasse

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestRule
import java.io.File

/** Native pixels and semantic diagnostics; never changes persistence or drops an assertion. */
internal fun <R:TestRule,A:ComponentActivity> AndroidComposeTestRule<R,A>.academicCapture(
    app:Application,folder:String,label:String,expectedStatus:String?=null,metadata:()->String
) {
    val dir=File(app.filesDir,folder).apply{mkdirs()}
    try {
        onNodeWithTag("auth-account").performClick()
        onNodeWithTag("open-sync").performClick()
        waitUntil(5000) {
            val retry=onAllNodesWithTag("academic-retry").fetchSemanticsNodes().singleOrNull()
            val status=onAllNodesWithTag("academic-status").fetchSemanticsNodes().singleOrNull()
            retry!=null && (if(expectedStatus=="Conflito preservado")!isEnabled().matches(retry) else isEnabled().matches(retry)) &&
                (expectedStatus==null || status!=null && hasText(expectedStatus).matches(status))
        }
        val retry=onNodeWithTag("academic-retry").performScrollTo().assertIsDisplayed()
        if(expectedStatus=="Conflito preservado") {
            retry.assertIsNotEnabled()
            onNodeWithTag("academic-progress").assertDoesNotExist()
        }else retry.assertIsEnabled()
        if(expectedStatus!=null)onNodeWithTag("academic-status").assertTextEquals(expectedStatus)
        File(dir,"$label.png").outputStream().use{onNode(isDialog()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
        onNodeWithText("Concluído").performClick()
    }catch(failure:Throwable) {
        runCatching{File(dir,"$label-state.txt").writeText(metadata())}
        runCatching {
            fun describe(node:androidx.compose.ui.semantics.SemanticsNode,depth:Int):String=
                "${"  ".repeat(depth)}${node.id} ${node.boundsInRoot} ${node.config}\n"+node.children.joinToString(""){describe(it,depth+1)}
            val roots=onAllNodes(isRoot(),useUnmergedTree=true).fetchSemanticsNodes()
            File(dir,"$label-semantics.txt").writeText(roots.joinToString("\n"){describe(it,0)})
        }
        runCatching{InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let{bitmap->File(dir,"$label-failed.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}}
        throw failure
    }
}
