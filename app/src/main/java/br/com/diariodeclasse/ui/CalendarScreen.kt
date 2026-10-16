package br.com.diariodeclasse.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import br.com.diariodeclasse.data.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle

@Composable fun CalendarScreen(vm: DiaryViewModel, data: DiarySnapshot, c: SchoolClass, term: Int, changeTerm: (Int) -> Unit, configureTerms: () -> Unit) {
    val initial = YearMonth.of(c.year, LocalDate.now().monthValue).atDay(minOf(LocalDate.now().dayOfMonth, YearMonth.of(c.year, LocalDate.now().monthValue).lengthOfMonth())).toString()
    var selected by rememberSaveable(c.id) { mutableStateOf(initial) }
    var month by rememberSaveable(c.id) { mutableIntStateOf(LocalDate.parse(initial).monthValue) }
    var opened by rememberSaveable(c.id) { mutableStateOf(false) }
    var mode by rememberSaveable(c.id) { mutableStateOf("Calendário") }
    fun open(day: String) {
        selected = day; month = LocalDate.parse(day).monthValue; opened = true; mode = "Calendário"
        data.terms.firstOrNull { it.classId == c.id && day in it.start..it.end }?.let { changeTerm(it.number) }
    }
    BackHandler(opened && mode == "Calendário") { vm.act(done = { opened = false }, clearError = false) {} }
    Column(Modifier.fillMaxSize()) {
        if (opened && mode == "Calendário") {
            DayAttendanceScreen(vm, data, c, selected, calendar = { vm.act(done = { opened = false }, clearError = false) {} }, termView = { vm.act(done = { mode = "Bimestre" }, clearError = false) {} })
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Fazer Chamada", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = configureTerms) { Text("Bimestres") }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Calendário", "Bimestre").forEach { label -> FilterChip(mode == label, onClick = { mode = label }, label = { Text(label) }, modifier = Modifier.testTag("frequency-mode-$label")) }
            }
            if (mode == "Bimestre") {
                TermSelector(term, changeTerm)
                val period = data.terms.firstOrNull { it.classId == c.id && it.number == term }
                Text(period?.let { "${shortDate(it.start)} a ${shortDate(it.end)} • total F / FJ" } ?: "Configure os bimestres.", Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.labelSmall)
                Text("Toque em uma célula para corrigir a data. Deslize horários →", Modifier.padding(12.dp), style = MaterialTheme.typography.labelSmall)
                val lessons = remember(data.lessons, c.id, period) { data.lessons.filter { it.classId == c.id && period != null && it.date in period.start..period.end }.sortedWith(compareBy({ it.date }, { it.ordinal })) }
                if (lessons.isEmpty()) Text("Nenhuma aula registrada neste bimestre.", Modifier.padding(12.dp))
                Box(Modifier.weight(1f)) { FrequencyGrid(data, c, lessons, period, false, { _, _, _ -> }, {}, ::open) }
            } else {
                val ym = YearMonth.of(c.year, month)
                val lessonsByDate = remember(data.lessons, c.id) { data.lessons.filter { it.classId == c.id }.groupBy { it.date } }
                val validMarks = remember(data.marks) { data.marks.filter { it.mark != "NR" }.groupBy { it.lessonId } }
                val students = remember(data.students) { data.students.associateBy { it.id } }
                LazyColumn(Modifier.fillMaxSize().testTag("attendance-calendar"), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Choice("Mês do ano letivo ${c.year}", (1..12).map { "$it • ${YearMonth.of(c.year, it).month.getDisplayName(TextStyle.FULL, brLocale)}" }, "$month • ${ym.month.getDisplayName(TextStyle.FULL, brLocale)}") { month = it.substringBefore(" •").toInt() }
                        Text("Toque em qualquer data para informar os horários ministrados.", style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.fillMaxWidth()) { listOf("S", "T", "Q", "Q", "S", "S", "D").forEach { Text(it, Modifier.weight(1f).padding(vertical = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center) } }
                    }
                    val offset = ym.atDay(1).dayOfWeek.value - 1
                    items((offset + ym.lengthOfMonth() + 6) / 7) { week ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            for (weekday in 0..6) {
                                val n = week * 7 + weekday - offset + 1
                                if (n !in 1..ym.lengthOfMonth()) { Spacer(Modifier.weight(1f).height(48.dp)); continue }
                                val day = ym.atDay(n).toString()
                                val daily = lessonsByDate[day].orEmpty()
                                val performed = daily.filter { it.state == "REALIZADA" }
                                val valid = performed.sumOf { l -> validMarks[l.id].orEmpty().count { students[it.studentId]?.enrolled(day) == true } }
                                val expected = data.roster(c.id).count { it.enrolled(day) } * performed.size
                                val marker = when { valid > 0 -> if (valid == expected) "✓" else "•"; daily.isNotEmpty() && daily.all { it.state == "CANCELADA" } -> "×"; performed.isNotEmpty() -> "NR"; daily.isNotEmpty() -> "◇"; else -> "" }
                                Surface(onClick = { open(day) }, modifier = Modifier.weight(1f).height(48.dp).testTag("date-$day").semantics { contentDescription = "${shortDate(day)}, ${when(marker) { "✓" -> "chamada preenchida"; "•" -> "chamada parcial"; "×" -> "horários cancelados"; "NR" -> "sem frequência registrada"; "◇" -> "aulas previstas legadas"; else -> "sem aulas" }}" },
                                    shape = MaterialTheme.shapes.small, color = if (day == selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                        Text("$n", style = MaterialTheme.typography.bodyMedium)
                                        if (marker.isNotEmpty()) Text(marker, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                    item { Text("✓ preenchida • • parcial • NR sem registros\n× cancelada • ◇ prevista legada\nEscolher mês ou data não cria nem altera aulas.", style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
    }
}

@Composable fun AttendanceSaveStatus(vm: DiaryViewModel) {
    val saving by vm.saving.collectAsState()
    val error by vm.error.collectAsState()
    val context by vm.errorContext.collectAsState()
    val attendanceError by vm.attendanceError.collectAsState()
    var discard by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
        if (attendanceError != null || error != null) {
            Text(attendanceError ?: "$context: $error", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            Row {
                TextButton(onClick = vm::retry, enabled = !saving) { Text("Tentar salvar novamente") }
                if (attendanceError != null) TextButton(onClick = { discard = true }, enabled = !saving) { Text("Descartar pendências") }
            }
        } else Text(if (saving) "Salvando…" else "Salvo no aparelho", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("attendance-save-status"))
    }
    if (discard) Confirm("Descartar alterações não salvas?", "Somente as intenções que falharam serão descartadas. Os registros que já estão no banco permanecerão exatamente como estão.", { discard = false }) { vm.discardAttendanceFailures(); discard = false }
}
