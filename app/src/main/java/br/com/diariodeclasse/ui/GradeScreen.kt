package br.com.diariodeclasse.ui

import br.com.diariodeclasse.ui.theme.NoctuaSpace
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.com.diariodeclasse.data.*

@Composable fun GradeScreen(vm: DiaryViewModel, data: DiarySnapshot, a: Activity, edit: () -> Unit, deleted: () -> Unit, dirty: (Boolean) -> Unit = {}) {
    val focus=LocalFocusManager.current
    var showRelocated by rememberSaveable(a.id) {mutableStateOf(false)}
    var query by rememberSaveable(a.id) { mutableStateOf("") }
    var orderName by rememberSaveable(a.id) { mutableStateOf(GradeRosterOrder.NUMBER.name) }
    val students=GradeRoster.select(data.currentRoster(a.classId,showRelocated).filter{it.enrolled(a.date)},query,GradeRosterOrder.valueOf(orderName))
    var confirm by remember { mutableStateOf(false) }
    var invalidDrafts by rememberSaveable(a.id, a.maximum) { mutableStateOf(mapOf<String,String>()) }
    val invalid=invalidDrafts.keys.toList()
    LaunchedEffect(invalid) { dirty(invalid.isNotEmpty()) }
    DisposableEffect(a.id) { onDispose { dirty(false) } }
    LazyColumn(modifier=Modifier.testTag("grades-list"),contentPadding = PaddingValues(NoctuaSpace.page), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            if(data.roster(a.classId).any {it.status=="Remanejado"}) RelocatedFilter(showRelocated) {showRelocated=it}
            Section(a.name, "${a.term}º bimestre • ${shortDate(a.date)} • máximo ${number(a.maximum)}")
            Text("Vazio: pendente • 0: nota zero")
        }
        item {
            OutlinedTextField(query,{query=it},placeholder={Text("Buscar por nome ou nº")},leadingIcon={Icon(Icons.Default.Search,null)},singleLine=true,keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={focus.clearFocus()}),modifier=Modifier.fillMaxWidth().testTag("grade-search"))
        }
        item { Choice("Ordenar alunos",GradeRosterOrder.entries.map{it.label},GradeRosterOrder.valueOf(orderName).label) { selected->orderName=GradeRosterOrder.entries.first{it.label==selected}.name } }
        item {
            TextButton(onClick = edit) { Text("Editar atividade") }
            TextButton(onClick = { confirm = true }) { Text("Excluir atividade") }
        }
        if(students.isEmpty()) item { Text("Nenhum aluno encontrado.") }
        items(students, key = { it.id }) { student ->
            val grade = data.grades.firstOrNull { it.activityId == a.id && it.studentId == student.id }?.value
            var text by rememberSaveable(a.id, student.id, a.maximum) { mutableStateOf(invalidDrafts[student.id] ?: grade?.let { editableNumber(it) } ?: "") }
            val valid = text.isBlank() || decimal(text)?.let { it.isFinite() && it in 0.0..a.maximum } == true
            Card(Modifier.fillMaxWidth().testTag("grade-student-${student.id}")) { Column(Modifier.padding(NoctuaSpace.medium)) {
                Text("${student.number}. ${student.name}", style = MaterialTheme.typography.titleMedium)
                if(student.status=="Remanejado") Text("Histórico somente leitura: ${grade?.let(::number) ?: "Pendente"}")
                else OutlinedTextField(value = text, onValueChange = { value ->
                    text = value
                    val numeric = decimal(value)
                    invalidDrafts = if (value.isBlank() || numeric != null && numeric.isFinite() && numeric in 0.0..a.maximum) invalidDrafts - student.id else invalidDrafts + (student.id to value)
                    if (value.isBlank() || numeric != null && numeric.isFinite() && numeric in 0.0..a.maximum) {
                        vm.act { vm.repository.saveGrade(a.id, student.id, if (value.isBlank()) null else numeric) }
                    }
                }, label = { Text("Nota (0 a ${number(a.maximum)})") }, isError = !valid,
                    supportingText = { Text(if (!valid) "Valor inválido — não salvo" else if (text.isBlank()) "Pendente" else "Salvamento automático") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth().testTag("grade-value-${student.id}"))
            } }
        }
    }
    if (confirm) DeleteActivityDialog(vm, a.id, { confirm = false }) { confirm = false; deleted() }
}
