package br.com.diariodeclasse.sync

import androidx.room.withTransaction
import br.com.diariodeclasse.data.*
import kotlinx.serialization.encodeToString

/** Metadata lives in the very same account-specific Room transaction as academic writes. */
class ClassSyncStore(val db:DiaryDatabase, val owner:String, private val now:()->Long=System::currentTimeMillis) {
    private val dao=db.syncDao()
    private suspend fun control():SyncControl {
        installSyncTriggers(db.openHelper.writableDatabase)
        val value=dao.control() ?: SyncControl(owner=owner).also{dao.save(it)}
        check(value.owner==owner){"Conta incompatível com a fila local."}
        return value
    }
    suspend fun enable()=db.withTransaction {
        val before=control();check(before.message!="RESTORE"){"Confira a restauração antes de retomar."}
        dao.save(before.copy(enabled=true,message="",checked=false))
    }
    suspend fun pause()=db.withTransaction {dao.save(control().copy(enabled=false))}
    /** Explicit per-class consent. Existing historical classes never enter the queue implicitly. */
    suspend fun enroll(id:String)=db.withTransaction {
        check(control().enabled)
        requireNotNull(bundle(id)).validate()
        if(dao.state(id)==null)dao.save(SyncClassState(id,owner))
    }
    private suspend fun bundle(id:String):ClassBundle?=db.dao().schoolClass(id)?.let{ClassBundle(it,db.dao().periods(id))}
    suspend fun prepare():List<SyncOperation> = db.withTransaction {
        if(!control().enabled)return@withTransaction emptyList()
        val existing=dao.operations().map{it.classId}.toSet()
        for(state in dao.states()) {
            check(state.owner==owner)
            if(state.conflict!=null || state.classId in existing || state.localVersion<=state.acknowledged)continue
            val value=bundle(state.classId)
            dao.insert(SyncOperation(newId(),state.classId,owner,state.localVersion,state.remoteRevision,
                if(value==null)"DELETE"else"UPSERT",value?.encode().orEmpty(),now()))
        }
        dao.operations().filter{dao.state(it.classId)?.conflict==null}.also { rows->check(rows.all{it.owner==owner}) }
    }
    suspend fun attempt(operation:SyncOperation)=db.withTransaction {control();dao.attempt(operation.id)}
    suspend fun accept(operation:SyncOperation,reply:PushReply)=db.withTransaction {
        if(!control().enabled)return@withTransaction
        reply.record.validate()
        check(reply.operationId==operation.id && reply.record.id==operation.classId)
        val current=dao.operations().firstOrNull{it.id==operation.id} ?: return@withTransaction
        check(current.copy(attempts=operation.attempts)==operation)
        val state=requireNotNull(dao.state(operation.classId))
        if(!reply.accepted) {
            dao.save(state.copy(conflict=ClassBundle.wire.encodeToString(reply.record)))
        } else {
            check(reply.record.revision==operation.baseRevision+1)
            check(reply.record.deleted==(operation.kind=="DELETE"))
            if(!reply.record.deleted)check(ClassBundle.decode(reply.record.payload)==ClassBundle.decode(operation.payload))
            dao.save(state.copy(remoteRevision=reply.record.revision,acknowledged=operation.localVersion,basePayload=reply.record.payload))
            dao.acknowledge(operation.id) // Never acknowledge the newer dirty counter.
        }
    }
    /** A fetched page is committed atomically, cursor included. A conflict preserves both payloads. */
    suspend fun receive(records:List<RemoteClass>)=db.withTransaction {
        val control=control();if(!control.enabled)return@withTransaction
        require(records.size<=100 && records.map{it.sequence}==records.map{it.sequence}.sorted().distinct())
        var cursor=control.cursor
        for(remote in records) {
            remote.validate();require(remote.sequence>cursor)
            val state=dao.state(remote.id)
            if(state!=null)check(state.owner==owner)
            if(state==null || remote.revision>state.remoteRevision) {
                val local=bundle(remote.id)
                val hasHistory=protected(remote.id)
                val dirty=state!=null && (state.localVersion>state.acknowledged || dao.operations().any{it.classId==remote.id})
                if(dirty || (local!=null && state==null) || hasHistory || state?.conflict!=null) {
                    dao.save((state ?: SyncClassState(remote.id,owner,localVersion=0)).copy(conflict=ClassBundle.wire.encodeToString(remote)))
                } else {
                    // Suppression flag and changes share one SQLite transaction; a crash rolls both back.
                    dao.save(control.copy(applying=true))
                    if(!remote.deleted) {
                        val value=ClassBundle.decode(remote.payload)
                        db.dao().save(value.school);value.terms.forEach{db.dao().save(it)}
                    } else if(local!=null) {
                        db.openHelper.writableDatabase.execSQL("DELETE FROM terms WHERE classId=?",arrayOf(remote.id))
                        db.openHelper.writableDatabase.execSQL("DELETE FROM classes WHERE id=?",arrayOf(remote.id))
                    }
                    dao.save(SyncClassState(remote.id,owner,localVersion=0,acknowledged=0,remoteRevision=remote.revision,basePayload=remote.payload))
                    dao.save(control.copy(applying=false))
                }
            }
            cursor=remote.sequence
        }
        dao.save(control.copy(cursor=cursor,applying=false))
    }
    private suspend fun protected(id:String):Boolean {
        val data=DiaryRepository(db).readSnapshot()
        return data.students.any{it.classId==id} || data.lessons.any{it.classId==id} || data.activities.any{it.classId==id} ||
            data.drafts.any{it.classId==id} || data.classArchives.any{it.classId==id} || data.lessonRecords.any{it.classId==id} ||
            data.exportRows.any{it.classId==id} || data.histories.any{it.classId==id} || data.timetables.any{t->t.slots.any{it.classId==id}}
    }
    suspend fun cursor():Long=db.withTransaction{control().cursor}
    suspend fun enabled():Boolean=db.withTransaction{control().enabled}
    suspend fun message(code:String,checked:Boolean=false)=db.withTransaction {dao.save(control().copy(message=code,checked=checked))}
}
