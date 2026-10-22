package br.com.diariodeclasse.ui

import androidx.compose.runtime.Composable
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.export.SpreadsheetKind

/** Compatibilidade com destinos de navegação salvos por versões anteriores. */
@Composable fun AttendanceExportScreen(data: DiarySnapshot, c: SchoolClass, term: Int, notify: (String) -> Unit,
    ready: Boolean, annual: Boolean = false, plans: Boolean = false, busyChanged: (Boolean) -> Unit) {
    ExportScreen(data, c, term, ready, when {
        plans -> SpreadsheetKind.PLANS
        annual -> SpreadsheetKind.ANNUAL
        else -> SpreadsheetKind.ATTENDANCE
    }, busyChanged)
}
