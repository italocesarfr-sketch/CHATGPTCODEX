package br.com.diariodeclasse

import br.com.diariodeclasse.sync.academic.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Test
import org.junit.Assert.*

class H12GuardTest {
    private val id="aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
    private fun decision(choice:AcademicChoice=AcademicChoice.KEEP_REMOTE,restore:Boolean=true,changes:List<AcademicChange> = emptyList()):AcademicDecision {
        val req=buildJsonObject{put("source",if(restore)"RESTORE"else"CONFLICT");put("choice",choice.name);put("hasChanges",changes.isNotEmpty())}
        val frozen=AcademicFrozenDecision("owner","context",restore,1,2,choice,changes,emptyList(),emptyList(),emptyList(),req,"hash",emptyList(),emptyList())
        return AcademicDecision(id,"owner",AcademicRecords.json.encodeToString(frozen),0)
    }
    @Test fun inheritedAcademicMutationsRemainBlocked():Unit=runBlocking {
        var calls=0
        val delegate=object:AcademicRemote by PhaseBRoomTest.Remote() {
            override suspend fun push(token:String,batch:AcademicBatch):AcademicPushReply{calls++;error("Must not reach transport")}
        }
        val reads=H05ReadOnlyRemote(delegate);val guard=H12ReconciliationRemote(reads,delegate)
        val batch=AcademicBatch(id,emptyList())
        val actions:List<suspend()->Unit> = listOf({guard.activate("fixture",delegate.status("fixture"))},
            {guard.enableC("fixture",0)},{guard.enableD("fixture",0)},{guard.enableE("fixture",0)},
            {guard.push("fixture",batch)},{guard.pushC("fixture",batch)},{guard.pushD("fixture",batch)},
            {guard.stageE("fixture",batch){true}},{guard.pushE("fixture",batch){true}})
        for(action in actions)try{action();fail()}catch(_:H05BlockedWrite){}
        assertEquals(9,reads.blockedWrites);assertEquals(0,calls)
    }
    @Test fun localChoiceConflictOrAcademicDeltaCannotBeAuthorized() {
        val delta=listOf(AcademicChange("classes",buildJsonObject{put("id","fixture")},1,null))
        for(d in listOf(decision(AcademicChoice.KEEP_LOCAL),decision(restore=false),decision(changes=delta))) {
            val delegate=PhaseBRoomTest.Remote();val guard=H12ReconciliationRemote(H05ReadOnlyRemote(delegate),delegate)
            try{guard.authorize(d);fail()}catch(_:IllegalStateException){}
            assertEquals(0,guard.decisionWrites)
        }
    }
    @Test fun onlyFrozenNoChangeRestoreDecisionCanReachTransport():Unit=runBlocking {
        var writes=0
        val delegate=object:AcademicRemote by PhaseBRoomTest.Remote() {
            override suspend fun resolveE(token:String,operation:String,request:JsonObject,cancel:Boolean):AcademicPushReply {
                writes++;return AcademicPushReply(operation,true,2,JsonArray(emptyList()))
            }
        }
        val guard=H12ReconciliationRemote(H05ReadOnlyRemote(delegate),delegate);val d=decision()
        val req=AcademicRecords.json.decodeFromString<AcademicFrozenDecision>(d.body).request
        val refused:List<suspend()->Unit> = listOf({guard.resolveE("fixture",id,req,false)},{guard.decisionE("fixture",id)})
        for(action in refused)try{action();fail()}catch(_:IllegalStateException){}
        guard.authorize(d)
        for(action in listOf<suspend()->Unit>({guard.resolveE("fixture",id,req,true)},
            {guard.resolveE("fixture","different",req,false)},
            {guard.resolveE("fixture",id,buildJsonObject{put("choice","KEEP_LOCAL")},false)}))try{action();fail()}catch(_:IllegalStateException){}
        assertEquals(0,writes)
        assertTrue(guard.resolveE("fixture",id,req,false).accepted);assertEquals(1,writes)
        try{guard.resolveE("fixture",id,req,false);fail()}catch(_:IllegalStateException){}
        assertEquals(1,writes)
    }
}
