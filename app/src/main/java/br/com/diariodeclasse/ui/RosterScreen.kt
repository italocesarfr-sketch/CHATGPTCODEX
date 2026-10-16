package br.com.diariodeclasse.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.domain.CsvRoster
import java.text.Collator

@Composable fun RosterScreen(vm: DiaryViewModel, data: DiarySnapshot, c: SchoolClass, photo: () -> Unit, profile: (String) -> Unit) {
    var form by remember { mutableStateOf(false) }; var bulk by remember { mutableStateOf(false) }
    var names by rememberSaveable { mutableStateOf("") }; var joined by rememberSaveable(c.id) { mutableStateOf(shortDate(defaultEnrollment(c.year))) }
    var alphabetical by rememberSaveable { mutableStateOf(true) }
    val context = LocalContext.current
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.act(done = { bulk = true }) {
            val text = context.contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytesLimited(2_000_000); bytes.toString(Charsets.UTF_8)
            }
                ?: throw IllegalArgumentException("Não foi possível ler o arquivo.")
            require(text.length <= 2_000_000) { "Arquivo muito grande. Use até 2 MB." }
            val imported = CsvRoster.names(text).joinToString("\n")
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { names = imported }
        }
    }
    val collator = remember { Collator.getInstance(brLocale) }
    val roster = data.roster(c.id).let { if (alphabetical) it.sortedWith { a, b -> collator.compare(a.name, b.name) } else it }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Section("Alunos", "${roster.size} matrículas • registros preservados por período")
            Button(onClick = photo, modifier = Modifier.fillMaxWidth()) { Text("Importar alunos por fotografia") }
            OutlinedButton(onClick = { form = true }, modifier = Modifier.fillMaxWidth()) { Text("+ Adicionar aluno") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { names = ""; bulk = true }, modifier = Modifier.weight(1f)) { Text("Colar lista") }
                OutlinedButton(onClick = { csv.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel")) }, modifier = Modifier.weight(1f)) { Text("Importar CSV") }
            }
            Row { Checkbox(alphabetical, { alphabetical = it }); Text("Ordem alfabética", Modifier.padding(top = 12.dp)) }
            Text("Desmarque para usar os números da chamada. Para reordenar, edite o número no perfil.", style = MaterialTheme.typography.bodySmall)
        }
        items(roster, key = { it.id }) { student ->
            Card(Modifier.fillMaxWidth().clickable { profile(student.id) }) { Column(Modifier.padding(16.dp)) {
                Text("${student.number}. ${student.name}", style = MaterialTheme.typography.titleMedium)
                Text("${student.status} • ingresso ${shortDate(student.joined)}", style = MaterialTheme.typography.bodySmall)
                if (!alphabetical) Row {
                    TextButton(onClick = { vm.act { vm.repository.moveStudent(student.id, -1) } }) { Text("↑ Subir") }
                    TextButton(onClick = { vm.act { vm.repository.moveStudent(student.id, 1) } }) { Text("↓ Descer") }
                }
            } }
        }
    }
    if (form) StudentForm(vm, c, null) { form = false }
    if (bulk) Form("Conferir lista de alunos", { bulk = false }) {
        Field("Um nome por linha", names, { names = it }, lines = 6)
        Field("Ingresso (dd/mm/aaaa)", joined, { joined = it })
        Info("Novos nomes serão ordenados alfabeticamente e numerados após os atuais. Nomes já cadastrados serão ignorados. Ajuste números e homônimos no cadastro individual.")
        Button(onClick = { vm.act(done = { bulk = false }) { vm.repository.addNames(c.id, names.lines(), iso(joined)) } }) { Text("Confirmar cadastro") }
    }
}

@Composable fun ProfileScreen(vm: DiaryViewModel, data: DiarySnapshot, c: SchoolClass, student: Student, term: Int, changeTerm: (Int) -> Unit) {
    var edit by remember { mutableStateOf(false) }; var transfer by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf("") }; var day by rememberSaveable { mutableStateOf(shortDate(today())) }
    val period = data.terms.firstOrNull { it.classId == c.id && it.number == term }
    val frequency = data.frequency(student, period, c.countJustified)
    val result = data.result(student, term, c.scale)
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Section(student.name, "${c.name} • nº ${student.number} • ${student.status}")
            TextButton(onClick = { edit = true }) { Text("Editar cadastro e observações") }
            if (student.status == "Ativo") TextButton(onClick = { transfer = true }) { Text("Transferir para outra turma") }
            TermSelector(term, changeTerm)
            Info("Nota ${number(result.total)} / ${number(c.scale)}${if (result.partial) " • parcial" else ""}\n${result.pending} notas pendentes\nFaltas: ${frequency.absent} • justificadas: ${frequency.justified}\nPresenças: ${frequency.present} • frequência: ${number(frequency.percent)}%\n${frequency.valid} chamadas válidas de ${frequency.performed} aulas realizadas")
            if (student.notes.isNotBlank()) Text("Observações: ${student.notes}")
        }
        items(data.activities.filter { it.classId == c.id && it.term == term }, key = { it.id }) { activity ->
            val grade = data.grades.firstOrNull { it.activityId == activity.id && it.studentId == student.id }?.value
            Text("${activity.name}: ${if (!student.enrolled(activity.date)) "Fora da matrícula" else grade?.let { number(it) } ?: "Pendente"} / ${number(activity.maximum)}")
        }
        item { Section("Histórico anual") }
        items((1..4).toList()) { n ->
            val r = data.result(student, n, c.scale)
            val f = data.frequency(student, data.terms.firstOrNull { it.classId == c.id && it.number == n }, c.countJustified)
            Text("${n}º bim. • ${number(r.total)}${if (r.partial) " (parcial)" else ""} • ${f.absent} F • ${f.justified} FJ")
        }
        item {
            val annual = data.frequency(student, null, c.countJustified)
            Info("Ano: ${annual.absent} faltas e ${annual.justified} faltas justificadas. Frequência ${number(annual.percent)}%.")
        }
    }
    if (edit) StudentForm(vm, c, student) { edit = false }
    if (transfer) Form("Transferir aluno", { transfer = false }) {
        val targets = data.classes.filter { it.id != c.id }
        targets.forEach { klass -> Row { RadioButton(target == klass.id, { target = klass.id }); Text("${klass.name} • ${klass.year}", Modifier.padding(top = 12.dp)) } }
        Field("Data da transferência (dd/mm/aaaa)", day, { day = it })
        Info("O histórico permanecerá nesta turma. A matrícula de destino começará na data informada.")
        Button(enabled = target.isNotBlank(), onClick = { vm.act(done = { transfer = false }) { vm.repository.transfer(student.id, target, iso(day)) } }) { Text("Confirmar transferência") }
    }
}

private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val size = read(buffer); if (size < 0) break
        require(output.size() + size <= limit) { "Arquivo muito grande. Use até 2 MB." }
        output.write(buffer, 0, size)
    }
    return output.toByteArray()
}
