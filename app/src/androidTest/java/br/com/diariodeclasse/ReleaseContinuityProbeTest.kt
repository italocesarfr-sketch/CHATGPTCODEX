package br.com.diariodeclasse

import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import br.com.diariodeclasse.backup.BackupCodec
import br.com.diariodeclasse.data.AppSelection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Exclusivo do emulador descartável do CI; nunca ativado na suíte normal. */
class ReleaseContinuityProbeTest {
    @Test fun releasePreservesSnapshotPreferencesAndBackupRoundTrip() {
        val phase = InstrumentationRegistry.getArguments().getString("releaseProbePhase")
        assumeTrue("Sonda exclusiva de release", phase == "seed" || phase == "verify")
        check(Build.HARDWARE in setOf("ranchu", "goldfish")) { "Somente emulador descartável." }
        runBlocking {
            val app = ApplicationProvider.getApplicationContext<DiaryApplication>()
            val manager = app.backupManager
            val file = File(app.filesDir, "noctua-ci-before.json")
            if (phase == "seed") {
                val school = manager.snapshot().classes.first()
                app.selectionStore.write(AppSelection(school.year, school.id))
                file.writeBytes(manager.backupBytes())
                assertEquals(manager.snapshot(), BackupCodec.decode(file.readBytes()).snapshot)
            } else {
                assertEquals("Noctua", app.packageManager.getApplicationLabel(app.applicationInfo).toString())
                val bytes = file.readBytes()
                val expected = BackupCodec.decode(bytes).snapshot
                assertEquals(expected, manager.snapshot()) // oito tabelas + seleção, antes de restaurar
                assertEquals(expected.selection, app.selectionStore.read())
                val original = expected.grades.first { it.value != null && it.value != 0.0 }
                app.repository.saveGrade(original.activityId, original.studentId, 0.0)
                assertNotEquals(expected, manager.snapshot())
                val restored = manager.restore(manager.prepare(bytes.inputStream()), "RESTAURAR")
                assertEquals("", restored.warning)
                assertEquals(expected, manager.snapshot())
                assertEquals(expected.selection, app.selectionStore.read())
                assertTrue(manager.hasSafetyCopy())
            }
        }
    }
}
