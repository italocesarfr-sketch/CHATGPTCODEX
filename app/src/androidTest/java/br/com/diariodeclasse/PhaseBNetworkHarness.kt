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
        val e=phase?.startsWith("e-")==true
        val c=phase?.startsWith("c-")==true
        val d=phase?.startsWith("d-")==true
        val owner=if(e)if(other)"99999999-9999-4999-8999-999999999999"else user().id else if(other)if(d)"77777777-7777-4777-8777-777777777777"else if(c)"55555555-5555-4555-8555-555555555555"else"22222222-2222-4222-8222-222222222222"else user().id
        val sid=if(e){if(other)"93939393-9393-4393-8393-939393939393"else if(secondary)"82828282-8282-4282-8282-828282828282"else"81818181-8181-4181-8181-818181818181"}else if(d){if(other)"12121212-1212-4212-8212-121212121212"else if(secondary)"13131313-1313-4313-8313-131313131313"else"14141414-1414-4414-8414-141414141414"}else if(c){if(other)"ffffffff-ffff-4fff-8fff-ffffffffffff"else if(secondary)"eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee"else"dddddddd-dddd-4ddd-8ddd-dddddddddddd"}else if(other)"cccccccc-cccc-4ccc-8ccc-cccccccccccc"else if(secondary)"bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"else"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        fun b64(bytes:ByteArray)=Base64.encodeToString(bytes,Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        val body=b64("""{"alg":"HS256","typ":"JWT"}""".toByteArray())+"."+
            b64("""{"sub":"$owner","role":"authenticated","session_id":"$sid","exp":${System.currentTimeMillis()/1000+3600}}""".toByteArray())
        val mac=Mac.getInstance("HmacSHA256");mac.init(SecretKeySpec(secret.toByteArray(),"HmacSHA256"))
        return body+"."+b64(mac.doFinal(body.toByteArray()))
    }
    fun user()=if(phase?.startsWith("e-")==true)fixtureUser.copy(id="88888888-8888-4888-8888-888888888888",email="phasee@example.invalid")else if(phase?.startsWith("d-")==true)fixtureUser.copy(id="66666666-6666-4666-8666-666666666666",email="phased@example.invalid")else if(phase?.startsWith("c-")==true)fixtureUser.copy(id="44444444-4444-4444-8444-444444444444",email="phasec@example.invalid")else fixtureUser
    fun raw()=SupabaseAcademicSync("http://10.0.2.2:3000/rpc","public-local-fixture")
    fun remote():AcademicRemote {
        val delegate=raw()
        return object:AcademicRemote by delegate {
            override suspend fun pull(token:String,after:Long):List<AcademicPage> {
                if(phase=="retry"&&!released || phase=="lost"&&committed)throw SyncFailure("NETWORK")
                return delegate.pull(token,after)
            }
            override suspend fun resolveE(token:String,operation:String,request:kotlinx.serialization.json.JsonObject,cancel:Boolean):AcademicPushReply {
                val reply=delegate.resolveE(token,operation,request,cancel)
                if(phase=="e-decision-lost"&&!cancel){committed=true;throw SyncFailure("NETWORK")}
                return reply
            }
            override suspend fun decisionE(token:String,operation:String):AcademicPushReply? {
                if(phase=="e-decision-lost"&&committed)throw SyncFailure("NETWORK")
                return delegate.decisionE(token,operation)
            }
            override suspend fun pullE(token:String,after:Long,offset:Int):List<AcademicFragment> {
                if(phase=="e-lost"&&committed)throw SyncFailure("NETWORK")
                return delegate.pullE(token,after,offset)
            }
            override suspend fun pushE(token:String,batch:AcademicBatch):AcademicPushReply {
                val reply=delegate.pushE(token,batch)
                if(phase=="e-lost"){committed=true;throw SyncFailure("NETWORK")}
                return reply
            }
            override suspend fun pullD(token:String,after:Long):List<AcademicPage> {
                if(phase=="d-retry"&&!released || phase=="d-lost"&&committed)throw SyncFailure("NETWORK")
                return delegate.pullD(token,after)
            }
            override suspend fun pushD(token:String,batch:AcademicBatch):AcademicPushReply {
                val reply=delegate.pushD(token,batch)
                if(phase=="d-lost"){committed=true;throw SyncFailure("NETWORK")}
                return reply
            }
            override suspend fun pullC(token:String,after:Long):List<AcademicPage> {
                if(phase=="c-retry"&&!released || phase=="c-lost"&&committed)throw SyncFailure("NETWORK")
                return delegate.pullC(token,after)
            }
            override suspend fun pushC(token:String,batch:AcademicBatch):AcademicPushReply {
                val reply=delegate.pushC(token,batch)
                if(phase=="c-lost"){committed=true;throw SyncFailure("NETWORK")}
                return reply
            }
            override suspend fun push(token:String,batch:AcademicBatch):AcademicPushReply {
                val reply=delegate.push(token,batch)
                if(phase=="lost"){committed=true;throw SyncFailure("NETWORK")}
                return reply
            }
        }
    }
    class Client:AuthClient by AcademicTestClient() {
        private fun valid()=fixtureSession().copy(user=user(),access=token())
        override suspend fun login(email:String,password:String)=AcademicTestClient().login(email,password).copy(access=token())
        override suspend fun verify(session:AuthSession)=valid()
        override suspend fun refresh(session:AuthSession)=valid()
    }
}
