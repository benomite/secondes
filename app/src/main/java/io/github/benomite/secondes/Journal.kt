package io.github.benomite.secondes

import java.io.IOException
import java.time.LocalDate

/** A day's clip as the journal shares it: the video it was taken from and the second chosen in it. */
data class SharedClip(val videoUri: String, val window: SecondWindow)

/**
 * A day of the shared journal: its one clip and the name of the member who filled it.
 * [pending] is true while the day was filled on this phone and not yet accepted by the server.
 */
data class JournalDay(val date: LocalDate, val clip: SharedClip, val author: String, val pending: Boolean = false)

/** The journal as the server holds it, one day per date. Both calls throw [IOException] when the server cannot be reached. */
interface RemoteJournal {
    fun fetchDays(): List<JournalDay>

    /** Writes [day] on the server, replacing whatever that date held; returns the day as the server stored it. */
    fun pushDay(day: JournalDay): JournalDay
}

/** Where the journal's days are kept on the phone. [save] replaces them all at once, or throws [IOException]. */
interface LocalJournal {
    fun load(): Map<LocalDate, JournalDay>

    fun save(days: Map<LocalDate, JournalDay>)
}
