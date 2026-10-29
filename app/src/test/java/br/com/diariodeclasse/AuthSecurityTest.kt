package br.com.diariodeclasse

import br.com.diariodeclasse.auth.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AuthSecurityTest {
    private val user=AuthUser("11111111-1111-4111-8111-111111111111","professor@example.invalid",true,"Professor Fictício")
    private val instant=1_800_000_000_000L
    private fun session()=AuthSession(user,"access-ficticio","refresh-ficticio",instant+3_600_000,instant,instant)
    private class Vault(var value:AuthSecrets=AuthSecrets()):AuthVault {
        override fun read()=value
        override fun write(value:AuthSecrets){this.value=value}
    }
    private inner class Client:AuthClient {
        var failure:AuthFailure?=null;var calls=0;var blocked:CompletableDeferred<Unit>?=null
        override suspend fun login(email:String,password:String):AuthSession {calls++;blocked?.await();failure?.let{throw it};return session()}
        override suspend fun verify(session:AuthSession):AuthSession {calls++;failure?.let{throw it};return session}
        override suspend fun refresh(session:AuthSession)=verify(session)
        override suspend fun signUp(name:String,email:String,password:String,link:PendingLink){calls++}
        override suspend fun recover(email:String,link:PendingLink){calls++}
        override suspend fun exchange(code:String,link:PendingLink):AuthSession {calls++;return session()}
        override suspend fun password(session:AuthSession,password:String){calls++}
        override suspend fun logout(session:AuthSession){calls++}
    }
    @Test fun emptyOrWrongCredentialsNeverAdmit():Unit=runBlocking {
        val vault=Vault();val client=Client();val auth=AuthController(client,vault){instant};auth.restore()
        auth.login("","");assertNull(auth.state.value.user);assertEquals(0,client.calls)
        client.failure=AuthFailure(AuthFailure.Kind.INVALID,"Credenciais inválidas.")
        auth.login(user.email,"Senha-ficticia-2026");assertNull(auth.state.value.user);assertNull(vault.value.session)
    }
    @Test fun unconfirmedIdentityAndUnconfirmedServerErrorNeverAdmit():Unit=runBlocking {
        val client=Client();client.failure=AuthFailure(AuthFailure.Kind.UNCONFIRMED,"Confirme o e-mail.")
        val vault=Vault();val auth=AuthController(client,vault){instant};auth.restore();auth.login(user.email,"Senha-ficticia-2026")
        assertNull(auth.state.value.user);assertNull(vault.value.session)
        assertThrows(IllegalArgumentException::class.java){user.copy(confirmed=false).validate()}
    }
    @Test fun signupChecksNameEmailLengthAndBothPasswordsBeforeNetwork():Unit=runBlocking {
        val client=Client();val auth=AuthController(client,Vault()){instant};auth.restore()
        for(values in listOf(listOf("",user.email,"12345678","12345678"),listOf("Nome","inválido","12345678","12345678"),listOf("Nome",user.email,"1234567","1234567"),listOf("Nome",user.email,"12345678","diferente"))) {
            auth.signup(values[0],values[1],values[2],values[3]);assertNull(auth.state.value.user)
        }
        assertEquals(0,client.calls)
        auth.signup("Professor Fictício",user.email,"Senha-ficticia-2026","Senha-ficticia-2026");assertEquals(1,client.calls);assertNull(auth.state.value.user)
    }
    @Test fun authenticatedSessionSurvivesControllerRecreationAndOfflineWithinPolicy():Unit=runBlocking {
        val vault=Vault();val client=Client();val first=AuthController(client,vault){instant};first.restore();first.login(user.email,"Senha-ficticia-2026")
        assertEquals(user,first.state.value.user)
        client.failure=AuthFailure(AuthFailure.Kind.NETWORK,"Sem conexão.")
        val second=AuthController(client,vault){instant+24*60*60_000};second.restore()
        assertEquals(user,second.state.value.user);assertTrue(second.state.value.offline);assertTrue(second.canUse(user.id))
    }
    @Test fun noPriorSessionCannotUseOfflineAndExpiredGraceLocksWithoutDeletingDataIdentity():Unit=runBlocking {
        val client=Client();client.failure=AuthFailure(AuthFailure.Kind.NETWORK,"Sem conexão.")
        val empty=AuthController(client,Vault()){instant};empty.restore();assertNull(empty.state.value.user)
        val vault=Vault(AuthSecrets(session=session(),legacyOwner=user.id));val auth=AuthController(client,vault){instant+AuthPolicy.OFFLINE_MILLIS}
        auth.restore();assertNull(auth.state.value.user);assertNotNull(vault.value.session);assertEquals(user.id,vault.value.legacyOwner)
    }
    @Test fun revokedRefreshClearsSessionButKeepsOwnership():Unit=runBlocking {
        val vault=Vault(AuthSecrets(session=session().copy(expiresAt=instant-1),legacyOwner=user.id));val client=Client()
        client.failure=AuthFailure(AuthFailure.Kind.SESSION,"Sessão revogada.")
        val auth=AuthController(client,vault){instant};auth.restore()
        assertNull(auth.state.value.user);assertNull(vault.value.session);assertEquals(user.id,vault.value.legacyOwner)
    }
    @Test fun logoutInvalidatesAnInFlightLoginWithoutWaitingForTheNetwork():Unit=runBlocking {
        val client=Client();val blocker=CompletableDeferred<Unit>();client.blocked=blocker
        val vault=Vault(AuthSecrets(legacyOwner=user.id));val auth=AuthController(client,vault){instant};auth.restore()
        val request=launch{auth.login(user.email,"Senha-ficticia-2026")};yield();assertEquals(1,client.calls)
        auth.logout();assertNull(auth.state.value.user);assertNull(vault.value.session)
        blocker.complete(Unit);request.join();assertNull(auth.state.value.user);assertNull(vault.value.session);assertEquals(user.id,vault.value.legacyOwner)
    }
    @Test fun explicitCredentialsSupersedeAnOlderAutomaticRestoreWithoutExposingItsAccount():Unit=runBlocking {
        val started=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        val other=user.copy(id="22222222-2222-4222-8222-222222222222",email="outra@example.invalid")
        val client=object:AuthClient by Client() {
            override suspend fun verify(session:AuthSession):AuthSession {started.complete(Unit);release.await();return session}
            override suspend fun login(email:String,password:String)=session().copy(user=other)
        }
        val vault=Vault(AuthSecrets(session=session(),legacyOwner=user.id));val auth=AuthController(client,vault){instant}
        val restore=launch{auth.restore()};started.await()
        val login=launch{auth.login(other.email,"Senha-ficticia-2026")};yield()
        assertNull(auth.state.value.user);assertNull(vault.value.session)
        release.complete(Unit);restore.join();login.join()
        assertEquals(other,auth.state.value.user);assertEquals(other.id,vault.value.session?.user?.id)
        assertEquals(user.id,vault.value.legacyOwner)
    }
    @Test fun pendingRecoveryNeverGrantsAcademicAdmissionAndRequiresReentryAfterPasswordChange():Unit=runBlocking {
        val vault=Vault();val client=Client();val auth=AuthController(client,vault){instant};auth.restore()
        auth.recover(user.email);val pending=vault.value.pending!!
        auth.callback("${pending.redirect}&code=code-ficticio-valido")
        assertTrue(auth.state.value.recovery);assertNull(auth.state.value.user);assertNull(vault.value.session)
        auth.newPassword("Nova-senha-ficticia-2026","Nova-senha-ficticia-2026")
        assertFalse(auth.state.value.recovery);assertNull(auth.state.value.user);assertNull(vault.value.session)
    }
    @Test fun maliciousExpiredDuplicateOrUncorrelatedLinksNeverReachServer():Unit=runBlocking {
        val pending=PendingLink.create("recovery",instant);val vault=Vault(AuthSecrets(pending=pending));val client=Client();val auth=AuthController(client,vault){instant};auth.restore()
        val valid="${pending.redirect}&code=code-ficticio-valido"
        for(link in listOf(valid.replace("br.com.diariodeclasse","https"),valid.replace("auth/callback","evil/callback"),valid+"&code=duplicado",valid+"#access_token=ficticio",valid.replace(pending.state,"estado-incorreto"),valid.replace("/callback","/callback/extra"))) {
            auth.callback(link);assertNull(auth.state.value.user);assertFalse(auth.state.value.recovery)
        }
        assertEquals(0,client.calls)
        val late=AuthController(client,vault){instant+31*60_000};late.restore();late.callback(valid);assertEquals(0,client.calls)
    }
    @Test fun pkceChallengesAreUniqueAndNeverExposeTheVerifierOrTokensThroughToString() {
        val one=PendingLink.create("signup",instant);val two=PendingLink.create("signup",instant)
        assertNotEquals(one.verifier,two.verifier);assertEquals(43,one.challenge.length)
        assertFalse(one.toString().contains(one.verifier));assertFalse(session().toString().contains("access-ficticio"));assertFalse(AuthSecrets(session()).toString().contains("refresh-ficticio"))
    }
    @Test fun accountBackupsRoundTripUnicodeAndRejectOtherAccountsWrongPasswordsAndTampering() {
        val original="FJ • Atestado médico\nHistórico legítimo com nota 0 e pendente".toByteArray()
        val password="Senha-backup-ficticia-2026".toCharArray()
        val sealed=AccountBackup.seal(original,user.id,password)
        // Public synthetic interoperability fixture; no real credential or school record.
        java.io.File("build/auth-backup-validation").apply{mkdirs()}.let { folder ->
            java.io.File(folder,"synthetic.noctua").writeBytes(sealed)
            java.io.File(folder,"expected.txt").writeBytes(original)
            java.io.File(folder,"unicode-password.noctua").writeBytes(AccountBackup.seal(original,user.id,"Senha-fictícia-ç-2026".toCharArray()))
        }
        assertTrue(AccountBackup.protected(sealed));assertFalse(sealed.toString(Charsets.UTF_8).contains("Histórico"))
        assertArrayEquals(original,AccountBackup.open(sealed,user.id,password))
        assertThrows(AuthFailure::class.java){AccountBackup.open(sealed,"22222222-2222-4222-8222-222222222222",password)}
        assertThrows(AuthFailure::class.java){AccountBackup.open(sealed,user.id,"Senha-errada-ficticia".toCharArray())}
        val corrupt=sealed.copyOf().also{it[it.size-10]=(it[it.size-10].toInt() xor 1).toByte()}
        assertThrows(AuthFailure::class.java){AccountBackup.open(corrupt,user.id,password)}
    }
    @Test fun clockRollbackAndExpiredOfflineGrantAreNotAccepted() {
        assertFalse(AuthPolicy.offlineAllowed(session(),instant-1))
        assertFalse(AuthPolicy.offlineAllowed(session().copy(lastSeen=instant+120000),instant))
        assertFalse(AuthPolicy.offlineAllowed(session(),instant+AuthPolicy.OFFLINE_MILLIS))
    }
}
