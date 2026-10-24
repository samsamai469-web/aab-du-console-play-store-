package com.example.util

import java.util.Locale

/**
 * Utilitaire de nettoyage intelligent des flux de conversation vocale et de traduction.
 * - Supprime les bruits parasites, les hésitations orales (euh, hum, uh, um...)
 * - Corrige les bégaiements et répétitions de mots
 * - Soigne la ponctuation et les majuscules
 * - Assure une présentation impeccable du texte original et de la traduction
 */
object ConversationCleaner {

    // Liste de tics verbaux et particules d'hésitation courantes (FR, EN, universels)
    private val FILLER_REGEX = Regex(
        """(?i)\b(euh+|euhm+|hum+|uh+|um+|ah+|eh+|ben+|hein+|er+|mm+)\b[,\.\s]*"""
    )

    // Détection de bégaiements / répétitions consécutives de mots (ex: "je je vais" -> "je vais")
    private val STUTTER_REGEX = Regex("""(?i)\b(\p{L}{2,})\s+\1\b""")

    /**
     * Vérifie si le texte capté par le microphone contient de véritables paroles
     * et non un simple souffle, clic de micro ou bruit parasite.
     */
    fun isValidSpeech(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val trimmed = text.trim()

        // Si moins de 2 caractères ou aucun caractère alphabétique
        if (trimmed.length < 2) return false
        val letterCount = trimmed.count { it.isLetter() }
        if (letterCount < 2) return false

        // Si le texte n'est composé que de bruits d'hésitation
        val cleaned = FILLER_REGEX.replace(trimmed, "").trim()
        return cleaned.any { it.isLetter() }
    }

    /**
     * Nettoie en profondeur la transcription brute issue du SpeechRecognizer :
     * 1. Élimine les hésitations ("euh", "hum", "euhm", etc.)
     * 2. Élimine les répétitions de bégaiement ("je je" -> "je")
     * 3. Nettoie la ponctuation orpheline ou erratique
     * 4. Ajuste les espaces et majuscules
     */
    fun cleanVoiceTranscript(rawText: String, sourceLang: String = ""): String {
        if (rawText.isBlank()) return ""

        var result = rawText.trim()
        val isLocalLang = sourceLang.contains("moor", ignoreCase = true) ||
            sourceLang.contains("dioula", ignoreCase = true) ||
            sourceLang.contains("jula", ignoreCase = true) ||
            sourceLang.contains("haoussa", ignoreCase = true) ||
            sourceLang.contains("hausa", ignoreCase = true)

        if (!isLocalLang) {
            // 1. Suppression des tics d'hésitation uniquement pour les langues non locales
            result = FILLER_REGEX.replace(result, " ")

            // 2. Normalisation des espaces multiples
            result = result.replace(Regex("""\s+"""), " ").trim()

            // 3. Suppression des bégaiements consécutifs (sauf en Mooré/Dioula où la réduplication est grammaticale)
            var prev = ""
            var iterations = 0
            while (prev != result && iterations < 3) {
                prev = result
                result = STUTTER_REGEX.replace(result, "$1")
                iterations++
            }
        } else {
            result = result.replace(Regex("""\s+"""), " ").trim()
        }

        // 4. Nettoyage de la ponctuation au début du texte
        result = result.replace(Regex("""^[\s,;:\.\?!\-–—]+"""), "")

        // 5. Nettoyage des ponctuations répétées (ex: ",," -> ",", ".." -> ".")
        result = result.replace(Regex("""([,\.\?!;:])\1+"""), "$1")

        // 6. Espaces avant/après ponctuation
        result = result.replace(Regex("""\s+([,\.\?!;:])"""), "$1 ")
        result = result.replace(Regex("""\s+"""), " ").trim()

        if (result.isBlank()) return ""

        // 7. Première lettre en majuscule pour un rendu propre
        result = result.replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString()
        }

        return result
    }

    /**
     * Nettoie et polit la traduction finale :
     * - Supprime les balises ou résidus de prompt IA ("Traduction :", guillemets d'encadrement inutiles)
     * - Assure une majuscule en début de phrase sans altérer les mots locaux
     */
    fun polishTranslatedText(rawTranslation: String): String {
        if (rawTranslation.isBlank()) return ""

        var clean = rawTranslation.trim()

        // Retirer les préfixes d'IA du type "Traduction : " ou "Translation: "
        clean = clean.replace(Regex("""(?i)^(traduction|translation|translated)\s*:\s*"""), "")

        // Retirer les guillemets englobants si présents
        if ((clean.startsWith("\"") && clean.endsWith("\"")) || (clean.startsWith("«") && clean.endsWith("»"))) {
            clean = clean.substring(1, clean.length - 1).trim()
        }

        // Nettoyer les espaces
        clean = clean.replace(Regex("""\s+"""), " ").trim()

        // Majuscule initiale
        clean = clean.replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString()
        }

        return clean
    }
}
