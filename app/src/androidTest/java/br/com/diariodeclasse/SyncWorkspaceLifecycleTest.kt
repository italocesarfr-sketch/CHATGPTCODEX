package br.com.diariodeclasse

import androidx.test.core.app.ApplicationProvider
import br.com.diariodeclasse.auth.*
import br.com.diariodeclasse.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*

class SyncWorkspaceLifecycleTest {
    @Test fun accountDatabaseClosesOnlyAfterItsQueueObserverHasStopped():Unit=runBlocking {
        val base=AuthSandbox(ApplicationProvider.getApplicationContext())
        val user=AuthUser("11111111-1111-4111-8111-111111111111","fixture@example.invalid",true)
        repeat(20) {
            val workspace=AccountWorkspace(base,user,false)
            val before=workspace.backup.snapshot()
            workspace.close() // Used to race the immediately started watchStates query.
            val reopened=AccountWorkspace(base,user,false)
            assertEquals(before,reopened.backup.snapshot())
            reopened.shutdown()
        }
        base.root.deleteRecursively()
    }
}
