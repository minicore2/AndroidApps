package com.example.clock

import android.app.Application
import android.content.Context
import com.example.clock.alarm.AlarmScheduler
import com.example.clock.alarm.Notifications
import com.example.clock.data.AlarmRepository
import com.example.clock.data.SettingsRepository
import com.example.clock.timer.TimerRepository

class ClockApp : Application() {
    lateinit var scheduler: AlarmScheduler
        private set
    lateinit var alarms: AlarmRepository
        private set
    lateinit var settings: SettingsRepository
        private set
    lateinit var timer: TimerRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        Notifications.createChannels(this)
        scheduler = AlarmScheduler(this)
        alarms = AlarmRepository(this, scheduler)
        settings = SettingsRepository(this)
        timer = TimerRepository(this)
        alarms.ensureScheduled()

        val timerState = timer.state.value
        if (timerState.running) {
            if (timerState.endAt > System.currentTimeMillis()) {
                scheduler.scheduleTimer(timerState.endAt, timerState.label)
            } else {
                timer.markFinished(timerState.label)
            }
        }
    }

    companion object {
        lateinit var instance: ClockApp
            private set
    }
}

val Context.clockApp: ClockApp
    get() = applicationContext as ClockApp
