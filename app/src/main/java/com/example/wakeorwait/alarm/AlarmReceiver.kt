package com.example.wakeorwait.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.wakeorwait.ui.screens.AlarmActivity

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_ID) ?: ""
        val difficulty = intent.getStringExtra(AlarmScheduler.EXTRA_DIFFICULTY) ?: "NORMAL"
        val ringtoneName = intent.getStringExtra(AlarmScheduler.EXTRA_RINGTONE_NAME) ?: "Siren Blitz"
        val ringtoneUri = intent.getStringExtra(AlarmScheduler.EXTRA_RINGTONE_URI)
        val alarmTime = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_TIME) ?: "7:00 AM"

        Log.d("AlarmReceiver", "Alarm received: id=$alarmId, diff=$difficulty, time=$alarmTime")

        // 1. Acquire temporary wake lock
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "WakeOrWait:AlarmWakeLock"
        )
        wakeLock.acquire(30000) // 30 seconds max safety

        // 2. Start Foreground AlarmService
        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            action = AlarmService.ACTION_START_ALARM
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmScheduler.EXTRA_DIFFICULTY, difficulty)
            putExtra(AlarmScheduler.EXTRA_RINGTONE_NAME, ringtoneName)
            putExtra(AlarmScheduler.EXTRA_RINGTONE_URI, ringtoneUri)
            putExtra(AlarmScheduler.EXTRA_ALARM_TIME, alarmTime)
        }
        ContextCompat.startForegroundService(context, serviceIntent)

        // 3. Launch full-screen AlarmActivity
        val activityIntent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmScheduler.EXTRA_DIFFICULTY, difficulty)
            putExtra(AlarmScheduler.EXTRA_RINGTONE_NAME, ringtoneName)
            putExtra(AlarmScheduler.EXTRA_RINGTONE_URI, ringtoneUri)
            putExtra(AlarmScheduler.EXTRA_ALARM_TIME, alarmTime)
        }
        context.startActivity(activityIntent)
    }
}
