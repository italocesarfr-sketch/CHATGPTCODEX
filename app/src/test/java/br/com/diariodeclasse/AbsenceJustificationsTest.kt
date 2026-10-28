package br.com.diariodeclasse

import br.com.diariodeclasse.data.*
import org.junit.Assert.*
import org.junit.Test

class AbsenceJustificationsTest {
    private val absence=Attendance("aula-ficticia","matricula-ficticia","F")
    @Test fun everyCategoryConvertsOnlyTheSelectedOccurrenceToFj() {
        for(reason in JustificationReasons.options) {
            val next=AbsenceJustifications.apply(absence,reason)
            assertEquals(absence.copy(mark="FJ",reason=reason),next)
            assertTrue(JustificationReasons.valid(next))
        }
    }
    @Test fun changingReasonKeepsFjAndRemovingReturnsNormalAbsence() {
        val old=absence.copy(mark="FJ",reason="Atestado médico")
        assertEquals(old.copy(reason="Outro"),AbsenceJustifications.apply(old,"Outro"))
        assertEquals(absence,AbsenceJustifications.apply(old,null))
    }
    @Test fun legacyFjWithoutReasonCanBeRemovedWithoutAssigningAnyCategory() {
        assertEquals(absence,AbsenceJustifications.apply(absence.copy(mark="FJ"),null))
    }
    @Test fun presenceNrAndInvalidReasonsCannotBeJustifiedImplicitly() {
        fun reject(block:()->Unit){try{block();fail()}catch(_:IllegalArgumentException){}}
        for(mark in listOf("P","NR"))reject{AbsenceJustifications.apply(absence.copy(mark=mark),"Outro")}
        reject{AbsenceJustifications.apply(absence,null)}
        for(reason in listOf("","Não autorizado"))reject{AbsenceJustifications.apply(absence,reason)}
    }
}
