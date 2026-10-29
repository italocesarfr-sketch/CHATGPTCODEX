package br.com.diariodeclasse

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import br.com.diariodeclasse.auth.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class AuthSignedUpgradeProbeTest {
    @Test fun originalSignedDataStaysLockedUntilVerifiedOwnerBindingAndNeverAppearsInAnotherAccount():Unit=runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("authSignedUpgrade")=="true")
        val app=ApplicationProvider.getApplicationContext<DiaryApplication>()
        val before=app.backupManager.snapshot()
        val owner=AuthUser(AuthConfig.LEGACY_OWNER_ID,"owner@example.invalid",true,"Proprietário Fictício")
        val other=AuthUser("22222222-2222-4222-8222-222222222222","other@example.invalid",true,"Outra Conta Fictícia")
        val client=PrincipalTestClient(owner);val auth=AuthController(client,KeystoreAuthVault(app));auth.restore()
        val manager=AccountWorkspaces(app,auth)
        assertNull(auth.state.value.user)
        assertThrows(IllegalStateException::class.java){manager.current()}
        auth.login(owner.email,"Senha-ficticia-2026");assertTrue(manager.needsClaim(owner))
        assertThrows(IllegalStateException::class.java){runBlocking{manager.activate(owner)}}
        val file=File(app.cacheDir,"auth-signed-before-link.json");val password="Backup-Ficticio-Assinado-2026".toCharArray()
        manager.claim(owner,Uri.fromFile(file),password.copyOf())
        assertEquals(before,manager.activate(owner).backup.snapshot())
        auth.logout();manager.lock();client.principal=other;auth.login(other.email,"Senha-ficticia-2026")
        assertTrue(manager.activate(other).backup.snapshot().classes.isEmpty())
        assertFalse(manager.current().backup.hasSafetyCopy())
        assertThrows(AuthFailure::class.java){AccountBackup.open(file.readBytes(),other.id,password)}
        auth.logout();manager.lock();client.principal=owner;auth.login(owner.email,"Senha-ficticia-2026")
        assertEquals(before,manager.activate(owner).backup.snapshot())
        auth.logout();manager.lock();assertEquals(owner.id,auth.legacyOwner());file.delete()
    }
}
