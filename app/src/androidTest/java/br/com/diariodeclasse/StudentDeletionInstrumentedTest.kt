package br.com.diariodeclasse

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.export.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import java.io.File

class StudentDeletionInstrumentedTest {
    private val context:Context get()=ApplicationProvider.getApplicationContext()
    private val name="student-deletion-exclusive.db"
    private lateinit var db:DiaryDatabase; private lateinit var repo:DiaryRepository; private lateinit var manager:BackupManager
    private lateinit var folder:File
    private val g=SchoolClass("student-delete-g",name="1º G fictício",subject="História",year=2026)
    private val h=g.copy(id="student-delete-h",name="1º H fictício")
    private val kept=Student("delete-kept","delete-person-kept",g.id,"Estudante Fictício Homônimo",1,"2026-01-01",notes="Preservar")
    private val wrong=kept.copy(id="delete-wrong",personId="delete-person-wrong",classId=h.id)
    private fun open(){db=Room.databaseBuilder(context,DiaryDatabase::class.java,name).build();repo=DiaryRepository(db);manager=BackupManager(db,FileSafetyBackupStore(folder),33,null)}
    private suspend fun data()=manager.snapshot().diarySnapshot()
    @Before fun setup()=runBlocking {
        context.deleteDatabase(name);folder=File(context.cacheDir,"student-delete-${newId()}");open()
        for((c,s) in listOf(g to kept,h to wrong)){
            repo.saveClass(c,"2026-01-01");repo.saveStudent(s)
            repo.saveActivity(Activity("activity-${c.id}",c.id,"Atividade fictícia",date="2026-08-10",term=3,maximum=10.0))
            repo.saveGrade("activity-${c.id}",s.id,0.0)
            repo.setDayHours(c.id,"2026-08-10",4)
            for((index,mark) in listOf("P","F","FJ","NR").withIndex())repo.mark("${c.id}:2026-08-10:${index+1}",s.id,mark)
            repo.saveRecomposition(Recomposition(s.id,3,5.0,"2026-08-20"))
            repo.saveLessonRecord(LessonRecord("record-${c.id}",c.id,"2026-08-10","Registro fictício","Preservar o texto da turma"))
            repo.saveExportLayout(c.id,emptyList(),listOf(ExportLayout.studentRow(s,0),ExportRow("blank:${c.id}",c.id,null,1)))
        }
    }
    @After fun cleanup(){db.close();context.deleteDatabase(name);folder.deleteRecursively()}
    @Test fun deletesEmptyEnrollmentWithoutChangingOtherRows()=runBlocking {
        val empty=wrong.copy(id="delete-empty",personId="empty-person",name="Aluno Fictício Sem Registros",number=2,notes="")
        repo.saveStudent(empty);val before=data();val p=repo.previewStudentDeletion(empty.id,h.id)
        assertTrue(p.counts.isEmpty());repo.deleteStudent(p)
        assertEquals(before.copy(students=before.students.filterNot{it.id==empty.id}),data())
    }
    @Test fun duplicateWithGradesAttendanceRecompositionPreservesEntireOtherEnrollmentAndSharedRecords()=runBlocking {
        val before=data();val reports=br.com.diariodeclasse.reports.SchoolReports(before,g).student(kept,3)
        val export=ExportProjection.build(before,g,ExportOptions(term=3))
        val p=repo.previewStudentDeletion(wrong.id,h.id)
        assertEquals(1,p.counts["Atividades com notas lançadas"]);assertEquals(4,p.counts["Registros de frequência"]);assertEquals(1,p.counts["Recomposições com nota"])
        repo.deleteStudent(p)
        assertEquals(ClassLifecycle.fingerprint(StudentDeletion.without(before,wrong.id)),ClassLifecycle.fingerprint(data()))
        val afterReport=br.com.diariodeclasse.reports.SchoolReports(data(),g).student(kept,3)
        // GradeSummary/Frequency são classes Java sem equals por valor. Confere todos os campos.
        assertEquals(reports.copy(grades=afterReport.grades,frequency=afterReport.frequency),afterReport)
        assertEquals(listOf(reports.grades.total,reports.grades.maximum,reports.grades.pending,reports.grades.partial),
            listOf(afterReport.grades.total,afterReport.grades.maximum,afterReport.grades.pending,afterReport.grades.partial))
        assertEquals(reports.attendance,afterReport.attendance)
        assertEquals(listOf(reports.original,reports.total,reports.enrollment,reports.launched,reports.hasData,reports.resultScale),
            listOf(afterReport.original,afterReport.total,afterReport.enrollment,afterReport.launched,afterReport.hasData,afterReport.resultScale))
        assertEquals(export,ExportProjection.build(data(),g,ExportOptions(term=3)))
        assertNull(repo.dao.student(wrong.id));assertEquals(kept,repo.dao.student(kept.id))
        val expected=manager.snapshot();val bytes=manager.backupBytes()
        db.close();open();assertEquals(expected,manager.snapshot())
        repo.saveStudent(wrong);manager.restore(manager.prepare(bytes.inputStream()),"RESTAURAR")
        assertEquals(expected,manager.snapshot())
        db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}
    }
    @Test fun cancelledPreviewIsReadOnlyAndChangedGradeWithSameCountBlocksConfirmation()=runBlocking {
        val before=manager.snapshot();val p=repo.previewStudentDeletion(wrong.id,h.id)
        assertEquals(before,manager.snapshot()) // cancelar o popup não invoca deleteStudent
        repo.saveGrade("activity-${h.id}",wrong.id,7.0);val changed=manager.snapshot()
        try{repo.deleteStudent(p);fail("Prévia obsoleta")}catch(e:IllegalArgumentException){assertTrue(e.message!!.contains("mudaram"))}
        assertEquals(changed,manager.snapshot())
    }
    @Test fun failureAfterDependentDeletesRollsBackEveryTable()=runBlocking {
        val before=manager.snapshot();val p=repo.previewStudentDeletion(wrong.id,h.id)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_student_delete BEFORE DELETE ON students WHEN OLD.id='delete-wrong' BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END")
        try{repo.deleteStudent(p);fail("Falha simulada deveria abortar")}catch(_:android.database.sqlite.SQLiteException){}
        assertEquals(before,manager.snapshot())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_student_delete")
        repo.deleteStudent(repo.previewStudentDeletion(wrong.id,h.id));assertNull(repo.dao.student(wrong.id))
    }
    @Test fun incomingOutgoingAndPreservedHistoryRelocationsRemainProtected()=runBlocking {
        val target=g.copy(id="delete-relocated",name="Destino fictício")
        repo.saveClass(target,"2026-01-01")
        val p=repo.previewRelocation(wrong.id,target.id,null,"2026-09-01");repo.confirmRelocation(p)
        val before=manager.snapshot()
        for((id,school) in listOf(wrong.id to h.id,p.target!!.id to target.id)){
            val blocked=repo.previewStudentDeletion(id,school);assertNotNull(blocked.blockedReason)
            try{repo.deleteStudent(blocked);fail("Histórico compartilhado")}catch(_:IllegalArgumentException){}
        }
        assertEquals(before,manager.snapshot())
        val deletion=repo.previewClassDeletion(h.id);repo.deleteClass(deletion,deletion.confirmation)
        val preserved=manager.snapshot();val blocked=repo.previewStudentDeletion(p.target!!.id,target.id)
        assertNotNull(blocked.blockedReason)
        try{repo.deleteStudent(blocked);fail("Histórico de origem excluída")}catch(_:IllegalArgumentException){}
        assertEquals(preserved,manager.snapshot())
    }
    @Test fun externalRelocationWithAdministrativeAuditCannotBeErased()=runBlocking {
        repo.confirmRelocation(repo.previewRelocation(wrong.id,null,"Destino externo fictício","2026-09-01"))
        repo.confirmRelocationCorrection(repo.previewRelocationCorrection(wrong.id,"2026-09-05","Destino corrigido fictício",emptySet()))
        val before=manager.snapshot();val p=repo.previewStudentDeletion(wrong.id,h.id)
        assertEquals(1,p.counts["Correções administrativas vinculadas"]);assertNotNull(p.blockedReason)
        try{repo.deleteStudent(p);fail("Auditoria compartilhada")}catch(_:IllegalArgumentException){}
        assertEquals(before,manager.snapshot())
    }
    @Test fun crossClassAcademicReferenceBlocksInsteadOfDeletingAnotherClassRecord()=runBlocking {
        repo.dao.save(Grade("activity-${g.id}",wrong.id,3.0)) // FK válida; vínculo acadêmico inconsistente fictício
        val before=manager.snapshot();val p=repo.previewStudentDeletion(wrong.id,h.id)
        assertTrue(p.blockedReason!!.contains("outra turma"))
        try{repo.deleteStudent(p);fail("Referência de outra turma")}catch(_:IllegalArgumentException){}
        assertEquals(before,manager.snapshot())
    }

    @Test fun unrelatedPreservedHistoryNeverBlocksOrChangesIndependentEnrollmentDeletion()=runBlocking {
        val previous=manager.snapshot()
        val fixture=BackupCodec.json.decodeFromString<BackupSnapshot>(InstrumentationRegistry.getInstrumentation().context.assets.open("history-deletion.json").bufferedReader().use{it.readText()})
        val seeded=previous.copy(classes=previous.classes+fixture.classes,students=previous.students+fixture.students,
            terms=previous.terms+fixture.terms,lessons=previous.lessons+fixture.lessons,marks=previous.marks+fixture.marks,
            activities=previous.activities+fixture.activities,grades=previous.grades+fixture.grades,
            recompositions=previous.recompositions+fixture.recompositions,relocations=previous.relocations+fixture.relocations)
        manager.restore(manager.prepare(BackupCodec.encode(seeded,33,null).inputStream()),"RESTAURAR")
        for(id in listOf("history-x","history-g")){val p=repo.previewClassDeletion(id);repo.deleteClass(p,p.confirmation)}
        val before=data();assertEquals(2,before.histories.size)
        val p=repo.previewStudentDeletion(wrong.id,h.id);assertNull(p.blockedReason)
        repo.deleteStudent(p)
        assertEquals(ClassLifecycle.fingerprint(StudentDeletion.without(before,wrong.id)),ClassLifecycle.fingerprint(data()))
        assertEquals(before.histories,data().histories)
    }

}
