// Transformer is @UnstableApi in media3.
@file:SuppressLint("UnsafeOptInUsageError")

package io.github.benomite.secondes

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Cuts a day's second out of the member's video into a standalone MP4 (H.264 and AAC, as the Drive app plays it),
 * with media3's Transformer, as the film of the year is built.
 */
class TransformerSecondCutter(private val context: Context) : SecondCutter {
    override suspend fun cut(videoUri: String, window: SecondWindow, output: File) {
        val item = MediaItem.Builder()
            .setUri(Uri.parse(videoUri))
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(window.startMs)
                    .setEndPositionMs(window.endMs)
                    .build(),
            )
            .build()
        output.delete()
        try {
            // A Transformer is driven from the thread that builds it; Dispatchers.Main has a Looper.
            withContext(Dispatchers.Main) { transform(item, output) }
        } catch (e: CancellationException) {
            output.delete()
            throw e
        } catch (e: IOException) {
            output.delete()
            throw e
        } catch (e: Exception) {
            // A video that cannot be read (deleted, access lost) or cut: the day stays pending, its clip not cut.
            output.delete()
            throw IOException("the second could not be cut from $videoUri", e)
        }
    }

    private suspend fun transform(item: MediaItem, output: File): ExportResult =
        suspendCancellableCoroutine { continuation ->
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        continuation.resume(exportResult)
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        continuation.resumeWithException(IOException("the cut failed", exportException))
                    }
                })
                .build()
            val handler = Handler(Looper.myLooper()!!)
            continuation.invokeOnCancellation { handler.post { transformer.cancel() } }
            transformer.start(item, output.path)
        }
}
