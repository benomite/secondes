package io.github.benomite.secondes

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Once a day, posts the "falling behind" notification when [runBehindReminderCheck] says it is due. */
class BehindReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result = try {
        // Integration point: NoJournalYet is replaced by the journal's FilledDaysSource once it is merged.
        runBehindReminderCheck(ReminderPrefs.of(applicationContext), NoJournalYet, LocalDate.now(), Instant.now()) {
            postBehindReminder(applicationContext, it)
        }
        Result.success()
    } catch (e: Exception) {
        Log.w(TAG, "falling-behind check failed, retrying", e)
        Result.retry()
    }

    companion object {
        private const val TAG = "BehindReminder"
        private const val WORK_NAME = "behind-reminder"

        /** Schedules the daily check; an already scheduled one is kept. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<BehindReminderWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

const val BEHIND_REMINDER_CHANNEL = "behind_reminder"
private const val BEHIND_REMINDER_ID = 12

/** Whether the app may post notifications (the runtime permission exists from Android 13). */
fun canPostNotifications(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

/** Posts "[emptyDays] days are missing"; tapping it opens the calendar. False when notifications are not allowed. */
fun postBehindReminder(context: Context, emptyDays: Int): Boolean {
    val manager = NotificationManagerCompat.from(context)
    if (!canPostNotifications(context) || !manager.areNotificationsEnabled()) return false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                BEHIND_REMINDER_CHANNEL,
                context.getString(R.string.behind_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }
    val open = Intent(context, MainActivity::class.java)
        .setAction(MainActivity.ACTION_OPEN_CALENDAR)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    val tap = PendingIntent.getActivity(
        context,
        0,
        open,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    val text = context.resources.getQuantityString(R.plurals.behind_text, emptyDays, emptyDays)
    val notification = NotificationCompat.Builder(context, BEHIND_REMINDER_CHANNEL)
        .setSmallIcon(android.R.drawable.ic_popup_reminder)
        .setContentTitle(context.getString(R.string.behind_title))
        .setContentText(text)
        .setContentIntent(tap)
        .setAutoCancel(true)
        .build()
    return try {
        manager.notify(BEHIND_REMINDER_ID, notification)
        true
    } catch (e: SecurityException) {
        Log.w("BehindReminder", "notification refused", e)
        false
    }
}
