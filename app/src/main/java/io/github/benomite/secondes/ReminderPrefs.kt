package io.github.benomite.secondes

import android.content.Context
import android.content.SharedPreferences
import java.time.Instant

/** Where the reminder keeps the user's settings and the time of the last notification. */
interface ReminderStore {
    /** The saved settings, the defaults when nothing was saved yet, or null when what is saved cannot be read. */
    fun settings(): ReminderSettings?

    /** When the last notification was sent; null when none was, or when the saved value cannot be read. */
    fun lastSentAt(): Instant?

    fun recordSent(at: Instant)
}

/** [ReminderStore] on SharedPreferences. */
class ReminderPrefs(private val prefs: SharedPreferences) : ReminderStore {
    override fun settings(): ReminderSettings? = try {
        ReminderSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, true),
            threshold = prefs.getInt(KEY_THRESHOLD, ReminderSettings.DEFAULT_THRESHOLD),
        )
    } catch (e: ClassCastException) {
        null
    }

    fun save(settings: ReminderSettings) {
        prefs.edit().putBoolean(KEY_ENABLED, settings.enabled).putInt(KEY_THRESHOLD, settings.threshold).apply()
    }

    override fun lastSentAt(): Instant? = try {
        if (prefs.contains(KEY_LAST_SENT)) Instant.ofEpochMilli(prefs.getLong(KEY_LAST_SENT, 0)) else null
    } catch (e: ClassCastException) {
        null
    }

    override fun recordSent(at: Instant) {
        prefs.edit().putLong(KEY_LAST_SENT, at.toEpochMilli()).apply()
    }

    companion object {
        const val FILE = "behind_reminder"
        const val KEY_ENABLED = "enabled"
        const val KEY_THRESHOLD = "threshold"
        const val KEY_LAST_SENT = "last_sent_at"

        fun of(context: Context) = ReminderPrefs(context.getSharedPreferences(FILE, Context.MODE_PRIVATE))
    }
}

/** The threshold typed in the settings, or null when it is not a whole number of at least one day. */
fun parseThreshold(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it >= 1 }
