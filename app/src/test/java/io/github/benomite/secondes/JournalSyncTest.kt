package io.github.benomite.secondes

import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The journal server in memory: one day per date, the last push wins. Offline, every call throws. */
private class FakeRemoteJournal : RemoteJournal {
    @Volatile var online = true
    private val days = mutableMapOf<LocalDate, JournalDay>()

    /** Every day written, in the server's order. */
    val writes = mutableListOf<JournalDay>()

    /** Runs inside a push, before the server answers. */
    var duringPush: () -> Unit = {}

    @Synchronized
    override fun fetchDays(): List<JournalDay> {
        if (!online) throw IOException("offline")
        return days.values.toList()
    }

    override fun pushDay(day: JournalDay): JournalDay {
        val stored = synchronized(this) {
            if (!online) throw IOException("offline")
            day.copy(pending = false).also {
                days[day.date] = it
                writes += it
            }
        }
        duringPush()
        return stored
    }

    @Synchronized
    fun stored(date: LocalDate): JournalDay? = days[date]

    @Synchronized
    fun storedCount(): Int = days.size
}

/** The phone's storage in memory; it keeps every save so a test can look at each state the journal went through. */
private class MemoryLocalJournal : LocalJournal {
    var failing = false
    var saved = mapOf<LocalDate, JournalDay>()
        private set
    val snapshots = mutableListOf<Map<LocalDate, JournalDay>>()

    override fun load() = saved

    @Synchronized
    override fun save(days: Map<LocalDate, JournalDay>) {
        if (failing) throw IOException("disk full")
        saved = days
        snapshots += days
    }
}

class JournalSyncTest {
    private val day = LocalDate.of(2026, 3, 4)
    private val clipA = SharedClip("content://alice/1", SecondWindow(2_000))
    private val clipB = SharedClip("content://bob/7", SecondWindow(5_000))

    private val server = FakeRemoteJournal()
    private val localA = MemoryLocalJournal()
    private val localB = MemoryLocalJournal()
    private val alice = JournalSync(localA, server, Dispatchers.Unconfined)
    private val bob = JournalSync(localB, server, Dispatchers.Unconfined)

    @Test
    fun aDayFilledByAIsPulledByBWithAsName() = runBlocking {
        assertTrue(alice.fill(day, clipA, "Alice"))
        assertTrue(alice.pushPending())

        assertTrue(bob.pull())

        assertEquals(JournalDay(day, clipA, "Alice", pending = false), bob.days.value[day])
        assertEquals(JournalDay(day, clipA, "Alice", pending = false), localB.saved[day])
    }

    @Test
    fun aDayFilledOfflineIsKeptPendingThenPushedOnceTheNetworkIsBack() = runBlocking {
        server.online = false
        assertTrue(alice.fill(day, clipA, "Alice"))

        assertEquals(JournalDay(day, clipA, "Alice", pending = true), alice.days.value[day])
        assertEquals(JournalDay(day, clipA, "Alice", pending = true), localA.saved[day])
        assertFalse(alice.pushPending())
        assertTrue(alice.days.value.getValue(day).pending)
        assertNull(server.stored(day))

        server.online = true
        assertTrue(alice.pushPending())

        assertEquals(JournalDay(day, clipA, "Alice", pending = false), alice.days.value[day])
        assertEquals(JournalDay(day, clipA, "Alice", pending = false), localA.saved[day])
        assertEquals(JournalDay(day, clipA, "Alice", pending = false), server.stored(day))
    }

    @Test
    fun bFillingADayFilledByAReplacesItAndAPullByAGetsBsClipAndName() = runBlocking {
        alice.fill(day, clipA, "Alice")
        assertTrue(alice.pushPending())
        assertTrue(bob.pull())

        assertTrue(bob.fill(day, clipB, "Bob"))
        assertTrue(bob.pushPending())
        assertTrue(alice.pull())

        assertEquals(mapOf(day to JournalDay(day, clipB, "Bob", pending = false)), alice.days.value)
        assertEquals(JournalDay(day, clipB, "Bob", pending = false), localA.saved[day])
    }

    @Test
    fun twoConcurrentWritesOnADayEndWithTheLaterServerWriteAndNeverAHalfDay() = runBlocking {
        val dates = (0L until 50L).map { day.plusDays(it) }
        server.online = false
        dates.forEach {
            alice.fill(it, clipA, "Alice")
            bob.fill(it, clipB, "Bob")
        }
        server.online = true

        // Both members send the same days at the same time, from two threads.
        val a = async(Dispatchers.Default) { alice.pushPending() }
        val b = async(Dispatchers.Default) { bob.pushPending() }
        assertTrue(a.await())
        assertTrue(b.await())
        assertTrue(alice.pull())
        assertTrue(bob.pull())

        assertEquals(dates.size, server.storedCount())
        dates.forEach { date ->
            val later = server.writes.last { it.date == date }
            assertEquals(later, server.stored(date))
            assertEquals(later, alice.days.value[date])
            assertEquals(later, bob.days.value[date])
        }
        // Every state either phone saved holds, for a day, one member's clip with that same member's name.
        val whole = listOf(clipA to "Alice", clipB to "Bob")
        (localA.snapshots + localB.snapshots).forEach { snapshot ->
            snapshot.values.forEach { assertTrue("half-written day: $it", (it.clip to it.author) in whole) }
        }
    }

    @Test
    fun aPullKeepsADayPendingHereAndOncePushedItIsTheLaterWrite() = runBlocking {
        server.online = false
        alice.fill(day, clipA, "Alice")
        server.online = true
        bob.fill(day, clipB, "Bob")
        assertTrue(bob.pushPending())

        assertTrue(alice.pull())
        assertEquals(JournalDay(day, clipA, "Alice", pending = true), alice.days.value[day])

        assertTrue(alice.pushPending())
        assertTrue(bob.pull())
        assertEquals(JournalDay(day, clipA, "Alice", pending = false), bob.days.value[day])
    }

    @Test
    fun openingTheAppPushesThePendingDaysThenPullsTheOthers() = runBlocking {
        val other = day.plusDays(1)
        server.online = false
        alice.fill(day, clipA, "Alice")
        server.online = true
        bob.fill(other, clipB, "Bob")
        bob.pushPending()

        alice.syncOnOpen()

        assertEquals(JournalDay(day, clipA, "Alice", pending = false), server.stored(day))
        assertEquals(
            mapOf(
                day to JournalDay(day, clipA, "Alice", pending = false),
                other to JournalDay(other, clipB, "Bob", pending = false),
            ),
            alice.days.value,
        )
    }

    @Test
    fun aPullThatCannotReachTheServerLeavesTheDaysAsTheyWere() = runBlocking {
        alice.fill(day, clipA, "Alice")
        server.online = false

        assertFalse(alice.pull())

        assertEquals(JournalDay(day, clipA, "Alice", pending = true), alice.days.value[day])
    }

    @Test
    fun aDayThatCannotBeSavedOnThePhoneChangesNothing() {
        localA.failing = true

        assertFalse(alice.fill(day, clipA, "Alice"))

        assertEquals(emptyMap<LocalDate, JournalDay>(), alice.days.value)
    }

    @Test
    fun aDayFilledAgainWhileItIsBeingSentStaysPendingWithItsNewerClip() = runBlocking {
        alice.fill(day, clipA, "Alice")
        server.duringPush = {
            server.duringPush = {}
            alice.fill(day, clipB, "Alice")
        }

        assertFalse(alice.pushPending())
        assertEquals(JournalDay(day, clipB, "Alice", pending = true), alice.days.value[day])

        assertTrue(alice.pushPending())
        assertEquals(JournalDay(day, clipB, "Alice", pending = false), server.stored(day))
    }

    @Test
    fun theDaysSavedOnThePhoneAreThereWhenTheAppOpensAgain() {
        alice.fill(day, clipA, "Alice")

        val reopened = JournalSync(localA, server, Dispatchers.Unconfined)

        assertEquals(JournalDay(day, clipA, "Alice", pending = true), reopened.days.value[day])
    }
}
