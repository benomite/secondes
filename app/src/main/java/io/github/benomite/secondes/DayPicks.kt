package io.github.benomite.secondes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import java.time.LocalDate

/** A filled day: the video it was taken from, the second chosen in it, and that second's first frame. */
data class DayPick(val videoUri: String, val window: SecondWindow, val thumbnail: ImageBitmap)

/** The second picker open on [date], over [video]; [window] is the second already chosen, if any. */
data class SecondPicking(val date: LocalDate, val videoUri: String, val video: VideoInfo, val window: SecondWindow?)

/**
 * The seconds picked on this phone, with their thumbnails, the second picker, when one is open, and the import
 * being previewed. Kept in memory only: the days themselves are kept by the journal ([JournalSync]).
 */
class DayPicks {
    var picks by mutableStateOf(mapOf<LocalDate, DayPick>())
        private set
    var picking by mutableStateOf<SecondPicking?>(null)
        private set
    var importing by mutableStateOf<ImportPlan?>(null)
        private set

    /** Opens the picker on [date] over a video; a day already filled from that video reopens on its second. */
    fun open(date: LocalDate, videoUri: String, video: VideoInfo) {
        val chosen = picks[date]?.takeIf { it.videoUri == videoUri }?.window
        picking = SecondPicking(date, videoUri, video, chosen)
    }

    /** Fills the open day with [window], replacing any second it had, and closes the picker. */
    fun confirm(window: SecondWindow, thumbnail: ImageBitmap) {
        val open = picking ?: return
        picks = picks + (open.date to DayPick(open.videoUri, window, thumbnail))
        picking = null
    }

    fun cancel() {
        picking = null
    }

    /** Shows the selected videos on the calendar, nothing saved yet; [read] in selection order, [filled] the journal's days. */
    fun startImport(read: List<ImportRead>, filled: Set<LocalDate>) {
        importing = planImport(read, filled)
    }

    fun chooseForImport(date: LocalDate, choice: DayChoice) {
        importing = importing?.choose(date, choice)
    }

    /**
     * Files the previewed videos on their days in [journal], signed [author], and ends the preview. False when a
     * day could not be saved on the phone; null, with nothing saved and the preview still open, while a choice is missing.
     */
    fun confirmImport(journal: JournalSync, author: String): Boolean? {
        val plan = importing ?: return null
        if (!plan.isResolved) return null
        var allSaved = true
        plan.toSave().forEach { (date, video) ->
            if (journal.fill(date, SharedClip(video.uri, video.window), author)) {
                picks = picks + (date to DayPick(video.uri, video.window, video.thumbnail))
            } else {
                allSaved = false
            }
        }
        importing = null
        return allSaved
    }

    /** Ends the preview: the days stay as they were. */
    fun cancelImport() {
        importing = null
    }

    /**
     * The journal's [days] as the calendar shows them ([journalClips]); during an import, with the previewed
     * videos on their days, signed [author].
     */
    fun clips(days: Collection<JournalDay>, author: String): List<Clip> {
        val saved = journalClips(days, picks)
        val plan = importing ?: return saved
        val previewed = plan.byDay.keys.mapNotNull { date ->
            plan.shownOn(date)?.let { Clip(date, author, it.thumbnail) }
        }
        val replaced = previewed.map { it.date }.toSet()
        return saved.filter { it.date !in replaced } + previewed
    }
}

/**
 * The journal's days as the calendar shows them, with their author and whether they are sent. A day shows the
 * thumbnail picked here for that same clip; a clip taken on another phone, or before the app was closed, shows
 * a black one.
 */
fun journalClips(days: Collection<JournalDay>, picks: Map<LocalDate, DayPick>): List<Clip> = days.map { day ->
    val pick = picks[day.date]?.takeIf { it.videoUri == day.clip.videoUri && it.window == day.clip.window }
    Clip(day.date, day.author, pick?.thumbnail ?: ImageBitmap(1, 1), pending = day.pending)
}
