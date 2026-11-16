package com.example.clock.timer

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.clock.alarm.AlarmService
import com.example.clock.alarm.Notifications
import com.example.clock.clockApp

class TimerService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var foregroundStarted = false

    private val ticker = object : Runnable {
        override fun run() {
            val app = application as com.example.clock.ClockApp
            val state = app.timer.state.value
            if (!state.running) {
                stopSelf()
                return
            }
            val remaining = state.endAt - System.currentTimeMillis()
            if (remaining <= 0L) {
                app.timer.markFinished(state.label)
                app.scheduler.cancelTimer()
                AlarmService.startTimer(this@TimerService, state.label, state.endAt)
                stopSelf()
                return
            }
            handler.postDelayed(this, 1_000L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> {
                timer.pause()
                scheduler.cancelTimer()
                finishService()
                return START_NOT_STICKY
            }

            ACTION_STOP -> {
                timer.reset()
                scheduler.cancelTimer()
                finishService()
                return START_NOT_STICKY
            }

            ACTION_START, null -> startTicking()
        }
        return START_NOT_STICKY
    }

    private fun startTicking() {
        val state = timer.state.value
        if (!state.running) {
            stopSelf()
            return
        }
        val notification = buildNotification(state)
        if (!foregroundStarted) {
            startForeground(NOTIFICATION_ID, notification)
            foregroundStarted = true
        } else {
            Notifications.cancelNotification(this, NOTIFICATION_ID)
            startForeground(NOTIFICATION_ID, notification)
        }
        scheduler.scheduleTimer(state.endAt, state.label)
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    private fun buildNotification(state: TimerState): android.app.Notification {
        return Notifications.timerNotification(
            context = this,
            title = state.label.ifBlank { "計時" },
            text = "倒數計時進行中",
            endAt = state.endAt,
            pauseIntent = servicePending(ACTION_PAUSE),
            stopIntent = servicePending(ACTION_STOP),
        )
    }

    private fun servicePending(action: String): PendingIntent {
        val intent = Intent(this, TimerService::class.java).apply {
            this.action = action
            data = Uri.parse("clock://timer-service/$action")
        }
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun finishService() {
        handler.removeCallbacksAndMessages(null)
        Notifications.cancelNotification(this, NOTIFICATION_ID)
        if (foregroundStarted) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foregroundStarted = false
        }
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private val timer get() = (application as com.example.clock.ClockApp).timer
    private val scheduler get() = (application as com.example.clock.ClockApp).scheduler

    companion object {
        const val ACTION_START = "com.example.clock.action.TIMER_START"
        const val ACTION_PAUSE = "com.example.clock.action.TIMER_PAUSE"
        const val ACTION_STOP = "com.example.clock.action.TIMER_STOP"
        const val NOTIFICATION_ID = 2001

        fun start(context: Context) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_START
                data = Uri.parse("clock://timer-service/start")
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TimerService::class.java))
            Notifications.cancelNotification(context, NOTIFICATION_ID)
        }
    }
}
