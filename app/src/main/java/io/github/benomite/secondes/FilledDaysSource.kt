package io.github.benomite.secondes

import java.time.LocalDate

/** What the reminder needs from the journal: the days that have a clip, and the day the journal starts. */
interface FilledDaysSource {
    fun filledDays(): Set<LocalDate>

    /** The first day of the journal, or null when there is no journal yet (then no reminder is sent). */
    fun journalStart(): LocalDate?
}

/**
 * Stands in until the journal lands: no journal, so no empty days to announce.
 * Integration point: replace it in [BehindReminderWorker] with the journal's own source.
 */
object NoJournalYet : FilledDaysSource {
    override fun filledDays(): Set<LocalDate> = emptySet()
    override fun journalStart(): LocalDate? = null
}
