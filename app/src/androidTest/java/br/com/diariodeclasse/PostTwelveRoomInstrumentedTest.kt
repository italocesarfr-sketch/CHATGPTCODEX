package br.com.diariodeclasse

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.domain.CsvRoster
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

class PostTwelveRoomInstrumentedTest {
    private lateinit var db:DiaryDatabase
    private lateinit var repo:DiaryRepository
    private lateinit var manager:BackupManager
    private lateinit var context:Context
    private val c=SchoolClass("ux12-room",name="Turma Fictícia Revisão",subject="História",year=2026)
    private val h=c.copy(id="ux12-other",name="Outra Turma Fictícia")
    @Before fun setup()=runBlocking {
        context=ApplicationProvider.getApplicationContext();context.deleteDatabase("ux12-test.db")
        db=Room.databaseBuilder(context,DiaryDatabase::class.java,"ux12-test.db").build();repo=DiaryRepository(db)
        manager=BackupManager(db,FileSafetyBackupStore(File(context.cacheDir,"ux12-safety")),22,null)
        repo.saveClass(c,"2026-01-01");repo.saveClass(h,"2026-01-01")
        val s=Student("ux12-student","ux12-person",c.id,"Estudante Fictício Matriculado",1,"2026-01-01")
        repo.saveStudent(s)
        val a=Activity("ux12-activity",c.id,"Avaliação Fictícia",date="2026-08-28",term=3,maximum=10.0)
        repo.saveActivity(a);repo.saveGrade(a.id,s.id,0.0);repo.setDayHours(c.id,"2026-08-28",1)
        repo.mark("${c.id}:2026-08-28:1",s.id,"FJ");repo.saveRecomposition(Recomposition(s.id,3,5.0,"2026-10-01"))
        repo.appendDrafts(c.id,listOf(CsvRoster.Row("Estudante Fictício Alfa",2),CsvRoster.Row("Estudante Fictício Beta",3)))
        repo.appendDrafts(h.id,listOf(CsvRoster.Row("Estudante Fictício Outra",1)))
    }
    @After fun clean(){db.close();context.deleteDatabase("ux12-test.db");File(context.cacheDir,"ux12-safety").deleteRecursively()}
    @Test fun discardOnlyCapturedDraftsPreservesEveryAcademicRowAndOtherImports()=runBlocking {
        val ids=repo.dao.draftRows(c.id).map{it.id}.toSet()
        repo.appendDrafts(c.id,listOf(CsvRoster.Row("Estudante Fictício Posterior",4)))
        val before=manager.snapshot();val bytes=manager.backupBytes()
        repo.discardDrafts(c.id,ids)
        val expected=before.copy(drafts=before.drafts.filterNot{it.id in ids})
        assertEquals(expected,manager.snapshot())
        db.close();db=Room.databaseBuilder(context,DiaryDatabase::class.java,"ux12-test.db").build();repo=DiaryRepository(db)
        manager=BackupManager(db,FileSafetyBackupStore(File(context.cacheDir,"ux12-safety")),22,null)
        assertEquals(expected,manager.snapshot())
        manager.restore(manager.prepare(bytes.inputStream()),"RESTAURAR");assertEquals(before,manager.snapshot())
    }
    @Test fun foreignIdsAndMidDeleteFailureLeaveTheWholeSnapshotUntouched()=runBlocking {
        val ids=repo.dao.draftRows(c.id).map{it.id}.toSet();val before=manager.snapshot()
        try{repo.discardDrafts(c.id,ids+repo.dao.draftRows(h.id).single().id);fail()}catch(_:IllegalArgumentException){}
        assertEquals(before,manager.snapshot())
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER ux12_fail BEFORE DELETE ON import_drafts WHEN OLD.id='${ids.last()}' BEGIN SELECT RAISE(ABORT,'fixture failure'); END")
        try{repo.discardDrafts(c.id,ids);fail()}catch(_:android.database.sqlite.SQLiteException){}
        assertEquals(before,manager.snapshot())
    }
}
