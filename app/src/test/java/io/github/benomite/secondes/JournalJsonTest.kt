package io.github.benomite.secondes

import java.io.File
import java.io.IOException
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// org.json is only a stub in a plain JVM test: Robolectric brings the real one.
@RunWith(RobolectricTestRunner::class)
class JournalJsonTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val day = JournalDay(
        LocalDate.of(2026, 3, 4),
        SharedClip("content://alice/1", SecondWindow(2_000)),
        "Alice",
        pending = true,
    )

    @Test
    fun aDayReadsBackAsItWasWritten() {
        assertEquals(day, decodeDay(encodeDay(day, withPending = true)))
        assertEquals(day.copy(pending = false), decodeDay(encodeDay(day)))
    }

    @Test
    fun aDayWithoutItsAuthorOrItsClipIsLeftOut() {
        val noAuthor = encodeDay(day).apply { remove("author") }
        val blankAuthor = encodeDay(day).put("author", " ")
        val noClip = encodeDay(day).apply { remove("clip") }
        val noStart = encodeDay(day).apply { getJSONObject("clip").remove("startMs") }
        val badDate = encodeDay(day).put("date", "someday")

        listOf(noAuthor, blankAuthor, noClip, noStart, badDate).forEach { assertNull(it.toString(), decodeDay(it)) }
        val array = "[${noAuthor}, ${encodeDay(day)}, ${noClip}]"
        assertEquals(listOf(day.copy(pending = false)), decodeDays(array))
    }

    @Test
    fun aServerAnswerThatIsNotAListFailsTheCall() {
        assertThrows(IOException::class.java) { decodeDays("<html>") }
    }

    @Test
    fun withNoServerConfiguredEveryCallFailsAndTheDaysStayPending() {
        val remote = HttpRemoteJournal("")
        assertThrows(IOException::class.java) { remote.fetchDays() }
        assertThrows(IOException::class.java) { remote.pushDay(day) }
    }

    @Test
    fun theDaysSavedInTheFileReadBackWithTheirPendingFlag() {
        val file = File(folder.root, "journal.json")
        val sent = day.copy(date = day.date.plusDays(1), pending = false)
        FileLocalJournal(file).save(mapOf(day.date to day, sent.date to sent))

        assertEquals(mapOf(day.date to day, sent.date to sent), FileLocalJournal(file).load())
    }

    @Test
    fun anUnreadableFileIsSetAsideBeforeAnythingIsWritten() {
        val file = File(folder.root, "journal.json")
        file.writeText("[{\"date\":\"2026-03-04\",\"author\":\"Alice\"}]")

        val journal = FileLocalJournal(file)
        assertEquals(emptyMap<LocalDate, JournalDay>(), journal.load())
        journal.save(mapOf(day.date to day))

        assertEquals("[{\"date\":\"2026-03-04\",\"author\":\"Alice\"}]", File(folder.root, "journal.json.unreadable").readText())
        assertEquals(mapOf(day.date to day), FileLocalJournal(file).load())
    }

    @Test
    fun noFileYetIsAnEmptyJournal() {
        val file = File(folder.root, "journal.json")
        assertTrue(FileLocalJournal(file).load().isEmpty())
    }
}
