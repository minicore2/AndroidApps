package com.example.clock.ui

import android.Manifest.permission.POST_NOTIFICATIONS
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import com.example.clock.ClockApp
import com.example.clock.ui.alarm.AlarmEditorScreen
import com.example.clock.ui.alarm.AlarmScreen
import com.example.clock.ui.alarm.SystemSettingsSheet
import com.example.clock.ui.stopwatch.StopwatchScreen
import com.example.clock.ui.timer.TimerScreen

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ClockRoot(app: ClockApp) {
    val context: Context = LocalContext.current

    var notificationRequested by remember { mutableStateOf(false) }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (!notificationRequested &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationRequested = true
            notificationLauncher.launch(POST_NOTIFICATIONS)
        }
    }

    var selectedTab by remember { mutableStateOf(0) }
    var editingAlarmId by remember { mutableStateOf<Long?>(null) }
    var settingsSheetOpen by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (selectedTab == 0 && editingAlarmId == null) {
                TopAppBar(
                    title = { Text(text = "鬧鐘", style = MaterialTheme.typography.titleLarge) },
                    actions = {
                        IconButton(onClick = { settingsSheetOpen = true }) {
                            Icon(imageVector = Icons.Filled.Settings, contentDescription = "系統設定")
                        }
                    },
                )
            }
        },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                tab(0, "鬧鐘", Icons.Filled.Alarm, selectedTab == 0) { selectedTab = 0 }
                tab(1, "碼表", Icons.Filled.AccessTime, selectedTab == 1) { selectedTab = 1 }
                tab(2, "倒數", Icons.Filled.Timer, selectedTab == 2) { selectedTab = 2 }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (editingAlarmId != null) {
                AlarmEditorScreen(app = app, alarmId = editingAlarmId!!, onClose = { editingAlarmId = null })
            } else {
                when (selectedTab) {
                    0 -> AlarmScreen(app = app, onEditAlarm = { editingAlarmId = it }, onSettings = { settingsSheetOpen = true })
                    1 -> StopwatchScreen()
                    2 -> TimerScreen(app = app)
                    else -> AlarmScreen(app = app, onEditAlarm = {}, onSettings = {})
                }
            }

            if (settingsSheetOpen) {
                Box(
                    modifier = Modifier.fillMaxSize().clickable { settingsSheetOpen = false }.zIndex(1f),
                )
                Dialog(onDismissRequest = { settingsSheetOpen = false }) {
                    SystemSettingsSheet(app = app, onDismiss = { settingsSheetOpen = false })
                }
            }
        }
    }
}

@Composable
private fun tab(index: Int, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) colorScheme.primaryContainer else colorScheme.surfaceVariant,
            contentColor = if (selected) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(imageVector = icon, contentDescription = null)
            Text(text = label, style = MaterialTheme.typography.labelMedium)
        }
    }
}
