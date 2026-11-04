package br.com.diariodeclasse

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import br.com.diariodeclasse.data.*
import br.com.diariodeclasse.sync.academic.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*

/** Real SQLite CursorWindow, not a serializer double; no academic fixture is deleted. */
class PhaseELargePayloadRoomTest {
    @Test fun multiMegabyteOutboxInboxAndDecisionSurviveReopenWithoutTruncatingUnicode():Unit=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>();val name="e-large-${newId()}.db"
        val owner="11111111-1111-4111-8111-111111111111"
        val body="{\"text\":\""+"Ação 👩🏽‍🏫 • reflexão; ".repeat(100000)+"\"}"
        assertTrue(body.toByteArray().size>2*1024*1024)
        var db=DiaryDatabase.open(context,name)
        try {
            val pending=AcademicPending(newId(),owner,body,body,1)
            val incoming=AcademicIncoming(1,owner,body,"CONFLICT")
            val decision=AcademicDecision(newId(),owner,body,1,response=body)
            db.academicDao().insert(pending);db.academicDao().insert(incoming);db.academicDao().insert(decision)
            db.close();db=DiaryDatabase.open(context,name);val dao=db.academicDao()
            assertEquals(listOf(pending),dao.pending());assertEquals(listOf(incoming),dao.incoming());assertEquals(listOf(decision),dao.decisions())
            assertEquals(pending.id,dao.watchPending().first().single().id)
            assertEquals("CONFLICT",dao.watchIncoming().first().single().reason)
            assertEquals("PENDING",dao.watchDecisions().first().single().phase)
            assertTrue(dao.watchDecisions().first().single().body.isEmpty())
        }finally{db.close();context.deleteDatabase(name)}
    }
}
