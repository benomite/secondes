package io.github.benomite.secondes

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val today = LocalDate.now()
                    var videos by remember { mutableStateOf<List<PickedVideo>?>(null) }
                    val picker = rememberLauncherForActivityResult(
                        ActivityResultContracts.PickMultipleVisualMedia(),
                    ) { uris -> videos = toPickedVideos(uris.map { readPickerItem(this@MainActivity, it) }) }

                    val days = remember { DayPicks() }
                    val scope = rememberCoroutineScope()
                    var unreadable by remember { mutableStateOf(false) }

                    // Opens the second picker on a day once the video is read; an unreadable video opens nothing and says so.
                    fun pickSecond(date: LocalDate, videoUri: String) {
                        scope.launch {
                            val video = withContext(Dispatchers.IO) { readVideoInfo(this@MainActivity, Uri.parse(videoUri)) }
                            unreadable = video == null
                            if (video != null) days.open(date, videoUri, video)
                        }
                    }

                    var dayToFill by rememberSaveable { mutableStateOf<LocalDate?>(null) }
                    val pickOne = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                        val date = dayToFill
                        dayToFill = null
                        if (date != null && uri != null) pickSecond(date, uri.toString())
                    }

                    val picking = days.picking
                    if (picking != null) {
                        BackHandler { days.cancel() }
                        SecondPickerScreen(
                            video = picking.video,
                            initialWindow = picking.window,
                            onConfirm = { window ->
                                scope.launch {
                                    val frame = withContext(Dispatchers.IO) {
                                        readFrame(this@MainActivity, Uri.parse(picking.videoUri), window.startMs)
                                    }
                                    // A frame that cannot be read leaves a black thumbnail: the day is filled all the same.
                                    days.confirm(window, frame ?: ImageBitmap(1, 1))
                                }
                            },
                            onCancel = days::cancel,
                            player = { window, modifier -> LoopingSecondPlayer(picking.videoUri, window, modifier) },
                        )
                    } else {
                        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                            Column(modifier = Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState())) {
                                Button(onClick = {
                                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                                }) { Text(stringResource(R.string.pick_videos)) }
                                videos?.let { list ->
                                    Text(stringResource(R.string.picked_count, list.size))
                                    list.forEach { Text("${it.uri}\n${it.captureDate ?: stringResource(R.string.no_date)}") }
                                }
                                if (unreadable) Text(stringResource(R.string.video_unreadable))
                            }
                            // Days are kept in memory only: they are lost when the app is closed.
                            JournalCalendarScreen(
                                year = today.year,
                                clips = days.clips(stringResource(R.string.me)),
                                firstDay = today,
                                today = today,
                                // A filled day reopens its video, on its second, to choose another one.
                                onPlay = { clip -> days.picks[clip.date]?.let { pickSecond(clip.date, it.videoUri) } },
                                onFill = { date ->
                                    dayToFill = date
                                    pickOne.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Reads DATE_TAKEN from the picker URI; a picker that does not expose it, or a failing query, gives no date. */
private fun readPickerItem(context: Context, uri: Uri): PickerItem {
    val date = try {
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATE_TAKEN), null, null, null)?.use {
            if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null
        }
    } catch (e: Exception) {
        null
    }
    return PickerItem(uri.toString(), date)
}
