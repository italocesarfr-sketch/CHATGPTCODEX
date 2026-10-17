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

@Composable fun RosterScreen(vm: DiaryViewModel, data: DiarySnapshot, c: SchoolClass, profile: (String) -> Unit) {
    var form by remember { mutableStateOf(false) }; var editing by remember { mutableStateOf<Student?>(null) }
    var bulk by rememberSaveable { mutableStateOf(false) }; var review by rememberSaveable { mutableStateOf(false) }
    var names by rememberSaveable { mutableStateOf("") }; var csvTarget by rememberSaveable { mutableStateOf(c.id) }
    var alphabetical by rememberSaveable(c.id) { mutableStateOf(true) }
    val context = LocalContext.current
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val target = csvTarget
            vm.act(done = { review = true }) {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytesLimited(2_000_000) }
                    ?: throw IllegalArgumentException("Não foi possível ler o arquivo.")
                val text = try { Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes)).toString() }
                    catch (_: java.nio.charset.CharacterCodingException) { throw IllegalArgumentException("Salve o arquivo CSV em UTF-8 para preservar os acentos.") }
                vm.repository.appendDrafts(target, CsvRoster.parse(text))
            }
        }
    }
    val collator = remember { Collator.getInstance(brLocale) }
    val roster = data.roster(c.id).let { if (alphabetical) it.sortedWith { a, b -> collator.compare(a.name, b.name) } else it.sortedBy { s -> s.number } }
    if (review) {
        ImportReviewScreen(vm, data, c) { review = false }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Section("Alunos da turma", "${c.name} • ${counted(roster.size, "matrícula", "matrículas")}")
            Button(onClick = { editing = null; form = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text("+ Adicionar aluno") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { names = ""; bulk = true }, modifier = Modifier.weight(1f).heightIn(min = 50.dp)) { Text("Colar lista") }
                OutlinedButton(onClick = { csvTarget = c.id; csv.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream")) }, modifier = Modifier.weight(1f).heightIn(min = 50.dp)) { Text("Importar CSV") }
            }
            val drafts = data.drafts.count { it.classId == c.id }
            if (drafts > 0) FilledTonalButton(onClick = { review = true }, modifier = Modifier.fillMaxWidth()) { Text("Conferir ${counted(drafts, "nome", "nomes")} em rascunho") }
            Choice("Organização da lista", listOf("Ordem alfabética", "Ordem da chamada"), if (alphabetical) "Ordem alfabética" else "Ordem da chamada") { alphabetical = it == "Ordem alfabética" }
            Text("A ordenação alfabética não altera os números. Na ordem da chamada, use Subir/Descer ou edite o aluno.", style = MaterialTheme.typography.bodySmall)
        }
        if (roster.isEmpty()) item { Info("Sua turma ainda não tem alunos. Adicione individualmente ou importe uma lista para conferir.") }
        items(roster, key = { it.id }) { student ->
            Card(Modifier.fillMaxWidth().clickable { profile(student.id) }) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${student.number}. ${student.name}", style = MaterialTheme.typography.titleMedium)
                Text("${student.status} • ingresso ${shortDate(student.joined)}", style = MaterialTheme.typography.bodySmall)
                Row {
                    TextButton(onClick = { editing = student; form = true }) { Text("Editar") }
                    TextButton(onClick = { profile(student.id) }) { Text("Ver histórico") }
                }
                if (!alphabetical) Row {
                    TextButton(onClick = { val id = student.id; vm.act { vm.repository.moveStudent(id, -1) } }) { Text("↑ Subir") }
                    TextButton(onClick = { val id = student.id; vm.act { vm.repository.moveStudent(id, 1) } }) { Text("↓ Descer") }
                }
            } }
        }
    }
    if (form) StudentForm(vm, c, editing) { form = false }
    if (bulk) Form("Colar lista de alunos", { bulk = false }) {
        Field("Um nome por linha (número opcional)", names, { names = it }, lines = 6)
        Info("Exemplo: 12 Estudante Fictício Alfa. Os números existentes serão preservados. Nada será matriculado antes da próxima conferência.")
        Button(onClick = {
            val text = names; val target = c.id
            vm.act(done = { bulk = false; review = true }) { vm.repository.appendDrafts(target, CsvRoster.parse(text)) }
        }, modifier = Modifier.fillMaxWidth()) { Text("Preparar conferência") }
        SaveError(vm)
    }
}

@Composable fun ProfileScreen(vm: DiaryViewModel, data: DiarySnapshot, c: SchoolClass, student: Student, term: Int, changeTerm: (Int) -> Unit) {
    var edit by remember { mutableStateOf(false) }; var transfer by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf("") }; var day by rememberSaveable { mutableStateOf(shortDate(today())) }
    val period = data.terms.firstOrNull { it.classId == c.id && it.number == term }
    val frequency = data.frequency(student, period, c.countJustified)
    val report = br.com.diariodeclasse.reports.SchoolReports(data, c).student(student, term)
    val result = report.grades
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Section(student.name, "${c.name} • nº ${student.number} • ${student.status}")
            TextButton(onClick = { edit = true }) { Text("Editar cadastro e observações") }
            if (student.status == "Ativo") TextButton(onClick = { transfer = true }) { Text("Transferir para outra turma") }
            TermSelector(term, changeTerm)
            Info("Nota ${report.total?.let(::number) ?: "Pendente"} / ${number(c.scale)}${if (result.partial) " • parcial" else ""}\n${result.pending} notas pendentes${recompositionText(report)}\nFaltas: ${frequency.absent} • justificadas: ${frequency.justified}\nPresenças: ${frequency.present} • frequência: ${number(frequency.percent)}%\n${frequency.valid} chamadas válidas de ${frequency.performed} aulas realizadas")
            if (student.notes.isNotBlank()) Text("Observações: ${student.notes}")
        }
        items(data.activities.filter { it.classId == c.id && it.term == term }, key = { it.id }) { activity ->
            val grade = data.grades.firstOrNull { it.activityId == activity.id && it.studentId == student.id }?.value
            Text("${activity.name}: ${if (!student.enrolled(activity.date)) "Fora da matrícula" else grade?.let { number(it) } ?: "Pendente"} / ${number(activity.maximum)}")
        }
        item { Section("Histórico anual") }
        items((1..4).toList()) { n ->
            val reportRow = br.com.diariodeclasse.reports.SchoolReports(data, c).student(student, n)
            val r = reportRow.grades
            val f = data.frequency(student, data.terms.firstOrNull { it.classId == c.id && it.number == n }, c.countJustified)
            Text("${n}º bim. • ${reportRow.total?.let(::number) ?: "Pendente"}${if (r.partial) " (parcial)" else ""} • ${f.absent} F • ${f.justified} FJ${recompositionText(reportRow)}")
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
