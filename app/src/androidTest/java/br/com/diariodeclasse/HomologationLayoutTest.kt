package br.com.diariodeclasse

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.core.app.ApplicationProvider
import android.graphics.Bitmap
import java.io.File
import br.com.diariodeclasse.auth.*
import br.com.diariodeclasse.ui.theme.NoctuaTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class HomologationLayoutTest {
    @get:Rule val compose=createComposeRule()
    private fun capture(name:String) {
        val app=ApplicationProvider.getApplicationContext<DiaryApplication>()
        val folder=File(app.filesDir,"homologacao-layout").apply{mkdirs()}
        File(folder,"$name.png").outputStream().use{compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun homologationIdentityAndLoginRemainReachableOnCompactLargeFont():Unit=runBlocking {
        val auth=AuthController(AcademicTestClient(),IsolationVault());auth.restore()
        compose.setContent {NoctuaTheme{
            val native=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(native.density,1.6f)){
                Surface(Modifier.width(320.dp).height(560.dp)){
                    HomologationFrame{val state by auth.state.collectAsState();AuthForm(state,auth)}
                }
            }
        }}
        compose.onNodeWithTag("homologacao-banner").assertTextEquals("NOcTuA Homologação").assertIsDisplayed()
        compose.onNodeWithTag("entry-enter").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        capture("login-320-fonte-1.6")
        compose.onNodeWithTag("entry-create").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("auth-confirm-password").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("homologacao-banner").assertIsDisplayed()
        capture("cadastro-320-fonte-1.6")
    }
}
