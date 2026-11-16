package com.example.clock.alarm

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.clock.ClockApp
import com.example.clock.clockApp
import com.example.clock.data.Alarm
import kotlin.math.min

class AlarmService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var fallbackRingtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var focusRequest: AudioFocusRequest? = null
    private var ringing = false
    private var lastStartId = 0

    private var timerMode = false
    private var timerEndAt = 0L
    private var alarmId = -1L
    private var isSnooze = false
    private var label = "鬧鐘"
    private var soundUri: String? = null
    private var shouldVibrate = true
    private var volumeRamp = true
    private var currentVolume = 0.08f

    private val autoStop = Runnable { stopRinging() }

    private val rampUp = object : Runnable {
        override fun run() {
            val mp = player ?: return
            currentVolume = min(1f, currentVolume + 0.06f)
            mp.setVolume(currentVolume, currentVolume)
            if (currentVolume < 1f) handler.postDelayed(this, 1_400L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        when (intent?.action) {
            ACTION_DISMISS -> {
                stopRinging()
                return START_NOT_STICKY
            }

            ACTION_SNOOZE -> {
                snoozeAndStop()
                return START_NOT_STICKY
            }

            ACTION_START, null -> begin(intent)
            else -> stopRinging()
        }
        return START_NOT_STICKY
    }

    private fun begin(intent: Intent?) {
        val app = application as ClockApp
        val newTimerMode = intent?.getStringExtra(RingContract.EXTRA_KIND) == RingContract.KIND_TIMER
        val newAlarmId = intent?.getLongExtra(RingContract.EXTRA_ALARM_ID, -1L) ?: -1L
        val newIsSnooze = intent?.getBooleanExtra(AlarmReceiver.EXTRA_SNOOZE, false) ?: false
        val newTimerEnd = intent?.getLongExtra(RingContract.EXTRA_TIMER_END, 0L) ?: 0L
        val alarm: Alarm? = if (!newTimerMode && newAlarmId > 0) app.alarms.find(newAlarmId) else null

        // The ticker and the AlarmManager broadcast can both report the same end.
        if (ringing && newTimerMode && timerMode && newTimerEnd != 0L && newTimerEnd == timerEndAt) {
            return
        }

        // One ring at a time: a newer alarm/timer takes over the audio instead of
        // being swallowed. Do not stop the service, or the newer start would die too.
        if (ringing) stopAudioAndVibration()

        timerMode = newTimerMode
        timerEndAt = newTimerEnd
        alarmId = newAlarmId
        isSnooze = newIsSnooze
        label = intent?.getStringExtra(RingContract.EXTRA_LABEL)?.takeIf { it.isNotBlank() }
            ?: alarm?.label?.takeIf { it.isNotBlank() }
            ?: if (timerMode) "計時結束" else "鬧鐘"
        soundUri = alarm?.soundUri
        shouldVibrate = alarm?.vibrate ?: true
        volumeRamp = alarm?.volumeRamp ?: true
        currentVolume = if (volumeRamp) 0.08f else 1f

        val fullScreenIntent = ringActivityIntent()
        val dismissIntent = actionPendingIntent(ACTION_DISMISS)
        val snoozeIntent = if (timerMode) null else actionPendingIntent(ACTION_SNOOZE)
        val title = if (timerMode) "計時結束" else label
        val text = when {
            timerMode -> label
            alarm != null -> buildString {
                append(alarm.timeText)
                if (isSnooze) append(" · 貪睡")
            }
            else -> "鬧鐘響了"
        }

        val notification = Notifications.ringNotification(
            context = this,
            title = title,
            text = text,
            fullScreenIntent = fullScreenIntent,
            dismissIntent = dismissIntent,
            snoozeIntent = snoozeIntent,
        )
        startForeground(Notifications.ALARM_NOTIFICATION_ID, notification)

        ringing = true
        RingingState.begin(alarmId, timerMode, label)

        // The full-screen intent is the supported path; this direct attempt also
        // covers launchers/OEMs that do not deliver the intent immediately.
        runCatching {
            startActivity(
                Intent(this, AlarmRingActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP,
                    )
                    putExtra(RingContract.EXTRA_ALARM_ID, alarmId)
                    putExtra(RingContract.EXTRA_KIND, if (timerMode) RingContract.KIND_TIMER else RingContract.KIND_ALARM)
                    putExtra(RingContract.EXTRA_LABEL, label)
                    putExtra(RingContract.EXTRA_SNOOZE, isSnooze)
                    putExtra(RingContract.EXTRA_TIMER_END, timerEndAt)
                },
            )
        }

        acquireWakeLock()
        startAudio()
        startVibration()
        handler.removeCallbacks(autoStop)
        handler.postDelayed(autoStop, AUTO_STOP_MS)
    }

    private fun ringActivityIntent(): PendingIntent {
        val intent = Intent(this, AlarmRingActivity::class.java).apply {
            data = Uri.parse("clock://ring/${if (timerMode) "timer" else "alarm"}/$alarmId")
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
            putExtra(RingContract.EXTRA_ALARM_ID, alarmId)
            putExtra(RingContract.EXTRA_KIND, if (timerMode) RingContract.KIND_TIMER else RingContract.KIND_ALARM)
            putExtra(RingContract.EXTRA_LABEL, label)
            putExtra(RingContract.EXTRA_SNOOZE, isSnooze)
            putExtra(RingContract.EXTRA_TIMER_END, timerEndAt)
        }
        return PendingIntent.getActivity(
            this,
            10,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun actionPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, AlarmService::class.java).apply {
            this.action = action
            data = Uri.parse("clock://alarm-service/$action/$alarmId/$timerMode/$isSnooze")
        }
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val power = getSystemService(PowerManager::class.java)
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Clock:ringing").apply {
            setReferenceCounted(false)
            acquire(15 * 60_000L)
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
        wakeLock = null
    }

    private fun startAudio() {
        val attributes: AudioAttributes = Notifications.alarmAudioAttributes()
        val uri = Notifications.playableUri(soundUri)
        requestAudioFocus()

        val mp = MediaPlayer()
        player = mp
        try {
            mp.setAudioAttributes(attributes)
            mp.setDataSource(this, uri)
            mp.isLooping = true
            mp.setOnPreparedListener { prepared ->
                if (player !== prepared) {
                    runCatching { prepared.release() }
                    return@setOnPreparedListener
                }
                runCatching {
                    prepared.setVolume(currentVolume, currentVolume)
                    prepared.start()
                    if (volumeRamp) {
                        handler.removeCallbacks(rampUp)
                        handler.postDelayed(rampUp, 1_500L)
                    } else {
                        currentVolume = 1f
                        prepared.setVolume(1f, 1f)
                    }
                }.onFailure {
                    Log.e(TAG, "MediaPlayer start failed", it)
                    playFallback(attributes)
                }
            }
            mp.setOnErrorListener { failed, _, _ ->
                if (player === failed) {
                    player = null
                    runCatching { failed.release() }
                    playFallback(attributes)
                }
                true
            }
            mp.prepareAsync()
        } catch (t: Throwable) {
            Log.e(TAG, "MediaPlayer prepare failed", t)
            if (player === mp) player = null
            runCatching { mp.release() }
            playFallback(attributes)
        }
    }

    private fun playFallback(attributes: AudioAttributes) {
        runCatching { player?.release() }
        player = null
        if (!ringing) return
        val uri = Notifications.playableUri(soundUri)
        val ringtone = runCatching { RingtoneManager.getRingtone(this, uri) }.getOrNull()
            ?: runCatching {
                RingtoneManager.getRingtone(
                    this,
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                )
            }.getOrNull()
        if (ringtone == null) {
            Log.e(TAG, "No playable ringtone for $uri")
            return
        }
        runCatching {
            ringtone.audioAttributes = attributes
            ringtone.play()
            fallbackRingtone = ringtone
            currentVolume = 1f
        }.onFailure { Log.e(TAG, "Ringtone fallback failed", it) }
    }

    private fun requestAudioFocus() {
        val manager = getSystemService(AudioManager::class.java)
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(Notifications.alarmAudioAttributes())
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener { }
            .build()
        focusRequest = request
        runCatching { manager.requestAudioFocus(request) }
    }

    private fun abandonAudioFocus() {
        focusRequest?.let {
            runCatching { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        }
        focusRequest = null
    }

    private fun startVibration() {
        if (!shouldVibrate) return
        val v: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        if (v == null || !v.hasVibrator()) return
        val pattern = longArrayOf(0L, 700L, 500L, 700L, 900L)
        runCatching {
            v.vibrate(VibrationEffect.createWaveform(pattern, 0))
            vibrator = v
        }
    }

    private fun snoozeAndStop() {
        if (!timerMode && alarmId > 0) {
            val app = application as ClockApp
            app.alarms.snooze(alarmId)
        }
        stopRinging(clearSnooze = false)
    }

    /**
     * Stops sound/vibration without touching the foreground state or notification,
     * so a newer alarm can take over this service instance.
     */
    private fun stopAudioAndVibration() {
        handler.removeCallbacks(rampUp)
        handler.removeCallbacks(autoStop)
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        runCatching { fallbackRingtone?.stop() }
        fallbackRingtone = null
        runCatching { vibrator?.cancel() }
        vibrator = null
        abandonAudioFocus()
        releaseWakeLock()
        ringing = false
    }

    private fun stopRinging(clearSnooze: Boolean = true) {
        val app = application as ClockApp
        if (clearSnooze && isSnooze && alarmId > 0) {
            app.alarms.clearSnooze(alarmId)
        }
        stopAudioAndVibration()
        RingingState.clear()
        Notifications.cancelNotification(this, Notifications.ALARM_NOTIFICATION_ID)
        sendBroadcast(Intent(ACTION_RING_STOPPED).setPackage(packageName))
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (lastStartId != 0) stopSelf(lastStartId) else stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        val wasRinging = ringing
        stopAudioAndVibration()
        if (wasRinging) RingingState.clear()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AlarmService"
        const val ACTION_START = "com.example.clock.action.START_RINGING"
        const val ACTION_DISMISS = "com.example.clock.action.DISMISS_RINGING"
        const val ACTION_SNOOZE = "com.example.clock.action.SNOOZE_RINGING"
        const val ACTION_RING_STOPPED = "com.example.clock.action.RING_STOPPED"
        private const val AUTO_STOP_MS = 10 * 60_000L

        fun start(context: Context, alarmId: Long, isSnooze: Boolean): Boolean {
            val intent = Intent(context, AlarmService::class.java).apply {
                action = ACTION_START
                data = Uri.parse("clock://start/$alarmId/$isSnooze")
                putExtra(RingContract.EXTRA_ALARM_ID, alarmId)
                putExtra(AlarmReceiver.EXTRA_SNOOZE, isSnooze)
            }
            return startService(context, intent)
        }

        fun startTimer(context: Context, label: String, endAt: Long): Boolean {
            val intent = Intent(context, AlarmService::class.java).apply {
                action = ACTION_START
                data = Uri.parse("clock://start/timer/$endAt")
                putExtra(RingContract.EXTRA_KIND, RingContract.KIND_TIMER)
                putExtra(RingContract.EXTRA_LABEL, label)
                putExtra(RingContract.EXTRA_TIMER_END, endAt)
            }
            return startService(context, intent)
        }

        fun dismiss(context: Context, alarmId: Long, timerMode: Boolean) {
            val intent = Intent(context, AlarmService::class.java).apply {
                action = ACTION_DISMISS
                data = Uri.parse("clock://dismiss/$alarmId/$timerMode")
            }
            context.startService(intent)
        }

        fun snooze(context: Context, alarmId: Long) {
            val intent = Intent(context, AlarmService::class.java).apply {
                action = ACTION_SNOOZE
                data = Uri.parse("clock://snooze/$alarmId")
            }
            context.startService(intent)
        }

        /**
         * Last-resort UI when the OS refuses to start the foreground service (for
         * example an inexact fallback alarm on Android 12+). A notification is legal
         * from a broadcast even when a background service start is not.
         */
        fun postFallbackNotification(context: Context, alarmId: Long, isSnooze: Boolean) {
            val app = context.clockApp
            val alarm = app.alarms.find(alarmId)
            val label = alarm?.label?.takeIf { it.isNotBlank() } ?: "鬧鐘"
            val text = alarm?.let {
                buildString {
                    append(it.timeText)
                    if (isSnooze) append(" · 貪睡")
                }
            } ?: "鬧鐘響了"
            val ringIntent = Intent(context, AlarmRingActivity::class.java).apply {
                data = Uri.parse("clock://ring/alarm/$alarmId")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(RingContract.EXTRA_ALARM_ID, alarmId)
                putExtra(RingContract.EXTRA_KIND, RingContract.KIND_ALARM)
                putExtra(RingContract.EXTRA_LABEL, label)
                putExtra(RingContract.EXTRA_SNOOZE, isSnooze)
            }
            val fullScreenIntent = PendingIntent.getActivity(
                context,
                10,
                ringIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val dismissIntent = PendingIntent.getService(
                context,
                ACTION_DISMISS.hashCode(),
                Intent(context, AlarmService::class.java).apply {
                    action = ACTION_DISMISS
                    data = Uri.parse("clock://dismiss/$alarmId/false")
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val snoozeIntent = PendingIntent.getService(
                context,
                ACTION_SNOOZE.hashCode(),
                Intent(context, AlarmService::class.java).apply {
                    action = ACTION_SNOOZE
                    data = Uri.parse("clock://snooze/$alarmId")
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = Notifications.ringNotification(
                context = context,
                title = label,
                text = text,
                fullScreenIntent = fullScreenIntent,
                dismissIntent = dismissIntent,
                snoozeIntent = snoozeIntent,
                audible = true,
            )
            runCatching {
                NotificationManagerCompat.from(context)
                    .notify(Notifications.ALARM_NOTIFICATION_ID, notification)
            }.onFailure { Log.e(TAG, "Fallback notification failed", it) }
            runCatching { context.startActivity(ringIntent) }
        }

        fun postFallbackTimerNotification(context: Context, label: String, endAt: Long) {
            val ringIntent = Intent(context, AlarmRingActivity::class.java).apply {
                data = Uri.parse("clock://ring/timer/$endAt")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(RingContract.EXTRA_KIND, RingContract.KIND_TIMER)
                putExtra(RingContract.EXTRA_LABEL, label)
                putExtra(RingContract.EXTRA_TIMER_END, endAt)
            }
            val fullScreenIntent = PendingIntent.getActivity(
                context,
                10,
                ringIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val dismissIntent = PendingIntent.getService(
                context,
                ACTION_DISMISS.hashCode(),
                Intent(context, AlarmService::class.java).apply {
                    action = ACTION_DISMISS
                    data = Uri.parse("clock://dismiss/0/true")
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = Notifications.ringNotification(
                context = context,
                title = "計時結束",
                text = label,
                fullScreenIntent = fullScreenIntent,
                dismissIntent = dismissIntent,
                snoozeIntent = null,
                audible = true,
            )
            runCatching {
                NotificationManagerCompat.from(context)
                    .notify(Notifications.ALARM_NOTIFICATION_ID, notification)
            }.onFailure { Log.e(TAG, "Fallback timer notification failed", it) }
            runCatching { context.startActivity(ringIntent) }
        }

        private fun startService(context: Context, intent: Intent): Boolean {
            try {
                ContextCompat.startForegroundService(context, intent)
                return true
            } catch (t: Throwable) {
                Log.e(TAG, "startForegroundService failed", t)
                return runCatching {
                    context.startService(intent)
                    true
                }.getOrDefault(false)
            }
        }
    }
}
