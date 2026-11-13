package fr.bobl.secondes.reminder

import androidx.work.ExistingPeriodicWorkPolicy
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private val day0 = LocalDate.of(2026, 3, 1)

private class FakeSource(var dates: Set<LocalDate>) : FilledDatesSource {
    override fun filledDates() = dates
}

private class FakeStore(var last: LocalDate? = null) : LastNotifiedStore {
    override fun get() = last
    override fun set(date: LocalDate?) {
        last = date
    }
}

private class FakeNotifier(var posts: Boolean = true) : ReminderNotifier {
    val sent = mutableListOf<Int>()
    override fun notifyBehind(daysBehind: Int): Boolean {
        if (posts) sent += daysBehind
        return posts
    }
}

class DaysBehindTest {
    @Test
    fun isZeroWhenTodayIsFilled() {
        assertEquals(0, daysBehind(setOf(day0), day0))
    }

    @Test
    fun countsDaysSinceTheLastFilledDay() {
        assertEquals(3, daysBehind(setOf(day0), day0.plusDays(3)))
    }

    @Test
    fun crossesAYearBoundary() {
        assertEquals(5, daysBehind(setOf(LocalDate.of(2025, 12, 29)), LocalDate.of(2026, 1, 3)))
    }

    @Test
    fun ignoresTheOrderInWhichDaysWereFilled() {
        val filled = linkedSetOf(day0.plusDays(5), day0, day0.plusDays(2))
        assertEquals(2, daysBehind(filled, day0.plusDays(7)))
    }

    @Test
    fun isZeroWhenNothingIsFilledOrTheLastDayIsInTheFuture() {
        assertEquals(0, daysBehind(emptySet(), day0))
        assertEquals(0, daysBehind(setOf(day0.plusDays(2)), day0))
    }
}

class ReminderPolicyTest {
    @Test
    fun noNotificationFromZeroToTwoDaysBehind() {
        for (behind in 0..2) assertEquals(false, shouldNotify(behind, null, day0))
    }

    @Test
    fun notifiesAtThreeDaysBehind() {
        assertEquals(true, shouldNotify(3, null, day0))
    }

    @Test
    fun noSecondNotificationWithinThreeDays() {
        assertEquals(false, shouldNotify(5, day0, day0.plusDays(2)))
    }

    @Test
    fun notifiesAgainAfterThreeDaysIfStillBehind() {
        assertEquals(true, shouldNotify(6, day0, day0.plusDays(3)))
    }
}

class RunReminderTest {
    private val filled = day0

    @Test
    fun postsNothingBelowThreeDays() {
        val notifier = FakeNotifier()
        runReminder(FakeSource(setOf(filled)), FakeStore(), notifier, filled.plusDays(2))
        assertEquals(emptyList<Int>(), notifier.sent)
    }

    @Test
    fun postsTheNumberOfDaysBehindAtThree() {
        val notifier = FakeNotifier()
        val store = FakeStore()
        runReminder(FakeSource(setOf(filled)), store, notifier, filled.plusDays(3))
        assertEquals(listOf(3), notifier.sent)
        assertEquals(filled.plusDays(3), store.last)
        assertEquals(true, reminderText(3).contains("3"))
    }

    @Test
    fun spacesNotificationsByThreeDays() {
        val notifier = FakeNotifier()
        val store = FakeStore()
        val source = FakeSource(setOf(filled))
        runReminder(source, store, notifier, filled.plusDays(3))
        runReminder(source, store, notifier, filled.plusDays(5))
        runReminder(source, store, notifier, filled.plusDays(6))
        assertEquals(listOf(3, 6), notifier.sent)
    }

    @Test
    fun postsNothingOnceUpToDateAndForgetsThePastNotification() {
        val notifier = FakeNotifier()
        val store = FakeStore(filled.plusDays(3))
        val today = filled.plusDays(4)
        runReminder(FakeSource(setOf(filled, today)), store, notifier, today)
        assertEquals(emptyList<Int>(), notifier.sent)
        assertNull(store.last)
    }

    @Test
    fun doesNotRecordANotificationThatWasNotPosted() {
        val store = FakeStore()
        runReminder(FakeSource(setOf(filled)), store, FakeNotifier(posts = false), filled.plusDays(4))
        assertNull(store.last)
    }
}

class ScheduleReminderTest {
    @Test
    fun enqueuesUnderAUniqueNameThatKeepsTheExistingWork() {
        val calls = mutableListOf<Pair<String, ExistingPeriodicWorkPolicy>>()
        val scheduled = mutableSetOf<String>()
        repeat(2) {
            scheduleReminder { name, policy, _ ->
                calls += name to policy
                if (policy == ExistingPeriodicWorkPolicy.KEEP) scheduled += name
            }
        }
        assertEquals(2, calls.size)
        assertEquals(setOf(REMINDER_WORK_NAME), scheduled)
        assertEquals(
            listOf(REMINDER_WORK_NAME to ExistingPeriodicWorkPolicy.KEEP),
            calls.distinct(),
        )
    }
}
