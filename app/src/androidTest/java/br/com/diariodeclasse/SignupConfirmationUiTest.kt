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
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import br.com.diariodeclasse.auth.*
import br.com.diariodeclasse.ui.theme.NoctuaTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Mock delivery/verification, real Compose/controller; not hosted Auth or email delivery. */
class SignupConfirmationUiTest {
    @get:Rule val compose=createComposeRule()
    private lateinit var auth:AuthController
    private lateinit var vault:IsolationVault
    private var confirmations=0;private var resends=0;private var closed=0;private var font=1f
    @Before fun show()=runBlocking {
        vault=IsolationVault()
        val client=object:AuthClient by AcademicTestClient() {
            override suspend fun signUp(name:String,email:String,password:String,link:PendingLink){}
            override suspend fun confirmSignup(email:String,code:String):AuthSession {
                confirmations++;if(code!="012345")throw AuthFailure(AuthFailure.Kind.LINK,"Código inválido ou expirado.")
                return fixtureSession()
            }
            override suspend fun resendSignup(email:String,link:PendingLink){resends++}
            override suspend fun logout(session:AuthSession){closed++}
        }
        auth=AuthController(client,vault);auth.restore()
        compose.setContent {NoctuaTheme{Surface(Modifier.width(320.dp).height(560.dp)){
            val density=LocalDensity.current;SideEffect{font=density.fontScale}
            val state by auth.state.collectAsState();AuthForm(state,auth)
        }}}
    }
    private fun tag(s:String)=compose.onNodeWithTag(s)
    private fun type(tag:String,value:String){tag(tag).performScrollTo().performTextReplacement(value)}
    private fun submit(){tag("entry-enter").performScrollTo().performClick();compose.waitUntil(10000){!auth.state.value.loading}}
    @Test fun signupCodeFromAnotherDeviceConfirmsThenRequiresPasswordLogin() {
        tag("entry-create").performScrollTo().performClick()
        type("auth-name","Professor Fictício");type("entry-email",fixtureUser.email)
        type("entry-password","Fixture-Password-2026");type("auth-confirm-password","Fixture-Password-2026");submit()
        tag("entry-password").assertDoesNotExist();tag("auth-confirm-code").assertExists();assertNull(vault.value.session)
        type("auth-confirm-code","012345");submit()
        assertEquals(1,confirmations);assertEquals(1,closed);assertNull(auth.state.value.user);assertNull(vault.value.session)
        tag("auth-confirm-code").assertDoesNotExist();tag("entry-password").assertExists()
        type("entry-password","Fixture-Password-2026");submit();assertEquals(fixtureUser.id,auth.state.value.user!!.id)
    }
    @Test fun invalidCodeStaysInConfirmationWithoutSessionOrAcademicAdmission():Unit=runBlocking {
        auth.beginSignupConfirmation(fixtureUser.email)
        type("auth-confirm-code","999999");submit()
        assertEquals(1,confirmations);assertNull(vault.value.session);assertNull(auth.state.value.user)
        tag("auth-confirm-code").assertExists();tag("auth-message").assertExists()
        assertFalse(vault.value.toString().contains("999999"))
    }
    @Test fun resendIsExplicitAndCooldownPreventsAnImmediateDuplicate():Unit=runBlocking {
        auth.beginSignupConfirmation(fixtureUser.email)
        tag("auth-resend-code").performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(10000){resends==1&&!auth.state.value.loading}
        tag("auth-resend-code").assertIsNotEnabled();assertEquals(1,resends);assertNull(vault.value.session)
    }
    @Test fun confirmationRemainsReachableWithNativeFontScale():Unit=runBlocking {
        auth.beginSignupConfirmation(fixtureUser.email)
        val expected=androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("expectedSystemFontScale") ?: "1.0"
        assertEquals(expected.toFloat(),font,0.01f)
        for(id in listOf("auth-confirm-code","entry-enter","auth-resend-code"))tag(id).performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        val app=ApplicationProvider.getApplicationContext<DiaryApplication>()
        File(app.filesDir,"signup-otp-$expected.png").outputStream().use{compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
        compose.onNodeWithText("Voltar para entrar").performScrollTo().performClick()
        compose.waitUntil(10000){auth.state.value.confirmationEmail==null};tag("entry-password").assertExists()
    }
}
