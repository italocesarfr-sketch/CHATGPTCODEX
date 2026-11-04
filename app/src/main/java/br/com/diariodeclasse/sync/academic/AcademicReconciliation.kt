package br.com.diariodeclasse.sync.academic

import androidx.room.withTransaction
import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.newId
import br.com.diariodeclasse.sync.SyncFailure
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

@Serializable data class AcademicReviewReceipt(val sequence:Long,val operation:String,val digest:String)
@Serializable data class AcademicFrozenDecision(val owner:String,val context:String,val restore:Boolean,
    val cursor:Long,val head:Long,val choice:AcademicChoice,val changes:List<AcademicChange>,
    val localChanges:List<AcademicChange>,val remote:List<AcademicVersion>,val receipts:List<AcademicReviewReceipt>,
    val request:JsonObject,val candidateHash:String,val oldPending:List<String>,val oldInbox:List<String>)
data class AcademicPreview(val context:String,val restore:Boolean,val cursor:Long,val head:Long,
    val base:List<AcademicRow>,val local:List<AcademicRow>,val remote:List<AcademicRow>,
    val known:Map<AcademicKey,Long>,val latest:Map<AcademicKey,Long>,val versions:List<AcademicVersion>,
    val receipts:List<AcademicReviewReceipt>,val evidence:List<AcademicEvidence>) {
    fun proposal(choice:AcademicChoice)=AcademicResolution.propose(base,local,remote,known,latest,restore,choice)
}

/** A review of the same versions/parts/Room transaction; no second synchronization mechanism. */
class AcademicReconciliation(private val store:AcademicStore,private val remote:AcademicRemote,
    private val session:suspend()->String,private val admitted:()->Boolean) {
    private val db get()=store.db
    private val dao get()=store.dao
    private fun digest(text:String)=BackupCodec.hash(text.toByteArray())
    private fun pageDigest(page:AcademicPage)=digest(AcademicRecords.canonical(AcademicRecords.json.encodeToJsonElement(page)))
    private suspend fun context():String=db.withTransaction {
        val c=requireNotNull(dao.control());check(c.owner==store.owner)
        val states=dao.states();val pending=dao.pending();val incoming=dao.incoming()
        check(states.all{it.owner==store.owner}&&pending.all{it.owner==store.owner}&&incoming.all{it.owner==store.owner})
        digest(buildJsonObject {
            put("snapshot",BackupCodec.fingerprint(store.snapshot()));put("cursor",c.cursor);put("stage",c.stage);put("enabled",c.enabled)
            put("states",buildJsonArray{states.forEach{s->add(buildJsonArray{add(s.kind);add(s.key1);add(s.key2);add(s.localVersion);add(s.acknowledged);add(s.revision);add(s.base ?: "")})}})
            put("pending",buildJsonArray{pending.forEach{add(buildJsonArray{add(it.id);add(it.body);add(it.versions);add(it.conflict ?: "")})}})
            put("inbox",buildJsonArray{incoming.forEach{add(buildJsonArray{add(it.sequence);add(it.body);add(it.reason)})}})
        }.toString())
    }
    private suspend fun cache(fragment:AcademicFragment)=db.withTransaction {
        fragment.validate();check(admitted())
        val raw=AcademicRecords.json.encodeToString(fragment)
        val hash=digest(AcademicRecords.canonical(AcademicRecords.json.encodeToJsonElement(fragment)))
        val old=dao.part(fragment.sequence,fragment.offset)
        if(old!=null) {
            check(old.owner==store.owner&&old.operation==fragment.operation&&old.total==fragment.total&&old.digest==hash)
            dao.retainPart(fragment.sequence,fragment.offset,raw)
        } else dao.insert(AcademicPart(fragment.sequence,fragment.offset,store.owner,fragment.operation,fragment.total,fragment.records.size,hash,raw))
    }
    /** Read-only academically. Transfer evidence can survive interruption before consent. */
    suspend fun preview():AcademicPreview {
        check(admitted());val c=requireNotNull(dao.control());check(c.owner==store.owner && c.stage in setOf("ACTIVE_E","RESTORE"))
        check(dao.decisions().none{it.phase in setOf("PENDING","CONFIRMED")})
        val token=session();check(admitted());val status=remote.status(token)
        if(status.protocol!=2||status.academicScope!=5||!status.testAllowed)throw SyncFailure("SETUP")
        val original=context();val local=AcademicRecords.rows(store.snapshot());val states=dao.states()
        val base=states.mapNotNull{s->s.base?.let{AcademicRow(s.kind,s.identity().key,AcademicRecords.json.parseToJsonElement(it).jsonObject)}}
        val restore=c.stage=="RESTORE";var after=if(restore)0 else c.cursor
        require(status.academicSequence>=after)
        val values=(if(restore)emptyList()else base).associateBy{it.identity}.toMutableMap()
        val versions=(if(restore)emptyList()else states.filter{it.revision>0}.map{AcademicVersion(it.kind,it.identity().key,it.revision,it.base?.let{b->AcademicRecords.json.parseToJsonElement(b).jsonObject})}).associateBy{it.identity}.toMutableMap()
        val receipts=mutableListOf<AcademicReviewReceipt>();var bytes=0L
        while(after<status.academicSequence) {
            require(receipts.size<256){"Review transaction limit"}
            var offset=0;val records=mutableListOf<AcademicVersion>();var operation:String?=null;var total=0
            do {
                val fragment=remote.pullE(token,after,offset).single();check(admitted());fragment.validate()
                require(fragment.sequence==after+1&&fragment.offset==offset)
                if(operation==null){operation=fragment.operation;total=fragment.total}else require(operation==fragment.operation&&total==fragment.total)
                bytes+=AcademicRecords.json.encodeToString(fragment).toByteArray().size;require(bytes<=64L*1024*1024){"Review size limit"}
                cache(fragment);records+=fragment.records;offset+=fragment.records.size
            }while(offset<total)
            val page=AcademicPage(requireNotNull(operation),after+1,records);page.validate(AcademicPaging.MAX_RECORDS)
            val hash=pageDigest(page);dao.applied(page.sequence)?.let{require(it.owner==store.owner&&it.operation==page.operation&&it.digest==hash)}
            for(v in page.records) {
                val n=PhaseBGuard.normalized(v.change());val previous=versions[v.identity]
                if(previous?.revision==v.revision)require(previous.payload==n.payload)
                else require(v.revision==(previous?.revision ?: 0)+1)
                val typed=v.copy(payload=n.payload);versions[v.identity]=typed
                if(typed.payload==null)values.remove(v.identity)else values[v.identity]=AcademicRow(v.kind,v.key,typed.payload)
            }
            receipts+=AcademicReviewReceipt(page.sequence,page.operation,hash);after=page.sequence
        }
        require(context()==original){"Local state changed during review"};check(admitted())
        if(restore)require(states.filter{it.revision>0}.all{it.identity() in versions}){"Remote history differs from acknowledged account"}
        val rows=values.values.toList();AcademicRecords.snapshot(rows)
        val known=states.filter{it.revision>0}.associate{it.identity() to it.revision};val latest=versions.mapValues{it.value.revision}
        require(known.all{(key,revision)->revision<=(latest[key] ?: 0)})
        return AcademicPreview(original,restore,c.cursor,status.academicSequence,base,local,rows,known,latest,versions.values.toList(),receipts,
            AcademicResolution.evidence(base,local,rows,known,latest,restore))
    }
    suspend fun confirm(preview:AcademicPreview,choice:AcademicChoice):AcademicDecision=db.withTransaction {
        check(admitted()&&context()==preview.context)
        check(dao.decisions().none{it.phase in setOf("PENDING","CONFIRMED")})
        val proposed=preview.proposal(choice);val id=newId()
        val request=buildJsonObject {
            put("expectedSequence",preview.head);put("choice",choice.name);put("source",if(preview.restore)"RESTORE"else"CONFLICT")
            put("localDigest",digest(AcademicRecords.json.encodeToString(preview.local)));put("hasChanges",proposed.changes.isNotEmpty())
            put("evidence",buildJsonArray{proposed.evidence.forEach{r->add(buildJsonObject{
                put("kind",r.kind);put("key",r.key);put("baseRevision",r.baseRevision);put("revision",r.revision);put("local",r.local ?: JsonNull)
            })}})
        }
        require(request.toString().toByteArray().size<=768*1024){"Decision evidence size limit"}
        val localChanges=AcademicRecords.delta(preview.local,AcademicRecords.rows(proposed.candidate),preview.local.associate{it.identity to (preview.known[it.identity]?.takeIf{r->r>0} ?: 1)})
        val frozen=AcademicFrozenDecision(store.owner,preview.context,preview.restore,preview.cursor,preview.head,choice,proposed.changes,localChanges,preview.versions,preview.receipts,request,
            BackupCodec.fingerprint(proposed.candidate),dao.pending().map{it.body},dao.incoming().map{it.body})
        val body=AcademicRecords.json.encodeToString(frozen);require(body.toByteArray().size<=AcademicPaging.MAX_BYTES)
        AcademicDecision(id,store.owner,body,System.currentTimeMillis()).also{dao.insert(it);store.message("DECISION")}
    }
    suspend fun pending()=dao.decisions().lastOrNull{it.phase in setOf("PENDING","CONFIRMED")}
    suspend fun run() {
        val control=dao.control() ?: return
        if(control.stage!="RESTORE"&&!control.enabled)return
        val decision=pending() ?: return;check(decision.owner==store.owner)
        val frozen=AcademicRecords.json.decodeFromString<AcademicFrozenDecision>(decision.body);check(frozen.owner==store.owner)
        try {
            check(admitted());val token=session();check(admitted())
            var reply=decision.response?.let{AcademicRecords.json.decodeFromString<AcademicPushReply>(it)}
            if(reply==null) {
                reply=remote.decisionE(token,decision.id)
                if(reply==null) {
                    if(context()!=frozen.context){val cancelled=remote.resolveE(token,decision.id,frozen.request,true);check(admitted());dao.decide(decision.id,"STALE",AcademicRecords.json.encodeToString(cancelled),"LOCAL_CHANGED");store.message("CONFLICT");return}
                    if(frozen.changes.isNotEmpty())remote.stageE(token,AcademicBatch(decision.id,frozen.changes))
                    check(admitted());reply=remote.resolveE(token,decision.id,frozen.request)
                }
                check(admitted());require(reply.operation==decision.id)
                dao.decide(decision.id,"CONFIRMED",AcademicRecords.json.encodeToString(reply),null)
            }
            require(reply.operation==decision.id)
            if(!reply.accepted){dao.decide(decision.id,"STALE",AcademicRecords.json.encodeToString(reply),"REMOTE_CHANGED");store.message("CONFLICT");return}
            require(reply.sequence==frozen.head+(if(frozen.changes.isEmpty())0 else 1) && reply.conflicts.isEmpty())
            check(admitted())
            db.withTransaction {
                if(context()!=frozen.context){dao.decide(decision.id,"STALE",AcademicRecords.json.encodeToString(reply),"LOCAL_CHANGED");store.message("CONFLICT");return@withTransaction}
                val c=requireNotNull(dao.control());val before=store.snapshot()
                val rows=AcademicRecords.rows(before).associateBy{it.identity}.toMutableMap()
                for(change in frozen.localChanges)if(change.payload==null)rows.remove(change.identity)else rows[change.identity]=AcademicRow(change.kind,change.key,change.payload)
                val candidate=AcademicRecords.snapshot(rows.values.toList());require(BackupCodec.fingerprint(candidate)==frozen.candidateHash)
                PhaseBGuard.validate(before,candidate,frozen.localChanges,phaseDKinds)
                // All source evidence and old queues remain in the immutable decision body.
                dao.save(c.copy(applying=true))
                store.apply(candidate,frozen.localChanges,null)
                db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use{check(!it.moveToFirst())}
                require(BackupCodec.fingerprint(store.snapshot())==frozen.candidateHash)
                val latest=frozen.remote.associateBy{it.identity}.toMutableMap()
                frozen.changes.forEach{r->latest[r.identity]=AcademicVersion(r.kind,r.key,r.base+1,r.payload)}
                for((key,v) in latest) {
                    val kind=AcademicKind.of(key.kind);val first=key.key.getValue(kind.primaryKey[0]).jsonPrimitive.content
                    val second=kind.primaryKey.getOrNull(1)?.let{key.key.getValue(it).jsonPrimitive.content}.orEmpty()
                    val old=dao.record(key.kind,first,second) ?: AcademicState(key.kind,first,second,store.owner,localVersion=0)
                    check(old.owner==store.owner)
                    dao.save(old.copy(base=v.payload?.let{AcademicRecords.canonical(it)},revision=v.revision,acknowledged=old.localVersion))
                }
                for(receipt in frozen.receipts) {
                    val old=dao.applied(receipt.sequence)
                    if(old==null)dao.insert(AcademicApplied(receipt.sequence,store.owner,receipt.operation,receipt.digest))
                    else check(old.owner==store.owner&&old.operation==receipt.operation&&old.digest==receipt.digest)
                    dao.confirmParts(receipt.sequence)
                }
                dao.pending().forEach{check(it.owner==store.owner);dao.acknowledge(it.id)};dao.resolvedInbox()
                dao.save(c.copy(stage="ACTIVE_E",enabled=true,cursor=frozen.head,applying=false,message="PENDING",checked=false))
                dao.decide(decision.id,"APPLIED",AcademicRecords.json.encodeToString(reply),null)
            }
        }catch(e:CancellationException){throw e}
        catch(e:Exception) {
            if(admitted()) {
                if((e as? SyncFailure)?.code=="CONFLICT")dao.decide(decision.id,"STALE",decision.response,"REMOTE_CHANGED")
                store.message((e as? SyncFailure)?.code ?: "RETRY")
            }
        }
    }
}
