package fr.bobl.secondes.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.benomite.secondes.R
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Where an export stands, as the export screen shows it. */
sealed interface ExportState {
    data object Idle : ExportState
    data object Running : ExportState
    data class Done(val file: File) : ExportState
    data class Failed(val message: String) : ExportState
}

/**
 * The export screen wired to the export engine: the entry point the journal shell plugs in,
 * handing it the clips of the year. [onExported] receives the film once written.
 */
@Composable
fun ExportRoute(
    clips: List<DatedClip>,
    modifier: Modifier = Modifier,
    onExported: (File) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var dateOverlay by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf<ExportState>(ExportState.Idle) }

    ExportScreen(
        clipCount = clips.size,
        dateOverlay = dateOverlay,
        onDateOverlayChange = { dateOverlay = it },
        state = state,
        onExport = {
            state = ExportState.Running
            scope.launch {
                state = try {
                    val output = File(File(context.filesDir, "exports"), "secondes.mp4")
                    YearExporter(context).export(clips, dateOverlay, output)
                    onExported(output)
                    ExportState.Done(output)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ExportState.Failed(e.message ?: e.javaClass.simpleName)
                }
            }
        },
        modifier = modifier,
    )
}

/** The export screen: a date overlay toggle, off by default, and an export button. */
@Composable
fun ExportScreen(
    clipCount: Int,
    dateOverlay: Boolean,
    onDateOverlayChange: (Boolean) -> Unit,
    state: ExportState,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.export_title), style = MaterialTheme.typography.headlineSmall)
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
                enabled = state != ExportState.Running,
            )
        }
        Button(
            onClick = onExport,
            enabled = clipCount > 0 && state != ExportState.Running,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.export_button))
        }
        when {
            clipCount == 0 -> Text(stringResource(R.string.export_nothing))
            state == ExportState.Running -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator()
                Text(stringResource(R.string.export_running))
            }
            state is ExportState.Done -> Text(stringResource(R.string.export_done, state.file.path))
            state is ExportState.Failed -> Text(
                stringResource(R.string.export_failed, state.message),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ExportScreenPreview() {
    var dateOverlay by remember { mutableStateOf(false) }
    MaterialTheme {
        Surface {
            ExportScreen(
                clipCount = 287,
                dateOverlay = dateOverlay,
                onDateOverlayChange = { dateOverlay = it },
                state = ExportState.Idle,
                onExport = {},
            )
        }
    }
}
