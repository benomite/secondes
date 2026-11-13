package io.github.benomite.secondes

/** Width over height of the film's vertical frame (9:16). The preview and the export both use it. */
const val VERTICAL_FRAME_ASPECT = 9f / 16f

/** Size of a video inside the vertical frame, as fractions of the frame's width and height. */
data class FrameFit(val widthFraction: Float, val heightFraction: Float)

/**
 * How a [videoWidth] x [videoHeight] video sits in the vertical frame: whole, never cropped, as
 * large as fits, centred. The rest of the frame is bars (above and below a horizontal video,
 * left and right of a video narrower than 9:16).
 */
fun fitInVerticalFrame(videoWidth: Int, videoHeight: Int): FrameFit {
    val videoAspect = videoWidth.toFloat() / videoHeight
    return if (videoAspect >= VERTICAL_FRAME_ASPECT) {
        FrameFit(1f, VERTICAL_FRAME_ASPECT / videoAspect)
    } else {
        FrameFit(videoAspect / VERTICAL_FRAME_ASPECT, 1f)
    }
}
