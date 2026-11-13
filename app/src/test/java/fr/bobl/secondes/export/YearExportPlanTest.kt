package fr.bobl.secondes.export

import java.io.File
import java.io.FileNotFoundException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class YearExportPlanTest {
    private val jan1 = DatedClip(LocalDate.of(2026, 1, 1), File("jan1.mp4"))
    private val jan4 = DatedClip(LocalDate.of(2026, 1, 4), File("jan4.mp4"))
    private val mar9 = DatedClip(LocalDate.of(2026, 3, 9), File("mar9.mp4"))
    private val iso = DateTimeFormatter.ISO_LOCAL_DATE

    @Test
    fun putsTheClipsInDateOrderWhateverTheInputOrder() {
        val plan = planYearFilm(listOf(mar9, jan1, jan4), dateOverlay = false)

        assertEquals(
            listOf(File("jan1.mp4"), File("jan4.mp4"), File("mar9.mp4")),
            plan.map { it.file },
        )
    }

    @Test
    fun emptyDaysAddNothing() {
        val plan = planYearFilm(listOf(jan4, jan1, mar9), dateOverlay = false)

        assertEquals(3, plan.size)
    }

    @Test
    fun keepsTheInputOrderOfClipsOfTheSameDay() {
        val other = DatedClip(jan1.date, File("jan1-other.mp4"))

        val plan = planYearFilm(listOf(jan4, jan1, other), dateOverlay = false)

        assertEquals(
            listOf(File("jan1.mp4"), File("jan1-other.mp4"), File("jan4.mp4")),
            plan.map { it.file },
        )
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
    fun refusesToExportNoClip() {
        assertThrows(IllegalArgumentException::class.java) { checkExportable(emptyList()) }
    }

    @Test
    fun refusesToExportAClipWhoseFileIsMissing() {
        val present = File.createTempFile("clip", ".mp4").apply { deleteOnExit() }
        val missing = DatedClip(LocalDate.of(2026, 2, 3), File(present.parentFile, "gone-${present.name}"))

        val error = assertThrows(FileNotFoundException::class.java) {
            checkExportable(listOf(DatedClip(jan1.date, present), missing))
        }
        assertTrue(error.message!!.contains("2026-02-03"))
    }

    @Test
    fun acceptsClipsWhoseFilesArePresent() {
        val present = File.createTempFile("clip", ".mp4").apply { deleteOnExit() }

        checkExportable(listOf(DatedClip(jan1.date, present)))
    }
}
