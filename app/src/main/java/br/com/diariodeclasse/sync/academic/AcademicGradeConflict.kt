package br.com.diariodeclasse.sync.academic

import kotlinx.serialization.json.*
import java.math.BigDecimal

/** A suggestion, never a resolution policy. Composite/interdependent sets use the full review. */
data class NumericGradeConflict(val student:String,val classroom:String,val activity:String,
    val local:Double,val remote:Double) {
    val higher get()=maxOf(local,remote)
    val other get()=minOf(local,remote)
    val higherChoice get()=if(local>remote)AcademicChoice.KEEP_LOCAL else AcademicChoice.KEEP_REMOTE
    val otherChoice get()=if(local>remote)AcademicChoice.KEEP_REMOTE else AcademicChoice.KEEP_LOCAL
}
fun conflictGradeNumber(value:Double):String=BigDecimal.valueOf(value).stripTrailingZeros().toPlainString().replace('.',',')

object AcademicNumericGrade {
    fun suggestion(preview:AcademicPreview):NumericGradeConflict?=runCatching {
        require(!preview.restore)
        val evidence=preview.evidence.single()
        require(evidence.kind=="grades"&&evidence.fields==listOf("value"))
        fun numeric(payload:JsonObject?):Double {
            val value=requireNotNull(payload)["value"] as? JsonPrimitive
            require(value!=null&&!value.isString&&value!=JsonNull)
            return requireNotNull(value.doubleOrNull).also{require(it.isFinite())}
        }
        val local=numeric(evidence.local);val remote=numeric(evidence.remote)
        require(local!=remote)
        val l=preview.local.associateBy{it.identity};val r=preview.remote.associateBy{it.identity}
        fun related(kind:String,id:JsonElement):JsonObject {
            val key=AcademicKey(kind,buildJsonObject{put("id",id)})
            val left=requireNotNull(l[key]).payload;val right=requireNotNull(r[key]).payload
            require(left==right) // A changed maximum/matrícula is not a simple numeric conflict.
            return left
        }
        val student=related("students",evidence.key.getValue("studentId"))
        val activity=related("activities",evidence.key.getValue("activityId"))
        require(student.getValue("classId")==activity.getValue("classId"))
        val school=related("classes",student.getValue("classId"))
        val maximum=activity.getValue("maximum").jsonPrimitive.double
        require(maximum.isFinite()&&maximum>0&&local in 0.0..maximum&&remote in 0.0..maximum)
        // Both choices must produce academically valid, immutable-history-preserving graphs.
        for(choice in AcademicChoice.entries)require(preview.proposal(choice).evidence==preview.evidence)
        NumericGradeConflict(student.getValue("name").jsonPrimitive.content,
            listOf("name","subject","year").joinToString(" • "){school.getValue(it).jsonPrimitive.content},
            activity.getValue("name").jsonPrimitive.content,local,remote)
    }.getOrNull()
}
