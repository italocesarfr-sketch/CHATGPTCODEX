package br.com.diariodeclasse

import android.content.Context
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.sync.*
import br.com.diariodeclasse.sync.academic.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.util.UUID

/** Real SQLite DTO/converter checks. No v2 network or second-device sync is claimed here. */
class AcademicProtocolRoomTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db:DiaryDatabase
    private lateinit var repo:DiaryRepository
    private lateinit var name:String
    private val school=SchoolClass("protocol-fictitious",name="Turma Fictícia",subject="História",year=2026)
    private val student=Student("protocol-student","protocol-person",school.id,"Estudante Fictício",1,"2026-01-01")
    @Before fun open() { name="academic-protocol-${UUID.randomUUID()}.db";db=DiaryDatabase.open(context,name);repo=DiaryRepository(db) }
    @After fun close() { db.close();context.deleteDatabase(name) }
    private suspend fun seed() {
        repo.saveClass(school,"2026-01-01")
        db.withTransaction {
            repo.dao.save(student)
            repo.dao.save(Lesson("protocol-lesson",school.id,"2026-08-10",1,"REALIZADA"))
            repo.dao.save(Attendance("protocol-lesson",student.id,"FJ","Atestado médico"))
            repo.dao.save(Activity("protocol-activity",school.id,"Produção fictícia","Produção textual","2026-08-10",3,10.0))
            repo.dao.save(Grade("protocol-activity",student.id,0.0))
            repo.dao.save(Recomposition(student.id,3,null,"2026-08-11"))
            repo.dao.insertRecord(LessonRecord("protocol-journal",school.id,"2026-08-10","Título","Descrição\nPreservada"))
        }
    }
    @Test fun sqliteTypesAndReopenPreserveFjReasonZeroNullAndJournal():Unit=runBlocking {
        seed();val before=ClassLifecycle.snapshot(repo.readSnapshot()).canonical()
        val rows=AcademicRecords.rows(before);assertEquals(before,AcademicRecords.snapshot(rows))
        db.close();db=DiaryDatabase.open(context,name);repo=DiaryRepository(db)
        assertEquals(before,ClassLifecycle.snapshot(repo.readSnapshot()).canonical())
        assertEquals("Atestado médico",AcademicRecords.snapshot(rows).marks.single().reason)
        assertNull(AcademicRecords.snapshot(rows).recompositions.single().value)
        assertEquals(0.0,AcademicRecords.snapshot(rows).grades.single().value!!,0.0)
        assertNull(db.syncDao().control()) // Preparing rows never enables sync or enrolls data.
    }
    @Test fun renameReviewWithLinkedDataDoesNotWriteOrAlterExistingV1Conflict():Unit=runBlocking {
        seed();val owner="11111111-1111-4111-8111-111111111111";val store=ClassSyncStore(db,owner)
        store.enable();store.enroll(school.id)
        val op=store.prepare().single()
        store.accept(op,PushReply(op.id,true,RemoteClass(school.id,1,1,false,op.payload)))
        val incoming=RemoteClass(school.id,2,2,false,ClassBundle(school.copy(name="Remoto"),repo.dao.periods(school.id)).encode())
        store.receive(listOf(incoming))
        val conflict=db.syncDao().state(school.id)!!.conflict;assertNotNull(conflict)
        val before=ClassLifecycle.snapshot(repo.readSnapshot()).canonical();val rows=AcademicRecords.rows(before)
        val changed=before.copy(classes=listOf(school.copy(name="Remoto")))
        val changes=AcademicRecords.delta(rows,AcademicRecords.rows(changed),rows.associate { it.identity to 1L })
        val review=AcademicMerge.review(rows,rows,changes,changes.map { it.identity }.toSet())
        assertNull(review.candidate);assertEquals("PRESERVED_CONFLICT",review.conflicts.single().reason)
        assertEquals(before,ClassLifecycle.snapshot(repo.readSnapshot()).canonical())
        assertEquals(conflict,db.syncDao().state(school.id)!!.conflict)
    }
    @Test fun applyingNoReviewAndRollingBackAcademicWritesDoNotActivateProtocol():Unit=runBlocking {
        seed();val before=ClassLifecycle.snapshot(repo.readSnapshot()).canonical()
        try {
            db.withTransaction { repo.dao.save(student.copy(name="Não persistir"));error("fixture rollback") }
            fail("Expected fixture rollback")
        } catch (_: IllegalStateException) { }
        assertEquals(before,ClassLifecycle.snapshot(repo.readSnapshot()).canonical())
        assertTrue(db.syncDao().operations().isEmpty());assertNull(db.syncDao().control())
    }
}
