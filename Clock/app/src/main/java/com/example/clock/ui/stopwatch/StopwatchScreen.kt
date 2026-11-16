package com.example.clock.ui.stopwatch

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import com.example.clock.ui.ClockCopy
import com.example.clock.ui.theme.ClockCounterStyle

@Composable
fun StopwatchScreen() {
    val viewModel = viewModel<StopwatchViewModel>()
    val ui by viewModel.ui.collectAsState()

    var now by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(16)
            now = SystemClock.elapsedRealtime()
        }
    }

    val elapsed = if (ui.running) ui.accumulated + now - ui.startedAt else ui.accumulated
    val hours = elapsed / 3_600_000L
    val minutes = (elapsed % 3_600_000L) / 60_000L
    val seconds = (elapsed % 60_000L) / 1_000L
    val centis = (elapsed % 1_000L) / 10L
    val timeText = "%02d:%02d:%02d.%02d".format(hours, minutes, seconds, centis)

    val colorScheme = MaterialTheme.colorScheme
    val fastest = ui.laps.minByOrNull { it.splitMs }
    val slowest = ui.laps.maxByOrNull { it.splitMs }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = timeText,
            style = ClockCounterStyle.copy(fontSize = 80.sp),
        )
        Spacer(modifier = Modifier.height(32.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val running = ui.running
            Button(
                onClick = { viewModel.toggle() },
                modifier = Modifier.width(150.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (running) colorScheme.error else colorScheme.primary,
                    contentColor = if (running) colorScheme.onError else colorScheme.onPrimary,
                ),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = if (running) "⏸" else "▶")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = if (running) ClockCopy.StopwatchPause else ClockCopy.StopwatchStart)
                }
            }
            if (running) {
                Button(
                    onClick = { viewModel.lap() },
                    modifier = Modifier.width(110.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "🚩",
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = ClockCopy.StopwatchLap)
                    }
                }
            } else if (ui.laps.isNotEmpty() || elapsed > 0L) {
                Button(
                    onClick = { viewModel.reset() },
                    modifier = Modifier.width(110.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.surfaceVariant,
                        contentColor = colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Text(text = ClockCopy.StopwatchReset)
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (ui.laps.isEmpty()) {
            Text(
                text = ClockCopy.StopwatchNoLaps,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize()
                .padding(vertical = 8.dp),
        ) {
            items(ui.laps, key = { it.number }) { lap ->
                val isFastest = fastest != null && lap.number == fastest.number
                val isSlowest = slowest != null && lap.number == slowest.number
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = ClockCopy.StopwatchLapTitle.format(lap.number),
                        style = MaterialTheme.typography.labelLarge,
                        color = colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(40.dp),
                    )
                    Column {
                        Text(
                            text = "%02d:%02d.%02d".format(
                                lap.splitMs / 60_000L,
                                (lap.splitMs % 60_000L) / 1000L,
                                (lap.splitMs % 1000L) / 10L,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isFastest) Color(0xFF4A9E3C)
                            else if (isSlowest) Color(0xFFF4A32E)
                            else colorScheme.onSurface,
                        )
                        Text(
                            text = ClockCopy.StopwatchTotal +
                                    " %02d:%02d.%02d".format(
                                        lap.totalMs / 60_000L,
                                        (lap.totalMs % 1000L) / 10L,
                                    ),
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.fillMaxSize())
                    if (isFastest) {
                        Text(text = ClockCopy.StopwatchFastest, style = MaterialTheme.typography.bodySmall, color = Color(0xFF4A9E3C))
                    } else if (isSlowest) {
                        Text(text = ClockCopy.StopwatchSlowest, style = MaterialTheme.typography.bodySmall, color = Color(0xFFF4A32E))
                    }
                }
            }
        }
    }
}
