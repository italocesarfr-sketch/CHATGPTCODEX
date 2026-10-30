package br.com.diariodeclasse.sync

import br.com.diariodeclasse.auth.AuthConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Fixed origin, public key and the current confirmed user's bearer only. No request/response logging. */
class SupabaseClassSync:ClassSyncRemote {
    private suspend fun rpc(name:String,token:String,body:JsonObject):String=withContext(Dispatchers.IO) {
        val connection=URL("${AuthConfig.URL}/rest/v1/rpc/$name").openConnection() as HttpsURLConnection
        try {
            connection.instanceFollowRedirects=false;connection.requestMethod="POST"
            connection.connectTimeout=15000;connection.readTimeout=20000
            connection.setRequestProperty("apikey",AuthConfig.KEY)
            connection.setRequestProperty("Authorization","Bearer $token")
            connection.setRequestProperty("Content-Type","application/json")
            connection.doOutput=true
            connection.outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))}
            val status=connection.responseCode
            if(status !in 200..299)throw SyncFailure(when(status){401,403->"SESSION";404->"SETUP";429->"RETRY";else->"REMOTE"})
            connection.inputStream.use{input->
                val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                while(true){val n=input.read(buffer);if(n<0)break;if(output.size()+n>2*1024*1024)throw SyncFailure("SIZE");output.write(buffer,0,n)}
                output.toString("UTF-8")
            }
        }catch(_:IOException){throw SyncFailure("NETWORK")}
        finally{connection.disconnect()}
    }
    override suspend fun push(token:String,operation:SyncOperation):PushReply {
        val raw=rpc("noctua_push_class",token,buildJsonObject {
            put("p_operation",operation.id);put("p_id",operation.classId);put("p_base",operation.baseRevision)
            put("p_deleted",operation.kind=="DELETE")
            put("p_payload",if(operation.kind=="DELETE")JsonNull else Json.parseToJsonElement(operation.payload))
        })
        return ClassBundle.wire.decodeFromString(raw)
    }
    override suspend fun pull(token:String,after:Long):List<RemoteClass> =
        ClassBundle.wire.decodeFromString(rpc("noctua_pull_classes",token,buildJsonObject{put("p_after",after)}))
}

class ClassSyncEngine(private val store:ClassSyncStore,private val remote:ClassSyncRemote,
    private val session:suspend()->String,private val admitted:()->Boolean) {
    suspend fun run() {
        if(!admitted() || !store.enabled())return
        try {
            val token=session() // Online verification required: cached offline admission cannot authorize a push.
            for(operation in store.prepare()) {
                if(!admitted())return
                store.attempt(operation)
                val reply=remote.push(token,operation)
                if(!admitted())return
                store.accept(operation,reply)
            }
            repeat(20) {
                if(!admitted())return
                val rows=remote.pull(token,store.cursor())
                if(!admitted())return
                store.receive(rows)
                if(rows.size<100){store.message("",checked=true);return}
            }
            store.message("PENDING") // Bound background work; next scheduled run continues at the durable cursor.
        }catch(e:kotlinx.coroutines.CancellationException){throw e}
        catch(e:Exception){if(admitted())store.message((e as? SyncFailure)?.code ?: "RETRY")}
    }
}
