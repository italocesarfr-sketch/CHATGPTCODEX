package br.com.diariodeclasse

import br.com.diariodeclasse.auth.*
import br.com.diariodeclasse.backup.BackupCodec
import br.com.diariodeclasse.sync.academic.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

internal class P1Stop(val code:String):Exception("P1_"+code)
internal fun p1Need(ok:Boolean,code:String){if(!ok)throw P1Stop(code)}
internal class P1Account(val email:String,val password:String,val id:String){override fun toString()="P1Account(redacted)"}

/** Instrumentation only. Normal Application startup, real Auth UI and account workspaces. */
class P1RecoverySupport(private val app:AcademicTestApplication,val mode:String,authorized:Boolean,second:Boolean) {
    init {
        require(BuildConfig.DEBUG&&mode in setOf("LOCAL","HOSTED"))
        if(mode=="HOSTED") {
            require(authorized&&BuildConfig.HOMOLOGACAO&&BuildConfig.APPLICATION_ID==AuthEnvironment.HOMOLOGATION_ID)
            AuthConfig.requireValid();require(AuthConfig.URL=="https://sscexzxtqdcsqdlcytum.supabase.co")
        } else {
            require(!BuildConfig.HOMOLOGACAO)
            PhaseBNetworkHarness.phase="e-p1";PhaseBNetworkHarness.secondary=second
        }
    }
    private val input by lazy {
        val f=File(app.filesDir,"p1-private.json");p1Need(f.isFile&&f.length()<=65536,"INPUT")
        val j=AcademicRecords.json.parseToJsonElement(f.readText()).jsonObject
        p1Need(j.keys==setOf("mode","accounts","fixtureClassId","consentP1")&&j.getValue("mode").jsonPrimitive.content==mode&&j.getValue("consentP1").jsonPrimitive.content=="YES","INPUT")
        j
    }
    internal val accounts by lazy {
        input.getValue("accounts").jsonArray.map { a->val j=a.jsonObject
            p1Need(j.keys==setOf("email","password","expectedId"),"INPUT")
            P1Account(j.getValue("email").jsonPrimitive.content,j.getValue("password").jsonPrimitive.content,j.getValue("expectedId").jsonPrimitive.content).also {
                p1Need(java.util.UUID.fromString(it.id).toString()==it.id&&it.id!=AuthConfig.LEGACY_OWNER_ID,"INPUT")
            }
        }.also{p1Need(it.size==2&&it[0].id!=it[1].id&&it[0].email!=it[1].email,"INPUT")}
    }
    internal val fixture get()=input.getValue("fixtureClassId").jsonPrimitive.content
    private fun expected(session:AuthSession) {p1Need(accounts.any{it.id==session.user.id&&it.email.equals(session.user.email,true)}&&session.user.confirmed,"IDENTITY")}
    private val delegate:AuthClient by lazy {
        if(mode=="HOSTED")SupabaseAuthClient()else object:AuthClient by AcademicTestClient() {
            private fun make(a:P1Account,refresh:String="p1-local-"+java.util.UUID.randomUUID())=fixtureSession().copy(
                user=AuthUser(a.id,a.email,true,"P1 Fictício"),access=PhaseBNetworkHarness.token(other=accounts.indexOf(a)==1),refresh=refresh)
            override suspend fun login(email:String,password:String)=make(accounts.single{it.email.equals(email,true)&&it.password==password})
            override suspend fun verify(session:AuthSession)=make(accounts.single{it.id==session.user.id},session.refresh)
            override suspend fun refresh(session:AuthSession)=verify(session)
            override suspend fun logout(session:AuthSession){}
        }
    }
    private class Created(var session:AuthSession,var ended:Boolean=false)
    private val created=mutableListOf<Created>()
    @Volatile var loginCalls=0;private set
    @Volatile var logoutCalls=0;private set
    private fun record(s:AuthSession)=synchronized(created){created.singleOrNull{it.session.refresh==s.refresh}}
    private val tracked=object:AuthClient {
        override suspend fun login(email:String,password:String):AuthSession {
            p1Need(accounts.any{it.email.equals(email,true)&&it.password==password},"AUTH_SCOPE")
            synchronized(created){p1Need(created.size<8,"LIMIT")}
            return delegate.login(email,password).also{expected(it);synchronized(created){created+=Created(it);loginCalls++}}
        }
        override suspend fun verify(session:AuthSession):AuthSession {
            val r=record(session);p1Need(r!=null&&!r.ended,"AUTH_SCOPE")
            return delegate.verify(session).also{expected(it);synchronized(created){r!!.session=it}}
        }
        override suspend fun refresh(session:AuthSession):AuthSession {
            val r=record(session);p1Need(r!=null&&!r.ended,"AUTH_SCOPE")
            return delegate.refresh(session).also{expected(it);synchronized(created){r!!.session=it}}
        }
        override suspend fun logout(session:AuthSession) {
            val r=record(session);p1Need(r!=null,"AUTH_SCOPE")
            if(r!!.ended)return
            delegate.logout(session);synchronized(created){r.ended=true;logoutCalls++}
        }
        private fun denied():Nothing=throw P1Stop("AUTH_SCOPE")
        override suspend fun signUp(name:String,email:String,password:String,link:PendingLink)=denied()
        override suspend fun recover(email:String,link:PendingLink)=denied()
        override suspend fun exchange(code:String,link:PendingLink):AuthSession=denied()
        override suspend fun password(session:AuthSession,password:String)=denied()
    }
    val auth by lazy{AuthController(tracked,KeystoreAuthVault(app))}
    val readonly by lazy{H05ReadOnlyRemote(if(mode=="HOSTED")SupabaseAcademicSync()else PhaseBNetworkHarness.raw())}
    @Volatile var directReads=0;private set
    val readCalls get()=readonly.reads+directReads
    val remote by lazy {object:AcademicRemote by readonly {
        override suspend fun status(token:String):AcademicProtocolStatus {
            p1Need(readCalls<512,"LIMIT")
            return readonly.status(token).also{p1Need(it.protocol==2&&it.academicScope==5&&it.testAllowed,"SCOPE");p1Need(it.academicSequence in 0..256,"LIMIT")}
        }
        override suspend fun pullE(token:String,after:Long,offset:Int):List<AcademicFragment> {
            p1Need(readCalls<512,"LIMIT");return readonly.pullE(token,after,offset)
        }
    }}
    /** Fixed SELECT endpoints only; never a caller-supplied URL/method or administrative token. */
    internal suspend fun select(token:String,owner:String,audits:Boolean):JsonArray=withContext(Dispatchers.IO) {
        p1Need(java.util.UUID.fromString(owner).toString()==owner&&readCalls<512,"LIMIT");directReads++
        val base=if(mode=="HOSTED")AuthConfig.URL+"/rest/v1"else"http://10.0.2.2:3000"
        val path=if(audits)"noctua_e_decisions?select=resolution_id,request,evidence,response&owner_id=eq.$owner&order=resolution_id.asc&limit=257"
            else "noctua_academic_records?select=kind&owner_id=eq.$owner&limit=1"
        val c=URL("$base/$path").openConnection() as HttpURLConnection
        c.requestMethod="GET";c.instanceFollowRedirects=false;c.connectTimeout=10000;c.readTimeout=30000
        c.setRequestProperty("apikey",if(mode=="HOSTED")AuthConfig.KEY else"public-local-fixture")
        c.setRequestProperty("Authorization","Bearer $token")
        try {
            p1Need(c.responseCode==200,"READ_HTTP")
            val bytes=c.inputStream.use{source->
                val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                while(true){val n=source.read(buffer);if(n<0)break;p1Need(out.size()+n<=2*1024*1024,"LIMIT");out.write(buffer,0,n)}
                out.toByteArray()
            }
            AcademicRecords.json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonArray.also{p1Need(it.size<=if(audits)256 else 1,"LIMIT")}
        }finally{c.disconnect()}
    }
    internal suspend fun inventory(token:String)=p1Inventory(readonly,token)
    internal suspend fun cleanup():Boolean {
        for(r in synchronized(created){created.toList()})if(!r.ended) {
            try{tracked.logout(r.session)}catch(_:Exception){return false}
        }
        return synchronized(created){created.isNotEmpty()&&created.all{it.ended}}
    }
    internal fun ledgerHash(rows:JsonArray)=BackupCodec.hash(AcademicRecords.canonical(rows).toByteArray())
}

internal suspend fun p1Inventory(remote:H05ReadOnlyRemote,token:String):H05Inventory {
    val s=remote.status(token)
    p1Need(s.protocol==2&&s.academicScope==5&&s.testAllowed,"SCOPE");p1Need(s.academicSequence in 0..256,"LIMIT")
    if(s.academicSequence==0L) {
        p1Need(remote.pullE(token,0,0).isEmpty()&&remote.status(token)==s,"REMOTE_CHANGED")
        return H05Inventory(emptyMap(),emptyList(),0)
    }
    return recoveryInventory(remote,token)
}
