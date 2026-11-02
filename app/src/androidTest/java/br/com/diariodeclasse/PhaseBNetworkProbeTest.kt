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
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.sync.*
import br.com.diariodeclasse.sync.academic.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import org.junit.*
import org.junit.Assert.*
import java.io.File
import kotlinx.serialization.json.*

/** Actual HTTP PostgREST + PostgreSQL, two emulator installations. Local Auth fixtures only. */
class PhaseBNetworkProbeTest {
    @get:Rule(order=0) val compose=createAndroidComposeRule<MainActivity>()
    @get:Rule(order=1) val entry=enterNoctuaRule(compose)
    private val app get()=compose.activity.application as AcademicTestApplication
    private val w get()=app.workspace
    private val store get()=AcademicStore(w.database,fixtureUser.id)
    private val school=SchoolClass("network-class-fictitious",name="Turma Fictícia de Rede",subject="História",year=2026)
    private fun student(n:Int)=Student("network-student-$n","network-person-$n",school.id,"Estudante Fictício ${n.toString().padStart(2,'0')}",n,"2026-01-01")
    private fun waitFor(block:suspend()->Boolean){compose.waitUntil(30000){runBlocking{block()}}}
    private fun online():Boolean {
        val cm=app.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)==true
    }
    private fun shell(command:String){ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)).use{it.readBytes()}}
    private fun offline(){shell("svc wifi disable");shell("svc data disable");waitFor{!online()}}
    private fun reconnect(){shell("svc wifi enable");shell("svc data enable");waitFor{online()}}
    private fun synced(){waitFor{val c=store.dao.control();c?.checked==true&&c.message.isEmpty()&&store.dao.pending().isEmpty()&&store.dao.states().all{it.localVersion==it.acknowledged}}}
    private fun shot(label:String) {
        compose.onNodeWithTag("auth-account").performClick();compose.onNodeWithTag("open-sync").performClick()
        val dir=File(app.filesDir,"phaseb-network").apply{mkdirs()}
        File(dir,"$label.png").outputStream().use{compose.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
        compose.onNodeWithText("Concluído").performClick()
    }
    @Test fun actualAndroidClientPersistsSendsRecoversAndConflictsAcrossInstallations():Unit=runBlocking {
        val phase=InstrumentationRegistry.getArguments().getString("phaseBNetworkPhase")
        Assume.assumeTrue(phase!=null)
        check(Build.HARDWARE in setOf("ranchu","goldfish") && BuildConfig.DEBUG)
        check(academicTestEligible(w) && w.user.id==fixtureUser.id)
        val client=PhaseBNetworkHarness.raw();val token=PhaseBNetworkHarness.token()
        when(phase) {
            "seed" -> {
                assertTrue(w.repository.readSnapshot().classes.isEmpty())
                w.syncMutex.withLock{AcademicCoordinator.engine(app,w).activate()}
                offline()
                w.repository.saveClass(school,"2026-01-01")
                for(n in 1..36)w.repository.saveStudent(student(n))
                w.repository.dao.save(Lesson("network-lesson",school.id,"2026-08-10",1,"REALIZADA"))
                w.repository.dao.save(Attendance("network-lesson",student(1).id,"FJ","Outro"))
                w.repository.dao.save(Activity("network-activity",school.id,"Produção fictícia",type="Produção textual",date="2026-08-10",term=3,maximum=10.0))
                w.repository.dao.save(Grade("network-activity",student(1).id,0.0))
                w.repository.dao.insertRecord(LessonRecord("network-journal",school.id,"2026-08-10","Título","Diário preservado"))
                val op=store.prepare()!!;assertEquals(41,AcademicRecords.json.decodeFromString<AcademicBatch>(op.body).changes.size)
                File(app.filesDir,"phaseb-operation.txt").writeText(op.id)
                assertEquals(36,w.repository.dao.roster(school.id).size);assertEquals(0,store.cursor())
                shot("01-offline-pendente")
            }
            "lost" -> {
                waitFor{PhaseBNetworkHarness.droppedCommittedReply() && store.dao.pending().singleOrNull()?.attempts?.let{it>0}==true}
                assertEquals(File(app.filesDir,"phaseb-operation.txt").readText(),store.pending()!!.id)
                assertEquals(0,store.cursor());assertEquals(36,w.repository.dao.roster(school.id).size)
            }
            "retry" -> {
                val pending=store.pending()!!;assertEquals(File(app.filesDir,"phaseb-operation.txt").readText(),pending.id)
                val batch=AcademicRecords.json.decodeFromString<AcademicBatch>(pending.body)
                val first=client.push(token,batch);assertEquals(first,client.push(token,batch));assertTrue(first.accepted)
                PhaseBNetworkHarness.released=true;w.academicCoordinator.request();synced()
                assertEquals(1,store.cursor());assertEquals(36,w.repository.dao.roster(school.id).size)
                assertEquals(41,client.pull(token,0).single().records.size)
                shot("02-enviado-apos-reinicio")
            }
            "offline-edit" -> {
                synced();offline()
                w.repository.saveClass(school.copy(name="Nome editado offline"),"2026-01-01")
                w.repository.saveStudent(w.repository.dao.student(student(1).id)!!.copy(notes="Edição offline"))
                val pending=store.prepare()!!;assertEquals(2,AcademicRecords.json.decodeFromString<AcademicBatch>(pending.body).changes.size)
                val before=store.cursor();reconnect();synced();assertTrue(store.cursor()>before)
                // The same process and workspace resumed through ConnectivityManager, no engine.run call.
                assertEquals("Nome editado offline",w.repository.dao.schoolClass(school.id)!!.name)
                assertEquals("Outro",w.repository.dao.attendance("network-lesson").single().reason)
                shot("03-reconectado-sem-reiniciar")
            }
            "recover" -> {
                assertTrue(w.repository.readSnapshot().classes.isEmpty());assertNull(store.dao.control())
                w.syncMutex.withLock{AcademicCoordinator.engine(app,w).activate()};w.academicCoordinator.request();synced()
                assertEquals("Nome editado offline",w.repository.dao.schoolClass(school.id)!!.name)
                assertEquals((1..36).map{student(it).id}.toSet(),w.repository.dao.roster(school.id).map{it.id}.toSet())
                for(n in 1..36)assertEquals(student(n).personId,w.repository.dao.student(student(n).id)!!.personId)
                assertEquals(4,w.repository.dao.periods(school.id).size)
                assertTrue(w.repository.readSnapshot().marks.isEmpty());assertTrue(w.repository.readSnapshot().grades.isEmpty())
                w.selection.write(AppSelection(2026,school.id));shot("04-segunda-instalacao")
                val other=PhaseBNetworkHarness.token(other=true);val status=client.status(other)
                client.activate(other,status);assertTrue(client.pull(other,0).isEmpty())
                val row=AcademicRecords.rows(ClassLifecycle.snapshot(w.repository.readSnapshot())).first{it.kind=="classes"}
                assertFalse(client.push(other,AcademicBatch(newId(),listOf(AcademicChange(row.kind,row.key,1,row.payload)))).accepted)
                assertTrue(client.pull(other,0).isEmpty())
                try{client.status("invalid-fixture-jwt");fail()}catch(e:SyncFailure){assertEquals("SESSION",e.code)}
            }
            "edit-secondary" -> {
                synced();w.repository.saveClass(school.copy(name="Editado no segundo dispositivo"),"2026-01-01")
                w.repository.saveStudent(w.repository.dao.student(student(1).id)!!.copy(name="Estudante Atualizado Fictício",notes="Segundo dispositivo"))
                synced();shot("05-edicao-segundo-dispositivo")
            }
            "observe-secondary" -> {
                waitFor{w.repository.dao.schoolClass(school.id)?.name=="Editado no segundo dispositivo"};synced()
                assertEquals("Estudante Atualizado Fictício",w.repository.dao.student(student(1).id)!!.name)
                assertEquals("network-person-1",w.repository.dao.student(student(1).id)!!.personId)
                assertEquals(0.0,w.repository.dao.activityGrades("network-activity").single().value!!,0.0)
                assertEquals("FJ",w.repository.dao.attendance("network-lesson").single().mark)
                assertEquals("Outro",w.repository.dao.attendance("network-lesson").single().reason)
                assertEquals("Diário preservado",w.repository.dao.lessonRecord("network-journal")!!.description)
                shot("06-nome-atualizado-com-historico-local")
            }
            "conflict-local" -> {
                synced();offline();w.repository.saveClass(w.repository.dao.schoolClass(school.id)!!.copy(name="Nome local em conflito"),"2026-01-01")
                assertNotNull(store.prepare())
            }
            "conflict-remote" -> {
                synced();w.repository.saveClass(w.repository.dao.schoolClass(school.id)!!.copy(name="Nome remoto em conflito"),"2026-01-01");synced()
            }
            "observe-conflict" -> {
                waitFor{store.dao.incoming().isNotEmpty()}
                assertEquals("Nome local em conflito",w.repository.dao.schoolClass(school.id)!!.name)
                assertNotNull(store.pending());val page=AcademicRecords.json.decodeFromString<AcademicPage>(store.dao.incoming().single().body)
                assertTrue(page.records.any{it.payload?.get("name")?.jsonPrimitive?.contentOrNull=="Nome remoto em conflito"})
                assertTrue(page.sequence>store.cursor());assertEquals(36,w.repository.dao.roster(school.id).size)
                assertEquals(0.0,w.repository.dao.activityGrades("network-activity").single().value!!,0.0)
                shot("07-conflito-preservado")
            }
            else -> error("Unknown fixture phase")
        }
        File(app.filesDir,"phaseb-network").apply{mkdirs()}.resolve("$phase.txt").writeText("Approved fictitious phase: $phase\nRoom=12; HTTP=PostgREST local; Supabase hosted=not tested\n")
    }
}
