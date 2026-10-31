package br.com.diariodeclasse

import br.com.diariodeclasse.sync.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Exercises the actual engine. The queue double models Room's persistent pause flag. */
class ClassSyncPauseTest {
    private class Queue:ClassSyncQueue {
        var active=true;var preparing:suspend()->Unit={};var attempting:suspend()->Unit={}
        val pending=mutableListOf(
            SyncOperation("operation-a","class-a","fictitious-owner",1,0,"DELETE","",1),
            SyncOperation("operation-b","class-b","fictitious-owner",1,0,"DELETE","",2))
        var acknowledgements=0;var received=0;var checked=false
        override suspend fun enabled()=active
        override suspend fun prepare()=pending.toList().also{preparing()}
        override suspend fun attempt(operation:SyncOperation){attempting()}
        override suspend fun accept(operation:SyncOperation,reply:PushReply){if(active){pending.removeAll{it.id==operation.id};acknowledgements++}}
        override suspend fun cursor()=0L
        override suspend fun receive(records:List<RemoteClass>){if(active)received++}
        override suspend fun message(code:String,checked:Boolean){this.checked=checked}
    }
    private class Remote(val queue:Queue):ClassSyncRemote {
        var pushes=0;var pulls=0;var pauseOnPush=false
        override suspend fun push(token:String,operation:SyncOperation):PushReply {
            pushes++;if(pauseOnPush)queue.active=false
            return PushReply(operation.id,true,RemoteClass(operation.classId,1,1,true,""))
        }
        override suspend fun pull(token:String,after:Long):List<RemoteClass>{pulls++;return emptyList()}
    }
    @Test fun pauseDuringFirstUploadStopsTheRemainingBatchAndKeepsBothOperations():Unit=runBlocking {
        val queue=Queue();val remote=Remote(queue).apply{pauseOnPush=true}
        ClassSyncEngine(queue,remote,{"fictitious-token"},{true}).run()
        assertEquals(1,remote.pushes);assertEquals(0,remote.pulls)
        assertEquals(2,queue.pending.size);assertEquals(0,queue.acknowledgements);assertFalse(queue.checked)
    }
    @Test fun pauseDuringPreparationDoesNotStartAnyNetworkRequest():Unit=runBlocking {
        val queue=Queue();queue.preparing={queue.active=false};val remote=Remote(queue)
        ClassSyncEngine(queue,remote,{"fictitious-token"},{true}).run()
        assertEquals(0,remote.pushes);assertEquals(0,remote.pulls);assertEquals(2,queue.pending.size)
    }
    @Test fun pauseWhileRecordingAttemptDoesNotStartUpload():Unit=runBlocking {
        val queue=Queue();queue.attempting={queue.active=false};val remote=Remote(queue)
        ClassSyncEngine(queue,remote,{"fictitious-token"},{true}).run()
        assertEquals(0,remote.pushes);assertEquals(0,remote.pulls);assertEquals(2,queue.pending.size)
    }
    @Test fun pauseDuringSessionVerificationStopsPreparationAndPull():Unit=runBlocking {
        val queue=Queue();val remote=Remote(queue)
        ClassSyncEngine(queue,remote,{queue.active=false;"fictitious-token"},{true}).run()
        assertEquals(0,remote.pushes);assertEquals(0,remote.pulls);assertFalse(queue.checked)
    }
}
