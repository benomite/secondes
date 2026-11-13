package fr.bobl.secondes.reminder

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

const val REMINDER_WORK_NAME = "catch-up-reminder"

/** Schedules the daily check once: KEEP under a unique name makes a second call a no-op. */
fun scheduleReminder(context: Context) {
    val workManager = WorkManager.getInstance(context)
    scheduleReminder { name, policy, request ->
        workManager.enqueueUniquePeriodicWork(name, policy, request)
    }
}

internal fun scheduleReminder(
    enqueue: (String, ExistingPeriodicWorkPolicy, PeriodicWorkRequest) -> Unit,
) {
    val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS).build()
    enqueue(REMINDER_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
}
