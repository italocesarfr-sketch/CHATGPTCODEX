package br.com.diariodeclasse.ui

import br.com.diariodeclasse.ui.theme.NoctuaSpace
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import br.com.diariodeclasse.R
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.domain.SchoolRules
import br.com.diariodeclasse.reports.*
import br.com.diariodeclasse.export.exportStatus

internal fun gradeStatus(row: StudentPeriodReport): String = when (row.state) {
    GradeState.OUTSIDE_ENROLLMENT -> "Fora da matrícula"
    GradeState.NO_ACTIVITIES -> "Sem atividades"
    GradeState.NO_SCORES -> "Notas não lançadas (${row.grades.pending} pend.)"
    GradeState.PARTIAL -> "Parcial (${row.grades.pending} pend.)"
    GradeState.COMPLETE -> "Notas completas"
}
private fun periodStatus(row: StudentPeriodReport): String = row.exportStatus()
private fun recompositionScore(row: StudentPeriodReport): String = row.recomposition?.let { it.value?.let(::number) ?: "Não realizada" } ?: "Não registrada"
private fun gradeBreakdown(row: StudentPeriodReport): String =
    "Original das atividades: ${row.original?.let(::number) ?: "Pendente"} • Recomposição: ${recompositionScore(row)}${row.recomposition?.let { " (${shortDate(it.date)})" }.orEmpty()}${row.recompositionSchool?.let { " • ${it.name}" }.orEmpty()}\nResultado vigente: ${row.total?.let(::number) ?: "Pendente"}"
// O perfil editável anterior continua usando o mesmo complemento quando há recomposição.
internal fun recompositionText(row: StudentPeriodReport): String = if (row.recomposition == null) "" else "\n${gradeBreakdown(row)}"
private fun enrollmentText(row: StudentPeriodReport): String = when (row.enrollment) {
    EnrollmentCoverage.FULL -> "Matrícula cobre o período"
    EnrollmentCoverage.PARTIAL -> "Matrícula parcial no período"
    EnrollmentCoverage.OUTSIDE -> "Fora do período de matrícula"
    EnrollmentCoverage.UNCONFIGURED -> "Bimestre não configurado"
}
private fun percent(value: Double?) = value?.let { "${number(it)}%" } ?: "Sem registros válidos"
private fun frequencyText(f: SchoolRules.Frequency?): String = if (f == null) "Bimestre não configurado" else
    "Aulas elegíveis: ${f.performed}\nFaltas: ${f.absent} • justificadas: ${f.justified}\nPresenças: ${f.present} • frequência: ${percent(f.percent)}\n${f.valid} chamadas válidas de ${f.performed} aulas realizadas • ${f.performed - f.valid} não registradas"
private fun summaryText(f: FrequencySummary): String =
    "${f.performed} aulas elegíveis • ${f.present} P • ${f.absent} F • ${f.justified} FJ\n${f.effectiveAbsences} faltas efetivas • ${percent(f.percent)}\n${f.valid} chamadas válidas • ${f.unregistered} não registradas"
private fun justificationPolicy(c: SchoolClass) = if (c.countJustified)
    "Faltas efetivas: F + FJ, conforme a configuração da turma. Justificar não abona a falta."
else "Faltas efetivas: somente F. FJ é abonada no percentual, conforme a configuração da turma."
private fun activityText(entry: ActivityResult) = when (entry.state) {
    ActivityScoreState.NOT_APPLICABLE -> "Fora da matrícula"
    ActivityScoreState.PENDING -> "Pendente"
    ActivityScoreState.RECORDED -> number(entry.applicableScore)
}

@Composable fun ReportScreen(data: DiarySnapshot, c: SchoolClass, term: Int, profile: (String) -> Unit) {
    var mode by rememberSaveable { mutableStateOf("Bimestral") }
    var order by rememberSaveable { mutableStateOf("Chamada") }
    var showRelocated by rememberSaveable(c.id) { mutableStateOf(false) }
    var showInformation by rememberSaveable(c.id, term, mode) { mutableStateOf(false) }
    val reports = remember(data, c, showRelocated) { SchoolReports(data, c, showRelocated) }
    val report = remember(reports, term) { reports.term(term) }
    val annual = remember(reports) { reports.year() }
    val selectedOrder = when (order) {
        "Mais faltas" -> StudentOrder.ABSENCES
        "Menor nota" -> StudentOrder.LOWEST_GRADE
        "Maior nota" -> StudentOrder.HIGHEST_GRADE
        else -> StudentOrder.CALL
    }
    val sorted = remember(report, selectedOrder, c.countJustified) { SchoolReports.ordered(report.students, selectedOrder, c.countJustified) }
    val ranking = remember(report, c.countJustified) { SchoolReports.rank(report.students, c.countJustified) }
    val horizontal = rememberScrollState()
    val listState = rememberLazyListState()
    if (showInformation) AlertDialog(
        onDismissRequest = { showInformation = false },
        title = { Text("Informações do relatório") },
        text = {
            Column(Modifier.testTag("report-information").verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(NoctuaSpace.medium)) {
                if (mode == "Bimestral") Text("Atividades: escala 0 a ${number(c.scale)}. Recomposição: 0 a 10. Pendências e pontuação disponível abaixo da escala mantêm o original parcial.")
                Text("Deslize as colunas para os lados. O nome permanece visível; toque para consultar o histórico. O status identifica a matrícula atual.")
                Text(justificationPolicy(c))
                if (mode == "Frequência" || mode == "Mais faltosos") Text("Aulas elegíveis variam conforme a matrícula.")
                if (mode == "Mais faltosos") Text("Ordem: faltas efetivas decrescentes; desempate pela chamada, nome e ID.")
                if (mode == "Resumo anual") {
                    Text("Quatro resultados separados; não há média anual ou decisão de aprovação.")
                    Text("A frequência anual inclui também aulas do ano fora dos intervalos bimestrais, respeitando a matrícula. Não é a média dos percentuais bimestrais.")
                }
            }
        },
        confirmButton = { TextButton(onClick = { showInformation = false }) { Text("Fechar") } }
    )
    // Só mudanças explícitas de contexto reposicionam a leitura, nunca emissões dos dados.
    LaunchedEffect(c.id, term, mode) { listState.scrollToItem(0); horizontal.scrollTo(0) }
    @Composable fun TableRow(name: String, tag: String, cells: List<String>, totalIndex: Int? = null,
        totalValue: Double? = null, status: String? = null, keys: List<String> = emptyList(), click: (() -> Unit)? = null) {
        Row(Modifier.fillMaxWidth().testTag(tag).then(if (click == null) Modifier else Modifier.clickable(onClick = click)).padding(vertical = 12.dp)) {
            Column(Modifier.width(150.dp).padding(end = 8.dp).testTag("$tag-name")) {
                Text(name, style = MaterialTheme.typography.bodySmall)
                status?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Row(Modifier.weight(1f).testTag("$tag-values").horizontalScroll(horizontal)) {
                cells.forEachIndexed { index, cell ->
                    val color = if (index != totalIndex || totalValue == null) MaterialTheme.colorScheme.onSurface else when {
                        totalValue < c.low -> br.com.diariodeclasse.ui.theme.NoctuaColors.danger; totalValue < c.high -> br.com.diariodeclasse.ui.theme.NoctuaColors.warning; else -> br.com.diariodeclasse.ui.theme.NoctuaColors.success
                    }
                    Box(Modifier.width(135.dp).then(keys.getOrNull(index)?.let { Modifier.testTag("$tag-$it") } ?: Modifier)) {
                        Text(cell, Modifier.fillMaxWidth().padding(end = 8.dp).testTag("$tag-cell-$index"), style = MaterialTheme.typography.bodySmall, color = color)
                    }
                }
            }
        }
        HorizontalDivider()
    }
    LazyColumn(Modifier.fillMaxSize().testTag("reports-list"), state = listState, contentPadding = PaddingValues(NoctuaSpace.page), verticalArrangement = Arrangement.spacedBy(NoctuaSpace.small)) {
        item {
            if(data.roster(c.id).any {it.status=="Remanejado"}) RelocatedFilter(showRelocated) {showRelocated=it}
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { Section(when (mode) {
                    "Frequência" -> "Frequência do ${term}º bimestre"
                    "Mais faltosos" -> "Mais faltosos do ${term}º bimestre"
                    "Resumo anual" -> "Resumo anual de ${c.year}"
                    else -> "Boletim do ${term}º bimestre"
                }, "${c.name} • ${c.subject} • ${c.year}") }
                IconButton(onClick = { showInformation = true }, modifier = Modifier.size(48.dp)) {
                    Icon(painterResource(R.drawable.ic_info), contentDescription = "Informações do relatório", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Choice("Relatório", listOf("Bimestral", "Frequência", "Mais faltosos", "Resumo anual"), mode) { mode = it }
            if (mode == "Bimestral") Choice("Ordenação", listOf("Chamada", "Mais faltas", "Menor nota", "Maior nota"), order) { order = it }
            if (mode != "Resumo anual") Text(report.period?.let { "${shortDate(it.start)} a ${shortDate(it.end)}" } ?: "Bimestre não configurado", style = MaterialTheme.typography.bodySmall)
        }
        if (report.students.isEmpty()) item { Info(if(data.roster(c.id).isEmpty()) "Nenhum aluno cadastrado nesta turma." else "Nenhum aluno nesta visualização. Use Mostrar remanejados para consultar o histórico.") }
        when (mode) {
            "Bimestral" -> {
                if (report.activities.isEmpty()) item { Info("Nenhuma atividade neste bimestre. Não há notas originais a totalizar; recomposições já registradas continuam identificadas.") }
                val fields = listOf("current", "original", "recomposition", "pending", "performed", "present", "absent", "justified", "effective-absences", "percent")
                item { TableRow("Aluno", "report-header", report.activities.map { "${it.name}${if(it.classId!=c.id) " • " + (data.classes.firstOrNull {school->school.id==it.classId}?.name?:it.classId) else ""}\nMáx. ${number(it.maximum)}" } + listOf("Resultado vigente", "Original atividades", "Recomposição", "Situação / pendências", "Aulas elegíveis", "Presenças (P)", "Faltas (F)", "Justificadas (FJ)", "Faltas efetivas", "Frequência")) }
                items(sorted, key = { it.student.id }) { row ->
                    val f = row.attendance
                    TableRow("${row.student.number}. ${row.student.name}", "report-row-${row.student.id}", row.activities.map(::activityText) + listOf(
                        row.total?.let(::number) ?: "Não lançado", row.original?.let(::number) ?: "Não lançado", recompositionScore(row) + if(row.recomposition?.studentId?.let {it!=row.student.id}==true) "\n${row.recompositionSchool?.name ?: "Matrícula de origem"}" else "",
                        "${periodStatus(row)}\n${enrollmentText(row)}", f?.performed?.toString() ?: "—", f?.present?.toString() ?: "—",
                        f?.absent?.toString() ?: "—", f?.justified?.toString() ?: "—", f?.effectiveAbsences?.toString() ?: "—", percent(f?.percent)),
                        report.activities.size, row.total, relocationLabel(data,row.student), report.activities.map { "activity-${it.id}" } + fields) { profile(row.student.id) }
                }
            }
            "Frequência", "Mais faltosos" -> {
                item {
                    Info("${report.performedLessons} horários realizados na turma.")
                    report.frequency?.let { Text("Soma das matrículas — ${frequencyText(it)}", Modifier.testTag("report-frequency-total"), style = MaterialTheme.typography.bodySmall) }
                    if (report.frequency?.performed == 0) Text("Nenhuma aula realizada elegível no período.")
                    else if (report.frequency?.valid == 0) Text("Há aulas realizadas, mas nenhuma chamada válida foi preenchida.")
                }
                val rows = if (mode == "Mais faltosos") ranking else report.students
                if (mode == "Mais faltosos" && rows.isEmpty()) item { Info("Nenhum aluno com faltas efetivas no período.") }
                item { TableRow("Aluno", "report-header", listOf("Aulas elegíveis", "Presenças", "Faltas (F)", "Justificadas (FJ)", "Faltas efetivas", "Frequência", "Chamadas válidas", "Não registradas")) }
                items(rows, key = { it.student.id }) { row ->
                    val f = row.attendance
                    TableRow("${row.student.number}. ${row.student.name}", "report-row-${row.student.id}", listOf(f?.performed, f?.present, f?.absent, f?.justified, f?.effectiveAbsences).map { it?.toString() ?: "—" } + listOf(percent(f?.percent), f?.valid?.toString() ?: "—", f?.unregistered?.toString() ?: "—"), status = relocationLabel(data,row.student),
                        keys = listOf("performed", "present", "absent", "justified", "effective-absences", "percent", "valid", "unregistered")) { profile(row.student.id) }
                }
            }
            "Resumo anual" -> {
                item { Info("${annual.performedLessons} horários realizados no ano. Soma das oportunidades das matrículas:\n${summaryText(annual.frequency)}") }
                item { TableRow("Aluno", "report-header", (1..4).map { "${it}º bimestre" } + "Frequência anual") }
                items(annual.students, key = { it.student.id }) { row ->
                    TableRow("${row.student.number}. ${row.student.name}", "report-row-${row.student.id}", row.terms.map { termRow ->
                        when {
                            termRow.period == null && !termRow.hasData -> "Bimestre não configurado"
                            !termRow.hasData && termRow.enrollment == EnrollmentCoverage.OUTSIDE -> "Fora da matrícula\n${periodStatus(termRow)}"
                            !termRow.hasData -> "Sem dados"
                            else -> "Resultado vigente: ${termRow.total?.let(::number) ?: "Não lançado"}\n${periodStatus(termRow)}\n${if (termRow.enrollment == EnrollmentCoverage.FULL) "" else enrollmentText(termRow) + "\n"}${termRow.attendance?.let { "Faltas efetivas: ${it.effectiveAbsences}\nF: ${it.absent} • FJ: ${it.justified}\n${percent(it.percent)}" } ?: "Bimestre não configurado"}"
                        }
                    } + summaryText(row.attendance), status = relocationLabel(data,row.student), keys = (1..4).map { "term-$it" } + "annual-frequency") { profile(row.student.id) }
                }
            }
        }
    }
}

@Composable fun IndividualReportScreen(data: DiarySnapshot, c: SchoolClass, student: Student, term: Int, changeTerm: (Int) -> Unit) {
    val reports = remember(data, c) { SchoolReports(data, c) }
    val row = remember(reports, student, term) { reports.student(student, term) }
    val year = remember(reports, student) { reports.year(student) }
    val listState = rememberLazyListState()
    LaunchedEffect(student.id, term) { listState.scrollToItem(0) }
    LazyColumn(Modifier.fillMaxSize().testTag("individual-report"), state = listState, contentPadding = PaddingValues(NoctuaSpace.page), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Section(student.name, "${c.name} • ${c.subject} • ${c.year} • nº ${student.number} • ${student.status}")
            RelocationNotice(data,student)
            Text("Matrícula: ${shortDate(student.joined)} a ${student.left?.let(::shortDate) ?: "sem saída informada"}")
            TermSelector(term, changeTerm)
            Text(row.period?.let { "${term}º bimestre • ${shortDate(it.start)} a ${shortDate(it.end)}" } ?: "Bimestre não configurado")
            Text(enrollmentText(row))
            Info("Nota ${row.total?.let(::number) ?: "não lançada"} / ${number(row.resultScale ?: c.scale)} • ${periodStatus(row)}\n${row.grades.pending} notas pendentes\n${gradeBreakdown(row)}\n${frequencyText(row.frequency)}\nFaltas efetivas: ${row.attendance?.effectiveAbsences?.toString() ?: "—"}")
            if (student.notes.isNotBlank()) Text("Observações: ${student.notes}")
        }
        if (row.activities.isEmpty()) item { Text("Nenhuma atividade neste bimestre.") }
        items(row.activities, key = { it.activity.id }) { entry ->
            Text("${entry.activity.name}: ${activityText(entry)} / ${number(entry.activity.maximum)}\n${entry.activity.type} • ${shortDate(entry.activity.date)} • ${data.classes.firstOrNull {it.id==entry.activity.classId}?.name ?: entry.activity.classId}", Modifier.testTag("individual-activity-${entry.activity.id}"))
        }
        item { Section("Histórico anual", "Quatro bimestres, sem média anual presumida") }
        items(year.terms, key = { it.number }) { entry ->
            Text("${entry.number}º bim. • ${entry.total?.let(::number) ?: "Não lançado"} • ${periodStatus(entry)}\n${enrollmentText(entry)}\n${gradeBreakdown(entry)}\n${frequencyText(entry.frequency)}\nFaltas efetivas: ${entry.attendance?.effectiveAbsences?.toString() ?: "—"}", Modifier.testTag("individual-term-${entry.number}"))
        }
        relocationHistory(data,student)
        item { Info("Resumo anual da matrícula\n${frequencyText(year.frequency)}\nFaltas efetivas: ${year.attendance.effectiveAbsences}\n${justificationPolicy(c)}") }
    }
}
