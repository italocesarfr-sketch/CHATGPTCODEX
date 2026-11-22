package br.com.diariodeclasse

import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import br.com.diariodeclasse.auth.*
import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.sync.SyncFailure
import br.com.diariodeclasse.sync.academic.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Real local HTTP/Room/Keystore/lifecycle, two CI AVDs; synthetic Auth, never hosted. */
class CommercialFinalNetworkProbeTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val app get()=compose.activity.application as AcademicTestApplication
    private val w get()=app.workspaces.current()
    private val store get()=AcademicStore(w.database,w.user.id)
    private val phase get()=requireNotNull(PhaseBNetworkHarness.phase)
    private suspend fun snapshot()=ClassLifecycle.snapshot(w.repository.readSnapshot()).canonical()
    private suspend fun grade()=w.repository.dao.activityGrades("d-a1").single{it.studentId=="ds2"}.value
    private fun file(name:String)=File(app.filesDir,"commercial-final-$name")
    private fun waitFor(check:suspend()->Boolean) {
        try{compose.waitUntil(60000){runBlocking{check()}}}catch(e:Throwable){
            val diagnostic=runBlocking{runCatching{val c=store.dao.control();"stage=${c?.stage} message=${c?.message} cursor=${c?.cursor} pending=${store.dao.pendingHeaders().size} inbox=${store.dao.incomingHeaders().size}"}.getOrDefault("WORKSPACE_LOCKED")}
            throw AssertionError("$phase $diagnostic",e)
        }
    }
    private fun synced(){waitFor{store.dao.control()?.let{it.stage=="ACTIVE_E"&&it.checked&&it.message.isEmpty()}==true&&
        store.dao.pendingHeaders().isEmpty()&&store.dao.incomingHeaders().isEmpty()&&store.dao.stagedParts().isEmpty()&&
        store.dao.states().all{it.localVersion==it.acknowledged}&&store.dao.decisions().none{it.phase in setOf("PENDING","CONFIRMED")}}}
    private fun shell(cmd:String){ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(cmd)).use{it.readBytes()}}
    private fun offline(){shell("svc wifi disable");shell("svc data disable");waitFor{(w.context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager).activeNetwork==null}}
    private fun online(){shell("svc wifi enable");shell("svc data enable");waitFor{(app.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager).activeNetwork!=null}}
    private fun login(other:Boolean=false) {
        compose.waitUntil(15000){compose.onAllNodesWithTag("entry-email").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("entry-email").performTextClearance();compose.onNodeWithTag("entry-email").performTextInput(if(other)CommercialFinalAuthClient.OTHER_EMAIL else fixtureUser.email)
        compose.onNodeWithTag("entry-password").performTextClearance();compose.onNodeWithTag("entry-password").performTextInput(CommercialFinalAuthClient.PASSWORD)
        compose.onNodeWithTag("entry-enter").performScrollTo().performClick()
        waitFor{runCatching{w.user.id==if(other)CommercialFinalAuthClient.OTHER_ID else PhaseBNetworkHarness.user().id}.getOrDefault(false)}
    }
    private fun popup(local:Double,remote:Double) {
        compose.waitUntil(30000){compose.onAllNodesWithTag("grade-conflict-dialog").fetchSemanticsNodes().isNotEmpty()&&
            compose.onAllNodesWithText("Neste aparelho: ${conflictGradeNumber(local)}").fetchSemanticsNodes().isNotEmpty()&&
            compose.onAllNodesWithText("Na nuvem: ${conflictGradeNumber(remote)}").fetchSemanticsNodes().isNotEmpty()}
    }
    private fun openReview() {
        compose.onNodeWithTag("automatic-sync-status").performClick()
        compose.waitUntil(15000){compose.onAllNodesWithTag("academic-review").fetchSemanticsNodes().singleOrNull()?.let{isEnabled().matches(it)&&hasClickAction().matches(it)}==true}
        compose.onNodeWithTag("academic-review").performScrollTo().performClick()
    }
    private fun choose(choice:AcademicChoice) {
        compose.waitUntil(30000){compose.onAllNodesWithTag("grade-keep-${choice.name}").fetchSemanticsNodes().isNotEmpty()||compose.onAllNodesWithTag("academic-choice-${choice.name}").fetchSemanticsNodes().isNotEmpty()}
        if(compose.onAllNodesWithTag("grade-keep-${choice.name}").fetchSemanticsNodes().isNotEmpty())
            compose.onNodeWithTag("grade-keep-${choice.name}").performScrollTo().assertIsEnabled().performClick()
        else {
            compose.onNodeWithTag("academic-confirm-decision").assertIsNotEnabled()
            compose.onNodeWithTag("academic-choice-${choice.name}").performScrollTo().performClick()
            compose.onNodeWithTag("academic-confirm-decision").performScrollTo().performClick()
        }
    }
    private fun shot(name:String) {
        compose.waitForIdle();val folder=file("screenshots").apply{mkdirs()}
        requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()).let{b->File(folder,"$name.png").outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
    }
    private suspend fun evidence() {
        val rows=snapshot();val c=requireNotNull(store.dao.control());val states=store.dao.states()
        w.database.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}
        assertEquals(AcademicRecords.rows(rows).size,AcademicRecords.rows(rows).map{it.identity}.toSet().size)
        file("$phase.json").writeText(buildJsonObject {
            put("phase",phase);put("hash",BackupCodec.fingerprint(rows));put("cursor",c.cursor);put("stage",c.stage);put("message",c.message)
            put("counts",buildJsonObject{AcademicRecords.rows(rows).groupBy{it.kind}.toSortedMap().forEach{(k,v)->put(k,v.size)}})
            put("pending",store.dao.pendingHeaders().size);put("inbox",store.dao.incomingHeaders().size)
            put("states",buildJsonArray{states.sortedWith(compareBy({it.kind},{it.key1},{it.key2})).forEach{s->add(buildJsonObject {
                put("kind",s.kind);put("key_hash",BackupCodec.hash((s.key1+"|"+s.key2).toByteArray()));put("revision",s.revision)
                put("local",s.localVersion);put("acknowledged",s.acknowledged)
            })}})
            put("decisions",buildJsonArray{store.dao.decisions().forEach{d->add(buildJsonObject{put("phase",d.phase);put("body_hash",BackupCodec.hash(d.body.toByteArray()))})}})
        }.toString())
    }
    @Test fun finalCommercialScenariosNineThroughThirteen():Unit=runBlocking {
        Assume.assumeTrue(PhaseBNetworkHarness.phase?.startsWith("e-auto-final-")==true)
        check(BuildConfig.DEBUG&&BuildConfig.ACADEMIC_COMMERCIAL&&Build.HARDWARE in setOf("ranchu","goldfish"))
        if(phase=="e-auto-final-reopen-offline") {
            waitFor{!app.auth.state.value.initializing};assertNotNull(app.auth.state.value.user);assertTrue(app.auth.state.value.offline)
        }
        if(phase=="e-auto-final-login-after-logout") {
            waitFor{!app.auth.state.value.initializing};assertNull(app.auth.state.value.user)
            assertTrue(runCatching{w}.isFailure);assertNull(KeystoreAuthVault(app).read().session)
            // Inspect only this synthetic fixture's disk persistence while the actual UI
            // remains locked. No new offline login or manual workspace admission.
            val namespace=BackupCodec.hash(PhaseBNetworkHarness.user().id.toByteArray())
            val path=File(app.getDatabasePath("diario.db").parentFile,"accounts/$namespace/diario.db")
            val db=DiaryDatabase.open(app,path.absolutePath)
            try {
                assertEquals(7.5,db.dao().activityGrades("d-a1").single{it.studentId=="ds2"}.value!!,0.0)
                assertEquals(file("pending-id").readText(),db.academicDao().pending().single().id)
            }finally{db.close()}
            file("$phase.json").writeText("""{"phase":"$phase","logged_out":true,"workspace_locked":true,"pending_preserved":true}""")
            return@runBlocking
        }
        if(phase=="e-auto-final-push-lost"&&app.auth.state.value.user==null)online()
        if(app.auth.state.value.user==null)login()
        waitFor{runCatching{w.user.id==PhaseBNetworkHarness.user().id}.getOrDefault(false)}
        try { when(phase) {
            "e-auto-final-offline-one","e-auto-final-offline-restart"->{
                synced();offline();w.repository.dao.save(Grade("d-a1","ds2",if(phase.endsWith("restart"))2.5 else 1.5))
                assertTrue(store.dao.states().any{it.kind=="grades"&&it.localVersion>it.acknowledged})
                file("pending-id").writeText(store.prepare()!!.id)
            }
            "e-auto-final-peer-one","e-auto-final-peer-two"->{
                synced();w.repository.dao.save(snapshot().lessonRecords.first().copy(description=phase));synced()
            }
            "e-auto-final-reopen-offline"->{assertEquals(2.5,grade()!!,0.0);assertEquals(file("pending-id").readText(),store.pending()!!.id)}
            "e-auto-final-converge-one","e-auto-final-converge-two"->{
                online();synced();assertEquals(if(phase.endsWith("one"))1.5 else 2.5,grade()!!,0.0)
                assertTrue(snapshot().lessonRecords.any{it.description==if(phase.endsWith("one"))"e-auto-final-peer-one"else"e-auto-final-peer-two"})
                assertTrue(store.dao.states().all{it.localVersion==it.acknowledged})
            }
            "e-auto-final-peer-observe-one","e-auto-final-peer-observe-two"->{waitFor{grade()==if(phase.endsWith("one"))1.5 else 2.5};synced()}
            "e-auto-final-conflict-local","e-auto-final-stale-local"->{synced();offline();w.repository.dao.save(Grade("d-a1","ds2",if(phase.endsWith("local")&&phase.contains("stale"))5.0 else 4.0));assertNotNull(store.prepare())}
            "e-auto-final-conflict-remote","e-auto-final-stale-remote"->{synced();w.repository.dao.save(Grade("d-a1","ds2",if(phase.contains("stale"))8.0 else 9.0));synced()}
            "e-auto-final-resolve-local"->{
                online();popup(4.0,9.0);val before=store.dao.decisions().size;val cursor=store.cursor()
                InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                compose.waitUntil(10000){compose.onAllNodesWithTag("grade-conflict-dialog").fetchSemanticsNodes().isEmpty()}
                assertTrue(store.blocked());assertEquals(4.0,grade()!!,0.0);assertEquals(before,store.dao.decisions().size);assertEquals(cursor,store.cursor())
                openReview();popup(4.0,9.0);shot("explicit-four-or-nine");choose(AcademicChoice.KEEP_LOCAL);synced();assertEquals(4.0,grade()!!,0.0)
                val frozen=AcademicRecords.json.decodeFromString<AcademicFrozenDecision>(store.dao.decisions().last().body)
                assertEquals(AcademicChoice.KEEP_LOCAL,frozen.choice);assertTrue(frozen.oldPending.isNotEmpty());assertTrue(frozen.oldInbox.isNotEmpty())
                assertTrue(PhaseBNetworkHarness.raw().decisionE(PhaseBNetworkHarness.token(),store.dao.decisions().last().id)!!.accepted)
            }
            "e-auto-final-observe-local"->{waitFor{grade()==4.0};synced()}
            "e-auto-final-decision-lost"->{
                online();popup(5.0,8.0);val count=store.dao.decisions().size;val cursor=store.cursor()
                val preview=w.syncMutex.withLock{AcademicReconciliation(store,PhaseBNetworkHarness.raw(),{PhaseBNetworkHarness.token()},{true}).preview()}
                val key=buildJsonObject{put("activityId","d-a1");put("studentId","ds2")}
                val payload=buildJsonObject{put("activityId","d-a1");put("studentId","ds2");put("value",9.5)}
                assertTrue(PhaseBNetworkHarness.raw().pushE(PhaseBNetworkHarness.token(fromSecondary=true),AcademicBatch(newId(),listOf(AcademicChange("grades",key,preview.latest.getValue(AcademicKey("grades",key)),payload)))){true}.accepted)
                choose(AcademicChoice.KEEP_REMOTE)
                compose.waitUntil(15000){compose.onAllNodesWithTag("grade-conflict-error").fetchSemanticsNodes().isNotEmpty()}
                assertEquals(count,store.dao.decisions().size);assertEquals(cursor,store.cursor());assertEquals(5.0,grade()!!,0.0)
                compose.onNodeWithTag("grade-keep-KEEP_LOCAL").assertIsNotEnabled();compose.onNodeWithTag("grade-keep-KEEP_REMOTE").assertIsNotEnabled();shot("stale-rejected")
                compose.onNodeWithTag("grade-conflict-refresh").performScrollTo().performClick();popup(5.0,9.5);choose(AcademicChoice.KEEP_REMOTE)
                waitFor{PhaseBNetworkHarness.droppedCommittedReply()&&store.dao.decisions().last().phase=="PENDING"}
                file("decision-id").writeText(store.dao.decisions().last().id)
                assertEquals(cursor,store.cursor());assertEquals(5.0,grade()!!,0.0)
            }
            "e-auto-final-decision-replay"->{
                synced();assertEquals(9.5,grade()!!,0.0);val d=store.dao.decisions().last()
                assertEquals(file("decision-id").readText(),d.id);assertEquals("APPLIED",d.phase)
                val frozen=AcademicRecords.json.decodeFromString<AcademicFrozenDecision>(d.body)
                assertEquals(5.0,frozen.request.getValue("evidence").jsonArray.single().jsonObject.getValue("local").jsonObject.getValue("value").jsonPrimitive.double,0.0)
                assertEquals(9.5,frozen.remote.single{it.kind=="grades"&&it.key.getValue("studentId").jsonPrimitive.content=="ds2"}.payload!!.getValue("value").jsonPrimitive.double,0.0)
            }
            "e-auto-final-observe-remote"->{waitFor{grade()==9.5};synced()}
            "e-auto-final-logout-pending"->{
                synced();offline();w.repository.dao.save(Grade("d-a1","ds2",7.5));file("pending-id").writeText(store.prepare()!!.id)
                br.com.diariodeclasse.ui.DiaryViewModel(app).logout();waitFor{app.auth.state.value.user==null&&runCatching{w}.isFailure}
                assertNull(KeystoreAuthVault(app).read().session);return@runBlocking
            }
            "e-auto-final-push-lost"->{online();waitFor{PhaseBNetworkHarness.droppedCommittedReply()&&store.pending()!=null};assertEquals(file("pending-id").readText(),store.pending()!!.id)}
            "e-auto-final-push-replay"->{synced();assertEquals(7.5,grade()!!,0.0);assertEquals(file("pending-id").readText(),store.dao.applied(store.cursor())!!.operation)}
            "e-auto-final-observe-push"->{waitFor{grade()==7.5};synced()}
            "e-auto-final-isolation"->{
                synced();val before=snapshot();val originalCursor=store.cursor();val other=PhaseBNetworkHarness.token(true)
                assertFalse(PhaseBNetworkHarness.raw().ensureE(other).eligible)
                try{PhaseBNetworkHarness.raw().pullE(other,0,0);fail()}catch(e:SyncFailure){assertEquals("SESSION",e.code)}
                br.com.diariodeclasse.ui.DiaryViewModel(app).logout();waitFor{app.auth.state.value.user==null&&runCatching{w}.isFailure}
                login(true);waitFor{store.stage()=="BOOTSTRAP_E"&&store.dao.control()?.message=="INELIGIBLE"};assertTrue(snapshot().classes.isEmpty());assertFalse(store.active())
                w.repository.saveClass(SchoolClass("other-local-only",name="Outra conta fictícia",subject="História",year=2026),"2026-01-01")
                assertTrue(store.dao.states().all{it.owner==CommercialFinalAuthClient.OTHER_ID})
                br.com.diariodeclasse.ui.DiaryViewModel(app).logout();waitFor{app.auth.state.value.user==null&&runCatching{w}.isFailure}
                login();synced();assertEquals(before,snapshot());assertEquals(originalCursor,store.cursor());assertNull(w.repository.dao.schoolClass("other-local-only"))
            }
            "e-auto-final-backup"->{
                synced();val plain=w.backup.backupBytes();val encrypted=AccountBackup.seal(plain,w.user.id,"Fictitious-Final-Backup-2026".toCharArray())
                assertTrue(plain.contentEquals(AccountBackup.open(encrypted,w.user.id,"Fictitious-Final-Backup-2026".toCharArray())))
                try{AccountBackup.open(encrypted,CommercialFinalAuthClient.OTHER_ID,"Fictitious-Final-Backup-2026".toCharArray());fail()}catch(_:AuthFailure){}
                file("backup.enc").writeBytes(encrypted);file("backup-hash").writeText(BackupCodec.fingerprint(snapshot()))
                offline();w.repository.dao.save(Grade("d-a1","ds2",5.5));file("restore-pending-body").writeText(store.prepare()!!.body)
            }
            "e-auto-final-after-backup"->{
                synced();w.repository.dao.save(Grade("d-a1","ds2",8.5))
                w.repository.saveStudent(Student("commercial-remote-only","commercial-new-person","d-source","Estudante Fictício Posterior",40,"2026-01-01"));synced()
            }
            "e-auto-final-restore"->{
                val pending=file("restore-pending-body").readText();assertEquals(pending,store.pending()!!.body)
                val plain=AccountBackup.open(file("backup.enc").readBytes(),w.user.id,"Fictitious-Final-Backup-2026".toCharArray())
                w.syncMutex.withLock{w.backup.restore(w.backup.prepare(plain.inputStream()),"RESTAURAR")}
                assertEquals("RESTORE",store.stage());assertFalse(store.active());assertEquals(pending,store.pending()!!.body)
                assertEquals(file("backup-hash").readText(),BackupCodec.fingerprint(snapshot()));assertNull(w.repository.dao.student("commercial-remote-only"))
                online();val cursor=store.cursor();openReview()
                compose.waitUntil(30000){compose.onAllNodesWithTag("academic-choice-KEEP_REMOTE").fetchSemanticsNodes().isNotEmpty()}
                assertEquals("RESTORE",store.stage());assertEquals(cursor,store.cursor());assertEquals(pending,store.pending()!!.body)
                shot("restore-explicit-remote");choose(AcademicChoice.KEEP_REMOTE);synced()
                assertEquals(8.5,grade()!!,0.0);assertNotNull(w.repository.dao.student("commercial-remote-only"))
                val d=AcademicRecords.json.decodeFromString<AcademicFrozenDecision>(store.dao.decisions().last().body)
                assertTrue(d.restore);assertTrue(pending in d.oldPending);assertEquals(AcademicChoice.KEEP_REMOTE,d.choice)
            }
            "e-auto-final-final"->{
                synced();waitFor{grade()==8.5};assertNotNull(w.repository.dao.student("commercial-remote-only"))
                val fixture=PhaseDFixture.read().canonical();val actual=snapshot()
                assertEquals(fixture.marks,actual.marks);assertEquals(fixture.histories,actual.histories);assertEquals(fixture.relocations,actual.relocations)
                assertEquals(172,AcademicRecords.rows(actual).size);assertEquals(15,AcademicRecords.rows(actual).map{it.kind}.toSet().size)
                assertEquals(37,w.repository.dao.roster("d-source").size)
                file("final.sha256").writeText(BackupCodec.fingerprint(actual))
            }
            else->error("Unknown final disposable scenario")
        } } catch(failure:Throwable) {
            runCatching{evidence()};runCatching{shot("$phase-failed")}
            throw failure
        }
        evidence()
    }
}
