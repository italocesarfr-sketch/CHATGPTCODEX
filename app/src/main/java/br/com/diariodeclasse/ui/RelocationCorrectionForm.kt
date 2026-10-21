package br.com.diariodeclasse.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import br.com.diariodeclasse.data.*

/** A confirmação usa uma prévia congelada e revalidada pelo repositório. */
@Composable fun RelocationCorrectionForm(vm: DiaryViewModel, student: Student, dismiss: () -> Unit) {
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    val data by vm.data.collectAsState()
    val saving by vm.saving.collectAsState()
    val link = data.relocations.firstOrNull { it.sourceStudentId == student.id }
    var day by rememberSaveable { mutableStateOf(link?.effectiveDate?.let(::shortDate).orEmpty()) }
    var target by rememberSaveable { mutableStateOf(link?.externalTarget.orEmpty()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var preview by remember { mutableStateOf<RelocationCorrectionPreview?>(null) }
    var confirm by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    val dirty = day != link?.effectiveDate?.let(::shortDate) || target != link?.externalTarget || selected.isNotEmpty()
    fun leave() { if (!saving) { if (dirty) discard = true else dismiss() } }
    Form("Corrigir remanejamento", ::leave) {
        Text(student.name, style = MaterialTheme.typography.titleMedium)
        if (link == null) Text("Remanejamento não encontrado.")
        else if (link.targetStudentId != null) Text("Remanejamento interno: a correção precisa ajustar as duas matrículas em conjunto. Nenhum vínculo foi alterado.")
        else {
            Text("Registrado: ${shortDate(link.effectiveDate)} • ${link.externalTarget}")
            Field("Data efetiva (dd/mm/aaaa)", day, { day = it; preview = null; selected = emptySet() }, Modifier.testTag("correction-date"), enabled = !saving)
            Field("Destino externo", target, { target = it; preview = null }, Modifier.testTag("correction-target"), enabled = !saving)
            OutlinedButton(enabled = !saving, onClick = {
                focus.clearFocus();keyboard?.hide()
                val d = day; val t = target; val remove = selected
                vm.act { val checked = vm.repository.previewRelocationCorrection(student.id, iso(d), t, remove)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { preview = checked } }
            }, modifier = Modifier.testTag("preview-correction")) { Text("Conferir correção") }
            preview?.let { checked ->
                Text("${shortDate(checked.link.effectiveDate)} → ${shortDate(checked.effectiveDate)}", style = MaterialTheme.typography.titleSmall)
                Text("${checked.link.externalTarget} → ${checked.externalTarget}")
                Text("A matrícula continuará Remanejada.")
                if (checked.records.isEmpty()) Text("Nenhum lançamento na data proposta ou depois.")
                checked.records.forEach { record ->
                    Column {
                        Text("${shortDate(record.date)} • ${record.type} • ${record.value}")
                        if (record.date < checked.effectiveDate) Text("Preservado no período corrigido", style = MaterialTheme.typography.labelMedium)
                        record.attendance?.let { mark -> Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Checkbox(mark.lessonId in selected, enabled = !saving, onCheckedChange = { value ->
                                selected = if (value) selected + mark.lessonId else selected - mark.lessonId
                            }, modifier = Modifier.testTag("remove-correction-${mark.lessonId}"))
                            Text("Retirar marcação indevida", Modifier.weight(1f))
                        } }
                    }
                }
                if (selected != checked.remove) Text("Toque em Conferir correção para atualizar a prévia.")
                checked.blockers.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(enabled = !saving && checked.canConfirm && selected == checked.remove, onClick = { confirm = true }, modifier = Modifier.testTag("save-correction")) { Text("Confirmar correção") }
            }
        }
        link?.corrections?.asReversed()?.forEach { audit ->
            Text("Correção confirmada: ${shortDate(audit.previousDate)} → ${shortDate(audit.effectiveDate)} • ${audit.previousTarget} → ${audit.externalTarget}")
            audit.removed.forEach { removed -> Text("Retirado: ${shortDate(removed.lesson.date)} • ${removed.lesson.ordinal}º horário • ${removed.attendance.mark}") }
        }
        SaveError(vm)
    }
    if (confirm) preview?.let { checked -> Confirm("Confirmar correção administrativa?",
        "${student.name}\n${shortDate(checked.link.effectiveDate)} → ${shortDate(checked.effectiveDate)}\nDestino: ${checked.externalTarget}\n${checked.remove.size} marcação(ões) selecionada(s) serão retiradas, sem atribuir presença ou falta. A matrícula continuará Remanejada.",
        { if (!saving) confirm = false }) { confirm = false; vm.act(done = dismiss) { vm.repository.confirmRelocationCorrection(checked) } } }
    if (discard) Confirm("Descartar a correção não confirmada?", "O remanejamento e os registros permanecerão como estão.", { discard = false }) { dismiss() }
}
