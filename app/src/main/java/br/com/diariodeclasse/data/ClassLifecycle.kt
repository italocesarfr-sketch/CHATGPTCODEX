package br.com.diariodeclasse.data

import androidx.room.*
import br.com.diariodeclasse.backup.*
import kotlinx.serialization.Serializable

@Entity(tableName="class_archives", foreignKeys=[ForeignKey(entity=SchoolClass::class,parentColumns=["id"],childColumns=["classId"],onDelete=ForeignKey.RESTRICT)])
@Serializable data class ClassArchive(@PrimaryKey val classId:String)

data class ClassDeletionPreview(val school:SchoolClass,val counts:Map<String,Int>,val blockedReason:String?,val fingerprint:String) {
    val confirmation get()="EXCLUIR ${school.name} (${school.year})"
}
object ClassLifecycle {
    fun snapshot(d:DiarySnapshot)=BackupSnapshot(d.classes,d.students,d.terms,d.lessons,d.marks,d.activities,d.grades,d.drafts,
        recompositions=d.recompositions,relocations=d.relocations,exportRows=d.exportRows,classArchives=d.classArchives,lessonRecords=d.lessonRecords,histories=d.histories,timetables=d.timetables)
    fun fingerprint(d:DiarySnapshot)=BackupCodec.fingerprint(snapshot(d))
    fun preview(d:DiarySnapshot,id:String):ClassDeletionPreview {
        val c=requireNotNull(d.classes.firstOrNull{it.id==id}) {"Turma não encontrada."}
        val people=d.roster(id);val ids=people.map{it.id}.toSet()
        val lessons=d.lessons.filter{it.classId==id}.map{it.id}.toSet();val activities=d.activities.filter{it.classId==id}.map{it.id}.toSet()
        val academic=d.academic()
        val links=academic.relocations.count{it.sourceStudentId in ids||it.targetStudentId in ids}
        val shared=people.any {person -> d.students.any {other -> other.classId!=id && other.personId==person.personId &&
            academic.lineage(other).none {it.id==person.id} && academic.lineage(person).none {it.id==other.id}}}
        val cross=d.marks.any{(it.studentId in ids)!=(it.lessonId in lessons)}||d.grades.any{(it.studentId in ids)!=(it.activityId in activities)}||d.exportRows.any{it.studentId!=null&&((it.studentId in ids)!=(it.classId==id))}
        val blocked=when {
            shared -> "Há identidades compartilhadas sem vínculo de remanejamento comprovado. Confira as matrículas antes de excluir; o histórico não será associado automaticamente."
            cross -> "Há registros vinculados indevidamente a outra turma. Revise os vínculos antes de excluir; nenhum dado foi removido."
            else -> null
        }
        val preserved=if(blocked==null) PreservedHistories.afterDeletion(d,id).histories.firstOrNull {it.classId==id}?.content else null
        return ClassDeletionPreview(c,linkedMapOf("Matrículas" to ids.size,"Horários de aula" to lessons.size,"Chamadas registradas" to d.marks.filter{it.lessonId in lessons}.map{it.lessonId}.distinct().size,"Registros individuais de frequência" to d.marks.count{it.lessonId in lessons},
            "Atividades" to activities.size,"Notas" to d.grades.count{it.activityId in activities},"Recomposições" to d.recompositions.count{it.studentId in ids},
            "Bimestres" to d.terms.count{it.classId==id},"Rascunhos" to d.drafts.count{it.classId==id},"Linhas de exportação" to d.exportRows.count{it.classId==id},"Remanejamentos" to links,"Registros de aula" to d.lessonRecords.count{it.classId==id},
            "Históricos de matrículas preservados" to (preserved?.students?.size?:0),"Notas preservadas no histórico" to (preserved?.grades?.size?:0),
            "Frequências preservadas no histórico" to (preserved?.marks?.size?:0),"Recomposições preservadas no histórico" to (preserved?.recompositions?.size?:0)),blocked,fingerprint(d))
    }
    fun without(d:DiarySnapshot,id:String):DiarySnapshot {
        return PreservedHistories.afterDeletion(d,id)
    }
    /** As mesmas projeções aprovadas comprovam o resultado antes de confirmar o commit SQLite. */
    fun verifyPreservedResults(before: DiarySnapshot, after: DiarySnapshot) {
        val removed=before.classes.map {it.id}.toSet()-after.classes.map {it.id}.toSet()
        for(student in after.students) {
            if(before.lineage(student).none {it.classId in removed}) continue
            val school=after.classes.first {it.id==student.classId}
            val old=br.com.diariodeclasse.reports.SchoolReports(before,school,true)
            val new=br.com.diariodeclasse.reports.SchoolReports(after,school,true)
            for(term in 1..4) {
                fun values(r: br.com.diariodeclasse.reports.StudentPeriodReport)=listOf(r.original,r.recomposition,r.total,r.state,r.enrollment,
                    r.grades.pending,r.grades.partial,r.attendance,r.enrollments,
                    r.activities.filter {it.eligible}.map {listOf(it.activity,it.score,it.enrollmentId)})
                check(values(old.student(student,term))==values(new.student(student,term))) {"Os resultados históricos divergiram. A exclusão foi desfeita."}
            }
            check(old.year(student).attendance==new.year(student).attendance && old.attendanceHistory(student)==new.attendanceHistory(student)) {"A frequência histórica divergiu. A exclusão foi desfeita."}
        }
    }
}
