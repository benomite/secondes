package io.github.benomite.secondes

import android.content.ComponentName
import androidx.activity.ComponentActivity
import org.junit.rules.ExternalResource
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * The release variant's merged manifest lacks the Compose test activity
 * (ui-test-manifest only reaches debug), so register it before the compose rule launches it.
 * Apply it with a lower `order` than `createComposeRule()`.
 */
class ComposeHostActivityRule : ExternalResource() {
    override fun before() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app.packageManager)
            .addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
    }
}
