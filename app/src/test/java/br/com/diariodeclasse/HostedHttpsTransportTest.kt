package br.com.diariodeclasse

import org.junit.Assert.*
import org.junit.*
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.net.ProtocolException
import java.net.URI
import java.net.URLDecoder
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate

/** Only disposable localhost TLS, synthetic headers/body, no Supabase or Auth. */
class HostedHttpsTransportTest {
    private lateinit var server: MockWebServer
    private lateinit var trust: HandshakeCertificates
    @Before fun start() {
        val certificate=HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").build()
        val serverTls=HandshakeCertificates.Builder().heldCertificate(certificate).build()
        trust=HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        server=MockWebServer();server.useHttps(serverTls.sslSocketFactory(),false);server.start()
    }
    @After fun stop() {server.shutdown()}
    private fun client(timeout:Duration=Duration.ofSeconds(3),limit:Int=262144)=HostedHttpsTransport(
        URI.create("https://localhost:${server.port}"),Pair(trust.sslSocketFactory(),trust.trustManager),timeout,limit)
    private fun failure(code:HostedHttpsTransport.Code, run:()->Unit):HostedHttpsTransport.Failure {
        try{run();fail("Expected sanitized failure")}catch(e:HostedHttpsTransport.Failure){assertEquals(code,e.code);assertNull(e.cause);return e}
        error("unreachable")
    }
    @Test fun oldJdkHttpsUrlConnectionRejectsPatchBeforeAnyConnection() {
        val c=URI.create("https://unused.example.invalid").toURL().openConnection() as HttpsURLConnection
        try {c.requestMethod="PATCH";fail("Expected JDK incompatibility")}
        catch(e:ProtocolException){assertEquals("Invalid HTTP method: PATCH",e.message)}finally{c.disconnect()}
    }
    @Test fun patchIsActuallySentOverTlsWithContradictoryFilterAndHeaders() {
        server.enqueue(MockResponse().setResponseCode(403).setBody("private-error-body"))
        val response=client().request(h03ZeroRowFilter("fixture?kind=eq.classes",7),"synthetic-public","synthetic-token","PATCH","{\"revision\":7}")
        val request=requireNotNull(server.takeRequest(2,TimeUnit.SECONDS))
        assertEquals("PATCH",request.method)
        assertEquals("synthetic-public",request.getHeader("apikey"))
        assertEquals("Bearer synthetic-token",request.getHeader("Authorization"))
        assertTrue(URLDecoder.decode(requireNotNull(request.path),"UTF-8").contains("and=(revision.eq.7,revision.lt.0)"))
        assertEquals("{\"revision\":7}",request.body.readUtf8())
        assertEquals(403,response.status);assertEquals(0,response.bytes.size);assertEquals(1,server.requestCount)
        assertFalse(response.toString().contains("synthetic-token"))
    }
    @Test fun nonPositiveRevisionCannotBuildZeroRowProbe() {
        for(r in listOf(0L,-1L))try{h03ZeroRowFilter("fixture",r);fail()}catch(_:IllegalArgumentException){}
    }
    @Test fun redirectsNeverForwardCredentials() {
        server.enqueue(MockResponse().setResponseCode(307).setHeader("Location",server.url("/rest/v1/target")))
        server.enqueue(MockResponse().setBody("must-not-be-read"))
        assertEquals(307,client().request("redirect","synthetic-public","synthetic-token","PATCH","{}").status)
        assertEquals(1,server.requestCount)
    }
    @Test fun oversizedResponseIsRejectedBeforeItCanBeReturned() {
        server.enqueue(MockResponse().setChunkedBody("A".repeat(65536),512))
        val c=client(limit=1024)
        assertEquals(200,failure(HostedHttpsTransport.Code.RESPONSE_LIMIT){c.request("large","synthetic",null,"GET",null)}.status)
    }
    @Test fun wholeBodyDeadlineAppliesAfterResponseHeaders() {
        server.enqueue(MockResponse().setBody("[]").setBodyDelay(2500,TimeUnit.MILLISECONDS))
        val c=client(timeout=Duration.ofMillis(800));val start=System.nanoTime()
        failure(HostedHttpsTransport.Code.TIMEOUT){c.request("stall","synthetic",null,"GET",null)}
        assertTrue(Duration.ofNanos(System.nanoTime()-start)<Duration.ofSeconds(2))
    }
    @Test fun stalledHeadersHaveDeadline() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val c=client(timeout=Duration.ofMillis(800))
        failure(HostedHttpsTransport.Code.TIMEOUT){c.request("stall","synthetic",null,"GET",null)}
    }
    @Test fun smallSuccessfulJsonAndHttpStatusRemainIntact() {
        server.enqueue(MockResponse().setBody("[]"))
        val r=client().request("ok","synthetic",null,"GET",null)
        assertEquals(200,r.status);assertEquals("[]",r.bytes.toString(Charsets.UTF_8))
    }
    @Test fun noHttpOrProductionOrOtherProjectEndpointIsAllowed() {
        for(url in listOf("http://localhost:1234","https://another.example.invalid","https://mpsgdchpgyxoqowfjoqm.supabase.co")) {
            try{HostedHttpsTransport(URI.create(url),Pair(trust.sslSocketFactory(),trust.trustManager));fail("Unsafe endpoint accepted")}catch(_:IllegalArgumentException){}
        }
    }
    @Test fun diagnosticsContainOnlyFixedStageCodeAndHttpStatus() {
        val out=ByteArrayOutputStream();val original=System.out
        try {
            System.setOut(PrintStream(out));val d=H03Diagnostics();d.enter(H03Diagnostics.Stage.PATCH);d.http(200)
            val e=d.failure(AssertionError("PRIVATE_RESPONSE_PASSWORD_TOKEN"))
            assertEquals("H03_DIAG stage=PATCH status=200 code=ASSERTION",e.message);assertNull(e.cause)
            assertFalse(out.toString().contains("PRIVATE_RESPONSE_PASSWORD_TOKEN"))
        }finally{System.setOut(original)}
    }
}
