package br.com.diariodeclasse

import br.com.diariodeclasse.auth.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import javax.net.ssl.HttpsURLConnection

/** Opt-in only. Two explicitly authorized fictitious accounts, no SQL/admin fixture. */
class HomologationHostedAuthTest {
    private data class Account(val email:String,val password:String,val id:String) {
        override fun toString()="Account(redacted)"
    }
    private data class Input(val accounts:List<Account>,val fixture:String,val permissionProbe:Boolean) {
        override fun toString()="Input(redacted)"
    }
    private fun input():Input {
        assumeTrue("Integração hospedada exclusiva da variante de homologação.",BuildConfig.HOMOLOGACAO)
        assumeTrue("Sessões hospedadas exigem autorização explícita separada do build.",
            System.getenv("NOCTUA_HOMOLOGACAO_AUTH_TEST_AUTHORIZED")=="YES")
        val path=System.getenv("NOCTUA_HOMOLOGACAO_AUTH_TEST_FILE")
        assumeTrue("Sessões de teste não fornecidas; não houve contato hospedado.",!path.isNullOrBlank())
        AuthConfig.requireValid()
        val j=try{Json.parseToJsonElement(File(requireNotNull(path)).readText()).jsonObject}
        catch(_:Exception){error("Arquivo privado inválido ou indisponível; conteúdo omitido.")}
        val accounts=j.getValue("accounts").jsonArray.map { v->v.jsonObject.let {
            Account(it.getValue("email").jsonPrimitive.content,it.getValue("password").jsonPrimitive.content,it.getValue("expectedId").jsonPrimitive.content)
        }}
        require(accounts.size==2&&accounts.map{it.id}.distinct().size==2)
        accounts.forEach { require(UUID.fromString(it.id).toString()==it.id&&it.id!=AuthConfig.LEGACY_OWNER_ID);AuthPolicy.credentials(it.email,it.password) }
        require(accounts.map{it.email.lowercase()}.distinct().size==2)
        val fixture=j.getValue("fixtureClassId").jsonPrimitive.content
        require(fixture.length in 1..128)
        return Input(accounts,fixture,j["allowZeroRowPermissionProbe"]?.jsonPrimitive?.booleanOrNull==true)
    }
    private fun request(path:String,token:String?=null,method:String="GET",body:JsonObject?=null):Pair<Int,JsonElement?> {
        AuthConfig.requireValid()
        check(AuthConfig.URL=="https://sscexzxtqdcsqdlcytum.supabase.co")
        val c=URL("${AuthConfig.URL}/rest/v1/$path").openConnection() as HttpsURLConnection
        try {
            c.instanceFollowRedirects=false;c.requestMethod=method;c.connectTimeout=15000;c.readTimeout=20000
            c.setRequestProperty("apikey",AuthConfig.KEY);token?.let{c.setRequestProperty("Authorization","Bearer $it")}
            if(body!=null){c.setRequestProperty("Content-Type","application/json");c.doOutput=true;c.outputStream.use{it.write(body.toString().toByteArray())}}
            val status=c.responseCode
            if(status !in 200..299)return status to null // Never log error bodies or bearer values.
            val bytes=c.inputStream.use{it.readNBytes(262145)};check(bytes.size<=262144)
            return status to bytes.toString(Charsets.UTF_8).takeIf{it.isNotBlank()}?.let{
                try{Json.parseToJsonElement(it)}catch(_:Exception){error("Resposta de teste inválida; conteúdo omitido.")}
            }
        }finally{c.disconnect()}
    }
    private fun query(i:Input):String {
        val key=buildJsonObject{put("id",i.fixture)}.toString()
        return "noctua_academic_records?kind=eq.classes&deleted=eq.false&owner_id=eq.${i.accounts[0].id}&key=eq.${URLEncoder.encode(key,"UTF-8")}&select=owner_id,revision,key"
    }
    private fun own(i:Input,s:AuthSession):JsonArray {
        val(status,value)=request(query(i),s.access);assertEquals("Leitura da fixture A indisponível",200,status)
        val rows=requireNotNull(value).jsonArray
        assertEquals("Cadastre exatamente a turma fictícia A antes da prova",1,rows.size)
        assertTrue(rows.single().jsonObject.getValue("owner_id").jsonPrimitive.content==i.accounts[0].id)
        return rows
    }
    @Test fun h01ConfirmedLoginIdentityRefreshAndWrongPassword():Unit=runBlocking {
        val i=input();val c=SupabaseAuthClient()
        for(a in i.accounts){var session=c.login(a.email,a.password)
            try {
                assertTrue("Identidade inesperada",session.user.id==a.id&&session.user.confirmed)
                assertTrue(c.verify(session).user.id==a.id);session=c.refresh(session);assertTrue(session.user.id==a.id)
                try{c.login(a.email,"Intentionally-incorrect-fixture-password-2026");fail("Senha incorreta aceita")}
                catch(e:AuthFailure){assertTrue(e.kind in setOf(AuthFailure.Kind.INVALID,AuthFailure.Kind.SESSION))}
            }finally{runCatching{c.logout(session)}}
        }
    }
    @Test fun h02RealOrdinaryAccountCannotReadTheOtherOwnersKnownFixture():Unit=runBlocking {
        val i=input();val c=SupabaseAuthClient();val a=c.login(i.accounts[0].email,i.accounts[0].password)
        try {own(i,a);val b=c.login(i.accounts[1].email,i.accounts[1].password)
            try{assertTrue(b.user.id==i.accounts[1].id)
                val(status,value)=request(query(i),b.access);assertEquals(200,status);assertTrue(requireNotNull(value).jsonArray.isEmpty())
            }finally{runCatching{c.logout(b)}}
        }finally{runCatching{c.logout(a)}}
    }
    @Test fun h03PrivateHelpersAndExplicitlyAuthorizedZeroRowPatchAreDenied():Unit=runBlocking {
        val i=input();assumeTrue("Probe de permissão sem linhas não autorizado.",i.permissionProbe)
        val c=SupabaseAuthClient();val a=c.login(i.accounts[0].email,i.accounts[0].password)
        try {
            val before=own(i,a);val revision=before.single().jsonObject.getValue("revision").jsonPrimitive.long
            require(revision>0)
            val(helper,_)=request("rpc/noctua_d_index",a.access,"POST",buildJsonObject{put("p_rows",JsonArray(emptyList()))})
            assertTrue("Helper privado executável",helper in setOf(401,403,404))
            // Contradictory predicate: no row can have this positive revision AND revision<0.
            // Even a permission regression cannot modify any record through this request.
            val filter=query(i)+"&and="+URLEncoder.encode("(revision.eq.$revision,revision.lt.0)","UTF-8")
            val(patch,_)=request(filter,a.access,"PATCH",buildJsonObject{put("revision",revision)})
            assertTrue("Escrita direta não foi recusada por permissão",patch in setOf(401,403))
            assertEquals("Fixture mudou durante a prova",before,own(i,a))
        }finally{runCatching{c.logout(a)}}
    }
    @Test fun h04AnonymousInvalidBearerAndRevokedSessionCannotInvokeAcademicRpc():Unit=runBlocking {
        val i=input();val c=SupabaseAuthClient();val a=c.login(i.accounts[0].email,i.accounts[0].password)
        try {
            for(token in listOf(null,"invalid-fictitious-bearer")) {
                val(status,_)=request("rpc/noctua_academic_status_c",token,"POST",buildJsonObject{})
                assertTrue("Requisição sem sessão válida aceita",status in setOf(401,403))
            }
            c.logout(a)
            val(status,_)=request("rpc/noctua_academic_status_c",a.access,"POST",buildJsonObject{})
            assertTrue("Sessão revogada autorizou RPC",status in setOf(401,403))
            try{c.refresh(a);fail("Refresh revogado aceito")}catch(e:AuthFailure){assertTrue(e.kind in setOf(AuthFailure.Kind.SESSION,AuthFailure.Kind.INVALID))}
        }finally{runCatching{c.logout(a)}}
    }
}
