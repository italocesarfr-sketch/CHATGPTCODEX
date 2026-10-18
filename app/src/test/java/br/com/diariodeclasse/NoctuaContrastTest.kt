package br.com.diariodeclasse

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import br.com.diariodeclasse.ui.theme.*
import org.junit.Assert.*
import org.junit.Test

class NoctuaContrastTest {
    private fun contrast(a:Color,b:Color):Double {
        val x=a.luminance().toDouble();val y=b.luminance().toDouble()
        return (maxOf(x,y)+0.05)/(minOf(x,y)+0.05)
    }
    @Test fun bodyAndSecondaryTextMeetAAOnEveryReadingSurface() {
        for(bg in listOf(NoctuaColors.background,NoctuaColors.surface,NoctuaColors.card,NoctuaColors.elevated))
            for(fg in listOf(NoctuaColors.text,NoctuaColors.muted))assertTrue("Contraste ${contrast(fg,bg)}",contrast(fg,bg)>=4.5)
    }
    @Test fun InteractiveLabelsAndDestructiveDialogsMeetAA() {
        for((fg,bg) in listOf(NoctuaDarkScheme.onPrimary to NoctuaDarkScheme.primary,
            NoctuaDarkScheme.onPrimaryContainer to NoctuaDarkScheme.primaryContainer,
            NoctuaDarkScheme.onSecondaryContainer to NoctuaDarkScheme.secondaryContainer,
            NoctuaDarkScheme.onErrorContainer to NoctuaDarkScheme.errorContainer))assertTrue(contrast(fg,bg)>=4.5)
    }
    @Test fun SemanticGradeColorsStayReadableAndDistinctOnDarkCards() {
        for(color in listOf(NoctuaColors.success,NoctuaColors.warning,NoctuaColors.danger))assertTrue(contrast(color,NoctuaColors.card)>=4.5)
        assertTrue(NoctuaColors.danger.red>NoctuaColors.danger.green)
        assertTrue(NoctuaColors.success.green>NoctuaColors.success.red)
        assertTrue(NoctuaColors.warning.red>NoctuaColors.warning.blue)
    }
    @Test fun FieldBoundariesHaveNonTextContrast() {assertTrue(contrast(NoctuaColors.outline,NoctuaColors.card)>=3.0)}
}
