package com.example.clock.data

import androidx.compose.runtime.Immutable
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Bit 0 = Monday ... bit 6 = Sunday. Zero means a one-shot alarm. */
const val NO_REPEAT = 0
const val EVERY_DAY = 0b1111111
const val WEEKDAYS = 0b0011111

fun dayBit(day: DayOfWeek): Int = 1 shl (day.value - 1)

fun hasDay(mask: Int, day: DayOfWeek): Boolean = mask and dayBit(day) != 0

@Immutable
data class Alarm(
    val id: Long = 0L,
    val hour: Int = 7,
    val minute: Int = 0,
    val label: String = "",
    val enabled: Boolean = true,
    val repeatDays: Int = NO_REPEAT,
    val soundUri: String? = null,
    val soundName: String? = null,
    val vibrate: Boolean = true,
    val snoozeMinutes: Int = 10,
    val volumeRamp: Boolean = true,
    val slideToDismiss: Boolean = true,
    val shakeToDismiss: Boolean = false,
    /** Epoch millis of the currently scheduled occurrence, for display and boot recovery. */
    val nextTriggerAt: Long = 0L,
    /** The next occurrence was intentionally skipped; the badge clears when the alarm fires. */
    val skippedNext: Boolean = false,
    /** The occurrence that was skipped. Idempotent when schedules are rebuilt. */
    val skipThrough: Long = 0L,
    /** A snooze that must survive process death / reboot. */
    val snoozeUntil: Long = 0L,
) {
    val isRepeating: Boolean get() = repeatDays != NO_REPEAT
    val timeText: String get() = "%02d:%02d".format(hour, minute)
}

object AlarmTimes {

    /** Next occurrence strictly after [afterMillis], honouring the day mask. */
    fun nextOccurrence(alarm: Alarm, afterMillis: Long = System.currentTimeMillis()): Long? {
        val zone = ZoneId.systemDefault()
        val after = Instant.ofEpochMilli(afterMillis).atZone(zone)
        val afterLocal = after.toLocalDateTime()
        val time = LocalTime.of(alarm.hour.coerceIn(0, 23), alarm.minute.coerceIn(0, 59))

        if (alarm.repeatDays == NO_REPEAT) {
            var candidate = LocalDateTime.of(after.toLocalDate(), time)
            if (!candidate.isAfter(afterLocal)) candidate = candidate.plusDays(1)
            return candidate.atZone(zone).toInstant().toEpochMilli()
        }

        for (offset in 0..7L) {
            val date = after.toLocalDate().plusDays(offset)
            if (!hasDay(alarm.repeatDays, date.dayOfWeek)) continue
            val candidate = LocalDateTime.of(date, time)
            if (candidate.isAfter(afterLocal)) {
                return candidate.atZone(zone).toInstant().toEpochMilli()
            }
        }
        return null
    }
}
