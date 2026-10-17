package br.com.diariodeclasse

import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.AppSelection
import br.com.diariodeclasse.data.Recomposition
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** API compatível com Noctua 0.5.0. Só executa no emulador descartável com argumento explícito. */
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
                manager.restore(manager.prepare(BackupCodec.encode(fixture,8,null).inputStream()),"RESTAURAR")
                app.selectionStore.write(AppSelection(2026,"classe-alfa"))
                file.writeBytes(manager.backupBytes())
            }
            val bytes=file.readBytes();val expected=BackupCodec.decode(bytes).snapshot
            assertEquals(expected,manager.snapshot())
            assertEquals(expected.selection,app.selectionStore.read())
            assertEquals("Noctua",app.packageManager.getApplicationLabel(app.applicationInfo).toString())
            if(phase=="restore") {
                app.repository.saveGrade("atividade-1","alfa",7.0)
                assertNotEquals(expected,manager.snapshot())
                manager.restore(manager.prepare(bytes.inputStream()),"RESTAURAR")
                assertEquals(expected,manager.snapshot())
                assertTrue(manager.hasSafetyCopy())
                // Depois de comprovar a restauração do backup v2 antigo, validar o novo formato.
                app.repository.saveRecomposition(Recomposition("alfa",3,5.0,"2026-11-20"))
                app.repository.saveRecomposition(Recomposition("alfa",1,0.0,"2026-11-20"))
                app.repository.saveRecomposition(Recomposition("gama",3,null,"2026-11-20"))
                val complete=manager.snapshot();val upgradedBackup=manager.backupBytes()
                app.repository.deleteRecomposition(complete.recompositions.first())
                manager.restore(manager.prepare(upgradedBackup.inputStream()),"RESTAURAR")
                assertEquals(complete,manager.snapshot())
                assertEquals(expected,complete.copy(recompositions=emptyList()))
                file.writeBytes(upgradedBackup)
            }
        }
    }
}
