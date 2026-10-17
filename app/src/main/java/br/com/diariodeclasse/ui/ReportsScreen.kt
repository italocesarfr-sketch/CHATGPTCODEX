package br.com.diariodeclasse.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.domain.SchoolRules
import br.com.diariodeclasse.reports.*

internal fun gradeStatus(row: StudentPeriodReport): String = when (row.state) {
    GradeState.OUTSIDE_ENROLLMENT -> "Fora da matrícula"
    GradeState.NO_ACTIVITIES -> "Sem atividades"
    GradeState.NO_SCORES -> "Notas não lançadas (${row.grades.pending} pend.)"
    GradeState.PARTIAL -> "Parcial (${row.grades.pending} pend.)"
    GradeState.COMPLETE -> "Notas completas"
}
private fun percent(f: SchoolRules.Frequency?) = f?.percent?.let { "${number(it)}%" } ?: "Sem registros válidos"
private fun frequencyText(f: SchoolRules.Frequency?): String = if (f == null) "Bimestre não configurado" else
    "Aulas elegíveis: ${f.performed} • presenças: ${f.present}\nFaltas: ${f.absent} • justificadas: ${f.justified}\nFrequência: ${percent(f)}\n${f.valid} chamadas válidas • ${f.performed - f.valid} não registradas"
private fun justificationPolicy(c: SchoolClass) = if (c.countJustified)
    "Faltas efetivas: F + FJ, conforme a configuração da turma. Justificar não abona a falta."
else "Faltas efetivas: somente F. FJ é abonada no percentual, conforme a configuração da turma."

@Composable fun ReportScreen(data: DiarySnapshot, c: SchoolClass, term: Int, profile: (String) -> Unit) {
    var mode by rememberSaveable { mutableStateOf("Bimestral") }
    var order by rememberSaveable { mutableStateOf("Chamada") }
    val reports = remember(data, c) { SchoolReports(data, c) }
    val report = remember(reports, term) { reports.term(term) }
    val annual = remember(reports) { reports.annual() }
    val sorted = when (order) {
        "Mais faltas" -> report.students.sortedWith(compareByDescending<StudentPeriodReport> { it.effectiveAbsences(c.countJustified) }.thenBy { it.student.number }.thenBy { it.student.id })
        "Menor nota" -> report.students.sortedWith(compareBy<StudentPeriodReport> { it.total == null }.thenBy { it.total }.thenBy { it.student.number }.thenBy { it.student.id })
        "Maior nota" -> report.students.sortedWith(compareBy<StudentPeriodReport> { it.total == null }.thenByDescending { it.total }.thenBy { it.student.number }.thenBy { it.student.id })
        else -> report.students
    }
    val ranking = remember(report, c.countJustified) { SchoolReports.rank(report.students, c.countJustified) }
    val horizontal = rememberScrollState()
    // Uma única lista vertical; a rolagem horizontal das células mantém o nome visível.
    @Composable fun TableRow(name: String, tag: String, cells: List<String>, totalIndex: Int? = null, totalValue: Double? = null, click: (() -> Unit)? = null) {
        Row(Modifier.fillMaxWidth().testTag(tag).then(if (click == null) Modifier else Modifier.clickable(onClick = click)).padding(vertical = 12.dp)) {
            Text(name, Modifier.width(150.dp), style = MaterialTheme.typography.bodySmall)
            Row(Modifier.weight(1f).horizontalScroll(horizontal)) {
                cells.forEachIndexed { index, cell ->
                    val color = if (index != totalIndex || totalValue == null) MaterialTheme.colorScheme.onSurface else when {
                        totalValue < c.low -> Color(0xFFAD3333); totalValue < c.high -> Color(0xFF9B7100); else -> Color(0xFF247749)
                    }
                    Text(cell, Modifier.width(135.dp).padding(end = 8.dp).testTag("$tag-cell-$index"), style = MaterialTheme.typography.bodySmall, color = color)
                }
            }
        }
        HorizontalDivider()
    }
    LazyColumn(Modifier.fillMaxSize().testTag("reports-list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Section(when (mode) {
                "Frequência" -> "Frequência do ${term}º bimestre"
                "Mais faltosos" -> "Mais faltosos do ${term}º bimestre"
                "Resumo anual" -> "Resumo anual de ${c.year}"
                else -> "Boletim do ${term}º bimestre"
            }, "${c.name} • ${c.subject} • ${c.year}")
            Choice("Relatório", listOf("Bimestral", "Frequência", "Mais faltosos", "Resumo anual"), mode) { mode = it }
            if (mode == "Bimestral") Choice("Ordenação", listOf("Chamada", "Mais faltas", "Menor nota", "Maior nota"), order) { order = it }
            if (mode == "Bimestral") Text("Escala: 0 a ${number(c.scale)}. Parcial se houver pendências ou pontuação disponível abaixo da escala.", style = MaterialTheme.typography.bodySmall)
            if (mode != "Resumo anual") Text(report.period?.let { "${shortDate(it.start)} a ${shortDate(it.end)}" } ?: "Bimestre não configurado", style = MaterialTheme.typography.bodySmall)
            Text("Deslize as colunas para os lados. Toque no aluno para ver seu histórico.", style = MaterialTheme.typography.bodySmall)
            if (mode == "Resumo anual") Text("Quatro resultados separados; não há média anual ou decisão de aprovação.", style = MaterialTheme.typography.bodySmall)
        }
        if (report.students.isEmpty()) item { Info("Nenhum aluno cadastrado nesta turma.") }
        when (mode) {
            "Bimestral" -> {
                if (report.activities.isEmpty()) item { Info("Nenhuma atividade neste bimestre. Não há notas a totalizar.") }
                item { TableRow("Aluno", "report-header", report.activities.map { "${it.name}\nMáx. ${number(it.maximum)}" } + listOf("Total obtido", "Pendências", "F / FJ", "Frequência")) }
                items(sorted, key = { it.student.id }) { row ->
                    TableRow("${row.student.number}. ${row.student.name}", "report-row-${row.student.id}", row.activities.map {
                        if (!it.eligible) "Fora da matrícula" else it.score?.let(::number) ?: "Pendente"
                    } + listOf(row.total?.let(::number) ?: "Não lançado", gradeStatus(row), row.frequency?.let { "${it.absent} / ${it.justified}" } ?: "—", percent(row.frequency)), report.activities.size, row.total) { profile(row.student.id) }
                }
            }
            "Frequência", "Mais faltosos" -> {
                item {
                    Info("${report.performedLessons} horários realizados na turma. Aulas elegíveis variam conforme a matrícula.\n${justificationPolicy(c)}")
                    if (mode == "Mais faltosos") Text("Ordem: faltas efetivas decrescentes; desempate pela chamada e nome.", style = MaterialTheme.typography.bodySmall)
                    report.frequency?.let { Text("Soma das matrículas — ${frequencyText(it)}", Modifier.testTag("report-frequency-total"), style = MaterialTheme.typography.bodySmall) }
                }
                val rows = if (mode == "Mais faltosos") ranking else report.students
                if (mode == "Mais faltosos" && rows.isEmpty()) item { Info("Nenhum aluno com faltas efetivas no período.") }
                item { TableRow("Aluno", "report-header", listOf("Aulas elegíveis", "Presenças", "Faltas (F)", "Justificadas (FJ)", "Faltas efetivas", "Frequência", "Chamadas válidas", "Não registradas")) }
                items(rows, key = { it.student.id }) { row ->
                    val f = row.frequency
                    TableRow("${row.student.number}. ${row.student.name}", "report-row-${row.student.id}", listOf(f?.performed, f?.present, f?.absent, f?.justified, f?.let { row.effectiveAbsences(c.countJustified) }).map { it?.toString() ?: "—" } + listOf(percent(f), f?.valid?.toString() ?: "—", f?.let { (it.performed-it.valid).toString() } ?: "—")) { profile(row.student.id) }
                }
            }
            "Resumo anual" -> {
                item { TableRow("Aluno", "report-header", (1..4).map { "${it}º bimestre" } + "Frequência anual") }
                items(annual, key = { it.student.id }) { row ->
                    TableRow("${row.student.number}. ${row.student.name}", "report-row-${row.student.id}", row.terms.map { termRow ->
                        if (termRow.period == null) "Bimestre não configurado"
                        else if (termRow.state == GradeState.NO_ACTIVITIES && termRow.frequency?.performed == 0) "Sem dados"
                        else "${termRow.total?.let(::number) ?: "Não lançado"}\n${gradeStatus(termRow)}\nF: ${termRow.frequency?.absent ?: 0} • FJ: ${termRow.frequency?.justified ?: 0}\n${percent(termRow.frequency)}"
                    } + "${row.frequency.absent} F • ${row.frequency.justified} FJ\n${percent(row.frequency)}") { profile(row.student.id) }
                }
                item { Text("A frequência anual inclui também aulas do ano fora dos intervalos bimestrais, respeitando a matrícula.", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable fun IndividualReportScreen(data: DiarySnapshot, c: SchoolClass, student: Student, term: Int, changeTerm: (Int) -> Unit) {
    val reports = remember(data, c) { SchoolReports(data, c) }
    val row = remember(reports, student, term) { reports.student(student, term) }
    val year = remember(reports, student) { reports.year(student) }
    LazyColumn(Modifier.fillMaxSize().testTag("individual-report"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Section(student.name, "${c.name} • ${c.subject} • ${c.year} • nº ${student.number} • ${student.status}")
            Text("Matrícula: ${shortDate(student.joined)} a ${student.left?.let(::shortDate) ?: "sem saída informada"}")
            TermSelector(term, changeTerm)
            Text(row.period?.let { "${term}º bimestre • ${shortDate(it.start)} a ${shortDate(it.end)}" } ?: "Bimestre não configurado")
            Info("Nota ${row.total?.let(::number) ?: "não lançada"} / ${number(c.scale)} • ${gradeStatus(row)}\n${row.grades.pending} notas pendentes\n${frequencyText(row.frequency)}")
            if (student.notes.isNotBlank()) Text("Observações: ${student.notes}")
        }
        if (row.activities.isEmpty()) item { Text("Nenhuma atividade neste bimestre.") }
        items(row.activities, key = { it.activity.id }) { entry ->
            Text("${entry.activity.name}: ${if (!entry.eligible) "Fora da matrícula" else entry.score?.let(::number) ?: "Pendente"} / ${number(entry.activity.maximum)}\n${entry.activity.type} • ${shortDate(entry.activity.date)}", Modifier.testTag("individual-activity-${entry.activity.id}"))
        }
        item { Section("Histórico anual", "Quatro bimestres, sem média anual presumida") }
        items(year.terms, key = { it.number }) { r ->
            Text("${r.number}º bim. • ${r.total?.let(::number) ?: "Não lançado"} • ${gradeStatus(r)}\n${frequencyText(r.frequency)}", Modifier.testTag("individual-term-${r.number}"))
        }
        item { Info("Resumo anual da matrícula\n${frequencyText(year.frequency)}\n${justificationPolicy(c)}") }
    }
}
