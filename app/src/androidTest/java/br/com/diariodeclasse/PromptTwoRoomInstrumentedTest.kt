package br.com.diariodeclasse

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.domain.CsvRoster
import br.com.diariodeclasse.export.*
import br.com.diariodeclasse.reports.SchoolReports
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

class PromptTwoRoomInstrumentedTest {
    private lateinit var context:Context;private lateinit var db:DiaryDatabase;private lateinit var repo:DiaryRepository;private lateinit var manager:BackupManager
    private val name="prompt-two-fixtures.db"
    private val c=SchoolClass("p2-room-c",name="Turma Fictícia Importação",subject="História",year=2026)
    private val h=c.copy(id="p2-room-h",name="Turma Fictícia Destino")
    private val s=Student("p2-room-s","p2-room-person",c.id,"Estudante Fictício Alfa",1,"2026-01-01")
    private fun open() {db=Room.databaseBuilder(context,DiaryDatabase::class.java,name).build();repo=DiaryRepository(db);manager=BackupManager(db,FileSafetyBackupStore(File(context.cacheDir,"p2-room-safety")),21,null)}
    @Before fun setup()=runBlocking {context=ApplicationProvider.getApplicationContext();context.deleteDatabase(name);open();repo.saveClass(c,"2026-01-01");repo.saveClass(h,"2026-01-01");repo.saveStudent(s)}
    @After fun clean(){db.close();context.deleteDatabase(name);File(context.cacheDir,"p2-room-safety").deleteRecursively()}
    @Test fun bulkReviewOnlyCapturedImportIdsDoesNotEnrollOrOverrideOtherFields()=runBlocking {
        repo.appendDrafts(c.id,listOf(CsvRoster.Row("Estudante Fictício Beta",2),CsvRoster.Row("Estudante Fictício Gama",3)))
        repo.appendDrafts(h.id,listOf(CsvRoster.Row("Estudante Fictício Outra Turma",1)))
        val before=manager.snapshot();val rows=repo.dao.draftRows(c.id);val ids=rows.map{it.id}.toSet()
        repo.reviewDrafts(c.id,ids,true)
        assertEquals(before.copy(drafts=before.drafts.map{if(it.id in ids)it.copy(reviewed=true)else it}),manager.snapshot())
        assertEquals(1,repo.dao.roster(c.id).size)
        repo.appendDrafts(c.id,listOf(CsvRoster.Row("Estudante Fictício Posterior",4)))
        repo.reviewDrafts(c.id,ids,false)
        assertTrue(repo.dao.draftRows(c.id).none{it.reviewed})
        val expected=manager.snapshot();val wrong=repo.dao.draftRows(h.id).single().id
        try{repo.reviewDrafts(c.id,ids+wrong,true);fail()}catch(_:IllegalArgumentException){}
        assertEquals(expected,manager.snapshot())
    }
    @Test fun bulkReviewFailureRollsBackAndDuplicateValidationStillBlocksEnrollment()=runBlocking {
        repo.appendDrafts(c.id,listOf(CsvRoster.Row(s.name,2),CsvRoster.Row("Estudante Fictício Delta",3)))
        val rows=repo.dao.draftRows(c.id);val before=manager.snapshot()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER p2_review_failure BEFORE UPDATE ON import_drafts WHEN OLD.id='${rows.last().id}' BEGIN SELECT RAISE(ABORT,'fictitious review failure'); END")
        try{repo.reviewDrafts(c.id,rows.map{it.id}.toSet(),true);fail()}catch(_:Exception){}
        assertEquals(before,manager.snapshot());db.openHelper.writableDatabase.execSQL("DROP TRIGGER p2_review_failure")
        repo.reviewDrafts(c.id,rows.map{it.id}.toSet(),true);val reviewed=manager.snapshot()
        try{repo.confirmImport(c.id,"2026-01-01");fail()}catch(_:IllegalArgumentException){}
        assertEquals(reviewed,manager.snapshot())
    }
    @Test fun secondTapPersistsNrRecalculatesFrequencyAndPreservesOtherDatesAndPupils()=runBlocking {
        val other=s.copy(id="p2-room-other",personId="p2-room-other-person",number=2,name="Estudante Fictício Outro")
        repo.saveStudent(other);repo.setDayHours(c.id,"2026-08-28",2);repo.setDayHours(c.id,"2026-08-29",1)
        val first=repo.dao.day(c.id,"2026-08-28").first();val next=repo.dao.day(c.id,"2026-08-29").first()
        repo.mark(next.id,s.id,"FJ");repo.mark(first.id,other.id,"F")
        val before=manager.snapshot()
        for(value in listOf("P","F","FJ")) {
            repo.toggleMark(first.id,s.id,value);assertEquals(value,repo.dao.attendance(first.id).first{it.studentId==s.id}.mark)
            repo.toggleMark(first.id,s.id,value);assertEquals("NR",repo.dao.attendance(first.id).first{it.studentId==s.id}.mark)
        }
        repo.toggleMark(first.id,s.id,"P");repo.toggleMark(first.id,s.id,"F");assertEquals("F",repo.dao.attendance(first.id).first{it.studentId==s.id}.mark)
        repo.toggleMark(first.id,s.id,"P");repo.toggleMark(first.id,s.id,"P")
        val snap=manager.snapshot();val d=snap.diarySnapshot();val f=SchoolReports(d,c).student(s,3).attendance!!
        assertEquals(3,f.performed);assertEquals(1,f.valid);assertEquals(0,f.present);assertEquals(0,f.absent);assertEquals(1,f.justified)
        assertEquals(before.marks,snap.marks.filterNot{it.lessonId==first.id&&it.studentId==s.id})
        db.close();open();assertEquals("NR",repo.dao.attendance(first.id).first{it.studentId==s.id}.mark)
        assertEquals(snap,manager.snapshot())
    }
    @Test fun exportAndShareAreReadOnlyAndNrAndReviewSurviveBackupRestoration()=runBlocking {
        repo.appendDrafts(c.id,listOf(CsvRoster.Row("Estudante Fictício Rascunho",2)));repo.reviewDrafts(c.id,repo.dao.draftRows(c.id).map{it.id}.toSet(),true)
        repo.setDayHours(c.id,"2026-08-28",2);val lessons=repo.dao.day(c.id,"2026-08-28")
        repo.mark(lessons[0].id,s.id,"FJ");repo.toggleMark(lessons[1].id,s.id,"P");repo.toggleMark(lessons[1].id,s.id,"P")
        val before=manager.snapshot();val table=AttendanceExportProjection.build(before.diarySnapshot(),c,"2026-08-01","2026-08-31")
        val file=SpreadsheetShare.prepare(context,table);val intent=SpreadsheetShare.intent(context,file)
        assertEquals(android.content.Intent.ACTION_SEND,intent.action)
        val uri=requireNotNull(intent.clipData).getItemAt(0).uri
        context.contentResolver.openInputStream(uri)!!.use{java.util.zip.ZipInputStream(it).use{zip->assertNotNull(zip.nextEntry)}}
        assertEquals(before,manager.snapshot());file.delete()
        try{SpreadsheetShare.intent(context,context.getDatabasePath(name));fail()}catch(_:IllegalArgumentException){}
        val backup=manager.backupBytes();repo.mark(lessons[1].id,s.id,"F");repo.reviewDrafts(c.id,repo.dao.draftRows(c.id).map{it.id}.toSet(),false)
        manager.restore(manager.prepare(backup.inputStream()),"RESTAURAR");assertEquals(before,manager.snapshot())
    }
}
