package io.github.benomite.secondes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SecondPickerScreenTest {
    @get:Rule(order = 0)
    val registerHostActivity = ComposeHostActivityRule()

    @get:Rule(order = 1)
    val rule = createComposeRule()

    private val horizontal = VideoInfo(10_000, 1920, 1080)

    private var confirmed: SecondWindow? = null
    private var cancelled = false
    private var playing: SecondWindow? = null

    private fun show(video: VideoInfo = horizontal, initial: SecondWindow? = null) =
        rule.setContent {
            SecondPickerScreen(
                video = video,
                initialWindow = initial,
                onConfirm = { confirmed = it },
                onCancel = { cancelled = true },
                player = { window, modifier ->
                    playing = window
                    Box(modifier.background(Color.Gray))
                },
            )
        }

    private fun slideTo(fraction: Float) =
        rule.onNodeWithTag(SECOND_SLIDER_TAG).performSemanticsAction(SemanticsActions.SetProgress) { it(fraction) }

    @Test
    fun opensOnTheFirstSecondAndPlaysIt() {
        show()
        rule.onNodeWithTag(SECOND_LABEL_TAG).assertTextEquals("0.0 s – 1.0 s")
        rule.onNodeWithTag(SECOND_SLIDER_TAG).assertIsEnabled()
        assertEquals(SecondWindow(0), playing)
    }

    @Test
    fun slidingMovesThePlayedSecondAndConfirmGivesIt() {
        show()
        slideTo(0.5f)
        rule.waitForIdle()

        rule.onNodeWithTag(SECOND_LABEL_TAG).assertTextEquals("4.5 s – 5.5 s")
        assertEquals(SecondWindow(4_500), playing)

        rule.onNodeWithTag(CONFIRM_SECOND_TAG).performClick()
        assertEquals(SecondWindow(4_500), confirmed)
    }

    @Test
    fun aFilledDayReopensOnItsSecond() {
        show(initial = SecondWindow(2_250))
        rule.onNodeWithTag(SECOND_LABEL_TAG).assertTextEquals("2.3 s – 3.3 s")
        rule.onNodeWithTag(CONFIRM_SECOND_TAG).performClick()
        assertEquals(SecondWindow(2_250), confirmed)
    }

    @Test
    fun aHorizontalVideoIsShownWholeInTheVerticalFrameWithBars() {
        show()
        val frame = rule.onNodeWithTag(VERTICAL_FRAME_TAG).assertIsDisplayed().getUnclippedBoundsInRoot()
        val picture = rule.onNodeWithTag(FRAMED_VIDEO_TAG).getUnclippedBoundsInRoot()

        val frameAspect = (frame.right - frame.left) / (frame.bottom - frame.top)
        assertEquals(VERTICAL_FRAME_ASPECT, frameAspect, 0.02f)
        assertEquals(frame.right - frame.left, picture.right - picture.left)
        val pictureAspect = (picture.right - picture.left) / (picture.bottom - picture.top)
        assertEquals(1920f / 1080f, pictureAspect, 0.05f)
        assertTrue(picture.top > frame.top && picture.bottom < frame.bottom)
    }

    @Test
    fun aVideoShorterThanASecondIsKeptWholeWithTheSliderOff() {
        show(video = VideoInfo(600, 1080, 1920))
        rule.onNodeWithTag(SECOND_SLIDER_TAG).assertIsNotEnabled()
        rule.onNodeWithTag(SHORT_VIDEO_NOTE_TAG).assertIsDisplayed()
        rule.onNodeWithTag(SECOND_LABEL_TAG).assertTextEquals("0.0 s – 0.6 s")
        rule.onNodeWithTag(CONFIRM_SECOND_TAG).performClick()
        assertEquals(SecondWindow(0, 600), confirmed)
    }

    @Test
    fun aVideoOfASecondOrMoreHasNoShortNote() {
        show()
        rule.onAllNodesWithTag(SHORT_VIDEO_NOTE_TAG).assertCountEquals(0)
    }

    @Test
    fun cancelLeavesWithoutChoosing() {
        show()
        rule.onNodeWithTag(CANCEL_SECOND_TAG).performClick()
        assertTrue(cancelled)
        assertEquals(null, confirmed)
    }
}
