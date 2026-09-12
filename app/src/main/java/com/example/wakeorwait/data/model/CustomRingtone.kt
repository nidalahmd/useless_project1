package com.example.wakeorwait.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class CustomRingtone(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val filePath: String,
    val isDefault: Boolean = false
)
