package br.com.diariodeclasse

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

class TimetableInstrumentedTest {
    @get:Rule val helper=MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),DiaryDatabase::class.java.canonicalName!!,FrameworkSQLiteOpenHelperFactory())
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private val name="weekly-test.db"
    private lateinit var db:DiaryDatabase
    private lateinit var repo:DiaryRepository
    private lateinit var backup:BackupManager
    private val c=SchoolClass(id="weekly-c",name="Turma Fictícia A",subject="História",year=2026)
    private val h=c.copy(id="weekly-h",name="Turma Fictícia B")
    private fun slot(day:Int=4,n:Int=1,school:SchoolClass=c)=WeeklySlot(classId=school.id,weekday=day,ordinal=n,className=school.name,subject=school.subject,shift=school.shift)
    private fun open(){db=Room.databaseBuilder(context,DiaryDatabase::class.java,name).addMigrations(MIGRATION_8_9).build();repo=DiaryRepository(db);backup=BackupManager(db,FileSafetyBackupStore(File(context.cacheDir,"weekly-safety")),29,null)}
    @Before fun setup()=runBlocking{context.deleteDatabase(name);open();repo.saveClass(c,"2026-01-01");repo.saveClass(h,"2026-01-01")}
    @After fun clean(){db.close();context.deleteDatabase(name)}
    private suspend fun snapshot()=backup.snapshot().diarySnapshot()
    @Test fun periodsPersistAndRoundTripWithBackupWithoutInventingAcademicRows()=runBlocking {
        val before=backup.snapshot();val p=repo.previewTimetable(2026,"2026-01-01",listOf(slot(n=2),slot(),slot(n=3,school=h)))
        assertEquals(before,backup.snapshot())
        repo.confirmTimetable(p);val expected=backup.snapshot();assertEquals(before,expected.copy(timetables=emptyList()))
        val bytes=backup.backupBytes();db.close();open();assertEquals(expected,backup.snapshot())
        repo.confirmTimetable(repo.previewTimetable(2026,"2026-11-01",emptyList()))
        backup.restore(backup.prepare(bytes.inputStream()),"RESTAURAR");assertEquals(expected,backup.snapshot())
        assertEquals(listOf(1,2),Timetables.planned(snapshot(),c.id,"2026-08-06").map{it.ordinal})
        db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}
    }
    @Test fun futureChangeAndCorrectionKeepMarksCancelledHoursAndJournalUntouched()=runBlocking {
        val s=Student(classId=c.id,name="Estudante Fictício",number=1,joined="2026-01-01");repo.saveStudent(s)
        repo.setDayHours(c.id,"2026-08-06",4)
        val lessons=repo.dao.day(c.id,"2026-08-06")
        listOf("P","F","FJ","NR").forEachIndexed{i,m->repo.dao.save(Attendance(lessons[i].id,s.id,m))}
        repo.dao.save(lessons.last().copy(state="CANCELADA"))
        repo.saveLessonRecord(LessonRecord(classId=c.id,date="2026-08-06",title="Conteúdo Fictício",description="Um conteúdo para várias aulas."))
        val before=backup.snapshot();repo.confirmTimetable(repo.previewTimetable(2026,"2026-01-01",listOf(slot(),slot(n=2))))
        val p=repo.previewTimetable(2026,"2026-08-01",listOf(slot(day=3)))
        assertEquals(1,p.affected.size);repo.confirmTimetable(p)
        assertEquals(before,backup.snapshot().copy(timetables=before.timetables))
        assertEquals(2,Timetables.planned(snapshot(),c.id,"2026-07-30").size)
        assertTrue(Timetables.planned(snapshot(),c.id,"2026-08-06").isEmpty())
    }
    @Test fun concurrentEditsConflictAndInsertionFailureRollsBackAllRows()=runBlocking {
        val p=repo.previewTimetable(2026,"2026-01-01",listOf(slot()))
        repo.confirmTimetable(repo.previewTimetable(2026,"2026-01-01",listOf(slot(day=5))))
        val before=backup.snapshot()
        try{repo.confirmTimetable(p);fail()}catch(_:IllegalArgumentException){}
        assertEquals(before,backup.snapshot())
        val q=repo.previewTimetable(2026,"2026-11-01",listOf(slot()))
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_weekly BEFORE INSERT ON weekly_timetables BEGIN SELECT RAISE(ABORT,'ficticio'); END")
        try{repo.confirmTimetable(q);fail()}catch(_:android.database.sqlite.SQLiteException){}
        assertEquals(before,backup.snapshot())
    }
    @Test fun newAcademicDateAfterPreviewRequiresAnotherReview()=runBlocking {
        repo.confirmTimetable(repo.previewTimetable(2026,"2026-01-01",listOf(slot())))
        val p=repo.previewTimetable(2026,"2026-08-01",emptyList())
        repo.setDayHours(c.id,"2026-08-06",1)
        val before=backup.snapshot()
        try{repo.confirmTimetable(p);fail()}catch(_:IllegalArgumentException){}
        assertEquals(before,backup.snapshot())
    }
    @Test fun archiveAndClassDeletionRetainSelfContainedTimetableHistory()=runBlocking {
        repo.confirmTimetable(repo.previewTimetable(2026,"2026-01-01",listOf(slot())))
        val expected=backup.snapshot().timetables
        repo.archiveClass(c.id,true);assertEquals(expected,backup.snapshot().timetables)
        val p=repo.previewClassDeletion(c.id);repo.deleteClass(p,p.confirmation)
        assertEquals(expected,backup.snapshot().timetables)
        assertEquals(c.name,backup.snapshot().timetables.single().slots.single().className)
        BackupCodec.decode(backup.backupBytes())
        try{repo.saveClass(c.copy(year=2027),"2027-01-01");fail("Identidade histórica não pode ser reutilizada")}catch(_:IllegalArgumentException){}
        assertEquals(expected,backup.snapshot().timetables)
    }
    @Test fun migrationEightToNineLeavesEveryExistingTableAndRowUnchanged()=runBlocking {
        // Freeze all fourteen existing tables with real rows, then execute only the additive migration.
        val student=Student(id="weekly-student",classId=c.id,name="Estudante Fictício",number=1,joined="2026-01-01")
        repo.saveStudent(student);repo.setDayHours(c.id,"2026-08-06",2)
        repo.dao.save(Attendance(repo.dao.day(c.id,"2026-08-06").first().id,student.id,"FJ"))
        repo.saveActivity(Activity(id="weekly-activity",classId=c.id,name="Atividade Fictícia",date="2026-08-06",term=3,maximum=10.0))
        repo.saveGrade("weekly-activity",student.id,0.0);repo.saveRecomposition(Recomposition(student.id,3,5.0,"2026-11-01"))
        repo.saveLessonRecord(LessonRecord(classId=c.id,date="2026-08-06",title="Fictício",description="Mantido"))
        val before=backup.snapshot()
        // A downgrade is never used by the application; the fixture starts with distributed schema 8.
        val oldName="weekly-frozen8.db";context.deleteDatabase(oldName)
        helper.createDatabase(oldName,8).close()
        val tables=listOf("classes","students","terms","lessons","marks","activities","grades","import_drafts","recompositions","relocations","export_rows","class_archives","lesson_records","preserved_histories")
        val source=db.openHelper.writableDatabase
        val old=android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(oldName).path,null,0)
        fun rows(sql:androidx.sqlite.db.SupportSQLiteDatabase,table:String):List<List<String?>> = sql.query("SELECT * FROM `$table`").use{r->buildList{while(r.moveToNext())add((0 until r.columnCount).map{r.getString(it)})}}
        try {
            old.execSQL("PRAGMA foreign_keys=ON")
            tables.forEach{table->source.query("SELECT * FROM `$table`").use{r->while(r.moveToNext()){
                val values=android.content.ContentValues();r.columnNames.forEachIndexed{i,key->if(r.isNull(i))values.putNull(key)else values.put(key,r.getString(i))}
                old.insertOrThrow(table,null,values)
            }}}
        } finally{old.close()}
        val frozen=tables.associateWith{rows(source,it)}
        helper.runMigrationsAndValidate(oldName,9,true,MIGRATION_8_9).use{migrated->tables.forEach{assertEquals(it,frozen[it],rows(migrated,it))}}
        val upgraded=Room.databaseBuilder(context,DiaryDatabase::class.java,oldName).addMigrations(MIGRATION_8_9).build()
        try {val manager=BackupManager(upgraded,FileSafetyBackupStore(File(context.cacheDir,"weekly-migrate")),29,null);assertEquals(before,manager.snapshot());assertTrue(manager.snapshot().timetables.isEmpty())}
        finally{upgraded.close();context.deleteDatabase(oldName)}
    }
    @Test fun annualOverrideRetainsAllRevisionsAndEveryAcademicRowAfterRestartAndRestore()=runBlocking {
        val student=Student(classId=c.id,name="Estudante Fictício",number=1,joined="2026-01-01");repo.saveStudent(student)
        repo.setDayHours(c.id,"2026-08-06",4)
        repo.dao.day(c.id,"2026-08-06").forEachIndexed{i,l->repo.dao.save(Attendance(l.id,student.id,listOf("P","F","FJ","NR")[i]))}
        repo.saveLessonRecord(LessonRecord(classId=c.id,date="2026-08-06",title="Fictício",description="Mantido integralmente"))
        repo.confirmTimetable(repo.previewTimetable(2026,"2026-10-01",listOf(slot())))
        repo.confirmTimetable(repo.previewTimetable(2026,"2026-11-01",listOf(slot(day=5))))
        repo.confirmTimetable(repo.previewTimetable(2026,"2026-12-01",emptyList()))
        val before=backup.snapshot()
        val p=Timetables.annualPreview(snapshot(),2026,listOf(slot(day=3)))
        assertEquals(before,backup.snapshot());repo.confirmTimetable(p)
        val expected=backup.snapshot()
        assertEquals(before,expected.copy(timetables=before.timetables));assertTrue(expected.timetables.containsAll(before.timetables))
        assertEquals(7,expected.timetables.size)
        assertEquals(1,Timetables.planned(snapshot(),c.id,"2026-08-05").size)
        assertEquals(1,Timetables.planned(snapshot(),c.id,"2026-12-30").size)
        val bytes=backup.backupBytes();db.close();open();assertEquals(expected,backup.snapshot())
        repo.confirmTimetable(repo.previewTimetable(2026,"2026-11-15",emptyList()))
        backup.restore(backup.prepare(bytes.inputStream()),"RESTAURAR");assertEquals(expected,backup.snapshot())
        db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}
    }
    @Test fun annualMultiInsertFailureAndConcurrentRevisionRollBackTheEntireBatch()=runBlocking {
        repo.confirmTimetable(repo.previewTimetable(2026,"2026-10-01",listOf(slot())))
        repo.confirmTimetable(repo.previewTimetable(2026,"2026-12-01",emptyList()))
        val p=Timetables.annualPreview(snapshot(),2026,listOf(slot(day=3)))
        val before=backup.snapshot()
        // A primeira inserção de janeiro ocorre; a falha na segunda precisa revertê-la também.
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_annual BEFORE INSERT ON weekly_timetables WHEN NEW.effective='2026-10-01' BEGIN SELECT RAISE(ABORT,'ficticio'); END")
        try{repo.confirmTimetable(p);fail()}catch(_:android.database.sqlite.SQLiteException){}
        assertEquals(before,backup.snapshot())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_annual")
        repo.confirmTimetable(repo.previewTimetable(2026,"2026-11-01",listOf(slot(day=5))))
        val concurrent=backup.snapshot()
        try{repo.confirmTimetable(p);fail()}catch(_:IllegalArgumentException){}
        assertEquals(concurrent,backup.snapshot())
    }
    @Test fun annualPreviewRechecksNewAcademicDatesAndArchivedClassBeforeAnyInsert()=runBlocking {
        repo.confirmTimetable(repo.previewTimetable(2026,"2026-10-01",listOf(slot())))
        var p=Timetables.annualPreview(snapshot(),2026,listOf(slot(day=3)))
        repo.setDayHours(c.id,"2026-08-05",1)
        var before=backup.snapshot()
        try{repo.confirmTimetable(p);fail()}catch(_:IllegalArgumentException){}
        assertEquals(before,backup.snapshot())
        p=Timetables.annualPreview(snapshot(),2026,listOf(slot(day=3)))
        repo.archiveClass(c.id,true);before=backup.snapshot()
        try{repo.confirmTimetable(p);fail()}catch(_:IllegalArgumentException){}
        assertEquals(before,backup.snapshot())
    }

    @Test fun legacyNinthPeriodIsKeptOnReadUpdateBackupAndExplicitlyRetiredWithoutAcademicLoss()=runBlocking {
        val legacy=WeeklyTimetable(year=2026,effective="2026-01-01",revision=1,slots=listOf(slot(),slot(n=9)))
        repo.dao.insertTimetable(legacy)
        val student=Student(classId=c.id,name="Estudante Fictício",number=1,joined="2026-01-01");repo.saveStudent(student)
        repo.setDayHours(c.id,"2026-08-06",9)
        repo.dao.day(c.id,"2026-08-06").take(4).forEachIndexed{i,l->repo.dao.save(Attendance(l.id,student.id,listOf("P","F","FJ","NR")[i]))}
        repo.saveLessonRecord(LessonRecord(classId=c.id,date="2026-08-06",title="Fictício",description="Mantido"))
        val before=backup.snapshot()
        repo.confirmTimetable(Timetables.annualPreview(snapshot(),2026,legacy.slots+slot(n=6)))
        assertEquals(listOf(1,6,9),Timetables.planned(snapshot(),c.id,"2026-08-06").map{it.ordinal})
        assertEquals(before,backup.snapshot().copy(timetables=before.timetables))
        val bytes=backup.backupBytes();val expected=backup.snapshot();db.close();open();assertEquals(expected,backup.snapshot())
        val p=Timetables.annualPreview(snapshot(),2026,legacy.slots.filter{it.ordinal<=6})
        repo.confirmTimetable(p)
        assertEquals(listOf(1),Timetables.planned(snapshot(),c.id,"2026-08-06").map{it.ordinal})
        assertTrue(backup.snapshot().timetables.contains(legacy));assertEquals(before,backup.snapshot().copy(timetables=before.timetables))
        backup.restore(backup.prepare(bytes.inputStream()),"RESTAURAR");assertEquals(expected,backup.snapshot())
        assertEquals(9,repo.dao.day(c.id,"2026-08-06").size)
    }

    @Test fun legacyWeekendScheduleMarksAndJournalSurviveWorkdayEditRestartAndBackup()=runBlocking {
        val legacy=WeeklyTimetable(id="weekend-legacy",year=2026,effective="2026-01-01",revision=1,slots=listOf(slot(day=6),slot(day=7)))
        repo.dao.insertTimetable(legacy)
        val student=Student(id="weekend-legacy-student",classId=c.id,name="Estudante Fictício Legado",number=1,joined="2026-01-01")
        repo.saveStudent(student);repo.setDayHours(c.id,"2026-10-24",2);repo.setDayHours(c.id,"2026-10-25",1)
        repo.mark("${c.id}:2026-10-24:1",student.id,"P");repo.mark("${c.id}:2026-10-24:2",student.id,"FJ")
        repo.mark("${c.id}:2026-10-25:1",student.id,"NR")
        repo.saveLessonRecord(LessonRecord(classId=c.id,date="2026-10-25",title="Registro Fictício Legado",description="Preservado, independente da grade."))
        val before=backup.snapshot()
        repo.confirmTimetable(Timetables.annualPreview(snapshot(),2026,legacy.slots+slot(day=5,n=2)))
        val after=backup.snapshot();assertEquals(before,after.copy(timetables=before.timetables));assertTrue(after.timetables.contains(legacy))
        for(date in listOf("2026-10-24","2026-10-25"))assertEquals(1,Timetables.planned(after.diarySnapshot(),c.id,date).size)
        val bytes=backup.backupBytes();db.close();open();assertEquals(after,backup.snapshot())
        repo.confirmTimetable(repo.previewTimetable(2026,"2026-11-01",emptyList()))
        backup.restore(backup.prepare(bytes.inputStream()),"RESTAURAR");assertEquals(after,backup.snapshot())
        db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}
    }

}
