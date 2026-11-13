// Transformer and its effects are @UnstableApi in media3.
@file:SuppressLint("UnsafeOptInUsageError")

package fr.bobl.secondes.export

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableString
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.Presentation
import androidx.media3.effect.TextOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Size of the film: vertical, 9:16. Each clip is scaled to fit in it. */
const val FILM_WIDTH = 1080
const val FILM_HEIGHT = 1920

private const val DATE_TEXT_SIZE_PX = 64

/**
 * Turns the dated clips of a year into one vertical film with sound: the clips one after the
 * other in date order, one second each, days without a clip skipped.
 */
class YearExporter(private val context: Context) {

    /**
     * Writes the film of [clips] to [output], replacing it. With [dateOverlay] on, each clip shows
     * its date. Throws when there is no clip, when a clip file is missing, or when the export fails;
     * [output] is then removed.
     */
    suspend fun export(clips: List<DatedClip>, dateOverlay: Boolean, output: File) {
        checkExportable(clips)
        val composition = yearComposition(planYearFilm(clips, dateOverlay))
        output.parentFile?.mkdirs()
        output.delete()
        try {
            withContext(Dispatchers.Main) { transform(composition, output) }
        } catch (e: Throwable) {
            output.delete()
            throw e
        }
    }

    // A Transformer is driven from the thread that builds it; Dispatchers.Main has a Looper.
    private suspend fun transform(composition: Composition, output: File): ExportResult =
        suspendCancellableCoroutine { continuation ->
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        continuation.resume(exportResult)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException,
                    ) {
                        continuation.resumeWithException(exportException)
                    }
                })
                .build()
            val handler = Handler(Looper.myLooper()!!)
            continuation.invokeOnCancellation { handler.post { transformer.cancel() } }
            transformer.start(composition, output.path)
        }
}

/**
 * The composition of the film: one sequence of [plan] clips, each cut to one second and scaled
 * into the vertical frame, with an audio track even if some clips are silent.
 */
internal fun yearComposition(plan: List<PlannedClip>): Composition {
    val items = plan.map { clip ->
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.fromFile(clip.file))
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder().setEndPositionMs(CLIP_DURATION_MS).build(),
            )
            .build()
        val videoEffects = buildList<Effect> {
            add(Presentation.createForWidthAndHeight(FILM_WIDTH, FILM_HEIGHT, Presentation.LAYOUT_SCALE_TO_FIT))
            clip.label?.let { add(dateOverlay(it)) }
        }
        EditedMediaItem.Builder(mediaItem).setEffects(Effects(emptyList(), videoEffects)).build()
    }
    return Composition.Builder(EditedMediaItemSequence.Builder(items).build())
        .experimentalSetForceAudioTrack(true)
        .build()
}

/** The date drawn at the bottom of a clip, white on a dark band. */
private fun dateOverlay(label: String): OverlayEffect {
    val text = SpannableString(label).apply {
        setSpan(ForegroundColorSpan(Color.WHITE), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        setSpan(BackgroundColorSpan(Color.argb(128, 0, 0, 0)), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        setSpan(AbsoluteSizeSpan(DATE_TEXT_SIZE_PX), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    val settings = OverlaySettings.Builder()
        .setOverlayFrameAnchor(0f, -1f)
        .setBackgroundFrameAnchor(0f, -0.85f)
        .build()
    return OverlayEffect(listOf(TextOverlay.createStaticTextOverlay(text, settings)))
}
