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

/** Semente executada na release REAL 0.7.1. Só emulador descartável com argumento explícito. */
class StableUpgradeProbeTest {
    @Test fun sameOwnerSignaturePreservesEveryRowPreferencesAndBackup() {
        val phase=InstrumentationRegistry.getArguments().getString("stableUpgradePhase")
        assumeTrue("Sonda exclusiva de atualização estável",phase in listOf("seed","check","restore"))
        check(Build.HARDWARE in setOf("ranchu","goldfish")) {"Somente emulador descartável."}
        runBlocking {
            val app=ApplicationProvider.getApplicationContext<DiaryApplication>()
            val repo=app.repository;val manager=app.backupManager
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
                app.selectionStore.write(AppSelection(2026,origin.id));file.writeBytes(manager.backupBytes())
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
                if(expected.relocations.size==1) {
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
                val stale=repo.dao.student("stable-stale")!!
                repo.repairReactivatedStudent(stale)
                assertEquals(stale.copy(left=null),repo.dao.student(stale.id))
                val preview=repo.previewRelocation(stale.id,"stable-history-target",null,"2026-10-17")
                repo.confirmRelocation(preview)
                val complete=manager.snapshot();val upgradedBackup=manager.backupBytes()
                assertEquals(expected.copy(students=complete.students,relocations=complete.relocations),complete)
                assertEquals(2,complete.relocations.size)
                assertEquals(expected.students.size+1,complete.students.size)
                assertEquals(expected.students.first {it.id=="stable-archived"},complete.students.first {it.id=="stable-archived"})
                assertEquals(expected.students.first {it.id=="stable-closed"},complete.students.first {it.id=="stable-closed"})
                val rec=complete.recompositions.first {it.studentId==stale.id}
                repo.deleteRecomposition(rec,preview.target!!.id)
                manager.restore(manager.prepare(upgradedBackup.inputStream()),"RESTAURAR")
                assertEquals(complete,manager.snapshot());file.writeBytes(roundTripExportPreferences(app,expected,bytes))
            }
        }
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
        repo.saveExportLayout("classe-alfa",emptyList(),rows)
        val configured=manager.snapshot();assertEquals(before,configured.copy(exportRows=emptyList()))
        val bytes=manager.backupBytes();assertEquals(5,BackupCodec.decode(bytes).envelope.formatVersion)
        repo.saveExportLayout("classe-alfa",rows,rows.map{it.copy(manualHidden=false)})
        manager.restore(manager.prepare(oldBytes.inputStream()),"RESTAURAR")
        assertEquals(original,manager.snapshot());assertTrue(manager.snapshot().exportRows.isEmpty())
        manager.restore(manager.prepare(bytes.inputStream()),"RESTAURAR");assertEquals(configured,manager.snapshot())
        verifyExportState(app,configured)
        return bytes
    }

}
