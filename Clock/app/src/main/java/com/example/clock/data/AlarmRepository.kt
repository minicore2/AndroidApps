package com.example.clock.data

import android.content.Context
import com.example.clock.alarm.AlarmScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class AlarmRepository(
    context: Context,
    private val scheduler: AlarmScheduler,
) {
    private val prefs = context.getSharedPreferences("alarms", Context.MODE_PRIVATE)
    private val lock = Any()
    private val _alarms = MutableStateFlow(load())
    val alarms: StateFlow<List<Alarm>> = _alarms.asStateFlow()

    fun find(id: Long): Alarm? = _alarms.value.firstOrNull { it.id == id }

    fun save(alarm: Alarm): Alarm {
        return if (alarm.id == 0L) add(alarm) else update(alarm)
    }

    fun add(alarm: Alarm): Alarm {
        synchronized(lock) {
            val id = maxOf(
                System.currentTimeMillis(),
                (_alarms.value.maxOfOrNull { it.id } ?: 0L) + 1,
            )
            val next = if (alarm.enabled) AlarmTimes.nextOccurrence(alarm) else null
            val saved = alarm.copy(
                id = id,
                nextTriggerAt = next ?: 0L,
                skippedNext = false,
                skipThrough = 0L,
                snoozeUntil = 0L,
            )
            replaceLocked(saved)
            if (saved.enabled && next != null) scheduler.scheduleAt(id, next)
            return saved
        }
    }

    fun update(alarm: Alarm): Alarm {
        synchronized(lock) {
            val next = if (alarm.enabled) AlarmTimes.nextOccurrence(alarm) else null
            val saved = alarm.copy(
                nextTriggerAt = next ?: 0L,
                skippedNext = false,
                skipThrough = 0L,
                snoozeUntil = 0L,
            )
            replaceLocked(saved)
            scheduler.cancel(saved.id)
            if (saved.enabled && next != null) scheduler.scheduleAt(saved.id, next)
            return saved
        }
    }

    fun setEnabled(id: Long, enabled: Boolean) {
        synchronized(lock) {
            val alarm = find(id) ?: return
            val next = if (enabled) AlarmTimes.nextOccurrence(alarm) else null
            val saved = alarm.copy(
                enabled = enabled,
                nextTriggerAt = next ?: 0L,
                skippedNext = false,
                skipThrough = 0L,
                snoozeUntil = 0L,
            )
            replaceLocked(saved)
            scheduler.cancel(id)
            if (enabled && next != null) scheduler.scheduleAt(id, next)
        }
    }

    /**
     * Skip exactly the next occurrence. Repeating alarms keep their settings and
     * fire on the following date; a one-shot alarm is turned off but not deleted.
     */
    fun skipNext(id: Long) {
        synchronized(lock) {
            val alarm = find(id) ?: return
            if (!alarm.enabled) return
            if (!alarm.isRepeating) {
                replaceLocked(
                    alarm.copy(
                        enabled = false,
                        nextTriggerAt = 0L,
                        skippedNext = false,
                        skipThrough = 0L,
                        snoozeUntil = 0L,
                    ),
                )
                scheduler.cancel(id)
                return
            }
            val after = maxOf(System.currentTimeMillis(), alarm.skipThrough + 60_000L)
            val first = AlarmTimes.nextOccurrence(alarm, after) ?: return
            val second = AlarmTimes.nextOccurrence(alarm, first + 60_000L) ?: return
            replaceLocked(
                alarm.copy(
                    nextTriggerAt = second,
                    skippedNext = true,
                    skipThrough = first,
                    snoozeUntil = 0L,
                ),
            )
            scheduler.cancel(id)
            scheduler.scheduleAt(id, second)
        }
    }

    fun delete(id: Long) {
        synchronized(lock) {
            scheduler.cancel(id)
            val updated = _alarms.value.filterNot { it.id == id }
            persistLocked(updated)
            _alarms.value = updated
        }
    }

    /** Called by the alarm receiver for a normal (not snoozed) firing. */
    fun markFired(id: Long) {
        synchronized(lock) {
            val alarm = find(id) ?: return
            if (!alarm.isRepeating) {
                scheduler.cancel(id)
                replaceLocked(
                    alarm.copy(
                        enabled = false,
                        nextTriggerAt = 0L,
                        skippedNext = false,
                        skipThrough = 0L,
                        snoozeUntil = 0L,
                    ),
                )
                return
            }
            val next = AlarmTimes.nextOccurrence(alarm, System.currentTimeMillis() + 60_000L)
            val saved = alarm.copy(
                nextTriggerAt = next ?: 0L,
                skippedNext = false,
                skipThrough = 0L,
                snoozeUntil = 0L,
            )
            replaceLocked(saved)
            scheduler.cancel(id)
            if (next != null) scheduler.scheduleAt(id, next)
        }
    }

    /** Persist and arm a snooze so it also survives a reboot. */
    fun snooze(id: Long) {
        synchronized(lock) {
            val alarm = find(id) ?: return
            val until = System.currentTimeMillis() + alarm.snoozeMinutes.coerceAtLeast(1) * 60_000L
            replaceLocked(alarm.copy(snoozeUntil = until))
            scheduler.scheduleAt(id, until, isSnooze = true)
        }
    }

    fun clearSnooze(id: Long) {
        synchronized(lock) {
            val alarm = find(id) ?: return
            if (alarm.snoozeUntil == 0L) return
            replaceLocked(alarm.copy(snoozeUntil = 0L))
        }
    }

    /** Re-arm alarms that are still in the future, e.g. after the process is recreated. */
    fun ensureScheduled() {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val updated = _alarms.value.map { alarm ->
                var current = alarm
                if (alarm.snoozeUntil > now) {
                    scheduler.scheduleAt(alarm.id, alarm.snoozeUntil, isSnooze = true)
                } else if (alarm.snoozeUntil != 0L) {
                    current = current.copy(snoozeUntil = 0L)
                }
                if (!current.enabled) return@map current
                when {
                    current.nextTriggerAt > now + 2_000L -> {
                        scheduler.scheduleAt(current.id, current.nextTriggerAt)
                        current
                    }
                    current.nextTriggerAt == 0L || current.nextTriggerAt < now - 60_000L -> {
                        if (!current.isRepeating) {
                            // A missed one-shot must not silently move to tomorrow.
                            scheduler.cancel(current.id)
                            if (current.snoozeUntil > now) {
                                scheduler.scheduleAt(current.id, current.snoozeUntil, isSnooze = true)
                            }
                            current.copy(
                                enabled = false,
                                nextTriggerAt = 0L,
                                skippedNext = false,
                                skipThrough = 0L,
                            )
                        } else {
                            val next = computeNext(current, now)
                            if (next == null) {
                                scheduler.cancel(current.id)
                                current.copy(
                                    nextTriggerAt = 0L,
                                    skippedNext = false,
                                    skipThrough = 0L,
                                )
                            } else {
                                scheduler.scheduleAt(current.id, next)
                                val keepSkip = current.skipThrough > now
                                current.copy(
                                    nextTriggerAt = next,
                                    skippedNext = keepSkip,
                                    skipThrough = if (keepSkip) current.skipThrough else 0L,
                                )
                            }
                        }
                    }
                    // Within the firing window: let the pending alarm deliver.
                    else -> current
                }
            }
            if (updated != _alarms.value) {
                persistLocked(updated)
                _alarms.value = updated
            }
        }
    }

    /**
     * Rebuild schedules after a reboot. Pending alarms do not survive a reboot, so a
     * missed one-shot alarm is switched off and a missed repeating alarm moves to its
     * next real occurrence (without applying "skip next" a second time).
     */
    fun rescheduleAfterBoot() {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val updated = _alarms.value.map { alarm ->
                var current = alarm
                if (alarm.snoozeUntil > now) {
                    scheduler.scheduleAt(alarm.id, alarm.snoozeUntil, isSnooze = true)
                } else if (alarm.snoozeUntil != 0L) {
                    current = current.copy(snoozeUntil = 0L)
                }
                if (!current.enabled) return@map current
                if (current.nextTriggerAt > now) {
                    scheduler.scheduleAt(current.id, current.nextTriggerAt)
                    return@map current
                }
                if (!current.isRepeating) {
                    scheduler.cancel(current.id)
                    if (current.snoozeUntil > now) {
                        scheduler.scheduleAt(current.id, current.snoozeUntil, isSnooze = true)
                    }
                    return@map current.copy(
                        enabled = false,
                        nextTriggerAt = 0L,
                        skippedNext = false,
                        skipThrough = 0L,
                    )
                }
                val next = computeNext(current, now)
                if (next == null) {
                    scheduler.cancel(current.id)
                    current.copy(nextTriggerAt = 0L, skippedNext = false, skipThrough = 0L)
                } else {
                    scheduler.scheduleAt(current.id, next)
                    val keepSkip = current.skipThrough > now
                    current.copy(
                        nextTriggerAt = next,
                        skippedNext = keepSkip,
                        skipThrough = if (keepSkip) current.skipThrough else 0L,
                    )
                }
            }
            if (updated != _alarms.value) {
                persistLocked(updated)
                _alarms.value = updated
            }
        }
    }

    fun rescheduleAll(recompute: Boolean) {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val updated = _alarms.value.map { alarm ->
                if (!alarm.enabled) {
                    if (alarm.snoozeUntil <= now) scheduler.cancel(alarm.id)
                    return@map alarm.copy(nextTriggerAt = 0L, skipThrough = 0L, skippedNext = false)
                }
                if (!recompute && alarm.nextTriggerAt > now + 2_000L) {
                    scheduler.cancel(alarm.id)
                    scheduler.scheduleAt(alarm.id, alarm.nextTriggerAt)
                    if (alarm.snoozeUntil > now) {
                        scheduler.scheduleAt(alarm.id, alarm.snoozeUntil, isSnooze = true)
                    }
                    return@map alarm
                }
                if (!recompute && alarm.nextTriggerAt in (now - 60_000L)..(now + 2_000L)) {
                    return@map alarm
                }
                val next = computeNext(alarm, now)
                scheduler.cancel(alarm.id)
                if (next != null) scheduler.scheduleAt(alarm.id, next)
                if (alarm.snoozeUntil > now) {
                    scheduler.scheduleAt(alarm.id, alarm.snoozeUntil, isSnooze = true)
                }
                val keepSkip = alarm.skipThrough > now
                alarm.copy(
                    nextTriggerAt = next ?: 0L,
                    skippedNext = keepSkip,
                    skipThrough = if (keepSkip) alarm.skipThrough else 0L,
                )
            }
            if (updated != _alarms.value) {
                persistLocked(updated)
                _alarms.value = updated
            }
        }
    }

    private fun computeNext(alarm: Alarm, from: Long): Long? {
        val anchor = maxOf(from, alarm.skipThrough + 60_000L)
        return AlarmTimes.nextOccurrence(alarm, anchor)
    }

    private fun replaceLocked(alarm: Alarm) {
        val list = _alarms.value.toMutableList()
        val index = list.indexOfFirst { it.id == alarm.id }
        if (index >= 0) list[index] = alarm else list.add(alarm)
        val sorted = list.sortedWith(compareBy({ it.hour }, { it.minute }, { it.id }))
        persistLocked(sorted)
        _alarms.value = sorted
    }

    private fun persistLocked(list: List<Alarm>) {
        val array = JSONArray()
        list.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_ALARMS, array.toString()).commit()
    }

    private fun load(): List<Alarm> {
        val raw = prefs.getString(KEY_ALARMS, null) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val alarms = mutableListOf<Alarm>()
        for (i in 0 until array.length()) {
            runCatching { array.getJSONObject(i).toAlarm() }.getOrNull()?.let(alarms::add)
        }
        return alarms
    }

    private fun Alarm.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("hour", hour)
        put("minute", minute)
        put("label", label)
        put("enabled", enabled)
        put("repeatDays", repeatDays)
        put("soundUri", soundUri ?: JSONObject.NULL)
        put("soundName", soundName ?: JSONObject.NULL)
        put("vibrate", vibrate)
        put("snoozeMinutes", snoozeMinutes)
        put("volumeRamp", volumeRamp)
        put("slideToDismiss", slideToDismiss)
        put("shakeToDismiss", shakeToDismiss)
        put("nextTriggerAt", nextTriggerAt)
        put("skippedNext", skippedNext)
        put("skipThrough", skipThrough)
        put("snoozeUntil", snoozeUntil)
    }

    private fun JSONObject.toAlarm(): Alarm = Alarm(
        id = optLong("id"),
        hour = optInt("hour", 7),
        minute = optInt("minute", 0),
        label = optString("label", ""),
        enabled = optBoolean("enabled", true),
        repeatDays = optInt("repeatDays", 0),
        soundUri = if (isNull("soundUri")) null else optString("soundUri").ifBlank { null },
        soundName = if (isNull("soundName")) null else optString("soundName").ifBlank { null },
        vibrate = optBoolean("vibrate", true),
        snoozeMinutes = optInt("snoozeMinutes", 10),
        volumeRamp = optBoolean("volumeRamp", true),
        slideToDismiss = optBoolean("slideToDismiss", true),
        shakeToDismiss = optBoolean("shakeToDismiss", false),
        nextTriggerAt = optLong("nextTriggerAt", 0L),
        skippedNext = optBoolean("skippedNext", false),
        skipThrough = optLong("skipThrough", 0L),
        snoozeUntil = optLong("snoozeUntil", 0L),
    )

    private companion object {
        const val KEY_ALARMS = "alarm_list_v1"
    }
}
