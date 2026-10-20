package br.com.diariodeclasse.ui

import br.com.diariodeclasse.ui.theme.NoctuaSpace
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.diariodeclasse.data.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Rascunho de UI salvo pelo SavedInstanceState; Concluir é o único commit da preferência. */
@Serializable
data class ExportEditSession(val classId: String, val mode: String, val expected: List<ExportRow>,
    val initial: List<ExportRow>, val rows: List<ExportRow>) {
    val dirty: Boolean get() = rows != initial
    fun hide(studentId: String, hidden: Boolean) = copy(rows = rows.map { if (it.studentId == studentId) it.copy(manualHidden = hidden) else it })
    fun move(id: String, direction: Int): ExportEditSession {
        val list = rows.toMutableList(); val index = list.indexOfFirst { it.id == id }; val next = index + direction
        if (index < 0 || next !in list.indices) return this
        val row = list.removeAt(index); list.add(next, row)
        return copy(rows = ExportLayout.normalize(list))
    }
    fun addBlank(after: String? = null): ExportEditSession {
        val list = rows.toMutableList()
        val index = after?.let { id -> list.indexOfFirst { it.id == id }.also { require(it >= 0) } + 1 } ?: list.size
        list.add(index, ExportLayout.blank(classId)); return copy(rows = ExportLayout.normalize(list))
    }
    fun removeBlank(id: String): ExportEditSession {
        require(rows.first { it.id == id }.studentId == null)
        return copy(rows = ExportLayout.normalize(rows.filterNot { it.id == id }))
    }
    fun encode(): String = Json.encodeToString(this)
    companion object {
        fun decode(value: String) = Json.decodeFromString<ExportEditSession>(value)
        fun start(data: DiarySnapshot, classId: String, mode: String): ExportEditSession {
            val rows = ExportLayout.rows(data, classId)
            return ExportEditSession(classId, mode, data.exportRows.filter { it.classId == classId }.sortedBy { it.position }, rows, rows)
        }
    }
}

@Composable fun ExportSaveControls(saving: Boolean, conclude: () -> Unit, cancel: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(NoctuaSpace.small)) {
        Button(enabled = !saving, onClick = conclude, modifier = Modifier.testTag("export-conclude")) { Text(if (saving) "Salvando…" else "Concluir") }
        OutlinedButton(enabled = !saving, onClick = cancel, modifier = Modifier.testTag("export-cancel")) { Text("Cancelar") }
    }
}

@Composable fun ExportExclusion(data: DiarySnapshot, student: Student, row: ExportRow?) {
    if (row?.manualHidden == true) Text("Oculto manualmente nas exportações", style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.testTag("export-hidden-${student.id}"))
    val link = data.relocations.firstOrNull { it.sourceStudentId == student.id }
    if (student.status == "Remanejado" || link != null) Text(
        "Exclusão automática: ${relocationLabel(data, student)}. Independente da ocultação manual.",
        style = MaterialTheme.typography.labelSmall, modifier = Modifier.testTag("export-auto-${student.id}"))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun ExportLayoutScreen(vm: DiaryViewModel, data: DiarySnapshot, c: SchoolClass,
    session: ExportEditSession, change: (ExportEditSession) -> Unit, conclude: () -> Unit, cancel: () -> Unit, export: () -> Unit = conclude) {
    val saving by vm.saving.collectAsState()
    var preview by rememberSaveable(c.id) { mutableStateOf(false) }
    val slots = ExportLayout.project(data, session.rows)
    val included = slots.filter { it.included }; val excluded = slots.filterNot { it.included }
    LazyColumn(Modifier.fillMaxSize().testTag("export-layout-list"), contentPadding = PaddingValues(NoctuaSpace.page), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Section("Organizar exportação", "${c.name} • ${c.year}")
            FlowRow(horizontalArrangement=Arrangement.spacedBy(NoctuaSpace.small)) {
                FilterChip(session.mode=="ORDER",{change(session.copy(mode="ORDER"))},label={Text("Disposição")},modifier=Modifier.testTag("export-order"))
                FilterChip(session.mode=="HIDE",{change(session.copy(mode="HIDE"))},label={Text("Ocultar alunos")},modifier=Modifier.testTag("export-hide"))
            }
            ExportSaveControls(saving, conclude, cancel)
            TextButton(enabled=!saving,onClick=export,modifier=Modifier.testTag("export-conclude-and-export")){Text("Concluir e exportar planilha")}
            SaveError(vm)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(NoctuaSpace.small)) {
            FilterChip(selected = preview, onClick = { preview = !preview }, label = { Text(if (preview) "Voltar à organização" else "Ver prévia") }, modifier = Modifier.testTag("export-preview"))
            if (!preview && session.mode=="ORDER") OutlinedButton(enabled = !saving, onClick = { change(session.addBlank()) }, modifier = Modifier.testTag("export-add-blank")) { Text("Adicionar linha vazia") }
            }
            Text(if (preview) "Prévia da sequência • ${included.size} linhas" else "Sequência de organização • ${slots.size} posições", style = MaterialTheme.typography.titleSmall)
        }
        if (slots.isEmpty()) item { Text("Nenhum aluno cadastrado.") }
        itemsIndexed(if (preview) included else slots, key = { _, slot -> slot.row.id }) { index, slot ->
            Card(Modifier.fillMaxWidth().testTag("export-row-${slot.row.id}")) {
                Column(Modifier.padding(NoctuaSpace.medium), verticalArrangement = Arrangement.spacedBy(NoctuaSpace.tiny)) {
                    Row {
                        if (!preview && slot.student != null) Checkbox(slot.row.manualHidden, { change(session.hide(slot.student.id, it)) }, enabled = !saving,
                            modifier = Modifier.testTag(if(session.mode=="HIDE") "export-hide-toggle-${slot.student.id}" else "export-order-hide-${slot.student.id}").semantics { contentDescription = "Ocultar manualmente ${slot.student.number}. ${slot.student.name}" })
                        Column(Modifier.weight(1f)) {
                            Text("${index + 1}. ${slot.student?.name ?: "Linha vazia"}", style = MaterialTheme.typography.titleSmall)
                            slot.student?.let { Text("Chamada nº ${it.number}", style = MaterialTheme.typography.bodySmall); ExportExclusion(data, it, slot.row) }
                        }
                    }
                    if (!preview && session.mode=="ORDER") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(enabled = !saving && index > 0, onClick = { change(session.move(slot.row.id, -1)) }, modifier = Modifier.testTag("export-up-${slot.row.id}")) { Text("↑ Subir") }
                            TextButton(enabled = !saving && index < slots.lastIndex, onClick = { change(session.move(slot.row.id, 1)) }, modifier = Modifier.testTag("export-down-${slot.row.id}")) { Text("↓ Descer") }
                            TextButton(enabled = !saving, onClick = { change(session.addBlank(slot.row.id)) }, modifier = Modifier.testTag("export-blank-after-${slot.row.id}")) { Text("Linha abaixo") }
                        }
                        if (slot.blank) TextButton(enabled = !saving, onClick = { change(session.removeBlank(slot.row.id)) }, modifier = Modifier.testTag("export-remove-${slot.row.id}")) { Text("Remover linha vazia") }
                    }
                }
            }
        }
        if (preview && excluded.isNotEmpty()) {
            item { Section("Fora da sequência", "Motivos de exclusão; estas entradas não são linhas da futura planilha") }
            itemsIndexed(excluded, key = { _, slot -> "excluded:${slot.row.id}" }) { _, slot ->
                Column(Modifier.testTag("export-excluded-${slot.student!!.id}")) {
                    Text("${slot.student.number}. ${slot.student.name}")
                    ExportExclusion(data, slot.student, slot.row)
                }
            }
        }
    }
}
