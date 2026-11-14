package fr.bobl.secondes.export

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.benomite.secondes.JournalDay
import io.github.benomite.secondes.R
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where an export stands, as the export screen shows it. */
sealed interface ExportState {
    data object Idle : ExportState

    /** The film is being built; [percent] done, from 0 to 100. */
    data class Running(val percent: Int) : ExportState

    /** The film is written to [file] with [seconds] days; [leftOut] days could not be read on this phone. */
    data class Done(val file: File, val seconds: Int, val leftOut: Int) : ExportState
    data class Failed(val message: String) : ExportState
}

/** What became of the last thing done with the finished film: saved, shared, copied to Drive, or failed. */
enum class FilmNotice { SAVED, SAVE_FAILED, SHARE_FAILED, COPIED_TO_DRIVE, DRIVE_FAILED }

const val YEAR_TAG = "export-year"
const val OLDER_YEAR_TAG = "export-older-year"
const val NEWER_YEAR_TAG = "export-newer-year"
const val DATE_OVERLAY_TAG = "export-date-overlay"
const val PROGRESS_TAG = "export-progress"

/**
 * The export screen wired to the export engine and to the phone: the journal's [days] are filtered to the
 * chosen year, [currentYear] by default, then built into the film, which can be saved to the gallery,
 * shared, and copied into the journal's Drive folder.
 */
@Composable
fun ExportRoute(
    days: Collection<JournalDay>,
    currentYear: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val years = remember(days, currentYear) { exportYears(days, currentYear) }
    var year by remember { mutableStateOf(currentYear) }
    var dateOverlay by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf<ExportState>(ExportState.Idle) }
    var notice by remember { mutableStateOf<FilmNotice?>(null) }
    val clips = remember(days, year) { yearClips(days, year) }
    val film = (state as? ExportState.Done)?.file

    fun save(file: File) {
        scope.launch {
            notice = try {
                withContext(Dispatchers.IO) { saveToGallery(context, file) }
                FilmNotice.SAVED
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                FilmNotice.SAVE_FAILED
            }
        }
    }

    // Below Android 10, writing to the gallery needs the storage permission; a refusal says the film is not saved.
    val askStorage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val file = film
        if (granted && file != null) save(file) else notice = FilmNotice.SAVE_FAILED
    }

    // The system's save dialog, where the user picks the journal's folder in Drive; the Drive app uploads it.
    val copyToDrive = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { target: Uri? ->
        val file = film
        if (target == null || file == null) return@rememberLauncherForActivityResult
        scope.launch {
            notice = try {
                withContext(Dispatchers.IO) { copyTo(context, file, target) }
                FilmNotice.COPIED_TO_DRIVE
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                FilmNotice.DRIVE_FAILED
            }
        }
    }

    BackHandler(onBack = onBack)
    ExportScreen(
        years = years,
        year = year,
        onYearChange = {
            year = it
            state = ExportState.Idle
            notice = null
        },
        clipCount = clips.size,
        dateOverlay = dateOverlay,
        onDateOverlayChange = { dateOverlay = it },
        state = state,
        notice = notice,
        onExport = {
            state = ExportState.Running(0)
            notice = null
            scope.launch {
                state = try {
                    // A day filled on another phone points to a video this phone cannot open: it is left out, and said so.
                    val (readable, unreadable) = withContext(Dispatchers.IO) {
                        splitReadable(clips) { isReadable(context, it) }
                    }
                    if (readable.isEmpty()) {
                        ExportState.Failed(context.getString(R.string.export_none_readable, clips.size))
                    } else {
                        val output = filmFile(context, year)
                        YearExporter(context).export(readable, dateOverlay, output) { state = ExportState.Running(it) }
                        ExportState.Done(output, readable.size, unreadable.size)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ExportState.Failed(e.message ?: e.javaClass.simpleName)
                }
            }
        },
        onSave = {
            val file = film ?: return@ExportScreen
            val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED
            if (needsPermission) askStorage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else save(file)
        },
        onShare = {
            val file = film ?: return@ExportScreen
            notice = try {
                context.startActivity(shareIntent(context, file))
                null
            } catch (e: ActivityNotFoundException) {
                FilmNotice.SHARE_FAILED
            } catch (e: IllegalArgumentException) {
                FilmNotice.SHARE_FAILED
            }
        },
        onCopyToDrive = {
            val file = film ?: return@ExportScreen
            try {
                copyToDrive.launch(file.name)
            } catch (e: ActivityNotFoundException) {
                notice = FilmNotice.DRIVE_FAILED
            }
        },
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * The export screen: the year, the current one by default, with arrows to the other [years]; the date
 * overlay, off by default; the export button and its progress; then what to do with the finished film.
 */
@Composable
fun ExportScreen(
    years: List<Int>,
    year: Int,
    onYearChange: (Int) -> Unit,
    clipCount: Int,
    dateOverlay: Boolean,
    onDateOverlayChange: (Boolean) -> Unit,
    state: ExportState,
    notice: FilmNotice?,
    onExport: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onCopyToDrive: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val running = state is ExportState.Running
    // The years are latest first: the older year is the next one in the list.
    val index = years.indexOf(year)
    val older = years.getOrNull(index + 1)
    val newer = if (index > 0) years[index - 1] else null
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.export_title), style = MaterialTheme.typography.headlineSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = { older?.let(onYearChange) },
                enabled = older != null && !running,
                modifier = Modifier.testTag(OLDER_YEAR_TAG),
            ) { Text("‹") }
            Text(year.toString(), style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag(YEAR_TAG))
            TextButton(
                onClick = { newer?.let(onYearChange) },
                enabled = newer != null && !running,
                modifier = Modifier.testTag(NEWER_YEAR_TAG),
            ) { Text("›") }
        }
        Text(stringResource(R.string.export_clip_count, clipCount))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.export_date_overlay), modifier = Modifier.weight(1f))
            Switch(
                checked = dateOverlay,
                onCheckedChange = onDateOverlayChange,
                enabled = !running,
                modifier = Modifier.testTag(DATE_OVERLAY_TAG),
            )
        }
        Button(
            onClick = onExport,
            enabled = clipCount > 0 && !running,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.export_button))
        }
        when {
            state is ExportState.Running -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(
                    progress = { state.percent / 100f },
                    modifier = Modifier.fillMaxWidth().testTag(PROGRESS_TAG),
                )
                Text(stringResource(R.string.export_running, state.percent))
            }
            clipCount == 0 -> Text(stringResource(R.string.export_nothing, year))
            state is ExportState.Done -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.export_done, state.seconds))
                if (state.leftOut > 0) Text(stringResource(R.string.export_left_out, state.leftOut))
                Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.export_save)) }
                OutlinedButton(onClick = onShare, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.export_share))
                }
                OutlinedButton(onClick = onCopyToDrive, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.export_drive))
                }
            }
            state is ExportState.Failed -> Text(
                stringResource(R.string.export_failed, state.message),
                color = MaterialTheme.colorScheme.error,
            )
        }
        notice?.let {
            val failed = it == FilmNotice.SAVE_FAILED || it == FilmNotice.SHARE_FAILED || it == FilmNotice.DRIVE_FAILED
            Text(
                stringResource(
                    when (it) {
                        FilmNotice.SAVED -> R.string.export_saved
                        FilmNotice.SAVE_FAILED -> R.string.export_save_failed
                        FilmNotice.SHARE_FAILED -> R.string.export_share_failed
                        FilmNotice.COPIED_TO_DRIVE -> R.string.export_drive_done
                        FilmNotice.DRIVE_FAILED -> R.string.export_drive_failed
                    },
                ),
                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
        // Leaving the screen while the film is built stops the export.
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
    }
}

@Preview(showBackground = true)
@Composable
private fun ExportScreenPreview() {
    var dateOverlay by remember { mutableStateOf(false) }
    MaterialTheme {
        Surface {
            ExportScreen(
                years = listOf(2026, 2025),
                year = 2026,
                onYearChange = {},
                clipCount = 287,
                dateOverlay = dateOverlay,
                onDateOverlayChange = { dateOverlay = it },
                state = ExportState.Idle,
                notice = null,
                onExport = {},
                onSave = {},
                onShare = {},
                onCopyToDrive = {},
                onBack = {},
            )
        }
    }
}
