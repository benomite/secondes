package io.github.benomite.secondes

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader

/**
 * The first screen: three polaroids, the app's name and its promise, then Google sign-in; once signed in, the two
 * choices, create or join a journal, each with its subtitle. [message] says what failed, if anything.
 */
@Composable
fun WelcomeScreen(
    signedIn: Boolean,
    busy: Boolean,
    @StringRes message: Int?,
    onSignIn: () -> Unit,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
) {
    // The app draws behind the system bars: the screen keeps its content clear of them.
    Column(modifier = Modifier.fillMaxSize().background(Palette.Paper).windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp)) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            PolaroidStack()
            Spacer(Modifier.height(32.dp))
            Text(stringResource(R.string.app_name), fontFamily = YoungSerif, fontSize = 48.sp, color = Palette.Ink)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.welcome_promise), style = MaterialTheme.typography.bodyLarge, color = Palette.Ink)
            Spacer(Modifier.height(24.dp))
        }
        if (!signedIn) {
            PrimaryButton(text = stringResource(R.string.welcome_sign_in), onClick = onSignIn, enabled = !busy, modifier = Modifier.fillMaxWidth())
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Palette.ToFill)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.welcome_signed_in), color = Palette.Ink)
            }
            Spacer(Modifier.height(16.dp))
            JournalChoices(busy = busy, onCreate = onCreate, onJoin = onJoin)
        }
        message?.let {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(it), color = MaterialTheme.colorScheme.error)
        }
    }
}

/** The two choices, create a journal or join one; both replace any journal stored on the phone. */
@Composable
private fun JournalChoices(busy: Boolean, onCreate: () -> Unit, onJoin: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PrimaryButton(onClick = onCreate, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            ChoiceLabel(Icons.Filled.Add, Palette.ToFill, R.string.journal_create, R.string.journal_create_subtitle)
        }
        SecondaryButton(onClick = onJoin, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            ChoiceLabel(FolderIcon, Palette.Ink, R.string.journal_join, R.string.journal_join_subtitle)
        }
    }
}

@Composable
private fun ChoiceLabel(icon: ImageVector, tint: Color, @StringRes title: Int, @StringRes subtitle: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
        Spacer(Modifier.size(16.dp))
        Column {
            Text(stringResource(title), fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            Text(stringResource(subtitle), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Three polaroids in a slightly tilted stack; the one on top is today's, still empty. */
@Composable
private fun PolaroidStack() {
    Box(modifier = Modifier.size(200.dp), contentAlignment = Alignment.Center) {
        Polaroid(0, Modifier.offset(x = (-30).dp, y = (-8).dp).width(120.dp)) { Box(Modifier.fillMaxSize().background(Palette.Muted.copy(alpha = 0.35f))) }
        Polaroid(1, Modifier.offset(x = 28.dp, y = (-4).dp).width(120.dp)) { Box(Modifier.fillMaxSize().background(Palette.Muted.copy(alpha = 0.6f))) }
        Polaroid(2, Modifier.offset(y = 6.dp).width(120.dp), caption = stringResource(R.string.welcome_today)) {
            Box(
                modifier = Modifier.fillMaxSize().padding(6.dp).drawBehind {
                    drawRoundRect(
                        color = Palette.ToFill,
                        style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))),
                        cornerRadius = CornerRadius(4.dp.toPx()),
                    )
                },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Add, contentDescription = null, tint = Palette.ToFill, modifier = Modifier.size(36.dp)) }
        }
    }
}

/** A journal was just created: how the others get it, from Drive, then the journal. */
@Composable
fun ShareJournalScreen(journal: StoredJournal, onOpenDrive: () -> Unit, onOpenJournal: () -> Unit) {
    BackHandler(onBack = onOpenJournal)
    Column(
        modifier = Modifier.fillMaxSize().background(Palette.Paper).windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.share_title), style = MaterialTheme.typography.headlineSmall, color = Palette.Ink)
        Text(stringResource(R.string.share_folder, journal.name), color = Palette.Ink)
        Text(stringResource(R.string.share_steps), color = Palette.Ink)
        SecondaryButton(text = stringResource(R.string.share_open_drive), onClick = onOpenDrive, modifier = Modifier.fillMaxWidth())
        PrimaryButton(text = stringResource(R.string.share_open_journal), onClick = onOpenJournal, modifier = Modifier.fillMaxWidth())
    }
}

/** The stored journal's folder can no longer be opened: said plainly, with the two ways to another journal. */
@Composable
fun AccessLostScreen(journal: StoredJournal, busy: Boolean, @StringRes message: Int?, onJoin: () -> Unit, onCreate: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Palette.Paper).windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.access_lost_title), style = MaterialTheme.typography.headlineSmall, color = Palette.Ink)
        Text(stringResource(R.string.access_lost_text, journal.name), color = Palette.Ink)
        Spacer(Modifier.height(8.dp))
        JournalChoices(busy = busy, onCreate = onCreate, onJoin = onJoin)
        message?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
    }
}

/** What Google's Drive file picker answered. */
sealed interface PickerAnswer {
    data class Picked(val folderId: String, val name: String) : PickerAnswer

    data object Cancelled : PickerAnswer

    data object Failed : PickerAnswer
}

/**
 * Google's Drive file picker, on folders, in a web view over the app's own page (assets/drive_picker.html), with the
 * drive.file [accessToken]. Picking a folder grants the app that folder. Back cancels.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DrivePickerScreen(accessToken: String, apiKey: String, appId: String, onAnswer: (PickerAnswer) -> Unit) {
    BackHandler { onAnswer(PickerAnswer.Cancelled) }
    val main = remember { Handler(Looper.getMainLooper()) }
    AndroidView(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).testTag("drive-picker"),
        factory = { context ->
            val assets = WebViewAssetLoader.Builder().addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context)).build()
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun picked(id: String, name: String) {
                            main.post { onAnswer(if (id.isBlank()) PickerAnswer.Failed else PickerAnswer.Picked(id, name)) }
                        }

                        @JavascriptInterface
                        fun cancelled() {
                            main.post { onAnswer(PickerAnswer.Cancelled) }
                        }

                        @JavascriptInterface
                        fun failed(@Suppress("UNUSED_PARAMETER") reason: String) {
                            main.post { onAnswer(PickerAnswer.Failed) }
                        }
                    },
                    "SecondesPicker",
                )
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                        assets.shouldInterceptRequest(request.url)

                    // The page stays the app's own: the picker lives in Google's frames inside it.
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                        request.isForMainFrame && request.url.host != PICKER_PAGE_HOST

                    override fun onPageFinished(view: WebView, url: String) {
                        if (url == PICKER_PAGE) {
                            val args = listOf(accessToken, apiKey, appId).joinToString(",") { org.json.JSONObject.quote(it) }
                            view.evaluateJavascript("start($args)", null)
                        }
                    }
                }
                loadUrl(PICKER_PAGE)
            }
        },
    )
}

private const val PICKER_PAGE_HOST = "appassets.androidplatform.net"
private const val PICKER_PAGE = "https://$PICKER_PAGE_HOST/assets/drive_picker.html"

/** A folder, as Material's outlined folder icon draws it. */
private val FolderIcon: ImageVector by lazy {
    ImageVector.Builder("Folder", 24.dp, 24.dp, 24f, 24f).addPath(
        pathData = PathParser().parsePathString(
            "M9.17,6l2,2H20v10H4V6h5.17M10,4H4C2.9,4 2.01,4.9 2.01,6L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V8c0,-1.1 -0.9,-2 -2,-2h-8l-2,-2z",
        ).toNodes(),
        fill = SolidColor(Color.Black),
    ).build()
}
