package br.com.diariodeclasse.sync.academic

import br.com.diariodeclasse.backup.BackupSnapshot
import br.com.diariodeclasse.backup.diarySnapshot
import br.com.diariodeclasse.data.*

/** Additional synchronization invariants; the existing academic validator remains authoritative. */
object PhaseDGuard {
    fun validate(before:BackupSnapshot,after:BackupSnapshot) {
        val academic=after.diarySnapshot().academic()
        // Recovering a moved enrollment without its source link would lose its interpretation.
        require(academic.students.filter{it.status=="Remanejado"}.all{s->academic.relocations.any{it.sourceStudentId==s.id}})
        academic.classes.forEach{c->br.com.diariodeclasse.sync.ClassBundle(c,academic.terms.filter{it.classId==c.id}).validate()}
        immutable(before,after)
    }
    fun immutable(before:BackupSnapshot,after:BackupSnapshot) {
        require(before.timetables.all{old->after.timetables.any{it==old}}){"Uma revisão confirmada de horários não pode ser alterada ou removida."}
        val old=before.diarySnapshot().academic();val next=after.diarySnapshot().academic()
        old.relocations.forEach { link ->
            val replacement=next.relocations.singleOrNull{it.sourceStudentId==link.sourceStudentId}
            if(replacement==null) {
                require(next.students.none{it.id==link.sourceStudentId || it.id==link.targetStudentId}){"O vínculo ainda pertence a uma matrícula preservada."}
            }else {
                require(replacement.corrections.take(link.corrections.size)==link.corrections){"Auditorias anteriores não podem ser alteradas."}
                if(replacement!=link) {
                    require(replacement.corrections.size>link.corrections.size){"A alteração do vínculo precisa de auditoria."}
                    val first=replacement.corrections[link.corrections.size]
                    require(first.previousDate==link.effectiveDate && first.previousTarget==link.externalTarget)
                }
                // An internal chain cannot be unilaterally reassigned by an old/offline client.
                if(link.targetStudentId!=null)require(replacement==link){"O remanejamento interno confirmado é imutável."}
            }
        }
        before.histories.forEach { h ->
            val kept=after.histories.singleOrNull{it.classId==h.classId}
            if(kept!=null)require(kept.content.school==h.content.school && kept.content.terms.sortedBy{it.number}==h.content.terms.sortedBy{it.number})
            h.content.students.forEach { student ->
                next.students.singleOrNull{it.id==student.id}?.let { require(it==student && kept!=null) }
            }
            h.content.lessons.forEach{r->next.lessons.singleOrNull{it.id==r.id}?.let{require(it==r)}}
            h.content.activities.forEach{r->next.activities.singleOrNull{it.id==r.id}?.let{require(it==r)}}
            val retained=next.students.map{it.id}.toSet()
            h.content.marks.filter{it.studentId in retained}.forEach{r->require(r in next.marks)}
            h.content.grades.filter{it.studentId in retained}.forEach{r->require(r in next.grades)}
            h.content.recompositions.filter{it.studentId in retained}.forEach{r->require(r in next.recompositions)}
        }
        val previousStudents=old.students.associateBy{it.id}
        next.students.forEach{s->previousStudents[s.id]?.let{require(it.personId==s.personId&&it.classId==s.classId)}}
    }
}
