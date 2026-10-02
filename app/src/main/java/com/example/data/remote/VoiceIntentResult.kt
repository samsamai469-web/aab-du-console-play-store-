package com.example.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class VoiceIntentResult(
    val cleanedFormattedText: String = "",
    val correctedText: String = cleanedFormattedText,
    val detectedSourceLang: String = "Français",
    val detectedTargetLang: String = "Mooré",
    val isDirectQuestion: Boolean = false,
    val suggestedAction: String = "TRANSLATE",
    val action: String = suggestedAction,
    val recipient: String? = null,
    val messageContent: String? = null,
    val searchQuery: String? = null,
    val screenTarget: String? = null,
    val isAmbiguous: Boolean = false,
    val clarificationQuestion: String? = null
)
