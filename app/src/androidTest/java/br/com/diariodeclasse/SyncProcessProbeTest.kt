package br.com.diariodeclasse

import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.sync.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

class SyncProcessProbeTest {
    @Test fun durableQueueAfterProcessDeath():Unit=runBlocking {
        val phase=InstrumentationRegistry.getArguments().getString("syncProbePhase")
        Assume.assumeTrue(phase in listOf("write","read"))
        val app=ApplicationProvider.getApplicationContext<DiaryApplication>()
        val file=File(app.filesDir,"sync-process-op.txt")
        if(phase=="write"){app.deleteDatabase("sync-process.db");file.delete()}
        val db=DiaryDatabase.open(app,"sync-process.db")
        try {
            val store=ClassSyncStore(db,"11111111-1111-4111-8111-111111111111")
            if(phase=="write") {
                DiaryRepository(db).saveClass(SchoolClass("process",name="Turma Fictícia",subject="História",year=2026),"2026-01-01")
                store.enable();store.enroll("process");file.writeText(store.prepare().single().id)
            } else {
                assertEquals(file.readText(),store.prepare().single().id)
                val op=store.prepare().single();assertEquals("Turma Fictícia",ClassBundle.decode(op.payload).school.name)
                assertEquals(0,db.syncDao().state("process")!!.acknowledged)
            }
        }finally{db.close()}
    }
}
