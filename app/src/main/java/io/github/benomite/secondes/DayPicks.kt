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

/** The days filled so far and the second picker, when one is open. Kept in memory only. */
class DayPicks {
    var picks by mutableStateOf(mapOf<LocalDate, DayPick>())
        private set
    var picking by mutableStateOf<SecondPicking?>(null)
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

    /** The filled days as the calendar shows them. */
    fun clips(author: String): List<Clip> = picks.map { (date, pick) -> Clip(date, author, pick.thumbnail) }
}
