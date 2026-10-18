package br.com.diariodeclasse.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import br.com.diariodeclasse.data.*
import java.time.LocalDate

/** Entrada secundária: Relatórios → Registros de Aula. Nenhuma ação cria uma chamada. */
@Composable fun LessonRecordsScreen(vm: DiaryViewModel, data: DiarySnapshot, school: SchoolClass,
    draft: LessonRecordDraft?, change: (LessonRecordDraft?) -> Unit, dismissEditor: () -> Unit,
    readOnly: Boolean = false) {
    val saving by vm.saving.collectAsState()
    var filters by rememberSaveable(school.id) { mutableStateOf(false) }
    var from by rememberSaveable(school.id) { mutableStateOf("01/01/${school.year}") }
    var until by rememberSaveable(school.id) { mutableStateOf("31/12/${school.year}") }
    var removing by remember { mutableStateOf<LessonRecord?>(null) }
    var copied by remember { mutableStateOf<Pair<String, String>?>(null) }
    val context = LocalContext.current
    val rows = runCatching { LessonRecords.list(data.lessonRecords, school.id,
        from.takeIf { it.isNotBlank() }?.let(::iso), until.takeIf { it.isNotBlank() }?.let(::iso)) }
    fun copy(id: String, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        copied = id to if (label == "Descrição") "Descrição copiada." else "Título copiado."
    }
    LazyColumn(Modifier.testTag("lesson-records-list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Section("Registros de Aula", "${school.name} • ${school.subject} • ${school.year}")
            Text("Espaço livre e facultativo. Registre quando quiser, mesmo sem chamada.", style = MaterialTheme.typography.bodySmall)
            if (!readOnly) Button(enabled = !saving, onClick = {
                val now = LocalDate.now(); val day = if (now.year == school.year) now.toString() else "${school.year}-01-01"
                change(LessonRecordDraft(null, LessonRecord(classId = school.id, date = day, title = "", description = "")))
            }, modifier = Modifier.fillMaxWidth().testTag("new-lesson-record")) { Text("+ Criar registro") }
            else Info("Turma arquivada: consulta e cópia disponíveis. Reative a turma para editar; você também pode duplicar para uma turma ativa.")
            TextButton(onClick = { filters = !filters }, modifier = Modifier.testTag("record-filters")) { Text(if (filters) "Recolher período" else "Filtrar período") }
            if (filters) {
                Text(if (readOnly) "Histórico desta turma arquivada. Deixe as datas vazias para consultar todo o período." else "A turma é escolhida no seletor acima. Deixe as datas vazias para consultar todo o histórico.", style = MaterialTheme.typography.bodySmall)
                Field("De (dd/mm/aaaa)", from, { from = it }, Modifier.testTag("records-from"))
                Field("Até (dd/mm/aaaa)", until, { until = it }, Modifier.testTag("records-until"))
            }
            if (rows.isFailure) Text("Confira o período: use dd/mm/aaaa e início anterior ou igual ao fim.", color = MaterialTheme.colorScheme.error)
        }
        if (rows.isSuccess && rows.getOrThrow().isEmpty()) item { Info("Nenhum registro neste período. Preencher é opcional e não interfere nas outras funções.") }
        items(rows.getOrDefault(emptyList()), key = { it.id }) { record ->
            OutlinedCard(Modifier.fillMaxWidth().testTag("record-${record.id}")) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(shortDate(record.date), style = MaterialTheme.typography.labelLarge)
                    if (record.title.isNotBlank()) Text(record.title, style = MaterialTheme.typography.titleMedium)
                    if (record.description.isNotBlank()) Text(record.description, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { copy(record.id, "Título", record.title) }, modifier = Modifier.testTag("copy-title-${record.id}")) { Text("Copiar título") }
                        TextButton(onClick = { copy(record.id, "Descrição", record.description) }, modifier = Modifier.testTag("copy-description-${record.id}")) { Text("Copiar descrição") }
                    }
                    if (copied?.first == record.id) Text(copied!!.second, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (!readOnly) TextButton(enabled = !saving, onClick = { change(LessonRecordDraft(record, record)) }, modifier = Modifier.testTag("edit-record-${record.id}")) { Text("Editar") }
                        TextButton(enabled = !saving && data.classes.any { c -> data.classArchives.none { it.classId == c.id } }, onClick = {
                            val target = if (readOnly) data.classes.first { c -> data.classArchives.none { it.classId == c.id } }.id else school.id
                            change(LessonRecordDraft(null, record.copy(id = newId(), classId = target)))
                        }, modifier = Modifier.testTag("duplicate-record-${record.id}")) { Text("Duplicar") }
                        if (!readOnly) TextButton(enabled = !saving, onClick = { removing = record }, modifier = Modifier.testTag("delete-record-${record.id}")) { Text("Excluir") }
                    }
                }
            }
        }
    }
    removing?.let { record -> Confirm("Excluir registro de aula?",
        "${shortDate(record.date)} • ${record.title.ifBlank { "Sem título" }}. O texto será excluído permanentemente. Chamadas, atividades e notas não serão alteradas.",
        { if (!saving) removing = null }, { if (!saving) vm.act(done = { removing = null }) { vm.repository.deleteLessonRecord(record) } }) }
    draft?.let { edit -> LessonRecordEditor(vm, data, edit, change, dismissEditor) }
}

@Composable private fun LessonRecordEditor(vm: DiaryViewModel, data: DiarySnapshot, draft: LessonRecordDraft,
    change: (LessonRecordDraft?) -> Unit, dismiss: () -> Unit) {
    val saving by vm.saving.collectAsState()
    val value = draft.value
    var dateText by rememberSaveable(value.id) { mutableStateOf(shortDate(value.date)) }
    var error by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    val choices = data.classes.filter { c -> data.classArchives.none { it.classId == c.id } || c.id == draft.expected?.classId }
    val school = data.classes.firstOrNull { it.id == value.classId }
    fun update(record: LessonRecord) { change(draft.copy(value = record)); error = null }
    Form(if (draft.expected == null) "Novo registro de aula" else "Editar registro de aula", { if (!saving) dismiss() }) {
        Text("Somente turma, data e seu texto. Sem ligação com a chamada.", style = MaterialTheme.typography.bodySmall)
        Box {
            OutlinedButton(enabled = !saving, onClick = { menu = true }, modifier = Modifier.fillMaxWidth().testTag("record-class")) { Text("${school?.name ?: "Selecionar turma"} • ${school?.year ?: ""} ▾") }
            DropdownMenu(menu, { menu = false }) { choices.forEach { c ->
                DropdownMenuItem(text = { Text("${c.name} • ${c.subject} • ${c.year}") }, onClick = { update(value.copy(classId = c.id)); menu = false })
            } }
        }
        Field("Data (dd/mm/aaaa)", dateText, { dateText = it; update(value.copy(date = runCatching { iso(it) }.getOrDefault(it))) }, Modifier.testTag("record-date"), enabled = !saving)
        Field("Título", value.title, { update(value.copy(title = it)) }, Modifier.testTag("record-title"), enabled = !saving)
        Field("Descrição", value.description, { update(value.copy(description = it)) }, Modifier.testTag("record-description"), lines = 4, enabled = !saving)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        SaveError(vm)
        Button(enabled = !saving, onClick = {
            val checked = runCatching { value.copy(date = iso(dateText)).also(LessonRecords::validate) }
            if (checked.isFailure) error = "Confira a data (dd/mm/aaaa) e escreva um título ou uma descrição."
            else vm.act(done = { change(null); vm.message("Registro de aula salvo.") }) { vm.repository.saveLessonRecord(checked.getOrThrow(), draft.expected) }
        }, modifier = Modifier.fillMaxWidth().testTag("save-lesson-record")) { Text(if (saving) "Salvando…" else "Salvar registro") }
    }
}
