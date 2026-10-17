package br.com.diariodeclasse.reports

import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.domain.SchoolRules
import java.util.Locale
import br.com.diariodeclasse.domain.RecompositionRules

enum class GradeState { OUTSIDE_ENROLLMENT, NO_ACTIVITIES, NO_SCORES, PARTIAL, COMPLETE }
enum class ActivityScoreState { NOT_APPLICABLE, PENDING, RECORDED }
enum class EnrollmentCoverage { FULL, PARTIAL, OUTSIDE, UNCONFIGURED }
enum class StudentOrder { CALL, ABSENCES, LOWEST_GRADE, HIGHEST_GRADE }

data class ActivityResult(val activity: Activity, val eligible: Boolean, val score: Double?, val enrollmentId: String? = null) {
    val state: ActivityScoreState get() = when {
        !eligible -> ActivityScoreState.NOT_APPLICABLE
        score == null -> ActivityScoreState.PENDING
        else -> ActivityScoreState.RECORDED
    }
    val applicableScore: Double? get() = score.takeIf { eligible }
}

/** Valores numéricos de leitura, sem texto formatado nem uma segunda fórmula de frequência. */
data class FrequencySummary(val performed: Int, val valid: Int, val present: Int, val absent: Int,
    val justified: Int, val effectiveAbsences: Int, val unregistered: Int, val percent: Double?) {
    companion object {
        fun from(f: SchoolRules.Frequency, countJustified: Boolean) = FrequencySummary(f.performed, f.valid,
            f.present, f.absent, f.justified, f.absent + if (countJustified) f.justified else 0, f.performed - f.valid, f.percent)
    }
}
data class StudentPeriodReport(
    val student: Student, val number: Int, val period: Term?, val activities: List<ActivityResult>,
    val grades: SchoolRules.GradeSummary, val frequency: SchoolRules.Frequency?, val state: GradeState, val recomposition: Recomposition? = null, val countJustified: Boolean = true, val enrollments: List<Student> = listOf(student), val recompositionSchool: SchoolClass? = null
) {
    val launched: Int get() = activities.count { it.eligible && it.score != null }
    val original: Double? get() = if (launched == 0) null else grades.total
    val total: Double? get() = RecompositionRules.effective(original, recomposition?.value)
    val attendance: FrequencySummary? get() = frequency?.let { FrequencySummary.from(it, countJustified) }
    val hasData: Boolean get() = activities.any { it.eligible } || (frequency?.performed ?: 0) > 0 || recomposition != null
    val enrollment: EnrollmentCoverage get() = period?.let { period ->
        val overlapping=enrollments.filter {it.joined<=period.end && (it.left==null||it.left>=period.start)}
        when {
            overlapping.isEmpty() -> EnrollmentCoverage.OUTSIDE
            overlapping.minOf {it.joined} > period.start || overlapping.all {it.left!=null&&it.left<period.end} -> EnrollmentCoverage.PARTIAL
            else -> EnrollmentCoverage.FULL
        }
    } ?: EnrollmentCoverage.UNCONFIGURED
    val resultScale: Double? get() = if (recomposition?.value != null) 10.0 else null
    fun effectiveAbsences(countJustified: Boolean): Int = frequency?.let { FrequencySummary.from(it, countJustified).effectiveAbsences } ?: 0
}
data class ClassPeriodReport(
    val period: Term?, val activities: List<Activity>, val students: List<StudentPeriodReport>,
    val frequency: SchoolRules.Frequency?, val performedLessons: Int, val countJustified: Boolean = true
)
data class StudentYearReport(val student: Student, val terms: List<StudentPeriodReport>, val frequency: SchoolRules.Frequency,
    val countJustified: Boolean = true) {
    val attendance: FrequencySummary get() = FrequencySummary.from(frequency, countJustified)
}
data class ClassYearReport(val school: SchoolClass, val students: List<StudentYearReport>,
    val frequency: FrequencySummary, val performedLessons: Int)

data class AttendanceHistory(val enrollment: Student, val school: SchoolClass, val lesson: Lesson, val mark: String?, val eligible: Boolean)

/** Projeções somente leitura. Notas/frequência continuam delegadas ao DiarySnapshot/SchoolRules. */
class SchoolReports(private val data: DiarySnapshot, val school: SchoolClass, showRelocated: Boolean = false) {
    private val yearStart = "${school.year}-01-01"
    private val yearEnd = "${school.year}-12-31"
    private val scoped = data.copy(lessons = data.lessons.filter { it.date in yearStart..yearEnd })
    private val roster = data.currentRoster(school.id, showRelocated).sortedWith(compareBy<Student> { it.number }.thenBy { it.name.lowercase(Locale.ROOT) }.thenBy { it.id })
    private val gradeIndex = data.grades.associateBy { it.activityId to it.studentId }
    private fun activities(number: Int) = data.activities.filter { a -> a.term == number && (a.classId == school.id || roster.any {data.activityOwner(it,a)!=null}) }
        .sortedWith(compareBy<Activity> { it.date }.thenBy { it.name }.thenBy { it.id })
    fun student(student: Student, number: Int): StudentPeriodReport {
        require(student.classId == school.id && number in 1..4)
        val period = data.terms.firstOrNull { it.classId == school.id && it.number == number }
        val chain=data.lineage(student)
        val columns=(activities(number)+data.activities.filter {it.term==number&&data.activityOwner(student,it)!=null}).distinctBy {it.id}.sortedWith(compareBy<Activity> {it.date}.thenBy {it.name}.thenBy {it.id})
        val rows = columns.map { a -> val owner=data.activityOwner(student,a); ActivityResult(a, owner!=null, gradeIndex[a.id to (owner?.id ?: student.id)]?.value,owner?.id) }
        val eligible = rows.filter { it.eligible }
        val grades = data.result(student, number, school.scale)
        val overlap = period == null || chain.any {it.joined <= period.end && (it.left == null || it.left >= period.start)}
        val state = when {
            !overlap || rows.isNotEmpty() && eligible.isEmpty() -> GradeState.OUTSIDE_ENROLLMENT
            eligible.isEmpty() -> GradeState.NO_ACTIVITIES
            eligible.none { it.score != null } -> GradeState.NO_SCORES
            grades.partial -> GradeState.PARTIAL
            else -> GradeState.COMPLETE
        }
        // null é usado pelo cálculo legado para o ano inteiro; nunca como substituto de bimestre ausente.
        val frequency = period?.let { scoped.frequency(student, it, school.countJustified) }
        return StudentPeriodReport(student, number, period, rows, grades, frequency, state, data.periodRecomposition(student,number), school.countJustified, chain, data.periodRecomposition(student,number)?.let {r->data.students.firstOrNull {it.id==r.studentId}?.let {owner->data.classes.firstOrNull {it.id==owner.classId}}})
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
        return ClassPeriodReport(period, activities(number), rows, frequency, performed, school.countJustified)
    }
    fun year(student: Student): StudentYearReport {
        require(student.classId == school.id)
        return StudentYearReport(student, (1..4).map { this.student(student, it) }, scoped.frequency(student, null, school.countJustified), school.countJustified)
    }
    fun annual() = roster.map(::year)
    fun year(): ClassYearReport {
        val rows = annual()
        val aggregate = SchoolRules.aggregateFrequency(rows.map { it.frequency }, school.countJustified)
        val performed = SchoolRules.frequency(yearStart, yearEnd, scoped.lessons.filter { it.classId == school.id }
            .map { SchoolRules.AttendanceEntry(it.date, it.state, null) }, school.countJustified).performed
        return ClassYearReport(school, rows, FrequencySummary.from(aggregate, school.countJustified), performed)
    }
    fun attendanceHistory(student: Student): List<AttendanceHistory> = data.lineage(student).flatMap { enrollment ->
        val school=data.classes.first {it.id==enrollment.classId}
        val marks=data.marks.filter {it.studentId==enrollment.id}.associateBy {it.lessonId}
        scoped.lessons.filter {it.classId==enrollment.classId && (enrollment.enrolled(it.date)||it.id in marks)}.map {lesson ->
            AttendanceHistory(enrollment,school,lesson,marks[lesson.id]?.mark,enrollment.enrolled(lesson.date))
        }
    }.sortedWith(compareBy<AttendanceHistory> {it.lesson.date}.thenBy {it.lesson.ordinal}.thenBy {it.school.id}.thenBy {it.lesson.id})
    fun mostAbsent(number: Int): List<StudentPeriodReport> = rank(term(number).students, school.countJustified)

    companion object {
        private val callOrder = compareBy<StudentPeriodReport> { it.student.number }
            .thenBy { it.student.name.lowercase(Locale.ROOT) }.thenBy { it.student.id }
        fun ordered(rows: List<StudentPeriodReport>, order: StudentOrder, countJustified: Boolean): List<StudentPeriodReport> {
            val comparator = when (order) {
                StudentOrder.CALL -> callOrder
                StudentOrder.ABSENCES -> compareByDescending<StudentPeriodReport> { it.effectiveAbsences(countJustified) }.then(callOrder)
                StudentOrder.LOWEST_GRADE -> compareBy<StudentPeriodReport> { it.total == null }.thenBy { it.total }.then(callOrder)
                StudentOrder.HIGHEST_GRADE -> compareBy<StudentPeriodReport> { it.total == null }.thenByDescending { it.total }.then(callOrder)
            }
            return rows.sortedWith(comparator)
        }
        fun rank(rows: List<StudentPeriodReport>, countJustified: Boolean) =
            ordered(rows.filter { it.effectiveAbsences(countJustified) > 0 }, StudentOrder.ABSENCES, countJustified)
    }
}
