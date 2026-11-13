package fr.bobl.secondes.reminder

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * How many days the journal is behind: the days between the most recent filled date and [today].
 * 0 when [today] is filled (up to date), 1 when the last filled day is yesterday, and so on.
 * The order of [filled] does not matter, and filled dates after [today] count as up to date.
 * With nothing filled there is no reference point, so the result is 0: a journal that was never
 * started is not "behind".
 */
fun daysBehind(filled: Set<LocalDate>, today: LocalDate): Int {
    val latest = filled.maxOrNull() ?: return 0
    return ChronoUnit.DAYS.between(latest, today).toInt().coerceAtLeast(0)
}
