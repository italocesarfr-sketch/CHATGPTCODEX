package br.com.diariodeclasse.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.domain.RecompositionRules
import br.com.diariodeclasse.reports.*

@Composable fun RecompositionScreen(vm: DiaryViewModel, data: DiarySnapshot, c: SchoolClass, term: Int, changeTerm: (Int) -> Unit) {
    var all by rememberSaveable(c.id) { mutableStateOf(false) }
    var feedback by rememberSaveable(c.id, term) { mutableStateOf("") }
    var editing by rememberSaveable(c.id, term) { mutableStateOf<String?>(null) }
    var showRelocated by rememberSaveable(c.id) {mutableStateOf(false)}
    val reports = remember(data, c, term, showRelocated) { SchoolReports(data, c, showRelocated).term(term) }
    val rows = reports.students.filter { all || RecompositionRules.belowFive(it.total) }
    Column(Modifier.fillMaxSize()) {
        if (feedback.isNotBlank()) Text(feedback, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("recomposition-feedback"), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.weight(1f).testTag("recomposition-list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                RelocatedFilter(showRelocated) {showRelocated=it}
                Section("Recomposição de Notas", "${c.name} • ${c.subject} • ${c.year}")
                Text("Bimestre de origem: ${term}º. A data da avaliação pode pertencer a outro bimestre.")
                TermSelector(term, changeTerm)
                Text("Vale de 0 a 10. O resultado vigente é o maior entre o original e a recomposição, limitado a 10. Não soma pontos às atividades.", style = MaterialTheme.typography.bodySmall)
                Choice("Mostrar", listOf("Abaixo de 5,00", "Todos os alunos"), if (all) "Todos os alunos" else "Abaixo de 5,00") { all = it == "Todos os alunos" }
                Text("Sem qualquer nota original lançada: Pendente, fora do filtro abaixo de 5. Notas originais parciais continuam identificadas.", style = MaterialTheme.typography.bodySmall)
                if (c.scale != 10.0) Info("A escala original da turma é ${number(c.scale)}. A recomposição usa 0 a 10 e seu resultado é limitado a 10; a soma das atividades não será alterada.")
            }
            if (rows.isEmpty()) item { Info(if (all) "Nenhum aluno cadastrado nesta turma." else "Nenhum aluno com resultado conhecido abaixo de 5,00. Consulte Todos os alunos para pendências e históricos.") }
            items(rows, key = { it.student.id }) { row ->
                OutlinedCard(Modifier.fillMaxWidth().testTag("recomposition-row-${row.student.id}")) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${row.student.number}. ${row.student.name}", style = MaterialTheme.typography.titleSmall)
                        Text("Original: ${row.original?.let(::number) ?: "Pendente"}\nRecomposição: ${row.recomposition?.value?.let(::number) ?: "Não realizada"}\nVigente: ${row.total?.let(::number) ?: "Pendente"}", Modifier.testTag("recomposition-values-${row.student.id}"))
                        Text(gradeStatus(row), style = MaterialTheme.typography.bodySmall)
                        row.recomposition?.let { Text("Avaliação: ${shortDate(it.date)} • origem ${it.term}º bimestre • ${row.recompositionSchool?.name ?: c.name}", style = MaterialTheme.typography.bodySmall) }
                        if(row.student.status!="Remanejado") TextButton(onClick = { editing = row.student.id }, modifier = Modifier.testTag("recomposition-edit-${row.student.id}")) {
                            Text(if (row.recomposition == null) "Lançar recomposição" else "Editar recomposição")
                        }
                    }
                }
            }
        }
    }
    editing?.let { id -> reports.students.firstOrNull { it.student.id == id }?.let { row ->
        key(id, term) { RecompositionEditor(vm, row, close = { editing = null }, saved = { feedback = it; editing = null }) }
    } }
}

/** O diálogo bloqueia filtros e seletores globais; Voltar pede descarte se houver rascunho. */
@Composable private fun RecompositionEditor(vm: DiaryViewModel, row: StudentPeriodReport, close: () -> Unit, saved: (String) -> Unit) {
    val expected = remember { row.recomposition }
    val initialValue = expected?.value?.let(::editableNumber).orEmpty()
    val initialDate = expected?.date?.let(::shortDate) ?: shortDate(today())
    var value by rememberSaveable { mutableStateOf(initialValue) }
    var date by rememberSaveable { mutableStateOf(initialDate) }
    var discard by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    val saving by vm.saving.collectAsState()
    val score = if (value.isBlank()) null else decimal(value)
    val valid = (value.isBlank() || score != null && score.isFinite() && score in 0.0..10.0) && runCatching { iso(date) }.isSuccess
    val dirty = value != initialValue || date != initialDate
    fun leave() { if (!saving) { if (dirty) discard = true else close() } }
    Form("Recomposição • ${row.number}º bimestre", ::leave) {
        Text("${row.student.number}. ${row.student.name}")
        if(expected!=null && expected.studentId!=row.student.id) Info("Recomposição preservada na matrícula de origem. A confirmação corrige esse mesmo registro, sem criar outra recomposição no destino.")
        Text("Resultado original: ${row.original?.let(::number) ?: "Pendente"}")
        Field("Nota da recomposição (0 a 10)", value, { if (!saving) value = it }, Modifier.testTag("recomposition-score"), enabled = !saving)
        Text("Vazio = não realizada. Zero é uma nota lançada.", style = MaterialTheme.typography.bodySmall)
        Field("Data da avaliação (dd/mm/aaaa)", date, { if (!saving) date = it }, Modifier.testTag("recomposition-date"), enabled = !saving)
        if (valid) Info("Prévia do resultado vigente: ${RecompositionRules.effective(row.original, score)?.let(::number) ?: "Pendente: faltam lançamentos originais"}")
        else Text("Informe nota de 0 a 10 e uma data válida.", color = MaterialTheme.colorScheme.error)
        Button(enabled = valid && !saving, onClick = {
            val record = Recomposition(expected?.studentId ?: row.student.id, row.number, score, iso(date))
            vm.act(done = { saved("Recomposição salva no ${row.number}º bimestre.") }) { vm.repository.saveRecomposition(record, expected, row.student.id) }
        }, modifier = Modifier.fillMaxWidth().testTag("recomposition-save")) { Text(if (saving) "Salvando…" else "Salvar recomposição") }
        if (expected != null) OutlinedButton(enabled = !saving, onClick = { deleting = true }, modifier = Modifier.fillMaxWidth().testTag("recomposition-delete")) { Text("Excluir recomposição") }
        SaveError(vm)
    }
    if (discard) Confirm("Descartar alterações não salvas?", "A nota e a data digitadas ainda não foram confirmadas. Os registros já salvos serão preservados.", { discard = false }) { discard = false; close() }
    if (deleting && expected != null) Confirm("Excluir recomposição?", "O resultado será recalculado a partir das atividades originais. As notas das atividades serão preservadas.", { deleting = false }) {
        deleting = false
        vm.act(done = { saved("Recomposição excluída. Resultado original preservado.") }) { vm.repository.deleteRecomposition(expected, row.student.id) }
    }
}
