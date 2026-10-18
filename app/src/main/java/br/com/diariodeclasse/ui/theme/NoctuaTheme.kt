package br.com.diariodeclasse.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Tokens nativos. A identidade noturna não depende das cores dinâmicas do aparelho. */
object NoctuaColors {
    val background = Color(0xFF0B1220)
    val surface = Color(0xFF101B2C)
    val card = Color(0xFF172438)
    val elevated = Color(0xFF203149)
    val text = Color(0xFFE4EAF5)
    val muted = Color(0xFFB1BFD3)
    val accent = Color(0xFF9DCADF)
    val accentInk = Color(0xFF092C3A)
    val accentContainer = Color(0xFF203F53)
    val outline = Color(0xFF8292AA)
    val success = Color(0xFF91D6AE)
    val warning = Color(0xFFE8CC84)
    val danger = Color(0xFFFFB4AB)
}
object NoctuaSpace {
    val tiny = 4.dp; val small = 8.dp; val medium = 12.dp
    val page = 16.dp; val section = 24.dp; val touch = 48.dp
}
val NoctuaDarkScheme = darkColorScheme(
    primary=NoctuaColors.accent,onPrimary=NoctuaColors.accentInk,
    primaryContainer=NoctuaColors.accentContainer,onPrimaryContainer=Color(0xFFDAF0FF),
    secondary=Color(0xFFB5C9E5),onSecondary=Color(0xFF1D314C),
    secondaryContainer=Color(0xFF263952),onSecondaryContainer=Color(0xFFDEEAFF),
    tertiary=NoctuaColors.success,onTertiary=Color(0xFF0C3526),
    background=NoctuaColors.background,onBackground=NoctuaColors.text,
    surface=NoctuaColors.surface,onSurface=NoctuaColors.text,
    surfaceVariant=NoctuaColors.elevated,onSurfaceVariant=NoctuaColors.muted,
    surfaceContainerLowest=Color(0xFF080F1B),surfaceContainerLow=Color(0xFF121F31),
    surfaceContainer=NoctuaColors.card,surfaceContainerHigh=NoctuaColors.elevated,
    surfaceContainerHighest=Color(0xFF293A52),outline=NoctuaColors.outline,outlineVariant=Color(0xFF394A61),
    error=NoctuaColors.danger,onError=Color(0xFF690005),
    errorContainer=Color(0xFF57232C),onErrorContainer=Color(0xFFFFDAD6))
private val NoctuaTypography = Typography(
    headlineSmall=androidx.compose.ui.text.TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.SemiBold,fontSize=22.sp,lineHeight=28.sp),
    headlineMedium=androidx.compose.ui.text.TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.SemiBold,fontSize=26.sp,lineHeight=32.sp),
    titleLarge=androidx.compose.ui.text.TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.SemiBold,fontSize=20.sp,lineHeight=26.sp),
    titleMedium=androidx.compose.ui.text.TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Medium,fontSize=16.sp,lineHeight=22.sp),
    bodyLarge=androidx.compose.ui.text.TextStyle(fontFamily=FontFamily.SansSerif,fontSize=16.sp,lineHeight=24.sp),
    bodyMedium=androidx.compose.ui.text.TextStyle(fontFamily=FontFamily.SansSerif,fontSize=14.sp,lineHeight=21.sp),
    bodySmall=androidx.compose.ui.text.TextStyle(fontFamily=FontFamily.SansSerif,fontSize=13.sp,lineHeight=19.sp),
    labelLarge=androidx.compose.ui.text.TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Medium,fontSize=14.sp,lineHeight=20.sp),
    labelMedium=androidx.compose.ui.text.TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Medium,fontSize=12.sp,lineHeight=16.sp),
    labelSmall=androidx.compose.ui.text.TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Medium,fontSize=11.sp,lineHeight=14.sp))
@Composable fun NoctuaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme=NoctuaDarkScheme,typography=NoctuaTypography,
        shapes=Shapes(small=RoundedCornerShape(10.dp),medium=RoundedCornerShape(16.dp),large=RoundedCornerShape(20.dp)),content=content)
}
/** A grafia e a leitura acessível continuam Noctua. NOTA é apenas uma ênfase tipográfica. */
@Composable fun NoctuaWordmark() {
    Text(buildAnnotatedString {
        "Noctua".forEachIndexed { i,c -> withStyle(SpanStyle(
            color=if(i in listOf(0,1,3,5)) NoctuaColors.text else NoctuaColors.muted,
            fontWeight=if(i in listOf(0,1,3,5)) FontWeight.SemiBold else FontWeight.Normal)){append(c)} }
    },style=MaterialTheme.typography.titleLarge,modifier=Modifier.semantics{contentDescription="Noctua"})
}
