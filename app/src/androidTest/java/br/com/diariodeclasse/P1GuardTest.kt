package br.com.diariodeclasse

import androidx.test.platform.app.InstrumentationRegistry
import br.com.diariodeclasse.sync.academic.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
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
    @Test fun scopeFailureKeepsSanitizedPredicatesAndNeverPullsOrWrites():Unit=runBlocking {
        val statuses=listOf(
            AcademicProtocolStatus(1,0,JsonArray(emptyList()),true,5,0),
            AcademicProtocolStatus(2,0,JsonArray(emptyList()),true,4,0),
            AcademicProtocolStatus(2,0,JsonArray(emptyList()),false,5,0),
            AcademicProtocolStatus(99,0,JsonArray(emptyList()),true,99,0))
        for(status in statuses) {
            var pulls=0;var recorded:JsonObject?=null
            val remote=H05ReadOnlyRemote(object:AcademicRemote {
                override suspend fun status(token:String)=status
                override suspend fun pullE(token:String,after:Long,offset:Int):List<AcademicFragment>{pulls++;error("must not pull")}
                override suspend fun activate(token:String,expected:AcademicProtocolStatus):AcademicProtocolStatus=error("must not activate")
                override suspend fun push(token:String,batch:AcademicBatch):AcademicPushReply=error("must not push")
                override suspend fun pull(token:String,after:Long):List<AcademicPage> = error("must not pull")
            })
            try{p1Inventory(remote,"synthetic"){recorded=p1Eligibility(it)};fail()}catch(e:P1Stop){assertEquals("SCOPE",e.code)}
            assertEquals(p1Eligibility(status),recorded);assertEquals(false,recorded!!["ready"]!!.jsonPrimitive.boolean)
            assertEquals(setOf("protocol","scope","test_allowed","ready"),recorded!!.keys)
            assertEquals(0,pulls);assertEquals(0,remote.blockedWrites);assertEquals(1,remote.reads)
        }
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
