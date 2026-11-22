package br.com.diariodeclasse

import br.com.diariodeclasse.auth.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.Assert.*

class SignupOtpTest {
    private val user=AuthUser("11111111-1111-4111-8111-111111111111","teacher@example.invalid",true)
    private var clock=100_000L
    private fun session()=AuthSession(user,"synthetic-access","synthetic-refresh",clock+3600000,clock,clock)
    private class Vault(var value:AuthSecrets=AuthSecrets()):AuthVault {
        override fun read()=value
        override fun write(value:AuthSecrets){this.value=value}
    }
    private inner class Client:AuthClient {
        var signup=0;var verified=0;var resent=0;var loggedOut=0;var fail:AuthFailure?=null;var mismatch=false;var wait:CompletableDeferred<Unit>?=null
        override suspend fun signUp(name:String,email:String,password:String,link:PendingLink){signup++;require(link.challenge.length==43)}
        override suspend fun confirmSignup(email:String,code:String):AuthSession {verified++;wait?.await();fail?.let{throw it};return session().let{if(mismatch)it.copy(user=user.copy(email="other@example.invalid"))else it}}
        override suspend fun resendSignup(email:String,link:PendingLink){resent++;require(link.purpose=="signup");fail?.let{throw it}}
        override suspend fun login(email:String,password:String)=session()
        override suspend fun verify(session:AuthSession):AuthSession {fail?.let{throw it};return session}
        override suspend fun refresh(session:AuthSession):AuthSession {fail?.let{throw it};return session}
        override suspend fun recover(email:String,link:PendingLink){}
        override suspend fun exchange(code:String,link:PendingLink)=session()
        override suspend fun password(session:AuthSession,password:String){}
        override suspend fun logout(session:AuthSession){loggedOut++}
    }
    private suspend fun signup(auth:AuthController)=auth.signup("Professor Fictício",user.email,"Fictitious-Password-2026","Fictitious-Password-2026")
    @Test fun codeFromAnotherDeviceConfirmsButNeverAdmitsAnOtpSession():Unit=runBlocking {
        val c=Client();val v=Vault();val a=AuthController(c,v){clock};a.restore();signup(a)
        assertEquals(user.email,a.state.value.confirmationEmail);assertNull(v.value.session)
        a.confirmSignup("012345");assertEquals(1,c.verified);assertEquals(1,c.loggedOut)
        assertNull(a.state.value.user);assertNull(v.value.session);assertNull(v.value.signup);assertNull(v.value.pending)
        a.login(user.email,"Fictitious-Password-2026");assertEquals(user,a.state.value.user)
    }
    @Test fun pendingTargetAndCooldownSurviveControllerAndVaultSerialization():Unit=runBlocking {
        val c=Client();val v=Vault();val a=AuthController(c,v){clock};a.restore();signup(a)
        val encoded=Json.encodeToString(AuthSecrets.serializer(),v.value)
        assertFalse(encoded.contains("Fictitious-Password"));assertFalse(encoded.contains("012345"))
        val restored=Vault(Json.decodeFromString(AuthSecrets.serializer(),encoded));val b=AuthController(c,restored){clock};b.restore()
        assertEquals(user.email,b.state.value.confirmationEmail);b.resendSignup();assertEquals(0,c.resent)
        clock+=60_001;b.resendSignup();assertEquals(1,c.resent);assertNull(b.state.value.user)
    }
    @Test fun oldSecretsDecodeWithoutMetadataAndNewMetadataIsRedacted() {
        val old=Json.decodeFromString<AuthSecrets>("{}");assertNull(old.signup)
        val s=PendingSignup(user.email,clock,clock)
        assertFalse(s.toString().contains(user.email));assertFalse(AuthSecrets(signup=s).toString().contains(user.email))
    }
    @Test fun configuredOtpLengthsAreAcceptedWithoutAssumingExactlySixDigits() {
        for(length in listOf(6,8,10))AuthPolicy.confirmationCode("0".repeat(length))
        for(length in listOf(5,11))assertThrows(AuthFailure::class.java){AuthPolicy.confirmationCode("0".repeat(length))}
    }
    @Test fun cachedSessionAwaitingRevalidationCannotBeReplacedByConfirmation():Unit=runBlocking {
        val saved=session().copy(expiresAt=1,verifiedAt=1,lastSeen=1)
        clock=40L*24*60*60_000
        val c=Client().apply{fail=AuthFailure(AuthFailure.Kind.NETWORK,"Sem conexão")}
        val v=Vault(AuthSecrets(session=saved,signup=PendingSignup(user.email,clock,clock)))
        val a=AuthController(c,v){clock};a.restore();assertNull(a.state.value.user)
        signup(a);a.beginSignupConfirmation(user.email);a.resendSignup();a.confirmSignup("012345")
        assertEquals(saved,v.value.session);assertEquals(0,c.signup);assertEquals(0,c.resent);assertEquals(0,c.verified);assertEquals(0,c.loggedOut)
    }
    @Test fun successfulConfirmationCannotReuseTheCodeOrCloseAnAdmittedSession():Unit=runBlocking {
        val c=Client();val v=Vault();val a=AuthController(c,v){clock};a.restore();signup(a);a.confirmSignup("012345")
        a.confirmSignup("012345");assertEquals(1,c.verified);assertEquals(1,c.loggedOut)
        a.login(user.email,"Fictitious-Password-2026");val admitted=v.value.session
        a.beginSignupConfirmation(user.email);a.confirmSignup("012345")
        assertEquals(admitted,v.value.session);assertEquals(user,a.state.value.user);assertEquals(1,c.verified);assertEquals(1,c.loggedOut)
    }
    @Test fun malformedExpiredOrClockRollbackCodesDoNotReachNetwork():Unit=runBlocking {
        val c=Client();val v=Vault();val a=AuthController(c,v){clock};a.restore();signup(a)
        for(code in listOf("","12345","12345678901","１２３４５６","123a56")){a.confirmSignup(code)}
        assertEquals(0,c.verified);clock+=60*60_000+1;a.confirmSignup("012345");assertEquals(0,c.verified)
        clock=99_999;a.confirmSignup("012345");assertEquals(0,c.verified)
    }
    @Test fun failuresCannotFloodVerifyAndResendAcrossRestart():Unit=runBlocking {
        val c=Client().apply{fail=AuthFailure(AuthFailure.Kind.LINK,"Código expirado")};val v=Vault();val a=AuthController(c,v){clock};a.restore();signup(a)
        repeat(5){a.confirmSignup("012345");clock+=2001}
        assertEquals(5,c.verified);val b=AuthController(c,v){clock};b.restore();b.confirmSignup("012345");assertEquals(5,c.verified)
        assertNull(b.state.value.user);assertNull(v.value.session);assertTrue(b.state.value.verifyAfter>clock)
    }
    @Test fun wrongConfirmedIdentityIsDiscardedAndOnlyThatTemporarySessionIsClosed():Unit=runBlocking {
        val c=Client().apply{mismatch=true};val v=Vault();val a=AuthController(c,v){clock};a.restore();signup(a);a.confirmSignup("012345")
        assertNull(a.state.value.user);assertNull(v.value.session);assertNotNull(v.value.signup);assertEquals(1,c.loggedOut)
    }
    @Test fun logoutDuringVerificationCannotResurrectOrStoreTheReturnedSession():Unit=runBlocking {
        val release=CompletableDeferred<Unit>();val c=Client().apply{wait=release};val v=Vault();val a=AuthController(c,v){clock};a.restore();signup(a)
        val pending=launch{a.confirmSignup("012345")};yield();assertEquals(1,c.verified);a.logout();release.complete(Unit);pending.join()
        assertNull(a.state.value.user);assertNull(v.value.session);assertNull(v.value.signup);assertEquals(1,c.loggedOut)
    }
    @Test fun cancelAndRecoveryNeverReuseSignupVerificationState():Unit=runBlocking {
        val c=Client();val v=Vault();val a=AuthController(c,v){clock};a.restore();signup(a)
        a.cancelSignupConfirmation();assertNull(v.value.signup);assertNull(v.value.pending)
        a.beginSignupConfirmation(user.email);assertEquals(user.email,a.state.value.confirmationEmail)
        a.cancelSignupConfirmation();a.recover(user.email);assertEquals("recovery",v.value.pending!!.purpose);assertNull(v.value.signup)
    }
}
