package io.github.benomite.secondes

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** #67, criterion 1: a release build with no build option sets the shared journal up from the app, through Drive. */
class JournalDriveSetupAcceptanceTest {
    private fun mainSources(): Map<String, String> {
        val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.associate { it.name to it.readText() }
    }

    /** The text of the class, other than the HTTP one, that implements RemoteJournal; null while none exists. */
    private fun driveRemoteSource(): String? = mainSources().values.firstOrNull { text ->
        Regex("""class\s+(\w+)[^{=]*:[^{]*\bRemoteJournal\b""").findAll(text).any { it.groupValues[1] != "HttpRemoteJournal" }
    }

    @Test
    fun theAppSyncsThroughADriveFolderNotThroughTheEmptyJournalBaseUrl() {
        assertNotNull("no RemoteJournal backed by Google Drive exists", driveRemoteSource())
        val main = mainSources().getValue("MainActivity.kt")
        assertFalse("the journal still depends on secondes.journalBaseUrl", main.contains("JOURNAL_BASE_URL"))
        assertFalse(main.contains("HttpRemoteJournal("))
    }

    @Test
    fun theSetupSignsInWithGoogleThenCreatesOrPicksTheFolderWithTheScopeOfIssue4() {
        assertNotNull("no RemoteJournal backed by Google Drive exists", driveRemoteSource())
        val main = mainSources().getValue("MainActivity.kt")
        assertTrue(main.contains("GoogleAuthorization"))
        assertTrue(main.contains(".create("))
        assertTrue(mainSources().getValue("GoogleDrive.kt").contains("auth/drive.file\""))
    }

    @Test
    fun noGoogleSettingNeededByTheSetupComesFromABuildOptionOrALocalProperty() {
        assertNotNull("no RemoteJournal backed by Google Drive exists", driveRemoteSource())
        val gradle = listOf(File("build.gradle.kts"), File("app/build.gradle.kts")).first { it.isFile }.readText()
        assertFalse(gradle.contains("secondes.journalBaseUrl"))
        assertFalse(Regex("""CLIENT_ID.*(releaseSetting|findProperty)""").containsMatchIn(gradle))
    }
}
