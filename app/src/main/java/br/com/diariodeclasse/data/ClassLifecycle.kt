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
        recompositions=d.recompositions,relocations=d.relocations,exportRows=d.exportRows,classArchives=d.classArchives,lessonRecords=d.lessonRecords)
    fun fingerprint(d:DiarySnapshot)=BackupCodec.fingerprint(snapshot(d))
    fun preview(d:DiarySnapshot,id:String):ClassDeletionPreview {
        val c=requireNotNull(d.classes.firstOrNull{it.id==id}) {"Turma não encontrada."}
        val people=d.roster(id);val ids=people.map{it.id}.toSet();val persons=people.map{it.personId}.toSet()
        val lessons=d.lessons.filter{it.classId==id}.map{it.id}.toSet();val activities=d.activities.filter{it.classId==id}.map{it.id}.toSet()
        val links=d.relocations.count{it.sourceStudentId in ids||it.targetStudentId in ids}
        val shared=d.students.any{it.classId!=id&&it.personId in persons}
        val cross=d.marks.any{(it.studentId in ids)!=(it.lessonId in lessons)}||d.grades.any{(it.studentId in ids)!=(it.activityId in activities)}||d.exportRows.any{it.studentId!=null&&((it.studentId in ids)!=(it.classId==id))}
        val blocked=when {
            links>0 -> "Há vínculos de remanejamento nesta turma. A exclusão foi bloqueada para preservar o histórico. Arquive a turma."
            shared -> "Há identidades de alunos compartilhadas com outras turmas. Arquive a turma para preservar o histórico."
            cross -> "Há registros vinculados a outra turma. A exclusão não é segura; arquive a turma."
            else -> null
        }
        return ClassDeletionPreview(c,linkedMapOf("Matrículas" to ids.size,"Horários de aula" to lessons.size,"Chamadas registradas" to d.marks.filter{it.lessonId in lessons}.map{it.lessonId}.distinct().size,"Registros individuais de frequência" to d.marks.count{it.lessonId in lessons},
            "Atividades" to activities.size,"Notas" to d.grades.count{it.activityId in activities},"Recomposições" to d.recompositions.count{it.studentId in ids},
            "Bimestres" to d.terms.count{it.classId==id},"Rascunhos" to d.drafts.count{it.classId==id},"Linhas de exportação" to d.exportRows.count{it.classId==id},"Remanejamentos" to links,"Registros de aula" to d.lessonRecords.count{it.classId==id}),blocked,fingerprint(d))
    }
    fun without(d:DiarySnapshot,id:String):DiarySnapshot {
        val ids=d.roster(id).map{it.id}.toSet();val lessons=d.lessons.filter{it.classId==id}.map{it.id}.toSet();val acts=d.activities.filter{it.classId==id}.map{it.id}.toSet()
        return d.copy(classes=d.classes.filterNot{it.id==id},students=d.students.filterNot{it.classId==id},terms=d.terms.filterNot{it.classId==id},lessons=d.lessons.filterNot{it.classId==id},
            marks=d.marks.filterNot{it.lessonId in lessons},activities=d.activities.filterNot{it.classId==id},grades=d.grades.filterNot{it.activityId in acts},drafts=d.drafts.filterNot{it.classId==id},
            recompositions=d.recompositions.filterNot{it.studentId in ids},exportRows=d.exportRows.filterNot{it.classId==id},classArchives=d.classArchives.filterNot{it.classId==id},lessonRecords=d.lessonRecords.filterNot{it.classId==id})
    }
}
