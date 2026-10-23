package com.example.data.model

import com.example.data.local.WebSource
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class SamAiChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: String, // "user" or "sam_ai"
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false,
    val isStreaming: Boolean = false,
    val sources: List<WebSource> = emptyList(),
    val model: String? = null
)

