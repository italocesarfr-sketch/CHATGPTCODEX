package br.com.diariodeclasse.sync

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import br.com.diariodeclasse.DiaryApplication
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock

/** Native persistent scheduler: no additional dependency; retries stay in the account's Room DB. */
object SyncJobs {
    fun schedule(context:Context) {
        val scheduler=context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        if(scheduler.getPendingJob(2101)!=null)return
        scheduler.schedule(JobInfo.Builder(2101,ComponentName(context,ClassSyncJob::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true)
            .setPeriodic(15*60_000L).setBackoffCriteria(30_000L,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build())
    }
    fun soon(context:Context) {
        val scheduler=context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        scheduler.schedule(JobInfo.Builder(2102,ComponentName(context,ClassSyncJob::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true)
            .setMinimumLatency(1500).setBackoffCriteria(30_000L,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build())
    }
}
class ClassSyncJob:JobService() {
    private val tasks=mutableMapOf<Int,Job>()
    override fun onStartJob(params:JobParameters):Boolean {
        val app=application as DiaryApplication
        tasks[params.jobId]=app.authScope.launch {
            try {
                val state=app.auth.state.first{!it.initializing}
                val user=state.user ?: return@launch
                if(state.recovery || app.workspaces.needsClaim(user) || !app.auth.canUse(user.id))return@launch
                val workspace=app.workspaces.activate(user)
                val store=ClassSyncStore(workspace.database,user.id)
                workspace.syncMutex.withLock {
                    ClassSyncEngine(store,SupabaseClassSync(),{app.auth.syncToken(user.id)},
                        {app.auth.canUse(user.id) && runCatching{app.workspaces.current()===workspace}.getOrDefault(false)}).run()
                }
            }catch(e:CancellationException){throw e}
            catch(_:Exception){ /* Preserve queue; periodic retry. Never log tokens or academic payloads. */ }
            finally { jobFinished(params,false) }
        }
        return true
    }
    override fun onStopJob(params:JobParameters):Boolean {tasks.remove(params.jobId)?.cancel();return true}
}
