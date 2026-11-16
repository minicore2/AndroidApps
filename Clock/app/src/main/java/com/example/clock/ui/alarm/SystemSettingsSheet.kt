package com.example.clock.ui.alarm

import android.Manifest.permission.POST_NOTIFICATIONS
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Divider
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
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
import androidx.core.content.ContextCompat
import com.example.clock.ClockApp
import com.example.clock.system.SystemSettings
import com.example.clock.ui.ClockCopy
import com.example.clock.data.TimeFormatMode

@Composable
fun SystemSettingsSheet(app: ClockApp, onDismiss: () -> Unit) {
    val context: Context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme

    val timeFormat by app.settings.timeFormat.collectAsState()
    var systemStatus by remember { mutableStateOf(SystemSettings.read(context)) }
    LaunchedEffect(Unit) {
        systemStatus = SystemSettings.read(context)
    }

    var notificationRequested by remember { mutableStateOf(false) }
    val notificationLauncher = rememberLauncherForActivityResult(RequestPermission()) { _: Boolean ->
        notificationRequested = true
    }
    val canPost = rememberUpdatedState(
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) true
        else ContextCompat.checkSelfPermission(context, POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = ClockCopy.SettingsTitle,
            style = MaterialTheme.typography.titleLarge,
            color = colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "調整鬧鐘可靠性與顯示偏好。變更後於回到鬧鐘清單時重新整理狀態。",
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(20.dp))

        // --- Time format ---
        SectionTitle(text = ClockCopy.TimeFormatTitle)
        val modes = listOf(
            TimeFormatMode.SYSTEM to ClockCopy.TimeFormatSystem,
            TimeFormatMode.H24 to ClockCopy.TimeFormat24,
            TimeFormatMode.H12 to ClockCopy.TimeFormat12,
        )
        modes.forEach { (mode, label) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = timeFormat == mode,
                    onClick = { app.settings.setTimeFormat(mode) },
                )
                Text(text = label, style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurface)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        Spacer(modifier = Modifier.height(12.dp))

        // --- Status rows ---
        SectionTitle(text = ClockCopy.StatusTitle)

        val items = listOf(
            StatusItem(
                title = ClockCopy.StatusExactAlarm,
                okSummary = "鬧鐘能準時在設定的時間響鐘。",
                badSummary = "系統可能會將鬧鐘延遲至較後的時間。",
                ok = systemStatus.exactAlarms,
                action = { SystemSettings.openExactAlarmSettings(context) },
                actionLabel = ClockCopy.ActionOpenSettings,
            ),
            StatusItem(
                title = ClockCopy.StatusNotifications,
                okSummary = "響鈴通知與動作按鈕可用。",
                badSummary = "通知被停用，響鈴畫面與提示可能受限。",
                ok = systemStatus.notificationsEnabled,
                actionLabel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !canPost.value) {
                    ClockCopy.ActionRequestNotification
                } else {
                    ClockCopy.ActionOpenSettings
                },
                action = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !canPost.value) {
                        notificationLauncher.launch(POST_NOTIFICATIONS)
                    } else {
                        SystemSettings.openAppNotificationSettings(context)
                    }
                },
            ),
            StatusItem(
                title = ClockCopy.StatusFullScreen,
                okSummary = "鎖定螢幕時會顯示全螢幕響鈴。",
                badSummary = "鎖定時可能不會顯示響鈴畫面。",
                ok = systemStatus.fullScreenAllowed,
                action = { SystemSettings.openFullScreenIntentSettings(context) },
                actionLabel = ClockCopy.ActionOpenSettings,
            ),
            StatusItem(
                title = ClockCopy.StatusDnd,
                okSummary = "勿擾模式不會靜音鬧鐘。",
                badSummary = "勿擾模式可能擋住鬧鐘聲與震動。",
                ok = systemStatus.dndAccess,
                action = { SystemSettings.openDndAccessSettings(context) },
                actionLabel = ClockCopy.ActionOpenSettings,
            ),
            StatusItem(
                title = ClockCopy.StatusBattery,
                okSummary = "系統不會限制應用在背景執行。",
                badSummary = "省電模式可能延遲或阻止鬧鐘。",
                ok = systemStatus.batteryUnrestricted,
                action = { SystemSettings.openBatterySettings(context) },
                actionLabel = ClockCopy.ActionOpenSettings,
            ),
            StatusItem(
                title = ClockCopy.StatusAlarmVolume,
                okSummary = ClockCopy.StatusVolumePercent.format(systemStatus.alarmVolumePercent),
                badSummary = ClockCopy.StatusVolumePercent.format(systemStatus.alarmVolumePercent),
                ok = systemStatus.alarmVolume > 0,
                action = { SystemSettings.openAlarmVolumeSettings(context) },
                actionLabel = ClockCopy.ActionOpenSettings,
            ),
        )

        items.forEach { item ->
            StatusRow(
                title = item.title,
                summary = if (item.ok) item.okSummary else item.badSummary,
                ok = item.ok,
                actionLabel = item.actionLabel,
                onAction = item.action,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = ClockCopy.PrivacyNotice,
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurfaceVariant,
            lineHeight = 20.sp,
        )
    }

    Spacer(modifier = Modifier.height(12.dp))
    Button(
        onClick = onDismiss,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = colorScheme.surfaceVariant,
            contentColor = colorScheme.onSurfaceVariant,
        ),
    ) {
        Text(text = ClockCopy.Close)
    }
}

private data class StatusItem(
    val title: String,
    val okSummary: String,
    val badSummary: String,
    val ok: Boolean,
    val actionLabel: String,
    val action: () -> Unit,
)

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StatusRow(
    title: String,
    summary: String,
    ok: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val stateColor = if (ok) Color(0xFF4A9E3C) else Color(0xFFF4A32E)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurface)
            Text(text = summary, style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(text = if (ok) "✓" else "!", color = stateColor)
            Spacer(modifier = Modifier.height(6.dp))
            TextButton(
                onClick = onAction,
                modifier = Modifier.widthIn(min = 64.dp),
            ) {
                Text(text = actionLabel)
            }
        }
    }
}

@Composable
private fun Spacer(modifier: Modifier) = androidx.compose.foundation.layout.Spacer(modifier = modifier)
