package io.github.benomite.secondes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoInfoTest {
    @Test
    fun readsTheLengthAndSize() {
        assertEquals(VideoInfo(12_345, 1920, 1080), videoInfoOf("12345", "1920", "1080", "0"))
    }

    @Test
    fun aTurnedVideoSwapsItsWidthAndHeight() {
        assertEquals(VideoInfo(5_000, 1080, 1920), videoInfoOf("5000", "1920", "1080", "90"))
        assertEquals(VideoInfo(5_000, 1080, 1920), videoInfoOf("5000", "1920", "1080", "270"))
        assertEquals(VideoInfo(5_000, 1920, 1080), videoInfoOf("5000", "1920", "1080", "180"))
    }

    @Test
    fun noRotationTagMeansNotTurned() {
        assertEquals(VideoInfo(5_000, 1920, 1080), videoInfoOf("5000", "1920", "1080", null))
    }

    @Test
    fun aMissingOrUnreadableLengthOrSizeGivesNothing() {
        assertNull(videoInfoOf(null, "1920", "1080", "0"))
        assertNull(videoInfoOf("abc", "1920", "1080", "0"))
        assertNull(videoInfoOf("0", "1920", "1080", "0"))
        assertNull(videoInfoOf("5000", null, "1080", "0"))
        assertNull(videoInfoOf("5000", "1920", "0", "0"))
    }
}
