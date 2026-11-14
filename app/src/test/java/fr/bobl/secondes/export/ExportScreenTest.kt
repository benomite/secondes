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

    private fun show(
        state: ExportState = ExportState.Idle,
        notice: FilmNotice? = null,
        onSave: () -> Unit = {},
        onShare: () -> Unit = {},
        onCopyToDrive: () -> Unit = {},
    ) = rule.setContent {
        var year by remember { mutableStateOf(2026) }
        var dateOverlay by remember { mutableStateOf(false) }
        ExportScreen(
            years = exportYears(days, 2026),
            year = year,
            onYearChange = { year = it },
            clipCount = yearClips(days, year).size,
            dateOverlay = dateOverlay,
            onDateOverlayChange = { dateOverlay = it },
            state = state,
            notice = notice,
            onExport = {},
            onSave = onSave,
            onShare = onShare,
            onCopyToDrive = onCopyToDrive,
            onBack = {},
        )
    }

    @Test
    fun opensOnTheCurrentYearAndLetsAnotherBeChosen() {
        show()
        rule.onNodeWithTag(YEAR_TAG).assertTextEquals("2026")
        rule.onNodeWithText("Days with a clip: 2").assertIsDisplayed()

        rule.onNodeWithTag(OLDER_YEAR_TAG).performClick()

        rule.onNodeWithTag(YEAR_TAG).assertTextEquals("2025")
        rule.onNodeWithText("Days with a clip: 1").assertIsDisplayed()
        rule.onNodeWithTag(OLDER_YEAR_TAG).assertIsNotEnabled()
    }

    @Test
    fun theDateIsOffByDefaultAndCanBeTurnedOn() {
        show()
        rule.onNodeWithTag(DATE_OVERLAY_TAG).assertIsOff()

        rule.onNodeWithTag(DATE_OVERLAY_TAG).performClick()

        rule.onNodeWithTag(DATE_OVERLAY_TAG).assertIsOn()
    }

    @Test
    fun showsTheProgressWhileTheFilmIsBuilt() {
        show(state = ExportState.Running(42))
        rule.onNodeWithTag(PROGRESS_TAG).assertIsDisplayed()
        rule.onNodeWithText("Exporting… 42%").assertIsDisplayed()
        rule.onNodeWithText("Export").assertIsNotEnabled()
        rule.onNodeWithTag(OLDER_YEAR_TAG).assertIsNotEnabled()
    }

    @Test
    fun aFinishedFilmCanBeSavedSharedAndCopiedToDrive() {
        val done = mutableListOf<String>()
        show(
            state = ExportState.Done(File("secondes-2026.mp4"), seconds = 2, leftOut = 1),
            onSave = { done += "save" },
            onShare = { done += "share" },
            onCopyToDrive = { done += "drive" },
        )
        rule.onNodeWithText("The film is ready: 2 seconds.").assertIsDisplayed()
        rule.onNodeWithText("1 day(s) filled on another phone could not be read here and are not in the film.")
            .assertIsDisplayed()

        rule.onNodeWithText("Save to the phone").performClick()
        rule.onNodeWithText("Share").performClick()
        rule.onNodeWithText("Copy to the journal's Drive folder").performClick()

        assertEquals(listOf("save", "share", "drive"), done)
    }

    @Test
    fun aFailedExportSaysSo() {
        show(state = ExportState.Failed("decoder"))
        rule.onNodeWithText("Export failed: decoder").assertIsDisplayed()
        rule.onAllNodesWithText("Save to the phone").assertCountEquals(0)
    }

    @Test
    fun aSaveThatFailedIsShown() {
        show(state = ExportState.Done(File("f.mp4"), seconds = 2, leftOut = 0), notice = FilmNotice.SAVE_FAILED)
        rule.onNodeWithText("The film could not be saved to the phone.").assertIsDisplayed()
    }
}
