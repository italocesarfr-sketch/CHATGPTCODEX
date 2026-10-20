package br.com.diariodeclasse.ui

import br.com.diariodeclasse.ui.theme.NoctuaSpace
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.domain.CsvRoster
import java.text.Collator

@Composable fun RosterScreen(vm: DiaryViewModel, data: DiarySnapshot, c: SchoolClass,
    importGuard: (((() -> Unit) -> Unit)?) -> Unit = {}, profile: (String) -> Unit) {
    val saving by vm.saving.collectAsState()
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
    var showRelocated by rememberSaveable(c.id) { mutableStateOf(false) }
    val roster = data.currentRoster(c.id, showRelocated).let { if (alphabetical) it.sortedWith { a, b -> collator.compare(a.name, b.name) } else it.sortedBy { s -> s.number } }
    if (review) {
        ImportReviewScreen(vm, data, c, importGuard) { review = false }
        return
    }
    LazyColumn(Modifier.fillMaxSize().testTag("roster-list"), contentPadding = PaddingValues(NoctuaSpace.page), verticalArrangement = Arrangement.spacedBy(NoctuaSpace.medium)) {
        item {
            if(data.roster(c.id).any {it.status=="Remanejado"}) RelocatedFilter(showRelocated) {showRelocated=it}
            Section("Alunos da turma", "${c.name} • ${counted(roster.size, "matrícula", "matrículas")}")
                Button(onClick = { editing = null; form = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text("+ Adicionar aluno") }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NoctuaSpace.small)) {
                    OutlinedButton(onClick = { names = ""; bulk = true }, modifier = Modifier.weight(1f).heightIn(min = 50.dp)) { Text("Colar lista") }
                    OutlinedButton(onClick = { csvTarget = c.id; csv.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream")) }, modifier = Modifier.weight(1f).heightIn(min = 50.dp)) { Text("Importar CSV") }
                }
                val drafts = data.drafts.count { it.classId == c.id }
                if (drafts > 0) FilledTonalButton(onClick = { review = true }, modifier = Modifier.fillMaxWidth().testTag("import-review-open")) { Text("Conferir ${counted(drafts, "aluno", "alunos")}") }
                Choice("Organização da lista", listOf("Ordem alfabética", "Ordem da chamada"), if (alphabetical) "Ordem alfabética" else "Ordem da chamada") { alphabetical = it == "Ordem alfabética" }
        }
        if (roster.isEmpty()) item { Text(if(data.roster(c.id).isEmpty()) "Nenhum aluno cadastrado." else "Nenhum aluno nesta visualização.") }
        items(roster, key = { it.id }) { student ->
            Card(Modifier.fillMaxWidth().testTag("roster-student-${student.id}").clickable { profile(student.id) }) { Column(Modifier.padding(NoctuaSpace.page), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row {
                    Text("${student.number}. ${student.name}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                }
                RelocationNotice(data,student)
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
        Button(onClick = {
            val text = names; val target = c.id
            vm.act(done = { bulk = false; review = true }) { vm.repository.appendDrafts(target, CsvRoster.parse(text)) }
        }, modifier = Modifier.fillMaxWidth()) { Text("Preparar conferência") }
        SaveError(vm)
    }
}

@Composable fun ProfileScreen(vm: DiaryViewModel, data: DiarySnapshot, c: SchoolClass, student: Student, term: Int, changeTerm: (Int) -> Unit) {
    var edit by remember { mutableStateOf(false) }; var relocate by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row {
            TextButton(onClick={edit=true}) {Text("Editar cadastro e observações")}
            if(student.status=="Ativo") TextButton(onClick={relocate=true}) {Text("Remanejar")}
        }
        Box(Modifier.weight(1f)) {IndividualReportScreen(data,c,student,term,changeTerm)}
    }
    if(edit) StudentForm(vm,c,student) {edit=false}
    if(relocate) StudentForm(vm,c,student,initialStatus="Remanejado") {relocate=false}
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
