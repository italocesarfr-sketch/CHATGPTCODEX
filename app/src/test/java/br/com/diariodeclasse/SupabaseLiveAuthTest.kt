package br.com.diariodeclasse

import br.com.diariodeclasse.auth.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Opt-in, authorized test account only. No credential is compiled, printed or written to a report. */
class SupabaseLiveAuthTest {
    @Test fun confirmedLoginRefreshLogoutAndRevokedRefreshUseTheRealKotlinClient():Unit=runBlocking {
        val path=System.getenv("NOCTUA_AUTH_LIVE_FILE")
        assumeTrue("Credencial de teste autorizada indisponível; execução real é separada da suíte offline.",!path.isNullOrBlank())
        val private=Json.parseToJsonElement(File(path!!).readText()).jsonObject
        val email=private.getValue("email").jsonPrimitive.content
        val password=private.getValue("password").jsonPrimitive.content
        val client=SupabaseAuthClient()
        val signed=client.login(email,password)
        assertTrue("E-mail precisa estar confirmado pelo servidor",signed.user.confirmed)
        assertTrue("Identidade precisa ser a autorizada",signed.user.id==AuthConfig.LEGACY_OWNER_ID)
        val verified=client.verify(signed)
        assertTrue(verified.user.id==signed.user.id)
        val renewed=client.refresh(verified)
        assertTrue(renewed.user.id==signed.user.id&&renewed.expiresAt>System.currentTimeMillis())
        try{client.login(email,"Senha-deliberadamente-incorreta-ficticia");fail("Senha incorreta foi aceita")}catch(e:AuthFailure){assertEquals(AuthFailure.Kind.INVALID,e.kind)}
        client.logout(renewed)
        try{client.refresh(renewed);fail("Refresh revogado foi aceito")}catch(e:AuthFailure){assertTrue(e.kind in listOf(AuthFailure.Kind.SESSION,AuthFailure.Kind.INVALID))}
    }
}
