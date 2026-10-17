package br.com.diariodeclasse.reports

import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.domain.SchoolRules
import java.util.Locale

enum class GradeState { OUTSIDE_ENROLLMENT, NO_ACTIVITIES, NO_SCORES, PARTIAL, COMPLETE }
data class ActivityResult(val activity: Activity, val eligible: Boolean, val score: Double?)
data class StudentPeriodReport(
    val student: Student, val number: Int, val period: Term?, val activities: List<ActivityResult>,
    val grades: SchoolRules.GradeSummary, val frequency: SchoolRules.Frequency?, val state: GradeState
) {
    val launched: Int get() = activities.count { it.eligible && it.score != null }
    val total: Double? get() = if (launched == 0) null else grades.total
    fun effectiveAbsences(countJustified: Boolean): Int = frequency?.let { it.absent + if (countJustified) it.justified else 0 } ?: 0
}
data class ClassPeriodReport(
    val period: Term?, val activities: List<Activity>, val students: List<StudentPeriodReport>,
    val frequency: SchoolRules.Frequency?, val performedLessons: Int
)
data class StudentYearReport(val student: Student, val terms: List<StudentPeriodReport>, val frequency: SchoolRules.Frequency)

/** Projeções somente leitura. Notas/frequência continuam delegadas ao DiarySnapshot/SchoolRules. */
class SchoolReports(private val data: DiarySnapshot, val school: SchoolClass) {
    private val yearStart = "${school.year}-01-01"
    private val yearEnd = "${school.year}-12-31"
    private val scoped = data.copy(lessons = data.lessons.filter { it.date in yearStart..yearEnd })
    private val roster = data.roster(school.id).sortedWith(compareBy<Student> { it.number }.thenBy { it.name }.thenBy { it.id })
    private val gradeIndex = data.grades.associateBy { it.activityId to it.studentId }
    private fun activities(number: Int) = data.activities.filter { it.classId == school.id && it.term == number }
        .sortedWith(compareBy<Activity> { it.date }.thenBy { it.name }.thenBy { it.id })
    fun student(student: Student, number: Int): StudentPeriodReport {
        require(student.classId == school.id && number in 1..4)
        val period = data.terms.firstOrNull { it.classId == school.id && it.number == number }
        val rows = activities(number).map { ActivityResult(it, student.enrolled(it.date), gradeIndex[it.id to student.id]?.value) }
        val eligible = rows.filter { it.eligible }
        val grades = data.result(student, number, school.scale)
        val overlap = period == null || student.joined <= period.end && (student.left == null || student.left >= period.start)
        val state = when {
            !overlap || rows.isNotEmpty() && eligible.isEmpty() -> GradeState.OUTSIDE_ENROLLMENT
            eligible.isEmpty() -> GradeState.NO_ACTIVITIES
            eligible.none { it.score != null } -> GradeState.NO_SCORES
            grades.partial -> GradeState.PARTIAL
            else -> GradeState.COMPLETE
        }
        // null é usado pelo cálculo legado para o ano inteiro; nunca como substituto de bimestre ausente.
        val frequency = period?.let { scoped.frequency(student, it, school.countJustified) }
        return StudentPeriodReport(student, number, period, rows, grades, frequency, state)
    }
    fun term(number: Int): ClassPeriodReport {
        require(number in 1..4)
        val rows = roster.map { student(it, number) }
        val period = data.terms.firstOrNull { it.classId == school.id && it.number == number }
        val frequency = period?.let { SchoolRules.aggregateFrequency(rows.mapNotNull { it.frequency }, school.countJustified) }
        val performed = period?.let {
            SchoolRules.frequency(yearStart, yearEnd, scoped.lessons.filter { l -> l.classId == school.id && l.date in it.start..it.end }
                .map { l -> SchoolRules.AttendanceEntry(l.date, l.state, null) }, school.countJustified).performed
        } ?: 0
        return ClassPeriodReport(period, activities(number), rows, frequency, performed)
    }
    fun year(student: Student): StudentYearReport {
        require(student.classId == school.id)
        return StudentYearReport(student, (1..4).map { this.student(student, it) }, scoped.frequency(student, null, school.countJustified))
    }
    fun annual() = roster.map(::year)
    fun mostAbsent(number: Int): List<StudentPeriodReport> = rank(term(number).students, school.countJustified)

    companion object {
        fun rank(rows: List<StudentPeriodReport>, countJustified: Boolean) = rows.filter { it.effectiveAbsences(countJustified) > 0 }
            .sortedWith(compareByDescending<StudentPeriodReport> { it.effectiveAbsences(countJustified) }
                .thenBy { it.student.number }.thenBy { it.student.name.lowercase(Locale.ROOT) }.thenBy { it.student.id })
    }
}
