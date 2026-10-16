package br.com.diariodeclasse.ui
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.diariodeclasse.DiaryApplication
import br.com.diariodeclasse.data.DiarySnapshot
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
class DiaryViewModel(application: Application) : AndroidViewModel(application) {
    val repository = (application as DiaryApplication).repository
    val data = repository.snapshot.stateIn(viewModelScope, SharingStarted.Eagerly, DiarySnapshot())
    val messages = Channel<String>(Channel.BUFFERED)
    private val commands = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()
    init {
        viewModelScope.launch {
            for (command in commands) {
                _saving.value = true
                try { withContext(Dispatchers.IO) { command() } }
                catch (error: CancellationException) { throw error }
                catch (error: Exception) {
                    messages.send(when (error) {
                        is java.time.DateTimeException -> "Data inválida. Use dd/mm/aaaa e confira o calendário."
                        is NumberFormatException -> "Número inválido. Revise os campos numéricos."
                        is IllegalArgumentException -> error.message ?: "Revise os campos."
                        else -> "Não foi possível salvar. Os registros anteriores foram preservados. Tente novamente."
                    })
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
    fun message(text: String) { messages.trySend(text) }
}
