package io.github.benomite.secondes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The primary button's height. */
val PrimaryButtonHeight = 56.dp

/** The secondary button's height. */
val SecondaryButtonHeight = 48.dp

/** The buttons' corners: rounded, never pill-shaped. */
val ButtonShape = RoundedCornerShape(12.dp)

/** The secondary button's outline. */
val SecondaryButtonBorder = 1.5.dp

/** The smallest touch target of the app, on both sides. */
val MinTouchTarget = 44.dp

/** The primary button: ink fill, light text, 56 dp high, 12 dp corners. */
@Composable
fun PrimaryButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = PrimaryButtonHeight),
        enabled = enabled,
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Palette.Ink,
            contentColor = Palette.OnInk,
            disabledContainerColor = Palette.Ink.copy(alpha = 0.12f),
            disabledContentColor = Palette.Ink.copy(alpha = 0.38f),
        ),
        content = content,
    )
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    PrimaryButton(onClick = onClick, modifier = modifier, enabled = enabled) { Text(text) }
}

/** The secondary button: a 1.5 dp ink outline on a transparent fill, 48 dp high, 12 dp corners. */
@Composable
fun SecondaryButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = SecondaryButtonHeight),
        enabled = enabled,
        shape = ButtonShape,
        border = BorderStroke(SecondaryButtonBorder, if (enabled) Palette.Ink else Palette.Ink.copy(alpha = 0.38f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = Palette.Ink,
            disabledContentColor = Palette.Ink.copy(alpha = 0.38f),
        ),
        content = content,
    )
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    SecondaryButton(onClick = onClick, modifier = modifier, enabled = enabled) { Text(text) }
}

/** The polaroid's frame around the photo, on the top and the sides. */
val PolaroidFrame = 3.dp

/** The polaroid's bottom margin, larger than its [PolaroidFrame], where its caption goes. */
val PolaroidBottomMargin = 22.dp

private val PolaroidTilts = floatArrayOf(-1.5f, 1.2f, -0.5f, 1.8f, -2f, 0.7f, -1f, 2f)

/** The tilt of the [index]th polaroid shown, between -2 and +2 degrees; two polaroids side by side never share it. */
fun polaroidRotation(index: Int): Float = PolaroidTilts[index.mod(PolaroidTilts.size)]

/** The tilt a [Polaroid] is drawn with, for the tests. */
val PolaroidRotationKey = SemanticsPropertyKey<Float>("PolaroidRotation")

const val POLAROID_PHOTO_TAG = "polaroid-photo"

/**
 * A polaroid: [photo], square, in a 3 dp card-colored frame with a larger bottom margin holding [caption], a soft
 * shadow, and the tilt of the [index]th polaroid (see [polaroidRotation]). Its width comes from [modifier].
 */
@Composable
fun Polaroid(index: Int, modifier: Modifier = Modifier, caption: String? = null, photo: @Composable BoxScope.() -> Unit) {
    val rotation = polaroidRotation(index)
    Column(
        modifier = modifier
            .semantics { this[PolaroidRotationKey] = rotation }
            .rotate(rotation)
            .shadow(elevation = 4.dp, shape = RectangleShape, ambientColor = Palette.Ink, spotColor = Palette.Ink.copy(alpha = 0.4f))
            .background(Palette.Card)
            .padding(start = PolaroidFrame, top = PolaroidFrame, end = PolaroidFrame),
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).testTag(POLAROID_PHOTO_TAG), content = photo)
        Box(modifier = Modifier.fillMaxWidth().height(PolaroidBottomMargin), contentAlignment = Alignment.CenterStart) {
            if (caption != null) {
                Text(caption, color = Palette.ToFillText, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 4.dp))
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFE6E8E1)
@Composable
private fun ButtonsPreview() {
    SecondesTheme {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton(text = stringResource(R.string.export_save), onClick = {}, modifier = Modifier.fillMaxWidth())
            SecondaryButton(text = stringResource(R.string.export_share), onClick = {}, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFE6E8E1)
@Composable
private fun PolaroidsPreview() {
    SecondesTheme {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(4) { i ->
                Polaroid(index = i, modifier = Modifier.width(80.dp), caption = if (i == 0) stringResource(R.string.welcome_today) else null) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).background(memberColor(i + 1)))
                }
            }
        }
    }
}
