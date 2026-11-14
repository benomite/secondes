package io.github.benomite.secondes

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** What the second picker needs to know of a video: its length and its size as it is shown (rotation applied). */
data class VideoInfo(val durationMs: Long, val width: Int, val height: Int)

/**
 * Builds a [VideoInfo] from the raw metadata strings of a video. Null when the duration or the size
 * is missing, unreadable or not positive. A video turned by 90 or 270 degrees swaps its width and height.
 */
fun videoInfoOf(durationMs: String?, width: String?, height: String?, rotationDegrees: String?): VideoInfo? {
    val duration = durationMs?.toLongOrNull()?.takeIf { it > 0 } ?: return null
    val w = width?.toIntOrNull()?.takeIf { it > 0 } ?: return null
    val h = height?.toIntOrNull()?.takeIf { it > 0 } ?: return null
    val turned = (rotationDegrees?.toIntOrNull() ?: 0) % 180 != 0
    return if (turned) VideoInfo(duration, h, w) else VideoInfo(duration, w, h)
}

/** Reads the video at [uri]; null when it cannot be opened or its metadata does not say its length and size. */
fun readVideoInfo(context: Context, uri: Uri): VideoInfo? = withRetriever(context, uri) {
    videoInfoOf(
        it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION),
        it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH),
        it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT),
        it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION),
    )
}

/** The creation date in the metadata of the video at [uri], as written there; null when it is absent or unreadable. */
fun readCreationDate(context: Context, uri: Uri): String? = withRetriever(context, uri) {
    it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
}

/** The frame of the video at [uri] closest to [atMs]; null when it cannot be read. */
fun readFrame(context: Context, uri: Uri, atMs: Long): ImageBitmap? = withRetriever(context, uri) {
    it.getFrameAtTime(atMs * 1_000, MediaMetadataRetriever.OPTION_CLOSEST)?.asImageBitmap()
}

private fun <T> withRetriever(context: Context, uri: Uri, read: (MediaMetadataRetriever) -> T?): T? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        read(retriever)
    } catch (e: Exception) {
        null
    } finally {
        retriever.release()
    }
}
