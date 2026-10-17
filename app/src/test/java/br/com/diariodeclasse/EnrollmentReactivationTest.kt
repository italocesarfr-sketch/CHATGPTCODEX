package br.com.diariodeclasse

import br.com.diariodeclasse.data.*
import org.junit.Assert.*
import org.junit.Test

class EnrollmentReactivationTest {
    private val origin=SchoolClass(id="x",name="1º X Fictício",subject="História",year=2026)
    private val target=origin.copy(id="g",name="1º G Fictício")
    private val closed=Student("fictional-enrollment","fictional-person",origin.id,"Estudante Fictícia Alfa",1,"2026-01-01","2026-10-16","Transferido","Observações fictícias")
    private fun data(vararg students:Student)=DiarySnapshot(classes=listOf(origin,target),students=students.toList())
    private fun rejected(action:()->Unit) {try {action();fail("Deveria bloquear")}catch(_:IllegalArgumentException){}}
    @Test fun explicitAdministrativeReactivationOnlyChangesStatusAndExit() {
        val before=data(closed)
        assertEquals(closed.copy(status="Ativo",left=null),EnrollmentReactivation.reopen(closed,before))
        assertEquals(closed,before.students.single())
    }
    @Test fun activeWithExitIsPreservedUntilExplicitRepair() {
        val stale=closed.copy(status="Ativo");val before=data(stale)
        assertEquals("2026-10-16",before.students.single().left)
        assertEquals(stale.copy(left=null),EnrollmentReactivation.reopen(stale,before))
        assertEquals(stale,before.students.single())
    }
    @Test fun completedMovementCannotBeReactivatedEvenWhenVisualStatusIsCorrupted() {
        val moved=closed.copy(status="Remanejado")
        rejected {EnrollmentReactivation.reopen(moved,data(moved))}
        val stale=moved.copy(status="Ativo")
        rejected {EnrollmentReactivation.reopen(stale,data(stale).copy(relocations=listOf(Relocation(stale.id,null,"2026-10-17","Destino fictício"))))}
    }
    @Test fun incomingMovementDatesAreNotReopened() {
        val incoming=closed.copy(id="destination",classId=target.id,joined="2026-09-01",status="Ativo")
        rejected {EnrollmentReactivation.reopen(incoming,data(incoming).copy(relocations=listOf(Relocation("source",incoming.id,"2026-09-01"))))}
    }
    @Test fun legacySuccessorWithSameIdentityPreventsOverlappingReactivation() {
        val successor=closed.copy(id="successor",classId=target.id,joined="2026-10-17",left=null,status="Ativo")
        rejected {EnrollmentReactivation.reopen(closed,data(closed,successor))}
    }
    @Test fun earlierClosedEnrollmentDoesNotPreventReactivation() {
        val previous=closed.copy(id="previous",classId=target.id,joined="2026-01-01",left="2026-05-31")
        val later=closed.copy(joined="2026-06-01")
        assertNull(EnrollmentReactivation.reopen(later,data(previous,later)).left)
    }
    @Test fun otherIdentityOrAcademicYearCannotBeChangedByReactivation() {
        val unrelated=closed.copy(id="other",personId="other-person",classId=target.id)
        val previous=closed.copy(id="previous-year",classId="previous-class",joined="2025-01-01",left=null,status="Ativo")
        val before=data(closed,unrelated,previous).copy(classes=listOf(origin,target,origin.copy(id="previous-class",year=2025)))
        assertNull(EnrollmentReactivation.reopen(closed,before).left)
        assertEquals(listOf(closed,unrelated,previous),before.students)
    }
}
