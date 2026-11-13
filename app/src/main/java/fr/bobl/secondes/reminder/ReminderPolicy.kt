package fr.bobl.secondes.reminder

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Notification policy of the catch-up reminder:
 * - notify when the journal is at least [MIN_DAYS_BEHIND] days behind;
 * - at most once every [MIN_DAYS_BETWEEN_NOTIFICATIONS] days;
 * - never when it is up to date (0 days behind).
 */
const val MIN_DAYS_BEHIND = 3
const val MIN_DAYS_BETWEEN_NOTIFICATIONS = 3

fun shouldNotify(daysBehind: Int, lastNotified: LocalDate?, today: LocalDate): Boolean {
    if (daysBehind < MIN_DAYS_BEHIND) return false
    if (lastNotified == null) return true
    return ChronoUnit.DAYS.between(lastNotified, today) >= MIN_DAYS_BETWEEN_NOTIFICATIONS
}
