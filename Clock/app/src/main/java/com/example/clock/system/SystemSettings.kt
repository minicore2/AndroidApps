package com.example.clock.system

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

data class SystemStatus(
    val exactAlarms: Boolean,
    val notificationsEnabled: Boolean,
    val fullScreenAllowed: Boolean,
    val dndAccess: Boolean,
    val batteryUnrestricted: Boolean,
    val alarmVolume: Int,
    val alarmVolumeMax: Int,
) {
    val alarmVolumePercent: Int
        get() = if (alarmVolumeMax <= 0) 0 else (alarmVolume * 100 / alarmVolumeMax)

    val allCriticalOk: Boolean
        get() = exactAlarms && notificationsEnabled && fullScreenAllowed && alarmVolume > 0
}

object SystemSettings {

    fun read(context: Context): SystemStatus {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val power = context.getSystemService(PowerManager::class.java)
        val audio = context.getSystemService(AudioManager::class.java)

        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        val notifications = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED)
        val fullScreen = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            notificationManager.canUseFullScreenIntent()
        val dnd = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            notificationManager.isNotificationPolicyAccessGranted
        val battery = power.isIgnoringBatteryOptimizations(context.packageName)
        val volume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        val volumeMax = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)

        return SystemStatus(
            exactAlarms = exact,
            notificationsEnabled = notifications,
            fullScreenAllowed = fullScreen,
            dndAccess = dnd,
            batteryUnrestricted = battery,
            alarmVolume = volume,
            alarmVolumeMax = volumeMax,
        )
    }

    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val intent = Intent(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            Uri.parse("package:${context.packageName}"),
        )
        launch(context, intent)
    }

    fun openAppNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        launch(context, intent)
    }

    fun openFullScreenIntentSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            launch(
                context,
                Intent(
                    Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        } else {
            openAppNotificationSettings(context)
        }
    }

    fun openDndAccessSettings(context: Context) {
        launch(context, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
    }

    fun openBatterySettings(context: Context) {
        val request = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}"),
        )
        if (!launch(context, request)) {
            launch(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    fun openAlarmVolumeSettings(context: Context) {
        val sound = Intent(Settings.ACTION_SOUND_SETTINGS)
        if (!launch(context, sound)) launch(context, Intent(Settings.ACTION_SETTINGS))
    }

    fun openAppSettings(context: Context) {
        launch(
            context,
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}"),
            ),
        )
    }

    private fun launch(context: Context, intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrElse {
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                true
            }.getOrDefault(false)
        }
    }
}
