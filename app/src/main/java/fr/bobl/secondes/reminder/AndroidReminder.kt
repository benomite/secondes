package fr.bobl.secondes.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import java.time.LocalDate

const val REMINDER_CHANNEL_ID = "catch-up-reminder"
private const val REMINDER_NOTIFICATION_ID = 20

fun reminderText(daysBehind: Int): String =
    "Ton journal a $daysBehind jours de retard : un petit clip pour rattraper ?"

/** Android 13+ asks for POST_NOTIFICATIONS at runtime; before, it is granted at install. */
fun isNotificationPermissionGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

class AndroidReminderNotifier(private val context: Context) : ReminderNotifier {
    override fun notifyBehind(daysBehind: Int): Boolean {
        if (!isNotificationPermissionGranted(context)) return false
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        manager.createNotificationChannel(
            NotificationChannel(REMINDER_CHANNEL_ID, "Rappel de rattrapage", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Secondes")
            .setContentText(reminderText(daysBehind))
            .setAutoCancel(true)
            .build()
        manager.notify(REMINDER_NOTIFICATION_ID, notification)
        return true
    }
}

class PreferencesLastNotifiedStore(context: Context) : LastNotifiedStore {
    private val prefs = context.getSharedPreferences("catch-up-reminder", Context.MODE_PRIVATE)

    override fun get(): LocalDate? =
        prefs.getString(KEY, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    override fun set(date: LocalDate?) {
        prefs.edit().apply { if (date == null) remove(KEY) else putString(KEY, date.toString()) }.apply()
    }

    private companion object {
        const val KEY = "last-notified"
    }
}
