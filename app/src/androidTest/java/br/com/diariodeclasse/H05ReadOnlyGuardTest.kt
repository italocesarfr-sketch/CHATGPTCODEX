package br.com.diariodeclasse

import androidx.test.platform.app.InstrumentationRegistry
import br.com.diariodeclasse.sync.academic.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*

class H05ReadOnlyGuardTest {
    @Before fun localOnly(){Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("h05Mode")=="LOCAL")}
    @Test fun everyMutationIsRejectedBeforeReachingTheTransport():Unit=runBlocking {
        var writes=0
        val delegate=object:AcademicRemote {
            override suspend fun status(token:String)=AcademicProtocolStatus(2,0,JsonArray(emptyList()),true,5,0)
            override suspend fun pullE(token:String,after:Long,offset:Int)=emptyList<AcademicFragment>()
            override suspend fun activate(token:String,expected:AcademicProtocolStatus):AcademicProtocolStatus{writes++;error("must not delegate")}
            override suspend fun push(token:String,batch:AcademicBatch):AcademicPushReply{writes++;error("must not delegate")}
            override suspend fun pull(token:String,after:Long)=emptyList<AcademicPage>()
        }
        val remote=H05ReadOnlyRemote(delegate)
        assertEquals(5,remote.status("synthetic").academicScope);assertTrue(remote.pullE("synthetic",0,0).isEmpty())
        val batch=AcademicBatch("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",emptyList())
        val actions:List<suspend()->Unit> = listOf(
            {remote.activate("synthetic",remote.status("synthetic"))},
            {remote.enableC("synthetic",0)},{remote.enableD("synthetic",0)},{remote.enableE("synthetic",0)},
            {remote.push("synthetic",batch)},{remote.pushC("synthetic",batch)},{remote.pushD("synthetic",batch)},
            {remote.stageE("synthetic",batch){true}},{remote.pushE("synthetic",batch){true}},
            {remote.resolveE("synthetic","synthetic",buildJsonObject{},false)})
        for(action in actions)try{action();fail("Write was not blocked")}catch(_:H05BlockedWrite){}
        assertEquals(10,remote.blockedWrites);assertEquals(0,writes)
    }
    @Test fun typeSummaryKeepsPresenceAndHashesInsideEachType() {
        val rows=AcademicRecords.rows(PhaseDFixture.read())
        val summary=h05TypeSummary(rows,rows)
        assertEquals(15,summary.size)
        for(value in summary) {
            val r=value.jsonObject
            assertEquals(setOf("kind","local_count","remote_count","local_hash","remote_hash","present"),r.keys)
            assertTrue(r.getValue("present").jsonPrimitive.boolean)
            assertEquals(r.getValue("local_count"),r.getValue("remote_count"))
            assertEquals(r.getValue("local_hash"),r.getValue("remote_hash"))
        }
    }
    @Test fun hostedApplicationCannotBeConfiguredWithoutIndependentAuthorization() {
        try{H05RecoveryApplication().configure("HOSTED",false);fail("Missing authorization accepted")}
        catch(_:IllegalArgumentException){}
    }
}
