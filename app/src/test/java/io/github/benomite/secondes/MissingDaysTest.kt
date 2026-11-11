package io.github.benomite.secondes

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class MissingDaysTest {
    private val jan1 = LocalDate.of(2026, 1, 1)
    private val jan5 = LocalDate.of(2026, 1, 5)

    @Test
    fun listsTheDaysWithoutAClip() {
        val filled = setOf(jan1, LocalDate.of(2026, 1, 3))

        val missing = missingDays(filled, jan1, jan5)

        assertEquals(
            listOf(LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 4), jan5),
            missing,
        )
    }

    @Test
    fun includesBothBounds() {
        assertEquals(listOf(jan1), missingDays(emptySet(), jan1, jan1))
    }

    @Test
    fun isEmptyWhenTheRangeIsReversed() {
        assertEquals(emptyList<LocalDate>(), missingDays(emptySet(), jan5, jan1))
    }
}
