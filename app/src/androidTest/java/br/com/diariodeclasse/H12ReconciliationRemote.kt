package br.com.diariodeclasse

import br.com.diariodeclasse.sync.academic.*
import kotlinx.serialization.json.*

/** Instrumentation only. Reads reuse H05; the only allowed write is one frozen RESTORE audit.
 * No staging, academic changes, activation, deletion or scope promotion can reach the server. */
internal class H12ReconciliationRemote(private val reads:H05ReadOnlyRemote,private val delegate:AcademicRemote):AcademicRemote by reads {
    var decisionWrites=0;private set
    var lostReplies=0;private set
    var loseNextReply=false
    private var allowed:AcademicDecision?=null
    private var request:JsonObject?=null
    fun authorize(decision:AcademicDecision) {
        check(allowed==null)
        val frozen=AcademicRecords.json.decodeFromString<AcademicFrozenDecision>(decision.body)
        check(frozen.restore&&frozen.choice==AcademicChoice.KEEP_REMOTE&&frozen.changes.isEmpty())
        check(frozen.request["source"]==JsonPrimitive("RESTORE")&&frozen.request["choice"]==JsonPrimitive("KEEP_REMOTE")&&frozen.request["hasChanges"]==JsonPrimitive(false))
        allowed=decision;request=frozen.request
    }
    override suspend fun decisionE(token:String,operation:String):AcademicPushReply? {
        check(operation==allowed?.id){"H12_DECISION_NOT_AUTHORIZED"}
        return delegate.decisionE(token,operation)
    }
    override suspend fun resolveE(token:String,operation:String,request:JsonObject,cancel:Boolean):AcademicPushReply {
        check(operation==allowed?.id&&request==this.request&&!cancel&&decisionWrites==0){"H12_DECISION_NOT_AUTHORIZED"}
        decisionWrites++
        val reply=delegate.resolveE(token,operation,request,false)
        if(loseNextReply) {
            check(!BuildConfig.HOMOLOGACAO){"H12_HOSTED_FAULT_FORBIDDEN"}
            loseNextReply=false;lostReplies++;throw br.com.diariodeclasse.sync.SyncFailure("NETWORK")
        }
        return reply
    }
}
