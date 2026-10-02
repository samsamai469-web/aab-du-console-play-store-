package com.example.data.engine

import com.example.data.remote.VoiceIntentResult
import com.example.data.repository.AiRepository

object VoiceIntentEngine {
    suspend fun analyzeSpokenInput(
        rawTranscript: String,
        currentSourceLang: String,
        currentTargetLang: String,
        aiRepository: AiRepository? = null
    ): VoiceIntentResult {
        val clean = rawTranscript.trim()
        return VoiceIntentResult(
            cleanedFormattedText = clean,
            detectedSourceLang = currentSourceLang,
            detectedTargetLang = currentTargetLang,
            isDirectQuestion = clean.endsWith("?") || clean.startsWith("pourquoi", ignoreCase = true) || clean.startsWith("comment", ignoreCase = true),
            suggestedAction = "TRANSLATE"
        )
    }
}
