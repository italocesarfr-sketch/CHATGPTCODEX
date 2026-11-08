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
import br.com.diariodeclasse.auth.*
import br.com.diariodeclasse.ui.theme.NoctuaTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class HomologationLayoutTest {
    @get:Rule val compose=createComposeRule()
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
        compose.onNodeWithTag("entry-create").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("auth-confirm-password").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("homologacao-banner").assertIsDisplayed()
    }
}
