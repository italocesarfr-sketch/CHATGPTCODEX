package br.com.diariodeclasse

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import br.com.diariodeclasse.auth.AccountBackup
import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.sync.SyncFailure
import br.com.diariodeclasse.sync.academic.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Same Room prepare/activate/engine functions as the consent UI; real disposable PostgREST. */
class BTransitionNetworkTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private val b="99999999-9999-4999-8999-999999999999"
    private val a="88888888-8888-4888-8888-888888888888"
    private var stage="START"
    private val names=mutableListOf<String>();private val databases=mutableListOf<DiaryDatabase>()
    private fun open(name:String="bt-${newId()}.db"):DiaryDatabase {if(name !in names)names+=name;return DiaryDatabase.open(context,name).also{databases+=it}}
    private suspend fun snapshot(db:DiaryDatabase)=ClassLifecycle.snapshot(DiaryRepository(db).readSnapshot()).canonical()
    private suspend fun rows(db:DiaryDatabase)=AcademicRecords.rows(snapshot(db))
    @After fun clean(){
        val report=File(context.filesDir,"b-transition-result.json")
        if(!report.exists())report.writeText(buildJsonObject{put("case",JsonPrimitive("B_TRANSITION_LOCAL"));put("passed",JsonPrimitive(false));put("stage",JsonPrimitive(stage))}.toString())
        databases.forEach{runCatching{it.close()}};names.forEach{context.deleteDatabase(it)}} // Only generated fixture paths.
    @Test fun sequentialConsentsPreserveDataAndBlockUnsafeExpansion():Unit=runBlocking {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("bTransitionLocal")=="YES")
        check(BuildConfig.DEBUG&&!BuildConfig.HOMOLOGACAO)
        val token=PhaseBNetworkHarness.token(other=true);val raw=PhaseBNetworkHarness.raw()
        val mutex=Mutex();var checks=0
        suspend fun blocked(code:String,block:suspend()->Unit){try{block();fail("Expected guard")}catch(e:BTransitionBlocked){assertEquals(code,e.code);checks++}}
        suspend fun bootstrap(db:DiaryDatabase):BTransitionRemote {
            val gate=BTransitionRemote(raw,token);val store=AcademicStore(db,b);val engine=AcademicEngine(store,gate,{token},{true})
            engine.activate();engine.run();assertEquals(1L,store.cursor());assertEquals(41,rows(db).size)
            assertEquals(0,gate.academicWrites);assertTrue(store.dao.pendingHeaders().isEmpty());return gate
        }
        // Dirty, stale, conflicted and legacy clients must be rejected without changing their rows.
        stage="DIRTY"
        val dirty=open();val dirtyGate=bootstrap(dirty);val dirtyStore=AcademicStore(dirty,b)
        dirty.dao().insertRecord(LessonRecord("bt-local-journal","b-transition-class","2026-08-10","Local","Conteúdo fictício ainda não enviado"))
        val before=snapshot(dirty)
        blocked("UNSYNCED_LOCAL"){dirtyGate.arm(dirty,b,b,3,true)};assertEquals(before,snapshot(dirty));assertEquals(2,raw.status(token).academicScope)
        stage="PENDING"
        PhaseDFixture.insert(dirty,PhaseDFixture.read());val allBefore=snapshot(dirty);val pending=dirtyStore.prepare()!!
        blocked("QUEUES"){dirtyGate.arm(dirty,b,b,3,true)};assertEquals(allBefore,snapshot(dirty));assertEquals(pending,dirtyStore.pending())
        assertEquals(15,rows(dirty).map{it.kind}.toSet().size)
        stage="GUARDS"
        val dbName="bt-clean-${newId()}.db";var db=open(dbName);var gate=bootstrap(db)
        val baseline=rows(db);val oldClient=open();val oldGate=bootstrap(oldClient);val oldSnapshot=snapshot(oldClient)
        blocked("CONSENT"){gate.arm(db,b,b,3,false)}
        blocked("OWNER"){gate.arm(db,a,b,3,true)}
        blocked("OWNER"){gate.status(PhaseBNetworkHarness.token())}
        blocked("LOCAL_STAGE"){gate.arm(db,b,b,5,true)}
        val dao=db.academicDao();val control=dao.control()!!
        dao.save(control.copy(message="CONFLICT"));blocked("CONFLICT_OR_RESTORE"){gate.arm(db,b,b,3,true)};dao.save(control)
        dao.save(control.copy(stage="RESTORE"));blocked("LOCAL_STAGE"){gate.arm(db,b,b,3,true)};dao.save(control)
        dao.save(control.copy(cursor=0));blocked("CURSOR"){gate.arm(db,b,b,3,true)};dao.save(control)
        val state=dao.states().first();dao.save(state.copy(owner=a));blocked("OWNER"){gate.arm(db,b,b,3,true)};dao.save(state)
        dao.save(state.copy(localVersion=state.localVersion+1));blocked("UNACKNOWLEDGED"){gate.arm(db,b,b,3,true)};dao.save(state)
        dao.save(state.copy(revision=state.revision+1));blocked("REVISION"){gate.arm(db,b,b,3,true)};dao.save(state)
        val receipt=dao.applied(1)!!
        db.openHelper.writableDatabase.execSQL("UPDATE sync_academic_applied SET digest=? WHERE sequence=1",arrayOf("invalid"))
        blocked("RECEIPTS"){gate.arm(db,b,b,3,true)}
        db.openHelper.writableDatabase.execSQL("UPDATE sync_academic_applied SET digest=? WHERE sequence=1",arrayOf(receipt.digest))
        stage="CAS"
        // Prove server CAS, not a client sleep: real RPC rejects a stale expected head.
        val stale=object:AcademicRemote by raw {override suspend fun enableC(token:String,expectedSequence:Long)=raw.enableC(token,expectedSequence+1)}
        val staleDb=open();val staleGate=bootstrap(staleDb);val checkedStale=BTransitionRemote(stale,token)
        checkedStale.arm(staleDb,b,b,3,true)
        try{AcademicEngine(AcademicStore(staleDb,b),checkedStale,{token},{true}).activateC();fail()}catch(_:SyncFailure){checks++}
        assertEquals(2,raw.status(token).academicScope);assertEquals("PREPARED_C",staleDb.academicDao().control()!!.stage);assertEquals(baseline,rows(staleDb))
        assertEquals(0,staleGate.academicWrites)
        // Verify a safety copy BEFORE the first remote expansion; never restore over another workspace.
        suspend fun backup() {
            val encoded=BackupCodec.encode(snapshot(db),BuildConfig.VERSION_CODE,"0".repeat(64))
            val password="Fictitious-B-Transition-2026".toCharArray();val output=java.io.ByteArrayOutputStream()
            try {
                AccountBackup.write(output,encoded,b,password);val envelope=AccountBackup.read(output.toByteArray().inputStream())
                val decoded=AccountBackup.open(envelope,b,password);assertArrayEquals(encoded,decoded);assertEquals(snapshot(db),BackupCodec.decode(decoded).snapshot.canonical())
                try{AccountBackup.open(envelope,a,password);fail()}catch(_:Exception){checks++}
            }finally{password.fill('\u0000')}
        }
        backup()
        // C response is lost after the server commits. Durable consent survives DB reopening.
        stage="LOST_REPLY"
        var lose=true
        val lost=object:AcademicRemote by raw {override suspend fun enableC(token:String,expectedSequence:Long):AcademicProtocolStatus {
            val result=raw.enableC(token,expectedSequence);if(lose){lose=false;throw SyncFailure("NETWORK")};return result}}
        gate=BTransitionRemote(lost,token)
        mutex.withLock {
            gate.arm(db,b,b,3,true)
            try{AcademicEngine(AcademicStore(db,b),gate,{token},{true}).activateC();fail()}catch(_:SyncFailure){checks++}
        }
        assertEquals(3,raw.status(token).academicScope);assertEquals("PREPARED_C",db.academicDao().control()!!.stage)
        db.close();db=open(dbName);gate=BTransitionRemote(raw,token)
        stage="RESUME"
        val scopes=mutableListOf<Int>()
        for(target in 3..5)mutex.withLock {
            // The unmodified encrypted logical backup is verified before each consent.
            backup()
            gate.arm(db,b,b,target,true)
            val engine=AcademicEngine(AcademicStore(db,b),gate,{token},{true})
            when(target){3->engine.activateC();4->engine.activateD();5->engine.activateE()};gate.disarm();engine.run()
            val s=raw.status(token);assertEquals(target,s.academicScope);assertEquals(1L,s.academicSequence);scopes+=target
            val c=db.academicDao().control()!!;assertEquals(1L,c.cursor);assertTrue(c.checked);assertEquals("",c.message)
            assertEquals(baseline,rows(db));assertTrue(db.academicDao().pendingHeaders().isEmpty());assertTrue(db.academicDao().incomingHeaders().isEmpty())
            assertTrue(db.academicDao().states().all{it.owner==b&&it.localVersion==it.acknowledged})
        }
        assertEquals(listOf(4,5),gate.scopes) // C resume did not resend the confirmed server operation.
        assertEquals(0,gate.academicWrites)
        blocked("REMOTE_SCOPE"){oldGate.arm(oldClient,b,b,3,true)};assertEquals(oldSnapshot,snapshot(oldClient))
        stage="FINAL_VERIFY"
        val inventory=p1Inventory(H05ReadOnlyRemote(gate),token);assertEquals(1L,inventory.head)
        assertEquals(baseline.associateBy{it.identity},inventory.rows().associateBy{it.identity})
        for(page in inventory.pages){val receipt=db.academicDao().applied(page.sequence)!!;assertEquals(page.operation,receipt.operation)
            assertEquals(BackupCodec.hash(AcademicRecords.canonical(AcademicRecords.json.encodeToJsonElement(AcademicPage.serializer(),page)).toByteArray()),receipt.digest)}
        File(context.filesDir,"b-transition-result.json").writeText(buildJsonObject {
            put("case",JsonPrimitive("B_TRANSITION_LOCAL"));put("passed",JsonPrimitive(true));put("hosted_requests",JsonPrimitive(false))
            put("records",JsonPrimitive(baseline.size));put("sequence",JsonPrimitive(inventory.head));put("hash",JsonPrimitive(h05RowsHash(baseline)))
            put("scopes",JsonArray(scopes.map{JsonPrimitive(it)}));put("negative_checks",JsonPrimitive(checks));put("academic_writes",JsonPrimitive(gate.academicWrites))
            for(k in listOf("pending_preserved","all_15_dirty_types_preserved","conflict_restore_owner_blocked","backup_verified","receipts_revisions_equal","lost_reply_reopen_verified","old_client_preserved_blocked"))put(k,JsonPrimitive(true))
        }.toString())
    }
}
