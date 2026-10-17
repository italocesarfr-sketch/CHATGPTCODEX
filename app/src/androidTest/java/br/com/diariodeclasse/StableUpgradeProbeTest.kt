package br.com.diariodeclasse

import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.*
import org.json.JSONObject
import org.json.JSONArray
import br.com.diariodeclasse.reports.SchoolReports
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Semente compatível com Noctua 0.6.1. Só executa no emulador descartável com argumento explícito. */
class StableUpgradeProbeTest {
    @Test fun sameOwnerSignaturePreservesEveryRowPreferencesAndBackup() {
        val phase=InstrumentationRegistry.getArguments().getString("stableUpgradePhase")
        assumeTrue("Sonda exclusiva de atualização estável",phase in listOf("seed","check","restore"))
        check(Build.HARDWARE in setOf("ranchu","goldfish")) {"Somente emulador descartável."}
        runBlocking {
            val app=ApplicationProvider.getApplicationContext<DiaryApplication>()
            val manager=app.backupManager
            val file=File(app.filesDir,"stable-upgrade-synthetic.json")
            if(phase=="seed") {
                // JSON do formato distribuído; não invoca construtores/copy ampliados na versão nova.
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
                app.selectionStore.write(AppSelection(2026,"classe-alfa"))
                file.writeBytes(manager.backupBytes())
            }
            val bytes=file.readBytes();val expected=BackupCodec.decode(bytes).snapshot
            assertEquals(expected,manager.snapshot())
            assertEquals(expected.selection,app.selectionStore.read())
            assertEquals("Noctua",app.packageManager.getApplicationLabel(app.applicationInfo).toString())
            if(phase!="seed") {
                val data=app.repository.snapshot.first {it.loaded}
                val school=data.classes.first {it.id=="classe-alfa"};val student=data.students.first {it.id=="alfa"}
                val reports=SchoolReports(data,school)
                assertEquals(1.5,reports.student(student,3).original!!,0.0)
                assertEquals(expected.recompositions.first {it.studentId=="alfa"&&it.term==3}.value!!,reports.student(student,3).total!!,0.0)
                assertEquals(4,reports.year(student).terms.size)
                assertEquals(1,reports.student(student,1).attendance!!.effectiveAbsences)
                if(expected.relocations.isNotEmpty()) {
                    val target=data.students.first {it.id==expected.relocations.single().targetStudentId}
                    val destination=data.classes.first {it.id==target.classId}
                    assertEquals(5.0,SchoolReports(data,destination).student(target,3).total!!,0.0)
                    assertEquals(2,data.lineage(target).size)
                }
            }
            if(phase=="restore") {
                app.repository.saveGrade("atividade-1","alfa",7.0)
                app.repository.deleteRecomposition(expected.recompositions.first())
                assertNotEquals(expected,manager.snapshot())
                manager.restore(manager.prepare(bytes.inputStream()),"RESTAURAR")
                assertEquals(expected,manager.snapshot())
                assertTrue(manager.hasSafetyCopy())
                // Correção explícita posterior à prova de preservação integral da versão 0.6.1.
                val previous=expected.recompositions.first {it.studentId=="alfa"&&it.term==3}
                app.repository.saveRecomposition(previous.copy(value=5.0),previous)
                val beforeMove=manager.snapshot()
                val school=beforeMove.classes.first {it.id=="classe-alfa"}
                val targetClass=school.copy(id="stable-relocation-target",name="Destino Fictício Atualização")
                app.repository.saveClass(targetClass,"2026-01-01")
                app.repository.saveTerms(targetClass.id,beforeMove.terms.filter {it.classId==school.id}.map {it.copy(classId=targetClass.id)})
                val preview=app.repository.previewRelocation("alfa",targetClass.id,null,"2026-12-01")
                app.repository.confirmRelocation(preview)
                val destination=preview.target!!
                val data=app.repository.snapshot.first {it.loaded && it.relocations.isNotEmpty()}
                assertEquals(5.0,SchoolReports(data,targetClass).student(destination,3).total!!,0.0)
                assertEquals(1,SchoolReports(data,targetClass).year(destination).frequency.absent)
                val complete=manager.snapshot();val upgradedBackup=manager.backupBytes()
                app.repository.deleteRecomposition(complete.recompositions.first(),destination.id)
                manager.restore(manager.prepare(upgradedBackup.inputStream()),"RESTAURAR")
                assertEquals(complete,manager.snapshot())
                assertEquals(expected.grades,complete.grades);assertEquals(expected.marks,complete.marks)
                assertEquals(expected.activities,complete.activities)
                assertEquals(expected.students.filter {it.id!="alfa"},complete.students.filter {it.id!="alfa"&&it.id!=destination.id})
                assertEquals(1,complete.relocations.size)
                file.writeBytes(upgradedBackup)
            }
        }
    }
}
