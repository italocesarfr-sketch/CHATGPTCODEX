package br.com.diariodeclasse.data

import androidx.room.*
import kotlinx.serialization.Serializable

/** Somente disposição de exportação. Uma linha sem studentId é um espaço explícito. */
@Entity(tableName = "export_rows", foreignKeys = [
    ForeignKey(entity = SchoolClass::class, parentColumns = ["id"], childColumns = ["classId"], onDelete = ForeignKey.RESTRICT),
    ForeignKey(entity = Student::class, parentColumns = ["id"], childColumns = ["studentId"], onDelete = ForeignKey.RESTRICT)
], indices = [Index(value = ["classId", "position"], unique = true), Index(value = ["studentId"], unique = true)])
@Serializable
data class ExportRow(@PrimaryKey val id: String, val classId: String, val studentId: String?,
    val position: Int, val manualHidden: Boolean = false)

data class ExportSlot(val row: ExportRow, val student: Student?, val automaticallyExcluded: Boolean) {
    val included: Boolean get() = !row.manualHidden && !automaticallyExcluded
    val blank: Boolean get() = row.studentId == null
}

/** Projeção compartilhada para a futura exportação; não filtra nem recalcula registros escolares. */
object ExportLayout {
    fun studentRow(student: Student, position: Int) = ExportRow("student:${student.id}", student.classId, student.id, position)
    fun blank(classId: String) = ExportRow("blank:${newId()}", classId, null, 0)
    fun normalize(rows: List<ExportRow>) = rows.mapIndexed { index, row -> row.copy(position = index) }
    fun rows(data: DiarySnapshot, classId: String): List<ExportRow> {
        val saved = data.exportRows.filter { it.classId == classId }.sortedBy { it.position }
        val known = saved.mapNotNull { it.studentId }.toSet()
        val appended = data.roster(classId).filter { it.id !in known }
            .sortedWith(compareBy<Student> { it.number }.thenBy { it.name }.thenBy { it.id })
            .mapIndexed { index, student -> studentRow(student, saved.size + index) }
        return saved + appended
    }
    fun project(data: DiarySnapshot, rows: List<ExportRow>): List<ExportSlot> {
        val students = data.students.associateBy { it.id }
        val moved = data.relocations.map { it.sourceStudentId }.toSet()
        return rows.map { row ->
            val student = row.studentId?.let { requireNotNull(students[it]) { "Matrícula de exportação não encontrada." } }
            ExportSlot(row, student, student?.let { it.status == "Remanejado" || it.id in moved } ?: false)
        }
    }
    fun validate(classes: List<SchoolClass>, students: List<Student>, rows: List<ExportRow>) {
        require(rows.map { it.id }.distinct().size == rows.size) { "Linhas de exportação repetidas." }
        require(rows.mapNotNull { it.studentId }.distinct().size == rows.count { it.studentId != null }) { "Matrícula repetida na disposição de exportação." }
        val byStudent = students.associateBy { it.id }; val classIds = classes.map { it.id }.toSet()
        rows.forEach { row ->
            require(row.classId in classIds && row.position >= 0) { "Turma ou posição de exportação inválida." }
            if (row.studentId == null) require(row.id.startsWith("blank:") && row.id.length > 6 && !row.manualHidden) { "Linha vazia inválida." }
            else require(row.id == "student:${row.studentId}" && byStudent[row.studentId]?.classId == row.classId) { "A linha deve referenciar a matrícula da própria turma." }
        }
        rows.groupBy { it.classId }.values.forEach { list ->
            require(list.map { it.position }.sorted() == list.indices.toList()) { "Posições de exportação repetidas ou incompletas." }
        }
    }
}
