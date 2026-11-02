package br.com.diariodeclasse
import br.com.diariodeclasse.sync.academic.*
import org.junit.Test
import org.junit.Assert.*
import br.com.diariodeclasse.data.*
import kotlinx.serialization.json.*
class PhaseBStatusTest {
    private val c=AcademicControl(owner="fixture",stage="ACTIVE",enabled=true,checked=true,message="")
    @Test fun truthfulStatesNeverClaimSyncedWithDirtyRowsOrUnresolvedInbox() {
        assertEquals("Sincronizado",academicLabel(c,emptyList(),emptyList(),emptyList()))
        assertEquals("Pendente de envio",academicLabel(c,listOf(AcademicState("classes","id",owner="fixture")),emptyList(),emptyList()))
        assertEquals("Conflito preservado",academicLabel(c,emptyList(),emptyList(),listOf(AcademicIncoming(1,"fixture","{}","GRAPH"))))
        for((code,label) in mapOf("SYNCING" to "Sincronizando","OFFLINE" to "Aguardando internet","RETRY" to "Falha temporária","CONFLICT" to "Conflito preservado"))
            assertEquals(label,academicLabel(c.copy(message=code),emptyList(),emptyList(),emptyList()))
    }
    @Test fun missingPersonIdentityOrWrongPrimitiveTypeCannotFallBackToGeneratedDefaults() {
        val s=Student("s","p","c","Fictício",1,"2026-01-01")
        val json=AcademicRecords.json.encodeToJsonElement(Student.serializer(),s).jsonObject
        val key=AcademicKind.STUDENTS.key(json)
        for(bad in listOf(JsonObject(json-"personId"),JsonObject(json+("number" to JsonPrimitive("1"))),JsonObject(json+("notes" to JsonNull)))) {
            try{PhaseBGuard.payload(AcademicChange("students",key.key,0,bad));fail("Invalid identity/default accepted")}catch(_:IllegalArgumentException){}
        }
        PhaseBGuard.payload(AcademicChange("students",key.key,0,json))
    }
}
