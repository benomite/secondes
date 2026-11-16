package fr.bobl.secondes.export

import io.github.benomite.secondes.JournalDay
import io.github.benomite.secondes.SecondWindow
import io.github.benomite.secondes.firstName
import io.github.benomite.secondes.memberJoinOrder
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

/** A member's share of the film: their first name, their place in the journal's join order (for their color) and their seconds. */
data class MemberShare(val name: String, val joinOrder: Int, val seconds: Int)

/** What the export screen tells about a year before the export: its seconds, each member's share, the empty days skipped. */
data class YearStats(val seconds: Int, val shares: List<MemberShare>, val emptyDays: Int)

/**
 * The stats of [year] in the journal's [days]. The empty days are those of the year with no clip, up to [today] for
 * the current year (the days to come are not empty yet); the film skips them. Members are in join order.
 */
fun yearStats(days: Collection<JournalDay>, year: Int, today: LocalDate): YearStats {
    val inYear = days.filter { it.date.year == year }
    val order = memberJoinOrder(days.map { it.author to it.date })
    val shares = inYear.groupBy { it.author }
        .map { (author, theirs) -> MemberShare(firstName(author), order.getValue(author), theirs.size) }
        .sortedBy { it.joinOrder }
    val last = minOf(LocalDate.of(year, 12, 31), today)
    val elapsed = if (last.year == year) last.dayOfYear else 0
    return YearStats(inYear.size, shares, (elapsed - inYear.size).coerceAtLeast(0))
}

/** The length of a film of [seconds] seconds, as "4 min 49", or "45 s" under a minute. */
fun filmLength(seconds: Int): String =
    if (seconds < 60) "$seconds s" else "${seconds / 60} min ${(seconds % 60).toString().padStart(2, '0')}"

/** The day the export has reached out of [total] clips, given the [percent] of the film built: 0 before the start, [total] at 100. */
fun dayReached(percent: Int, total: Int): Int = ((percent.coerceIn(0, 100) * total + 99) / 100).coerceIn(0, total)
