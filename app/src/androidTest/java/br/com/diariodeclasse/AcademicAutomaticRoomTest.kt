package br.com.diariodeclasse

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.sync.*
import br.com.diariodeclasse.sync.academic.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

/** Durable bootstrap guards. The two-AVD probe supplies real HTTP/lifecycle evidence. */
class AcademicAutomaticRoomTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private val owner="11111111-1111-4111-8111-111111111111"
    private lateinit var name:String
    private lateinit var db:DiaryDatabase
    private val store get()=AcademicStore(db,owner)
    @Before fun open(){name="auto-${newId()}.db";db=DiaryDatabase.open(context,name)}
    @After fun close(){db.close();context.deleteDatabase(name)}
    private fun engine(remote:AcademicRemote)=AcademicEngine(store,remote,{owner},{true})
    @Test fun adoptsOnlyAlreadyAuthorizedEWithoutAnyRemoteActivation():Unit=runBlocking {
        val base=PhaseBRoomTest.Remote().apply{scope=5}
        val remote=object:AcademicRemote by base {
            override suspend fun activate(token:String,expected:AcademicProtocolStatus):AcademicProtocolStatus=error("must not activate remotely")
            override suspend fun enableE(token:String,expectedSequence:Long):AcademicProtocolStatus=error("must not expand remotely")
        }
        assertTrue(engine(remote).recoverAuthorizedE());assertEquals("ACTIVE_E",store.stage())
        assertFalse(engine(remote).recoverAuthorizedE());assertEquals(0,base.pushes)
        assertTrue(store.dao.states().isEmpty());assertTrue(store.dao.pending().isEmpty())
    }
    @Test fun refusesUnapprovedScopeAndUnauthorizedAccountWithoutChangingRoom():Unit=runBlocking {
        val base=PhaseBRoomTest.Remote()
        assertFalse(engine(base).recoverAuthorizedE());assertNull(store.stage())
        val unauthorized=object:AcademicRemote by base {
            override suspend fun status(token:String)=base.status(token).copy(academicScope=5,testAllowed=false)
        }
        assertFalse(engine(unauthorized).recoverAuthorizedE());assertNull(store.stage())
    }
    @Test fun pauseRestoreAndPreparedConsentAreNeverReplaced():Unit=runBlocking {
        val remote=PhaseBRoomTest.Remote().apply{scope=5}
        for(stage in listOf("RESTORE","BLOCKED","PREPARED","ACTIVE_E")) {
            val old=AcademicControl(owner=owner,stage=stage,enabled=false,cursor=2,message="CONFLICT")
            store.dao.save(old);assertFalse(engine(remote).recoverAuthorizedE());assertEquals(old,store.dao.control())
        }
    }
    @Test fun legacyV1MetadataAndOrphanedAcademicOutboxBlockAutomaticAdoption():Unit=runBlocking {
        val remote=PhaseBRoomTest.Remote().apply{scope=5}
        store.dao.insert(AcademicPending("frozen",owner,"immutable","versions",1))
        assertFalse(engine(remote).recoverAuthorizedE());assertEquals("immutable",store.pending()!!.body)
        store.dao.acknowledge("frozen")
        db.syncDao().save(SyncControl(owner=owner))
        assertFalse(engine(remote).recoverAuthorizedE());assertNull(store.stage())
    }
    @Test fun accountBoundOfflineDataIsSeededWithoutInventingDeletions():Unit=runBlocking {
        val school=SchoolClass("account-school",name="Fictícia",subject="História",year=2026)
        DiaryRepository(db).saveClass(school,"2026-01-01")
        val remote=PhaseBRoomTest.Remote().apply{scope=5}
        assertTrue(engine(remote).recoverAuthorizedE())
        val batch=AcademicRecords.json.decodeFromString<AcademicBatch>(store.prepare()!!.body)
        assertEquals(5,batch.changes.size);assertTrue(batch.changes.all{it.payload!=null&&it.base==0L})
        db.close();db=DiaryDatabase.open(context,name)
        assertEquals(batch.operation,store.prepare()!!.id);assertEquals(school,db.dao().schoolClass(school.id))
    }
}
