package io.github.benomite.secondes

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** #67, criterion 5: offline, a filled day stays on the phone as 'not sent', then goes at the next sync. */
class JournalDriveOfflineAcceptanceTest {
    private fun mainSources(): Map<String, String> {
        val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.associate { it.name to it.readText() }
    }

    /** The text of the class, other than the HTTP one, that implements RemoteJournal; null while none exists. */
    private fun driveRemoteSource(): String? = mainSources().values.firstOrNull { text ->
        Regex("""class\s+(\w+)[^{=]*:[^{]*\bRemoteJournal\b""").findAll(text).any { it.groupValues[1] != "HttpRemoteJournal" }
    }

    @Test
    fun theDriveJournalFailsWithAnIOExceptionSoTheDaysStayPending() {
        val drive = driveRemoteSource()
        assertNotNull("no RemoteJournal backed by Google Drive exists", drive)
        assertTrue(drive!!.contains("IOException"))
        assertFalse(Regex("""catch\s*\(\w+:\s*IOException\)\s*\{\s*\}""").containsMatchIn(drive))
    }

    @Test
    fun theDaysFilledAreSavedOnThePhoneFirstAndSentAtOpenThroughDrive() {
        assertNotNull("no RemoteJournal backed by Google Drive exists", driveRemoteSource())
        val main = mainSources().getValue("MainActivity.kt")
        assertTrue(main.contains("FileLocalJournal"))
        assertTrue(main.contains("syncOnOpen"))
        assertFalse(main.contains("JOURNAL_BASE_URL"))
    }
}
