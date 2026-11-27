package io.github.benomite.secondes

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle

fun filledDayTag(date: LocalDate) = "day-filled-$date"
fun emptyDayTag(date: LocalDate) = "day-empty-$date"
fun todayTag(date: LocalDate) = "day-today-$date"
fun futureDayTag(date: LocalDate) = "day-future-$date"
fun beforeStartDayTag(date: LocalDate) = "day-before-start-$date"
fun thumbnailTag(date: LocalDate) = "thumbnail-$date"
fun dayNumberTag(date: LocalDate) = "day-number-$date"
fun notSentTag(date: LocalDate) = "not-sent-$date"
const val TO_FILL_COUNT_TAG = "to-fill-count"
const val MONTH_TITLE_TAG = "month-title"
const val HOME_ACTIONS_TAG = "home-actions"

/** The color a filled day's number is written in, for the tests. */
val DayNumberColorKey = SemanticsPropertyKey<Color>("DayNumberColor")

/** A day's cell: taller than wide, like the clips and the polaroids. */
private const val CELL_RATIO = 0.68f

/**
 * The home calendar: the month shown, opening on [today]'s month, as polaroids for the filled days and dashed cells
 * for the days to fill, with a stamp counting them and a legend of the members' colors. [top] goes above the month
 * (the import preview), [bottom] under it, within thumb reach (the actions, see [HomeActions]).
 */
@Composable
fun JournalCalendarScreen(
    clips: List<Clip>,
    today: LocalDate,
    onPlay: (Clip) -> Unit,
    onFill: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    previewDays: Set<LocalDate> = emptySet(),
    top: @Composable ColumnScope.() -> Unit = {},
    bottom: @Composable ColumnScope.() -> Unit = {},
) {
    var shown by rememberSaveable { mutableStateOf(YearMonth.from(today)) }
    val month = calendarMonth(shown, clips, today)
    // Each member's color, from their place in the journal: the same on every phone.
    val joinOrder = memberJoinOrder(clips.map { it.author to it.date })
    Column(modifier = modifier.fillMaxSize()) {
        top()
        Column(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            MonthHeader(shown, daysToFill(shown, clips, today), onMove = { shown = shown.plusMonths(it) })
            Spacer(Modifier.height(12.dp))
            Weekdays()
            MonthGrid(month, previewDays, joinOrder, onPlay, onFill)
            Spacer(Modifier.height(12.dp))
            Legend(joinOrder)
        }
        bottom()
    }
}

/** The year as a small caps label, the month's name, the previous and next buttons, and the stamp on the right. */
@Composable
private fun MonthHeader(month: YearMonth, toFill: Int, onMove: (Long) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.calendar_year, month.year).uppercase(locale),
                color = Palette.Label,
                fontFamily = Figtree,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                letterSpacing = 1.5.sp,
            )
            Text(
                month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) },
                fontFamily = YoungSerif,
                fontSize = 40.sp,
                color = Palette.Ink,
                modifier = Modifier.testTag(MONTH_TITLE_TAG),
            )
            Row {
                IconButton(onClick = { onMove(-1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.calendar_previous_month), tint = Palette.Ink)
                }
                IconButton(onClick = { onMove(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.calendar_next_month), tint = Palette.Ink)
                }
            }
        }
        Stamp(toFill)
    }
}

/** The round orange stamp, slightly tilted: the number of days to fill in the month shown. */
@Composable
private fun Stamp(toFill: Int) {
    val locale = LocalConfiguration.current.locales[0]
    Box(
        modifier = Modifier.padding(end = 4.dp).size(84.dp).rotate(-8f).border(2.dp, Palette.ToFill, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // A large figure: the stamp's orange is enough for it (3:1); the small label takes the darker shade.
            Text(toFill.toString(), fontFamily = YoungSerif, fontSize = 30.sp, color = Palette.ToFill, modifier = Modifier.testTag(TO_FILL_COUNT_TAG))
            Text(
                stringResource(R.string.calendar_to_fill).uppercase(locale),
                fontFamily = Figtree,
                fontWeight = FontWeight.SemiBold,
                fontSize = 9.sp,
                letterSpacing = 1.sp,
                color = Palette.ToFillText,
            )
        }
    }
}

/** The weekdays, Monday first. */
@Composable
private fun Weekdays() {
    Row(modifier = Modifier.fillMaxWidth()) {
        stringArrayResource(R.array.calendar_weekdays).forEach { day ->
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(day, style = MaterialTheme.typography.labelMedium, color = Palette.MutedText)
            }
        }
    }
}

/** The month's days in seven columns, Monday first. */
@Composable
private fun MonthGrid(
    month: CalendarMonth,
    previewDays: Set<LocalDate>,
    joinOrder: Map<String, Int>,
    onPlay: (Clip) -> Unit,
    onFill: (LocalDate) -> Unit,
) {
    val leading = month.month.atDay(1).dayOfWeek.value - 1
    val cells = List(leading) { null } + month.days
    cells.chunked(7).forEach { week ->
        Row(modifier = Modifier.fillMaxWidth()) {
            week.forEach { day ->
                Box(modifier = Modifier.weight(1f).padding(2.dp).aspectRatio(CELL_RATIO), contentAlignment = Alignment.Center) {
                    if (day != null) DayCell(day, day.date in previewDays, joinOrder, onPlay, onFill)
                }
            }
            repeat(7 - week.size) { Box(modifier = Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun DayCell(day: CalendarDay, previewed: Boolean, joinOrder: Map<String, Int>, onPlay: (Clip) -> Unit, onFill: (LocalDate) -> Unit) {
    // A day an import would fill or change is framed until the import is confirmed or cancelled.
    val frame = if (previewed) Modifier.border(2.dp, MaterialTheme.colorScheme.primary) else Modifier
    val number = day.date.dayOfMonth.toString()
    when (day.state) {
        DayState.FILLED -> {
            val clip = day.clip ?: return
            val color = joinOrder[clip.author]?.let(::memberColor) ?: Palette.Ink
            Box(modifier = Modifier.fillMaxSize().then(frame).testTag(filledDayTag(day.date)).clickable { onPlay(clip) }) {
                Polaroid(
                    index = day.date.dayOfMonth,
                    modifier = Modifier.fillMaxWidth().align(Alignment.Center),
                    footer = {
                        // The day number in its author's color, with a dot of the same color.
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 2.dp)) {
                            Text(
                                number,
                                fontFamily = YoungSerif,
                                fontSize = 12.sp,
                                color = color,
                                modifier = Modifier.testTag(dayNumberTag(day.date)).semantics { this[DayNumberColorKey] = color },
                            )
                            Spacer(Modifier.size(3.dp))
                            Box(Modifier.size(5.dp).clip(CircleShape).background(color))
                        }
                    },
                ) {
                    Image(
                        bitmap = clip.thumbnail,
                        contentDescription = firstName(clip.author),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().testTag(thumbnailTag(day.date)),
                    )
                }
                if (clip.pending) {
                    Text(
                        stringResource(R.string.calendar_not_sent),
                        fontSize = 9.sp,
                        lineHeight = 10.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.TopCenter).background(Palette.Card).testTag(notSentTag(day.date)),
                    )
                }
            }
        }
        DayState.TO_FILL -> Box(
            modifier = Modifier
                .fillMaxSize()
                .then(frame)
                .dashedToFill()
                .testTag(emptyDayTag(day.date))
                .clickable { onFill(day.date) },
        ) {
            DayNumber(number, Palette.Ink)
            Text("+", fontSize = 18.sp, color = Palette.ToFillText, modifier = Modifier.align(Alignment.Center))
        }
        // Today, still empty: a solid ink border; a tap fills it like any day to fill.
        DayState.TODAY -> Box(
            modifier = Modifier
                .fillMaxSize()
                .then(frame)
                .clip(CellShape)
                .background(Palette.ToFill.copy(alpha = 0.08f))
                .border(2.dp, Palette.Ink, CellShape)
                .testTag(todayTag(day.date))
                .clickable { onFill(day.date) },
        ) {
            DayNumber(number, Palette.Ink)
            Text(
                stringResource(R.string.calendar_today),
                style = MaterialTheme.typography.labelSmall,
                color = Palette.Ink,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
            )
        }
        // Before the journal's start: neutral, with no dashed frame, but a tap still fills it.
        DayState.BEFORE_START -> Box(
            modifier = Modifier
                .fillMaxSize()
                .then(frame)
                .testTag(beforeStartDayTag(day.date))
                .clickable { onFill(day.date) },
        ) {
            DayNumber(number, Palette.Ink)
        }
        DayState.FUTURE -> Box(modifier = Modifier.fillMaxSize().testTag(futureDayTag(day.date))) {
            DayNumber(number, Palette.MutedText)
        }
    }
}

private val CellShape = RoundedCornerShape(4.dp)

@Composable
private fun BoxScope.DayNumber(number: String, color: Color) {
    Text(number, fontFamily = YoungSerif, fontSize = 13.sp, color = color, modifier = Modifier.align(Alignment.TopStart).padding(start = 4.dp, top = 2.dp))
}

/** The look of what is still to fill: a dashed orange border on a very light orange fill. */
internal fun Modifier.dashedToFill(): Modifier = this
    .clip(CellShape)
    .background(Palette.ToFill.copy(alpha = 0.08f))
    .drawBehind {
        drawRoundRect(
            color = Palette.ToFill,
            style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
            cornerRadius = CornerRadius(4.dp.toPx()),
        )
    }

/** Each member's first name in their color, however many they are, then the dashed square of the days to fill. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(joinOrder: Map<String, Int>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        joinOrder.entries.sortedBy { it.value }.forEach { (author, order) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(memberColor(order)))
                Spacer(Modifier.size(5.dp))
                Text(firstName(author), style = MaterialTheme.typography.labelMedium, color = Palette.Ink)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).dashedToFill())
            Spacer(Modifier.size(5.dp))
            Text(stringResource(R.string.calendar_to_fill), style = MaterialTheme.typography.labelMedium, color = Palette.Ink)
        }
    }
}

/** The home's actions, at the bottom of the screen: the import first, then the film of the year and the settings. */
@Composable
fun HomeActions(onImport: () -> Unit, onFilm: () -> Unit, onSettings: () -> Unit, importEnabled: Boolean = true) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag(HOME_ACTIONS_TAG), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PrimaryButton(text = stringResource(R.string.import_videos), onClick = onImport, enabled = importEnabled, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(text = stringResource(R.string.export_open), onClick = onFilm, modifier = Modifier.weight(1f))
            SecondaryButton(onClick = onSettings, modifier = Modifier.height(SecondaryButtonHeight)) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings))
            }
        }
    }
}
