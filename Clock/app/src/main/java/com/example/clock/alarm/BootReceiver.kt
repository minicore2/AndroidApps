package com.example.clock.alarm

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.clock.clockApp

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.clockApp
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> {
                app.alarms.rescheduleAfterBoot()

                // A countdown does not get its AlarmManager trigger back after a
                // reboot. Re-arm it, or finish it if it already expired.
                val timer = app.timer.state.value
                if (timer.running) {
                    if (timer.endAt > System.currentTimeMillis()) {
                        app.scheduler.scheduleTimer(timer.endAt, timer.label)
                    } else {
                        app.timer.markFinished(timer.label)
                    }
                }
            }

            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> app.alarms.rescheduleAll(recompute = true)

            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            -> app.alarms.rescheduleAll(recompute = false)
        }
    }
}
