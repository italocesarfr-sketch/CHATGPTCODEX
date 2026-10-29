package br.com.diariodeclasse.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.net.URL
import java.net.URLEncoder
import java.io.IOException
import javax.net.ssl.HttpsURLConnection

/** GoTrue HTTPS API. No academic endpoint, redirect following, HTTP logger or administrative key. */
class SupabaseAuthClient(private val now: () -> Long = System::currentTimeMillis,private val connected:()->Boolean={true}) : AuthClient {
    private val json=Json { ignoreUnknownKeys=true }
    private suspend fun request(method:String,path:String,body:JsonObject?=null,token:String?=null):JsonObject = withContext(Dispatchers.IO) {
        if(!connected())throw AuthFailure(AuthFailure.Kind.NETWORK,"Sem conexão com o serviço. Confira a internet.")
        val connection=URL("${AuthConfig.URL}/auth/v1/$path").openConnection() as HttpsURLConnection
        try {
            connection.requestMethod=method;connection.instanceFollowRedirects=false
            connection.connectTimeout=15000;connection.readTimeout=20000
            connection.setRequestProperty("apikey",AuthConfig.KEY)
            connection.setRequestProperty("Content-Type","application/json")
            token?.let { connection.setRequestProperty("Authorization","Bearer $it") }
            if(body!=null) { connection.doOutput=true;connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) } }
            val status=connection.responseCode
            val stream=if(status in 200..299)connection.inputStream else connection.errorStream
            val raw=stream?.use { input ->
                val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                while(true){val count=input.read(buffer);if(count<0)break;check(output.size()+count<=262144);output.write(buffer,0,count)}
                output.toByteArray().toString(Charsets.UTF_8) }.orEmpty()
            val result=if(raw.isBlank())JsonObject(emptyMap())else runCatching {json.parseToJsonElement(raw).jsonObject}.getOrElse{JsonObject(emptyMap())}
            if(status !in 200..299) {
                val code=result["error_code"]?.jsonPrimitive?.contentOrNull
                throw when {
                    code=="email_not_confirmed" -> AuthFailure(AuthFailure.Kind.UNCONFIRMED,"Confirme seu e-mail antes de entrar.")
                    status==429 -> AuthFailure(AuthFailure.Kind.RATE_LIMIT,"Muitas tentativas. Aguarde antes de tentar novamente.")
                    status>=500 -> AuthFailure(AuthFailure.Kind.SERVER,"Serviço indisponível. Tente novamente mais tarde.")
                    status in listOf(401,403) || code in listOf("refresh_token_not_found","refresh_token_already_used","session_not_found") -> AuthFailure(AuthFailure.Kind.SESSION,"Sessão encerrada. Entre novamente.")
                    else -> AuthFailure(AuthFailure.Kind.INVALID,"Não foi possível autenticar. Confira os dados ou solicite recuperação.")
                }
            }
            result
        } catch(e:IOException) { throw AuthFailure(AuthFailure.Kind.NETWORK,"Sem conexão com o serviço. Confira a internet.") }
        finally { connection.disconnect() }
    }
    private fun user(value:JsonObject):AuthUser {
        if(value["email_confirmed_at"]?.jsonPrimitive?.contentOrNull==null)throw AuthFailure(AuthFailure.Kind.UNCONFIRMED,"Confirme seu e-mail antes de entrar.")
        if(value["is_anonymous"]?.jsonPrimitive?.booleanOrNull==true)throw AuthFailure(AuthFailure.Kind.SESSION,"Esta conta não permite acesso aos registros.")
        return AuthUser(value.getValue("id").jsonPrimitive.content,value.getValue("email").jsonPrimitive.content,true,
            value["user_metadata"]?.jsonObject?.get("full_name")?.jsonPrimitive?.contentOrNull.orEmpty()).also { it.validate() }
    }
    private suspend fun parseSession(value:JsonObject):AuthSession {
        val access=value.getValue("access_token").jsonPrimitive.content
        val current=user(request("GET","user",token=access)) // Server-confirmed identity, never trust a decoded JWT alone.
        val stamp=now()
        return AuthSession(current,access,value.getValue("refresh_token").jsonPrimitive.content,
            value["expires_at"]?.jsonPrimitive?.longOrNull?.times(1000) ?: stamp+value.getValue("expires_in").jsonPrimitive.long*1000,stamp,stamp)
    }
    override suspend fun signUp(name:String,email:String,password:String,link:PendingLink) {
        request("POST","signup?redirect_to=${URLEncoder.encode(link.redirect,"UTF-8")}",buildJsonObject {
            put("email",email);put("password",password);putJsonObject("data"){put("full_name",name)}
            put("code_challenge",link.challenge);put("code_challenge_method","s256")
        })
    }
    override suspend fun login(email:String,password:String)=parseSession(request("POST","token?grant_type=password",buildJsonObject{put("email",email);put("password",password)}))
    override suspend fun refresh(session:AuthSession)=parseSession(request("POST","token?grant_type=refresh_token",buildJsonObject{put("refresh_token",session.refresh)})).also {
        if(it.user.id!=session.user.id)throw AuthFailure(AuthFailure.Kind.SESSION,"A identidade da sessão mudou. Entre novamente.")
    }
    override suspend fun verify(session:AuthSession):AuthSession {
        val current=user(request("GET","user",token=session.access))
        if(current.id!=session.user.id)throw AuthFailure(AuthFailure.Kind.SESSION,"A identidade da sessão mudou. Entre novamente.")
        return session.copy(user=current,verifiedAt=now(),lastSeen=now())
    }
    override suspend fun recover(email:String,link:PendingLink) {
        request("POST","recover?redirect_to=${URLEncoder.encode(link.redirect,"UTF-8")}",buildJsonObject {put("email",email);put("code_challenge",link.challenge);put("code_challenge_method","s256")})
    }
    override suspend fun exchange(code:String,link:PendingLink)=parseSession(request("POST","token?grant_type=pkce",buildJsonObject{put("auth_code",code);put("code_verifier",link.verifier)}))
    override suspend fun password(session:AuthSession,password:String) { request("PUT","user",buildJsonObject{put("password",password)},session.access) }
    override suspend fun logout(session:AuthSession) { request("POST","logout?scope=local",token=session.access) }
}
