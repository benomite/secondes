package io.github.benomite.secondes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImportPreviewPanelTest {
    @get:Rule(order = 0)
    val registerHostActivity = ComposeHostActivityRule()

    @get:Rule(order = 1)
    val rule = createComposeRule()

    private val mar4 = LocalDate.of(2026, 3, 4)
    private val mar5 = LocalDate.of(2026, 3, 5)
    private val thumb = ImageBitmap(1, 1)
    private val video = VideoInfo(10_000, 1080, 1920)

    private fun read(position: Int, date: LocalDate?, mime: String = "video/mp4") =
        ImportRead(position, "content://v/$position", mime, video, date, thumb)

    @Test
    fun aSharedDayAndAFilledDayAreChosenBeforeConfirming() {
        var plan by mutableStateOf(planImport(listOf(read(1, mar4), read(2, mar4), read(3, mar5)), setOf(mar5)))
        var confirmed = false
        rule.setContent {
            ImportPreviewPanel(plan, mapOf(mar5 to thumb), { d, c -> plan = plan.choose(d, c) }, { confirmed = true }, {})
        }

        rule.onNodeWithText("2026-03-04: 2 selected videos share this day", substring = true).assertExists()
        rule.onNodeWithText("2026-03-05 already has a clip", substring = true).assertExists()
        rule.onNodeWithTag(CONFIRM_IMPORT_TAG).assertIsNotEnabled()

        rule.onNodeWithTag(importOptionTag(mar4, 2)).performClick()
        rule.onNodeWithTag(importOptionTag(mar4, 2)).assertIsSelected()
        rule.onNodeWithTag(CONFIRM_IMPORT_TAG).assertIsNotEnabled()

        rule.onNodeWithTag(keepExistingTag(mar5)).performClick()
        rule.onNodeWithTag(CONFIRM_IMPORT_TAG).assertIsEnabled().performClick()

        assertTrue(confirmed)
        assertEquals(mapOf(mar4 to "content://v/2"), plan.toSave().mapValues { it.value.uri })
    }

    @Test
    fun aRefusedPhotoIsSaidClearly() {
        rule.setContent {
            ImportPreviewPanel(planImport(listOf(read(1, mar4, mime = "image/jpeg")), emptySet()), emptyMap(), { _, _ -> }, {}, {})
        }
        rule.onNodeWithText("Item 1 is not a video", substring = true).assertExists()
    }

    @Test
    fun cancelIsOffered() {
        var cancelled = false
        rule.setContent {
            ImportPreviewPanel(planImport(listOf(read(1, mar4)), emptySet()), emptyMap(), { _, _ -> }, {}, { cancelled = true })
        }
        rule.onNodeWithTag(CANCEL_IMPORT_TAG).performClick()
        assertTrue(cancelled)
    }
}
