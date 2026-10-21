package br.com.diariodeclasse.export

import br.com.diariodeclasse.data.*
import java.time.LocalDate

/** Somente registros efetivamente escritos, sem consultar frequência ou lista de alunos. */
object LessonPlanExportProjection {
    fun build(data: DiarySnapshot, school: SchoolClass, start: String, end: String): ExportTable {
        require(data.classes.any { it.id == school.id }) { "Turma não encontrada." }
        require(LocalDate.parse(start).toString() == start && LocalDate.parse(end).toString() == end && start <= end &&
            LocalDate.parse(start).year == school.year && LocalDate.parse(end).year == school.year) { "Informe um período válido dentro do ano letivo." }
        val records = data.lessonRecords.filter { it.classId == school.id && it.date in start..end }
            .sortedWith(compareBy({ it.date }, { it.id })).map { record ->
                ExportRecord(null, listOf(ExportCell.number(LocalDate.parse(record.date).toEpochDay() + 25569, NumberStyle.DATE),
                    ExportCell.text(record.title), ExportCell.text(record.description)))
            }
        return ExportTable("Noctua • ${school.name} • ${school.subject} • ${school.year} • $start a $end • Planos de aula",
            ExportOptions(), listOf(ExportColumn("date", "Data"), ExportColumn("title", "Título"), ExportColumn("description", "Descrição")), records, SheetLayout.LESSON_PLANS)
    }
}
