package io.github.benomite.secondes

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GoogleDriveTest {
    private var server: ServerSocket? = null
    private val requests = CopyOnWriteArrayList<String>()

    @After
    fun stop() {
        server?.close()
        server = null
    }

    /** A local stand-in for Drive's REST API answering [code] and [body] to every request. */
    private fun drive(code: Int, body: String = ""): DriveFolders {
        val local = ServerSocket(0, 0, InetAddress.getLoopbackAddress())
        thread(isDaemon = true) {
            while (!local.isClosed) {
                val socket = try {
                    local.accept()
                } catch (e: IOException) {
                    break
                }
                socket.use { answer(it, code, body) }
            }
        }
        server = local
        return DriveFolders("http://127.0.0.1:${local.localPort}/drive/v3")
    }

    private fun answer(socket: Socket, code: Int, body: String) {
        val input = BufferedReader(InputStreamReader(socket.getInputStream()))
        val requestLine = input.readLine() ?: return
        var authorization: String? = null
        var length = 0
        while (true) {
            val header = input.readLine() ?: break
            if (header.isEmpty()) break
            val (name, value) = header.split(":", limit = 2).map { it.trim() }
            if (name.equals("Authorization", ignoreCase = true)) authorization = value
            if (name.equals("Content-Length", ignoreCase = true)) length = value.toInt()
        }
        repeat(length) { input.read() }
        requests += "${requestLine.substringBeforeLast(" ")} $authorization"
        val bytes = body.toByteArray()
        val head = "HTTP/1.1 $code Status\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        socket.getOutputStream().apply {
            write(head.toByteArray())
            write(bytes)
            flush()
        }
    }

    @Test
    fun theOnlyDriveScopeIsDriveFile() {
        assertEquals("https://www.googleapis.com/auth/drive.file", DRIVE_FILE_SCOPE)
    }

    @Test
    fun aFolderThatAnswersIsOpen() {
        assertEquals(FolderAccess.OPEN, drive(200, """{"id":"f1","trashed":false}""").access("token", "f1"))
        assertEquals(listOf("GET /drive/v3/files/f1?fields=id,trashed&supportsAllDrives=true Bearer token"), requests)
    }

    @Test
    fun aFolderDeletedOrUnsharedIsLost() {
        assertEquals(FolderAccess.LOST, drive(404, """{"error":{"code":404}}""").access("token", "f1"))
    }

    @Test
    fun aFolderInTheTrashIsLost() {
        assertEquals(FolderAccess.LOST, drive(200, """{"id":"f1","trashed":true}""").access("token", "f1"))
    }

    @Test
    fun anyOtherFailureDecidesNothing() {
        assertEquals(FolderAccess.UNKNOWN, drive(401).access("token", "f1"))
        stop()
        assertEquals(FolderAccess.UNKNOWN, drive(500).access("token", "f1"))
        stop()
        assertEquals(FolderAccess.UNKNOWN, drive(200, "not json").access("token", "f1"))
        stop()
        assertEquals(FolderAccess.UNKNOWN, drive(200, """{"trashed":false}""").access("token", "f1"))
        stop()
        // Drive cannot be reached at all.
        assertEquals(FolderAccess.UNKNOWN, DriveFolders("http://127.0.0.1:1/drive/v3").access("token", "f1"))
    }

    @Test
    fun creatingAFolderGivesItsId() {
        assertEquals("new-id", drive(200, """{"id":"new-id"}""").create("token", JOURNAL_FOLDER_NAME))
        assertEquals(listOf("POST /drive/v3/files?fields=id Bearer token"), requests)
    }

    @Test
    fun aFailedCreationIsAnError() {
        assertTrue(runCatching { drive(403).create("token", JOURNAL_FOLDER_NAME) }.exceptionOrNull() is IOException)
        stop()
        assertTrue(runCatching { drive(200, "{}").create("token", JOURNAL_FOLDER_NAME) }.exceptionOrNull() is IOException)
    }

    @Test
    fun anAnswerWithoutIdHasNoFolderId() {
        assertNull(createdFolderIdOf("{}"))
        assertNull(createdFolderIdOf("""{"id":"  "}"""))
        assertNull(createdFolderIdOf("not json"))
    }
}
