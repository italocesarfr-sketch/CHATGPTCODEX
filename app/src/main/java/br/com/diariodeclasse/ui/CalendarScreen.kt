package br.com.diariodeclasse.ui

import br.com.diariodeclasse.ui.theme.NoctuaSpace
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

@Composable fun CalendarScreen(vm: DiaryViewModel, data: DiarySnapshot, c: SchoolClass, term: Int, changeTerm: (Int) -> Unit, configureTerms: () -> Unit, month: Int, changeMonth: (Int) -> Unit) {
    val initial = YearMonth.of(c.year, LocalDate.now().monthValue).atDay(minOf(LocalDate.now().dayOfMonth, YearMonth.of(c.year, LocalDate.now().monthValue).lengthOfMonth())).toString()
    var selected by rememberSaveable(c.id) { mutableStateOf(initial) }
    var opened by rememberSaveable(c.id) { mutableStateOf(false) }
    var mode by rememberSaveable(c.id) { mutableStateOf("Calendário") }
    fun open(day: String) {
        selected = day; changeMonth(LocalDate.parse(day).monthValue); opened = true; mode = "Calendário"
        data.terms.firstOrNull { it.classId == c.id && day in it.start..it.end }?.let { changeTerm(it.number) }
    }
    BackHandler(opened && mode == "Calendário") { vm.act(done = { opened = false }, clearError = false) {} }
    Column(Modifier.fillMaxSize()) {
        if (opened && mode == "Calendário") {
            DayAttendanceScreen(vm, data, c, selected, calendar = { vm.act(done = { opened = false }, clearError = false) {} }, termView = { vm.act(done = { mode = "Bimestre" }, clearError = false) {} })
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = NoctuaSpace.medium), verticalAlignment = Alignment.CenterVertically) {
                Text("Frequência", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = configureTerms) { Text("Bimestres") }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = NoctuaSpace.medium), horizontalArrangement = Arrangement.spacedBy(NoctuaSpace.small)) {
                listOf("Calendário", "Bimestre").forEach { label -> FilterChip(mode == label, onClick = { mode = label }, label = { Text(label) }, modifier = Modifier.testTag("frequency-mode-$label")) }
            }
            if (mode == "Bimestre") {
                TermSelector(term, changeTerm)
                val period = data.terms.firstOrNull { it.classId == c.id && it.number == term }
                Text(period?.let { "${shortDate(it.start)} a ${shortDate(it.end)} • total F / FJ" } ?: "Configure os bimestres.", Modifier.padding(horizontal = NoctuaSpace.medium), style = MaterialTheme.typography.labelSmall)
                val lessons = remember(data.lessons, c.id, period) { data.lessons.filter { it.classId == c.id && period != null && it.date in period.start..period.end }.sortedWith(compareBy({ it.date }, { it.ordinal })) }
                if (lessons.isEmpty()) Text("Nenhuma aula registrada neste bimestre.", Modifier.padding(NoctuaSpace.medium))
                Box(Modifier.weight(1f)) { FrequencyGrid(data, c, lessons, period, false, { _, _, _ -> }, {}, ::open) }
            } else {
                val lessonsByDate = remember(data.lessons, c.id) { data.lessons.filter { it.classId == c.id }.groupBy { it.date } }
                val validMarks = remember(data.marks) { data.marks.filter { it.mark != "NR" }.groupBy { it.lessonId } }
                val students = remember(data.students) { data.students.associateBy { it.id } }
                SchoolCalendar(c.year,month,selected,changeMonth,::open,marker={day ->
                    val daily=lessonsByDate[day].orEmpty()
                    val performed=daily.filter{it.state=="REALIZADA"}
                    val valid=performed.sumOf{l->validMarks[l.id].orEmpty().count{students[it.studentId]?.enrolled(day)==true}}
                    val expected=data.currentRoster(c.id).count{it.enrolled(day)}*performed.size
                    val marker=when{valid>0->if(valid==expected)"✓"else"•";daily.isNotEmpty()&&daily.all{it.state=="CANCELADA"}->"×";performed.isNotEmpty()->"NR";daily.isNotEmpty()->"◇";else->""}
                    CalendarMarker(marker,when(marker){"✓"->"chamada preenchida";"•"->"chamada parcial";"×"->"horários cancelados";"NR"->"sem frequência registrada";"◇"->"aulas previstas legadas";else->"sem aulas"})
                },instruction="",
                    legend="✓ completa • • parcial • NR sem registros\n× cancelada • ◇ prevista",tag="attendance-calendar")
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
    Column(Modifier.fillMaxWidth().padding(horizontal = NoctuaSpace.medium).semantics { liveRegion = LiveRegionMode.Polite }) {
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
