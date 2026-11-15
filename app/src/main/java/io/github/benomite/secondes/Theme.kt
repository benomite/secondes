package io.github.benomite.secondes

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/** The app's paper and ink colors. */
object Palette {
    /** The background of every screen. */
    val Paper = Color(0xFFE6E8E1)

    /** Cards, polaroid frames and text fields. */
    val Card = Color(0xFFFCFCF8)

    /** The text, and the primary button's fill. */
    val Ink = Color(0xFF22261F)

    /** Text on ink. */
    val OnInk = Color(0xFFF3F4EE)

    /** Secondary text. */
    val Secondary = Color(0xFF4A5044)

    /** Small caps labels. */
    val Label = Color(0xFF5C6356)

    /** Muted lines and marks. Too light for text on paper (3.6:1): text uses [MutedText]. */
    val Muted = Color(0xFF737A6C)

    /** [Muted], darkened so that muted text reaches 4.5:1 on [Paper] and [Card]. */
    val MutedText = Color(0xFF636A5D)

    /** What is still to fill: strokes and icons. Too light for text (3.5:1 on paper): text uses [ToFillText]. */
    val ToFill = Color(0xFFC9572A)

    /** [ToFill], darkened so that its text, and the error messages, reach 4.5:1 on [Paper] and [Card]. */
    val ToFillText = Color(0xFFA8471F)
}

/** Titles, day numbers and big figures. Bundled in the app's resources: nothing is downloaded. */
val YoungSerif = FontFamily(Font(R.font.young_serif_regular, FontWeight.Normal))

/** All other text, in its four weights. Bundled in the app's resources: nothing is downloaded. */
val Figtree = FontFamily(
    Font(R.font.figtree_regular, FontWeight.Normal),
    Font(R.font.figtree_medium, FontWeight.Medium),
    Font(R.font.figtree_semibold, FontWeight.SemiBold),
    Font(R.font.figtree_bold, FontWeight.Bold),
)

/** Material's color roles, all taken from the [Palette]: no default Material purple is left. */
val SecondesColors: ColorScheme = lightColorScheme(
    primary = Palette.Ink,
    onPrimary = Palette.OnInk,
    primaryContainer = Palette.Card,
    onPrimaryContainer = Palette.Ink,
    inversePrimary = Palette.OnInk,
    secondary = Palette.Secondary,
    onSecondary = Palette.OnInk,
    secondaryContainer = Palette.Card,
    onSecondaryContainer = Palette.Ink,
    tertiary = Palette.Secondary,
    onTertiary = Palette.OnInk,
    tertiaryContainer = Palette.Card,
    onTertiaryContainer = Palette.Ink,
    background = Palette.Paper,
    onBackground = Palette.Ink,
    surface = Palette.Paper,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.Card,
    onSurfaceVariant = Palette.Secondary,
    surfaceTint = Palette.Ink,
    inverseSurface = Palette.Ink,
    inverseOnSurface = Palette.OnInk,
    error = Palette.ToFillText,
    onError = Palette.OnInk,
    errorContainer = Palette.Card,
    onErrorContainer = Palette.ToFillText,
    outline = Palette.Ink,
    outlineVariant = Palette.Muted,
    scrim = Palette.Ink,
    surfaceBright = Palette.Card,
    surfaceContainer = Palette.Card,
    surfaceContainerHigh = Palette.Card,
    surfaceContainerHighest = Palette.Card,
    surfaceContainerLow = Palette.Card,
    surfaceContainerLowest = Palette.Card,
    surfaceDim = Palette.Paper,
)

/** Young Serif for the titles (display, headline, large and medium titles), Figtree for everything else. */
val SecondesTypography: Typography = Typography().let { base ->
    fun serif(style: TextStyle) = style.copy(fontFamily = YoungSerif, fontWeight = FontWeight.Normal)
    fun sans(style: TextStyle) = style.copy(fontFamily = Figtree)
    Typography(
        displayLarge = serif(base.displayLarge),
        displayMedium = serif(base.displayMedium),
        displaySmall = serif(base.displaySmall),
        headlineLarge = serif(base.headlineLarge),
        headlineMedium = serif(base.headlineMedium),
        headlineSmall = serif(base.headlineSmall),
        titleLarge = serif(base.titleLarge),
        titleMedium = serif(base.titleMedium),
        titleSmall = sans(base.titleSmall).copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = sans(base.bodyLarge),
        bodyMedium = sans(base.bodyMedium),
        bodySmall = sans(base.bodySmall),
        labelLarge = sans(base.labelLarge).copy(fontWeight = FontWeight.SemiBold),
        labelMedium = sans(base.labelMedium),
        labelSmall = sans(base.labelSmall),
    )
}

/** The app's identity, applied at the root so every screen inherits it. */
@Composable
fun SecondesTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SecondesColors, typography = SecondesTypography, content = content)
}
