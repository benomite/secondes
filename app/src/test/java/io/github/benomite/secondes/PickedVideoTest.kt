package io.github.benomite.secondes

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PickedVideoTest {
    @Test
    fun mapsOneVideoToItsUriAndCaptureDate() {
        val result = toPickedVideos(listOf(PickerItem("content://media/picker/1", 1_700_000_000_000)))

        assertEquals(
            listOf(PickedVideo("content://media/picker/1", Instant.ofEpochMilli(1_700_000_000_000))),
            result,
        )
    }

    @Test
    fun keepsEverySelectedVideoInOrder() {
        val result = toPickedVideos(
            listOf(
                PickerItem("content://media/picker/2", 2_000),
                PickerItem("content://media/picker/1", 1_000),
                PickerItem("content://media/picker/3", 3_000),
            ),
        )

        assertEquals(
            listOf("content://media/picker/2", "content://media/picker/1", "content://media/picker/3"),
            result.map { it.uri },
        )
        assertEquals(listOf(2_000L, 1_000L, 3_000L), result.map { it.captureDate!!.toEpochMilli() })
    }

    @Test
    fun givesANullDateWhenThePickerHasNone() {
        val result = toPickedVideos(
            listOf(PickerItem("content://a", null), PickerItem("content://b", 0), PickerItem("content://c", 5_000)),
        )

        assertNull(result[0].captureDate)
        assertNull(result[1].captureDate)
        assertEquals(Instant.ofEpochMilli(5_000), result[2].captureDate)
    }

    @Test
    fun isEmptyWhenNothingIsSelected() {
        assertEquals(emptyList<PickedVideo>(), toPickedVideos(emptyList()))
    }
}
