package br.com.diariodeclasse.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.diariodeclasse.data.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle

@Composable fun CalendarScreen(vm: DiaryViewModel, data: DiarySnapshot, c: SchoolClass, term: Int, attendance: (String) -> Unit) {
    val period = data.terms.firstOrNull { it.classId == c.id && it.number == term }
    var month by rememberSaveable(c.id, term) { mutableIntStateOf(period?.start?.let { LocalDate.parse(it).monthValue } ?: 1) }
    var selected by rememberSaveable(c.id, term) { mutableStateOf(period?.start ?: "${c.year}-01-01") }
    var filter by rememberSaveable { mutableStateOf(false) }
    var extra by remember { mutableStateOf(false) }; var note by remember { mutableStateOf("Reposição de aula") }
    val ym = YearMonth.of(c.year, month)
    val lessons = data.lessons.filter { it.classId == c.id }
    val dayLessons = lessons.filter { it.date == selected }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { month = if (month == 1) 12 else month - 1 }) { Text("‹") }
                Text("${ym.month.getDisplayName(TextStyle.FULL, brLocale)} ${c.year}", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { month = if (month == 12) 1 else month + 1 }) { Text("›") }
            }
            Row { Checkbox(filter, { filter = it }); Text("Exibir apenas o ${term}º bimestre", Modifier.padding(top = 12.dp)) }
            Row { listOf("S", "T", "Q", "Q", "S", "S", "D").forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center) } }
            val offset = ym.atDay(1).dayOfWeek.value - 1
            for (week in 0 until (offset + ym.lengthOfMonth() + 6) / 7) {
                Row(Modifier.fillMaxWidth()) {
                    for (weekday in 0..6) {
                        val n = week * 7 + weekday - offset + 1
                        if (n !in 1..ym.lengthOfMonth()) { Spacer(Modifier.weight(1f).height(60.dp)); continue }
                        val day = ym.atDay(n).toString()
                        val matches = !filter || period != null && day in period.start..period.end
                        val daily = lessons.filter { it.date == day }
                        val completed = daily.any { it.state == "REALIZADA" && data.marks.any { mark -> mark.lessonId == it.id && mark.mark != "NR" } }
                        val cancelled = daily.isNotEmpty() && daily.all { it.state == "CANCELADA" }
                        val label = if (!matches) "" else when { cancelled -> "×"; completed -> "✓"; daily.isNotEmpty() -> "${daily.size} a"; else -> "" }
                        Column(Modifier.weight(1f).height(60.dp).padding(2.dp)
                            .background(if (selected == day) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                            .clickable(enabled = matches) { selected = day }, verticalArrangement = Arrangement.Center) {
                            Text(if (matches) "$n" else "·", Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                            Text(label, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            Text("a = aulas previstas • ✓ = chamada registrada • × = sem regência", style = MaterialTheme.typography.bodySmall)
            Section(shortDate(selected), period?.let { "${term}º bimestre: ${shortDate(it.start)} a ${shortDate(it.end)}" })
            OutlinedButton(onClick = { extra = true }, modifier = Modifier.fillMaxWidth()) { Text("+ Reposição / aula extraordinária") }
        }
        if (dayLessons.isEmpty()) item { Info("Não há aulas nesta data. Dias sem aula não geram faltas.") }
        items(dayLessons, key = { it.id }) { lesson ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${lesson.ordinal}ª aula • ${lesson.state.lowercase().replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.titleMedium)
                if (lesson.note.isNotBlank()) Text(lesson.note)
                Button(onClick = { attendance(lesson.id) }, modifier = Modifier.fillMaxWidth()) { Text(if (lesson.state == "REALIZADA") "Consultar / editar chamada" else "Fazer chamada") }
            } }
        }
    }
    if (extra) Form("Registrar aula em ${shortDate(selected)}", { extra = false }) {
        Choice("Motivo", listOf("Reposição de aula", "Aula extraordinária", "Dia de prova", "Passeio pedagógico", "Evento pedagógico"), note) { note = it }
        Button(onClick = { vm.act(done = { extra = false }) { vm.repository.extraLesson(c.id, selected, note) } }) { Text("Adicionar aula") }
    }
}

@Composable fun AttendanceScreen(vm: DiaryViewModel, data: DiarySnapshot, lesson: Lesson) {
    val students = data.roster(lesson.classId).filter { it.enrolled(lesson.date) }
    val marks = data.marks.filter { it.lessonId == lesson.id }.associateBy { it.studentId }
    var confirmation by remember { mutableStateOf("") }
    var cancelReason by remember { mutableStateOf("Aula não realizada") }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Section("${lesson.ordinal}ª aula • ${shortDate(lesson.date)}", "${marks.count { it.value.mark != "NR" }} de ${students.size} frequências registradas")
            Info("P: presente • F: faltou • FJ: falta justificada • NR: não registrado. Cada aula tem sua própria chamada.")
            if (lesson.state == "PREVISTA") {
                Button(onClick = { vm.act { vm.repository.startLesson(lesson.id) } }, modifier = Modifier.fillMaxWidth()) { Text("Iniciar chamada — aula realizada") }
            } else if (lesson.state == "CANCELADA") {
                Info("Aula sem regência. Frequências anteriores estão preservadas, mas não entram nos totais.")
                OutlinedButton(onClick = { confirmation = "reactivate" }) { Text("Reativar aula") }
            } else {
                OutlinedButton(onClick = { confirmation = "present" }, modifier = Modifier.fillMaxWidth()) { Text("Marcar todos presentes") }
                if (lesson.ordinal > 1) OutlinedButton(onClick = { confirmation = "copy" }, modifier = Modifier.fillMaxWidth()) { Text("Copiar primeira chamada do dia") }
            }
            if (lesson.state != "CANCELADA") TextButton(onClick = { confirmation = "cancel" }) { Text("Marcar aula como não realizada") }
        }
        items(students, key = { it.id }) { student ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                Text("${student.number}. ${student.name}", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("P", "F", "FJ", "NR").forEach { mark ->
                        val selected = (marks[student.id]?.mark ?: "NR") == mark
                        FilterChip(selected = selected, onClick = { vm.act { vm.repository.mark(lesson.id, student.id, mark) } },
                            enabled = lesson.state == "REALIZADA", label = { Text(mark) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp))
                    }
                }
            } }
        }
        if (students.isEmpty()) item { Info("Nenhum aluno matriculado nesta data. Confira as datas de ingresso e saída.") }
    }
    when (confirmation) {
        "cancel" -> Form("Aula sem regência", { confirmation = "" }) {
            Choice("Motivo", listOf("Aula não realizada", "Feriado", "Recesso escolar", "Conselho de classe", "Paralisação", "Passeio sem regência"), cancelReason) { cancelReason = it }
            Text("A aula continuará no calendário e não contará na frequência.")
            Button(onClick = { vm.act(done = { confirmation = "" }) { vm.repository.lessonState(lesson.id, "CANCELADA", cancelReason) } }) { Text("Confirmar cancelamento") }
        }
        "present", "copy", "reactivate" -> Confirm("Confirmar alteração", when (confirmation) {
            "present" -> "As marcações desta aula serão substituídas por P para todos os alunos."
            "copy" -> "As marcações desta aula serão substituídas pelas da primeira aula realizada do dia."
            else -> "A aula voltará a ser prevista. As marcações anteriores serão preservadas."
        }, { confirmation = "" }) {
            val action = confirmation
            vm.act(done = { confirmation = "" }) {
                when (action) { "present" -> vm.repository.allPresent(lesson.id); "copy" -> vm.repository.copyFirst(lesson.id); else -> vm.repository.lessonState(lesson.id, "PREVISTA", "") }
            }
        }
    }
}
