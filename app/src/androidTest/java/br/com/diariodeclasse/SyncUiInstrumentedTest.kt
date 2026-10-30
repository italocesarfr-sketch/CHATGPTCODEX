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
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import br.com.diariodeclasse.auth.*
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.sync.*
import br.com.diariodeclasse.ui.theme.NoctuaTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

class SyncUiInstrumentedTest {
    @get:Rule val compose=createComposeRule()
    private lateinit var workspace:AccountWorkspace
    private val school=SchoolClass("ui-sync",name="Turma Fictícia",subject="História",year=2026)
    @Before fun setup():Unit=runBlocking {
        workspace=AccountWorkspace(AuthSandbox(ApplicationProvider.getApplicationContext()),AuthUser("11111111-1111-4111-8111-111111111111","fixture@example.invalid",true),false)
        workspace.repository.saveClass(school,"2026-01-01")
    }
    @After fun cleanup(){workspace.close()}
    private fun show(font:Float) {
        compose.setContent{NoctuaTheme{Surface {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,font)) {SyncPanel(workspace,listOf(school)){} }
        }}}
    }
    private fun capture(name:String) {
        val app=ApplicationProvider.getApplicationContext<DiaryApplication>()
        val folder=File(app.filesDir,"sync-screenshots").apply{mkdirs()}
        File(folder,"$name.png").outputStream().use{compose.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun optInIsExplicitAndDoesNotEnrollHistoricalClassesAutomatically():Unit=runBlocking {
        show(1f);compose.onNodeWithText("Ativar teste nesta conta").performScrollTo().performClick()
        compose.waitUntil(5000){runBlocking{workspace.database.syncDao().control()?.enabled==true}}
        assertTrue(workspace.database.syncDao().states().isEmpty())
        compose.onNodeWithText("Turma Fictícia • História • 2026").performScrollTo().performClick()
        compose.onNodeWithText("Cancelar").performClick();assertTrue(workspace.database.syncDao().states().isEmpty())
        compose.onNodeWithText("Turma Fictícia • História • 2026").performScrollTo().performClick();compose.onNodeWithText("Participar").performClick()
        compose.waitUntil(5000){runBlocking{workspace.database.syncDao().states().isNotEmpty()}}
        capture("turmas-pendentes")
    }
    @Test fun compactLargeFontHasScrollableAccessibleActions(){
        show(1.6f);compose.onNodeWithText("Ativar teste nesta conta").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Concluído").assertIsDisplayed();capture("turmas-fonte-1.6")
    }
}
