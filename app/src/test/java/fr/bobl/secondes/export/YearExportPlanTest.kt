package fr.bobl.secondes.export

import io.github.benomite.secondes.JournalDay
import io.github.benomite.secondes.SecondWindow
import io.github.benomite.secondes.SharedClip
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Test

class YearExportPlanTest {
    private val jan1 = DatedClip(LocalDate.of(2026, 1, 1), "content://jan1", SecondWindow(0))
    private val jan4 = DatedClip(LocalDate.of(2026, 1, 4), "content://jan4", SecondWindow(2_000))
    private val mar9 = DatedClip(LocalDate.of(2026, 3, 9), "content://mar9", SecondWindow(500))
    private val iso = DateTimeFormatter.ISO_LOCAL_DATE

    private fun day(date: LocalDate, uri: String = "content://$date", startMs: Long = 0) =
        JournalDay(date, SharedClip(uri, SecondWindow(startMs)), "Léa")

    @Test
    fun putsTheClipsInDateOrderWhateverTheInputOrder() {
        val plan = planYearFilm(listOf(mar9, jan1, jan4), dateOverlay = false)

        assertEquals(listOf("content://jan1", "content://jan4", "content://mar9"), plan.map { it.videoUri })
    }

    @Test
    fun eachClipKeepsItsChosenSecond() {
        val plan = planYearFilm(listOf(mar9, jan4), dateOverlay = false)

        assertEquals(listOf(SecondWindow(2_000, 3_000), SecondWindow(500, 1_500)), plan.map { it.window })
    }

    @Test
    fun emptyDaysAddNothing() {
        val plan = planYearFilm(listOf(jan4, jan1, mar9), dateOverlay = false)

        assertEquals(3, plan.size)
    }

    @Test
    fun keepsTheInputOrderOfClipsOfTheSameDay() {
        val other = DatedClip(jan1.date, "content://jan1-other", SecondWindow(0))

        val plan = planYearFilm(listOf(jan4, jan1, other), dateOverlay = false)

        assertEquals(listOf("content://jan1", "content://jan1-other", "content://jan4"), plan.map { it.videoUri })
    }

    @Test
    fun labelsEachClipWithItsDateWhenTheOverlayIsOn() {
        val plan = planYearFilm(listOf(mar9, jan1), dateOverlay = true, formatter = iso)

        assertEquals(listOf("2026-01-01", "2026-03-09"), plan.map { it.label })
    }

    @Test
    fun drawsNoTextWhenTheOverlayIsOff() {
        val plan = planYearFilm(listOf(mar9, jan1), dateOverlay = false, formatter = iso)

        assertEquals(listOf(null, null), plan.map { it.label })
    }

    @Test
    fun anEmptyYearPlansNothing() {
        assertEquals(emptyList<PlannedClip>(), planYearFilm(emptyList(), dateOverlay = true))
    }

    @Test
    fun yearClipsKeepOnlyTheDaysOfTheChosenYear() {
        val days = listOf(
            day(LocalDate.of(2025, 12, 31)),
            day(LocalDate.of(2026, 1, 1)),
            day(LocalDate.of(2026, 12, 31)),
            day(LocalDate.of(2027, 1, 1)),
        )

        assertEquals(
            listOf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)),
            yearClips(days, 2026).map { it.date },
        )
        assertEquals(listOf(LocalDate.of(2025, 12, 31)), yearClips(days, 2025).map { it.date })
    }

    @Test
    fun yearClipsAreInDateOrderWhateverTheJournalOrder() {
        val days = listOf(day(LocalDate.of(2026, 7, 14)), day(LocalDate.of(2026, 2, 1)), day(LocalDate.of(2026, 5, 3)))

        assertEquals(
            listOf(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 5, 3), LocalDate.of(2026, 7, 14)),
            yearClips(days, 2026).map { it.date },
        )
    }

    @Test
    fun daysWithoutAClipLeaveNoGap() {
        // Only the 1st, 2nd and 10th are filled: the film goes straight from the 2nd to the 10th.
        val days = listOf(day(LocalDate.of(2026, 1, 10)), day(LocalDate.of(2026, 1, 1)), day(LocalDate.of(2026, 1, 2)))

        val plan = planYearFilm(yearClips(days, 2026), dateOverlay = true, formatter = iso)

        assertEquals(listOf("2026-01-01", "2026-01-02", "2026-01-10"), plan.map { it.label })
    }

    @Test
    fun yearClipsCarryEachDaysVideoAndSecond() {
        val days = listOf(day(LocalDate.of(2026, 4, 2), uri = "content://video/7", startMs = 4_200))

        assertEquals(
            listOf(DatedClip(LocalDate.of(2026, 4, 2), "content://video/7", SecondWindow(4_200, 5_200))),
            yearClips(days, 2026),
        )
    }

    @Test
    fun aYearWithoutDaysHasNoClip() {
        assertEquals(emptyList<DatedClip>(), yearClips(listOf(day(LocalDate.of(2025, 6, 1))), 2026))
    }

    @Test
    fun theCurrentYearIsOfferedEvenWithoutDays() {
        assertEquals(listOf(2026), exportYears(emptyList(), 2026))
    }

    @Test
    fun theYearsOfTheJournalAreOfferedLatestFirst() {
        val days = listOf(day(LocalDate.of(2024, 3, 1)), day(LocalDate.of(2026, 1, 1)), day(LocalDate.of(2024, 8, 9)))

        assertEquals(listOf(2026, 2024), exportYears(days, 2026))
    }

    @Test
    fun unreadableVideosAreSetApartInDateOrder() {
        val (readable, unreadable) = splitReadable(listOf(jan1, jan4, mar9)) { it != "content://jan4" }

        assertEquals(listOf(jan1, mar9), readable)
        assertEquals(listOf(jan4), unreadable)
    }
}
