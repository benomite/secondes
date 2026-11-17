package io.github.benomite.secondes

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** #67, criterion 4: the app reads and writes only the chosen folder, so another family's folder is never seen. */
class JournalDriveFolderIsolationAcceptanceTest {
    private fun mainSources(): Map<String, String> {
        val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.associate { it.name to it.readText() }
    }

    /** The text of the class, other than the HTTP one, that implements RemoteJournal; null while none exists. */
    private fun driveRemoteSource(): String? = mainSources().values.firstOrNull { text ->
        Regex("""class\s+(\w+)[^{=]*:[^{]*\bRemoteJournal\b""").findAll(text).any { it.groupValues[1] != "HttpRemoteJournal" }
    }

    @Test
    fun everyListingIsRestrictedToTheChosenFolderAndEveryNewFileIsCreatedInIt() {
        val drive = driveRemoteSource()
        assertNotNull("no RemoteJournal backed by Google Drive exists", drive)
        assertTrue(drive!!.contains("in parents"))
        assertTrue("new files must name the chosen folder as parent", drive.contains("\"parents\""))
    }

    @Test
    fun noRequestReachesTheWholeDriveOrAnotherSharedSpace() {
        val drive = driveRemoteSource()
        assertNotNull("no RemoteJournal backed by Google Drive exists", drive)
        assertFalse(drive!!.contains("allDrives"))
        assertFalse(drive.contains("sharedWithMe"))
        assertFalse(drive.contains("'root'"))
        assertTrue(mainSources().getValue("GoogleDrive.kt").contains("auth/drive.file\""))
    }
}
