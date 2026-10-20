package br.com.diariodeclasse.backup

import br.com.diariodeclasse.ui.theme.NoctuaSpace
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.diariodeclasse.ui.DiaryViewModel
import br.com.diariodeclasse.ui.Info
import java.time.LocalDate

/** Acesso isolado à segurança dos dados; não modifica a navegação de turmas ou chamadas. */
@Composable fun BackupDialog(diary: DiaryViewModel, dismiss: () -> Unit) {
    val vm: BackupViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    var ready by remember { mutableStateOf(false) }
    var confirmation by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(diary) { diary.awaitPendingWrites(); ready = true }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) vm.export(uri, false) else vm.cancelled() }
    val safety = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) vm.export(uri, true) else vm.cancelled() }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) { confirmation = ""; vm.prepare(uri) } else vm.cancelled() }
    val enabled = ready && !state.busy
    Dialog(onDismissRequest = { if (enabled) dismiss() }) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().heightIn(max = 700.dp).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(NoctuaSpace.medium)) {
                Text("Backup e restauração", style = MaterialTheme.typography.titleLarge)
                Text("Guarde o arquivo em um local protegido. Ele contém dados dos alunos.")
                if (!ready || state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.message.isNotBlank() && !state.exportSucceeded) Text(state.message, Modifier.testTag("backup-message"))
                Button(enabled = enabled, onClick = { create.launch("diario-backup-${LocalDate.now()}.json") }, modifier = Modifier.fillMaxWidth()) { Text("Criar backup completo") }
                OutlinedButton(enabled = enabled, onClick = { open.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) { Text("Selecionar backup para restaurar") }
                if (state.hasSafetyCopy) OutlinedButton(enabled = enabled, onClick = { safety.launch("diario-copia-anterior-${LocalDate.now()}.json") }, modifier = Modifier.fillMaxWidth()) { Text("Exportar cópia anterior à restauração") }
                state.prepared?.let { prepared ->
                    HorizontalDivider()
                    Text("Prévia do backup", style = MaterialTheme.typography.titleMedium)
                    Text("Criado em ${prepared.createdAt}\n${prepared.summary}")
                    Info("Restaurar substitui todos os dados atuais. Uma cópia anterior será preservada. Confira o arquivo antes de confirmar.")
                    OutlinedTextField(value = confirmation, onValueChange = { confirmation = it }, singleLine = true, enabled = enabled, label = { Text("Digite RESTAURAR") }, modifier = Modifier.fillMaxWidth())
                    Button(enabled = enabled && confirmation == "RESTAURAR", onClick = { vm.restore(confirmation) }, modifier = Modifier.fillMaxWidth()) { Text("Confirmar substituição dos dados") }
                    TextButton(enabled = enabled, onClick = { confirmation = ""; vm.discardPreview() }) { Text("Cancelar restauração") }
                }
                HorizontalDivider()
                TextButton(enabled = enabled, onClick = dismiss, modifier = Modifier.fillMaxWidth()) { Text("Voltar") }
            }
        }
    }
    if (state.exportSucceeded) AlertDialog(onDismissRequest = vm::acknowledgeExport,
        title = { Text("Backup salvo com sucesso!", Modifier.testTag("backup-success")) },
        confirmButton = { TextButton(onClick = vm::acknowledgeExport) { Text("OK") } })
}
