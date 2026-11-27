package fr.bobl.secondes.export

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import io.github.benomite.secondes.JournalDay
import io.github.benomite.secondes.MinTouchTarget
import io.github.benomite.secondes.Palette
import io.github.benomite.secondes.Polaroid
import io.github.benomite.secondes.PrimaryButton
import io.github.benomite.secondes.R
import io.github.benomite.secondes.SecondaryButton
import io.github.benomite.secondes.SecondesTheme
import io.github.benomite.secondes.YoungSerif
import io.github.benomite.secondes.dashedToFill
import io.github.benomite.secondes.memberColor
import java.io.File
import java.time.LocalDate
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

fun yearTag(year: Int) = "export-year-$year"
const val BACK_TAG = "export-back"
const val SECONDS_TAG = "export-seconds"
const val DATE_OVERLAY_TAG = "export-date-overlay"
const val PROGRESS_TAG = "export-progress"

/**
 * The export screen wired to the export engine and to the phone: the journal's [days] are filtered to the
 * chosen year, [today]'s by default, then built into the film, which can be saved to the gallery,
 * shared, and copied into the journal's Drive folder.
 */
@Composable
fun ExportRoute(
    days: Collection<JournalDay>,
    today: LocalDate,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val years = remember(days, today) { exportYears(days, today.year) }
    var year by remember { mutableStateOf(today.year) }
    var dateOverlay by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf<ExportState>(ExportState.Idle) }
    var notice by remember { mutableStateOf<FilmNotice?>(null) }
    val clips = remember(days, year) { yearClips(days, year) }
    val stats = remember(days, year, today) { yearStats(days, year, today) }
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
        stats = stats,
        dateOverlay = dateOverlay,
        onDateOverlayChange = { dateOverlay = it },
        state = state,
        notice = notice,
        onExport = {
            state = ExportState.Running(0)
            notice = null
            scope.launch {
                state = try {
                    // Every member's day plays from its clip brought from the family's folder; a day written before the
                    // clips were there points to a video only its author's phone can open: it is left out, and said so.
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
 * The export screen: back and the title, the [years] as buttons (the chosen one filled), a polaroid of the film
 * beside its seconds, length, each member's share and the empty days skipped; the date overlay, off by default;
 * the build and its progress, or its failure, in a dashed box; then what to do with the finished film.
 */
@Composable
fun ExportScreen(
    years: List<Int>,
    year: Int,
    onYearChange: (Int) -> Unit,
    stats: YearStats,
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
    val done = state as? ExportState.Done
    val seconds = done?.seconds ?: stats.seconds
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Leaving the screen while the film is built stops the export.
            TextButton(
                onClick = onBack,
                modifier = Modifier.defaultMinSize(MinTouchTarget, MinTouchTarget).testTag(BACK_TAG),
            ) { Text("‹", style = MaterialTheme.typography.headlineSmall, color = Palette.Ink) }
            Text(stringResource(R.string.export_title), style = MaterialTheme.typography.headlineSmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            years.forEach { y ->
                val chosen = y == year
                val onClick = { if (!chosen) onYearChange(y) }
                val tag = Modifier.heightIn(min = MinTouchTarget).testTag(yearTag(y))
                if (chosen) {
                    PrimaryButton(onClick = onClick, modifier = tag, enabled = !running) { Text(y.toString()) }
                } else {
                    SecondaryButton(onClick = onClick, modifier = tag, enabled = !running) { Text(y.toString()) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Polaroid(index = 0, modifier = Modifier.weight(1f), caption = year.toString()) {
                Box(Modifier.fillMaxSize().background(Palette.Ink), contentAlignment = Alignment.Center) {
                    Text(year.toString(), fontFamily = YoungSerif, fontSize = 36.sp, color = Palette.OnInk)
                }
            }
            Column(modifier = Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    seconds.toString(),
                    fontFamily = YoungSerif,
                    fontSize = 56.sp,
                    color = Palette.Ink,
                    modifier = Modifier.testTag(SECONDS_TAG),
                )
                Text(stringResource(R.string.export_seconds_length, filmLength(seconds)), color = Palette.Secondary)
                stats.shares.forEach {
                    Text(
                        stringResource(R.string.export_member_share, it.name, it.seconds),
                        color = memberColor(it.joinOrder),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    LocalContext.current.resources.getQuantityString(R.plurals.export_empty_days, stats.emptyDays, stats.emptyDays),
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.MutedText,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.export_date_overlay), fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.export_date_overlay_hint), style = MaterialTheme.typography.bodySmall, color = Palette.MutedText)
            }
            Switch(
                checked = dateOverlay,
                onCheckedChange = onDateOverlayChange,
                enabled = !running,
                modifier = Modifier.testTag(DATE_OVERLAY_TAG),
            )
        }
        when {
            state is ExportState.Running -> DashedBox {
                Text(stringResource(R.string.export_running), fontWeight = FontWeight.SemiBold, color = Palette.ToFillText)
                Text(
                    stringResource(R.string.export_running_day, dayReached(state.percent, stats.seconds), stats.seconds),
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.ToFillText,
                )
                LinearProgressIndicator(
                    progress = { state.percent / 100f },
                    modifier = Modifier.fillMaxWidth().testTag(PROGRESS_TAG),
                    color = Palette.ToFill,
                    trackColor = Palette.ToFill.copy(alpha = 0.2f),
                )
            }
            state is ExportState.Failed -> DashedBox {
                Text(stringResource(R.string.export_failed, state.message), color = Palette.ToFillText)
            }
            stats.seconds == 0 -> Text(stringResource(R.string.export_nothing, year))
            done != null && done.leftOut > 0 -> Text(stringResource(R.string.export_left_out, done.leftOut))
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
        if (done != null) {
            PrimaryButton(text = stringResource(R.string.export_save), onClick = onSave, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                SecondaryButton(text = stringResource(R.string.export_share), onClick = onShare, modifier = Modifier.weight(1f))
                SecondaryButton(text = stringResource(R.string.export_drive), onClick = onCopyToDrive, modifier = Modifier.weight(1f))
            }
        } else {
            PrimaryButton(
                text = stringResource(R.string.export_button),
                onClick = onExport,
                enabled = stats.seconds > 0 && !running,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The dashed orange box of the build in progress, and of its failure. */
@Composable
private fun DashedBox(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().dashedToFill().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Preview(showBackground = true)
@Composable
private fun ExportScreenPreview() {
    var dateOverlay by remember { mutableStateOf(false) }
    SecondesTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ExportScreen(
                years = listOf(2026, 2025),
                year = 2026,
                onYearChange = {},
                stats = YearStats(289, listOf(MemberShare("Léa", 1, 150), MemberShare("Tom", 2, 139)), 12),
                dateOverlay = dateOverlay,
                onDateOverlayChange = { dateOverlay = it },
                state = ExportState.Running(62),
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
