package com.example.clock.ui.timer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width

import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.clock.ClockApp
import kotlin.math.roundToInt
import com.example.clock.timer.TimerEngine
import com.example.clock.ui.ClockCopy
import com.example.clock.ui.theme.ClockCounterStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun TimerScreen(app: ClockApp) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val state by app.timer.state.collectAsState()

    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }

    val remaining = state.remainingMs(now)
    val running = state.running
    val finished = !running && remaining <= 0L && state.pausedRemainingMs <= 0L && state.durationMs > 0L
    val paused = !running && !finished && remaining > 0L

    val totalSeconds = (remaining.coerceAtLeast(0L) + 500L) / 1000L
    val displayHours = totalSeconds / 3600L
    val displayMinutes = (totalSeconds % 3600L) / 60L
    val displaySeconds = totalSeconds % 60L
    val timeText = if (displayHours > 0) {
        "%d:%02d:%02d".format(displayHours, displayMinutes, displaySeconds)
    } else {
        "%02d:%02d".format(displayMinutes, displaySeconds)
    }

    var customHours by remember { mutableStateOf((state.durationMs / 3_600_000L).toInt()) }
    var customMinutes by remember { mutableStateOf(((state.durationMs % 3_600_000L) / 60_000L).toInt()) }
    var customSeconds by remember { mutableStateOf(((state.durationMs % 60_000L) / 1000L).toInt()) }
    var timerLabel by remember { mutableStateOf(state.label) }

    val customDurationMs =
        ((customHours.toLong() * 3600 + customMinutes * 60 + customSeconds) * 1000L).coerceAtLeast(1_000L)

    fun startTimer() {
        TimerEngine.start(
            context,
            if (customDurationMs != state.durationMs) customDurationMs else state.durationMs,
            timerLabel,
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // --- Progress indicator ---
        TimerRing(
            progress = state.progress(now),
            onClick = { if (!running) startTimer() },
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = timeText,
            style = ClockCounterStyle.copy(fontSize = 72.sp),
        )

        if (timerLabel.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = timerLabel,
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.onSurface,
            )
        }

        if (running) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = ClockCopy.TimerEndsAt.format(
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(state.endAt)),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        // --- Primary action ---
        val primaryLabel = when {
            finished -> ClockCopy.TimerRestart
            running -> ClockCopy.TimerPause
            paused -> ClockCopy.TimerResume
            else -> ClockCopy.TimerStart
        }
        val primaryAction: () -> Unit = when {
            finished -> { { startTimer() } }
            running -> { { TimerEngine.pause(context) } }
            paused -> { { TimerEngine.resume(context) } }
            else -> { { startTimer() } }
        }
        Button(
            onClick = primaryAction,
            modifier = Modifier.fillMaxWidth(0.7f),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (finished || paused) colorScheme.primary else colorScheme.secondary,
                contentColor = if (finished || paused) colorScheme.onPrimary else colorScheme.onSecondary,
            ),
        ) {
            Text(text = primaryLabel)
        }

        Spacer(modifier = Modifier.height(12.dp))
        if (paused || finished) {
            Button(
                onClick = { TimerEngine.reset(context) },
                modifier = Modifier.fillMaxWidth(0.7f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.surfaceVariant,
                    contentColor = colorScheme.onSurfaceVariant,
                ),
            ) {
                Text(text = ClockCopy.TimerReset)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (!running) {
            // --- Presets ---
            Text(
                text = ClockCopy.TimerCustom,
                style = MaterialTheme.typography.labelLarge,
                color = colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                presetButton(context, ClockCopy.TimerPresetInstantNoodles, 3L * 60_000L)
                presetButton(context, ClockCopy.TimerPresetRice, 30L * 60_000L)
                presetButton(context, ClockCopy.TimerPresetNap, 20L * 60_000L)
                presetButton(context, ClockCopy.TimerPresetStudy, 25L * 60_000L)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- Custom H/M/S ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                stepper("時", customHours, 0, 23) { customHours = it }
                stepper("分", customMinutes, 0, 59) { customMinutes = it }
                stepper("秒", customSeconds, 0, 59) { customSeconds = it }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { TimerEngine.setDuration(context, customDurationMs) },
                modifier = Modifier.width(110.dp),
            ) {
                Text(text = "套用")
            }

            Spacer(modifier = Modifier.height(16.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "標籤",
                    style = MaterialTheme.typography.labelLarge,
                    color = colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = timerLabel,
                    onValueChange = { newLabel -> timerLabel = newLabel },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun TimerRing(progress: Float, onClick: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .size(220.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            Text(
                text = (progress * 100f).roundToInt().toString() + "%",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun presetButton(context: android.content.Context, label: String, durationMs: Long) {
    val colorScheme = MaterialTheme.colorScheme
    Button(
        onClick = { TimerEngine.setDuration(context, durationMs) },
        colors = ButtonDefaults.buttonColors(
            containerColor = colorScheme.surfaceVariant,
            contentColor = colorScheme.onSurfaceVariant,
        ),
    ) {
        Text(text = label)
    }
}

@Composable
private fun stepper(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = { if (value > min) onChange(value - 1) }) {
            Text(text = "-")
        }
        Text(
            text = "%02d".format(value),
            style = MaterialTheme.typography.titleMedium,
            color = colorScheme.onSurface,
        )
        IconButton(onClick = { if (value < max) onChange(value + 1) }) {
            Text(text = "+")
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurfaceVariant,
        )
    }
}
