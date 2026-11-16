package com.example.clock.ui

import android.content.Context
import android.text.format.DateFormat
import com.example.clock.data.TimeFormatMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatClock(context: Context, hour: Int, minute: Int, mode: TimeFormatMode): String {
    val calendar = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, hour)
        set(java.util.Calendar.MINUTE, minute)
        set(java.util.Calendar.SECOND, 0)
    }
    val date = calendar.time
    return when (mode) {
        TimeFormatMode.SYSTEM -> DateFormat.getTimeFormat(context).format(date)
        TimeFormatMode.H24 -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        TimeFormatMode.H12 -> SimpleDateFormat("a h:mm", Locale.getDefault()).format(date)
    }
}

fun nextOccurrenceLabel(context: Context, nextTriggerAt: Long): String {
    if (nextTriggerAt <= 0L) return "未排定"
    val zone = java.time.ZoneId.systemDefault()
    val now = java.time.ZonedDateTime.now(zone)
    val target = java.time.Instant.ofEpochMilli(nextTriggerAt).atZone(zone)
    val days = java.time.temporal.ChronoUnit.DAYS.between(now.toLocalDate(), target.toLocalDate())
    val time = DateFormat.getTimeFormat(context).format(Date(nextTriggerAt))
    return when (days) {
        0L -> "今天 $time"
        1L -> "明天 $time"
        else -> "${target.monthValue}/${target.dayOfMonth} $time"
    }
}

fun formatDuration(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) + 500L) / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}
