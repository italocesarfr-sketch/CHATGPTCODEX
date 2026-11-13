package br.com.diariodeclasse

import android.os.Build
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import br.com.diariodeclasse.auth.*
import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.ClassLifecycle
import br.com.diariodeclasse.sync.SyncFailure
import br.com.diariodeclasse.sync.academic.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Test
import java.io.File

private enum class H05Stage { INPUT, CLEAN_INSTALL, AUTH, ELIGIBILITY, BASELINE, CONSENT, RECOVERY, VERIFY, REOPEN, NO_REMOTE_CHANGE, CLEANUP, DONE }
private class H05Stop(val code:String):Exception()
private fun need(value:Boolean,code:String="STATE") {if(!value)throw H05Stop(code)}
private data class H05Inventory(val versions:Map<AcademicKey,AcademicVersion>,val pages:List<AcademicPage>,val head:Long) {
    fun rows()=versions.values.filter{it.payload!=null}.map { val c=PhaseBGuard.normalized(it.change());AcademicRow(c.kind,c.key,c.payload!!) }
}

/** One clean account workspace, real Room and existing engine. No UI dump, raw database or APK artifact. */
class H05RecoveryProbeTest {
    @Test fun cleanInstallationRecoversReadOnly():Unit=runBlocking {
        val args=InstrumentationRegistry.getArguments()
        org.junit.Assume.assumeTrue("H05 requires its separate disposable runner opt-in",args.getString("h05Mode") in setOf("LOCAL","HOSTED"))
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as H05RecoveryApplication
        val report=File(app.filesDir,"h05-result.json");val input=File(app.filesDir,"h05-private.json")
        var stage=H05Stage.INPUT;val start=SystemClock.elapsedRealtime()
        val timings=linkedMapOf<String,Long>();val output=linkedMapOf<String,JsonElement>()
        fun put(k:String,v:Boolean){output[k]=JsonPrimitive(v)}
        fun put(k:String,v:Long){output[k]=JsonPrimitive(v)}
        fun write(status:String,code:String?=null) {
            output["case"]=JsonPrimitive("H05");output["mode"]=JsonPrimitive(app.h05Mode)
            output["status"]=JsonPrimitive(status);output["stage"]=JsonPrimitive(stage.name)
            output["failure_code"]=code?.let{JsonPrimitive(it)} ?: JsonNull
            output["total_ms"]=JsonPrimitive(SystemClock.elapsedRealtime()-start)
            output["timings_ms"]=JsonObject(timings.mapValues{JsonPrimitive(it.value)})
            report.writeText(JsonObject(output).toString())
        }
        suspend fun <T> at(value:H05Stage,block:suspend()->T):T {
            stage=value;val t=SystemClock.elapsedRealtime()
            try{return block()}finally{timings[value.name]=SystemClock.elapsedRealtime()-t}
        }
        fun hash(rows:List<AcademicRow>)=BackupCodec.hash(AcademicRecords.canonical(AcademicRecords.json.encodeToJsonElement(rows.sortedWith(compareBy({it.kind},{AcademicRecords.canonical(it.key)})))).toByteArray())
        fun eligible(s:AcademicProtocolStatus) {need(s.protocol==2&&s.academicScope==5&&s.testAllowed,"SCOPE_NOT_READY");need(s.academicSequence in 1..256,"LIMIT")}
        suspend fun inventory(token:String):H05Inventory {
            val status=app.readonly.status(token);eligible(status)
            var cursor=0L;var calls=0;var bytes=0L;var received=0
            val versions=linkedMapOf<AcademicKey,AcademicVersion>();val pages=mutableListOf<AcademicPage>()
            while(cursor<status.academicSequence) {
                var offset=0;var operation="";var total=0;val records=mutableListOf<AcademicVersion>()
                do {
                    need(++calls<=128,"LIMIT")
                    val list=app.readonly.pullE(token,cursor,offset);need(list.size==1,"REMOTE_CHANGED")
                    val f=list.single();f.validate();need(f.sequence==cursor+1&&f.offset==offset,"INVALID_DATA")
                    if(offset==0){operation=f.operation;total=f.total;need(total<=1000,"LIMIT")}
                    need(f.operation==operation&&f.total==total,"INVALID_DATA")
                    received+=f.records.size;need(received<=4096,"LIMIT")
                    bytes+=AcademicRecords.json.encodeToString(f).toByteArray().size;need(bytes<=8*1024*1024,"LIMIT")
                    records+=f.records;offset+=f.records.size
                }while(offset<total)
                need(offset==total,"INVALID_DATA")
                val page=AcademicPage(operation,cursor+1,records);page.validate(AcademicPaging.MAX_RECORDS)
                for(v in records){need(v.revision==(versions[v.identity]?.revision ?: 0L)+1,"INVALID_DATA");versions[v.identity]=v}
                pages+=page;cursor++
            }
            need(app.readonly.pullE(token,cursor,0).isEmpty(),"REMOTE_CHANGED")
            val after=app.readonly.status(token);eligible(after);need(after==status,"REMOTE_CHANGED")
            need(versions.values.count{it.payload!=null}<=1000,"LIMIT")
            return H05Inventory(versions,pages,cursor).also { AcademicRecords.snapshot(it.rows()) }
        }
        suspend fun verify(w:AccountWorkspace,expected:H05Inventory):List<AcademicRow> {
            val db=w.database;val store=AcademicStore(db,w.user.id)
            val snapshot=ClassLifecycle.snapshot(w.repository.readSnapshot()).canonical();BackupCodec.validate(snapshot)
            val rows=AcademicRecords.rows(snapshot);need(rows.associateBy{it.identity}==expected.rows().associateBy{it.identity},"DATA_MISMATCH")
            val c=store.dao.control();need(c!=null&&c.stage=="ACTIVE_E"&&c.checked&&c.message.isEmpty()&&c.cursor==expected.head,"STATE")
            need(store.dao.pending().isEmpty()&&store.dao.incoming().isEmpty()&&store.dao.stagedParts().isEmpty()&&store.dao.decisions().isEmpty(),"QUEUE_NOT_EMPTY")
            val states=store.dao.states();need(states.size==expected.versions.size,"DATA_MISMATCH")
            for(s in states) {
                val v=expected.versions[s.identity()];need(v!=null&&s.owner==w.user.id&&s.revision==v.revision&&s.localVersion==s.acknowledged,"REVISION_MISMATCH")
                val expectedBody=v!!.payload?.let{PhaseBGuard.normalized(v.change()).payload}
                need(s.base?.let{AcademicRecords.json.parseToJsonElement(it)}==expectedBody,"DATA_MISMATCH")
            }
            for(p in expected.pages) {
                val a=store.dao.applied(p.sequence);need(a!=null&&a.owner==w.user.id&&a.operation==p.operation,"RECEIPT_MISMATCH")
                need(a!!.digest==BackupCodec.hash(AcademicRecords.canonical(AcademicRecords.json.encodeToJsonElement(p)).toByteArray()),"RECEIPT_MISMATCH")
            }
            db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use{need(!it.moveToFirst(),"INVALID_DATA")}
            need(db.syncDao().operations().isEmpty()&&db.syncDao().states().isEmpty(),"LEGACY_QUEUE")
            need(app.readonly.blockedWrites==0,"OUTBOUND_BLOCKED")
            return rows
        }
        var failure:String?=null
        try {
            withTimeout(300_000) {
                need(BuildConfig.DEBUG&&Build.HARDWARE in setOf("ranchu","goldfish"),"DISPOSABLE_EMULATOR_REQUIRED")
                need(input.isFile,"INPUT_REQUIRED")
                val j=AcademicRecords.json.parseToJsonElement(input.readText()).jsonObject
                need(j.getValue("mode").jsonPrimitive.content==app.h05Mode&&j.getValue("consentE").jsonPrimitive.content=="YES","CONSENT_REQUIRED")
                val expected=j.getValue("expectedId").jsonPrimitive.content
                val fixture=j.getValue("fixtureClassId").jsonPrimitive.content
                java.util.UUID.fromString(expected);need(expected!=AuthConfig.LEGACY_OWNER_ID,"WRONG_IDENTITY")
                at(H05Stage.CLEAN_INSTALL) {
                    need(!report.exists(),"NOT_CLEAN")
                    val dbDir=app.getDatabasePath("diario.db").parentFile!!
                    need(!dbDir.exists()||dbDir.walkTopDown().none{it.isFile},"NOT_CLEAN")
                    need(KeystoreAuthVault(app).read().let{it.session==null&&it.legacyOwner==null&&it.pending==null},"NOT_CLEAN")
                    app.auth.restore();need(app.auth.state.value.user==null,"NOT_CLEAN");put("clean_install_verified",true)
                }
                at(H05Stage.AUTH) {
                    app.auth.login(j.getValue("email").jsonPrimitive.content,j.getValue("password").jsonPrimitive.content)
                    need(app.auth.state.value.user?.let{it.id==expected&&it.confirmed}==true,"AUTH_FAILED")
                }
                val user=app.auth.state.value.user!!;val w=app.workspaces.activate(user);w.academicCoordinator.close()
                w.syncMutex.withLock {
                    val store=AcademicStore(w.database,user.id)
                    need(AcademicRecords.rows(ClassLifecycle.snapshot(w.repository.readSnapshot())).isEmpty()&&store.dao.states().isEmpty()&&store.dao.control()==null,"NOT_CLEAN")
                    val token=app.auth.syncToken(user.id)
                    at(H05Stage.ELIGIBILITY){eligible(app.readonly.status(token))}
                    val baseline=at(H05Stage.BASELINE){inventory(token)}
                    need(baseline.rows().any{it.kind=="classes"&&it.key["id"]?.jsonPrimitive?.content==fixture},"FIXTURE_MISSING")
                    at(H05Stage.CONSENT){AcademicCoordinator.engine(app,w).activate();need(store.stage()=="ACTIVE_E","CONSENT_REQUIRED");put("consent_recorded",true)}
                    at(H05Stage.RECOVERY) {
                        for(turn in 0 until 5) {
                            AcademicCoordinator.engine(app,w).run()
                            need(app.readonly.blockedWrites==0,"OUTBOUND_BLOCKED")
                            need(!store.blocked(),"CONFLICT")
                            if(store.cursor()==baseline.head&&store.dao.control()?.checked==true)break
                            need(store.dao.control()?.message in setOf("PENDING",""),"RECOVERY_FAILED")
                        }
                    }
                    val rows=at(H05Stage.VERIFY){verify(w,baseline)}
                    val expectedRows=baseline.rows()
                    output["types"]=JsonArray(AcademicKind.entries.map { k ->
                        val local=rows.filter{it.kind==k.wire};val remote=expectedRows.filter{it.kind==k.wire}
                        buildJsonObject{put("kind",k.wire);put("local_count",local.size);put("remote_count",remote.size)
                            put("local_hash",hash(local));put("remote_hash",hash(remote));put("present",remote.isNotEmpty())}
                    })
                    put("records",rows.size.toLong());put("cursor",store.cursor());put("receipts",baseline.pages.size.toLong())
                    put("tombstones",baseline.versions.values.count{it.payload==null}.toLong())
                    put("ids_and_payloads_equal",true);put("revisions_equal",true);put("references_valid",true);put("queues_empty",true)
                    output["remote_hash"]=JsonPrimitive(hash(expectedRows));output["local_hash"]=JsonPrimitive(hash(rows))
                    at(H05Stage.NO_REMOTE_CHANGE){val after=inventory(token);need(after.versions==baseline.versions&&after.pages==baseline.pages,"REMOTE_CHANGED");put("remote_unchanged",true)}
                }
                at(H05Stage.REOPEN) {
                    val beforeHash=output.getValue("local_hash").jsonPrimitive.content
                    app.workspaces.lock();val reopened=app.workspaces.activate(user);reopened.academicCoordinator.close()
                    reopened.syncMutex.withLock {
                        val store=AcademicStore(reopened.database,user.id)
                        val rows=AcademicRecords.rows(ClassLifecycle.snapshot(reopened.repository.readSnapshot()))
                        need(hash(rows)==beforeHash&&store.cursor()==output.getValue("cursor").jsonPrimitive.long,"REOPEN_MISMATCH")
                        need(store.dao.pending().isEmpty()&&store.dao.incoming().isEmpty(),"QUEUE_NOT_EMPTY")
                    }
                    put("reopened_equal",true)
                }
                need(app.readonly.blockedWrites==0,"OUTBOUND_BLOCKED")
                put("blocked_writes",0L);put("read_calls",app.readonly.reads.toLong());put("transport_ms",app.readonly.transportMillis)
            }
        }catch(e:Exception){failure=when(e){is H05Stop->e.code;is H05BlockedWrite->"OUTBOUND_BLOCKED";is TimeoutCancellationException->"TIMEOUT";is SyncFailure->"TRANSPORT_FAILED";else->"UNEXPECTED"}}
        finally {
            val last=stage
            try {at(H05Stage.CLEANUP){app.workspaces.lock();app.auth.logout();input.delete();need(!input.exists(),"CLEANUP_FAILED");put("private_input_removed",true)}}
            catch(_:Exception){failure="CLEANUP_FAILED"}
            if(failure==null){stage=H05Stage.DONE;write("PASSED")}else{stage=last;write("FAILED",failure)}
        }
        if(failure!=null)throw AssertionError("H05_${stage.name}_$failure") // No private exception/cause.
    }
}
