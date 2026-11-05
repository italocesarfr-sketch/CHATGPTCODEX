package br.com.diariodeclasse

import android.content.Context
import androidx.room.withTransaction
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import br.com.diariodeclasse.auth.*
import br.com.diariodeclasse.data.newId
import br.com.diariodeclasse.sync.SyncPanel
import br.com.diariodeclasse.sync.academic.*
import br.com.diariodeclasse.ui.theme.NoctuaTheme
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import org.junit.*
import org.junit.rules.ExternalResource

/** A real isolated Room file; no authenticated remote account or academic data. */
class SyncPanelReadinessTest {
    private lateinit var workspace:AccountWorkspace
    private lateinit var path:String
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    @get:Rule(order=0) val storage=object:ExternalResource() {
        override fun before() {runBlocking {
            val user=AuthUser(newId(),"readiness@example.invalid",true,"Fictício")
            workspace=AccountWorkspace(context,user,false)
            path=requireNotNull(workspace.database.openHelper.writableDatabase.path)
            val dao=workspace.database.academicDao()
            dao.save(AcademicControl(owner=user.id,stage="ACTIVE_D",enabled=true,message="CONFLICT"))
            for(n in 1..100)dao.save(AcademicState("students","fictitious-$n",owner=user.id))
            dao.insert(AcademicIncoming(1,user.id,"{}","CONFLICT"))
        }}
        override fun after(){workspace.close();context.deleteDatabase(path)}
    }
    @get:Rule(order=1) val compose=createComposeRule()
    @Test fun loadedAcademicPanelDoesNotFallBackToLegacyDuringParentRecomposition() {
        var generation by mutableStateOf(0)
        compose.setContent {NoctuaTheme {Text("$generation");SyncPanel(workspace,emptyList(),{})}}
        compose.waitUntil(5000){compose.onAllNodesWithTag("academic-status").fetchSemanticsNodes().size==1}
        repeat(20) {
            compose.runOnIdle{generation++}
            compose.onNodeWithTag("academic-status").assertTextEquals("Conflito preservado")
            compose.onNodeWithTag("academic-retry").assertIsNotEnabled()
            compose.onNodeWithTag("academic-progress").assertDoesNotExist()
            compose.onNodeWithTag("academic-enable").assertDoesNotExist()
            compose.onNodeWithTag("sync-loading").assertDoesNotExist()
        }
    }
    @Test fun asynchronousRoomPublicationWhileOpeningAndClosingDialogRemainsOnUiThread() {
        var visible by mutableStateOf(false)
        compose.setContent {NoctuaTheme {if(visible)SyncPanel(workspace,emptyList(),{visible=false})}}
        repeat(20) { n->
            compose.runOnIdle{visible=true}
            // Actual Room invalidation/emission on its query executor, not mutableState
            // assigned by runOnIdle. This reproduced the off-thread frame path in E.
            runBlocking(Dispatchers.IO) {workspace.database.withTransaction {
                val dao=workspace.database.academicDao();dao.resolvedInbox()
                dao.save(AcademicControl(owner=workspace.user.id,stage="ACTIVE_E",enabled=true,message=if(n%2==0)"SYNCING"else"RETRY"))
            }}
            val expected=if(n%2==0)"Sincronizando"else"Falha temporária"
            compose.waitUntil(10000){compose.onAllNodes(hasTestTag("academic-status") and hasText(expected)).fetchSemanticsNodes().size==1}
            compose.onNodeWithTag("academic-status").assertTextEquals(expected)
            if(n%2==0) {
                compose.onNodeWithTag("academic-progress").assertExists()
                compose.onNodeWithTag("academic-retry").assertIsNotEnabled()
            }else {
                compose.onNodeWithTag("academic-progress").assertDoesNotExist()
                compose.onNodeWithTag("academic-retry").assertIsEnabled()
            }
            compose.onNodeWithText("Concluído").performClick()
            compose.waitUntil(10000){compose.onAllNodesWithTag("academic-status").fetchSemanticsNodes().isEmpty()}
        }
    }

}
