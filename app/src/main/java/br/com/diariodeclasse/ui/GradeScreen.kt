package br.com.diariodeclasse.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.com.diariodeclasse.data.*

@Composable fun GradeScreen(vm: DiaryViewModel, data: DiarySnapshot, a: Activity, edit: () -> Unit, deleted: () -> Unit, dirty: (Boolean) -> Unit = {}) {
    var confirm by remember { mutableStateOf(false) }
    val invalid = remember(a.id) { mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(invalid.values.toList()) { dirty(invalid.values.any { it }) }
    DisposableEffect(a.id) { onDispose { dirty(false) } }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Section(a.name, "${a.term}º bimestre • ${shortDate(a.date)} • máximo ${number(a.maximum)}")
            Info("Notas válidas são salvas automaticamente. Deixe vazio para pendente; digite 0 para nota zero.")
            TextButton(onClick = edit) { Text("Editar atividade") }
            TextButton(onClick = { confirm = true }) { Text("Excluir atividade") }
        }
        items(data.roster(a.classId).filter { it.enrolled(a.date) }, key = { it.id }) { student ->
            val grade = data.grades.firstOrNull { it.activityId == a.id && it.studentId == student.id }?.value
            var text by rememberSaveable(a.id, student.id) { mutableStateOf(grade?.let { number(it) } ?: "") }
            val valid = text.isBlank() || decimal(text)?.let { it.isFinite() && it in 0.0..a.maximum } == true
            LaunchedEffect(student.id, text, a.maximum) { invalid[student.id] = !valid }
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                Text("${student.number}. ${student.name}", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(value = text, onValueChange = { value ->
                    text = value
                    val numeric = decimal(value)
                    invalid[student.id] = !(value.isBlank() || numeric != null && numeric.isFinite() && numeric in 0.0..a.maximum)
                    if (value.isBlank() || numeric != null && numeric.isFinite() && numeric in 0.0..a.maximum) {
                        vm.act { vm.repository.saveGrade(a.id, student.id, if (value.isBlank()) null else numeric) }
                    }
                }, label = { Text("Nota (0 a ${number(a.maximum)})") }, isError = !valid,
                    supportingText = { Text(if (!valid) "Valor inválido — não salvo" else if (text.isBlank()) "Pendente" else "Salvamento automático") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
            } }
        }
    }
    if (confirm) Confirm("Excluir atividade?", "A atividade e suas notas serão excluídas permanentemente.", { confirm = false }) {
        vm.act(done = { confirm = false; deleted() }) { vm.repository.dao.deleteActivity(a.id) }
    }
}

@Composable fun ReportScreen(data: DiarySnapshot, c: SchoolClass, term: Int, profile: (String) -> Unit) {
    var order by rememberSaveable { mutableStateOf("Chamada") }
    val period = data.terms.firstOrNull { it.classId == c.id && it.number == term }
    val activities = data.activities.filter { it.classId == c.id && it.term == term }
    val students = data.roster(c.id)
    val sorted = when (order) {
        "Mais faltas" -> students.sortedByDescending { data.frequency(it, period, c.countJustified).absent }
        "Menor nota" -> students.sortedBy { data.result(it, term, c.scale).total }
        "Maior nota" -> students.sortedByDescending { data.result(it, term, c.scale).total }
        else -> students
    }
    val scroll = rememberScrollState()
    Column(Modifier.padding(16.dp)) {
        Section("Boletim do ${term}º bimestre", "${c.name} • ${c.subject} • ${c.year}")
        Choice("Ordenação", listOf("Chamada", "Mais faltas", "Menor nota", "Maior nota"), order) { order = it }
        Text("Deslize a tabela para os lados. Toque no aluno para ver seu histórico.", style = MaterialTheme.typography.bodySmall)
        Box(Modifier.horizontalScroll(scroll)) {
            LazyColumn(Modifier.width((220 + activities.size * 120 + 350).dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item {
                    Row(Modifier.padding(vertical = 12.dp)) {
                        Text("Aluno", Modifier.width(220.dp))
                        activities.forEach { Text(it.name, Modifier.width(120.dp)) }
                        Text("Nota final", Modifier.width(150.dp)); Text("F / FJ", Modifier.width(80.dp)); Text("Frequência", Modifier.width(120.dp))
                    }
                    HorizontalDivider()
                }
                items(sorted, key = { it.id }) { student ->
                    val result = data.result(student, term, c.scale)
                    val f = data.frequency(student, period, c.countJustified)
                    val color = when { result.total < c.low -> Color(0xFFAD3333); result.total < c.high -> Color(0xFF9B7100); else -> Color(0xFF247749) }
                    Row(Modifier.clickable { profile(student.id) }.padding(vertical = 12.dp)) {
                        Text("${student.number}. ${student.name}", Modifier.width(220.dp))
                        activities.forEach { a ->
                            val score = data.grades.firstOrNull { it.activityId == a.id && it.studentId == student.id }?.value
                            Text(if (!student.enrolled(a.date)) "—" else score?.let { number(it) } ?: "Pendente", Modifier.width(120.dp))
                        }
                        Text("${number(result.total)}${if (result.partial) "\nParcial (${result.pending} pend.)" else ""}", Modifier.width(150.dp), color = color)
                        Text("${f.absent} / ${f.justified}", Modifier.width(80.dp))
                        Text("${number(f.percent)}%", Modifier.width(120.dp))
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
