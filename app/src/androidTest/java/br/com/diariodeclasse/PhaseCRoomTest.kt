package br.com.diariodeclasse

import android.content.Context
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.sync.academic.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*

/** Room/transaction tests; actual HTTP on independent AVDs is tested separately. */
class PhaseCRoomTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private val owner="11111111-1111-4111-8111-111111111111"
    private lateinit var name:String;private lateinit var db:DiaryDatabase
    private val repo get()=DiaryRepository(db)
    private val store get()=AcademicStore(db,owner)
    private val remote=PhaseBRoomTest.Remote()
    private val c=SchoolClass("c-class",name="Turma Fictícia",subject="História",year=2026)
    private val students=(1..36).map{Student("cs$it","cp$it",c.id,"Fictício $it",it,"2026-01-01")}
    private fun engine(target:AcademicStore=store)=AcademicEngine(target,remote,{owner},{true})
    @Before fun open(){name="phasec-${newId()}.db";db=DiaryDatabase.open(context,name)}
    @After fun close(){db.close();context.deleteDatabase(name)}
    private suspend fun seedB(){repo.saveClass(c,"2026-01-01");students.forEach{repo.saveStudent(it)};engine().activate();engine().run()}
    private suspend fun seedC(){
        repo.dao.save(Lesson("cl",c.id,"2026-08-10",1,"REALIZADA"))
        repo.dao.save(Activity("ca",c.id,"Produção fictícia",type="Produção textual",date="2026-08-10",term=3,maximum=7.5))
        repo.dao.save(Grade("ca",students[0].id,0.0));repo.dao.save(Grade("ca",students[1].id,null));repo.dao.save(Grade("ca",students[2].id,7.5))
        for((i,m) in listOf("P","F","FJ","NR").withIndex())repo.dao.save(Attendance("cl",students[i].id,m,if(m=="FJ")"Outro"else null))
        repo.dao.save(Recomposition(students[0].id,3,0.0,"2026-08-10"))
        repo.dao.insertRecord(LessonRecord("cj",c.id,"2026-08-10","Local","Diário não sincronizado"))
    }
    @Test fun clearingPfIsDurableTombstoneAndRecoversInIndependentRoom():Unit=runBlocking {
        seedB();seedC();engine().activateC();engine().run()
        val before=repo.readSnapshot()
        repo.toggleMark("cl",students[0].id,"P")
        assertTrue(repo.dao.attendance("cl").none{it.studentId==students[0].id})
        val pending=store.prepare()!!
        val change=AcademicRecords.json.decodeFromString<AcademicBatch>(pending.body).changes.single()
        assertEquals("marks",change.kind);assertNull(change.payload)
        db.close();db=DiaryDatabase.open(context,name)
        assertTrue(repo.dao.attendance("cl").none{it.studentId==students[0].id})
        remote.lost=true;engine().run();assertEquals(pending.id,store.pending()!!.id)
        engine().run();assertNull(store.pending())
        val receiverName="pf-receiver-${newId()}.db";val receiverDb=DiaryDatabase.open(context,receiverName)
        try {
            val receiver=AcademicStore(receiverDb,owner);engine(receiver).activate();engine(receiver).run();engine(receiver).run()
            val received=DiaryRepository(receiverDb).readSnapshot()
            assertTrue(received.marks.none{it.lessonId=="cl"&&it.studentId==students[0].id})
            assertEquals(before.marks.filterNot{it.lessonId=="cl"&&it.studentId==students[0].id}.toSet(),received.marks.toSet())
            assertTrue(receiver.dao.incoming().isEmpty());assertTrue(receiver.dao.pending().isEmpty())
        } finally {receiverDb.close();context.deleteDatabase(receiverName)}
        repeat(20){repo.toggleMark("cl",students[0].id,"F")}
        assertTrue(repo.dao.attendance("cl").none{it.studentId==students[0].id})
        repo.toggleMark("cl",students[2].id,"F")
        assertEquals(Attendance("cl",students[2].id,"F"),repo.dao.attendance("cl").single{it.studentId==students[2].id})
        assertEquals("NR",repo.dao.attendance("cl").single{it.studentId==students[3].id}.mark)
    }
    @Test fun scopeExpansionRequiresConsentAndPreservesPreviouslyLocalRecords():Unit=runBlocking {
        seedB();seedC();engine().run();assertEquals(41,remote.rows.size);assertTrue(store.dao.states().none{it.kind in phaseCKinds-phaseBKinds})
        engine().activateC();assertEquals("ACTIVE_C",store.stage());engine().run()
        assertEquals(51,remote.rows.size);assertFalse(remote.rows.keys.any{it.kind=="lesson_records"});assertEquals("Diário não sincronizado",repo.dao.lessonRecord("cj")!!.description)
    }
    @Test fun independentRoomRecoversZeroNullAbsentPFFjNrReasonsAndIds():Unit=runBlocking {
        seedB();seedC();engine().activateC();engine().run()
        val n="phasec-receiver-${newId()}.db";val second=DiaryDatabase.open(context,n)
        try {
            val receiver=AcademicStore(second,owner);engine(receiver).activate();engine(receiver).run();engine(receiver).run()
            val a=ClassLifecycle.snapshot(repo.readSnapshot()).copy(lessonRecords=emptyList()).canonical()
            assertEquals(a,ClassLifecycle.snapshot(DiaryRepository(second).readSnapshot()).canonical())
            assertEquals(36,second.dao().roster(c.id).size);assertEquals(3,second.dao().activityGrades("ca").size)
            assertEquals(0.0,second.dao().activityGrades("ca").single{it.studentId==students[0].id}.value!!,0.0)
            assertNull(second.dao().activityGrades("ca").single{it.studentId==students[1].id}.value)
            assertTrue(second.dao().activityGrades("ca").none{it.studentId==students[3].id})
            assertEquals(listOf("P","F","FJ","NR"),second.dao().attendance("cl").sortedBy{students.indexOfFirst{s->s.id==it.studentId}}.map{it.mark})
            assertTrue(receiver.dao.pending().isEmpty())
        }finally{second.close();context.deleteDatabase(n)}
    }
    @Test fun transactionRollbackDoesNotLeaveCapturedAcademicOperations():Unit=runBlocking {
        seedB();engine().activateC();val before=store.dao.states()
        try{db.withTransaction{seedC();error("Synthetic rollback")};fail()}catch(_:IllegalStateException){}
        assertTrue(repo.readSnapshot().activities.isEmpty());assertEquals(before,store.dao.states());assertNull(store.prepare())
    }
    @Test fun remoteGradeAndJustificationEditDoesNotChangeAnotherOccurrence():Unit=runBlocking {
        seedB();seedC();engine().activateC();engine().run()
        val grade=remote.rows.values.single{it.kind=="grades"&&it.key["studentId"]!!.jsonPrimitive.content==students[0].id}
        val mark=remote.rows.values.single{it.kind=="marks"&&it.key["studentId"]!!.jsonPrimitive.content==students[2].id}
        remote.pushC(owner,AcademicBatch(newId(),listOf(AcademicChange(grade.kind,grade.key,grade.revision,JsonObject(grade.payload!!+("value" to JsonPrimitive(6.0)))),
            AcademicChange(mark.kind,mark.key,mark.revision,JsonObject(mark.payload!!+("reason" to JsonPrimitive("Atestado médico")))))))
        engine().run();assertEquals(6.0,repo.dao.activityGrades("ca").single{it.studentId==students[0].id}.value!!,0.0)
        assertEquals("Atestado médico",repo.dao.attendance("cl").single{it.studentId==students[2].id}.reason)
        assertEquals("F",repo.dao.attendance("cl").single{it.studentId==students[1].id}.mark)
        assertEquals("Diário não sincronizado",repo.dao.lessonRecord("cj")!!.description)
    }
    @Test fun concurrentGradePreservesBothVariantsAndStopsCursor():Unit=runBlocking {
        seedB();seedC();engine().activateC();engine().run();val cursor=store.cursor()
        repo.dao.save(Grade("ca",students[0].id,3.0))
        val r=remote.rows.values.single{it.kind=="grades"&&it.key["studentId"]!!.jsonPrimitive.content==students[0].id}
        remote.pushC(owner,AcademicBatch(newId(),listOf(AcademicChange(r.kind,r.key,r.revision,JsonObject(r.payload!!+("value" to JsonPrimitive(5.0)))))))
        engine().run();assertEquals(cursor,store.cursor());assertEquals(3.0,repo.dao.activityGrades("ca").single{it.studentId==students[0].id}.value!!,0.0)
        assertEquals(1,store.dao.incoming().size);assertTrue(store.dao.incoming().single().body.contains("5.0"))
        val pushes=remote.pushes
        repeat(2){engine().run();assertEquals("CONFLICT",store.dao.control()!!.message)}
        assertEquals(pushes,remote.pushes) // A retry inspects the preserved conflict; it cannot resend.
        assertEquals(cursor,store.cursor());assertEquals(3.0,repo.dao.activityGrades("ca").single{it.studentId==students[0].id}.value!!,0.0)
    }
    @Test fun invalidGradeRejectsEntirePageIncludingUnrelatedClassRename():Unit=runBlocking {
        seedB();seedC();engine().activateC();engine().run();val cursor=store.cursor();val before=repo.readSnapshot()
        val g=remote.rows.values.single{it.kind=="grades"&&it.key["studentId"]!!.jsonPrimitive.content==students[0].id}
        val cl=remote.rows.values.single{it.kind=="classes"}
        assertFalse(store.receive(AcademicPage(newId(),cursor+1,listOf(g.copy(revision=g.revision+1,payload=JsonObject(g.payload!!+("value" to JsonPrimitive(99)))),
            cl.copy(revision=cl.revision+1,payload=JsonObject(cl.payload!!+("name" to JsonPrimitive("Não aplicar"))))))))
        assertEquals(before,repo.readSnapshot());assertEquals(cursor,store.cursor())
    }
    @Test fun cOutboxAndLostAckSurviveProcessReopenWithoutDuplicateGrades():Unit=runBlocking {
        seedB();seedC();engine().activateC();val op=store.prepare()!!;remote.lost=true;engine().run()
        assertEquals(op.id,store.pending()!!.id);db.close();db=DiaryDatabase.open(context,name);engine().run()
        assertNull(store.pending());assertEquals(3,remote.rows.values.count{it.kind=="grades"});assertEquals("ACTIVE_C",store.stage())
    }
    @Test fun independentGradesConvergeWhileOtherDataRemainLocal():Unit=runBlocking {
        seedB();seedC();engine().activateC();engine().run();repo.dao.save(Grade("ca",students[0].id,1.0))
        val r=remote.rows.values.single{it.kind=="grades"&&it.key["studentId"]!!.jsonPrimitive.content==students[1].id}
        remote.pushC(owner,AcademicBatch(newId(),listOf(AcademicChange(r.kind,r.key,r.revision,JsonObject(r.payload!!+("value" to JsonPrimitive(2.0)))))))
        engine().run();assertEquals(listOf(1.0,2.0,7.5),repo.dao.activityGrades("ca").sortedBy{students.indexOfFirst{s->s.id==it.studentId}}.map{it.value})
        assertTrue(store.dao.incoming().isEmpty());assertNotNull(repo.dao.lessonRecord("cj"))
    }
    @Test fun changedRemoteHeadDuringConsentCannotBeAdoptedByRetry():Unit=runBlocking {
        seedB();seedC();store.prepareC(remote.status(owner));val before=repo.readSnapshot()
        val r=remote.rows.values.single{it.kind=="classes"}
        remote.push(owner,AcademicBatch(newId(),listOf(AcademicChange(r.kind,r.key,r.revision,JsonObject(r.payload!!+("name" to JsonPrimitive("Mudou durante consentimento")))))))
        try{store.prepareC(remote.status(owner));fail()}catch(e:br.com.diariodeclasse.sync.SyncFailure){assertEquals("CONFLICT",e.code)}
        assertEquals("PREPARED_C",store.stage());assertEquals(before,repo.readSnapshot());assertEquals(3,store.scope()) // C consent is durable; server expansion is still blocked.
    }

    @Test fun decimalWireReplayPreservesFrozenOperationAndRawReceiptDigest():Unit=runBlocking {
        seedB();seedC();engine().activateC();engine().run();repo.dao.save(Grade("ca",students[0].id,0.0001))
        val op=store.prepare()!!;val batch=AcademicRecords.json.decodeFromString<AcademicBatch>(op.body)
        remote.pushC(owner,batch) // Server commits; its reply is lost.
        val page=remote.pages.last();val wire=page.copy(records=page.records.map{r->r.copy(payload=JsonObject(r.payload!!+("value" to AcademicRecords.json.parseToJsonElement("0.00010"))))})
        remote.pages[remote.pages.lastIndex]=wire
        assertTrue(store.receive(wire));assertNull(store.pending());assertTrue(store.receive(wire))
        assertEquals(0.0001,repo.dao.activityGrades("ca").single{it.studentId==students[0].id}.value!!,0.0)
        assertTrue(store.dao.incoming().isEmpty());assertEquals(wire.sequence,store.cursor())
        val n="decimal-receiver-${newId()}.db";val second=DiaryDatabase.open(context,n)
        try {
            val receiver=AcademicStore(second,owner);engine(receiver).activate();engine(receiver).run()
            assertEquals(0.0001,second.dao().activityGrades("ca").single{it.studentId==students[0].id}.value!!,0.0)
            assertTrue(receiver.dao.incoming().isEmpty())
        }finally{second.close();context.deleteDatabase(n)}
    }

}
