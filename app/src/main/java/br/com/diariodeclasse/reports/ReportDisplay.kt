package br.com.diariodeclasse.reports

/** Apenas apresentação. Os valores e desempates continuam em SchoolReports.ordered. */
object ReportDisplay {
    val views = listOf("Geral", "Notas", "Frequência")
    fun orders(view: String): List<StudentOrder> = listOf(StudentOrder.CALL, StudentOrder.ALPHABETICAL) + when(view) {
        "Notas" -> listOf(StudentOrder.HIGHEST_GRADE, StudentOrder.LOWEST_GRADE)
        "Frequência" -> listOf(StudentOrder.ABSENCES, StudentOrder.LEAST_ABSENCES)
        else -> listOf(StudentOrder.HIGHEST_GRADE, StudentOrder.LOWEST_GRADE, StudentOrder.ABSENCES, StudentOrder.LEAST_ABSENCES)
    }
    fun compatible(view: String, order: StudentOrder) = order.takeIf { it in orders(view) } ?: StudentOrder.CALL
    fun label(order: StudentOrder) = when(order) {
        StudentOrder.CALL -> "Nº da chamada"
        StudentOrder.ALPHABETICAL -> "Nome A–Z"
        StudentOrder.HIGHEST_GRADE -> "Maior nota"
        StudentOrder.LOWEST_GRADE -> "Menor nota"
        StudentOrder.ABSENCES -> "Mais faltas"
        StudentOrder.LEAST_ABSENCES -> "Menos faltas"
    }
}
