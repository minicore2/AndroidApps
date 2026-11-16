package com.example.clock.ui.stopwatch

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class StopwatchViewModel : ViewModel() {

    data class Lap(val number: Int, val splitMs: Long, val totalMs: Long)

    data class UiState(
        val running: Boolean = false,
        val startedAt: Long = 0L,
        val accumulated: Long = 0L,
        val laps: List<Lap> = emptyList(),
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    /** Elapsed milliseconds counted when paused, plus the running segment start. */
    private var startTime = 0L
    private var accumulated = 0L

    fun toggle() {
        val now = SystemClock.elapsedRealtime()
        val current = _ui.value
        if (current.running) {
            accumulated += now - startTime
            _ui.update { it.copy(running = false, startedAt = 0L, accumulated = accumulated) }
        } else {
            startTime = now
            _ui.update { it.copy(running = true, startedAt = startTime, accumulated = accumulated) }
        }
    }

    fun lap() {
        val current = _ui.value
        if (!current.running) return
        val now = SystemClock.elapsedRealtime()
        val elapsed = current.accumulated + now - startTime
        val previousTotal = current.laps.lastOrNull()?.totalMs ?: 0L
        _ui.update {
            it.copy(laps = listOf(Lap(current.laps.size + 1, elapsed - previousTotal, elapsed)) + it.laps)
        }
    }

    fun reset() {
        accumulated = 0L
        startTime = 0L
        _ui.value = UiState()
    }
}
