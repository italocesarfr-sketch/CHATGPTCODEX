package br.com.diariodeclasse

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.domain.CsvRoster
import br.com.diariodeclasse.export.*
import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.sync.academic.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.ByteArrayOutputStream

/** Actual repository commands and Room triggers; no fake insertion of the four audited kinds. */
class AcademicAuxiliaryCaptureTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db:DiaryDatabase
    private lateinit var name:String
    private val repo get()=DiaryRepository(db)
    private val store get()=AcademicStore(db,"11111111-1111-4111-8111-111111111111")
    private val c=SchoolClass("aux-source",name="Turma Fictícia Origem",subject="História",year=2026)
    private val student=Student("aux-student","aux-person",c.id,"Estudante Fictício",1,"2026-01-01")
    @Before fun open():Unit=runBlocking {
        name="aux-${newId()}.db";db=DiaryDatabase.open(context,name);assertTrue(store.prepareCommercialBootstrap())
        repo.saveClass(c,"2026-01-01");repo.saveStudent(student)
    }
    @After fun close(){db.close();context.deleteDatabase(name)}
    @Test fun exportingFilesWithoutCustomizationDoesNotCreateExportRowsOrDirtyOperations():Unit=runBlocking {
        val before=store.dao.states();val data=repo.readSnapshot()
        val table=ExportProjection.build(data,c,ExportOptions(term=1))
        val xlsx=ByteArrayOutputStream();val csv=ByteArrayOutputStream()
        SpreadsheetWriter.xlsx(table,xlsx);SpreadsheetWriter.csv(table,csv)
        assertTrue(xlsx.size()>0);assertTrue(csv.size()>0)
        assertTrue(db.backupDao().exportRows().isEmpty());assertEquals(before,store.dao.states())
    }
    @Test fun archiveCustomLayoutAndUnconfirmedImportCreateOnlyTheirPersistedKinds():Unit=runBlocking {
        repo.archiveClass(c.id,true)
        val row=ExportLayout.studentRow(student,0).copy(manualHidden=true)
        repo.saveExportLayout(c.id,emptyList(),listOf(row))
        repo.appendDrafts(c.id,listOf(CsvRoster.Row("Nome Fictício Pendente",2)))
        val snap=ClassLifecycle.snapshot(repo.readSnapshot())
        assertEquals(1,snap.classArchives.size);assertEquals(listOf(row),snap.exportRows);assertEquals(1,snap.drafts.size)
        for(kind in listOf("class_archives","export_rows","import_drafts")) {
            assertTrue(AcademicRecords.rows(snap).any{it.kind==kind})
            assertTrue(store.dao.states().any{it.kind==kind&&it.localVersion>it.acknowledged})
        }
        assertTrue(snap.histories.isEmpty());assertEquals(1,snap.students.size)
    }
    @Test fun deletionOfAnOriginWithValidMovementCreatesPreservedHistoryAndCapturesIt():Unit=runBlocking {
        val target=c.copy(id="aux-target",name="Destino Fictício");repo.saveClass(target,"2026-01-01")
        repo.saveActivity(Activity("aux-activity",c.id,"Atividade Fictícia",date="2026-08-10",term=3,maximum=10.0))
        repo.saveGrade("aux-activity",student.id,5.0)
        repo.confirmRelocation(repo.previewRelocation(student.id,target.id,null,"2026-09-01"))
        val before=repo.readSnapshot();val destination=before.students.single{it.classId==target.id}
        val preview=repo.previewClassDeletion(c.id);assertNull(preview.blockedReason)
        repo.deleteClass(preview,preview.confirmation)
        val snap=ClassLifecycle.snapshot(repo.readSnapshot())
        assertNotNull(repo.dao.student(destination.id));assertEquals(destination,repo.dao.student(destination.id))
        assertTrue(snap.histories.any{it.classId==c.id&&it.content.grades.any{g->g.studentId==student.id&&g.value==5.0}})
        assertTrue(AcademicRecords.rows(snap).any{it.kind=="preserved_histories"})
        assertTrue(store.dao.states().any{it.kind=="preserved_histories"&&it.localVersion>it.acknowledged})
        db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}
    }
}
