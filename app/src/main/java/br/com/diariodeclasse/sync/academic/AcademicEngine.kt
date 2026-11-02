package br.com.diariodeclasse.sync.academic

import br.com.diariodeclasse.sync.SyncFailure
import kotlinx.coroutines.CancellationException

class AcademicEngine(private val store:AcademicStore,private val remote:AcademicRemote,
    private val session:suspend()->String,private val admitted:()->Boolean) {
    /** Called only after explicit test consent. Resuming PREPARED never invents a fresh consent. */
    suspend fun activate() {
        check(admitted())
        val token=session();check(admitted())
        val status=remote.status(token);check(admitted())
        store.prepareConsent(status)
        val fenced=if(status.protocol==1)remote.activate(token,status)else status
        check(admitted());store.activate(fenced)
    }
    suspend fun run() {
        suspend fun allowed()=admitted()&&store.active()
        if(!allowed())return
        try {
            store.message("SYNCING")
            val token=session();if(!allowed())return
            repeat(12) {
                // Recovery first: a clean installation must not interpret remote records as deletions.
                var caughtUp=false
                for(pageTurn in 0 until 32) {
                    if(!allowed() || store.blocked())return
                    val pages=remote.pull(token,store.cursor());if(!allowed())return
                    require(pages.size<=1)
                    if(pages.isEmpty()){caughtUp=true;break}
                    if(!store.receive(pages.single()))return
                }
                if(!caughtUp){store.message("PENDING");return}
                if(!allowed())return
                val pending=store.prepare()
                if(pending==null){store.message("",checked=true);return}
                store.attempt(pending);if(!allowed())return
                val reply=remote.push(token,AcademicRecords.json.decodeFromString<AcademicBatch>(pending.body))
                if(!allowed())return
                store.accept(pending,reply)
                if(store.blocked())return
            }
            store.message("PENDING")
        }catch(e:CancellationException){throw e}
        catch(e:Exception){if(admitted())store.message((e as? SyncFailure)?.code ?: "RETRY")}
    }
}
