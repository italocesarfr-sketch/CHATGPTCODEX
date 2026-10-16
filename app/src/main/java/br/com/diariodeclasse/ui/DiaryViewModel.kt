package br.com.diariodeclasse.ui
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.diariodeclasse.DiaryApplication
import br.com.diariodeclasse.data.DiarySnapshot
import br.com.diariodeclasse.data.AppSelection
import br.com.diariodeclasse.data.resolveSelection
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
class DiaryViewModel(application: Application) : AndroidViewModel(application) {
    val repository = (application as DiaryApplication).repository
    private val selectionStore = (application as DiaryApplication).selectionStore
    val selection = selectionStore.state
    fun select(value: AppSelection, done: () -> Unit) = act(done) { selectionStore.write(value) }
    fun initializeSelection() = act {
        if (selectionStore.read() == null) selectionStore.write(resolveSelection(null, repository.db.backupDao().classes()))
    }
    val restoreGeneration = (application as DiaryApplication).backupManager.generation
    val restoreNotice: String get() = (getApplication<Application>() as DiaryApplication).backupManager.restoreNotice
    val data = repository.snapshot.stateIn(viewModelScope, SharingStarted.Eagerly, DiarySnapshot())
    val messages = Channel<String>(Channel.BUFFERED)
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    fun clearError() { _error.value = null }
    private val commands = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()
    init {
        viewModelScope.launch {
            for (command in commands) {
                _saving.value = true
                clearError()
                try { withContext(Dispatchers.IO) { command() } }
                catch (error: CancellationException) { throw error }
                catch (error: Exception) {
                    val message = when (error) {
                        is java.time.DateTimeException -> "Data inválida. Use dd/mm/aaaa e confira o calendário."
                        is NumberFormatException -> "Número inválido. Revise os campos numéricos."
                        is IllegalArgumentException -> error.message ?: "Revise os campos."
                        else -> "Não foi possível salvar. Os registros anteriores foram preservados. Tente novamente."
                    }
                    _error.value = message
                    messages.send(message)
                } finally { _saving.value = false }
            }
        }
    }
    fun act(done: (() -> Unit)? = null, action: suspend () -> Unit) {
        commands.trySend {
            action()
            if (done != null) withContext(Dispatchers.Main) { done() }
        }
    }
    suspend fun awaitPendingWrites() {
        val drained = CompletableDeferred<Unit>()
        commands.send { drained.complete(Unit) }
        drained.await()
    }
    fun message(text: String) { messages.trySend(text) }
}
