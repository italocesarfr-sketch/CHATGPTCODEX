package br.com.diariodeclasse.auth

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import br.com.diariodeclasse.BuildConfig
import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.ui.DiaryCommands
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest

class AccountContext(base:Context,val accountId:String,private val folder:File):ContextWrapper(base){override fun getCacheDir()=folder}
class AccountWorkspace(base:Context,val user:AuthUser,val legacy:Boolean):AutoCloseable {
    val namespace=MessageDigest.getInstance("SHA-256").digest(user.id.toByteArray()).joinToString(""){"%02x".format(it)}
    val cache=File(base.cacheDir,"accounts/$namespace").apply{mkdirs()}
    val context=AccountContext(base,user.id,cache)
    val commands=DiaryCommands()
    val selection=LocalSelectionStore(base,if(legacy)"diary-context"else"diary-context-$namespace")
    private val dbPath=if(legacy)base.getDatabasePath("diario.db")else File(base.getDatabasePath("diario.db").parentFile,"accounts/$namespace/diario.db")
    val database=DiaryDatabase.open(base,dbPath.also{check(it.parentFile!!.isDirectory||it.parentFile!!.mkdirs())}.absolutePath)
    val repository=DiaryRepository(database)
    val backup=BackupManager(database,FileSafetyBackupStore(if(legacy)File(base.filesDir,"safety-backups")else File(base.filesDir,"accounts/$namespace/safety-backups")),BuildConfig.VERSION_CODE,InstallationIdentity.certificate(base),selection)
    suspend fun shutdown()=kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
        withContext(Dispatchers.Main.immediate){commands.finish()}
        withContext(Dispatchers.IO){close()}
    }
    override fun close(){database.close();cache.deleteRecursively()}
}

class AccountWorkspaces(private val base:Context,private val auth:AuthController) {
    private val mutex=Mutex()
    @Volatile private var active:AccountWorkspace?=null
    fun current():AccountWorkspace=active?.also{check(auth.canUse(it.user.id)){"Entre novamente para acessar os registros."}} ?: error("Entre antes de acessar os registros.")
    fun hasLegacy()=base.getDatabasePath("diario.db").exists()
    fun needsClaim(user:AuthUser)=hasLegacy()&&auth.legacyOwner()==null&&user.id==AuthConfig.LEGACY_OWNER_ID
    suspend fun activate(user:AuthUser)=mutex.withLock {
        user.validate();check(auth.state.value.user?.id==user.id)
        check(!needsClaim(user)){"Confirme a vinculação dos dados anteriores."}
        if(active?.user?.id!=user.id){active?.shutdown();active=AccountWorkspace(base,user,auth.legacyOwner()==user.id)}
        current()
    }
    suspend fun lock()=mutex.withLock{active?.shutdown();active=null;withContext(Dispatchers.Main.immediate){AccountClipboard.clear(base)}}
    /** Called only after the system credential result and explicit owner/backup consent. No row is reassigned. */
    suspend fun claim(user:AuthUser,backupUri:Uri,password:CharArray)=mutex.withLock {
        check(auth.state.value.user?.id==user.id&&needsClaim(user))
        check(user.id==AuthConfig.LEGACY_OWNER_ID)
        withContext(Dispatchers.IO) {
            val old=AccountWorkspace(base,user,true)
            try {
                val before=old.backup.snapshot();val bytes=BackupCodec.encode(before,BuildConfig.VERSION_CODE,InstallationIdentity.certificate(base))
                requireNotNull(base.contentResolver.openOutputStream(backupUri,"wt")).use{AccountBackup.write(it,bytes,user.id,password);it.flush()}
                val saved=requireNotNull(base.contentResolver.openInputStream(backupUri)).use{AccountBackup.read(it)}
                check(AccountBackup.open(saved,user.id,password).contentEquals(bytes)){"Não foi possível conferir o backup. A vinculação não foi realizada."}
                check(old.backup.snapshot()==before)
                auth.bindLegacy(user) // Durable atomic metadata only; original Room IDs/file remain intact.
            } finally{old.shutdown();password.fill('\u0000')}
        }
    }
}
