package com.example.wakeorwait.data.model

import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Serializable
data class AlarmHistory(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val alarmTime: String,
    val difficulty: Difficulty,
    val isCompleted: Boolean,
    val timeTakenSeconds: Int,
    val resultMessage: String
) {
    val formattedDate: String
        get() {
            val sdf = SimpleDateFormat("MMM d — h:mm a", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }

    val durationText: String
        get() {
            return if (isCompleted) {
                val mins = timeTakenSeconds / 60
                val secs = timeTakenSeconds % 60
                if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
            } else {
                "Timed out after 10m"
            }
        }
}
