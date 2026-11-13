package io.github.benomite.secondes

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import java.util.Locale
import kotlinx.coroutines.delay

const val VERTICAL_FRAME_TAG = "vertical-frame"
const val FRAMED_VIDEO_TAG = "framed-video"
const val SECOND_SLIDER_TAG = "second-slider"
const val SECOND_LABEL_TAG = "second-label"
const val SHORT_VIDEO_NOTE_TAG = "short-video-note"
const val CONFIRM_SECOND_TAG = "confirm-second"
const val CANCEL_SECOND_TAG = "cancel-second"

/** While the slider moves, the loop waits this long for it to settle before it restarts on the new second. */
private const val LOOP_RESTART_DELAY_MS = 120L

/**
 * Chooses the one-second window of [video]: a slider over the video, the chosen second playing in
 * a loop in the vertical frame, and a confirm button. Opens on [initialWindow] when the day already
 * has a second. [player] plays a window in a loop, sized to the space it is given.
 */
@Composable
fun SecondPickerScreen(
    video: VideoInfo,
    initialWindow: SecondWindow?,
    onConfirm: (SecondWindow) -> Unit,
    onCancel: () -> Unit,
    player: @Composable (SecondWindow, Modifier) -> Unit,
) {
    val duration = video.durationMs
    var window by remember(video, initialWindow) {
        mutableStateOf(secondWindowAt(initialWindow?.startMs ?: 0, duration))
    }
    val short = isShorterThanOneSecond(duration)
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Choose the second", style = MaterialTheme.typography.titleMedium)
        VerticalFrame(video, Modifier.weight(1f)) { player(window, Modifier.fillMaxSize()) }
        Text(
            "${seconds(window.startMs)} – ${seconds(window.endMs)}",
            modifier = Modifier.testTag(SECOND_LABEL_TAG),
        )
        if (short) {
            Text(
                "This video is shorter than one second: the day will last ${seconds(duration)}.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag(SHORT_VIDEO_NOTE_TAG),
            )
        }
        Slider(
            value = sliderFraction(window, duration),
            onValueChange = { window = secondWindowAtFraction(it, duration) },
            enabled = !short,
            modifier = Modifier.fillMaxWidth().testTag(SECOND_SLIDER_TAG),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onCancel, modifier = Modifier.testTag(CANCEL_SECOND_TAG)) { Text("Cancel") }
            Button(onClick = { onConfirm(window) }, modifier = Modifier.testTag(CONFIRM_SECOND_TAG)) {
                Text("Use this second")
            }
        }
    }
}

/** The vertical frame of the film, with [video] whole inside it and bars around it (see [fitInVerticalFrame]). */
@Composable
private fun VerticalFrame(video: VideoInfo, modifier: Modifier, content: @Composable () -> Unit) {
    val fit = fitInVerticalFrame(video.width, video.height)
    Box(
        modifier = modifier
            .aspectRatio(VERTICAL_FRAME_ASPECT, matchHeightConstraintsFirst = true)
            .background(Color.Black)
            .testTag(VERTICAL_FRAME_TAG),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(fit.widthFraction)
                .fillMaxHeight(fit.heightFraction)
                .testTag(FRAMED_VIDEO_TAG),
        ) { content() }
    }
}

private fun seconds(ms: Long) = String.format(Locale.ROOT, "%.1f s", ms / 1_000.0)

/**
 * Plays [window] of the video at [videoUri] in a loop, with its sound, filling [modifier]'s space.
 * Restarts on the new window when it changes; pauses while the app is in the background.
 */
@OptIn(UnstableApi::class)
@Composable
fun LoopingSecondPlayer(videoUri: String, window: SecondWindow, modifier: Modifier) {
    val context = LocalContext.current
    val exoPlayer = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(exoPlayer, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> exoPlayer.pause()
                Lifecycle.Event.ON_START -> exoPlayer.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }
    LaunchedEffect(exoPlayer, window) {
        delay(LOOP_RESTART_DELAY_MS)
        val clip = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(window.startMs)
            .setEndPositionMs(window.endMs)
            .build()
        exoPlayer.setMediaItem(MediaItem.Builder().setUri(videoUri).setClippingConfiguration(clip).build())
        exoPlayer.prepare()
    }
    AndroidView(
        factory = {
            PlayerView(it).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                useController = false
                // The frame around it is already sized to the video: fill it, no second fit, no crop.
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
            }
        },
        update = { it.player = exoPlayer },
        modifier = modifier,
    )
}
