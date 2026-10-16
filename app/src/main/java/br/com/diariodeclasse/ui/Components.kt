package br.com.diariodeclasse.ui
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
val brLocale: Locale = Locale.forLanguageTag("pt-BR")
fun number(value: Double?): String = value?.let { String.format(brLocale, "%.2f", it) } ?: "—"
fun shortDate(value: String): String = runCatching { LocalDate.parse(value).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) }.getOrDefault(value)
fun decimal(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()
fun iso(text: String): String = LocalDate.parse(text.trim(), DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(java.time.format.ResolverStyle.STRICT)).toString()
fun today(): String = LocalDate.now().toString()
fun defaultEnrollment(year: Int): String = if (LocalDate.now().year == year) today() else "$year-01-01"
fun requiredDecimal(text: String): Double = requireNotNull(decimal(text)) { "Informe um valor numérico válido." }
@Composable fun Field(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, lines: Int = 1) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, modifier = modifier.fillMaxWidth(), singleLine = lines == 1, minLines = lines)
}
@Composable fun Section(title: String, subtitle: String? = null) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
@Composable fun Info(text: String) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Text(text, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
    }
}
@Composable fun Choice(label: String, options: List<String>, value: String, select: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text("$value ▾") }
            DropdownMenu(expanded, { expanded = false }) {
                options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { select(option); expanded = false }) }
            }
        }
    }
}
@Composable fun Form(title: String, dismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = dismiss) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 4.dp) {
            Column(Modifier.fillMaxWidth().heightIn(max = 690.dp).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                content()
                TextButton(onClick = dismiss, modifier = Modifier.fillMaxWidth()) { Text("Voltar") }
            }
        }
    }
}
@Composable fun Confirm(title: String, text: String, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = confirm) { Text("Confirmar") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancelar") } })
}
