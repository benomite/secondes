package io.github.benomite.secondes

import java.time.Instant

/** A video returned by the Photos Picker: its content URI and, when the picker exposes it, its capture date. */
data class PickedVideo(val uri: String, val captureDate: Instant?)

/** What is read from the picker for one selected item: its URI and the raw DATE_TAKEN value (epoch ms), if any. */
data class PickerItem(val uri: String, val dateTakenMillis: Long?)

/** Maps the picker result to records, in selection order. A missing or non-positive date gives a null captureDate. */
fun toPickedVideos(items: List<PickerItem>): List<PickedVideo> =
    items.map { item ->
        PickedVideo(
            uri = item.uri,
            captureDate = item.dateTakenMillis?.takeIf { it > 0 }?.let(Instant::ofEpochMilli),
        )
    }
