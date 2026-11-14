package io.github.benomite.secondes

import androidx.compose.ui.graphics.ImageBitmap
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

// ImageBitmap needs a real Bitmap, which a plain JVM test does not have.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BatchImportTest {
    private val paris = ZoneId.of("Europe/Paris")
    private val mar4 = LocalDate.of(2026, 3, 4)
    private val mar5 = LocalDate.of(2026, 3, 5)
    private val video = VideoInfo(10_000, 1080, 1920)
    private val thumbA = ImageBitmap(1, 1)
    private val thumbB = ImageBitmap(2, 2)

    private fun read(
        position: Int,
        shotOn: LocalDate?,
        mime: String? = "video/mp4",
        info: VideoInfo? = video,
        thumb: ImageBitmap? = thumbA,
    ) = ImportRead(position, "content://v/$position", mime, info, shotOn, thumb)

    // The shooting date

    @Test
    fun readsTheMetadataCreationDate() {
        assertEquals(Instant.parse("2026-03-04T18:30:12Z"), parseMetadataDate("20260304T183012.000Z"))
        assertEquals(Instant.parse("2026-03-04T18:30:12Z"), parseMetadataDate("20260304T183012"))
    }

    @Test
    fun anEmptyOrUnreadableMetadataDateIsNoDate() {
        assertNull(parseMetadataDate(null))
        assertNull(parseMetadataDate(""))
        assertNull(parseMetadataDate("yesterday"))
        assertNull(parseMetadataDate("20261399T999999.000Z"))
        assertNull(parseMetadataDate("19040101T000000.000Z"))
        assertNull(parseMetadataDate("19700101T000000.000Z"))
    }

    @Test
    fun theShootingDayComesFromTheMetadataBeforeThePickerDate() {
        assertEquals(mar4, shootingDay("20260304T120000.000Z", Instant.parse("2026-01-01T12:00:00Z"), paris))
    }

    @Test
    fun theShootingDayFallsBackOnThePickerDate() {
        assertEquals(mar5, shootingDay(null, Instant.parse("2026-03-05T08:00:00Z"), paris))
        assertEquals(mar5, shootingDay("garbage", Instant.parse("2026-03-05T08:00:00Z"), paris))
    }

    @Test
    fun theShootingDayIsTheLocalDayNotTheUtcOne() {
        // 23:30 UTC on March 4 is already March 5 in Paris.
        assertEquals(mar5, shootingDay("20260304T233000.000Z", null, paris))
        assertEquals(mar4, shootingDay("20260304T233000.000Z", null, ZoneOffset.UTC))
    }

    @Test
    fun noDateAnywhereGivesNoShootingDay() {
        assertNull(shootingDay(null, null, paris))
    }

    // Refusals

    @Test
    fun aPhotoIsRefusedAndNeverFiled() {
        val plan = planImport(listOf(read(1, mar4, mime = "image/jpeg"), read(2, mar5)), emptySet())
        assertEquals(listOf(RefusedItem(1, RefusalReason.NOT_A_VIDEO)), plan.refused)
        assertEquals(setOf(mar5), plan.toSave().keys)
    }

    @Test
    fun anUnreadableVideoOrOneWithNoShootingDateIsRefused() {
        val plan = planImport(listOf(read(1, mar4, info = null), read(2, null)), emptySet())
        assertEquals(
            listOf(RefusedItem(1, RefusalReason.UNREADABLE), RefusedItem(2, RefusalReason.NO_SHOOTING_DATE)),
            plan.refused,
        )
        assertTrue(plan.videos.isEmpty())
    }

    @Test
    fun anUnknownMimeTypeIsLeftToTheVideoRead() {
        assertFalse(isNotAVideo(null))
        assertFalse(isNotAVideo("video/quicktime"))
        assertTrue(isNotAVideo("image/heic"))
        assertEquals(1, planImport(listOf(read(1, mar4, mime = null)), emptySet()).videos.size)
    }

    // Date assignment and conflicts

    @Test
    fun eachVideoGoesOnTheDayItWasShotWithItsFirstSecond() {
        val plan = planImport(listOf(read(1, mar4, thumb = thumbA), read(2, mar5, thumb = thumbB)), emptySet())
        assertTrue(plan.isResolved)
        val saved = plan.toSave()
        assertEquals("content://v/1", saved[mar4]?.uri)
        assertEquals("content://v/2", saved[mar5]?.uri)
        assertEquals(SecondWindow(0), saved[mar4]?.window)
        assertSame(thumbB, saved[mar5]?.thumbnail)
    }

    @Test
    fun aMissingThumbnailLeavesABlackOne() {
        val plan = planImport(listOf(read(1, mar4, thumb = null)), emptySet())
        assertEquals(1, plan.videos.single().thumbnail.width)
    }

    @Test
    fun videosSharingADayWaitForTheUsersChoice() {
        val plan = planImport(listOf(read(1, mar4), read(2, mar4), read(3, mar5)), emptySet())
        assertEquals(listOf(mar4), plan.choiceDays)
        assertFalse(plan.isResolved)
        assertNull(plan.choiceOf(mar4))
        assertTrue(plan.toSave().isEmpty())

        val chosen = plan.choose(mar4, DayChoice.Take("content://v/2"))
        assertTrue(chosen.isResolved)
        assertEquals(mapOf(mar4 to "content://v/2", mar5 to "content://v/3"), chosen.toSave().mapValues { it.value.uri })
    }

    @Test
    fun aChoiceThatIsNotAnOptionOfTheDayChangesNothing() {
        val plan = planImport(listOf(read(1, mar4), read(2, mar4), read(3, mar5)), emptySet())
        assertEquals(plan, plan.choose(mar4, DayChoice.Take("content://v/3")))
        assertEquals(plan, plan.choose(mar4, DayChoice.KeepExisting))
        assertEquals(plan, plan.choose(LocalDate.of(2026, 1, 1), DayChoice.Take("content://v/1")))
    }

    @Test
    fun aFilledDayAsksToKeepOrReplace() {
        val plan = planImport(listOf(read(1, mar4)), setOf(mar4))
        assertEquals(listOf(mar4), plan.choiceDays)
        assertFalse(plan.isResolved)

        val kept = plan.choose(mar4, DayChoice.KeepExisting)
        assertTrue(kept.isResolved)
        assertTrue(kept.toSave().isEmpty())
        assertEquals("content://v/1", plan.choose(mar4, DayChoice.Take("content://v/1")).toSave()[mar4]?.uri)
    }

    @Test
    fun aDayWithASingleVideoNeedsNoChoiceWhenAnotherDayIsFilled() {
        val plan = planImport(listOf(read(1, mar5)), setOf(mar4))
        assertTrue(plan.isResolved)
        assertEquals(setOf(mar5), plan.toSave().keys)
    }

    @Test
    fun thePreviewShowsTheFirstSelectedVideoUntilAChoiceAndAFilledDayKeepsItsClip() {
        val shared = planImport(listOf(read(1, mar4, thumb = thumbA), read(2, mar4, thumb = thumbB)), emptySet())
        assertEquals(1, shared.shownOn(mar4)?.position)
        assertEquals(2, shared.choose(mar4, DayChoice.Take("content://v/2")).shownOn(mar4)?.position)

        val filled = planImport(listOf(read(1, mar4)), setOf(mar4))
        assertNull(filled.shownOn(mar4))
        assertNull(filled.choose(mar4, DayChoice.KeepExisting).shownOn(mar4))
        assertEquals(1, filled.choose(mar4, DayChoice.Take("content://v/1")).shownOn(mar4)?.position)
    }

    // The import through the days' storage

    /** The phone's journal storage in memory; a failing one cannot save. */
    private class ImportLocalJournal(var failing: Boolean = false) : LocalJournal {
        var saved = mapOf<LocalDate, JournalDay>()

        override fun load() = saved

        override fun save(days: Map<LocalDate, JournalDay>) {
            if (failing) throw IOException("disk full")
            saved = days
        }
    }

    /** No server: the import is saved on the phone first, sending it is the journal's own concern. */
    private object OfflineJournal : RemoteJournal {
        override fun fetchDays(): List<JournalDay> = throw IOException("offline")

        override fun pushDay(day: JournalDay): JournalDay = throw IOException("offline")
    }

    private val local = ImportLocalJournal()
    private val journal = JournalSync(local, OfflineJournal, Dispatchers.Unconfined)
    private val oldClip = SharedClip("content://old", SecondWindow(3_000))

    /** Mar 4 already filled by Léa, from this phone, with its thumbnail. */
    private fun filledDays(): DayPicks {
        val days = DayPicks()
        days.open(mar4, oldClip.videoUri, video)
        days.confirm(oldClip.window, thumbB)
        assertTrue(journal.fill(mar4, oldClip, "Léa"))
        return days
    }

    private fun DayPicks.calendar() = clips(journal.days.value.values, "Léa")

    @Test
    fun thePreviewIsOnTheCalendarButNothingIsSavedBeforeConfirming() {
        val days = DayPicks()
        days.startImport(listOf(read(1, mar5, thumb = thumbA)), journal.days.value.keys)

        assertTrue(days.picks.isEmpty())
        assertTrue(journal.days.value.isEmpty())
        val clip = days.calendar().single()
        assertEquals(mar5, clip.date)
        assertEquals("Léa", clip.author)
        assertSame(thumbA, clip.thumbnail)
    }

    @Test
    fun aDayFilledInTheJournalIsAFilledDayOfTheImport() {
        val days = DayPicks()
        // Filled by another member: no thumbnail here, but the day is filled all the same.
        journal.fill(mar4, SharedClip("content://bob/7", SecondWindow(1_000)), "Bob")
        days.startImport(listOf(read(1, mar4)), journal.days.value.keys)

        assertEquals(listOf(mar4), days.importing?.choiceDays)
        assertEquals("Bob", days.calendar().single().author)
    }

    @Test
    fun thePreviewOfAReplacementShowsTheNewVideoOnTheFilledDay() {
        val days = filledDays()
        days.startImport(listOf(read(1, mar4, thumb = thumbA)), journal.days.value.keys)
        assertSame(thumbB, days.calendar().single().thumbnail)

        days.chooseForImport(mar4, DayChoice.Take("content://v/1"))
        assertSame(thumbA, days.calendar().single().thumbnail)
        assertEquals(oldClip, journal.days.value[mar4]?.clip)
    }

    @Test
    fun cancellingTheImportLeavesTheCalendarUnchanged() {
        val days = filledDays()
        val before = days.picks
        val saved = journal.days.value
        days.startImport(listOf(read(1, mar4), read(2, mar5)), journal.days.value.keys)
        days.chooseForImport(mar4, DayChoice.Take("content://v/1"))
        days.cancelImport()

        assertNull(days.importing)
        assertEquals(before, days.picks)
        assertEquals(saved, journal.days.value)
        assertEquals(saved, local.saved)
        assertEquals(listOf(mar4), days.calendar().map { it.date })
        assertSame(thumbB, days.calendar().single().thumbnail)
    }

    @Test
    fun confirmingFilesTheChosenVideosOnly() {
        val days = DayPicks()
        days.startImport(listOf(read(1, mar4), read(2, mar4), read(3, mar5)), journal.days.value.keys)
        // A day still waits for its choice: nothing is saved and the preview stays open.
        assertNull(days.confirmImport(journal, "Léa"))
        assertTrue(journal.days.value.isEmpty())
        assertNotNull(days.importing)

        days.chooseForImport(mar4, DayChoice.Take("content://v/2"))
        assertEquals(true, days.confirmImport(journal, "Léa"))
        assertNull(days.importing)
        val filed = journal.days.value
        assertEquals(mapOf(mar4 to "content://v/2", mar5 to "content://v/3"), filed.mapValues { it.value.clip.videoUri })
        assertEquals(SecondWindow(0), filed[mar4]?.clip?.window)
        assertTrue(filed.values.all { it.author == "Léa" && it.pending })
        assertEquals(filed, local.saved)
        // The calendar shows the imported days with the thumbnails read for them.
        assertEquals(setOf(mar4, mar5), days.calendar().map { it.date }.toSet())
        assertTrue(days.calendar().all { it.thumbnail === thumbA })
    }

    @Test
    fun keepingOrReplacingAFilledDayIsWhatConfirmSaves() {
        val kept = filledDays()
        kept.startImport(listOf(read(1, mar4)), journal.days.value.keys)
        kept.chooseForImport(mar4, DayChoice.KeepExisting)
        assertEquals(true, kept.confirmImport(journal, "Léa"))
        assertEquals(oldClip, journal.days.value[mar4]?.clip)
        assertSame(thumbB, kept.calendar().single().thumbnail)

        val replaced = filledDays()
        replaced.startImport(listOf(read(1, mar4, thumb = thumbA)), journal.days.value.keys)
        replaced.chooseForImport(mar4, DayChoice.Take("content://v/1"))
        assertEquals(true, replaced.confirmImport(journal, "Léa"))
        assertEquals(SharedClip("content://v/1", SecondWindow(0)), journal.days.value[mar4]?.clip)
        assertSame(thumbA, replaced.calendar().single().thumbnail)
    }

    @Test
    fun anImportThatCannotBeSavedOnThePhoneSaysSoAndFilesNothing() {
        val days = DayPicks()
        days.startImport(listOf(read(1, mar5)), journal.days.value.keys)
        local.failing = true

        assertEquals(false, days.confirmImport(journal, "Léa"))
        assertTrue(journal.days.value.isEmpty())
        assertTrue(days.picks.isEmpty())
        assertTrue(days.calendar().isEmpty())
    }
}
