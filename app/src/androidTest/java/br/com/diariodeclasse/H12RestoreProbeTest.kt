package br.com.diariodeclasse

import android.os.Build
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import br.com.diariodeclasse.auth.*
import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.sync.academic.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import org.junit.Test
import java.io.File

/** Uses actual encrypted backups, Room, E engine and HTTP. No hosted academic mutations. */
class H12RestoreProbeTest {
    @Test fun oldEncryptedBackupRequiresExplicitCloudReconciliation():Unit=runBlocking {
        val args=InstrumentationRegistry.getArguments()
        org.junit.Assume.assumeTrue("H12 requires independent disposable runner",args.getString("h12Mode") in setOf("LOCAL","HOSTED"))
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AcademicTestApplication
        val mode=args.getString("h12Mode")!!;val input=File(app.filesDir,"h12-private.json")
        val report=File(app.filesDir,"h12-result.json");val start=SystemClock.elapsedRealtime()
        var stage="INPUT";var failure:String?=null
        val result=linkedMapOf<String,JsonElement>();val timings=linkedMapOf<String,JsonElement>()
        fun flag(name:String,value:Boolean=true){result[name]=JsonPrimitive(value)}
        fun number(name:String,value:Long){result[name]=JsonPrimitive(value)}
        suspend fun <T> at(name:String,block:suspend()->T):T {
            stage=name;val t=SystemClock.elapsedRealtime();try{return block()}finally{timings[name]=JsonPrimitive(SystemClock.elapsedRealtime()-t)}
        }
        fun need(ok:Boolean,code:String){check(ok){"H12_"+code}}
        var privateBackup:ByteArray?=null
        try {withTimeout(300_000) {
            need(BuildConfig.DEBUG&&Build.HARDWARE in setOf("ranchu","goldfish"),"DISPOSABLE_ONLY")
            need(!report.exists()&&input.isFile,"CLEAN_INPUT_REQUIRED")
            val j=AcademicRecords.json.parseToJsonElement(input.readText()).jsonObject
            need(j["mode"]==JsonPrimitive(mode)&&j["consentE"]==JsonPrimitive("YES")&&j["consentReconciliation"]==JsonPrimitive("KEEP_REMOTE"),"CONSENT_REQUIRED")
            need(mode!="HOSTED"||args.getString("h12Authorized")=="YES","AUTHORIZATION_REQUIRED")
            val expected=j.getValue("expectedId").jsonPrimitive.content
            val fixture=j.getValue("fixtureClassId").jsonPrimitive.content
            val dbDir=app.getDatabasePath("diario.db").parentFile!!
            need(!dbDir.exists()||dbDir.walkTopDown().none{it.isFile},"NOT_CLEAN")
            need(KeystoreAuthVault(app).read().let{it.session==null&&it.legacyOwner==null},"NOT_CLEAN")
            flag("clean_install_verified")
            at("AUTH") {
                app.auth.restore();app.auth.login(j.getValue("email").jsonPrimitive.content,j.getValue("password").jsonPrimitive.content)
                need(app.auth.state.value.user?.let{it.id==expected&&it.confirmed&&it.id!=AuthConfig.LEGACY_OWNER_ID}==true,"WRONG_IDENTITY")
                flag("authenticated")
            }
            val user=app.auth.state.value.user!!
            var workspace=app.workspaces.activate(user);workspace.academicCoordinator.close()
            val token=app.auth.syncToken(user.id)
            val guarded=H12ReconciliationRemote(app.readonly,if(mode=="HOSTED")SupabaseAcademicSync()else PhaseBNetworkHarness.raw())
            fun store()=AcademicStore(workspace.database,user.id)
            fun admitted()=app.auth.canUse(user.id)&&app.workspaces.current()===workspace
            fun engine()=AcademicEngine(store(),guarded,{app.auth.syncToken(user.id)},{admitted()})
            suspend fun rows()=AcademicRecords.rows(ClassLifecycle.snapshot(workspace.repository.readSnapshot()).canonical())
            var after:H05Inventory?=null
            var decisionId=""
            workspace.syncMutex.withLock {
                val baseline=at("BASELINE"){recoveryInventory(app.readonly,token)}
                need(baseline.rows().any{it.kind=="classes"&&it.key["id"]==JsonPrimitive(fixture)},"FIXTURE_MISSING")
                at("RECOVERY") {
                    engine().activate();engine().run()
                    need(rows().associateBy{it.identity}==baseline.rows().associateBy{it.identity},"RECOVERY_MISMATCH")
                    need(store().dao.pending().isEmpty()&&!store().blocked(),"RECOVERY_PENDING")
                }
                val password="Fictitious-H12-Backup-2026".toCharArray()
                var oldRows:List<AcademicRow> = emptyList()
                privateBackup=at("BACKUP") {
                    if(mode=="LOCAL") {
                        oldRows=rows()
                        AccountBackup.seal(workspace.backup.backupBytes(),user.id,password)
                    }else {
                        // Reconstruct an earlier *committed* historical graph, never fabricate cloud values.
                        // No new remote changes are made merely to manufacture an old backup.
                        val candidates=linkedMapOf<AcademicKey,AcademicVersion>();var old:List<AcademicRow>?=null
                        for(page in baseline.pages.dropLast(1)) {
                            page.records.forEach{candidates[it.identity]=it}
                            val candidate=H05Inventory(candidates.toMap(),emptyList(),page.sequence).rows()
                            if(runCatching{AcademicRecords.snapshot(candidate)}.isSuccess&&
                                candidate.any{it.kind=="classes"&&it.key["id"]==JsonPrimitive(fixture)}&&
                                candidate.size<baseline.rows().size&&candidate.any{r->baseline.rows().any{it.identity==r.identity&&it.payload!=r.payload}})old=candidate
                        }
                        oldRows=old ?: error("H12_NO_SAFE_OLD_BASELINE")
                        val name="h12-historical-${newId()}.db";val db=DiaryDatabase.open(app,name)
                        try {
                            PhaseDFixture.insert(db,AcademicRecords.snapshot(oldRows))
                            val backup=BackupManager(db,FileSafetyBackupStore(File(app.cacheDir,"h12-history-safety")),BuildConfig.VERSION_CODE,null,owner=user.id)
                            AccountBackup.seal(backup.backupBytes(),user.id,password)
                        }finally{db.close();app.deleteDatabase(name)}
                    }
                }
                flag("encrypted_backup_verified",AccountBackup.protected(privateBackup!!))
                flag("historical_backup_reconstructed",mode=="HOSTED")
                result["backup_hash"]=JsonPrimitive(h05RowsHash(oldRows));number("backup_records",oldRows.size.toLong())
                at("WRONG_ACCOUNT") {
                    val before=h05RowsHash(rows())
                    val other=j.getValue("otherId").jsonPrimitive.content
                    java.util.UUID.fromString(other);need(other!=user.id,"WRONG_IDENTITY")
                    var rejected=false
                    try{AccountBackup.open(privateBackup!!,other,password)}catch(_:AuthFailure){rejected=true}
                    need(rejected&&before==h05RowsHash(rows()),"WRONG_ACCOUNT_ACCEPTED");flag("wrong_account_rejected")
                }
                if(mode=="LOCAL")at("PEER_CHANGE") {
                    need(!BuildConfig.HOMOLOGACAO,"HOSTED_MUTATION_FORBIDDEN")
                    val remote=PhaseBNetworkHarness.raw()
                    val grade=baseline.rows().first{it.kind=="grades"&&it.payload["studentId"]==JsonPrimitive("ds2")}
                    val value=JsonObject(grade.payload+mapOf("value" to JsonPrimitive(9.0)))
                    val source=PhaseDFixture.read().students.single{it.id=="ds2"}
                    val extra=source.copy(id="h12-cloud-only",personId="h12-person",number=40)
                    val student=AcademicRecords.json.encodeToJsonElement(Student.serializer(),extra).jsonObject
                    val journal=baseline.rows().first{it.kind=="lesson_records"}
                    val changes=listOf(AcademicChange(grade.kind,grade.key,baseline.versions.getValue(grade.identity).revision,value),
                        AcademicChange("students",buildJsonObject{put("id",extra.id)},0,student),
                        AcademicChange(journal.kind,journal.key,baseline.versions.getValue(journal.identity).revision,
                            JsonObject(journal.payload+mapOf("description" to JsonPrimitive("H12 conteúdo fictício posterior ao backup")))))
                    need(remote.pushE(token,AcademicBatch(newId(),changes)){admitted()}.accepted,"PEER_FAILED")
                }
                val current=at("CURRENT"){recoveryInventory(app.readonly,token)};after=current
                val oldKeys=oldRows.map{it.identity}.toSet();val remoteOnly=current.rows().filter{it.identity !in oldKeys}
                need(remoteOnly.isNotEmpty()&&h05RowsHash(oldRows)!=h05RowsHash(current.rows()),"NO_POST_BACKUP_CHANGE")
                number("remote_only_records",remoteOnly.size.toLong())
                val cursor=store().cursor()
                at("RESTORE") {
                    val raw=AccountBackup.open(privateBackup!!,user.id,password)
                    need(h05RowsHash(AcademicRecords.rows(BackupCodec.decode(raw).snapshot))==h05RowsHash(oldRows),"BACKUP_MISMATCH")
                    workspace.backup.restore(workspace.backup.prepare(raw.inputStream()),"RESTAURAR")
                    need(store().stage()=="RESTORE"&&!store().active()&&store().cursor()==cursor,"RESTORE_NOT_PAUSED")
                    engine().run();engine().run() // Neither initial wakeup nor retry may decide for the user.
                    need(store().stage()=="RESTORE"&&!store().active()&&store().cursor()==cursor&&store().dao.decisions().isEmpty(),"AUTOMATIC_RECONCILIATION")
                    need(h05RowsHash(rows())==h05RowsHash(oldRows),"BACKUP_NOT_RESTORED")
                    need(workspace.backup.hasSafetyCopy(),"SAFETY_COPY_MISSING")
                    val observed=recoveryInventory(app.readonly,token)
                    need(observed==current,"REMOTE_CHANGED");flag("restore_paused");flag("safety_copy_verified")
                }
                at("REVIEW") {
                    val review=AcademicReconciliation(store(),guarded,{app.auth.syncToken(user.id)},{admitted()})
                    val preview=review.preview()
                    need(preview.restore&&preview.head==current.head&&preview.evidence.isNotEmpty(),"REVIEW_MISSING")
                    val proposal=preview.proposal(AcademicChoice.KEEP_REMOTE)
                    need(proposal.changes.isEmpty()&&h05RowsHash(AcademicRecords.rows(proposal.candidate))==h05RowsHash(current.rows()),"ACADEMIC_WRITE_PROPOSED")
                    need(preview.evidence.any{it.local==null&&it.remote!=null}&&preview.evidence.any{it.local!=null&&it.remote!=null&&it.local!=it.remote},"REVIEW_INCOMPLETE")
                    val decision=review.confirm(preview,AcademicChoice.KEEP_REMOTE);decisionId=decision.id;guarded.authorize(decision)
                    flag("explicit_review_verified")
                }
                at("DECISION") {
                    // Lose only a disposable server's accepted response. Persisted decision must replay.
                    guarded.loseNextReply=mode=="LOCAL";engine().run()
                    if(mode=="LOCAL")need(store().dao.decisions().single().phase=="PENDING"&&store().stage()=="RESTORE"&&store().cursor()==cursor,"LOST_REPLY_NOT_PRESERVED")
                }
            }
            at("REOPEN") {
                app.workspaces.lock();workspace=app.workspaces.activate(user);workspace.academicCoordinator.close()
                workspace.syncMutex.withLock {
                    engine().run();engine().run()
                    val expectedRemote=requireNotNull(after);val actual=rows();val s=store()
                    need(h05RowsHash(actual)==h05RowsHash(expectedRemote.rows()),"DATA_MISMATCH")
                    need(actual.map{it.identity}.toSet().size==actual.size,"DUPLICATE")
                    need(s.stage()=="ACTIVE_E"&&s.cursor()==expectedRemote.head&&s.dao.control()?.checked==true&&s.dao.control()?.message=="","STATE")
                    need(s.dao.pending().isEmpty()&&s.dao.incoming().isEmpty()&&s.dao.stagedParts().isEmpty(),"QUEUE_NOT_EMPTY")
                    val decisions=s.dao.decisions();need(decisions.size==1&&decisions.single().id==decisionId&&decisions.single().phase=="APPLIED","DECISION_MISMATCH")
                    val frozen=AcademicRecords.json.decodeFromString<AcademicFrozenDecision>(decisions.single().body)
                    need(frozen.restore&&frozen.choice==AcademicChoice.KEEP_REMOTE&&frozen.changes.isEmpty()&&frozen.remote.isNotEmpty()&&frozen.localChanges.isNotEmpty(),"AUDIT_INCOMPLETE")
                    val states=s.dao.states();need(states.size==expectedRemote.versions.size,"REVISION_MISMATCH")
                    states.forEach { state ->
                        val version=expectedRemote.versions.getValue(state.identity())
                        need(state.owner==user.id&&state.revision==version.revision&&state.localVersion==state.acknowledged,"REVISION_MISMATCH")
                        need(state.base?.let{AcademicRecords.json.parseToJsonElement(it)}==PhaseBGuard.normalized(version.change()).payload,"BASE_MISMATCH")
                    }
                    expectedRemote.pages.forEach { page ->
                        val receipt=s.dao.applied(page.sequence)
                        need(receipt!=null&&receipt.owner==user.id&&receipt.operation==page.operation&&receipt.digest==BackupCodec.hash(AcademicRecords.canonical(AcademicRecords.json.encodeToJsonElement(AcademicPage.serializer(),page)).toByteArray()),"RECEIPT_MISMATCH")
                    }
                    workspace.database.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use{need(!it.moveToFirst(),"INVALID_REFERENCES")}
                    need(workspace.database.syncDao().operations().isEmpty()&&workspace.database.syncDao().states().isEmpty(),"LEGACY_QUEUE")
                    need(recoveryInventory(app.readonly,token)==expectedRemote,"REMOTE_CHANGED")
                    need(guarded.decisionWrites==1&&app.readonly.blockedWrites==0,"UNEXPECTED_WRITE")
                    need(mode!="LOCAL"||guarded.lostReplies==1,"REPLAY_NOT_TESTED")
                    result["types"]=h05TypeSummary(actual,expectedRemote.rows())
                    result["local_hash"]=JsonPrimitive(h05RowsHash(actual));result["remote_hash"]=JsonPrimitive(h05RowsHash(expectedRemote.rows()))
                    number("records",actual.size.toLong());number("cursor",s.cursor());number("receipts",expectedRemote.pages.size.toLong())
                    number("decision_writes",guarded.decisionWrites.toLong());number("lost_replies",guarded.lostReplies.toLong())
                    number("blocked_writes",app.readonly.blockedWrites.toLong());number("read_calls",app.readonly.reads.toLong());number("transport_ms",app.readonly.transportMillis)
                    flag("remote_unchanged");flag("ids_and_payloads_equal");flag("revisions_equal");flag("references_valid");flag("queues_empty");flag("audit_verified");flag("reopened_equal")
                }
            }
        }}catch(e:Exception) {
            val code=e.message.orEmpty()
            failure=if(code.matches(Regex("(H12|RECOVERY)_[A-Z_]+")))code else if(e is TimeoutCancellationException)"H12_TIMEOUT"else"H12_UNEXPECTED"
        }finally {
            privateBackup?.fill(0)
            try{app.workspaces.lock();app.auth.logout();input.delete();check(!input.exists());flag("private_input_removed")}
            catch(_:Exception){failure="H12_CLEANUP_FAILED"}
            result["case"]=JsonPrimitive("H12");result["mode"]=JsonPrimitive(mode);result["stage"]=JsonPrimitive(if(failure==null)"DONE"else stage)
            result["status"]=JsonPrimitive(if(failure==null)"PASSED"else"FAILED");result["failure_code"]=failure?.let{JsonPrimitive(it)}?:JsonNull
            number("total_ms",SystemClock.elapsedRealtime()-start);result["timings_ms"]=JsonObject(timings)
            report.writeText(JsonObject(result).toString())
        }
        if(failure!=null)throw AssertionError(failure) // Fixed codes only; never attach private cause/response.
    }
}
