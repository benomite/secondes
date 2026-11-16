package io.github.benomite.secondes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp")
class JournalCalendarScreenTest {
    @get:Rule(order = 0)
    val registerHostActivity = ComposeHostActivityRule()

    @get:Rule(order = 1)
    val rule = createComposeRule()

    private val jan1 = LocalDate.of(2026, 1, 1)
    private val jan2 = LocalDate.of(2026, 1, 2)
    private val jan3 = LocalDate.of(2026, 1, 3)
    private val clip = Clip(jan1, "Léa", ImageBitmap(1, 1))

    private fun show(onPlay: (Clip) -> Unit = {}, onFill: (LocalDate) -> Unit = {}) =
        rule.setContent {
            JournalCalendarScreen(listOf(clip), jan3, onPlay, onFill)
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
    fun tappingAFilledDayPlaysItsClip() {
        var played: Clip? = null
        show(onPlay = { played = it })
        rule.onNodeWithTag(filledDayTag(jan1)).performClick()
        assertEquals(clip, played)
    }

    @Test
    fun aFilledDayIsAPolaroidWithItsNumberInItsAuthorsColor() {
        val first = Clip(jan1, "Léa", ImageBitmap(1, 1))
        val second = Clip(jan2, "Tom", ImageBitmap(1, 1))
        rule.setContent { JournalCalendarScreen(listOf(first, second), jan3, {}, {}) }
        rule.onAllNodesWithTag(POLAROID_PHOTO_TAG, useUnmergedTree = true).assertCountEquals(2)
        rule.onNodeWithTag(dayNumberTag(jan1), useUnmergedTree = true)
            .assertTextEquals("1")
            .assert(SemanticsMatcher.expectValue(DayNumberColorKey, memberColor(1)))
        rule.onNodeWithTag(dayNumberTag(jan2), useUnmergedTree = true)
            .assertTextEquals("2")
            .assert(SemanticsMatcher.expectValue(DayNumberColorKey, memberColor(2)))
    }

    @Test
    fun theLegendShowsEachMembersFirstNameAndTheDaysToFill() {
        val clips = listOf("Léa Martin", "Tom", "Ana", "Jo", "Max", "Zoé").mapIndexed { i, name ->
            Clip(jan1.plusDays(i.toLong()), name, ImageBitmap(1, 1))
        }
        rule.setContent { JournalCalendarScreen(clips, LocalDate.of(2026, 1, 10), {}, {}) }
        listOf("Léa", "Tom", "Ana", "Jo", "Max", "Zoé").forEach { rule.onNodeWithText(it).assertIsDisplayed() }
        rule.onAllNodesWithText("Martin", substring = true, useUnmergedTree = true).assertCountEquals(0)
        rule.onNodeWithText("to fill").assertIsDisplayed()
    }

    @Test
    fun aDayNotSentYetSaysSoUntilItIsSent() {
        var clips by mutableStateOf(listOf(clip.copy(pending = true)))
        rule.setContent { JournalCalendarScreen(clips, jan3, {}, {}) }
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

    @Test
    fun todayEmptyIsMarkedAndTappingItFillsIt() {
        var filled: LocalDate? = null
        show(onFill = { filled = it })
        rule.onNodeWithTag(todayTag(jan3)).assertIsDisplayed()
        rule.onNodeWithText("today", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag(todayTag(jan3)).performClick()
        assertEquals(jan3, filled)
    }

    @Test
    fun futureDaysAreShownButCannotBeTapped() {
        show()
        val jan4 = LocalDate.of(2026, 1, 4)
        rule.onNodeWithTag(futureDayTag(jan4)).assertIsDisplayed().assertHasNoClickAction()
        rule.onAllNodesWithTag(emptyDayTag(jan4)).assertCountEquals(0)
        rule.onNodeWithTag(emptyDayTag(jan2)).assertHasClickAction()
    }

    @Test
    fun theStampCountsTheDaysToFillShownOnTheMonth() {
        val today = LocalDate.of(2026, 11, 16)
        val clips = listOf(3, 8, 9).map { Clip(LocalDate.of(2026, 11, it), "Léa", ImageBitmap(1, 1)) }
        rule.setContent { JournalCalendarScreen(clips, today, {}, {}) }
        // 16 days up to today, 3 filled: 12 dashed days and today, empty.
        rule.onNodeWithTag(TO_FILL_COUNT_TAG).assertTextEquals("13")
        val dashed = (1..15).map { LocalDate.of(2026, 11, it) }.count { day ->
            rule.onAllNodesWithTag(emptyDayTag(day)).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodesWithTag(todayTag(today)).assertCountEquals(1)
        assertEquals(12, dashed)
    }

    @Test
    fun opensOnTheCurrentMonth() {
        rule.setContent { JournalCalendarScreen(emptyList(), LocalDate.of(2026, 11, 16), {}, {}) }
        rule.onNodeWithTag(MONTH_TITLE_TAG).assertTextEquals("November")
        rule.onNodeWithText("OUR YEAR 2026").assertIsDisplayed()
        rule.onNodeWithTag(todayTag(LocalDate.of(2026, 11, 16))).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "fr-rFR-w411dp-h891dp")
    fun aFrenchPhoneShowsTheMonthAndWeekdaysInFrenchWithNoIsoDate() {
        rule.setContent { JournalCalendarScreen(emptyList(), LocalDate.of(2026, 11, 16), {}, {}) }
        rule.onNodeWithTag(MONTH_TITLE_TAG).assertTextEquals("Novembre")
        rule.onNodeWithText("NOTRE ANNÉE 2026").assertIsDisplayed()
        listOf("lu", "ma", "me", "je", "ve", "sa", "di").forEach { rule.onNodeWithText(it).assertIsDisplayed() }
        rule.onNodeWithText("À REMPLIR").assertIsDisplayed()
        rule.onNodeWithText("auj.", useUnmergedTree = true).assertIsDisplayed()
        rule.onAllNodesWithText("2026-", substring = true, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun thePreviousAndNextButtonsMoveOneMonthAndTheStampFollows() {
        val clips = listOf(Clip(LocalDate.of(2026, 10, 1), "Léa", ImageBitmap(1, 1)))
        rule.setContent { JournalCalendarScreen(clips, LocalDate.of(2026, 11, 16), {}, {}) }
        rule.onNodeWithTag(TO_FILL_COUNT_TAG).assertTextEquals("16")

        rule.onNodeWithContentDescription("Previous month").performClick()
        rule.onNodeWithTag(MONTH_TITLE_TAG).assertTextEquals("October")
        rule.onNodeWithTag(TO_FILL_COUNT_TAG).assertTextEquals("30")

        rule.onNodeWithContentDescription("Next month").performClick()
        rule.onNodeWithContentDescription("Next month").performClick()
        rule.onNodeWithTag(MONTH_TITLE_TAG).assertTextEquals("December")
        rule.onNodeWithTag(TO_FILL_COUNT_TAG).assertTextEquals("0")
        rule.onNodeWithTag(futureDayTag(LocalDate.of(2026, 12, 1))).assertHasNoClickAction()
    }

    @Test
    fun theActionsSitAtTheBottomOfTheScreen() {
        var opened = ""
        rule.setContent {
            JournalCalendarScreen(listOf(clip), jan3, {}, {}, bottom = {
                HomeActions(onImport = { opened = "import" }, onFilm = { opened = "film" }, onSettings = { opened = "settings" })
            })
        }
        val root = rule.onRoot().getUnclippedBoundsInRoot()
        val actions = rule.onNodeWithTag(HOME_ACTIONS_TAG).getUnclippedBoundsInRoot()
        val grid = rule.onNodeWithTag(emptyDayTag(jan2)).getUnclippedBoundsInRoot()
        assertEquals(root.bottom.value, actions.bottom.value, 1f)
        assertTrue(actions.top > grid.bottom)
        assertTrue(actions.top > root.bottom - 200.dp)

        rule.onNodeWithText("Import videos").performClick()
        assertEquals("import", opened)
        rule.onNodeWithText("The film of the year").performClick()
        assertEquals("film", opened)
        rule.onNodeWithContentDescription("Settings").performClick()
        assertEquals("settings", opened)
    }
}
