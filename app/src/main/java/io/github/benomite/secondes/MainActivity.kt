package io.github.benomite.secondes

import android.Manifest
import android.content.Context
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BehindReminderWorker.schedule(this)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // The reminder is on by default, so the notification permission is asked at launch.
                    val reminderOn = remember { ReminderPrefs.of(this@MainActivity).settings()?.enabled == true }
                    AskNotificationPermission(reminderOn)
                    var showSettings by remember { mutableStateOf(false) }
                    if (showSettings) {
                        ReminderSettingsScreen(onBack = { showSettings = false })
                    } else {
                        PickerScreen(onSettings = { showSettings = true })
                    }
                }
            }
        }
    }

    companion object {
        /**
         * The action of the falling-behind notification's tap. Integration point: the calendar screen opens on it
         * once the calendar is merged; until then the tap opens this activity on its first screen.
         */
        const val ACTION_OPEN_CALENDAR = "io.github.benomite.secondes.OPEN_CALENDAR"
    }
}

@Composable
private fun PickerScreen(onSettings: () -> Unit) {
    val context = LocalContext.current
    var videos by remember { mutableStateOf<List<PickedVideo>?>(null) }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris -> videos = toPickedVideos(uris.map { readPickerItem(context, it) }) }

    Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.placeholder))
        Button(onClick = {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
        }) { Text(stringResource(R.string.pick_videos)) }
        TextButton(onClick = onSettings) { Text(stringResource(R.string.settings)) }
        videos?.let { list ->
            Text(stringResource(R.string.picked_count, list.size))
            list.forEach { Text("${it.uri}\n${it.captureDate ?: stringResource(R.string.no_date)}") }
        }
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
