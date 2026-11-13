package br.com.diariodeclasse

import android.os.Debug
import android.os.Process
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import br.com.diariodeclasse.backup.*
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.sync.academic.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Bounded, opt-in Room/codec benchmark. HTTP/SQL are measured separately, never subtracted from UI waits. */
class H13RoomBenchmarkTest {
    @Test fun graduatedAtomicRecoveryAndConfirmedReplay():Unit=runBlocking {
        org.junit.Assume.assumeTrue("H13 benchmark opt-in on disposable CI only",InstrumentationRegistry.getArguments().getString("h13Benchmark")=="YES")
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        val samples=mutableListOf<JsonElement>()
        fun now()=SystemClock.elapsedRealtimeNanos()
        fun ms(start:Long)=(now()-start)/1_000_000.0
        val owner="11111111-1111-4111-8111-111111111111"
        val base=PhaseDFixture.read();val c=base.classes.first{it.id=="d-source"};val model=base.students.first{it.status=="Ativo"&&it.classId==c.id}
        val small=BackupSnapshot(listOf(c),(1..36).map{model.copy(id="h13-small-$it",personId="h13-person-$it",number=it)},base.terms.filter{it.classId==c.id},emptyList(),emptyList(),emptyList(),emptyList(),emptyList()).canonical()
        for(fixture in listOf(small,PhaseEFixture.read(0),PhaseEFixture.read(100),PhaseEFixture.read())) {
            val expectedRows=AcademicRecords.rows(fixture).size;assertTrue(expectedRows in listOf(41,171,671,5171));BackupCodec.validate(fixture)
            repeat(3){trial->
                val name="h13-room-${newId()}.db";val db=DiaryDatabase.open(context,name)
                try {
                    val store=AcademicStore(db,owner);store.dao.save(AcademicControl(owner=owner,stage="ACTIVE_E",enabled=true))
                    val cpuStart=Process.getElapsedCpuTime();val serializeStart=now()
                    val raw=PhaseEFixture.fragments(fixture).map{AcademicRecords.json.encodeToString(it)};val serializeMs=ms(serializeStart)
                    val decodeStart=now();val parts=raw.map{AcademicRecords.json.decodeFromString<AcademicFragment>(it)};val decodeMs=ms(decodeStart)
                    assertEquals((expectedRows+127)/128,parts.size)
                    val receiveStart=now();val beforeCpu=Process.getElapsedCpuTime();val partTimes=mutableListOf<Double>();var pss=0
                    for((i,part)in parts.withIndex()) {
                        val t=now();assertTrue(store.receivePart(part));partTimes+=ms(t)
                        if(i!=parts.lastIndex){assertEquals(0,store.cursor());assertTrue(db.backupDao().classes().isEmpty())}
                        val memory=Debug.MemoryInfo();Debug.getMemoryInfo(memory);pss=maxOf(pss,memory.totalPss)
                    }
                    val receiveMs=ms(receiveStart);val receiveCpu=Process.getElapsedCpuTime()-beforeCpu
                    val actual=ClassLifecycle.snapshot(DiaryRepository(db).readSnapshot()).canonical();assertEquals(fixture,actual)
                    assertEquals(1,store.cursor());assertTrue(store.dao.pending().isEmpty()&&store.dao.incoming().isEmpty()&&store.dao.stagedParts().isEmpty())
                    val beforeHash=BackupCodec.fingerprint(actual);val replayStart=now()
                    for(part in parts)assertTrue(store.receivePart(part))
                    val replayMs=ms(replayStart);assertEquals(1,store.cursor());assertEquals(beforeHash,BackupCodec.fingerprint(ClassLifecycle.snapshot(DiaryRepository(db).readSnapshot()).canonical()))
                    db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}
                    val runtime=Runtime.getRuntime()
                    samples+=buildJsonObject {
                        put("records",expectedRows);put("parts",parts.size);put("trial",trial+1);put("serialize_ms",serializeMs);put("decode_ms",decodeMs)
                        put("receive_wall_ms",receiveMs);put("receive_process_cpu_ms",receiveCpu);put("last_part_apply_ms",partTimes.last());put("staging_parts_ms",partTimes.dropLast(1).sum())
                        put("confirmed_replay_ms",replayMs);put("scenario_process_cpu_ms",Process.getElapsedCpuTime()-cpuStart)
                        put("sampled_process_pss_kib",pss);put("java_heap_used_bytes",runtime.totalMemory()-runtime.freeMemory());put("java_heap_limit_bytes",runtime.maxMemory())
                        put("sha256",h05RowsHash(AcademicRecords.rows(actual)));put("snapshot_fingerprint",beforeHash)
                        put("ids_payloads_equal",true);put("queues_empty",true);put("cursor",1);put("http_measured",false);put("pss_is_sample_not_peak",true)
                    }
                }finally{db.close();assertTrue(context.deleteDatabase(name))}
            }
        }
        File(context.filesDir,"h13-room.json").writeText(buildJsonObject{put("case","H13");put("layer","Android Room/codec; no HTTP/SQL server");put("samples",JsonArray(samples))}.toString())
    }
}
