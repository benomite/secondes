package fr.bobl.secondes.export

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.benomite.secondes.ComposeHostActivityRule
import io.github.benomite.secondes.JournalDay
import io.github.benomite.secondes.SecondWindow
import io.github.benomite.secondes.SharedClip
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// A phone-sized screen, so the finished film's buttons are all on it.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class ExportScreenTest {
    @get:Rule(order = 0)
    val registerHostActivity = ComposeHostActivityRule()

    @get:Rule(order = 1)
    val rule = createComposeRule()

    private fun day(date: LocalDate) = JournalDay(date, SharedClip("content://$date", SecondWindow(0)), "Léa")

    private val days = listOf(day(LocalDate.of(2025, 5, 1)), day(LocalDate.of(2026, 1, 1)), day(LocalDate.of(2026, 2, 1)))

    private val today = LocalDate.of(2026, 3, 1)

    private fun show(
        state: ExportState = ExportState.Idle,
        notice: FilmNotice? = null,
        onSave: () -> Unit = {},
        onShare: () -> Unit = {},
        onCopyToDrive: () -> Unit = {},
        onBack: () -> Unit = {},
    ) = rule.setContent {
        var year by remember { mutableStateOf(2026) }
        var dateOverlay by remember { mutableStateOf(false) }
        ExportScreen(
            years = exportYears(days, 2026),
            year = year,
            onYearChange = { year = it },
            stats = yearStats(days, year, today),
            dateOverlay = dateOverlay,
            onDateOverlayChange = { dateOverlay = it },
            state = state,
            notice = notice,
            onExport = {},
            onSave = onSave,
            onShare = onShare,
            onCopyToDrive = onCopyToDrive,
            onBack = onBack,
        )
    }

    @Test
    fun opensOnTheCurrentYearAndLetsAnotherBeChosen() {
        show()
        rule.onNodeWithText("The film of the year").assertIsDisplayed()
        rule.onNodeWithTag(yearTag(2026)).assertIsDisplayed()
        rule.onNodeWithTag(SECONDS_TAG).assertTextEquals("2")

        rule.onNodeWithTag(yearTag(2025)).performClick()

        rule.onNodeWithTag(SECONDS_TAG).assertTextEquals("1")
        rule.onNodeWithText("Léa · 1 s").assertIsDisplayed()
    }

    @Test
    fun beforeTheExportItTellsTheSecondsTheLengthEachSharesAndTheEmptyDays() {
        show()
        rule.onNodeWithText("seconds, that is 2 s").assertIsDisplayed()
        rule.onNodeWithText("Léa · 2 s").assertIsDisplayed()
        // 1 January to 1 March 2026 is 60 days, two of them filled.
        rule.onNodeWithText("58 empty days, skipped with no black screen").assertIsDisplayed()
    }

    @Test
    fun backLeavesTheScreen() {
        var left = false
        show(onBack = { left = true })
        rule.onNodeWithTag(BACK_TAG).performClick()
        assertTrue(left)
    }

    @Test
    fun theDateIsOffByDefaultAndCanBeTurnedOn() {
        show()
        rule.onNodeWithTag(DATE_OVERLAY_TAG).assertIsOff()

        rule.onNodeWithTag(DATE_OVERLAY_TAG).performClick()

        rule.onNodeWithTag(DATE_OVERLAY_TAG).assertIsOn()
    }

    @Test
    fun showsTheDayReachedOutOfTheTotalWhileTheFilmIsBuilt() {
        show(state = ExportState.Running(50))
        rule.onNodeWithTag(PROGRESS_TAG).assertIsDisplayed()
        rule.onNodeWithText("Building the film…").assertIsDisplayed()
        rule.onNodeWithText("day 1 / 2").assertIsDisplayed()
        rule.onNodeWithText("Build the film").assertIsNotEnabled()
        rule.onNodeWithTag(yearTag(2025)).assertIsNotEnabled()
    }

    @Test
    fun aFinishedFilmCanBeSavedSharedAndSentToDrive() {
        val done = mutableListOf<String>()
        show(
            state = ExportState.Done(File("secondes-2026.mp4"), seconds = 2, leftOut = 1),
            onSave = { done += "save" },
            onShare = { done += "share" },
            onCopyToDrive = { done += "drive" },
        )
        rule.onNodeWithText("1 day(s) filled on another phone could not be read here and are not in the film.")
            .assertIsDisplayed()

        rule.onNodeWithText("Save to the gallery").performClick()
        rule.onNodeWithText("Share").performClick()
        rule.onNodeWithText("Send to Drive").performClick()

        assertEquals(listOf("save", "share", "drive"), done)
    }

    @Test
    fun aFailedExportSaysSo() {
        show(state = ExportState.Failed("decoder"))
        rule.onNodeWithText("Export failed: decoder").assertIsDisplayed()
        rule.onAllNodesWithText("Save to the gallery").assertCountEquals(0)
    }

    @Test
    fun aSaveThatFailedIsShown() {
        show(state = ExportState.Done(File("f.mp4"), seconds = 2, leftOut = 0), notice = FilmNotice.SAVE_FAILED)
        rule.onNodeWithText("The film could not be saved to the phone.").assertIsDisplayed()
    }
}
