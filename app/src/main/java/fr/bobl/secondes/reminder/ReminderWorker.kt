package fr.bobl.secondes.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.time.LocalDate

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val source = ReminderDependencies.filledDatesSource ?: return Result.success()
        runReminder(
            source,
            PreferencesLastNotifiedStore(applicationContext),
            AndroidReminderNotifier(applicationContext),
            LocalDate.now(),
        )
        return Result.success()
    }
}
