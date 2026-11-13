package io.github.benomite.secondes

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var showSettings by mutableStateOf(false)

    // Held by the activity, not the composition, so the notification's tap can close an open second picker.
    private val days = DayPicks()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BehindReminderWorker.schedule(this)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // The reminder is on by default, so the notification permission is asked at launch.
                    val reminderOn = remember { ReminderPrefs.of(this@MainActivity).settings()?.enabled == true }
                    AskNotificationPermission(reminderOn)

                    val today = LocalDate.now()
                    val scope = rememberCoroutineScope()
                    var readingImport by remember { mutableStateOf(false) }
                    // The selected items are read, then shown on the calendar as a preview; nothing is saved yet.
                    val picker = rememberLauncherForActivityResult(
                        ActivityResultContracts.PickMultipleVisualMedia(),
                    ) { uris ->
                        if (uris.isNotEmpty()) {
                            readingImport = true
                            scope.launch {
                                val zone = ZoneId.systemDefault()
                                val read = withContext(Dispatchers.IO) {
                                    uris.mapIndexed { i, uri -> readForImport(this@MainActivity, i + 1, uri, zone) }
                                }
                                days.startImport(read)
                                readingImport = false
                            }
                        }
                    }

                    var message by remember { mutableStateOf<Int?>(null) }

                    // Opens the second picker on a day once the video is read; a photo or an unreadable video opens nothing and says so.
                    fun pickSecond(date: LocalDate, videoUri: String) {
                        scope.launch {
                            val uri = Uri.parse(videoUri)
                            val notAVideo = withContext(Dispatchers.IO) { isNotAVideo(mimeTypeOf(this@MainActivity, uri)) }
                            val video = if (notAVideo) null else withContext(Dispatchers.IO) { readVideoInfo(this@MainActivity, uri) }
                            message = when {
                                notAVideo -> R.string.not_a_video
                                video == null -> R.string.video_unreadable
                                else -> null
                            }
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
                    val importing = days.importing
                    if (showSettings) {
                        // The days picked so far are held by the activity, so a visit to the settings keeps them.
                        ReminderSettingsScreen(onBack = { showSettings = false })
                    } else if (picking != null) {
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
                            if (importing != null) {
                                BackHandler { days.cancelImport() }
                                Column(modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                                    ImportPreviewPanel(
                                        plan = importing,
                                        existing = days.picks.mapValues { it.value.thumbnail },
                                        onChoose = days::chooseForImport,
                                        onConfirm = days::confirmImport,
                                        onCancel = days::cancelImport,
                                    )
                                }
                            } else {
                                Column(modifier = Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState())) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // The system photo picker, restricted to videos: Google Photos' library shows there when it is the phone's cloud media app.
                                        Button(enabled = !readingImport, onClick = {
                                            message = null
                                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                                        }) { Text(stringResource(R.string.import_videos)) }
                                        TextButton(onClick = { showSettings = true }) { Text(stringResource(R.string.settings)) }
                                    }
                                    if (readingImport) Text(stringResource(R.string.import_reading))
                                    message?.let { Text(stringResource(it)) }
                                }
                            }
                            // Days are kept in memory only: they are lost when the app is closed.
                            JournalCalendarScreen(
                                year = today.year,
                                clips = days.clips(stringResource(R.string.me)),
                                firstDay = today,
                                today = today,
                                // A filled day reopens its video, on its second, to choose another one; not during an import preview.
                                onPlay = { clip ->
                                    if (importing == null) days.picks[clip.date]?.let { pickSecond(clip.date, it.videoUri) }
                                },
                                // An empty day takes the single video picked for it, as the user's explicit choice.
                                onFill = { date ->
                                    if (importing == null) {
                                        message = null
                                        dayToFill = date
                                        pickOne.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                                    }
                                },
                                previewDays = importing?.byDay?.keys.orEmpty(),
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // The falling-behind notification's tap leaves the settings, or the second picker, for the calendar.
        if (intent.action == ACTION_OPEN_CALENDAR) {
            showSettings = false
            days.cancel()
        }
    }

    companion object {
        /** The action of the falling-behind notification's tap: it opens the calendar screen. */
        const val ACTION_OPEN_CALENDAR = "io.github.benomite.secondes.OPEN_CALENDAR"
    }
}

/** Asks for the notification permission (Android 13 and later) whenever [reminderOn] becomes true. */
@Composable
private fun AskNotificationPermission(reminderOn: Boolean) {
    val context = LocalContext.current
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(reminderOn) {
        if (reminderOn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !canPostNotifications(context)) {
            ask.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/** The reminder's off switch and threshold, saved as soon as the user changes them to a valid value. */
@Composable
private fun ReminderSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { ReminderPrefs.of(context) }
    // Unreadable saved settings show the defaults, and are overwritten only once the user changes something.
    val saved = remember { prefs.settings() ?: ReminderSettings() }
    var enabled by remember { mutableStateOf(saved.enabled) }
    var thresholdText by remember { mutableStateOf(saved.threshold.toString()) }
    var touched by remember { mutableStateOf(false) }
    val threshold = parseThreshold(thresholdText)
    AskNotificationPermission(enabled && touched)
    LaunchedEffect(enabled, threshold, touched) {
        // The switch is saved even while the field holds an invalid number: the last valid threshold is kept.
        if (touched) prefs.save(settingsToSave(enabled, threshold, prefs.settings() ?: saved))
    }
    BackHandler(onBack = onBack)

    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.reminder_switch), modifier = Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = {
                enabled = it
                touched = true
            })
        }
        OutlinedTextField(
            value = thresholdText,
            onValueChange = {
                thresholdText = it
                touched = true
            },
            label = { Text(stringResource(R.string.reminder_threshold)) },
            enabled = enabled,
            isError = threshold == null,
            supportingText = { if (threshold == null) Text(stringResource(R.string.reminder_threshold_invalid)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
    }
}

/** The MIME type of [uri]; null when the provider does not say it or cannot be asked. */
private fun mimeTypeOf(context: Context, uri: Uri): String? = try {
    context.contentResolver.getType(uri)
} catch (e: Exception) {
    null
}

/**
 * Reads what the import preview needs of one selected item: a photo is not read further; a video gives its
 * length and size, its shooting day (metadata creation date, else the picker's DATE_TAKEN) and its first frame.
 */
private fun readForImport(context: Context, position: Int, uri: Uri, zone: ZoneId): ImportRead {
    val mimeType = mimeTypeOf(context, uri)
    if (isNotAVideo(mimeType)) return ImportRead(position, uri.toString(), mimeType, null, null, null)
    val video = readVideoInfo(context, uri)
    val captureDate = toPickedVideos(listOf(readPickerItem(context, uri))).single().captureDate
    val shotOn = shootingDay(readCreationDate(context, uri), captureDate, zone)
    val thumbnail = if (video != null && shotOn != null) readFrame(context, uri, secondWindowAt(0, video.durationMs).startMs) else null
    return ImportRead(position, uri.toString(), mimeType, video, shotOn, thumbnail)
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
