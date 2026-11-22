package br.com.diariodeclasse

import android.content.Context
import android.net.ConnectivityManager
import br.com.diariodeclasse.auth.*

/** Synthetic Auth only. The real Keystore vault/controller/UI persist sessions across force-stop.
 * No GoTrue endpoint, hosted user or network login is used by this fixture. */
class CommercialFinalAuthClient(private val context:Context):AuthClient by AcademicTestClient() {
    companion object {
        const val OTHER_ID="99999999-9999-4999-8999-999999999999"
        const val OTHER_EMAIL="commercial-other@example.invalid"
        const val PASSWORD="Fixture-Password-2026"
    }
    private fun fresh(other:Boolean)=fixtureSession().copy(
        user=if(other)fixtureUser.copy(id=OTHER_ID,email=OTHER_EMAIL)else PhaseBNetworkHarness.user(),
        access=PhaseBNetworkHarness.token(other),refresh="synthetic-commercial-refresh")
    override suspend fun login(email:String,password:String):AuthSession {
        if((context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).activeNetwork==null)
            throw AuthFailure(AuthFailure.Kind.NETWORK,"Login fictício requer conexão, como o fluxo real.")
        if(email !in setOf(fixtureUser.email,OTHER_EMAIL)||password!=PASSWORD)
            throw AuthFailure(AuthFailure.Kind.INVALID,"Credenciais fictícias inválidas.")
        return fresh(email==OTHER_EMAIL)
    }
    override suspend fun verify(session:AuthSession):AuthSession {
        if((context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).activeNetwork==null)
            throw AuthFailure(AuthFailure.Kind.NETWORK,"Sem conexão na fixture local.")
        return fresh(session.user.id==OTHER_ID)
    }
    override suspend fun refresh(session:AuthSession)=verify(session)
}
