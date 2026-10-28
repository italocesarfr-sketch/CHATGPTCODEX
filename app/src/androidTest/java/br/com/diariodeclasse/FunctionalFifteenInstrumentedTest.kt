package br.com.diariodeclasse

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.export.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

class FunctionalFifteenInstrumentedTest {
    @get:Rule val helper=MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),DiaryDatabase::class.java.canonicalName!!,FrameworkSQLiteOpenHelperFactory())
    private val context:Context get()=ApplicationProvider.getApplicationContext()
    private val name="functional-fifteen-only.db"
    private lateinit var db:DiaryDatabase
    private lateinit var repo:DiaryRepository
    private lateinit var backup:BackupManager
    private val c=SchoolClass("fifteen-c",name="Turma Fictícia Quinze",subject="História",year=2026)
    private val s=Student("fifteen-s","fifteen-p",c.id,"Estudante Fictícia Quinze",1,"2026-01-01",notes="Preservar observação")
    private val other=s.copy(id="fifteen-other",personId="other-person",number=2)
    private val day="2026-10-20"
    private val l=Lesson("fifteen-l",c.id,day,1,"REALIZADA")
    private fun open() {
        db=Room.databaseBuilder(context,DiaryDatabase::class.java,name).addMigrations(MIGRATION_7_8,MIGRATION_8_9,MIGRATION_9_10).build()
        repo=DiaryRepository(db);backup=BackupManager(db,FileSafetyBackupStore(File(context.cacheDir,"fifteen-safety")),25,null)
    }
    @Before fun setup()=runBlocking { context.deleteDatabase(name);open();repo.saveClass(c,"2026-01-01");repo.saveStudent(s);repo.saveStudent(other);db.dao().save(l) }
    @After fun close(){db.close();context.deleteDatabase(name)}
    private suspend fun snapshot()=backup.snapshot().diarySnapshot()
    private suspend fun relocate() {repo.confirmRelocation(repo.previewRelocation(s.id,null,"foi pro noturno","2026-10-25"))}
    private suspend fun fails(block:suspend()->Unit){try{block();fail("Operação insegura aceita")}catch(_:IllegalArgumentException){}}
    @Test fun externalCorrectionWithdrawsOnlyExplicitFJAndPreservesAllOtherRecords()=runBlocking {
        val early=l.copy(id="early",date="2026-08-20");db.dao().save(early);db.dao().save(Attendance(early.id,s.id,"P"))
        db.dao().save(Attendance(l.id,s.id,"FJ"));db.dao().save(Attendance(l.id,other.id,"F"))
        val a=Activity("early-a",c.id,"Prova fictícia",date="2026-08-20",term=3,maximum=10.0);repo.saveActivity(a);repo.saveGrade(a.id,s.id,0.0)
        repo.saveRecomposition(Recomposition(s.id,3,4.5,"2026-09-01"))
        relocate();val before=snapshot()
        val blocked=repo.previewRelocationCorrection(s.id,"2026-10-17","foi pro noturno")
        assertFalse(blocked.canConfirm);fails{repo.confirmRelocationCorrection(blocked)}
        val p=repo.previewRelocationCorrection(s.id,"2026-10-17","Noturno corrigido",setOf(l.id))
        repo.confirmRelocationCorrection(p)
        val after=snapshot();val corrected=after.students.first{it.id==s.id}
        assertEquals(s.id,corrected.id);assertEquals(s.personId,corrected.personId);assertEquals("Remanejado",corrected.status);assertEquals("2026-10-16",corrected.left);assertEquals(s.notes,corrected.notes)
        assertEquals(before.grades,after.grades);assertEquals(before.recompositions,after.recompositions);assertEquals(before.activities,after.activities)
        assertEquals(before.marks.filterNot{it.studentId==s.id&&it.lessonId==l.id},after.marks)
        assertFalse(after.marks.any{it.studentId==s.id&&it.lessonId==l.id})
        assertEquals("FJ",after.relocations.single().corrections.single().removed.single().attendance.mark)
        assertEquals(0,after.frequency(corrected,null,true).justified)
        assertTrue(repo.diagnoseDayHours(c.id,day).blockers.any{it.contains("referência histórica")})
        db.close();open();assertEquals(ClassLifecycle.fingerprint(after),ClassLifecycle.fingerprint(snapshot()))
        val bytes=backup.backupBytes();repo.saveStudent(other.copy(name="Nome fictício corrigido"));backup.restore(backup.prepare(bytes.inputStream()),"RESTAURAR")
        assertEquals(ClassLifecycle.fingerprint(after),ClassLifecycle.fingerprint(snapshot()))
    }
    @Test fun correctionRevalidatesPreviewAndRollsBackOnFailure()=runBlocking {
        db.dao().save(Attendance(l.id,s.id,"FJ"));relocate()
        val preview=repo.previewRelocationCorrection(s.id,"2026-10-17","Noturno",setOf(l.id));val before=snapshot()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_correction BEFORE UPDATE ON students BEGIN SELECT RAISE(ABORT,'simulated failure'); END")
        try{repo.confirmRelocationCorrection(preview);fail()}catch(_:android.database.sqlite.SQLiteException){}
        assertEquals(ClassLifecycle.fingerprint(before),ClassLifecycle.fingerprint(snapshot()))
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_correction")
        db.dao().save(Attendance(l.id,other.id,"P"));val changed=snapshot()
        fails{repo.confirmRelocationCorrection(preview)};assertEquals(ClassLifecycle.fingerprint(changed),ClassLifecycle.fingerprint(snapshot()))
    }
    @Test fun successiveDateAndDestinationCorrectionsPreserveAuditAcrossRestartAndRestore()=runBlocking {
        db.dao().save(Attendance(l.id,s.id,"FJ"));relocate()
        val before=snapshot()
        for((date,destination) in listOf("2026-10-23" to "foi pro noturno", "2026-10-23" to "Noturno corrigido", "2026-10-22" to "Destino externo confirmado")) {
            repo.confirmRelocationCorrection(repo.previewCorrectionDestination(s.id,date,destination,null))
        }
        val after=snapshot();val link=after.relocations.single()
        assertEquals(3,link.corrections.size);assertEquals(before.marks,after.marks)
        assertEquals(before.students.first{it.id==other.id},after.students.first{it.id==other.id})
        assertEquals(before.students.first{it.id==s.id}.copy(left="2026-10-21"),after.students.first{it.id==s.id})
        assertEquals(listOf("2026-10-25","2026-10-23","2026-10-23"),link.corrections.map{it.previousDate})
        assertTrue(link.corrections.all{it.removed.isEmpty()})
        val bytes=backup.backupBytes();db.close();open();assertEquals(ClassLifecycle.fingerprint(after),ClassLifecycle.fingerprint(snapshot()))
        repo.saveStudent(other.copy(name="Outro nome fictício"));backup.restore(backup.prepare(bytes.inputStream()),"RESTAURAR")
        assertEquals(ClassLifecycle.fingerprint(after),ClassLifecycle.fingerprint(snapshot()))
        db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}
    }
    @Test fun registeredDestinationLinksExistingEnrollmentWithoutExternalTextOrDuplication()=runBlocking {
        val target=c.copy(id="correction-target",name="Turma Fictícia Destino")
        repo.saveClass(target,"2026-01-01")
        repo.saveStudent(s.copy(id="existing-target",classId=target.id,joined="2026-10-25"))
        relocate();val before=snapshot()
        for(id in listOf(c.id,"missing")) fails {repo.previewCorrectionDestination(s.id,"2026-10-17","Não salvar como texto",id)}
        assertEquals(ClassLifecycle.fingerprint(before),ClassLifecycle.fingerprint(snapshot()))
        assertEquals(1,snapshot().students.count{it.classId==target.id&&it.personId==s.personId})
        assertEquals("foi pro noturno",snapshot().relocations.single().externalTarget)
        val p=repo.previewCorrectionDestination(s.id,"2026-10-25","Não salvar como texto",target.id)
        assertEquals("existing-target",p.target!!.id);assertTrue(p.linkedExisting)
        repo.confirmRelocationCorrection(p)
        assertNull(snapshot().relocations.single().externalTarget)
        assertEquals("existing-target",snapshot().relocations.single().targetStudentId)
        assertEquals(1,snapshot().students.count{it.classId==target.id&&it.personId==s.personId})
    }
    @Test fun nrOnlyCleanupIsExplicitAtomicAndLimitedToSelectedClassAndDay()=runBlocking {
        val next=l.copy(id="next-day",date="2026-10-21");db.dao().save(next);db.dao().save(Attendance(next.id,s.id,"FJ"))
        db.dao().save(Attendance(l.id,s.id,"NR"));val p=repo.diagnoseDayHours(c.id,day)
        assertTrue(p.canRemove);fails{repo.clearDiagnosedDay(p,false)}
        repo.clearDiagnosedDay(p,true);assertTrue(db.dao().day(c.id,day).isEmpty());assertTrue(db.dao().attendance(l.id).isEmpty());assertEquals("FJ",db.dao().attendance(next.id).single().mark)
        db.close();open();assertTrue(db.dao().day(c.id,day).isEmpty());db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}
    }
    @Test fun meaningfulMarksMixedNRAndConcurrentChangesBlockRemoval()=runBlocking {
        for(mark in listOf("P","F","FJ")) {
            db.dao().save(Attendance(l.id,s.id,mark));val p=repo.diagnoseDayHours(c.id,day)
            assertFalse(p.canRemove);fails{repo.clearDiagnosedDay(p,true)};assertEquals(mark,db.dao().attendance(l.id).single().mark)
        }
        db.dao().save(Attendance(l.id,s.id,"NR"));val p=repo.diagnoseDayHours(c.id,day)
        db.dao().save(Attendance(l.id,other.id,"P"));fails{repo.clearDiagnosedDay(p,true)};assertEquals(2,db.dao().attendance(l.id).size)
    }
    @Test fun cleanupFailureRestoresNRAndHours()=runBlocking {
        db.dao().save(Attendance(l.id,s.id,"NR"));val before=snapshot();val p=repo.diagnoseDayHours(c.id,day)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_cleanup BEFORE DELETE ON lessons BEGIN SELECT RAISE(ABORT,'simulated failure'); END")
        try{repo.clearDiagnosedDay(p,true);fail()}catch(_:android.database.sqlite.SQLiteException){}
        assertEquals(ClassLifecycle.fingerprint(before),ClassLifecycle.fingerprint(snapshot()))
    }
    @Test fun journalActivitiesRecompositionsAndNotesAreProtected()=runBlocking {
        repo.saveLessonRecord(LessonRecord("fifteen-r",c.id,day,"Título fictício","Descrição"));assertFalse(repo.diagnoseDayHours(c.id,day).canRemove)
        val a=Activity("fifteen-a",c.id,"Prova fictícia",date=day,term=4,maximum=10.0);repo.saveActivity(a)
        repo.saveRecomposition(Recomposition(s.id,4,null,day));db.dao().save(l.copy(note="Observação"))
        val p=repo.diagnoseDayHours(c.id,day);assertEquals(4,p.blockers.size);fails{repo.clearDiagnosedDay(p,true)}
    }
    @Test fun internalRelocationCannotBeCorrectedUnilaterally()=runBlocking {
        val dest=c.copy(id="fifteen-dest",name="Destino fictício");repo.saveClass(dest,"2026-01-01")
        repo.confirmRelocation(repo.previewRelocation(s.id,dest.id,null,"2026-10-17"));val before=snapshot()
        fails{repo.previewRelocationCorrection(s.id,"2026-10-18","Externo")};assertEquals(ClassLifecycle.fingerprint(before),ClassLifecycle.fingerprint(snapshot()))
    }
    @Test fun auditSurvivesDeletedIntermediateOriginWithoutDuplicatingAcademicData()=runBlocking {
        val dest=c.copy(id="fifteen-dest",name="Destino fictício");repo.saveClass(dest,"2026-01-01")
        val p=repo.previewRelocation(s.id,dest.id,null,"2026-09-01");repo.confirmRelocation(p)
        val target=snapshot().students.single{it.classId==dest.id}
        repo.confirmRelocation(repo.previewRelocation(target.id,null,"Noturno","2026-10-25"))
        repo.confirmRelocationCorrection(repo.previewRelocationCorrection(target.id,"2026-10-26","Noturno corrigido"))
        val before=snapshot();val deletion=repo.previewClassDeletion(dest.id);repo.deleteClass(deletion,deletion.confirmation)
        val after=snapshot();assertEquals(1,after.academic().relocations.first{it.sourceStudentId==target.id}.corrections.size)
        assertEquals(before.grades,after.academic().grades);BackupCodec.validate(backup.snapshot())
    }
    @Test fun additiveMigrationFromDistributedV7KeepsAllFourteenTablesAndOldBackup()=runBlocking {
        db.close();context.deleteDatabase(name)
        helper.createDatabase(name,7).use { sql ->
            sql.execSQL("INSERT INTO classes VALUES ('old','Turma fictícia','Médio','História',2026,'Matutino','0,0,0,0,0,0,0',50,10,5,7,1,0)")
            sql.execSQL("INSERT INTO students VALUES ('old-s','old-person','old','Estudante Fictícia Antiga',1,'2026-01-01','2026-10-16','Remanejado','Preservar')")
            sql.execSQL("INSERT INTO relocations VALUES ('old-s',NULL,'2026-10-17','Noturno')")
            sql.execSQL("INSERT INTO lessons VALUES ('old-l','old','2026-08-20',2,'REALIZADA','Original')")
            sql.execSQL("INSERT INTO marks VALUES ('old-l','old-s','FJ')")
        }
        helper.runMigrationsAndValidate(name,8,true,MIGRATION_7_8).use { migrated ->
            migrated.query("PRAGMA user_version").use { assertTrue(it.moveToFirst());assertEquals(8,it.getInt(0)) }
        }
        open()
        val current=snapshot();assertEquals("old-s",current.students.single().id);assertEquals("FJ",current.marks.single().mark);assertTrue(current.relocations.single().corrections.isEmpty())
        db.openHelper.writableDatabase.query("PRAGMA user_version").use{assertTrue(it.moveToFirst());assertEquals(10,it.getInt(0))}
        assertTrue(backup.snapshot().timetables.isEmpty())
        val payload=BackupCodec.json.encodeToString(BackupSnapshot.serializer(),backup.snapshot())
        val old=BackupEnvelope("diario-de-classe-backup",8,"br.com.diariodeclasse",7,BackupCodec.V7_IDENTITY,"2026-10-21T00:00:00Z",24,null,BackupCodec.hash(payload.toByteArray()),payload)
        val decoded=BackupCodec.decode(BackupCodec.json.encodeToString(BackupEnvelope.serializer(),old).toByteArray())
        assertEquals(backup.snapshot(),decoded.snapshot)
    }
}
