package br.com.diariodeclasse.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.diariodeclasse.data.*
import kotlin.math.ceil

/** Uma rolagem horizontal compartilhada, com nomes fora dela. Só compõe as colunas visíveis. */
@Composable private fun HorizontalCells(count: Int, width: Dp, scroll: ScrollState, cell: @Composable (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val columnPx = with(density) { width.toPx() }
        val visible = ceil(with(density) { maxWidth.toPx() } / columnPx).toInt() + 2
        val first by remember(scroll, columnPx, count) { derivedStateOf { (scroll.value / columnPx).toInt().minus(1).coerceIn(0, (count - 1).coerceAtLeast(0)) } }
        val end = (first + visible).coerceAtMost(count)
        Row(Modifier.horizontalScroll(scroll).testTag("frequency-horizontal")) {
            Spacer(Modifier.width(width * first))
            for (i in first until end) Box(Modifier.width(width)) { cell(i) }
            Spacer(Modifier.width(width * (count - end)))
        }
    }
}

@Composable fun FrequencyGrid(data: DiarySnapshot, c: SchoolClass, columns: List<Lesson>, period: Term?, daily: Boolean,
    mark: (Lesson, Student, String) -> Unit, options: (Lesson) -> Unit, openDay: (String) -> Unit) {
    val scroll = rememberScrollState()
    val width = if (daily) 152.dp else 56.dp
    var showRelocated by rememberSaveable(c.id) {mutableStateOf(false)}
    val roster = data.currentRoster(c.id,showRelocated)
    val byMark = remember(data.marks) { data.marks.associate { (it.lessonId to it.studentId) to it.mark } }
    val totals = remember(data, c, period, showRelocated) { roster.associate { it.id to data.frequency(it, period, c.countJustified) } }
    var fullName by remember { mutableStateOf<Student?>(null) }
    val background = MaterialTheme.colorScheme.surfaceContainer
    Column(Modifier.fillMaxSize().testTag(if (daily) "daily-frequency" else "term-frequency")) {
        if(data.roster(c.id).any {it.status=="Remanejado"}) RelocatedFilter(showRelocated) {showRelocated=it}
        if(data.relocations.any {link->roster.any {it.id==link.targetStudentId}}) Text("Totais incluem histórico vinculado; as colunas são desta turma.",Modifier.padding(horizontal=8.dp),style=MaterialTheme.typography.labelSmall)
        Row(Modifier.fillMaxWidth().background(background), verticalAlignment = Alignment.CenterVertically) {
            Text("Aluno", Modifier.width(124.dp).padding(8.dp), style = MaterialTheme.typography.labelLarge)
            Box(Modifier.weight(1f)) { HorizontalCells(columns.size + 1, width, scroll) { index ->
                if (index == columns.size) Text("F / FJ\n${if (period == null) "Ano" else "Bim."}", Modifier.fillMaxWidth().height(52.dp).padding(6.dp), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                else {
                    val l = columns[index]
                    if (daily) TextButton(onClick = { options(l) }, modifier = Modifier.fillMaxWidth().height(52.dp).testTag("hour-options-${l.ordinal}")) {
                        Text("${l.ordinal}º horário ${when(l.state) { "CANCELADA" -> "×"; "PREVISTA" -> "◇"; else -> "" }} ▾", style = MaterialTheme.typography.labelMedium)
                    } else Text(shortDate(l.date).take(5) + "\n${l.ordinal}º${if(l.state == "CANCELADA") " ×" else ""}", Modifier.fillMaxWidth().height(52.dp).clickable { openDay(l.date) }.padding(4.dp), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                }
            } }
        }
        HorizontalDivider()
        LazyColumn(Modifier.weight(1f).testTag("frequency-students")) {
            items(roster, key = { it.id }) { student ->
                val height = if (daily) 64.dp else 60.dp
                Row(Modifier.fillMaxWidth().height(height), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(124.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface).clickable { fullName = student }.testTag("student-name-${student.id}").padding(6.dp), verticalArrangement = Arrangement.Center) {
                        Text("${student.number}. ${student.name}", maxLines = if (daily) 2 else 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        if (daily && columns.firstOrNull()?.let { !student.enrolled(it.date) } == true) Text("Fora da matrícula", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    }
                    Box(Modifier.weight(1f)) { HorizontalCells(columns.size + 1, width, scroll) { index ->
                        if (index == columns.size) {
                            val f = totals[student.id]!!
                            Text("${f.absent} / ${f.justified}", Modifier.fillMaxWidth().height(height).wrapContentHeight().testTag("frequency-total-${student.id}"), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
                        } else {
                            val lesson = columns[index]
                            val current = byMark[lesson.id to student.id] ?: "NR"
                            if (daily) AttendanceCell(lesson, student, current, mark)
                            else {
                                val value = if (!student.enrolled(lesson.date)) "—" else current + when(lesson.state) { "CANCELADA" -> "×"; "PREVISTA" -> "◇"; else -> "" }
                                Text(value, Modifier.fillMaxWidth().height(height).clickable { openDay(lesson.date) }.wrapContentHeight().semantics { contentDescription = "${student.name}, ${shortDate(lesson.date)}, ${lesson.ordinal}º horário, $value" }, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                            }
                        }
                    } }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            if (roster.isEmpty()) item { Text(if(data.roster(c.id).isEmpty()) "Cadastre os alunos em Cadastrar Turma → Gerenciar alunos." else "Use Mostrar remanejados para consultar as chamadas anteriores, somente leitura.", Modifier.padding(16.dp)) }
        }
    }
    fullName?.let { student -> AlertDialog(onDismissRequest = { fullName = null }, title = { Text(student.name) },
        text = { Text("Nº ${student.number} • ${student.status}\nIngresso: ${shortDate(student.joined)}\nSaída: ${student.left?.let { shortDate(it) } ?: "não informada"}") }, confirmButton = { TextButton(onClick = { fullName = null }) { Text("Voltar") } }) }
}

@Composable private fun AttendanceCell(lesson: Lesson, student: Student, current: String, change: (Lesson, Student, String) -> Unit) {
    var menu by remember(lesson.id, student.id) { mutableStateOf(false) }
    val eligible = student.enrolled(lesson.date)
    val enabled = eligible && student.status!="Remanejado" && lesson.state == "REALIZADA"
    Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf("P", "F").forEach { value ->
            val selected = current == value
            val color = if (value == "F") MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
            OutlinedButton(onClick = { change(lesson, student, value) }, enabled = enabled, contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selected) color else MaterialTheme.colorScheme.surface),
                modifier = Modifier.width(48.dp).height(48.dp).testTag("cell-${lesson.id}-${student.id}-$value").semantics {
                    this.selected = selected
                    contentDescription = "${if (value == "P") "Presente" else "Faltou"}, ${student.name}, ${lesson.ordinal}º horário, ${shortDate(lesson.date)}"
                }) { Text(value, style = MaterialTheme.typography.labelLarge) }
        }
        Box {
            TextButton(onClick = { menu = true }, enabled = enabled, contentPadding = PaddingValues(0.dp), modifier = Modifier.width(48.dp).height(48.dp).testTag("cell-menu-${lesson.id}-${student.id}").semantics { contentDescription = "Outras marcações de ${student.name}, ${lesson.ordinal}º horário. Atual: $current" }) {
                Text(if (!eligible) "—" else "$current ▾", style = MaterialTheme.typography.labelSmall)
            }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text("FJ — falta justificada") }, onClick = { menu = false; change(lesson, student, "FJ") })
                DropdownMenuItem(text = { Text("NR — não registrado") }, onClick = { menu = false; change(lesson, student, "NR") })
            }
        }
    }
}
