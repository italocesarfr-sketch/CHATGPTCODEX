package br.com.diariodeclasse.ui
import br.com.diariodeclasse.ui.theme.NoctuaSpace
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
fun counted(n: Int, one: String, many: String): String = "$n ${if (n == 1) one else many}"
val brLocale: Locale = Locale.forLanguageTag("pt-BR")
fun number(value: Double?): String = value?.let { String.format(brLocale, "%.2f", it) } ?: "—"
/** Campos de edição não usam o arredondamento visual do boletim. */
fun editableNumber(value: Double): String = value.toString().replace('.', ',')
fun shortDate(value: String): String = runCatching { LocalDate.parse(value).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) }.getOrDefault(value)
fun decimal(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()
fun iso(text: String): String = LocalDate.parse(text.trim(), DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(java.time.format.ResolverStyle.STRICT)).toString()
fun today(): String = LocalDate.now().toString()
/** A matrícula padrão acompanha o ano letivo, não o dia em que o cadastro é digitado. */
fun defaultEnrollment(year: Int): String = LocalDate.of(year, 1, 1).toString()
fun requiredDecimal(text: String): Double = requireNotNull(decimal(text)) { "Informe um valor numérico válido." }
@Composable fun SaveError(vm: DiaryViewModel) {
    val error by vm.error.collectAsState()
    LaunchedEffect(Unit) { vm.clearError() }
    if (error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}
@Composable fun Field(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, lines: Int = 1, enabled: Boolean = true) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, modifier = modifier.fillMaxWidth(), singleLine = lines == 1, minLines = lines, enabled = enabled)
}
@Composable fun Section(title: String, subtitle: String? = null) {
    Column(Modifier.padding(vertical = br.com.diariodeclasse.ui.theme.NoctuaSpace.small)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
@Composable fun Info(text: String) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Text(text, Modifier.padding(br.com.diariodeclasse.ui.theme.NoctuaSpace.medium), style = MaterialTheme.typography.bodyMedium)
    }
}
/** Orientação sob demanda; avisos de risco e resultados continuam junto da ação. */
@Composable fun ContextHelp(title: String, text: String) {
    var opened by remember { mutableStateOf(false) }
    IconButton(onClick = { opened = true }, modifier = Modifier.size(48.dp)) {
        Icon(androidx.compose.ui.res.painterResource(br.com.diariodeclasse.R.drawable.ic_info), title)
    }
    if (opened) AlertDialog(onDismissRequest = { opened = false }, title = { Text(title) },
        text = { Text(text, Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = { opened = false }) { Text("Fechar") } })
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
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.padding(NoctuaSpace.page).widthIn(max = 640.dp).fillMaxWidth().imePadding(), shape = MaterialTheme.shapes.large, tonalElevation = 4.dp) {
            Column(Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp - 80).coerceAtLeast(200).dp).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
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

/** Mesmos controles e estilo, sem indicar seleção ou impor novo fluxo. */
@Composable fun ReportActions(organize: () -> Unit, export: () -> Unit, enabled: Boolean = true) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val actions: @Composable (Modifier)->Unit = { item ->
            OutlinedButton(onClick=organize,enabled=enabled,contentPadding=PaddingValues(horizontal=12.dp,vertical=8.dp),
                modifier=item.heightIn(min=48.dp).testTag("export-organize")) { Text("Personalizar planilha",maxLines=2,textAlign=androidx.compose.ui.text.style.TextAlign.Center) }
            OutlinedButton(onClick=export,enabled=enabled,contentPadding=PaddingValues(horizontal=12.dp,vertical=8.dp),
                modifier=item.heightIn(min=48.dp).testTag("open-export")) { Text("Exportar",maxLines=1,textAlign=androidx.compose.ui.text.style.TextAlign.Center) }
        }
        if(LocalDensity.current.fontScale>1.2f || maxWidth<280.dp)
            Column(verticalArrangement=Arrangement.spacedBy(NoctuaSpace.small)){actions(Modifier.fillMaxWidth())}
        else Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(NoctuaSpace.small)) { actions(Modifier.weight(1f).fillMaxHeight()) }
    }
}
