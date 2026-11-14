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
    private fun waitFor(test:suspend()->Boolean){compose.waitUntil(60000){runBlocking{test()}}}
    private suspend fun snapshot()=ClassLifecycle.snapshot(w.repository.readSnapshot()).canonical()
    private fun synced(){waitFor{store.dao.control()?.let{it.stage=="ACTIVE_E"&&it.checked&&it.message.isEmpty()}==true&&store.dao.pending().isEmpty()&&store.dao.incoming().isEmpty()&&store.dao.states().all{it.localVersion==it.acknowledged}}}
    private fun shell(cmd:String){ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(cmd)).use{it.readBytes()}}
    private fun offline(){shell("svc wifi disable");shell("svc data disable");waitFor{(w.context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager).activeNetwork==null}}
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
                assertNull(store.pending());assertTrue(store.cursor()>=3)
            }
            "e-auto-receive-offline"->{
                waitFor{w.repository.dao.activityGrades("d-a1").any{it.studentId=="ds2"&&it.value==5.5}};synced()
                assertEquals(PhaseDFixture.read().marks,snapshot().marks)
                assertEquals(PhaseDFixture.read().relocations,snapshot().relocations)
                val other=PhaseBNetworkHarness.token(true)
                // Other synthetic tenant has no E scope, no permission to read this account.
                try{PhaseBNetworkHarness.raw().pullE(other,0,0);fail()}catch(e:br.com.diariodeclasse.sync.SyncFailure){assertEquals("SESSION",e.code)}
            }
            else->error("Unknown automatic phase")
        }
    }
}
