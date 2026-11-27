package io.github.benomite.secondes

import android.content.Context
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

// org.json and the shared preferences need Robolectric.
@RunWith(RobolectricTestRunner::class)
class StoredJournalDaysTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val prefs = JournalPrefs(
        RuntimeEnvironment.getApplication().getSharedPreferences("stored-journal-days-test", Context.MODE_PRIVATE),
    )
    private val file get() = File(folder.root, "journal.json")

    private fun day(date: LocalDate, author: String) =
        JournalDay(date, SharedClip("content://$author/$date", SecondWindow(0)), author)

    @Test
    fun noJournalChosenYetReadsAsNoJournal() {
        assertNull(StoredJournalDays(prefs, FileLocalJournal(file)).filledDays())
    }

    @Test
    fun theDaysOfEveryMemberAreRead() {
        prefs.save(StoredJournal("folder", "Our year", JournalOrigin.CREATED))
        val mine = day(LocalDate.of(2026, 11, 20), "Léa")
        val theirs = day(LocalDate.of(2026, 11, 22), "Tom")
        FileLocalJournal(file).save(mapOf(mine.date to mine, theirs.date to theirs))

        assertEquals(setOf(mine.date, theirs.date), StoredJournalDays(prefs, FileLocalJournal(file)).filledDays())
    }

    @Test
    fun aChosenJournalWithNoDayYetHasNoFilledDay() {
        prefs.save(StoredJournal("folder", "Our year", JournalOrigin.JOINED))
        assertEquals(emptySet<LocalDate>(), StoredJournalDays(prefs, FileLocalJournal(file)).filledDays())
    }

    @Test
    fun anUnreadableJournalReadsAsNoJournalAndIsLeftAsItIs() {
        prefs.save(StoredJournal("folder", "Our year", JournalOrigin.CREATED))
        file.writeText("not a journal")

        assertNull(StoredJournalDays(prefs, FileLocalJournal(file)).filledDays())
        assertEquals("not a journal", file.readText())
        assertTrue(!File(folder.root, "journal.json.unreadable").exists())
    }
}
