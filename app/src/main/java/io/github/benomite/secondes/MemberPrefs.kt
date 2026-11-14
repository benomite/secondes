package io.github.benomite.secondes

import android.content.Context
import android.content.SharedPreferences

/** The name this member signs the journal's days with, as typed in the settings. */
class MemberPrefs(private val prefs: SharedPreferences) {
    /** The saved name, or null when none is saved, it is blank, or what is saved cannot be read. */
    fun name(): String? = try {
        prefs.getString(KEY_NAME, null)?.trim()?.takeIf { it.isNotEmpty() }
    } catch (e: ClassCastException) {
        null
    }

    fun save(name: String) {
        prefs.edit().putString(KEY_NAME, name.trim()).apply()
    }

    companion object {
        const val FILE = "member"
        const val KEY_NAME = "name"

        fun of(context: Context) = MemberPrefs(context.getSharedPreferences(FILE, Context.MODE_PRIVATE))
    }
}
