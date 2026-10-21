package br.com.diariodeclasse.data

import androidx.room.TypeConverter
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate

/** Auditoria somente de leitura; nunca participa dos cálculos acadêmicos. */
@Serializable data class RemovedAttendance(val lesson: Lesson, val attendance: Attendance)
@Serializable data class RelocationCorrection(
    val id: String, val confirmedAt: String, val previousDate: String, val effectiveDate: String,
    val previousTarget: String, val externalTarget: String, val removed: List<RemovedAttendance>
)
class RelocationCorrectionConverters {
    private val json = Json { encodeDefaults = true }
    @TypeConverter fun encodeCorrections(value: List<RelocationCorrection>): String = json.encodeToString(value)
    @TypeConverter fun decodeCorrections(value: String): List<RelocationCorrection> = json.decodeFromString(value)
}
data class AffectedAcademicRecord(val date: String, val type: String, val value: String, val attendance: Attendance? = null)
data class RelocationCorrectionPreview internal constructor(
    internal val before: DiarySnapshot, val source: Student, val link: Relocation,
    val effectiveDate: String, val externalTarget: String, val records: List<AffectedAcademicRecord>,
    val remove: Set<String>, val blockers: List<String>
) { val canConfirm get() = blockers.isEmpty() }

object RelocationCorrections {
    fun preview(data: DiarySnapshot, studentId: String, day: String, destination: String,
        remove: Set<String> = emptySet()): RelocationCorrectionPreview {
        val source = requireNotNull(data.students.firstOrNull { it.id == studentId }) { "Matrícula não encontrada no cadastro atual." }
        val link = requireNotNull(data.relocations.firstOrNull { it.sourceStudentId == studentId }) { "Remanejamento não encontrado." }
        require(link.targetStudentId == null) { "Remanejamento interno: a correção precisa ajustar as duas matrículas em conjunto. Nenhum vínculo foi alterado." }
        require(source.status == "Remanejado" && source.left == LocalDate.parse(link.effectiveDate).minusDays(1).toString()) { "Matrícula e remanejamento divergem. Confira o histórico antes de corrigir." }
        val school = data.classes.first { it.id == source.classId }
        val parsedDay = runCatching { LocalDate.parse(day) }.getOrNull()
        require(parsedDay != null && parsedDay.toString() == day && parsedDay.year == school.year && day > source.joined) { "Informe uma data efetiva no ano letivo, posterior ao ingresso." }
        val target = destination.trim()
        require(target.length in 2..160) { "Informe o destino externo (2 a 160 caracteres)." }
        val lessons = data.lessons.associateBy { it.id }
        val marks = data.marks.filter { it.studentId == source.id && lessons.getValue(it.lessonId).date >= day }
        require(remove.all { id -> marks.any { it.lessonId == id } }) { "A seleção de frequências mudou. Confira novamente." }
        val activities = data.activities.associateBy { it.id }
        val grades = data.grades.filter { it.studentId == source.id && activities.getValue(it.activityId).date >= day }
        val recompositions = data.recompositions.filter { it.studentId == source.id && it.date >= day }
        val records = marks.map { mark -> val lesson = lessons.getValue(mark.lessonId)
            AffectedAcademicRecord(lesson.date, "${lesson.ordinal}º horário", mark.mark, mark)
        } + grades.map { g -> val a = activities.getValue(g.activityId)
            AffectedAcademicRecord(a.date, "Nota • ${a.name}", g.value?.toString() ?: "Pendente")
        } + recompositions.map { AffectedAcademicRecord(it.date, "Recomposição • ${it.term}º bimestre", it.value?.toString() ?: "Pendente") }
        val blockers = buildList {
            if(day == link.effectiveDate && target == link.externalTarget && remove.isEmpty()) add("Nenhuma correção informada.")
            marks.filter { it.mark != "NR" && it.lessonId !in remove }.forEach { m -> val l = lessons.getValue(m.lessonId)
                add("${l.date} • ${l.ordinal}º horário: ${m.mark}. Preserve a data ou selecione explicitamente a retirada da marcação indevida.") }
            if (grades.isNotEmpty()) add("${grades.size} lançamento(s) de nota na data proposta ou depois. As notas serão preservadas; confira a data efetiva.")
            if (recompositions.isNotEmpty()) add("${recompositions.size} recomposição(ões) na data proposta ou depois. Confira a data efetiva; nenhum resultado será excluído.")
        }
        return RelocationCorrectionPreview(data, source, link, day, target,
            records.sortedWith(compareBy({ it.date }, { it.type }, { it.value })), remove.toSet(), blockers)
    }
}
