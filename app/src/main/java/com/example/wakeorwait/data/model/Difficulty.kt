package com.example.wakeorwait.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class Difficulty(
    val displayName: String,
    val description: String,
    val emoji: String,
    val colorHex: Long
) {
    EASY(
        displayName = "Easy",
        description = "20 Jumping Jacks",
        emoji = "🟢",
        colorHex = 0xFF4CAF50
    ),
    NORMAL(
        displayName = "Normal",
        description = "20 Jumping Jacks + Math",
        emoji = "🟡",
        colorHex = 0xFFFF9800
    ),
    EVIL(
        displayName = "Evil",
        description = "20 Jumping Jacks + Math + Reverse Word",
        emoji = "🔴",
        colorHex = 0xFFE53935
    );

    val totalChallenges: Int
        get() = when (this) {
            EASY -> 1
            NORMAL -> 2
            EVIL -> 3
        }
}
