package fr.bobl.secondes.export

import java.io.File
import java.io.FileNotFoundException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** One second of the diary: the day it belongs to and the local video file holding it. */
data class DatedClip(val date: LocalDate, val file: File)

/** A clip as it goes into the film: its file, and the date text drawn on it, if any. */
data class PlannedClip(val file: File, val label: String?)

/** Length of each clip in the film, in milliseconds. */
const val CLIP_DURATION_MS = 1_000L

val defaultDateFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

/**
 * Throws unless the film of [clips] can be made: at least one clip, and every clip file present.
 * A missing file is an error, not an empty day: the export stops rather than drop it silently.
 */
fun checkExportable(clips: List<DatedClip>) {
    require(clips.isNotEmpty()) { "No clip to export" }
    clips.firstOrNull { !it.file.isFile }?.let {
        throw FileNotFoundException("Clip of ${it.date} not found: ${it.file}")
    }
}

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
        PlannedClip(clip.file, if (dateOverlay) formatter.format(clip.date) else null)
    }
