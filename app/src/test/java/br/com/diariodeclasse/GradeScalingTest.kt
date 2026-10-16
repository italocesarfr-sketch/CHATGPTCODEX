package br.com.diariodeclasse

import br.com.diariodeclasse.domain.GradeScaling
import org.junit.Assert.*
import org.junit.Test

class GradeScalingTest {
    @Test fun requestedReductionAndIncreaseExamples() {
        assertEquals(0.8,GradeScaling.proportional(8.0,10.0,1.0)!!,1e-15)
        assertEquals(1.6,GradeScaling.proportional(4.0,5.0,2.0)!!,1e-15)
        assertEquals(3.75,GradeScaling.proportional(1.5,2.0,5.0)!!,1e-15)
    }
    @Test fun zeroAndPendingRemainDistinct() {
        assertNull(GradeScaling.proportional(null,10.0,1.0))
        assertEquals(0.0,GradeScaling.proportional(0.0,10.0,1.0)!!,0.0)
    }
    @Test fun successiveConversionsDoNotRoundToDisplayedCents() {
        val original=8.123456789123456
        var value=original
        repeat(1000) {
            value=GradeScaling.proportional(value,10.0,3.0)!!
            value=GradeScaling.proportional(value,3.0,7.0)!!
            value=GradeScaling.proportional(value,7.0,10.0)!!
        }
        assertEquals(original,value,1e-12)
        assertEquals(0.08123456789123456,GradeScaling.proportional(original,10.0,0.1)!!,1e-16)
    }
    @Test fun invalidOrUnrepresentableValuesAreRejected() {
        listOf(Triple(8.0,0.0,1.0),Triple(8.0,10.0,Double.POSITIVE_INFINITY),Triple(11.0,10.0,1.0),Triple(Double.NaN,10.0,1.0),Triple(1.0,10.0,Double.MIN_VALUE)).forEach {(value,old,next)->
            try {GradeScaling.proportional(value,old,next);fail("Valor inválido deveria ser rejeitado")}catch(_:IllegalArgumentException){}
        }
    }
}
