package br.com.diariodeclasse.data

/** Classificações de apresentação. Não participam de nenhum cálculo acadêmico. */
val activityTypes = listOf("Prova", "Trabalho", "Redação", "Produção textual", "Atividade prática", "Seminário", "Estudo dirigido", "Outro")

object JustificationReasons {
    val options = listOf("Atestado médico", "Acompanhamento médico", "Falecimento familiar", "Convocação oficial", "Atividade escolar ou esportiva", "Outro")
    fun valid(attendance: Attendance): Boolean = attendance.reason == null ||
        (attendance.mark == "FJ" && attendance.reason in options)
}

/** Justificar é uma ação explícita sobre F/FJ; não há regra nova de abono ou cálculo. */
object AbsenceJustifications {
    fun apply(expected: Attendance, reason: String?): Attendance {
        require(expected.mark in listOf("F", "FJ")) { "Registre a falta antes de justificá-la." }
        require(reason == null || reason in JustificationReasons.options) { "Escolha um motivo válido." }
        require(reason != null || expected.mark == "FJ") { "Esta falta já está sem justificativa." }
        return expected.copy(mark = if (reason == null) "F" else "FJ", reason = reason)
    }
}
