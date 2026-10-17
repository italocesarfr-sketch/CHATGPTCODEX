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
    var showRelocated by rememberSaveable(a.id) {mutableStateOf(false)}
    var confirm by remember { mutableStateOf(false) }
    var invalid by rememberSaveable(a.id, a.maximum) { mutableStateOf(listOf<String>()) }
    LaunchedEffect(invalid) { dirty(invalid.isNotEmpty()) }
    DisposableEffect(a.id) { onDispose { dirty(false) } }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            RelocatedFilter(showRelocated) {showRelocated=it}
            Section(a.name, "${a.term}º bimestre • ${shortDate(a.date)} • máximo ${number(a.maximum)}")
            Info("Notas válidas são salvas automaticamente. Deixe vazio para pendente; digite 0 para nota zero.")
            TextButton(onClick = edit) { Text("Editar atividade") }
            TextButton(onClick = { confirm = true }) { Text("Excluir atividade") }
        }
        items(data.currentRoster(a.classId,showRelocated).filter { it.enrolled(a.date) }, key = { it.id }) { student ->
            val grade = data.grades.firstOrNull { it.activityId == a.id && it.studentId == student.id }?.value
            var text by rememberSaveable(a.id, student.id, a.maximum) { mutableStateOf(grade?.let { editableNumber(it) } ?: "") }
            val valid = text.isBlank() || decimal(text)?.let { it.isFinite() && it in 0.0..a.maximum } == true
            LaunchedEffect(student.id, text, a.maximum) { invalid = if (valid) invalid - student.id else (invalid + student.id).distinct() }
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                Text("${student.number}. ${student.name}", style = MaterialTheme.typography.titleMedium)
                if(student.status=="Remanejado") Text("Histórico somente leitura: ${grade?.let(::number) ?: "Pendente"}")
                else OutlinedTextField(value = text, onValueChange = { value ->
                    text = value
                    val numeric = decimal(value)
                    invalid = if (value.isBlank() || numeric != null && numeric.isFinite() && numeric in 0.0..a.maximum) invalid - student.id else (invalid + student.id).distinct()
                    if (value.isBlank() || numeric != null && numeric.isFinite() && numeric in 0.0..a.maximum) {
                        vm.act { vm.repository.saveGrade(a.id, student.id, if (value.isBlank()) null else numeric) }
                    }
                }, label = { Text("Nota (0 a ${number(a.maximum)})") }, isError = !valid,
                    supportingText = { Text(if (!valid) "Valor inválido — não salvo" else if (text.isBlank()) "Pendente" else "Salvamento automático") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
            } }
        }
    }
    if (confirm) DeleteActivityDialog(vm, a.id, { confirm = false }) { confirm = false; deleted() }
}
