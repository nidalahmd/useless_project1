package com.example.wakeorwait.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.example.wakeorwait.MainActivity
import com.example.wakeorwait.data.model.Alarm
import com.example.wakeorwait.data.repository.AlarmRepository

class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun scheduleAlarm(alarm: Alarm) {
        if (!alarm.isEnabled) return

        val triggerTime = alarm.getNextTriggerTimeMillis()
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM_TRIGGER
            putExtra(EXTRA_ALARM_ID, alarm.id)
            putExtra(EXTRA_DIFFICULTY, alarm.difficulty.name)
            putExtra(EXTRA_RINGTONE_NAME, alarm.ringtoneName)
            putExtra(EXTRA_RINGTONE_URI, alarm.ringtoneUri)
            putExtra(EXTRA_ALARM_TIME, alarm.formatTime(false))
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Show Intent for AlarmClockInfo (clicking on status bar alarm clock opens MainActivity)
        val showIntent = Intent(context, MainActivity::class.java)
        val showPendingIntent = PendingIntent.getActivity(
            context,
            alarm.id.hashCode() + 1,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val clockInfo = AlarmManager.AlarmClockInfo(triggerTime, showPendingIntent)
        try {
            alarmManager.setAlarmClock(clockInfo, pendingIntent)
            Log.d("AlarmScheduler", "Alarm scheduled for ${alarm.id} at $triggerTime")
        } catch (e: SecurityException) {
            Log.e("AlarmScheduler", "SecurityException scheduling exact alarm", e)
        }
    }

    fun cancelAlarm(alarmId: String) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM_TRIGGER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        Log.d("AlarmScheduler", "Alarm cancelled for $alarmId")
    }

    fun rescheduleAllActiveAlarms() {
        val repo = AlarmRepository.getInstance(context)
        val activeAlarms = repo.alarms.value.filter { it.isEnabled }
        for (alarm in activeAlarms) {
            scheduleAlarm(alarm)
        }
    }

    companion object {
        const val ACTION_ALARM_TRIGGER = "com.example.wakeorwait.ACTION_ALARM_TRIGGER"
        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val EXTRA_DIFFICULTY = "extra_difficulty"
        const val EXTRA_RINGTONE_NAME = "extra_ringtone_name"
        const val EXTRA_RINGTONE_URI = "extra_ringtone_uri"
        const val EXTRA_ALARM_TIME = "extra_alarm_time"
    }
}
