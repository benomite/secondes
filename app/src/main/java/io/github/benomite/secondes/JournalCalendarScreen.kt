package io.github.benomite.secondes

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.time.LocalDate

fun filledDayTag(date: LocalDate) = "day-filled-$date"
fun emptyDayTag(date: LocalDate) = "day-empty-$date"
fun thumbnailTag(date: LocalDate) = "thumbnail-$date"
fun notSentTag(date: LocalDate) = "not-sent-$date"
const val MISSING_COUNT_TAG = "missing-count"

@Composable
fun JournalCalendarScreen(
    year: Int,
    clips: List<Clip>,
    firstDay: LocalDate,
    today: LocalDate,
    onPlay: (Clip) -> Unit,
    onFill: (LocalDate) -> Unit,
    previewDays: Set<LocalDate> = emptySet(),
) {
    val months = calendarMonths(year, clips)
    LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        item {
            Text(
                "Missing days: ${missingDayCount(clips, firstDay, today)}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag(MISSING_COUNT_TAG),
            )
        }
        items(months) { month ->
            Text(
                month.month.toString(),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 12.dp),
            )
            val leading = month.month.atDay(1).dayOfWeek.value - 1
            val cells = List(leading) { null } + month.days
            cells.chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        Box(modifier = Modifier.weight(1f)) {
                            if (day != null) DayCell(day, day.date in previewDays, onPlay, onFill)
                        }
                    }
                    repeat(7 - week.size) { Box(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: CalendarDay, previewed: Boolean, onPlay: (Clip) -> Unit, onFill: (LocalDate) -> Unit) {
    val clip = day.clip
    if (clip != null) {
        Column(
            modifier = Modifier
                .padding(1.dp)
                // A day an import would fill or change is framed until the import is confirmed or cancelled.
                .then(if (previewed) Modifier.border(2.dp, MaterialTheme.colorScheme.primary) else Modifier)
                .testTag(filledDayTag(day.date))
                .clickable { onPlay(clip) },
        ) {
            Image(
                bitmap = clip.thumbnail,
                contentDescription = clip.author,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).testTag(thumbnailTag(day.date)),
            )
            Text("${day.date.dayOfMonth} · ${clip.author}", style = MaterialTheme.typography.labelSmall)
            if (clip.pending) {
                Text(
                    "Not sent",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag(notSentTag(day.date)),
                )
            }
        }
    } else {
        Box(
            modifier = Modifier
                .padding(1.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .alpha(0.5f)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .testTag(emptyDayTag(day.date))
                .clickable { onFill(day.date) },
        ) {
            Text(day.date.dayOfMonth.toString(), style = MaterialTheme.typography.labelSmall)
        }
    }
}
