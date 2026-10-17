package br.com.diariodeclasse.domain

import java.math.BigDecimal
import java.math.MathContext

/** Cálculo decimal com 34 algarismos; Room mantém Double, sem arredondamento de exibição. */
object GradeScaling {
    fun proportional(value: Double?, oldMaximum: Double, newMaximum: Double): Double? {
        require(oldMaximum.isFinite() && oldMaximum > 0 && newMaximum.isFinite() && newMaximum > 0) { "Pontuação máxima inválida." }
        if (value == null) return null
        require(value.isFinite() && value in 0.0..oldMaximum) { "Há nota incompatível com o máximo anterior." }
        val result = BigDecimal.valueOf(value).multiply(BigDecimal.valueOf(newMaximum))
            .divide(BigDecimal.valueOf(oldMaximum), MathContext.DECIMAL128).toDouble()
        require(result.isFinite() && result in 0.0..newMaximum && (value == 0.0 || result > 0)) { "O resultado excede a precisão disponível. Use outra pontuação máxima." }
        return result
    }
}
