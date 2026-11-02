package br.com.diariodeclasse
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.sync.academic.*
import kotlinx.serialization.json.*
import org.junit.Test
import org.junit.Assert.*
class PhaseCStatusTest {
    @Test fun pendingCGradesAndConflictsCannotClaimSynced() {
        val c=AcademicControl(owner="fixture",stage="ACTIVE_C",enabled=true,checked=true,message="")
        assertEquals("Sincronizado",academicLabel(c,emptyList(),emptyList(),emptyList()))
        assertEquals("Pendente de envio",academicLabel(c,listOf(AcademicState("grades","a","s","fixture")),emptyList(),emptyList()))
        assertEquals("Conflito preservado",academicLabel(c,emptyList(),listOf(AcademicPending("op","fixture","{}","[]",0,conflict="preserved")),emptyList()))
        assertEquals("Transição pendente",academicLabel(c.copy(stage="PREPARED_C",enabled=false),emptyList(),emptyList(),emptyList()))
        assertEquals("Conflito preservado",academicLabel(c.copy(stage="PREPARED_C",enabled=false,message="CONFLICT"),emptyList(),emptyList(),emptyList()))
        assertEquals("Pausado após restauração",academicLabel(c.copy(stage="RESTORE",enabled=false),emptyList(),emptyList(),emptyList()))
    }
    @Test fun typedCompositeKeysAndNullGradeRemainDistinctFromZeroAndAbsent() {
        val zero=Grade("a","s",0.0);val pending=Grade("a","s",null)
        for(value in listOf(zero,pending)) {
            val payload=AcademicRecords.json.encodeToJsonElement(Grade.serializer(),value).jsonObject
            val key=AcademicKind.GRADES.key(payload)
            PhaseBGuard.payload(AcademicChange("grades",key.key,0,payload))
            assertEquals(key,AcademicState("grades","a","s","fixture").identity())
        }
        val p=AcademicRecords.json.encodeToJsonElement(Grade.serializer(),pending).jsonObject
        try{PhaseBGuard.payload(AcademicChange("grades",AcademicKind.GRADES.key(p).key,0,JsonObject(p-"value")));fail()}catch(_:IllegalArgumentException){}
        assertNotEquals(AcademicRecords.json.encodeToJsonElement(Grade.serializer(),zero),p)
        assertEquals(buildJsonObject{put("studentId","s");put("term",3)},AcademicState("recompositions","s","3","fixture").identity().key)
    }
}
