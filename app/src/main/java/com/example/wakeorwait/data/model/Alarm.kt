package com.example.wakeorwait.data.model

import kotlinx.serialization.Serializable
import java.util.Calendar
import java.util.UUID

@Serializable
data class Alarm(
    val id: String = UUID.randomUUID().toString(),
    val hour: Int,
    val minute: Int,
    val repeatDays: Set<Int> = emptySet(), // 1 = Monday, ..., 7 = Sunday
    val difficulty: Difficulty = Difficulty.NORMAL,
    val ringtoneUri: String? = null,
    val ringtoneName: String = "Siren Blitz",
    val isEnabled: Boolean = true,
    val label: String = "Alarm"
) {
    fun formatTime(is24Hour: Boolean): String {
        return if (is24Hour) {
            String.format("%02d:%02d", hour, minute)
        } else {
            val period = if (hour >= 12) "PM" else "AM"
            val displayHour = when (val h = hour % 12) {
                0 -> 12
                else -> h
            }
            String.format("%d:%02d %s", displayHour, minute, period)
        }
    }

    fun repeatDaysText(): String {
        if (repeatDays.isEmpty()) return "Once"
        if (repeatDays.size == 7) return "Every day"
        if (repeatDays == setOf(1, 2, 3, 4, 5)) return "Weekdays"
        if (repeatDays == setOf(6, 7)) return "Weekends"

        val dayNames = mapOf(
            1 to "Mon",
            2 to "Tue",
            3 to "Wed",
            4 to "Thu",
            5 to "Fri",
            6 to "Sat",
            7 to "Sun"
        )
        return repeatDays.sorted().joinToString(" ") { dayNames[it] ?: "" }
    }

    /**
     * Computes next trigger epoch milliseconds from current time.
     */
    fun getNextTriggerTimeMillis(currentTimeMillis: Long = System.currentTimeMillis()): Long {
        val now = Calendar.getInstance().apply { timeInMillis = currentTimeMillis }

        if (repeatDays.isEmpty()) {
            val target = Calendar.getInstance().apply {
                timeInMillis = currentTimeMillis
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (target.timeInMillis <= now.timeInMillis) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }
            return target.timeInMillis
        } else {
            // Check up to 7 days ahead to find matching day of week
            var minTargetMillis = Long.MAX_VALUE
            for (day in repeatDays) {
                // Java Calendar: Sunday=1, Monday=2, ..., Saturday=7
                val calDay = when (day) {
                    7 -> Calendar.SUNDAY
                    else -> day + 1
                }
                val target = Calendar.getInstance().apply {
                    timeInMillis = currentTimeMillis
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                var daysDiff = calDay - now.get(Calendar.DAY_OF_WEEK)
                if (daysDiff < 0 || (daysDiff == 0 && target.timeInMillis <= now.timeInMillis)) {
                    daysDiff += 7
                }
                target.add(Calendar.DAY_OF_YEAR, daysDiff)
                if (target.timeInMillis < minTargetMillis) {
                    minTargetMillis = target.timeInMillis
                }
            }
            return minTargetMillis
        }
    }
}
