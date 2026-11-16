package com.example.clock.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.clock.ClockApp
import com.example.clock.ui.alarm.AlarmRingScreen
import com.example.clock.ui.theme.ClockTheme

class AlarmRingActivity : ComponentActivity() {

    private data class RingArgs(
        val alarmId: Long = -1L,
        val timerMode: Boolean = false,
        val label: String = "",
        val isSnooze: Boolean = false,
        val timerEndAt: Long = 0L,
    )

    private var args by mutableStateOf(RingArgs())
    private var ensuredRinging = false

    private val stoppedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            finishAndRemoveTask()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyLockScreenFlags()
        args = readArgs(intent)
        ensureRinging()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })
        registerStoppedReceiver()
        setContent {
            ClockTheme {
                val app = application as ClockApp
                val alarms by app.alarms.alarms.collectAsState()
                val alarm = if (args.timerMode) null else alarms.firstOrNull { it.id == args.alarmId }
                AlarmRingScreen(
                    alarm = alarm,
                    timerMode = args.timerMode,
                    timerLabel = args.label,
                    onSnooze = { snooze() },
                    onDismiss = { dismiss() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        args = readArgs(intent)
        ensureRinging()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(stoppedReceiver) }
        super.onDestroy()
    }

    private fun applyLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun registerStoppedReceiver() {
        val filter = IntentFilter(AlarmService.ACTION_RING_STOPPED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stoppedReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(stoppedReceiver, filter)
        }
    }

    private fun readArgs(intent: Intent?): RingArgs = RingArgs(
        alarmId = intent?.getLongExtra(RingContract.EXTRA_ALARM_ID, -1L) ?: -1L,
        timerMode = intent?.getStringExtra(RingContract.EXTRA_KIND) == RingContract.KIND_TIMER,
        label = intent?.getStringExtra(RingContract.EXTRA_LABEL)?.ifBlank { "計時結束" }
            ?: "計時結束",
        isSnooze = intent?.getBooleanExtra(RingContract.EXTRA_SNOOZE, false) ?: false,
        timerEndAt = intent?.getLongExtra(RingContract.EXTRA_TIMER_END, 0L) ?: 0L,
    )

    /**
     * If the activity was opened from a fallback notification after the service
     * start was refused, the activity is foreground and may start the service.
     */
    private fun ensureRinging() {
        if (ensuredRinging) return
        ensuredRinging = true
        if (RingingState.current.value != null) return
        if (args.timerMode) {
            AlarmService.startTimer(this, args.label, args.timerEndAt)
        } else if (args.alarmId > 0) {
            AlarmService.start(this, args.alarmId, args.isSnooze)
        }
    }

    private fun dismiss() {
        AlarmService.dismiss(this, args.alarmId, args.timerMode)
        finishAndRemoveTask()
    }

    private fun snooze() {
        if (!args.timerMode && args.alarmId > 0) {
            AlarmService.snooze(this, args.alarmId)
        }
        finishAndRemoveTask()
    }
}
