package br.com.diariodeclasse.auth

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AuthState(val loading:Boolean=true,val user:AuthUser?=null,val message:String="",val recovery:Boolean=false,
    val storageFailure:Boolean=false,val offline:Boolean=false,val initializing:Boolean=false,
    val confirmationEmail:String?=null,val resendAfter:Long=0,val verifyAfter:Long=0,val needsConfirmation:Boolean=false)

/** Owns admission only. Workspaces are closed by the app before a different principal is admitted. */
open class AuthController(private val client:AuthClient,private val vault:AuthVault,private val now:()->Long=System::currentTimeMillis) {
    private val mutex=Mutex()
    private val epoch=java.util.concurrent.atomic.AtomicLong()
    private var operationEpoch=0L
    private suspend fun serialized(requested:Long=epoch.get(),block:suspend()->Unit) {
        mutex.withLock { if(requested!=epoch.get())return@withLock;operationEpoch=requested;block() }
    }
    private fun mutate(block:()->Unit):Boolean=synchronized(epoch){if(operationEpoch!=epoch.get())false else{block();true}}
    private fun store(value:AuthSecrets){mutate{vault.write(value)}}
    private val mutable=MutableStateFlow(AuthState(initializing=true))
    val state=mutable.asStateFlow()
    private var recoverySession:AuthSession?=null
    private fun publish(value:AuthState){mutate{mutable.value=value}}
    private fun secrets()=vault.read()
    private fun requireConfirmationSignedOut() {
        if(mutable.value.user!=null || mutable.value.recovery || secrets().session!=null)
            throw AuthFailure(AuthFailure.Kind.SESSION,"Revalide e encerre a sessão anterior antes de confirmar outro cadastro.")
    }
    private fun signedOut(message:String="") {
        val pending=secrets().signup
        publish(AuthState(false,message=message,confirmationEmail=pending?.email,
            resendAfter=pending?.resendAfter ?: 0,verifyAfter=pending?.verifyAfter ?: 0))
    }
    suspend fun restore()=serialized {
        try {
            val saved=secrets().session
            if(saved==null){signedOut();return@serialized}
            revalidate(saved)
        } catch(e:CancellationException){throw e}
        catch(_:Exception){publish(AuthState(false,message="Não foi possível abrir a proteção local. Os registros foram preservados.",storageFailure=true))}
    }
    private suspend fun revalidate(saved:AuthSession) {
        try {
            saved.user.validate()
            val current=if(saved.expiresAt<=now()+60_000 || now()-saved.verifiedAt>=15*60_000)client.refresh(saved)else client.verify(saved)
            if(current.user.id!=saved.user.id)throw AuthFailure(AuthFailure.Kind.SESSION,"A identidade da sessão mudou. Entre novamente.")
            store(secrets().copy(session=current));publish(AuthState(false,current.user))
        } catch(e:AuthFailure) {
            if(e.kind in listOf(AuthFailure.Kind.NETWORK,AuthFailure.Kind.SERVER,AuthFailure.Kind.RATE_LIMIT) && AuthPolicy.offlineAllowed(saved,now())) {
                store(secrets().copy(session=saved.copy(lastSeen=maxOf(saved.lastSeen,now()))))
                publish(AuthState(false,saved.user,offline=true))
            } else if(e.kind in listOf(AuthFailure.Kind.NETWORK,AuthFailure.Kind.SERVER,AuthFailure.Kind.RATE_LIMIT)) {
                publish(AuthState(false,message="Conecte-se para revalidar a sessão. Os dados continuam neste aparelho."))
            } else {
                store(secrets().copy(session=null));publish(AuthState(false,message=e.message.orEmpty()))
            }
        }
    }
    suspend fun tick()=serialized {
        try {
            val saved=secrets().session ?: return@serialized
            if(mutable.value.user==null || mutable.value.offline || saved.expiresAt<now()+60_000 || now()-saved.verifiedAt>=15*60_000 || !AuthPolicy.offlineAllowed(saved,now()))revalidate(saved)
            else store(secrets().copy(session=saved.copy(lastSeen=maxOf(saved.lastSeen,now()))))
        } catch(e:CancellationException){throw e}
        catch(_:Exception){publish(AuthState(false,message="Não foi possível abrir a proteção local. Os registros foram preservados.",storageFailure=true))}
    }
    private suspend fun action(requested:Long=epoch.get(),block:suspend()->Unit)=serialized(requested) {
        publish(mutable.value.copy(loading=true,message=""))
        try {block()} catch(e:CancellationException){throw e}
        catch(e:Exception){publish(mutable.value.copy(loading=false,message=when(e){is AuthFailure->e.message.orEmpty();else->"Não foi possível concluir. Os dados foram preservados."},needsConfirmation=(e as? AuthFailure)?.kind==AuthFailure.Kind.UNCONFIRMED))}
    }
    suspend fun login(email:String,password:String) {
        val requested=synchronized(epoch) {
            val request=epoch.incrementAndGet() // Bind this exact credential request, even if another thread logs out before it acquires the mutex.
            try {vault.clearSession();recoverySession=null;mutable.value=AuthState(loading=true)}
            catch(_:Exception){mutable.value=AuthState(false,message="Não foi possível abrir a proteção local.",storageFailure=true);return}
            request
        }
        action(requested) {
            AuthPolicy.credentials(email.trim(),password)
            val session=client.login(email.trim(),password);session.user.validate()
            store(secrets().copy(session=session));publish(AuthState(false,session.user))
        }
    }
    suspend fun signup(name:String,email:String,password:String,confirmation:String)=action {
        requireConfirmationSignedOut()
        AuthPolicy.signup(name,email.trim(),password,confirmation)
        secrets().signup?.let{if(now()<it.resendAfter)throw AuthFailure(AuthFailure.Kind.RATE_LIMIT,"Aguarde um minuto antes de solicitar outro código.")}
        val link=PendingLink.create("signup",now());store(secrets().copy(pending=link,signup=PendingSignup(email.trim(),now(),now())))
        signedOut();publish(mutable.value.copy(loading=true))
        client.signUp(name.trim(),email.trim(),password,link)
        store(secrets().copy(signup=PendingSignup(email.trim(),now(),now())))
        signedOut("Verifique seu e-mail e digite o código aqui. Você pode abrir a mensagem em outro aparelho.")
    }
    suspend fun beginSignupConfirmation(email:String)=action {
        requireConfirmationSignedOut();AuthPolicy.email(email.trim())
        if(secrets().signup?.email!=email.trim())store(secrets().copy(signup=PendingSignup(email.trim(),now(),0)))
        signedOut("Digite o código recebido por e-mail ou solicite um novo.")
    }
    suspend fun cancelSignupConfirmation()=serialized {
        requireConfirmationSignedOut()
        store(secrets().copy(signup=null,pending=secrets().pending?.takeUnless{it.purpose=="signup"}));signedOut()
    }
    suspend fun resendSignup()=action {
        requireConfirmationSignedOut()
        val old=secrets().signup ?: throw AuthFailure(AuthFailure.Kind.INVALID,"Informe seu e-mail para solicitar a confirmação.")
        if(now()<old.resendAfter)throw AuthFailure(AuthFailure.Kind.RATE_LIMIT,"Aguarde um minuto antes de solicitar outro código.")
        val link=PendingLink.create("signup",now())
        // Reserve the cooldown before network I/O; process restart cannot flood resend.
        store(secrets().copy(pending=link,signup=PendingSignup(old.email,now(),now())))
        try{client.resendSignup(old.email,link)}finally{signedOut()}
        signedOut("Solicitação recebida. Verifique seu e-mail e use o código mais recente.")
    }
    suspend fun confirmSignup(code:String)=action {
        requireConfirmationSignedOut()
        val old=secrets().signup ?: throw AuthFailure(AuthFailure.Kind.LINK,"Solicite um código de confirmação.")
        AuthPolicy.confirmationCode(code.trim())
        if(!old.validAt(now()))throw AuthFailure(AuthFailure.Kind.LINK,"Código inválido ou expirado. Solicite um novo código.")
        if(now()<old.verifyAfter)throw AuthFailure(AuthFailure.Kind.RATE_LIMIT,"Aguarde antes de tentar outro código.")
        val attempts=if(old.attempts>=5)1 else old.attempts+1
        val next=old.copy(attempts=attempts,verifyAfter=now()+if(attempts>=5)60_000L else 2_000L)
        store(secrets().copy(signup=next));signedOut();publish(mutable.value.copy(loading=true))
        val verified=client.confirmSignup(old.email,code.trim())
        try {
            verified.user.validate()
            if(!verified.user.email.equals(old.email,true))throw AuthFailure(AuthFailure.Kind.SESSION,"A confirmação não corresponde ao e-mail informado.")
        }finally{runCatching{client.logout(verified)}} // Only the ephemeral verification session; never store it.
        store(secrets().copy(signup=null,pending=null,session=null))
        signedOut("E-mail confirmado. Entre com sua senha.")
    }
    suspend fun recover(email:String)=action {
        AuthPolicy.email(email.trim())
        val link=PendingLink.create("recovery",now());store(secrets().copy(pending=link,signup=null))
        client.recover(email.trim(),link)
        publish(AuthState(false,message="Solicitação recebida. Verifique seu e-mail."))
    }
    suspend fun callback(raw:String)=action {
        val pending=secrets().pending ?: throw AuthFailure(AuthFailure.Kind.LINK,"Solicite um novo link neste aparelho.")
        val code=pending.codeFrom(raw,now())
        val returned=client.exchange(code,pending);returned.user.validate()
        store(secrets().copy(session=null,pending=null,signup=null))
        if(pending.purpose=="recovery") {
            mutate{recoverySession=returned};publish(AuthState(false,message="Defina sua nova senha.",recovery=true))
        } else {
            runCatching{client.logout(returned)}
            publish(AuthState(false,message="E-mail confirmado. Entre com sua senha."))
        }
    }
    suspend fun newPassword(password:String,confirmation:String)=action {
        val session=recoverySession ?: throw AuthFailure(AuthFailure.Kind.LINK,"Solicite outro link de recuperação.")
        AuthPolicy.credentials(session.user.email,password);if(password!=confirmation)throw AuthFailure(AuthFailure.Kind.INVALID,"As senhas não coincidem.")
        client.password(session,password);runCatching{client.logout(session)};recoverySession=null
        store(secrets().copy(session=null));publish(AuthState(false,message="Senha atualizada. Entre novamente."))
    }
    suspend fun logout() {
        val saved=synchronized(epoch) {
            epoch.incrementAndGet() // Late HTTP responses can never resurrect this session.
            try {
                val previous=secrets().session ?: recoverySession;vault.clearSession();recoverySession=null
                mutable.value=AuthState(false);previous
            } catch(_:Exception){mutable.value=AuthState(false,message="A proteção local precisa ser verificada. Os registros foram preservados.",storageFailure=true);null}
        }
        saved?.let{withTimeoutOrNull(5000){runCatching{client.logout(it)}}}
    }
    fun canUse(userId:String):Boolean = mutable.value.user?.id==userId && runCatching{secrets().session?.let{it.user.id==userId&&AuthPolicy.offlineAllowed(it,now())}==true}.getOrDefault(false)
    /** Only the admitted account's unexpired token; the academic server must also enforce active sessions. */
    internal suspend fun syncToken(userId:String):String {
        tick()
        check(canUse(userId) && !state.value.offline){"Revalide a sessão para sincronizar."}
        return requireNotNull(secrets().session).also{check(it.user.id==userId && it.expiresAt>now())}.access
    }
    fun closeExpiredView() {
        val user=mutable.value.user ?: return
        if(!canUse(user.id))mutable.value=AuthState(loading=true,initializing=true)
    }
    fun legacyOwner():String?=secrets().legacyOwner
    suspend fun bindLegacy(user:AuthUser)=serialized {
        user.validate();require(user.id==AuthConfig.LEGACY_OWNER_ID) {"Esta conta não está autorizada a vincular os dados anteriores."}
        check(canUse(user.id)){"Revalide a sessão antes de vincular os dados anteriores."}
        val owner=secrets().legacyOwner;require(owner==null||owner==user.id)
        check(mutate{vault.bindOwner(user.id)})
        publish(mutable.value.copy(message="Dados anteriores vinculados à sua conta."))
    }
}
