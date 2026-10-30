package br.com.diariodeclasse

import androidx.room.withTransaction
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.sync.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID

/** Real Room and engine; deterministic in-memory transport. This is NOT a real Supabase test. */
class ClassSyncInstrumentedTest {
    @get:Rule val helper=MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),DiaryDatabase::class.java.canonicalName!!,FrameworkSQLiteOpenHelperFactory())
    private val context=ApplicationProvider.getApplicationContext<android.content.Context>()
    private val owner="11111111-1111-4111-8111-111111111111"
    private lateinit var db:DiaryDatabase
    private lateinit var repo:DiaryRepository
    private lateinit var store:ClassSyncStore
    private lateinit var name:String
    private val c=SchoolClass("sync-fictitious",name="Turma Fictícia",subject="História",year=2026)
    private class Remote:ClassSyncRemote {
        var sequence=0L;var failAfterCommit=false;var deny=false
        val records=mutableMapOf<Pair<String,String>,RemoteClass>()
        val receipts=mutableMapOf<Pair<String,String>,PushReply>()
        override suspend fun push(token:String,operation:SyncOperation):PushReply {
            if(deny)throw SyncFailure("SESSION")
            val key=token to operation.classId
            val result=receipts.getOrPut(token to operation.id) {
                val current=records[key]
                if((current?.revision ?: 0L)!=operation.baseRevision)PushReply(operation.id,false,requireNotNull(current))
                else PushReply(operation.id,true,RemoteClass(operation.classId,operation.baseRevision+1,++sequence,operation.kind=="DELETE",operation.payload)).also{records[key]=it.record}
            }
            if(failAfterCommit){failAfterCommit=false;throw SyncFailure("NETWORK")}
            return result
        }
        override suspend fun pull(token:String,after:Long):List<RemoteClass> {
            if(deny)throw SyncFailure("SESSION")
            return records.filterKeys{it.first==token}.values.filter{it.sequence>after}.sortedBy{it.sequence}.take(100)
        }
    }
    @Before fun open(){name="sync-${UUID.randomUUID()}.db";db=DiaryDatabase.open(context,name);repo=DiaryRepository(db);store=ClassSyncStore(db,owner)}
    @After fun close(){db.close();context.deleteDatabase(name)}
    private suspend fun seed(){repo.saveClass(c,"2026-01-01");store.enable();store.enroll(c.id)}
    private suspend fun run(remote:Remote,token:String=owner)=ClassSyncEngine(store,remote,{token},{true}).run()
    private suspend fun rejects(block:suspend()->Unit){try{block();fail("Esperava bloqueio")}catch(_:IllegalArgumentException){}catch(_:IllegalStateException){}}
    @Test fun noHistoricalUploadWithoutExplicitEnrollment():Unit=runBlocking {
        repo.saveClass(c,"2026-01-01");assertTrue(store.prepare().isEmpty());store.enable();assertTrue(store.prepare().isEmpty())
        store.enroll(c.id);val op=store.prepare().single();assertEquals(c.id,op.classId);assertEquals(4,ClassBundle.decode(op.payload).terms.size)
    }
    @Test fun classAndTermsAreQueuedInsideTheAcademicTransactionAndRollbackTogether():Unit=runBlocking {
        seed();val before=db.syncDao().states()
        rejects{db.withTransaction{repo.saveClass(c.copy(name="Não gravar"),"2026-01-01");error("forced")}}
        assertEquals(c,repo.dao.schoolClass(c.id));assertEquals(before,db.syncDao().states())
        repo.saveTerms(c.id,repo.dao.periods(c.id));assertTrue(db.syncDao().state(c.id)!!.localVersion>before.single().localVersion)
    }
    @Test fun lostAcknowledgementRetriesSameIdWithoutDuplicateRemoteRecord():Unit=runBlocking {
        seed();val remote=Remote();val op=store.prepare().single();remote.failAfterCommit=true;run(remote)
        assertEquals(op.id,store.prepare().single().id);assertEquals(0,db.syncDao().state(c.id)!!.acknowledged)
        run(remote);assertTrue(store.prepare().isEmpty());assertEquals(1,remote.records.size);assertEquals(1,remote.receipts.size)
    }
    @Test fun newerLocalEditDuringUploadIsNotAcknowledgedAsSynchronized():Unit=runBlocking {
        seed();val op=store.prepare().single();repo.saveClass(c.copy(name="Edição posterior"),"2026-01-01")
        val reply=Remote().push(owner,op);store.accept(op,reply)
        val next=store.prepare().single();assertNotEquals(op.id,next.id);assertEquals(1,next.baseRevision)
        assertEquals("Edição posterior",ClassBundle.decode(next.payload).school.name)
    }
    @Test fun sameAccountRetrievesClassInASecondDatabaseButOtherAccountCannot():Unit=runBlocking {
        seed();val remote=Remote();run(remote)
        assertTrue(remote.pull("22222222-2222-4222-8222-222222222222",0).isEmpty())
        val otherName="second-${UUID.randomUUID()}.db";val second=DiaryDatabase.open(context,otherName)
        try {val receiver=ClassSyncStore(second,owner);receiver.enable();ClassSyncEngine(receiver,remote,{owner},{true}).run()
            assertEquals(c,second.dao().schoolClass(c.id));assertEquals(4,second.dao().periods(c.id).size)
            assertTrue(receiver.prepare().isEmpty())
        }finally{second.close();context.deleteDatabase(otherName)}
    }
    @Test fun offlineAndInvalidCredentialsNeverAcknowledgeWork():Unit=runBlocking {
        seed();val remote=Remote();remote.deny=true;run(remote);assertEquals("SESSION",db.syncDao().control()!!.message);assertEquals(0,db.syncDao().state(c.id)!!.acknowledged)
        remote.deny=false;run(remote);assertEquals(1,db.syncDao().state(c.id)!!.acknowledged)
    }
    @Test fun concurrentDevicesPreserveBothVersionsAsConflict():Unit=runBlocking {
        seed();val remote=Remote();run(remote);repo.saveClass(c.copy(name="Edição local"),"2026-01-01")
        val other=SyncOperation(newId(),c.id,owner,2,1,"UPSERT",ClassBundle(c.copy(name="Edição remota"),repo.dao.periods(c.id)).encode(),1)
        remote.push(owner,other);run(remote)
        assertNotNull(db.syncDao().state(c.id)!!.conflict);assertEquals("Edição local",repo.dao.schoolClass(c.id)!!.name)
        assertEquals("Edição remota",ClassBundle.decode(remote.records[owner to c.id]!!.payload).school.name)
        assertTrue(store.prepare().isEmpty());assertEquals(1,db.syncDao().operations().size)
    }
    @Test fun pullCannotOverwriteAnUnenrolledLocalIdOrProtectedHistory():Unit=runBlocking {
        seed();val incoming=RemoteClass(c.id,1,1,false,ClassBundle(c.copy(name="Remota"),repo.dao.periods(c.id)).encode())
        store.receive(listOf(incoming));assertEquals(c,repo.dao.schoolClass(c.id));assertNotNull(db.syncDao().state(c.id)!!.conflict)
    }
    @Test fun collisionWithUnenrolledClassNeverOverwritesIt():Unit=runBlocking {
        repo.saveClass(c,"2026-01-01");store.enable()
        store.receive(listOf(RemoteClass(c.id,1,1,false,ClassBundle(c.copy(name="Outra origem"),repo.dao.periods(c.id)).encode())))
        assertEquals(c,repo.dao.schoolClass(c.id));assertNotNull(db.syncDao().state(c.id)!!.conflict)
    }
    @Test fun invalidPageRollsBackAcademicChangesAndCursorTogether():Unit=runBlocking {
        seed();val remote=Remote();run(remote);val before=repo.readSnapshot();val cursor=store.cursor()
        val valid=RemoteClass(c.id,2,2,false,ClassBundle(c.copy(name="Não confirmar"),repo.dao.periods(c.id)).encode())
        rejects{store.receive(listOf(valid,RemoteClass("wrong",0,3,true,"")))}
        assertEquals(before,repo.readSnapshot());assertEquals(cursor,store.cursor());assertFalse(db.syncDao().control()!!.applying)
    }
    @Test fun remoteAcademicSettingsCannotChangeAClassWithStudents():Unit=runBlocking {
        seed();val remote=Remote();run(remote);repo.saveStudent(Student("protected",classId=c.id,name="Fictício Protegido",number=1,joined="2026-01-01"))
        remote.push(owner,SyncOperation(newId(),c.id,owner,2,1,"UPSERT",ClassBundle(c.copy(scale=20.0),repo.dao.periods(c.id)).encode(),1))
        val before=repo.readSnapshot();run(remote);assertEquals(before,repo.readSnapshot());assertNotNull(db.syncDao().state(c.id)!!.conflict)
    }
    @Test fun lateResponseAfterPauseDoesNotAcknowledgeTheQueue():Unit=runBlocking {
        seed();val op=store.prepare().single();val reply=Remote().push(owner,op);store.pause();store.accept(op,reply)
        assertEquals(op.id,db.syncDao().operations().single().id);assertEquals(0,db.syncDao().state(c.id)!!.acknowledged)
    }
    @Test fun lossOfAdmissionDuringRequestKeepsOperationForIdempotentRetry():Unit=runBlocking {
        seed();var admitted=true;val remote=Remote()
        val wrapped=object:ClassSyncRemote by remote {
            override suspend fun push(token:String,operation:SyncOperation):PushReply=remote.push(token,operation).also{admitted=false}
        }
        ClassSyncEngine(store,wrapped,{owner},{admitted}).run()
        assertEquals(0,db.syncDao().state(c.id)!!.acknowledged);assertEquals(1,db.syncDao().operations().size)
        run(remote);assertTrue(db.syncDao().operations().isEmpty());assertEquals(1,remote.receipts.size)
    }
    @Test fun remoteDeletionDoesNotDeleteAcademicRecords():Unit=runBlocking {
        seed();val remote=Remote();run(remote)
        val s=Student("sync-student",classId=c.id,name="Aluno Fictício",number=1,joined="2026-01-01")
        repo.saveStudent(s);repo.setDayHours(c.id,"2026-10-01",1);repo.mark("${c.id}:2026-10-01:1",s.id,"FJ")
        val before=repo.readSnapshot();remote.push(owner,SyncOperation(newId(),c.id,owner,2,1,"DELETE","",1));run(remote)
        assertEquals(before,repo.readSnapshot());assertNotNull(db.syncDao().state(c.id)!!.conflict)
    }
    @Test fun remoteDeletionOfAnEmptyParticipatingClassIsNotAnEcho():Unit=runBlocking {
        seed();val remote=Remote();run(remote);remote.push(owner,SyncOperation(newId(),c.id,owner,2,1,"DELETE","",1));run(remote)
        assertNull(repo.dao.schoolClass(c.id));assertTrue(repo.dao.periods(c.id).isEmpty());assertTrue(store.prepare().isEmpty())
    }
    @Test fun localDeletionCreatesATombstoneAndKeepsExistingSchoolRules():Unit=runBlocking {
        seed();val remote=Remote();run(remote);val preview=repo.previewClassDeletion(c.id);repo.deleteClass(preview,preview.confirmation)
        val op=store.prepare().single();assertEquals("DELETE",op.kind);assertEquals(1,op.baseRevision);run(remote);assertTrue(remote.records[owner to c.id]!!.deleted)
    }
    @Test fun queueSurvivesDatabaseReopenWithSameOperationId():Unit=runBlocking {
        seed();val op=store.prepare().single();db.close();db=DiaryDatabase.open(context,name);repo=DiaryRepository(db);store=ClassSyncStore(db,owner)
        assertEquals(op,store.prepare().single());assertEquals(c,repo.dao.schoolClass(c.id))
    }
    @Test fun ownerCannotBeReassignedOnAnExistingQueue():Unit=runBlocking {
        seed();rejects{ClassSyncStore(db,"22222222-2222-4222-8222-222222222222").enable()};assertEquals(owner,db.syncDao().control()!!.owner)
    }
    @Test fun restorePreservesSchoolDataAndPausesOldCloudOperations():Unit=runBlocking {
        seed();val manager=BackupManager(db,FileSafetyBackupStore(File(context.cacheDir,"sync-backup-${UUID.randomUUID()}")),39,null)
        val before=manager.snapshot();val bytes=manager.backupBytes();val op=store.prepare().single()
        repo.saveClass(c.copy(name="Alterada"),"2026-01-01")
        manager.restore(manager.prepare(bytes.inputStream()),"RESTAURAR")
        assertEquals(before,manager.snapshot());assertFalse(store.enabled());assertEquals(op.id,db.syncDao().operations().single().id)
        assertTrue(store.prepare().isEmpty());assertEquals("RESTORE",db.syncDao().state(c.id)!!.conflict)
        rejects{store.enable()}
    }
    @Test fun migration10To11RetainsIdentifiersAndAcademicBackup():Unit=runBlocking {
        val oldName="sync-old-${UUID.randomUUID()}.db"
        helper.createDatabase(oldName,10).use{sql->sql.execSQL("INSERT INTO classes VALUES ('legacy-sync','Turma Fictícia','Médio','História',2026,'Matutino','0,0,0,0,0,0,0',50,10,5,7,1,0)")}
        helper.runMigrationsAndValidate(oldName,11,true,MIGRATION_10_11).close()
        val migrated=DiaryDatabase.open(context,oldName)
        try {assertEquals("legacy-sync",migrated.backupDao().classes().single().id);assertNull(migrated.syncDao().control());assertTrue(migrated.syncDao().operations().isEmpty())}
        finally{migrated.close();context.deleteDatabase(oldName)}
    }
}
