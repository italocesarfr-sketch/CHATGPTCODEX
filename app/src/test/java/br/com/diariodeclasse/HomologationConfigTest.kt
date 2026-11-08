package br.com.diariodeclasse

import br.com.diariodeclasse.auth.*
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class HomologationConfigTest {
    // Explicit synthetic public configuration for pure validation only; never packaged.
    private val fixture="sb_publishable_synthetic_unit_test_configuration"
    private fun hash(s:String)=MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString(""){"%02x".format(it)}
    private fun validate(project:String=AuthEnvironment.HOMOLOGATION_PROJECT,
        url:String="https://$project.supabase.co",key:String=fixture,
        id:String=AuthEnvironment.HOMOLOGATION_ID,scheme:String=id,debug:Boolean=true,
        denied:String=hash("different-public-key")) = AuthEnvironment.validate(true,debug,id,project,url,key,scheme,denied)
    private fun denied(block:()->Unit){try{block();fail("Configuração insegura foi aceita")}catch(_:IllegalArgumentException){}}
    @Test fun exactHomologationIdentityIsAccepted(){validate()}
    @Test fun absentOrAdministrativeOrMalformedKeyFailsClosed(){
        for(key in listOf("","sb_secret_never-a-client-key","service_role","eyJ-not-a-publishable-key",fixture+"\n"))denied{validate(key=key)}
    }
    @Test fun productionPublicKeyFingerprintIsRejected(){denied{validate(denied=hash(fixture))}}
    @Test fun productionAndArbitraryOriginsAndRedirectsAreRejected(){
        for(url in listOf("https://mpsgdchpgyxoqowfjoqm.supabase.co","https://example.invalid","http://sscexzxtqdcsqdlcytum.supabase.co","https://sscexzxtqdcsqdlcytum.supabase.co/","https://sscexzxtqdcsqdlcytum.supabase.co@evil.invalid"))denied{validate(url=url)}
        denied{validate(project="mpsgdchpgyxoqowfjoqm")}
    }
    @Test fun sharedOfficialIdOrCallbackOrNonDebugBuildIsRejected(){
        denied{validate(id="br.com.diariodeclasse")};denied{validate(scheme="br.com.diariodeclasse")};denied{validate(debug=false)}
    }
    @Test fun compiledVariantHasFixedIdentityAndCallbackEvenWithoutAKey(){
        if(BuildConfig.HOMOLOGACAO){
            assertEquals("br.com.diariodeclasse.homologacao",BuildConfig.APPLICATION_ID)
            assertEquals("https://sscexzxtqdcsqdlcytum.supabase.co",AuthConfig.URL)
            assertEquals("br.com.diariodeclasse.homologacao://auth/callback",AuthConfig.CALLBACK)
            assertTrue(BuildConfig.DEBUG)
            if(BuildConfig.SUPABASE_PUBLISHABLE_KEY.isBlank())denied{AuthConfig.requireValid()}
        }else{
            assertEquals("br.com.diariodeclasse",BuildConfig.APPLICATION_ID)
            assertEquals("https://mpsgdchpgyxoqowfjoqm.supabase.co",AuthConfig.URL)
            assertEquals("br.com.diariodeclasse://auth/callback",AuthConfig.CALLBACK)
            AuthConfig.requireValid()
        }
    }
    @Test fun pkceAcceptsOnlyThisVariantsCallbackAndPreservesStateExpiryGuards(){
        val link=PendingLink.create("recovery",1000)
        val valid="${link.redirect}&code=fictitious-valid-code"
        assertEquals("fictitious-valid-code",link.codeFrom(valid,1001))
        val other=if(BuildConfig.HOMOLOGACAO)"br.com.diariodeclasse"else"br.com.diariodeclasse.homologacao"
        for(raw in listOf(valid.replace(AuthConfig.CALLBACK_SCHEME+"://",other+"://"),valid+"&code=duplicate",valid+"#access_token=fictitious",valid.replace(link.state,"wrong-state"),valid.replace("/callback","/callback/extra"))){
            try{link.codeFrom(raw,1001);fail("Retorno indevido aceito")}catch(_:AuthFailure){}
        }
        try{link.codeFrom(valid,31*60_000);fail("Retorno vencido aceito")}catch(_:AuthFailure){}
    }
    @Test fun localGatewayOverrideIsUnavailableInHomologation(){
        if(BuildConfig.HOMOLOGACAO)denied{br.com.diariodeclasse.sync.academic.SupabaseAcademicSync("http://10.0.2.2:3000/rpc",fixture)}
    }
}
