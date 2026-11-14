package br.com.diariodeclasse

import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.sync.academic.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Real Room + real disposable PostgREST, two fresh CI AVDs. No hosted capability. */
class FunctionalNetworkProbeTest {
    @get:Rule(order=0) val compose=createAndroidComposeRule<MainActivity>()
    @get:Rule(order=1) val entry=enterNoctuaRule(compose)
    private val app get()=compose.activity.application as AcademicTestApplication
    private val w get()=app.workspace
    private val store get()=AcademicStore(w.database,PhaseBNetworkHarness.user().id)
    private val school=SchoolClass("functional-class",name="1º G Fictícia",subject="História",year=2026)
    private val alpha=Student("functional-alpha","functional-person-a",school.id,"Álvaro Fictício",1,"2026-01-01")
    private val beta=alpha.copy(id="functional-beta",personId="functional-person-b",name="Beatriz Fictícia",number=2)
    private val day="2026-08-10"
    private fun lesson(n:Int)="${school.id}:$day:$n"
    private fun shell(cmd:String){ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(cmd)).use{it.readBytes()}}
    private fun waitFor(test:suspend()->Boolean){compose.waitUntil(30000){runBlocking{test()}}}
    private fun synced(){waitFor{store.dao.control()?.let{it.checked&&it.message.isEmpty()}==true&&store.dao.pending().isEmpty()&&store.dao.incoming().isEmpty()&&store.dao.states().all{it.localVersion==it.acknowledged}}}
    private suspend fun preserved(){
        assertEquals(Attendance(lesson(1),beta.id,"FJ","Outro"),w.repository.dao.attendance(lesson(1)).single{it.studentId==beta.id})
        assertEquals(Attendance(lesson(2),alpha.id,"NR"),w.repository.dao.attendance(lesson(2)).single())
        assertEquals(setOf(alpha,beta),w.repository.dao.roster(school.id).toSet())
    }
    @Test fun realTransport():Unit=runBlocking {
        val phase=InstrumentationRegistry.getArguments().getString("phaseBNetworkPhase")
        Assume.assumeTrue(phase?.startsWith("c-functional-")==true)
        check(BuildConfig.DEBUG&&Build.HARDWARE in setOf("ranchu","goldfish")&&academicTestEligible(w))
        when(phase){
            "c-functional-seed"->{
                assertTrue(w.repository.readSnapshot().classes.isEmpty())
                w.syncMutex.withLock {val e=AcademicCoordinator.engine(app,w);e.activate();e.run();e.activateC()}
                w.repository.saveClass(school,"2026-01-01");w.repository.saveStudent(alpha);w.repository.saveStudent(beta)
                w.repository.setDayHours(school.id,day,3)
                w.repository.mark(lesson(1),alpha.id,"P");w.repository.mark(lesson(1),beta.id,"FJ")
                w.repository.setJustification(Attendance(lesson(1),beta.id,"FJ"),"Outro")
                w.repository.mark(lesson(2),alpha.id,"NR");w.repository.mark(lesson(3),alpha.id,"F")
                w.academicCoordinator.request();synced();preserved()
            }
            "c-functional-recover"->{
                assertTrue(w.repository.readSnapshot().classes.isEmpty())
                w.syncMutex.withLock{AcademicCoordinator.engine(app,w).activate()};w.academicCoordinator.request()
                waitFor{w.repository.dao.roster(school.id).size==2};synced();preserved()
                assertEquals("P",w.repository.dao.attendance(lesson(1)).single{it.studentId==alpha.id}.mark)
            }
            "c-functional-clear-offline"->{
                synced();shell("svc wifi disable");shell("svc data disable")
                w.syncMutex.withLock {
                    w.repository.toggleMark(lesson(1),alpha.id,"P")
                    repeat(20){w.repository.toggleMark(lesson(1),alpha.id,"F")}
                    assertTrue(w.repository.dao.attendance(lesson(1)).none{it.studentId==alpha.id})
                    val p=store.prepare()!!;val changes=AcademicRecords.json.decodeFromString<AcademicBatch>(p.body).changes
                    assertEquals(1,changes.size);assertEquals("marks",changes.single().kind);assertNull(changes.single().payload)
                    File(app.filesDir,"functional-pending.txt").writeText(p.id)
                };preserved()
            }
            "c-functional-reopen"->{
                assertTrue(w.repository.dao.attendance(lesson(1)).none{it.studentId==alpha.id})
                assertEquals(File(app.filesDir,"functional-pending.txt").readText(),store.pending()!!.id);preserved()
            }
            "c-functional-sent"->{w.academicCoordinator.request();synced();preserved();assertTrue(w.repository.dao.attendance(lesson(1)).none{it.studentId==alpha.id})}
            "c-functional-receive-remove"->{
                w.academicCoordinator.request();waitFor{w.repository.dao.attendance(lesson(1)).none{it.studentId==alpha.id}};synced();preserved()
                w.repository.toggleMark(lesson(3),alpha.id,"F");w.academicCoordinator.request();synced()
                assertTrue(w.repository.dao.attendance(lesson(3)).isEmpty())
            }
            "c-functional-final"->{
                w.academicCoordinator.request();waitFor{w.repository.dao.attendance(lesson(3)).isEmpty()};synced();preserved()
                assertTrue(w.repository.dao.attendance(lesson(1)).none{it.studentId==alpha.id})
                // C returns one committed batch per pull; inspect all pages, not just bootstrap.
                val versions=mutableListOf<AcademicVersion>();var after=0L;var count=0
                while(true){
                    check(count++<128)
                    val pages=PhaseBNetworkHarness.raw().pullC(PhaseBNetworkHarness.token(),after)
                    if(pages.isEmpty())break
                    assertTrue(pages.first().sequence>after);versions.addAll(pages.flatMap{it.records});after=pages.last().sequence
                }
                val removed=versions.filter{it.kind=="marks"&&it.payload==null}
                assertEquals(setOf(lesson(1),lesson(3)),removed.map{it.key.getValue("lessonId").jsonPrimitive.content}.toSet())
                assertTrue(removed.all{it.revision==2L})
            }
            "c-functional-activities"->{
                val target=school.copy(id="functional-other-class",name="2º H Fictícia")
                val third=school.copy(id="functional-third-class",name="3º I Fictícia")
                val future=school.copy(id="functional-future-class",name="1º K Fictícia 2027",year=2027)
                for(c in listOf(target,third,future))w.repository.saveClass(c,"${c.year}-01-01")
                w.repository.saveStudent(alpha.copy(id="functional-other-student",personId="functional-other-person",classId=target.id,joined="2026-01-01"))
                w.repository.saveStudent(alpha.copy(id="functional-third-student",personId="functional-third-person",classId=third.id))
                w.academicCoordinator.request();synced()
                w.syncMutex.withLock {
                    val template=Activity("functional-activity",school.id,"Produção Fictícia em Lote",type="Produção textual",date=day,term=3,maximum=7.5)
                    val before=w.repository.readSnapshot();val states=store.dao.states()
                    for(dates in listOf(emptyMap(),mapOf(future.id to "2027-08-10"))) {
                        try{ActivityCopies.plan(before,school.id,template,ActivityScope.CUSTOM,setOf(school.id,future.id),dates);fail()}catch(_:IllegalArgumentException){}
                    }
                    try{ActivityCopies.plan(before,school.id,template,ActivityScope.ALL,emptySet());fail()}catch(_:IllegalArgumentException){}
                    assertEquals(before,w.repository.readSnapshot());assertEquals(states,store.dao.states());assertNull(store.prepare())
                    val request=ActivityCopies.plan(before,school.id,template,ActivityScope.CUSTOM,setOf(school.id,target.id,third.id))
                    val copies=w.repository.createActivities(request)
                    assertEquals(3,copies.size)
                    val op=store.prepare()!!;val changes=AcademicRecords.json.decodeFromString<AcademicBatch>(op.body).changes
                    assertEquals(3,changes.size);assertTrue(changes.all{it.kind=="activities"})
                    assertEquals(copies,w.repository.createActivities(request))
                    for(copy in copies)w.repository.saveGrade(copy.id,when(copy.classId){school.id->alpha.id;third.id->"functional-third-student";else->"functional-other-student"},when(copy.classId){school.id->0.0;third.id->5.0;else->null})
                }
                w.academicCoordinator.request();synced();preserved()
            }
            "c-functional-activities-recover"->{
                w.academicCoordinator.request();waitFor{w.repository.readSnapshot().activities.size==3&&w.repository.readSnapshot().grades.size==3};synced()
                val copies=w.repository.readSnapshot().activities
                assertEquals(setOf(school.id,"functional-other-class","functional-third-class"),copies.map{it.classId}.toSet())
                assertEquals(0.0,w.repository.dao.activityGrades("functional-activity").single().value!!,0.0)
                val other=copies.single{it.classId=="functional-other-class"};assertEquals("2026-08-10",other.date);assertNull(w.repository.dao.activityGrades(other.id).single().value)
                assertEquals(5.0,w.repository.readSnapshot().grades.single{it.studentId=="functional-third-student"}.value!!,0.0)
                assertTrue(w.repository.readSnapshot().activities.none{it.classId=="functional-future-class"})
                w.repository.saveGrade(other.id,"functional-other-student",6.5);w.academicCoordinator.request();synced();preserved()
            }
            "c-functional-activities-final"->{
                w.academicCoordinator.request();waitFor{w.repository.readSnapshot().grades.any{it.studentId=="functional-other-student"&&it.value==6.5}};synced()
                assertEquals(0.0,w.repository.dao.activityGrades("functional-activity").single().value!!,0.0)
                assertEquals(3,w.repository.readSnapshot().activities.size);assertEquals(5.0,w.repository.readSnapshot().grades.single{it.studentId=="functional-third-student"}.value!!,0.0);preserved()
            }
            else->error("Unknown functional phase")
        }
    }
}
