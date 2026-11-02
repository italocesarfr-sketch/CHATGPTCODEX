package br.com.diariodeclasse
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import br.com.diariodeclasse.sync.academic.*
import br.com.diariodeclasse.ui.theme.NoctuaTheme
import org.junit.*
import org.junit.Assert.*
import java.io.File
class PhaseDPanelLayoutTest {
    @get:Rule val compose=createComposeRule()
    @Test fun truthfulStatesAndActionsFitCompactWidthAndNativeFont() {
        var code by mutableStateOf("");val measured=java.util.concurrent.atomic.AtomicReference(0f)
        compose.setContent {NoctuaTheme{Surface(Modifier.width(320.dp).height(560.dp)) {
            measured.set(LocalDensity.current.fontScale)
            Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
                AcademicPanelContent(AcademicControl(owner="fixture",stage="ACTIVE_D",enabled=true,checked=true,message=code),emptyList(),emptyList(),emptyList(),false,{},{},{})
            }
        }}}
        val expected=InstrumentationRegistry.getArguments().getString("expectedSystemFontScale")?.toFloat() ?: 1f
        compose.waitUntil(10000){kotlin.math.abs(measured.get()-expected)<0.01f}
        assertEquals(expected,measured.get(),0.01f)
        for((value,label) in listOf("" to "Sincronizado","SYNCING" to "Sincronizando","OFFLINE" to "Aguardando internet","RETRY" to "Falha temporária","CONFLICT" to "Conflito preservado","PENDING" to "Pendente de envio")) {
            compose.runOnIdle{code=value};compose.onNodeWithTag("academic-status").performScrollTo().assertTextEquals(label).assertIsDisplayed()
            compose.onNodeWithTag("academic-retry").performScrollTo().assertHeightIsAtLeast(48.dp)
            if(value=="SYNCING")compose.onNodeWithTag("academic-retry").assertIsNotEnabled()
            else compose.onNodeWithTag("academic-retry").assertIsEnabled()
        }
        val app=ApplicationProvider.getApplicationContext<android.content.Context>()
        val folder=File(app.filesDir,"phased-layout").apply{mkdirs()}
        File(folder,"compact-font-$expected.png").outputStream().use{compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
}
