package io.github.benomite.secondes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class JournalCalendarScreenTest {
    @get:Rule(order = 0)
    val registerHostActivity = ComposeHostActivityRule()

    @get:Rule(order = 1)
    val rule = createComposeRule()

    private val jan1 = LocalDate.of(2026, 1, 1)
    private val jan2 = LocalDate.of(2026, 1, 2)
    private val clip = Clip(jan1, "Léa", ImageBitmap(1, 1))

    private fun show(onPlay: (Clip) -> Unit = {}, onFill: (LocalDate) -> Unit = {}) =
        rule.setContent {
            JournalCalendarScreen(2026, listOf(clip), jan1, jan2, onPlay, onFill)
        }

    @Test
    fun filledAndEmptyDaysRenderDifferentMarkers() {
        show()
        rule.onNodeWithTag(filledDayTag(jan1)).assertIsDisplayed()
        rule.onNodeWithTag(thumbnailTag(jan1), useUnmergedTree = true).assertIsDisplayed()
        rule.onAllNodesWithTag(emptyDayTag(jan1)).assertCountEquals(0)
        rule.onNodeWithTag(emptyDayTag(jan2)).assertIsDisplayed()
        rule.onAllNodesWithTag(filledDayTag(jan2)).assertCountEquals(0)
        rule.onAllNodesWithTag(thumbnailTag(jan2), useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun showsTheMissingDayCount() {
        show()
        rule.onNodeWithTag(MISSING_COUNT_TAG).assertTextEquals("Missing days: 1")
    }

    @Test
    fun tappingAFilledDayPlaysItsClip() {
        var played: Clip? = null
        show(onPlay = { played = it })
        rule.onNodeWithTag(filledDayTag(jan1)).performClick()
        assertEquals(clip, played)
    }

    @Test
    fun aDayFilledByAnotherMemberShowsThatMembersName() {
        show()
        rule.onNodeWithText("1 · Léa", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun aDayNotSentYetSaysSoUntilItIsSent() {
        var clips by mutableStateOf(listOf(clip.copy(pending = true)))
        rule.setContent { JournalCalendarScreen(2026, clips, jan1, jan2, {}, {}) }
        rule.onNodeWithTag(notSentTag(jan1), useUnmergedTree = true).assertIsDisplayed().assertTextEquals("Not sent")

        clips = listOf(clip.copy(pending = false))
        rule.waitForIdle()
        rule.onAllNodesWithTag(notSentTag(jan1), useUnmergedTree = true).assertCountEquals(0)
        rule.onNodeWithTag(filledDayTag(jan1)).assertIsDisplayed()
    }

    @Test
    fun tappingAnEmptyDayFillsThatDate() {
        var filled: LocalDate? = null
        show(onFill = { filled = it })
        rule.onNodeWithTag(emptyDayTag(jan2)).performClick()
        assertEquals(jan2, filled)
    }
}
