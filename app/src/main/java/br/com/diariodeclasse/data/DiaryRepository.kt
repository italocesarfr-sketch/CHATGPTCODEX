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
    val grades: List<Grade> = emptyList(), val drafts: List<ImportDraft> = emptyList(), val loaded: Boolean = false, val recompositions: List<Recomposition> = emptyList(), val relocations: List<Relocation> = emptyList(), val exportRows: List<ExportRow> = emptyList(), val classArchives: List<ClassArchive> = emptyList(), val lessonRecords: List<LessonRecord> = emptyList(), val histories: List<PreservedHistory> = emptyList()
) {
    private val academicData by lazy { PreservedHistories.merge(this) }
    fun academic(): DiarySnapshot = academicData
    fun schoolName(id: String): String = academic().classes.firstOrNull {it.id==id}?.name ?: id
    fun roster(classId: String) = students.filter { it.classId == classId }
    fun currentRoster(classId: String, showRelocated: Boolean = false) = roster(classId).filter { showRelocated || it.status != "Remanejado" }
    /** Somente antecessores explicitamente vinculados; personId sozinho nunca infere um vínculo. */
    fun lineage(student: Student): List<Student> {
        if(histories.isNotEmpty()) return academic().lineage(student)
        val result = mutableListOf<Student>(); val seen = mutableSetOf<String>(); var current: Student? = student
        while (current != null) {
            require(seen.add(current.id)) { "Ciclo no histórico de remanejamento." }
            result.add(current)
            val source = relocations.firstOrNull { it.targetStudentId == current!!.id }?.sourceStudentId
            current = source?.let { id -> requireNotNull(students.firstOrNull { it.id == id }) {"Origem do histórico não encontrada."} }
        }
        return result.reversed()
    }
    fun activityOwner(student: Student, activity: Activity): Student? = lineage(student).firstOrNull { it.classId == activity.classId && it.enrolled(activity.date) }
    fun periodRecomposition(student: Student, term: Int): Recomposition? {
        if(histories.isNotEmpty()) return academic().periodRecomposition(student,term)
        val ids = lineage(student).map { it.id }.toSet()
        val rows = recompositions.filter { it.studentId in ids && it.term == term }
        require(rows.size <= 1) { "Há duas recomposições para o mesmo período. Não é possível consolidar." }
        return rows.singleOrNull()
    }
    fun frequency(student: Student, term: Term?, countJustified: Boolean): SchoolRules.Frequency {
        if(histories.isNotEmpty()) return academic().frequency(student,term,countJustified)
        val parts = lineage(student).map { enrollment ->
            val byLesson = marks.filter { it.studentId == enrollment.id }.associateBy { it.lessonId }
            val period = term?.let { t -> terms.firstOrNull { it.classId == enrollment.classId && it.number == t.number } ?: t }
            SchoolRules.frequency(enrollment.joined, enrollment.left,
                lessons.filter { it.classId == enrollment.classId && (period == null || it.date in period.start..period.end) }
                    .map { SchoolRules.AttendanceEntry(it.date, it.state, byLesson[it.id]?.mark) }, countJustified)
        }
        return SchoolRules.aggregateFrequency(parts, countJustified)
    }
    fun result(student: Student, term: Int, scale: Double): SchoolRules.GradeSummary {
        if(histories.isNotEmpty()) return academic().result(student,term,scale)
        val list = activities.filter { it.term == term && activityOwner(student, it) != null }
        val byActivity = grades.associateBy { it.activityId to it.studentId }
        return SchoolRules.grades(list.map { byActivity[it.id to activityOwner(student, it)!!.id]?.value }, list.map { it.maximum }, scale)
    }

}

class DiaryRepository(val db: DiaryDatabase) {
    val dao = db.dao()
    // Cada emissão lê as quatorze tabelas no mesmo snapshot SQLite confirmado.
    // Evita combinar, por exemplo, máximo novo de atividade com notas ainda antigas.
    val snapshot = db.invalidationTrackerFlow("classes", "students", "terms", "lessons", "marks",
        "activities", "grades", "import_drafts", "recompositions", "relocations", "export_rows", "class_archives", "lesson_records", "preserved_histories").map {
        readSnapshot()
    }.distinctUntilChanged()
    internal suspend fun readSnapshot(): DiarySnapshot = db.withTransaction {
            val read = db.backupDao()
            DiarySnapshot(
                classes = read.classes().sortedWith(compareByDescending<SchoolClass> { it.year }.thenBy { it.name }.thenBy { it.id }),
                students = read.students().sortedWith(compareBy<Student> { it.number }.thenBy { it.name }.thenBy { it.id }),
                terms = read.terms().sortedWith(compareBy<Term> { it.number }.thenBy { it.classId }),
                lessons = read.lessons().sortedWith(compareBy<Lesson> { it.date }.thenBy { it.ordinal }.thenBy { it.id }),
                marks = read.marks(),
                activities = read.activities().sortedWith(compareBy<Activity> { it.date }.thenBy { it.name }.thenBy { it.id }),
                grades = read.grades(), drafts = read.drafts().sortedWith(compareBy<ImportDraft> { it.position }.thenBy { it.id }),
                recompositions = read.recompositions(), relocations = read.relocations(), exportRows = read.exportRows(), classArchives = read.classArchives(), lessonRecords = read.lessonRecords(), histories = read.histories(), loaded = true)
    }

    /** Comparação otimista impede sobrescrever/excluir uma edição confirmada por outra tela. */
    suspend fun saveLessonRecord(value: LessonRecord, expected: LessonRecord? = null) = db.withTransaction {
        LessonRecords.validate(value)
        requireNotNull(dao.schoolClass(value.classId)) { "Turma não encontrada." }
        require(dao.lessonRecord(value.id) == expected) { "O registro mudou ou foi excluído. Reabra para conferir; nada foi sobrescrito." }
        require(expected == null || expected.id == value.id) { "A identidade do registro deve ser preservada." }
        if (expected == null) dao.insertRecord(value) else check(dao.updateRecord(value) == 1)
    }
    suspend fun deleteLessonRecord(expected: LessonRecord) = db.withTransaction {
        require(dao.lessonRecord(expected.id) == expected) { "O registro mudou ou foi excluído. Confira novamente antes de excluir." }
        check(dao.removeRecord(expected.id) == 1)
    }

    /** Substitui apenas a disposição desta turma; dados escolares e IDs das linhas são preservados. */
    suspend fun saveExportLayout(classId: String, expected: List<ExportRow>, requested: List<ExportRow>) = db.withTransaction {
        val data = readSnapshot()
        require(data.classes.any { it.id == classId }) { "Turma não encontrada." }
        require(dao.exportRows(classId) == expected.sortedBy { it.position }) { "A disposição mudou após abrir a edição. Cancele e reabra para conferir; nada foi sobrescrito." }
        require(requested.all { it.classId == classId }) { "Há linhas de outra turma na disposição." }
        ExportLayout.validate(data.classes, data.students, requested)
        // Matrículas cadastradas durante a edição entram somente no final.
        val known = requested.mapNotNull { it.studentId }.toSet()
        val appended = ExportLayout.rows(data, classId).filter { it.studentId != null && it.studentId !in known }
        val rows = ExportLayout.normalize(requested + appended)
        ExportLayout.validate(data.classes, data.students, rows)
        dao.removeExportRows(classId)
        dao.insertExportRows(rows)
    }

    suspend fun archiveClass(classId: String, archived: Boolean) = db.withTransaction {
        requireNotNull(dao.schoolClass(classId)) { "Turma não encontrada." }
        if (archived) {
            if (db.backupDao().classArchives().none { it.classId == classId }) dao.insertArchive(ClassArchive(classId))
        } else dao.removeArchive(classId)
    }
    suspend fun previewClassDeletion(classId: String): ClassDeletionPreview = ClassLifecycle.preview(readSnapshot(), classId)
    suspend fun deleteClass(preview: ClassDeletionPreview, confirmation: String) = db.withTransaction {
        val before = readSnapshot()
        val current = ClassLifecycle.preview(before, preview.school.id)
        require(current.fingerprint == preview.fingerprint) { "Os dados mudaram. Confira novamente a prévia; nada foi excluído." }
        require(current.blockedReason == null) { requireNotNull(current.blockedReason) }
        require(confirmation == current.confirmation) { "Digite exatamente a identificação da turma para confirmar." }
        val expected=PreservedHistories.afterDeletion(before,current.school.id)
        br.com.diariodeclasse.backup.BackupCodec.validate(ClassLifecycle.snapshot(expected))
        // Histórico e exclusão compartilham a transação: falhar qualquer etapa desfaz tudo.
        val retained=expected.histories.associateBy {it.classId}
        before.histories.filter {it.classId !in retained}.forEach {dao.removeHistory(it.classId)}
        dao.saveHistories(expected.histories.filter {it !in before.histories})
        val sql = db.openHelper.writableDatabase
        val id = current.school.id
        // Sem cascata de turmas; só registros exclusivos validados, com FKs ativas.
        for (query in listOf(
            "DELETE FROM relocations WHERE sourceStudentId IN (SELECT id FROM students WHERE classId=?) OR targetStudentId IN (SELECT id FROM students WHERE classId=?)",
            "DELETE FROM marks WHERE lessonId IN (SELECT id FROM lessons WHERE classId=?)",
            "DELETE FROM grades WHERE activityId IN (SELECT id FROM activities WHERE classId=?)",
            "DELETE FROM recompositions WHERE studentId IN (SELECT id FROM students WHERE classId=?)",
            "DELETE FROM lesson_records WHERE classId=?", "DELETE FROM export_rows WHERE classId=?", "DELETE FROM class_archives WHERE classId=?",
            "DELETE FROM import_drafts WHERE classId=?", "DELETE FROM activities WHERE classId=?",
            "DELETE FROM lessons WHERE classId=?", "DELETE FROM terms WHERE classId=?",
            "DELETE FROM students WHERE classId=?", "DELETE FROM classes WHERE id=?")) sql.execSQL(query, if(query.startsWith("DELETE FROM relocations")) arrayOf(id,id) else arrayOf(id))
        sql.query("PRAGMA foreign_key_check").use { check(!it.moveToFirst()) { "Falha na integridade. Exclusão desfeita." } }
        val after=readSnapshot()
        check(ClassLifecycle.fingerprint(after) == ClassLifecycle.fingerprint(expected)) { "Conferência da exclusão falhou; operação desfeita." }
        ClassLifecycle.verifyPreservedResults(before,after)
    }

    suspend fun saveClass(value: SchoolClass, effective: String) = db.withTransaction {
        require(value.name.isNotBlank() && value.subject.isNotBlank()) { "Informe turma e disciplina." }
        require(value.year in 2000..2100 && value.duration in 1..240) { "Ano ou duração inválidos." }
        require(value.scale.isFinite() && value.scale > 0 && value.low >= 0 && value.low < value.high && value.high <= value.scale) { "Revise a escala e as faixas de nota." }
        val counts = value.schedule.split(',').map { it.toInt() }
        require(counts.size == 7 && counts.all { it in 0..12 }) { "Use de 0 a 12 aulas por dia." }
        val previous = dao.schoolClass(value.id)
        require(readSnapshot().histories.none {it.classId==value.id}) {"O identificador pertence a uma turma excluída com histórico preservado. Crie uma nova turma."}
        require(previous == null || previous.year == value.year) { "Crie outra turma para mudar o ano e preservar o histórico." }
        require(date(effective).year == value.year) { "A vigência deve estar no ano letivo." }
        val linked=readSnapshot()
        RelocationRules.validateLinks(linked.copy(classes=linked.classes.map {if(it.id==value.id) value else it}))
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
        val linked=readSnapshot()
        RelocationRules.validateLinks(linked.copy(terms=linked.terms.filter {it.classId!=classId}+sorted))
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
        val old=dao.student(value.id);val data=readSnapshot()
        require(old!=null || data.histories.none {h->h.content.students.any {it.id==value.id}}) {"Esta matrícula pertence ao histórico preservado de uma turma excluída."}
        require(old==null || old.classId==value.classId && old.personId==value.personId) {"A identidade e a turma da matrícula não podem ser substituídas."}
        require(old?.status!="Remanejado" || value.status=="Remanejado") {"Matrícula já remanejada. Consulte o histórico; a origem não pode ser reativada."}
        val linked=data.academic().relocations.any {it.sourceStudentId==value.id||it.targetStudentId==value.id}
        if(linked) require(old!=null && old.joined==value.joined && old.left==value.left && old.status==value.status) {"Matrícula vinculada: datas e situação são preservadas pelo remanejamento. Nome, número e observações podem ser corrigidos."}
        require(value.status!="Remanejado" || old?.status=="Remanejado") {"Use a prévia e a confirmação de remanejamento."}
        val reactivating=old!=null && value.status=="Ativo" &&
            (old.status=="Transferido" || old.status=="Ativo" && old.left!=null && value.left==null)
        val saved=if(reactivating) {
            require(value.joined==old!!.joined && (value.left==null || value.left==old.left)) {
                "Reative sem alterar o ingresso ou informar outra saída. Confirme essas alterações separadamente."
            }
            EnrollmentReactivation.reopen(old,data)
            value.copy(left=null)
        } else value
        validateStudent(saved)
        dao.save(saved.copy(name = saved.name.trim()))
    }
    /** Reparo direcionado do estado legado Ativo + saída. A confirmação só vale para o registro conferido. */
    suspend fun repairReactivatedStudent(expected: Student) = db.withTransaction {
        val current=requireNotNull(dao.student(expected.id)) {"Matrícula não encontrada."}
        require(current==expected) {"Os dados mudaram após abrir o formulário. Reabra o cadastro e confira a matrícula."}
        require(current.status=="Ativo" && current.left!=null) {"Estado inconsistente: este reparo exige matrícula ativa com saída preenchida."}
        saveStudent(current.copy(left=null))
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
        require(readSnapshot().academic().relocations.none {it.sourceStudentId==studentId||it.targetStudentId==studentId}) {"Use Remanejado na edição para preservar o histórico vinculado."}
        require(student.classId != target.id && student.status == "Ativo" && day > student.joined) { "Transferência inválida." }
        require(date(day).year == target.year) { "A data deve estar no ano da turma de destino." }
        val roster = dao.roster(targetId)
        require(roster.none { it.personId == student.personId || RosterParser.key(it.name) == RosterParser.key(student.name) }) { "Possível duplicidade no destino." }
        dao.save(student.copy(status = "Transferido", left = date(day).minusDays(1).toString()))
        dao.save(student.copy(id = newId(), classId = targetId, number = (roster.maxOfOrNull { it.number } ?: 0) + 1,
            joined = day, left = null, status = "Ativo"))
    }
    suspend fun previewRelocation(sourceId: String, targetClassId: String?, external: String?, day: String,
        proposed: Student? = null): RelocationPreview = db.withTransaction {
        val before=readSnapshot();val source=requireNotNull(before.students.firstOrNull {it.id==sourceId}) {"Matrícula de origem não encontrada."}
        val edited=proposed ?: source
        require(edited.id==source.id && edited.personId==source.personId && edited.classId==source.classId && edited.joined==source.joined && edited.left==source.left && edited.status==source.status) {"Confirme o remanejamento sem alterar os limites da matrícula de origem."}
        validateStudent(edited)
        require(source.status!="Remanejado" && before.academic().relocations.none {it.sourceStudentId==sourceId}) {"Matrícula já remanejada. Consulte o histórico do destino."}
        require(source.status=="Ativo") {"Matrícula encerrada. Confirme uma reativação legítima no cadastro antes de remanejar."}
        require(source.left==null) {"Estado inconsistente: matrícula ativa com data de saída. Se ela foi reativada, confirme a reativação no cadastro antes de remanejar. Nenhum registro foi alterado."}
        require(targetClassId!=source.classId) {"A turma de destino deve ser diferente da turma de origem."}
        val origin=before.classes.first {it.id==source.classId}
        val effective=try { date(day) } catch(_:java.time.format.DateTimeParseException) {
            throw IllegalArgumentException("Data de remanejamento inválida. Confira o calendário.")
        }
        require(effective.toString()==day && effective.year==origin.year && day>source.joined) {"Data de remanejamento inválida: deve ser posterior ao ingresso e estar no ano letivo."}
        require(before.marks.none {m->m.studentId==sourceId && m.mark!="NR" && before.lessons.any {it.id==m.lessonId&&it.date>=day}} &&
            before.grades.none {g->g.studentId==sourceId&&g.value!=null&&before.activities.any {it.id==g.activityId&&it.date>=day}} &&
            before.recompositions.none {it.studentId==sourceId&&it.date>=day}) {"Há lançamentos na origem na data efetiva ou depois dela. Confira a data e os registros antes de remanejar; nada será apagado."}
        val target:Student?;val targetName:String
        if(targetClassId==null) {
            require(!external.isNullOrBlank() && external.trim().length in 2..160) {"Informe o nome da outra turma (2 a 160 caracteres)."}
            require(RosterParser.key(external)!=RosterParser.key(origin.name)) {"O destino externo não pode ser a turma de origem. Identifique a outra turma/escola."}
            target=null;targetName=external.trim()
        } else {
            require(external==null) {"Selecione apenas um destino."}
            require(before.classArchives.none { it.classId == targetClassId }) {"A turma de destino está arquivada. Reative-a antes de remanejar."}
            val school=requireNotNull(before.classes.firstOrNull {it.id==targetClassId}) {"Turma de destino não encontrada."}
            require(RelocationRules.compatible(origin,school,before.terms)) {"Destino incompatível: use mesmo ano, disciplina, etapa, escala, bimestres e política de FJ. Não há conversão automática."}
            require(before.lineage(source).none {it.classId==targetClassId}) {"O destino já faz parte deste histórico. Retorno/ciclo não permitido."}
            val roster=before.roster(targetClassId);val existing=roster.filter {it.personId==source.personId}
            require(existing.size<=1) {"Há identidades repetidas no destino. Confira as matrículas."}
            target=existing.singleOrNull()?.also {
                require(it.status=="Ativo"&&it.left==null&&it.joined==day && before.academic().relocations.none {l->l.targetStudentId==it.id||l.sourceStudentId==it.id}) {"A matrícula existente no destino deve estar ativa, sem outro vínculo e com ingresso exatamente na data efetiva. Seus dados não serão ajustados silenciosamente."}
                require(before.marks.none {m->m.studentId==it.id&&m.mark!="NR"&&before.lessons.any {l->l.id==m.lessonId&&l.date<day}} && before.grades.none {g->g.studentId==it.id&&g.value!=null&&before.activities.any {a->a.id==g.activityId&&a.date<day}}) {"A matrícula de destino tem lançamentos anteriores ao ingresso. Confira-os antes de vincular."}
            } ?: run {
                require(roster.none {RosterParser.possibleDuplicate(it.name,edited.name)}) {"Há nome semelhante no destino com outra identidade. Não será feita fusão automática; confira a matrícula."}
                edited.copy(id=newId(),classId=targetClassId,number=(roster.maxOfOrNull {it.number}?:0)+1,joined=day,left=null,status="Ativo")
            }
            targetName=school.name
        }
        val link=Relocation(source.id,target?.id,day,if(target==null) targetName else null)
        val ended=edited.copy(status="Remanejado",left=date(day).minusDays(1).toString())
        val after=before.copy(students=before.students.map {if(it.id==source.id) ended else it}+listOfNotNull(target?.takeIf {t->before.students.none {it.id==t.id}}),relocations=before.relocations+link)
        RelocationRules.validateLinks(after)
        RelocationPreview(source,target,link,targetName,before,edited)
    }
    suspend fun confirmRelocation(preview: RelocationPreview) = db.withTransaction {
        require(readSnapshot()==preview.prior) {"Os dados mudaram após a prévia. Gere uma nova prévia antes de confirmar."}
        val checked=previewRelocation(preview.source.id,preview.target?.classId,preview.link.externalTarget,preview.link.effectiveDate,preview.proposedSource)
        // O UUID da matrícula nova pertence à prévia confirmada; a revalidação não altera IDs.
        require(checked.target?.let {it.copy(id=preview.target!!.id)}==preview.target && checked.link.copy(targetStudentId=preview.target?.id)==preview.link && checked.targetName==preview.targetName) {"Prévia inconsistente. Refaça a conferência."}
        dao.save(preview.proposedSource.copy(status="Remanejado",left=preview.end))
        preview.target?.takeIf { t->preview.prior.students.none {it.id==t.id} }?.let {dao.save(it)}
        dao.insertRelocation(preview.link)
        RelocationRules.validateLinks(readSnapshot())
    }
    private suspend fun validateStudent(value: Student) {
        require(value.name.trim().length >= 2 && value.number > 0) { "Informe nome e número válido." }
        date(value.joined); value.left?.let { require(date(it) >= date(value.joined)) { "Saída anterior ao ingresso." } }
        require(value.status in listOf("Ativo","Transferido","Remanejado")) {"Situação da matrícula inválida."}
        require(value.status == "Ativo" || value.left != null) { "Informe a data de saída." }
        require(dao.roster(value.classId).none { it.id != value.id && it.number == value.number }) { "Número já utilizado nesta turma." }
    }
    private suspend fun ensureWritable(student: Student) {
        require(student.status!="Remanejado") {"Matrícula remanejada: consulte o histórico; lançamentos na origem estão bloqueados."}
    }
    private suspend fun recompositionContext(owner: Student, contextStudentId: String?, term: Int, existing: Recomposition?) {
        val context=if(contextStudentId==null) owner else requireNotNull(dao.student(contextStudentId))
        ensureWritable(context)
        val data=readSnapshot();val chain=data.lineage(context)
        require(chain.any {it.id==owner.id}) {"Matrícula fora do histórico selecionado."}
        if(owner.id!=context.id) require(existing!=null) {"Não crie um lançamento novo em matrícula histórica."}
        val other=data.periodRecomposition(context,term)
        require(other==null || other.studentId==owner.id) {"Há recomposição neste bimestre em outra matrícula do histórico. Edite o registro existente; não crie outro."}
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
                dao.roster(classId).filter { it.status!="Remanejado" && it.enrolled(day) }.forEach { dao.save(Attendance(lesson.id, it.id, "P")) }
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
        ensureWritable(student)
        require(lesson.state == "REALIZADA" && student.classId == lesson.classId && student.enrolled(lesson.date)) { "Aula ou matrícula não permite chamada." }
        dao.save(Attendance(lessonId, studentId, mark))
    }
    /** Resolve o segundo toque dentro da transação, inclusive com cliques rápidos antes do Flow. */
    suspend fun toggleMark(lessonId: String, studentId: String, value: String) = db.withTransaction {
        require(value in listOf("P", "F", "FJ", "NR"))
        val current = dao.attendance(lessonId).firstOrNull { it.studentId == studentId }?.mark
        mark(lessonId, studentId, if(value != "NR" && current == value) "NR" else value)
    }
    suspend fun startLesson(id: String) = db.withTransaction {
        val lesson = requireNotNull(dao.lesson(id)); require(lesson.state != "CANCELADA") { "Reative a aula antes de registrar." }
        val c = requireNotNull(dao.schoolClass(lesson.classId)); dao.save(lesson.copy(state = "REALIZADA"))
        if (c.startPresent && dao.attendance(id).isEmpty()) {
            dao.roster(c.id).filter { it.status!="Remanejado" && it.enrolled(lesson.date) }.forEach { dao.save(Attendance(id, it.id, "P")) }
        }
    }
    suspend fun allPresent(id: String) = db.withTransaction {
        val lesson = requireNotNull(dao.lesson(id)); require(lesson.state == "REALIZADA")
        dao.roster(lesson.classId).filter { it.status!="Remanejado" && it.enrolled(lesson.date) }.forEach { dao.save(Attendance(id, it.id, "P")) }
    }
    suspend fun copyFirst(id: String) = db.withTransaction {
        val target = requireNotNull(dao.lesson(id)); require(target.state == "REALIZADA")
        val first = dao.day(target.classId, target.date).firstOrNull { it.ordinal < target.ordinal && it.state == "REALIZADA" }
        requireNotNull(first) { "Nenhuma chamada anterior realizada neste dia." }
        val eligible = dao.roster(target.classId).filter { it.status!="Remanejado" && it.enrolled(target.date) }.map { it.id }.toSet()
        val source = dao.attendance(first.id).associateBy { it.studentId }
        eligible.forEach { studentId -> dao.save(Attendance(target.id, studentId, source[studentId]?.mark ?: "NR")) }
    }
    private suspend fun validateActivity(value: Activity) {
        require(value.name.isNotBlank() && value.maximum.isFinite() && value.maximum > 0 && value.term in 1..4) { "Informe nome, bimestre e valor válidos." }
        val linked=readSnapshot().academic()
        require(dao.activity(value.id)!=null || linked.activities.none {it.id==value.id}) {"Esta atividade pertence ao histórico preservado de uma turma excluída."}
        linked.students.filter {it.classId==value.classId && linked.relocations.any {r->r.sourceStudentId==it.id||r.targetStudentId==it.id}}.forEach {student ->
            require(dao.activityGrades(value.id).none {it.studentId==student.id&&it.value!=null} || student.enrolled(value.date)) {"A nova data excluiria uma nota do histórico vinculado. Confira a matrícula e a atividade."}
        }
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
        ensureWritable(student)
        require(student.classId == activity.classId && student.enrolled(activity.date)) { "Aluno fora do período de matrícula." }
        require(value == null || value.isFinite() && value in 0.0..activity.maximum) { "Nota deve estar entre 0 e ${activity.maximum}." }
        dao.save(Grade(activityId, studentId, value))
    }
    suspend fun saveRecomposition(value: Recomposition, expected: Recomposition? = null, contextStudentId: String? = null) = db.withTransaction {
        val data=readSnapshot().academic()
        val student = requireNotNull(data.students.firstOrNull {it.id==value.studentId}) { "Matrícula não encontrada." }
        recompositionContext(student,contextStudentId,value.term,expected)
        require(value.term in 1..4 && data.terms.any { it.classId==student.classId && it.number == value.term }) { "Selecione o bimestre de origem." }
        require(date(value.date).toString() == value.date) { "Data inválida." }
        require(value.value == null || value.value.isFinite() && value.value in 0.0..10.0) { "Recomposição deve estar entre 0 e 10." }
        require(data.recompositions.firstOrNull {it.studentId==value.studentId && it.term==value.term} == expected) { "A recomposição mudou. Reabra o lançamento antes de confirmar." }
        if(dao.student(value.studentId)!=null) dao.save(value) else updateHistoricalRecomposition(value.studentId,value.term,value)
    }
    suspend fun deleteRecomposition(expected: Recomposition, contextStudentId: String? = null) = db.withTransaction {
        val data=readSnapshot().academic()
        require(data.recompositions.firstOrNull {it.studentId==expected.studentId && it.term==expected.term} == expected) { "A recomposição mudou. Reabra o lançamento." }
        recompositionContext(requireNotNull(data.students.firstOrNull {it.id==expected.studentId}),contextStudentId,expected.term,expected)
        if(dao.student(expected.studentId)!=null) dao.deleteRecomposition(expected.studentId, expected.term)
        else updateHistoricalRecomposition(expected.studentId,expected.term,null)
    }
    private suspend fun updateHistoricalRecomposition(studentId: String, term: Int, value: Recomposition?) {
        val history=db.backupDao().histories().single {h->h.content.students.any {it.id==studentId}}
        val rows=history.content.recompositions.filterNot {it.studentId==studentId && it.term==term}+listOfNotNull(value)
        check(dao.updateHistory(history.copy(content=history.content.copy(recompositions=rows).canonical()))==1)
        br.com.diariodeclasse.backup.BackupCodec.validate(ClassLifecycle.snapshot(readSnapshot()))
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
    /** Atualiza apenas os IDs conferidos nesta sessão, sem sobrescrever nomes ou matricular. */
    suspend fun reviewDrafts(classId: String, ids: Set<String>, reviewed: Boolean) = db.withTransaction {
        require(ids.isNotEmpty()) { "Nenhum rascunho para conferir." }
        val current = dao.draftRows(classId).map { it.id }.toSet()
        require(current.containsAll(ids)) { "A importação mudou. Confira a lista novamente." }
        // SQLite do Android 8 limita a quantidade de parâmetros por comando.
        ids.toList().chunked(900).forEach{chunk->check(dao.reviewDrafts(classId,chunk,reviewed)==chunk.size)}
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
