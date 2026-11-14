package br.com.diariodeclasse

import androidx.test.platform.app.InstrumentationRegistry
import br.com.diariodeclasse.sync.academic.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import org.junit.*
import org.junit.Assert.*

class P1GuardTest {
    @Before fun local(){Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("p1Mode")=="LOCAL")}
    @Test fun sharedBudgetRejectsThe513thReadBeforeTransport() {
        val budget=P1ReadBudget();var transmitted=0
        repeat(512){budget.claim();transmitted++}
        try{budget.claim();transmitted++;fail()}catch(e:P1Stop){assertEquals("LIMIT",e.code)}
        assertEquals(512,transmitted);assertEquals(512,budget.size())
    }
    @Test fun reuseEveryH05MutationGuard(){H05ReadOnlyGuardTest().everyMutationIsRejectedBeforeReachingTheTransport()}
    @Test fun independentlyAuthorizedHostOnly() {
        try{AcademicTestApplication().configureP1("HOSTED",false,false);fail()}catch(_:IllegalArgumentException){}
    }
    @Test fun anEmptyAuthorizedBHasVerifiedZeroInventoryWithoutRelaxingH05():Unit=runBlocking {
        var pulls=0
        val remote=H05ReadOnlyRemote(object:AcademicRemote {
            override suspend fun status(token:String)=AcademicProtocolStatus(2,0,JsonArray(emptyList()),true,5,0)
            override suspend fun pullE(token:String,after:Long,offset:Int):List<AcademicFragment>{assertEquals(0L,after);assertEquals(0,offset);pulls++;return emptyList()}
            override suspend fun activate(token:String,expected:AcademicProtocolStatus):AcademicProtocolStatus=error("not allowed")
            override suspend fun push(token:String,batch:AcademicBatch):AcademicPushReply=error("not allowed")
            override suspend fun pull(token:String,after:Long):List<AcademicPage> = error("not allowed")
        })
        val inventory=p1Inventory(remote,"synthetic")
        assertEquals(0L,inventory.head);assertTrue(inventory.rows().isEmpty());assertTrue(inventory.pages.isEmpty());assertEquals(1,pulls);assertEquals(0,remote.blockedWrites)
        try{recoveryEligible(AcademicProtocolStatus(2,0,JsonArray(emptyList()),true,5,0));fail()}catch(_:IllegalStateException){}
    }
}
