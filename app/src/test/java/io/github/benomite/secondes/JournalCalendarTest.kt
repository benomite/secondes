package io.github.benomite.secondes

import androidx.compose.ui.graphics.ImageBitmap
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

// ImageBitmap needs a real Bitmap, which a plain JVM test does not have.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class JournalCalendarTest {
    private val thumb = ImageBitmap(1, 1)
    private fun clip(d: LocalDate) = Clip(d, "Léa", thumb)
    private val jan1 = LocalDate.of(2026, 1, 1)
    private val january = YearMonth.of(2026, 1)
    private val nov16 = LocalDate.of(2026, 11, 16)

    @Test
    fun aMonthHasItsDays() {
        assertEquals(28, calendarMonth(YearMonth.of(2026, 2), emptyList(), nov16).days.size)
        assertEquals(30, calendarMonth(YearMonth.of(2026, 11), emptyList(), nov16).days.size)
        assertEquals(LocalDate.of(2026, 11, 30), calendarMonth(YearMonth.of(2026, 11), emptyList(), nov16).days.last().date)
    }

    @Test
    fun filledDaysCarryTheirClipAndEmptyDaysDoNot() {
        val month = calendarMonth(january, listOf(clip(jan1)), nov16)
        assertNotNull(month.days[0].clip)
        assertEquals("Léa", month.days[0].clip?.author)
        assertNull(month.days[1].clip)
    }

    @Test
    fun eachDayHasItsStateSeenFromToday() {
        val clips = listOf(clip(LocalDate.of(2026, 10, 1)), clip(LocalDate.of(2026, 11, 3)), clip(LocalDate.of(2026, 11, 16)))
        val month = calendarMonth(YearMonth.of(2026, 11), clips, LocalDate.of(2026, 11, 16))
        assertEquals(DayState.TO_FILL, month.days[1].state)
        assertEquals(DayState.FILLED, month.days[2].state)
        assertEquals(DayState.FILLED, month.days[15].state)
        assertEquals(DayState.FUTURE, month.days[16].state)
        assertEquals(DayState.TODAY, calendarMonth(YearMonth.of(2026, 11), emptyList(), nov16).days[15].state)
    }

    @Test
    fun countsTheDaysToFillUpToToday() {
        assertEquals(2, daysToFill(january, listOf(clip(jan1)), LocalDate.of(2026, 1, 3)))
    }

    @Test
    fun noDayToFillWhenEveryDayIsFilled() {
        val clips = (1..3).map { clip(LocalDate.of(2026, 1, it)) }
        assertEquals(0, daysToFill(january, clips, LocalDate.of(2026, 1, 3)))
    }

    @Test
    fun todayCountsUntilItIsFilled() {
        assertEquals(1, daysToFill(january, emptyList(), jan1))
        assertEquals(0, daysToFill(january, listOf(clip(jan1)), jan1))
    }

    @Test
    fun daysOfOtherMonthsAreNotCounted() {
        val clips = listOf(clip(LocalDate.of(2025, 12, 31)), clip(LocalDate.of(2026, 2, 1)))
        assertEquals(1, daysToFill(january, clips, jan1))
    }

    @Test
    fun aPastMonthCountsAllItsEmptyDaysAndAMonthToComeNone() {
        assertEquals(30, daysToFill(january, listOf(clip(jan1)), nov16))
        assertEquals(0, daysToFill(YearMonth.of(2026, 12), emptyList(), nov16))
    }

    private val november = YearMonth.of(2026, 11)
    private val dec1 = LocalDate.of(2026, 12, 1)

    @Test
    fun theJournalStartsOnItsFirstFilledDayOrTodayWhileNoneIsFilled() {
        assertEquals(dec1, journalStart(emptyList(), dec1))
        assertEquals(LocalDate.of(2026, 11, 20), journalStart(listOf(LocalDate.of(2026, 11, 24), LocalDate.of(2026, 11, 20)), dec1))
    }

    @Test
    fun aJournalStartedWithNoDayFilledLeavesTheDaysBeforeTodayNeutral() {
        val month = calendarMonth(november, emptyList(), dec1)
        assertTrue(month.days.all { it.state == DayState.BEFORE_START })
        assertEquals(0, daysToFill(november, emptyList(), dec1))
        // Today, empty, is still to fill.
        assertEquals(DayState.TODAY, calendarMonth(YearMonth.of(2026, 12), emptyList(), dec1).days[0].state)
        assertEquals(1, daysToFill(YearMonth.of(2026, 12), emptyList(), dec1))
    }

    @Test
    fun aDayFilledBeforeTheStartBecomesTheNewStart() {
        val nov20 = LocalDate.of(2026, 11, 20)
        val month = calendarMonth(november, listOf(clip(nov20)), dec1)
        assertTrue(month.days.take(19).all { it.state == DayState.BEFORE_START })
        assertEquals(DayState.FILLED, month.days[19].state)
        assertTrue(month.days.drop(20).all { it.state == DayState.TO_FILL })
        // Nov 21..30.
        assertEquals(10, daysToFill(november, listOf(clip(nov20)), dec1))
    }

    @Test
    fun theStartIsTheFirstDayFilledByAnyMember() {
        val clips = listOf(clip(LocalDate.of(2026, 11, 24)), Clip(LocalDate.of(2026, 11, 20), "Tom", thumb))
        val month = calendarMonth(november, clips, dec1)
        assertEquals(DayState.BEFORE_START, month.days[18].state)
        assertEquals(DayState.TO_FILL, month.days[20].state)
        assertEquals(9, daysToFill(november, clips, dec1))
    }
}
