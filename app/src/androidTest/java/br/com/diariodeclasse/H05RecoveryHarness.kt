package br.com.diariodeclasse

import br.com.diariodeclasse.auth.*
import br.com.diariodeclasse.sync.academic.*
import kotlinx.serialization.json.JsonObject

class H05RecoveryApplication:DiaryApplication() {
    var h05Mode="";private set
    fun configure(mode:String,authorized:Boolean) {
        require(BuildConfig.DEBUG)
        if(mode=="HOSTED") {
            require(authorized&&BuildConfig.HOMOLOGACAO&&BuildConfig.APPLICATION_ID==AuthEnvironment.HOMOLOGATION_ID)
            AuthConfig.requireValid();require(AuthConfig.URL=="https://sscexzxtqdcsqdlcytum.supabase.co")
        }else {
            require(mode=="LOCAL"&&!BuildConfig.HOMOLOGACAO)
            PhaseBNetworkHarness.phase="e-h05";PhaseBNetworkHarness.secondary=false
        }
        h05Mode=mode
    }
    // Controlled test lifecycle: do not start Activity/scheduled jobs before consent.
    // Real AuthController/Keystore/workspace/Room/engine remain in use below.
    override fun onCreate() { require(h05Mode in setOf("LOCAL","HOSTED")) }
    override val auth by lazy {
        val client:AuthClient=if(h05Mode=="HOSTED")SupabaseAuthClient()else object:AuthClient by PhaseBNetworkHarness.Client() {
            override suspend fun login(email:String,password:String):AuthSession {
                AcademicTestClient().login(email,password) // Existing synthetic credential validator, local only.
                return fixtureSession().copy(user=PhaseBNetworkHarness.user(),access=PhaseBNetworkHarness.token())
            }
        }
        AuthController(client,KeystoreAuthVault(this))
    }
    val readonly by lazy { H05ReadOnlyRemote(if(h05Mode=="HOSTED")SupabaseAcademicSync()else PhaseBNetworkHarness.raw()) }
    override fun academicRemote():AcademicRemote=readonly
}

class H05BlockedWrite:Exception("H05_OUTBOUND_WRITE_BLOCKED")
/** Allow exactly the existing read-only RPCs. Never delegate a scope change or write. */
class H05ReadOnlyRemote(private val delegate:AcademicRemote):AcademicRemote {
    var blockedWrites=0;private set
    var reads=0;private set
    var transportMillis=0L;private set
    private fun blocked():Nothing { synchronized(this){blockedWrites++};throw H05BlockedWrite() }
    private suspend fun <T> measure(block:suspend()->T):T {
        val start=android.os.SystemClock.elapsedRealtime()
        synchronized(this){reads++;if(reads>512)throw br.com.diariodeclasse.sync.SyncFailure("SIZE")}
        try{return block()}finally{synchronized(this){transportMillis+=android.os.SystemClock.elapsedRealtime()-start}}
    }
    override suspend fun status(token:String)=measure{delegate.status(token)}
    override suspend fun pullE(token:String,after:Long,offset:Int)=measure{
        delegate.pullE(token,after,offset).also { page ->
            require(page.size<=1&&page.all{it.total<=1000}) {"H05_VOLUME_LIMIT"}
        }
    }
    override suspend fun activate(token:String,expected:AcademicProtocolStatus):AcademicProtocolStatus=blocked()
    override suspend fun enableC(token:String,expectedSequence:Long):AcademicProtocolStatus=blocked()
    override suspend fun enableD(token:String,expectedSequence:Long):AcademicProtocolStatus=blocked()
    override suspend fun enableE(token:String,expectedSequence:Long):AcademicProtocolStatus=blocked()
    override suspend fun push(token:String,batch:AcademicBatch):AcademicPushReply=blocked()
    override suspend fun pushC(token:String,batch:AcademicBatch):AcademicPushReply=blocked()
    override suspend fun pushD(token:String,batch:AcademicBatch):AcademicPushReply=blocked()
    override suspend fun stageE(token:String,batch:AcademicBatch,admitted:()->Boolean):Unit=blocked()
    override suspend fun pushE(token:String,batch:AcademicBatch,admitted:()->Boolean):AcademicPushReply=blocked()
    override suspend fun resolveE(token:String,operation:String,request:JsonObject,cancel:Boolean):AcademicPushReply=blocked()
    override suspend fun decisionE(token:String,operation:String):AcademicPushReply?=throw IllegalStateException("H05_UNSUPPORTED_READ")
    override suspend fun pull(token:String,after:Long):List<AcademicPage> = throw IllegalStateException("H05_UNSUPPORTED_READ")
    override suspend fun pullC(token:String,after:Long):List<AcademicPage> = throw IllegalStateException("H05_UNSUPPORTED_READ")
    override suspend fun pullD(token:String,after:Long):List<AcademicPage> = throw IllegalStateException("H05_UNSUPPORTED_READ")
}

internal fun h05RowsHash(rows:List<AcademicRow>):String = br.com.diariodeclasse.backup.BackupCodec.hash(
    AcademicRecords.canonical(AcademicRecords.json.encodeToJsonElement(
        kotlinx.serialization.builtins.ListSerializer(AcademicRow.serializer()),
        rows.sortedWith(compareBy({it.kind},{AcademicRecords.canonical(it.key)})))).toByteArray())

internal fun h05TypeSummary(local:List<AcademicRow>,remote:List<AcademicRow>) = kotlinx.serialization.json.JsonArray(
    AcademicKind.entries.map { kind ->
        val l=local.filter{it.kind==kind.wire};val r=remote.filter{it.kind==kind.wire}
        kotlinx.serialization.json.buildJsonObject {
            put("kind",kotlinx.serialization.json.JsonPrimitive(kind.wire))
            put("local_count",kotlinx.serialization.json.JsonPrimitive(l.size))
            put("remote_count",kotlinx.serialization.json.JsonPrimitive(r.size))
            put("local_hash",kotlinx.serialization.json.JsonPrimitive(h05RowsHash(l)))
            put("remote_hash",kotlinx.serialization.json.JsonPrimitive(h05RowsHash(r)))
            put("present",kotlinx.serialization.json.JsonPrimitive(r.isNotEmpty()))
        }
    })
