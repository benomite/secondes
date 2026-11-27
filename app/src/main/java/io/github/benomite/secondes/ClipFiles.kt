package io.github.benomite.secondes

import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.security.MessageDigest

/** Cuts [window] of the video at [videoUri] into a standalone video file at [output]; throws [IOException] when it cannot. */
fun interface SecondCutter {
    suspend fun cut(videoUri: String, window: SecondWindow, output: File)
}

/**
 * The days' one-second clips kept on this phone, in [dir]: those cut here from a member's video by [cutter], and
 * those brought from the family's Drive folder. Each file is named by the MD5 of its content, as Drive's
 * md5Checksum names it: a clip already here is not downloaded again. A kept clip does not depend on the video it was
 * cut from: deleting that video from the gallery changes nothing.
 */
class ClipFiles(dir: File, private val cutter: SecondCutter) {
    private val dir = dir.absoluteFile

    /** The file of [clip] when it is a clip kept here; null for a member's own video, or a kept file gone missing. */
    fun fileOf(clip: SharedClip): File? {
        if (!clip.videoUri.startsWith("file:")) return null
        val file = try {
            File(URI(clip.videoUri))
        } catch (e: URISyntaxException) {
            return null
        } catch (e: IllegalArgumentException) {
            return null
        }
        return file.takeIf { it.parentFile == dir && it.isFile }
    }

    /**
     * Cuts the second chosen in [clip]'s video into the day's own clip file, kept here; returns that clip, played
     * whole, with [clip] as its source. Throws [IOException] when the video cannot be read or cut: nothing is kept.
     */
    suspend fun keep(clip: SharedClip): SharedClip {
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("no folder for the clips")
        val cut = File(dir, "cut.tmp")
        try {
            cutter.cut(clip.videoUri, clip.window, cut)
            if (!cut.isFile || cut.length() == 0L) throw IOException("the cut gave no clip")
            val kept = File(dir, "${md5Of(cut)}.mp4")
            if (!kept.isFile && !cut.renameTo(kept)) throw IOException("the clip could not be kept")
            val whole = SecondWindow(0, clip.window.endMs - clip.window.startMs)
            return SharedClip(uriOf(kept), whole, clip.copy(source = null))
        } finally {
            cut.delete()
        }
    }

    /** The clip kept here whose content has the MD5 [md5], played over [window]; null when there is none. */
    fun withMd5(md5: String?, window: SecondWindow): SharedClip? {
        val name = md5?.lowercase()?.takeIf { it.matches(MD5) } ?: return null
        return File(dir, "$name.mp4").takeIf { it.isFile }?.let { SharedClip(uriOf(it), window) }
    }

    /** Keeps [content], a clip brought from the family's folder, played over [window]; throws [IOException] when it cannot. */
    fun store(content: ByteArray, window: SecondWindow): SharedClip {
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("no folder for the clips")
        val kept = File(dir, "${hex(MessageDigest.getInstance("MD5").digest(content))}.mp4")
        if (!kept.isFile) {
            // Written whole, then renamed: a download cut halfway never stands for a clip.
            val temporary = File(dir, "download.tmp")
            temporary.writeBytes(content)
            if (!temporary.renameTo(kept)) {
                temporary.delete()
                throw IOException("the clip could not be kept")
            }
        }
        return SharedClip(uriOf(kept), window)
    }

    private companion object {
        val MD5 = Regex("[0-9a-f]{32}")

        fun uriOf(file: File): String = file.toURI().toString()

        fun md5Of(file: File): String {
            val digest = MessageDigest.getInstance("MD5")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return hex(digest.digest())
        }

        fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
    }
}
