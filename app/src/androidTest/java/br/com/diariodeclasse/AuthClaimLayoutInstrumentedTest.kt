package br.com.diariodeclasse

import android.app.KeyguardManager
import android.content.Context
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
import androidx.test.platform.app.InstrumentationRegistry
import br.com.diariodeclasse.auth.*
import br.com.diariodeclasse.ui.theme.NoctuaTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class AuthClaimLayoutInstrumentedTest {
    @get:Rule val compose=createComposeRule()
    @Test fun legacyClaimStaysReachableAtNativeFontScaleAndCannotProceedWithoutDeviceCredential():Unit=runBlocking {
        val app=ApplicationProvider.getApplicationContext<DiaryApplication>()
        val guard=app.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        assertFalse("Fixture exige emulador descartável sem PIN.",guard.isDeviceSecure)
        val before=app.backupManager.snapshot()
        val font=InstrumentationRegistry.getArguments().getString("expectedSystemFontScale") ?: "1.0"
        var measured=0f
        compose.setContent {NoctuaTheme{Surface(Modifier.width(320.dp).height(560.dp)) {
            val current=LocalDensity.current.fontScale;SideEffect{measured=current}
            LegacyClaim(app,AuthUser(AuthConfig.LEGACY_OWNER_ID,"owner@example.invalid",true,"Proprietário Fictício")){fail("Sem credencial Android não pode vincular.")}
        }}}
        assertEquals(font.toFloat(),measured,0.01f)
        compose.onNodeWithTag("auth-claim-password").performScrollTo().assertHeightIsAtLeast(48.dp).performTextReplacement("Backup-Ficticio-2026")
        compose.onNodeWithTag("auth-claim-legacy").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithText("Configure um PIN ou senha de bloqueio no Android para autorizar a vinculação.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Sair sem vincular").performScrollTo().assertIsDisplayed()
        assertEquals(before,app.backupManager.snapshot())
        val folder=File(app.filesDir,"auth-screenshots").apply{mkdirs()}
        File(folder,"vinculacao-320-fonte-$font.png").outputStream().use{compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
}
