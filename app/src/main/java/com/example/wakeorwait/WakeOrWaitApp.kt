package com.example.wakeorwait

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.os.Build
import android.provider.Settings

class WakeOrWaitApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            val alarmChannel = NotificationChannel(
                ALARM_CHANNEL_ID,
                "WakeOrWait Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent alarm notifications that trigger challenges"
                setSound(Settings.System.DEFAULT_ALARM_ALERT_URI, audioAttributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                setBypassDnd(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            val timeoutChannel = NotificationChannel(
                TIMEOUT_CHANNEL_ID,
                "WakeOrWait Alarm Results",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for alarm completion and 10-minute timeouts"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(alarmChannel)
            notificationManager?.createNotificationChannel(timeoutChannel)
        }
    }

    companion object {
        const val ALARM_CHANNEL_ID = "wakeorwait_alarm_channel"
        const val TIMEOUT_CHANNEL_ID = "wakeorwait_timeout_channel"
    }
}
