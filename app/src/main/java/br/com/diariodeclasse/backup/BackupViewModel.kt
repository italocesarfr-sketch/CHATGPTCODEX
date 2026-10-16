package br.com.diariodeclasse.backup

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.diariodeclasse.DiaryApplication
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BackupUiState(val busy: Boolean = false, val message: String = "", val prepared: PreparedRestore? = null, val hasSafetyCopy: Boolean = false)
class BackupViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as DiaryApplication
    private val manager = app.backupManager
    private val _state = MutableStateFlow(BackupUiState(hasSafetyCopy = manager.hasSafetyCopy()))
    val state = _state.asStateFlow()
    private fun operation(action: suspend () -> BackupUiState) {
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true, message = "Processando no aparelho…")
        viewModelScope.launch {
            try { _state.value = withContext(Dispatchers.IO) { action() } }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                _state.value = _state.value.copy(busy = false, message = if (error is BackupFailure) error.message.orEmpty()
                    else "Operação não concluída. Os dados do aplicativo foram preservados. Confira o arquivo e o espaço disponível.", hasSafetyCopy = manager.hasSafetyCopy())
            }
        }
    }
    fun cancelled() { if (!_state.value.busy) _state.value = _state.value.copy(message = "Operação cancelada. Nenhum dado foi restaurado.") }
    fun discardPreview() { if (!_state.value.busy) _state.value = _state.value.copy(prepared = null, message = "Prévia cancelada. O banco permanece igual.") }
    fun export(uri: Uri, safety: Boolean) = operation {
        requireNotNull(app.contentResolver.openOutputStream(uri, "wt")) { "Destino indisponível." }.use {
            if (safety) manager.writeLatestSafetyCopy(it) else manager.writeBackup(it)
        }
        BackupUiState(message = "Backup salvo no destino escolhido.", hasSafetyCopy = manager.hasSafetyCopy())
    }
    fun prepare(uri: Uri) = operation {
        val prepared = requireNotNull(app.contentResolver.openInputStream(uri)) { "Arquivo indisponível." }.use { manager.prepare(it) }
        BackupUiState(message = "Arquivo validado. Confira o conteúdo antes de restaurar.", prepared = prepared, hasSafetyCopy = manager.hasSafetyCopy())
    }
    fun restore(confirmation: String) {
        val prepared = _state.value.prepared ?: return
        operation {
            manager.restore(prepared, confirmation)
            BackupUiState(message = "Restauração concluída. Uma cópia dos dados anteriores está disponível para exportar.", hasSafetyCopy = manager.hasSafetyCopy())
        }
    }
}
