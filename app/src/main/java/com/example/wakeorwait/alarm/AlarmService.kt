package com.example.wakeorwait.alarm

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.wakeorwait.R
import com.example.wakeorwait.WakeOrWaitApp
import com.example.wakeorwait.audio.RingtoneManager
import com.example.wakeorwait.data.model.AlarmHistory
import com.example.wakeorwait.data.model.Difficulty
import com.example.wakeorwait.data.repository.AlarmRepository
import com.example.wakeorwait.ui.screens.AlarmActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class AlarmServiceState(
    val isActive: Boolean = false,
    val remainingSeconds: Int = 600, // 10 minutes
    val elapsedSeconds: Int = 0,
    val isTimedOut: Boolean = false,
    val isCompleted: Boolean = false,
    val alarmId: String = "",
    val alarmTime: String = "7:00 AM",
    val difficulty: Difficulty = Difficulty.NORMAL,
    val ringtoneName: String = "Siren Blitz"
)

class AlarmService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var timerJob: Job? = null
    private var vibrator: Vibrator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_ALARM
        Log.d("AlarmService", "onStartCommand action: $action")

        when (action) {
            ACTION_START_ALARM -> {
                val alarmId = intent?.getStringExtra(AlarmScheduler.EXTRA_ALARM_ID) ?: ""
                val diffStr = intent?.getStringExtra(AlarmScheduler.EXTRA_DIFFICULTY) ?: "NORMAL"
                val diff = try { Difficulty.valueOf(diffStr) } catch (e: Exception) { Difficulty.NORMAL }
                val ringtoneName = intent?.getStringExtra(AlarmScheduler.EXTRA_RINGTONE_NAME) ?: "Siren Blitz"
                val ringtoneUri = intent?.getStringExtra(AlarmScheduler.EXTRA_RINGTONE_URI)
                val alarmTime = intent?.getStringExtra(AlarmScheduler.EXTRA_ALARM_TIME) ?: "7:00 AM"

                startAlarmExecution(alarmId, diff, ringtoneName, ringtoneUri, alarmTime)
            }
            ACTION_STOP_ALARM_COMPLETED -> {
                val timeTaken = intent?.getIntExtra(EXTRA_TIME_TAKEN_SECONDS, 0) ?: 0
                stopAlarmSuccess(timeTaken)
            }
            ACTION_STOP_ALARM_TIMEOUT -> {
                stopAlarmTimeout()
            }
        }

        return START_STICKY
    }

    private fun startAlarmExecution(
        alarmId: String,
        diff: Difficulty,
        ringtoneName: String,
        ringtoneUri: String?,
        alarmTime: String
    ) {
        _serviceState.value = AlarmServiceState(
            isActive = true,
            remainingSeconds = MAX_DURATION_SECONDS,
            elapsedSeconds = 0,
            isTimedOut = false,
            isCompleted = false,
            alarmId = alarmId,
            alarmTime = alarmTime,
            difficulty = diff,
            ringtoneName = ringtoneName
        )

        // 1. Post Foreground Notification with Full Screen Intent
        val notification = createAlarmNotification(alarmTime, diff)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // 2. Start Audio Playback (Looping)
        RingtoneManager.getInstance(this).startAlarmAudio(ringtoneName, ringtoneUri)

        // 3. Start Vibration
        startVibration()

        // 4. Start strict 10-Minute Timeout Countdown
        startTenMinuteTimer(alarmId, alarmTime, diff)
    }

    private fun startVibration() {
        try {
            val pattern = longArrayOf(0, 800, 400, 800, 400)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            Log.e("AlarmService", "Failed to start vibration", e)
        }
    }

    private fun stopVibration() {
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun startTenMinuteTimer(alarmId: String, alarmTime: String, difficulty: Difficulty) {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            var remaining = MAX_DURATION_SECONDS
            var elapsed = 0

            while (isActive && remaining > 0) {
                delay(1000)
                remaining--
                elapsed++

                _serviceState.value = _serviceState.value.copy(
                    remainingSeconds = remaining,
                    elapsedSeconds = elapsed
                )
            }

            // 10 minutes reached! Strict timeout triggered
            if (remaining <= 0) {
                stopAlarmTimeout()
            }
        }
    }

    private fun stopAlarmSuccess(timeTakenSeconds: Int) {
        timerJob?.cancel()
        timerJob = null

        RingtoneManager.getInstance(this).stopAlarmAudio()
        stopVibration()

        val currentState = _serviceState.value
        _serviceState.value = currentState.copy(
            isActive = false,
            isCompleted = true,
            elapsedSeconds = timeTakenSeconds
        )

        // Save history
        AlarmRepository.getInstance(this).addHistory(
            AlarmHistory(
                alarmTime = currentState.alarmTime,
                difficulty = currentState.difficulty,
                isCompleted = true,
                timeTakenSeconds = timeTakenSeconds,
                resultMessage = "Congratulations. You're technically awake."
            )
        )

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopAlarmTimeout() {
        timerJob?.cancel()
        timerJob = null

        RingtoneManager.getInstance(this).stopAlarmAudio()
        stopVibration()

        val currentState = _serviceState.value
        _serviceState.value = currentState.copy(
            isActive = false,
            isTimedOut = true,
            remainingSeconds = 0
        )

        // Save history
        AlarmRepository.getInstance(this).addHistory(
            AlarmHistory(
                alarmTime = currentState.alarmTime,
                difficulty = currentState.difficulty,
                isCompleted = false,
                timeTakenSeconds = MAX_DURATION_SECONDS,
                resultMessage = "You survived 10 minutes. 🫡"
            )
        )

        // Show humorous timeout notification
        postTimeoutNotification()

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun postTimeoutNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(this, WakeOrWaitApp.TIMEOUT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("10-Minute Timeout Reached 🫡")
            .setContentText("You survived 10 minutes! You waited 10 minutes just to avoid challenges. Respect.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        nm.notify(TIMEOUT_NOTIFICATION_ID, notif)
    }

    private fun createAlarmNotification(alarmTime: String, diff: Difficulty): Notification {
        val fullScreenIntent = Intent(this, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AlarmScheduler.EXTRA_DIFFICULTY, diff.name)
            putExtra(AlarmScheduler.EXTRA_ALARM_TIME, alarmTime)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            1001,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, WakeOrWaitApp.ALARM_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("WakeOrWait Alarm — $alarmTime")
            .setContentText("${diff.emoji} ${diff.displayName} Challenge in progress! 10-min max.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        timerJob?.cancel()
        RingtoneManager.getInstance(this).stopAlarmAudio()
        stopVibration()
        _serviceState.value = _serviceState.value.copy(isActive = false)
    }

    companion object {
        const val ACTION_START_ALARM = "com.example.wakeorwait.ACTION_START_ALARM"
        const val ACTION_STOP_ALARM_COMPLETED = "com.example.wakeorwait.ACTION_STOP_ALARM_COMPLETED"
        const val ACTION_STOP_ALARM_TIMEOUT = "com.example.wakeorwait.ACTION_STOP_ALARM_TIMEOUT"
        const val EXTRA_TIME_TAKEN_SECONDS = "extra_time_taken_seconds"

        const val NOTIFICATION_ID = 9991
        const val TIMEOUT_NOTIFICATION_ID = 9992
        const val MAX_DURATION_SECONDS = 600 // 10 minutes

        private val _serviceState = MutableStateFlow(AlarmServiceState())
        val serviceState: StateFlow<AlarmServiceState> = _serviceState.asStateFlow()

        fun completeChallenge(context: Context, timeTakenSeconds: Int) {
            val intent = Intent(context, AlarmService::class.java).apply {
                action = ACTION_STOP_ALARM_COMPLETED
                putExtra(EXTRA_TIME_TAKEN_SECONDS, timeTakenSeconds)
            }
            context.startService(intent)
        }

        fun triggerTimeout(context: Context) {
            val intent = Intent(context, AlarmService::class.java).apply {
                action = ACTION_STOP_ALARM_TIMEOUT
            }
            context.startService(intent)
        }
    }
}
