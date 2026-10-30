package br.com.diariodeclasse.auth

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AuthState(val loading:Boolean=true,val user:AuthUser?=null,val message:String="",val recovery:Boolean=false,
    val storageFailure:Boolean=false,val offline:Boolean=false,val initializing:Boolean=false)

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
    suspend fun restore()=serialized {
        try {
            val saved=secrets().session
            if(saved==null){publish(AuthState(false));return@serialized}
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
        catch(e:Exception){publish(mutable.value.copy(loading=false,message=when(e){is AuthFailure->e.message.orEmpty();else->"Não foi possível concluir. Os dados foram preservados."}))}
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
        AuthPolicy.signup(name,email.trim(),password,confirmation)
        val link=PendingLink.create("signup",now());store(secrets().copy(pending=link))
        client.signUp(name.trim(),email.trim(),password,link)
        publish(AuthState(false,message="Confira seu e-mail para confirmar o cadastro. Depois, entre com sua senha."))
    }
    suspend fun recover(email:String)=action {
        AuthPolicy.email(email.trim())
        val link=PendingLink.create("recovery",now());store(secrets().copy(pending=link))
        client.recover(email.trim(),link)
        publish(AuthState(false,message="Solicitação recebida. Verifique seu e-mail."))
    }
    suspend fun callback(raw:String)=action {
        val pending=secrets().pending ?: throw AuthFailure(AuthFailure.Kind.LINK,"Solicite um novo link neste aparelho.")
        val code=pending.codeFrom(raw,now())
        val returned=client.exchange(code,pending);returned.user.validate()
        store(secrets().copy(session=null,pending=null))
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
    /** Academic network access always verifies a live session; offline grace only admits local data. */
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
