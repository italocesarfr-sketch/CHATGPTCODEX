package br.com.diariodeclasse

import android.content.Context
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.sync.academic.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*

class ActivityCopiesRoomTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db:DiaryDatabase;private lateinit var filename:String
    private val repo get()=DiaryRepository(db)
    private val c=SchoolClass("copies-a",name="1º A Fictícia",subject="História",year=2026)
    private val other=c.copy(id="copies-b",name="2º B Fictícia")
    private val a=Activity("copies-activity",c.id,"Produção fictícia",type="Produção textual",date="2026-08-10",term=3,maximum=7.5)
    private val owner="11111111-1111-4111-8111-111111111111"
    private val store get()=AcademicStore(db,owner)
    @Before fun open():Unit=runBlocking{filename="copies-${newId()}.db";db=DiaryDatabase.open(context,filename);for(school in listOf(c,other)){repo.saveClass(school,"2026-01-01");repo.saveStudent(Student("student-${school.id}","person-${school.id}",school.id,"Fictício",1,"2026-01-01"))}}
    @After fun close(){db.close();context.deleteDatabase(filename)}
    private suspend fun plan()=ActivityCopies.plan(repo.readSnapshot(),c.id,a,ActivityScope.ALL,emptySet())
    @Test fun independentActivitiesGradesAndRetrySurviveReopen():Unit=runBlocking{
        val p=plan();val created=repo.createActivities(p);assertEquals(2,created.size)
        for((i,copy)in created.withIndex())repo.saveGrade(copy.id,"student-${copy.classId}",if(i==0)0.0 else null)
        db.close();db=DiaryDatabase.open(context,filename)
        assertEquals(created,repo.createActivities(p));assertEquals(2,repo.readSnapshot().activities.size)
        assertEquals(setOf(0.0,null),repo.readSnapshot().grades.map{it.value}.toSet())
        repo.saveGrade(created[0].id,"student-${created[0].classId}",7.5)
        assertNull(repo.dao.activityGrades(created[1].id).single().value)
    }
    @Test fun duplicateInLastTargetRollsBackAllWithoutOutboxChanges():Unit=runBlocking{
        val remote=PhaseBRoomTest.Remote();val engine=AcademicEngine(store,remote,{owner},{true});engine.activate();engine.run();engine.activateC();engine.run()
        repo.saveActivity(a.copy(id="existing",classId=other.id));engine.run()
        val before=repo.readSnapshot();val versions=store.dao.states()
        try{repo.createActivities(plan());fail()}catch(_:IllegalArgumentException){}
        assertEquals(before,repo.readSnapshot());assertEquals(versions,store.dao.states());assertNull(store.prepare())
    }
    @Test fun staleClassOrTermRejectsCompleteSelection():Unit=runBlocking{
        val p=plan();repo.dao.save(Term(other.id,3,"2026-08-20","2026-09-30"))
        try{repo.createActivities(p);fail()}catch(_:IllegalArgumentException){}
        assertTrue(repo.readSnapshot().activities.isEmpty())
    }
    @Test fun allSelectionChangesRequireFreshReview():Unit=runBlocking{
        val request=plan();repo.saveClass(c.copy(id="copies-new",name="3º Nova Fictícia"),"2026-01-01")
        try{repo.createActivities(request);fail()}catch(_:IllegalArgumentException){}
        assertTrue(repo.readSnapshot().activities.isEmpty())
        assertEquals(3,repo.createActivities(plan()).size)
    }
    @Test fun incompatibleManualSelectionNeverPartiallySaves():Unit=runBlocking{
        val bad=other.copy(id="wrong",subject="Geografia");repo.saveClass(bad,"2026-01-01")
        try{val p=ActivityCopies.plan(repo.readSnapshot(),c.id,a,ActivityScope.CUSTOM,setOf(c.id,bad.id));repo.createActivities(p);fail()}catch(_:IllegalArgumentException){}
        assertTrue(repo.readSnapshot().activities.isEmpty())
    }
    @Test fun insertFailureRollsBackActivitiesAndCapturedVersions():Unit=runBlocking{
        val remote=PhaseBRoomTest.Remote();val engine=AcademicEngine(store,remote,{owner},{true});engine.activate();engine.run();engine.activateC();engine.run()
        val before=store.dao.states()
        db.openHelper.writableDatabase.execSQL("CREATE TEMP TRIGGER fail_copy BEFORE INSERT ON activities WHEN NEW.classId='copies-b' BEGIN SELECT RAISE(ABORT,'Synthetic insert failure'); END")
        try{repo.createActivities(plan());fail()}catch(_:android.database.sqlite.SQLiteException){}
        assertTrue(repo.readSnapshot().activities.isEmpty());assertEquals(before,store.dao.states());assertNull(store.prepare())
    }
    @Test fun concurrentRetriesCreateExactlyOneCopyPerClass():Unit=runBlocking{
        val p=plan()
        val results=listOf(async(Dispatchers.IO){repo.createActivities(p)},async(Dispatchers.IO){repo.createActivities(p)}).awaitAll()
        assertEquals(results[0],results[1]);assertEquals(2,repo.readSnapshot().activities.size)
    }
    @Test fun copiesCapturedTogetherAndRecoveredWithoutNewIds():Unit=runBlocking{
        val remote=PhaseBRoomTest.Remote();fun engine(s:AcademicStore)=AcademicEngine(s,remote,{owner},{true})
        engine(store).activate();engine(store).run();engine(store).activateC();engine(store).run()
        val created=repo.createActivities(plan());val pending=store.prepare()!!
        val changes=AcademicRecords.json.decodeFromString<AcademicBatch>(pending.body).changes
        assertEquals(created.map{it.id}.toSet(),changes.map{it.key.getValue("id").jsonPrimitive.content}.toSet());assertTrue(changes.all{it.kind=="activities"})
        remote.lost=true;engine(store).run();assertEquals(pending.id,store.pending()!!.id);engine(store).run()
        val targetName="copies-receiver-${newId()}.db";val targetDb=DiaryDatabase.open(context,targetName)
        try{val target=AcademicStore(targetDb,owner);engine(target).activate();engine(target).run();assertEquals(created.toSet(),DiaryRepository(targetDb).readSnapshot().activities.toSet())}
        finally{targetDb.close();context.deleteDatabase(targetName)}
    }
}
