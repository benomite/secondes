package io.github.benomite.secondes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComponentsTest {
    @get:Rule(order = 0)
    val registerHostActivity = ComposeHostActivityRule()

    @get:Rule(order = 1)
    val rule = createComposeRule()

    @Test
    fun thePrimaryButtonIs56dpHighAndTheSecondary48dp() {
        rule.setContent {
            SecondesTheme {
                Column {
                    PrimaryButton(onClick = {}, modifier = Modifier.testTag("primary")) { Text("1") }
                    SecondaryButton(onClick = {}, modifier = Modifier.testTag("secondary")) { Text("2") }
                }
            }
        }
        rule.onNodeWithTag("primary").assertHeightIsEqualTo(56.dp).assertWidthIsAtLeast(MinTouchTarget)
        rule.onNodeWithTag("secondary").assertHeightIsEqualTo(48.dp).assertWidthIsAtLeast(MinTouchTarget)
    }

    @Test
    fun theButtonsHaveTheirSpecifiedLook() {
        assertEquals(56.dp, PrimaryButtonHeight)
        assertEquals(48.dp, SecondaryButtonHeight)
        assertEquals(1.5.dp, SecondaryButtonBorder)
        assertTrue(PrimaryButtonHeight >= MinTouchTarget && SecondaryButtonHeight >= MinTouchTarget)
        // 12 dp corners, the same on a 56 dp button as on a wide one: never a pill.
        val corner = ButtonShape.topStart.toPx(androidx.compose.ui.geometry.Size(400f, 56f), androidx.compose.ui.unit.Density(1f))
        assertEquals(12f, corner)
    }

    @Test
    fun polaroidsHaveA3dpFrameALargerBottomMarginAndEachItsTilt() {
        rule.setContent {
            SecondesTheme {
                Column {
                    repeat(4) { i ->
                        Polaroid(index = i, modifier = Modifier.width(100.dp).testTag("polaroid-$i")) { Box(Modifier.fillMaxSize()) }
                    }
                }
            }
        }
        val density = rule.density
        val tilts = (0 until 4).map { i ->
            val polaroid = rule.onNodeWithTag("polaroid-$i").fetchSemanticsNode()
            val tilt = polaroid.config[PolaroidRotationKey]
            assertTrue("tilt $tilt", tilt in -2f..2f)
            tilt
        }
        tilts.zipWithNext().forEach { (a, b) -> assertNotEquals(a, b) }

        val photos = rule.onAllNodesWithTag(POLAROID_PHOTO_TAG, useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(4, photos.size)
        val polaroid = rule.onNodeWithTag("polaroid-0").fetchSemanticsNode().size
        val photo = photos.first().size
        with(density) {
            val sides = (polaroid.width - photo.width).toDp()
            val bottom = (polaroid.height - photo.height).toDp() - PolaroidFrame
            assertEquals(6f, sides.value, 0.5f)
            assertTrue("bottom margin $bottom", bottom > PolaroidFrame)
        }
    }

    @Test
    fun twoPolaroidsSideBySideNeverShareATiltAndAllStayWithinTwoDegrees() {
        for (i in -20..20) {
            assertTrue(polaroidRotation(i) in -2f..2f)
            assertNotEquals(polaroidRotation(i), polaroidRotation(i + 1))
        }
    }
}
