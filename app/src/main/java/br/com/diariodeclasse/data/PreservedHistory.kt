package br.com.diariodeclasse.data

import androidx.room.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Contexto original de uma turma excluída. Não é uma turma cadastrada nem uma cópia no destino. */
@Serializable
data class HistoricalContent(
    val school: SchoolClass, val students: List<Student>, val terms: List<Term>,
    val lessons: List<Lesson>, val marks: List<Attendance>, val activities: List<Activity>,
    val grades: List<Grade>, val recompositions: List<Recomposition>, val relocations: List<Relocation>
) {
    fun canonical() = copy(students=students.sortedBy {it.id}, terms=terms.sortedBy {it.number},
        lessons=lessons.sortedBy {it.id}, marks=marks.sortedWith(compareBy({it.lessonId},{it.studentId})),
        activities=activities.sortedBy {it.id}, grades=grades.sortedWith(compareBy({it.activityId},{it.studentId})),
        recompositions=recompositions.sortedWith(compareBy({it.studentId},{it.term})), relocations=relocations.sortedBy {it.sourceStudentId})
}

/** Sem FK para a turma removida: os vínculos lógicos são verificados antes da escrita/restauração. */
@Entity(tableName="preserved_histories")
@Serializable
data class PreservedHistory(@PrimaryKey val classId: String, val content: HistoricalContent)

class HistoricalConverters {
    private val json=Json {encodeDefaults=true}
    @TypeConverter fun encode(value: HistoricalContent): String = json.encodeToString(value.canonical())
    @TypeConverter fun decode(value: String): HistoricalContent = json.decodeFromString(value)
}

object PreservedHistories {
    /** União por IDs, sem deduplicar silenciosamente conflitos. Nunca usada em seletores/cadastro. */
    fun merge(data: DiarySnapshot): DiarySnapshot {
        if(data.histories.isEmpty()) return data
        val parts=data.histories.map {it.content}
        return data.copy(classes=data.classes+parts.map {it.school}, students=data.students+parts.flatMap {it.students},
            terms=data.terms+parts.flatMap {it.terms}, lessons=data.lessons+parts.flatMap {it.lessons},
            marks=data.marks+parts.flatMap {it.marks}, activities=data.activities+parts.flatMap {it.activities},
            grades=data.grades+parts.flatMap {it.grades}, recompositions=data.recompositions+parts.flatMap {it.recompositions},
            relocations=data.relocations+parts.flatMap {it.relocations}, histories=emptyList())
    }

    private fun connected(data: DiarySnapshot, roots: Set<String>): Set<String> {
        val neighbors=mutableMapOf<String,MutableList<String>>()
        data.relocations.forEach {link -> link.targetStudentId?.let {target ->
            neighbors.getOrPut(link.sourceStudentId) {mutableListOf()}.add(target)
            neighbors.getOrPut(target) {mutableListOf()}.add(link.sourceStudentId)
        }}
        val seen=roots.toMutableSet();val queue=ArrayDeque(roots)
        while(queue.isNotEmpty()) neighbors[queue.removeFirst()].orEmpty().forEach {if(seen.add(it)) queue.addLast(it)}
        return seen
    }

    /** Plano puro: conserva apenas componentes com alguma matrícula em outra turma cadastrada. */
    fun afterDeletion(data: DiarySnapshot, classId: String): DiarySnapshot {
        val academic=data.academic()
        val live=data.students.filterNot {it.classId==classId}.map {it.id}.toSet()
        val retained=connected(academic,live)
        val deletedClasses=data.histories.map {it.classId}.toSet()+classId
        val historicalStudents=academic.students.filter {it.classId in deletedClasses && it.id in retained}
        val keptIds=live+historicalStudents.map {it.id}
        val historyClassByStudent=historicalStudents.associate {it.id to it.classId}
        // Um vínculo aparece uma vez: na origem excluída, ou no destino excluído se a origem vive.
        val historyLinks=academic.relocations.filter {it.sourceStudentId in keptIds && (it.targetStudentId==null || it.targetStudentId in keptIds)}
            .mapNotNull {link -> (historyClassByStudent[link.sourceStudentId] ?: historyClassByStudent[link.targetStudentId])?.let {it to link}}
        val fragments=deletedClasses.sorted().mapNotNull {id ->
            val students=historicalStudents.filter {it.classId==id}
            if(students.isEmpty()) return@mapNotNull null
            val ids=students.map {it.id}.toSet()
            val marks=academic.marks.filter {it.studentId in ids}
            val grades=academic.grades.filter {it.studentId in ids}
            val marked=marks.map {it.lessonId}.toSet();val graded=grades.map {it.activityId}.toSet()
            val lessons=academic.lessons.filter {it.classId==id && (it.id in marked || students.any {s->s.enrolled(it.date)})}
            val activities=academic.activities.filter {it.classId==id && (it.id in graded || students.any {s->s.enrolled(it.date)})}
            PreservedHistory(id,HistoricalContent(academic.classes.first {it.id==id},students,academic.terms.filter {it.classId==id},
                lessons,marks,activities,grades,academic.recompositions.filter {it.studentId in ids},historyLinks.filter {it.first==id}.map {it.second}).canonical())
        }
        val localIds=data.roster(classId).map {it.id}.toSet()
        val lessonIds=data.lessons.filter {it.classId==classId}.map {it.id}.toSet()
        val activityIds=data.activities.filter {it.classId==classId}.map {it.id}.toSet()
        return data.copy(classes=data.classes.filterNot {it.id==classId}, students=data.students.filterNot {it.classId==classId},
            terms=data.terms.filterNot {it.classId==classId}, lessons=data.lessons.filterNot {it.classId==classId},
            marks=data.marks.filterNot {it.lessonId in lessonIds}, activities=data.activities.filterNot {it.classId==classId},
            grades=data.grades.filterNot {it.activityId in activityIds}, drafts=data.drafts.filterNot {it.classId==classId},
            recompositions=data.recompositions.filterNot {it.studentId in localIds},
            relocations=data.relocations.filter {it.sourceStudentId in live && (it.targetStudentId==null || it.targetStudentId in live)},
            exportRows=data.exportRows.filterNot {it.classId==classId}, classArchives=data.classArchives.filterNot {it.classId==classId},
            lessonRecords=data.lessonRecords.filterNot {it.classId==classId}, histories=fragments)
    }

    fun validateStorage(data: DiarySnapshot) {
        require(data.histories.map {it.classId}.distinct().size==data.histories.size) {"Histórico de turma duplicado."}
        val academic=data.academic()
        val connected=connected(academic,data.students.map {it.id}.toSet())
        data.histories.forEach {h ->
            require(h.classId==h.content.school.id && data.classes.none {it.id==h.classId}) {"Origem histórica conflitante com turma cadastrada."}
            require(h.content.students.isNotEmpty() && h.content.students.all {it.classId==h.classId && it.id in connected}) {"Histórico sem vínculo com matrícula preservada."}
            require(h.content.terms.all {it.classId==h.classId} && h.content.lessons.all {it.classId==h.classId} && h.content.activities.all {it.classId==h.classId}) {"Contexto histórico de outra turma."}
            val ids=h.content.students.map {it.id}.toSet()
            require(h.content.marks.all {it.studentId in ids} && h.content.grades.all {it.studentId in ids} && h.content.recompositions.all {it.studentId in ids}) {"Registro histórico de outra matrícula."}
            require(h.content.relocations.all {it.sourceStudentId in ids || it.targetStudentId in ids}) {"Vínculo histórico de outra matrícula."}
        }
    }
}
