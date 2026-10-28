package br.com.diariodeclasse

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.export.*
import br.com.diariodeclasse.reports.SchoolReports
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Bancos exclusivos de teste, sem limpar diario.db. */
class RelocationConversionInstrumentedTest {
    private val context:Context get()=ApplicationProvider.getApplicationContext()
    private val name="relocation-conversion-fixture.db"
    private lateinit var db:DiaryDatabase
    private lateinit var repo:DiaryRepository
    private lateinit var backup:BackupManager
    private val x=SchoolClass("convert-x",name="Turma Fictícia X",subject="História",year=2026)
    private val g=x.copy(id="convert-g",name="Turma Fictícia G")
    private val h=x.copy(id="convert-h",name="Turma Fictícia H")
    private val s=Student("convert-s","convert-person",x.id,"Estudante Fictícia Conversão",1,"2026-01-01",notes="Observação legítima")
    private val other=s.copy(id="convert-other",personId="another-person",name="Outro Estudante Fictício",number=2)
    private val early=Lesson("convert-early",x.id,"2026-08-20",1,"REALIZADA")
    private val early2=early.copy(id="convert-early-double",ordinal=2)
    private val wrong=early.copy(id="convert-wrong",date="2026-09-20")
    private val a=Activity("convert-a",x.id,"Atividade fictícia",date=early.date,term=3,maximum=5.0)
    private fun open(){db=Room.databaseBuilder(context,DiaryDatabase::class.java,name).addMigrations(MIGRATION_7_8,MIGRATION_8_9,MIGRATION_9_10).build();repo=DiaryRepository(db);backup=BackupManager(db,FileSafetyBackupStore(File(context.cacheDir,"conversion-safety")),27,null)}
    @Before fun setup()=runBlocking {
        context.deleteDatabase(name);open()
        for(c in listOf(x,g,h))repo.saveClass(c,"2026-01-01")
        repo.saveStudent(s);repo.saveStudent(other)
        for(l in listOf(early,early2,wrong))repo.dao.save(l)
        repo.dao.save(Attendance(early.id,s.id,"P"));repo.dao.save(Attendance(early2.id,s.id,"FJ"));repo.dao.save(Attendance(wrong.id,s.id,"FJ"));repo.dao.save(Attendance(wrong.id,other.id,"F"))
        repo.saveActivity(a);repo.saveGrade(a.id,s.id,4.0);repo.saveRecomposition(Recomposition(s.id,3,5.0,"2026-08-21"))
        repo.confirmRelocation(repo.previewRelocation(s.id,null,"Matutino externo","2026-09-25"))
    }
    @After fun close(){db.close();context.deleteDatabase(name)}
    private suspend fun snapshot()=backup.snapshot().diarySnapshot()
    private suspend fun preview(remove:Set<String> =setOf(wrong.id))=repo.previewCorrectionDestination(s.id,"2026-09-17","",g.id,remove)
    private suspend fun fails(block:suspend()->Unit){try{block();fail("Operação insegura aceita")}catch(_:IllegalArgumentException){}}
    private suspend fun convertWithPrior(count:Int) {
        for(n in 1..count) {
            val day=if(n==3) "2026-09-17" else if(n==1) "2026-09-23" else "2026-09-22"
            repo.confirmRelocationCorrection(repo.previewRelocationCorrection(s.id,day,"Destino externo $n",if(n==3)setOf(wrong.id) else emptySet()))
        }
        val before=snapshot();val removals=if(count==3)emptySet() else setOf(wrong.id)
        val p=preview(removals);assertTrue(p.blockers.toString(),p.canConfirm)
        repo.confirmRelocationCorrection(p)
        val after=snapshot();val target=repo.dao.student(p.target!!.id)!!;val link=after.relocations.single()
        assertEquals(before.relocations.single().corrections,link.corrections.take(count));assertEquals(count+1,link.corrections.size)
        assertEquals("CONVERSAO_INTERNA",link.corrections.last().operation);assertNull(link.externalTarget);assertEquals(target.id,link.targetStudentId)
        assertEquals(s.personId,target.personId);assertNotEquals(s.id,target.id);assertEquals(g.id,target.classId);assertEquals("Ativo",target.status);assertEquals("2026-09-17",target.joined)
        assertEquals(before.students.first{it.id==s.id}.copy(left="2026-09-16"),after.students.first{it.id==s.id})
        assertEquals(before.students.size+1,after.students.size);assertEquals(1,after.students.count{it.classId==g.id&&it.personId==s.personId})
        assertEquals(before.marks.filterNot{it.studentId==s.id&&it.lessonId in removals},after.marks)
        assertEquals(before.grades,after.grades);assertEquals(before.recompositions,after.recompositions);assertEquals(before.activities,after.activities)
        assertTrue(after.marks.none{it.studentId==target.id});assertTrue(after.grades.none{it.studentId==target.id})
        assertEquals(before.copy(students=after.students,relocations=after.relocations,marks=after.marks),after)
        assertEquals(4.0,SchoolReports(after,g).student(target,3).original!!,0.0)
        assertEquals(5.0,SchoolReports(after,g).student(target,3).total!!,0.0)
        db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}
    }
    @Test fun conversionWithoutPreviousCorrection()=runBlocking{convertWithPrior(0)}
    @Test fun conversionAfterDateCorrection()=runBlocking{convertWithPrior(1)}
    @Test fun conversionAfterMultipleCorrections()=runBlocking{convertWithPrior(2)}
    @Test fun conversionAfterPreviouslyWithdrawnFJ()=runBlocking{convertWithPrior(3)}
    @Test fun existingCompatibleEnrollmentPreservesItsGradesAndFrequency()=runBlocking {
        val target=s.copy(id="existing-g",classId=g.id,number=7,joined="2026-09-17",notes="Observação do destino")
        repo.saveStudent(target)
        val lesson=early.copy(id="destination-lesson",classId=g.id,date="2026-09-18");repo.dao.save(lesson);repo.mark(lesson.id,target.id,"P")
        val activity=a.copy(id="destination-activity",classId=g.id,date=lesson.date);repo.saveActivity(activity);repo.saveGrade(activity.id,target.id,3.0)
        val before=snapshot();val p=preview();assertTrue(p.linkedExisting);assertEquals(target,p.target)
        repo.confirmRelocationCorrection(p);val after=snapshot()
        assertEquals(before.students.size,after.students.size);assertEquals(target,repo.dao.student(target.id));assertEquals(before.grades,after.grades)
        assertEquals(1,after.students.count{it.personId==s.personId&&it.classId==g.id})
        val report=SchoolReports(after,g).student(target,3);assertEquals(7.0,report.original!!,0.0);assertEquals(7.0,report.total!!,0.0)
        assertEquals(3,report.attendance!!.valid);assertEquals(2,report.attendance!!.present);assertEquals(1,report.attendance!!.justified)
    }
    @Test fun concurrencyAndInjectedFailureLeaveNoPartialConversionOrEnrollment()=runBlocking {
        val stale=preview();repo.dao.save(Attendance(wrong.id,other.id,"P"));val changed=snapshot()
        try{repo.confirmRelocationCorrection(stale);fail()}catch(_:RelocationCorrectionChanged){}
        assertEquals(changed,snapshot())
        val fresh=preview();db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_conversion BEFORE UPDATE ON relocations BEGIN SELECT RAISE(ABORT,'synthetic rollback'); END")
        try{repo.confirmRelocationCorrection(fresh);fail()}catch(_:android.database.sqlite.SQLiteException){}
        assertEquals(changed,snapshot());assertTrue(snapshot().students.none{it.classId==g.id})
        assertEquals("FJ",repo.dao.attendance(wrong.id).first{it.studentId==s.id}.mark)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_conversion")
        repo.confirmRelocationCorrection(preview());assertEquals(1,snapshot().students.count{it.classId==g.id})
    }
    @Test fun legacyV8AuditJsonSurvivesReopenConversionBackupAndRestore()=runBlocking {
        repo.confirmRelocationCorrection(repo.previewRelocationCorrection(s.id,"2026-09-23","Noturno corrigido"))
        val prior=snapshot().relocations.single().corrections
        val json=JsonArray(prior.map{val obj=BackupCodec.json.parseToJsonElement(BackupCodec.json.encodeToString(it)).jsonObject;JsonObject(obj.filterKeys{it !in setOf("operation","sourceStudentId","targetStudentId","targetClassId","targetLabel")})}).toString()
        db.openHelper.writableDatabase.execSQL("UPDATE relocations SET corrections=? WHERE sourceStudentId=?",arrayOf(json,s.id))
        db.close();open();assertEquals(prior,snapshot().relocations.single().corrections)
        repo.confirmRelocationCorrection(preview());val converted=snapshot();val bytes=backup.backupBytes()
        assertEquals(12,BackupCodec.decode(bytes).envelope.formatVersion)
        db.close();open();assertEquals(converted,snapshot())
        repo.saveStudent(other.copy(name="Nome fictício alterado"));backup.restore(backup.prepare(bytes.inputStream()),"RESTAURAR")
        assertEquals(converted,snapshot());assertEquals(prior,snapshot().relocations.single().corrections.take(prior.size))
        db.openHelper.writableDatabase.query("SELECT identity_hash FROM room_master_table WHERE id=42").use{assertTrue(it.moveToFirst());assertEquals(BackupCodec.IDENTITY,it.getString(0))}
    }
    @Test fun conversionHistoryAndReportsRemainAfterSuccessiveMovesAndClassDeletion()=runBlocking {
        val p=preview();repo.confirmRelocationCorrection(p);val target=p.target!!
        val lesson=early.copy(id="dest-p",classId=g.id,date="2026-09-18");repo.dao.save(lesson);repo.mark(lesson.id,target.id,"P")
        val activity=a.copy(id="dest-a",classId=g.id,date=lesson.date);repo.saveActivity(activity);repo.saveGrade(activity.id,target.id,3.0)
        val move=repo.previewRelocation(target.id,h.id,null,"2026-10-01");repo.confirmRelocation(move)
        val final=move.target!!;val before=snapshot();val report=SchoolReports(before,h).student(final,3)
        assertEquals(7.0,report.total!!,0.0);assertEquals(3,report.attendance!!.valid)
        for(c in listOf(x,g)){val deletion=repo.previewClassDeletion(c.id);assertNull(deletion.blockedReason);repo.deleteClass(deletion,deletion.confirmation)}
        val after=snapshot();assertEquals(3,after.lineage(final).size)
        val consolidated=SchoolReports(after,h).student(final,3)
        assertEquals(report.original,consolidated.original);assertEquals(report.total,consolidated.total);assertEquals(report.attendance,consolidated.attendance)
        assertEquals("CONVERSAO_INTERNA",after.academic().relocations.first{it.sourceStudentId==s.id}.corrections.single().operation)
        val table=ExportProjection.build(after,h,ExportOptions(term=3));assertTrue(table.records.any{it.enrollmentId==final.id})
        val bytes=backup.backupBytes();db.close();open();assertEquals(after,snapshot())
        backup.restore(backup.prepare(bytes.inputStream()),"RESTAURAR");assertEquals(after,snapshot())
    }
    @Test fun pendingDestinationRecordsAndRecompositionConflictNeverGetAdjustedSilently()=runBlocking {
        val target=s.copy(id="existing",classId=g.id,joined="2026-09-17");repo.saveStudent(target)
        repo.saveRecomposition(Recomposition(target.id,3,7.0,"2026-09-18"))
        val before=snapshot();val p=preview();assertFalse(p.canConfirm);assertTrue(p.blockers.any{it.contains("duas recomposições")})
        fails{repo.confirmRelocationCorrection(p)};assertEquals(before,snapshot())
        repo.deleteRecomposition(Recomposition(target.id,3,7.0,"2026-09-18"))
        repo.archiveClass(g.id,true);val archived=snapshot();fails{preview()};assertEquals(archived,snapshot())
    }
    @Test fun previewAndCancellationNeverWriteAndInternalCorrectionRemainsProtected()=runBlocking {
        val before=snapshot();val p=preview();assertEquals(before,snapshot());db.close();open();assertEquals(before,snapshot())
        repo.confirmRelocationCorrection(p);val converted=snapshot()
        fails{repo.previewRelocationCorrection(s.id,"2026-09-18","Não alterar internamente")};assertEquals(converted,snapshot())
    }
}
