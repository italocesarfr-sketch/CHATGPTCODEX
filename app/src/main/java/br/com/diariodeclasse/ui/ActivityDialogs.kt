package br.com.diariodeclasse.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.domain.GradeScaling
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable fun GradeChangeDialog(vm: DiaryViewModel, preview: ActivityEditPreview, cancel: () -> Unit, apply: (GradeChangeMode) -> Unit) {
    val saving by vm.saving.collectAsState()
    val old=preview.before.activity
    Form("Confirmar alteração da pontuação", { if(!saving) cancel() }) {
        Text(old.name,style=MaterialTheme.typography.titleMedium)
        Text("Máximo anterior: ${editableNumber(old.maximum)}\nNovo máximo: ${editableNumber(preview.proposed.maximum)}")
        Text("${counted(preview.before.launched,"nota lançada","notas lançadas")}. Pendentes continuam pendentes; zero continua zero.")
        Text("Prévia proporcional",style=MaterialTheme.typography.titleSmall)
        val students=vm.data.collectAsState().value.students.associateBy {it.id}
        preview.before.grades.filter {it.value!=null}.take(4).forEach { g ->
            val after=runCatching {GradeScaling.proportional(g.value,old.maximum,preview.proposed.maximum)}.getOrNull()
            Text("${students[g.studentId]?.name ?: "Aluno"}: ${editableNumber(g.value!!)} → ${after?.let(::editableNumber) ?: "fora da precisão disponível"}")
        }
        Info("A prévia usa precisão interna. Boletins exibem duas casas decimais; o recálculo não arredonda as notas para duas casas. Atividade e notas só serão salvas juntas após sua escolha.")
        Button(enabled=!saving,onClick={apply(GradeChangeMode.PROPORTIONAL)},modifier=Modifier.fillMaxWidth().testTag("recalculate-grades")) {Text("Recalcular proporcionalmente")}
        OutlinedButton(enabled=!saving&&preview.canKeep,onClick={apply(GradeChangeMode.KEEP)},modifier=Modifier.fillMaxWidth().testTag("keep-grades")) {Text("Manter notas existentes")}
        if(!preview.canKeep) Text("Manter indisponível: há nota acima do novo máximo.",color=MaterialTheme.colorScheme.error)
        TextButton(enabled=!saving,onClick=cancel,modifier=Modifier.fillMaxWidth()) {Text("Cancelar")}
        SaveError(vm)
    }
}

@Composable fun DeleteActivityDialog(vm: DiaryViewModel, id: String, dismiss: () -> Unit, deleted: () -> Unit) {
    var snapshot by remember(id) {mutableStateOf<ActivitySnapshot?>(null)}
    val saving by vm.saving.collectAsState()
    fun refresh() { vm.act {
        val current=vm.repository.activitySnapshot(id)
        withContext(Dispatchers.Main) {snapshot=current}
    } }
    LaunchedEffect(id) {refresh()}
    Form("Excluir atividade?",{if(!saving)dismiss()}) {
        snapshot?.let { current ->
            Text(current.activity.name,style=MaterialTheme.typography.titleMedium)
            Text("${counted(current.launched,"nota já lançada","notas já lançadas")}",modifier=Modifier.testTag("deletion-grade-count"))
            Info("A atividade e TODAS as notas vinculadas serão excluídas permanentemente, incluindo registros pendentes. Essa operação não pode ser desfeita. Outras atividades não serão alteradas.")
            Button(enabled=!saving,onClick={vm.act(done=deleted){vm.repository.deleteActivity(current)}},modifier=Modifier.fillMaxWidth().testTag("confirm-delete-activity")) {Text("Excluir permanentemente")}
        } ?: Text("Carregando contagem de notas…")
        OutlinedButton(enabled=!saving,onClick={refresh()}) {Text("Atualizar prévia")}
        TextButton(enabled=!saving,onClick=dismiss) {Text("Cancelar")}
        SaveError(vm)
    }
}
