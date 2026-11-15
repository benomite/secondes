package io.github.benomite.secondes

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
    // Room kept at the end of the list, so the last row can scroll clear of the navigation bar.
    bottomInset: Dp = 0.dp,
) {
    val months = calendarMonths(year, clips)
    // Each member's color, from their place in the journal: the same on every phone.
    val joinOrder = memberJoinOrder(clips.map { it.author to it.date })
    val locale = LocalConfiguration.current.locales[0]
    val monthFormat = DateTimeFormatter.ofPattern("LLLL yyyy", locale)
    // No side padding: a day of a 360 dp wide phone stays at least 44 dp wide, a comfortable touch target.
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp + bottomInset)) {
        item {
            Text(
                stringResource(R.string.calendar_missing_days, missingDayCount(clips, firstDay, today)),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag(MISSING_COUNT_TAG),
            )
        }
        items(months) { month ->
            Text(
                month.month.format(monthFormat).replaceFirstChar { it.titlecase(locale) },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
            val leading = month.month.atDay(1).dayOfWeek.value - 1
            val cells = List(leading) { null } + month.days
            cells.chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        Box(modifier = Modifier.weight(1f)) {
                            if (day != null) DayCell(day, day.date in previewDays, joinOrder, onPlay, onFill)
                        }
                    }
                    repeat(7 - week.size) { Box(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: CalendarDay, previewed: Boolean, joinOrder: Map<String, Int>, onPlay: (Clip) -> Unit, onFill: (LocalDate) -> Unit) {
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
                contentDescription = firstName(clip.author),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).testTag(thumbnailTag(day.date)),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The author's color, as a dot: the ochre is too light for text on paper.
                joinOrder[clip.author]?.let {
                    Box(Modifier.padding(end = 2.dp).size(6.dp).clip(CircleShape).background(memberColor(it)))
                }
                // The day number in Young Serif, then the author's first name.
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontFamily = YoungSerif)) { append(day.date.dayOfMonth.toString()) }
                        append(" · ")
                        append(firstName(clip.author))
                    },
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
            if (clip.pending) {
                Text(
                    stringResource(R.string.calendar_not_sent),
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
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .testTag(emptyDayTag(day.date))
                .clickable { onFill(day.date) },
        ) {
            Text(
                day.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = YoungSerif),
                color = Palette.MutedText,
                modifier = Modifier.padding(start = 3.dp),
            )
        }
    }
}
