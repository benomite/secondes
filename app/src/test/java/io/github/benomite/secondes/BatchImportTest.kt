package io.github.benomite.secondes

import androidx.compose.ui.graphics.ImageBitmap
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
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

    private fun filledDays(): DayPicks {
        val days = DayPicks()
        days.open(mar4, "content://old", video)
        days.confirm(SecondWindow(3_000), thumbB)
        return days
    }

    @Test
    fun thePreviewIsOnTheCalendarButNothingIsSavedBeforeConfirming() {
        val days = DayPicks()
        days.startImport(listOf(read(1, mar5, thumb = thumbA)))

        assertTrue(days.picks.isEmpty())
        val clip = days.clips("Me").single()
        assertEquals(mar5, clip.date)
        assertSame(thumbA, clip.thumbnail)
    }

    @Test
    fun thePreviewOfAReplacementShowsTheNewVideoOnTheFilledDay() {
        val days = filledDays()
        days.startImport(listOf(read(1, mar4, thumb = thumbA)))
        assertSame(thumbB, days.clips("Me").single().thumbnail)

        days.chooseForImport(mar4, DayChoice.Take("content://v/1"))
        assertSame(thumbA, days.clips("Me").single().thumbnail)
        assertEquals("content://old", days.picks[mar4]?.videoUri)
    }

    @Test
    fun cancellingTheImportLeavesTheCalendarUnchanged() {
        val days = filledDays()
        val before = days.picks
        days.startImport(listOf(read(1, mar4), read(2, mar5)))
        days.chooseForImport(mar4, DayChoice.Take("content://v/1"))
        days.cancelImport()

        assertNull(days.importing)
        assertEquals(before, days.picks)
        assertEquals(listOf(mar4), days.clips("Me").map { it.date })
        assertSame(thumbB, days.clips("Me").single().thumbnail)
    }

    @Test
    fun confirmingFilesTheChosenVideosOnly() {
        val days = DayPicks()
        days.startImport(listOf(read(1, mar4), read(2, mar4), read(3, mar5)))
        days.confirmImport()
        // A day still waits for its choice: nothing is saved and the preview stays open.
        assertTrue(days.picks.isEmpty())
        assertNotNull(days.importing)

        days.chooseForImport(mar4, DayChoice.Take("content://v/2"))
        days.confirmImport()
        assertNull(days.importing)
        assertEquals(mapOf(mar4 to "content://v/2", mar5 to "content://v/3"), days.picks.mapValues { it.value.videoUri })
        assertEquals(SecondWindow(0), days.picks[mar4]?.window)
    }

    @Test
    fun keepingOrReplacingAFilledDayIsWhatConfirmSaves() {
        val kept = filledDays()
        kept.startImport(listOf(read(1, mar4)))
        kept.chooseForImport(mar4, DayChoice.KeepExisting)
        kept.confirmImport()
        assertEquals(DayPick("content://old", SecondWindow(3_000), thumbB), kept.picks[mar4])

        val replaced = filledDays()
        replaced.startImport(listOf(read(1, mar4, thumb = thumbA)))
        replaced.chooseForImport(mar4, DayChoice.Take("content://v/1"))
        replaced.confirmImport()
        assertEquals(DayPick("content://v/1", SecondWindow(0), thumbA), replaced.picks[mar4])
    }
}
