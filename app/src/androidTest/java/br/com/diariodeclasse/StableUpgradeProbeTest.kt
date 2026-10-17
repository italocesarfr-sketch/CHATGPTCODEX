package br.com.diariodeclasse

import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.AppSelection
import br.com.diariodeclasse.data.Recomposition
import br.com.diariodeclasse.reports.SchoolReports
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** API compatível com Noctua 0.6.0. Só executa no emulador descartável com argumento explícito. */
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
                val fixture=InstrumentationRegistry.getInstrumentation().context.assets.open("baseline-v1.json").bufferedReader().use {BackupCodec.json.decodeFromString<BackupSnapshot>(it.readText())}
                manager.restore(manager.prepare(BackupCodec.encode(fixture.copy(recompositions=listOf(
                    Recomposition("alfa",1,0.0,"2026-11-20"),Recomposition("alfa",3,5.5,"2026-11-20"),Recomposition("gama",3,null,"2026-11-20"))),9,null).inputStream()),"RESTAURAR")
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
            }
            if(phase=="restore") {
                app.repository.saveGrade("atividade-1","alfa",7.0)
                app.repository.deleteRecomposition(expected.recompositions.first())
                assertNotEquals(expected,manager.snapshot())
                manager.restore(manager.prepare(bytes.inputStream()),"RESTAURAR")
                assertEquals(expected,manager.snapshot())
                assertTrue(manager.hasSafetyCopy())
                // Correção explícita posterior à prova de preservação integral da versão 0.6.0.
                val previous=expected.recompositions.first {it.studentId=="alfa"&&it.term==3}
                app.repository.saveRecomposition(previous.copy(value=5.0),previous)
                val complete=manager.snapshot();val upgradedBackup=manager.backupBytes()
                app.repository.deleteRecomposition(complete.recompositions.first())
                manager.restore(manager.prepare(upgradedBackup.inputStream()),"RESTAURAR")
                assertEquals(complete,manager.snapshot())
                assertEquals(expected.copy(recompositions=emptyList()),complete.copy(recompositions=emptyList()))
                file.writeBytes(upgradedBackup)
            }
        }
    }
}
