package io.github.benomite.secondes

/** Length of a day in the diary: one second of video, in milliseconds. */
const val ONE_SECOND_MS = 1_000L

/** The one-second window chosen inside a video: from [startMs] (included) to [endMs] (excluded). */
data class SecondWindow(val startMs: Long) {
    val endMs: Long get() = startMs + ONE_SECOND_MS
}

/**
 * The window starting as close to [startMs] as a video of [durationMs] allows: never before the
 * start, never past the end. Null when the video is shorter than one second, so holds no window.
 */
fun secondWindowAt(startMs: Long, durationMs: Long): SecondWindow? {
    val lastStart = durationMs - ONE_SECOND_MS
    if (lastStart < 0) return null
    return SecondWindow(startMs.coerceIn(0, lastStart))
}

/**
 * The window under a slider at [fraction] (0 = first second, 1 = last second) of a video of
 * [durationMs]. Out-of-range fractions are clamped. Null when the video is shorter than one second.
 */
fun secondWindowAtFraction(fraction: Float, durationMs: Long): SecondWindow? {
    val lastStart = durationMs - ONE_SECOND_MS
    if (lastStart < 0) return null
    return SecondWindow(Math.round(fraction.coerceIn(0f, 1f).toDouble() * lastStart))
}

/** Where [window] sits on a slider over a video of [durationMs], the inverse of [secondWindowAtFraction]. */
fun sliderFraction(window: SecondWindow, durationMs: Long): Float {
    val lastStart = durationMs - ONE_SECOND_MS
    if (lastStart <= 0) return 0f
    return (window.startMs.toFloat() / lastStart).coerceIn(0f, 1f)
}
