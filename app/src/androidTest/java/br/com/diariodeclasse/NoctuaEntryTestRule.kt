package br.com.diariodeclasse

import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.rules.ExternalResource

/** The separate instrumentation Application supplies a confirmed fictitious principal. */
fun enterNoctuaRule(compose: AndroidComposeTestRule<*, MainActivity>) = object : ExternalResource() {
    override fun before() {
        try {
            compose.waitUntil(10000) { compose.onAllNodesWithTag("nav-0").fetchSemanticsNodes().isNotEmpty() }
        } catch(failure:Throwable) {
            // Diagnostics only; retain the original readiness assertion and timeout.
            runCatching {
                val app=compose.activity.application as DiaryApplication
                val phase=androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("phaseBNetworkPhase") ?: "suite"
                val folder=java.io.File(app.filesDir,"entry-diagnostics").apply{mkdirs()}
                val auth=app.auth.state.value
                java.io.File(folder,"$phase-state.txt").writeText("initializing=${auth.initializing} hasUser=${auth.user!=null} recovery=${auth.recovery} storageFailure=${auth.storageFailure} workspaceReady=${runCatching{app.workspaces.current()}.isSuccess}")
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let{bitmap->
                    java.io.File(folder,"$phase.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
                }
            }
            throw failure
        }
    }
}
