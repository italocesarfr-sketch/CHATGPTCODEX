package br.com.diariodeclasse

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
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

class AuthUiInstrumentedTest {
    @get:Rule val compose=createComposeRule()
    private lateinit var auth:AuthController
    private lateinit var vault:IsolationVault
    private var measuredFont=1f
    private var signups=0;private var recoveries=0;private var passwords=0
    @Before fun show()=runBlocking {
        vault=IsolationVault()
        val client=object:AuthClient by AcademicTestClient() {
            override suspend fun signUp(name:String,email:String,password:String,link:PendingLink){signups++;assertEquals("Professor Fictício",name);assertTrue(link.challenge.isNotEmpty())}
            override suspend fun recover(email:String,link:PendingLink){recoveries++}
            override suspend fun password(session:AuthSession,password:String){passwords++}
        }
        auth=AuthController(client,vault);auth.restore()
        compose.setContent {NoctuaTheme{Surface(Modifier.width(320.dp).height(560.dp)) {
            val font=LocalDensity.current.fontScale;SideEffect{measuredFont=font}
            val state by auth.state.collectAsState();AuthForm(state,auth)
        }}}
    }
    private fun tag(value:String)=compose.onNodeWithTag(value)
    private fun type(value:String,text:String){tag(value).performScrollTo().performTextReplacement(text)}
    private fun submit(){tag("entry-enter").performScrollTo().performClick();compose.waitUntil(5000){!auth.state.value.loading}}
    private fun shot(name:String) {
        val app=ApplicationProvider.getApplicationContext<DiaryApplication>()
        val folder=File(app.filesDir,"auth-screenshots").apply{mkdirs()}
        File(folder,"$name.png").outputStream().use{compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun passwordAndConfirmationVisibilityAreIndependent(){
        tag("entry-create").performScrollTo().performClick()
        type("entry-password","Ficticia-2026");type("auth-confirm-password","Ficticia-2026")
        tag("entry-show-password").performScrollTo().performClick()
        tag("entry-password").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password).not())
        tag("auth-confirm-password").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        tag("auth-show-confirmation").performScrollTo().performClick()
        tag("auth-confirm-password").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password).not())
        tag("entry-show-password").performScrollTo().performClick()
        tag("entry-password").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        tag("auth-confirm-password").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password).not())
    }
    @Test fun blankAndIncorrectCredentialsNeverOpenAnAcademicScreenAndVisibilityStillWorks() {
        submit();assertNull(auth.state.value.user);tag("auth-message").assertExists()
        type("entry-email","professor@example.invalid");type("entry-password","incorreta-ficticia")
        tag("entry-password").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        tag("entry-show-password").performClick();tag("entry-password").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password).not())
        submit();assertNull(auth.state.value.user);tag("entry-email").assertExists();compose.onNodeWithTag("nav-0").assertDoesNotExist()
    }
    @Test fun signupAndRecoveryUseTheClientAndNeverCreateLocalAcademicRecords():Unit=runBlocking {
        val before=ApplicationProvider.getApplicationContext<DiaryApplication>().backupManager.snapshot()
        tag("entry-create").performScrollTo().performClick()
        type("auth-name","Professor Fictício");type("entry-email","professor@example.invalid")
        type("entry-password","Senha-ficticia-2026");type("auth-confirm-password","diferente");submit();assertEquals(0,signups)
        type("entry-password","Senha-ficticia-2026");type("auth-confirm-password","Senha-ficticia-2026");submit();assertEquals(1,signups);assertNull(auth.state.value.user)
        compose.onNodeWithText("Voltar para entrar").performScrollTo().performClick()
        tag("entry-forgot").performScrollTo().performClick();submit();assertEquals(1,recoveries)
        assertNull(auth.state.value.user);assertEquals(before,ApplicationProvider.getApplicationContext<DiaryApplication>().backupManager.snapshot())
    }
    @Test fun allFormsRemainScrollableAndReachableWithTheNativeFontScale() {
        val font=androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("expectedSystemFontScale") ?: "1.0"
        assertEquals(font.toFloat(),measuredFont,0.01f)
        for(mode in listOf("login","signup","forgot")) {
            if(mode=="signup")tag("entry-create").performScrollTo().performClick()
            if(mode=="forgot")tag("entry-forgot").performScrollTo().performClick()
            for(id in listOf("entry-email","entry-enter")+if(mode=="signup")listOf("auth-name","auth-confirm-password")else emptyList())tag(id).performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            shot("$mode-320-fonte-$font")
            if(mode!="login")compose.onNodeWithText("Voltar para entrar").performScrollTo().performClick()
        }
    }
    @Test fun aValidRecoveryCodeOnlyOpensPasswordResetAndRequiresLoginAfterSaving():Unit=runBlocking {
        tag("entry-forgot").performScrollTo().performClick()
        type("entry-email","professor@example.invalid");submit()
        val link=vault.value.pending!!;auth.callback("${link.redirect}&code=code-ficticio-valido")
        compose.waitUntil(5000){auth.state.value.recovery}
        tag("entry-email").assertDoesNotExist();tag("auth-confirm-password").assertExists();assertNull(auth.state.value.user)
        shot("nova-senha")
        type("entry-password","Nova-senha-ficticia-2026");type("auth-confirm-password","Nova-senha-ficticia-2026");submit()
        assertEquals(1,passwords);assertFalse(auth.state.value.recovery);assertNull(vault.value.session);tag("entry-email").assertExists()
        compose.onNodeWithText("Entrar").assertExists();compose.onNodeWithText("Enviar recuperação").assertDoesNotExist()
    }
}
