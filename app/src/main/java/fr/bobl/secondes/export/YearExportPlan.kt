package fr.bobl.secondes.export

import io.github.benomite.secondes.JournalDay
import io.github.benomite.secondes.SecondWindow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** One second of the diary: the day it belongs to, the video holding it and the second chosen in it. */
data class DatedClip(val date: LocalDate, val videoUri: String, val window: SecondWindow)

/** A clip as it goes into the film: its video, its second, and the date text drawn on it, if any. */
data class PlannedClip(val videoUri: String, val window: SecondWindow, val label: String?)

/** Length of each clip in the film, in milliseconds. */
const val CLIP_DURATION_MS = 1_000L

val defaultDateFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

/**
 * The clips of the journal's days that fall in [year], in date order. A day without a clip is not in the
 * journal, so it is simply absent: the film goes from one filled day to the next.
 */
fun yearClips(days: Collection<JournalDay>, year: Int): List<DatedClip> =
    days.filter { it.date.year == year }
        .sortedBy { it.date }
        .map { DatedClip(it.date, it.clip.videoUri, it.clip.window) }

/** The years the export screen offers, latest first: [currentYear], even empty, and every year the journal has a day in. */
fun exportYears(days: Collection<JournalDay>, currentYear: Int): List<Int> =
    (days.map { it.date.year } + currentYear).distinct().sortedDescending()

/** The clips of [clips] split into those whose video [isReadable] on this phone, and the others. */
fun splitReadable(clips: List<DatedClip>, isReadable: (String) -> Boolean): Pair<List<DatedClip>, List<DatedClip>> =
    clips.partition { isReadable(it.videoUri) }

/**
 * The clips of the film, in date order whatever the order of [clips]; days without a clip are
 * simply absent. Clips of the same day keep their input order. With [dateOverlay] off, no clip
 * carries a label, so no text is drawn.
 */
fun planYearFilm(
    clips: List<DatedClip>,
    dateOverlay: Boolean,
    formatter: DateTimeFormatter = defaultDateFormatter,
): List<PlannedClip> =
    clips.sortedBy { it.date }.map { clip ->
        PlannedClip(clip.videoUri, clip.window, if (dateOverlay) formatter.format(clip.date) else null)
    }
