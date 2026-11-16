package io.github.benomite.secondes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The second picker's polaroid, its strip of frames and the orange frame that slides on it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SecondPickerStripTest {
    @get:Rule(order = 0)
    val registerHostActivity = ComposeHostActivityRule()

    @get:Rule(order = 1)
    val rule = createComposeRule()

    private var confirmed: SecondWindow? = null
    private var otherVideo = false
    private var backed = false
    private var playing: Boolean? = null

    private fun show(video: VideoInfo = VideoInfo(10_000, 1920, 1080, hasAudio = true), author: String? = "Benoît") =
        rule.setContent {
            SecondPickerScreen(
                date = LocalDate.of(2026, 11, 3),
                author = author,
                video = video,
                initialWindow = SecondWindow(7_000),
                stripFrames = emptyList(),
                onConfirm = { confirmed = it },
                onOtherVideo = { otherVideo = true },
                onBack = { backed = true },
                player = { _, isPlaying, modifier ->
                    playing = isPlaying
                    Box(modifier.background(Color.Gray))
                },
            )
        }

    @Test
    fun theDayIsWrittenInFrenchAtTheTop() {
        assertEquals("mardi 3 novembre", secondDayTitle(LocalDate.of(2026, 11, 3), Locale.FRENCH))
    }

    @Test
    @Config(qualifiers = "fr")
    fun aFrenchPhoneSeesTheFrenchScreen() {
        show()
        rule.onNodeWithText("CHOISIR LA SECONDE").assertIsDisplayed()
        rule.onNodeWithTag(SECOND_DAY_TAG).assertTextEquals("mardi 3 novembre")
        // The day and the author are written in the polaroid's bottom margin.
        rule.onNodeWithText("mardi 3 novembre · Benoît").assertIsDisplayed()
        rule.onNodeWithText("Glisse le cadre sur le bon moment").assertIsDisplayed()
        rule.onNodeWithTag(SECOND_LABEL_TAG).assertTextEquals("0:07 → 0:08")
        rule.onNodeWithText("Garder cette seconde").assertIsDisplayed()
        rule.onNodeWithText("Choisir une autre vidéo").assertIsDisplayed()
        rule.onNodeWithText("avec le son").assertIsDisplayed()
    }

    @Test
    fun theTimesUnderTheStripAreTheStartAndTheEndOfTheVideo() {
        show()
        rule.onNodeWithTag(SECOND_STRIP_END_TAG).assertTextEquals("0:10")
    }

    @Test
    fun draggingTheFrameAlongTheStripMovesTheSecond() {
        show()
        rule.onNodeWithTag(SECOND_SLIDER_TAG).performTouchInput {
            down(Offset(width * 0.5f, centerY))
            moveTo(Offset(width * 0.8f, centerY))
            up()
        }
        rule.waitForIdle()
        rule.onNodeWithTag(SECOND_LABEL_TAG).assertTextEquals("0:08 → 0:09")

        rule.onNodeWithTag(CONFIRM_SECOND_TAG).performClick()
        assertEquals(SecondWindow(7_500), confirmed)
    }

    @Test
    fun tappingTheStripPutsTheFrameThere() {
        show()
        rule.onNodeWithTag(SECOND_SLIDER_TAG).performTouchInput { click(Offset(width * 0.25f, centerY)) }
        rule.waitForIdle()
        rule.onNodeWithTag(SECOND_LABEL_TAG).assertTextEquals("0:02 → 0:03")
    }

    @Test
    fun theSoundBadgeShowsOnlyForAClipWithSound() {
        show(video = VideoInfo(10_000, 1920, 1080, hasAudio = false))
        rule.onAllNodesWithTag(SOUND_BADGE_TAG).assertCountEquals(0)
    }

    @Test
    fun theVideoPlaysAndATapPausesIt() {
        show()
        assertEquals(true, playing)
        rule.onNodeWithTag(PLAY_TOGGLE_TAG).performClick()
        rule.waitForIdle()
        assertEquals(false, playing)
        rule.onNodeWithTag(PLAY_TOGGLE_TAG).performClick()
        rule.waitForIdle()
        assertEquals(true, playing)
    }

    @Test
    fun anotherVideoAndBackAreTwoDifferentExits() {
        show()
        rule.onNodeWithTag(CANCEL_SECOND_TAG).performClick()
        assertTrue(otherVideo)
        assertFalse(backed)
        rule.onNodeWithTag(BACK_SECOND_TAG).performClick()
        assertTrue(backed)
    }

    @Test
    fun theClockWritesMinutesAndSeconds() {
        assertEquals("0:07", clockTime(7_000))
        assertEquals("0:08", clockTime(7_500))
        assertEquals("1:05", clockTime(65_000))
    }

    @Test
    fun theStripFramesAreSpreadOverTheWholeVideo() {
        assertEquals(listOf(1_250L, 3_750L, 6_250L, 8_750L), stripFrameTimes(10_000, 4))
    }

    @Test
    fun aFingerOnTheStripCentresTheSecondUnderIt() {
        assertEquals(SecondWindow(4_500), secondWindowAtStripPosition(0.5f, 10_000))
        assertEquals(SecondWindow(0), secondWindowAtStripPosition(0f, 10_000))
        assertEquals(SecondWindow(9_000), secondWindowAtStripPosition(1f, 10_000))
    }

    @Test
    fun aVideoWithSoundIsReadAsSuch() {
        assertTrue(videoInfoOf("5000", "1920", "1080", "0", hasAudio = "yes")!!.hasAudio)
        assertFalse(videoInfoOf("5000", "1920", "1080", "0", hasAudio = null)!!.hasAudio)
    }
}
