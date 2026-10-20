package br.com.diariodeclasse.data

import java.time.LocalDate

/** Validação reutilizada na conferência e, novamente, dentro da transação de remoção. */
object EmptyDayHours {
    fun validate(data: DiarySnapshot, classId: String, day: String): List<Lesson> {
        val school = requireNotNull(data.classes.firstOrNull { it.id == classId }) { "Turma não encontrada." }
        require(LocalDate.parse(day).year == school.year) { "Escolha uma data do ano letivo da turma." }
        val lessons = data.lessons.filter { it.classId == classId && it.date == day }.sortedBy { it.ordinal }
        require(lessons.isNotEmpty()) { "Esta data já está sem horários." }
        val ids = lessons.map { it.id }.toSet()
        val linked = lessons.any { it.note.isNotEmpty() } || data.marks.any { it.lessonId in ids } ||
            data.lessonRecords.any { it.classId == classId && it.date == day } ||
            data.activities.any { it.classId == classId && it.date == day } ||
            data.recompositions.any { r -> r.date == day && data.students.any { it.id == r.studentId && it.classId == classId } } ||
            data.histories.any { h -> h.content.lessons.any { it.id in ids } || h.content.marks.any { it.lessonId in ids } }
        require(!linked) { "Existem registros vinculados a esta data. Os horários foram preservados." }
        return lessons
    }
}
