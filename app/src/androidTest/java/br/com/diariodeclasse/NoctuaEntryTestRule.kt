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
        compose.waitUntil(10000) { compose.onAllNodesWithTag("nav-0").fetchSemanticsNodes().isNotEmpty() }
    }
}
