package br.com.diariodeclasse

import android.graphics.Bitmap
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import br.com.diariodeclasse.sync.academic.*
import br.com.diariodeclasse.ui.theme.NoctuaTheme
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import java.io.File

class PhaseEReviewLayoutTest {
    @get:Rule val compose=createComposeRule()
    @Test fun explicitChoiceIsReachableAtNativeFontScaleAndCancelDoesNotConfirm() {
        val s=PhaseDFixture.read().canonical();val rows=AcademicRecords.rows(s);val known=rows.associate{it.identity to 1L}
        val evidence=s.grades.take(6).map{g->val key=buildJsonObject{put("activityId",g.activityId);put("studentId",g.studentId)}
            AcademicEvidence("grades",key,1,2,buildJsonObject{put("value",0)},buildJsonObject{put("value",6.0)},buildJsonObject{put("value",JsonNull)})}
        val preview=AcademicPreview("context",false,1,2,rows,rows,rows,known,known,emptyList(),emptyList(),evidence)
        var confirmations=0;var cancelled=false;val scale=java.util.concurrent.atomic.AtomicReference(0f)
        compose.setContent{NoctuaTheme{scale.set(LocalDensity.current.fontScale);AcademicReviewDialog(preview,false,"",{},{cancelled=true},{confirmations++})}}
        val expected=InstrumentationRegistry.getArguments().getString("expectedSystemFontScale")?.toFloat() ?: 1f
        compose.waitUntil(10000){kotlin.math.abs(scale.get()-expected)<0.01f};assertEquals(expected,scale.get(),0.01f)
        compose.onNodeWithTag("academic-confirm-decision").assertIsNotEnabled().assertIsDisplayed()
        compose.onNodeWithText("Próximos").performScrollTo().performClick()
        compose.onNodeWithTag("academic-choice-KEEP_REMOTE").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithTag("academic-confirm-decision").assertIsEnabled().assertIsDisplayed()
        assertEquals(0,confirmations)
        val app=ApplicationProvider.getApplicationContext<android.content.Context>();val dir=File(app.filesDir,"phasee-layout").apply{mkdirs()}
        File(dir,"native-font-$expected.png").outputStream().use{compose.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
        compose.onNodeWithText("Cancelar").performClick();assertTrue(cancelled);assertEquals(0,confirmations)
    }
}
