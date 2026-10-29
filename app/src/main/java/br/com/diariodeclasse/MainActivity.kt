package br.com.diariodeclasse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.diariodeclasse.ui.DiaryApp
import br.com.diariodeclasse.ui.DiaryViewModel
import br.com.diariodeclasse.auth.AuthRoot
import android.content.Intent
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import br.com.diariodeclasse.ui.theme.NoctuaTheme

class MainActivity : ComponentActivity() {
    internal val academicOwner:androidx.lifecycle.ViewModelStoreOwner get() {
        val app=application as DiaryApplication
        val user=requireNotNull(app.auth.state.value.user)
        check(app.auth.canUse(user.id));app.workspaces.current()
        return androidx.lifecycle.ViewModelProvider(this)[br.com.diariodeclasse.auth.AccountViewModels::class.java].owner(user.id)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle=SystemBarStyle.dark(android.graphics.Color.rgb(11,18,32)))
        val app=application as DiaryApplication
        setContent { NoctuaTheme { AuthRoot(app) } }
        receiveCallback(intent)
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { while(true){app.auth.tick();delay(60_000)} } }
    }
    override fun onStart(){super.onStart();(application as DiaryApplication).auth.closeExpiredView()}
    override fun onNewIntent(intent:Intent){super.onNewIntent(intent);receiveCallback(intent)}
    private fun receiveCallback(intent:Intent?) {
        val raw=intent?.dataString ?: return
        intent.data=null // Do not retain auth codes in the Activity saved intent.
        (application as DiaryApplication).authScope.launch{(application as DiaryApplication).auth.callback(raw)}
    }
}
