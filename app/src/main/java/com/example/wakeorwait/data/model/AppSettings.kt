package com.example.wakeorwait.data.model

import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    val is24HourFormat: Boolean = false,
    val defaultDifficulty: Difficulty = Difficulty.NORMAL,
    val defaultRingtoneName: String = "Siren Blitz",
    val defaultRingtoneUri: String? = null,
    val jumpingJacksTarget: Int = 20,
    val mathDifficulty: String = "Standard",
    val reverseWordDifficulty: String = "Standard"
)
