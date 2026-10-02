package com.example.data.remote

class VoiceAiAssistantService {
    fun analyzeVoiceSentence(
        rawTranscript: String,
        currentLanguage: String = "Français"
    ): VoiceIntentResult {
        val trimmed = rawTranscript.trim()
        if (trimmed.isBlank()) {
            return VoiceIntentResult(
                cleanedFormattedText = "",
                correctedText = "",
                detectedSourceLang = currentLanguage,
                isAmbiguous = true,
                clarificationQuestion = "Je n’ai pas bien entendu. Pouvez-vous répéter ?"
            )
        }

        val capitalized = trimmed.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        val punctuated = if (!capitalized.endsWith(".") && !capitalized.endsWith("?") && !capitalized.endsWith("!")) {
            if (capitalized.startsWith("Comment", ignoreCase = true) ||
                capitalized.startsWith("Pourquoi", ignoreCase = true) ||
                capitalized.startsWith("Est-ce", ignoreCase = true) ||
                capitalized.startsWith("Où", ignoreCase = true) ||
                capitalized.startsWith("Qui", ignoreCase = true) ||
                capitalized.startsWith("Quel", ignoreCase = true)
            ) {
                "$capitalized ?"
            } else {
                "$capitalized."
            }
        } else {
            capitalized
        }

        return VoiceIntentResult(
            cleanedFormattedText = punctuated,
            correctedText = punctuated,
            detectedSourceLang = currentLanguage,
            detectedTargetLang = if (currentLanguage.equals("Mooré", ignoreCase = true)) "Français" else "Mooré",
            isDirectQuestion = punctuated.endsWith("?"),
            suggestedAction = "TRANSLATE",
            action = "TRANSLATE"
        )
    }
}
