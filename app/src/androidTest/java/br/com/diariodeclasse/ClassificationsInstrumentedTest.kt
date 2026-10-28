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
import br.com.diariodeclasse.export.*
import br.com.diariodeclasse.reports.SchoolReports
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

class ClassificationsInstrumentedTest {
    @get:Rule val helper=MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),DiaryDatabase::class.java.canonicalName!!,FrameworkSQLiteOpenHelperFactory())
    private val context:Context get()=ApplicationProvider.getApplicationContext()
    private val name="classifications-exclusive.db"
    private lateinit var db:DiaryDatabase
    private lateinit var repo:DiaryRepository
    private lateinit var backup:BackupManager
    private val c=SchoolClass(id="classifications-c",name="Turma Fictícia",subject="História",year=2026)
    private val s=Student(id="classifications-s",classId=c.id,name="Estudante Fictício Áurea",number=1,joined="2026-01-01")
    private val other=s.copy(id="classifications-other",personId="other-person",number=2)
    private val day="2026-08-10"
    private val first="${c.id}:$day:1";private val second="${c.id}:$day:2"
    private fun open(){db=Room.databaseBuilder(context,DiaryDatabase::class.java,name).addMigrations(MIGRATION_9_10).build();repo=DiaryRepository(db);backup=BackupManager(db,FileSafetyBackupStore(File(context.cacheDir,"classifications-safety")),36,null)}
    @Before fun setup()=runBlocking{context.deleteDatabase(name);open();repo.saveClass(c,"2026-01-01");repo.saveStudent(s);repo.saveStudent(other);repo.setDayHours(c.id,day,2)}
    @After fun cleanup(){db.close();context.deleteDatabase(name)}
    private suspend fun mark(lesson:String=first,student:String=s.id)=repo.dao.attendance(lesson).single{it.studentId==student}
    private suspend fun reason(value:String?,lesson:String=first,student:String=s.id){repo.setJustification(mark(lesson,student),value)}
    private suspend fun data()=backup.snapshot().diarySnapshot()
    private suspend fun fails(block:suspend()->Unit){try{block();fail("Alteração insegura aceita")}catch(_:IllegalArgumentException){}}
    @Test fun everyReasonIsOptionalPerOccurrenceAndSurvivesRestartAndRestore()=runBlocking {
        repo.mark(first,s.id,"FJ");repo.mark(second,s.id,"FJ");reason("Outro",second)
        repo.mark(first,other.id,"FJ");reason("Acompanhamento médico",first,other.id)
        val before=backup.snapshot();val frequency=SchoolReports(data(),c).student(s,3).attendance
        for(value in JustificationReasons.options) {
            reason(value);assertEquals(value,mark().reason)
            assertEquals(frequency,SchoolReports(data(),c).student(s,3).attendance)
            assertEquals("Outro",mark(second).reason);assertEquals("Acompanhamento médico",mark(first,other.id).reason)
        }
        reason(null);assertNull(mark().reason);reason("Atestado médico")
        val after=backup.snapshot();assertEquals(before.copy(marks=after.marks),after)
        db.close();open();assertEquals(after,backup.snapshot())
        val bytes=backup.backupBytes();reason("Outro");backup.restore(backup.prepare(bytes.inputStream()),"RESTAURAR")
        assertEquals(after,backup.snapshot());assertEquals(12,BackupCodec.decode(bytes).envelope.formatVersion)
    }
    @Test fun changingOrClearingMarksRemovesOnlyItsOwnReasonAndCopyIncludesTheChosenFJ()=runBlocking {
        repo.mark(first,s.id,"FJ");reason("Atestado médico");repo.mark(second,other.id,"FJ");reason("Outro",second,other.id)
        val kept=mark(second,other.id)
        for(next in listOf("P","F","NR")) {
            repo.mark(first,s.id,"FJ");reason("Convocação oficial");repo.mark(first,s.id,next)
            assertEquals(next,mark().mark);assertNull(mark().reason);assertEquals(kept,mark(second,other.id))
        }
        repo.mark(first,s.id,"F");repo.toggleMark(first,s.id,"FJ");assertNull(mark().reason)
        reason("Falecimento familiar");repo.copyFirst(second);assertEquals("Falecimento familiar",mark(second).reason)
        repo.toggleMark(first,s.id,"FJ");assertEquals("NR",mark().mark);assertNull(mark().reason)
        assertEquals("Falecimento familiar",mark(second).reason)
        repo.allPresent(second);assertTrue(repo.dao.attendance(second).all{it.mark=="P"&&it.reason==null})
    }
    @Test fun staleChoiceAndSqlFailureRollbackNeverResurrectAnAttendance()=runBlocking {
        repo.mark(first,s.id,"FJ");val stale=mark();repo.mark(first,s.id,"F")
        val before=backup.snapshot();fails{repo.setJustification(stale,"Outro")};assertEquals(before,backup.snapshot())
        repo.mark(first,s.id,"FJ");val current=mark()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_reason BEFORE UPDATE OF reason ON marks BEGIN SELECT RAISE(ABORT,'fixture'); END")
        val frozen=backup.snapshot()
        try {repo.setJustification(current,"Outro");fail()}catch(_:android.database.sqlite.SQLiteException){}
        assertEquals(frozen,backup.snapshot());db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_reason")
        reason("Outro");fails{repo.setJustification(current,"Atestado médico")};assertEquals("Outro",mark().reason)
    }
    @Test fun newActivityCategoryPersistsWithItsOriginalGradeAndExportProjection()=runBlocking {
        val a=Activity(id="classification-text",classId=c.id,name="Texto Fictício",type="Produção textual",date=day,term=3,maximum=10.0)
        repo.saveActivity(a);repo.saveGrade(a.id,s.id,0.0)
        repo.saveActivity(a.copy(name="Texto Fictício Editado"));db.close();open()
        assertEquals(a.copy(name="Texto Fictício Editado"),repo.dao.activity(a.id))
        val row=SchoolReports(data(),c).student(s,3);assertEquals("Produção textual",row.activities.single().activity.type);assertEquals(0.0,row.original!!,0.0)
        val table=ExportProjection.build(data(),c,ExportOptions(term=3));assertEquals(0.0,table.records.first{it.enrollmentId==s.id}.cells[table.columns.indexOfFirst{it.key=="activity:${a.id}"}].number!!,0.0)
    }
    @Test fun historicalCorrectionArchivesTheReasonAndDetectsConcurrentChanges()=runBlocking {
        val late=Lesson(id="classification-late",classId=c.id,date="2026-10-20",ordinal=1,state="REALIZADA");repo.dao.save(late)
        repo.mark(late.id,s.id,"FJ");reason("Atestado médico",late.id)
        repo.mark(first,s.id,"P");repo.mark(first,other.id,"FJ");reason("Outro",first,other.id)
        repo.confirmRelocation(repo.previewRelocation(s.id,null,"Destino Fictício Externo","2026-10-25"))
        val preview=repo.previewRelocationCorrection(s.id,"2026-10-17","Destino Fictício Externo",setOf(late.id))
        // A edição normal de matrícula remanejada permanece bloqueada.
        fails{repo.setJustification(mark(late.id),"Outro")}
        repo.dao.save(mark(late.id).copy(reason="Convocação oficial"))
        fails{repo.confirmRelocationCorrection(preview)}
        val valid=repo.previewRelocationCorrection(s.id,"2026-10-17","Destino Fictício Externo",setOf(late.id));repo.confirmRelocationCorrection(valid)
        assertFalse(repo.dao.attendance(late.id).any{it.studentId==s.id})
        assertEquals("Convocação oficial",backup.snapshot().relocations.single().corrections.single().removed.single().attendance.reason)
        assertEquals("P",mark().mark);assertEquals("Outro",mark(first,other.id).reason)
        val bytes=backup.backupBytes();backup.restore(backup.prepare(bytes.inputStream()),"RESTAURAR")
        db.close();open();assertEquals("Convocação oficial",backup.snapshot().relocations.single().corrections.single().removed.single().attendance.reason)
    }
    @Test fun sharedHistoryKeepsReasonAfterSourceClassDeletionAndIndependentDeletionKeepsOtherStudents()=runBlocking {
        val target=c.copy(id="classification-target",name="Destino Fictício");repo.saveClass(target,"2026-01-01")
        repo.mark(first,s.id,"FJ");reason("Atividade escolar ou esportiva")
        val p=repo.previewRelocation(s.id,target.id,null,"2026-09-01");repo.confirmRelocation(p)
        val deletion=repo.previewClassDeletion(c.id);repo.deleteClass(deletion,deletion.confirmation)
        val history=data().academic();assertEquals("Atividade escolar ou esportiva",history.marks.single{it.studentId==s.id}.reason)
        val active=p.target!!;assertEquals(1,SchoolReports(data(),target).student(active,3).attendance!!.justified)
        val wrong=other.copy(id="classification-wrong",classId=target.id,number=2);repo.saveStudent(wrong)
        repo.setDayHours(target.id,"2026-09-10",1);val l="${target.id}:2026-09-10:1";repo.mark(l,wrong.id,"FJ");reason("Outro",l,wrong.id)
        val before=backup.snapshot();repo.deleteStudent(repo.previewStudentDeletion(wrong.id,target.id))
        val after=backup.snapshot();assertEquals(before.marks.filterNot{it.studentId==wrong.id},after.marks)
        assertEquals(before.histories,after.histories);assertEquals(before.relocations,after.relocations)
    }
    @Test fun additiveMigrationNineToTenPreservesEveryPreviousColumnAndNullReasons()=runBlocking {
        repo.mark(first,s.id,"FJ");repo.mark(second,other.id,"F")
        repo.saveActivity(Activity(id="migration-old-activity",classId=c.id,name="Prova Fictícia Antiga",date=day,term=3,maximum=10.0))
        repo.saveGrade("migration-old-activity",s.id,0.0);repo.saveRecomposition(Recomposition(s.id,3,5.0,"2026-08-20"))
        repo.saveLessonRecord(LessonRecord(classId=c.id,date=day,title="Diário Fictício Antigo",description="Preservado"))
        val before=backup.snapshot();val oldName="classifications-frozen9.db";context.deleteDatabase(oldName)
        val tables=listOf("classes","students","terms","lessons","marks","activities","grades","import_drafts","recompositions","relocations","export_rows","class_archives","lesson_records","preserved_histories","weekly_timetables")
        fun rows(sql:androidx.sqlite.db.SupportSQLiteDatabase,t:String)=sql.query("SELECT * FROM `$t`").use{r->buildList{while(r.moveToNext())add((0 until r.columnCount).map{r.getString(it)})}}
        val oldRows=helper.createDatabase(oldName,9).use{old->
            tables.forEach{table->db.openHelper.writableDatabase.query("SELECT * FROM `$table`").use{r->while(r.moveToNext()){
                val values=ContentValues();r.columnNames.forEachIndexed{i,key->if(key!="reason") {if(r.isNull(i))values.putNull(key)else values.put(key,r.getString(i))}}
                old.insert(table,0,values)
            }}}
            tables.associateWith{rows(old,it)}
        }
        helper.runMigrationsAndValidate(oldName,10,true,MIGRATION_9_10).use{migrated->
            tables.forEach{t->assertEquals(t,oldRows[t],rows(migrated,t).map{if(t=="marks")it.dropLast(1)else it})}
            migrated.query("SELECT reason FROM marks").use{r->while(r.moveToNext())assertTrue(r.isNull(0))}
            migrated.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}
        }
        val upgraded=Room.databaseBuilder(context,DiaryDatabase::class.java,oldName).addMigrations(MIGRATION_9_10).build()
        try{assertEquals(before,BackupManager(upgraded,FileSafetyBackupStore(File(context.cacheDir,"classified-migration-safety")),36,null).snapshot())}
        finally{upgraded.close();context.deleteDatabase(oldName)}
    }
}
