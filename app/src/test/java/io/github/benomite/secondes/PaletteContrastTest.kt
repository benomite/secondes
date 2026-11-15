package io.github.benomite.secondes

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** WCAG AA: every text color of the palette reaches 4.5:1 on each background it is used on. */
class PaletteContrastTest {
    private fun channel(c: Float): Double = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    private fun luminance(c: Color) = 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    // The text colors on paper and on cards (frames, text fields), and the light text on the ink buttons.
    private val textPairs = listOf(
        Triple("ink on paper", Palette.Ink, Palette.Paper),
        Triple("ink on card", Palette.Ink, Palette.Card),
        Triple("secondary on paper", Palette.Secondary, Palette.Paper),
        Triple("secondary on card", Palette.Secondary, Palette.Card),
        Triple("label on paper", Palette.Label, Palette.Paper),
        Triple("label on card", Palette.Label, Palette.Card),
        Triple("muted text on paper", Palette.MutedText, Palette.Paper),
        Triple("muted text on card", Palette.MutedText, Palette.Card),
        Triple("to-fill text on paper", Palette.ToFillText, Palette.Paper),
        Triple("to-fill text on card", Palette.ToFillText, Palette.Card),
        Triple("light text on ink", Palette.OnInk, Palette.Ink),
        // Material draws text with these roles: the error messages and the field labels.
        Triple("error on paper", SecondesColors.error, SecondesColors.background),
        Triple("field label on paper", SecondesColors.onSurfaceVariant, SecondesColors.surface),
        Triple("on primary on primary", SecondesColors.onPrimary, SecondesColors.primary),
        Triple("on surface on surface", SecondesColors.onSurface, SecondesColors.surface),
    )

    @Test
    fun everyTextColorReachesFourAndAHalfToOne() {
        textPairs.forEach { (name, text, background) ->
            val ratio = contrast(text, background)
            assertTrue("$name: $ratio", ratio >= 4.5)
        }
    }

    @Test
    fun theFormulaMatchesWcag() {
        assertEquals(21.0, contrast(Color.Black, Color.White), 0.01)
        assertEquals(1.0, contrast(Palette.Paper, Palette.Paper), 0.001)
    }

    @Test
    fun theMutedAndOrangeOfTheSpecFailOnPaperSoTextUsesTheirDarkerShades() {
        // Kept for lines, marks and icons only.
        assertTrue(contrast(Palette.Muted, Palette.Paper) < 4.5)
        assertTrue(contrast(Palette.ToFill, Palette.Paper) < 4.5)
    }
}
