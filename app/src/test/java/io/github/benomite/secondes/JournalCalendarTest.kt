package io.github.benomite.secondes

import androidx.compose.ui.graphics.ImageBitmap
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class JournalCalendarTest {
    private val thumb = ImageBitmap(1, 1)
    private fun clip(d: LocalDate) = Clip(d, "Léa", thumb)
    private val jan1 = LocalDate.of(2026, 1, 1)

    @Test
    fun hasTwelveMonthsWithTheirDays() {
        val months = calendarMonths(2026, emptyList())
        assertEquals(12, months.size)
        assertEquals(28, months[1].days.size)
        assertEquals(365, months.sumOf { it.days.size })
    }

    @Test
    fun filledDaysCarryTheirClipAndEmptyDaysDoNot() {
        val months = calendarMonths(2026, listOf(clip(jan1)))
        assertNotNull(months[0].days[0].clip)
        assertEquals("Léa", months[0].days[0].clip?.author)
        assertNull(months[0].days[1].clip)
    }

    @Test
    fun countsMissingDaysUpToToday() {
        assertEquals(2, missingDayCount(listOf(clip(jan1)), jan1, LocalDate.of(2026, 1, 3)))
    }

    @Test
    fun noMissingDaysWhenEveryDayIsFilled() {
        val clips = (1..3).map { clip(LocalDate.of(2026, 1, it)) }
        assertEquals(0, missingDayCount(clips, jan1, LocalDate.of(2026, 1, 3)))
    }

    @Test
    fun journalStartingTodayMissesOnlyTodayUntilFilled() {
        assertEquals(1, missingDayCount(emptyList(), jan1, jan1))
        assertEquals(0, missingDayCount(listOf(clip(jan1)), jan1, jan1))
    }

    @Test
    fun daysOutsideTheJournalAreNotMissing() {
        val clips = listOf(clip(LocalDate.of(2025, 12, 31)), clip(LocalDate.of(2026, 2, 1)))
        assertEquals(1, missingDayCount(clips, jan1, jan1))
    }
}
