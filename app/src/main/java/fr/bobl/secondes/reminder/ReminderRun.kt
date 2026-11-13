package fr.bobl.secondes.reminder

import java.time.LocalDate

/** Where the reminder reads the days that have a clip. Implemented by the journal. */
interface FilledDatesSource {
    fun filledDates(): Set<LocalDate>
}

/** Remembers the day of the last notification, so the policy can space them out. */
interface LastNotifiedStore {
    fun get(): LocalDate?
    fun set(date: LocalDate?)
}

interface ReminderNotifier {
    /** Posts the notification; false when it could not be posted (e.g. permission denied). */
    fun notifyBehind(daysBehind: Int): Boolean
}

/** One pass of the reminder: reads the journal, applies the policy, notifies if needed. */
fun runReminder(
    source: FilledDatesSource,
    store: LastNotifiedStore,
    notifier: ReminderNotifier,
    today: LocalDate,
) {
    val behind = daysBehind(source.filledDates(), today)
    if (behind == 0) {
        // Up to date: the next time the journal falls behind, the reminder may fire right away.
        store.set(null)
        return
    }
    if (!shouldNotify(behind, store.get(), today)) return
    // Only a notification that was really posted counts, so a denied permission is retried.
    if (notifier.notifyBehind(behind)) store.set(today)
}

/** Set by the journal at startup; the worker does nothing until it is. */
object ReminderDependencies {
    @Volatile
    var filledDatesSource: FilledDatesSource? = null
}
