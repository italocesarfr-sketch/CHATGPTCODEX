package br.com.diariodeclasse
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.ui.DiaryViewModel
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Executado separadamente pelo CI, com force-stop entre write e read. */
@RunWith(AndroidJUnit4::class)
class FrequencyProcessProbeTest {
    @Test fun committedLastCellSurvivesProcessRestart()=runBlocking {
        val phase=InstrumentationRegistry.getArguments().getString("frequencyProbePhase")
        Assume.assumeTrue(phase=="write"||phase=="read")
        val app=ApplicationProvider.getApplicationContext<DiaryApplication>()
        val repo=app.repository
        val c=SchoolClass(id="frequency-cold-fixture",name="Turma Fictícia Reinício",subject="História",year=2026)
        val s=Student(id="frequency-cold-student",classId=c.id,name="Estudante Fictício Reinício",number=1,joined="2026-01-01")
        if(phase=="write") {
            val vm=DiaryViewModel(app)
            vm.act(context="Sonda sintética de reinício") {
                repo.saveClass(c,"2026-01-01");repo.saveStudent(s);repo.setDayHours(c.id,"2026-08-06",2)
                val rows=repo.dao.day(c.id,"2026-08-06");repo.mark(rows[0].id,s.id,"P");repo.mark(rows[1].id,s.id,"F")
            }
            vm.awaitPendingWrites()
            assertNull(vm.attendanceError.value)
        } else {
            val rows=repo.dao.day(c.id,"2026-08-06")
            assertEquals(2,rows.size);assertEquals("F",repo.dao.attendance(rows[1].id).single().mark)
            val sql=app.database.openHelper.writableDatabase
            app.database.runInTransaction {
                sql.execSQL("DELETE FROM marks WHERE studentId=?",arrayOf(s.id))
                for(t in listOf("lessons","terms","students"))sql.execSQL("DELETE FROM $t WHERE classId=?",arrayOf(c.id))
                sql.execSQL("DELETE FROM classes WHERE id=?",arrayOf(c.id))
            }
        }
    }
}
