package com.example.wakeorwait

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object HomeNavKey : NavKey

@Serializable
data class SetAlarmNavKey(val alarmId: String? = null) : NavKey

@Serializable
data object AlarmHistoryNavKey : NavKey

@Serializable
data object SettingsNavKey : NavKey
