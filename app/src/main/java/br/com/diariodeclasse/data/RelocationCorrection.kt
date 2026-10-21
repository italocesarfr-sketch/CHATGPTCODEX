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
    val previousTarget: String, val externalTarget: String?, val removed: List<RemovedAttendance>,
    val operation: String = "CORRECAO_EXTERNA", val sourceStudentId: String? = null,
    val targetStudentId: String? = null, val targetClassId: String? = null, val targetLabel: String? = null
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
    val remove: Set<String>, val blockers: List<String>, val attendanceBlockers: Map<String, String> = emptyMap(),
    val target: Student? = null, val destinationClass: SchoolClass? = null
) {
    val canConfirm get() = blockers.isEmpty()
    val linkedExisting get() = target != null && before.students.any { it.id == target.id }
    val destinationLabel get() = destinationClass?.let { "${it.name} • ${it.subject} • ${it.year} • ${it.shift}" } ?: externalTarget
}

/** O snapshot da prévia precisa continuar idêntico até o commit. */
class RelocationCorrectionChanged : IllegalArgumentException("Os registros mudaram. A prévia precisa ser atualizada; nada foi alterado.")

/** Seleção por ID. Uma turma cadastrada nunca pode ser salva como descrição externa. */
object CorrectionDestinations {
    fun eligible(data: DiarySnapshot, source: Student): List<SchoolClass> {
        val origin = data.classes.first { it.id == source.classId }
        return data.classes.filter { candidate ->
            data.lineage(source).none { it.classId == candidate.id } && data.classArchives.none { it.classId == candidate.id } &&
                RelocationRules.compatible(origin, candidate, data.terms)
        }.sortedWith(compareBy({ it.year }, { it.name }, { it.subject }, { it.shift }, { it.id }))
    }

}

object RelocationCorrections {
    fun preview(data: DiarySnapshot, studentId: String, day: String, destination: String,
        remove: Set<String> = emptySet(), targetClassId: String? = null, newStudentId: String? = null): RelocationCorrectionPreview {
        val source = requireNotNull(data.students.firstOrNull { it.id == studentId }) { "Matrícula não encontrada no cadastro atual." }
        val link = requireNotNull(data.relocations.firstOrNull { it.sourceStudentId == studentId }) { "Remanejamento não encontrado." }
        require(link.targetStudentId == null) { "Remanejamento interno: a correção precisa ajustar as duas matrículas em conjunto. Nenhum vínculo foi alterado." }
        require(source.status == "Remanejado" && source.left == LocalDate.parse(link.effectiveDate).minusDays(1).toString()) { "Matrícula e remanejamento divergem. Confira o histórico antes de corrigir." }
        val school = data.classes.first { it.id == source.classId }
        val parsedDay = runCatching { LocalDate.parse(day) }.getOrNull()
        require(parsedDay != null && parsedDay.toString() == day && parsedDay.year == school.year && day > source.joined) { "Informe uma data efetiva no ano letivo, posterior ao ingresso." }
        val target = if (targetClassId == null) destination.trim() else requireNotNull(link.externalTarget)
        require(target.length in 2..160) { "Informe o destino externo (2 a 160 caracteres)." }
        val lessons = data.lessons.associateBy { it.id }
        val marks = data.marks.filter { it.studentId == source.id && lessons.getValue(it.lessonId).date >= day }
        require(remove.all { id -> marks.any { it.lessonId == id } }) { "A seleção de frequências mudou. Confira novamente." }
        val activities = data.activities.associateBy { it.id }
        val grades = data.grades.filter { it.studentId == source.id && activities.getValue(it.activityId).date >= day }
        val recompositions = data.recompositions.filter { it.studentId == source.id && it.date >= day }
        val affectedFrom = minOf(day, link.effectiveDate)
        val records = data.marks.filter { it.studentId == source.id && lessons.getValue(it.lessonId).date >= affectedFrom }.map { mark -> val lesson = lessons.getValue(mark.lessonId)
            AffectedAcademicRecord(lesson.date, "${lesson.ordinal}º horário", mark.mark, mark.takeIf { lesson.date >= day })
        } + data.grades.filter { it.studentId == source.id && activities.getValue(it.activityId).date >= affectedFrom }.map { g -> val a = activities.getValue(g.activityId)
            AffectedAcademicRecord(a.date, "Nota • ${a.name}", g.value?.toString() ?: "Pendente")
        } + data.recompositions.filter { it.studentId == source.id && it.date >= affectedFrom }.map { AffectedAcademicRecord(it.date, "Recomposição • ${it.term}º bimestre", it.value?.toString() ?: "Pendente") }
        val attendanceBlockers = marks.filter { it.mark != "NR" && it.lessonId !in remove }.associate { m ->
            val l = lessons.getValue(m.lessonId)
            m.lessonId to "${LocalDate.parse(l.date).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))} • ${l.ordinal}º horário • ${m.mark}: confirme a retirada ou reveja a data."
        }
        val blockers = buildList {
            if(targetClassId == null && day == link.effectiveDate && target == link.externalTarget && remove.isEmpty()) add("Nenhuma correção informada.")
            addAll(attendanceBlockers.values)
            if (grades.isNotEmpty()) add("${grades.size} nota(s) na data proposta ou depois. Reveja a data; as notas serão preservadas.")
            if (recompositions.isNotEmpty()) add("${recompositions.size} recomposição(ões) na data proposta ou depois. Reveja a data; os resultados serão preservados.")
        }
        val enrollment = targetClassId?.let { RelocationRules.internalTarget(data, source, it, day, newStudentId = newStudentId) }
        if (enrollment != null && data.students.any { it.id == enrollment.id }) {
            require(data.marks.none { m -> m.studentId == enrollment.id && lessons.getValue(m.lessonId).date < day } &&
                data.grades.none { g -> g.studentId == enrollment.id && activities.getValue(g.activityId).date < day } &&
                data.recompositions.none { it.studentId == enrollment.id && it.date < day }) {
                "A matrícula de destino possui frequência, nota ou recomposição anterior ao ingresso, inclusive registros pendentes. Confira o histórico; nenhuma data será ajustada."
            }
        }
        var preview = RelocationCorrectionPreview(data, source, link, day, target,
            records.sortedWith(compareBy({ it.date }, { it.type }, { it.value })), remove.toSet(), blockers, attendanceBlockers,
            enrollment, targetClassId?.let { id -> data.classes.first { it.id == id } })
        if (enrollment != null) {
            try {
                val after = proposed(preview, audit(preview))
                RelocationRules.validateLinks(after)
                br.com.diariodeclasse.backup.BackupCodec.validate(ClassLifecycle.snapshot(after))
            } catch (conflict: IllegalArgumentException) {
                preview = preview.copy(blockers = preview.blockers + (conflict.message ?: "Histórico do destino incompatível."))
            }
        }
        return preview
    }
    fun audit(preview: RelocationCorrectionPreview): RelocationCorrection {
        val removed = preview.before.marks.filter { it.studentId == preview.source.id && it.lessonId in preview.remove }
            .map { RemovedAttendance(preview.before.lessons.first { l -> l.id == it.lessonId }, it) }.sortedBy { it.lesson.id }
        return RelocationCorrection(newId(), java.time.Instant.now().toString(), preview.link.effectiveDate,
            preview.effectiveDate, requireNotNull(preview.link.externalTarget), preview.externalTarget.takeIf { preview.target == null }, removed,
            if (preview.target == null) "CORRECAO_EXTERNA" else "CONVERSAO_INTERNA",
            preview.source.id.takeIf { preview.target != null }, preview.target?.id, preview.target?.classId,
            preview.destinationLabel.takeIf { preview.target != null })
    }
    fun proposed(preview: RelocationCorrectionPreview, audit: RelocationCorrection): DiarySnapshot {
        val target = preview.target
        return preview.before.copy(
            students = preview.before.students.map { if (it.id == preview.source.id) it.copy(left = LocalDate.parse(preview.effectiveDate).minusDays(1).toString()) else it } +
                listOfNotNull(target?.takeIf { t -> preview.before.students.none { it.id == t.id } }),
            marks = preview.before.marks.filterNot { it.studentId == preview.source.id && it.lessonId in preview.remove },
            relocations = preview.before.relocations.map { if (it.sourceStudentId == preview.source.id) it.copy(
                effectiveDate = preview.effectiveDate, targetStudentId = target?.id, externalTarget = audit.externalTarget,
                corrections = it.corrections + audit) else it })
    }

}
