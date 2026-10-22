package br.com.diariodeclasse.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.com.diariodeclasse.R
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.export.*
import br.com.diariodeclasse.ui.theme.NoctuaSpace
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ExportScreen(data: DiarySnapshot, c: SchoolClass, initialTerm: Int, ready: Boolean = true,
    initialKind: SpreadsheetKind = SpreadsheetKind.NOTES, busyChanged: (Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val feedback = remember { SnackbarHostState() }
    fun notify(text: String) { scope.launch { feedback.currentSnackbarData?.dismiss(); feedback.showSnackbar(text) } }
    var kindName by rememberSaveable(c.id) { mutableStateOf(initialKind.name) }
    val kind = SpreadsheetKind.valueOf(kindName)
    // Uma preferência por modelo e turma. Rotação preserva o estado, sem gravar no banco/backup.
    var preferencesJson by rememberSaveable(c.id) { mutableStateOf("{}") }
    val preferences = remember(preferencesJson) { Json.decodeFromString<Map<String, SpreadsheetPreferences>>(preferencesJson) }
    val p = preferences[kindName] ?: SpreadsheetPreferences()
    fun change(value: SpreadsheetPreferences) { preferencesJson = Json.encodeToString(preferences + (kindName to value)) }
    var settings by rememberSaveable(c.id) { mutableStateOf(false) }
    var preview by rememberSaveable(c.id) { mutableStateOf(false) }
    var selecting by remember { mutableStateOf(false) }
    var pendingPath by rememberSaveable(c.id) { mutableStateOf<String?>(null) }
    var preparing by remember { mutableStateOf(false) }
    var error by rememberSaveable(c.id) { mutableStateOf<String?>(null) }
    val busy = preparing || pendingPath != null
    LaunchedEffect(busy) { busyChanged(busy) }
    DisposableEffect(Unit) { onDispose { busyChanged(false) } }
    val result = remember(data, c, initialTerm, kind, p) { runCatching { ExportPresentation.build(data, c, initialTerm, kind, p) } }
    val table = result.getOrNull()
    val hasData = table != null && table.records.isNotEmpty() && (kind != SpreadsheetKind.ATTENDANCE || table.columns.any { it.key.startsWith("lesson:") })
    val canExport = ready && !busy && hasData
    val period = data.terms.firstOrNull { it.classId == c.id && it.number == initialTerm }
    val periodLabel = when {
        kind == SpreadsheetKind.ANNUAL || kind.regular && p.allTerms -> "Resumo anual"
        kind.ranged && p.customRange -> "${shortDate(p.start)} a ${shortDate(p.end)}"
        else -> "${initialTerm}º bimestre"
    }
    fun delivered(uri: Uri?) {
        val path = pendingPath ?: return
        scope.launch {
            try {
                val saved = withContext(Dispatchers.IO) { ExportFiles.deliver(File(path), uri != null) {
                    context.contentResolver.openOutputStream(requireNotNull(uri), "wt")
                } }
                notify(if (saved) "Arquivo exportado." else "Exportação cancelada.")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                error = "Não foi possível gravar o arquivo. Os registros foram preservados."
            } finally { pendingPath = null }
        }
    }
    val xlsx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.XLSX.mime), ::delivered)
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.CSV.mime), ::delivered)
    val tsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.TSV.mime), ::delivered)
    fun export() {
        val frozen = table ?: return
        val format = if (kind.regular) p.format else ExportFormat.XLSX
        preparing = true; error = null
        scope.launch {
            var file: File? = null
            try {
                file = withContext(Dispatchers.IO) {
                    if (p.share) SpreadsheetShare.prepare(context, frozen, format)
                    else ExportFiles.prepare(File(context.cacheDir, "exports"), frozen, format)
                }
                if (p.share) context.startActivity(Intent.createChooser(SpreadsheetShare.intent(context, file), "Compartilhar planilha"))
                else {
                    pendingPath = file.absolutePath
                    val identity = (c.name + "-" + c.subject).replace(Regex("[^\\p{L}\\p{N} _.-]"), "_").take(70)
                    val name = "Noctua-$identity-${c.year}-${kind.name.lowercase()}-${if (p.allTerms || kind == SpreadsheetKind.ANNUAL) "anual" else "bimestre-$initialTerm"}.${format.extension}"
                    when (format) { ExportFormat.XLSX -> xlsx.launch(name); ExportFormat.CSV -> csv.launch(name); ExportFormat.TSV -> tsv.launch(name) }
                }
            } catch (e: Exception) {
                withContext(NonCancellable + Dispatchers.IO) { file?.delete() }; pendingPath = null
                if (e is CancellationException) throw e
                error = "Não foi possível preparar a exportação."
            } finally { preparing = false }
        }
    }
    val format = if (kind.regular) p.format else ExportFormat.XLSX
    val modelTag = when (kind) { SpreadsheetKind.ATTENDANCE -> "attendance-export-screen"; SpreadsheetKind.PLANS -> "plans-export-screen"; SpreadsheetKind.ANNUAL -> "annual-export-screen"; else -> "export-content" }
    val actionBar: @Composable () -> Unit = {
        // Em fontes ampliadas as duas ações passam a uma coluna, sem comprimir os rótulos.
        val actions: @Composable (Modifier) -> Unit = { actionModifier ->
            OutlinedButton(onClick = {
                try {
                    val text = ExportPresentation.copy(requireNotNull(table), p); require(text.isNotEmpty())
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Noctua • ${c.name}", text))
                    notify(if (p.copyMode == "Dados completos") "Dados copiados." else "Somente notas copiadas.")
                } catch (_: Exception) { error = "Não foi possível copiar os dados." }
            }, enabled = canExport, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), modifier = actionModifier.heightIn(min = 48.dp).testTag("export-copy")) { Text("Copiar") }
            Button(onClick = ::export, enabled = canExport, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), modifier = actionModifier.heightIn(min = 48.dp).testTag("export-file")) {
                Text(if (busy) "Aguardando…" else if (!ready) "Aguardando salvamento…" else "${if (p.share) "Compartilhar" else "Exportar"} ${format.name}")
            }
        }
        Surface(tonalElevation = 3.dp) {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = NoctuaSpace.page, vertical = NoctuaSpace.small)) {
                val actionWidth = (maxWidth - NoctuaSpace.small) / 2
                if (LocalDensity.current.fontScale >= 1.3f || maxWidth < 260.dp || p.share) Column(verticalArrangement = Arrangement.spacedBy(NoctuaSpace.small)) { actions(Modifier.fillMaxWidth()) }
                else Row(horizontalArrangement = Arrangement.spacedBy(NoctuaSpace.small)) {
                    actions(Modifier.width(actionWidth))
                }
            }
        }
    }
    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0), snackbarHost = { SnackbarHost(feedback, Modifier.testTag("export-feedback")) }, bottomBar = actionBar) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).testTag("export-screen")) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag(modelTag), contentPadding = PaddingValues(NoctuaSpace.page), verticalArrangement = Arrangement.spacedBy(NoctuaSpace.small)) {
                item { Section("Exportar", "${c.name} • ${c.subject} • ${c.year} • $periodLabel") }
                item {
                    Text("Tipo de planilha", style = MaterialTheme.typography.labelLarge)
                    Box {
                        OutlinedButton(onClick = { selecting = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("export-type")) { Text("${kind.label} ▾") }
                        DropdownMenu(selecting, { selecting = false }) {
                            SpreadsheetKind.entries.forEach { entry ->
                                DropdownMenuItem(text = { Text(entry.label) }, onClick = { kindName = entry.name; selecting = false; error = null }, modifier = Modifier.testTag("export-type-${entry.name}"))
                            }
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(buildList {
                            add(format.name)
                            if (kind != SpreadsheetKind.PLANS) { if (p.headers) add("Cabeçalhos"); if (p.callNumber) add("Nº da chamada") }
                        }.joinToString(" • "), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        IconButton(onClick = { settings = true }, enabled = !busy, modifier = Modifier.size(48.dp).testTag("export-options")) {
                            Icon(painterResource(R.drawable.ic_filter), "Ajustes da exportação")
                        }
                    }
                    if (p.copyMode != "Dados completos" && kind.regular && kind != SpreadsheetKind.FREQUENCY)
                        Text("Copiar: ${p.copyMode.lowercase()}", style = MaterialTheme.typography.bodyMedium)
                    if (p.share) Text("Destino: compartilhar", style = MaterialTheme.typography.bodyMedium)
                }
                item {
                    if (table == null) Text(if (kind.ranged && !p.customRange && period == null) "Configure o bimestre ou escolha um intervalo nos ajustes." else "Informe um intervalo válido dentro de ${c.year} nos ajustes.", color = MaterialTheme.colorScheme.error)
                    else if (!hasData) Text(when (kind) { SpreadsheetKind.PLANS -> "Nenhum plano de aula neste período."; SpreadsheetKind.ATTENDANCE -> "Nenhum horário realizado elegível neste período."; else -> "Nenhuma linha para exportar." })
                    TextButton(onClick = { preview = true }, enabled = table != null && !busy, modifier = Modifier.testTag("export-show-preview")) { Text("Ver prévia") }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("export-error")) }
                }
            }

        }
    }
    if (settings) ModalBottomSheet(onDismissRequest = { settings = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), modifier = Modifier.testTag("export-settings")) {
        Column(Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.85f).dp).imePadding()) {
        Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = NoctuaSpace.page), verticalArrangement = Arrangement.spacedBy(NoctuaSpace.small)) {
            Text("Ajustes da exportação", style = MaterialTheme.typography.titleLarge)
            if (kind.regular) {
                Choice("Abrangência", listOf("Bimestre selecionado", "Quatro bimestres"), if (p.allTerms) "Quatro bimestres" else "Bimestre selecionado") { change(p.copy(allTerms = it == "Quatro bimestres")) }
                Choice("Formato", ExportFormat.entries.map { it.name }, p.format.name) { change(p.copy(format = ExportFormat.valueOf(it))) }
            }
            if (kind.ranged) {
                Choice("Período", listOf("Bimestre selecionado", "Intervalo de datas"), if (p.customRange) "Intervalo de datas" else "Bimestre selecionado") {
                    change(p.copy(customRange = it == "Intervalo de datas", start = p.start.ifEmpty { period?.start ?: "${c.year}-01-01" }, end = p.end.ifEmpty { period?.end ?: "${c.year}-12-31" }))
                }
                if (p.customRange) {
                    Field("Data inicial (dd/mm/aaaa)", shortDate(p.start), { change(p.copy(start = runCatching { iso(it) }.getOrDefault(it))) }, Modifier.testTag("attendance-export-start"))
                    Field("Data final (dd/mm/aaaa)", shortDate(p.end), { change(p.copy(end = runCatching { iso(it) }.getOrDefault(it))) }, Modifier.testTag("attendance-export-end"))
                }
            }
            if (kind != SpreadsheetKind.PLANS) {
                ExportCheck("Incluir cabeçalhos${if (kind == SpreadsheetKind.NOTES) " e máximos" else ""}", p.headers, "export-headers") { change(p.copy(headers = it)) }
                ExportCheck("Incluir número da chamada", p.callNumber, "export-number") { change(p.copy(callNumber = it)) }
            }
            if (kind.regular && kind != SpreadsheetKind.FREQUENCY) Choice("Conteúdo da cópia", listOf("Dados completos", "Somente notas vigentes", "Todas as notas"), p.copyMode) { change(p.copy(copyMode = it)) }
            Choice("Destino do arquivo", listOf("Salvar arquivo", "Compartilhar"), if (p.share) "Compartilhar" else "Salvar arquivo") { change(p.copy(share = it == "Compartilhar")) }
        }
            TextButton(onClick = { settings = false }, modifier = Modifier.fillMaxWidth().padding(horizontal = NoctuaSpace.page).heightIn(min = 48.dp).testTag("export-settings-done")) { Text("Concluído") }
            Spacer(Modifier.height(NoctuaSpace.medium))
        }
    }
    if (preview && table != null) ExportPreview(table, { preview = false })
}

@Composable private fun ExportCheck(label: String, checked: Boolean, tag: String, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = checked, role = Role.Checkbox, onValueChange = change).testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, null); Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable private fun ExportPreview(table: ExportTable, close: () -> Unit) {
    val horizontal = rememberScrollState()
    val rows = remember(table) { table.rows() }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(Modifier.fillMaxSize().testTag("export-preview-dialog")) {
                Row(Modifier.fillMaxWidth().padding(NoctuaSpace.page), verticalAlignment = Alignment.CenterVertically) {
                    Text("Prévia", Modifier.weight(1f).testTag("export-preview-heading"), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = close, modifier = Modifier.heightIn(min = 48.dp).testTag("export-preview-close")) { Text("Fechar") }
                }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = NoctuaSpace.page)) {
                    itemsIndexed(rows) { index, row ->
                        Row(Modifier.horizontalScroll(horizontal).testTag("export-preview-$index")) {
                            row.forEach { cell -> Text(SpreadsheetWriter.plain(cell).ifEmpty { "—" }, Modifier.width(160.dp).padding(6.dp), style = MaterialTheme.typography.bodyMedium) }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
