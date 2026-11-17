package io.github.benomite.secondes

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
 * The shared journal, offline first: a day is saved on the phone as soon as it is filled, marked pending,
 * and sent to the family's journal ([DriveRemoteJournal]) when it can be. The last write of a day is that day; a pull
 * brings the other members' days. Every change is saved whole: a day always has its clip and its author.
 */
class JournalSync(
    private val local: LocalJournal,
    private val remote: RemoteJournal,
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

    /** Sends the pending days. True when none is left pending; false when the server or the phone failed. */
    suspend fun pushPending(): Boolean = syncing.withLock {
        for (day in state.value.values.filter { it.pending }) {
            val stored = try {
                withContext(io) { remote.pushDay(day.copy(pending = false)) }
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
     * Brings the server's days. A day pending here keeps its clip: once pushed it is the later write, which the
     * server keeps. False when the server or the phone failed: then the days here are left as they were.
     */
    suspend fun pull(): Boolean = syncing.withLock {
        val remoteDays = try {
            withContext(io) { remote.fetchDays() }
        } catch (e: IOException) {
            return@withLock false
        }
        update { current ->
            val merged = current.toMutableMap()
            remoteDays.forEach { day -> if (current[day.date]?.pending != true) merged[day.date] = day.copy(pending = false) }
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

/** The readable name of [author]'s file for [date] in the journal's folder, e.g. `2026-11-17 Léa.json`. */
fun dayFileName(date: LocalDate, author: String): String =
    "$date ${author.replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""), "_")}.json"

/**
 * The shared journal in the family's Google Drive folder: one plain JSON file per member per day, named by
 * [dayFileName] and holding the day as [encodeDay] writes it, author included. A member writes only its own
 * files, so no one overwrites another's day; when several members filled the same date, the file written last
 * is the day. Every request names the folder chosen on this phone ([folderId]): listings ask for its children
 * only, and new files are created in it, so another family's folder is never read nor written.
 * With no folder chosen, no access or no network, every call throws [IOException]: the days stay pending.
 */
class DriveRemoteJournal(
    private val folderId: () -> String?,
    private val token: DriveToken,
    private val baseUrl: String = "https://www.googleapis.com",
) : RemoteJournal {
    override fun fetchDays(): List<JournalDay> {
        val folder = chosenFolder()
        val accessToken = token.get()
        val latest = mutableMapOf<LocalDate, Pair<Instant, JournalDay>>()
        for (file in list(accessToken, "'${quoted(folder)}' in parents and mimeType = '$JSON' and trashed = false")) {
            // A file of the folder that is not a whole day (a note, a half-written copy) is left out.
            val day = dayOf(request(accessToken, "GET", "drive/v3/files/${encoded(file.id)}?alt=media&supportsAllDrives=true"))
                ?: continue
            val known = latest[day.date]
            if (known == null || file.modifiedTime > known.first) latest[day.date] = file.modifiedTime to day
        }
        return latest.values.map { it.second }
    }

    override fun pushDay(day: JournalDay): JournalDay {
        val folder = chosenFolder()
        val accessToken = token.get()
        val name = dayFileName(day.date, day.author)
        val content = encodeDay(day).toString(2).toByteArray()
        val own = list(accessToken, "'${quoted(folder)}' in parents and name = '${quoted(name)}' and trashed = false").firstOrNull()
        if (own == null) {
            // A new file, created in the chosen folder: its metadata and its content in one multipart upload.
            val metadata = JSONObject().put("name", name).put("mimeType", JSON).put("parents", JSONArray().put(folder))
            val head = "--$BOUNDARY\r\nContent-Type: $JSON; charset=UTF-8\r\n\r\n$metadata\r\n" +
                "--$BOUNDARY\r\nContent-Type: $JSON\r\n\r\n"
            request(
                accessToken, "POST", "upload/drive/v3/files?uploadType=multipart&fields=id&supportsAllDrives=true",
                "multipart/related; boundary=$BOUNDARY",
                head.toByteArray() + content + "\r\n--$BOUNDARY--\r\n".toByteArray(),
            )
        } else {
            // This member's own file for the day gets the new content; HttpURLConnection cannot send PATCH itself.
            request(
                accessToken, "POST", "upload/drive/v3/files/${encoded(own.id)}?uploadType=media&fields=id&supportsAllDrives=true",
                JSON, content, methodOverride = "PATCH",
            )
        }
        return day
    }

    private fun chosenFolder(): String =
        folderId()?.takeIf { it.isNotBlank() } ?: throw IOException("no journal folder is chosen on this phone")

    private class DriveFile(val id: String, val modifiedTime: Instant)

    /** The files matching [query], every page of them; throws [IOException] when Drive's answer cannot be read. */
    private fun list(accessToken: String, query: String): List<DriveFile> {
        val files = mutableListOf<DriveFile>()
        var pageToken: String? = null
        do {
            val path = "drive/v3/files?q=${encoded(query)}&fields=${encoded("nextPageToken,files(id,modifiedTime)")}" +
                "&pageSize=1000&supportsAllDrives=true&includeItemsFromAllDrives=true" +
                (pageToken?.let { "&pageToken=${encoded(it)}" } ?: "")
            val answer = try {
                JSONObject(request(accessToken, "GET", path))
            } catch (e: JSONException) {
                throw IOException("Drive answered a file list that cannot be read", e)
            }
            val page = answer.optJSONArray("files") ?: throw IOException("Drive answered no file list")
            for (i in 0 until page.length()) {
                val file = page.optJSONObject(i) ?: continue
                val id = file.optString("id").takeIf { it.isNotBlank() } ?: continue
                files += DriveFile(id, modifiedTimeOf(file.optString("modifiedTime")))
            }
            pageToken = answer.optString("nextPageToken").takeIf { it.isNotBlank() }
        } while (pageToken != null)
        return files
    }

    private fun request(
        accessToken: String,
        method: String,
        path: String,
        contentType: String? = null,
        body: ByteArray? = null,
        methodOverride: String? = null,
    ): String {
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
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000
        const val JSON = "application/json"
        const val BOUNDARY = "secondes-day"

        fun encoded(text: String): String = URLEncoder.encode(text, "UTF-8").replace("+", "%20")

        /** [text] inside a Drive query's single quotes. */
        fun quoted(text: String): String = text.replace("\\", "\\\\").replace("'", "\\'")

        /** The day a file holds; null when it is not a whole day as [encodeDay] writes it. */
        fun dayOf(text: String): JournalDay? = try {
            decodeDay(JSONObject(text))?.copy(pending = false)
        } catch (e: JSONException) {
            null
        }

        /** Drive's modification time; a file without a readable one counts as the oldest. */
        fun modifiedTimeOf(text: String): Instant = try {
            Instant.parse(text)
        } catch (e: DateTimeParseException) {
            Instant.EPOCH
        }
    }
}
