package io.github.benomite.secondes

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class JournalChoiceTest {
    private val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("journal-test", Context.MODE_PRIVATE)

    @Test
    fun aFreshInstallHasNoJournalAndAsksToSignIn() {
        assertNull(JournalPrefs(prefs).stored())
        val selection = JournalSelection(JournalPrefs(prefs))
        assertEquals(JournalScreen.SignIn, selection.screen)

        selection.signedIn()
        assertEquals(JournalScreen.Choose, selection.screen)
    }

    @Test
    fun aJoinedJournalOpensAndIsOpenedDirectlyOnTheNextLaunch() {
        val selection = JournalSelection(JournalPrefs(prefs))
        selection.signedIn()

        assertTrue(selection.joined("folder-1", "Our journal"))
        val joined = StoredJournal("folder-1", "Our journal", JournalOrigin.JOINED)
        assertEquals(JournalScreen.Open(joined), selection.screen)

        // Killed and relaunched: the same journal, with no sign-in and no choice.
        assertEquals(JournalScreen.Open(joined), JournalSelection(JournalPrefs(prefs)).screen)
    }

    @Test
    fun aCreatedJournalExplainsTheSharingThenOpensAndIsOpenedDirectlyOnTheNextLaunch() {
        val selection = JournalSelection(JournalPrefs(prefs))
        selection.signedIn()

        assertTrue(selection.created("folder-2", JOURNAL_FOLDER_NAME))
        val created = StoredJournal("folder-2", JOURNAL_FOLDER_NAME, JournalOrigin.CREATED)
        assertEquals(JournalScreen.Share(created), selection.screen)

        selection.openShared()
        assertEquals(JournalScreen.Open(created), selection.screen)

        assertEquals(JournalScreen.Open(created), JournalSelection(JournalPrefs(prefs)).screen)
    }

    @Test
    fun signingInAgainDoesNotLeaveTheOpenJournal() {
        val selection = JournalSelection(JournalPrefs(prefs))
        selection.joined("folder-1", "Our journal")
        selection.signedIn()
        assertEquals(JournalScreen.Open(StoredJournal("folder-1", "Our journal", JournalOrigin.JOINED)), selection.screen)
    }

    @Test
    fun aLostFolderSaysSoAndEitherChoiceReplacesTheStoredJournal() {
        JournalSelection(JournalPrefs(prefs)).joined("gone", "Old journal")
        val selection = JournalSelection(JournalPrefs(prefs))
        val old = StoredJournal("gone", "Old journal", JournalOrigin.JOINED)

        selection.accessChecked(old, FolderAccess.LOST)
        assertEquals(JournalScreen.AccessLost(old), selection.screen)

        assertTrue(selection.joined("other", "Other journal"))
        val other = StoredJournal("other", "Other journal", JournalOrigin.JOINED)
        assertEquals(JournalScreen.Open(other), selection.screen)
        assertEquals(other, JournalPrefs(prefs).stored())

        selection.accessChecked(other, FolderAccess.LOST)
        assertTrue(selection.created("new", JOURNAL_FOLDER_NAME))
        assertEquals(StoredJournal("new", JOURNAL_FOLDER_NAME, JournalOrigin.CREATED), JournalPrefs(prefs).stored())
    }

    @Test
    fun anOpenOrUndecidedFolderKeepsTheJournalOpen() {
        val selection = JournalSelection(JournalPrefs(prefs))
        selection.joined("folder-1", "Our journal")
        val journal = StoredJournal("folder-1", "Our journal", JournalOrigin.JOINED)

        selection.accessChecked(journal, FolderAccess.OPEN)
        assertEquals(JournalScreen.Open(journal), selection.screen)
        selection.accessChecked(journal, FolderAccess.UNKNOWN)
        assertEquals(JournalScreen.Open(journal), selection.screen)
    }

    @Test
    fun aLateAnswerAboutAReplacedJournalChangesNothing() {
        val selection = JournalSelection(JournalPrefs(prefs))
        selection.joined("old", "Old")
        selection.joined("new", "New")

        selection.accessChecked(StoredJournal("old", "Old", JournalOrigin.JOINED), FolderAccess.LOST)
        assertEquals(JournalScreen.Open(StoredJournal("new", "New", JournalOrigin.JOINED)), selection.screen)
    }

    @Test
    fun anUnreadableOrPartialStoredJournalIsNoJournal() {
        prefs.edit().putString(JournalPrefs.KEY_FOLDER_ID, "folder-1").commit()
        assertNull(JournalPrefs(prefs).stored())

        prefs.edit().putString(JournalPrefs.KEY_ORIGIN, "SOMETHING_ELSE").commit()
        assertNull(JournalPrefs(prefs).stored())

        prefs.edit().putInt(JournalPrefs.KEY_FOLDER_ID, 3).putString(JournalPrefs.KEY_ORIGIN, "JOINED").commit()
        assertNull(JournalPrefs(prefs).stored())
        assertEquals(JournalScreen.SignIn, JournalSelection(JournalPrefs(prefs)).screen)
    }

    @Test
    fun aJournalThatCannotBeSavedChangesNothing() {
        val failing = FailingPrefs(prefs)
        val selection = JournalSelection(JournalPrefs(failing))
        selection.signedIn()

        assertFalse(selection.joined("folder-1", "Our journal"))
        assertFalse(selection.created("folder-2", JOURNAL_FOLDER_NAME))
        assertEquals(JournalScreen.Choose, selection.screen)
        assertNull(JournalPrefs(prefs).stored())
    }

    /** Preferences whose writes never reach the disk. */
    private class FailingPrefs(private val real: SharedPreferences) : SharedPreferences by real {
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor by real.edit() {
            override fun commit() = false
        }
    }
}
