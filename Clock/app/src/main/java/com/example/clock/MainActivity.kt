package com.example.clock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.clock.alarm.AlarmService
import com.example.clock.alarm.RingingState
import com.example.clock.ui.alarm.AlarmRingScreen
import com.example.clock.ui.ClockRoot
import com.example.clock.ui.theme.ClockTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ClockTheme {
                val app = application as ClockApp
                val ringing by RingingState.current.collectAsState()
                val active = ringing
                if (active != null) {
                    val alarms by app.alarms.alarms.collectAsState()
                    val alarm = if (active.timerMode) {
                        null
                    } else {
                        alarms.firstOrNull { it.id == active.alarmId }
                    }
                    AlarmRingScreen(
                        alarm = alarm,
                        timerMode = active.timerMode,
                        timerLabel = active.label,
                        onSnooze = {
                            if (!active.timerMode && active.alarmId > 0) {
                                AlarmService.snooze(this, active.alarmId)
                            }
                            RingingState.clear()
                        },
                        onDismiss = {
                            AlarmService.dismiss(this, active.alarmId, active.timerMode)
                            RingingState.clear()
                        },
                    )
                } else {
                    ClockRoot(app = app)
                }
            }
        }
    }
}
