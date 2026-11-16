package com.example.clock.alarm

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RingingInfo(
    val alarmId: Long,
    val timerMode: Boolean,
    val label: String,
)

/**
 * In-process fallback so opening the app while the alarm rings still presents the
 * dismiss UI on devices where background activity starts or full-screen intents
 * are restricted.
 */
object RingingState {
    private val _current = MutableStateFlow<RingingInfo?>(null)
    val current: StateFlow<RingingInfo?> = _current.asStateFlow()

    fun begin(alarmId: Long, timerMode: Boolean, label: String) {
        _current.value = RingingInfo(alarmId, timerMode, label)
    }

    fun clear() {
        _current.value = null
    }
}
