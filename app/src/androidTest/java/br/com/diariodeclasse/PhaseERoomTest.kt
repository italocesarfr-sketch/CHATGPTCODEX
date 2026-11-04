package br.com.diariodeclasse

import android.content.Context
import androidx.room.withTransaction
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.sync.academic.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*

internal object PhaseEFixture {
    fun read(count:Int=1000):BackupSnapshot {
        val s=PhaseDFixture.read();val students=(0 until count).map{n->s.students[1].copy(id="e-student-$n",personId="e-person-$n",number=37+n,joined="2026-01-01",left=null,status="Ativo")}
        return s.copy(students=s.students+students,
            marks=s.marks+students.flatMapIndexed{n,student->s.lessons.take(2).map{Attendance(it.id,student.id,listOf("P","F","FJ","NR")[n%4],if(n%4==2)"Outro"else null)}},
            grades=s.grades+students.flatMapIndexed{n,student->s.activities.map{Grade(it.id,student.id,if(n%3==0)null else if(n%3==1)0.0 else 5.0)}}).canonical().also{BackupCodec.validate(it)}
    }
    fun fragments(s:BackupSnapshot,operation:String=newId(),sequence:Long=1):List<AcademicFragment> {
        val records=AcademicRecords.rows(s).map{AcademicVersion(it.kind,it.key,1,it.payload)}
        return records.chunked(128).mapIndexed{index,part->AcademicFragment(operation,sequence,records.size,index*128,part)}
    }
}
class PhaseERoomTest {
    @get:Rule val helper=MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),DiaryDatabase::class.java)
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private val owner="11111111-1111-4111-8111-111111111111"
    private lateinit var name:String;private lateinit var db:DiaryDatabase
    private val store get()=AcademicStore(db,owner)
    private suspend fun snapshot()=ClassLifecycle.snapshot(DiaryRepository(db).readSnapshot()).canonical()
    @Before fun open():Unit=runBlocking{name="phasee-${newId()}.db";db=DiaryDatabase.open(context,name);store.dao.save(AcademicControl(owner=owner,stage="ACTIVE_E",enabled=true))}
    @After fun close(){db.close();context.deleteDatabase(name)}
    @Test fun partialReceiveReopensWithoutAnyAcademicWriteOrCursorAdvance():Unit=runBlocking {
        val parts=PhaseEFixture.fragments(PhaseEFixture.read(50));assertTrue(store.receivePart(parts.first()))
        assertTrue(snapshot().classes.isEmpty());assertEquals(0,store.cursor());assertEquals(128,store.nextOffset())
        db.close();db=DiaryDatabase.open(context,name)
        assertEquals(128,store.nextOffset());assertTrue(store.receivePart(parts.first()));assertTrue(snapshot().classes.isEmpty())
        parts.drop(1).forEach{assertTrue(store.receivePart(it))}
        assertEquals(PhaseEFixture.read(50),snapshot());assertEquals(1,store.cursor());assertTrue(store.dao.stagedParts().isEmpty())
    }
    @Test fun moreThanThreeThousandRowsRecoverExactlyAndChangedClassOnlyEnqueuesOneRow():Unit=runBlocking {
        val fixture=PhaseEFixture.read(600);val parts=PhaseEFixture.fragments(fixture)
        assertEquals(3171,parts.first().total);parts.forEach{assertTrue(store.receivePart(it))}
        assertEquals(fixture,snapshot());assertEquals(15,store.dao.states().map{it.kind}.toSet().size)
        val c=fixture.classes.first();db.dao().save(c.copy(name="Renomeada sem perder notas"))
        val batch=AcademicRecords.json.decodeFromString<AcademicBatch>(store.prepare()!!.body)
        assertEquals(1,batch.changes.size);assertEquals("classes",batch.changes.single().kind)
        assertEquals(fixture.grades,snapshot().grades);assertEquals(fixture.marks,snapshot().marks)
    }
    @Test fun senderRetainsSameLargeOperationAfterReopenAndAcknowledgesExactVersions():Unit=runBlocking {
        val fixture=PhaseEFixture.read(100);PhaseDFixture.insert(db,fixture)
        val sent=store.prepare()!!;val batch=AcademicRecords.json.decodeFromString<AcademicBatch>(sent.body)
        assertEquals(671,batch.changes.size);db.close();db=DiaryDatabase.open(context,name)
        assertEquals(sent,store.prepare());store.accept(sent,AcademicPushReply(sent.id,true,1,JsonArray(emptyList())))
        assertNull(store.pending());assertTrue(store.dao.states().all{it.acknowledged==it.localVersion&&it.revision==1L});assertEquals(fixture,snapshot())
    }
    @Test fun concurrentLocalEditKeepsEveryFragmentAndEntireIncomingVariant():Unit=runBlocking {
        val fixture=PhaseEFixture.read(20);val parts=PhaseEFixture.fragments(fixture)
        assertTrue(store.receivePart(parts.first()));DiaryRepository(db).saveClass(fixture.classes.first().copy(name="Variante local"),"2026-01-01")
        val before=snapshot();var applied=true;for(part in parts.drop(1))applied=store.receivePart(part)
        assertFalse(applied);assertEquals(before,snapshot());assertEquals(0,store.cursor());assertEquals(1,store.dao.incoming().size)
        assertEquals(parts.size,store.dao.stagedParts().size);assertTrue(store.dao.incoming().single().body.contains("Turma fictícia d-source"))
    }
    @Test fun invalidMovementPreventsEveryAcademicWriteAndPersistsBlockedPage():Unit=runBlocking {
        val fixture=PhaseEFixture.read(20);val valid=PhaseEFixture.fragments(fixture);val records=valid.flatMap{it.records}.filter{it.kind!="relocations"}
        val parts=records.chunked(128).mapIndexed{i,p->AcademicFragment(valid.first().operation,1,records.size,i*128,p)}
        parts.dropLast(1).forEach{assertTrue(store.receivePart(it))};assertFalse(store.receivePart(parts.last()))
        assertTrue(snapshot().classes.isEmpty());assertEquals(0,store.cursor());assertFalse(store.dao.incoming().isEmpty());assertFalse(store.dao.stagedParts().isEmpty())
    }
    @Test fun crashAfterLastFragmentBeforeApplyReplaysThatFragmentAndRejectsForgedReplay():Unit=runBlocking {
        val fixture=PhaseEFixture.read(20);val parts=PhaseEFixture.fragments(fixture)
        parts.dropLast(1).forEach{assertTrue(store.receivePart(it))};val last=parts.last()
        val raw=AcademicRecords.json.encodeToString(last);val digest=BackupCodec.hash(AcademicRecords.canonical(AcademicRecords.json.encodeToJsonElement(last)).toByteArray())
        store.dao.insert(AcademicPart(1,last.offset,owner,last.operation,last.total,last.records.size,digest,raw))
        db.close();db=DiaryDatabase.open(context,name);assertEquals(last.offset,store.nextOffset());assertTrue(snapshot().classes.isEmpty())
        assertTrue(store.receivePart(last));assertEquals(fixture,snapshot());assertTrue(store.receivePart(last));assertEquals(1,store.cursor())
        try{store.receivePart(last.copy(records=last.records.reversed()));fail()}catch(_:IllegalStateException){}
        assertEquals(fixture,snapshot())
    }
    @Test fun migrationTwelveToThirteenPreservesFrozenQueueAndAcademicIdentities():Unit=runBlocking {
        val n="phasee-migration-${newId()}.db"
        helper.createDatabase(n,12).use{sql->
            sql.execSQL("INSERT INTO classes VALUES('legacy','Fictícia','Médio','História',2026,'Matutino','0,0,0,0,0,0,0',50,10,5,7,1,0)")
            sql.execSQL("INSERT INTO sync_academic_control VALUES(1,'$owner','ACTIVE_D',1,3,0,'CONFLICT',0)")
            sql.execSQL("INSERT INTO sync_academic_outbox VALUES('frozen','$owner','immutable-body','versions',1,2,'preserved')")
        }
        helper.runMigrationsAndValidate(n,13,true,ACADEMIC_MIGRATION_12_13).close()
        val migrated=DiaryDatabase.open(context,n)
        try {
            assertEquals("legacy",migrated.dao().schoolClass("legacy")!!.id);val dao=migrated.academicDao()
            assertEquals("ACTIVE_D",dao.control()!!.stage);assertEquals("immutable-body",dao.pending().single().body)
            assertEquals("preserved",dao.pending().single().conflict);assertTrue(dao.parts().isEmpty())
        }finally{migrated.close();context.deleteDatabase(n)}
    }
}
