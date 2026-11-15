package io.github.benomite.secondes

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONException
import org.json.JSONObject

/** The only Google scope the app asks for: the Drive files it created or the user picked, nothing else of the Drive. */
const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"

/** The name of the folder 'Create a journal' makes in the user's Drive. */
const val JOURNAL_FOLDER_NAME = "secondes"

/** What asking Google for the drive.file access gave. */
sealed interface DriveAuthorization {
    data class Granted(val accessToken: String) : DriveAuthorization

    /** The user has to sign in, or consent, on Google's own screen first. */
    data class NeedsConsent(val intentSender: IntentSender) : DriveAuthorization

    data object Failed : DriveAuthorization
}

/** Google sign-in, through the authorization of the [DRIVE_FILE_SCOPE] scope only. */
class GoogleAuthorization(private val activity: Activity) {
    private val client = Identity.getAuthorizationClient(activity)
    private val request = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE))).build()

    /** Asks for the access: granted without a screen once the user has signed in and consented. */
    suspend fun authorize(): DriveAuthorization = suspendCancellableCoroutine { done ->
        client.authorize(request)
            .addOnSuccessListener { done.resume(authorizationOf(it)) }
            .addOnFailureListener { done.resume(DriveAuthorization.Failed) }
    }

    /** The access token Google's sign-in screen returned in [data]; null when it was cancelled or failed. */
    fun tokenFrom(data: Intent?): String? = if (data == null) null else try {
        client.getAuthorizationResultFromIntent(data).accessToken
    } catch (e: ApiException) {
        null
    }

    private fun authorizationOf(result: AuthorizationResult): DriveAuthorization {
        val token = result.accessToken
        val consent = result.pendingIntent
        return when {
            result.hasResolution() && consent != null -> DriveAuthorization.NeedsConsent(consent.intentSender)
            token != null -> DriveAuthorization.Granted(token)
            else -> DriveAuthorization.Failed
        }
    }
}

/** The journal's folders on Google Drive, through its REST API, with an access token for [DRIVE_FILE_SCOPE]. */
class DriveFolders(private val baseUrl: String = "https://www.googleapis.com/drive/v3") {
    /** Creates a folder named [name] at the root of the user's Drive; its id, or an IOException. */
    fun create(accessToken: String, name: String): String {
        val body = JSONObject().put("name", name).put("mimeType", FOLDER_MIME_TYPE).toString()
        val connection = open("files?fields=id", accessToken)
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toByteArray()) }
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("Drive answered HTTP $code to the folder creation")
            val answer = connection.inputStream.bufferedReader().use { it.readText() }
            return createdFolderIdOf(answer) ?: throw IOException("Drive answered no folder id")
        } finally {
            connection.disconnect()
        }
    }

    /** Whether the folder [folderId] can still be opened; [FolderAccess.UNKNOWN] when Drive cannot be asked. */
    fun access(accessToken: String, folderId: String): FolderAccess {
        val connection = try {
            open("files/${URLEncoder.encode(folderId, "UTF-8")}?fields=id,trashed&supportsAllDrives=true", accessToken)
        } catch (e: IOException) {
            return FolderAccess.UNKNOWN
        }
        return try {
            val code = connection.responseCode
            val answer = if (code in 200..299) connection.inputStream.bufferedReader().use { it.readText() } else null
            folderAccessOf(code, answer)
        } catch (e: IOException) {
            FolderAccess.UNKNOWN
        } finally {
            connection.disconnect()
        }
    }

    private fun open(path: String, accessToken: String): HttpURLConnection {
        val connection = URL("${baseUrl.trimEnd('/')}/$path").openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.setRequestProperty("Authorization", "Bearer $accessToken")
        return connection
    }

    private companion object {
        const val TIMEOUT_MS = 10_000
        const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
    }
}

/** The id in Drive's answer to a folder creation; null when the answer has none. */
fun createdFolderIdOf(answer: String): String? = try {
    JSONObject(answer).optString("id").takeIf { it.isNotBlank() }
} catch (e: JSONException) {
    null
}

/**
 * What Drive's answer to reading the folder says of it. Drive answers 404 for a folder deleted, or no longer shared
 * with the account; a folder in the trash is lost too. Any other failure (expired sign-in, quota, server) and an
 * answer that cannot be read decide nothing.
 */
fun folderAccessOf(httpCode: Int, answer: String?): FolderAccess = when {
    httpCode == HttpURLConnection.HTTP_NOT_FOUND -> FolderAccess.LOST
    httpCode !in 200..299 || answer == null -> FolderAccess.UNKNOWN
    else -> try {
        val folder = JSONObject(answer)
        when {
            !folder.has("id") -> FolderAccess.UNKNOWN
            folder.optBoolean("trashed", false) -> FolderAccess.LOST
            else -> FolderAccess.OPEN
        }
    } catch (e: JSONException) {
        FolderAccess.UNKNOWN
    }
}
