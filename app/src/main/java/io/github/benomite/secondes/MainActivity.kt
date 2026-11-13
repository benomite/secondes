package io.github.benomite.secondes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val today = LocalDate.now()
                    // No clip storage, playback or capture flow exists yet: the calendar is
                    // wired to an empty journal starting today, with no-op taps.
                    JournalCalendarScreen(
                        year = today.year,
                        clips = emptyList(),
                        firstDay = today,
                        today = today,
                        onPlay = {},
                        onFill = {},
                    )
                }
            }
        }
    }
}
