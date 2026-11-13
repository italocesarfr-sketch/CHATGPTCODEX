package br.com.diariodeclasse

import java.io.ByteArrayOutputStream
import java.io.InterruptedIOException
import java.net.URI
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** JVM test transport only. No Android/production source or runtime dependency. */
internal class HostedHttpsTransport(
    private val endpoint: URI = URI.create("https://sscexzxtqdcsqdlcytum.supabase.co"),
    localTestTls: Pair<SSLSocketFactory, X509TrustManager>? = null,
    deadline: Duration = Duration.ofSeconds(20),
    private val limit: Int = 262144,
) {
    enum class Code { TIMEOUT, RESPONSE_LIMIT, NETWORK, INTERRUPTED }
    class Failure(val code: Code, val status: Int? = null) : Exception("HOSTED_TRANSPORT_${code.name}")
    class Response(val status: Int, val bytes: ByteArray) {
        override fun toString() = "Response(status=$status, body=redacted)"
    }
    init {
        // Test trust is accepted only for a disposable loopback server, never another project.
        if (localTestTls == null) require(endpoint.toString() == "https://sscexzxtqdcsqdlcytum.supabase.co")
        else require(endpoint.scheme == "https" && endpoint.host == "localhost" &&
            endpoint.port > 0 && endpoint.rawPath.isEmpty() && endpoint.rawQuery == null && endpoint.userInfo == null)
        require(endpoint.rawFragment == null && !deadline.isNegative && !deadline.isZero && limit in 1..262144)
    }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(deadline.toMillis(), TimeUnit.MILLISECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .apply { localTestTls?.let { sslSocketFactory(it.first, it.second) } }
        .build()

    fun request(path: String, key: String, token: String?, method: String, body: String?): Response {
        require(method in setOf("GET", "POST", "PATCH"))
        val uri = URI.create("$endpoint/rest/v1/$path")
        require(uri.scheme == endpoint.scheme && uri.authority == endpoint.authority && uri.fragment == null &&
            uri.normalize().path.startsWith("/rest/v1/"))
        val builder = Request.Builder().url(uri.toString()).header("apikey", key)
        token?.let { builder.header("Authorization", "Bearer $it") }
        val payload = if(method == "GET") null else (body ?: "").toRequestBody("application/json".toMediaType())
        val request = builder.method(method, payload).build()
        var status: Int? = null
        try {
            client.newCall(request).execute().use { response ->
                status = response.code
                // Never expose private error/redirect bodies to the test or diagnostics.
                if (!response.isSuccessful) return Response(response.code, ByteArray(0))
                val responseBody = response.body ?: return Response(response.code, ByteArray(0))
                if (responseBody.contentLength() > limit) throw Failure(Code.RESPONSE_LIMIT, status)
                val bytes = ByteArrayOutputStream()
                responseBody.byteStream().use { input ->
                    val chunk = ByteArray(8192)
                    while(true) {
                        val count = input.read(chunk, 0, minOf(chunk.size, limit + 1 - bytes.size()))
                        if(count < 0) break
                        if(bytes.size() + count > limit) throw Failure(Code.RESPONSE_LIMIT, status)
                        bytes.write(chunk, 0, count)
                    }
                }
                return Response(response.code, bytes.toByteArray())
            }
        } catch (e: Failure) { throw e }
        catch (_: InterruptedIOException) {
            throw Failure(if(Thread.currentThread().isInterrupted) Code.INTERRUPTED else Code.TIMEOUT, status)
        } catch (_: java.io.IOException) { throw Failure(Code.NETWORK, status) }
    }
}

/** Fixed vocabulary only: never accepts a URL, ID, body, exception message or credentials. */
internal class H03Diagnostics {
    enum class Stage { INPUT, LOGIN, FIXTURE_BEFORE, REVISION, HELPER, PATCH, FIXTURE_AFTER, COMPARE }
    enum class Code { STARTED, HTTP, PASSED, ASSERTION, TIMEOUT, RESPONSE_LIMIT, NETWORK, INTERRUPTED, UNEXPECTED }
    private var stage = Stage.INPUT
    private var status: Int? = null
    fun enter(value: Stage) { stage = value; status = null; emit(Code.STARTED) }
    fun http(value: Int) { require(value in 100..599); status = value; emit(Code.HTTP) }
    fun emit(code: Code): String = "H03_DIAG stage=${stage.name} status=${status ?: "NONE"} code=${code.name}"
        .also { println(it) }
    fun failure(error: Throwable): AssertionError {
        val code = when (error) {
            is HostedHttpsTransport.Failure -> {
                error.status?.let { status = it }
                Code.valueOf(error.code.name)
            }
            is AssertionError -> Code.ASSERTION
            else -> Code.UNEXPECTED
        }
        return AssertionError(emit(code)) // No private message or original cause.
    }
}

internal fun h03ZeroRowFilter(query: String, revision: Long): String {
    require(revision > 0)
    return query + "&and=" + java.net.URLEncoder.encode("(revision.eq.$revision,revision.lt.0)","UTF-8")
}
