package io.github.benomite.secondes

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
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
import androidx.compose.runtime.collectAsState
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
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var showSettings by mutableStateOf(false)

    // Held by the activity, not the composition, so the notification's tap can close an open second picker.
    private val days = DayPicks()

    // The shared journal: kept on the phone first, sent to the server and brought from it outside the screen.
    private lateinit var journal: JournalSync
    private val syncScope = MainScope()
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        journal = JournalSync(FileLocalJournal(File(filesDir, JOURNAL_FILE)), HttpRemoteJournal(BuildConfig.JOURNAL_BASE_URL))
        BehindReminderWorker.schedule(this)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // The reminder is on by default, so the notification permission is asked at launch.
                    val reminderOn = remember { ReminderPrefs.of(this@MainActivity).settings()?.enabled == true }
                    AskNotificationPermission(reminderOn)

                    val today = LocalDate.now()
                    var videos by remember { mutableStateOf<List<PickedVideo>?>(null) }
                    val picker = rememberLauncherForActivityResult(
                        ActivityResultContracts.PickMultipleVisualMedia(),
                    ) { uris -> videos = toPickedVideos(uris.map { readPickerItem(this@MainActivity, it) }) }

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

                    val journalDays by journal.days.collectAsState()
                    var notSaved by remember { mutableStateOf(false) }

                    // Picks a video for a day, empty or filled by anyone; a member with no name yet types it first.
                    fun fillDay(date: LocalDate) {
                        if (MemberPrefs.of(this@MainActivity).name() == null) {
                            showSettings = true
                            return
                        }
                        dayToFill = date
                        pickOne.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                    }

                    val picking = days.picking
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
                                    // The day replaces whoever filled it, is saved here, then sent when the server answers.
                                    val author = MemberPrefs.of(this@MainActivity).name()
                                    notSaved = author == null ||
                                        !journal.fill(picking.date, SharedClip(picking.videoUri, window), author)
                                    if (!notSaved) syncScope.launch { journal.pushPending() }
                                }
                            },
                            onCancel = days::cancel,
                            player = { window, modifier -> LoopingSecondPlayer(picking.videoUri, window, modifier) },
                        )
                    } else {
                        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                            Column(modifier = Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState())) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Button(onClick = {
                                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                                    }) { Text(stringResource(R.string.pick_videos)) }
                                    TextButton(onClick = { showSettings = true }) { Text(stringResource(R.string.settings)) }
                                }
                                videos?.let { list ->
                                    Text(stringResource(R.string.picked_count, list.size))
                                    list.forEach { Text("${it.uri}\n${it.captureDate ?: stringResource(R.string.no_date)}") }
                                }
                                if (unreadable) Text(stringResource(R.string.video_unreadable))
                                if (notSaved) Text(stringResource(R.string.day_not_saved))
                            }
                            // The journal's days, this member's and the others', each with its author's name.
                            JournalCalendarScreen(
                                year = today.year,
                                clips = journalClips(journalDays.values, days.picks),
                                firstDay = today,
                                today = today,
                                onPlay = { clip ->
                                    val day = journalDays[clip.date]
                                    if (day != null && day.author == MemberPrefs.of(this@MainActivity).name()) {
                                        // A day of this member's reopens its video, on its second, to choose another one.
                                        pickSecond(clip.date, day.clip.videoUri)
                                    } else {
                                        // A day filled by another member is replaced by a video picked here.
                                        fillDay(clip.date)
                                    }
                                },
                                onFill = { fillDay(it) },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Opening the app sends the days filled offline, then brings the other members' days.
        syncScope.launch { journal.syncOnOpen() }
        // While the app is open, the network coming back sends the days filled offline.
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                syncScope.launch { journal.pushPending() }
            }
        }
        getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(callback)
        networkCallback = callback
    }

    override fun onStop() {
        networkCallback?.let { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it) }
        networkCallback = null
        super.onStop()
    }

    override fun onDestroy() {
        syncScope.cancel()
        super.onDestroy()
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

        /** The file, in the app's private files, where the journal's days are kept. */
        const val JOURNAL_FILE = "journal.json"
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

    val member = remember { MemberPrefs.of(context) }
    var name by remember { mutableStateOf(member.name().orEmpty()) }

    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleLarge)
        // The name the days filled here are signed with, for the other members of the journal.
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                member.save(it)
            },
            label = { Text(stringResource(R.string.member_name)) },
            isError = name.isBlank(),
            supportingText = { if (name.isBlank()) Text(stringResource(R.string.member_name_missing)) },
            singleLine = true,
        )
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
