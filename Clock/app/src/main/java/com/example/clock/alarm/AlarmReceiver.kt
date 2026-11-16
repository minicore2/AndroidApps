package com.example.clock.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.clock.clockApp

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.clockApp
        when (intent.action) {
            ACTION_ALARM_FIRE -> {
                val id = intent.getLongExtra(EXTRA_ID, -1L)
                val snooze = intent.getBooleanExtra(EXTRA_SNOOZE, false)
                if (id <= 0L) return
                if (!snooze) app.alarms.markFired(id)
                if (!AlarmService.start(context, id, snooze)) {
                    AlarmService.postFallbackNotification(context, id, snooze)
                }
            }

            ACTION_TIMER_FIRE -> {
                val label = intent.getStringExtra(EXTRA_LABEL)?.ifBlank { "計時結束" } ?: "計時結束"
                val endAt = intent.getLongExtra(EXTRA_TIMER_END, 0L)
                app.timer.markFinished(label)
                if (!AlarmService.startTimer(context, label, endAt)) {
                    AlarmService.postFallbackTimerNotification(context, label, endAt)
                }
            }
        }
    }

    companion object {
        const val ACTION_ALARM_FIRE = "com.example.clock.action.ALARM_FIRE"
        const val ACTION_TIMER_FIRE = "com.example.clock.action.TIMER_FIRE"
        const val EXTRA_ID = "alarm_id"
        const val EXTRA_SNOOZE = "is_snooze"
        const val EXTRA_KIND = "kind"
        const val EXTRA_LABEL = "label"
        const val EXTRA_TIMER_END = "timer_end"
        const val KIND_TIMER = "timer"
    }
}
