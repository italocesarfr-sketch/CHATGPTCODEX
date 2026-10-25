package br.com.diariodeclasse.data

/** Plano somente de leitura. A identidade é a matrícula, nunca o nome ou personId. */
data class StudentDeletionPreview(
    val student: Student, val school: SchoolClass, val counts: Map<String, Int>,
    val markCounts: Map<String, Int>, val exportPosition: Boolean, val blockedReason: String?, val fingerprint: String
)

object StudentDeletion {
    private fun Relocation.references(id: String) = sourceStudentId == id || targetStudentId == id ||
        corrections.any { c -> c.sourceStudentId == id || c.targetStudentId == id || c.removed.any { it.attendance.studentId == id } }

    fun blank(row: ExportRow) = row.copy(id = "blank:deleted:${row.id}", studentId = null, manualHidden = false)

    fun preview(data: DiarySnapshot, studentId: String, classId: String): StudentDeletionPreview {
        val student = requireNotNull(data.students.singleOrNull { it.id == studentId && it.classId == classId }) {
            "Esta matrícula não está mais nesta turma. Reabra a lista de alunos."
        }
        val school = requireNotNull(data.classes.singleOrNull { it.id == classId }) { "Turma não encontrada." }
        val grades = data.grades.filter { it.studentId == studentId }
        val marks = data.marks.filter { it.studentId == studentId }
        val recompositions = data.recompositions.filter { it.studentId == studentId }
        val rows = data.exportRows.filter { it.studentId == studentId }
        val links = data.academic().relocations.filter { it.references(studentId) }
        val historicalReference = data.histories.any { h ->
            h.content.students.any { it.id == studentId } || h.content.marks.any { it.studentId == studentId } ||
                h.content.grades.any { it.studentId == studentId } || h.content.recompositions.any { it.studentId == studentId }
        }
        val crossClass = grades.any { g -> data.activities.none { it.id == g.activityId && it.classId == classId } } ||
            marks.any { m -> data.lessons.none { it.id == m.lessonId && it.classId == classId } } || rows.any { it.classId != classId }
        val blocked = when {
            links.isNotEmpty() -> "Esta matrícula participa de um remanejamento confirmado. A exclusão individual não pode preservar esse histórico e suas auditorias. Nenhum registro será apagado."
            historicalReference -> "Esta matrícula é referenciada por histórico preservado de outra turma. A exclusão individual não pode romper esse vínculo."
            student.status == "Remanejado" -> "A matrícula está Remanejada, mas seu vínculo não pôde ser conferido. Revise o histórico antes de excluir."
            marks.any { it.mark !in setOf("P", "F", "FJ", "NR") } -> "Há frequência com situação não reconhecida. Revise a chamada antes de excluir."
            crossClass -> "Há notas, frequências ou posição de planilha vinculadas a outra turma. Revise esses vínculos antes de excluir."
            rows.size > 1 || rows.any { row -> data.exportRows.any { it.id == blank(row).id } } ->
                "A posição personalizada da matrícula está em conflito. Revise a disposição da planilha antes de excluir."
            else -> null
        }
        val counts = linkedMapOf(
            "Atividades com notas lançadas" to grades.count { it.value != null },
            "Lançamentos de nota pendentes" to grades.count { it.value == null },
            "Registros de frequência" to marks.size,
            "Recomposições com nota" to recompositions.count { it.value != null },
            "Recomposições sem nota" to recompositions.count { it.value == null },
            "Observações desta matrícula" to if(student.notes.isBlank()) 0 else 1,
            "Remanejamentos vinculados" to links.size,
            "Correções administrativas vinculadas" to links.sumOf { it.corrections.size }
        ).filterValues { it > 0 }
        return StudentDeletionPreview(student, school, counts, marks.groupingBy { it.mark }.eachCount(), rows.isNotEmpty(), blocked, ClassLifecycle.fingerprint(data))
    }

    /** Só chama depois de revalidar o plano. Mantém todas as outras linhas e seus IDs/posições. */
    fun without(data: DiarySnapshot, studentId: String) = data.copy(
        students = data.students.filterNot { it.id == studentId },
        grades = data.grades.filterNot { it.studentId == studentId },
        marks = data.marks.filterNot { it.studentId == studentId },
        recompositions = data.recompositions.filterNot { it.studentId == studentId },
        exportRows = data.exportRows.map { if(it.studentId == studentId) blank(it) else it }
    )
}
