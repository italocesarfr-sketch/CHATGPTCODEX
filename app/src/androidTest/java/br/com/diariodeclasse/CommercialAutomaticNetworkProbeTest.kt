package br.com.diariodeclasse

import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.sync.SyncFailure
import br.com.diariodeclasse.sync.academic.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Actual startup/Room/HTTP. No activate/run/request/send button in this proof. Synthetic Auth only. */
class CommercialAutomaticNetworkProbeTest {
    @get:Rule(order=0) val ui=CommercialNativeUiRule()
    @get:Rule(order=1) val entry=object:org.junit.rules.ExternalResource(){override fun before(){ui.waitUntil(10000){ui.exists("nav-0")}}}
    private val app get()=ui.activity.application as AcademicTestApplication
    private val w get()=app.workspaces.current()
    private val store get()=AcademicStore(w.database,w.user.id)
    private suspend fun snapshot()=ClassLifecycle.snapshot(w.repository.readSnapshot()).canonical()
    private fun waitFor(condition:suspend()->Boolean){ui.waitUntil(60000){runBlocking{condition()}}}
    private fun synced(){waitFor{store.dao.control()?.let{it.stage=="ACTIVE_E"&&it.checked&&it.message.isEmpty()}==true &&
        store.dao.pendingHeaders().isEmpty()&&store.dao.incomingHeaders().isEmpty()&&store.dao.states().all{it.localVersion==it.acknowledged}}}
    private fun shell(command:String){ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)).use{it.readBytes()}}
    @Test fun commercialStartupAndAutomaticSyncAcrossIndependentInstallations():Unit=runBlocking {
        val phase=InstrumentationRegistry.getArguments().getString("phaseBNetworkPhase")
        Assume.assumeTrue(phase?.startsWith("e-auto-commercial-")==true)
        check(BuildConfig.DEBUG&&BuildConfig.ACADEMIC_COMMERCIAL&&Build.HARDWARE in setOf("ranchu","goldfish"))
        when(phase) {
            "e-auto-commercial-init-lost"->{
                waitFor{PhaseBNetworkHarness.droppedCommittedReply()&&store.stage()=="BOOTSTRAP_E"}
                assertTrue(snapshot().classes.isEmpty());assertFalse(store.active())
                PhaseDFixture.insert(w.database,PhaseDFixture.read())
                assertEquals(15,store.dao.states().map{it.kind}.toSet().size)
                assertTrue(store.dao.pending().isEmpty())
            }
            "e-auto-commercial-seed"->{
                synced();assertEquals(PhaseDFixture.read().canonical(),snapshot());assertEquals(1L,store.cursor())
                assertEquals(15,store.dao.states().map{it.kind}.toSet().size)
            }
            "e-auto-commercial-recover"->{
                synced();assertEquals(PhaseDFixture.read().canonical(),snapshot());assertEquals(1L,store.cursor())
                assertTrue(store.dao.states().all{it.revision==1L})
                w.repository.dao.save(snapshot().lessonRecords.first().copy(description="Edição no AVD B"));synced()
            }
            "e-auto-commercial-observe-offline"->{
                waitFor{snapshot().lessonRecords.any{it.description=="Edição no AVD B"}};synced()
                shell("svc wifi disable");shell("svc data disable")
                waitFor{(w.context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager).activeNetwork==null}
                w.repository.dao.save(Grade("d-a1","ds2",0.0))
                assertTrue(store.dao.states().any{it.kind=="grades"&&it.localVersion>it.acknowledged})
            }
            "e-auto-commercial-reconnect"->{
                assertEquals(0.0,w.repository.dao.activityGrades("d-a1").single{it.studentId=="ds2"}.value!!,0.0)
                shell("svc wifi enable");shell("svc data enable");synced()
                assertEquals(3L,store.cursor());assertEquals(2L,store.dao.record("grades","d-a1","ds2")!!.revision)
                File(app.filesDir,"commercial-final.sha256").writeText(BackupCodec.hash(BackupCodec.json.encodeToString(BackupSnapshot.serializer(),snapshot()).toByteArray()))
            }
            "e-auto-commercial-observe-final"->{
                waitFor{w.repository.dao.activityGrades("d-a1").any{it.studentId=="ds2"&&it.value==0.0}};synced()
                assertEquals(PhaseDFixture.read().canonical().marks,snapshot().marks)
                assertEquals(PhaseDFixture.read().canonical().relocations,snapshot().relocations)
                try{PhaseBNetworkHarness.raw().ensureE(PhaseBNetworkHarness.token(true)).let{assertFalse(it.eligible)}}catch(e:SyncFailure){fail("Valid other synthetic account must return INELIGIBLE, not fail transport: ${e.code}")}
                File(app.filesDir,"commercial-final.sha256").writeText(BackupCodec.hash(BackupCodec.json.encodeToString(BackupSnapshot.serializer(),snapshot()).toByteArray()))
            }
            else->error("Unknown disposable commercial phase")
        }
    }
}
