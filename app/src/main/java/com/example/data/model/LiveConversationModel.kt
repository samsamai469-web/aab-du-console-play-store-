package com.example.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

enum class SpeakerRole {
    SPEAKER_A,
    SPEAKER_B
}

enum class ConversationLiveState(val icon: String, val label: String) {
    READY("🟢", "Prêt"),
    LISTENING("🎤", "Écoute"),
    TRANSCRIBING("🔄", "Transcription"),
    TRANSLATING("🌍", "Traduction"),
    SPEAKING("🔊", "Lecture"),
    PAUSED("⏸️", "Pause"),
    STOPPED("⛔", "Arrêté"),
    ERROR("🔴", "Erreur");

    companion object {
        val IDLE = READY
        val PROCESSING = TRANSCRIBING
    }
}

@Serializable
data class LiveConversationTurn(
    val id: String = UUID.randomUUID().toString(),
    val speakerRole: String = "SPEAKER_A", // "SPEAKER_A" or "SPEAKER_B"
    val speakerName: String = "Personne A",
    val sourceLang: String,
    val targetLang: String,
    val originalText: String,
    val translatedText: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isAutoPlayed: Boolean = true
)
