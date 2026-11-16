package com.example.clock.timer

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TimerState(
    val durationMs: Long = 10 * 60_000L,
    val endAt: Long = 0L,
    val pausedRemainingMs: Long = 10 * 60_000L,
    val running: Boolean = false,
    val label: String = "計時",
) {
    fun remainingMs(now: Long = System.currentTimeMillis()): Long =
        if (running) (endAt - now).coerceAtLeast(0L) else pausedRemainingMs.coerceAtLeast(0L)

    fun progress(now: Long = System.currentTimeMillis()): Float {
        if (durationMs <= 0L) return 0f
        return (remainingMs(now).toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    }
}

class TimerRepository(context: Context) {
    private val prefs = context.getSharedPreferences("timer", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<TimerState> = _state.asStateFlow()

    fun start(durationMs: Long, label: String) {
        val duration = durationMs.coerceAtLeast(1_000L)
        update(
            TimerState(
                durationMs = duration,
                endAt = System.currentTimeMillis() + duration,
                pausedRemainingMs = duration,
                running = true,
                label = label,
            ),
        )
    }

    fun pause() {
        val current = _state.value
        if (!current.running) return
        val remaining = current.remainingMs().coerceAtLeast(0L)
        update(
            current.copy(
                running = false,
                endAt = 0L,
                pausedRemainingMs = remaining,
            ),
        )
    }

    fun resume() {
        val current = _state.value
        if (current.running || current.pausedRemainingMs <= 0L) return
        update(
            current.copy(
                running = true,
                endAt = System.currentTimeMillis() + current.pausedRemainingMs,
            ),
        )
    }

    fun reset() {
        val current = _state.value
        update(
            current.copy(
                running = false,
                endAt = 0L,
                pausedRemainingMs = current.durationMs,
            ),
        )
    }

    fun setDuration(durationMs: Long) {
        val current = _state.value
        if (current.running) return
        val duration = durationMs.coerceAtLeast(1_000L)
        update(
            current.copy(
                durationMs = duration,
                pausedRemainingMs = duration,
                endAt = 0L,
            ),
        )
    }

    fun markFinished(label: String? = null) {
        val current = _state.value
        update(
            current.copy(
                running = false,
                endAt = 0L,
                pausedRemainingMs = 0L,
                label = label ?: current.label,
            ),
        )
    }

    private fun update(state: TimerState) {
        _state.value = state
        prefs.edit()
            .putLong("durationMs", state.durationMs)
            .putLong("endAt", state.endAt)
            .putLong("pausedRemainingMs", state.pausedRemainingMs)
            .putBoolean("running", state.running)
            .putString("label", state.label)
            .apply()
    }

    private fun load(): TimerState {
        val duration = prefs.getLong("durationMs", 10 * 60_000L)
        return TimerState(
            durationMs = duration,
            endAt = prefs.getLong("endAt", 0L),
            pausedRemainingMs = prefs.getLong("pausedRemainingMs", duration),
            running = prefs.getBoolean("running", false),
            label = prefs.getString("label", "計時") ?: "計時",
        )
    }
}
