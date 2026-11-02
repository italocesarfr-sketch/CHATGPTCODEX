package br.com.diariodeclasse

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import br.com.diariodeclasse.sync.academic.*
import br.com.diariodeclasse.ui.theme.NoctuaTheme
import org.junit.*
import org.junit.Assert.*

class AcademicConflictPanelTest {
    @get:Rule val compose=createComposeRule()
    @Test fun conflictArrivingBeforeControlEmissionHasNoSpinnerAndCannotResend() {
        var inbox by mutableStateOf(emptyList<AcademicIncoming>())
        var outbox by mutableStateOf(emptyList<AcademicPending>())
        var busy by mutableStateOf(false)
        var clicks=0
        compose.setContent {NoctuaTheme{Surface {
            AcademicPanelContent(AcademicControl(owner="fixture",stage="ACTIVE_C",enabled=true,message="SYNCING"),
                emptyList(),outbox,inbox,busy,{clicks++},{},{})
        }}}
        compose.onNodeWithTag("academic-status").assertTextEquals("Sincronizando")
        compose.onNodeWithTag("academic-progress").assertExists()
        compose.onNodeWithTag("academic-retry").assertIsNotEnabled()
        compose.runOnIdle{inbox=listOf(AcademicIncoming(2,"fixture","{}","CONFLICT"))}
        compose.onNodeWithTag("academic-status").assertTextEquals("Conflito preservado")
        compose.onNodeWithTag("academic-progress").assertDoesNotExist()
        compose.onNodeWithTag("academic-retry").assertIsNotEnabled()
        compose.runOnIdle{assertEquals(0,clicks);inbox=emptyList();outbox=listOf(AcademicPending("op","fixture","{}","[]",0,conflict="preserved"))}
        compose.onNodeWithTag("academic-status").assertTextEquals("Conflito preservado")
        compose.onNodeWithTag("academic-progress").assertDoesNotExist()
        compose.onNodeWithTag("academic-retry").assertIsNotEnabled()
        compose.runOnIdle{busy=true}
        compose.onNodeWithTag("academic-retry").assertIsNotEnabled()
    }
}
