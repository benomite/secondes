package io.github.benomite.secondes

import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** The user's reminder settings: off, or on with the number of empty days that triggers it (10 by default). */
data class ReminderSettings(val enabled: Boolean = true, val threshold: Int = DEFAULT_THRESHOLD) {
    companion object {
        const val DEFAULT_THRESHOLD = 10
    }
}

/** At most one "falling behind" notification per this period. */
val REMINDER_MIN_INTERVAL: Duration = Duration.ofDays(7)

/**
 * The number of empty days to announce, or null when no notification is due: the reminder is off,
 * the journal has fewer empty days than the threshold between [journalStart] and [today] (both included),
 * or the last one was sent less than a week before [now].
 */
fun behindReminderDue(
    settings: ReminderSettings,
    filled: Set<LocalDate>,
    journalStart: LocalDate,
    today: LocalDate,
    lastSentAt: Instant?,
    now: Instant,
): Int? {
    if (!settings.enabled || settings.threshold < 1) return null
    if (lastSentAt != null && Duration.between(lastSentAt, now) < REMINDER_MIN_INTERVAL) return null
    val empty = missingDays(filled, journalStart, today).size
    return empty.takeIf { it >= settings.threshold }
}
