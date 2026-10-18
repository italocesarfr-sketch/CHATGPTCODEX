package br.com.diariodeclasse.ui
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.diariodeclasse.DiaryApplication
import br.com.diariodeclasse.data.*
import kotlinx.coroutines.flow.*
class DiaryViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as DiaryApplication
    val repository = app.repository
    private val selectionStore = app.selectionStore
    private val commands = app.commands
    val selection = selectionStore.state
    fun select(value: AppSelection, done: () -> Unit) = act(done, clearError = false) { selectionStore.write(value) }
    fun initializeSelection() = act(clearError = false) {
        if (selectionStore.read() == null) {
            val archives=repository.db.backupDao().classArchives().map{it.classId}.toSet()
            selectionStore.write(resolveSelection(null, repository.db.backupDao().classes().filter{it.id !in archives}))
        }
    }
    val restoreGeneration = app.backupManager.generation
    val restoreNotice: String get() = app.backupManager.restoreNotice
    val data = repository.snapshot.stateIn(viewModelScope, SharingStarted.Eagerly, DiarySnapshot())
    val messages = commands.messages
    val error = commands.error
    val errorContext = commands.errorContext
    val attendanceError = commands.attendanceError
    fun discardAttendanceFailures() = commands.discardAttendanceFailures()
    val saving = commands.saving
    fun clearError() = commands.clearError()
    fun retry() = commands.retry()
    fun act(done: (() -> Unit)? = null, clearError: Boolean = true, context: String = "", key: String = java.util.UUID.randomUUID().toString(), action: suspend () -> Unit) = commands.act(done, clearError, context, key, action)
    suspend fun awaitPendingWrites() = commands.awaitPendingWrites()
    fun message(text: String) { messages.trySend(text) }
}
