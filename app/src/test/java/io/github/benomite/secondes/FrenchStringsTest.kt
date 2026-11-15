package io.github.benomite.secondes

import android.content.Context
import android.content.res.Configuration
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** The app follows the phone's language: French texts on a French phone, English otherwise. */
@RunWith(RobolectricTestRunner::class)
class FrenchStringsTest {
    private fun inLanguage(locale: Locale): Context {
        val app = RuntimeEnvironment.getApplication()
        val config = Configuration(app.resources.configuration).apply { setLocale(locale) }
        return app.createConfigurationContext(config)
    }

    @Test
    fun aFrenchPhoneGetsFrenchScreensAndNotification() {
        val fr = inLanguage(Locale.FRENCH)
        assertEquals("Choisis la seconde", fr.getString(R.string.second_title))
        assertEquals("Jours manquants : 3", fr.getString(R.string.calendar_missing_days, 3))
        assertEquals("Ton journal prend du retard", fr.getString(R.string.behind_title))
        assertEquals("4 jours manquent", fr.resources.getQuantityString(R.plurals.behind_text, 4, 4))
        assertEquals("Film de l'année", fr.getString(R.string.export_title))
        assertEquals("0,5 s", fr.getString(R.string.second_value, 0.5))
    }

    @Test
    fun anEnglishPhoneGetsEnglish() {
        val en = inLanguage(Locale.ENGLISH)
        assertEquals("Choose the second", en.getString(R.string.second_title))
        assertEquals("Your journal is falling behind", en.getString(R.string.behind_title))
        assertEquals("0.5 s", en.getString(R.string.second_value, 0.5))
    }
}
