package br.com.diariodeclasse
import android.app.Application
import br.com.diariodeclasse.BuildConfig
import br.com.diariodeclasse.backup.*
import java.io.File
import kotlinx.coroutines.*
import br.com.diariodeclasse.data.DiaryDatabase
import br.com.diariodeclasse.data.DiaryRepository
class DiaryApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CoroutineScope(Dispatchers.IO).launch {
            val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
            File(cacheDir, "photos").listFiles()?.filter { it.isFile && it.lastModified() < cutoff }?.forEach { it.delete() }
        }
    }
    val database by lazy { DiaryDatabase.open(this) }
    val backupManager by lazy { BackupManager(database, FileSafetyBackupStore(File(filesDir, "safety-backups")), BuildConfig.VERSION_CODE, InstallationIdentity.certificate(this)) }
    val repository by lazy { DiaryRepository(database) }
}
