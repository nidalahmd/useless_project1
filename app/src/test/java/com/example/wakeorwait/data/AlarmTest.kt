package com.example.wakeorwait.data

import com.example.wakeorwait.data.model.Alarm
import com.example.wakeorwait.data.model.Difficulty
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class AlarmTest {

    @Test
    fun testTimeFormatting() {
        val alarm1 = Alarm(hour = 7, minute = 5, difficulty = Difficulty.EASY)
        assertEquals("7:05 AM", alarm1.formatTime(is24Hour = false))
        assertEquals("07:05", alarm1.formatTime(is24Hour = true))

        val alarm2 = Alarm(hour = 19, minute = 30, difficulty = Difficulty.EVIL)
        assertEquals("7:30 PM", alarm2.formatTime(is24Hour = false))
        assertEquals("19:30", alarm2.formatTime(is24Hour = true))

        val alarm3 = Alarm(hour = 0, minute = 0, difficulty = Difficulty.NORMAL)
        assertEquals("12:00 AM", alarm3.formatTime(is24Hour = false))
        assertEquals("00:00", alarm3.formatTime(is24Hour = true))

        val alarm4 = Alarm(hour = 12, minute = 0, difficulty = Difficulty.NORMAL)
        assertEquals("12:00 PM", alarm4.formatTime(is24Hour = false))
        assertEquals("12:00", alarm4.formatTime(is24Hour = true))
    }

    @Test
    fun testRepeatDaysText() {
        val once = Alarm(hour = 8, minute = 0, repeatDays = emptySet())
        assertEquals("Once", once.repeatDaysText())

        val everyDay = Alarm(hour = 8, minute = 0, repeatDays = setOf(1, 2, 3, 4, 5, 6, 7))
        assertEquals("Every day", everyDay.repeatDaysText())

        val weekdays = Alarm(hour = 8, minute = 0, repeatDays = setOf(1, 2, 3, 4, 5))
        assertEquals("Weekdays", weekdays.repeatDaysText())

        val custom = Alarm(hour = 8, minute = 0, repeatDays = setOf(1, 3, 5))
        assertEquals("Mon Wed Fri", custom.repeatDaysText())
    }

    @Test
    fun testNextTriggerTimeCalculation() {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = now }

        // Alarm 1 hour ahead today
        val futureHour = (cal.get(Calendar.HOUR_OF_DAY) + 1) % 24
        val alarm = Alarm(hour = futureHour, minute = 0, repeatDays = emptySet())
        val trigger = alarm.getNextTriggerTimeMillis(now)

        assertTrue(trigger > now)
    }
}
