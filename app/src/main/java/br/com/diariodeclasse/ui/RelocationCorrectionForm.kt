package br.com.diariodeclasse.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import br.com.diariodeclasse.data.*
import kotlinx.coroutines.*

/** A revisão é somente leitura. Somente a confirmação passa pela fila transacional existente. */
@Composable fun RelocationCorrectionForm(vm: DiaryViewModel, student: Student, dismiss: () -> Unit) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val data by vm.data.collectAsState()
    val saving by vm.saving.collectAsState()
    val link = data.relocations.firstOrNull { it.sourceStudentId == student.id }
    val original = remember(student.id) { link }
    var day by rememberSaveable { mutableStateOf(original?.effectiveDate?.let(::shortDate).orEmpty()) }
    var target by rememberSaveable { mutableStateOf(original?.externalTarget.orEmpty()) }
    val originalClassId = data.students.firstOrNull { it.id == original?.targetStudentId }?.classId.orEmpty()
    var internal by rememberSaveable { mutableStateOf(original?.targetStudentId != null) }
    var targetClass by rememberSaveable { mutableStateOf(originalClassId) }
    var selectedIds by remember { mutableStateOf(arrayListOf<String>()) }
    var observed by remember { mutableStateOf<DiarySnapshot?>(null) }
    val selected = selectedIds.toSet()
    var reviewing by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<RelocationCorrectionPreview?>(null) }
    var checking by remember { mutableStateOf(false) }
    var committing by remember { mutableStateOf(false) }
    var issue by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    val protectedInternal = original?.targetStudentId != null
    val dirty = day != original?.effectiveDate?.let(::shortDate) || target != original?.externalTarget.orEmpty() ||
        internal != protectedInternal || (internal && targetClass != originalClassId) || selected.isNotEmpty()

    fun leave() {
        if (saving || committing) return
        if (reviewing) { reviewing = false; confirm = false; checking = false; issue = null }
        else if (dirty) discard = true else dismiss()
    }
    // Cancelar esta leitura não cancela gravações. LaunchedEffect descarta resultados de seleções antigas.
    LaunchedEffect(reviewing, selected, data, reload, committing) {
        if (!reviewing || committing) return@LaunchedEffect
        checking = true; issue = null
        try {
            delay(120)
            val parsed = iso(day)
            val prior = observed
            val changed = prior != null && prior != data
            val removals = if (changed) emptySet() else selected.filterTo(mutableSetOf()) { id ->
                data.lessons.any { it.id == id && it.date >= parsed } && data.marks.any { it.lessonId == id && it.studentId == student.id }
            }
            var checked = withContext(Dispatchers.IO) {
                vm.repository.previewCorrectionDestination(student.id, parsed, target, targetClass.takeIf { internal }, removals)
            }
            if (prior != null && prior != checked.before) {
                checked = RelocationCorrections.preview(checked.before, student.id, parsed, target)
                confirm = false
                notice = "Os registros mudaram. Confira a prévia atualizada e selecione novamente as marcações. Nada foi alterado."
            }
            if (selected != checked.remove) selectedIds = ArrayList(checked.remove)
            observed = checked.before
            preview = checked
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            confirm = false
            issue = when (error) {
                is java.time.DateTimeException -> "Data inválida. Use dd/mm/aaaa."
                is IllegalArgumentException -> error.message ?: "Revise os dados da correção."
                else -> "Não foi possível conferir. Nenhum registro foi alterado."
            }
        } finally { checking = false }
    }

    Form("Corrigir remanejamento", ::leave) {
        Text(student.name, style = MaterialTheme.typography.titleMedium)
        if (link == null) Text("Remanejamento não encontrado.")
        else if (!reviewing) {
            Text("Registrado: ${shortDate(link.effectiveDate)} • ${link.externalTarget ?: data.classes.firstOrNull { it.id == originalClassId }?.name.orEmpty()}")
            Field("Data efetiva (dd/mm/aaaa)", day, { day = it }, Modifier.testTag("correction-date"), enabled = !saving && !protectedInternal)
            Text("Destino do remanejamento", style = MaterialTheme.typography.titleSmall)
            listOf(true to "Uma das minhas turmas", false to "Outra turma / destino externo").forEach { (kind, label) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(internal == kind, { internal = kind; issue = null }, enabled = !saving && !protectedInternal,
                        modifier = Modifier.testTag(if (kind) "correction-internal" else "correction-external").semantics { contentDescription = label })
                    Text(label, Modifier.weight(1f))
                }
            }
            if (internal) {
                val eligible = CorrectionDestinations.eligible(data, student)
                val choices = if (protectedInternal) data.classes.filter { it.id == originalClassId } else eligible
                var expanded by remember { mutableStateOf(false) }
                fun label(c: SchoolClass) = "${c.name} • ${c.subject} • ${c.year} • ${c.shift}"
                Box {
                    OutlinedButton(onClick = { expanded = true }, enabled = !saving && !protectedInternal && choices.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().testTag("correction-class")) {
                        Text(choices.firstOrNull { it.id == targetClass }?.let(::label) ?: "Selecionar turma")
                    }
                    DropdownMenu(expanded, { expanded = false }) {
                        choices.forEach { c -> DropdownMenuItem(text = { Text(label(c)) }, onClick = { targetClass = c.id; expanded = false },
                            modifier = Modifier.testTag("correction-class-${c.id}")) }
                    }
                }
                Text(if (protectedInternal) "Remanejamento interno: a correção precisa ajustar as duas matrículas em conjunto. Nenhum vínculo foi alterado."
                    else if (eligible.isEmpty()) "Nenhuma turma com ano, disciplina, calendário, escala e política de FJ compatíveis."
                    else CorrectionDestinations.INTERNAL_BLOCK, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else Field("Destino externo", target, { target = it }, Modifier.testTag("correction-target"), enabled = !saving)
            if (!protectedInternal) OutlinedButton(enabled = !saving && !internal, onClick = {
                focus.clearFocus(); keyboard?.hide(); issue = null; notice = null; preview = null; checking = true; reviewing = true
            }, modifier = Modifier.testTag("preview-correction")) { Text("Corrigir") }
        } else {
            preview?.let { checked ->
                Text(if (checked.link.effectiveDate == checked.effectiveDate) "Data: ${shortDate(checked.effectiveDate)}" else "Data: ${shortDate(checked.link.effectiveDate)} → ${shortDate(checked.effectiveDate)}", style = MaterialTheme.typography.titleSmall)
                Text(if (checked.link.externalTarget == checked.externalTarget) "Destino: ${checked.externalTarget}"
                    else "Destino: ${checked.link.externalTarget} → ${checked.externalTarget}")
                if (checked.records.isEmpty()) Text("Nenhum lançamento no período afetado.")
                checked.records.forEach { record ->
                    Column {
                        Text("${shortDate(record.date)} • ${record.type} • ${record.value}")
                        if (record.date < checked.effectiveDate) Text("Preservado no período corrigido", style = MaterialTheme.typography.labelMedium)
                        record.attendance?.let { mark ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(mark.lessonId in selected, enabled = !saving, onCheckedChange = { value ->
                                    checking = true; confirm = false
                                    selectedIds = ArrayList(if (value) selected + mark.lessonId else selected - mark.lessonId)
                                }, modifier = Modifier.testTag("remove-correction-${mark.lessonId}").semantics {
                                    contentDescription = "Retirar marcação indevida: ${shortDate(record.date)}, ${record.type}, ${record.value}"
                                })
                                Text("Retirar marcação indevida", Modifier.weight(1f))
                            }
                            if (!checking && mark.lessonId in checked.attendanceBlockers) Text("Confirme a retirada ou reveja a data.", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                if (!checking && issue == null) checked.blockers.filterNot { it in checked.attendanceBlockers.values }.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            if (checking) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("correction-checking").semantics { contentDescription = "Atualizando prévia" })
            notice?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            issue?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            if (issue != null) TextButton(onClick = { reload++ }, enabled = !saving) { Text("Atualizar prévia") }
            Button(enabled = !saving && !checking && issue == null && preview?.canConfirm == true && selected == preview?.remove,
                onClick = { confirm = true }, modifier = Modifier.testTag("save-correction")) { Text("Confirmar correção") }
        }
        if (!reviewing) link?.corrections?.asReversed()?.forEach { audit ->
            Text(if (audit.previousDate == audit.effectiveDate) "Correção confirmada: ${shortDate(audit.effectiveDate)}" else "Correção confirmada: ${shortDate(audit.previousDate)} → ${shortDate(audit.effectiveDate)}")
            Text(if (audit.previousTarget == audit.externalTarget) "Destino: ${audit.externalTarget}" else "Destino: ${audit.previousTarget} → ${audit.externalTarget}")
            audit.removed.forEach { removed -> Text("Retirado: ${shortDate(removed.lesson.date)} • ${removed.lesson.ordinal}º horário • ${removed.attendance.mark}") }
        }
        SaveError(vm)
    }
    if (confirm && !checking) preview?.let { checked ->
        val counts = checked.records.mapNotNull { it.attendance }.filter { it.lessonId in checked.remove }
            .groupingBy { it.mark }.eachCount().toSortedMap().entries.joinToString(" • ") { "${it.value} ${it.key}" }
        val dates = if (checked.link.effectiveDate == checked.effectiveDate) shortDate(checked.effectiveDate)
            else "${shortDate(checked.link.effectiveDate)} → ${shortDate(checked.effectiveDate)}"
        val summary = "${student.name}\nData: $dates\nDestino: ${checked.externalTarget}\n" +
            (if (counts.isEmpty()) "Nenhuma marcação será retirada." else "Retirar: $counts. Sem atribuir presença ou falta.") + "\nA matrícula continuará Remanejada."
        AlertDialog(onDismissRequest = { if (!saving) confirm = false }, title = { Text("Confirmar correção?") },
            text = { Text(summary, Modifier.verticalScroll(rememberScrollState())) },
            dismissButton = { TextButton(onClick = { confirm = false }, enabled = !saving) { Text("Cancelar") } },
            confirmButton = { TextButton(enabled = !saving, onClick = {
            confirm = false; committing = true
            vm.act {
                try {
                    vm.repository.confirmRelocationCorrection(checked)
                    withContext(Dispatchers.Main) { dismiss() }
                } catch (changed: RelocationCorrectionChanged) {
                    withContext(Dispatchers.Main) {
                        committing = false; selectedIds = arrayListOf(); reload++
                        notice = "Os registros mudaram. Confira a prévia atualizada e selecione novamente as marcações. Nada foi alterado."
                    }
                } catch (error: Exception) {
                    withContext(Dispatchers.Main) { committing = false }
                    throw error
                }
            }
        }) { Text("Confirmar") } })
    }
    if (discard) Confirm("Descartar a correção não confirmada?", "O remanejamento e os registros permanecerão como estão.", { discard = false }) { dismiss() }
}
