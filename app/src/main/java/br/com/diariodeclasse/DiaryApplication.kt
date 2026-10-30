package br.com.diariodeclasse
import android.app.Application
import kotlinx.coroutines.launch
import br.com.diariodeclasse.BuildConfig
import br.com.diariodeclasse.backup.*
import java.io.File
import br.com.diariodeclasse.data.LocalSelectionStore
import br.com.diariodeclasse.data.DiaryDatabase
import br.com.diariodeclasse.data.DiaryRepository
open class DiaryApplication : Application() {
    val authScope=kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()+kotlinx.coroutines.Dispatchers.Main.immediate)
    override fun onCreate() {
        super.onCreate()
        listOf("accounts","exports","exports-share").forEach{File(cacheDir,it).deleteRecursively()}
        authScope.launch { auth.restore() }
        br.com.diariodeclasse.sync.SyncJobs.schedule(this)
    }
    open val auth by lazy { br.com.diariodeclasse.auth.AuthController(br.com.diariodeclasse.auth.SupabaseAuthClient(connected={
        val connectivity=getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        connectivity.getNetworkCapabilities(connectivity.activeNetwork)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)==true
    }),br.com.diariodeclasse.auth.KeystoreAuthVault(this)) }
    val workspaces by lazy { br.com.diariodeclasse.auth.AccountWorkspaces(this,auth) }
    open val workspace: br.com.diariodeclasse.auth.AccountWorkspace get() = workspaces.current()
    val commands get() = workspace.commands
    val selectionStore get() = workspace.selection
    val database get() = workspace.database
    val backupManager get() = workspace.backup
    val repository get() = workspace.repository
}
