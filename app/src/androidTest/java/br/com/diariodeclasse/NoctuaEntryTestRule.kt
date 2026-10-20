package br.com.diariodeclasse

import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.rules.ExternalResource

/** Regressões existentes atravessam a entrada real, sem bypass de produção. */
fun enterNoctuaRule(compose: AndroidComposeTestRule<*, MainActivity>) = object : ExternalResource() {
    override fun before() {
        compose.onNodeWithTag("entry-enter").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onNodeWithTag("nav-0").fetchSemanticsNodeOrNull() != null }
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.fetchSemanticsNodeOrNull() =
    try { fetchSemanticsNode() } catch (_: AssertionError) { null }
