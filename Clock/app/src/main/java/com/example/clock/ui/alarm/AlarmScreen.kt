package com.example.clock.ui.alarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.clock.ClockApp
import com.example.clock.system.SystemSettings
import com.example.clock.ui.ClockCopy
import com.example.clock.ui.theme.ClockTheme

@Composable
fun AlarmScreen(
    app: ClockApp,
    onEditAlarm: (Long) -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val alarms by app.alarms.alarms.collectAsState()
    var systemStatus by remember { mutableStateOf(SystemSettings.read(context)) }
    var refreshStatus by remember { mutableStateOf(false) }
    LaunchedEffect(refreshStatus) {
        systemStatus = SystemSettings.read(context)
    }
    val criticalMissing = !systemStatus.allCriticalOk

    var deleteAlarmId by remember { mutableStateOf<Long?>(null) }
    val deleteLabel = rememberUpdatedState(
        alarms.firstOrNull { it.id == deleteAlarmId ?: -1L }?.label ?: "",
    )

    Column(modifier = Modifier.fillMaxSize()) {
        if (criticalMissing) {
            CriticalBanner(onOpenSettings = {
                onSettings()
                refreshStatus = true
            })
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (alarms.isEmpty()) {
                EmptyAlarm(onAddAlarm = { onEditAlarm(0L) })
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(alarms, key = { it.id }) { alarm ->
                        AlarmListItem(
                            alarm = alarm,
                            onEnabledChange = { enabled ->
                                app.alarms.setEnabled(alarm.id, enabled)
                            },
                            onClick = { onEditAlarm(alarm.id) },
                            onSkipNext = { app.alarms.skipNext(alarm.id) },
                            onDelete = { deleteAlarmId = alarm.id },
                            onOverflowClick = {},
                        )
                    }
                }

                FloatingActionButton(
                    onClick = { onEditAlarm(0L) },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                ) {
                    Text(text = "+")
                }
            }
        }

        deleteAlarmId?.let { id ->
            ConfirmDialog(
                title = ClockCopy.DeleteTitle,
                message = ClockCopy.DeleteMessage.format(deleteLabel.value),
                onConfirm = {
                    app.alarms.delete(id)
                    deleteAlarmId = null
                },
                onDismiss = { deleteAlarmId = null },
            )
        }
    }
}

@Composable
private fun EmptyAlarm(onAddAlarm: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "⏰")
        Spacer(modifier = Modifier.size(16.dp))
        Text(
            text = ClockCopy.EmptyTitle,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = ClockCopy.EmptyBody,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onAddAlarm,
            modifier = Modifier.fillMaxWidth(0.6f),
        ) {
            Text(text = ClockCopy.AddFab)
        }
    }
}

@Composable
private fun CriticalBanner(onOpenSettings: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "!")
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = ClockCopy.BannerCritical,
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurface,
            )
            Text(
                text = "開啟精確鬧鐘、通知與全螢幕鬧鐘權限，確保響鈴可靠。",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = onOpenSettings,
            colors = ButtonDefaults.buttonColors(
                containerColor = colorScheme.primary,
                contentColor = colorScheme.onPrimary,
            ),
        ) {
            Text(text = ClockCopy.BannerOpen)
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = { Text(text = message) },
        confirmButton = {
            TextButton(
                onClick = { onConfirm() },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(text = ClockCopy.DeleteConfirm)
            }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss() }) {
                Text(text = "取消")
            }
        },
    )
}
