package io.github.benomite.secondes

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var videos by remember { mutableStateOf<List<PickedVideo>?>(null) }
                    val picker = rememberLauncherForActivityResult(
                        ActivityResultContracts.PickMultipleVisualMedia(),
                    ) { uris -> videos = toPickedVideos(uris.map { readPickerItem(this@MainActivity, it) }) }

                    Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
                        Text(stringResource(R.string.placeholder))
                        Button(onClick = {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                        }) { Text(stringResource(R.string.pick_videos)) }
                        videos?.let { list ->
                            Text(stringResource(R.string.picked_count, list.size))
                            list.forEach { Text("${it.uri}\n${it.captureDate ?: stringResource(R.string.no_date)}") }
                        }
                    }
                }
            }
        }
    }
}

/** Reads DATE_TAKEN from the picker URI; a picker that does not expose it, or a failing query, gives no date. */
private fun readPickerItem(context: Context, uri: Uri): PickerItem {
    val date = try {
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATE_TAKEN), null, null, null)?.use {
            if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null
        }
    } catch (e: Exception) {
        null
    }
    return PickerItem(uri.toString(), date)
}
