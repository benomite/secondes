package io.github.benomite.secondes

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** #67, criterion 3: each member's day goes to its own file, named by member and day, so no one overwrites another. */
class JournalDriveTwoMembersAcceptanceTest {
    private fun mainSources(): Map<String, String> {
        val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.associate { it.name to it.readText() }
    }

    /** The text of the class, other than the HTTP one, that implements RemoteJournal; null while none exists. */
    private fun driveRemoteSource(): String? = mainSources().values.firstOrNull { text ->
        Regex("""class\s+(\w+)[^{=]*:[^{]*\bRemoteJournal\b""").findAll(text).any { it.groupValues[1] != "HttpRemoteJournal" }
    }

    @Test
    fun eachMemberDayIsItsOwnFileNamedWithTheAuthorAndTheDate() {
        val drive = driveRemoteSource()
        assertNotNull("no RemoteJournal backed by Google Drive exists", drive)
        val naming = Regex("""name.*""").findAll(drive!!).map { it.value }.toList()
        assertTrue("no file name built from the author and the date", naming.any { it.contains("author") && it.contains("date") })
    }

    @Test
    fun aPushWritesTheDayAsPlainJsonAndNeverDeletesAnotherMembersFile() {
        val drive = driveRemoteSource()
        assertNotNull("no RemoteJournal backed by Google Drive exists", drive)
        assertTrue(drive!!.contains("encodeDay"))
        assertTrue(drive.contains("application/json"))
        assertFalse(drive.contains("\"DELETE\""))
    }
}
