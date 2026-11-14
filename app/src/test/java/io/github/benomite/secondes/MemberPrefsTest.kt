package io.github.benomite.secondes

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class MemberPrefsTest {
    private val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("member-test", Context.MODE_PRIVATE)

    @Test
    fun noNameSavedYetIsNoName() {
        assertNull(MemberPrefs(prefs).name())
    }

    @Test
    fun theNameIsSavedTrimmed() {
        MemberPrefs(prefs).save("  Léa ")
        assertEquals("Léa", MemberPrefs(prefs).name())
    }

    @Test
    fun aBlankOrUnreadableNameIsNoName() {
        MemberPrefs(prefs).save("   ")
        assertNull(MemberPrefs(prefs).name())

        prefs.edit().putInt(MemberPrefs.KEY_NAME, 3).commit()
        assertNull(MemberPrefs(prefs).name())
    }
}
