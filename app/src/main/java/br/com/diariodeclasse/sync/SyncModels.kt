package br.com.diariodeclasse.sync

import br.com.diariodeclasse.backup.BackupCodec
import br.com.diariodeclasse.backup.BackupSnapshot
import br.com.diariodeclasse.data.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.time.LocalDate

/** First wire aggregate: class settings and the four terms. No student data. */
@Serializable data class ClassBundle(val school: SchoolClass, val terms: List<Term>) {
    fun validate() {
        require(school.id.length in 1..128)
        BackupCodec.validate(BackupSnapshot(listOf(school),emptyList(),terms,emptyList(),emptyList(),emptyList(),emptyList(),emptyList()))
        require(terms.size==4 && terms.map{it.number}.toSet()==setOf(1,2,3,4))
        terms.sortedBy{it.number}.forEach {
            require(it.classId==school.id && LocalDate.parse(it.start).year==school.year && LocalDate.parse(it.end).year==school.year)
        }
        terms.sortedBy{it.number}.zipWithNext().forEach{(a,b)->require(a.end<b.start)}
    }
    fun encode():String { validate(); return wire.encodeToString(copy(terms=terms.sortedBy{it.number})) }
    companion object {
        val wire=Json { encodeDefaults=true }
        fun decode(raw:String):ClassBundle=wire.decodeFromString<ClassBundle>(raw).also{it.validate()}
    }
}
@Serializable data class RemoteClass(val id:String, val revision:Long, val sequence:Long, val deleted:Boolean, val payload:String) {
    fun validate() {
        require(id.length in 1..128 && revision>0 && sequence>0)
        if(!deleted)require(ClassBundle.decode(payload).school.id==id)
    }
}
@Serializable data class PushReply(val operationId:String, val accepted:Boolean, val record:RemoteClass)
interface ClassSyncRemote {
    suspend fun push(token:String, operation:SyncOperation):PushReply
    suspend fun pull(token:String, after:Long):List<RemoteClass>
}
class SyncFailure(val code:String):Exception(code) // Only fixed non-personal codes; never raw backend messages.
