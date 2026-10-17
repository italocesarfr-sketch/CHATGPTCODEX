package br.com.diariodeclasse

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NoctuaIdentityInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun displaysNoctuaWithoutChangingApplicationIdentity() {
        val context = compose.activity
        assertEquals("br.com.diariodeclasse", context.packageName)
        assertEquals("Noctua", context.packageManager.getApplicationLabel(context.applicationInfo).toString())
        compose.onNodeWithText("Noctua").assertIsDisplayed()
    }
}
