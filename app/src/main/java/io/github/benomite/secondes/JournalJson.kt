package io.github.benomite.secondes

import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeParseException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * A day as the phone keeps it: `{"date", "author", "clip": {"videoUri", "startMs", "endMs", "source"?}}`, `source`
 * being the second the clip was cut from (same shape), plus `pending` when [withPending].
 */
fun encodeDay(day: JournalDay, withPending: Boolean = false): JSONObject = JSONObject()
    .put("date", day.date.toString())
    .put("author", day.author)
    .put("clip", encodeClip(day.clip))
    .apply { if (withPending) put("pending", day.pending) }

private fun encodeClip(clip: SharedClip): JSONObject = JSONObject()
    .put("videoUri", clip.videoUri)
    .put("startMs", clip.window.startMs)
    .put("endMs", clip.window.endMs)
    .apply { clip.source?.let { put("source", encodeClip(it)) } }

/** The day held by [json], or null when it lacks its date, its author or its clip: a day is never kept half-written. */
fun decodeDay(json: JSONObject): JournalDay? {
    val date = dateOf(json) ?: return null
    val author = json.optString("author").takeIf { it.isNotBlank() } ?: return null
    val clip = json.optJSONObject("clip")?.let(::decodeClip) ?: return null
    return JournalDay(date, clip, author, json.optBoolean("pending", false))
}

private fun decodeClip(json: JSONObject): SharedClip? {
    val videoUri = json.optString("videoUri").takeIf { it.isNotBlank() } ?: return null
    val window = windowOf(json) ?: return null
    // A source that cannot be read only loses the way back to the member's own video: the clip itself is whole.
    return SharedClip(videoUri, window, json.optJSONObject("source")?.let(::decodeClip))
}

private fun dateOf(json: JSONObject): LocalDate? = try {
    LocalDate.parse(json.optString("date"))
} catch (e: DateTimeParseException) {
    null
}

private fun windowOf(json: JSONObject): SecondWindow? {
    if (!json.has("startMs") || !json.has("endMs")) return null
    return try {
        SecondWindow(json.getLong("startMs"), json.getLong("endMs"))
    } catch (e: JSONException) {
        null
    }
}

/** A day as its JSON file in the family's folder holds it: its clip is the file [clipFile] next to it, played over [window]. */
data class DayFile(val date: LocalDate, val author: String, val clipFile: String, val window: SecondWindow)

/**
 * The day's JSON file in the family's folder: `{"date", "author", "clip": {"file", "startMs", "endMs"}}`, `file` being
 * the name of the day's clip next to it ([dayClipName]). It never names a video on the author's phone.
 */
fun encodeDayFile(day: JournalDay): JSONObject = JSONObject()
    .put("date", day.date.toString())
    .put("author", day.author)
    .put(
        "clip",
        JSONObject()
            .put("file", dayClipName(day.date, day.author))
            .put("startMs", day.clip.window.startMs)
            .put("endMs", day.clip.window.endMs),
    )

/** The day of a JSON file of the family's folder, or null when it lacks its date, its author, its clip file or its second. */
fun decodeDayFile(json: JSONObject): DayFile? {
    val date = dateOf(json) ?: return null
    val author = json.optString("author").takeIf { it.isNotBlank() } ?: return null
    val clip = json.optJSONObject("clip") ?: return null
    val file = clip.optString("file").takeIf { it.isNotBlank() } ?: return null
    val window = windowOf(clip) ?: return null
    return DayFile(date, author, file, window)
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
