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
val phaseCKinds=phaseBKinds+setOf("activities","grades","lessons","marks","recompositions")
val phaseDKinds=AcademicKind.entries.map{it.wire}.toSet()
private fun AcademicKey.parts():Pair<String,String> = when(kind) {
    "terms" -> key.getValue("classId").jsonPrimitive.content to key.getValue("number").jsonPrimitive.int.toString()
    "grades" -> key.getValue("activityId").jsonPrimitive.content to key.getValue("studentId").jsonPrimitive.content
    "marks" -> key.getValue("lessonId").jsonPrimitive.content to key.getValue("studentId").jsonPrimitive.content
    "recompositions" -> key.getValue("studentId").jsonPrimitive.content to key.getValue("term").jsonPrimitive.int.toString()
    "relocations" -> key.getValue("sourceStudentId").jsonPrimitive.content to ""
    "class_archives","preserved_histories" -> key.getValue("classId").jsonPrimitive.content to ""
    "classes","students","activities","lessons","lesson_records","weekly_timetables","export_rows","import_drafts" -> key.getValue("id").jsonPrimitive.content to ""
    else -> throw SyncFailure("UNSUPPORTED")
}
fun AcademicState.identity():AcademicKey = AcademicKey(kind,buildJsonObject {
    when(kind){
        "terms"->{put("classId",key1);put("number",key2.toInt())}
        "grades"->{put("activityId",key1);put("studentId",key2)}
        "marks"->{put("lessonId",key1);put("studentId",key2)}
        "recompositions"->{put("studentId",key1);put("term",key2.toInt())}
        "relocations"->put("sourceStudentId",key1)
        "class_archives","preserved_histories"->put("classId",key1)
        else->put("id",key1)
    }
})
@Serializable private data class SentVersion(val kind:String,val key1:String,val key2:String,val version:Long)

/** Domain transition guards supplement (not replace) the existing full snapshot validator. */
object PhaseBGuard {
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    fun payload(change:AcademicChange) {
        val value=change.payload ?: return
        validateValue(value,serializer(change.kind).descriptor)
    }
    /** Every nested persisted field is required, even when Kotlin has a convenient default. */
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private fun validateValue(value:JsonElement,d:kotlinx.serialization.descriptors.SerialDescriptor) {
        if(value==JsonNull){require(d.isNullable);return}
        when(d.kind) {
            kotlinx.serialization.descriptors.StructureKind.CLASS -> {
                require(value is JsonObject)
                require(value.keys==(0 until d.elementsCount).map{d.getElementName(it)}.toSet())
                for(i in 0 until d.elementsCount)validateValue(value.getValue(d.getElementName(i)),d.getElementDescriptor(i))
            }
            kotlinx.serialization.descriptors.StructureKind.LIST -> {
                require(value is JsonArray);value.forEach{validateValue(it,d.getElementDescriptor(0))}
            }
            else -> {
                require(value is JsonPrimitive)
                require(when(d.kind){
                    kotlinx.serialization.descriptors.PrimitiveKind.STRING->value.isString
                    kotlinx.serialization.descriptors.PrimitiveKind.INT->!value.isString&&value.intOrNull!=null
                    kotlinx.serialization.descriptors.PrimitiveKind.DOUBLE->!value.isString&&value.doubleOrNull?.isFinite()==true
                    kotlinx.serialization.descriptors.PrimitiveKind.BOOLEAN->!value.isString&&value.booleanOrNull!=null
                    else->false
                })
            }
        }
    }
    private fun serializer(kind:String):kotlinx.serialization.KSerializer<out Any> = when(kind) {
        "classes"->SchoolClass.serializer();"terms"->Term.serializer();"students"->Student.serializer()
        "activities"->Activity.serializer();"grades"->Grade.serializer();"lessons"->Lesson.serializer()
        "marks"->Attendance.serializer();"recompositions"->Recomposition.serializer()
        "lesson_records"->LessonRecord.serializer();"relocations"->Relocation.serializer()
        "weekly_timetables"->WeeklyTimetable.serializer();"class_archives"->ClassArchive.serializer()
        "preserved_histories"->PreservedHistory.serializer();"export_rows"->ExportRow.serializer()
        "import_drafts"->ImportDraft.serializer();else->throw SyncFailure("UNSUPPORTED")
    }
    /** PostgreSQL JSONB may render 1.0E-4 as 0.00010. Compare the actual Room types,
     * while retaining the original wire page for receipts/inbox. Strict shape comes first. */
    fun normalized(change:AcademicChange):AcademicChange {
        payload(change)
        val value=change.payload ?: return change
        fun <T> typed(serializer:kotlinx.serialization.KSerializer<T>)=
            AcademicRecords.json.encodeToJsonElement(serializer,AcademicRecords.json.decodeFromJsonElement(serializer,value)).jsonObject
        val result=typed(serializer(change.kind))
        return change.copy(payload=result).also{it.validate()}
    }
    fun validate(before:BackupSnapshot,after:BackupSnapshot,changes:List<AcademicChange>,allowed:Set<String> = phaseBKinds) {
        BackupCodec.validate(after)
        val changedClasses=changes.filter{it.kind=="classes"||it.kind=="terms"}.map{
            it.key.getValue(if(it.kind=="classes")"id"else"classId").jsonPrimitive.content}.toSet()
        after.classes.filter{it.id in changedClasses}.forEach{ClassBundle(it,after.terms.filter{t->t.classId==it.id}).validate()}
        val oldClasses=before.classes.associateBy{it.id};val oldStudents=before.students.associateBy{it.id}
        if(allowed==phaseDKinds)PhaseDGuard.validate(before,after)
        changes.forEach { change ->
            require(change.kind in allowed)
            payload(change)
            when(change.kind) {
                "classes" -> after.classes.firstOrNull{it.id==change.key.getValue("id").jsonPrimitive.content}?.let { c ->
                    require(oldClasses[c.id]?.year?.let{it==c.year}!=false)
                    if(c.id !in oldClasses)require(before.histories.none{it.classId==c.id} && before.timetables.none{r->r.slots.any{it.classId==c.id}})
                    ClassBundle(c,after.terms.filter{it.classId==c.id}).validate()
                }
                "activities" -> after.activities.firstOrNull{it.id==change.key.getValue("id").jsonPrimitive.content}?.let{a->
                    require(before.activities.firstOrNull{it.id==a.id}?.classId?.let{it==a.classId}!=false)
                }
                "lessons" -> after.lessons.firstOrNull{it.id==change.key.getValue("id").jsonPrimitive.content}?.let{l->
                    require(before.lessons.firstOrNull{it.id==l.id}?.classId?.let{it==l.classId}!=false)
                }
                "students" -> after.students.firstOrNull{it.id==change.key.getValue("id").jsonPrimitive.content}?.let { s ->
                    val old=oldStudents[s.id]
                    require(old==null || old.personId==s.personId && old.classId==s.classId)
                    require(after.students.none{it.id!=s.id && it.classId==s.classId && it.number==s.number})
                    require(s.status=="Ativo" || s.left!=null)
                    if(old==null)require(before.histories.none{h->h.content.students.any{it.id==s.id}})
                    // A moved enrollment without its chain would be an incomplete recovery in phase B.
                    if(allowed!=phaseDKinds) {
                        require(s.status!="Remanejado")
                        require(before.diarySnapshot().academic().relocations.none{it.sourceStudentId==s.id || it.targetStudentId==s.id})
                    }
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
    suspend fun active()=db.withTransaction {control()?.let{it.stage in listOf("ACTIVE","ACTIVE_C","ACTIVE_D")&&it.enabled}==true}
    suspend fun stage()=db.withTransaction {control()?.stage}
    private suspend fun snapshot()=ClassLifecycle.snapshot(DiaryRepository(db).readSnapshot())
    suspend fun scope()=db.withTransaction {when(control()?.stage?.substringAfterLast("_")){"D"->4;"C"->3;else->2}}
    private suspend fun kinds()=when(scope()){4->phaseDKinds;3->phaseCKinds;else->phaseBKinds}
    private suspend fun core(rows:List<AcademicRow>):List<AcademicRow>{val allowed=kinds();return rows.filter{it.kind in allowed}}
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
        val local=snapshot();val initialKinds=if(remote.academicScope==4)phaseDKinds else phaseBKinds
        val rows=AcademicRecords.rows(local).filter{it.kind in initialKinds}
        PhaseBGuard.validate(local,local,rows.map{AcademicChange(it.kind,it.key,0,it.payload)},initialKinds)
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
    /** Separate consent: an APK update alone never enrolls previously local evaluations. */
    suspend fun prepareC(remote:AcademicProtocolStatus)=db.withTransaction {
        require(remote.protocol==2 && remote.testAllowed && remote.academicScope in 2..3)
        val c=requireNotNull(control());check(c.stage in listOf("PREPARED","ACTIVE","PREPARED_C","ACTIVE_C"))
        check(!blocked() && dao.pending().isEmpty())
        if(!c.stage.endsWith("_C")) {
            check(dao.states().none{it.localVersion>it.acknowledged}){"Sincronize as pendências B antes de ampliar."}
        }
        // Even a retry may not silently adopt a newly changed B head.
        if(remote.academicScope==2 && c.cursor!=remote.academicSequence)throw SyncFailure("CONFLICT")
        val local=snapshot();val rows=AcademicRecords.rows(local).filter{it.kind in phaseCKinds}
        PhaseBGuard.validate(local,local,rows.map{AcademicChange(it.kind,it.key,0,it.payload)},phaseCKinds)
        require(rows.size<=256)
        dao.save(c.copy(stage="PREPARED_C",enabled=false,message="PENDING",checked=false));seed()
    }
    suspend fun activateC(remote:AcademicProtocolStatus)=db.withTransaction {
        require(remote.protocol==2 && remote.academicScope==3 && remote.testAllowed)
        val c=requireNotNull(control());check(c.stage in listOf("PREPARED_C","ACTIVE_C"))
        dao.save(c.copy(stage="ACTIVE_C",enabled=true,message="PENDING",checked=false));seed()
    }
    /** A separate durable consent; existing C counters may be dirty due to a complete movement. */
    suspend fun prepareD(remote:AcademicProtocolStatus)=db.withTransaction {
        require(remote.protocol==2 && remote.testAllowed && remote.academicScope in 3..4)
        val c=requireNotNull(control());check(c.stage in listOf("PREPARED","ACTIVE","PREPARED_C","ACTIVE_C","PREPARED_D","ACTIVE_D"))
        check(!blocked() && dao.pending().isEmpty())
        if(remote.academicScope==3 && c.cursor!=remote.academicSequence)throw SyncFailure("CONFLICT")
        val local=snapshot();val rows=AcademicRecords.rows(local)
        PhaseBGuard.validate(local,local,rows.map{AcademicChange(it.kind,it.key,0,it.payload)},phaseDKinds)
        require(rows.size<=256)
        require(AcademicRecords.json.encodeToString(rows).toByteArray().size<=1024*1024)
        dao.save(c.copy(stage="PREPARED_D",enabled=false,message="PENDING",checked=false));seed()
    }
    suspend fun activateD(remote:AcademicProtocolStatus)=db.withTransaction {
        require(remote.protocol==2 && remote.academicScope==4 && remote.testAllowed)
        val c=requireNotNull(control());check(c.stage in listOf("PREPARED_D","ACTIVE_D"))
        dao.save(c.copy(stage="ACTIVE_D",enabled=true,message="PENDING",checked=false));seed()
    }
    suspend fun pause()=db.withTransaction {control()?.let{dao.save(it.copy(enabled=false,message="PAUSED"))}}
    suspend fun resume()=db.withTransaction {val c=requireNotNull(control());check(c.stage in listOf("ACTIVE","ACTIVE_C","ACTIVE_D"));dao.save(c.copy(enabled=true,message="PENDING"))}
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
        PhaseBGuard.validate(current,current,changes,kinds())
        if(scope()==4) {
            // Bases are the last acknowledged records, not a replacement database. They may
            // omit local changes/new rows, so only compare immutable parts against current.
            val acknowledged=AcademicRecords.snapshot(base)
            PhaseDGuard.immutable(acknowledged,current)
        }
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
            if(!c.enabled || c.stage !in listOf("ACTIVE","ACTIVE_C","ACTIVE_D"))return@withTransaction false
            val digest=BackupCodec.hash(AcademicRecords.canonical(AcademicRecords.json.encodeToJsonElement(page)).toByteArray())
            if(page.sequence<=c.cursor) {
                val receipt=requireNotNull(dao.applied(page.sequence))
                require(receipt.owner==owner && receipt.operation==page.operation && receipt.digest==digest)
                return@withTransaction true
            }
            if(blocked())return@withTransaction false
            require(page.sequence==c.cursor+1)
            val allowed=kinds()
            if(page.records.any{it.kind !in allowed})throw SyncFailure("UNSUPPORTED")
            val records=page.records.map{it.copy(payload=PhaseBGuard.normalized(it.change()).payload)}
            val before=snapshot();val local=AcademicRecords.rows(before)
            val states=dao.states();check(states.all{it.owner==owner})
            val pending=dao.pending().singleOrNull()
            if(pending?.id==page.operation) {
                val batch=AcademicRecords.json.decodeFromString<AcademicBatch>(pending.body)
                require(records.size==batch.changes.size && records.all{r->batch.changes.any{it.identity==r.identity&&it.base+1==r.revision&&it.payload==r.payload}})
                accept(pending,AcademicPushReply(page.operation,true,page.sequence,JsonArray(emptyList())))
            }
            val fresh=dao.states()
            val changes=records.mapNotNull {r->
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
                PhaseBGuard.validate(before,candidate,changes,kinds())
                dao.save(c.copy(applying=true))
                apply(candidate,changes)
                db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use{check(!it.moveToFirst())}
                check(snapshot().canonical()==candidate.canonical())
                val candidateRows=core(AcademicRecords.rows(candidate)).associateBy{it.identity}
                changes.forEach {r->
                    val s=state(r.identity)
                    val candidateRow=candidateRows[r.identity]
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
                if(c.stage !in listOf("ACTIVE","ACTIVE_C","ACTIVE_D"))return@withTransaction
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
        // Park only rows whose positions change. Unchanged rows and IDs remain untouched.
        // Negative positions are private transaction-local values; the final graph is validated
        // before/after and no cursor, observer or outbox sees this intermediate state.
        val existing= snapshot().exportRows.associateBy{it.id}
        val exportKeys=changes.filter{it.kind=="export_rows"&&it.payload!=null}.map{it.key.getValue("id").jsonPrimitive.content}.toSet()
        candidate.exportRows.filter{it.id in exportKeys && existing[it.id]?.position?.let{old->old!=it.position}==true}
            .forEachIndexed{index,r->sql.execSQL("UPDATE export_rows SET position=? WHERE id=?",arrayOf(-index-1,r.id))}
        val deletes=listOf("export_rows","relocations","grades","marks","recompositions","lesson_records","import_drafts",
            "class_archives","activities","lessons","students","terms","classes","preserved_histories","weekly_timetables")
        for(kind in deletes)changes.filter{it.kind==kind&&it.payload==null}.forEach{r->
            val (a,b)=r.identity.parts()
            val columns=when(kind){"terms"->"classId=? AND number=?";"grades"->"activityId=? AND studentId=?";
                "marks"->"lessonId=? AND studentId=?";"recompositions"->"studentId=? AND term=?";
                "relocations"->"sourceStudentId=?";"class_archives","preserved_histories"->"classId=?";else->"id=?"}
            sql.execSQL("DELETE FROM $kind WHERE $columns",if(kind in setOf("terms","grades","marks","recompositions"))arrayOf(a,b)else arrayOf(a))
        }
        fun <T> selected(rows:List<T>,serializer:kotlinx.serialization.KSerializer<T>,kind:AcademicKind)=
            rows.filter{kind.key(AcademicRecords.json.encodeToJsonElement(serializer,it).jsonObject) in keys}
        selected(candidate.classes,SchoolClass.serializer(),AcademicKind.CLASSES).forEach{db.dao().save(it)}
        selected(candidate.terms,Term.serializer(),AcademicKind.TERMS).forEach{db.dao().save(it)}
        selected(candidate.students,Student.serializer(),AcademicKind.STUDENTS).forEach{db.dao().save(it)}
        selected(candidate.lessons,Lesson.serializer(),AcademicKind.LESSONS).forEach{db.dao().save(it)}
        selected(candidate.activities,Activity.serializer(),AcademicKind.ACTIVITIES).forEach{db.dao().save(it)}
        selected(candidate.marks,Attendance.serializer(),AcademicKind.MARKS).forEach{db.dao().save(it)}
        selected(candidate.grades,Grade.serializer(),AcademicKind.GRADES).forEach{db.dao().save(it)}
        selected(candidate.recompositions,Recomposition.serializer(),AcademicKind.RECOMPOSITIONS).forEach{db.dao().save(it)}
        selected(candidate.lessonRecords,LessonRecord.serializer(),AcademicKind.JOURNAL).forEach{db.dao().save(it)}
        selected(candidate.relocations,Relocation.serializer(),AcademicKind.RELOCATIONS).forEach{db.dao().save(it)}
        selected(candidate.timetables,WeeklyTimetable.serializer(),AcademicKind.TIMETABLES).forEach{db.dao().save(it)}
        selected(candidate.classArchives,ClassArchive.serializer(),AcademicKind.ARCHIVES).forEach{db.dao().save(it)}
        selected(candidate.drafts,ImportDraft.serializer(),AcademicKind.DRAFTS).forEach{db.dao().save(it)}
        db.dao().saveHistories(selected(candidate.histories,PreservedHistory.serializer(),AcademicKind.HISTORIES))
        selected(candidate.exportRows,ExportRow.serializer(),AcademicKind.EXPORT_ROWS).forEach{db.dao().save(it)}

    }
}
