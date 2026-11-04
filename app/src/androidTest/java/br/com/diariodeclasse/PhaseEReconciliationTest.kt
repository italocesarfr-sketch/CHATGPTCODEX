package br.com.diariodeclasse

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import br.com.diariodeclasse.auth.AccountBackup
import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.sync.SyncFailure
import br.com.diariodeclasse.sync.academic.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Room/engine state tests use a transport double; HTTP evidence is a separate two-AVD probe. */
class PhaseEReconciliationTest {
    @get:Rule val helper=MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),DiaryDatabase::class.java)
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private val owner="11111111-1111-4111-8111-111111111111"
    private lateinit var name:String;private lateinit var db:DiaryDatabase
    private val remote=DecisionRemote()
    private val store get()=AcademicStore(db,owner)
    private val review get()=AcademicReconciliation(store,remote,{owner},{true})
    private fun engine()=AcademicEngine(store,remote,{owner},{true})
    private suspend fun snapshot()=ClassLifecycle.snapshot(DiaryRepository(db).readSnapshot()).canonical()
    private suspend fun grade()=db.dao().activityGrades("d-a1").single{it.studentId=="ds2"}.value
    @Before fun open(){name="e-reconcile-${newId()}.db";db=DiaryDatabase.open(context,name)}
    @After fun close(){db.close();context.deleteDatabase(name)}
    private suspend fun seed(){engine().activate();PhaseDFixture.insert(db,PhaseDFixture.read());engine().run();assertEquals(1,store.cursor())}
    private suspend fun peer(value:Double,rename:Boolean=false) {
        val p=AcademicRecords.json.encodeToJsonElement(Grade.serializer(),Grade("d-a1","ds2",value)).jsonObject
        val changes=mutableListOf(AcademicChange("grades",buildJsonObject{put("activityId","d-a1");put("studentId","ds2")},remote.base.rows.getValue(AcademicKey("grades",buildJsonObject{put("activityId","d-a1");put("studentId","ds2")})).revision,p))
        if(rename){val c=PhaseDFixture.read().classes.first();val row=remote.base.rows.getValue(AcademicKey("classes",buildJsonObject{put("id",c.id)}));changes+=AcademicChange(row.kind,row.key,row.revision,AcademicRecords.json.encodeToJsonElement(SchoolClass.serializer(),c.copy(name="Remota com nota")).jsonObject)}
        remote.base.push(owner,AcademicBatch(newId(),changes))
    }
    private suspend fun conflict(rename:Boolean=false){seed();db.dao().save(Grade("d-a1","ds2",6.0));assertNotNull(store.prepare());peer(8.0,rename);engine().run();assertTrue(store.blocked());assertEquals(1,store.cursor())}
    @Test fun previewAndCancelDoNotChangeSchoolRowsQueuesOrCursor():Unit=runBlocking {
        conflict();val before=snapshot();val pending=store.pending();val inbox=store.dao.incoming();val preview=review.preview()
        assertEquals(1,preview.evidence.size);assertEquals(listOf("value"),preview.evidence.single().fields)
        assertEquals(before,snapshot());assertEquals(pending,store.pending());assertEquals(inbox,store.dao.incoming());assertEquals(1,store.cursor());assertTrue(store.dao.decisions().isEmpty())
    }
    @Test fun localChoiceIsAuditedAndReplayedAfterLostReplyAndRoomReopen():Unit=runBlocking {
        conflict();val decision=review.confirm(review.preview(),AcademicChoice.KEEP_LOCAL);remote.loseDecision=true
        engine().run();assertEquals("PENDING",store.dao.decisions().single().phase);assertNotNull(store.pending());assertEquals(1,store.cursor())
        db.close();db=DiaryDatabase.open(context,name);engine().run()
        assertEquals(6.0,grade()!!,0.0);assertEquals("APPLIED",store.dao.decisions().single().phase);assertEquals(3,store.cursor());assertFalse(store.blocked())
        assertEquals(1,remote.decisions.size);assertTrue(store.dao.decisions().single().body.contains("oldPending"));assertEquals(decision.id,store.dao.decisions().single().id)
    }
    @Test fun remoteChoiceDoesNotWriteAnUnnecessaryServerGradeRevision():Unit=runBlocking {
        conflict();review.confirm(review.preview(),AcademicChoice.KEEP_REMOTE);engine().run()
        assertEquals(8.0,grade()!!,0.0);assertEquals(2,remote.base.pages.size);assertEquals(2,store.cursor());assertNull(store.pending());assertTrue(store.dao.incoming().isEmpty())
    }
    @Test fun localChangeBetweenPreviewAndConfirmRejectsTheOldPreview():Unit=runBlocking {
        conflict();val preview=review.preview();db.dao().save(Grade("d-a1","ds2",7.0))
        try{review.confirm(preview,AcademicChoice.KEEP_REMOTE);fail()}catch(_:IllegalStateException){}
        assertEquals(7.0,grade()!!,0.0);assertTrue(store.dao.decisions().isEmpty());assertTrue(store.blocked());assertEquals(1,store.cursor())
    }
    @Test fun remoteChangeAfterConfirmationStaysBlockedAndCanBeReviewedAgain():Unit=runBlocking {
        conflict();review.confirm(review.preview(),AcademicChoice.KEEP_LOCAL);peer(9.0);engine().run()
        assertEquals("STALE",store.dao.decisions().single().phase);assertEquals(6.0,grade()!!,0.0);assertEquals(1,store.cursor());assertTrue(store.blocked())
        review.confirm(review.preview(),AcademicChoice.KEEP_REMOTE);engine().run()
        assertEquals(9.0,grade()!!,0.0);assertEquals(3,store.cursor());assertEquals(listOf("STALE","APPLIED"),store.dao.decisions().map{it.phase})
    }
    @Test fun localChangeAfterConfirmationCancelsWithoutSendingTheOldProposal():Unit=runBlocking {
        conflict();review.confirm(review.preview(),AcademicChoice.KEEP_LOCAL);db.dao().save(Grade("d-a1","ds2",7.0));engine().run()
        assertEquals("STALE",store.dao.decisions().single().phase);assertEquals(7.0,grade()!!,0.0);assertEquals(2,remote.base.pages.size);assertEquals(1,store.cursor())
    }
    @Test fun sqliteFailureAfterParentUpdateRollsBackThenReplaysConfirmedDecision():Unit=runBlocking {
        conflict(true);val before=snapshot();review.confirm(review.preview(),AcademicChoice.KEEP_REMOTE)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER e_stop_grade BEFORE UPDATE ON grades BEGIN SELECT RAISE(ABORT, 'fixture interruption'); END")
        engine().run();assertEquals(before,snapshot());assertEquals(1,store.cursor());assertEquals("CONFIRMED",store.dao.decisions().single().phase);assertTrue(store.blocked())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER e_stop_grade");engine().run()
        assertEquals(8.0,grade()!!,0.0);assertEquals("Remota com nota",snapshot().classes.single{it.id=="d-source"}.name);assertEquals("APPLIED",store.dao.decisions().single().phase);assertEquals(1,remote.decisions.size)
    }
    @Test fun encryptedOldBackupRemainsPausedAndReconciliationKeepsRemoteOnlyEnrollment():Unit=runBlocking {
        seed();val folder=File(context.cacheDir,"e-restore-${newId()}");val manager=BackupManager(db,FileSafetyBackupStore(folder),45,null)
        try {
            val encrypted=AccountBackup.seal(manager.backupBytes(),owner,"Fictitious-Backup-2026".toCharArray())
            try{AccountBackup.open(encrypted,"22222222-2222-4222-8222-222222222222","Fictitious-Backup-2026".toCharArray());fail()}catch(_:br.com.diariodeclasse.auth.AuthFailure){}
            peer(9.0)
            val extra=PhaseDFixture.read().students.single{it.id=="ds2"}.copy(id="remote-only",personId="new-person",number=40)
            val value=AcademicRecords.json.encodeToJsonElement(Student.serializer(),extra).jsonObject
            remote.base.push(owner,AcademicBatch(newId(),listOf(AcademicChange("students",buildJsonObject{put("id",extra.id)},0,value))))
            val raw=AccountBackup.open(encrypted,owner,"Fictitious-Backup-2026".toCharArray());manager.restore(manager.prepare(raw.inputStream()),"RESTAURAR")
            engine().run();assertEquals("RESTORE",store.stage());assertFalse(store.active());assertNull(db.dao().student(extra.id))
            review.confirm(review.preview(),AcademicChoice.KEEP_REMOTE);engine().run()
            assertEquals(9.0,grade()!!,0.0);assertEquals(extra,db.dao().student(extra.id));assertEquals("ACTIVE_E",store.stage());assertFalse(store.blocked())
        }finally{folder.deleteRecursively()}
    }
    @Test fun restoreInFreshIndependentAccountCreatesAPauseBeforeAnyFirstEnrollment():Unit=runBlocking {
        seed();val raw=BackupCodec.encode(snapshot(),45,null);peer(9.0)
        val n="e-independent-restore-${newId()}.db";val second=DiaryDatabase.open(context,n);val folder=File(context.cacheDir,"e-independent-${newId()}")
        try {
            val manager=BackupManager(second,FileSafetyBackupStore(folder),45,null,owner=owner)
            manager.restore(manager.prepare(raw.inputStream()),"RESTAURAR")
            val receiver=AcademicStore(second,owner);val e=AcademicEngine(receiver,remote,{owner},{true});e.run()
            assertEquals("RESTORE",receiver.stage());assertFalse(receiver.active());assertTrue(receiver.dao.states().isEmpty())
            val r=AcademicReconciliation(receiver,remote,{owner},{true});r.confirm(r.preview(),AcademicChoice.KEEP_REMOTE);e.run()
            assertEquals(9.0,second.dao().activityGrades("d-a1").single{it.studentId=="ds2"}.value!!,0.0)
            assertEquals(37,second.backupDao().students().size);assertEquals("ACTIVE_E",receiver.stage());assertTrue(receiver.dao.pending().isEmpty())
        }finally{second.close();context.deleteDatabase(n);folder.deleteRecursively()}
    }
    @Test fun migrationThirteenToFourteenPreservesStagedPartsAndAddsEmptyDecisionAudit():Unit=runBlocking {
        val n="e-decision-migration-${newId()}.db"
        helper.createDatabase(n,13).use{sql->sql.execSQL("INSERT INTO sync_academic_control VALUES(1,'$owner','ACTIVE_E',1,0,0,'OFFLINE',0)");sql.execSQL("INSERT INTO sync_academic_parts VALUES(1,0,'$owner','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',1,1,'digest','frozen-part')")}
        helper.runMigrationsAndValidate(n,14,true,ACADEMIC_MIGRATION_13_14).use{sql->sql.query("SELECT body FROM sync_academic_parts").use{assertTrue(it.moveToFirst());assertEquals("frozen-part",it.getString(0))};sql.query("SELECT count(*) FROM sync_academic_decisions").use{assertTrue(it.moveToFirst());assertEquals(0,it.getInt(0))}}
        context.deleteDatabase(n)
    }
    private class DecisionRemote:AcademicRemote by PhaseBRoomTest.Remote() {
        val base=PhaseBRoomTest.Remote().apply{scope=5}
        val decisions=mutableMapOf<String,AcademicPushReply>();private val requests=mutableMapOf<String,JsonObject>();private val staged=mutableMapOf<String,AcademicBatch>();var loseDecision=false
        override suspend fun status(token:String)=base.status(token)
        override suspend fun activate(token:String,expected:AcademicProtocolStatus)=base.status(token)
        override suspend fun pushE(token:String,batch:AcademicBatch)=base.push(token,batch)
        override suspend fun stageE(token:String,batch:AcademicBatch){val old=staged.putIfAbsent(batch.operation,batch);require(old==null||old==batch)}
        override suspend fun pullE(token:String,after:Long,offset:Int):List<AcademicFragment> {
            val page=base.pull(token,after).singleOrNull() ?: return emptyList()
            return listOf(AcademicFragment(page.operation,page.sequence,page.records.size,offset,page.records.drop(offset).take(128)))
        }
        override suspend fun decisionE(token:String,operation:String)=decisions[operation]
        override suspend fun resolveE(token:String,operation:String,request:JsonObject,cancel:Boolean):AcademicPushReply {
            decisions[operation]?.let{require(requests[operation]==request);return it}
            val reply=if(cancel||request.getValue("expectedSequence").jsonPrimitive.long!=base.pages.size.toLong())AcademicPushReply(operation,false,null,buildJsonArray{add(buildJsonObject{put("reason","changed")})})
                else if(request.getValue("hasChanges").jsonPrimitive.boolean)base.push(token,staged.getValue(operation))else AcademicPushReply(operation,true,base.pages.size.toLong(),JsonArray(emptyList()))
            requests[operation]=request;decisions[operation]=reply
            if(loseDecision){loseDecision=false;throw SyncFailure("NETWORK")};return reply
        }
    }
}
