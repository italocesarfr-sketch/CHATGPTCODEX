package br.com.diariodeclasse.data

import br.com.diariodeclasse.domain.RosterParser
import java.time.LocalDate

/** Contrato compartilhado por prévia, transação e validação de backup; não altera notas. */
object RelocationRules {
    fun compatible(a: SchoolClass, b: SchoolClass, terms: List<Term>): Boolean =
        a.id != b.id && a.year == b.year && a.stage == b.stage &&
            RosterParser.key(a.subject) == RosterParser.key(b.subject) && a.scale == b.scale &&
            a.countJustified == b.countJustified && (1..4).all { n ->
                val x=terms.firstOrNull {it.classId==a.id&&it.number==n}
                val y=terms.firstOrNull {it.classId==b.id&&it.number==n}
                x!=null && y!=null && x.start==y.start && x.end==y.end
            }
    /** Mesma resolução usada pelo remanejamento inicial e pela conversão administrativa. */
    fun internalTarget(data: DiarySnapshot, source: Student, targetClassId: String, day: String,
        edited: Student = source, newStudentId: String? = null): Student {
        require(targetClassId != source.classId) { "A turma de destino deve ser diferente da turma de origem." }
        require(data.classArchives.none { it.classId == targetClassId }) { "A turma de destino está arquivada. Reative-a antes de remanejar." }
        val origin = data.classes.first { it.id == source.classId }
        val school = requireNotNull(data.classes.firstOrNull { it.id == targetClassId }) { "Turma de destino não encontrada." }
        require(compatible(origin, school, data.terms)) { "Destino incompatível: use mesmo ano, disciplina, etapa, escala, bimestres e política de FJ. Não há conversão automática." }
        require(data.lineage(source).none { it.classId == targetClassId }) { "O destino já faz parte deste histórico. Retorno/ciclo não permitido." }
        val roster = data.roster(targetClassId)
        val existing = roster.filter { it.personId == source.personId }
        require(existing.size <= 1) { "Há identidades repetidas no destino. Confira as matrículas." }
        return existing.singleOrNull()?.also { target ->
            require(target.status == "Ativo" && target.left == null && target.joined == day &&
                data.academic().relocations.none { it.targetStudentId == target.id || it.sourceStudentId == target.id }) {
                "A matrícula existente no destino deve estar ativa, sem outro vínculo e com ingresso exatamente na data efetiva. Seus dados não serão ajustados silenciosamente."
            }
            require(data.marks.none { m -> m.studentId == target.id && m.mark != "NR" && data.lessons.any { it.id == m.lessonId && it.date < day } } &&
                data.grades.none { g -> g.studentId == target.id && g.value != null && data.activities.any { it.id == g.activityId && it.date < day } }) {
                "A matrícula de destino tem lançamentos anteriores ao ingresso. Confira-os antes de vincular."
            }
        } ?: run {
            require(roster.none { RosterParser.possibleDuplicate(it.name, edited.name) }) { "Há nome semelhante no destino com outra identidade. Não será feita fusão automática; confira a matrícula." }
            val last = roster.maxOfOrNull { it.number } ?: 0
            require(last < Int.MAX_VALUE) { "Não há número de chamada disponível no destino." }
            val id = newStudentId ?: newId()
            require(data.academic().students.none { it.id == id }) { "Identificador da nova matrícula já está em uso. Refaça a prévia." }
            edited.copy(id = id, classId = targetClassId, number = last + 1, joined = day, left = null, status = "Ativo")
        }
    }
    fun validateLinks(data: DiarySnapshot) {
        if(data.histories.isNotEmpty()) { validateLinks(data.academic()); return }
        val students=data.students.associateBy {it.id};val classes=data.classes.associateBy {it.id}
        require(data.relocations.map {it.sourceStudentId}.distinct().size==data.relocations.size) {"Matrícula de origem já remanejada."}
        require(data.relocations.mapNotNull {it.targetStudentId}.distinct().size==data.relocations.count {it.targetStudentId!=null}) {"Destino já possui outro vínculo."}
        data.relocations.forEach { link ->
            val source=requireNotNull(students[link.sourceStudentId]) {"Origem não encontrada."}
            val day=LocalDate.parse(link.effectiveDate)
            require(day.toString()==link.effectiveDate && day.year==classes[source.classId]?.year && link.effectiveDate>source.joined) {"Data de remanejamento inválida."}
            require(source.status=="Remanejado" && source.left==day.minusDays(1).toString()) {"Encerramento da origem não confere com o vínculo."}
            require(link.corrections.map { it.id }.distinct().size == link.corrections.size)
            link.corrections.forEachIndexed { index, correction ->
                require(correction.id.isNotBlank())
                require(correction.operation in setOf("CORRECAO_EXTERNA", "CONVERSAO_INTERNA")) { "Tipo de correção administrativa inválido." }
                if (correction.operation == "CONVERSAO_INTERNA") {
                    require(index == link.corrections.lastIndex && link.targetStudentId != null) { "A conversão deve encerrar a sequência de correções externas." }
                    val target = requireNotNull(students[link.targetStudentId])
                    require(correction.externalTarget == null && correction.sourceStudentId == source.id &&
                        correction.targetStudentId == target.id && correction.targetClassId == target.classId && !correction.targetLabel.isNullOrBlank()) { "Auditoria da conversão não confere com o destino." }
                } else {
                    require(correction.externalTarget != null && correction.externalTarget.length in 2..160)
                    require(correction.sourceStudentId == null && correction.targetStudentId == null && correction.targetClassId == null && correction.targetLabel == null)
                }
                java.time.Instant.parse(correction.confirmedAt)
                listOf(correction.previousDate, correction.effectiveDate).forEach { value ->
                    val parsed = LocalDate.parse(value)
                    require(parsed.toString() == value && parsed.year == day.year && value > source.joined)
                }
                require(correction.previousTarget.isNotBlank())
                if (index > 0) require(correction.previousDate == link.corrections[index-1].effectiveDate && correction.previousTarget == link.corrections[index-1].externalTarget)
                require(correction.removed.map { it.attendance.lessonId }.distinct().size == correction.removed.size)
                correction.removed.forEach { removed ->
                    require(removed.attendance.studentId == source.id && removed.attendance.lessonId == removed.lesson.id && removed.lesson.classId == source.classId)
                    require(removed.attendance.mark in listOf("P", "F", "FJ", "NR") && removed.lesson.date >= correction.effectiveDate)
                    require(LocalDate.parse(removed.lesson.date).toString() == removed.lesson.date && removed.lesson.ordinal > 0)
                }
            }
            link.corrections.lastOrNull()?.let {
                require(it.effectiveDate == link.effectiveDate && it.externalTarget == link.externalTarget)
                require((it.operation == "CONVERSAO_INTERNA") == (link.targetStudentId != null)) { "O destino atual diverge do último ato administrativo." }
            }
            if(link.targetStudentId==null) require(!link.externalTarget.isNullOrBlank()) {"Informe o destino externo."}
            else {
                require(link.externalTarget==null) {"Destino interno e externo não podem coexistir."}
                val target=requireNotNull(students[link.targetStudentId]) {"Destino não encontrado."}
                require(source.personId==target.personId && target.joined==link.effectiveDate) {"Identidade ou ingresso incompatível no destino."}
                require(compatible(classes.getValue(source.classId),classes.getValue(target.classId),data.terms)) {"Calendários, escala, disciplina, etapa ou política de FJ incompatíveis. Não haverá conversão automática."}
            }
        }
        data.students.forEach { student ->
            val chain=data.lineage(student)
            require(chain.map {it.classId}.distinct().size==chain.size) {"Retorno à mesma turma criaria um ciclo de histórico."}
            require(chain.map {it.personId}.distinct().size==1) {"Identidades diferentes no mesmo histórico."}
            for(term in 1..4) data.periodRecomposition(student,term)
        }
    }
}

class RelocationPreview internal constructor(val source: Student, val target: Student?, val link: Relocation,
    val targetName: String, val prior: DiarySnapshot, val proposedSource: Student) {
    val end: String get() = LocalDate.parse(link.effectiveDate).minusDays(1).toString()
    val linkedExisting: Boolean get() = target!=null && prior.students.any {it.id==target.id}
    val gradeCount: Int get() = prior.grades.count {it.studentId in prior.lineage(source).map {s->s.id} && it.value!=null}
    val markCount: Int get() = prior.marks.count {it.studentId in prior.lineage(source).map {s->s.id} && it.mark!="NR"}
}
