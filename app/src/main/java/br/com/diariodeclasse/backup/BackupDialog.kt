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
    var password by remember { mutableStateOf("") }
    var allowLegacy by rememberSaveable { mutableStateOf(false) }
    var credentialMessage by remember { mutableStateOf("") }
    val app = diary.getApplication<br.com.diariodeclasse.DiaryApplication>()
    LaunchedEffect(diary) { diary.awaitPendingWrites(); ready = true }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> if (uri != null) vm.export(uri, false, password.toCharArray()) else vm.cancelled(); password="" }
    val safety = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> if (uri != null) vm.export(uri, true, password.toCharArray()) else vm.cancelled(); password="" }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) { confirmation = ""; vm.prepare(uri,password.toCharArray(),allowLegacy) } else vm.cancelled();password="";allowLegacy=false }
    val credential = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if(result.resultCode==android.app.Activity.RESULT_OK){allowLegacy=true;open.launch(arrayOf("application/json"))}
    }
    val enabled = ready && !state.busy
    Dialog(onDismissRequest = { if (enabled) dismiss() }) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().heightIn(max = 700.dp).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(NoctuaSpace.medium)) {
                Text("Backup e restauração", style = MaterialTheme.typography.titleLarge)
                Text("Guarde o arquivo em um local protegido. Ele contém dados dos alunos.")
                OutlinedTextField(password,{password=it},label={Text("Senha do backup")},keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Password),visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation(),singleLine=true,enabled=enabled,modifier=Modifier.fillMaxWidth().testTag("backup-password"))
                Text("Use 12 ou mais caracteres para criar um backup. Guarde a senha: ela será necessária para restaurar.")
                if (!ready || state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.message.isNotBlank() && !state.exportSucceeded) Text(state.message, Modifier.testTag("backup-message"))
                if (credentialMessage.isNotBlank()) Text(credentialMessage)
                Button(enabled = enabled && password.length>=12, onClick = { create.launch("diario-backup-${LocalDate.now()}.noctua") }, modifier = Modifier.fillMaxWidth()) { Text("Criar backup completo") }
                OutlinedButton(enabled = enabled, onClick = { open.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) { Text("Selecionar backup para restaurar") }
                if(app.workspace.user.id==br.com.diariodeclasse.auth.AuthConfig.LEGACY_OWNER_ID)TextButton(enabled=enabled,onClick={
                    val guard=app.getSystemService(android.content.Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
                    val intent=if(guard.isDeviceSecure)guard.createConfirmDeviceCredentialIntent("Restaurar backup anterior",app.workspace.user.email)else null
                    if(intent!=null){credentialMessage="";credential.launch(intent)}
                    else credentialMessage="Configure um PIN ou senha de bloqueio no Android para restaurar um backup anterior."
                }){Text("Restaurar backup anterior à autenticação")}
                if (state.hasSafetyCopy) OutlinedButton(enabled = enabled && password.length>=12, onClick = { safety.launch("diario-copia-anterior-${LocalDate.now()}.noctua") }, modifier = Modifier.fillMaxWidth()) { Text("Exportar cópia anterior à restauração") }
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
