package br.com.diariodeclasse.sync.academic

import br.com.diariodeclasse.BuildConfig
import br.com.diariodeclasse.auth.AuthConfig
import br.com.diariodeclasse.sync.SyncFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

@Serializable data class AcademicVersion(val kind:String,val key:JsonObject,val revision:Long,val payload:JsonObject?) {
    val identity get()=AcademicKey(kind,key)
    fun change()=AcademicChange(kind,key,revision-1,payload).also{require(revision>0);it.validate()}
}
@Serializable data class AcademicPage(val operation:String,val sequence:Long,val records:List<AcademicVersion>) {
    fun validate(){require(java.util.UUID.fromString(operation).toString()==operation && sequence>0)
        require(records.size in 1..256 && records.map{it.identity}.distinct().size==records.size);records.forEach{it.change()}}
}
@Serializable data class AcademicPushReply(val operation:String,val accepted:Boolean,val sequence:Long?,val conflicts:JsonArray)
@Serializable data class AcademicProtocolStatus(val protocol:Int,val v1Sequence:Long,val v1:JsonArray,val testAllowed:Boolean=false)
interface AcademicRemote {
    suspend fun status(token:String):AcademicProtocolStatus
    suspend fun activate(token:String,expected:AcademicProtocolStatus):AcademicProtocolStatus
    suspend fun push(token:String,batch:AcademicBatch):AcademicPushReply
    suspend fun pull(token:String,after:Long):List<AcademicPage>
}

/** Same production origin/public key as Auth. Test override cannot target an arbitrary host or release. */
class SupabaseAcademicSync internal constructor(private val endpoint:String,private val apiKey:String):AcademicRemote {
    constructor():this("${AuthConfig.URL}/rest/v1/rpc",AuthConfig.KEY)
    init {require(endpoint=="${AuthConfig.URL}/rest/v1/rpc" ||
        BuildConfig.DEBUG && endpoint=="http://10.0.2.2:3000/rpc")}
    private suspend fun rpc(name:String,token:String,body:JsonObject):String=withContext(Dispatchers.IO) {
        require(token.isNotBlank())
        val connection=URL("$endpoint/$name").openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects=false;connection.requestMethod="POST"
            connection.connectTimeout=15000;connection.readTimeout=20000
            connection.setRequestProperty("apikey",apiKey);connection.setRequestProperty("Authorization","Bearer $token")
            connection.setRequestProperty("Content-Type","application/json");connection.doOutput=true
            val bytes=body.toString().toByteArray(Charsets.UTF_8);require(bytes.size<=1100*1024)
            connection.outputStream.use{it.write(bytes)}
            if(connection.responseCode !in 200..299)throw SyncFailure(when(connection.responseCode){401,403->"SESSION";404->"SETUP";409->"CONFLICT";429,503,504->"RETRY";else->"REMOTE"})
            connection.inputStream.use { input ->
                val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                while(true){val n=input.read(buffer);if(n<0)break;if(output.size()+n>2*1024*1024)throw SyncFailure("SIZE");output.write(buffer,0,n)}
                output.toString("UTF-8")
            }
        }catch(_:IOException){throw SyncFailure("NETWORK")}finally{connection.disconnect()}
    }
    override suspend fun status(token:String)=AcademicRecords.json.decodeFromString<AcademicProtocolStatus>(rpc("noctua_academic_status",token,buildJsonObject{}))
    override suspend fun activate(token:String,expected:AcademicProtocolStatus)=AcademicRecords.json.decodeFromString<AcademicProtocolStatus>(rpc("noctua_activate_academic",token,buildJsonObject {
        put("p_expected_sequence",expected.v1Sequence);put("p_expected_v1",expected.v1)
    }))
    override suspend fun push(token:String,batch:AcademicBatch):AcademicPushReply {
        batch.validate()
        return AcademicRecords.json.decodeFromString(rpc("noctua_push_academic",token,buildJsonObject {
            put("p_operation",batch.operation);put("p_changes",AcademicRecords.json.encodeToJsonElement(batch.changes))
        }))
    }
    override suspend fun pull(token:String,after:Long):List<AcademicPage> = AcademicRecords.json.decodeFromString(
        rpc("noctua_pull_academic",token,buildJsonObject{put("p_after",after)}))
}
