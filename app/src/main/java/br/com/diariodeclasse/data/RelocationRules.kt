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
