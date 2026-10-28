package br.com.diariodeclasse

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.reports.SchoolReports
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

class FjFlowInstrumentedTest {
    private val context:Context get()=ApplicationProvider.getApplicationContext()
    private val name="fj-flow-exclusive.db"
    private lateinit var db:DiaryDatabase
    private lateinit var repo:DiaryRepository
    private lateinit var backup:BackupManager
    private val c=SchoolClass(id="fj-flow-c",name="Turma Fictícia",subject="História",year=2026)
    private val s=Student(id="fj-flow-s",classId=c.id,name="Estudante Fictícia",number=1,joined="2026-01-01")
    private val other=s.copy(id="fj-flow-other",personId="other-person",number=2)
    private val first="${c.id}:2026-08-10:1";private val second="${c.id}:2026-08-10:2"
    private fun open(){db=Room.databaseBuilder(context,DiaryDatabase::class.java,name).build();repo=DiaryRepository(db);backup=BackupManager(db,FileSafetyBackupStore(File(context.cacheDir,"fj-flow-safety")),37,null)}
    @Before fun setup()=runBlocking{context.deleteDatabase(name);open();repo.saveClass(c,"2026-01-01");repo.saveStudent(s);repo.saveStudent(other);repo.setDayHours(c.id,"2026-08-10",2)}
    @After fun cleanup(){db.close();context.deleteDatabase(name)}
    private suspend fun current(id:String=first,student:String=s.id)=repo.dao.attendance(id).single{it.studentId==student}
    private suspend fun fails(block:suspend()->Unit){try{block();fail("Alteração insegura aceita")}catch(_:IllegalArgumentException){}}
    @Test fun eachReasonAndRemovalApplyAtomicallyToOnlyTheirOwnAbsence()=runBlocking {
        repo.mark(first,s.id,"F");repo.mark(second,s.id,"FJ");repo.setJustification(current(second),"Outro")
        repo.mark(first,other.id,"P");val before=backup.snapshot()
        for(reason in JustificationReasons.options) {
            repo.justifyAbsence(current(),reason)
            assertEquals(Attendance(first,s.id,"FJ",reason),current())
            val report=SchoolReports(backup.snapshot().diarySnapshot(),c).student(s,3)
            assertEquals(2,report.attendance!!.justified);assertEquals(0,report.attendance!!.absent)
            assertEquals("Outro",current(second).reason);assertEquals("P",current(first,other.id).mark)
        }
        repo.justifyAbsence(current(),null);assertEquals(Attendance(first,s.id,"F"),current())
        val after=backup.snapshot();assertEquals(before.copy(marks=after.marks),after)
    }
    @Test fun bigPAndFAlwaysSetTheirStateAndClearOnlyTheSelectedReason()=runBlocking {
        repo.mark(first,s.id,"F");repo.justifyAbsence(current(),"Atestado médico")
        repo.mark(second,s.id,"FJ");repo.setJustification(current(second),"Outro");val kept=current(second)
        for(value in listOf("P","F")) {
            repo.mark(first,s.id,value);repo.mark(first,s.id,value)
            assertEquals(Attendance(first,s.id,value),current());assertEquals(kept,current(second))
            repo.mark(first,s.id,"F");repo.justifyAbsence(current(),"Convocação oficial")
        }
        repo.mark(first,s.id,"FJ");repo.setJustification(current(),null)
        repo.justifyAbsence(current(),null);assertEquals("F",current().mark)
        repo.mark(first,s.id,"NR");fails{repo.justifyAbsence(current(),"Outro")};assertNull(current().reason)
    }
    @Test fun staleSelectionAndSqlFailureLeaveTheWholeSnapshotUnchanged()=runBlocking {
        repo.mark(first,s.id,"F");val stale=current();repo.mark(first,s.id,"P")
        val before=backup.snapshot();fails{repo.justifyAbsence(stale,"Outro")};assertEquals(before,backup.snapshot())
        repo.mark(first,s.id,"F")
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_fj_flow BEFORE UPDATE ON marks BEGIN SELECT RAISE(ABORT,'fixture'); END")
        val frozen=backup.snapshot()
        try{repo.justifyAbsence(current(),"Outro");fail()}catch(_:android.database.sqlite.SQLiteException){}
        assertEquals(frozen,backup.snapshot());db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_fj_flow")
        repo.justifyAbsence(current(),"Atestado médico");val selected=current();repo.justifyAbsence(current(),"Outro")
        val after=backup.snapshot();fails{repo.justifyAbsence(selected,null)};assertEquals(after,backup.snapshot())
    }
    @Test fun restartBackupAndProtectedCorrectionKeepExistingAcademicHistory()=runBlocking {
        repo.mark(first,s.id,"F");repo.justifyAbsence(current(),"Atividade escolar ou esportiva")
        val late=Lesson("fj-flow-late",c.id,"2026-10-20",1,"REALIZADA");repo.dao.save(late)
        repo.mark(late.id,s.id,"F");repo.justifyAbsence(current(late.id),"Atestado médico")
        repo.confirmRelocation(repo.previewRelocation(s.id,null,"Destino Fictício Externo","2026-10-25"))
        val frozen=backup.snapshot();fails{repo.justifyAbsence(current(),null)};assertEquals(frozen,backup.snapshot())
        val correction=repo.previewRelocationCorrection(s.id,"2026-10-17","Destino Fictício Externo",setOf(late.id));repo.confirmRelocationCorrection(correction)
        assertEquals("Atestado médico",backup.snapshot().relocations.single().corrections.single().removed.single().attendance.reason)
        assertEquals("Atividade escolar ou esportiva",current().reason)
        val saved=backup.snapshot();db.close();open();assertEquals(saved,backup.snapshot())
        val bytes=backup.backupBytes();repo.saveStudent(other.copy(name="Nome Fictício Alterado"))
        backup.restore(backup.prepare(bytes.inputStream()),"RESTAURAR");assertEquals(saved,backup.snapshot())
        assertEquals(12,BackupCodec.decode(bytes).envelope.formatVersion)
        db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}
    }
}
