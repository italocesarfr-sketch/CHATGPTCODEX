package br.com.diariodeclasse

import android.content.Context
import androidx.room.withTransaction
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.sync.*
import br.com.diariodeclasse.sync.academic.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Real Room lifecycle/transactions; transport here is deterministic. Network proof is separate. */
class PhaseBRoomTest {
    @get:Rule val helper=MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),DiaryDatabase::class.java.canonicalName!!,FrameworkSQLiteOpenHelperFactory())
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private val owner="11111111-1111-4111-8111-111111111111"
    private lateinit var name:String;private lateinit var db:DiaryDatabase
    private val repo get()=DiaryRepository(db)
    private val store get()=AcademicStore(db,owner)
    private val c=SchoolClass("phaseb-class",name="Turma Fictícia",subject="História",year=2026)
    private val students=(1..36).map{Student("phaseb-$it","person-$it",c.id,"Estudante Fictício ${it.toString().padStart(2,'0')}",it,"2026-01-01")}
    internal class Remote:AcademicRemote {
        val rows=mutableMapOf<AcademicKey,AcademicVersion>();val pages=mutableListOf<AcademicPage>()
        val receipts=mutableMapOf<String,AcademicPushReply>();var fail=false;var lost=false;var pushes=0
        var scope=2
        override suspend fun status(token:String)=AcademicProtocolStatus(2,0,JsonArray(emptyList()),true,scope,pages.size.toLong())
        override suspend fun enableD(token:String,expectedSequence:Long):AcademicProtocolStatus{check(expectedSequence==pages.size.toLong());scope=4;return status(token)}
        override suspend fun pushD(token:String,batch:AcademicBatch)=push(token,batch)
        override suspend fun pullD(token:String,after:Long)=pull(token,after)
        override suspend fun enableC(token:String,expectedSequence:Long):AcademicProtocolStatus{check(expectedSequence==pages.size.toLong());scope=3;return status(token)}
        override suspend fun pushC(token:String,batch:AcademicBatch)=push(token,batch)
        override suspend fun pullC(token:String,after:Long)=pull(token,after)
        override suspend fun activate(token:String,expected:AcademicProtocolStatus)=status(token)
        override suspend fun pull(token:String,after:Long):List<AcademicPage>{if(fail)throw SyncFailure("NETWORK");return pages.filter{it.sequence>after}.take(1)}
        override suspend fun push(token:String,batch:AcademicBatch):AcademicPushReply {
            if(fail)throw SyncFailure("NETWORK");pushes++
            val reply=receipts.getOrPut(batch.operation) {
                val conflicts=batch.changes.filter{(rows[it.identity]?.revision ?: 0)!=it.base}
                if(conflicts.isNotEmpty())AcademicPushReply(batch.operation,false,null,JsonArray(conflicts.map{buildJsonObject{put("kind",it.kind);put("key",it.key);put("current",JsonNull)}}))
                else {
                    val values=batch.changes.map{AcademicVersion(it.kind,it.key,it.base+1,it.payload)}
                    values.forEach{rows[it.identity]=it};pages+=AcademicPage(batch.operation,pages.size.toLong()+1,values)
                    AcademicPushReply(batch.operation,true,pages.size.toLong(),JsonArray(emptyList()))
                }
            }
            if(lost){lost=false;throw SyncFailure("NETWORK")};return reply
        }
    }
    @Before fun open(){name="phaseb-${newId()}.db";db=DiaryDatabase.open(context,name)}
    @After fun close(){db.close();context.deleteDatabase(name)}
    private suspend fun seed(){repo.saveClass(c,"2026-01-01");db.withTransaction{students.forEach{repo.dao.save(it)}}}
    private fun engine(remote:AcademicRemote,target:AcademicStore=store)=AcademicEngine(target,remote,{owner},{true})
    private suspend fun rows()=ClassLifecycle.snapshot(repo.readSnapshot()).canonical()
    @Test fun createThirtySixAndRecoverInIndependentRoomWithExactIds():Unit=runBlocking {
        seed();val remote=Remote();engine(remote).activate();engine(remote).run()
        assertTrue(store.dao.pending().isEmpty());assertEquals(41,remote.rows.size)
        val n="receiver-${newId()}.db";val second=DiaryDatabase.open(context,n)
        try {
            val receiver=AcademicStore(second,owner);engine(remote,receiver).activate();engine(remote,receiver).run();engine(remote,receiver).run()
            assertEquals(rows(),ClassLifecycle.snapshot(DiaryRepository(second).readSnapshot()).canonical())
            assertEquals(36,second.dao().roster(c.id).size);assertEquals(4,second.dao().periods(c.id).size)
            assertTrue(receiver.receive(remote.pages.single())) // Repeated response is verified by the durable receipt.
            assertTrue(receiver.dao.incoming().isEmpty())
            assertEquals(1,remote.pushes)
        }finally{second.close();context.deleteDatabase(n)}
    }
    @Test fun academicWriteAndDirtyCaptureRollbackTogether():Unit=runBlocking {
        val remote=Remote();engine(remote).activate()
        try{db.withTransaction{repo.saveClass(c,"2026-01-01");error("fixture rollback")};fail()}catch(_:IllegalStateException){}
        assertTrue(repo.readSnapshot().classes.isEmpty());assertTrue(store.dao.states().isEmpty());assertNull(store.prepare())
    }
    @Test fun offlineOutboxAndImmutableOperationSurviveReopenAndLostAck():Unit=runBlocking {
        seed();val remote=Remote();engine(remote).activate();val sent=store.prepare()!!
        remote.fail=true;engine(remote).run();assertEquals(sent.id,store.pending()!!.id)
        db.close();db=DiaryDatabase.open(context,name);assertEquals(sent.body,store.pending()!!.body)
        remote.fail=false;remote.lost=true;engine(remote).run();assertEquals(sent.id,store.pending()!!.id)
        db.close();db=DiaryDatabase.open(context,name);engine(remote).run()
        assertNull(store.pending());assertEquals(41,remote.rows.size);assertEquals(1,remote.pages.size)
    }
    @Test fun editsDuringAnUploadRemainPendingAfterAcknowledgingOnlySentVersion():Unit=runBlocking {
        seed();val remote=Remote();engine(remote).activate();val old=store.prepare()!!
        val batch=AcademicRecords.json.decodeFromString<AcademicBatch>(old.body)
        repo.saveClass(c.copy(name="Edição posterior"),"2026-01-01")
        store.accept(old,remote.push(owner,batch));assertTrue(store.dao.states().any{it.localVersion>it.acknowledged})
        engine(remote).run();assertEquals("Edição posterior",remote.rows.values.first{it.kind=="classes"}.payload!!["name"]!!.jsonPrimitive.content)
    }
    @Test fun remoteRenameKeepsLocalGradesAttendanceAndJournal():Unit=runBlocking {
        seed();val remote=Remote();engine(remote).activate();engine(remote).run()
        repo.dao.save(Lesson("pbl",c.id,"2026-08-10",1,"REALIZADA"));repo.dao.save(Attendance("pbl",students[0].id,"FJ","Outro"))
        repo.dao.save(Activity("pba",c.id,"Atividade fictícia",date="2026-08-10",term=3,maximum=10.0));repo.dao.save(Grade("pba",students[0].id,0.0))
        repo.dao.insertRecord(LessonRecord("pbj",c.id,"2026-08-10","Título","Conteúdo preservado"))
        val before=rows();val key=remote.rows.keys.first{it.kind=="classes"};val base=remote.rows.getValue(key)
        remote.push(owner,AcademicBatch(newId(),listOf(AcademicChange(key.kind,key.key,base.revision,
            JsonObject(base.payload!!+("name" to JsonPrimitive("Nome remoto")))))))
        engine(remote).run();val after=rows()
        assertEquals(before,after.copy(classes=before.classes));assertEquals("Nome remoto",after.classes.single().name)
    }
    @Test fun concurrentNamesArePreservedWithWholeInboxAndStoppedCursor():Unit=runBlocking {
        seed();val remote=Remote();engine(remote).activate();engine(remote).run();val cursor=store.cursor()
        repo.saveClass(c.copy(name="Local"),"2026-01-01")
        val r=remote.rows.values.first{it.kind=="classes"}
        remote.push(owner,AcademicBatch(newId(),listOf(AcademicChange(r.kind,r.key,r.revision,JsonObject(r.payload!!+("name" to JsonPrimitive("Remoto")))))))
        engine(remote).run();assertEquals(cursor,store.cursor());assertEquals("Local",repo.dao.schoolClass(c.id)!!.name)
        assertEquals(1,store.dao.incoming().size);assertEquals("CONFLICT",store.dao.incoming().single().reason)
        repeat(2){engine(remote).run();assertEquals("CONFLICT",store.dao.control()!!.message)}
        assertEquals(cursor,store.cursor());assertEquals("Local",repo.dao.schoolClass(c.id)!!.name)
    }
    @Test fun independentStudentEditsMergeAndKeepOfficialNumbers():Unit=runBlocking {
        seed();val remote=Remote();engine(remote).activate();engine(remote).run()
        repo.saveStudent(students[0].copy(notes="Local"))
        val r=remote.rows.values.first{it.kind=="students"&&it.payload!!["id"]!!.jsonPrimitive.content==students[1].id}
        remote.push(owner,AcademicBatch(newId(),listOf(AcademicChange(r.kind,r.key,r.revision,JsonObject(r.payload!!+("notes" to JsonPrimitive("Remoto")))))))
        engine(remote).run();assertEquals("Local",repo.dao.student(students[0].id)!!.notes);assertEquals("Remoto",repo.dao.student(students[1].id)!!.notes)
        assertEquals((1..36).toList(),repo.dao.roster(c.id).map{it.number});assertTrue(store.dao.incoming().isEmpty())
    }
    @Test fun unknownEntityPreventsPartialClassApplicationAndCursorAdvance():Unit=runBlocking {
        seed();val remote=Remote();engine(remote).activate();engine(remote).run();val before=rows();val cursor=store.cursor()
        val r=remote.rows.values.first{it.kind=="classes"}
        val page=AcademicPage(newId(),cursor+1,listOf(AcademicVersion(r.kind,r.key,r.revision+1,JsonObject(r.payload!!+("name" to JsonPrimitive("Não aplicar")))),
            AcademicVersion("activities",buildJsonObject{put("id","future")},1,buildJsonObject{put("id","future")})))
        assertFalse(store.receive(page));assertEquals(before,rows());assertEquals(cursor,store.cursor())
        assertEquals("UNSUPPORTED",store.dao.incoming().single().reason)
    }
    @Test fun invalidStudentIdentityIsRejectedWithoutChangingOtherRows():Unit=runBlocking {
        seed();val remote=Remote();engine(remote).activate();engine(remote).run();val before=rows();val r=remote.rows.values.first{it.kind=="students"}
        assertFalse(store.receive(AcademicPage(newId(),store.cursor()+1,listOf(r.copy(revision=2,payload=JsonObject(r.payload!!+("personId" to JsonPrimitive("different"))))))))
        assertEquals(before,rows());assertEquals(1,store.dao.incoming().size)
    }
    @Test fun legacyConflictPreventsTransitionAndRemainsUnchanged():Unit=runBlocking {
        seed();val v1=ClassSyncStore(db,owner);v1.enable();v1.enroll(c.id);val before=db.syncDao().state(c.id)!!
        db.syncDao().save(before.copy(conflict="LEGACY_PRESERVED"))
        try{engine(Remote()).activate();fail()}catch(_:IllegalStateException){}
        assertEquals("LEGACY_PRESERVED",db.syncDao().state(c.id)!!.conflict);assertNull(store.dao.control())
    }
    @Test fun backupRestorePreservesOutboxButPausesBothProtocols():Unit=runBlocking {
        seed();val remote=Remote();engine(remote).activate();engine(remote).run();repo.saveStudent(students[0].copy(notes="Pendente"));val op=store.prepare()!!
        val folder=File(context.cacheDir,"phaseb-backup-${newId()}")
        try {
            val manager=BackupManager(db,FileSafetyBackupStore(folder),40,null)
            val before=manager.snapshot();val restore=manager.prepare(manager.backupBytes().inputStream());manager.restore(restore,"RESTAURAR")
            assertEquals(before,manager.snapshot());assertEquals("RESTORE",store.dao.control()!!.stage)
            assertEquals(op.id,store.pending()!!.id);assertFalse(store.active())
        }finally{folder.deleteRecursively()}
    }
    @Test fun migrationElevenToTwelvePreservesAcademicRowsAndStartsDisabled():Unit=runBlocking {
        val n="phaseb-migration-${newId()}.db"
        helper.createDatabase(n,11).use { sql ->
            sql.execSQL("INSERT INTO classes VALUES('legacy','Turma Fictícia','Médio','História',2026,'Matutino','0,0,0,0,0,0,0',50,10,5,7,1,0)")
            sql.execSQL("INSERT INTO students VALUES('s','p','legacy','Estudante Fictício',1,'2026-01-01',NULL,'Ativo','Preservado')")
            sql.execSQL("INSERT INTO lessons VALUES('l','legacy','2026-08-10',1,'REALIZADA','')")
            sql.execSQL("INSERT INTO marks VALUES('l','s','FJ','Outro')")
            sql.execSQL("INSERT INTO activities VALUES('a','legacy','Produção','Produção textual','2026-08-10',3,10,'','')")
            sql.execSQL("INSERT INTO grades VALUES('a','s',0)")
            sql.execSQL("INSERT INTO lesson_records VALUES('j','legacy','2026-08-10','Título','Descrição')")
        }
        helper.runMigrationsAndValidate(n,12,true,ACADEMIC_MIGRATION_11_12).close()
        val migrated=DiaryDatabase.open(context,n)
        try {
            assertEquals("p",migrated.dao().student("s")!!.personId);assertEquals("Outro",migrated.dao().attendance("l").single().reason)
            assertEquals(0.0,migrated.dao().activityGrades("a").single().value!!,0.0);assertEquals("Descrição",migrated.dao().lessonRecord("j")!!.description)
            assertNull(migrated.academicDao().control());assertTrue(migrated.academicDao().pending().isEmpty())
        }finally{migrated.close();context.deleteDatabase(n)}
    }
}
