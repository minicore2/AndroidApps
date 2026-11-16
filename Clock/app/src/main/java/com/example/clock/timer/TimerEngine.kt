package com.example.clock.timer

import android.content.Context
import com.example.clock.clockApp

/** Coordinates timer state, the foreground service, and the exact end-of-timer alarm. */
object TimerEngine {

    fun start(context: Context, durationMs: Long, label: String) {
        context.clockApp.timer.start(durationMs, label)
        TimerService.start(context)
    }

    fun pause(context: Context) {
        val app = context.clockApp
        app.timer.pause()
        app.scheduler.cancelTimer()
        TimerService.stop(context)
    }

    fun resume(context: Context) {
        context.clockApp.timer.resume()
        TimerService.start(context)
    }

    fun reset(context: Context) {
        val app = context.clockApp
        app.timer.reset()
        app.scheduler.cancelTimer()
        TimerService.stop(context)
    }

    fun setDuration(context: Context, durationMs: Long) {
        context.clockApp.timer.setDuration(durationMs)
    }
}
