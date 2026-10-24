package br.com.diariodeclasse.data

import androidx.room.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.Instant

/** Previsão independente de Lesson. O retrato da turma mantém legível uma revisão após sua exclusão. */
@Serializable
data class WeeklySlot(val id: String = newId(), val classId: String, val weekday: Int, val ordinal: Int,
    val className: String, val subject: String, val shift: String)

/** Cada revisão é imutável. A maior revisão de uma data vale até a próxima vigência do mesmo ano. */
@Serializable
@Entity(tableName = "weekly_timetables", indices = [Index(value = ["year", "effective", "revision"], unique = true)])
data class WeeklyTimetable(@PrimaryKey val id: String = newId(), val year: Int, val effective: String,
    val revision: Int, val createdAt: String = Instant.now().toString(), val slots: List<WeeklySlot>)

class TimetableConverters {
    @TypeConverter fun encode(slots: List<WeeklySlot>): String = Json.encodeToString(slots)
    @TypeConverter fun decode(value: String): List<WeeklySlot> = Json.decodeFromString(value)
}

data class TimetablePreview(val expected: List<WeeklyTimetable>, val proposed: WeeklyTimetable,
    val through: String, val affected: List<String>,
    val additional: List<WeeklyTimetable> = emptyList(), val annual: Boolean = false) {
    val additions get() = listOf(proposed) + additional
}

enum class TimetableScope(val label: String) { TODAY("A partir de hoje"), YEAR("Para o ano todo") }

object Timetables {
    private fun validateEditedSlots(expected: List<WeeklyTimetable>, slots: List<WeeklySlot>) {
        val historical = expected.flatMap { it.slots }
        require(slots.filter { it.ordinal > 6 }.all { slot -> historical.any {
            it.id == slot.id && it.classId == slot.classId && it.weekday == slot.weekday && it.ordinal == slot.ordinal
        } }) { "Novos horários devem usar os períodos de 1º a 6º do turno. Períodos antigos podem ser mantidos ou retirados explicitamente." }
    }
    fun effective(year: Int, scope: TimetableScope, today: LocalDate = LocalDate.now()): String {
        require(year in 2000..2100) { "Confira o ano letivo." }
        if (scope == TimetableScope.YEAR) return "$year-01-01"
        require(today.year == year) { "A partir de hoje só está disponível no ano atual. Para $year, escolha Para o ano todo." }
        return today.toString()
    }

    /** Sombreia cada fronteira existente com uma nova revisão. Nenhuma revisão antiga é alterada.
     * O mesmo resolvedor da 0.17.0 e o backup 11 continuam suficientes para ler o resultado. */
    fun annualPreview(data: DiarySnapshot, year: Int, slots: List<WeeklySlot>): TimetablePreview {
        val start = effective(year, TimetableScope.YEAR)
        val expected = data.timetables.filter { it.year == year }.sortedBy { it.id }
        validateEditedSlots(expected,slots)
        val historical = expected.flatMap { it.slots }
        slots.filter { it !in historical }.forEach { slot ->
            val school = requireNotNull(data.classes.firstOrNull { it.id == slot.classId }) { "Turma não encontrada. Reabra os horários." }
            require(school.year == year && data.classArchives.none { it.classId == school.id }) { "Escolha uma turma ativa do mesmo ano." }
        }
        val createdAt = Instant.now().toString()
        val additions = (listOf(start) + expected.map { it.effective }).distinct().sorted().map { date ->
            WeeklyTimetable(year = year, effective = date,
                revision = (expected.filter { it.effective == date }.maxOfOrNull { it.revision } ?: 0) + 1,
                createdAt = createdAt, slots = slots.sortedWith(compareBy({ it.weekday }, { it.ordinal })))
        }
        validate(data.timetables + additions)
        val through = "$year-12-31"
        return TimetablePreview(expected, additions.first(), through,
            affected(data, additions, start, through), additions.drop(1), annual = true)
    }

    private fun affected(data: DiarySnapshot, additions: List<WeeklyTimetable>, start: String, end: String): List<String> {
        val after = data.copy(timetables = data.timetables + additions)
        return (data.lessons.map { it.classId to it.date } + data.lessonRecords.map { it.classId to it.date }).distinct()
            .filter { (id, day) -> day in start..end && planned(data,id,day).map { it.ordinal } != planned(after,id,day).map { it.ordinal } }
            .sortedWith(compareBy({it.second},{it.first})).map { (id,day) ->
                "$day • ${data.classes.firstOrNull { it.id == id }?.name ?: additions.flatMap { it.slots }.firstOrNull { it.classId == id }?.className.orEmpty()}" }
    }
    fun shiftOrder(shift:String): Int = when(shift.trim().lowercase(java.util.Locale.ROOT)) {
        "matutino" -> 0; "vespertino" -> 1; "noturno" -> 2; else -> 3
    }
    fun current(rows: List<WeeklyTimetable>, day: String): WeeklyTimetable? = rows
        .filter { it.year == LocalDate.parse(day).year && it.effective <= day }
        .maxWithOrNull(compareBy<WeeklyTimetable> { it.effective }.thenBy { it.revision })
    fun planned(data: DiarySnapshot, classId: String, day: String): List<WeeklySlot> {
        val weekday = LocalDate.parse(day).dayOfWeek.value
        return current(data.timetables, day)?.slots.orEmpty().filter { it.classId == classId && it.weekday == weekday }.sortedBy { it.ordinal }
    }
    fun validate(rows: List<WeeklyTimetable>) {
        require(rows.map { it.id }.distinct().size == rows.size) { "Revisões de horários repetidas." }
        require(rows.map { Triple(it.year,it.effective,it.revision) }.distinct().size == rows.size) { "Vigências de horários conflitantes." }
        require(rows.flatMap { it.slots }.groupBy { it.id }.values.all { versions ->
            versions.map { Triple(it.classId,it.weekday,it.ordinal) }.distinct().size == 1
        }) { "Um identificador de período pertence a configurações diferentes." }
        rows.forEach { row ->
            require(row.id.isNotBlank() && row.year in 2000..2100 && row.revision > 0 && LocalDate.parse(row.effective).year == row.year) { "Confira a vigência da grade." }
            Instant.parse(row.createdAt)
            require(row.slots.map { it.id }.distinct().size == row.slots.size) { "Horários repetidos." }
            val conflict=row.slots.groupBy { Triple(it.weekday,it.shift.trim().lowercase(java.util.Locale.ROOT),it.ordinal) }.values.firstOrNull { it.size>1 }
            require(conflict==null) {
                val slot=conflict!!.first()
                val day=java.time.DayOfWeek.of(slot.weekday).getDisplayName(java.time.format.TextStyle.FULL,java.util.Locale.forLanguageTag("pt-BR"))
                "Conflito: $day, ${slot.shift}, ${slot.ordinal}º horário. Escolha outro período."
            }
            require(row.slots.map { Triple(it.classId,it.weekday,it.ordinal) }.distinct().size == row.slots.size) { "Um período da turma está repetido." }
            require(row.slots.all { it.id.isNotBlank() && it.classId.isNotBlank() && it.weekday in 1..7 && it.ordinal in 1..12 && it.className.isNotBlank() && it.subject.isNotBlank() && it.shift.isNotBlank() }) { "Confira os períodos da grade." }
        }
    }
    fun preview(data: DiarySnapshot, year: Int, effective: String, slots: List<WeeklySlot>): TimetablePreview {
        require(LocalDate.parse(effective).year == year) { "A vigência deve estar no ano selecionado." }
        val expected = data.timetables.filter { it.year == year }.sortedBy { it.id }
        validateEditedSlots(expected,slots)
        val revision = (expected.filter { it.effective == effective }.maxOfOrNull { it.revision } ?: 0) + 1
        val proposed = WeeklyTimetable(year=year,effective=effective,revision=revision,slots=slots.sortedWith(compareBy({it.weekday},{it.ordinal})))
        validate(expected + proposed)
        // Mantém retratos históricos de turmas excluídas/arquivadas, mas não cria novos períodos nelas.
        val previous = current(expected,effective)?.slots.orEmpty()
        slots.filter { it !in previous }.forEach { slot ->
            val school = requireNotNull(data.classes.firstOrNull { it.id == slot.classId }) { "Turma não encontrada. Reabra os horários." }
            require(school.year == year && data.classArchives.none { it.classId == school.id }) { "Escolha uma turma ativa do mesmo ano." }
        }
        val next = expected.map { it.effective }.filter { it > effective }.minOrNull()
        val through = next?.let { LocalDate.parse(it).minusDays(1).toString() } ?: "$year-12-31"
        return TimetablePreview(expected,proposed,through,affected(data,listOf(proposed),effective,through))
    }
}
