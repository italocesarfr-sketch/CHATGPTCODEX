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
import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.sync.*
import br.com.diariodeclasse.sync.academic.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Fifteen types over actual HTTP/PostgreSQL on independent AVDs. Synthetic Auth only. */
class PhaseDNetworkProbeTest {
    @get:Rule(order=0) val compose=createAndroidComposeRule<MainActivity>()
    @get:Rule(order=1) val entry=enterNoctuaRule(compose)
    private val app get()=compose.activity.application as AcademicTestApplication
    private val w get()=app.workspace
    private val store get()=AcademicStore(w.database,PhaseBNetworkHarness.user().id)
    private val journalId="d-journal-target"
    private fun waitFor(block:suspend()->Boolean){compose.waitUntil(30000){runBlocking{block()}}}
    private fun shell(cmd:String){ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(cmd)).use{it.readBytes()}}
    private fun online():Boolean {val cm=app.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as ConnectivityManager;return cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)==true}
    private fun offline(){shell("svc wifi disable");shell("svc data disable");waitFor{!online()}}
    private fun reconnect(){shell("svc wifi enable");shell("svc data enable");waitFor{online()}}
    private fun synced(){waitFor{val c=store.dao.control();c?.checked==true&&c.message.isEmpty()&&store.dao.pending().isEmpty()&&store.dao.incoming().isEmpty()&&store.dao.states().all{it.localVersion==it.acknowledged}}}
    private suspend fun snapshot()=ClassLifecycle.snapshot(w.repository.readSnapshot()).canonical()
    private suspend fun edit(text:String){val j=w.repository.dao.lessonRecord(journalId)!!;w.repository.dao.save(j.copy(description=text))}
    private suspend fun journal()=w.repository.dao.lessonRecord(journalId)!!.description
    private fun shot(label:String,expectedStatus:String?=null)=compose.academicCapture(app,"phased-network",label,expectedStatus) {
        runBlocking { kotlinx.coroutines.withTimeout(2000) {
            val c=store.dao.control()
            "stage=${c?.stage} message=${c?.message} cursor=${c?.cursor} enabled=${c?.enabled} checked=${c?.checked}\n"+
                "outbox=${store.dao.pending().size} inbox=${store.dao.incoming().size}"
        } }
    }
    @Test fun fifteenAcademicTypesOnIndependentInstallations():Unit=runBlocking {
        val phase=InstrumentationRegistry.getArguments().getString("phaseBNetworkPhase")
        Assume.assumeTrue(phase?.startsWith("d-")==true)
        check(Build.HARDWARE in setOf("ranchu","goldfish")&&BuildConfig.DEBUG&&academicTestEligible(w))
        val client=PhaseBNetworkHarness.raw();val token=PhaseBNetworkHarness.token()
        when(phase) {
            "d-seed"->{
                assertTrue(snapshot().classes.isEmpty())
                w.syncMutex.withLock{val e=AcademicCoordinator.engine(app,w);e.activate();e.run();e.activateC()}
                compose.onNodeWithTag("auth-account").performClick();compose.onNodeWithTag("open-sync").performClick()
                compose.onNodeWithTag("academic-enable-d").performClick()
                compose.onNodeWithText("Ampliar teste").assertIsNotEnabled()
                compose.onNode(isToggleable()).performClick()
                compose.onNodeWithText("Ampliar teste").performClick()
                waitFor{store.stage()=="ACTIVE_D"};compose.onNodeWithText("Concluído").performClick()
                offline();PhaseDFixture.insert(w.database,PhaseDFixture.read())
                val pending=store.prepare()!!;val changes=AcademicRecords.json.decodeFromString<AcademicBatch>(pending.body).changes
                assertEquals(phaseDKinds,changes.map{it.kind}.toSet());assertEquals(PhaseDFixture.read().canonical(),snapshot())
                File(app.filesDir,"phased-operation.txt").writeText(pending.id);shot("01-d-offline")
            }
            "d-lost"->{
                waitFor{PhaseBNetworkHarness.droppedCommittedReply()&&store.pending()?.attempts?.let{it>0}==true}
                assertEquals(File(app.filesDir,"phased-operation.txt").readText(),store.pending()!!.id);assertEquals(0,store.cursor())
            }
            "d-retry"->{
                val p=store.pending()!!;val batch=AcademicRecords.json.decodeFromString<AcademicBatch>(p.body)
                val reply=client.pushD(token,batch);assertTrue(reply.accepted);assertEquals(reply,client.pushD(token,batch))
                PhaseBNetworkHarness.released=true;w.academicCoordinator.request();synced();assertEquals(1,store.cursor());shot("02-d-reenvio")
            }
            "d-recover"->{
                assertTrue(snapshot().classes.isEmpty());w.syncMutex.withLock{AcademicCoordinator.engine(app,w).activate()};w.academicCoordinator.request();synced()
                assertEquals(PhaseDFixture.read().canonical(),snapshot());assertEquals(4,store.scope())
                assertEquals(36,w.repository.dao.roster("d-source").size)
                val other=PhaseBNetworkHarness.token(other=true);client.activate(other,client.status(other));client.enableC(other,0);client.enableD(other,0)
                assertTrue(client.pullD(other,0).isEmpty())
                val row=AcademicRecords.rows(snapshot()).first{it.kind=="lesson_records"}
                assertFalse(client.pushD(other,AcademicBatch(newId(),listOf(AcademicChange(row.kind,row.key,1,row.payload)))).accepted)
                assertTrue(client.pullD(other,0).isEmpty());shot("03-d-recuperado")
            }
            "d-reorder-secondary"->{
                synced();val before=w.repository.dao.exportRows("d-source")
                val requested=before.map{it.copy(position=when(it.position){0->2;2->0;else->1})}
                w.repository.saveExportLayout("d-source",before,requested);synced()
                assertEquals("student:ds2",w.repository.dao.exportRows("d-source").first().id)
                shot("04-d-planilha-reordenada")
            }
            "d-observe-reorder"->{
                waitFor{w.repository.dao.exportRows("d-source").first().id=="student:ds2"};synced()
                val layout=w.repository.dao.exportRows("d-source")
                assertEquals("blank:d-blank",layout[1].id);assertFalse(layout[1].manualHidden)
                assertEquals("student:ds1",layout[2].id);assertTrue(layout[2].manualHidden)
                assertEquals(37,layout.size);assertEquals(36,w.repository.dao.roster("d-source").size)
                assertEquals(PhaseDFixture.read().canonical().marks,snapshot().marks)
                shot("05-d-planilha-recebida")
            }
            "d-edit-secondary"->{synced();edit("Edição inversa; ação & reflexão\nNo segundo dispositivo.");synced();shot("04-d-inverso")}
            "d-observe-secondary"->{waitFor{journal().startsWith("Edição inversa")};synced();assertEquals(PhaseDFixture.read().canonical().marks,snapshot().marks);shot("05-d-recebido")}
            "d-offline-reconnect"->{synced();offline();edit("Edição offline persistida\nReconexão sem reiniciar.");assertNotNull(store.prepare());reconnect();synced();assertTrue(journal().startsWith("Edição offline"));shot("06-d-reconectado")}
            "d-independent-local"->{synced();offline();w.repository.dao.save(snapshot().drafts.single().copy(reviewed=false));assertNotNull(store.prepare())}
            "d-independent-remote"->{synced();w.repository.archiveClass("d-source",false);synced()}
            "d-merge"->{synced();assertFalse(snapshot().drafts.single().reviewed);assertTrue(snapshot().classArchives.isEmpty());assertEquals(PhaseDFixture.read().relocations,snapshot().relocations);shot("07-d-independentes")}
            "d-arm-receive"->{
                synced();w.syncMutex.withLock{store.pause()}
                File(app.filesDir,"phased-before-kill.txt").writeText("${store.cursor()}\n${BackupCodec.fingerprint(snapshot())}")
            }
            "d-rename-remote"->{
                synced();val c=w.repository.dao.schoolClass("d-target")!!
                w.repository.dao.save(c.copy(name="Destino fictício atualizado"));synced()
            }
            "d-crash-receive"->{
                assertFalse(store.active())
                w.syncMutex.withLock {
                    val page=client.pullD(token,store.cursor()).single()
                    // Enabled is changed only in this outer transaction; the deliberate process
                    // death rolls it back too, preventing an automatic retry before inspection.
                    w.database.withTransaction {
                        store.resume()
                        store.receive(page) {
                            File(app.filesDir,"phased-kill-reached.txt").writeText("Parent written; cursor not committed")
                            android.os.Process.killProcess(android.os.Process.myPid())
                            error("Synthetic process kill did not terminate")
                        }
                    }
                }
                fail("The instrumented process must have terminated")
            }
            "d-recover-receive"->{
                assertFalse(store.active());val lines=File(app.filesDir,"phased-before-kill.txt").readLines()
                assertEquals(lines[0].toLong(),store.cursor());assertEquals(lines[1],BackupCodec.fingerprint(snapshot()))
                assertTrue(store.dao.incoming().isEmpty());assertTrue(File(app.filesDir,"phased-kill-reached.txt").isFile)
                w.syncMutex.withLock{store.resume()};w.academicCoordinator.request();synced()
                assertEquals("Destino fictício atualizado",w.repository.dao.schoolClass("d-target")!!.name)
                assertTrue(store.cursor()>lines[0].toLong());shot("08-d-recebimento-retomado")
            }
            "d-new-timetable"->{
                synced();val before=w.repository.readSnapshot();val slots=before.timetables.single{it.id=="d-t2"}.slots
                val target=before.classes.single{it.id=="d-target"}
                val added=WeeklySlot(classId=target.id,weekday=5,ordinal=2,className=target.name,subject=target.subject,shift=target.shift)
                w.repository.confirmTimetable(w.repository.previewTimetable(2026,"2026-11-03",slots+added));synced()
                assertEquals(3,snapshot().timetables.size);assertEquals(ClassLifecycle.snapshot(before).canonical().marks,snapshot().marks)
            }
            "d-observe-timetable"->{
                waitFor{snapshot().timetables.size==3};synced()
                val s=snapshot();assertTrue(s.timetables.containsAll(PhaseDFixture.read().timetables))
                assertEquals(listOf(2),Timetables.planned(s.diarySnapshot(),"d-target","2026-11-06").map{it.ordinal})
                assertEquals(listOf(1),Timetables.planned(s.diarySnapshot(),"d-source","2026-10-15").map{it.ordinal})
                assertEquals(PhaseDFixture.read().canonical().lessons,s.lessons)
                shot("08-d-nova-vigencia")
            }
            "d-new-relocation"->{
                synced();val before=snapshot()
                val preview=w.repository.previewRelocation("ds2","d-target",null,"2026-11-10")
                w.repository.confirmRelocation(preview);synced()
                val after=snapshot();assertEquals(before.marks,after.marks);assertEquals(before.grades,after.grades)
                assertEquals("Remanejado",w.repository.dao.student("ds2")!!.status)
                val link=after.relocations.single{it.sourceStudentId=="ds2"}
                assertEquals("2026-11-10",w.repository.dao.student(link.targetStudentId!!)!!.joined)
                File(app.filesDir,"phased-movement-hash.txt").writeText(BackupCodec.fingerprint(after));shot("09-d-remanejamento")
            }
            "d-observe-relocation"->{
                waitFor{snapshot().relocations.any{it.sourceStudentId=="ds2"}};synced()
                val s=snapshot();val link=s.relocations.single{it.sourceStudentId=="ds2"};val source=s.students.single{it.id=="ds2"}
                val target=s.students.single{it.id==link.targetStudentId}
                assertEquals(source.personId,target.personId);assertEquals("2026-11-09",source.left);assertEquals("2026-11-10",target.joined)
                assertEquals(2,s.students.count{it.classId=="d-target"});assertEquals(PhaseDFixture.read().canonical().marks,s.marks)
                File(app.filesDir,"phased-movement-hash.txt").writeText(BackupCodec.fingerprint(s));shot("10-d-movimentacao-recebida")
            }
            "d-delete-source"->{
                synced();val before=w.repository.readSnapshot();val preview=w.repository.previewClassDeletion("d-source");assertNull(preview.blockedReason)
                w.repository.deleteClass(preview,preview.confirmation);synced();val after=w.repository.readSnapshot();ClassLifecycle.verifyPreservedResults(before,after)
                assertEquals(setOf("d-past","d-source"),after.histories.map{it.classId}.toSet());assertEquals(2,after.students.size)
                assertEquals(2,after.academic().relocations.single{it.sourceStudentId=="ds1"}.corrections.size)
                File(app.filesDir,"phased-deleted-hash.txt").writeText(BackupCodec.fingerprint(snapshot()));shot("08-d-historico")
            }
            "d-observe-deletion"->{
                waitFor{w.repository.dao.schoolClass("d-source")==null};synced();val s=snapshot()
                assertEquals(setOf("d-past","d-source"),s.histories.map{it.classId}.toSet());assertEquals(2,s.students.size)
                assertEquals(2,s.diarySnapshot().academic().relocations.single{it.sourceStudentId=="ds1"}.corrections.size)
                assertEquals(s,BackupCodec.decode(BackupCodec.encode(s,42,null)).snapshot.canonical())
                File(app.filesDir,"phased-deleted-hash.txt").writeText(BackupCodec.fingerprint(s));shot("09-d-historico-recebido")
            }
            "d-conflict-local"->{synced();offline();edit("Variante local preservada");assertNotNull(store.prepare())}
            "d-conflict-remote"->{synced();edit("Variante remota preservada");synced()}
            "d-observe-conflict"->{
                waitFor{store.dao.incoming().isNotEmpty()};assertEquals("Variante local preservada",journal());assertNotNull(store.pending())
                val incoming=store.dao.incoming().single();assertTrue(incoming.body.contains("Variante remota preservada"));assertTrue(incoming.sequence>store.cursor())
                w.syncMutex.withLock{repeat(2){AcademicCoordinator.engine(app,w).run()}};assertEquals("CONFLICT",store.dao.control()!!.message);shot("10-d-conflito","Conflito preservado")
            }
            else->error("Unknown D phase")
        }
        File(app.filesDir,"phased-network").apply{mkdirs()}.resolve("$phase.txt").writeText("Approved $phase; local HTTP/PostgREST; synthetic Auth only.\n")
    }
}
