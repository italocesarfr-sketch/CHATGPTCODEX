package br.com.diariodeclasse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.diariodeclasse.ui.DiaryApp
import br.com.diariodeclasse.ui.NoctuaStart
import br.com.diariodeclasse.ui.theme.NoctuaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle=SystemBarStyle.dark(android.graphics.Color.rgb(11,18,32)))
        setContent { NoctuaTheme { NoctuaStart { DiaryApp(viewModel()) } } }
    }
}
