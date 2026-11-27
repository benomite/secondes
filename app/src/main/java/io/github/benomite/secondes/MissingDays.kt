package io.github.benomite.secondes

import java.time.LocalDate

/** Days between [from] and [to], both included, that have no clip in [filled]. */
fun missingDays(filled: Set<LocalDate>, from: LocalDate, to: LocalDate): List<LocalDate> {
    if (to.isBefore(from)) return emptyList()
    return generateSequence(from) { it.plusDays(1) }
        .takeWhile { !it.isAfter(to) }
        .filterNot { it in filled }
        .toList()
}

/**
 * The day the journal starts: its first filled day, by any member, or [today] while no day up to today is filled.
 * The days before it are not late: they may be filled, but nothing asks for them.
 */
fun journalStart(filled: Collection<LocalDate>, today: LocalDate): LocalDate =
    filled.minOrNull()?.takeIf { !it.isAfter(today) } ?: today
