package io.github.benomite.secondes

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** How this phone came to the journal: it created the folder, or picked one shared with it. */
enum class JournalOrigin { CREATED, JOINED }

/** The journal this phone works on: a Google Drive folder, by id, with its name as Drive gave it. */
data class StoredJournal(val folderId: String, val name: String, val origin: JournalOrigin)

/** The journal chosen on this phone, kept across launches. */
class JournalPrefs(private val prefs: SharedPreferences) {
    /** The stored journal; null when none is stored or what is stored cannot be read whole. */
    fun stored(): StoredJournal? = try {
        val id = prefs.getString(KEY_FOLDER_ID, null)?.takeIf { it.isNotBlank() }
        val origin = prefs.getString(KEY_ORIGIN, null)?.let { name -> JournalOrigin.entries.find { it.name == name } }
        if (id == null || origin == null) null else StoredJournal(id, prefs.getString(KEY_NAME, null).orEmpty(), origin)
    } catch (e: ClassCastException) {
        null
    }

    /** Replaces the stored journal with [journal]; false when it could not be written, then nothing changed. */
    fun save(journal: StoredJournal): Boolean = prefs.edit()
        .putString(KEY_FOLDER_ID, journal.folderId)
        .putString(KEY_NAME, journal.name)
        .putString(KEY_ORIGIN, journal.origin.name)
        .commit()

    companion object {
        const val FILE = "journal"
        const val KEY_FOLDER_ID = "folder_id"
        const val KEY_NAME = "name"
        const val KEY_ORIGIN = "origin"

        fun of(context: Context) = JournalPrefs(context.getSharedPreferences(FILE, Context.MODE_PRIVATE))
    }
}

/** Whether the stored journal's folder can still be opened, as Drive answered. */
enum class FolderAccess {
    OPEN,

    /** The folder is deleted, in the trash, or no longer shared with this account. */
    LOST,

    /** Drive could not be asked, or its answer says neither: nothing is decided. */
    UNKNOWN,
}

/** What the app shows before, or instead of, the journal. */
sealed interface JournalScreen {
    /** First launch: the app's name, its promise, and Google sign-in. */
    data object SignIn : JournalScreen

    /** Signed in, no journal yet: create one or join one. */
    data object Choose : JournalScreen

    /** A journal was just created: how to share its folder with the others. */
    data class Share(val journal: StoredJournal) : JournalScreen

    /** The journal itself. */
    data class Open(val journal: StoredJournal) : JournalScreen

    /** The stored journal's folder can no longer be opened: join or create another one. */
    data class AccessLost(val journal: StoredJournal) : JournalScreen
}

/**
 * The journal selection: a phone with a stored journal opens it directly; otherwise it signs in, then creates or
 * joins one, which replaces whatever was stored. A journal whose folder is found lost leaves the journal screen.
 */
class JournalSelection(private val prefs: JournalPrefs) {
    var screen by mutableStateOf(prefs.stored()?.let { JournalScreen.Open(it) } ?: JournalScreen.SignIn)
        private set

    /** Google sign-in succeeded: a phone with no journal yet is asked to create or join one. */
    fun signedIn() {
        if (screen == JournalScreen.SignIn) screen = JournalScreen.Choose
    }

    /** Stores the folder created in Drive and explains how to share it; false, nothing changed, when it was not stored. */
    fun created(folderId: String, name: String): Boolean {
        val journal = StoredJournal(folderId, name, JournalOrigin.CREATED)
        if (!prefs.save(journal)) return false
        screen = JournalScreen.Share(journal)
        return true
    }

    /** Stores the folder picked in Drive and opens it; false, nothing changed, when it was not stored. */
    fun joined(folderId: String, name: String): Boolean {
        val journal = StoredJournal(folderId, name, JournalOrigin.JOINED)
        if (!prefs.save(journal)) return false
        screen = JournalScreen.Open(journal)
        return true
    }

    /** Leaves the sharing explanation for the journal just created. */
    fun openShared() {
        (screen as? JournalScreen.Share)?.let { screen = JournalScreen.Open(it.journal) }
    }

    /** Drive answered [access] for [journal]'s folder: a lost folder, still the open journal, says so. */
    fun accessChecked(journal: StoredJournal, access: FolderAccess) {
        if (access == FolderAccess.LOST && screen == JournalScreen.Open(journal)) screen = JournalScreen.AccessLost(journal)
    }
}
