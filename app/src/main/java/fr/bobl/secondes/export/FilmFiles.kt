package fr.bobl.secondes.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

/** The folder, in the app's private files, where the films are written; shared through [FILM_PROVIDER_SUFFIX]. */
fun filmsDir(context: Context): File = File(context.filesDir, "exports")

/** The film of [year], in [filmsDir]. */
fun filmFile(context: Context, year: Int): File = File(filmsDir(context), filmFileName(year))

fun filmFileName(year: Int) = "secondes-$year.mp4"

/** The authority of the app's FileProvider, after its package name, as declared in the manifest. */
const val FILM_PROVIDER_SUFFIX = ".films"

/** The folder of the gallery the films are saved in. */
private const val GALLERY_FOLDER = "secondes"

/** True when the video at [videoUri] can be opened on this phone; a video picked on another phone cannot. */
fun isReadable(context: Context, videoUri: String): Boolean = try {
    context.contentResolver.openFileDescriptor(Uri.parse(videoUri), "r")?.use { true } ?: false
} catch (e: Exception) {
    false
}

/**
 * Copies [film] into the phone's gallery, in Movies/secondes. Throws [IOException] when it cannot be
 * written; then nothing half-written is left in the gallery. Below Android 10 it needs the storage permission.
 */
fun saveToGallery(context: Context, film: File) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, film.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$GALLERY_FOLDER")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("the gallery refused the film")
        try {
            copyTo(context, film, uri)
            resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e as? IOException ?: IOException(e.message, e)
        }
    } else {
        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), GALLERY_FOLDER)
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("cannot create $dir")
        val target = File(dir, film.name)
        try {
            film.copyTo(target, overwrite = true)
        } catch (e: IOException) {
            target.delete()
            throw e
        }
        MediaScannerConnection.scanFile(context, arrayOf(target.path), arrayOf("video/mp4"), null)
    }
}

/** Writes [film] into the document at [target], replacing its content. Throws [IOException] when it cannot. */
fun copyTo(context: Context, film: File, target: Uri) {
    val output = context.contentResolver.openOutputStream(target, "wt")
        ?: throw IOException("cannot write to $target")
    output.use { out -> film.inputStream().use { it.copyTo(out) } }
}

/** The system share sheet with [film], readable by the app the user picks. */
fun shareIntent(context: Context, film: File): Intent {
    val uri = FileProvider.getUriForFile(context, context.packageName + FILM_PROVIDER_SUFFIX, film)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return Intent.createChooser(send, null)
}
