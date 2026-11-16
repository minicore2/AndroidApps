package com.example.clock.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.clock.MainActivity
import com.example.clock.data.Alarm
import com.example.clock.data.AlarmTimes

class AlarmScheduler(private val context: Context) {

    private val alarmManager: AlarmManager =
        context.getSystemService(AlarmManager::class.java)

    fun scheduleAt(alarmId: Long, triggerAt: Long, isSnooze: Boolean = false) {
        if (triggerAt <= System.currentTimeMillis()) return
        val operation = alarmPendingIntent(alarmId, isSnooze)
        val showIntent = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            // setAlarmClock is exact, wakes the device, survives Doze, and is shown
            // in the system status bar as the next alarm.
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showIntent), operation)
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
        }
    }

    fun scheduleNext(alarm: Alarm) {
        val next = AlarmTimes.nextOccurrence(alarm) ?: return
        scheduleAt(alarm.id, next)
    }

    fun cancel(alarmId: Long) {
        alarmManager.cancel(alarmPendingIntent(alarmId, isSnooze = false))
        alarmManager.cancel(alarmPendingIntent(alarmId, isSnooze = true))
    }

    fun scheduleTimer(endAt: Long, label: String) {
        if (endAt <= System.currentTimeMillis()) return
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TIMER_FIRE
            data = Uri.parse("clock://timer/fire")
            putExtra(AlarmReceiver.EXTRA_KIND, AlarmReceiver.KIND_TIMER)
            putExtra(AlarmReceiver.EXTRA_LABEL, label)
            putExtra(AlarmReceiver.EXTRA_TIMER_END, endAt)
        }
        val operation = PendingIntent.getBroadcast(
            context,
            2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            // A countdown is not the system "next alarm"; do not hijack that icon.
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAt, operation)
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAt, operation)
        }
    }

    fun cancelTimer() {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TIMER_FIRE
            data = Uri.parse("clock://timer/fire")
        }
        val operation = PendingIntent.getBroadcast(
            context,
            2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.cancel(operation)
    }

    private fun alarmPendingIntent(alarmId: Long, isSnooze: Boolean): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_ALARM_FIRE
            data = Uri.parse("clock://alarm/$alarmId?snooze=$isSnooze")
            putExtra(AlarmReceiver.EXTRA_ID, alarmId)
            putExtra(AlarmReceiver.EXTRA_SNOOZE, isSnooze)
        }
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
