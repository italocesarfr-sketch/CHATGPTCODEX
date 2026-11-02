package br.com.diariodeclasse
import br.com.diariodeclasse.sync.academic.*
import org.junit.Test
import org.junit.Assert.*
class PhaseBStatusTest {
    private val c=AcademicControl(owner="fixture",stage="ACTIVE",enabled=true,checked=true,message="")
    @Test fun truthfulStatesNeverClaimSyncedWithDirtyRowsOrUnresolvedInbox() {
        assertEquals("Sincronizado",academicLabel(c,emptyList(),emptyList(),emptyList()))
        assertEquals("Pendente de envio",academicLabel(c,listOf(AcademicState("classes","id",owner="fixture")),emptyList(),emptyList()))
        assertEquals("Conflito preservado",academicLabel(c,emptyList(),emptyList(),listOf(AcademicIncoming(1,"fixture","{}","GRAPH"))))
        for((code,label) in mapOf("SYNCING" to "Sincronizando","OFFLINE" to "Aguardando internet","RETRY" to "Falha temporária","CONFLICT" to "Conflito preservado"))
            assertEquals(label,academicLabel(c.copy(message=code),emptyList(),emptyList(),emptyList()))
    }
}
