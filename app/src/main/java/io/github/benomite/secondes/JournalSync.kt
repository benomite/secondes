package io.github.benomite.secondes

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * The shared journal, offline first: a day is saved on the phone as soon as it is filled, marked pending, its second
 * cut into a standalone clip kept on the phone ([ClipFiles]), and sent with that clip to the family's journal
 * ([DriveRemoteJournal]) when it can be. The last write of a day is that day; a pull brings the other members' days,
 * their clips with them. Every change is saved whole: a day always has its clip and its author.
 */
class JournalSync(
    private val local: LocalJournal,
    private val remote: RemoteJournal,
    private val clips: ClipFiles,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val lock = Any()
    private val syncing = Mutex()
    private val state = MutableStateFlow(local.load())

    /** The journal's days as this phone knows them. */
    val days: StateFlow<Map<LocalDate, JournalDay>> = state.asStateFlow()

    /**
     * Fills [date] with [clip] by [author], replacing whoever filled it before; the day is pending until pushed.
     * False when the day could not be saved on the phone: then nothing changed.
     */
    fun fill(date: LocalDate, clip: SharedClip, author: String): Boolean =
        update { it + (date to JournalDay(date, clip, author, pending = true)) }

    /** What the app does when it opens: send the days filled offline, then bring the other members' days. */
    suspend fun syncOnOpen() {
        pushPending()
        pull()
    }

    /**
     * Sends the pending days, each with its clip, cut here first when it is not yet (offline too). True when none
     * is left pending; false when the server or the phone failed, or a day's second could not be cut.
     */
    suspend fun pushPending(): Boolean = syncing.withLock {
        for (date in state.value.values.filter { it.pending }.map { it.date }) {
            val day = withKeptClip(date) ?: continue
            val clip = clips.fileOf(day.clip) ?: continue
            val stored = try {
                withContext(io) { remote.pushDay(day.copy(pending = false), clip) }
            } catch (e: IOException) {
                return@withLock false
            }
            // A day filled again while it was being sent stays pending: its newer clip is sent next.
            val saved = update { current ->
                if (current[day.date] == day) current + (day.date to stored.copy(date = day.date, pending = false)) else current
            }
            if (!saved) return@withLock false
        }
        state.value.values.none { it.pending }
    }

    /**
     * [date]'s pending day with its clip kept on the phone: its second is cut now when it is not yet, and the day
     * saved with the cut clip, still pending. Null when the second cannot be cut (its video unreadable), or the day
     * cannot be saved, or it changed meanwhile: it stays pending, shown as not sent.
     */
    private suspend fun withKeptClip(date: LocalDate): JournalDay? {
        val day = state.value[date]?.takeIf { it.pending } ?: return null
        if (clips.fileOf(day.clip) != null) return day
        val kept = try {
            withContext(io) { clips.keep(day.clip) }
        } catch (e: IOException) {
            return null
        }
        val next = day.copy(clip = kept)
        val saved = update { current -> if (current[date] == day) current + (date to next) else current }
        return next.takeIf { saved && state.value[date] == next }
    }

    /**
     * Brings the server's days, each with its clip. A day pending here keeps its clip: once pushed it is the later
     * write, which the server keeps. False when the server or the phone failed: then the days here are left as they were.
     */
    suspend fun pull(): Boolean = syncing.withLock {
        val remoteDays = try {
            withContext(io) { remote.fetchDays() }
        } catch (e: IOException) {
            return@withLock false
        }
        update { current ->
            val merged = current.toMutableMap()
            remoteDays.forEach { day ->
                val here = current[day.date]
                if (here?.pending != true) {
                    // The day sent from here comes back as the same clip: it keeps the way back to the video it was cut from.
                    val source = here?.clip?.takeIf { it.videoUri == day.clip.videoUri && here.author == day.author }?.source
                    merged[day.date] = day.copy(clip = day.clip.copy(source = day.clip.source ?: source), pending = false)
                }
            }
            merged
        }
    }

    /** Saves [change] of the days, whole, then shows it; a save that fails changes nothing. */
    private fun update(change: (Map<LocalDate, JournalDay>) -> Map<LocalDate, JournalDay>): Boolean =
        synchronized(lock) {
            val next = change(state.value)
            if (next == state.value) return true
            try {
                local.save(next)
            } catch (e: IOException) {
                return false
            }
            state.value = next
            true
        }
}

/** The Google access token for the Drive calls; throws [IOException] when there is none (not signed in, offline). */
fun interface DriveToken {
    fun get(): String
}

/** The readable name of [author]'s JSON file for [date] in the journal's folder, e.g. `2026-11-17 Léa.json`. */
fun dayFileName(date: LocalDate, author: String): String = "${dayBaseName(date, author)}.json"

/** The name of [author]'s one-second clip for [date], next to its JSON file in the journal's folder, e.g. `2026-11-17 Léa.mp4`. */
fun dayClipName(date: LocalDate, author: String): String = "${dayBaseName(date, author)}.mp4"

private fun dayBaseName(date: LocalDate, author: String): String =
    "$date ${author.replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""), "_")}"

/**
 * The shared journal in the family's Google Drive folder: per member per day, a one-second clip named by
 * [dayClipName] and a plain JSON file named by [dayFileName], holding the day as [encodeDayFile] writes it, author
 * included, pointing to that clip. The clip is uploaded first, the JSON file only once it is there, so a day is never
 * in the folder without its second. A member writes only its own files, so no one overwrites another's day; when
 * several members filled the same date, the JSON file written last is the day. Reading a day downloads its clip into
 * [clips], whoever its author. Every request names the folder chosen on this phone ([folderId]): listings ask for its
 * children only, and new files are created in it, so another family's folder is never read nor written.
 * With no folder chosen, no access or no network, every call throws [IOException]: the days stay pending.
 */
class DriveRemoteJournal(
    private val folderId: () -> String?,
    private val token: DriveToken,
    private val clips: ClipFiles,
    private val baseUrl: String = "https://www.googleapis.com",
) : RemoteJournal {
    override fun fetchDays(): List<JournalDay> {
        val folder = chosenFolder()
        val accessToken = token.get()
        // The clips of the folder, by name; of two files of the same name, the one written last.
        val clipFiles = mutableMapOf<String, DriveFile>()
        for (file in list(accessToken, "'${quoted(folder)}' in parents and mimeType = '$MP4' and trashed = false")) {
            val known = clipFiles[file.name]
            if (known == null || file.modifiedTime > known.modifiedTime) clipFiles[file.name] = file
        }
        val latest = mutableMapOf<LocalDate, Pair<Instant, Found>>()
        for (file in list(accessToken, "'${quoted(folder)}' in parents and mimeType = '$JSON' and trashed = false")) {
            // A file of the folder that is not a whole day (a note, a half-written copy), or a day whose clip is
            // not in the folder, is left out.
            val found = foundIn(text(accessToken, "GET", "drive/v3/files/${encoded(file.id)}?alt=media&supportsAllDrives=true"), clipFiles)
                ?: continue
            val known = latest[found.date]
            if (known == null || file.modifiedTime > known.first) latest[found.date] = file.modifiedTime to found
        }
        // A clip that cannot be downloaded fails the whole call: the days here stay as they were, and the clips
        // already brought are kept, so the next pull goes on from there.
        return latest.values.map { (_, found) ->
            when (found) {
                is Found.Legacy -> found.day
                is Found.WithClip -> JournalDay(found.day.date, clipOf(accessToken, found.clip, found.day.window), found.day.author)
            }
        }
    }

    override fun pushDay(day: JournalDay, clip: File): JournalDay {
        val folder = chosenFolder()
        val accessToken = token.get()
        val video = clip.readBytes()
        // The clip first, then the day's file pointing to it: when the clip cannot be written, the day is not either.
        write(accessToken, folder, dayClipName(day.date, day.author), MP4, video)
        write(accessToken, folder, dayFileName(day.date, day.author), JSON, encodeDayFile(day).toString(2).toByteArray())
        return day
    }

    /** The clip [file] of the folder, kept on this phone: downloaded unless the same clip is already here. */
    private fun clipOf(accessToken: String, file: DriveFile, window: SecondWindow): SharedClip =
        clips.withMd5(file.md5, window)
            ?: clips.store(request(accessToken, "GET", "drive/v3/files/${encoded(file.id)}?alt=media&supportsAllDrives=true"), window)

    /** Writes [content] as this member's file [name] of [folder]: created there, or its own file of that name replaced. */
    private fun write(accessToken: String, folder: String, name: String, mimeType: String, content: ByteArray) {
        val own = list(accessToken, "'${quoted(folder)}' in parents and name = '${quoted(name)}' and trashed = false").firstOrNull()
        if (own == null) {
            // A new file, created in the chosen folder: its metadata and its content in one multipart upload.
            val metadata = JSONObject().put("name", name).put("mimeType", mimeType).put("parents", JSONArray().put(folder))
            val head = "--$BOUNDARY\r\nContent-Type: $JSON; charset=UTF-8\r\n\r\n$metadata\r\n" +
                "--$BOUNDARY\r\nContent-Type: $mimeType\r\n\r\n"
            request(
                accessToken, "POST", "upload/drive/v3/files?uploadType=multipart&fields=id&supportsAllDrives=true",
                "multipart/related; boundary=$BOUNDARY",
                head.toByteArray() + content + "\r\n--$BOUNDARY--\r\n".toByteArray(),
            )
        } else {
            // This member's own file gets the new content; HttpURLConnection cannot send PATCH itself.
            request(
                accessToken, "POST", "upload/drive/v3/files/${encoded(own.id)}?uploadType=media&fields=id&supportsAllDrives=true",
                mimeType, content, methodOverride = "PATCH",
            )
        }
    }

    private fun chosenFolder(): String =
        folderId()?.takeIf { it.isNotBlank() } ?: throw IOException("no journal folder is chosen on this phone")

    private class DriveFile(val id: String, val name: String, val modifiedTime: Instant, val md5: String?)

    /** A day read from a JSON file of the folder. */
    private sealed interface Found {
        val date: LocalDate

        /** A day pointing to its clip, [clip], in the folder. */
        class WithClip(val day: DayFile, val clip: DriveFile) : Found {
            override val date: LocalDate get() = day.date
        }

        /** A day written before the clips were in the folder: it names a video on its author's phone. */
        class Legacy(val day: JournalDay) : Found {
            override val date: LocalDate get() = day.date
        }
    }

    /** The files matching [query], every page of them; throws [IOException] when Drive's answer cannot be read. */
    private fun list(accessToken: String, query: String): List<DriveFile> {
        val files = mutableListOf<DriveFile>()
        var pageToken: String? = null
        do {
            val path = "drive/v3/files?q=${encoded(query)}&fields=${encoded("nextPageToken,files(id,name,modifiedTime,md5Checksum)")}" +
                "&pageSize=1000&supportsAllDrives=true&includeItemsFromAllDrives=true" +
                (pageToken?.let { "&pageToken=${encoded(it)}" } ?: "")
            val answer = try {
                JSONObject(text(accessToken, "GET", path))
            } catch (e: JSONException) {
                throw IOException("Drive answered a file list that cannot be read", e)
            }
            val page = answer.optJSONArray("files") ?: throw IOException("Drive answered no file list")
            for (i in 0 until page.length()) {
                val file = page.optJSONObject(i) ?: continue
                val id = file.optString("id").takeIf { it.isNotBlank() } ?: continue
                val md5 = file.optString("md5Checksum").takeIf { it.isNotBlank() }
                files += DriveFile(id, file.optString("name"), modifiedTimeOf(file.optString("modifiedTime")), md5)
            }
            pageToken = answer.optString("nextPageToken").takeIf { it.isNotBlank() }
        } while (pageToken != null)
        return files
    }

    private fun text(accessToken: String, method: String, path: String): String =
        request(accessToken, method, path).toString(Charsets.UTF_8)

    private fun request(
        accessToken: String,
        method: String,
        path: String,
        contentType: String? = null,
        body: ByteArray? = null,
        methodOverride: String? = null,
    ): ByteArray {
        val connection = URL("${baseUrl.trimEnd('/')}/$path").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            methodOverride?.let { connection.setRequestProperty("X-HTTP-Method-Override", it) }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", contentType)
                connection.outputStream.use { it.write(body) }
            }
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("Drive answered HTTP $code to ${methodOverride ?: method} ${path.substringBefore('?')}")
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000
        const val JSON = "application/json"
        const val MP4 = "video/mp4"
        const val BOUNDARY = "secondes-day"

        fun encoded(text: String): String = URLEncoder.encode(text, "UTF-8").replace("+", "%20")

        /** [text] inside a Drive query's single quotes. */
        fun quoted(text: String): String = text.replace("\\", "\\\\").replace("'", "\\'")

        /**
         * The day a JSON file holds, as [encodeDayFile] writes it, with its clip among [clipFiles]; or a day written
         * before, as [encodeDay] writes it. Null when it is not a whole day, or its clip is not in the folder.
         */
        fun foundIn(text: String, clipFiles: Map<String, DriveFile>): Found? {
            val json = try {
                JSONObject(text)
            } catch (e: JSONException) {
                return null
            }
            val day = decodeDayFile(json)
            if (day != null) return clipFiles[day.clipFile]?.let { Found.WithClip(day, it) }
            return decodeDay(json)?.copy(pending = false)?.let { Found.Legacy(it) }
        }

        /** Drive's modification time; a file without a readable one counts as the oldest. */
        fun modifiedTimeOf(text: String): Instant = try {
            Instant.parse(text)
        } catch (e: DateTimeParseException) {
            Instant.EPOCH
        }
    }
}
