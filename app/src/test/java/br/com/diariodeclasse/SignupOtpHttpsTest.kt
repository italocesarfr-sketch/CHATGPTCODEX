package br.com.diariodeclasse

import br.com.diariodeclasse.auth.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import okhttp3.tls.*
import org.junit.*
import org.junit.Assert.*
import javax.net.ssl.HttpsURLConnection

/** Real TLS loopback transport, production Kotlin client. No GoTrue/email/hosted request. */
class SignupOtpHttpsTest {
    private lateinit var server:MockWebServer
    private lateinit var client:SupabaseAuthClient
    private val email="teacher@example.invalid"
    private val logger=java.util.logging.Logger.getLogger(MockWebServer::class.java.name)
    private var previousLogLevel:java.util.logging.Level?=null
    @Before fun start() {
        previousLogLevel=logger.level;logger.level=java.util.logging.Level.OFF
        val certificate=HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        server=MockWebServer().apply{useHttps(HandshakeCertificates.Builder().heldCertificate(certificate).build().sslSocketFactory(),false);start()}
        val trust=HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        client=SupabaseAuthClient(openConnection={requested->
            assertEquals(java.net.URI(AuthConfig.URL).host,requested.host)
            (server.url(requested.file).toUrl().openConnection() as HttpsURLConnection).apply{sslSocketFactory=trust.sslSocketFactory()}
        })
    }
    @After fun close(){server.shutdown();logger.level=previousLogLevel}
    private fun reply(code:Int,body:String)=server.enqueue(MockResponse().setResponseCode(code).setHeader("Content-Type","application/json").setBody(body))
    @Test fun signupAndResendPreservePkceButOtpUsesOfficialEmailVerify():Unit=runBlocking {
        val link=PendingLink.create("signup",System.currentTimeMillis());reply(200,"{}");client.signUp("Fictício",email,"Fictitious-2026",link)
        val signup=server.takeRequest();assertEquals("POST",signup.method);assertTrue(signup.path!!.startsWith("/auth/v1/signup?redirect_to="))
        val sb=Json.parseToJsonElement(signup.body.readUtf8()).jsonObject;assertEquals(link.challenge,sb["code_challenge"]!!.jsonPrimitive.content);assertEquals("s256",sb["code_challenge_method"]!!.jsonPrimitive.content)
        reply(200,"{}");client.resendSignup(email,link);val resend=server.takeRequest();assertTrue(resend.path!!.startsWith("/auth/v1/resend?redirect_to="));assertEquals("signup",Json.parseToJsonElement(resend.body.readUtf8()).jsonObject["type"]!!.jsonPrimitive.content)
        reply(200,"""{"access_token":"synthetic-access","refresh_token":"synthetic-refresh","expires_in":3600}""")
        reply(200,"""{"id":"11111111-1111-4111-8111-111111111111","email":"$email","email_confirmed_at":"2026-11-21T00:00:00Z","is_anonymous":false}""")
        val session=client.confirmSignup(email,"012345");assertTrue(session.user.confirmed)
        val verify=server.takeRequest();assertEquals("/auth/v1/verify",verify.path);assertEquals("POST",verify.method)
        val body=Json.parseToJsonElement(verify.body.readUtf8()).jsonObject
        assertEquals(setOf("email","token","type"),body.keys);assertEquals("email",body["type"]!!.jsonPrimitive.content);assertEquals("012345",body["token"]!!.jsonPrimitive.content)
        val identity=server.takeRequest();assertEquals("GET",identity.method);assertEquals("/auth/v1/user",identity.path);assertEquals("Bearer synthetic-access",identity.getHeader("Authorization"))
        reply(204,"");client.logout(session)
        val logout=server.takeRequest();assertEquals("POST",logout.method);assertEquals("/auth/v1/logout?scope=local",logout.path);assertEquals("Bearer synthetic-access",logout.getHeader("Authorization"))
        reply(403,"""{"error_code":"otp_expired"}""")
        try{client.confirmSignup(email,"012345");fail()}catch(e:AuthFailure){assertEquals(AuthFailure.Kind.LINK,e.kind)}
        assertEquals("/auth/v1/verify",server.takeRequest().path)
    }
    @Test fun invalidExpiredAndRateLimitedCodesNeverReturnASession():Unit=runBlocking {
        for((status,code,kind)in listOf(Triple(403,"otp_expired",AuthFailure.Kind.LINK),Triple(400,"invalid_credentials",AuthFailure.Kind.INVALID),Triple(429,"over_request_rate_limit",AuthFailure.Kind.RATE_LIMIT))) {
            reply(status,"""{"error_code":"$code","msg":"private provider contents must not leak"}""")
            try{client.confirmSignup(email,"012345");fail()}catch(e:AuthFailure){assertEquals(kind,e.kind);assertFalse(e.message!!.contains("private provider"))}
            server.takeRequest()
        }
    }
    @Test fun redirectsAreNotFollowedAndNoCredentialsReachAnotherEndpoint():Unit=runBlocking {
        server.enqueue(MockResponse().setResponseCode(307).setHeader("Location",server.url("/prohibited")))
        try{client.confirmSignup(email,"012345");fail()}catch(e:AuthFailure){assertEquals(AuthFailure.Kind.INVALID,e.kind)}
        assertEquals(1,server.requestCount)
    }
    @Test fun unconfirmedOrAnonymousIdentityIsNotAcceptedEvenWithTokenFields():Unit=runBlocking {
        for(user in listOf("""{"id":"11111111-1111-4111-8111-111111111111","email":"$email","email_confirmed_at":null}""",
            """{"id":"11111111-1111-4111-8111-111111111111","email":"$email","email_confirmed_at":"2026-11-21","is_anonymous":true}""")) {
            reply(200,"""{"access_token":"synthetic-access","refresh_token":"synthetic-refresh","expires_in":3600}""");reply(200,user)
            try{client.confirmSignup(email,"012345");fail()}catch(_:AuthFailure){}
            server.takeRequest();server.takeRequest()
        }
    }
}
