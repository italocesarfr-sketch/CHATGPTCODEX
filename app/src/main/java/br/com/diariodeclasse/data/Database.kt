package br.com.diariodeclasse.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import kotlinx.serialization.Serializable
import br.com.diariodeclasse.backup.BackupDao

fun newId(): String = UUID.randomUUID().toString()

@Entity(tableName = "classes")
@Serializable
data class SchoolClass(
    @PrimaryKey val id: String = newId(), val name: String, val stage: String = "Médio",
    val subject: String, val year: Int, val shift: String = "Matutino",
    val schedule: String = "0,0,0,0,0,0,0", val duration: Int = 50,
    val scale: Double = 10.0, val low: Double = 5.0, val high: Double = 7.0,
    val countJustified: Boolean = true, val startPresent: Boolean = false
)
@Entity(tableName = "students", foreignKeys = [ForeignKey(entity = SchoolClass::class,
    parentColumns = ["id"], childColumns = ["classId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("classId")])
@Serializable
data class Student(
    @PrimaryKey val id: String = newId(), val personId: String = newId(), val classId: String,
    val name: String, val number: Int, val joined: String, val left: String? = null,
    val status: String = "Ativo", val notes: String = ""
)
@Entity(tableName = "terms", primaryKeys = ["classId", "number"], foreignKeys = [ForeignKey(
    entity = SchoolClass::class, parentColumns = ["id"], childColumns = ["classId"], onDelete = ForeignKey.RESTRICT)])
@Serializable
data class Term(val classId: String, val number: Int, val start: String, val end: String)
@Entity(tableName = "lessons", foreignKeys = [ForeignKey(entity = SchoolClass::class,
    parentColumns = ["id"], childColumns = ["classId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["classId", "date", "ordinal"], unique = true)])
@Serializable
data class Lesson(@PrimaryKey val id: String, val classId: String, val date: String,
    val ordinal: Int, val state: String = "PREVISTA", val note: String = "")
@Entity(tableName = "marks", primaryKeys = ["lessonId", "studentId"], foreignKeys = [
    ForeignKey(entity = Lesson::class, parentColumns = ["id"], childColumns = ["lessonId"], onDelete = ForeignKey.RESTRICT),
    ForeignKey(entity = Student::class, parentColumns = ["id"], childColumns = ["studentId"], onDelete = ForeignKey.RESTRICT)
], indices = [Index("studentId")])
@Serializable
data class Attendance(val lessonId: String, val studentId: String, val mark: String)
@Entity(tableName = "activities", foreignKeys = [ForeignKey(entity = SchoolClass::class,
    parentColumns = ["id"], childColumns = ["classId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("classId")])
@Serializable
data class Activity(@PrimaryKey val id: String = newId(), val classId: String, val name: String,
    val type: String = "Prova", val date: String, val term: Int, val maximum: Double,
    val description: String = "", val notes: String = "")
@Entity(tableName = "grades", primaryKeys = ["activityId", "studentId"], foreignKeys = [
    ForeignKey(entity = Activity::class, parentColumns = ["id"], childColumns = ["activityId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = Student::class, parentColumns = ["id"], childColumns = ["studentId"], onDelete = ForeignKey.RESTRICT)
], indices = [Index("studentId")])
@Serializable
data class Grade(val activityId: String, val studentId: String, val value: Double?)
@Entity(tableName = "import_drafts", foreignKeys = [ForeignKey(entity = SchoolClass::class,
    parentColumns = ["id"], childColumns = ["classId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("classId")])
@Serializable
data class ImportDraft(@PrimaryKey val id: String = newId(), val classId: String,
    val name: String, val number: Int?, val position: Int, val page: Int,
    val reason: String = "Adicionado manualmente", val reviewed: Boolean = false,
    val allowHomonym: Boolean = false)

/** Uma recomposição por matrícula e bimestre de origem. Data não define o bimestre afetado. */
@Entity(tableName = "recompositions", primaryKeys = ["studentId", "term"], foreignKeys = [
    ForeignKey(entity = Student::class, parentColumns = ["id"], childColumns = ["studentId"], onDelete = ForeignKey.RESTRICT)])
@Serializable
data class Recomposition(val studentId: String, val term: Int, val value: Double?, val date: String)

/** Vínculo dirigido entre matrículas. Registros escolares continuam com seus IDs de origem. */
@Entity(tableName = "relocations", foreignKeys = [
    ForeignKey(entity = Student::class, parentColumns = ["id"], childColumns = ["sourceStudentId"], onDelete = ForeignKey.RESTRICT),
    ForeignKey(entity = Student::class, parentColumns = ["id"], childColumns = ["targetStudentId"], onDelete = ForeignKey.RESTRICT)
], indices = [Index(value = ["targetStudentId"], unique = true)])
@Serializable
data class Relocation(@PrimaryKey val sourceStudentId: String, val targetStudentId: String?,
    val effectiveDate: String, val externalTarget: String? = null)

@Dao
interface DiaryDao {
    @Query("SELECT * FROM lesson_records WHERE id = :id") suspend fun lessonRecord(id: String): LessonRecord?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertRecord(value: LessonRecord)
    @Update suspend fun updateRecord(value: LessonRecord): Int
    @Query("DELETE FROM lesson_records WHERE id = :id") suspend fun removeRecord(id: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertArchive(value: ClassArchive)
    @Query("DELETE FROM class_archives WHERE classId = :classId") suspend fun removeArchive(classId: String)

    @Query("SELECT * FROM export_rows WHERE classId = :classId ORDER BY position") suspend fun exportRows(classId: String): List<ExportRow>
    @Query("DELETE FROM export_rows WHERE classId = :classId") suspend fun removeExportRows(classId: String)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertExportRows(rows: List<ExportRow>)
    @Query("SELECT * FROM classes ORDER BY year DESC, name") fun classes(): Flow<List<SchoolClass>>
    @Query("SELECT * FROM students ORDER BY number, name") fun students(): Flow<List<Student>>
    @Query("SELECT * FROM terms ORDER BY number") fun terms(): Flow<List<Term>>
    @Query("SELECT * FROM lessons ORDER BY date, ordinal") fun lessons(): Flow<List<Lesson>>
    @Query("SELECT * FROM marks") fun marks(): Flow<List<Attendance>>
    @Query("SELECT * FROM activities ORDER BY date, name") fun activities(): Flow<List<Activity>>
    @Query("SELECT * FROM grades") fun grades(): Flow<List<Grade>>
    @Query("SELECT * FROM import_drafts ORDER BY position") fun drafts(): Flow<List<ImportDraft>>
    @Query("SELECT * FROM classes WHERE id = :id") suspend fun schoolClass(id: String): SchoolClass?
    @Query("SELECT * FROM students WHERE classId = :classId ORDER BY number, name") suspend fun roster(classId: String): List<Student>
    @Query("SELECT * FROM students WHERE id = :id") suspend fun student(id: String): Student?
    @Query("SELECT * FROM terms WHERE classId = :classId ORDER BY number") suspend fun periods(classId: String): List<Term>
    @Query("SELECT * FROM lessons WHERE id = :id") suspend fun lesson(id: String): Lesson?
    @Query("SELECT * FROM lessons WHERE classId = :classId AND date = :date ORDER BY ordinal") suspend fun day(classId: String, date: String): List<Lesson>
    @Query("SELECT * FROM marks WHERE lessonId = :lessonId") suspend fun attendance(lessonId: String): List<Attendance>
    @Query("SELECT * FROM activities WHERE id = :id") suspend fun activity(id: String): Activity?
    @Query("SELECT * FROM grades WHERE activityId = :id") suspend fun activityGrades(id: String): List<Grade>
    @Query("SELECT * FROM import_drafts WHERE classId = :classId ORDER BY position") suspend fun draftRows(classId: String): List<ImportDraft>
    @Query("SELECT * FROM recompositions ORDER BY studentId, term") fun recompositions(): Flow<List<Recomposition>>
    @Query("SELECT * FROM recompositions WHERE studentId = :studentId AND term = :term") suspend fun recomposition(studentId: String, term: Int): Recomposition?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertRelocation(value: Relocation)
    @Update suspend fun updateHistory(value: PreservedHistory): Int
    @Upsert suspend fun saveHistories(values: List<PreservedHistory>)
    @Query("DELETE FROM preserved_histories WHERE classId = :classId") suspend fun removeHistory(classId: String)
    @Upsert suspend fun save(value: Recomposition)
    @Query("DELETE FROM recompositions WHERE studentId = :studentId AND term = :term") suspend fun deleteRecomposition(studentId: String, term: Int)
    @Upsert suspend fun save(value: SchoolClass)
    @Upsert suspend fun save(value: Student)
    @Upsert suspend fun save(value: Term)
    @Upsert suspend fun save(value: Lesson)
    @Upsert suspend fun save(value: Attendance)
    @Upsert suspend fun save(value: Activity)
    @Upsert suspend fun save(value: Grade)
    @Upsert suspend fun save(value: ImportDraft)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertLessons(values: List<Lesson>)
    @Query("DELETE FROM lessons WHERE classId = :classId AND date >= :from AND state = 'PREVISTA' AND note = '' AND id NOT IN (SELECT lessonId FROM marks)")
    suspend fun removeUnrecordedPlans(classId: String, from: String)
    @Query("DELETE FROM activities WHERE id = :id") suspend fun deleteActivity(id: String)
    @Query("DELETE FROM import_drafts WHERE id = :id") suspend fun deleteDraft(id: String)
    @Query("DELETE FROM import_drafts WHERE classId = :classId") suspend fun clearDraft(classId: String)
}

@Database(entities = [SchoolClass::class, Student::class, Term::class, Lesson::class,
    Attendance::class, Activity::class, Grade::class, ImportDraft::class, Recomposition::class, Relocation::class, ExportRow::class, ClassArchive::class, LessonRecord::class, PreservedHistory::class], version = 7, exportSchema = true)
@TypeConverters(HistoricalConverters::class)
abstract class DiaryDatabase : RoomDatabase() {
    abstract fun dao(): DiaryDao
    abstract fun backupDao(): BackupDao
    companion object {
        fun open(context: Context): DiaryDatabase = Room.databaseBuilder(context, DiaryDatabase::class.java, "diario.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7).build()
    }
}
