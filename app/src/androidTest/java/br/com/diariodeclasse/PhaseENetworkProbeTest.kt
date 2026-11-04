package br.com.diariodeclasse

import android.os.Process
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.sync.*
import br.com.diariodeclasse.sync.academic.*
import kotlinx.serialization.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Actual Android HTTP and Room on two AVDs, synthetic Auth only. */
class PhaseENetworkProbeTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun largeAcademicTransactionsStagePersistAndRecoverAcrossInstallations():Unit=runBlocking {
        val args=InstrumentationRegistry.getArguments();val phase=args.getString("phaseBNetworkPhase")
        org.junit.Assume.assumeTrue(phase?.startsWith("e-")==true)
        val app=compose.activity.application as DiaryApplication
        compose.waitUntil(15000){runCatching{app.workspaces.current()}.isSuccess}
        val w=app.workspaces.current();val store=AcademicStore(w.database,w.user.id);val client=PhaseBNetworkHarness.raw();val token=PhaseBNetworkHarness.token()
        fun snapshot()=runBlocking{ClassLifecycle.snapshot(w.repository.readSnapshot()).canonical()}
        fun waitFor(check:suspend()->Boolean){compose.waitUntil(120000){runBlocking{check()}}}
        fun synced(){waitFor{store.dao.control()?.let{it.stage=="ACTIVE_E"&&it.checked&&it.message.isEmpty()}==true&&store.dao.pending().isEmpty()&&store.dao.incoming().isEmpty()&&store.dao.stagedParts().isEmpty()}}
        fun shell(command:String){InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use{android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use{it.readBytes()}}}
        fun offline(){shell("svc wifi disable");shell("svc data disable");waitFor{(w.context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager).activeNetwork==null}}
        fun reconnect(){shell("svc wifi enable");shell("svc data enable");w.academicCoordinator.request()}
        suspend fun shot(name:String,expected:String?=null)=w.syncMutex.withLock {
            compose.academicCapture(app,"phasee-network",name,expected){runBlocking{"stage=${store.stage()} cursor=${store.cursor()} pending=${store.dao.pending().size} parts=${store.dao.stagedParts().size}"}}
        }
        when(phase) {
            "e-seed"->{
                assertTrue(snapshot().classes.isEmpty())
                w.syncMutex.withLock{val engine=AcademicCoordinator.engine(app,w);engine.activate();engine.run();engine.activateC();engine.activateD()}
                compose.onNodeWithTag("auth-account").performClick();compose.onNodeWithTag("open-sync").performClick()
                compose.onNodeWithTag("academic-enable-e").performClick();compose.onNodeWithText("Ampliar teste").assertIsNotEnabled()
                compose.onNode(isToggleable()).performClick();compose.onNodeWithText("Ampliar teste").performClick()
                waitFor{store.stage()=="ACTIVE_E"};compose.onNodeWithText("Concluído").performClick()
                offline();PhaseDFixture.insert(w.database,PhaseEFixture.read());val pending=store.prepare()!!
                assertEquals(5171,AcademicRecords.json.decodeFromString<AcademicBatch>(pending.body).changes.size)
                File(app.filesDir,"phasee-operation.txt").writeText(pending.id);shot("01-e-offline")
            }
            "e-lost"->{waitFor{PhaseBNetworkHarness.droppedCommittedReply()&&store.pending()!=null};assertEquals(0,store.cursor());assertEquals(File(app.filesDir,"phasee-operation.txt").readText(),store.pending()!!.id)}
            "e-replay"->{
                synced();assertEquals(1,store.cursor());assertEquals(PhaseEFixture.read(),snapshot())
                assertTrue(store.dao.parts().size>32);assertTrue(store.dao.parts().all{it.body.isEmpty()});shot("02-e-recuperacao-propria")
            }
            "e-crash-part"->{
                assertTrue(snapshot().classes.isEmpty())
                w.syncMutex.withLock {
                    AcademicCoordinator.engine(app,w).activate();val fragment=client.pullE(token,0,0).single()
                    assertEquals(5171,fragment.total);assertTrue(store.receivePart(fragment));store.pause()
                    assertTrue(snapshot().classes.isEmpty());assertEquals(0,store.cursor());assertEquals(128,store.nextOffset())
                    File(app.filesDir,"phasee-kill.txt").writeText("First part durable; no academic rows; cursor 0")
                    Process.killProcess(Process.myPid());error("process must terminate")
                }
            }
            "e-recover"->{
                assertTrue(snapshot().classes.isEmpty());assertEquals(0,store.cursor());assertEquals(128,store.nextOffset())
                store.resume();w.academicCoordinator.request();synced();assertEquals(PhaseEFixture.read(),snapshot())
                assertEquals(1036,w.repository.dao.roster("d-source").size);assertEquals(15,store.dao.states().map{it.kind}.toSet().size)
                val other=PhaseBNetworkHarness.token(true);client.activate(other,client.status(other));client.enableC(other,0);client.enableD(other,0);client.enableE(other,0)
                assertTrue(client.pullE(other,0,0).isEmpty());shot("03-e-recuperado")
            }
            "e-grade-secondary"->{synced();w.repository.dao.save(Grade("d-a1","e-student-1",4.5));synced();shot("04-e-nota-inversa")}
            "e-observe-grade"->{waitFor{snapshot().grades.single{it.activityId=="d-a1"&&it.studentId=="e-student-1"}.value==4.5};synced();assertEquals(5171,AcademicRecords.rows(snapshot()).size)}
            "e-offline-reconnect"->{
                synced();offline();val journal=snapshot().lessonRecords.first();w.repository.saveLessonRecord(journal.copy(description="Edição E offline\nConteúdo preservado; ação & reflexão."),journal)
                assertNotNull(store.prepare());reconnect();synced();shot("05-e-reconectado")
            }
            "e-observe-journal"->{waitFor{snapshot().lessonRecords.any{it.description.startsWith("Edição E offline")}};synced();assertEquals(PhaseEFixture.read().marks,snapshot().marks);shot("06-e-diario-recebido")}
            "e-conflict-local"->{synced();offline();w.repository.dao.save(Grade("d-a1","e-student-1",6.0));assertNotNull(store.prepare())}
            "e-conflict-remote"->{synced();w.repository.dao.save(Grade("d-a1","e-student-1",8.0));synced()}
            "e-conflict-observe"->{
                val cursor=store.cursor();waitFor{store.dao.incoming().isNotEmpty()};assertEquals(cursor,store.cursor())
                assertEquals(6.0,snapshot().grades.single{it.activityId=="d-a1"&&it.studentId=="e-student-1"}.value!!,0.0)
                assertNotNull(store.pending());val incoming=AcademicRecords.json.decodeFromString<AcademicPage>(store.dao.incoming().single().body)
                assertEquals(8.0,incoming.records.single{it.kind=="grades"}.payload!!.getValue("value").jsonPrimitive.double,0.0);shot("07-e-conflito","Conflito preservado")
            }
            else->error("Unknown E phase")
        }
    }
}
