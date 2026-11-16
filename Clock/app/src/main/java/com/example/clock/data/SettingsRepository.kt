package com.example.clock.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TimeFormatMode { SYSTEM, H24, H12 }

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _timeFormat = MutableStateFlow(
        runCatching {
            TimeFormatMode.valueOf(prefs.getString(KEY_TIME_FORMAT, TimeFormatMode.SYSTEM.name)!!)
        }.getOrDefault(TimeFormatMode.SYSTEM),
    )
    val timeFormat: StateFlow<TimeFormatMode> = _timeFormat.asStateFlow()

    fun setTimeFormat(mode: TimeFormatMode) {
        prefs.edit().putString(KEY_TIME_FORMAT, mode.name).apply()
        _timeFormat.value = mode
    }

    private companion object {
        const val KEY_TIME_FORMAT = "time_format"
    }
}
