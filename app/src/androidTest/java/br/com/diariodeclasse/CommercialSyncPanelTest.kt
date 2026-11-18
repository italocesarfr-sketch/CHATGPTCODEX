package br.com.diariodeclasse

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import br.com.diariodeclasse.auth.*
import br.com.diariodeclasse.data.newId
import br.com.diariodeclasse.sync.SyncPanel
import br.com.diariodeclasse.sync.academic.AcademicControl
import br.com.diariodeclasse.ui.theme.NoctuaTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.rules.ExternalResource

class CommercialSyncPanelTest {
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    private lateinit var workspace:AccountWorkspace
    private lateinit var path:String
    @get:Rule(order=0) val storage=object:ExternalResource() {
        override fun before() {
            Assume.assumeTrue("Commercial UI is explicitly opted in",BuildConfig.ACADEMIC_COMMERCIAL)
            workspace=AccountWorkspace(context,AuthUser(newId(),"synthetic@example.invalid",true),false)
            path=workspace.database.openHelper.writableDatabase.path!!
        }
        override fun after(){if(::workspace.isInitialized){workspace.close();context.deleteDatabase(path)}}
    }
    @get:Rule(order=1) val compose=createComposeRule()
    @Test fun freshAccountPanelHasNoTechnicalEnrollmentOrSendControls() {
        compose.setContent {NoctuaTheme {SyncPanel(workspace,emptyList(),{})}}
        compose.waitUntil(10000){compose.onAllNodesWithText("Concluído").fetchSemanticsNodes().size==1&&compose.onAllNodesWithTag("sync-loading").fetchSemanticsNodes().isEmpty()}
        for(tag in listOf("academic-enable","academic-enable-c","academic-enable-d","academic-enable-e","academic-retry"))
            compose.onNodeWithTag(tag).assertDoesNotExist()
        compose.onNodeWithText("Ativar teste nesta conta").assertDoesNotExist()
        compose.onNodeWithText("Testar turmas e alunos").assertDoesNotExist()
        compose.onNodeWithText("Concluído").assertIsDisplayed()
        compose.waitForIdle()
        val image=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        requireNotNull(image).let{bitmap->java.io.File(context.filesDir,"commercial-ui.png").outputStream().use{
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }}
    }
    @Test fun offlineBootstrapShowsTruthfulStatusWithoutManualProtocolButtons() {
        runBlocking {workspace.database.academicDao().save(AcademicControl(owner=workspace.user.id,stage="BOOTSTRAP_E",message="OFFLINE"))}
        compose.setContent {NoctuaTheme {SyncPanel(workspace,emptyList(),{})}}
        compose.waitUntil(10000){compose.onAllNodesWithTag("academic-status").fetchSemanticsNodes().size==1}
        compose.onNodeWithTag("academic-status").assertTextEquals("Aguardando internet")
        compose.onNodeWithTag("academic-retry").assertDoesNotExist()
        compose.onNodeWithTag("academic-enable").assertDoesNotExist()
        compose.onNodeWithText("Concluído").assertIsDisplayed()
    }
}
