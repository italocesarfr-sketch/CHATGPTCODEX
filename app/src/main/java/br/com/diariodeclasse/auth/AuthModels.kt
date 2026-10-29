package br.com.diariodeclasse.auth

import kotlinx.serialization.Serializable
import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

object AuthConfig {
    const val URL = "https://mpsgdchpgyxoqowfjoqm.supabase.co"
    const val KEY = "sb_publishable_61hfac3DL6eLG2gRBpEIeg_zBd1d_mZ"
    const val CALLBACK = "br.com.diariodeclasse://auth/callback"
    const val LEGACY_OWNER_ID = "f624fdc9-2225-4211-8efe-46f9af2c53e5"
}
class AuthFailure(val kind: Kind, message: String) : Exception(message) {
    enum class Kind { INVALID, UNCONFIRMED, NETWORK, RATE_LIMIT, SERVER, SESSION, LINK }
}
@Serializable data class AuthUser(val id: String, val email: String, val confirmed: Boolean, val name: String = "") {
    fun validate() { require(UUID.fromString(id).toString() == id); require(confirmed && email.isNotBlank()) }
}
@Serializable data class AuthSession(val user: AuthUser, val access: String, val refresh: String, val expiresAt: Long,
    val verifiedAt: Long, val lastSeen: Long) {
    override fun toString() = "AuthSession(redacted)"
}
@Serializable data class PendingLink(val verifier: String, val state: String, val purpose: String, val createdAt: Long) {
    override fun toString() = "PendingLink(redacted)"
    val redirect: String get() = "${AuthConfig.CALLBACK}?state=$state"
    val challenge: String get() = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
    companion object {
        fun create(purpose: String, now: Long): PendingLink {
            require(purpose in listOf("signup", "recovery"))
            fun random() = ByteArray(32).also { SecureRandom().nextBytes(it) }.let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
            return PendingLink(random(), random(), purpose, now)
        }
    }
    fun codeFrom(raw: String, now: Long): String {
        try {
            val uri = URI(raw)
            require(uri.scheme == "br.com.diariodeclasse" && uri.host == "auth" && uri.rawPath == "/callback" && uri.port == -1 && uri.userInfo == null && uri.fragment == null)
            require(now >= createdAt && now - createdAt <= 30 * 60_000L)
            val pairs = uri.rawQuery.orEmpty().split('&').map { val p=it.split('=',limit=2); require(p.size==2); URLDecoder.decode(p[0],"UTF-8") to URLDecoder.decode(p[1],"UTF-8") }
            require(pairs.map { it.first }.distinct().size == pairs.size && pairs.all { it.first in listOf("state","code") })
            val map=pairs.toMap()
            require(MessageDigest.isEqual(state.toByteArray(),map["state"].orEmpty().toByteArray()))
            return map["code"].orEmpty().also { require(it.length in 8..1024 && it.none(Char::isWhitespace)) }
        } catch (_: Exception) { throw AuthFailure(AuthFailure.Kind.LINK,"Link inválido ou vencido. Solicite outro neste aparelho.") }
    }
}
object AuthPolicy {
    const val OFFLINE_MILLIS = 7 * 24 * 60 * 60_000L
    fun offlineAllowed(session: AuthSession, now: Long): Boolean = session.user.confirmed && now >= session.verifiedAt &&
        now + 60_000 >= session.lastSeen && now - session.verifiedAt < OFFLINE_MILLIS
    fun email(email:String) {
        if(!(email.length <= 254 && email.matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))))throw AuthFailure(AuthFailure.Kind.INVALID,"Informe um e-mail válido.")
    }
    fun credentials(email: String, password: String) {
        email(email)
        if(password.length !in 8..4096)throw AuthFailure(AuthFailure.Kind.INVALID,"A senha precisa ter pelo menos oito caracteres.")
    }
    fun signup(name: String, email: String, password: String, confirmation: String) {
        if(name.trim().length !in 2..200)throw AuthFailure(AuthFailure.Kind.INVALID,"Informe seu nome completo.")
        credentials(email,password); if(password!=confirmation)throw AuthFailure(AuthFailure.Kind.INVALID,"As senhas não coincidem.")
    }
}
interface AuthClient {
    suspend fun signUp(name: String, email: String, password: String, link: PendingLink)
    suspend fun login(email: String, password: String): AuthSession
    suspend fun refresh(session: AuthSession): AuthSession
    suspend fun verify(session: AuthSession): AuthSession
    suspend fun recover(email: String, link: PendingLink)
    suspend fun exchange(code: String, link: PendingLink): AuthSession
    suspend fun password(session: AuthSession, password: String)
    suspend fun logout(session: AuthSession)
}
