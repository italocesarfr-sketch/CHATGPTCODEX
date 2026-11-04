package br.com.diariodeclasse.sync.academic

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement

/** Limits apply to a complete transaction, not to the teacher's whole database. */
object AcademicPaging {
    const val MAX_RECORDS=16384
    const val MAX_BYTES=16*1024*1024
    const val PART_BYTES=768*1024
    const val PART_RECORDS=128
    fun split(values:List<JsonElement>):List<JsonArray> {
        require(values.size in 1..MAX_RECORDS)
        require(JsonArray(values).toString().toByteArray(Charsets.UTF_8).size<=MAX_BYTES)
        val parts=mutableListOf<JsonArray>();var current=mutableListOf<JsonElement>();var bytes=2
        for(value in values) {
            val size=value.toString().toByteArray(Charsets.UTF_8).size
            // A single persisted row (including an embedded history) is never cut in half.
            require(size+2<=PART_BYTES)
            if(current.size==PART_RECORDS || bytes+size+1>PART_BYTES){parts+=JsonArray(current);current=mutableListOf();bytes=2}
            current+=value;bytes+=size+1
        }
        if(current.isNotEmpty())parts+=JsonArray(current)
        return parts
    }
    fun parts(batch:AcademicBatch)=split(batch.changes.map{AcademicRecords.json.encodeToJsonElement(it)})
}

@Serializable data class AcademicFragment(val operation:String,val sequence:Long,val total:Int,
    val offset:Int,val records:List<AcademicVersion>) {
    fun validate() {
        require(java.util.UUID.fromString(operation).toString()==operation && sequence>0)
        require(total in 1..AcademicPaging.MAX_RECORDS && offset in 0 until total)
        require(records.size in 1..AcademicPaging.PART_RECORDS && offset+records.size<=total)
        require(records.map{it.identity}.distinct().size==records.size)
        records.forEach{it.change()}
        require(AcademicRecords.json.encodeToJsonElement(this).toString().toByteArray(Charsets.UTF_8).size<=AcademicPaging.PART_BYTES+4096)
    }
}
@Serializable data class AcademicManifestAck(val operation:String,val total:Int,val parts:Int)
@Serializable data class AcademicPartAck(val operation:String,val part:Int)
