package io.github.benomite.secondes

import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** #67, criterion 2: a second member joins the same folder and sees the first member's days, by name, at app open. */
class JournalDriveJoinAcceptanceTest {
    private fun mainSources(): Map<String, String> {
        val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.associate { it.name to it.readText() }
    }

    private val driveClass = Regex("""class\s+(\w+)[^{=]*:[^{]*\bRemoteJournal\b""")

    /** The text of the class, other than the HTTP one, that implements RemoteJournal; null while none exists. */
    private fun driveRemoteSource(): String? = mainSources().values.firstOrNull { text ->
        driveClass.findAll(text).any { it.groupValues[1] != "HttpRemoteJournal" }
    }

    @Test
    fun theDriveJournalReadsEveryFileOfTheChosenFolderAndDecodesTheDaysWithTheirAuthor() {
        val drive = driveRemoteSource()
        assertNotNull("no RemoteJournal backed by Google Drive exists", drive)
        assertTrue(drive!!.contains("in parents"))
        assertTrue(drive.contains("alt=media"))
        assertTrue(drive.contains("decodeDay"))
    }

    @Test
    fun theAppOpeningSyncsThroughTheDriveJournal() {
        val drive = driveRemoteSource()
        assertNotNull("no RemoteJournal backed by Google Drive exists", drive)
        val name = driveClass.findAll(drive!!).map { it.groupValues[1] }.first { it != "HttpRemoteJournal" }
        val main = mainSources().getValue("MainActivity.kt")
        assertTrue(main.contains("syncOnOpen"))
        assertTrue(main.contains(name))
    }
}
