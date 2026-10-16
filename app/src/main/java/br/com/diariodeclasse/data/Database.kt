package br.com.diariodeclasse.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

@Entity(tableName = "classes")
data class SchoolClass(
    @PrimaryKey val id: String = newId(), val name: String, val stage: String = "Médio",
    val subject: String, val year: Int, val shift: String = "Matutino",
    val schedule: String = "0,0,0,0,0,0,0", val duration: Int = 50,
    val scale: Double = 10.0, val low: Double = 5.0, val high: Double = 7.0,
    val countJustified: Boolean = true, val startPresent: Boolean = false
)
@Entity(tableName = "students", foreignKeys = [ForeignKey(entity = SchoolClass::class,
    parentColumns = ["id"], childColumns = ["classId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("classId")])
data class Student(
    @PrimaryKey val id: String = newId(), val personId: String = newId(), val classId: String,
    val name: String, val number: Int, val joined: String, val left: String? = null,
    val status: String = "Ativo", val notes: String = ""
)
@Entity(tableName = "terms", primaryKeys = ["classId", "number"], foreignKeys = [ForeignKey(
    entity = SchoolClass::class, parentColumns = ["id"], childColumns = ["classId"], onDelete = ForeignKey.RESTRICT)])
data class Term(val classId: String, val number: Int, val start: String, val end: String)
@Entity(tableName = "lessons", foreignKeys = [ForeignKey(entity = SchoolClass::class,
    parentColumns = ["id"], childColumns = ["classId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["classId", "date", "ordinal"], unique = true)])
data class Lesson(@PrimaryKey val id: String, val classId: String, val date: String,
    val ordinal: Int, val state: String = "PREVISTA", val note: String = "")
@Entity(tableName = "marks", primaryKeys = ["lessonId", "studentId"], foreignKeys = [
    ForeignKey(entity = Lesson::class, parentColumns = ["id"], childColumns = ["lessonId"], onDelete = ForeignKey.RESTRICT),
    ForeignKey(entity = Student::class, parentColumns = ["id"], childColumns = ["studentId"], onDelete = ForeignKey.RESTRICT)
], indices = [Index("studentId")])
data class Attendance(val lessonId: String, val studentId: String, val mark: String)
@Entity(tableName = "activities", foreignKeys = [ForeignKey(entity = SchoolClass::class,
    parentColumns = ["id"], childColumns = ["classId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("classId")])
data class Activity(@PrimaryKey val id: String = newId(), val classId: String, val name: String,
    val type: String = "Prova", val date: String, val term: Int, val maximum: Double,
    val description: String = "", val notes: String = "")
@Entity(tableName = "grades", primaryKeys = ["activityId", "studentId"], foreignKeys = [
    ForeignKey(entity = Activity::class, parentColumns = ["id"], childColumns = ["activityId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = Student::class, parentColumns = ["id"], childColumns = ["studentId"], onDelete = ForeignKey.RESTRICT)
], indices = [Index("studentId")])
data class Grade(val activityId: String, val studentId: String, val value: Double?)
@Entity(tableName = "import_drafts", foreignKeys = [ForeignKey(entity = SchoolClass::class,
    parentColumns = ["id"], childColumns = ["classId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("classId")])
data class ImportDraft(@PrimaryKey val id: String = newId(), val classId: String,
    val name: String, val number: Int?, val position: Int, val page: Int,
    val reason: String = "Adicionado manualmente", val reviewed: Boolean = false,
    val allowHomonym: Boolean = false)

@Dao
interface DiaryDao {
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
    Attendance::class, Activity::class, Grade::class, ImportDraft::class], version = 1, exportSchema = true)
abstract class DiaryDatabase : RoomDatabase() {
    abstract fun dao(): DiaryDao
    companion object {
        fun open(context: Context): DiaryDatabase = Room.databaseBuilder(context, DiaryDatabase::class.java, "diario.db").build()
    }
}
