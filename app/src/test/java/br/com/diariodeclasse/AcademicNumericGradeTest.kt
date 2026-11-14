package br.com.diariodeclasse

import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.sync.academic.*
import kotlinx.serialization.json.*
import org.junit.Test
import org.junit.Assert.*

class AcademicNumericGradeTest {
    private val fixture get()=BackupCodec.json.decodeFromString<BackupSnapshot>(java.io.File("src/androidTest/assets/phase-d.json").readText()).canonical()
    private fun preview(local:Double?,remote:Double?,deleted:Boolean=false,restore:Boolean=false):AcademicPreview {
        val base=fixture.let{s->s.copy(grades=s.grades.map{if(it.activityId=="d-a1"&&it.studentId=="ds2")it.copy(value=4.0)else it})}
        fun variant(value:Double?,remove:Boolean=false)=base.copy(grades=base.grades.mapNotNull {
            if(it.activityId=="d-a1"&&it.studentId=="ds2")if(remove)null else it.copy(value=value)else it
        })
        val b=AcademicRecords.rows(base);val l=AcademicRecords.rows(variant(local));val r=AcademicRecords.rows(variant(remote,deleted))
        val known=b.associate{it.identity to 1L};val baseMap=b.associateBy{it.identity};val remoteMap=r.associateBy{it.identity}
        val latest=(baseMap.keys+remoteMap.keys).associateWith{if(baseMap[it]==remoteMap[it])1L else 2L}
        val evidence=AcademicResolution.evidence(b,l,r,known,latest,restore)
        return AcademicPreview("context",restore,1,2,b,l,r,known,latest,emptyList(),emptyList(),evidence)
    }
    @Test fun suggestsGreaterRemoteWithoutChangingEitherVariant() {
        val p=preview(6.0,8.0);val result=AcademicNumericGrade.suggestion(p)!!
        assertEquals(8.0,result.higher,0.0);assertEquals(6.0,result.other,0.0)
        assertEquals(AcademicChoice.KEEP_REMOTE,result.higherChoice);assertEquals(AcademicChoice.KEEP_LOCAL,result.otherChoice)
        assertEquals("Estudante fictício ds2",result.student);assertTrue(result.classroom.contains("d-source"));assertEquals("Produção fictícia",result.activity)
        assertEquals(p.evidence.single().local,p.local.single{it.identity==p.evidence.single().identity}.payload)
    }
    @Test fun suggestsGreaterLocalAndAllowsExplicitZero() {
        val result=AcademicNumericGrade.suggestion(preview(9.0,0.0))!!
        assertEquals(AcademicChoice.KEEP_LOCAL,result.higherChoice);assertEquals(AcademicChoice.KEEP_REMOTE,result.otherChoice)
        assertEquals("0",conflictGradeNumber(result.other))
    }
    @Test fun equalValuesNeverProduceAHigherSuggestion(){assertNull(AcademicNumericGrade.suggestion(preview(7.0,7.0)))}
    @Test fun pendingNullAndTombstonesUseFullReview() {
        assertNull(AcademicNumericGrade.suggestion(preview(7.0,null)))
        assertNull(AcademicNumericGrade.suggestion(preview(7.0,8.0,true)))
    }
    @Test fun restoreNeverUsesTheSimplifiedGradeDecision(){assertNull(AcademicNumericGrade.suggestion(preview(6.0,8.0,restore=true)))}
    @Test fun invalidMaximumNegativeAndNonFiniteNeverBecomeSuggestions() {
        val p=preview(6.0,8.0);val e=p.evidence.single()
        for(value in listOf(11.0,-1.0,Double.NaN,Double.POSITIVE_INFINITY)) {
            val invalid=JsonObject(e.local!!+mapOf("value" to JsonPrimitive(value)))
            assertNull(AcademicNumericGrade.suggestion(p.copy(evidence=listOf(e.copy(local=invalid)))))
        }
    }
    @Test fun dependentOrMultipleDifferencesKeepExistingConsistentSetReview() {
        val p=preview(6.0,8.0);val extra=p.evidence.single().copy(kind="activities")
        assertNull(AcademicNumericGrade.suggestion(p.copy(evidence=p.evidence+extra)))
    }
    @Test fun exactDecimalsStayDistinguishable(){assertNotEquals(conflictGradeNumber(0.0001),conflictGradeNumber(0.0002))}
    @Test fun aNormalLaterEditOnCurrentBaseIsNotAConcurrentConflict() {
        val p=preview(6.0,8.0)
        val evidence=AcademicResolution.evidence(p.remote,p.remote,p.remote,p.latest,p.latest,false)
        assertTrue(evidence.isEmpty());assertNull(AcademicNumericGrade.suggestion(p.copy(base=p.remote,local=p.remote,known=p.latest,evidence=evidence)))
    }
}
