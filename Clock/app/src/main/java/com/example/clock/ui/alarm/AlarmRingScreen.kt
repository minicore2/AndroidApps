package com.example.clock.ui.alarm

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.clock.data.Alarm
import com.example.clock.ui.ClockCopy
import com.example.clock.ui.theme.ClockTimeStyle

@Composable
fun AlarmRingScreen(
    alarm: Alarm?,
    timerMode: Boolean,
    timerLabel: String,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme

    val dismissCalled = remember { mutableStateOf(false) }
    val snoozeCalled = remember { mutableStateOf(false) }
    val onDismissCurrent = rememberUpdatedState(onDismiss)
    val onSnoozeCurrent = rememberUpdatedState(onSnooze)

    fun safeDismiss() {
        if (!dismissCalled.value) {
            dismissCalled.value = true
            onDismissCurrent.value()
        }
    }
    fun safeSnooze() {
        if (!snoozeCalled.value) {
            snoozeCalled.value = true
            onSnoozeCurrent.value()
        }
    }

    val slideEnabled = !timerMode && alarm != null &&
        (alarm.slideToDismiss || (!alarm.slideToDismiss && !alarm.shakeToDismiss))
    val shakeEnabled = !timerMode && alarm != null && alarm.shakeToDismiss
    val showSnooze = !timerMode && alarm != null

    // Accelerometer shake-to-dismiss (no extra permission required)
    ShakeListener(enabled = shakeEnabled, onShake = { safeDismiss() })

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (timerMode) {
            Text(
                text = ClockCopy.TimerFinishedTitle,
                style = MaterialTheme.typography.headlineSmall,
                color = colorScheme.onSurface,
            )
            if (timerLabel.isNotBlank()) {
                Text(
                    text = timerLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = colorScheme.onSurfaceVariant,
                )
            }
        } else {
            alarm?.let { a ->
                Text(
                    text = a.timeText,
                    style = ClockTimeStyle,
                    color = colorScheme.onSurface,
                )
                if (a.label.isNotBlank()) {
                    Text(
                        text = a.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        if (showSnooze && alarm != null) {
            Button(
                onClick = { safeSnooze() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.surfaceVariant,
                    contentColor = colorScheme.onSurfaceVariant,
                ),
            ) {
                Text(text = ClockCopy.SnoozeButton.format(alarm.snoozeMinutes))
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (slideEnabled) {
            SlideToDismiss(
                onDismiss = { safeDismiss() },
                modifier = Modifier.width(260.dp),
            )
        } else {
            Button(
                onClick = { safeDismiss() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.error,
                    contentColor = colorScheme.onError,
                ),
                modifier = Modifier.width(200.dp),
            ) {
                Text(text = ClockCopy.DismissButton)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        val hint = when {
            slideEnabled -> ClockCopy.SlideHint
            shakeEnabled -> ClockCopy.ShakeHint
            else -> ""
        }
        if (hint.isNotBlank()) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Drag-to-dismiss slider. Dragging past the threshold triggers [onDismiss]. */
@Composable
private fun SlideToDismiss(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val threshold = 180f
    var offset by remember { mutableStateOf(0f) }
    val completed = remember { mutableStateOf(false) }

    if (completed.value) {
        onDismiss()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { },
                    onDrag = { change, dragAmount ->
                        val newOffset = (offset + dragAmount.x).coerceIn(0f, threshold * 2f)
                        offset = newOffset
                        if (newOffset >= threshold) {
                            completed.value = true
                        }
                    },
                    onDragEnd = {
                        if (!completed.value) {
                            offset = 0f
                        }
                    },
                )
            },
    ) {
        // Track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .align(Alignment.Center)
                .background(colorScheme.onSurfaceVariant.copy(alpha = 0.2f)),
        )
        // Thumb
        Box(
            modifier = Modifier
                .size(32.dp)
                .align(Alignment.Center)
                .padding(start = (offset / 4).dp)
                .background(colorScheme.primary)
                .clip(androidx.compose.foundation.shape.CircleShape),
        )
        // Label
        Text(
            text = ClockCopy.SlideHint,
            style = MaterialTheme.typography.labelMedium,
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

/** Listens to the accelerometer and invokes [onShake] when a shake is detected. */
@Composable
private fun ShakeListener(enabled: Boolean, onShake: () -> Unit) {
    val context = LocalContext.current
    val onShakeCurrent = rememberUpdatedState(onShake)
    val triggered = remember { mutableStateOf(false) }

    DisposableEffect(enabled) {
        if (!enabled) {
            onDispose { }
        } else {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val listener = object : SensorEventListener {
                private var lastX = 0f
                private var lastY = 0f
                private var lastZ = 0f
                private var lastTime = 0L
                override fun onSensorChanged(event: SensorEvent?) {
                    if (event == null || triggered.value) return
                    val now = event.timestamp
                    if (lastTime != 0L && now - lastTime < 100_000_000L) return // 100ms
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]
                    val delta = Math.abs(x - lastX) + Math.abs(y - lastY) + Math.abs(z - lastZ)
                    if (delta > 1800f) {
                        triggered.value = true
                        onShakeCurrent.value()
                    }
                    lastX = x
                    lastY = y
                    lastZ = z
                    lastTime = now
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
            onDispose {
                sensorManager.unregisterListener(listener)
                triggered.value = false
            }
        }
    }
}

@Composable
private fun Spacer(modifier: Modifier) = androidx.compose.foundation.layout.Spacer(modifier = modifier)
