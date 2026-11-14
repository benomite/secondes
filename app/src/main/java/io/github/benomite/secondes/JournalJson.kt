package io.github.benomite.secondes

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeParseException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** A day as JSON: `{"date", "author", "clip": {"videoUri", "startMs", "endMs"}}`, plus `pending` when [withPending]. */
fun encodeDay(day: JournalDay, withPending: Boolean = false): JSONObject = JSONObject()
    .put("date", day.date.toString())
    .put("author", day.author)
    .put(
        "clip",
        JSONObject()
            .put("videoUri", day.clip.videoUri)
            .put("startMs", day.clip.window.startMs)
            .put("endMs", day.clip.window.endMs),
    )
    .apply { if (withPending) put("pending", day.pending) }

/** The day held by [json], or null when it lacks its date, its author or its clip: a day is never kept half-written. */
fun decodeDay(json: JSONObject): JournalDay? {
    val date = try {
        LocalDate.parse(json.optString("date"))
    } catch (e: DateTimeParseException) {
        return null
    }
    val author = json.optString("author").takeIf { it.isNotBlank() } ?: return null
    val clip = json.optJSONObject("clip") ?: return null
    val videoUri = clip.optString("videoUri").takeIf { it.isNotBlank() } ?: return null
    if (!clip.has("startMs") || !clip.has("endMs")) return null
    val window = try {
        SecondWindow(clip.getLong("startMs"), clip.getLong("endMs"))
    } catch (e: JSONException) {
        return null
    }
    return JournalDay(date, SharedClip(videoUri, window), author, json.optBoolean("pending", false))
}

/** The complete days of a JSON array; an incomplete day is left out. Throws [IOException] when [text] is not an array. */
fun decodeDays(text: String): List<JournalDay> {
    val array = try {
        JSONArray(text)
    } catch (e: JSONException) {
        throw IOException("the journal is not a JSON array", e)
    }
    return (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let(::decodeDay) }
}

/**
 * The journal server over HTTP: `GET <base>/days` lists the days, `PUT <base>/days/<date>` writes one and
 * answers it as stored. The server keeps the last write of a day. With no [baseUrl] there is no server:
 * every call fails, and the days filled stay pending on the phone.
 */
class HttpRemoteJournal(private val baseUrl: String) : RemoteJournal {
    override fun fetchDays(): List<JournalDay> = decodeDays(request("GET", "days", null))

    override fun pushDay(day: JournalDay): JournalDay {
        val answer = request("PUT", "days/${day.date}", encodeDay(day).toString())
        val stored = try {
            decodeDay(JSONObject(answer))
        } catch (e: JSONException) {
            null
        }
        return stored ?: throw IOException("the server answered an incomplete day for ${day.date}")
    }

    private fun request(method: String, path: String, body: String?): String {
        if (baseUrl.isBlank()) throw IOException("no journal server is configured")
        val connection = URL("${baseUrl.trimEnd('/')}/$path").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("$method $path answered HTTP $code")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000
    }
}

/**
 * The days kept in [file] as a JSON array, written whole through a temporary file then renamed, so the file
 * never holds half a save. A file that cannot be read is set aside, next to it, before anything is written.
 */
class FileLocalJournal(private val file: File) : LocalJournal {
    /** Set when an unreadable file could not be set aside: saving would overwrite it, so saves fail. */
    private var blocked = false

    override fun load(): Map<LocalDate, JournalDay> {
        if (!file.exists()) return emptyMap()
        return try {
            val array = JSONArray(file.readText())
            (0 until array.length()).associate { i ->
                val day = array.optJSONObject(i)?.let(::decodeDay) ?: throw IOException("day $i is incomplete")
                day.date to day
            }
        } catch (e: IOException) {
            setAside()
        } catch (e: JSONException) {
            setAside()
        }
    }

    /** Moves the unreadable file to `<name>.unreadable`; the journal starts empty without losing it. */
    private fun setAside(): Map<LocalDate, JournalDay> {
        blocked = !file.renameTo(File(file.parentFile, "${file.name}.unreadable"))
        return emptyMap()
    }

    override fun save(days: Map<LocalDate, JournalDay>) {
        if (blocked) throw IOException("${file.name} could not be read nor set aside")
        val array = JSONArray()
        days.values.sortedBy { it.date }.forEach { array.put(encodeDay(it, withPending = true)) }
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(array.toString())
        if (!temporary.renameTo(file)) throw IOException("${file.name} could not be replaced")
    }
}
