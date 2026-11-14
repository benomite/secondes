package io.github.benomite.secondes

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.LocalDate

const val CONFIRM_IMPORT_TAG = "import-confirm"
const val CANCEL_IMPORT_TAG = "import-cancel"
fun keepExistingTag(date: LocalDate) = "import-keep-$date"
fun importOptionTag(date: LocalDate, position: Int) = "import-option-$date-$position"

/**
 * The import shown above the calendar: what was refused, the days that wait for a choice, and the
 * confirm and cancel buttons. Confirm is enabled once every such day has its choice.
 */
@Composable
fun ImportPreviewPanel(
    plan: ImportPlan,
    existing: Map<LocalDate, ImageBitmap>,
    onChoose: (LocalDate, DayChoice) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.import_preview), style = MaterialTheme.typography.titleSmall)
        plan.refused.forEach { item ->
            val text = when (item.reason) {
                RefusalReason.NOT_A_VIDEO -> R.string.import_refused_not_video
                RefusalReason.UNREADABLE -> R.string.import_refused_unreadable
                RefusalReason.NO_SHOOTING_DATE -> R.string.import_refused_no_date
            }
            Text(stringResource(text, item.position), color = MaterialTheme.colorScheme.error)
        }
        plan.choiceDays.forEach { date ->
            val onDay = plan.byDay.getValue(date)
            val clip = existing[date]
            Text(
                when {
                    clip == null -> stringResource(R.string.import_shared_day, date.toString(), onDay.size)
                    onDay.size == 1 -> stringResource(R.string.import_filled_day, date.toString())
                    else -> stringResource(R.string.import_filled_shared_day, date.toString(), onDay.size)
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val choice = plan.choiceOf(date)
                if (clip != null) {
                    Option(
                        label = stringResource(R.string.import_keep_existing),
                        thumbnail = clip,
                        selected = choice == DayChoice.KeepExisting,
                        tag = keepExistingTag(date),
                        onClick = { onChoose(date, DayChoice.KeepExisting) },
                    )
                }
                onDay.forEach { video ->
                    Option(
                        label = stringResource(R.string.import_video_n, video.position),
                        thumbnail = video.thumbnail,
                        selected = choice == DayChoice.Take(video.uri),
                        tag = importOptionTag(date, video.position),
                        onClick = { onChoose(date, DayChoice.Take(video.uri)) },
                    )
                }
            }
        }
        if (!plan.isResolved) Text(stringResource(R.string.import_choices_missing))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onConfirm, enabled = plan.isResolved, modifier = Modifier.testTag(CONFIRM_IMPORT_TAG)) {
                Text(stringResource(R.string.import_confirm))
            }
            TextButton(onClick = onCancel, modifier = Modifier.testTag(CANCEL_IMPORT_TAG)) {
                Text(stringResource(R.string.import_cancel))
            }
        }
    }
}

@Composable
private fun Option(label: String, thumbnail: ImageBitmap, selected: Boolean, tag: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .border(2.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .selectable(selected = selected, onClick = onClick)
            .testTag(tag)
            .padding(4.dp),
    ) {
        Image(
            bitmap = thumbnail,
            contentDescription = label,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(56.dp),
        )
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
