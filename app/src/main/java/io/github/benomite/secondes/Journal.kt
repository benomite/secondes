package io.github.benomite.secondes

import java.io.File
import java.io.IOException
import java.time.LocalDate

/** The journal as the family shares it, one day per date. Both calls throw [IOException] when it cannot be reached. */
interface RemoteJournal {
    /** The family's days, each with its one-second clip brought to this phone. */
    fun fetchDays(): List<JournalDay>

    /**
     * Writes [day], whose one-second clip is the file [clip], which then is that date's day for the family; returns
     * the day as it was stored. The clip is written first: when it cannot be, the day is not written at all.
     */
    fun pushDay(day: JournalDay, clip: File): JournalDay
}

/** Where the journal's days are kept on the phone. [save] replaces them all at once, or throws [IOException]. */
interface LocalJournal {
    fun load(): Map<LocalDate, JournalDay>

    fun save(days: Map<LocalDate, JournalDay>)
}

/**
 * A day's clip: the video it plays from and the second chosen in it. Filled here, it is first the second chosen in
 * the member's own video; once cut ([ClipFiles]), or brought from the family's Drive folder, it is the day's
 * standalone one-second clip file kept on the phone, played whole. [source] is the second it was cut from, known
 * only on the phone that cut it: it is never written in the family's folder.
 */
data class SharedClip(val videoUri: String, val window: SecondWindow, val source: SharedClip? = null)

/**
 * A day of the shared journal: its one clip and the name of the member who filled it.
 * [pending] is true while the day was filled on this phone and not yet sent to the family's Drive folder.
 */
data class JournalDay(val date: LocalDate, val clip: SharedClip, val author: String, val pending: Boolean = false)
