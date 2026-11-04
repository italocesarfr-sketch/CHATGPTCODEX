package br.com.diariodeclasse.sync.academic

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import br.com.diariodeclasse.BuildConfig
import br.com.diariodeclasse.DiaryApplication
import br.com.diariodeclasse.auth.AccountWorkspace
import br.com.diariodeclasse.auth.AuthConfig
import br.com.diariodeclasse.sync.SyncJobs
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.withLock

fun academicTestEligible(workspace:AccountWorkspace)=BuildConfig.DEBUG && !workspace.legacy && workspace.user.id!=AuthConfig.LEGACY_OWNER_ID

/** Foreground/connection/local changes converge on one mutex; JobScheduler is the durable fallback. */
class AcademicCoordinator(private val base:Context,private val workspace:AccountWorkspace,private val scope:CoroutineScope) {
    private val app=base.applicationContext as? DiaryApplication
    private val signals=Channel<Unit>(Channel.CONFLATED)
    private val network=base.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val callback=object:ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network:Network){request()}
        override fun onCapabilitiesChanged(network:Network,caps:NetworkCapabilities){if(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))request()}
        override fun onLost(network:Network){request()}
    }
    private var retry:Job?=null
    init {
        if(academicTestEligible(workspace)) {
            network.registerDefaultNetworkCallback(callback)
            scope.launch {
                var failures=0
                for(signal in signals) {
                    delay(250) // Coalesce a burst of table invalidations, never delay persistence.
                    val c=workspace.database.academicDao().control() ?: continue
                    val decisionPending=workspace.database.academicDao().decisions().any{it.phase in setOf("PENDING","CONFIRMED")}
                    if(c.stage !in listOf("ACTIVE","PREPARED","ACTIVE_C","PREPARED_C","ACTIVE_D","PREPARED_D","ACTIVE_E","PREPARED_E") && !(c.stage=="RESTORE"&&decisionPending) || c.stage in listOf("ACTIVE","ACTIVE_C","ACTIVE_D","ACTIVE_E")&&!c.enabled)continue
                    val application=app ?: continue
                    run(application,workspace)
                    val after=workspace.database.academicDao().control() ?: continue
                    if(after.message in setOf("NETWORK","RETRY","REMOTE","PENDING")) {
                        failures=(failures+1).coerceAtMost(6)
                        retry?.cancel();retry=scope.launch{delay((1000L shl failures).coerceAtMost(60_000));request()}
                        SyncJobs.soon(base)
                    } else failures=0
                }
            }
            request()
        }
    }
    fun request(){signals.trySend(Unit)}
    fun close(){if(academicTestEligible(workspace))runCatching{network.unregisterNetworkCallback(callback)};signals.close()}
    companion object {
        fun admitted(app:DiaryApplication,w:AccountWorkspace)=academicTestEligible(w)&&app.auth.canUse(w.user.id)&&
            runCatching{app.workspaces.current()===w}.getOrDefault(false)
        private fun online(context:Context):Boolean {
            val c=context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            return c.getNetworkCapabilities(c.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)==true
        }
        fun engine(app:DiaryApplication,w:AccountWorkspace)=AcademicEngine(AcademicStore(w.database,w.user.id),app.academicRemote(),
            {app.auth.syncToken(w.user.id)},{admitted(app,w)})
        suspend fun run(app:DiaryApplication,w:AccountWorkspace)=w.syncMutex.withLock {
            if(!admitted(app,w))return@withLock
            val store=AcademicStore(w.database,w.user.id)
            if(!online(w.context)){store.message("OFFLINE");return@withLock}
            if(store.stage() in listOf("PREPARED","PREPARED_C","PREPARED_D","PREPARED_E")) {
                try{if(store.stage()=="PREPARED_E")engine(app,w).activateE()else if(store.stage()=="PREPARED_D")engine(app,w).activateD()else if(store.stage()=="PREPARED_C")engine(app,w).activateC()else engine(app,w).activate()}catch(e:CancellationException){throw e}
                catch(e:Exception){store.message((e as? br.com.diariodeclasse.sync.SyncFailure)?.code ?: "RETRY");return@withLock}
            }
            engine(app,w).run()
        }
    }
}
