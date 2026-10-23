package br.com.diariodeclasse.ui

import br.com.diariodeclasse.ui.theme.NoctuaSpace
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import br.com.diariodeclasse.data.*
import kotlinx.coroutines.*

@Composable fun DayAttendanceScreen(vm: DiaryViewModel, data: DiarySnapshot, c: SchoolClass, day: String, calendar: () -> Unit, termView: () -> Unit) {
    val lessons = data.lessons.filter { it.classId == c.id && it.date == day }.sortedBy { it.ordinal }
    val period = data.terms.firstOrNull { it.classId == c.id && day in it.start..it.end }
    val active = lessons.filter { it.state != "CANCELADA" }
    val performed = lessons.count { it.state == "REALIZADA" }
    val performedIds = lessons.filter { it.state == "REALIZADA" }.map { it.id }.toSet()
    val enrolledIds = data.currentRoster(c.id).filter { it.enrolled(day) }.map { it.id }.toSet()
    val filled = data.marks.count { it.lessonId in performedIds && it.studentId in enrolledIds && it.mark != "NR" }
    val expected = performed * enrolledIds.size
    val planned = Timetables.planned(data,c.id,day).size
    var count by rememberSaveable(c.id, day) { mutableIntStateOf(planned.takeIf { it > 0 } ?: 1) }
    var more by remember { mutableStateOf(false) }
    var hoursForm by rememberSaveable { mutableStateOf(false) }
    var hoursText by rememberSaveable { mutableStateOf("") }
    var pendingCount by remember { mutableStateOf<Int?>(null) }
    var cancellationApproved by remember { mutableStateOf(false) }
    var reactivationCount by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    var checkingDay by remember { mutableStateOf(false) }
    var clearError by remember { mutableStateOf<String?>(null) }
    var emptyDay by remember { mutableStateOf<DayHoursDiagnosis?>(null) }
    var allPresent by remember { mutableStateOf(false) }
    var option by remember { mutableStateOf<Lesson?>(null) }
    var action by remember { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    val saving by vm.saving.collectAsState()
    val context = "${c.name} • ${shortDate(day)}"
    fun saveHours(n: Int, confirmed: Boolean = false, reactivate: Boolean = false) {
        vm.act(done = { hoursForm = false; pendingCount = null }, context = context) { vm.repository.setDayHours(c.id, day, n, confirmed, reactivate) }
    }
    fun applyHours(n: Int, cancellation: Boolean = false) {
        val cancelled = lessons.count { it.ordinal <= n && it.state == "CANCELADA" }
        if (cancelled > 0) {
            pendingCount = n; cancellationApproved = cancellation; reactivationCount = cancelled
        } else saveHours(n, cancellation)
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val scrollControls=LocalDensity.current.fontScale>1.2f && lessons.isNotEmpty()
    val gridHeight=maxHeight.coerceAtLeast(240.dp)
    Column((if(scrollControls) Modifier.fillMaxWidth().verticalScroll(rememberScrollState()) else Modifier.fillMaxSize()).testTag("attendance-day-$day")) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val dateOnOwnLine=maxWidth<360.dp || LocalDensity.current.fontScale>1.2f
            Column {
                Row(Modifier.fillMaxWidth().padding(horizontal = NoctuaSpace.small), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = calendar) { Text("‹ Calendário") }
                    if(dateOnOwnLine) Spacer(Modifier.weight(1f))
                    else Text(shortDate(day), Modifier.weight(1f).align(Alignment.CenterVertically).testTag("attendance-day-date"),
                        style=MaterialTheme.typography.titleSmall,textAlign=androidx.compose.ui.text.style.TextAlign.Center,maxLines=1,softWrap=false)
                    TextButton(onClick = termView) { Text("Bimestre") }
                    ContextHelp("Ajuda da chamada", "Toque novamente em P, F ou FJ para limpar. NR não conta como presença nem falta. Use o menu da célula para justificar.")
                }
                if(dateOnOwnLine) Text(shortDate(day),Modifier.fillMaxWidth().padding(horizontal=NoctuaSpace.medium).testTag("attendance-day-date"),
                    style=MaterialTheme.typography.titleSmall,textAlign=androidx.compose.ui.text.style.TextAlign.Center,maxLines=1,softWrap=false)
            }
        }
        Text(period?.let { "${it.number}º bimestre • ${counted(performed, "horário", "horários")} • $filled/$expected registros" } ?: "Fora dos bimestres • somente anual • $filled/$expected registros", Modifier.padding(horizontal = NoctuaSpace.medium), style = MaterialTheme.typography.labelSmall)
        if(planned>0) Text("${counted(planned,"aula prevista","aulas previstas")} para esta turma.",Modifier.padding(horizontal=NoctuaSpace.medium).testTag("planned-hours"),style=MaterialTheme.typography.bodyMedium)
        AttendanceSaveStatus(vm)
        if (lessons.isEmpty()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(NoctuaSpace.medium), verticalArrangement = Arrangement.spacedBy(NoctuaSpace.small)) {
                Text("Quantos horários foram ministrados?", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(NoctuaSpace.small)) {
                    (1..3).forEach { n -> FilterChip(count == n, { count = n }, label = { Text("$n") }, modifier = Modifier.testTag("hours-choice-$n")) }
                    Box {
                        FilterChip(count > 3, { more = true }, label = { Text(if(count > 3) "$count" else "Mais") })
                        DropdownMenu(more, { more = false }) { (4..12).forEach { n -> DropdownMenuItem(text = { Text("$n horários") }, onClick = { count = n; more = false }) } }
                    }
                }
                Button(enabled = !saving, onClick = { saveHours(count) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Registrar horários") }
                SaveError(vm)
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = NoctuaSpace.small), horizontalArrangement = Arrangement.spacedBy(NoctuaSpace.tiny)) {
                OutlinedButton(enabled = !saving, onClick = { hoursText = (active.maxOfOrNull { it.ordinal } ?: 0).toString(); hoursForm = true }, modifier = Modifier.weight(1f)) { Text("Horários: ${active.size} • Alterar", style = MaterialTheme.typography.labelSmall) }
                FilledTonalButton(enabled = !saving && performed > 0, onClick = { allPresent = true }, modifier = Modifier.weight(1f)) { Text("Marcar todos como presentes", style = MaterialTheme.typography.labelSmall) }
            }
            if (lessons.any { it.state == "PREVISTA" }) TextButton(onClick = { applyHours(active.maxOfOrNull { it.ordinal }?.coerceAtMost(12) ?: 0) }, enabled = !saving) { Text("Confirmar horários ministrados") }
            Box(if(scrollControls) Modifier.height(gridHeight) else Modifier.weight(1f)) { FrequencyGrid(data, c, lessons, period, true,
                mark = { lesson, student, value ->
                    val lessonId = lesson.id; val studentId = student.id
                    vm.act(context = context, key = "mark:$lessonId:$studentId") { vm.repository.toggleMark(lessonId, studentId, value) }
                }, options = { option = it; action = "menu" }, openDay = {}) }
        }
    }
    }
    if (hoursForm && pendingCount == null && emptyDay == null) Form("Quantidade de horários", { hoursForm = false }) {
        OutlinedTextField(hoursText, { hoursText = it }, label = { Text("Horários ministrados (0 a 12)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("Reduzir cancela horários e preserva as chamadas.", style = MaterialTheme.typography.bodyMedium)
        Button(enabled = !saving, onClick = {
            val n = hoursText.toIntOrNull()
            if (n == null || n !in 0..12) vm.message("Informe de 0 a 12 horários.")
            else if (lessons.any { it.ordinal > n && it.state != "CANCELADA" }) { pendingCount = n; cancellationApproved = false; reactivationCount = 0 }
            else applyHours(n)
        }) { Text("Aplicar horários") }
        TextButton(enabled = !saving && !checkingDay, onClick = {
            checkingDay = true; clearError = null
            scope.launch {
                try { emptyDay = withContext(Dispatchers.IO) { vm.repository.diagnoseDayHours(c.id, day) } }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { clearError = if(e is IllegalArgumentException) e.message else "Não foi possível conferir os horários. Tente novamente." }
                finally { checkingDay = false }
            }
        }, modifier = Modifier.testTag("clear-day-hours")) { Text("Limpar horários do dia") }
        clearError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("clear-day-error")) }
        SaveError(vm)
    }
    pendingCount?.let { n ->
        if (reactivationCount > 0) AlertDialog(onDismissRequest = { pendingCount = null },
            title = { Text("Reativar $reactivationCount ${if (reactivationCount == 1) "horário cancelado" else "horários cancelados"}?") },
            text = { Column { Text("As chamadas anteriores serão preservadas."); SaveError(vm) } },
            dismissButton = { TextButton(enabled = !saving, onClick = { pendingCount = null }) { Text("Não") } },
            confirmButton = { Button(enabled = !saving, onClick = { saveHours(n, cancellationApproved, true) }) { Text("Reativar") } })
        else Confirm("Cancelar horários excedentes?", "Os horários de número maior que $n serão cancelados. As chamadas serão preservadas.", { pendingCount = null }) { applyHours(n, true) }
    }
    emptyDay?.let { checked -> AlertDialog(onDismissRequest = { if (!saving) emptyDay = null },
        title = { Text(if (checked.canRemove) "Remover os horários desta data?" else "Horários preservados") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("${c.name} • ${shortDate(day)}")
            if (checked.lessons.isEmpty()) Text("Esta data já está sem horários.")
            checked.details.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (checked.canRemove) Text(if (checked.nrCount > 0) "Existem somente registros sem lançamento. A data voltará a ficar sem aulas cadastradas." else "A data voltará a ficar sem aulas cadastradas.")
            else if (checked.lessons.isNotEmpty()) Text("Os vínculos acima impedem a remoção. Cancelar horários continua disponível.")
            SaveError(vm)
        } },
        dismissButton = { TextButton(enabled = !saving, onClick = { emptyDay = null }) { Text(if (checked.canRemove) "Cancelar" else "Fechar") } },
        confirmButton = { if (checked.canRemove) Button(enabled = !saving, onClick = {
            vm.act(done = { emptyDay = null; hoursForm = false }) { vm.repository.clearDiagnosedDay(checked, confirmedNR = checked.nrCount > 0) }
        }, modifier = Modifier.testTag("confirm-clear-day")) { Text(if (checked.nrCount > 0) "Remover horários e registros NR" else "Remover") } }) }
    if (allPresent) Confirm("Marcar todos como presentes?", "Substitui por P as marcações dos horários realizados nesta data. Alunos fora da matrícula e horários cancelados/previstos não serão alterados.", { allPresent = false }) {
        vm.act(done = { allPresent = false }, context = context) { vm.repository.allPresentDay(c.id, day) }
    }
    option?.let { lesson ->
        if (action == "menu") Form("${lesson.ordinal}º horário • ${shortDate(day)}", { option = null }) {
            Text("${when (lesson.state) { "REALIZADA" -> "Aula realizada"; "CANCELADA" -> "Aula cancelada — excluída dos totais"; else -> "Aula prevista legada — não contabilizada" }}")
            if (lesson.note.isNotBlank()) Text(lesson.note)
            when (lesson.state) {
                "REALIZADA" -> TextButton(onClick = { action = "cancel"; note = lesson.note }) { Text("Cancelar horário") }
                "CANCELADA" -> TextButton(onClick = { action = "reactivate" }) { Text("Reativar como realizada") }
                else -> TextButton(onClick = { action = "start" }) { Text("Confirmar aula realizada") }
            }
            TextButton(onClick = { action = "note"; note = lesson.note }) { Text("Observação do horário") }
            if (lesson.ordinal > 1 && lesson.state == "REALIZADA") TextButton(onClick = { action = "copy" }) { Text("Copiar primeira chamada realizada do dia") }
        }
        if (action == "cancel" || action == "note") Form(if (action == "cancel") "Cancelar horário" else "Observação do horário", { option = null }) {
            Field("Motivo / observação", note, { note = it }, lines = 2)
            if (action == "cancel") Info("O horário ficará cancelado, mantendo todas as marcações. Não contará na frequência.")
            Button(enabled = !saving, onClick = {
                val value = note; val cancel = action == "cancel"
                vm.act(done = { option = null }, context = context) { vm.repository.lessonState(lesson.id, if (cancel) "CANCELADA" else lesson.state, value) }
            }) { Text(if (action == "cancel") "Confirmar cancelamento" else "Salvar observação") }
            SaveError(vm)
        }
        if (action in listOf("reactivate", "start", "copy")) Confirm("Confirmar alteração do horário", when(action) {
            "copy" -> "Substitui a chamada deste horário pela primeira aula realizada anterior. NR da origem limpa a marcação anterior no destino."
            "reactivate" -> "O horário voltará a contar como realizado. Suas marcações antigas serão preservadas e voltarão aos totais."
            else -> "Confirma que a aula foi ministrada. A preferência de presença inicial da turma será respeitada."
        }, { option = null }) {
            val selectedAction = action
            vm.act(done = { option = null }, context = context) {
                when (selectedAction) { "copy" -> vm.repository.copyFirst(lesson.id); "start" -> vm.repository.startLesson(lesson.id); else -> vm.repository.lessonState(lesson.id, "REALIZADA") }
            }
        }
    }
}
