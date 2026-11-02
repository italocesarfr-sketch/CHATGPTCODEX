package br.com.diariodeclasse

import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.withTransaction
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.sync.*
import br.com.diariodeclasse.sync.academic.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Real Android HTTP + two independent emulators; local signed Auth fixtures, never hosted. */
class PhaseCNetworkProbeTest {
    @get:Rule(order=0) val compose=createAndroidComposeRule<MainActivity>()
    @get:Rule(order=1) val entry=enterNoctuaRule(compose)
    private val app get()=compose.activity.application as AcademicTestApplication
    private val w get()=app.workspace
    private val store get()=AcademicStore(w.database,PhaseBNetworkHarness.user().id)
    private val school=SchoolClass("c-network-class",name="Turma Fictícia C",subject="História",year=2026)
    private fun student(n:Int)=Student("c-net-s$n","c-net-p$n",school.id,"Fictício C $n",n,"2026-01-01")
    private fun waitFor(block:suspend()->Boolean){compose.waitUntil(30000){runBlocking{block()}}}
    private fun online():Boolean {val cm=app.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)==true}
    private fun shell(command:String){ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)).use{it.readBytes()}}
    private fun offline(){shell("svc wifi disable");shell("svc data disable");waitFor{!online()}}
    private fun reconnect(){shell("svc wifi enable");shell("svc data enable");waitFor{online()}}
    private fun synced(){waitFor{val c=store.dao.control();c?.checked==true&&c.message.isEmpty()&&store.dao.pending().isEmpty()&&store.dao.incoming().isEmpty()&&store.dao.states().all{it.localVersion==it.acknowledged}}}
    private suspend fun grade(n:Int)=w.repository.dao.activityGrades("c-net-a1").single{it.studentId==student(n).id}.value
    private suspend fun edit(n:Int,value:Double){w.repository.dao.save(Grade("c-net-a1",student(n).id,value))}
    private suspend fun shot(label:String,expectedStatus:String?=null) {
        // Snapshot assertions and pixels share the account mutex with polling. A new
        // legitimate poll cannot change SYNCING between the wait and the assertion.
        w.syncMutex.withLock {
            compose.academicCapture(app,"phasec-network",label,expectedStatus) {
                runBlocking { kotlinx.coroutines.withTimeout(2000) {
                    val c=store.dao.control()
                    "stage=${c?.stage} message=${c?.message} cursor=${c?.cursor} enabled=${c?.enabled} checked=${c?.checked}\n"+
                        "outbox=${store.dao.pending().size} inbox=${store.dao.incoming().size}"
                } }
            }
        }
    }
    private suspend fun assertFixture(){
        assertEquals(36,w.repository.dao.roster(school.id).size);assertEquals(4,w.repository.dao.periods(school.id).size)
        for(n in 1..36)assertEquals(student(n).personId,w.repository.dao.student(student(n).id)!!.personId)
        assertEquals(2,w.repository.readSnapshot().activities.size);assertEquals(2,w.repository.readSnapshot().lessons.size)
        assertEquals(71,w.repository.readSnapshot().marks.size);assertEquals(36,w.repository.readSnapshot().grades.size)
        assertEquals(0.0,grade(1)!!,0.0);assertEquals(0.0001,grade(2)!!,0.0);assertNull(grade(4))
        assertTrue(w.repository.dao.activityGrades("c-net-a1").none{it.studentId==student(36).id})
        val marks=w.repository.dao.attendance("c-net-l1").associateBy{it.studentId}
        assertEquals("P",marks.getValue(student(1).id).mark);assertEquals("F",marks.getValue(student(2).id).mark)
        assertEquals("FJ",marks.getValue(student(3).id).mark);assertEquals("Outro",marks.getValue(student(3).id).reason)
        assertEquals("NR",marks.getValue(student(4).id).mark)
        assertEquals(7.5,w.repository.dao.schoolClass(school.id)!!.let{w.repository.readSnapshot().activities.single{it.id=="c-net-a1"}.maximum},0.0)
        assertEquals(0.0,w.repository.readSnapshot().recompositions.single().value!!,0.0)
    }
    @Test fun actualAcademicCTransportAcrossTwoIndependentAndroidInstallations():Unit=runBlocking {
        val phase=InstrumentationRegistry.getArguments().getString("phaseBNetworkPhase")
        Assume.assumeTrue(phase?.startsWith("c-")==true)
        check(Build.HARDWARE in setOf("ranchu","goldfish")&&BuildConfig.DEBUG&&academicTestEligible(w))
        check(w.user.id==PhaseBNetworkHarness.user().id)
        val client=PhaseBNetworkHarness.raw();val token=PhaseBNetworkHarness.token()
        when(phase){
            "c-seed"->{
                assertTrue(w.repository.readSnapshot().classes.isEmpty())
                w.syncMutex.withLock{AcademicCoordinator.engine(app,w).activate();AcademicCoordinator.engine(app,w).run();AcademicCoordinator.engine(app,w).activateC()}
                offline()
                w.database.withTransaction {
                    w.repository.saveClass(school,"2026-01-01");for(n in 1..36)w.repository.saveStudent(student(n))
                    for(h in 1..2)w.repository.dao.save(Lesson("c-net-l$h",school.id,"2026-08-10",h,"REALIZADA"))
                    w.repository.dao.save(Activity("c-net-a1",school.id,"Produção fictícia",type="Produção textual",date="2026-08-10",term=3,maximum=7.5))
                    w.repository.dao.save(Activity("c-net-a2",school.id,"Trabalho fictício",type="Trabalho",date="2026-08-11",term=3,maximum=10.0))
                    for(n in 1..36){
                        if(n!=36)w.repository.dao.save(Grade("c-net-a1",student(n).id,if(n==2)0.0001 else when(n%4){0->null;1->0.0;else->5.0}))
                        for(h in 1..2)if(h!=2||n!=36){val m=listOf("NR","P","F","FJ")[n%4]
                            w.repository.dao.save(Attendance("c-net-l$h",student(n).id,m,if(m=="FJ")"Outro"else null))}
                    }
                    w.repository.dao.save(Grade("c-net-a2",student(1).id,10.0))
                    w.repository.dao.save(Recomposition(student(1).id,3,0.0,"2026-08-11"))
                    w.repository.dao.insertRecord(LessonRecord("c-net-journal",school.id,"2026-08-10","Local","Diário preservado fora da fase C"))
                }
                val pending=store.prepare()!!;val batch=AcademicRecords.json.decodeFromString<AcademicBatch>(pending.body)
                assertEquals(153,batch.changes.size);assertEquals(phaseCKinds,batch.changes.map{it.kind}.toSet())
                File(app.filesDir,"phasec-operation.txt").writeText(pending.id);assertFixture();shot("01-c-offline")
            }
            "c-lost"->{
                waitFor{PhaseBNetworkHarness.droppedCommittedReply()&&store.dao.pending().singleOrNull()?.attempts?.let{it>0}==true}
                assertEquals(File(app.filesDir,"phasec-operation.txt").readText(),store.pending()!!.id);assertEquals(0,store.cursor())
            }
            "c-retry"->{
                val p=store.pending()!!;val batch=AcademicRecords.json.decodeFromString<AcademicBatch>(p.body)
                val first=client.pushC(token,batch);assertEquals(first,client.pushC(token,batch));assertTrue(first.accepted)
                PhaseBNetworkHarness.released=true;w.academicCoordinator.request();synced();assertFixture();assertEquals(1,store.cursor());shot("02-c-reenvio")
            }
            "c-recover"->{
                assertTrue(w.repository.readSnapshot().classes.isEmpty())
                w.syncMutex.withLock{AcademicCoordinator.engine(app,w).activate()};w.academicCoordinator.request();synced();assertFixture()
                assertEquals("ACTIVE_C",store.stage());assertTrue(w.repository.readSnapshot().lessonRecords.isEmpty())
                val other=PhaseBNetworkHarness.token(other=true);client.activate(other,client.status(other));client.enableC(other,0)
                assertTrue(client.pullC(other,0).isEmpty())
                val row=AcademicRecords.rows(ClassLifecycle.snapshot(w.repository.readSnapshot())).first{it.kind=="grades"}
                assertFalse(client.pushC(other,AcademicBatch(newId(),listOf(AcademicChange(row.kind,row.key,1,row.payload)))).accepted)
                assertTrue(client.pullC(other,0).isEmpty());shot("03-c-recuperado")
            }
            "c-edit-secondary"->{synced();edit(1,4.5);w.repository.dao.save(Attendance("c-net-l1",student(3).id,"FJ","Atestado médico"));synced();shot("04-c-editado")}
            "c-observe-secondary"->{
                waitFor{grade(1)==4.5};synced();assertEquals("Atestado médico",w.repository.dao.attendance("c-net-l1").single{it.studentId==student(3).id}.reason)
                assertEquals("Diário preservado fora da fase C",w.repository.dao.lessonRecord("c-net-journal")!!.description);shot("05-c-inverso")
            }
            "c-offline-reconnect"->{synced();offline();edit(5,1.0);assertNotNull(store.prepare());reconnect();synced();assertEquals(1.0,grade(5)!!,0.0);shot("06-c-reconectado")}
            "c-independent-local"->{synced();offline();edit(2,3.0);assertNotNull(store.prepare())}
            "c-independent-remote"->{synced();edit(3,6.0);synced()}
            "c-merge"->{synced();assertEquals(3.0,grade(2)!!,0.0);assertEquals(6.0,grade(3)!!,0.0);assertNotNull(w.repository.dao.lessonRecord("c-net-journal"));shot("07-c-independentes")}
            "c-conflict-local"->{synced();offline();edit(1,1.0);assertNotNull(store.prepare())}
            "c-conflict-remote"->{synced();edit(1,2.0);synced()}
            "c-observe-conflict"->{
                waitFor{store.dao.incoming().isNotEmpty()};assertEquals(1.0,grade(1)!!,0.0);assertNotNull(store.pending())
                val page=AcademicRecords.json.decodeFromString<AcademicPage>(store.dao.incoming().single().body)
                assertTrue(page.sequence>store.cursor());assertTrue(page.records.any{it.kind=="grades"&&it.payload?.get("value")?.jsonPrimitive?.doubleOrNull==2.0})
                w.syncMutex.withLock{repeat(2){AcademicCoordinator.engine(app,w).run()}}
                assertEquals("CONFLICT",store.dao.control()!!.message)
                assertNotNull(w.repository.dao.lessonRecord("c-net-journal"));shot("08-c-conflito","Conflito preservado")
            }
            else->error("Unknown phase")
        }
        File(app.filesDir,"phasec-network").apply{mkdirs()}.resolve("$phase.txt").writeText("Approved $phase; local HTTP/PostgREST; synthetic Auth only.\n")
    }
}
