package br.com.diariodeclasse

import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.sync.academic.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import kotlinx.coroutines.sync.withLock
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** No coordinator.request/run after edits or reconnection: exercise actual lifecycle signals. */
class AcademicAutomaticNetworkProbeTest {
    @get:Rule(order=0) val compose=createAndroidComposeRule<MainActivity>()
    @get:Rule(order=1) val entry=enterNoctuaRule(compose)
    private val app get()=compose.activity.application as AcademicTestApplication
    private val w get()=app.workspaces.current()
    private val store get()=AcademicStore(w.database,w.user.id)
    private fun waitFor(test:suspend()->Boolean){
        try{compose.waitUntil(60000){runBlocking{test()}}}catch(e:Throwable){
            val state=runBlocking{val c=store.dao.control();"stage=${c?.stage} message=${c?.message} cursor=${c?.cursor} pending=${store.dao.pendingHeaders().size} inbox=${store.dao.incomingHeaders().size} decisions=${store.dao.decisionHeaders().map{it.phase}}"}
            throw AssertionError("Automatic probe ${PhaseBNetworkHarness.phase}: $state",e)
        }
    }
    private suspend fun snapshot()=ClassLifecycle.snapshot(w.repository.readSnapshot()).canonical()
    private fun synced(){waitFor{store.dao.control()?.let{it.stage=="ACTIVE_E"&&it.checked&&it.message.isEmpty()}==true&&store.dao.pending().isEmpty()&&store.dao.incoming().isEmpty()&&store.dao.states().all{it.localVersion==it.acknowledged}}}
    private fun shell(cmd:String){ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(cmd)).use{it.readBytes()}}
    private fun offline(){shell("svc wifi disable");shell("svc data disable");waitFor{(w.context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager).activeNetwork==null}}
    private suspend fun grade()=w.repository.dao.activityGrades("d-a1").single{it.studentId=="ds2"}.value
    private fun popup(local:Double,remote:Double) {
        compose.waitUntil(30000){compose.onAllNodesWithTag("grade-conflict-dialog").fetchSemanticsNodes().isNotEmpty() &&
            compose.onAllNodesWithText("Neste aparelho: ${conflictGradeNumber(local)}").fetchSemanticsNodes().isNotEmpty() &&
            compose.onAllNodesWithText("Na nuvem: ${conflictGradeNumber(remote)}").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Neste aparelho: ${conflictGradeNumber(local)}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Na nuvem: ${conflictGradeNumber(remote)}").performScrollTo().assertIsDisplayed()
    }
    private fun shot(name:String) {
        compose.waitForIdle();val dir=File(app.filesDir,"automatic-grade-screenshots").apply{mkdirs()}
        requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()).let{b->File(dir,"$name.png").outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}
    }
    @Test fun automaticLifecycleAcrossTwoInstallations():Unit=runBlocking {
        val phase=InstrumentationRegistry.getArguments().getString("phaseBNetworkPhase")
        Assume.assumeTrue(phase?.startsWith("e-auto-")==true)
        check(BuildConfig.DEBUG&&Build.HARDWARE in setOf("ranchu","goldfish"))
        when(phase) {
            "e-auto-seed"->{
                // One-time experimental authorization is a prerequisite, not routine sync.
                w.syncMutex.withLock {
                    val e=AcademicCoordinator.engine(app,w);e.activate();e.run();e.activateC();e.activateD();e.activateE()
                }
                PhaseDFixture.insert(w.database,PhaseDFixture.read())
                synced();assertEquals(15,store.dao.states().map{it.kind}.toSet().size)
            }
            "e-auto-recover"->{
                // Fresh second AVD: never calls activate, run, request, restore or backup.
                synced();assertEquals(PhaseDFixture.read().canonical(),snapshot())
                assertEquals(36,w.repository.dao.roster("d-source").size)
                val receiptCount=store.cursor();assertEquals(1,receiptCount)
                assertTrue(store.dao.states().all{it.revision==1L});assertTrue(store.dao.pending().isEmpty())
                w.repository.dao.save(Grade("d-a1","ds2",4.5));synced()
            }
            "e-auto-observe"->{
                waitFor{w.repository.dao.activityGrades("d-a1").any{it.studentId=="ds2"&&it.value==4.5}};synced()
            }
            "e-auto-offline-logout"->{
                synced();offline()
                val repository=w.repository
                w.commands.act { repository.dao.save(Grade("d-a1","ds2",5.5)) }
                // Exercise the ViewModel's own barrier, independently of navigation's barrier.
                br.com.diariodeclasse.ui.DiaryViewModel(app).logout()
                compose.waitUntil(10000){app.auth.state.value.user==null}
                compose.waitUntil(10000){runCatching{app.workspaces.current()}.isFailure}
            }
            "e-auto-reopen-offline"->{
                assertEquals(5.5,w.repository.dao.activityGrades("d-a1").single{it.studentId=="ds2"}.value!!,0.0)
                val pending=store.prepare()!!;File(app.filesDir,"auto-pending.txt").writeText(pending.id)
                assertTrue(store.dao.states().any{it.localVersion>it.acknowledged})
            }
            "e-auto-reconnect-lost"->{
                shell("svc wifi enable");shell("svc data enable")
                waitFor{PhaseBNetworkHarness.droppedCommittedReply()&&store.pending()!=null}
                assertEquals(File(app.filesDir,"auto-pending.txt").readText(),store.pending()!!.id)
                assertTrue(store.dao.states().any{it.localVersion>it.acknowledged})
            }
            "e-auto-reconnect"->{
                shell("svc wifi enable");shell("svc data enable")
                synced();assertEquals(5.5,w.repository.dao.activityGrades("d-a1").single{it.studentId=="ds2"}.value!!,0.0)
                assertNull(store.pending());assertEquals(3L,store.cursor())
                assertEquals(3L,store.dao.record("grades","d-a1","ds2")!!.revision)
                assertEquals(File(app.filesDir,"auto-pending.txt").readText(),store.dao.applied(3)!!.operation)
            }
            "e-auto-receive-offline"->{
                waitFor{w.repository.dao.activityGrades("d-a1").any{it.studentId=="ds2"&&it.value==5.5}};synced()
                assertEquals(PhaseDFixture.read().canonical().marks,snapshot().marks)
                assertEquals(PhaseDFixture.read().canonical().relocations,snapshot().relocations)
                val other=PhaseBNetworkHarness.token(true)
                // Other synthetic tenant has no E scope, no permission to read this account.
                try{PhaseBNetworkHarness.raw().pullE(other,0,0);fail()}catch(e:br.com.diariodeclasse.sync.SyncFailure){assertEquals("SESSION",e.code)}
            }
            "e-auto-conflict-local","e-auto-second-local"->{
                if(phase=="e-auto-second-local")waitFor{grade()==8.5}
                synced();offline();w.repository.dao.save(Grade("d-a1","ds2",6.0));assertNotNull(store.prepare())
            }
            "e-auto-conflict-remote","e-auto-second-remote"->{
                synced();w.repository.dao.save(Grade("d-a1","ds2",if(phase=="e-auto-second-remote")9.0 else 8.0));synced()
            }
            "e-auto-grade-high"->{
                shell("svc wifi enable");shell("svc data enable")
                popup(6.0,8.0);val cursor=store.cursor();val before=store.dao.decisions().size
                shot("higher-suggestion")
                InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                compose.waitUntil(10000){compose.onAllNodesWithTag("grade-conflict-dialog").fetchSemanticsNodes().isEmpty()}
                assertTrue(store.blocked());assertEquals(cursor,store.cursor());assertEquals(before,store.dao.decisions().size);assertEquals(6.0,grade()!!,0.0)
                // Dismissal suppresses automatic nagging; the existing review reopens the same simple choice.
                compose.onNodeWithTag("automatic-sync-status").performClick()
                compose.waitUntil(10000){compose.onAllNodesWithTag("academic-review").fetchSemanticsNodes().singleOrNull()?.let{isEnabled().matches(it)}==true}
                compose.onNodeWithTag("academic-review").performScrollTo().performClick();popup(6.0,8.0)
                compose.onNodeWithText("Sim, manter 8").assertIsDisplayed().performClick()
                synced();assertEquals(8.0,grade()!!,0.0)
                assertEquals(before+1,store.dao.decisions().size);assertEquals("APPLIED",store.dao.decisions().last().phase)
                val frozen=AcademicRecords.json.decodeFromString<AcademicFrozenDecision>(store.dao.decisions().last().body)
                assertEquals(AcademicChoice.KEEP_REMOTE,frozen.choice);assertTrue(PhaseBNetworkHarness.raw().decisionE(PhaseBNetworkHarness.token(),store.dao.decisions().last().id)!!.accepted)
            }
            "e-auto-observe-high"->{
                waitFor{grade()==8.0};synced()
                w.repository.dao.save(Grade("d-a1","ds2",8.5));synced();assertFalse(store.blocked())
            }
            "e-auto-grade-stale-low"->{
                shell("svc wifi enable");shell("svc data enable");popup(6.0,9.0)
                val cursor=store.cursor();val count=store.dao.decisions().size
                val preview=w.syncMutex.withLock{AcademicReconciliation(store,PhaseBNetworkHarness.raw(),{PhaseBNetworkHarness.token()},{true}).preview()}
                val key=AcademicKey("grades",buildJsonObject{put("activityId","d-a1");put("studentId","ds2")})
                val payload=buildJsonObject{put("activityId","d-a1");put("studentId","ds2");put("value",9.5)}
                val reply=PhaseBNetworkHarness.raw().pushE(PhaseBNetworkHarness.token(fromSecondary=true),AcademicBatch(newId(),listOf(AcademicChange("grades",key.key,preview.latest.getValue(key),payload)))){true}
                assertTrue(reply.accepted)
                compose.onNodeWithText("Não, manter 6").performClick()
                compose.waitUntil(15000){compose.onAllNodesWithTag("grade-conflict-error").fetchSemanticsNodes().isNotEmpty()}
                compose.onNodeWithTag("grade-keep-KEEP_LOCAL").assertIsNotEnabled();compose.onNodeWithTag("grade-keep-KEEP_REMOTE").assertIsNotEnabled()
                assertEquals(count,store.dao.decisions().size);assertEquals(cursor,store.cursor());assertEquals(6.0,grade()!!,0.0);assertTrue(store.blocked());shot("stale-blocked")
                compose.onNodeWithTag("grade-conflict-refresh").performScrollTo().performClick();popup(6.0,9.5)
                compose.onNodeWithText("Não, manter 6").assertIsDisplayed().performClick()
                waitFor{PhaseBNetworkHarness.droppedCommittedReply()&&store.dao.decisions().last().phase=="PENDING"}
                File(app.filesDir,"auto-grade-decision.txt").writeText(store.dao.decisions().last().id)
                assertTrue(store.blocked());assertEquals(cursor,store.cursor())
            }
            "e-auto-grade-replay"->{
                synced();assertEquals(6.0,grade()!!,0.0)
                val decision=store.dao.decisions().last();assertEquals(File(app.filesDir,"auto-grade-decision.txt").readText(),decision.id);assertEquals("APPLIED",decision.phase)
                val frozen=AcademicRecords.json.decodeFromString<AcademicFrozenDecision>(decision.body)
                assertEquals(AcademicChoice.KEEP_LOCAL,frozen.choice)
                assertEquals(6.0,frozen.request.getValue("evidence").jsonArray.single().jsonObject.getValue("local").jsonObject.getValue("value").jsonPrimitive.double,0.0)
                assertEquals(9.5,frozen.remote.single{it.identity==AcademicKey("grades",buildJsonObject{put("activityId","d-a1");put("studentId","ds2")})}.payload!!.getValue("value").jsonPrimitive.double,0.0)
                assertEquals(2,store.dao.decisions().size)
            }
            "e-auto-observe-low"->{
                waitFor{grade()==6.0};synced();assertEquals(PhaseDFixture.read().canonical().marks,snapshot().marks)
                assertEquals(PhaseDFixture.read().canonical().relocations,snapshot().relocations)
                assertEquals(PhaseDFixture.read().canonical().histories,snapshot().histories)
            }
            else->error("Unknown automatic phase")
        }
    }
}
