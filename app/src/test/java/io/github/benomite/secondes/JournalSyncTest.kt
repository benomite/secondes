package io.github.benomite.secondes

import java.io.File
import java.io.IOException
import java.time.LocalDate
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The journal server in memory: one day per date, the last push wins, its clip's content beside it. Offline, every
 * call throws. As in the family's folder, a clip's [SharedClip.source] is never stored.
 */
private class FakeRemoteJournal : RemoteJournal {
    @Volatile var online = true
    private val days = mutableMapOf<LocalDate, JournalDay>()
    private val clipContents = mutableMapOf<LocalDate, String>()

    /** Every day written, in the server's order. */
    val writes = mutableListOf<JournalDay>()

    /** Runs inside a push, before the server answers. */
    var duringPush: () -> Unit = {}

    @Synchronized
    override fun fetchDays(): List<JournalDay> {
        if (!online) throw IOException("offline")
        return days.values.toList()
    }

    override fun pushDay(day: JournalDay, clip: File): JournalDay {
        synchronized(this) {
            if (!online) throw IOException("offline")
            val stored = day.copy(clip = day.clip.copy(source = null), pending = false)
            clipContents[day.date] = clip.readText()
            days[day.date] = stored
            writes += stored
        }
        duringPush()
        return day.copy(pending = false)
    }

    @Synchronized
    fun stored(date: LocalDate): JournalDay? = days[date]

    @Synchronized
    fun storedClip(date: LocalDate): String? = clipContents[date]

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

/** Cuts a second into a small text file naming it; a video in [deleted] cannot be read any more. */
class FakeSecondCutter : SecondCutter {
    val deleted = mutableSetOf<String>()

    override suspend fun cut(videoUri: String, window: SecondWindow, output: File) {
        if (videoUri in deleted) throw IOException("$videoUri is gone")
        output.writeText("second ${window.startMs}-${window.endMs} of $videoUri")
    }
}

class JournalSyncTest {
    private val day = LocalDate.of(2026, 3, 4)
    private val clipA = SharedClip("content://alice/1", SecondWindow(2_000))
    private val clipB = SharedClip("content://bob/7", SecondWindow(5_000))

    private val server = FakeRemoteJournal()
    private val localA = MemoryLocalJournal()
    private val localB = MemoryLocalJournal()
    private val cutterA = FakeSecondCutter()
    private val clipsA = ClipFiles(createTempDirectory("alice").toFile(), cutterA)
    private val clipsB = ClipFiles(createTempDirectory("bob").toFile(), FakeSecondCutter())
    private val alice = JournalSync(localA, server, clipsA, Dispatchers.Unconfined)
    private val bob = JournalSync(localB, server, clipsB, Dispatchers.Unconfined)

    /** [clip] cut on Alice's phone: the one-second clip file kept there, with [clip] as its source. */
    private fun keptA(clip: SharedClip) = runBlocking { clipsA.keep(clip) }

    private fun keptB(clip: SharedClip) = runBlocking { clipsB.keep(clip) }

    /** The clip as the family's folder holds it: without the way back to its author's video. */
    private fun SharedClip.shared() = copy(source = null)

    @Test
    fun aDayFilledByAIsPulledByBWithAsName() = runBlocking {
        assertTrue(alice.fill(day, clipA, "Alice"))
        assertTrue(alice.pushPending())

        assertTrue(bob.pull())

        assertEquals(JournalDay(day, keptA(clipA).shared(), "Alice", pending = false), bob.days.value[day])
        assertEquals(JournalDay(day, keptA(clipA).shared(), "Alice", pending = false), localB.saved[day])
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

        assertEquals(JournalDay(day, keptA(clipA), "Alice", pending = false), alice.days.value[day])
        assertEquals(JournalDay(day, keptA(clipA), "Alice", pending = false), localA.saved[day])
        assertEquals(JournalDay(day, keptA(clipA).shared(), "Alice", pending = false), server.stored(day))
    }

    @Test
    fun offlineTheSecondIsCutOnThePhoneAndTheDayStaysNotSentUntilItsClipIsSent() = runBlocking {
        server.online = false
        assertTrue(alice.fill(day, clipA, "Alice"))

        assertFalse(alice.pushPending())

        // Cut here, offline: the day now plays from its own clip, and is still 'not sent'; nothing reached the server.
        val kept = alice.days.value.getValue(day)
        assertEquals(JournalDay(day, keptA(clipA), "Alice", pending = true), kept)
        assertEquals(kept, localA.saved[day])
        assertNotNull(clipsA.fileOf(kept.clip))
        assertEquals(0, server.storedCount())

        // The video it was cut from is deleted from the gallery: the day is sent all the same, with its second.
        cutterA.deleted += clipA.videoUri
        server.online = true
        assertTrue(alice.pushPending())
        assertFalse(alice.days.value.getValue(day).pending)
        assertEquals("second 2000-3000 of content://alice/1", server.storedClip(day))
    }

    @Test
    fun aSecondThatCannotBeCutLeavesTheDayNotSentAndTheOtherDaysGo() = runBlocking {
        val other = day.plusDays(1)
        cutterA.deleted += clipA.videoUri
        alice.fill(day, clipA, "Alice")
        alice.fill(other, clipB, "Alice")

        assertFalse(alice.pushPending())

        assertEquals(JournalDay(day, clipA, "Alice", pending = true), alice.days.value[day])
        assertNull(server.stored(day))
        assertEquals(JournalDay(other, keptA(clipB).shared(), "Alice"), server.stored(other))
    }

    @Test
    fun bFillingADayFilledByAReplacesItAndAPullByAGetsBsClipAndName() = runBlocking {
        alice.fill(day, clipA, "Alice")
        assertTrue(alice.pushPending())
        assertTrue(bob.pull())

        assertTrue(bob.fill(day, clipB, "Bob"))
        assertTrue(bob.pushPending())
        assertTrue(alice.pull())

        assertEquals(mapOf(day to JournalDay(day, keptB(clipB).shared(), "Bob", pending = false)), alice.days.value)
        assertEquals(JournalDay(day, keptB(clipB).shared(), "Bob", pending = false), localA.saved[day])
    }

    @Test
    fun aDaySentFromHereKeepsTheWayBackToItsVideoWhenItComesBackFromTheServer() = runBlocking {
        alice.fill(day, clipA, "Alice")
        assertTrue(alice.pushPending())

        assertTrue(alice.pull())

        assertEquals(clipA, alice.days.value.getValue(day).clip.source)
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
            assertEquals(later.clip, alice.days.value.getValue(date).clip.shared())
            assertEquals(later.author, alice.days.value.getValue(date).author)
            assertEquals(later.clip, bob.days.value.getValue(date).clip.shared())
            assertEquals(later.author, bob.days.value.getValue(date).author)
        }
        // Every state either phone saved holds, for a day, one member's clip with that same member's name.
        val whole = listOf(clipA to "Alice", clipB to "Bob", keptA(clipA).shared() to "Alice", keptB(clipB).shared() to "Bob")
        (localA.snapshots + localB.snapshots).forEach { snapshot ->
            snapshot.values.forEach { assertTrue("half-written day: $it", (it.clip.shared() to it.author) in whole) }
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
        assertEquals(JournalDay(day, keptA(clipA).shared(), "Alice", pending = false), bob.days.value[day])
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

        assertEquals(JournalDay(day, keptA(clipA).shared(), "Alice", pending = false), server.stored(day))
        assertEquals(
            mapOf(
                day to JournalDay(day, keptA(clipA), "Alice", pending = false),
                other to JournalDay(other, keptB(clipB).shared(), "Bob", pending = false),
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
        assertEquals(JournalDay(day, keptA(clipB).shared(), "Alice", pending = false), server.stored(day))
    }

    @Test
    fun theDaysSavedOnThePhoneAreThereWhenTheAppOpensAgain() {
        alice.fill(day, clipA, "Alice")

        val reopened = JournalSync(localA, server, clipsA, Dispatchers.Unconfined)

        assertEquals(JournalDay(day, clipA, "Alice", pending = true), reopened.days.value[day])
    }
}
