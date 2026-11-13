package io.github.benomite.secondes

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class JournalCalendarScreenTest {
    @get:Rule
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
        rule.onNodeWithTag(thumbnailTag(jan1)).assertIsDisplayed()
        rule.onAllNodesWithTag(emptyDayTag(jan1)).assertCountEquals(0)
        rule.onNodeWithTag(emptyDayTag(jan2)).assertIsDisplayed()
        rule.onAllNodesWithTag(filledDayTag(jan2)).assertCountEquals(0)
        rule.onAllNodesWithTag(thumbnailTag(jan2)).assertCountEquals(0)
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
    fun tappingAnEmptyDayFillsThatDate() {
        var filled: LocalDate? = null
        show(onFill = { filled = it })
        rule.onNodeWithTag(emptyDayTag(jan2)).performClick()
        assertEquals(jan2, filled)
    }
}
