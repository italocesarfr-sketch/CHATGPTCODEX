package br.com.diariodeclasse

import android.content.ContentValues
import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.reports.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.decodeFromString
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.io.File

class RelocationInstrumentedTest {
    @get:Rule val helper=MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),DiaryDatabase::class.java.canonicalName!!,FrameworkSQLiteOpenHelperFactory())
    private val context:Context get()=ApplicationProvider.getApplicationContext()
    private val name="relocation-exclusive.db"
    private lateinit var db:DiaryDatabase
    private lateinit var repo:DiaryRepository
    private lateinit var manager:BackupManager
    private lateinit var folder:File
    private val g=SchoolClass(id="rel-g",name="1º G Fictício",subject="História",year=2026,schedule="0,0,0,2,1,0,0")
    private val h=g.copy(id="rel-h",name="1º H Fictício",schedule="0,0,3,0,0,0,0")
    private val student=Student(id="rel-a",personId="rel-person",classId=g.id,name="Estudante Fictício Alfa",number=1,joined="2026-01-01")
    private val other=student.copy(id="rel-other",personId="rel-other-person",name="Estudante Fictício Distinto",number=2)
    private val old=Activity("rel-old",g.id,"Trabalho Fictício Origem",date="2026-08-10",term=3,maximum=5.0)
    private val next=old.copy(id="rel-next",classId=h.id,name="Trabalho Fictício Destino",date="2026-09-10")
    private fun open() {
        db=Room.databaseBuilder(context,DiaryDatabase::class.java,name).addMigrations(MIGRATION_1_2,MIGRATION_2_3,MIGRATION_3_4,MIGRATION_4_5, MIGRATION_5_6).build()
        repo=DiaryRepository(db);manager=BackupManager(db,FileSafetyBackupStore(folder),11,null)
    }
    @Before fun setup() {runBlocking {
        context.deleteDatabase(name);folder=File(context.cacheDir,"relocation-${java.util.UUID.randomUUID()}");open()
        repo.saveClass(g,"2026-01-01");repo.saveClass(h,"2026-01-01");repo.saveStudent(student);repo.saveStudent(other)
        repo.saveActivity(old);repo.saveActivity(next);repo.saveGrade(old.id,student.id,2.0);repo.saveGrade(old.id,other.id,4.0)
        repo.setDayHours(g.id,"2026-08-10",2);repo.mark("${g.id}:2026-08-10:1",student.id,"P");repo.mark("${g.id}:2026-08-10:2",student.id,"F")
        repo.setDayHours(h.id,"2026-09-10",3)
    }}
    @After fun cleanup() {db.close();context.deleteDatabase(name);folder.deleteRecursively()}
    private suspend fun current()=withTimeout(10000){repo.snapshot.first {it.loaded}}
    private suspend fun preview(day:String="2026-09-01")=repo.previewRelocation(student.id,h.id,null,day)
    private suspend fun rejected(block:suspend ()->Unit) {try {block();fail("Deveria bloquear")}catch(_:IllegalArgumentException){}}
    @Test fun transferredThenReactivatedEnrollmentCanRelocateAfterReopenWithoutLosingHistory() {runBlocking {
        repo.saveRecomposition(Recomposition(student.id,3,5.0,"2026-08-20"))
        val before=manager.snapshot()
        repo.saveStudent(student.copy(status="Transferido",left="2026-10-16"))
        val transferred=repo.dao.student(student.id)!!
        assertEquals("Transferido",transferred.status)
        repo.saveStudent(transferred.copy(status="Ativo")) // Mesma edição do formulário 0.7.0: a saída antiga é reenviada.
        db.close();open()
        val active=repo.dao.student(student.id)!!
        assertEquals("Ativo",active.status);assertEquals(student.id,active.id);assertEquals(student.personId,active.personId)
        val p=repo.previewRelocation(active.id,h.id,null,"2026-10-17")
        repo.confirmRelocation(p)
        val after=manager.snapshot()
        assertEquals(before.grades,after.grades);assertEquals(before.marks,after.marks)
        assertEquals(before.recompositions,after.recompositions)
        assertEquals(1,after.relocations.size)
        assertEquals(before.students.size+1,after.students.size)
        assertEquals(student.personId,p.target!!.personId)
    }}
    @Test fun reactivationClearsOnlyTheExitAndImmediateRelocationKeepsIdentity() {runBlocking {
        val closed=student.copy(status="Transferido",left="2026-10-16",notes="Observação fictícia preservada")
        repo.saveStudent(closed)
        repo.saveStudent(closed.copy(status="Ativo"))
        assertEquals(closed.copy(status="Ativo",left=null),repo.dao.student(student.id))
        val before=manager.snapshot();val p=repo.previewRelocation(student.id,h.id,null,"2026-10-17")
        repo.confirmRelocation(p)
        assertEquals(student.id,repo.dao.student(student.id)!!.id)
        assertEquals(student.personId,p.target!!.personId)
        assertEquals(before.grades,manager.snapshot().grades);assertEquals(before.marks,manager.snapshot().marks)
        assertEquals(1,manager.snapshot().students.count {it.classId==h.id&&it.personId==student.personId})
    }}
    @Test fun legacyActiveExitRequiresExplicitRepairAndBackupNeverRepairsAutomatically() {runBlocking {
        repo.saveRecomposition(Recomposition(student.id,3,5.0,"2026-08-20"))
        val stale=student.copy(left="2026-10-16",notes="Caso fictício da versão 0.7.0")
        repo.dao.save(stale) // Simula exatamente o estado persistido pela versão distribuída.
        val before=manager.snapshot();val oldBackup=manager.backupBytes()
        db.close();open();assertEquals(before,manager.snapshot())
        expectMessage("Estado inconsistente") {repo.previewRelocation(student.id,h.id,null,"2026-10-17")}
        repo.saveStudent(stale.copy(notes="Correção fictícia de observação"))
        assertEquals(stale.left,repo.dao.student(student.id)!!.left) // Salvar nome/observações não reabre a matrícula.
        manager.restore(manager.prepare(oldBackup.inputStream()),"RESTAURAR")
        assertEquals(before,manager.snapshot())
        repo.repairReactivatedStudent(stale)
        assertEquals(stale.copy(left=null),repo.dao.student(student.id))
        assertEquals(before.grades,manager.snapshot().grades);assertEquals(before.marks,manager.snapshot().marks)
        assertEquals(before.recompositions,manager.snapshot().recompositions)
        val p=repo.previewRelocation(student.id,h.id,null,"2026-10-17");repo.confirmRelocation(p)
        val moved=manager.snapshot();val newBackup=manager.backupBytes()
        db.close();open();assertEquals(moved,manager.snapshot())
        manager.restore(manager.prepare(oldBackup.inputStream()),"RESTAURAR");assertEquals(before,manager.snapshot())
        manager.restore(manager.prepare(newBackup.inputStream()),"RESTAURAR");assertEquals(moved,manager.snapshot())
        assertEquals(1,moved.relocations.size);assertEquals(before.students.size+1,moved.students.size)
        assertEquals(5.0,SchoolReports(current(),h).student(p.target!!,3).total!!,0.0)
    }}
    private suspend fun expectMessage(fragment:String,action:suspend ()->Unit) {
        try {action();fail("Deveria bloquear: $fragment")}catch(error:IllegalArgumentException) {
            assertTrue("Mensagem recebida: ${error.message}",error.message?.contains(fragment)==true)
        }
    }
    @Test fun closedRelocatedSameDestinationAndInvalidDatesHaveDistinctMessages() {runBlocking {
        val closed=student.copy(status="Transferido",left="2026-10-16");repo.saveStudent(closed)
        val before=manager.snapshot()
        expectMessage("Matrícula encerrada") {preview()};assertEquals(before,manager.snapshot())
        repo.saveStudent(closed.copy(status="Ativo"))
        expectMessage("diferente da turma de origem") {repo.previewRelocation(student.id,g.id,null,"2026-10-17")}
        for(day in listOf("2026-02-30","2025-10-17","2026-01-01"))
            expectMessage("Data de remanejamento inválida") {repo.previewRelocation(student.id,h.id,null,day)}
        val p=preview();repo.confirmRelocation(p);val moved=manager.snapshot()
        expectMessage("Matrícula já remanejada") {preview()}
        expectMessage("Matrícula já remanejada") {repo.saveStudent(repo.dao.student(student.id)!!.copy(status="Ativo",left=null))}
        assertEquals(moved,manager.snapshot())
    }}
    @Test fun administrativeTransferWithExistingSuccessorCannotBeReopenedOrDuplicated() {runBlocking {
        repo.transfer(student.id,h.id,"2026-09-01")
        val before=manager.snapshot();val closed=repo.dao.student(student.id)!!
        expectMessage("outra matrícula") {repo.saveStudent(closed.copy(status="Ativo"))}
        assertEquals(before,manager.snapshot());assertTrue(before.relocations.isEmpty())
        assertEquals(2,before.students.count {it.personId==student.personId})
    }}
    @Test fun failedOrStaleExplicitRepairDoesNotChangeAnyTable() {runBlocking {
        val stale=student.copy(left="2026-10-16");repo.dao.save(stale)
        val before=manager.snapshot()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_reactivation BEFORE UPDATE ON students WHEN OLD.id='rel-a' AND NEW.`left` IS NULL BEGIN SELECT RAISE(ABORT,'test-only'); END")
        try {repo.repairReactivatedStudent(stale);fail()}catch(_:android.database.sqlite.SQLiteException){}
        assertEquals(before,manager.snapshot())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_reactivation")
        repo.saveStudent(stale.copy(notes="Observação alterada após abrir formulário"));val changed=manager.snapshot()
        expectMessage("dados mudaram") {repo.repairReactivatedStudent(stale)}
        assertEquals(changed,manager.snapshot())
    }}
    @Test fun internalMoveCombinesPointsAndDifferentGridsWithoutCopyingAnyHistory() {runBlocking {
        val before=manager.snapshot();val p=preview();assertEquals(before,manager.snapshot())
        repo.confirmRelocation(p);val target=p.target!!
        assertEquals(student.personId,target.personId);assertEquals("Remanejado",repo.dao.student(student.id)!!.status)
        assertEquals("2026-08-31",repo.dao.student(student.id)!!.left)
        repo.saveGrade(next.id,target.id,3.0)
        repo.mark("${h.id}:2026-09-10:1",target.id,"P");repo.mark("${h.id}:2026-09-10:2",target.id,"FJ")
        val data=current();val reports=SchoolReports(data,h);val row=reports.student(target,3)
        assertEquals(5.0,row.original!!,0.0);assertEquals(10.0,row.grades.maximum,0.0)
        assertEquals(5,row.attendance!!.performed);assertEquals(4,row.attendance!!.valid)
        assertEquals(2,row.attendance!!.present);assertEquals(1,row.attendance!!.absent);assertEquals(1,row.attendance!!.justified)
        assertEquals(50.0,row.attendance!!.percent!!,0.0)
        assertEquals(listOf(other.id),SchoolReports(data,g).term(3).students.map {it.student.id})
        assertEquals(2.0,SchoolReports(data,g,true).student(data.students.first {it.id==student.id},3).original!!,0.0)
        val after=manager.snapshot()
        assertEquals(before.grades.filter {it.studentId==other.id},after.grades.filter {it.studentId==other.id})
        assertEquals(before.students.first {it.id==other.id},after.students.first {it.id==other.id})
        assertEquals(before.marks,after.marks.filter {it.studentId!=target.id})
        assertEquals(before.activities,after.activities)
        assertTrue(after.grades.none {it.studentId==target.id&&it.activityId==old.id})
        assertEquals(3,reports.attendanceHistory(target).count {it.school.id==h.id})
    }}
    @Test fun sourceWritesBulkPresenceReactivationAndRepeatedMoveArePrevented() {runBlocking {
        val p=preview();repo.confirmRelocation(p);val preserved=repo.dao.attendance("${g.id}:2026-08-10:2").first {it.studentId==student.id}
        rejected {repo.mark("${g.id}:2026-08-10:2",student.id,"P")}
        rejected {repo.saveGrade(old.id,student.id,5.0)}
        rejected {repo.saveStudent(repo.dao.student(student.id)!!.copy(status="Ativo",left=null))}
        rejected {preview()};rejected {repo.confirmRelocation(p)}
        repo.allPresentDay(g.id,"2026-08-10")
        assertEquals(preserved,repo.dao.attendance("${g.id}:2026-08-10:2").first {it.studentId==student.id})
        rejected {repo.previewRelocation(p.target!!.id,g.id,null,"2026-10-01")}
        assertEquals(1,manager.snapshot().relocations.size)
    }}
    @Test fun cancelledPreviewStalePreviewAndMidTransactionFailureLeaveAllTenTablesIntact() {runBlocking {
        val before=manager.snapshot();val p=preview();assertEquals(before,manager.snapshot()) // Cancelar apenas descarta esta prévia.
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_relocation BEFORE INSERT ON relocations BEGIN SELECT RAISE(ABORT,'test-only'); END")
        try {repo.confirmRelocation(p);fail()}catch(_:android.database.sqlite.SQLiteException){}
        assertEquals(before,manager.snapshot())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_relocation")
        repo.saveGrade(old.id,other.id,4.5);val changed=manager.snapshot()
        rejected {repo.confirmRelocation(p)};assertEquals(changed,manager.snapshot())
    }}
    @Test fun externalMoveOnlyClosesSourceAndPersistsDestination() {runBlocking {
        val before=manager.snapshot();val p=repo.previewRelocation(student.id,null,"Outra Turma Fictícia Externa","2026-09-01")
        assertNull(p.target);repo.confirmRelocation(p)
        val after=manager.snapshot();assertEquals(before.students.size,after.students.size);assertEquals(before.classes,after.classes)
        assertEquals(before.marks,after.marks);assertEquals(before.grades,after.grades)
        assertEquals("Outra Turma Fictícia Externa",after.relocations.single().externalTarget)
        db.close();open();assertEquals(after,manager.snapshot())
    }}
    @Test fun preexistingRecompositionIsOneRecordAndCanBeCorrectedExplicitlyFromDestination() {runBlocking {
        val rec=Recomposition(student.id,3,7.0,"2026-08-20");repo.saveRecomposition(rec)
        val p=preview();repo.confirmRelocation(p);val target=p.target!!
        repo.saveGrade(next.id,target.id,3.0)
        assertEquals(7.0,SchoolReports(current(),h).student(target,3).total!!,0.0)
        rejected {repo.saveRecomposition(Recomposition(target.id,3,8.0,"2026-11-20"))}
        rejected {repo.saveRecomposition(rec.copy(value=8.0),rec)}
        val updated=rec.copy(value=8.0);repo.saveRecomposition(updated,rec,target.id)
        assertEquals(listOf(updated),manager.snapshot().recompositions)
        assertEquals(8.0,SchoolReports(current(),h).student(target,3).total!!,0.0)
        repo.deleteRecomposition(updated,target.id)
        assertEquals(5.0,SchoolReports(current(),h).student(target,3).total!!,0.0)
        repo.applyActivityEdit(repo.previewActivityEdit(old.copy(maximum=2.5)),GradeChangeMode.PROPORTIONAL)
        assertEquals(4.0,SchoolReports(current(),h).student(target,3).total!!,0.0)
        assertEquals(1.0,repo.dao.activityGrades(old.id).first {it.studentId==student.id}.value!!,0.0)
    }}
    @Test fun conflictingRecompositionsFutureMarksAndDifferentPoliciesAreBlockedWithoutMutation() {runBlocking {
        repo.mark("${g.id}:2026-08-10:1",student.id,"FJ")
        val before=manager.snapshot();rejected {preview("2026-08-10")};assertEquals(before,manager.snapshot())
        repo.saveClass(h.copy(countJustified=false),"2026-01-01");val incompatible=manager.snapshot()
        rejected {preview()};assertEquals(incompatible,manager.snapshot())
        repo.saveClass(h,"2026-01-01")
        val existing=student.copy(id="rel-existing",classId=h.id,joined="2026-09-01")
        repo.saveStudent(existing)
        repo.saveRecomposition(Recomposition(student.id,3,5.0,"2026-08-20"))
        repo.saveRecomposition(Recomposition(existing.id,3,null,"2026-11-20"))
        val conflicted=manager.snapshot();rejected {preview()};assertEquals(conflicted,manager.snapshot())
    }}
    @Test fun linkingSameIdentityAndChainsDoNotCreateDuplicateEnrollments() {runBlocking {
        val existing=student.copy(id="rel-existing",classId=h.id,joined="2026-09-01")
        repo.saveStudent(existing);val p=preview();assertTrue(p.linkedExisting);assertEquals(existing,p.target)
        repo.confirmRelocation(p);assertEquals(3,manager.snapshot().students.size)
        val third=g.copy(id="rel-third",name="1º I Fictício");repo.saveClass(third,"2026-01-01")
        repo.saveGrade(next.id,existing.id,3.0)
        val second=repo.previewRelocation(existing.id,third.id,null,"2026-10-01");repo.confirmRelocation(second)
        val data=current();val target=second.target!!
        assertEquals(listOf(student.id,existing.id,target.id),data.lineage(target).map {it.id})
        assertEquals(5.0,SchoolReports(data,third).student(target,3).original!!,0.0)
        assertEquals(2,data.grades.count {it.studentId in data.lineage(target).map {s->s.id}})
        rejected {repo.previewRelocation(target.id,g.id,null,"2026-11-01")}
        rejected {repo.saveClass(h.copy(scale=20.0),"2026-01-01")}
        val terms=repo.dao.periods(h.id);rejected {repo.saveTerms(h.id,terms.map {if(it.number==3)it.copy(start="2026-07-02")else it})}
    }}
    @Test fun zeroPendingAndBackupRoundTripSurviveDatabaseReopen() {runBlocking {
        repo.saveGrade(old.id,student.id,0.0)
        val p=preview();repo.confirmRelocation(p);val target=p.target!!
        val expected=manager.snapshot();val bytes=manager.backupBytes()
        db.close();open();assertEquals(expected,manager.snapshot())
        val row=SchoolReports(current(),h).student(target,3)
        assertEquals(0.0,row.original!!,0.0);assertEquals(1,row.grades.pending)
        repo.saveGrade(next.id,target.id,4.0)
        manager.restore(manager.prepare(bytes.inputStream()),"RESTAURAR")
        assertEquals(expected,manager.snapshot());assertEquals(expected,BackupCodec.decode(bytes).snapshot)
        val restored=SchoolReports(current(),h).student(target,3)
        assertEquals(0.0,restored.total!!,0.0);assertEquals(1,restored.grades.pending)
    }}
    @Test fun failedRestoreWithLinksRollsBackTheWholeGraph() {runBlocking {
        val p=preview();repo.confirmRelocation(p);val bytes=manager.backupBytes()
        repo.saveGrade(next.id,p.target!!.id,3.0);val current=manager.snapshot()
        val prepared=manager.prepare(bytes.inputStream())
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_restore_link BEFORE INSERT ON relocations BEGIN SELECT RAISE(ABORT,'test-only'); END")
        try {manager.restore(prepared,"RESTAURAR");fail()}catch(_:android.database.sqlite.SQLiteException){}
        assertEquals(current,manager.snapshot());assertTrue(manager.hasSafetyCopy())
        assertEquals(current,BackupCodec.decode(FileSafetyBackupStore(folder).latest()!!.readBytes()).snapshot)
    }}
    @Test fun migrationFromDistributedV2PreservesEveryRowAndRecomposition() {runBlocking {
        db.close();context.deleteDatabase(name)
        val raw=InstrumentationRegistry.getInstrumentation().context.assets.open("baseline-v1.json").bufferedReader().use {it.readText()}
        val expected=BackupCodec.json.decodeFromString<BackupSnapshot>(raw).copy(recompositions=listOf(Recomposition("alfa",1,0.0,"2026-11-20"),Recomposition("alfa",3,6.0,"2026-11-20"),Recomposition("gama",3,null,"2026-11-20"))).canonical()
        val json=JSONObject(BackupCodec.json.encodeToString(BackupSnapshot.serializer(),expected))
        helper.createDatabase(name,2).use {sql->
            for(field in listOf("classes","students","terms","lessons","activities","marks","grades","drafts","recompositions")) {
                val rows=json.getJSONArray(field)
                for(i in 0 until rows.length()) {
                    val row=rows.getJSONObject(i);val values=ContentValues()
                    row.keys().forEach {key->when(val v=row.get(key)) {JSONObject.NULL->values.putNull(key);is Boolean->values.put(key,if(v)1 else 0);else->values.put(key,v.toString())}}
                    sql.insert(if(field=="drafts") "import_drafts" else field,0,values)
                }
            }
        }
        helper.runMigrationsAndValidate(name,3,true,MIGRATION_2_3).close();open()
        assertEquals(expected,manager.snapshot());assertTrue(manager.snapshot().relocations.isEmpty())
        assertEquals(6.0,SchoolReports(current(),expected.classes.first {it.id=="classe-alfa"}).student(expected.students.first {it.id=="alfa"},3).total!!,0.0)
        db.close();open();assertEquals(expected,manager.snapshot())
    }}
}
