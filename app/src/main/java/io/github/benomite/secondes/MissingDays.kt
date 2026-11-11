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
