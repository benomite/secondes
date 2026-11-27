package io.github.benomite.secondes

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlin.io.path.createTempDirectory
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
    /** A file of the fake Drive: its folder, its name, its type, its content and when it was last written. */
    private class StoredFile(val parent: String, val name: String, val mimeType: String, var content: String, var modified: Instant)

    /**
     * A local stand-in for the Drive REST calls the journal makes: list a folder's children (`'<id>' in parents`,
     * optionally by `name` or `mimeType`, with their md5Checksum), read a file (`alt=media`), create one in its
     * `parents` (multipart upload), and replace a file's content (media upload with `X-HTTP-Method-Override: PATCH`).
     * Every request is recorded; an upload of a file of type [refusedType] fails with HTTP 500.
     */
    private class FakeDrive {
        val files = linkedMapOf<String, StoredFile>()
        val requests = CopyOnWriteArrayList<String>()

        /** The names of the files created or replaced, in the order Drive received them. */
        val uploads = CopyOnWriteArrayList<String>()

        @Volatile var refusedType: String? = null
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

        fun put(parent: String, name: String, content: String, mimeType: String = "application/json") {
            files["file-${files.size + 1}"] = StoredFile(parent, name, mimeType, content, tick())
        }

        fun content(name: String) = files.values.single { it.name == name }.content

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
                    val type = Regex("""mimeType = '([^']*)'""").find(q)?.groupValues?.get(1)
                    val listed = JSONArray()
                    files.filter { (_, f) -> f.parent == parent && (name == null || f.name == name) && (type == null || f.mimeType == type) }
                        .forEach { (id, f) ->
                            listed.put(
                                JSONObject().put("id", id).put("name", f.name).put("modifiedTime", f.modified.toString())
                                    .put("md5Checksum", md5(f.content)),
                            )
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
                    val type = metadata.getString("mimeType")
                    if (type == refusedType) {
                        send(socket, 500, "")
                    } else {
                        val id = "file-${files.size + 1}"
                        files[id] = StoredFile(parent, metadata.getString("name"), type, parts[2], tick())
                        uploads += metadata.getString("name")
                        send(socket, 200, JSONObject().put("id", id).toString())
                    }
                }
                method == "PATCH" && path.startsWith("/upload/drive/v3/files/") && query["uploadType"] == "media" -> {
                    val file = files[path.removePrefix("/upload/drive/v3/files/")]
                    if (file == null) {
                        send(socket, 404, "")
                    } else if (file.mimeType == refusedType) {
                        send(socket, 500, "")
                    } else {
                        file.content = body
                        file.modified = tick()
                        uploads += file.name
                        send(socket, 200, "{\"id\":\"x\"}")
                    }
                }
                else -> send(socket, 400, "")
            }
        }

        private fun unquoted(text: String) = text.replace("\\'", "'").replace("\\\\", "\\")

        private fun md5(text: String) =
            MessageDigest.getInstance("MD5").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

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

    /** A phone: its own folder of clips, its own cutter of seconds. */
    private class Phone(val cutter: FakeSecondCutter = FakeSecondCutter()) {
        val clips = ClipFiles(createTempDirectory("clips").toFile(), cutter)
        val local = MemoryLocal()
    }

    private val phone = Phone()

    private fun remote(
        folder: String?,
        token: DriveToken = DriveToken { "token" },
        url: String = drive.url,
        on: Phone = phone,
    ) = DriveRemoteJournal({ folder }, token, on.clips, url)

    private fun member(folder: String, on: Phone = Phone()) = JournalSync(on.local, remote(folder, on = on), on.clips, Dispatchers.Unconfined)

    /** What the fake cutter writes for [clip]'s second. */
    private fun secondOf(clip: SharedClip) = "second ${clip.window.startMs}-${clip.window.endMs} of ${clip.videoUri}"

    /** The content of the file [clip] plays on [on]'s phone; it fails when the phone has no such file. */
    private fun played(clip: SharedClip, on: Phone): String = on.clips.fileOf(clip)!!.readText()

    /** A clip file to send, as cut on the phone. */
    private fun clipFile(clip: SharedClip): File = File.createTempFile("clip", ".mp4").apply { writeText(secondOf(clip)) }

    @Test
    fun aDayFilledByAliceAppearsOnBobsPhoneWithHerNameWhenBobOpensTheApp() = runBlocking {
        val alice = member("family")
        val bobsPhone = Phone()
        val bob = member("family", bobsPhone)
        alice.fill(day, clipA, "Alice")
        assertTrue(alice.pushPending())

        bob.syncOnOpen()

        val seen = bob.days.value.getValue(day)
        assertEquals("Alice", seen.author)
        assertFalse(seen.pending)
        // Bob's phone never had Alice's video: the day plays the clip brought from the folder.
        assertEquals(secondOf(clipA), played(seen.clip, bobsPhone))
        assertEquals(SecondWindow(0, 1_000), seen.clip.window)
    }

    @Test
    fun theClipIsInTheFolderNextToTheDaysFileWithTheSameNameAndTheFileOnlyNamesIt() = runBlocking {
        val alice = member("family")
        alice.fill(day, clipA, "Alice")
        assertTrue(alice.pushPending())

        assertEquals(listOf("2026-03-04 Alice.json", "2026-03-04 Alice.mp4"), drive.namesIn("family"))
        assertEquals("video/mp4", drive.files.values.single { it.name == "2026-03-04 Alice.mp4" }.mimeType)
        assertEquals(secondOf(clipA), drive.content("2026-03-04 Alice.mp4"))
        val json = JSONObject(drive.content("2026-03-04 Alice.json"))
        assertEquals("2026-03-04 Alice.mp4", json.getJSONObject("clip").getString("file"))
        assertEquals(setOf("file", "startMs", "endMs"), json.getJSONObject("clip").keys().asSequence().toSet())
    }

    @Test
    fun theClipIsUploadedBeforeTheDaysFile() = runBlocking {
        val alice = member("family")
        alice.fill(day, clipA, "Alice")
        assertTrue(alice.pushPending())
        alice.fill(day, clipB, "Alice")
        assertTrue(alice.pushPending())

        assertEquals(
            listOf("2026-03-04 Alice.mp4", "2026-03-04 Alice.json", "2026-03-04 Alice.mp4", "2026-03-04 Alice.json"),
            drive.uploads,
        )
    }

    @Test
    fun whenTheClipUploadFailsTheDaysFileIsNotUploadedAndTheDayStaysNotSent() = runBlocking {
        drive.refusedType = "video/mp4"
        assertThrows(IOException::class.java) { remote("family").pushDay(JournalDay(day, clipA, "Alice"), clipFile(clipA)) }
        assertTrue(drive.namesIn("family").isEmpty())

        val alice = member("family")
        alice.fill(day, clipA, "Alice")
        assertFalse(alice.pushPending())
        assertTrue(alice.days.value.getValue(day).pending)
        assertTrue(drive.uploads.isEmpty())
        assertTrue(drive.namesIn("family").isEmpty())
    }

    @Test
    fun aFreshInstallJoiningTheFolderGetsTheWholeJournalBackEachDayWithItsSecond() = runBlocking {
        val alice = member("family")
        val bob = member("family")
        alice.fill(day, clipA, "Alice")
        alice.fill(day.plusDays(1), clipB, "Alice")
        bob.fill(day.plusDays(2), clipB, "Bob")
        assertTrue(alice.pushPending())
        assertTrue(bob.pushPending())

        val reinstalled = Phone()
        member("family", reinstalled).let { fresh ->
            fresh.syncOnOpen()
            val days = fresh.days.value
            assertEquals(setOf(day, day.plusDays(1), day.plusDays(2)), days.keys)
            assertEquals(secondOf(clipA), played(days.getValue(day).clip, reinstalled))
            assertEquals(secondOf(clipB), played(days.getValue(day.plusDays(1)).clip, reinstalled))
            assertEquals(secondOf(clipB), played(days.getValue(day.plusDays(2)).clip, reinstalled))
        }
    }

    @Test
    fun aDayStillPlaysAfterItsVideoIsDeletedFromTheGallery() = runBlocking {
        val alicesPhone = Phone()
        val alice = member("family", alicesPhone)
        alice.fill(day, clipA, "Alice")
        assertTrue(alice.pushPending())

        alicesPhone.cutter.deleted += clipA.videoUri
        alice.syncOnOpen()

        assertEquals(secondOf(clipA), played(alice.days.value.getValue(day).clip, alicesPhone))
    }

    @Test
    fun aClipAlreadyOnThePhoneIsNotDownloadedAgain() = runBlocking {
        val alice = member("family")
        val bob = member("family")
        alice.fill(day, clipA, "Alice")
        assertTrue(alice.pushPending())
        assertTrue(bob.pull())
        drive.requests.clear()

        assertTrue(alice.pull())
        assertTrue(bob.pull())

        val mp4 = drive.files.entries.single { it.value.name.endsWith(".mp4") }.key
        assertFalse(drive.requests.toString(), drive.requests.any { it.startsWith("GET /drive/v3/files/$mp4") })
    }

    @Test
    fun aDayWhoseClipIsNotInTheFolderIsLeftOut() {
        remote("family").pushDay(JournalDay(day, clipA, "Alice"), clipFile(clipA))
        drive.files.values.removeIf { it.name.endsWith(".mp4") }

        assertTrue(remote("family").fetchDays().isEmpty())
    }

    @Test
    fun aDayWrittenBeforeTheClipsWereInTheFolderIsStillRead() {
        drive.put("family", "2026-03-04 Alice.json", encodeDay(JournalDay(day, clipA, "Alice")).toString())

        assertEquals(listOf(JournalDay(day, clipA, "Alice")), remote("family").fetchDays())
    }

    @Test
    fun eachMemberWritesItsOwnFileForADaySoNoOneOverwritesTheOther() = runBlocking {
        val alicesPhone = Phone()
        val alice = member("family", alicesPhone)
        val bob = member("family")
        alice.fill(day, clipA, "Alice")
        alice.pushPending()
        bob.fill(day.plusDays(1), clipB, "Bob")
        bob.fill(day, clipB, "Bob")
        bob.pushPending()

        assertEquals(
            listOf(
                "2026-03-04 Alice.json", "2026-03-04 Alice.mp4", "2026-03-04 Bob.json", "2026-03-04 Bob.mp4",
                "2026-03-05 Bob.json", "2026-03-05 Bob.mp4",
            ),
            drive.namesIn("family"),
        )
        alice.syncOnOpen()
        // Bob's file for the 4th, written last, is that day; Alice's own files are still in the folder, untouched.
        assertEquals("Bob", alice.days.value.getValue(day).author)
        assertEquals(secondOf(clipB), played(alice.days.value.getValue(day).clip, alicesPhone))
        assertEquals("Bob", alice.days.value.getValue(day.plusDays(1)).author)
        assertEquals(secondOf(clipB), played(alice.days.value.getValue(day.plusDays(1)).clip, alicesPhone))
        val aliceFile = JSONObject(drive.content("2026-03-04 Alice.json"))
        assertEquals("2026-03-04 Alice.mp4", aliceFile.getJSONObject("clip").getString("file"))
        assertEquals(secondOf(clipA), drive.content("2026-03-04 Alice.mp4"))
    }

    @Test
    fun aMemberFillingItsDayAgainReplacesItsOwnFileWithoutAddingAnother() = runBlocking {
        val alice = member("family")
        alice.fill(day, clipA, "Alice")
        alice.pushPending()
        alice.fill(day, clipB, "Alice")
        alice.pushPending()

        assertEquals(listOf("2026-03-04 Alice.json", "2026-03-04 Alice.mp4"), drive.namesIn("family"))
        val other = Phone()
        val seen = member("family", other).also { it.pull() }.days.value.getValue(day)
        assertEquals("Alice", seen.author)
        assertEquals(secondOf(clipB), played(seen.clip, other))
    }

    @Test
    fun theFileIsPlainReadableJsonWithTheAuthorsName() {
        remote("family").pushDay(JournalDay(day, clipA, "Léa"), clipFile(clipA))

        val file = drive.files.values.single { it.mimeType == "application/json" }
        assertEquals("2026-03-04 Léa.json", file.name)
        val json = JSONObject(file.content)
        assertEquals("2026-03-04", json.getString("date"))
        assertEquals("Léa", json.getString("author"))
        assertEquals("2026-03-04 Léa.mp4", json.getJSONObject("clip").getString("file"))
        assertFalse(json.getJSONObject("clip").has("videoUri"))
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
        assertEquals(listOf("2026-03-06 Carol.json", "2026-03-06 Carol.mp4"), drive.namesIn("other-family"))
        assertEquals(listOf("2026-03-04 Alice.json", "2026-03-04 Alice.mp4"), drive.namesIn("family"))
        // Every listing Carol's phone made asks for the children of her folder only.
        val listings = drive.requests.filter { it.startsWith("GET /drive/v3/files ") }
        assertTrue(listings.isNotEmpty())
        assertTrue(listings.toString(), listings.all { it.contains("'other-family' in parents") })
    }

    @Test
    fun aFileOfTheFolderThatIsNotAWholeDayIsLeftOut() {
        drive.put("family", "notes.json", "{\"hello\": 1}")
        drive.put("family", "broken.json", "not json")
        remote("family").pushDay(JournalDay(day, clipA, "Alice"), clipFile(clipA))

        val days = remote("family").fetchDays()
        assertEquals(listOf(day to "Alice"), days.map { it.date to it.author })
        assertEquals(secondOf(clipA), played(days.single().clip, phone))
    }

    @Test
    fun offlineTheDayStaysOnThePhoneNotSentThenGoesAtTheNextSync() = runBlocking {
        val unreachable = ServerSocket(0, 0, InetAddress.getLoopbackAddress()).use { "http://127.0.0.1:${it.localPort}" }
        val offline = JournalSync(phone.local, remote("family", url = unreachable), phone.clips, Dispatchers.Unconfined)
        offline.fill(day, clipA, "Alice")

        assertFalse(offline.pushPending())
        offline.syncOnOpen()
        assertTrue(phone.local.saved.getValue(day).pending)
        assertTrue(drive.files.isEmpty())

        // The network is back: the app's next sync sends the day kept on the phone, the clip first.
        val online = JournalSync(phone.local, remote("family"), phone.clips, Dispatchers.Unconfined)
        online.syncOnOpen()
        assertFalse(phone.local.saved.getValue(day).pending)
        assertEquals(listOf("2026-03-04 Alice.json", "2026-03-04 Alice.mp4"), drive.namesIn("family"))
        assertEquals(listOf("2026-03-04 Alice.mp4", "2026-03-04 Alice.json"), drive.uploads)
    }

    @Test
    fun withNoFolderChosenOrNoGoogleAccessEveryCallFailsAndNothingIsSent() {
        val noAccess = remote("family", DriveToken { throw IOException("not signed in") })
        listOf(remote(null), remote(""), noAccess).forEach { journal ->
            assertThrows(IOException::class.java) { journal.fetchDays() }
            assertThrows(IOException::class.java) { journal.pushDay(JournalDay(day, clipA, "Alice"), clipFile(clipA)) }
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
        assertEquals("2026-03-04 Léa.mp4", dayClipName(day, "Léa"))
        assertEquals("2026-03-04 A_B.mp4", dayClipName(day, "A/B"))
    }
}
