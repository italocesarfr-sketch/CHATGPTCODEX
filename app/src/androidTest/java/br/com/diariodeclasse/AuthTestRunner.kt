package br.com.diariodeclasse

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import br.com.diariodeclasse.auth.*
import kotlinx.coroutines.runBlocking

/** This class exists only in the separate instrumentation APK, never in the app APK. */
class AuthTestRunner:AndroidJUnitRunner() {
    private var h05Mode:String?=null
    private var h05Authorized=false
    private var h12Mode:String?=null
    private var h12Authorized=false
    private var phaseB:String?=null
    private var secondary=false
    private var expectedFont:Float?=null
    override fun onCreate(arguments:android.os.Bundle) {
        h05Mode=arguments.getString("h05Mode")
        h12Mode=arguments.getString("h12Mode")
        h12Authorized=arguments.getString("h12Authorized")=="YES"
        h12Mode?.let{require(it in setOf("LOCAL","HOSTED")&&h05Mode==null&&arguments.getString("phaseBNetworkPhase")==null){"H12_INVALID_MODE"}}
        h05Authorized=arguments.getString("h05Authorized")=="YES"
        h05Mode?.let{require(it in setOf("LOCAL","HOSTED")&&arguments.getString("phaseBNetworkPhase")==null){"H05_INVALID_MODE"}}
        arguments.getString("phaseBNetworkPhase")?.let { phase ->
            phaseB=phase
            secondary=arguments.getString("phaseBDevice")=="secondary"
        }
        expectedFont=arguments.getString("expectedSystemFontScale")?.toFloat()
        super.onCreate(arguments)
    }
    override fun onStart() {
        // SettingsProvider commits before ActivityManager has necessarily delivered the
        // native configuration. Launching the test Activity in that gap can recreate it
        // and lose setContent. Observe real resources; never inject a test Density.
        val desired=expectedFont ?: android.provider.Settings.System.getFloat(targetContext.contentResolver,android.provider.Settings.System.FONT_SCALE,1f)
        require(desired.isFinite()&&desired>0)
        val deadline=android.os.SystemClock.uptimeMillis()+10000
        var observed=0f
        do {
            waitForIdleSync()
            runOnMainSync{observed=targetContext.resources.configuration.fontScale}
            if(kotlin.math.abs(observed-desired)<0.01f)break
        }while(android.os.SystemClock.uptimeMillis()<deadline)
        check(kotlin.math.abs(observed-desired)<0.01f){"Native font configuration not delivered: expected=$desired resources=$observed settings="+android.provider.Settings.System.getFloat(targetContext.contentResolver,android.provider.Settings.System.FONT_SCALE,1f)}
        super.onStart()
    }
    override fun callApplicationOnCreate(app:Application) {
        // Choose the immutable fictitious principal before Application.onCreate schedules
        // jobs or constructs AccountWorkspaces, which retains that AuthController.
        // Changing controllers in onStart races the persisted JobScheduler on reconnect.
        // Reflection retains compatibility with the final baseline v1 Application.
        phaseB?.let{phase->
            app.javaClass.getMethod("configurePhaseB",String::class.java,Boolean::class.javaPrimitiveType).invoke(app,phase,secondary)}
        h05Mode?.let{mode->(app as AcademicTestApplication).configureH05(mode,h05Authorized)}
        h12Mode?.let{mode->
            // Reuse the controlled, read-only startup; H05 authorization cannot admit H12.
            (app as AcademicTestApplication).configureH05(mode,h12Authorized)
        }
        super.callApplicationOnCreate(app)
        phaseB?.let{app.javaClass.getMethod("verifyPhaseBStartup").invoke(app)}
    }
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
    private var started=false
    private var h05Support:H05RecoverySupport?=null
    fun configureH05(mode:String,authorized:Boolean) {
        check(!started&&h05Support==null){"H05_STARTUP_ONLY"}
        h05Support=H05RecoverySupport(this).also{it.configure(mode,authorized)}
    }
    val h05Mode get()=requireNotNull(h05Support).h05Mode
    val readonly get()=requireNotNull(h05Support).readonly
    override fun onCreate() {
        started=true
        // Controlled H05 lifecycle only: no normal job or Activity startup before consent.
        if(h05Support==null)super.onCreate()
    }
    private val fixtureVault=AcademicTestVault()
    private val ordinaryAuth by lazy{AuthController(AcademicTestClient(),fixtureVault)}
    private var networkAuth:AuthController?=null
    override val auth get()=h05Support?.auth ?: networkAuth ?: ordinaryAuth
    fun configurePhaseB(phase:String,secondary:Boolean){
        PhaseBNetworkHarness.phase=phase;PhaseBNetworkHarness.secondary=secondary
        networkAuth=AuthController(PhaseBNetworkHarness.Client(),AcademicTestVault().apply{value=value.copy(session=fixtureSession().copy(user=PhaseBNetworkHarness.user(),access=PhaseBNetworkHarness.token()),legacyOwner=null)}).also{runBlocking{it.restore()}}
    }
    fun verifyPhaseBStartup(){
        check(auth.state.value.user?.id==PhaseBNetworkHarness.user().id)
        // Exercise eager startup (as a restored JobService can) before runner.onStart.
        // The same controller must still own this manager when the Activity opens.
        workspaces
    }
    override fun academicRemote()=h05Support?.readonly ?: if(PhaseBNetworkHarness.phase!=null)PhaseBNetworkHarness.remote()else super.academicRemote()
    // The academic suite uses the same legacy fixture DB, independently of UI authentication tests.
    // Production DiaryApplication has no such override or test credentials.
    private val fixtureWorkspace by lazy{AccountWorkspace(this,fixtureUser,true)}
    override val workspace get()=if(h05Support!=null||PhaseBNetworkHarness.phase!=null)workspaces.current()else fixtureWorkspace
    fun restoreFixtureUser()=runBlocking {auth.logout();auth.login(fixtureUser.email,"Fixture-Password-2026")}
}
