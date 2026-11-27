package io.github.benomite.secondes

import androidx.compose.ui.graphics.ImageBitmap
import java.time.LocalDate
import java.time.YearMonth

/** A day's clip: who filled it, its thumbnail, and whether it is still [pending], not sent to the journal yet. */
data class Clip(val date: LocalDate, val author: String, val thumbnail: ImageBitmap, val pending: Boolean = false)

/** What a day of the calendar shows, seen from today. */
enum class DayState {
    /** A day with a clip. */
    FILLED,

    /** A past day with no clip, from the journal's start: still to fill. */
    TO_FILL,

    /** A past day with no clip, before the journal's start (see [journalStart]): neutral, it may still be filled. */
    BEFORE_START,

    /** Today, with no clip yet: to fill as well. */
    TODAY,

    /** A day still to come: it cannot be filled yet. */
    FUTURE,
}

/** A day of the calendar; [clip] is null when the day is empty. */
data class CalendarDay(val date: LocalDate, val clip: Clip?, val state: DayState)

data class CalendarMonth(val month: YearMonth, val days: List<CalendarDay>)

/** The days of [month], each with its clip and its state seen from [today]; [clips] are the whole journal's. */
fun calendarMonth(month: YearMonth, clips: Collection<Clip>, today: LocalDate): CalendarMonth {
    val byDate = clips.associateBy { it.date }
    val start = journalStart(byDate.keys, today)
    return CalendarMonth(
        month,
        (1..month.lengthOfMonth()).map { d ->
            val date = month.atDay(d)
            val clip = byDate[date]
            val state = when {
                clip != null -> DayState.FILLED
                date == today -> DayState.TODAY
                date.isAfter(today) -> DayState.FUTURE
                date.isBefore(start) -> DayState.BEFORE_START
                else -> DayState.TO_FILL
            }
            CalendarDay(date, clip, state)
        },
    )
}

/**
 * Number of days of [month] with no clip from the journal's start up to and including [today]: the past ones, and
 * today while it is empty. The days before the start are not counted; [clips] are the whole journal's.
 */
fun daysToFill(month: YearMonth, clips: Collection<Clip>, today: LocalDate): Int {
    val filled = clips.map { it.date }.toSet()
    val first = maxOf(month.atDay(1), journalStart(filled, today))
    val last = minOf(month.atEndOfMonth(), today)
    return missingDays(filled, first, last).size
}
