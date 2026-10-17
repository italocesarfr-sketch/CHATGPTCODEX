package br.com.diariodeclasse.domain

/** A soma original é calculada pelas regras existentes, nunca substituída nas atividades. */
object RecompositionRules {
    fun effective(original: Double?, recomposition: Double?): Double? {
        require(original == null || original.isFinite() && original >= 0)
        require(recomposition == null || recomposition.isFinite() && recomposition in 0.0..10.0)
        // Sem lançamento original, não inferir nota zero nem classificar como abaixo de 5.
        if (original == null) return null
        // Sem recomposição realizada, conservar integralmente a escala/regra anterior.
        return if (recomposition == null) original else maxOf(original, recomposition).coerceAtMost(10.0)
    }
    fun belowFive(effective: Double?): Boolean = effective != null && effective < 5.0
}
