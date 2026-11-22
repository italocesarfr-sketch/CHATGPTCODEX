package br.com.diariodeclasse

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.platform.WindowRecomposerFactory
import androidx.compose.ui.platform.WindowRecomposerPolicy
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Real lifecycle/Choreographer for HTTP + Room integration, with normal semantics assertions.
 * Compose 1.7's test continuation interceptor can migrate Room flow work to its clock
 * (or recompose inline on Room's executor). Neither is the app's runtime dispatcher.
 * No UI content, DAO, thread guard, auth admission or engine call is replaced here.
 */
// Test-only adapter to the exact Compose version pinned by this project's BOM.
// Review this adapter when upgrading Compose; never use it in application code.
@OptIn(ExperimentalComposeUiApi::class,InternalComposeUiApi::class)
class CommercialRuntimeActivityRule:TestRule {
    private val launch=ActivityScenarioRule(MainActivity::class.java)
    val activity:MainActivity get() {
        lateinit var value:MainActivity
        launch.scenario.onActivity{value=it}
        return value
    }
    override fun apply(base:Statement,description:Description)=object:Statement() {
        override fun evaluate() {
            // Includes later dialog windows. The outer Compose rule restores its own
            // factory afterwards; only these integration tests use production timing.
            WindowRecomposerPolicy.withFactory(WindowRecomposerFactory.LifecycleAware) {
                launch.apply(base,description).evaluate()
            }
        }
    }
}

fun commercialRuntimeComposeRule()=AndroidComposeTestRule(CommercialRuntimeActivityRule()){it.activity}
