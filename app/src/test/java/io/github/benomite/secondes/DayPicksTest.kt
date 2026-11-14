package io.github.benomite.secondes

import androidx.compose.ui.graphics.ImageBitmap
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

// ImageBitmap needs a real Bitmap, which a plain JVM test does not have.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DayPicksTest {
    private val day = LocalDate.of(2026, 3, 4)
    private val video = VideoInfo(10_000, 1920, 1080)
    private val uri = "content://videos/1"
    private val thumb = ImageBitmap(1, 1)

    @Test
    fun anEmptyDayOpensWithNoSecondChosen() {
        val days = DayPicks()
        days.open(day, uri, video)
        assertEquals(SecondPicking(day, uri, video, null), days.picking)
    }

    @Test
    fun confirmingFillsTheDayOnTheCalendarAndClosesThePicker() {
        val days = DayPicks()
        days.open(day, uri, video)
        days.confirm(SecondWindow(4_000), thumb)

        assertNull(days.picking)
        assertEquals(DayPick(uri, SecondWindow(4_000), thumb), days.picks[day])
        val journalDay = JournalDay(day, SharedClip(uri, SecondWindow(4_000)), "Léa", pending = true)
        val clip = journalClips(listOf(journalDay), days.picks).single()
        assertEquals(day, clip.date)
        assertEquals("Léa", clip.author)
        assertSame(thumb, clip.thumbnail)
        assertTrue(clip.pending)
    }

    @Test
    fun aDayReplacedByAnotherMembersClipDoesNotShowTheThumbnailPickedHere() {
        val days = DayPicks()
        days.open(day, uri, video)
        days.confirm(SecondWindow(4_000), thumb)

        val replaced = JournalDay(day, SharedClip("content://bob/7", SecondWindow(1_000)), "Bob")
        val clip = journalClips(listOf(replaced), days.picks).single()
        assertEquals("Bob", clip.author)
        assertNotSame(thumb, clip.thumbnail)
        assertFalse(clip.pending)
    }

    @Test
    fun aFilledDayReopensOnItsSecondAndTakesAnotherOne() {
        val days = DayPicks()
        days.open(day, uri, video)
        days.confirm(SecondWindow(4_000), thumb)

        days.open(day, uri, video)
        assertEquals(SecondWindow(4_000), days.picking?.window)

        val other = ImageBitmap(2, 2)
        days.confirm(SecondWindow(7_500), other)
        assertEquals(DayPick(uri, SecondWindow(7_500), other), days.picks[day])
        assertEquals(1, days.picks.size)
    }

    @Test
    fun cancellingLeavesTheDayAsItWas() {
        val days = DayPicks()
        days.open(day, uri, video)
        days.confirm(SecondWindow(4_000), thumb)

        days.open(day, uri, video)
        days.cancel()

        assertNull(days.picking)
        assertEquals(SecondWindow(4_000), days.picks[day]?.window)
    }

    @Test
    fun confirmingWithNoPickerOpenChangesNothing() {
        val days = DayPicks()
        days.confirm(SecondWindow(4_000), thumb)
        assertEquals(emptyMap<LocalDate, DayPick>(), days.picks)
    }
}
