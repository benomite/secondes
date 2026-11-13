package io.github.benomite.secondes

import android.content.SharedPreferences
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** In-memory SharedPreferences: a value of the wrong type throws ClassCastException, as the real one does. */
private class FakePrefs(val values: MutableMap<String, Any?> = mutableMapOf()) : SharedPreferences {
    override fun getAll(): MutableMap<String, *> = values
    override fun getString(key: String, defValue: String?) = if (key in values) values[key] as String? else defValue
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        @Suppress("UNCHECKED_CAST")
        if (key in values) values[key] as MutableSet<String>? else defValues
    override fun getInt(key: String, defValue: Int) = if (key in values) values[key] as Int else defValue
    override fun getLong(key: String, defValue: Long) = if (key in values) values[key] as Long else defValue
    override fun getFloat(key: String, defValue: Float) = if (key in values) values[key] as Float else defValue
    override fun getBoolean(key: String, defValue: Boolean) = if (key in values) values[key] as Boolean else defValue
    override fun contains(key: String) = key in values
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        val pending = mutableMapOf<String, Any?>()

        private fun put(key: String, value: Any?): SharedPreferences.Editor {
            pending[key] = value
            return this
        }

        override fun putString(key: String, value: String?) = put(key, value)
        override fun putStringSet(key: String, values: MutableSet<String>?) = put(key, values)
        override fun putInt(key: String, value: Int) = put(key, value)
        override fun putLong(key: String, value: Long) = put(key, value)
        override fun putFloat(key: String, value: Float) = put(key, value)
        override fun putBoolean(key: String, value: Boolean) = put(key, value)
        override fun remove(key: String) = put(key, null)
        override fun clear(): SharedPreferences.Editor {
            values.clear()
            return this
        }
        override fun commit(): Boolean {
            apply()
            return true
        }
        override fun apply() {
            pending.forEach { (k, v) -> if (v == null) values.remove(k) else values[k] = v }
        }
    }
}

class ReminderPrefsTest {
    @Test
    fun nothingSavedGivesTheDefaultsAndNoLastSend() {
        val prefs = ReminderPrefs(FakePrefs())
        assertEquals(ReminderSettings(enabled = true, threshold = 10), prefs.settings())
        assertNull(prefs.lastSentAt())
    }

    @Test
    fun keepsTheChangedThresholdAndTheOffSwitch() {
        val store = FakePrefs()
        ReminderPrefs(store).save(ReminderSettings(enabled = false, threshold = 4))
        assertEquals(ReminderSettings(enabled = false, threshold = 4), ReminderPrefs(store).settings())
    }

    @Test
    fun keepsTheLastSendTime() {
        val store = FakePrefs()
        val at = Instant.parse("2026-01-20T09:00:00Z")
        ReminderPrefs(store).recordSent(at)
        assertEquals(at, ReminderPrefs(store).lastSentAt())
    }

    @Test
    fun unreadableSettingsAreNullAndLeftAsTheyAre() {
        val store = FakePrefs(mutableMapOf(ReminderPrefs.KEY_THRESHOLD to "ten"))
        assertNull(ReminderPrefs(store).settings())
        assertEquals("ten", store.values[ReminderPrefs.KEY_THRESHOLD])
    }

    @Test
    fun anUnreadableLastSendCountsAsNone() {
        val store = FakePrefs(mutableMapOf(ReminderPrefs.KEY_LAST_SENT to "yesterday"))
        assertNull(ReminderPrefs(store).lastSentAt())
    }

    @Test
    fun aThresholdMustBeAWholeNumberOfAtLeastOneDay() {
        assertEquals(10, parseThreshold("10"))
        assertEquals(3, parseThreshold(" 3 "))
        assertNull(parseThreshold("0"))
        assertNull(parseThreshold("-2"))
        assertNull(parseThreshold(""))
        assertNull(parseThreshold("ten"))
        assertNull(parseThreshold("2.5"))
    }
}
