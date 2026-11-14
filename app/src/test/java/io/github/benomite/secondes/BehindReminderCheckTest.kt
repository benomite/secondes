package io.github.benomite.secondes

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BehindReminderCheckTest {
    private val start = LocalDate.of(2026, 1, 1)
    private val today = LocalDate.of(2026, 1, 11)
    private val now = Instant.parse("2026-01-20T09:00:00Z")

    private class MemoryStore(
        var settings: ReminderSettings? = ReminderSettings(),
        var lastSent: Instant? = null,
    ) : ReminderStore {
        override fun settings() = settings
        override fun lastSentAt() = lastSent
        override fun recordSent(at: Instant) {
            lastSent = at
        }
    }

    /** Jan 1..10 empty, Jan 11 filled: 10 empty days on Jan 11. */
    private val journal = object : FilledDaysSource {
        override fun filledDays() = setOf(today)
        override fun journalStart() = start
    }

    private val posted = mutableListOf<Int>()

    private fun check(
        store: ReminderStore,
        source: FilledDaysSource = journal,
        at: Instant = now,
        allowed: Boolean = true,
    ) = runBehindReminderCheck(store, source, today, at) {
        posted += it
        allowed
    }

    @Test
    fun postsTheEmptyDayCountAndRecordsTheSend() {
        val store = MemoryStore()
        assertEquals(10, check(store))
        assertEquals(listOf(10), posted)
        assertEquals(now, store.lastSent)
    }

    @Test
    fun aDailyCheckPostsAtMostOncePerWeek() {
        val store = MemoryStore()
        (0L..13L).forEach { day -> check(store, at = now.plusSeconds(day * 86_400)) }
        assertEquals(2, posted.size)
        assertEquals(now.plusSeconds(7 * 86_400), store.lastSent)
    }

    @Test
    fun aRefusedNotificationDoesNotUseUpTheWeek() {
        val store = MemoryStore()
        assertNull(check(store, allowed = false))
        assertNull(store.lastSent)
        assertEquals(10, check(store))
    }

    @Test
    fun noJournalYetSendsNothing() {
        val store = MemoryStore()
        assertNull(check(store, source = NoJournalYet))
        assertEquals(emptyList<Int>(), posted)
    }

    @Test
    fun turnedOffSendsNothing() {
        assertNull(check(MemoryStore(settings = ReminderSettings(enabled = false))))
        assertEquals(emptyList<Int>(), posted)
    }

    @Test
    fun unreadableSettingsSendNothing() {
        val store = MemoryStore(settings = null)
        assertNull(check(store))
        assertEquals(emptyList<Int>(), posted)
        assertNull(store.lastSent)
    }
}
