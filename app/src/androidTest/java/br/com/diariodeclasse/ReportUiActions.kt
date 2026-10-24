package br.com.diariodeclasse

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeContentTestRule

/** Os mesmos fluxos de regressão usam os novos pontos de acesso, sem remover asserções. */
fun openReportHelp(compose: ComposeContentTestRule) {
    compose.onNodeWithTag("report-options").performScrollTo().performClick()
    compose.onNodeWithTag("report-help").performScrollTo().performClick()
}
