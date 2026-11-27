package io.github.benomite.secondes

import android.content.Context
import java.io.File
import java.time.LocalDate

/** What the reminder needs from the journal: the days that have a clip, by any member. */
fun interface FilledDaysSource {
    /** The filled days, or null when there is no journal yet or it cannot be read (then no reminder is sent). */
    fun filledDays(): Set<LocalDate>?
}

/**
 * The journal kept on this phone, once one is chosen in [prefs]: the days in [journal], this member's and those of
 * the others brought from the Drive folder. A journal file that cannot be read is left as it is and reads as null.
 */
class StoredJournalDays(private val prefs: JournalPrefs, private val journal: FileLocalJournal) : FilledDaysSource {
    override fun filledDays(): Set<LocalDate>? {
        if (prefs.stored() == null) return null
        return journal.read()?.keys
    }

    companion object {
        fun of(context: Context) = StoredJournalDays(
            JournalPrefs.of(context),
            FileLocalJournal(File(context.filesDir, MainActivity.JOURNAL_FILE)),
        )
    }
}
