package com.example.clock.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.clock.MainActivity
import com.example.clock.R

object Notifications {
    const val CHANNEL_ALARM_PLAIN = "alarm_channel_v2_plain"
    const val CHANNEL_ALARM_DND = "alarm_channel_v2_dnd"
    const val CHANNEL_ALARM_FALLBACK_PLAIN = "alarm_fallback_v1_plain"
    const val CHANNEL_ALARM_FALLBACK_DND = "alarm_fallback_v1_dnd"
    const val CHANNEL_TIMER = "timer_channel_v1"
    const val ALARM_NOTIFICATION_ID = 1001

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)

        val alarm = NotificationChannel(
            CHANNEL_ALARM_PLAIN,
            "鬧鐘",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "鬧鐘與計時器響鈴。"
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            setShowBadge(true)
        }
        manager.createNotificationChannel(alarm)
        manager.createNotificationChannel(fallbackChannelDnd(false))

        // Bypass-DND is frozen when a channel is created, so the bypassing channel
        // must not exist until the user has actually granted policy access.
        if (manager.isNotificationPolicyAccessGranted) {
            manager.createNotificationChannel(alarmChannelDnd(false))
            manager.createNotificationChannel(fallbackChannelDnd(true))
        }

        val timer = NotificationChannel(
            CHANNEL_TIMER,
            "倒數計時",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "顯示倒數計時進行中的通知。"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        manager.createNotificationChannel(timer)
    }

    private fun alarmChannelDnd(fallback: Boolean) = NotificationChannel(
        if (fallback) CHANNEL_ALARM_FALLBACK_DND else CHANNEL_ALARM_DND,
        if (fallback) "鬧鐘（備援，略過勿擾）" else "鬧鐘（略過勿擾）",
        NotificationManager.IMPORTANCE_HIGH,
    ).apply {
        description = "鬧鐘與計時器響鈴。此頻道可略過勿擾模式的限制。"
        if (fallback) {
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                alarmAudioAttributes(),
            )
            enableVibration(true)
        } else {
            setSound(null, null)
            enableVibration(false)
        }
        setBypassDnd(true)
        lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        setShowBadge(true)
    }

    /** Used only when the OS refuses the foreground service: make the notification audible. */
    private fun fallbackChannelDnd(dnd: Boolean): NotificationChannel = NotificationChannel(
        if (dnd) CHANNEL_ALARM_FALLBACK_DND else CHANNEL_ALARM_FALLBACK_PLAIN,
        if (dnd) "鬧鐘（備援，略過勿擾）" else "鬧鐘（備援）",
        NotificationManager.IMPORTANCE_HIGH,
    ).apply {
        description = "前景服務無法啟動時，以通知音提醒使用者。"
        setSound(
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            alarmAudioAttributes(),
        )
        enableVibration(true)
        vibrationPattern = longArrayOf(0L, 700L, 500L, 700L, 900L)
        setBypassDnd(dnd)
        lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        setShowBadge(true)
    }

    private fun alarmChannel(context: Context): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return CHANNEL_ALARM_PLAIN
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.isNotificationPolicyAccessGranted) return CHANNEL_ALARM_PLAIN
        manager.createNotificationChannel(alarmChannelDnd(false))
        return CHANNEL_ALARM_DND
    }

    private fun fallbackChannel(context: Context): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return CHANNEL_ALARM_FALLBACK_PLAIN
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.isNotificationPolicyAccessGranted) return CHANNEL_ALARM_FALLBACK_PLAIN
        manager.createNotificationChannel(alarmChannelDnd(true))
        return CHANNEL_ALARM_FALLBACK_DND
    }

    fun ringNotification(
        context: Context,
        title: String,
        text: String,
        fullScreenIntent: PendingIntent,
        dismissIntent: PendingIntent,
        snoozeIntent: PendingIntent?,
        audible: Boolean = false,
    ): android.app.Notification {
        val builder = NotificationCompat.Builder(
            context,
            if (audible) fallbackChannel(context) else alarmChannel(context),
        )
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreenIntent)
            .setFullScreenIntent(fullScreenIntent, true)
            .setColor(0xFFFFB74D.toInt())
            .addAction(0, "停止", dismissIntent)
        if (!audible) builder.setSilent(true)
        if (snoozeIntent != null) {
            builder.addAction(0, "貪睡", snoozeIntent)
        }
        return builder.build()
    }

    fun timerNotification(
        context: Context,
        title: String,
        text: String,
        endAt: Long,
        pauseIntent: PendingIntent,
        stopIntent: PendingIntent,
    ): android.app.Notification {
        val open = PendingIntent.getActivity(
            context,
            21,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_TIMER)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(true)
            .setWhen(endAt)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setContentIntent(open)
            .addAction(0, "暫停", pauseIntent)
            .addAction(0, "停止", stopIntent)
            .build()
    }

    fun cancelNotification(context: Context, id: Int) {
        NotificationManagerCompat.from(context).cancel(id)
    }

    fun playableUri(raw: String?): Uri {
        val parsed = raw?.takeIf { it.isNotBlank() }?.let(Uri::parse)
        if (parsed != null) return parsed
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI
    }

    fun alarmAudioAttributes(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
}
