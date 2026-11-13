package io.github.benomite.secondes

import androidx.compose.ui.graphics.ImageBitmap
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** A selected video ready to be filed: its [position] in the selection (from 1), the day it was shot, its first second. */
data class ImportVideo(
    val position: Int,
    val uri: String,
    val date: LocalDate,
    val window: SecondWindow,
    val thumbnail: ImageBitmap,
)

enum class RefusalReason { NOT_A_VIDEO, UNREADABLE, NO_SHOOTING_DATE }

/** A selected item that is not filed, and why. */
data class RefusedItem(val position: Int, val reason: RefusalReason)

/** What the user decided for a day of the import. */
sealed interface DayChoice {
    /** The day keeps the clip it already had. */
    data object KeepExisting : DayChoice

    /** The day takes the selected video at [uri]. */
    data class Take(val uri: String) : DayChoice
}

/**
 * An import shown on the calendar before anything is saved. [filled] are the days that already have a clip.
 * A day that several selected videos share, or that already has a clip, waits for the user's [choices];
 * a day with a single selected video and no clip takes it.
 */
data class ImportPlan(
    val videos: List<ImportVideo>,
    val filled: Set<LocalDate>,
    val refused: List<RefusedItem> = emptyList(),
    val choices: Map<LocalDate, DayChoice> = emptyMap(),
) {
    val byDay: Map<LocalDate, List<ImportVideo>> = videos.groupBy { it.date }

    fun needsChoice(date: LocalDate): Boolean = (byDay[date]?.size ?: 0) > 1 || (date in filled && date in byDay)

    /** The day's choice; null while the user has not made one it needs. */
    fun choiceOf(date: LocalDate): DayChoice? {
        val onDay = byDay[date] ?: return null
        return choices[date] ?: if (needsChoice(date)) null else DayChoice.Take(onDay.single().uri)
    }

    /** The days that need a choice, in calendar order. */
    val choiceDays: List<LocalDate> = byDay.keys.filter(::needsChoice).sorted()

    val isResolved: Boolean get() = choiceDays.all { choiceOf(it) != null }

    /** Records [choice] for [date]; a choice that is not one of the day's options changes nothing. */
    fun choose(date: LocalDate, choice: DayChoice): ImportPlan {
        val onDay = byDay[date] ?: return this
        val valid = when (choice) {
            DayChoice.KeepExisting -> date in filled
            is DayChoice.Take -> onDay.any { it.uri == choice.uri }
        }
        return if (valid) copy(choices = choices + (date to choice)) else this
    }

    /** The video each day takes once confirmed; empty while a choice is missing. */
    fun toSave(): Map<LocalDate, ImportVideo> {
        if (!isResolved) return emptyMap()
        return byDay.mapNotNull { (date, onDay) ->
            val take = choiceOf(date) as? DayChoice.Take ?: return@mapNotNull null
            onDay.first { it.uri == take.uri }.let { date to it }
        }.toMap()
    }

    /** The video shown on [date] during the preview: the chosen one, else the first selected; null when the day keeps its clip. */
    fun shownOn(date: LocalDate): ImportVideo? {
        val onDay = byDay[date] ?: return null
        return when (val choice = choiceOf(date)) {
            DayChoice.KeepExisting -> null
            is DayChoice.Take -> onDay.first { it.uri == choice.uri }
            null -> if (date in filled) null else onDay.first()
        }
    }
}

/** Whether a MIME type is a video's. An unknown type is not refused here: reading the video decides. */
fun isNotAVideo(mimeType: String?): Boolean = mimeType != null && !mimeType.startsWith("video/")

private val METADATA_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

/**
 * The creation date written in a video's metadata (MediaMetadataRetriever's METADATA_KEY_DATE, UTC,
 * e.g. "20260304T183012.000Z"). Null when missing, unreadable, or one of the empty dates encoders
 * write (1904, 1970).
 */
fun parseMetadataDate(raw: String?): Instant? {
    val text = raw?.trim()?.takeIf { it.length >= 15 } ?: return null
    val instant = try {
        LocalDateTime.parse(text.substring(0, 15), METADATA_DATE).toInstant(ZoneOffset.UTC)
    } catch (e: DateTimeParseException) {
        return null
    }
    return instant.takeIf { it.atOffset(ZoneOffset.UTC).year > 1970 }
}

/**
 * The day a video was shot, in [zone]: from its metadata creation date, else from the capture date the
 * picker gave. Null when neither says it: the import time is never used.
 */
fun shootingDay(metadataDate: String?, captureDate: Instant?, zone: ZoneId): LocalDate? =
    (parseMetadataDate(metadataDate) ?: captureDate)?.atZone(zone)?.toLocalDate()

/** What is read from one selected item before the preview. */
data class ImportRead(
    val position: Int,
    val uri: String,
    val mimeType: String?,
    val video: VideoInfo?,
    val shotOn: LocalDate?,
    val thumbnail: ImageBitmap?,
)

/** Sorts the read items into videos to preview and refused items; a missing thumbnail leaves a black one. */
fun planImport(items: List<ImportRead>, filled: Set<LocalDate>): ImportPlan {
    val videos = mutableListOf<ImportVideo>()
    val refused = mutableListOf<RefusedItem>()
    for (item in items) {
        val reason = when {
            isNotAVideo(item.mimeType) -> RefusalReason.NOT_A_VIDEO
            item.video == null -> RefusalReason.UNREADABLE
            item.shotOn == null -> RefusalReason.NO_SHOOTING_DATE
            else -> null
        }
        if (reason != null) {
            refused += RefusedItem(item.position, reason)
        } else {
            videos += ImportVideo(
                item.position,
                item.uri,
                item.shotOn!!,
                secondWindowAt(0, item.video!!.durationMs),
                item.thumbnail ?: ImageBitmap(1, 1),
            )
        }
    }
    return ImportPlan(videos, filled, refused)
}
