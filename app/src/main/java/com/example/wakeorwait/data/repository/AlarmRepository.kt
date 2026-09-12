package com.example.wakeorwait.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.wakeorwait.data.model.Alarm
import com.example.wakeorwait.data.model.AlarmHistory
import com.example.wakeorwait.data.model.AppSettings
import com.example.wakeorwait.data.model.CustomRingtone
import com.example.wakeorwait.data.model.Difficulty
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class AlarmRepository private constructor(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _alarms = MutableStateFlow<List<Alarm>>(emptyList())
    val alarms: StateFlow<List<Alarm>> = _alarms.asStateFlow()

    private val _history = MutableStateFlow<List<AlarmHistory>>(emptyList())
    val history: StateFlow<List<AlarmHistory>> = _history.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _customRingtones = MutableStateFlow<List<CustomRingtone>>(emptyList())
    val customRingtones: StateFlow<List<CustomRingtone>> = _customRingtones.asStateFlow()

    init {
        loadSettings()
        loadAlarms()
        loadHistory()
        loadCustomRingtones()
    }

    private fun loadSettings() {
        val raw = prefs.getString(KEY_SETTINGS, null)
        if (raw != null) {
            try {
                _settings.value = json.decodeFromString(raw)
            } catch (e: Exception) {
                _settings.value = AppSettings()
            }
        }
    }

    fun updateSettings(newSettings: AppSettings) {
        _settings.value = newSettings
        prefs.edit().putString(KEY_SETTINGS, json.encodeToString(newSettings)).apply()
    }

    private fun loadAlarms() {
        val raw = prefs.getString(KEY_ALARMS, null)
        if (raw != null) {
            try {
                val list: List<Alarm> = json.decodeFromString(raw)
                _alarms.value = list
                return
            } catch (e: Exception) {
                // fall through to default
            }
        }
        // Provide initial default alarm
        val initialAlarm = Alarm(
            hour = 7,
            minute = 0,
            repeatDays = setOf(1, 2, 3, 4, 5),
            difficulty = Difficulty.EVIL,
            ringtoneName = "Siren Blitz",
            isEnabled = true,
            label = "Morning Challenge"
        )
        _alarms.value = listOf(initialAlarm)
        persistAlarms()
    }

    private fun persistAlarms() {
        prefs.edit().putString(KEY_ALARMS, json.encodeToString(_alarms.value)).apply()
    }

    fun saveAlarm(alarm: Alarm) {
        val current = _alarms.value.toMutableList()
        val index = current.indexOfFirst { it.id == alarm.id }
        if (index >= 0) {
            current[index] = alarm
        } else {
            current.add(0, alarm)
        }
        _alarms.value = current
        persistAlarms()
    }

    fun toggleAlarm(id: String, isEnabled: Boolean) {
        val current = _alarms.value.map {
            if (it.id == id) it.copy(isEnabled = isEnabled) else it
        }
        _alarms.value = current
        persistAlarms()
    }

    fun deleteAlarm(id: String) {
        _alarms.value = _alarms.value.filter { it.id != id }
        persistAlarms()
    }

    fun getAlarmById(id: String): Alarm? {
        return _alarms.value.find { it.id == id }
    }

    fun getNextActiveAlarm(): Alarm? {
        val active = _alarms.value.filter { it.isEnabled }
        if (active.isEmpty()) return null
        return active.minByOrNull { it.getNextTriggerTimeMillis() }
    }

    private fun loadHistory() {
        val raw = prefs.getString(KEY_HISTORY, null)
        if (raw != null) {
            try {
                _history.value = json.decodeFromString(raw)
                return
            } catch (e: Exception) {
                _history.value = emptyList()
            }
        }
        // Add a sample history item for demonstration
        val sampleHistory = listOf(
            AlarmHistory(
                alarmTime = "7:00 AM",
                difficulty = Difficulty.EVIL,
                isCompleted = true,
                timeTakenSeconds = 151,
                resultMessage = "Congratulations. You're technically awake."
            )
        )
        _history.value = sampleHistory
        persistHistory()
    }

    private fun persistHistory() {
        prefs.edit().putString(KEY_HISTORY, json.encodeToString(_history.value)).apply()
    }

    fun addHistory(item: AlarmHistory) {
        val current = _history.value.toMutableList()
        current.add(0, item)
        _history.value = current
        persistHistory()
    }

    private fun loadCustomRingtones() {
        val raw = prefs.getString(KEY_CUSTOM_RINGTONES, null)
        if (raw != null) {
            try {
                val list: List<CustomRingtone> = json.decodeFromString(raw)
                // Filter out any where file was deleted
                _customRingtones.value = list.filter { File(it.filePath).exists() }
                return
            } catch (e: Exception) {
                _customRingtones.value = emptyList()
            }
        }
        _customRingtones.value = emptyList()
    }

    private fun persistCustomRingtones() {
        prefs.edit().putString(KEY_CUSTOM_RINGTONES, json.encodeToString(_customRingtones.value)).apply()
    }

    fun addCustomRingtone(ringtone: CustomRingtone) {
        val current = _customRingtones.value.toMutableList()
        current.add(ringtone)
        _customRingtones.value = current
        persistCustomRingtones()
    }

    fun deleteCustomRingtone(id: String) {
        val toDelete = _customRingtones.value.find { it.id == id }
        if (toDelete != null) {
            try {
                File(toDelete.filePath).delete()
            } catch (e: Exception) {
                // ignore
            }
        }
        _customRingtones.value = _customRingtones.value.filter { it.id != id }
        persistCustomRingtones()
    }

    companion object {
        private const val PREFS_NAME = "wakeorwait_prefs"
        private const val KEY_ALARMS = "alarms_list"
        private const val KEY_HISTORY = "history_list"
        private const val KEY_SETTINGS = "settings"
        private const val KEY_CUSTOM_RINGTONES = "custom_ringtones"

        @Volatile
        private var INSTANCE: AlarmRepository? = null

        fun getInstance(context: Context): AlarmRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AlarmRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
