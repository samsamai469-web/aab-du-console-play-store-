package com.example.data.engine

object SmartSpeechUnderstandingEngine {
    enum class IntentType(val label: String) {
        GREETING("Salutations"),
        TRANSLATION("Traduction"),
        VOCABULARY("Vocabulaire"),
        GENERAL_QUERY("Question générale"),
        COMMAND("Commande")
    }

    enum class ConfidenceLevel {
        HIGH,
        MEDIUM,
        LOW
    }

    data class UnderstandingResult(
        val text: String = "",
        val understoodCleanText: String = text,
        val detectedLanguage: String = "Français",
        val targetLanguage: String = "Mooré",
        val intent: IntentType = IntentType.TRANSLATION,
        val confidence: ConfidenceLevel = ConfidenceLevel.HIGH,
        val corrections: List<String> = emptyList(),
        val correctionsApplied: List<String> = corrections,
        val requiresClarification: Boolean = false,
        val clarificationPrompt: String? = null
    )

    fun understandSpeech(
        rawTranscript: String = "",
        rawSpoken: String = rawTranscript,
        selectedSourceLanguage: String = "Auto-détection",
        sourceLangHint: String = selectedSourceLanguage,
        targetLangHint: String = "Français"
    ): UnderstandingResult {
        val input = if (rawTranscript.isNotBlank()) rawTranscript else rawSpoken
        val clean = input.trim()
        if (clean.isBlank()) {
            return UnderstandingResult(
                text = "",
                understoodCleanText = "",
                detectedLanguage = "Français",
                targetLanguage = targetLangHint,
                intent = IntentType.GENERAL_QUERY,
                confidence = ConfidenceLevel.LOW,
                requiresClarification = true,
                clarificationPrompt = "Je n’ai pas bien compris. Veuillez répéter."
            )
        }

        val deprolonged = DictionaryLookupEngine.normalizeMooreProlongations(clean)
        val lower = deprolonged.lowercase()
        val detectedLang = when {
            LanguageDetector.detect(deprolonged) == "Mooré" -> "Mooré"
            lower.contains("i ni sogoma") || lower.contains("ani sogoma") || lower.contains("i ni ce") || lower.contains("kene") || lower.contains("toro") -> "Dioula"
            lower.contains("sannu") || lower.contains("ina kwana") || lower.contains("nagode") -> "Haoussa"
            sourceLangHint != "Auto-détection" && sourceLangHint.isNotBlank() -> sourceLangHint
            else -> "Français"
        }

        val targetLang = if (detectedLang == "Français") {
            if (targetLangHint == "Français") "Mooré" else targetLangHint
        } else {
            "Français"
        }

        val intent = when {
            lower.contains("bonjour") || lower.contains("salut") || lower.contains("bonsoir") ||
            lower.contains("nii yi beogo") || lower.contains("ne yibeogo") || lower.contains("nii zabre") ||
            lower.contains("nii windga") || lower.contains("niyungo") ||
            lower.contains("i ni sogoma") || lower.contains("sannu") -> IntentType.GREETING
            lower.contains("traduire") || lower.contains("comment on dit") || lower.contains("signifie") -> IntentType.TRANSLATION
            lower.contains("mot") || lower.contains("apprendre") -> IntentType.VOCABULARY
            else -> IntentType.TRANSLATION
        }

        return UnderstandingResult(
            text = deprolonged,
            understoodCleanText = deprolonged,
            detectedLanguage = detectedLang,
            targetLanguage = targetLang,
            intent = intent,
            confidence = ConfidenceLevel.HIGH,
            corrections = if (deprolonged != clean) listOf("Prolongation vocale normalisée : $deprolonged") else emptyList(),
            correctionsApplied = if (deprolonged != clean) listOf("Prolongation vocale normalisée : $deprolonged") else emptyList(),
            requiresClarification = false,
            clarificationPrompt = null
        )
    }
}
