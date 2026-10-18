package br.com.diariodeclasse.data

import androidx.room.*
import kotlinx.serialization.Serializable
import java.time.LocalDate

/** Texto facultativo, sem vínculo com horários, chamadas ou atividades. */
@Entity(tableName = "lesson_records", foreignKeys = [ForeignKey(entity = SchoolClass::class,
    parentColumns = ["id"], childColumns = ["classId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["classId", "date"])])
@Serializable
data class LessonRecord(@PrimaryKey val id: String = newId(), val classId: String,
    val date: String, val title: String, val description: String)

object LessonRecords {
    fun validate(value: LessonRecord) {
        require(value.id.isNotBlank() && value.classId.isNotBlank()) { "Identificação do registro inválida." }
        require(LocalDate.parse(value.date).toString() == value.date) { "Data inválida." }
        require(value.title.isNotBlank() || value.description.isNotBlank()) { "Escreva um título ou uma descrição para salvar." }
    }
    fun list(rows: List<LessonRecord>, classId: String, from: String?, until: String?): List<LessonRecord> {
        from?.let { LocalDate.parse(it) }; until?.let { LocalDate.parse(it) }
        require(from == null || until == null || from <= until) { "O início do período deve ser anterior ou igual ao fim." }
        return rows.filter { it.classId == classId && (from == null || it.date >= from) && (until == null || it.date <= until) }
            .sortedWith(compareBy<LessonRecord> { it.date }.thenBy { it.id })
    }
}

/** A edição só chega ao banco após Salvar; rascunho serializável sobrevive à recriação da tela. */
@Serializable
data class LessonRecordDraft(val expected: LessonRecord?, val value: LessonRecord) {
    val dirty get() = expected?.let { it != value } ?: (value.title.isNotBlank() || value.description.isNotBlank())
    fun duplicate() = LessonRecordDraft(null, value.copy(id = newId()))
}
