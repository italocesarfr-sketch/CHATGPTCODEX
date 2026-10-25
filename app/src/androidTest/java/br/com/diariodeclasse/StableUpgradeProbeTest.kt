package br.com.diariodeclasse

import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.reports.SchoolReports
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Semente executada na release REAL de referência. Só emulador descartável com argumento explícito. */
class StableUpgradeProbeTest {
    @Test fun sameOwnerSignaturePreservesEveryRowPreferencesAndBackup() {
        val phase=InstrumentationRegistry.getArguments().getString("stableUpgradePhase")
        assumeTrue("Sonda exclusiva de atualização estável",phase in listOf("seed","check","restore","delete-history","reopen-history","delete-student","reopen-student"))
        check(Build.HARDWARE in setOf("ranchu","goldfish")) {"Somente emulador descartável."}
        runBlocking {
            val app=ApplicationProvider.getApplicationContext<DiaryApplication>()
            val repo=app.repository;val manager=app.backupManager
            if(phase in listOf("delete-student","reopen-student")) {
                verifyStudentDeletionAfterSignedUpgrade(app,phase!!)
                return@runBlocking
            }
            if(phase in listOf("delete-history","reopen-history")) {
                verifyDeletedHistoryAfterSignedUpgrade(app,phase!!)
                return@runBlocking
            }
            val file=File(app.filesDir,"stable-upgrade-synthetic.json")
            if(phase=="seed") {
                val raw=InstrumentationRegistry.getInstrumentation().context.assets.open("baseline-v1.json").bufferedReader().use {it.readText()}
                val payload=JSONObject(raw).put("recompositions",JSONArray().apply {
                    put(JSONObject().put("studentId","alfa").put("term",1).put("value",0.0).put("date","2026-11-20"))
                    put(JSONObject().put("studentId","alfa").put("term",3).put("value",5.5).put("date","2026-11-20"))
                    put(JSONObject().put("studentId","gama").put("term",3).put("value",JSONObject.NULL).put("date","2026-11-20"))
                }).toString()
                val envelope=JSONObject().put("format","diario-de-classe-backup").put("formatVersion",3)
                    .put("applicationId","br.com.diariodeclasse").put("databaseVersion",2)
                    .put("schemaIdentityHash","32104b8df79c150ac61b09fadc450221").put("createdAt","2026-10-17T00:00:00Z")
                    .put("appVersionCode",10).put("sourceCertificateSha256",JSONObject.NULL)
                    .put("payloadSha256",BackupCodec.hash(payload.toByteArray())).put("payload",payload).toString().toByteArray()
                manager.restore(manager.prepare(envelope.inputStream()),"RESTAURAR")
                val initial=manager.snapshot();val origin=initial.classes.first {it.id=="classe-alfa"}
                val target=origin.copy(id="stable-history-target",name="Destino Fictício Atualização")
                repo.saveClass(target,"2026-01-01")
                repo.saveTerms(target.id,initial.terms.filter {it.classId==origin.id}.map {it.copy(classId=target.id)})
                // Movimento consolidado na versão antiga: todas as dez tabelas participam da prova.
                val archived=Student("stable-archived","stable-archived-person",origin.id,"Estudante Fictício Histórico",4,"2026-01-01",notes="Histórico fictício preservado")
                repo.saveStudent(archived);repo.saveGrade("atividade-1",archived.id,3.0);repo.saveGrade("atividade-2",archived.id,1.0)
                repo.setDayHours(origin.id,"2026-08-10",2)
                repo.mark("${origin.id}:2026-08-10:1",archived.id,"P");repo.mark("${origin.id}:2026-08-10:2",archived.id,"FJ")
                repo.saveRecomposition(Recomposition(archived.id,3,6.0,"2026-08-20"))
                repo.confirmRelocation(repo.previewRelocation(archived.id,target.id,null,"2026-09-01"))
                val stale=Student("stable-stale","stable-stale-person",origin.id,"Estudante Fictícia Reativada",5,"2026-01-01",notes="Mesmo ID após corrigir")
                repo.saveStudent(stale);repo.saveGrade("atividade-1",stale.id,0.0);repo.saveGrade("atividade-2",stale.id,0.0)
                repo.mark("${origin.id}:2026-08-10:1",stale.id,"P");repo.mark("${origin.id}:2026-08-10:2",stale.id,"F")
                repo.saveRecomposition(Recomposition(stale.id,3,0.0,"2026-08-20"))
                repo.saveStudent(stale.copy(status="Transferido",left="2026-10-16"))
                repo.saveStudent(repo.dao.student(stale.id)!!.copy(status="Ativo"))
                assertNull(repo.dao.student(stale.id)!!.left) // A correção 0.7.1 já está na referência real.
                // Estado legado que pode ter sido preservado da 0.7.0, ainda sem confirmação de reparo.
                repo.dao.save(repo.dao.student(stale.id)!!.copy(left="2026-10-16"))
                repo.saveStudent(stale.copy(id="stable-closed",personId="stable-closed-person",name="Estudante Fictício Encerrado",number=6,status="Transferido",left="2026-09-30"))
                app.selectionStore.write(AppSelection(2026,origin.id));val seedData=repo.snapshot.first{it.loaded}
                val seedRows=ExportLayout.normalize(listOf(ExportRow("blank:stable-seed",origin.id,null,0))+ExportLayout.rows(seedData,origin.id).reversed().map{it.copy(manualHidden=it.studentId=="alfa")})
                repo.saveExportLayout(origin.id,emptyList(),seedRows)
                repo.saveLessonRecord(LessonRecord("stable-existing-record",origin.id,"2026-08-12","Título Fictício Legado","Descrição da 0.10.0\nMantida na atualização visual."))
                repo.saveLessonRecord(LessonRecord("stable-existing-second",target.id,"2026-08-12","Registro Fictício Arquivado","Também preservado no novo calendário."))
                repo.archiveClass("stable-history-target",true)
                // A semente roda na release 0.15.0 real e inclui a auditoria já distribuída.
                val external=Student("stable-external-corrected","stable-external-person",origin.id,"Estudante Fictícia Correção",7,"2026-01-01")
                repo.saveStudent(external)
                val externalLesson=Lesson("stable-external-lesson",origin.id,"2026-10-20",1,"REALIZADA")
                repo.dao.save(externalLesson);repo.dao.save(Attendance(externalLesson.id,external.id,"FJ"))
                repo.confirmRelocation(repo.previewRelocation(external.id,null,"Destino externo fictício","2026-10-25"))
                repo.confirmRelocationCorrection(repo.previewRelocationCorrection(external.id,"2026-10-17","Destino externo corrigido",setOf(externalLesson.id)))
                // A release 0.17.3 original lê o legado 9º; não o cadastra pelo editor comum.
                // Fixture histórica inserida pelo DAO, como em um banco que veio da 0.17.0.
                val originalSlots=listOf(1,2,9).map{WeeklySlot(classId=origin.id,weekday=4,ordinal=it,className=origin.name,subject=origin.subject,shift=origin.shift)}
                repo.dao.insertTimetable(WeeklyTimetable(year=2026,effective="2026-10-01",revision=1,slots=originalSlots))
                repo.confirmTimetable(repo.previewTimetable(2026,"2026-12-01",emptyList()))
                // Dois cadastros independentes semeados NA release anterior, antes da atualização.
                for(suffix in listOf("kept","wrong")) {
                    val school=origin.copy(id="stable-delete-$suffix-class",name="Turma Fictícia Duplicidade $suffix")
                    repo.saveClass(school,"2026-01-01")
                    val enrollment=Student("stable-delete-$suffix","stable-delete-person-$suffix",school.id,"Estudante Fictício Homônimo Atualização",1,"2026-01-01")
                    repo.saveStudent(enrollment)
                    val activity=Activity("stable-delete-$suffix-score",school.id,"Atividade Fictícia Duplicidade",date="2026-08-10",term=3,maximum=10.0)
                    repo.saveActivity(activity);repo.saveGrade(activity.id,enrollment.id,0.0)
                    repo.setDayHours(school.id,"2026-08-10",1);repo.mark("${school.id}:2026-08-10:1",enrollment.id,"FJ")
                    repo.saveRecomposition(Recomposition(enrollment.id,3,5.0,"2026-08-20"))
                }
                file.writeBytes(manager.backupBytes())
            }
            val bytes=file.readBytes();val expected=BackupCodec.decode(bytes).snapshot
            assertEquals(expected,manager.snapshot()) // Atualização/leitura nunca reativam automaticamente.
            assertEquals(expected.selection,app.selectionStore.read())
            assertEquals("Noctua",app.packageManager.getApplicationLabel(app.applicationInfo).toString())
            assertEquals("Transferido",repo.dao.student("stable-closed")!!.status)
            assertEquals("2026-09-30",repo.dao.student("stable-closed")!!.left)
            assertEquals("Remanejado",repo.dao.student("stable-archived")!!.status)
            if(phase!="seed") {
                verifyExportState(app,expected)
                val data=repo.snapshot.first {it.loaded};val school=data.classes.first {it.id=="classe-alfa"}
                val student=data.students.first {it.id=="alfa"};val reports=SchoolReports(data,school)
                assertEquals(1.5,reports.student(student,3).original!!,0.0)
                assertEquals(5.5,reports.student(student,3).total!!,0.0)
                assertEquals(4,reports.year(student).terms.size)
                assertEquals(1,reports.student(student,1).attendance!!.effectiveAbsences)
                val oldLink=expected.relocations.first {it.sourceStudentId=="stable-archived"}
                val archivedTarget=data.students.first {it.id==oldLink.targetStudentId}
                val destination=data.classes.first {it.id==archivedTarget.classId}
                assertEquals(6.0,SchoolReports(data,destination).student(archivedTarget,3).total!!,0.0)
                assertEquals(2,data.lineage(archivedTarget).size)
                if(expected.relocations.none {it.sourceStudentId=="stable-stale"}) {
                    assertEquals("Ativo",repo.dao.student("stable-stale")!!.status)
                    assertEquals("2026-10-16",repo.dao.student("stable-stale")!!.left)
                } else {
                    val moved=expected.relocations.first {it.sourceStudentId=="stable-stale"}
                    val targetStudent=data.students.first {it.id==moved.targetStudentId}
                    assertEquals(0.0,SchoolReports(data,destination).student(targetStudent,1).original!!,0.0)
                    assertEquals(0.0,data.periodRecomposition(targetStudent,3)!!.value!!,0.0)
                    assertEquals(1,SchoolReports(data,destination).year(targetStudent).frequency.absent)
                }
            }
            if(phase=="restore") {
                repo.saveGrade("atividade-1","alfa",7.0)
                repo.deleteRecomposition(expected.recompositions.first {it.studentId=="alfa"&&it.term==1})
                assertNotEquals(expected,manager.snapshot())
                manager.restore(manager.prepare(bytes.inputStream()),"RESTAURAR")
                assertEquals(expected,manager.snapshot());assertTrue(manager.hasSafetyCopy())
                repo.archiveClass("stable-history-target",false)
                val stale=repo.dao.student("stable-stale")!!
                repo.repairReactivatedStudent(stale)
                assertEquals(stale.copy(left=null),repo.dao.student(stale.id))
                val preview=repo.previewRelocation(stale.id,"stable-history-target",null,"2026-10-17")
                repo.confirmRelocation(preview)
                val complete=manager.snapshot();val upgradedBackup=manager.backupBytes()
                assertEquals(expected.copy(students=complete.students,relocations=complete.relocations,classArchives=emptyList()),complete)
                assertEquals(expected.relocations.size+1,complete.relocations.size)
                assertEquals(expected.students.size+1,complete.students.size)
                assertEquals(expected.students.first {it.id=="stable-archived"},complete.students.first {it.id=="stable-archived"})
                assertEquals(expected.students.first {it.id=="stable-closed"},complete.students.first {it.id=="stable-closed"})
                val rec=complete.recompositions.first {it.studentId==stale.id}
                repo.deleteRecomposition(rec,preview.target!!.id)
                manager.restore(manager.prepare(upgradedBackup.inputStream()),"RESTAURAR")
                assertEquals(complete,manager.snapshot());roundTripExportPreferences(app,expected,bytes)
                repo.archiveClass("stable-history-target",false)
                val conversion=repo.previewCorrectionDestination("stable-external-corrected","2026-10-17","","stable-history-target")
                repo.confirmRelocationCorrection(conversion)
                val converted=manager.snapshot().relocations.first{it.sourceStudentId=="stable-external-corrected"}
                assertEquals(2,converted.corrections.size)
                assertEquals("CORRECAO_EXTERNA",converted.corrections.first().operation)
                assertEquals("CONVERSAO_INTERNA",converted.corrections.last().operation)
                assertEquals("FJ",converted.corrections.first().removed.single().attendance.mark)
                assertNotNull(converted.targetStudentId);assertNull(converted.externalTarget)
                // Corrige a grade antiga de outubro após atualizar a release 0.17.1 original.
                val source=repo.dao.schoolClass("classe-alfa")!!
                val slots=(1..2).map { WeeklySlot(classId=source.id,weekday=4,ordinal=it,className=source.name,subject=source.subject,shift=source.shift) }
                val oldSchedules=manager.snapshot().timetables
                val annual=Timetables.annualPreview(manager.snapshot().diarySnapshot(),2026,slots)
                repo.confirmTimetable(annual)
                assertTrue(manager.snapshot().timetables.containsAll(oldSchedules))
                assertEquals(2,Timetables.planned(manager.snapshot().diarySnapshot(),source.id,"2026-12-31").size)
                val scheduled=manager.snapshot();val scheduleBytes=manager.backupBytes()
                repo.confirmTimetable(repo.previewTimetable(2026,"2026-11-01",emptyList()))
                manager.restore(manager.prepare(scheduleBytes.inputStream()),"RESTAURAR")
                assertEquals(scheduled,manager.snapshot())
                assertEquals(2,Timetables.planned(manager.snapshot().diarySnapshot(),source.id,"2026-08-06").size)
                file.writeBytes(manager.backupBytes())
            }
        }
    }
    private suspend fun verifyStudentDeletionAfterSignedUpgrade(app:DiaryApplication,phase:String) {
        val repo=app.repository;val manager=app.backupManager
        val file=File(app.filesDir,"stable-deleted-student.json")
        if(phase=="delete-student") {
            val before=manager.snapshot();val data=before.diarySnapshot()
            val expected=StudentDeletion.without(data,"stable-delete-wrong")
            val plan=repo.previewStudentDeletion("stable-delete-wrong","stable-delete-wrong-class")
            assertNull(plan.blockedReason);assertEquals(1,plan.counts["Atividades com notas lançadas"])
            assertEquals(1,plan.counts["Registros de frequência"]);assertEquals(1,plan.counts["Recomposições com nota"])
            repo.deleteStudent(plan)
            assertEquals(ClassLifecycle.fingerprint(expected),ClassLifecycle.fingerprint(manager.snapshot().diarySnapshot()))
            assertEquals(before.students.first{it.id=="stable-delete-kept"},repo.dao.student("stable-delete-kept"))
            file.writeBytes(manager.backupBytes())
            repo.saveGrade("stable-delete-kept-score","stable-delete-kept",9.0)
            manager.restore(manager.prepare(file.readBytes().inputStream()),"RESTAURAR")
        }
        assertEquals(BackupCodec.decode(file.readBytes()).snapshot,manager.snapshot())
        assertNull(repo.dao.student("stable-delete-wrong"));assertNotNull(repo.dao.student("stable-delete-kept"))
        assertEquals(0.0,manager.snapshot().grades.first{it.studentId=="stable-delete-kept"}.value!!,0.0)
        assertEquals(2,manager.snapshot().histories.size)
    }
    private suspend fun verifyDeletedHistoryAfterSignedUpgrade(app:DiaryApplication,phase:String) {
        val repo=app.repository;val manager=app.backupManager
        val state=File(app.filesDir,"stable-deleted-history.json")
        if(phase=="delete-history") {
            val previous=manager.snapshot()
            val fixture=BackupCodec.json.decodeFromString<BackupSnapshot>(InstrumentationRegistry.getInstrumentation().context.assets.open("history-deletion.json").bufferedReader().use {it.readText()})
            val seeded=previous.copy(classes=previous.classes+fixture.classes,students=previous.students+fixture.students,
                terms=previous.terms+fixture.terms,lessons=previous.lessons+fixture.lessons,marks=previous.marks+fixture.marks,
                activities=previous.activities+fixture.activities,grades=previous.grades+fixture.grades,
                recompositions=previous.recompositions+fixture.recompositions,relocations=previous.relocations+fixture.relocations)
            val bytes=BackupCodec.encode(seeded,BuildConfig.VERSION_CODE,null)
            manager.restore(manager.prepare(bytes.inputStream()),"RESTAURAR")
            for(id in listOf("history-x","history-g")) {
                val p=repo.previewClassDeletion(id);assertNull(p.blockedReason);repo.deleteClass(p,p.confirmation)
            }
            val now=manager.snapshot()
            val previousTables:List<Pair<List<*>,List<*>>> = listOf(previous.classes to now.classes,previous.students to now.students,
                previous.terms to now.terms,previous.lessons to now.lessons,previous.marks to now.marks,
                previous.activities to now.activities,previous.grades to now.grades,previous.recompositions to now.recompositions,
                previous.relocations to now.relocations,previous.histories to now.histories)
            previousTables.forEach {(old,current)->assertTrue("Registros anteriores à exclusão devem permanecer íntegros",old.all {row->current.any {it==row}})}
            assertEquals(previous,now.copy(classes=previous.classes,students=previous.students,terms=previous.terms,
                lessons=previous.lessons,marks=previous.marks,activities=previous.activities,grades=previous.grades,
                recompositions=previous.recompositions,relocations=previous.relocations,histories=previous.histories))
            state.writeBytes(manager.backupBytes())
            repo.saveGrade("hh-score","hh",0.0)
            manager.restore(manager.prepare(state.readBytes().inputStream()),"RESTAURAR")
        }
        val expected=BackupCodec.decode(state.readBytes()).snapshot
        assertEquals(expected,manager.snapshot());assertEquals(2,expected.histories.size)
        assertNull(repo.dao.schoolClass("history-x"));assertNull(repo.dao.schoolClass("history-g"))
        val data=repo.snapshot.first {it.loaded};val target=repo.dao.student("hh")!!
        val reports=SchoolReports(data,repo.dao.schoolClass("history-h")!!)
        assertEquals(4.5,reports.student(target,3).original!!,0.0);assertEquals(6.0,reports.student(target,3).total!!,0.0)
        assertEquals(8,reports.student(target,3).attendance!!.performed);assertEquals(3,data.lineage(target).size)
    }
    private suspend fun verifyExportState(app:DiaryApplication, expected:BackupSnapshot) {
        val data=app.repository.snapshot.first{it.loaded}
        assertEquals(expected.exportRows,data.exportRows)
        val slots=ExportLayout.project(data,ExportLayout.rows(data,"classe-alfa"))
        val alfa=slots.first{it.student?.id=="alfa"}
        if(expected.exportRows.isEmpty()) assertTrue(alfa.included)
        else {
            assertTrue(slots.first().blank);assertTrue(alfa.row.manualHidden);assertFalse(alfa.included)
        }
        val origin=slots.first{it.student?.id=="stable-archived"}
        assertTrue(origin.automaticallyExcluded);assertFalse(origin.row.manualHidden)
        assertTrue(ExportLayout.project(data,ExportLayout.rows(data,"stable-history-target")).all{it.included})
    }
    private suspend fun roundTripExportPreferences(app:DiaryApplication, original:BackupSnapshot, oldBytes:ByteArray):ByteArray {
        val repo=app.repository;val manager=app.backupManager
        val data=repo.snapshot.first{it.loaded};val before=manager.snapshot()
        val rows=ExportLayout.normalize(listOf(ExportLayout.blank("classe-alfa"))+ExportLayout.rows(data,"classe-alfa").reversed().map{it.copy(manualHidden=it.studentId=="alfa")})
        repo.saveExportLayout("classe-alfa",before.exportRows.filter{it.classId=="classe-alfa"},rows)
        val configured=manager.snapshot();assertEquals(before,configured.copy(exportRows=before.exportRows))
        val bytes=manager.backupBytes();assertEquals(11,BackupCodec.decode(bytes).envelope.formatVersion)
        repo.saveExportLayout("classe-alfa",rows,rows.map{it.copy(manualHidden=false)})
        manager.restore(manager.prepare(oldBytes.inputStream()),"RESTAURAR")
        assertEquals(original,manager.snapshot());assertEquals(original.exportRows,manager.snapshot().exportRows)
        manager.restore(manager.prepare(bytes.inputStream()),"RESTAURAR");assertEquals(configured,manager.snapshot())
        verifyExportState(app,configured)
        repo.archiveClass("stable-history-target",true)
        assertEquals(configured,manager.snapshot().copy(classArchives=configured.classArchives))
        val archivedBytes=manager.backupBytes();val archived=manager.snapshot()
        repo.archiveClass("stable-history-target",false)
        manager.restore(manager.prepare(archivedBytes.inputStream()),"RESTAURAR")
        assertEquals(archived,manager.snapshot())
        val table=br.com.diariodeclasse.export.ExportProjection.build(repo.snapshot.first{it.loaded},configured.classes.first{it.id=="classe-alfa"},br.com.diariodeclasse.export.ExportOptions(term=3))
        assertFalse(table.records.any{it.enrollmentId=="alfa"})
        val note=LessonRecord(id="stable-record",classId="classe-alfa",date="2026-08-12",title="Título Fictício Atualização",description="Descrição livre\nSem chamada nem horário.")
        repo.saveLessonRecord(note)
        val noted=manager.snapshot();val noteBytes=manager.backupBytes()
        assertEquals(archived,noted.copy(lessonRecords=archived.lessonRecords))
        repo.saveLessonRecord(note.copy(title="Correção fictícia"),note)
        manager.restore(manager.prepare(noteBytes.inputStream()),"RESTAURAR")
        assertEquals(noted,manager.snapshot())
        assertEquals(note,repo.dao.lessonRecord(note.id))
        return noteBytes
    }

}
