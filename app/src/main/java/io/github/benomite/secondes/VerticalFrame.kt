package io.github.benomite.secondes

import fr.bobl.secondes.export.FILM_HEIGHT
import fr.bobl.secondes.export.FILM_WIDTH

/** Width over height of the film's vertical frame, the one the year export writes (9:16). */
val VERTICAL_FRAME_ASPECT = FILM_WIDTH.toFloat() / FILM_HEIGHT

/** Size of a video inside the vertical frame, as fractions of the frame's width and height. */
data class FrameFit(val widthFraction: Float, val heightFraction: Float)

/**
 * How a [videoWidth] x [videoHeight] video sits in the vertical frame, as the export scales it
 * (Presentation.LAYOUT_SCALE_TO_FIT): whole, never cropped, as large as fits, centred. The rest
 * of the frame is bars (above and below a horizontal video, left and right of a video narrower than 9:16).
 */
fun fitInVerticalFrame(videoWidth: Int, videoHeight: Int): FrameFit {
    val videoAspect = videoWidth.toFloat() / videoHeight
    return if (videoAspect >= VERTICAL_FRAME_ASPECT) {
        FrameFit(1f, VERTICAL_FRAME_ASPECT / videoAspect)
    } else {
        FrameFit(videoAspect / VERTICAL_FRAME_ASPECT, 1f)
    }
}
