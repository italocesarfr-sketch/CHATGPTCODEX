package br.com.diariodeclasse

import br.com.diariodeclasse.sync.academic.*
import org.junit.Assert.*
import org.junit.Test

class CommercialAdmissionTest {
    @Test fun commercialAndTestAuthorizationHaveSeparateWireContracts() {
        val status=AcademicRecords.json.decodeFromString<AcademicProtocolStatus>(
            """{"protocol":2,"v1Sequence":0,"v1":[],"testAllowed":false,"academicScope":5,"academicSequence":0}""")
        assertFalse(status.testAllowed)
        val enrollment=AcademicRecords.json.decodeFromString<AcademicEnrollment>(
            """{"eligible":true,"initialized":true,"reason":"READY","academicSequence":1}""")
        enrollment.validate();assertTrue(enrollment.initialized)
    }
    @Test fun inconsistentOrUnknownAdmissionNeverBecomesReady() {
        for(value in listOf(AcademicEnrollment(false,true,"READY"),AcademicEnrollment(true,false,"READY"),
            AcademicEnrollment(true,true,"NEW"),AcademicEnrollment(true,true,"OTHER"),AcademicEnrollment(true,true,"READY",-1))) {
            assertThrows(IllegalArgumentException::class.java){value.validate()}
        }
        for(reason in listOf("INELIGIBLE","REVIEW"))AcademicEnrollment(false,false,reason).validate()
    }
    @Test fun bootstrapCannotClaimSyncedEvenWithNoDirtyRows() {
        val c=AcademicControl(owner="fixture",stage="BOOTSTRAP_E",enabled=false,checked=true,message="")
        assertEquals("Preparando sincronização",academicLabel(c,emptyList(),emptyList(),emptyList()))
        assertEquals("Aguardando internet",academicLabel(c.copy(message="OFFLINE"),emptyList(),emptyList(),emptyList()))
        assertEquals("Sincronização indisponível para esta conta",academicLabel(c.copy(message="INELIGIBLE"),emptyList(),emptyList(),emptyList()))
        assertEquals("Dados preservados • revisão necessária",academicLabel(c.copy(message="REVIEW"),emptyList(),emptyList(),emptyList()))
    }
    @Test fun restoreAndConflictsStillOverrideBootstrapIndicators() {
        val c=AcademicControl(owner="fixture",stage="RESTORE",message="SYNCING")
        assertEquals("Pausado após restauração",academicLabel(c,emptyList(),emptyList(),emptyList()))
        val pending=listOf(AcademicPending("op","fixture","{}","[]",0,conflict="saved"))
        assertEquals("Conflito preservado",academicLabel(c.copy(stage="BOOTSTRAP_E"),emptyList(),pending,emptyList()))
    }
}
