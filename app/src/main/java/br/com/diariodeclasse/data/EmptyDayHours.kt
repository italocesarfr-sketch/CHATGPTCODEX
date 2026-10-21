package br.com.diariodeclasse.data

import java.time.LocalDate

/** Diagnóstico imutável: NR persistido é diferente de ausência de linha. */
data class DayHoursDiagnosis internal constructor(
    val classId: String, val day: String, val lessons: List<Lesson>, val marks: List<Attendance>,
    val details: List<String>, val blockers: List<String>, internal val fingerprint: String
) {
    val nrCount get() = marks.count { it.mark == "NR" }
    val canRemove get() = lessons.isNotEmpty() && blockers.isEmpty()
}
object EmptyDayHours {
    fun diagnose(data: DiarySnapshot, classId: String, day: String): DayHoursDiagnosis {
        val school = requireNotNull(data.classes.firstOrNull { it.id == classId }) { "Turma não encontrada." }
        require(LocalDate.parse(day).toString() == day && LocalDate.parse(day).year == school.year) { "Selecione uma data do ano letivo." }
        val lessons = data.lessons.filter { it.classId == classId && it.date == day }.sortedBy { it.ordinal }
        val ids = lessons.map { it.id }.toSet()
        val marks = data.marks.filter { it.lessonId in ids }.sortedWith(compareBy({ it.lessonId }, { it.studentId }))
        val details = mutableListOf<String>(); val blockers = mutableListOf<String>()
        fun block(message: String) { details += message; blockers += message }
        lessons.forEach { lesson ->
            val counts = marks.filter { it.lessonId == lesson.id }.groupingBy { it.mark }.eachCount()
            if (counts.isNotEmpty()) details += "${lesson.ordinal}º horário: " + counts.toSortedMap().entries.joinToString(" • ") { "${it.value} ${it.key}" }
            counts.filterKeys { it != "NR" }.forEach { (mark, count) -> blockers += "${lesson.ordinal}º horário: $count $mark" }
            if (lesson.note.isNotEmpty()) block("${lesson.ordinal}º horário: observação registrada.")
        }
        val records = data.lessonRecords.filter { it.classId == classId && it.date == day }
        if (records.isNotEmpty()) block("${records.size} registro(s) do diário: ${records.joinToString { it.title }}")
        val activities = data.activities.filter { it.classId == classId && it.date == day }
        if (activities.isNotEmpty()) block("${activities.size} atividade(s): ${activities.joinToString { it.name }}")
        val people = data.students.filter { it.classId == classId }.map { it.id }.toSet()
        val recompositions = data.recompositions.filter { it.studentId in people && it.date == day }
        if (recompositions.isNotEmpty()) block("${recompositions.size} recomposição(ões) registrada(s).")
        val protectedIds = data.histories.flatMap { it.content.lessons }.map { it.id }.toSet() +
            data.histories.flatMap { it.content.marks }.map { it.lessonId } +
            data.academic().relocations.flatMap { it.corrections }.flatMap { it.removed }.map { it.lesson.id }
        lessons.filter { it.id in protectedIds }.forEach { block("${it.ordinal}º horário: referência histórica preservada.") }
        // Vínculo de matrícula, sozinho, não protege um NR: só referências efetivas ao horário.
        return DayHoursDiagnosis(classId, day, lessons, marks, details, blockers, ClassLifecycle.fingerprint(data))
    }
    /** Caminho legado remove apenas horários sem qualquer linha de frequência. */
    fun validate(data: DiarySnapshot, classId: String, day: String): List<Lesson> {
        val diagnosis = diagnose(data, classId, day)
        require(diagnosis.lessons.isNotEmpty()) { "Esta data já está sem horários." }
        require(diagnosis.canRemove && diagnosis.marks.isEmpty()) {
            diagnosis.blockers.joinToString("\n").ifEmpty { "Há ${diagnosis.nrCount} registro(s) NR. Confira a remoção dos horários e registros NR." }
        }
        return diagnosis.lessons
    }
}
