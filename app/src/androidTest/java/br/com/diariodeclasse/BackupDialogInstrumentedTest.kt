package br.com.diariodeclasse

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupDialogInstrumentedTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun openingAndCancellingBackupDoesNotModifyRecords() = runBlocking {
        val manager=(compose.activity.application as DiaryApplication).backupManager
        compose.waitUntil(5000) { (compose.activity.application as DiaryApplication).selectionStore.read() != null }
        val before=manager.snapshot()
        compose.onNodeWithText("Backup").performClick()
        compose.onNodeWithText("Backup e restauração").assertIsDisplayed()
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("Voltar").fetchSemanticsNodes().any { !it.config.contains(SemanticsProperties.Disabled) }
        }
        compose.onNodeWithText("Selecionar backup para restaurar").assertExists()
        compose.onNodeWithText("Confirmar substituição dos dados").assertDoesNotExist()
        compose.onNodeWithText("Voltar",useUnmergedTree=false).performScrollTo().performClick()
        compose.onNodeWithText("Backup e restauração").assertDoesNotExist()
        assertEquals(before,manager.snapshot())
    }
}
