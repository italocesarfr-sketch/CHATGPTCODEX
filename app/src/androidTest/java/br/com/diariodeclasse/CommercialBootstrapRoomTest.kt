package br.com.diariodeclasse

import android.content.Context
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.sync.*
import br.com.diariodeclasse.sync.academic.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

/** Real independent Room stores; transport double only, not a hosted or HTTP proof. */
class CommercialBootstrapRoomTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private val owner="11111111-1111-4111-8111-111111111111"
    private lateinit var db:DiaryDatabase
    private lateinit var name:String
    private val store get()=AcademicStore(db,owner)
    private val repo get()=DiaryRepository(db)
    private class Remote:AcademicRemote {
        val core=PhaseBRoomTest.Remote().apply{scope=5}
        var ensures=0;var initialized=false;var loseInitialization=false
        override suspend fun ensureE(token:String):AcademicEnrollment {
            ensures++;initialized=true
            if(loseInitialization){loseInitialization=false;throw SyncFailure("NETWORK")}
            return commercialStatus(token)
        }
        override suspend fun commercialStatus(token:String)=AcademicEnrollment(true,initialized,if(initialized)"READY"else"NEW",core.pages.size.toLong())
        override suspend fun status(token:String)=core.status(token).copy(testAllowed=false)
        override suspend fun activate(token:String,expected:AcademicProtocolStatus):AcademicProtocolStatus=error("manual activation forbidden")
        override suspend fun push(token:String,batch:AcademicBatch)=error("old protocol forbidden")
        override suspend fun pull(token:String,after:Long)=error("old protocol forbidden")
        override suspend fun pushE(token:String,batch:AcademicBatch,admitted:()->Boolean)=core.push(token,batch)
        override suspend fun pullE(token:String,after:Long,offset:Int):List<AcademicFragment> {
            if(core.fail)throw SyncFailure("NETWORK")
            val p=core.pages.firstOrNull{it.sequence>after} ?: return emptyList()
            return listOf(AcademicFragment(p.operation,p.sequence,p.records.size,offset,p.records.drop(offset).take(128)))
        }
    }
    private fun engine(remote:AcademicRemote,target:AcademicStore=store)=AcademicEngine(target,remote,{owner},{true})
    private suspend fun snapshot(target:DiaryDatabase=db)=ClassLifecycle.snapshot(DiaryRepository(target).readSnapshot()).canonical()
    @Before fun open(){name="commercial-${newId()}.db";db=DiaryDatabase.open(context,name)}
    @After fun close(){db.close();context.deleteDatabase(name)}

    @Test fun newEmptyAccountInitializesWithoutManualActivation():Unit=runBlocking {
        val remote=Remote();assertTrue(engine(remote).initializeCommercialE());engine(remote).run()
        assertEquals("ACTIVE_E",store.stage());assertEquals(1,remote.ensures)
        assertTrue(store.dao.states().isEmpty());assertEquals(0,remote.core.pushes)
    }
    @Test fun offlineCaptureSurvivesReopenAndLostInitializationResponse():Unit=runBlocking {
        assertTrue(store.prepareCommercialBootstrap())
        PhaseDFixture.insert(db,PhaseDFixture.read());val before=snapshot()
        assertEquals(15,store.dao.states().map{it.kind}.toSet().size)
        val remote=Remote().apply{loseInitialization=true}
        try{engine(remote).initializeCommercialE();fail()}catch(e:SyncFailure){assertEquals("NETWORK",e.code)}
        assertEquals("BOOTSTRAP_E",store.stage());assertEquals(before,snapshot())
        db.close();db=DiaryDatabase.open(context,name)
        engine(remote).initializeCommercialE();engine(remote).run()
        assertEquals(before,snapshot());assertEquals(15,remote.core.rows.keys.map{it.kind}.toSet().size)
        assertTrue(store.dao.pending().isEmpty());assertEquals(1,remote.core.pages.size)
    }
    @Test fun independentRoomRecoversBeforeUploadThenEditsFlowBack():Unit=runBlocking {
        val remote=Remote();engine(remote).initializeCommercialE();PhaseDFixture.insert(db,PhaseDFixture.read());engine(remote).run()
        val secondName="commercial-second-${newId()}.db";val second=DiaryDatabase.open(context,secondName)
        try {
            val other=AcademicStore(second,owner);engine(remote,other).initializeCommercialE();engine(remote,other).run()
            assertEquals(snapshot(),snapshot(second));assertEquals(1,remote.core.pushes)
            val record=snapshot(second).lessonRecords.first().copy(description="Edição fictícia no segundo dispositivo")
            second.dao().save(record);engine(remote,other).run();engine(remote).run()
            assertEquals(snapshot(),snapshot(second));assertEquals(record,snapshot().lessonRecords.first{it.id==record.id})
            assertTrue(other.dao.incoming().isEmpty());assertTrue(store.dao.pending().isEmpty())
        }finally{second.close();context.deleteDatabase(secondName)}
    }
    @Test fun orphanQueuesAndOldV1ControlsCannotTriggerEnsure():Unit=runBlocking {
        val remote=Remote();store.dao.insert(AcademicPending("saved",owner,"immutable","[]",0))
        try{engine(remote).initializeCommercialE();fail()}catch(e:SyncFailure){assertEquals("REVIEW",e.code)}
        assertEquals(0,remote.ensures);assertEquals("immutable",store.pending()!!.body)
        store.dao.acknowledge("saved");db.syncDao().save(SyncControl(owner=owner))
        try{engine(remote).initializeCommercialE();fail()}catch(e:SyncFailure){assertEquals("REVIEW",e.code)}
        assertEquals(0,remote.ensures)
    }
    @Test fun restorationDuringNetworkWaitCannotBeOverwritten():Unit=runBlocking {
        val base=Remote()
        val remote=object:AcademicRemote by base {
            override suspend fun ensureE(token:String):AcademicEnrollment {
                store.dao.save(AcademicControl(owner=owner,stage="RESTORE",message="RESTORE"))
                return AcademicEnrollment(true,true,"READY")
            }
        }
        try{engine(remote).initializeCommercialE();fail()}catch(_:IllegalStateException){}
        assertEquals("RESTORE",store.stage());assertFalse(store.active())
    }
    @Test fun oldPreparedScopesAreNotCommercialConsents():Unit=runBlocking {
        val remote=Remote()
        for(stage in listOf("PREPARED","PREPARED_C","PREPARED_D","PREPARED_E","RESTORE","BLOCKED")) {
            val old=AcademicControl(owner=owner,stage=stage,enabled=false,message="preserved")
            store.dao.save(old)
            try{engine(remote).initializeCommercialE();fail()}catch(e:SyncFailure){assertEquals("REVIEW",e.code)}
            assertEquals(old,store.dao.control())
        }
        assertEquals(0,remote.ensures)
    }
    @Test fun bootstrapWriteAndDirtyCounterRollbackTogether():Unit=runBlocking {
        store.prepareCommercialBootstrap()
        try{db.withTransaction{repo.saveClass(SchoolClass("rollback",name="Fictícia",subject="História",year=2026),"2026-01-01");error("rollback")};fail()}
        catch(_:IllegalStateException){}
        assertTrue(snapshot().classes.isEmpty());assertTrue(store.dao.states().isEmpty())
    }
}
