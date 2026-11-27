package io.github.benomite.secondes

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BehindReminderCheckTest {
    private val start = LocalDate.of(2026, 1, 1)
    private val today = LocalDate.of(2026, 1, 12)
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

    /** Jan 1 filled, the journal's start; Jan 2..11 empty: 10 missed days on Jan 12. */
    private val journal = FilledDaysSource { setOf(start) }

    /** No journal chosen on this phone yet, or one whose file cannot be read. */
    private val noJournal = FilledDaysSource { null }

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
        assertNull(check(store, source = noJournal))
        assertEquals(emptyList<Int>(), posted)
    }

    @Test
    fun aJournalWithNoDayFilledYetSendsNothing() {
        val store = MemoryStore(settings = ReminderSettings(threshold = 1))
        assertNull(check(store, source = { emptySet() }))
        assertEquals(emptyList<Int>(), posted)
    }

    @Test
    fun theDaysBeforeTheFirstFilledDayOfAnyMemberAreNotAnnounced() {
        // Jan 10 filled by another member: only Jan 11 is missed, Jan 1..9 are before the start.
        val store = MemoryStore(settings = ReminderSettings(threshold = 1))
        assertEquals(1, check(store, source = { setOf(LocalDate.of(2026, 1, 10)) }))
        assertEquals(listOf(1), posted)
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
