package br.com.diariodeclasse

import android.content.Context
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.sync.academic.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*

/** Same engine/outbox as B/C. This transport double is NOT claimed as an HTTP/backend proof. */
class PhaseDRoomTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private val owner="11111111-1111-4111-8111-111111111111"
    private lateinit var name:String;private lateinit var db:DiaryDatabase
    private val remote=PhaseBRoomTest.Remote()
    private val fixture get()=PhaseDFixture.read()
    private val store get()=AcademicStore(db,owner)
    private val repo get()=DiaryRepository(db)
    private suspend fun rows()=ClassLifecycle.snapshot(repo.readSnapshot()).canonical()
    private fun engine(s:AcademicStore=store)=AcademicEngine(s,remote,{owner},{true})
    @Before fun open(){name="phased-${newId()}.db";db=DiaryDatabase.open(context,name)}
    @After fun close(){db.close();context.deleteDatabase(name)}
    private suspend fun activate(){engine().activate();engine().activateC();engine().activateD()}
    private suspend fun seed(){activate();PhaseDFixture.insert(db,fixture);engine().run();assertFalse(store.blocked())}
    @Test fun fifteenKindsCapturedTransactionallyAndRecoverExactIndependentRoom():Unit=runBlocking {
        seed();assertEquals(15,remote.rows.keys.map{it.kind}.toSet().size);assertEquals(fixture.canonical(),rows())
        val n="phased-receiver-${newId()}.db";val second=DiaryDatabase.open(context,n)
        try {
            val receiver=AcademicStore(second,owner);engine(receiver).activate();engine(receiver).run();engine(receiver).run()
            assertEquals(fixture.canonical(),ClassLifecycle.snapshot(DiaryRepository(second).readSnapshot()).canonical())
            assertEquals(4,receiver.scope());assertEquals("ACTIVE_D",receiver.stage())
            assertEquals(36,second.dao().roster("d-source").size)
            assertTrue(receiver.dao.pending().isEmpty());assertTrue(receiver.dao.incoming().isEmpty())
            assertEquals(2,second.dao().student("ds1")!!.let{fixture.relocations.single().corrections.size})
        }finally{second.close();context.deleteDatabase(n)}
    }
    @Test fun allDChangesAndDirtyCountersRollbackTogether():Unit=runBlocking {
        activate();val before=store.dao.states()
        try{db.withTransaction{PhaseDFixture.insert(db,fixture);error("synthetic rollback")};fail()}catch(_:IllegalStateException){}
        assertTrue(repo.readSnapshot().classes.isEmpty());assertEquals(before,store.dao.states());assertNull(store.prepare())
    }
    @Test fun offlineJournalAndDraftPersistAfterReopenLostReplyAndReplay():Unit=runBlocking {
        seed();val original=rows();val journal=original.lessonRecords.first().copy(description="Edição offline\nAção & reflexão")
        repo.dao.save(journal);val sent=store.prepare()!!;remote.fail=true;engine().run();assertEquals(sent.id,store.pending()!!.id)
        db.close();db=DiaryDatabase.open(context,name);assertEquals(sent.body,store.pending()!!.body)
        remote.fail=false;remote.lost=true;engine().run();assertEquals(sent.id,store.pending()!!.id);engine().run()
        assertNull(store.pending());assertEquals(original.copy(lessonRecords=original.lessonRecords.map{if(it.id==journal.id)journal else it}),rows())
        assertEquals(2,remote.pages.size)
    }
    @Test fun exportPositionsCanSwapWithoutAUniqueConstraintCollision():Unit=runBlocking {
        seed();val before=rows()
        db.openHelper.writableDatabase.execSQL("CREATE TABLE untouched_layout_probe(n INTEGER NOT NULL)")
        db.openHelper.writableDatabase.execSQL("INSERT INTO untouched_layout_probe VALUES(0)")
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER untouched_layout_update AFTER UPDATE ON export_rows WHEN OLD.id='blank:d-blank' BEGIN UPDATE untouched_layout_probe SET n=n+1; END")
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER untouched_layout_delete AFTER DELETE ON export_rows WHEN OLD.id='blank:d-blank' BEGIN UPDATE untouched_layout_probe SET n=n+1; END")
        val changed=before.exportRows.map{it.copy(position=when(it.position){0->2;2->0;else->1})}
        val changes=changed.filter{it.studentId!=null}.map{r->val p=AcademicRecords.json.encodeToJsonElement(ExportRow.serializer(),r).jsonObject;AcademicChange("export_rows",AcademicKind.EXPORT_ROWS.key(p).key,1,p)}
        remote.pushD(owner,AcademicBatch(newId(),changes));engine().run()
        assertFalse(store.blocked());assertEquals(before.copy(exportRows=changed).canonical(),rows())
        db.openHelper.writableDatabase.query("SELECT n FROM untouched_layout_probe").use{assertTrue(it.moveToFirst());assertEquals(0,it.getInt(0))}
    }
    @Test fun removalOfBlankAndReorderingMultipleRowsKeepsAcademicRecords():Unit=runBlocking {
        seed();val before=rows();val changed=before.exportRows.filter{it.studentId!=null}.map{it.copy(position=if(it.studentId=="ds2")0 else 1)}
        val edits=changed.map{r->val p=AcademicRecords.json.encodeToJsonElement(ExportRow.serializer(),r).jsonObject;AcademicChange("export_rows",AcademicKind.EXPORT_ROWS.key(p).key,1,p)}+
            AcademicChange("export_rows",buildJsonObject{put("id","blank:d-blank")},1,null)
        remote.pushD(owner,AcademicBatch(newId(),edits));engine().run()
        assertFalse(store.blocked());assertEquals(before.copy(exportRows=changed).canonical(),rows())
    }
    @Test fun concurrentLayoutPreservesLocalPositionsAndWholeRemoteBatch():Unit=runBlocking {
        seed();val cursor=store.cursor()
        db.withTransaction {
            db.openHelper.writableDatabase.execSQL("UPDATE export_rows SET position=-1 WHERE id='student:ds1'")
            db.openHelper.writableDatabase.execSQL("UPDATE export_rows SET position=0 WHERE id='student:ds2'")
            db.openHelper.writableDatabase.execSQL("UPDATE export_rows SET position=2 WHERE id='student:ds1'")
        }
        assertNotNull(store.prepare());val before=rows()
        val edits=fixture.exportRows.filter{it.id!="student:ds1"}.map{r->val p=AcademicRecords.json.encodeToJsonElement(ExportRow.serializer(),r.copy(position=if(r.studentId==null)2 else 1)).jsonObject;AcademicChange("export_rows",AcademicKind.EXPORT_ROWS.key(p).key,1,p)}
        remote.pushD(owner,AcademicBatch(newId(),edits));engine().run()
        assertEquals(cursor,store.cursor());assertEquals(before,rows());assertNotNull(store.pending())
        assertEquals(1,store.dao.incoming().size);assertTrue(store.dao.incoming().single().body.contains("blank:d-blank"))
    }
    @Test fun localLayoutReplacementCapturesReinsertedIdsWithoutOuterAbortPolicyCollision():Unit=runBlocking {
        seed();val before=rows();val old=repo.dao.exportRows("d-source")
        val requested=old.reversed().mapIndexed{index,row->row.copy(position=index)}
        repo.saveExportLayout("d-source",old,requested)
        val layout=repo.dao.exportRows("d-source")
        assertEquals(37,layout.size);assertEquals("student:ds2",layout[0].id)
        assertEquals("blank:d-blank",layout[1].id);assertEquals("student:ds1",layout[2].id);assertTrue(layout[2].manualHidden)
        assertEquals(before.marks,rows().marks);assertEquals(before.grades,rows().grades)
        val pending=store.prepare()!!;val changes=AcademicRecords.json.decodeFromString<AcademicBatch>(pending.body).changes
        assertTrue(changes.all{it.kind=="export_rows"});assertEquals(36,changes.size)
        engine().run();assertNull(store.pending())
        assertTrue(store.dao.states().all{it.localVersion==it.acknowledged})
        assertEquals(37,remote.rows.values.count{it.kind=="export_rows"&&it.payload!=null})
    }
    @Test fun journalRepositoryUpdateRetainsExistingCaptureIdentityUnderAbortWriter():Unit=runBlocking {
        seed();val before=rows();val old=before.lessonRecords.first()
        val changed=old.copy(description="Edição explícita pelo repositório\nTexto preservado.")
        repo.saveLessonRecord(changed,old)
        val state=store.dao.states().single{it.kind=="lesson_records"&&it.key1==old.id}
        assertTrue(state.localVersion>state.acknowledged)
        engine().run();assertNull(store.pending())
        assertEquals(before.copy(lessonRecords=before.lessonRecords.map{if(it.id==old.id)changed else it}),rows())
        assertEquals(1,remote.pages.last().records.size)
        assertEquals(old.id,remote.pages.last().records.single().key["id"]!!.jsonPrimitive.content)
    }
    @Test fun concurrentDiaryPreservesLocalRemoteAndCursor():Unit=runBlocking {
        seed();val cursor=store.cursor();val journal=fixture.lessonRecords.first();repo.dao.save(journal.copy(description="Local"))
        val p=AcademicRecords.json.encodeToJsonElement(LessonRecord.serializer(),journal.copy(description="Remoto")).jsonObject
        remote.pushD(owner,AcademicBatch(newId(),listOf(AcademicChange("lesson_records",AcademicKind.JOURNAL.key(p).key,1,p))))
        repeat(2){engine().run();assertEquals("CONFLICT",store.dao.control()!!.message)}
        assertEquals(cursor,store.cursor());assertEquals("Local",repo.dao.lessonRecord(journal.id)!!.description)
        assertTrue(store.dao.incoming().single().body.contains("Remoto"))
    }
    @Test fun invalidHalfMovementRollsBackUnrelatedJournalAndCursor():Unit=runBlocking {
        seed();val before=rows();val cursor=store.cursor()
        val link=remote.rows.values.single{it.kind=="relocations"};val journal=remote.rows.values.first{it.kind=="lesson_records"}
        assertFalse(store.receive(AcademicPage(newId(),cursor+1,listOf(link.copy(revision=2,payload=null),journal.copy(revision=2,payload=JsonObject(journal.payload!!+("description" to JsonPrimitive("Não aplicar"))))))))
        assertEquals(before,rows());assertEquals(cursor,store.cursor());assertEquals(1,store.dao.incoming().size)
    }
    @Test fun sqliteFailureAfterParentWriteRollsBackEntireIncomingPage():Unit=runBlocking {
        seed();val before=rows();val cursor=store.cursor()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_d_journal BEFORE UPDATE ON lesson_records BEGIN SELECT RAISE(ABORT, 'synthetic receiver interruption'); END")
        val school=remote.rows.values.first{it.kind=="classes"}
        val journal=remote.rows.values.first{it.kind=="lesson_records"}
        val page=AcademicPage(newId(),cursor+1,listOf(school.copy(revision=2,payload=JsonObject(school.payload!!+("name" to JsonPrimitive("Never commit half")))),
            journal.copy(revision=2,payload=JsonObject(journal.payload!!+("description" to JsonPrimitive("Never commit half"))))))
        assertFalse(store.receive(page));assertEquals(before,rows());assertEquals(cursor,store.cursor())
        assertEquals(AcademicRecords.json.encodeToString(AcademicPage.serializer(),page),store.dao.incoming().single().body)
        db.close();db=DiaryDatabase.open(context,name)
        assertEquals(before,rows());assertEquals(cursor,store.cursor());assertEquals(1,store.dao.incoming().size)
    }
    @Test fun confirmedTimetableAndAuditMutationRemainBlocked():Unit=runBlocking {
        seed();val before=rows();repo.dao.save(fixture.timetables.first().copy(createdAt="2026-10-02T00:00:00Z"))
        try{store.prepare();fail()}catch(_:IllegalArgumentException){}
        assertTrue(remote.pages.size==1);assertNull(store.pending())
        assertEquals(before.timetables,fixture.timetables.sortedBy{it.id})
    }
    @Test fun backupLogicalRoundtripKeepsHistoricalChainAndDrafts():Unit=runBlocking {
        seed();val snapshot=rows();val decoded=BackupCodec.decode(BackupCodec.encode(snapshot,42,null)).snapshot
        assertEquals(snapshot,decoded.canonical());assertEquals(2,decoded.relocations.single().corrections.size)
        assertEquals("Atestado médico",decoded.relocations.single().corrections.first().removed.single().attendance.reason)
        assertEquals(6,decoded.timetables.first().slots.last().weekday);assertFalse(decoded.histories.isEmpty())
    }
    @Test fun staleCHeadCannotBeSilentlyAdoptedDuringDConsentRetry():Unit=runBlocking {
        engine().activate();engine().activateC();store.prepareD(remote.status(owner))
        remote.pushC(owner,AcademicBatch(newId(),AcademicRecords.rows(fixture).filter{it.kind=="classes"||it.kind=="terms"}.map{AcademicChange(it.kind,it.key,0,it.payload)}))
        try{store.prepareD(remote.status(owner));fail()}catch(e:br.com.diariodeclasse.sync.SyncFailure){assertEquals("CONFLICT",e.code)}
        assertEquals("PREPARED_D",store.stage());assertFalse(store.active())
    }
    @Test fun noDDataCapturedOrEnabledBeforeSeparateConsent():Unit=runBlocking {
        engine().activate();engine().activateC()
        repo.saveClass(fixture.classes.last(),"2026-01-01")
        repo.dao.save(fixture.lessonRecords.first().copy(classId=fixture.classes.last().id))
        engine().run();assertTrue(store.dao.states().none{it.kind=="lesson_records"});assertEquals(3,store.scope())
    }
}

internal object PhaseDFixture {
    fun read():BackupSnapshot=InstrumentationRegistry.getInstrumentation().context.assets.open("phase-d.json").bufferedReader().use{BackupCodec.json.decodeFromString(it.readText())}
    suspend fun insert(db:DiaryDatabase,s:BackupSnapshot)=db.withTransaction {
        BackupCodec.validate(s);val d=db.dao()
        s.classes.forEach{d.save(it)};s.terms.forEach{d.save(it)};s.students.forEach{d.save(it)}
        s.lessons.forEach{d.save(it)};s.activities.forEach{d.save(it)};s.grades.forEach{d.save(it)};s.marks.forEach{d.save(it)}
        s.recompositions.forEach{d.save(it)};s.relocations.forEach{d.save(it)};s.lessonRecords.forEach{d.save(it)}
        s.classArchives.forEach{d.save(it)};s.timetables.forEach{d.save(it)};d.saveHistories(s.histories)
        d.insertExportRows(s.exportRows);s.drafts.forEach{d.save(it)}
    }
}
