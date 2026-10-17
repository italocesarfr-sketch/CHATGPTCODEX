package br.com.diariodeclasse.data

import androidx.room.withTransaction
import androidx.room.invalidationTrackerFlow
import br.com.diariodeclasse.domain.RosterParser
import br.com.diariodeclasse.domain.CsvRoster
import br.com.diariodeclasse.domain.SchoolRules
import br.com.diariodeclasse.domain.GradeScaling
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import java.text.Collator
import java.time.LocalDate
import java.util.Locale

private fun date(value: String) = LocalDate.parse(value)
fun Student.enrolled(day: String) = day >= joined && (left == null || day <= left)

data class DiarySnapshot(
    val classes: List<SchoolClass> = emptyList(), val students: List<Student> = emptyList(),
    val terms: List<Term> = emptyList(), val lessons: List<Lesson> = emptyList(),
    val marks: List<Attendance> = emptyList(), val activities: List<Activity> = emptyList(),
    val grades: List<Grade> = emptyList(), val drafts: List<ImportDraft> = emptyList(), val loaded: Boolean = false, val recompositions: List<Recomposition> = emptyList()
) {
    fun roster(classId: String) = students.filter { it.classId == classId }
    fun frequency(student: Student, term: Term?, countJustified: Boolean): SchoolRules.Frequency {
        val byLesson = marks.filter { it.studentId == student.id }.associateBy { it.lessonId }
        return SchoolRules.frequency(student.joined, student.left,
            lessons.filter { it.classId == student.classId && (term == null || it.date in term.start..term.end) }
                .map { SchoolRules.AttendanceEntry(it.date, it.state, byLesson[it.id]?.mark) }, countJustified)
    }
    fun result(student: Student, term: Int, scale: Double): SchoolRules.GradeSummary {
        val list = activities.filter { it.classId == student.classId && it.term == term && student.enrolled(it.date) }
        val byActivity = grades.filter { it.studentId == student.id }.associateBy { it.activityId }
        return SchoolRules.grades(list.map { byActivity[it.id]?.value }, list.map { it.maximum }, scale)
    }
}

class DiaryRepository(val db: DiaryDatabase) {
    val dao = db.dao()
    // Cada emissão lê as nove tabelas no mesmo snapshot SQLite confirmado.
    // Evita combinar, por exemplo, máximo novo de atividade com notas ainda antigas.
    val snapshot = db.invalidationTrackerFlow("classes", "students", "terms", "lessons", "marks",
        "activities", "grades", "import_drafts", "recompositions").map {
        db.withTransaction {
            val read = db.backupDao()
            DiarySnapshot(
                classes = read.classes().sortedWith(compareByDescending<SchoolClass> { it.year }.thenBy { it.name }.thenBy { it.id }),
                students = read.students().sortedWith(compareBy<Student> { it.number }.thenBy { it.name }.thenBy { it.id }),
                terms = read.terms().sortedWith(compareBy<Term> { it.number }.thenBy { it.classId }),
                lessons = read.lessons().sortedWith(compareBy<Lesson> { it.date }.thenBy { it.ordinal }.thenBy { it.id }),
                marks = read.marks(),
                activities = read.activities().sortedWith(compareBy<Activity> { it.date }.thenBy { it.name }.thenBy { it.id }),
                grades = read.grades(), drafts = read.drafts().sortedWith(compareBy<ImportDraft> { it.position }.thenBy { it.id }),
                recompositions = read.recompositions(), loaded = true)
        }
    }.distinctUntilChanged()
    suspend fun saveClass(value: SchoolClass, effective: String) = db.withTransaction {
        require(value.name.isNotBlank() && value.subject.isNotBlank()) { "Informe turma e disciplina." }
        require(value.year in 2000..2100 && value.duration in 1..240) { "Ano ou duração inválidos." }
        require(value.scale.isFinite() && value.scale > 0 && value.low >= 0 && value.low < value.high && value.high <= value.scale) { "Revise a escala e as faixas de nota." }
        val counts = value.schedule.split(',').map { it.toInt() }
        require(counts.size == 7 && counts.all { it in 0..12 }) { "Use de 0 a 12 aulas por dia." }
        val previous = dao.schoolClass(value.id)
        require(previous == null || previous.year == value.year) { "Crie outra turma para mudar o ano e preservar o histórico." }
        require(date(effective).year == value.year) { "A vigência deve estar no ano letivo." }
        dao.save(value)
        if (previous == null) {
            for (n in 1..4) {
                val start = LocalDate.of(value.year, (n - 1) * 3 + 1, 1)
                dao.save(Term(value.id, n, start.toString(), start.plusMonths(3).minusDays(1).toString()))
            }
        }
        // Horário semanal é somente legado. Nunca gerar/remover aulas automaticamente.
    }
    suspend fun saveTerms(classId: String, periods: List<Term>) = db.withTransaction {
        val c = requireNotNull(dao.schoolClass(classId))
        validateTerms(c, periods)
        val sorted = periods.sortedBy { it.number }
        sorted.forEach { dao.save(it) }
    }
    fun validateTerms(c: SchoolClass, periods: List<Term>) {
        require(periods.size == 4 && periods.map { it.number }.toSet() == setOf(1, 2, 3, 4)) { "Informe os quatro bimestres." }
        val sorted = periods.sortedBy { it.number }
        sorted.forEach {
            require(it.classId == c.id && date(it.start).year == c.year && date(it.end).year == c.year && it.start <= it.end) { "Datas inválidas para o ano letivo." }
        }
        sorted.zipWithNext().forEach { (a, b) -> require(a.end < b.start) { "Bimestres não podem se sobrepor." } }
    }
    suspend fun saveStudent(value: Student) = db.withTransaction {
        require(value.name.trim().length >= 2 && value.number > 0) { "Informe nome e número válido." }
        date(value.joined); value.left?.let { require(date(it) >= date(value.joined)) { "Saída anterior ao ingresso." } }
        require(value.status == "Ativo" || value.left != null) { "Informe a data de saída." }
        require(dao.roster(value.classId).none { it.id != value.id && it.number == value.number }) { "Número já utilizado nesta turma." }
        dao.save(value.copy(name = value.name.trim()))
    }
    suspend fun addNames(classId: String, names: List<String>, joined: String) = db.withTransaction {
        date(joined)
        val roster = dao.roster(classId)
        val keys = roster.map { RosterParser.key(it.name) }.toMutableSet()
        var number = roster.maxOfOrNull { it.number } ?: 0
        val collator = Collator.getInstance(Locale.forLanguageTag("pt-BR"))
        names.map { it.trim() }.filter { it.length >= 2 }.sortedWith { a, b -> collator.compare(a, b) }.forEach {
            if (keys.add(RosterParser.key(it))) dao.save(Student(classId = classId, name = it, number = ++number, joined = joined))
        }
    }
    suspend fun moveStudent(studentId: String, direction: Int) = db.withTransaction {
        val student = requireNotNull(dao.student(studentId))
        val roster = dao.roster(student.classId)
        val index = roster.indexOfFirst { it.id == studentId }
        val target = roster.getOrNull(index + direction) ?: return@withTransaction
        dao.save(student.copy(number = target.number))
        dao.save(target.copy(number = student.number))
    }
    suspend fun transfer(studentId: String, targetId: String, day: String) = db.withTransaction {
        val student = requireNotNull(dao.student(studentId)); val target = requireNotNull(dao.schoolClass(targetId))
        require(student.classId != target.id && student.status == "Ativo" && day > student.joined) { "Transferência inválida." }
        require(date(day).year == target.year) { "A data deve estar no ano da turma de destino." }
        val roster = dao.roster(targetId)
        require(roster.none { it.personId == student.personId || RosterParser.key(it.name) == RosterParser.key(student.name) }) { "Possível duplicidade no destino." }
        dao.save(student.copy(status = "Transferido", left = date(day).minusDays(1).toString()))
        dao.save(student.copy(id = newId(), classId = targetId, number = (roster.maxOfOrNull { it.number } ?: 0) + 1,
            joined = day, left = null, status = "Ativo"))
    }
    suspend fun lessonState(id: String, state: String, note: String? = null) = db.withTransaction {
        require(state in listOf("PREVISTA", "REALIZADA", "CANCELADA"))
        val lesson = requireNotNull(dao.lesson(id))
        dao.save(lesson.copy(state = state, note = note ?: lesson.note))
    }
    suspend fun extraLesson(classId: String, day: String, note: String) = db.withTransaction {
        val c = requireNotNull(dao.schoolClass(classId)); require(date(day).year == c.year)
        val n = (dao.day(classId, day).maxOfOrNull { it.ordinal } ?: 0) + 1
        dao.save(Lesson("$classId:$day:$n", classId, day, n, note = note.ifBlank { "Aula extraordinária" }))
    }
    /** Única ação de quantidade: reutiliza IDs por ordinal; nunca exclui aulas ou marcas. */
    suspend fun setDayHours(classId: String, day: String, count: Int, confirmCancellation: Boolean = false) = db.withTransaction {
        val c = requireNotNull(dao.schoolClass(classId)) { "Turma não encontrada." }
        require(date(day).year == c.year) { "Escolha uma data do ano letivo da turma." }
        require(count in 0..12) { "Informe de 0 a 12 horários." }
        val old = dao.day(classId, day)
        val excess = old.filter { it.ordinal > count && it.state != "CANCELADA" }
        require(excess.isEmpty() || confirmCancellation) { "Confirme o cancelamento dos horários excedentes. Nenhum registro será apagado." }
        excess.forEach { dao.save(it.copy(state = "CANCELADA")) }
        for (n in 1..count) {
            val previous = old.firstOrNull { it.ordinal == n }
            // Canceladas só voltam a contar mediante reativação explícita por horário.
            if (previous?.state == "CANCELADA") continue
            val lesson = previous ?: Lesson("$classId:$day:$n", classId, day, n)
            dao.save(lesson.copy(state = "REALIZADA"))
            if (c.startPresent && dao.attendance(lesson.id).isEmpty()) {
                dao.roster(classId).filter { it.enrolled(day) }.forEach { dao.save(Attendance(lesson.id, it.id, "P")) }
            }
        }
    }
    suspend fun allPresentDay(classId: String, day: String) = db.withTransaction {
        val lessons = dao.day(classId, day).filter { it.state == "REALIZADA" }
        require(lessons.isNotEmpty()) { "Registre os horários ministrados antes de marcar presença." }
        lessons.forEach { allPresent(it.id) }
    }
    suspend fun mark(lessonId: String, studentId: String, mark: String) = db.withTransaction {
        require(mark in listOf("P", "F", "FJ", "NR"))
        val lesson = requireNotNull(dao.lesson(lessonId)); val student = requireNotNull(dao.student(studentId))
        require(lesson.state == "REALIZADA" && student.classId == lesson.classId && student.enrolled(lesson.date)) { "Aula ou matrícula não permite chamada." }
        dao.save(Attendance(lessonId, studentId, mark))
    }
    suspend fun startLesson(id: String) = db.withTransaction {
        val lesson = requireNotNull(dao.lesson(id)); require(lesson.state != "CANCELADA") { "Reative a aula antes de registrar." }
        val c = requireNotNull(dao.schoolClass(lesson.classId)); dao.save(lesson.copy(state = "REALIZADA"))
        if (c.startPresent && dao.attendance(id).isEmpty()) {
            dao.roster(c.id).filter { it.enrolled(lesson.date) }.forEach { dao.save(Attendance(id, it.id, "P")) }
        }
    }
    suspend fun allPresent(id: String) = db.withTransaction {
        val lesson = requireNotNull(dao.lesson(id)); require(lesson.state == "REALIZADA")
        dao.roster(lesson.classId).filter { it.enrolled(lesson.date) }.forEach { dao.save(Attendance(id, it.id, "P")) }
    }
    suspend fun copyFirst(id: String) = db.withTransaction {
        val target = requireNotNull(dao.lesson(id)); require(target.state == "REALIZADA")
        val first = dao.day(target.classId, target.date).firstOrNull { it.ordinal < target.ordinal && it.state == "REALIZADA" }
        requireNotNull(first) { "Nenhuma chamada anterior realizada neste dia." }
        val eligible = dao.roster(target.classId).filter { it.enrolled(target.date) }.map { it.id }.toSet()
        val source = dao.attendance(first.id).associateBy { it.studentId }
        eligible.forEach { studentId -> dao.save(Attendance(target.id, studentId, source[studentId]?.mark ?: "NR")) }
    }
    private suspend fun validateActivity(value: Activity) {
        require(value.name.isNotBlank() && value.maximum.isFinite() && value.maximum > 0 && value.term in 1..4) { "Informe nome, bimestre e valor válidos." }
        val period = dao.periods(value.classId).first { it.number == value.term }
        date(value.date); require(value.date in period.start..period.end) { "A data deve estar dentro do bimestre selecionado." }
    }
    suspend fun activitySnapshot(id: String): ActivitySnapshot = db.withTransaction {
        ActivitySnapshot(requireNotNull(dao.activity(id)) { "Atividade não encontrada." }, dao.activityGrades(id).sortedBy { it.studentId })
    }
    private suspend fun checkActivitySnapshot(expected: ActivitySnapshot) {
        require(activitySnapshot(expected.activity.id) == expected) { "A atividade ou suas notas mudaram. Reabra ou atualize a prévia antes de confirmar." }
    }
    suspend fun previewActivityEdit(proposed: Activity, original: Activity? = null): ActivityEditPreview = db.withTransaction {
        val before = activitySnapshot(proposed.id)
        require(original == null || before.activity == original) { "A atividade mudou. Feche e reabra o formulário." }
        require(proposed.classId == before.activity.classId) { "A atividade deve permanecer na mesma turma." }
        validateActivity(proposed)
        ActivityEditPreview(before, proposed)
    }
    suspend fun applyActivityEdit(preview: ActivityEditPreview, mode: GradeChangeMode) = db.withTransaction {
        checkActivitySnapshot(preview.before)
        require(preview.proposed.id == preview.before.activity.id && preview.proposed.classId == preview.before.activity.classId)
        validateActivity(preview.proposed)
        require(mode != GradeChangeMode.KEEP || preview.canKeep) { "Há notas acima do novo máximo. Recalcule proporcionalmente ou cancele." }
        val converted = if (mode == GradeChangeMode.PROPORTIONAL && preview.maximumChanged) preview.before.grades.map {
            it.copy(value = GradeScaling.proportional(it.value, preview.before.activity.maximum, preview.proposed.maximum))
        } else emptyList()
        dao.save(preview.proposed)
        converted.forEach { dao.save(it) }
    }
    suspend fun deleteActivity(expected: ActivitySnapshot) = db.withTransaction {
        checkActivitySnapshot(expected)
        // A FK existente remove as notas na mesma transação, mantendo as outras atividades.
        dao.deleteActivity(expected.activity.id)
    }
    suspend fun saveActivity(value: Activity) = db.withTransaction {
        validateActivity(value)
        val previous = dao.activity(value.id)
        require(previous == null || previous.classId == value.classId)
        require(previous == null || previous.maximum == value.maximum || dao.activityGrades(value.id).none { it.value != null }) { "Confirme a alteração do máximo pela prévia de recálculo." }
        dao.save(value)
    }
    suspend fun saveGrade(activityId: String, studentId: String, value: Double?) = db.withTransaction {
        val activity = requireNotNull(dao.activity(activityId)); val student = requireNotNull(dao.student(studentId))
        require(student.classId == activity.classId && student.enrolled(activity.date)) { "Aluno fora do período de matrícula." }
        require(value == null || value.isFinite() && value in 0.0..activity.maximum) { "Nota deve estar entre 0 e ${activity.maximum}." }
        dao.save(Grade(activityId, studentId, value))
    }
    suspend fun saveRecomposition(value: Recomposition, expected: Recomposition? = null) = db.withTransaction {
        val student = requireNotNull(dao.student(value.studentId)) { "Matrícula não encontrada." }
        require(value.term in 1..4 && dao.periods(student.classId).any { it.number == value.term }) { "Selecione o bimestre de origem." }
        require(date(value.date).toString() == value.date) { "Data inválida." }
        require(value.value == null || value.value.isFinite() && value.value in 0.0..10.0) { "Recomposição deve estar entre 0 e 10." }
        require(dao.recomposition(value.studentId, value.term) == expected) { "A recomposição mudou. Reabra o lançamento antes de confirmar." }
        dao.save(value)
    }
    suspend fun deleteRecomposition(expected: Recomposition) = db.withTransaction {
        require(dao.recomposition(expected.studentId, expected.term) == expected) { "A recomposição mudou. Reabra o lançamento." }
        dao.deleteRecomposition(expected.studentId, expected.term)
    }
    suspend fun appendDrafts(classId: String, candidates: List<CsvRoster.Row>) = db.withTransaction {
        requireNotNull(dao.schoolClass(classId))
        require(candidates.isNotEmpty()) { "Nenhum nome encontrado. Confira o arquivo ou a lista." }
        val old = dao.draftRows(classId)
        val page = (old.maxOfOrNull { it.page } ?: 0) + 1
        val position = (old.maxOfOrNull { it.position } ?: -1) + 1
        candidates.forEachIndexed { index, candidate -> dao.save(ImportDraft(classId = classId, name = candidate.name,
            number = candidate.number, position = position + index, page = page, reason = "Importação textual — confira nome e número")) }
    }
    suspend fun confirmImport(classId: String, joined: String): Int = db.withTransaction {
        val klass = requireNotNull(dao.schoolClass(classId))
        require(date(joined).year == klass.year) { "A data de ingresso deve pertencer ao ano da turma." }
        val rows = dao.draftRows(classId); require(rows.isNotEmpty()) { "Nenhum aluno para importar." }
        require(rows.all { it.reviewed && it.name.trim().length >= 2 }) { "Confira e marque cada nome antes de confirmar." }
        val roster = dao.roster(classId)
        val names = roster.map { RosterParser.key(it.name) }.toMutableSet()
        val numbers = roster.map { it.number }.toMutableSet()
        var next = maxOf(numbers.maxOrNull() ?: 0, rows.mapNotNull { it.number }.maxOrNull() ?: 0) + 1
        rows.forEach { row ->
            val key = RosterParser.key(row.name)
            require(key.isNotEmpty() && (!names.any { RosterParser.possibleDuplicate(it, key) } || row.allowHomonym)) { "Possível duplicidade: corrija, exclua ou confirme homônimo." }
            names.add(key)
            val number = row.number ?: run { while (next in numbers) next++; next++ }
            require(number > 0 && numbers.add(number)) { "Há números de chamada repetidos. Corrija-os ou deixe em branco." }
            dao.save(Student(classId = classId, name = row.name.trim(), number = number, joined = joined))
        }
        dao.clearDraft(classId)
        rows.size
    }
}
