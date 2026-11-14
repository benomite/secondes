package io.github.benomite.secondes

import androidx.compose.ui.graphics.ImageBitmap
import java.time.LocalDate
import java.time.YearMonth

/** A day's clip: who filled it, its thumbnail, and whether it is still [pending], not sent to the journal yet. */
data class Clip(val date: LocalDate, val author: String, val thumbnail: ImageBitmap, val pending: Boolean = false)

/** A day of the calendar; [clip] is null when the day is empty. */
data class CalendarDay(val date: LocalDate, val clip: Clip?)

data class CalendarMonth(val month: YearMonth, val days: List<CalendarDay>)

/** The twelve months of [year], each with its days and their clips. */
fun calendarMonths(year: Int, clips: Collection<Clip>): List<CalendarMonth> {
    val byDate = clips.associateBy { it.date }
    return (1..12).map { m ->
        val month = YearMonth.of(year, m)
        CalendarMonth(
            month,
            (1..month.lengthOfMonth()).map { d ->
                val date = month.atDay(d)
                CalendarDay(date, byDate[date])
            },
        )
    }
}

/** Number of days with no clip from the journal's [firstDay] up to and including [today]. */
fun missingDayCount(clips: Collection<Clip>, firstDay: LocalDate, today: LocalDate): Int =
    missingDays(clips.map { it.date }.toSet(), firstDay, today).size
