package br.com.diariodeclasse.sync.academic

import br.com.diariodeclasse.backup.BackupSnapshot
import br.com.diariodeclasse.sync.SyncFailure
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable enum class AcademicChoice { KEEP_LOCAL, KEEP_REMOTE }
@Serializable data class AcademicEvidence(val kind:String,val key:JsonObject,val baseRevision:Long,
    val revision:Long,val base:JsonObject?,val local:JsonObject?,val remote:JsonObject?) {
    val identity get()=AcademicKey(kind,key)
    val fields get()=(local.orEmpty().keys+remote.orEmpty().keys).filter{local?.get(it)!=remote?.get(it)}.sorted()
}
data class AcademicResolutionProposal(val candidate:BackupSnapshot,val changes:List<AcademicChange>,val evidence:List<AcademicEvidence>)

/** One explicit choice for a consistent set, never a last-writer-wins policy. No I/O. */
object AcademicResolution {
    fun evidence(base:List<AcademicRow>,local:List<AcademicRow>,remote:List<AcademicRow>,
        known:Map<AcademicKey,Long>,latest:Map<AcademicKey,Long>,restore:Boolean,group:Set<AcademicKey> = emptySet()):List<AcademicEvidence> {
        val b=base.associateBy{it.identity};val l=local.associateBy{it.identity};val r=remote.associateBy{it.identity}
        val keys=if(restore)(l.keys+r.keys).filter{l[it]?.payload!=r[it]?.payload}.toSet()
            else {
                val delta=AcademicRecords.delta(base,remote,known)
                AcademicMerge.review(base,local,delta,known).conflicts.map{it.identity}.toSet()
            }
        val expanded=if(!restore&&keys.isNotEmpty())keys+group.filter{l[it]?.payload!=r[it]?.payload}else keys
        return expanded.sortedWith(compareBy({it.kind},{AcademicRecords.canonical(it.key)})).map{key->
            AcademicEvidence(key.kind,key.key,known[key] ?: 0,latest[key] ?: 0,b[key]?.payload,l[key]?.payload,r[key]?.payload)
        }
    }
    fun propose(base:List<AcademicRow>,local:List<AcademicRow>,remote:List<AcademicRow>,
        known:Map<AcademicKey,Long>,latest:Map<AcademicKey,Long>,restore:Boolean,choice:AcademicChoice,group:Set<AcademicKey> = emptySet()):AcademicResolutionProposal {
        val evidence=evidence(base,local,remote,known,latest,restore,group)
        val candidate=if(restore) {
            val proposed=remote.associateBy{it.identity}.toMutableMap()
            // Backup absence is not a deletion. Remote-only rows are always retained.
            for(row in local)if(choice==AcademicChoice.KEEP_LOCAL || row.identity !in latest)proposed[row.identity]=row
            AcademicRecords.snapshot(proposed.values.toList())
        }else {
            val keys=evidence.map{it.identity}.toSet();val delta=AcademicRecords.delta(base,remote,known)
            val adjusted=local.associateBy{it.identity}.toMutableMap()
            if(choice==AcademicChoice.KEEP_REMOTE) {
                val r=remote.associateBy{it.identity}
                for(key in keys){val row=r[key];if(row==null)adjusted.remove(key)else adjusted[key]=row}
            }
            val selected=if(choice==AcademicChoice.KEEP_LOCAL)delta.filter{it.identity !in keys}else delta
            AcademicMerge.review(base,adjusted.values.toList(),selected,known).candidate ?: throw SyncFailure("GRAPH")
        }
        val before=AcademicRecords.snapshot(local)
        val localChanges=AcademicRecords.delta(local,AcademicRecords.rows(candidate),local.associate{it.identity to (known[it.identity]?.takeIf{n->n>0} ?: 1L)})
        PhaseBGuard.validate(before,candidate,localChanges,phaseDKinds)
        // Local backup/edit may predate an immutable remote audit or timetable revision.
        // Explicit choice never authorizes removing already confirmed remote history.
        PhaseDGuard.immutable(AcademicRecords.snapshot(remote),candidate)
        val changes=AcademicRecords.delta(remote,AcademicRecords.rows(candidate),latest)
        require(changes.size<=AcademicPaging.MAX_RECORDS)
        return AcademicResolutionProposal(candidate,changes,evidence)
    }
}
