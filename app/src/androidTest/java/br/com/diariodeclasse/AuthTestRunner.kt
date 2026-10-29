package br.com.diariodeclasse

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import br.com.diariodeclasse.auth.*
import kotlinx.coroutines.runBlocking

/** This class exists only in the separate instrumentation APK, never in the app APK. */
class AuthTestRunner:AndroidJUnitRunner() {
    override fun newApplication(cl:ClassLoader,name:String,context:Context):Application {
        // AGP emits one instrumentation component. Legacy seed APKs have a final
        // DiaryApplication without auth; never load its new test subclass there.
        val hasAuth=cl.loadClass(name).methods.any{it.name=="getAuth"}
        return super.newApplication(cl,if(hasAuth)"br.com.diariodeclasse.AcademicTestApplication"else name,context)
    }
}
class AcademicTestVault:AuthVault {
    var value=AuthSecrets(session=fixtureSession(),legacyOwner=fixtureUser.id)
    override fun read()=value
    override fun write(value:AuthSecrets){this.value=value}
}
val fixtureUser=AuthUser("11111111-1111-4111-8111-111111111111","professor@example.invalid",true,"Professor Fictício")
fun fixtureSession()=AuthSession(fixtureUser,"fixture-access-not-real","fixture-refresh-not-real",System.currentTimeMillis()+3_600_000,System.currentTimeMillis(),System.currentTimeMillis())
class AcademicTestClient:AuthClient {
    override suspend fun login(email:String,password:String):AuthSession {
        if(email!=fixtureUser.email||password!="Fixture-Password-2026")throw AuthFailure(AuthFailure.Kind.INVALID,"Credenciais inválidas.")
        return fixtureSession()
    }
    override suspend fun verify(session:AuthSession)=session.copy(verifiedAt=System.currentTimeMillis(),lastSeen=System.currentTimeMillis())
    override suspend fun refresh(session:AuthSession)=fixtureSession()
    override suspend fun logout(session:AuthSession){}
    override suspend fun signUp(name:String,email:String,password:String,link:PendingLink){}
    override suspend fun recover(email:String,link:PendingLink){}
    override suspend fun exchange(code:String,link:PendingLink)=fixtureSession()
    override suspend fun password(session:AuthSession,password:String){}
}
class AcademicTestApplication:DiaryApplication() {
    private val fixtureVault=AcademicTestVault()
    override val auth by lazy{AuthController(AcademicTestClient(),fixtureVault)}
    // The academic suite uses the same legacy fixture DB, independently of UI authentication tests.
    // Production DiaryApplication has no such override or test credentials.
    override val workspace by lazy{AccountWorkspace(this,fixtureUser,true)}
    fun restoreFixtureUser()=runBlocking {auth.logout();auth.login(fixtureUser.email,"Fixture-Password-2026")}
}
