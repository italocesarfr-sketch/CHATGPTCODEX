package br.com.diariodeclasse.ui

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

/** Fila do processo: recriar Activity/ViewModel não cancela gravações já solicitadas.
 * Apenas o commit Room é durável; encerramento do processo antes dele não é prometido. */
class DiaryCommands {
    private data class Command(val action: suspend () -> Unit, val done: (() -> Unit)?, val clear: Boolean, val context: String, val key: String)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val queue = Channel<Command>(Channel.UNLIMITED)
    private val pending = AtomicInteger()
    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _errorContext = MutableStateFlow("")
    val errorContext = _errorContext.asStateFlow()
    val messages = Channel<String>(Channel.BUFFERED)
    private var failed: Command? = null
    private val failedAttendance = linkedMapOf<String, Command>()
    private val _attendanceError = MutableStateFlow<String?>(null)
    val attendanceError = _attendanceError.asStateFlow()
    private fun updateAttendanceError() {
        _attendanceError.value = failedAttendance.values.takeIf { it.isNotEmpty() }?.let {
            "${it.size} alteração(ões) não salva(s). Origem: ${it.last().context}. Tente novamente ou descarte a intenção não salva para manter os registros atuais."
        }
    }
    fun discardAttendanceFailures() { if (failed?.context?.isNotEmpty() == true) failed = null; failedAttendance.clear(); updateAttendanceError(); clearError() }

    fun clearError() { _error.value = null }
    init {
        scope.launch {
            for (command in queue) {
                if (command.clear) clearError()
                try {
                    withContext(Dispatchers.IO) { command.action() }
                    command.done?.invoke()
                    if (command.context.isNotEmpty()) { failedAttendance.remove(command.key); updateAttendanceError() }
                    if (command.clear && failed?.key == command.key) failed = null
                } catch (error: CancellationException) { throw error }
                catch (error: Exception) {
                    val message = when (error) {
                        is java.time.DateTimeException -> "Data inválida. Use dd/mm/aaaa e confira o calendário."
                        is NumberFormatException -> "Número inválido. Revise os campos numéricos."
                        is IllegalArgumentException -> error.message ?: "Revise os campos."
                        else -> "Não foi possível salvar. Os registros anteriores foram preservados. Tente novamente."
                    }
                    if (command.context.isNotEmpty()) { failedAttendance[command.key] = command; updateAttendanceError() }
                    failed = command; _errorContext.value = command.context; _error.value = message
                    messages.trySend(message)
                } finally { _saving.value = pending.decrementAndGet() > 0 }
            }
        }
    }
    fun act(done: (() -> Unit)? = null, clearError: Boolean = true, context: String = "", key: String = java.util.UUID.randomUUID().toString(), action: suspend () -> Unit) {
        pending.incrementAndGet(); _saving.value = true
        check(queue.trySend(Command(action, done, clearError, context, key)).isSuccess)
    }
    fun retry() {
        val pending = failedAttendance.values.toList().ifEmpty { listOfNotNull(failed) }
        pending.forEach { act(it.done, true, it.context, it.key, it.action) }
    }
    suspend fun awaitPendingWrites() {
        val completed = CompletableDeferred<Unit>()
        act(done = { completed.complete(Unit) }, clearError = false) {}
        completed.await()
    }
}
