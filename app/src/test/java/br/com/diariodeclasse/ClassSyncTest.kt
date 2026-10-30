package br.com.diariodeclasse

import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.sync.*
import kotlinx.serialization.decodeFromString
import org.junit.Test
import org.junit.Assert.*

class ClassSyncTest {
    private val school=SchoolClass(id="same-stable-id",name="Turma Fictícia Ç",subject="História",year=2026)
    private fun bundle()=ClassBundle(school,(1..4).map{n->val start=java.time.LocalDate.of(2026,(n-1)*3+1,1);Term(school.id,n,start.toString(),start.plusMonths(3).minusDays(1).toString())})
    private fun rejects(block:()->Unit){try{block();fail("Deveria rejeitar")}catch(_:IllegalArgumentException){}catch(_:IllegalStateException){}}
    @Test fun wirePreservesAllClassAndTermFieldsAndUsesNoStudentData(){
        val value=bundle();assertEquals(value,ClassBundle.decode(value.encode()))
        assertFalse(value.encode().contains("students"));assertFalse(value.encode().contains("marks"))
    }
    @Test fun canonicalTermsOrderIsStable(){val value=bundle();assertEquals(value.encode(),value.copy(terms=value.terms.reversed()).encode())}
    @Test fun rejectsIncompleteAndForeignTerms(){val value=bundle();rejects{value.copy(terms=value.terms.take(3)).encode()};rejects{value.copy(terms=value.terms.map{it.copy(classId="other")}).encode()}}
    @Test fun rejectsOverlappingOrOtherYearTerms(){val value=bundle();rejects{value.copy(terms=value.terms.map{it.copy(start="2026-01-01")}).encode()};rejects{value.copy(school=school.copy(year=2027)).encode()}}
    @Test fun rejectsInvalidAcademicSettings(){rejects{bundle().copy(school=school.copy(scale=0.0)).encode()};rejects{bundle().copy(school=school.copy(schedule="1,2")).encode()}}
    @Test fun remoteRecordMustUseSameClassIdAndPositiveRevision(){rejects{RemoteClass("other",1,1,false,bundle().encode()).validate()};rejects{RemoteClass(school.id,0,1,false,bundle().encode()).validate()}}
    @Test fun tombstoneDoesNotRequireAnAcademicPayload(){RemoteClass(school.id,2,4,true,"").validate()}
    @Test fun decodeRejectsExtraStudentPayload(){rejects{ClassBundle.decode(bundle().encode().dropLast(1)+",\"students\":[]}")}}
    @Test fun pushReceiptRetainsOperationIdentity(){val raw="""{"operationId":"same-op","accepted":true,"record":{"id":"id","revision":2,"sequence":4,"deleted":true,"payload":""}}""";assertEquals("same-op",ClassBundle.wire.decodeFromString<PushReply>(raw).operationId)}
    @Test fun statusNeverClaimsOfflinePendingOrConflictedWorkIsSynchronized(){
        val control=SyncControl(owner="owner",enabled=true,checked=true)
        val pending=SyncClassState("id","owner")
        assertEquals("Turmas: alterações pendentes",syncLabel(control,listOf(pending)))
        assertEquals("Turmas: conflito preservado",syncLabel(control,listOf(pending.copy(conflict="remote"))))
        assertEquals("Turmas: aguarda configuração",syncLabel(control.copy(message="SETUP"),emptyList()))
        assertEquals("Turmas sincronizadas",syncLabel(control,listOf(pending.copy(acknowledged=1))))
        assertEquals("Turmas: sincronização desativada",syncLabel(null,emptyList()))
    }
}
