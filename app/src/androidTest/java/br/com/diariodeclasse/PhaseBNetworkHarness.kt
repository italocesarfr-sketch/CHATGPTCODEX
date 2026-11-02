package br.com.diariodeclasse

import android.util.Base64
import br.com.diariodeclasse.auth.*
import br.com.diariodeclasse.sync.SyncFailure
import br.com.diariodeclasse.sync.academic.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** This signing key and the entire harness exist ONLY in the instrumentation APK. */
object PhaseBNetworkHarness {
    @Volatile var phase:String?=null
    @Volatile var secondary=false
    @Volatile var released=false
    @Volatile private var committed=false
    fun droppedCommittedReply()=committed
    private const val secret="noctua-phase-b-disposable-jwt-secret-2026-only"
    fun token(other:Boolean=false):String {
        val owner=if(other)"22222222-2222-4222-8222-222222222222"else fixtureUser.id
        val sid=if(other)"cccccccc-cccc-4ccc-8ccc-cccccccccccc"else if(secondary)"bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"else"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        fun b64(bytes:ByteArray)=Base64.encodeToString(bytes,Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        val body=b64("""{"alg":"HS256","typ":"JWT"}""".toByteArray())+"."+
            b64("""{"sub":"$owner","role":"authenticated","session_id":"$sid","exp":${System.currentTimeMillis()/1000+3600}}""".toByteArray())
        val mac=Mac.getInstance("HmacSHA256");mac.init(SecretKeySpec(secret.toByteArray(),"HmacSHA256"))
        return body+"."+b64(mac.doFinal(body.toByteArray()))
    }
    fun raw()=SupabaseAcademicSync("http://10.0.2.2:3000/rpc","public-local-fixture")
    fun remote():AcademicRemote {
        val delegate=raw()
        return object:AcademicRemote by delegate {
            override suspend fun pull(token:String,after:Long):List<AcademicPage> {
                if(phase=="retry"&&!released || phase=="lost"&&committed)throw SyncFailure("NETWORK")
                return delegate.pull(token,after)
            }
            override suspend fun push(token:String,batch:AcademicBatch):AcademicPushReply {
                val reply=delegate.push(token,batch)
                if(phase=="lost"){committed=true;throw SyncFailure("NETWORK")}
                return reply
            }
        }
    }
    class Client:AuthClient by AcademicTestClient() {
        private fun valid()=fixtureSession().copy(access=token())
        override suspend fun login(email:String,password:String)=AcademicTestClient().login(email,password).copy(access=token())
        override suspend fun verify(session:AuthSession)=valid()
        override suspend fun refresh(session:AuthSession)=valid()
    }
}
