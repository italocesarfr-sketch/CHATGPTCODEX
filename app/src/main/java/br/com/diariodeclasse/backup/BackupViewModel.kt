package br.com.diariodeclasse.backup

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.diariodeclasse.DiaryApplication
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BackupUiState(val busy: Boolean = false, val message: String = "", val prepared: PreparedRestore? = null, val hasSafetyCopy: Boolean = false, val exportSucceeded: Boolean = false)
class BackupViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as DiaryApplication
    private val manager = app.backupManager
    private val commands = app.commands
    private val owner = app.workspace.user.id
    private val legacy = owner == br.com.diariodeclasse.auth.AuthConfig.LEGACY_OWNER_ID
    private val _state = MutableStateFlow(BackupUiState(hasSafetyCopy = manager.hasSafetyCopy()))
    val state = _state.asStateFlow()
    private fun operation(failureMessage: String? = null, action: suspend () -> BackupUiState) {
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true, message = "Processando no aparelho…", exportSucceeded = false)
        viewModelScope.launch {
            try { _state.value = withContext(Dispatchers.IO) { action() } }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                _state.value = _state.value.copy(busy = false, exportSucceeded = false, message = if (error is BackupFailure || error is br.com.diariodeclasse.auth.AuthFailure) error.message.orEmpty()
                    else failureMessage ?: "Operação não concluída. Os dados do aplicativo foram preservados. Confira o arquivo e o espaço disponível.", hasSafetyCopy = manager.hasSafetyCopy())
            }
        }
    }
    fun cancelled() { if (!_state.value.busy) _state.value = _state.value.copy(message = "Operação cancelada.", exportSucceeded = false) }
    fun acknowledgeExport() { if (!_state.value.busy) _state.value = _state.value.copy(message = "", exportSucceeded = false) }
    fun discardPreview() { if (!_state.value.busy) _state.value = _state.value.copy(prepared = null, message = "Prévia cancelada. O banco permanece igual.") }
    fun export(uri: Uri, safety: Boolean, password: CharArray) = exportTo({ app.contentResolver.openOutputStream(uri, "wt") }, safety, password)
    internal fun exportTo(open: () -> java.io.OutputStream?, safety: Boolean = false, password: CharArray = charArrayOf()) = operation("Não foi possível salvar o backup. Confira o destino e o espaço disponível.") {
        check(app.workspace.user.id == owner)
        val raw = if(safety) java.io.ByteArrayOutputStream().also{manager.writeLatestSafetyCopy(it)}.toByteArray() else manager.backupBytes()
        try {
            requireNotNull(open()) { "Destino indisponível." }.use {
                br.com.diariodeclasse.auth.AccountBackup.write(it,raw,owner,password);it.flush()
            }
        } finally { password.fill('\u0000') }
        BackupUiState(message = "Backup salvo com sucesso!", hasSafetyCopy = manager.hasSafetyCopy(), exportSucceeded = true)
    }
    fun prepare(uri: Uri, password: CharArray, legacyCredentialVerified: Boolean = false) = operation {
        check(app.workspace.user.id == owner)
        val bytes = requireNotNull(app.contentResolver.openInputStream(uri)) { "Arquivo indisponível." }.use { br.com.diariodeclasse.auth.AccountBackup.read(it) }
        val raw = try {
            if(br.com.diariodeclasse.auth.AccountBackup.protected(bytes)) br.com.diariodeclasse.auth.AccountBackup.open(bytes,owner,password)
            else {
                check(legacy && legacyCredentialVerified) { "Backups anteriores à autenticação exigem a conta proprietária e confirmação do Android." }
                bytes
            }
        } finally { password.fill('\u0000') }
        val prepared = manager.prepare(raw.inputStream())
        BackupUiState(message = "Arquivo validado. Confira o conteúdo antes de restaurar.", prepared = prepared, hasSafetyCopy = manager.hasSafetyCopy())
    }
    fun restore(confirmation: String) {
        val prepared = _state.value.prepared ?: return
        operation {
            val result = manager.restore(prepared, confirmation)
            withContext(Dispatchers.Main) { commands.clearFailuresAfterRestore() }
            BackupUiState(message = "Restauração concluída. Uma cópia dos dados anteriores está disponível para exportar. ${result.warning}", hasSafetyCopy = manager.hasSafetyCopy())
        }
    }
}
