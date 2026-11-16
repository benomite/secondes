package io.github.benomite.secondes

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

const val VERTICAL_FRAME_TAG = "vertical-frame"
const val FRAMED_VIDEO_TAG = "framed-video"
const val SECOND_SLIDER_TAG = "second-slider"
const val SECOND_LABEL_TAG = "second-label"
const val SECOND_DAY_TAG = "second-day"
const val SECOND_STRIP_END_TAG = "second-strip-end"
const val SHORT_VIDEO_NOTE_TAG = "short-video-note"
const val CONFIRM_SECOND_TAG = "confirm-second"
const val CANCEL_SECOND_TAG = "cancel-second"
const val BACK_SECOND_TAG = "back-second"
const val PLAY_TOGGLE_TAG = "play-toggle"
const val SOUND_BADGE_TAG = "sound-badge"

/** How many frames the strip over the whole video shows. */
const val STRIP_FRAME_COUNT = 8

/** While the slider moves, the loop waits this long for it to settle before it restarts on the new second. */
private const val LOOP_RESTART_DELAY_MS = 120L

private val StripHeight = 56.dp
private val StripFrameBorder = 3.dp

/** [date] written as the phone's language writes it, weekday first: "mardi 3 novembre". */
fun secondDayTitle(date: LocalDate, locale: Locale): String =
    date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale))

/**
 * Chooses the one-second window of [video] for the day [date]: the video playing the chosen second in a loop, with
 * its sound, in a polaroid; under it a strip of [stripFrames] of the whole video on which an orange one-second
 * frame slides; a button to keep the second and one to choose [onOtherVideo]. Opens on [initialWindow] when the day
 * already has a second. [author] is written in the polaroid's margin with the day. [player] plays a window in a loop
 * (or not, while paused), sized to the space it is given.
 */
@Composable
fun SecondPickerScreen(
    date: LocalDate,
    author: String?,
    video: VideoInfo,
    initialWindow: SecondWindow?,
    stripFrames: List<ImageBitmap?>,
    onConfirm: (SecondWindow) -> Unit,
    onOtherVideo: () -> Unit,
    onBack: () -> Unit,
    player: @Composable (SecondWindow, Boolean, Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val duration = video.durationMs
    val locale = LocalConfiguration.current.locales[0]
    var window by remember(video, initialWindow) {
        mutableStateOf(secondWindowAt(initialWindow?.startMs ?: 0, duration))
    }
    var playing by remember(video) { mutableStateOf(true) }
    val short = isShorterThanOneSecond(duration)
    val day = secondDayTitle(date, locale)
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack, modifier = Modifier.size(MinTouchTarget).testTag(BACK_SECOND_TAG)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = Palette.Ink)
            }
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(
                    stringResource(R.string.second_title).uppercase(locale),
                    color = Palette.Label,
                    fontFamily = Figtree,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    letterSpacing = 1.5.sp,
                )
                Text(day, style = MaterialTheme.typography.headlineSmall, color = Palette.Ink, modifier = Modifier.testTag(SECOND_DAY_TAG))
            }
        }
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            // The polaroid is as large as the room allows: a square photo, and its margins under it.
            val width = (minOf(maxWidth, maxHeight - PolaroidFrame - PolaroidBottomMargin) * 0.94f).coerceAtLeast(0.dp)
            val caption = author?.let { stringResource(R.string.second_caption, day, it) } ?: day
            Polaroid(index = date.dayOfMonth, modifier = Modifier.width(width), caption = caption) {
                // Not `clickable`: that would merge the frame, the badge and the button into one node.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .pointerInput(Unit) { detectTapGestures { playing = !playing } }
                        .semantics {
                            onClick {
                                playing = !playing
                                true
                            }
                        }
                        .testTag(PLAY_TOGGLE_TAG),
                ) {
                    VerticalFrame(video, Modifier.align(Alignment.Center).fillMaxHeight()) { player(window, playing, Modifier.fillMaxSize()) }
                    if (!playing) PlayButton(Modifier.align(Alignment.Center))
                    if (video.hasAudio) SoundBadge(Modifier.align(Alignment.TopStart).padding(8.dp))
                }
            }
        }
        Text(stringResource(R.string.second_hint), color = Palette.Secondary, style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.second_range, clockTime(window.startMs), clockTime(window.endMs)),
            color = Palette.ToFillText,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.testTag(SECOND_LABEL_TAG),
        )
        if (short) {
            Text(
                stringResource(R.string.second_short_video, seconds(duration)),
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Secondary,
                modifier = Modifier.testTag(SHORT_VIDEO_NOTE_TAG),
            )
        }
        SecondStrip(
            durationMs = duration,
            window = window,
            frames = stripFrames,
            enabled = !short,
            onWindow = { window = it },
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(clockTime(0), color = Palette.MutedText, fontSize = 12.sp)
            Text(clockTime(duration), color = Palette.MutedText, fontSize = 12.sp, modifier = Modifier.testTag(SECOND_STRIP_END_TAG))
        }
        PrimaryButton(
            text = stringResource(R.string.second_confirm),
            onClick = { onConfirm(window) },
            modifier = Modifier.fillMaxWidth().testTag(CONFIRM_SECOND_TAG),
        )
        SecondaryButton(
            text = stringResource(R.string.second_cancel),
            onClick = onOtherVideo,
            modifier = Modifier.fillMaxWidth().testTag(CANCEL_SECOND_TAG),
        )
    }
}

/** The big round play button shown over the paused video. */
@Composable
private fun PlayButton(modifier: Modifier) {
    Box(modifier = modifier.size(56.dp).clip(CircleShape).background(Palette.Card.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.second_play), tint = Palette.Ink, modifier = Modifier.size(32.dp))
    }
}

/** The small "with sound" badge, for a clip that has sound. */
@Composable
private fun SoundBadge(modifier: Modifier) {
    Text(
        stringResource(R.string.second_sound),
        color = Palette.Ink,
        fontFamily = Figtree,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Palette.Card.copy(alpha = 0.85f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .testTag(SOUND_BADGE_TAG),
    )
}

/**
 * A strip of [frames] of the whole video, dimmed except under the orange one-second frame at [window], which slides
 * under the finger (a tap or a drag puts it there) and calls [onWindow]. While [enabled] is false (a video shorter
 * than one second) the frame covers the whole strip and does not move. The strip is also a slider for accessibility.
 */
@Composable
private fun SecondStrip(durationMs: Long, window: SecondWindow, frames: List<ImageBitmap?>, enabled: Boolean, onWindow: (SecondWindow) -> Unit) {
    val description = stringResource(R.string.second_strip)
    val currentWindow by rememberUpdatedState(onWindow)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(StripHeight)
            .clip(RoundedCornerShape(6.dp))
            .semantics {
                contentDescription = description
                if (enabled) {
                    progressBarRangeInfo = ProgressBarRangeInfo(sliderFraction(window, durationMs), 0f..1f)
                    setProgress {
                        currentWindow(secondWindowAtFraction(it, durationMs))
                        true
                    }
                } else {
                    disabled()
                }
            }
            .pointerInput(durationMs, enabled) {
                if (enabled) detectTapGestures { currentWindow(secondWindowAtStripPosition(it.x / size.width, durationMs)) }
            }
            .pointerInput(durationMs, enabled) {
                if (enabled) {
                    detectHorizontalDragGestures(
                        onDragStart = { currentWindow(secondWindowAtStripPosition(it.x / size.width, durationMs)) },
                    ) { change, _ ->
                        change.consume()
                        currentWindow(secondWindowAtStripPosition(change.position.x / size.width, durationMs))
                    }
                }
            }
            .testTag(SECOND_SLIDER_TAG),
    ) {
        Row(Modifier.fillMaxSize()) {
            repeat(STRIP_FRAME_COUNT) { i ->
                val frame = frames.getOrNull(i)
                if (frame != null) {
                    Image(frame, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                } else {
                    Box(Modifier.weight(1f).fillMaxHeight().background(Palette.Muted.copy(alpha = 0.35f)))
                }
            }
        }
        val start = if (durationMs > 0) window.startMs.toFloat() / durationMs else 0f
        val end = if (durationMs > 0) window.endMs.toFloat() / durationMs else 1f
        // Outside the frame, the strip is dimmed.
        Box(
            Modifier.fillMaxSize().drawBehind {
                val dim = Palette.Ink.copy(alpha = 0.55f)
                drawRect(dim, Offset.Zero, Size(size.width * start, size.height))
                drawRect(dim, Offset(size.width * end, 0f), Size(size.width * (1 - end), size.height))
            },
        )
        Box(
            Modifier
                .offset(x = maxWidth * start)
                .width(maxWidth * (end - start))
                .fillMaxHeight()
                .border(StripFrameBorder, Palette.ToFill, RoundedCornerShape(4.dp)),
        )
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

/** [ms] in seconds, with one decimal, written as the phone's language writes it. */
@Composable
private fun seconds(ms: Long) = stringResource(R.string.second_value, ms / 1_000.0)

/**
 * Plays [window] of the video at [videoUri] in a loop, with its sound, filling [modifier]'s space, while [playing];
 * paused otherwise. Restarts on the new window when it changes; pauses while the app is in the background.
 */
@OptIn(UnstableApi::class)
@Composable
fun LoopingSecondPlayer(videoUri: String, window: SecondWindow, playing: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val exoPlayer = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val shouldPlay by rememberUpdatedState(playing)
    DisposableEffect(exoPlayer, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> exoPlayer.pause()
                Lifecycle.Event.ON_START -> if (shouldPlay) exoPlayer.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }
    LaunchedEffect(exoPlayer, playing) { exoPlayer.playWhenReady = playing }
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
