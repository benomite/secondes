package io.github.benomite.secondes

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.ResourceFont
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTest {
    private val palette = setOf(
        Palette.Paper, Palette.Card, Palette.Ink, Palette.OnInk, Palette.Secondary, Palette.Label,
        Palette.Muted, Palette.MutedText, Palette.ToFill, Palette.ToFillText,
    )

    @Test
    fun everyColorRoleComesFromThePaletteSoNoMaterialPurpleIsLeft() {
        val c = SecondesColors
        val roles = mapOf(
            "primary" to c.primary, "onPrimary" to c.onPrimary, "primaryContainer" to c.primaryContainer,
            "onPrimaryContainer" to c.onPrimaryContainer, "inversePrimary" to c.inversePrimary,
            "secondary" to c.secondary, "onSecondary" to c.onSecondary, "secondaryContainer" to c.secondaryContainer,
            "onSecondaryContainer" to c.onSecondaryContainer, "tertiary" to c.tertiary, "onTertiary" to c.onTertiary,
            "tertiaryContainer" to c.tertiaryContainer, "onTertiaryContainer" to c.onTertiaryContainer,
            "background" to c.background, "onBackground" to c.onBackground, "surface" to c.surface,
            "onSurface" to c.onSurface, "surfaceVariant" to c.surfaceVariant, "onSurfaceVariant" to c.onSurfaceVariant,
            "surfaceTint" to c.surfaceTint, "inverseSurface" to c.inverseSurface, "inverseOnSurface" to c.inverseOnSurface,
            "error" to c.error, "onError" to c.onError, "errorContainer" to c.errorContainer,
            "onErrorContainer" to c.onErrorContainer, "outline" to c.outline, "outlineVariant" to c.outlineVariant,
            "scrim" to c.scrim, "surfaceBright" to c.surfaceBright, "surfaceContainer" to c.surfaceContainer,
            "surfaceContainerHigh" to c.surfaceContainerHigh, "surfaceContainerHighest" to c.surfaceContainerHighest,
            "surfaceContainerLow" to c.surfaceContainerLow, "surfaceContainerLowest" to c.surfaceContainerLowest,
            "surfaceDim" to c.surfaceDim,
        )
        roles.forEach { (role, color) -> assertTrue("$role is $color", color in palette) }
        assertEquals(Palette.Paper, c.background)
        assertEquals(Palette.Ink, c.onBackground)
    }

    @Test
    fun titlesAndBigFiguresAreInYoungSerifAndAllOtherTextInFigtree() {
        val t = SecondesTypography
        listOf(t.displayLarge, t.displayMedium, t.displaySmall, t.headlineLarge, t.headlineMedium, t.headlineSmall, t.titleLarge, t.titleMedium)
            .forEach { assertEquals(YoungSerif, it.fontFamily) }
        listOf(t.titleSmall, t.bodyLarge, t.bodyMedium, t.bodySmall, t.labelLarge, t.labelMedium, t.labelSmall)
            .forEach { style: TextStyle -> assertEquals(Figtree, style.fontFamily) }
    }

    @Test
    fun theFontsAreBundledResourcesNotDownloaded() {
        val young = (YoungSerif as FontListFontFamily).fonts
        val figtree = (Figtree as FontListFontFamily).fonts
        assertTrue((young + figtree).all { it is ResourceFont })
        assertEquals(
            listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold),
            figtree.map { it.weight },
        )
        val fontFiles = File("src/main/res/font").list().orEmpty().toSet()
        assertTrue(fontFiles.containsAll(setOf("young_serif_regular.ttf", "figtree_regular.ttf", "figtree_medium.ttf", "figtree_semibold.ttf", "figtree_bold.ttf")))
        // No downloadable-fonts provider: no font is described by a provider query.
        File("src/main/res").walk().filter { it.extension == "xml" }.forEach {
            assertFalse(it.path, it.readText().contains("fontProviderAuthority"))
        }
    }

    @Test
    fun theAppDeclaresEnglishAndFrenchAndOpensOnThePaperTheme() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:localeConfig=\"@xml/locales_config\""))
        assertTrue(manifest.contains("android:theme=\"@style/Theme.Secondes\""))
        val locales = File("src/main/res/xml/locales_config.xml").readText()
        assertTrue(locales.contains("android:name=\"en\"") && locales.contains("android:name=\"fr\""))
    }
}
