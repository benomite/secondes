package io.github.benomite.secondes

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BehindReminderTest {
    private val now = Instant.parse("2026-01-20T09:00:00Z")

    /** Jan 1 filled, the journal's start; Jan 2..11 empty (10 days); today Jan 12, empty, is not missed. */
    private val jan1 = LocalDate.of(2026, 1, 1)
    private val tenEmpty = setOf(jan1)
    private val jan12 = LocalDate.of(2026, 1, 12)

    private fun due(
        settings: ReminderSettings = ReminderSettings(),
        filled: Set<LocalDate> = tenEmpty,
        today: LocalDate = jan12,
        lastSentAt: Instant? = null,
    ) = behindReminderDue(settings, filled, today, lastSentAt, now)

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
    fun countsEveryEmptyDayFromTheStartUpToYesterday() {
        assertEquals(12, due(today = LocalDate.of(2026, 1, 14)))
    }

    @Test
    fun todayIsNotCountedAsMissed() {
        // Jan 2..11 empty: 10 missed days whether today, Jan 12, is filled or not.
        assertEquals(10, due(filled = tenEmpty + jan12))
        assertEquals(10, due())
    }

    @Test
    fun aJournalWithNoDayFilledIsNotBehind() {
        // Started on Dec 1 with no day filled: the start is today, so November and earlier are not missed.
        val dec1 = LocalDate.of(2026, 12, 1)
        assertNull(due(settings = ReminderSettings(threshold = 1), filled = emptySet(), today = dec1))
    }

    @Test
    fun theDaysBeforeTheFirstFilledDayAreNotMissed() {
        val nov20 = LocalDate.of(2026, 11, 20)
        val dec1 = LocalDate.of(2026, 12, 1)
        // Nov 21..30 empty: 10 days, whatever came before Nov 20.
        assertEquals(10, due(filled = setOf(nov20), today = dec1))
        // A day filled earlier moves the start: Nov 11..19 and Nov 21..30 are now missed.
        assertEquals(19, due(filled = setOf(LocalDate.of(2026, 11, 10), nov20), today = dec1))
    }
}
