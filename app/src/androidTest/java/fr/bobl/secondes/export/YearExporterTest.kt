@file:SuppressLint("UnsafeOptInUsageError")

package fr.bobl.secondes.export

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.LocalDate
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs the real export on a device: needs a hardware or emulated video encoder. */
@RunWith(AndroidJUnit4::class)
class YearExporterTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir = File(context.cacheDir, "year-exporter-test")
    private lateinit var source: File

    @Before
    fun makeASourceClip() = runBlocking {
        dir.deleteRecursively()
        dir.mkdirs()
        source = sourceClip()
    }

    @Test
    fun theFilmLastsOneSecondPerClipIsVerticalAndHasSound() = runBlocking {
        val clips = listOf(
            DatedClip(LocalDate.of(2026, 3, 2), source),
            DatedClip(LocalDate.of(2026, 1, 1), source),
            DatedClip(LocalDate.of(2026, 1, 5), source),
        )
        val output = File(dir, "film.mp4")

        YearExporter(context).export(clips, dateOverlay = false, output)

        val retriever = MediaMetadataRetriever().apply { setDataSource(output.path) }
        try {
            val durationMs = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            assertEquals(3 * CLIP_DURATION_MS.toDouble(), durationMs.toDouble(), FRAME_MS)
            val width = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val height = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            assertTrue("$width x $height is not vertical", height > width)
        } finally {
            retriever.release()
        }
        assertTrue("no audio track", hasAudioTrack(output))
    }

    @Test
    fun theDateIsDrawnOnlyWithTheOverlayOn() = runBlocking {
        val clips = listOf(DatedClip(LocalDate.of(2026, 1, 1), source))
        val withDate = File(dir, "with-date.mp4")
        val withoutDate = File(dir, "without-date.mp4")

        YearExporter(context).export(clips, dateOverlay = true, withDate)
        YearExporter(context).export(clips, dateOverlay = false, withoutDate)

        assertTrue("no text drawn with the overlay on", brightPixelsAtTheBottom(withDate) > 100)
        assertEquals("text drawn with the overlay off", 0, brightPixelsAtTheBottom(withoutDate))
    }

    /** A 1.5 s vertical clip, plain dark gray, with a (silent) audio track. */
    private suspend fun sourceClip(): File {
        val image = File(dir, "gray.png")
        val bitmap = Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(60, 60, 60))
        image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val item = EditedMediaItem.Builder(
            MediaItem.Builder().setUri(Uri.fromFile(image)).setImageDurationMs(1_500).build(),
        ).setFrameRate(30).build()
        val composition = Composition.Builder(EditedMediaItemSequence.Builder(item).build())
            .experimentalSetForceAudioTrack(true)
            .build()
        val clip = File(dir, "source.mp4")
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<ExportResult> { continuation ->
                Transformer.Builder(context)
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
                    .start(composition, clip.path)
            }
        }
        return clip
    }

    private fun MediaMetadataRetriever.metadata(key: Int): Long =
        checkNotNull(extractMetadata(key)) { "no metadata $key" }.toLong()

    private fun hasAudioTrack(file: File): Boolean {
        val extractor = MediaExtractor().apply { setDataSource(file.path) }
        try {
            return (0 until extractor.trackCount).any {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")
            }
        } finally {
            extractor.release()
        }
    }

    /** Pixels much brighter than the gray source in the bottom quarter, where the date goes. */
    private fun brightPixelsAtTheBottom(file: File): Int {
        val retriever = MediaMetadataRetriever().apply { setDataSource(file.path) }
        val frame = try {
            checkNotNull(retriever.getFrameAtTime(500_000, MediaMetadataRetriever.OPTION_CLOSEST))
        } finally {
            retriever.release()
        }
        var bright = 0
        for (y in frame.height * 3 / 4 until frame.height) {
            for (x in 0 until frame.width) {
                val pixel = frame.getPixel(x, y)
                if (Color.red(pixel) > 200 && Color.green(pixel) > 200 && Color.blue(pixel) > 200) bright++
            }
        }
        return bright
    }

    private companion object {
        const val FRAME_MS = 1_000.0 / 30
    }
}
