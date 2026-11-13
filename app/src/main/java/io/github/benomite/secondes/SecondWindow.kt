package io.github.benomite.secondes

import fr.bobl.secondes.export.CLIP_DURATION_MS

/**
 * The window chosen inside a video: from [startMs] (included) to [endMs] (excluded). One second
 * long, except in a video shorter than one second, where it is the whole video.
 */
data class SecondWindow(val startMs: Long, val endMs: Long = startMs + CLIP_DURATION_MS)

/** A video shorter than one second holds no full second: its day keeps the whole video. */
fun isShorterThanOneSecond(durationMs: Long): Boolean = durationMs < CLIP_DURATION_MS

/**
 * The window starting as close to [startMs] as a video of [durationMs] allows: never before the
 * start, never past the end. In a video shorter than one second, the whole video.
 */
fun secondWindowAt(startMs: Long, durationMs: Long): SecondWindow {
    if (isShorterThanOneSecond(durationMs)) return wholeVideo(durationMs)
    return SecondWindow(startMs.coerceIn(0, durationMs - CLIP_DURATION_MS))
}

/**
 * The window under a slider at [fraction] (0 = first second, 1 = last second) of a video of
 * [durationMs]. Out-of-range fractions are clamped. In a video shorter than one second, the whole video.
 */
fun secondWindowAtFraction(fraction: Float, durationMs: Long): SecondWindow {
    if (isShorterThanOneSecond(durationMs)) return wholeVideo(durationMs)
    val lastStart = durationMs - CLIP_DURATION_MS
    return SecondWindow(Math.round(fraction.coerceIn(0f, 1f).toDouble() * lastStart))
}

/** Where [window] sits on a slider over a video of [durationMs], the inverse of [secondWindowAtFraction]. */
fun sliderFraction(window: SecondWindow, durationMs: Long): Float {
    val lastStart = durationMs - CLIP_DURATION_MS
    if (lastStart <= 0) return 0f
    return (window.startMs.toFloat() / lastStart).coerceIn(0f, 1f)
}

private fun wholeVideo(durationMs: Long) = SecondWindow(0, durationMs.coerceAtLeast(0))
