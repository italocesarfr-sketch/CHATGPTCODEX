package br.com.diariodeclasse

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import br.com.diariodeclasse.auth.*
import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.ClassLifecycle
import br.com.diariodeclasse.sync.academic.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.*
import java.io.File

/** Only UI login/logout and observations. The production startup/coordinator owns recovery. */
class P1RecoveryProbeTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun waitFor(test:()->Boolean){compose.waitUntil(120000,test)}
    private fun tag(name:String)=compose.onNodeWithTag(name)
    @Test fun uiLoginAutomaticallyRecoversAndIsolatesAccounts():Unit=runBlocking {
        val args=InstrumentationRegistry.getArguments()
        Assume.assumeTrue("P1 independent opt-in required",args.getString("p1Mode") in setOf("LOCAL","HOSTED"))
        val app=compose.activity.application as AcademicTestApplication;val support=app.p1
        val report=File(app.filesDir,"p1-result.json");val previousReport=report.exists();val start=SystemClock.elapsedRealtime()
        var stage="INPUT";var failure:String?=null;val out=linkedMapOf<String,JsonElement>();val times=linkedMapOf<String,Long>()
        fun flag(k:String){out[k]=JsonPrimitive(true)}
        fun write(status:String) {
            out["case"]=JsonPrimitive("P1");out["mode"]=JsonPrimitive(support.mode);out["status"]=JsonPrimitive(status);out["stage"]=JsonPrimitive(stage)
            out["failure_code"]=failure?.let{JsonPrimitive(it)} ?: JsonNull
            out["total_ms"]=JsonPrimitive(SystemClock.elapsedRealtime()-start)
            out["timings_ms"]=JsonObject(times.mapValues{JsonPrimitive(it.value)})
            out["blocked_writes"]=JsonPrimitive(support.readonly.blockedWrites);out["read_calls"]=JsonPrimitive(support.readCalls)
            out["login_calls"]=JsonPrimitive(support.loginCalls);out["logout_calls"]=JsonPrimitive(support.logoutCalls)
            report.writeText(JsonObject(out).toString())
        }
        suspend fun <T> at(name:String,block:suspend()->T):T {
            stage=name;val t=SystemClock.elapsedRealtime();write("RUNNING")
            try{return block()}finally{times[name]=SystemClock.elapsedRealtime()-t}
        }
        fun current(account:P1Account)=app.workspaces.current().also{p1Need(it.user.id==account.id&&!it.legacy,"IDENTITY")}
        fun login(account:P1Account) {
            waitFor{compose.onAllNodesWithTag("entry-email").fetchSemanticsNodes().isNotEmpty()&&!app.auth.state.value.loading}
            tag("entry-email").performScrollTo().performTextReplacement(account.email)
            tag("entry-password").performScrollTo().performTextReplacement(account.password)
            compose.runOnIdle{(app.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(compose.activity.window.decorView.windowToken,0)}
            tag("entry-enter").performScrollTo().assertIsEnabled().performClick()
            waitFor{app.auth.state.value.let{!it.loading&&(it.user?.id==account.id||it.message.isNotEmpty())}}
            p1Need(app.auth.state.value.user?.let{it.id==account.id&&it.confirmed}==true,"AUTH")
            waitFor{runCatching{current(account)}.isSuccess&&compose.onAllNodesWithTag("auth-account").fetchSemanticsNodes().isNotEmpty()}
        }
        fun logout() {
            tag("auth-account").performClick();tag("auth-logout").performClick()
            waitFor{app.auth.state.value.user==null&&compose.onAllNodesWithTag("entry-email").fetchSemanticsNodes().isNotEmpty()}
        }
        suspend fun verify(account:P1Account,expected:H05Inventory):List<AcademicRow> {
            waitFor{runBlocking{val w=current(account);val c=w.database.academicDao().control()
                c?.let{it.stage=="ACTIVE_E"&&it.checked&&it.message.isEmpty()&&it.cursor==expected.head}==true}}
            val w=current(account);val db=w.database;val dao=db.academicDao()
            val rows=AcademicRecords.rows(ClassLifecycle.snapshot(w.repository.readSnapshot()).canonical())
            p1Need(rows.associateBy{it.identity}==expected.rows().associateBy{it.identity},"DATA")
            val states=dao.states();p1Need(states.size==expected.versions.size,"DATA")
            for(s in states) {
                val v=expected.versions[s.identity()];p1Need(v!=null&&s.owner==account.id&&s.revision==v.revision&&s.localVersion==s.acknowledged,"REVISION")
                p1Need(s.base?.let{AcademicRecords.json.parseToJsonElement(it)}==v!!.payload?.let{PhaseBGuard.normalized(v.change()).payload},"REVISION")
            }
            for(page in expected.pages) {
                val receipt=dao.applied(page.sequence)
                p1Need(receipt!=null&&receipt.owner==account.id&&receipt.operation==page.operation&&receipt.digest==BackupCodec.hash(AcademicRecords.canonical(AcademicRecords.json.encodeToJsonElement(AcademicPage.serializer(),page)).toByteArray()),"RECEIPT")
            }
            p1Need(dao.pendingHeaders().isEmpty()&&dao.incomingHeaders().isEmpty()&&dao.stagedParts().isEmpty()&&dao.decisionHeaders().isEmpty(),"QUEUE")
            p1Need(db.syncDao().operations().isEmpty()&&db.syncDao().states().isEmpty(),"LEGACY")
            db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use{p1Need(!it.moveToFirst(),"REFERENCES")}
            p1Need(support.readonly.blockedWrites==0,"WRITE_ATTEMPT")
            return rows
        }
        try {
            withTimeout(300000) {
                at("CLEAN") {
                    p1Need(BuildConfig.DEBUG&&Build.HARDWARE in setOf("ranchu","goldfish"),"DEVICE")
                    val dir=app.getDatabasePath("diario.db").parentFile!!
                    p1Need(!previousReport&&(!dir.exists()||dir.walkTopDown().none{it.isFile}),"NOT_CLEAN")
                    p1Need(KeystoreAuthVault(app).read().let{it.session==null&&it.legacyOwner==null&&it.pending==null},"NOT_CLEAN")
                    waitFor{!app.auth.state.value.initializing};p1Need(app.auth.state.value.user==null,"NOT_CLEAN")
                    flag("clean_install_verified")
                }
                val a=support.accounts[0];val b=support.accounts[1]
                at("LOGIN_A"){login(a);flag("ui_login_a_verified")}
                val tokenA=app.auth.syncToken(a.id)
                val baselineA=at("RECOVER_A"){support.inventory(tokenA)}
                p1Need(baselineA.rows().any{it.kind=="classes"&&it.key["id"]?.jsonPrimitive?.content==support.fixture},"FIXTURE")
                val ledgerA=support.ledgerHash(support.select(tokenA,a.id,true))
                val rowsA=at("VERIFY_A"){verify(a,baselineA)}
                p1Need(support.select(tokenA,b.id,false).isEmpty(),"ISOLATION")
                out["types_a"]=h05TypeSummary(rowsA,baselineA.rows());out["a_hash"]=JsonPrimitive(h05RowsHash(rowsA))
                out["a_head"]=JsonPrimitive(baselineA.head);out["a_records"]=JsonPrimitive(rowsA.size)
                flag("automatic_startup_verified")
                at("LOGIN_B"){logout();login(b);flag("ui_login_b_verified")}
                val tokenB=app.auth.syncToken(b.id)
                val baselineB=at("RECOVER_B"){support.inventory(tokenB)}
                val ledgerB=support.ledgerHash(support.select(tokenB,b.id,true))
                val rowsB=at("VERIFY_B"){verify(b,baselineB)}
                p1Need(support.select(tokenB,a.id,false).isEmpty(),"ISOLATION")
                p1Need(support.inventory(tokenB)==baselineB&&support.ledgerHash(support.select(tokenB,b.id,true))==ledgerB,"REMOTE_CHANGED")
                out["types_b"]=h05TypeSummary(rowsB,baselineB.rows());out["b_hash"]=JsonPrimitive(h05RowsHash(rowsB))
                out["b_head"]=JsonPrimitive(baselineB.head);out["b_records"]=JsonPrimitive(rowsB.size)
                at("RETURN_A"){logout();login(a)}
                val returned=at("VERIFY_RETURN"){verify(a,baselineA)}
                p1Need(returned.associateBy{it.identity}==rowsA.associateBy{it.identity},"DATA")
                val newToken=app.auth.syncToken(a.id)
                p1Need(support.inventory(newToken)==baselineA&&support.ledgerHash(support.select(newToken,a.id,true))==ledgerA,"REMOTE_CHANGED")
                for(f in listOf("ids_payloads_equal","revisions_equal","receipts_equal","references_valid","queues_empty","remote_unchanged","audits_unchanged","a_b_a_isolated"))flag(f)
            }
        }catch(e:Exception){failure=when(e){is P1Stop->e.code;is TimeoutCancellationException->"TIMEOUT";is H05BlockedWrite->"WRITE_ATTEMPT";else->"UNEXPECTED"}}
        finally {
            val cleanup=runCatching { withTimeoutOrNull(30000){
                app.auth.logout();app.workspaces.lock();support.cleanup()
            }==true }.getOrDefault(false)
            if(cleanup)flag("sessions_cleanup_verified")else if(failure==null)failure="CLEANUP"
            val input=File(app.filesDir,"p1-private.json");if(input.delete()||!input.exists())flag("private_input_removed")else if(failure==null)failure="CLEANUP"
            if(failure==null)stage="DONE"
            write(if(failure==null)"PASSED"else"FAILED")
        }
        p1Need(failure==null,"PROOF_FAILED")
    }
}
