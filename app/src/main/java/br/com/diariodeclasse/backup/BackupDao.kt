package br.com.diariodeclasse.backup

import androidx.room.*
import br.com.diariodeclasse.data.*

/** Usado somente pelo serviço de backup; nenhuma tela acessa as operações de substituição. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM classes ORDER BY id") suspend fun classes(): List<SchoolClass>
    @Query("SELECT * FROM students ORDER BY id") suspend fun students(): List<Student>
    @Query("SELECT * FROM terms ORDER BY classId, number") suspend fun terms(): List<Term>
    @Query("SELECT * FROM lessons ORDER BY id") suspend fun lessons(): List<Lesson>
    @Query("SELECT * FROM marks ORDER BY lessonId, studentId") suspend fun marks(): List<Attendance>
    @Query("SELECT * FROM activities ORDER BY id") suspend fun activities(): List<Activity>
    @Query("SELECT * FROM grades ORDER BY activityId, studentId") suspend fun grades(): List<Grade>
    @Query("SELECT * FROM import_drafts ORDER BY id") suspend fun drafts(): List<ImportDraft>
    @Query("DELETE FROM marks") suspend fun removeMarks()
    @Query("DELETE FROM grades") suspend fun removeGrades()
    @Query("DELETE FROM import_drafts") suspend fun removeDrafts()
    @Query("DELETE FROM activities") suspend fun removeActivities()
    @Query("DELETE FROM lessons") suspend fun removeLessons()
    @Query("DELETE FROM terms") suspend fun removeTerms()
    @Query("DELETE FROM students") suspend fun removeStudents()
    @Query("DELETE FROM classes") suspend fun removeClasses()
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertClasses(rows: List<SchoolClass>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertStudents(rows: List<Student>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertTerms(rows: List<Term>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertLessons(rows: List<Lesson>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertMarks(rows: List<Attendance>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertActivities(rows: List<Activity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertGrades(rows: List<Grade>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertDrafts(rows: List<ImportDraft>)
}
