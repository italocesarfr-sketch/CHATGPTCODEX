package br.com.diariodeclasse
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.diariodeclasse.ui.DiaryApp
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val colors = if (isSystemInDarkTheme()) darkColorScheme(primary = Color(0xFF8BD5C3), secondary = Color(0xFFC9C49A))
                else lightColorScheme(primary = Color(0xFF216657), secondary = Color(0xFF676341), background = Color(0xFFF7F9F5))
            MaterialTheme(colorScheme = colors) { DiaryApp(viewModel()) }
        }
    }
}
