package br.com.diariodeclasse.data

/** Classificações de apresentação. Não participam de nenhum cálculo acadêmico. */
val activityTypes = listOf("Prova", "Trabalho", "Redação", "Produção textual", "Atividade prática", "Seminário", "Estudo dirigido", "Outro")

object JustificationReasons {
    val options = listOf("Atestado médico", "Acompanhamento médico", "Falecimento familiar", "Convocação oficial", "Atividade escolar ou esportiva", "Outro")
    fun valid(attendance: Attendance): Boolean = attendance.reason == null ||
        (attendance.mark == "FJ" && attendance.reason in options)
}
