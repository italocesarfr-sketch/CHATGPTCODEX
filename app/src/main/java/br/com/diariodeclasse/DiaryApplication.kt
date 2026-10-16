package br.com.diariodeclasse
import android.app.Application
import br.com.diariodeclasse.BuildConfig
import br.com.diariodeclasse.backup.*
import java.io.File
import br.com.diariodeclasse.data.LocalSelectionStore
import br.com.diariodeclasse.data.DiaryDatabase
import br.com.diariodeclasse.data.DiaryRepository
class DiaryApplication : Application() {
    val commands by lazy { br.com.diariodeclasse.ui.DiaryCommands() }
    val selectionStore by lazy { LocalSelectionStore(this) }
    val database by lazy { DiaryDatabase.open(this) }
    val backupManager by lazy { BackupManager(database, FileSafetyBackupStore(File(filesDir, "safety-backups")), BuildConfig.VERSION_CODE, InstallationIdentity.certificate(this), selectionStore) }
    val repository by lazy { DiaryRepository(database) }
}
