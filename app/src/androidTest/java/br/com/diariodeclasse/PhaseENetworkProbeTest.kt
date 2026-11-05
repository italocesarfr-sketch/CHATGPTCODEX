package br.com.diariodeclasse

import android.os.Process
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.auth.AccountBackup
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
        fun waitFor(check:suspend()->Boolean){
            try{compose.waitUntil(120000){runBlocking{check()}}}catch(e:Throwable){
                val diagnostic=runBlocking{val c=store.dao.control();"phase=$phase stage=${c?.stage} enabled=${c?.enabled} message=${c?.message} cursor=${c?.cursor} pending=${store.dao.pending().size} parts=${store.dao.stagedParts().size} transport=${PhaseBNetworkHarness.lastEFailure}"}
                File(app.filesDir,"phasee-wait.txt").writeText(diagnostic)
                throw AssertionError(diagnostic,e)
            }
        }
        fun synced(){waitFor{store.dao.control()?.let{it.stage=="ACTIVE_E"&&it.checked&&it.message.isEmpty()}==true&&store.dao.pending().isEmpty()&&store.dao.incoming().isEmpty()&&store.dao.stagedParts().isEmpty()&&store.dao.states().all{it.localVersion==it.acknowledged}&&store.dao.decisions().none{it.phase in setOf("PENDING","CONFIRMED")}}}
        fun shell(command:String){InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use{android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use{it.readBytes()}}}
        fun offline(){shell("svc wifi disable");shell("svc data disable");waitFor{(w.context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager).activeNetwork==null}}
        fun reconnect(){shell("svc wifi enable");shell("svc data enable");w.academicCoordinator.request()}
        suspend fun shot(name:String,expected:String?=null)=w.syncMutex.withLock {
            compose.academicCapture(app,"phasee-network",name,expected){runBlocking{"stage=${store.stage()} cursor=${store.cursor()} pending=${store.dao.pending().size} parts=${store.dao.stagedParts().size}"}}
        }
        suspend fun choose(choice:AcademicChoice) {
          try {
            compose.waitUntil(10000){compose.onAllNodesWithTag("auth-account").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("auth-account").performClick();compose.onNodeWithTag("open-sync").performClick()
            // Compose idleness does not await the five asynchronous Room flows of SyncPanel.
            // Use the merged, actionable UI state, as the existing capture/readiness probes do.
            compose.waitUntil(10000){compose.onAllNodesWithTag("academic-review").fetchSemanticsNodes().singleOrNull()?.let{isEnabled().matches(it)&&hasClickAction().matches(it)}==true}
            compose.onNodeWithTag("academic-review").performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
            compose.waitUntil(120000){compose.onAllNodesWithTag("academic-choice-${choice.name}").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("academic-confirm-decision").assertIsNotEnabled()
            compose.onNodeWithTag("academic-choice-${choice.name}").performScrollTo().performClick()
            compose.onNodeWithTag("academic-confirm-decision").performClick()
            compose.waitUntil(10000){compose.onAllNodesWithTag("academic-confirm-decision").fetchSemanticsNodes().isEmpty()}
            compose.onNodeWithText("Concluído").performClick()
          }catch(failure:Throwable) {
            val dir=File(app.filesDir,"phasee-network").apply{mkdirs()}
            runCatching{InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let{bitmap->File(dir,"$phase-review-failed.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}}
            runCatching {
                fun describe(node:androidx.compose.ui.semantics.SemanticsNode):String=node.config.toString()+"\n"+node.children.joinToString("\n"){describe(it)}
                File(dir,"$phase-review-semantics.txt").writeText(compose.onAllNodes(isRoot(),useUnmergedTree=true).fetchSemanticsNodes().joinToString("\n"){describe(it)})
            }
            throw failure
          }
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
            "e-admission-stop"->{
                w.syncMutex.withLock {
                    val pending=store.pending()!!;val batch=AcademicRecords.json.decodeFromString<AcademicBatch>(pending.body);var calls=0
                    try{client.stageE(token,batch){++calls<3};fail()}catch(_:kotlinx.coroutines.CancellationException){}
                    assertEquals(3,calls);assertEquals(0,client.status(token).academicSequence)
                    assertEquals(pending.id,store.pending()!!.id);assertEquals(0,store.cursor());assertEquals(PhaseEFixture.read(),snapshot())
                }
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
            "e-conflict-remote"->{
                synced();w.repository.dao.save(Grade("d-a1","e-student-1",8.0))
                assertTrue(store.dao.states().any{it.kind=="grades"&&it.key1=="d-a1"&&it.key2=="e-student-1"&&it.localVersion>it.acknowledged})
                synced()
                val confirmed=client.pullE(token,store.cursor()-1,0).single().records.single()
                assertEquals("grades",confirmed.kind);assertEquals(8.0,confirmed.payload!!.getValue("value").jsonPrimitive.double,0.0)
            }
            "e-conflict-observe"->{
                val cursor=store.cursor();waitFor{store.dao.incoming().isNotEmpty()};assertEquals(cursor,store.cursor())
                assertEquals(6.0,snapshot().grades.single{it.activityId=="d-a1"&&it.studentId=="e-student-1"}.value!!,0.0)
                assertNotNull(store.pending());val incoming=AcademicRecords.json.decodeFromString<AcademicPage>(store.dao.incoming().single().body)
                assertEquals(8.0,incoming.records.single{it.kind=="grades"}.payload!!.getValue("value").jsonPrimitive.double,0.0);shot("07-e-conflito","Conflito preservado")
            }
            "e-decision-lost"->{
                waitFor{store.blocked()};choose(AcademicChoice.KEEP_LOCAL)
                waitFor{PhaseBNetworkHarness.droppedCommittedReply()};assertEquals("PENDING",store.dao.decisions().last().phase)
                assertNotNull(store.pending());assertTrue(store.dao.incoming().isNotEmpty())
                File(app.filesDir,"phasee-decision.txt").writeText(store.dao.decisions().last().id)
            }
            "e-decision-replay"->{
                synced();val decision=store.dao.decisions().last();assertEquals("APPLIED",decision.phase)
                assertEquals(File(app.filesDir,"phasee-decision.txt").readText(),decision.id)
                assertEquals(6.0,w.repository.dao.activityGrades("d-a1").single{it.studentId=="e-student-1"}.value!!,0.0)
                assertTrue(client.decisionE(token,decision.id)!!.accepted);shot("08-e-decisao-recuperada")
            }
            "e-observe-decision"->{waitFor{w.repository.dao.activityGrades("d-a1").single{it.studentId=="e-student-1"}.value==6.0};synced()}
            "e-second-local"->{synced();offline();w.repository.dao.save(Grade("d-a1","e-student-1",7.0));assertNotNull(store.prepare())}
            "e-second-remote"->{synced();w.repository.dao.save(Grade("d-a1","e-student-1",9.0));synced()}
            "e-resolve-remote"->{
                waitFor{store.blocked()};choose(AcademicChoice.KEEP_REMOTE);synced()
                assertEquals(9.0,w.repository.dao.activityGrades("d-a1").single{it.studentId=="e-student-1"}.value!!,0.0)
                assertEquals(2,store.dao.decisions().count{it.phase=="APPLIED"});shot("09-e-escolha-remota")
            }
            "e-backup-before"->{
                synced();val encrypted=AccountBackup.seal(w.backup.backupBytes(),w.user.id,"Fictitious-E-Backup-2026".toCharArray())
                File(app.filesDir,"phasee-backup.enc").writeBytes(encrypted);w.syncMutex.withLock{store.pause()}
            }
            "e-after-backup-remote"->{
                synced();w.repository.dao.save(Grade("d-a1","e-student-1",10.0))
                w.repository.saveStudent(Student("e-remote-only","e-remote-person","d-source","Estudante Fictício Novo",1037,"2026-01-01"));synced()
            }
            "e-restore-review"->{
                assertFalse(store.active());val encrypted=File(app.filesDir,"phasee-backup.enc").readBytes()
                try{AccountBackup.open(encrypted,"99999999-9999-4999-8999-999999999999","Fictitious-E-Backup-2026".toCharArray());fail()}catch(_:br.com.diariodeclasse.auth.AuthFailure){}
                val raw=AccountBackup.open(encrypted,w.user.id,"Fictitious-E-Backup-2026".toCharArray())
                w.backup.restore(w.backup.prepare(raw.inputStream()),"RESTAURAR")
                assertEquals("RESTORE",store.stage());assertNull(w.repository.dao.student("e-remote-only"))
                choose(AcademicChoice.KEEP_REMOTE);synced()
                assertEquals(10.0,w.repository.dao.activityGrades("d-a1").single{it.studentId=="e-student-1"}.value!!,0.0)
                assertEquals("e-remote-person",w.repository.dao.student("e-remote-only")!!.personId)
                assertEquals(PhaseEFixture.read().marks,snapshot().marks);assertEquals(5172,AcademicRecords.rows(snapshot()).size)
                shot("10-e-backup-reconciliado")
            }
            "e-final"->{synced();File(app.filesDir,"phasee-final.sha256").writeText(BackupCodec.fingerprint(snapshot()))}
            else->error("Unknown E phase")
        }
    }
}
