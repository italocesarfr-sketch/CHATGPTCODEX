package br.com.diariodeclasse
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.diariodeclasse.ui.DiaryCommands
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class CommandQueueInstrumentedTest {
    @Test fun unrelatedSuccessDoesNotHideFrequencyFailureAndRetryKeepsCapturedTarget()=runBlocking {
        val queue=DiaryCommands();var available=false;var target=""
        queue.act(context="Turma fictícia A • 06/08/2026",key="a-1") { if(!available)throw IOException("simulada");target="a" }
        queue.awaitPendingWrites();assertNotNull(queue.attendanceError.value)
        queue.act(context="Turma fictícia B",key="b-1") {}
        queue.awaitPendingWrites();assertNotNull(queue.attendanceError.value)
        available=true
        withContext(Dispatchers.Main){queue.retry()}
        queue.awaitPendingWrites();assertEquals("a",target);assertNull(queue.attendanceError.value)
    }
    @Test fun latestValidIntentForSameCellReplacesFailedIntentAndDiscardDoesNotWrite()=runBlocking {
        val queue=DiaryCommands();var count=0
        queue.act(context="Turma fictícia",key="cell") {throw IOException("simulada")}
        queue.awaitPendingWrites()
        queue.act(context="Turma fictícia",key="cell") {count++}
        queue.awaitPendingWrites();assertNull(queue.attendanceError.value);assertEquals(1,count)
        queue.act(context="Turma fictícia",key="other") {throw IOException("simulada")}
        queue.awaitPendingWrites()
        withContext(Dispatchers.Main){queue.discardAttendanceFailures()}
        assertNull(queue.attendanceError.value);assertEquals(1,count)
    }
}
