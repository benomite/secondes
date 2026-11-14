package io.github.benomite.secondes

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BehindReminderTest {
    private val start = LocalDate.of(2026, 1, 1)
    private val now = Instant.parse("2026-01-20T09:00:00Z")

    /** Jan 1..10 empty (10 days), Jan 11 filled. */
    private val tenEmpty = setOf(LocalDate.of(2026, 1, 11))
    private val jan11 = LocalDate.of(2026, 1, 11)

    private fun due(
        settings: ReminderSettings = ReminderSettings(),
        filled: Set<LocalDate> = tenEmpty,
        today: LocalDate = jan11,
        lastSentAt: Instant? = null,
    ) = behindReminderDue(settings, filled, start, today, lastSentAt, now)

    @Test
    fun defaultsToOnWithTenDays() {
        assertEquals(ReminderSettings(enabled = true, threshold = 10), ReminderSettings())
    }

    @Test
    fun announcesTheEmptyDayCountOnceTheDefaultThresholdIsReached() {
        assertEquals(10, due())
    }

    @Test
    fun staysSilentBelowTheThreshold() {
        assertNull(due(filled = tenEmpty + LocalDate.of(2026, 1, 5)))
    }

    @Test
    fun followsAChangedThreshold() {
        val nine = tenEmpty + LocalDate.of(2026, 1, 5)
        assertEquals(9, due(settings = ReminderSettings(threshold = 9), filled = nine))
        assertNull(due(settings = ReminderSettings(threshold = 11)))
    }

    @Test
    fun staysSilentWhenTurnedOff() {
        assertNull(due(settings = ReminderSettings(enabled = false)))
    }

    @Test
    fun neverTwiceWithinAWeek() {
        assertNull(due(lastSentAt = now.minusSeconds(7 * 86_400 - 1)))
        assertEquals(10, due(lastSentAt = now.minusSeconds(7 * 86_400)))
    }

    @Test
    fun countsEveryEmptyDayUpToToday() {
        assertEquals(12, due(today = LocalDate.of(2026, 1, 13)))
    }
}
