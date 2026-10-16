package br.com.diariodeclasse
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.diariodeclasse.ui.DiaryApp
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val colors = if (isSystemInDarkTheme()) darkColorScheme(
                primary = Color(0xFF8BD5C3), onPrimary = Color(0xFF143E31), primaryContainer = Color(0xFF224F43), onPrimaryContainer = Color(0xFFC6F2E2),
                secondary = Color(0xFFAFCBBC), secondaryContainer = Color(0xFF324B40), onSecondaryContainer = Color(0xFFDAEADF),
                background = Color(0xFF101713), surface = Color(0xFF101713), onSurface = Color(0xFFDCE6DF),
                surfaceVariant = Color(0xFF404942), onSurfaceVariant = Color(0xFFBFC9C1),
                surfaceContainer = Color(0xFF18211C), surfaceContainerLow = Color(0xFF141D18), surfaceContainerHigh = Color(0xFF222C26), surfaceContainerHighest = Color(0xFF2D3831))
            else lightColorScheme(
                primary = Color(0xFF216657), primaryContainer = Color(0xFFD4ECE2), onPrimaryContainer = Color(0xFF143E31),
                secondary = Color(0xFF51665E), secondaryContainer = Color(0xFFE4EEE8), onSecondaryContainer = Color(0xFF213E32),
                background = Color(0xFFF6F8F7), surface = Color(0xFFFCFDFC), onSurface = Color(0xFF1D2923),
                surfaceVariant = Color(0xFFE5ECE7), onSurfaceVariant = Color(0xFF4C5D53),
                surfaceContainer = Color(0xFFF0F4F1), surfaceContainerLow = Color(0xFFF6F8F6), surfaceContainerHigh = Color(0xFFEAF0EB), surfaceContainerHighest = Color(0xFFE4EBE6))
            MaterialTheme(colorScheme = colors, shapes = Shapes(small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(22.dp))) { DiaryApp(viewModel()) }
        }
    }
}
