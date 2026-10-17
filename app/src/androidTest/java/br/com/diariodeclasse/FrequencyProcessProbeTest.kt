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
        check(android.os.Build.HARDWARE in setOf("ranchu","goldfish")) {"Somente emulador descartável."}
        val app=ApplicationProvider.getApplicationContext<DiaryApplication>()
        val repo=app.repository
        val c=SchoolClass(id="frequency-cold-fixture",name="Turma Fictícia Reinício",subject="História",year=2026)
        val s=Student(id="frequency-cold-student",classId=c.id,name="Estudante Fictício Reinício",number=1,joined="2026-01-01")
        val activity=Activity(id="grade-cold-fixture",classId=c.id,name="Atividade Fictícia Reinício",date="2026-08-06",term=3,maximum=10.0)
        val target=c.copy(id="reactivation-cold-target",name="Destino Fictício Reinício")
        if(phase=="write") {
            val vm=DiaryViewModel(app)
            vm.act(context="Sonda sintética de reinício") {
                repo.saveClass(c,"2026-01-01");repo.saveStudent(s);repo.setDayHours(c.id,"2026-08-06",2)
                val rows=repo.dao.day(c.id,"2026-08-06");repo.mark(rows[0].id,s.id,"P");repo.mark(rows[1].id,s.id,"F")
                repo.saveActivity(activity);repo.saveGrade(activity.id,s.id,8.0)
                repo.applyActivityEdit(repo.previewActivityEdit(activity.copy(maximum=1.0)),GradeChangeMode.PROPORTIONAL)
                repo.saveRecomposition(Recomposition(s.id,3,5.0,"2026-08-20"))
                repo.saveClass(target,"2026-01-01")
                repo.saveStudent(s.copy(status="Transferido",left="2026-10-16"))
                repo.saveStudent(repo.dao.student(s.id)!!.copy(status="Ativo"))
                repo.saveExportLayout(c.id,emptyList(),listOf(ExportRow("blank:process-fixture",c.id,null,0),ExportLayout.studentRow(s,1).copy(manualHidden=true)))
            }
            vm.awaitPendingWrites()
            assertNull(vm.attendanceError.value)
            assertEquals(s,repo.dao.student(s.id))
        } else {
            val rows=repo.dao.day(c.id,"2026-08-06")
            assertEquals(2,rows.size);assertEquals("F",repo.dao.attendance(rows[1].id).single().mark)
            assertEquals(1.0,repo.dao.activity(activity.id)!!.maximum,0.0)
            assertEquals(0.8,repo.dao.activityGrades(activity.id).single().value!!,0.0)
            val active=repo.dao.student(s.id)!!
            assertEquals("Ativo",active.status);assertNull(active.left)
            val persisted=repo.dao.exportRows(c.id)
            assertEquals(2,persisted.size);assertNull(persisted.first().studentId);assertTrue(persisted.last().manualHidden)
            val before=app.backupManager.snapshot()
            val preview=repo.previewRelocation(active.id,target.id,null,"2026-10-17")
            repo.confirmRelocation(preview)
            val after=app.backupManager.snapshot()
            assertEquals(active.personId,preview.target!!.personId)
            assertEquals(before.grades,after.grades);assertEquals(before.marks,after.marks)
            assertEquals(before.recompositions,after.recompositions);assertEquals(before.exportRows,after.exportRows)
            assertEquals(2,after.students.count {it.personId==active.personId})
            assertEquals(1,after.relocations.count {it.sourceStudentId==s.id})
            val sql=app.database.openHelper.writableDatabase
            app.database.runInTransaction {
                for(id in listOf(c.id,target.id)) sql.execSQL("DELETE FROM export_rows WHERE classId=?",arrayOf(id))
                sql.execSQL("DELETE FROM relocations WHERE sourceStudentId=?",arrayOf(s.id))
                sql.execSQL("DELETE FROM recompositions WHERE studentId=?",arrayOf(s.id))
                sql.execSQL("DELETE FROM marks WHERE studentId=?",arrayOf(s.id))
                for(id in listOf(c.id,target.id)) {
                    for(t in listOf("activities","lessons","terms","students"))sql.execSQL("DELETE FROM $t WHERE classId=?",arrayOf(id))
                    sql.execSQL("DELETE FROM classes WHERE id=?",arrayOf(id))
                }
            }
        }
    }
}
