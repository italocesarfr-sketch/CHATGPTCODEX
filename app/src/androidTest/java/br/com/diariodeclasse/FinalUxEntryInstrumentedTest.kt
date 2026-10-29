package br.com.diariodeclasse

import android.content.Context
import android.graphics.Bitmap
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import br.com.diariodeclasse.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

class FinalUxEntryInstrumentedTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private lateinit var app:DiaryApplication
    private var previous:AppSelection?=null
    private val c=SchoolClass("ux14-entry-class",name="Turma Fictícia Entrada",subject="História",year=2025)
    @Before fun setup()=runBlocking {
        app=compose.activity.application as DiaryApplication;previous=app.selectionStore.read()
        app.repository.saveClass(c,"2025-01-01");app.selectionStore.write(AppSelection(2025,c.id));app.auth.logout()
    }
    @After fun cleanup()=runBlocking {
        app.commands.awaitPendingWrites()
        app.repository.dao.schoolClass(c.id)?.let {val p=app.repository.previewClassDeletion(c.id);app.repository.deleteClass(p,p.confirmation)}
        app.selectionStore.write(previous);(app as AcademicTestApplication).restoreFixtureUser()
    }
    private fun tag(v:String)=compose.onNodeWithTag(v)
    private fun shot(name:String) {
        val folder=File(app.filesDir,"ux14-screenshots");folder.mkdirs()
        File(folder,"$name.png").outputStream().use{compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    private fun hideKeyboard() {compose.activityRule.scenario.onActivity{a->(a.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(a.window.decorView.windowToken,0)}}
    private fun entered() {tag("entry-email").performScrollTo().performTextReplacement(fixtureUser.email);tag("entry-password").performScrollTo().performTextReplacement("Fixture-Password-2026");tag("entry-enter").performScrollTo().performClick();compose.waitUntil(10000){compose.onAllNodesWithTag("nav-0").fetchSemanticsNodes().isNotEmpty()}}
    @Test fun entryRequiresRealCredentialsAndRestoresTheExistingSelection() {
        val before=runBlocking{app.backupManager.snapshot()}
        tag("entry-logo").assertIsDisplayed()
        tag("entry-enter").performScrollTo().performClick()
        compose.onNodeWithTag("nav-0").assertDoesNotExist()
        compose.onNodeWithTag("auth-message").assertExists()
        @Suppress("DEPRECATION") val permissions=app.packageManager.getPackageInfo(app.packageName,android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertTrue(permissions.contains("android.permission.INTERNET"))
        compose.onNodeWithText("Prévia de entrada • acesso livre, sem autenticação.").assertDoesNotExist()
        tag("entry-email").assertExists();tag("entry-password").assertExists();shot("01-autenticacao-real")
        entered();compose.waitUntil(10000){compose.onAllNodesWithText(c.name).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("2025 ▾").assertExists();tag("entry-email").assertDoesNotExist()
        assertEquals(before,runBlocking{app.backupManager.snapshot()})
        compose.onNodeWithTag("nav-1").performClick()
        compose.onNodeWithTag("nav-1").assertContentDescriptionEquals("Horários / Chamada / Diário")
        compose.onNodeWithTag("nav-icon-1",useUnmergedTree=true).assertIsDisplayed()
        compose.onNodeWithTag("nav-3").performClick()
        compose.onNodeWithText("Buscar").assertExists()
        compose.onNodeWithTag("open-export").performScrollTo().performClick();compose.onNodeWithTag("export-screen").assertExists()
        compose.onNodeWithTag("export-preview-0").assertDoesNotExist();compose.onNodeWithTag("export-show-preview").assertIsDisplayed()
        compose.onNodeWithText("‹ Voltar").performClick()
        compose.onNodeWithTag("export-organize").performScrollTo().performClick();compose.onNodeWithTag("export-layout-list").assertExists()
        compose.onNodeWithTag("export-cancel").performScrollTo().performClick()
        compose.activityRule.scenario.recreate();tag("nav-0").assertExists();tag("entry-email").assertDoesNotExist()
        assertEquals(AppSelection(2025,c.id),app.selectionStore.read())
    }
    @Test fun passwordVisibilityWorksButNeitherCredentialSurvivesRecreationOrChangesSchoolData() {
        val before=runBlocking{app.backupManager.snapshot()}
        tag("entry-email").performScrollTo().performTextInput("professor@example.invalid")
        tag("entry-password").performScrollTo().performTextInput("segredo-ficticio")
        tag("entry-password").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        tag("entry-show-password").performClick()
        tag("entry-password").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password).not())
        tag("entry-show-password").performClick()
        tag("entry-password").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        hideKeyboard();compose.activityRule.scenario.recreate()
        for(id in listOf("entry-email","entry-password"))tag(id).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText,AnnotatedString("")))
        assertEquals(before,runBlocking{app.backupManager.snapshot()})
        entered();assertEquals(before,runBlocking{app.backupManager.snapshot()})
    }
    @Test fun createAndForgotOpenFunctionalFormsWithoutChangingSchoolData() {
        val before=runBlocking{app.backupManager.snapshot()}
        tag("entry-create").performScrollTo().performClick()
        tag("auth-name").assertExists();tag("auth-confirm-password").assertExists()
        compose.onNodeWithText("Voltar para entrar").performScrollTo().performClick()
        tag("entry-forgot").performScrollTo().performClick()
        compose.onNodeWithText("Enviar recuperação").assertExists()
        tag("entry-password").assertDoesNotExist()
        assertEquals(before,runBlocking{app.backupManager.snapshot()})
    }
    @Test fun logoutFromTheAccountMenuClosesAcademicAccessAndReentryKeepsTheSameSchoolData() {
        val before=runBlocking{app.backupManager.snapshot()}
        entered();tag("auth-account").performClick()
        compose.onNodeWithText(fixtureUser.email).assertIsDisplayed()
        shot("05-conta-autenticada")
        tag("auth-logout").performClick()
        compose.waitUntil(10000){compose.onAllNodesWithTag("entry-enter").fetchSemanticsNodes().isNotEmpty()}
        tag("nav-0").assertDoesNotExist();assertNull(app.auth.state.value.user)
        assertThrows(IllegalStateException::class.java){app.workspaces.current()}
        assertEquals(before,runBlocking{app.backupManager.snapshot()})
        entered();assertEquals(before,runBlocking{app.backupManager.snapshot()})
        assertEquals(AppSelection(2025,c.id),app.selectionStore.read())
    }
    @Test fun backFromMainFinishesWithoutReturningToLoginAndPreservesSelection() {
        entered()
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent KEYCODE_BACK").close()
        compose.waitUntil(10000){compose.activityRule.scenario.state==Lifecycle.State.DESTROYED}
        assertEquals(AppSelection(2025,c.id),app.selectionStore.read())
    }
}
