package io.github.benomite.secondes

import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.URLDecoder
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// org.json is only a stub in a plain JVM test: Robolectric brings the real one.
@RunWith(RobolectricTestRunner::class)
class DriveRemoteJournalTest {
    /** A file of the fake Drive: its folder, its name, its content and when it was last written. */
    private class StoredFile(val parent: String, val name: String, var content: String, var modified: Instant)

    /**
     * A local stand-in for the Drive REST calls the journal makes: list a folder's children (`'<id>' in parents`,
     * optionally by `name`), read a file (`alt=media`), create one in its `parents` (multipart upload), and replace
     * a file's content (media upload with `X-HTTP-Method-Override: PATCH`). Every request is recorded.
     */
    private class FakeDrive {
        val files = linkedMapOf<String, StoredFile>()
        val requests = CopyOnWriteArrayList<String>()
        private var clock = Instant.parse("2026-11-17T10:00:00Z")
        private val server = ServerSocket(0, 0, InetAddress.getLoopbackAddress())

        val url get() = "http://127.0.0.1:${server.localPort}"

        init {
            thread(isDaemon = true) {
                while (!server.isClosed) {
                    val socket = try {
                        server.accept()
                    } catch (e: IOException) {
                        break
                    }
                    socket.use { answer(it) }
                }
            }
        }

        fun stop() = server.close()

        fun put(parent: String, name: String, content: String) {
            files["file-${files.size + 1}"] = StoredFile(parent, name, content, tick())
        }

        fun namesIn(parent: String) = files.values.filter { it.parent == parent }.map { it.name }.sorted()

        private fun tick(): Instant = clock.also { clock = clock.plusSeconds(1) }

        /** One HTTP request read off [socket], answered on it. */
        private fun answer(socket: Socket) {
            val input = socket.getInputStream()
            val (requestMethod, target) = readLine(input).split(" ")
            val headers = mutableMapOf<String, String>()
            while (true) {
                val header = readLine(input)
                if (header.isEmpty()) break
                headers[header.substringBefore(":").trim().lowercase()] = header.substringAfter(":").trim()
            }
            val length = headers["content-length"]?.toInt() ?: 0
            val bytes = ByteArray(length)
            var read = 0
            while (read < length) read += input.read(bytes, read, length - read)
            val body = bytes.toString(Charsets.UTF_8)
            val uri = URI(target)
            val path = uri.rawPath
            val query = uri.rawQuery.orEmpty().split("&").filter { it.isNotEmpty() }
                .associate { it.substringBefore("=") to URLDecoder.decode(it.substringAfter("="), "UTF-8") }
            val method = headers["x-http-method-override"] ?: requestMethod
            requests += "$method $path ${query["q"].orEmpty()}"
            when {
                method == "GET" && path == "/drive/v3/files" -> {
                    val q = query.getValue("q")
                    val parent = Regex("""'((?:[^'\\]|\\.)*)' in parents""").find(q)?.groupValues?.get(1)?.let(::unquoted)
                    val name = Regex("""name = '((?:[^'\\]|\\.)*)'""").find(q)?.groupValues?.get(1)?.let(::unquoted)
                    val listed = JSONArray()
                    files.filter { (_, f) -> f.parent == parent && (name == null || f.name == name) }.forEach { (id, f) ->
                        listed.put(JSONObject().put("id", id).put("modifiedTime", f.modified.toString()))
                    }
                    send(socket, 200, JSONObject().put("files", listed).toString())
                }
                method == "GET" && path.startsWith("/drive/v3/files/") && query["alt"] == "media" -> {
                    val file = files[path.removePrefix("/drive/v3/files/")]
                    if (file == null) send(socket, 404, "") else send(socket, 200, file.content)
                }
                method == "POST" && path == "/upload/drive/v3/files" && query["uploadType"] == "multipart" -> {
                    val boundary = headers.getValue("content-type").substringAfter("boundary=")
                    val parts = body.split("--$boundary").map { it.substringAfter("\r\n\r\n").removeSuffix("\r\n") }
                    val metadata = JSONObject(parts[1])
                    val parent = metadata.getJSONArray("parents").getString(0)
                    val id = "file-${files.size + 1}"
                    files[id] = StoredFile(parent, metadata.getString("name"), parts[2], tick())
                    send(socket, 200, JSONObject().put("id", id).toString())
                }
                method == "PATCH" && path.startsWith("/upload/drive/v3/files/") && query["uploadType"] == "media" -> {
                    val file = files[path.removePrefix("/upload/drive/v3/files/")]
                    if (file == null) {
                        send(socket, 404, "")
                    } else {
                        file.content = body
                        file.modified = tick()
                        send(socket, 200, "{\"id\":\"x\"}")
                    }
                }
                else -> send(socket, 400, "")
            }
        }

        private fun unquoted(text: String) = text.replace("\\'", "'").replace("\\\\", "\\")

        private fun readLine(input: InputStream): String {
            val line = StringBuilder()
            while (true) {
                val c = input.read()
                if (c == -1 || c == '\n'.code) break
                if (c != '\r'.code) line.append(c.toChar())
            }
            return line.toString()
        }

        private fun send(socket: Socket, code: Int, text: String) {
            val bytes = text.toByteArray()
            val head = "HTTP/1.1 $code X\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
            socket.getOutputStream().apply {
                write(head.toByteArray())
                write(bytes)
                flush()
            }
        }
    }

    private class MemoryLocal : LocalJournal {
        var saved: Map<LocalDate, JournalDay> = emptyMap()

        override fun load() = saved

        override fun save(days: Map<LocalDate, JournalDay>) {
            saved = days
        }
    }

    private val drive = FakeDrive()
    private val day = LocalDate.of(2026, 3, 4)
    private val clipA = SharedClip("content://alice/1", SecondWindow(2_000))
    private val clipB = SharedClip("content://bob/7", SecondWindow(5_000))

    @After
    fun stop() = drive.stop()

    private fun remote(folder: String?, token: DriveToken = DriveToken { "token" }, url: String = drive.url) =
        DriveRemoteJournal({ folder }, token, url)

    private fun member(folder: String) = JournalSync(MemoryLocal(), remote(folder), Dispatchers.Unconfined)

    @Test
    fun aDayFilledByAliceAppearsOnBobsPhoneWithHerNameWhenBobOpensTheApp() = runBlocking {
        val alice = member("family")
        val bob = member("family")
        alice.fill(day, clipA, "Alice")
        assertTrue(alice.pushPending())

        bob.syncOnOpen()

        assertEquals(JournalDay(day, clipA, "Alice"), bob.days.value[day])
    }

    @Test
    fun eachMemberWritesItsOwnFileForADaySoNoOneOverwritesTheOther() = runBlocking {
        val alice = member("family")
        val bob = member("family")
        alice.fill(day, clipA, "Alice")
        alice.pushPending()
        bob.fill(day.plusDays(1), clipB, "Bob")
        bob.fill(day, clipB, "Bob")
        bob.pushPending()

        assertEquals(listOf("2026-03-04 Alice.json", "2026-03-04 Bob.json", "2026-03-05 Bob.json"), drive.namesIn("family"))
        alice.syncOnOpen()
        // Bob's file for the 4th, written last, is that day; Alice's own file is still in the folder, untouched.
        assertEquals(JournalDay(day, clipB, "Bob"), alice.days.value[day])
        assertEquals(JournalDay(day.plusDays(1), clipB, "Bob"), alice.days.value[day.plusDays(1)])
        val aliceFile = JSONObject(drive.files.values.single { it.name == "2026-03-04 Alice.json" }.content)
        assertEquals("content://alice/1", aliceFile.getJSONObject("clip").getString("videoUri"))
    }

    @Test
    fun aMemberFillingItsDayAgainReplacesItsOwnFileWithoutAddingAnother() = runBlocking {
        val alice = member("family")
        alice.fill(day, clipA, "Alice")
        alice.pushPending()
        alice.fill(day, clipB, "Alice")
        alice.pushPending()

        assertEquals(listOf("2026-03-04 Alice.json"), drive.namesIn("family"))
        assertEquals(JournalDay(day, clipB, "Alice"), member("family").also { it.pull() }.days.value[day])
    }

    @Test
    fun theFileIsPlainReadableJsonWithTheAuthorsName() {
        remote("family").pushDay(JournalDay(day, clipA, "Léa"))

        val file = drive.files.values.single()
        assertEquals("2026-03-04 Léa.json", file.name)
        val json = JSONObject(file.content)
        assertEquals("2026-03-04", json.getString("date"))
        assertEquals("Léa", json.getString("author"))
        assertEquals("content://alice/1", json.getJSONObject("clip").getString("videoUri"))
        assertFalse(json.has("pending"))
    }

    @Test
    fun anotherFamilysFolderIsNeverReadNorWritten() = runBlocking {
        val alice = member("family")
        alice.fill(day, clipA, "Alice")
        alice.pushPending()
        drive.requests.clear()

        val carol = member("other-family")
        carol.fill(day.plusDays(2), clipB, "Carol")
        carol.syncOnOpen()

        assertEquals(setOf(day.plusDays(2)), carol.days.value.keys)
        assertEquals(listOf("2026-03-06 Carol.json"), drive.namesIn("other-family"))
        assertEquals(listOf("2026-03-04 Alice.json"), drive.namesIn("family"))
        // Every listing Carol's phone made asks for the children of her folder only.
        val listings = drive.requests.filter { it.startsWith("GET /drive/v3/files ") }
        assertTrue(listings.isNotEmpty())
        assertTrue(listings.toString(), listings.all { it.contains("'other-family' in parents") })
    }

    @Test
    fun aFileOfTheFolderThatIsNotAWholeDayIsLeftOut() {
        drive.put("family", "notes.json", "{\"hello\": 1}")
        drive.put("family", "broken.json", "not json")
        remote("family").pushDay(JournalDay(day, clipA, "Alice"))

        assertEquals(listOf(JournalDay(day, clipA, "Alice")), remote("family").fetchDays())
    }

    @Test
    fun offlineTheDayStaysOnThePhoneNotSentThenGoesAtTheNextSync() = runBlocking {
        val unreachable = ServerSocket(0, 0, InetAddress.getLoopbackAddress()).use { "http://127.0.0.1:${it.localPort}" }
        val local = MemoryLocal()
        val offline = JournalSync(local, remote("family", url = unreachable), Dispatchers.Unconfined)
        offline.fill(day, clipA, "Alice")

        assertFalse(offline.pushPending())
        offline.syncOnOpen()
        assertTrue(local.saved.getValue(day).pending)
        assertTrue(drive.files.isEmpty())

        // The network is back: the app's next sync sends the day kept on the phone.
        val online = JournalSync(local, remote("family"), Dispatchers.Unconfined)
        online.syncOnOpen()
        assertFalse(local.saved.getValue(day).pending)
        assertEquals(listOf("2026-03-04 Alice.json"), drive.namesIn("family"))
    }

    @Test
    fun withNoFolderChosenOrNoGoogleAccessEveryCallFailsAndNothingIsSent() {
        val noAccess = remote("family", DriveToken { throw IOException("not signed in") })
        listOf(remote(null), remote(""), noAccess).forEach { journal ->
            assertThrows(IOException::class.java) { journal.fetchDays() }
            assertThrows(IOException::class.java) { journal.pushDay(JournalDay(day, clipA, "Alice")) }
        }
        assertTrue(drive.requests.isEmpty())
    }

    @Test
    fun aDriveErrorFailsTheCallInsteadOfReadingAnEmptyFolder() {
        val answers = remote("family", DriveToken { "token" }, "${drive.url}/missing")
        assertThrows(IOException::class.java) { answers.fetchDays() }
    }

    @Test
    fun aFileNameKeepsTheAuthorReadableAndSafeOnAnyDisk() {
        assertEquals("2026-03-04 Léa.json", dayFileName(day, "Léa"))
        assertEquals("2026-03-04 A_B.json", dayFileName(day, "A/B"))
    }
}
