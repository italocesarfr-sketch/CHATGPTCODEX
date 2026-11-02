package br.com.diariodeclasse.sync.academic

import androidx.room.withTransaction
import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.sync.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

val phaseBKinds=setOf("classes","terms","students")
private fun AcademicKey.parts():Pair<String,String> = when(kind) {
    "terms" -> key.getValue("classId").jsonPrimitive.content to key.getValue("number").jsonPrimitive.int.toString()
    "classes","students" -> key.getValue("id").jsonPrimitive.content to ""
    else -> throw SyncFailure("UNSUPPORTED")
}
fun AcademicState.identity():AcademicKey = AcademicKey(kind,buildJsonObject {
    if(kind=="terms"){put("classId",key1);put("number",key2.toInt())}else put("id",key1)
})
@Serializable private data class SentVersion(val kind:String,val key1:String,val key2:String,val version:Long)

/** Domain transition guards supplement (not replace) the existing full snapshot validator. */
object PhaseBGuard {
    fun validate(before:BackupSnapshot,after:BackupSnapshot,changes:List<AcademicChange>) {
        BackupCodec.validate(after)
        val changedClasses=changes.filter{it.kind=="classes"||it.kind=="terms"}.map{
            it.key.getValue(if(it.kind=="classes")"id"else"classId").jsonPrimitive.content}.toSet()
        after.classes.filter{it.id in changedClasses}.forEach{ClassBundle(it,after.terms.filter{t->t.classId==it.id}).validate()}
        val oldClasses=before.classes.associateBy{it.id};val oldStudents=before.students.associateBy{it.id}
        changes.forEach { change ->
            require(change.kind in phaseBKinds)
            when(change.kind) {
                "classes" -> after.classes.firstOrNull{it.id==change.key.getValue("id").jsonPrimitive.content}?.let { c ->
                    require(oldClasses[c.id]?.year?.let{it==c.year}!=false)
                    if(c.id !in oldClasses)require(before.histories.none{it.classId==c.id} && before.timetables.none{r->r.slots.any{it.classId==c.id}})
                    ClassBundle(c,after.terms.filter{it.classId==c.id}).validate()
                }
                "students" -> after.students.firstOrNull{it.id==change.key.getValue("id").jsonPrimitive.content}?.let { s ->
                    val old=oldStudents[s.id]
                    require(old==null || old.personId==s.personId && old.classId==s.classId)
                    require(after.students.none{it.id!=s.id && it.classId==s.classId && it.number==s.number})
                    require(s.status=="Ativo" || s.left!=null)
                    if(old==null)require(before.histories.none{h->h.content.students.any{it.id==s.id}})
                    // A moved enrollment without its chain would be an incomplete recovery in phase B.
                    require(s.status!="Remanejado")
                    require(before.diarySnapshot().academic().relocations.none{it.sourceStudentId==s.id || it.targetStudentId==s.id})
                }
            }
        }
    }
}

/** Persistent outbox and inbox in the account's own Room file; no database replacement. */
class AcademicStore(val db:DiaryDatabase,val owner:String,private val now:()->Long=System::currentTimeMillis) {
    val dao=db.academicDao()
    init {require(java.util.UUID.fromString(owner).toString()==owner)}
    private suspend fun control():AcademicControl?=dao.control()?.also{check(it.owner==owner)}
    suspend fun active()=db.withTransaction {control()?.let{it.stage=="ACTIVE"&&it.enabled}==true}
    suspend fun stage()=db.withTransaction {control()?.stage}
    private suspend fun snapshot()=ClassLifecycle.snapshot(DiaryRepository(db).readSnapshot())
    private fun core(rows:List<AcademicRow>)=rows.filter{it.kind in phaseBKinds}
    private suspend fun state(key:AcademicKey):AcademicState {
        val (a,b)=key.parts()
        return dao.states().firstOrNull{it.kind==key.kind&&it.key1==a&&it.key2==b}?.also{check(it.owner==owner)}
            ?:AcademicState(key.kind,a,b,owner,localVersion=0)
    }
    private suspend fun seed() {
        val existing=dao.states().map{it.identity()}.toSet()
        core(AcademicRecords.rows(snapshot())).filter{it.identity !in existing}.forEach { r ->
            val (a,b)=r.identity.parts();dao.save(AcademicState(r.kind,a,b,owner))
        }
    }
    /** Explicit consent is durable before the network transition. No legacy conflict is cleared. */
    suspend fun prepareConsent(remote:AcademicProtocolStatus)=db.withTransaction {
        require(remote.protocol in 1..2 && remote.v1Sequence>=0)
        check(remote.testAllowed){"Conta não autorizada para o teste acadêmico."}
        val old=control();check(old?.stage !in listOf("RESTORE","BLOCKED"))
        check(db.syncDao().states().none{it.conflict!=null} && db.syncDao().operations().isEmpty())
        val local=snapshot();val rows=core(AcademicRecords.rows(local))
        PhaseBGuard.validate(local,local,rows.map{AcademicChange(it.kind,it.key,0,it.payload)})
        require(rows.size<=256) // No partial, unvalidated initial bootstrap for larger collections.
        if(remote.protocol==1)for(value in remote.v1) {
            val record=ClassBundle.wire.decodeFromJsonElement<RemoteClass>(value)
            record.validate()
            val c=local.classes.firstOrNull{it.id==record.id}
            if(c!=null){check(!record.deleted);check(ClassBundle(c,local.terms.filter{it.classId==c.id})==ClassBundle.decode(record.payload))}
            if(record.deleted)check(local.classes.none{it.id==record.id})
        }
        if(old==null)dao.save(AcademicControl(owner=owner))
        db.syncDao().control()?.let{db.syncDao().save(it.copy(enabled=false))}
        seed()
    }
    suspend fun activate(remote:AcademicProtocolStatus)=db.withTransaction {
        check(remote.protocol==2)
        val before=requireNotNull(control());check(before.stage in listOf("PREPARED","ACTIVE"))
        dao.save(before.copy(stage="ACTIVE",enabled=true,message="PENDING",checked=false));seed()
    }
    suspend fun pause()=db.withTransaction {control()?.let{dao.save(it.copy(enabled=false,message="PAUSED"))}}
    suspend fun resume()=db.withTransaction {val c=requireNotNull(control());check(c.stage=="ACTIVE");dao.save(c.copy(enabled=true,message="PENDING"))}
    suspend fun message(value:String,checked:Boolean?=null)=db.withTransaction {
        control()?.let{dao.save(it.copy(message=value,checked=checked ?: it.checked))}
    }
    suspend fun cursor()=db.withTransaction {control()?.cursor ?: 0}
    suspend fun blocked()=db.withTransaction {dao.incoming().isNotEmpty() || dao.pending().any{it.conflict!=null}}
    suspend fun pending():AcademicPending?=db.withTransaction {dao.pending().singleOrNull()}
    suspend fun prepare():AcademicPending?=db.withTransaction {
        if(!active() || blocked())return@withTransaction null
        dao.pending().singleOrNull()?.let{return@withTransaction it}
        seed()
        val current=snapshot();val rows=core(AcademicRecords.rows(current))
        val states=dao.states().also{check(it.all{s->s.owner==owner})}
        val base=states.mapNotNull {s->s.base?.let{AcademicRow(s.kind,s.identity().key,AcademicRecords.json.parseToJsonElement(it).jsonObject)}}
        val changes=AcademicRecords.delta(base,rows,states.filter{it.revision>0}.associate{it.identity() to it.revision})
        if(changes.isEmpty()) {
            states.filter{it.localVersion>it.acknowledged}.forEach{dao.save(it.copy(acknowledged=it.localVersion))}
            return@withTransaction null
        }
        PhaseBGuard.validate(current,current,changes)
        val batch=AcademicBatch(newId(),changes);batch.validate()
        val raw=AcademicRecords.json.encodeToString(batch);require(raw.toByteArray().size<=1024*1024)
        val versions=changes.map {r->val s=state(r.identity);SentVersion(s.kind,s.key1,s.key2,s.localVersion)}
        AcademicPending(batch.operation,owner,raw,AcademicRecords.json.encodeToString(versions),now()).also{dao.insert(it)}
    }
    suspend fun attempt(pending:AcademicPending)=db.withTransaction {check(pending.owner==owner);dao.attempt(pending.id)}
    suspend fun accept(sent:AcademicPending,reply:AcademicPushReply)=db.withTransaction {
        if(!active())return@withTransaction
        val current=dao.pending().singleOrNull{it.id==sent.id} ?: return@withTransaction
        check(current.owner==owner && current.body==sent.body && current.versions==sent.versions)
        val batch=AcademicRecords.json.decodeFromString<AcademicBatch>(sent.body);batch.validate()
        require(reply.operation==sent.id)
        if(!reply.accepted) {
            require(reply.sequence==null && reply.conflicts.isNotEmpty())
            val identities=batch.changes.map{it.identity}.toSet()
            reply.conflicts.forEach {v->val o=v.jsonObject
                val key=AcademicKey(o.getValue("kind").jsonPrimitive.content,o.getValue("key").jsonObject);require(key in identities)
            }
            dao.conflict(sent.id,AcademicRecords.json.encodeToString(reply));message("CONFLICT");return@withTransaction
        }
        require(reply.sequence!=null && reply.sequence>(control()?.cursor ?: 0) && reply.conflicts.isEmpty())
        val versions=AcademicRecords.json.decodeFromString<List<SentVersion>>(sent.versions)
        require(versions.size==batch.changes.size)
        batch.changes.forEach {r->
            val s=state(r.identity);val version=versions.single{it.kind==s.kind&&it.key1==s.key1&&it.key2==s.key2}.version
            require(s.revision==r.base && version<=s.localVersion)
            dao.save(s.copy(revision=r.base+1,base=r.payload?.let{AcademicRecords.canonical(it)},acknowledged=version))
        }
        dao.acknowledge(sent.id)
    }
    /** False means the whole page was saved for review; its cursor never advanced. */
    suspend fun receive(page:AcademicPage):Boolean {
        try {page.validate();return db.withTransaction {
            val c=control() ?: return@withTransaction false
            if(!c.enabled || c.stage!="ACTIVE")return@withTransaction false
            val digest=BackupCodec.hash(AcademicRecords.canonical(AcademicRecords.json.encodeToJsonElement(page)).toByteArray())
            if(page.sequence<=c.cursor) {
                val receipt=requireNotNull(dao.applied(page.sequence))
                require(receipt.owner==owner && receipt.operation==page.operation && receipt.digest==digest)
                return@withTransaction true
            }
            if(blocked())return@withTransaction false
            require(page.sequence==c.cursor+1)
            if(page.records.any{it.kind !in phaseBKinds})throw SyncFailure("UNSUPPORTED")
            val before=snapshot();val local=AcademicRecords.rows(before)
            val states=dao.states();check(states.all{it.owner==owner})
            val pending=dao.pending().singleOrNull()
            if(pending?.id==page.operation) {
                val batch=AcademicRecords.json.decodeFromString<AcademicBatch>(pending.body)
                require(page.records.size==batch.changes.size && page.records.all{r->batch.changes.any{it.identity==r.identity&&it.base+1==r.revision&&it.payload==r.payload}})
                accept(pending,AcademicPushReply(page.operation,true,page.sequence,JsonArray(emptyList())))
            }
            val fresh=dao.states()
            val changes=page.records.mapNotNull {r->
                val s=fresh.firstOrNull{it.identity()==r.identity}
                if(s!=null && r.revision==s.revision) {
                    require(r.payload==s.base?.let{AcademicRecords.json.parseToJsonElement(it)})
                    null
                }else {require(r.revision==(s?.revision ?: 0)+1);r.change()}
            }
            if(changes.isNotEmpty()) {
                if(pending!=null && pending.id!=page.operation && changes.any{r->AcademicRecords.json.decodeFromString<AcademicBatch>(pending.body).changes.any{it.identity==r.identity}})
                    throw SyncFailure("CONFLICT")
                val base=fresh.mapNotNull{s->s.base?.let{AcademicRow(s.kind,s.identity().key,AcademicRecords.json.parseToJsonElement(it).jsonObject)}}
                val review=AcademicMerge.review(base,local,changes,fresh.filter{it.revision>0}.associate{it.identity() to it.revision})
                if(review.conflicts.isNotEmpty())throw SyncFailure("CONFLICT")
                val candidate=review.candidate ?: throw SyncFailure("GRAPH")
                PhaseBGuard.validate(before,candidate,changes)
                dao.save(c.copy(applying=true))
                apply(candidate,changes)
                db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use{check(!it.moveToFirst())}
                check(snapshot().canonical()==candidate.canonical())
                changes.forEach {r->
                    val s=state(r.identity)
                    val candidateRow=core(AcademicRecords.rows(candidate)).firstOrNull{it.identity==r.identity}
                    val converged=candidateRow?.payload==r.payload
                    dao.save(s.copy(revision=r.base+1,base=r.payload?.let{AcademicRecords.canonical(it)},
                        acknowledged=if(converged)s.localVersion else s.acknowledged))
                }
            }
            dao.insert(AcademicApplied(page.sequence,owner,page.operation,digest))
            dao.save(c.copy(cursor=page.sequence,applying=false,message="PENDING",checked=false));true
        }}catch(e:CancellationException){throw e}
        catch(e:Exception) {
            // This transaction runs only after the academic transaction rolled back completely.
            db.withTransaction {
                val c=control() ?: return@withTransaction
                if(c.stage!="ACTIVE")return@withTransaction
                val raw=AcademicRecords.json.encodeToString(page)
                val old=dao.incoming().firstOrNull{it.sequence==page.sequence}
                if(old==null)dao.insert(AcademicIncoming(page.sequence,owner,raw,(e as? SyncFailure)?.code ?: "GRAPH"))
                else check(old.owner==owner && old.body==raw)
                dao.save(c.copy(message=if(e is SyncFailure)e.code else "CONFLICT",applying=false))
            }
            return false
        }
    }
    private suspend fun apply(candidate:BackupSnapshot,changes:List<AcademicChange>) {
        val keys=changes.map{it.identity}.toSet();val sql=db.openHelper.writableDatabase
        // Upserts keep primary keys; only explicitly deleted B records are removed, children first.
        candidate.classes.filter{AcademicKind.CLASSES.key(AcademicRecords.json.encodeToJsonElement(it).jsonObject) in keys}.forEach{db.dao().save(it)}
        candidate.terms.filter{AcademicKind.TERMS.key(AcademicRecords.json.encodeToJsonElement(it).jsonObject) in keys}.forEach{db.dao().save(it)}
        candidate.students.filter{AcademicKind.STUDENTS.key(AcademicRecords.json.encodeToJsonElement(it).jsonObject) in keys}.forEach{db.dao().save(it)}
        for(kind in listOf("students","terms","classes"))changes.filter{it.kind==kind&&it.payload==null}.forEach{r->
            val (a,b)=r.identity.parts()
            if(kind=="terms")sql.execSQL("DELETE FROM terms WHERE classId=? AND number=?",arrayOf(a,b))
            else sql.execSQL("DELETE FROM $kind WHERE id=?",arrayOf(a))
        }
    }
}
