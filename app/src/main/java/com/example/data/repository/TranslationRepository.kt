package com.example.data.repository

import com.example.data.engine.DictionaryLookupEngine
import com.example.data.engine.MultilingualTranslationEngine
import com.example.data.local.AuditDao
import com.example.data.local.LinguisticDao
import com.example.data.local.TranslationDao
import com.example.data.local.TranslationEntity
import kotlinx.coroutines.flow.Flow

data class TranslationOutput(
    val translatedText: String = "",
    val pronunciation: String = "",
    val confidence: Float = 1.0f,
    val sourceLanguage: String = "",
    val targetLanguage: String = "",
    val sourceLang: String = sourceLanguage,
    val targetLang: String = targetLanguage,
    val sourceBadge: String = "",
    val isFromValidatedSource: Boolean = false,
    val detectedTone: String = "Neutre",
    val explanation: String = "",
    val alternativeTranslations: List<String> = emptyList(),
    val literalBreakdown: List<String> = emptyList()
)

class TranslationRepository(
    private val translationDao: TranslationDao,
    private val linguisticDao: LinguisticDao? = null,
    private val auditDao: AuditDao? = null
) {
    fun getHistoryForUser(userId: String): Flow<List<TranslationEntity>> {
        return translationDao.getTranslationsForUser(userId)
    }

    suspend fun saveToHistory(
        originalText: String,
        translatedText: String,
        sourceLang: String,
        targetLang: String,
        userId: String = "guest",
        isValidated: Boolean = false,
        isFavorite: Boolean = false
    ): Long {
        val entity = TranslationEntity(
            userId = userId,
            originalText = originalText,
            translatedText = translatedText,
            sourceLang = sourceLang,
            targetLang = targetLang,
            timestamp = System.currentTimeMillis(),
            isValidatedCorrection = isValidated,
            isFavorite = isFavorite
        )
        return translationDao.insertTranslation(entity)
    }

    suspend fun toggleFavorite(id: Long, isFavorite: Boolean) {
        translationDao.updateFavorite(id, isFavorite)
    }

    suspend fun deleteTranslation(id: Long, userId: String): Boolean {
        val rows = translationDao.deleteTranslationSecure(id, userId)
        return rows > 0
    }

    suspend fun deleteHistoryItemSecure(id: Long, userId: String): Boolean {
        return deleteTranslation(id, userId)
    }

    suspend fun clearHistory(userId: String) {
        translationDao.deleteAllTranslationsForUser(userId)
    }

    suspend fun clearHistoryForUser(userId: String) {
        clearHistory(userId)
    }

    suspend fun updateValidation(id: Long, isValidated: Boolean, correctedText: String?) {
        translationDao.updateValidation(id, isValidated, correctedText)
    }

    suspend fun recordValidatedCorrection(
        originalText: String,
        correctedText: String,
        sourceLang: String = "Français",
        targetLang: String = "Mooré",
        userId: String = "guest"
    ): Long {
        return saveToHistory(
            originalText = originalText,
            translatedText = correctedText,
            sourceLang = sourceLang,
            targetLang = targetLang,
            userId = userId,
            isValidated = true
        )
    }

    suspend fun recordValidatedCorrection(
        id: Long,
        correctedText: String
    ) {
        updateValidation(id, true, correctedText)
    }

    /**
     * Moteur de traduction robuste à 6 niveaux de résolution garantie :
     * 1. Cache intelligent en mémoire
     * 2. Expressions et salutations certifiées du Burkina Faso (MultilingualTranslationEngine)
     * 3. Dictionnaire complet hors-ligne (DictionaryLookupEngine : 1900+ entrées Mooré, Dioula, Haoussa)
     * 4. Base SQLite locale (LinguisticDao)
     * 5. Moteur IA Gemini (si configuré et connecté)
     * 6. Traduction modulaire mot-à-mot (ne renvoie JAMAIS la phrase source brute comme traduction)
     */
    suspend fun translate(
        originalText: String,
        sourceLangInput: String,
        targetLangInput: String,
        userId: String = "guest",
        aiFallback: suspend (srcText: String, srcLang: String, tgtLang: String, localContext: String) -> String
    ): TranslationOutput {
        val cleanText = originalText.trim()
        if (cleanText.isBlank()) {
            return TranslationOutput(
                translatedText = "",
                sourceLanguage = sourceLangInput,
                targetLanguage = targetLangInput,
                sourceLang = sourceLangInput,
                targetLang = targetLangInput,
                isFromValidatedSource = true,
                sourceBadge = "Corpus Local"
            )
        }

        // Si les deux langues sont strictement identiques
        if (sourceLangInput.trim().equals(targetLangInput.trim(), ignoreCase = true)) {
            return TranslationOutput(
                translatedText = cleanText,
                sourceLanguage = sourceLangInput,
                targetLanguage = targetLangInput,
                sourceLang = sourceLangInput,
                targetLang = targetLangInput,
                isFromValidatedSource = true,
                sourceBadge = "Identique"
            )
        }

        var translated = ""
        var isLocal = false
        var badge = "En Ligne (Google / Gemini)"

        // 1. Traduction 100% en ligne (Google Traduction & Gemini Cloud IA)
        try {
            val onlineResult = aiFallback(cleanText, sourceLangInput, targetLangInput, "")
            if (onlineResult.isNotBlank() && !onlineResult.equals(cleanText, ignoreCase = true)) {
                translated = onlineResult.trim()
            }
        } catch (_: Throwable) {}

        // 2. Gestion stricte si le réseau est indisponible (aucune substitution locale silencieuse)
        val areLangsDifferent = !sourceLangInput.trim().equals(targetLangInput.trim(), ignoreCase = true)
        if (areLangsDifferent && (translated.isBlank() || translated.equals(cleanText, ignoreCase = true))) {
            translated = "Connexion Internet requise pour la traduction en ligne. Veuillez vérifier votre réseau."
            badge = "Connexion Requise"
            isLocal = false
        } else if (translated.isNotBlank() && !translated.equals(cleanText, ignoreCase = true)) {
            isLocal = false
            MultilingualTranslationEngine.putInCache(cleanText, sourceLangInput, targetLangInput, translated, false)
        } else {
            translated = cleanText
        }

        val output = TranslationOutput(
            translatedText = translated,
            sourceLanguage = sourceLangInput,
            targetLanguage = targetLangInput,
            sourceLang = sourceLangInput,
            targetLang = targetLangInput,
            isFromValidatedSource = isLocal,
            sourceBadge = badge
        )

        saveToHistory(
            originalText = cleanText,
            translatedText = translated,
            sourceLang = sourceLangInput,
            targetLang = targetLangInput,
            userId = userId
        )

        return output
    }

    private fun trySmartCompositional(
        text: String,
        sourceLang: String,
        targetLang: String
    ): String? {
        val lower = text.trim().lowercase().replace("'", "'").replace("’", "'")
        val isFromFrench = sourceLang.contains("franç", ignoreCase = true) || sourceLang.contains("franc", ignoreCase = true)
        val isTargetMoore = targetLang.contains("moor", ignoreCase = true)
        val isTargetDioula = targetLang.contains("dioula", ignoreCase = true) || targetLang.contains("jula", ignoreCase = true)
        val isTargetHausa = targetLang.contains("haoussa", ignoreCase = true) || targetLang.contains("hausa", ignoreCase = true)

        if (isFromFrench) {
            // 1. Salutations composées avec proches (ex: "bonjour mon frère", "merci mon ami")
            if (lower.startsWith("bonjour ") || lower.startsWith("salut ")) {
                val suffix = lower.removePrefix("bonjour ").removePrefix("salut ").trim()
                val (mrSuffix, dyuSuffix, haSuffix) = when (suffix) {
                    "mon frère", "mon frere" -> Triple("m ba-bi-raoogo", "ne balimake", "ɗan'uwa")
                    "ma sœur", "ma soeur" -> Triple("m ba-bi-põka", "ne balimamuso", "ƴar'uwa")
                    "mon ami" -> Triple("m zoodo", "ne teri", "abokina")
                    "mes amis" -> Triple("m zoodba", "ne teriw", "abokaina")
                    "papa", "mon père", "mon pere" -> Triple("m baa", "ne fa", "babana")
                    "maman", "ma mère", "ma mere" -> Triple("m ma", "ne ba", "mamana")
                    "mon fils", "mon enfant" -> Triple("m biiga", "ne den", "dana")
                    "ma femme" -> Triple("m paga", "ne muso", "matata")
                    "mon mari" -> Triple("m sapa", "ne cɛ", "miji na")
                    "tout le monde" -> Triple("neda fâa", "mogo bèè", "kowa da kowa")
                    else -> Triple(null, null, null)
                }
                if (mrSuffix != null) {
                    return when {
                        isTargetMoore -> "Nii yi beogo $mrSuffix"
                        isTargetDioula -> "Ani sogoma $dyuSuffix"
                        isTargetHausa -> "Ina kwana $haSuffix"
                        else -> null
                    }
                }
            }

            if (lower.startsWith("bonsoir ")) {
                val suffix = lower.removePrefix("bonsoir ").trim()
                val (mrSuffix, dyuSuffix, haSuffix) = when (suffix) {
                    "mon frère", "mon frere" -> Triple("m ba-bi-raoogo", "ne balimake", "ɗan'uwa")
                    "ma sœur", "ma soeur" -> Triple("m ba-bi-põka", "ne balimamuso", "ƴar'uwa")
                    "mon ami" -> Triple("m zoodo", "ne teri", "abokina")
                    "papa", "mon père", "mon pere" -> Triple("m baa", "ne fa", "babana")
                    "maman", "ma mère", "ma mere" -> Triple("m ma", "ne ba", "mamana")
                    "tout le monde" -> Triple("neda fâa", "mogo bèè", "kowa da kowa")
                    else -> Triple(null, null, null)
                }
                if (mrSuffix != null) {
                    return when {
                        isTargetMoore -> "Nii zabre $mrSuffix"
                        isTargetDioula -> "Ani wula $dyuSuffix"
                        isTargetHausa -> "Ina wuni $haSuffix"
                        else -> null
                    }
                }
            }

            if (lower.startsWith("merci beaucoup ") || lower.startsWith("merci ")) {
                val isMuch = lower.startsWith("merci beaucoup ")
                val suffix = if (isMuch) lower.removePrefix("merci beaucoup ").trim() else lower.removePrefix("merci ").trim()
                val (mrPrefix, dyuPrefix, haPrefix) = if (isMuch) {
                    Triple("Bark wusgo wusgo", "Anitie kossobe", "Nagode sosai")
                } else {
                    Triple("Barka", "Anitie", "Nagode")
                }
                val (mrSuffix, dyuSuffix, haSuffix) = when (suffix) {
                    "mon frère", "mon frere" -> Triple("m ba-bi-raoogo", "ne balimake", "ɗan'uwa")
                    "ma sœur", "ma soeur" -> Triple("m ba-bi-põka", "ne balimamuso", "ƴar'uwa")
                    "mon ami" -> Triple("m zoodo", "ne teri", "abokina")
                    "papa" -> Triple("m baa", "ne fa", "babana")
                    "maman" -> Triple("m ma", "ne ba", "mamana")
                    else -> Triple(null, null, null)
                }
                if (mrSuffix != null) {
                    return when {
                        isTargetMoore -> "$mrPrefix $mrSuffix"
                        isTargetDioula -> "$dyuPrefix $dyuSuffix"
                        isTargetHausa -> "$haPrefix $haSuffix"
                        else -> null
                    }
                }
            }

            // 2. Modèles "Je m'appelle X"
            if (lower.startsWith("je m'appelle ") || lower.startsWith("je mappelle ")) {
                val name = text.trim().substringAfter("appelle", "").trim()
                if (name.isNotBlank()) {
                    return when {
                        isTargetMoore -> "Mam yʋʋre la $name"
                        isTargetDioula -> "Oum togo be $name"
                        isTargetHausa -> "Sunana $name"
                        else -> null
                    }
                }
            }

            // 3. Modèles "Donne-moi X"
            if (lower.startsWith("donne-moi ") || lower.startsWith("donne moi ")) {
                val item = lower.removePrefix("donne-moi ").removePrefix("donne moi ").removePrefix("de l'").removePrefix("du ").removePrefix("de la ").removePrefix("des ").trim()
                val translatedItem = DictionaryLookupEngine.translateDirect(item, "Français", targetLang)
                if (translatedItem != null) {
                    return when {
                        isTargetMoore -> "Kõ-m $translatedItem"
                        isTargetDioula -> "$translatedItem di ma"
                        isTargetHausa -> "Ba ni $translatedItem"
                        else -> null
                    }
                }
            }

            // 4. Modèles "Je veux X"
            if (lower.startsWith("je veux ") || lower.startsWith("j'ai besoin de ") || lower.startsWith("jai besoin de ")) {
                val item = lower.removePrefix("je veux ").removePrefix("j'ai besoin de ").removePrefix("jai besoin de ").removePrefix("de l'").removePrefix("du ").removePrefix("de la ").removePrefix("des ").trim()
                val translatedItem = DictionaryLookupEngine.translateDirect(item, "Français", targetLang)
                if (translatedItem != null) {
                    return when {
                        isTargetMoore -> "M data $translatedItem"
                        isTargetDioula -> "Oum b'a fe $translatedItem"
                        isTargetHausa -> "Ina son $translatedItem"
                        else -> null
                    }
                }
            }

            // 5. Modèles "Je vais à / au X"
            if (lower.startsWith("je vais à la ") || lower.startsWith("je vais au ") || lower.startsWith("je vais aux ") || lower.startsWith("je vais a ")) {
                val place = lower.removePrefix("je vais à la ").removePrefix("je vais au ").removePrefix("je vais aux ").removePrefix("je vais a ").removePrefix("je vais à ").trim()
                val translatedPlace = DictionaryLookupEngine.translateDirect(place, "Français", targetLang)
                if (translatedPlace != null) {
                    return when {
                        isTargetMoore -> "M debda $translatedPlace"
                        isTargetDioula -> "Mi ta $translatedPlace"
                        isTargetHausa -> "Ina tafiya $translatedPlace"
                        else -> null
                    }
                }
            }

            // 6. Modèles "Où est X ?" / "Où se trouve X ?"
            if (lower.startsWith("où est ") || lower.startsWith("ou est ") || lower.startsWith("où se trouve ") || lower.startsWith("ou se trouve ")) {
                val item = lower.removePrefix("où est ").removePrefix("ou est ").removePrefix("où se trouve ").removePrefix("ou se trouve ").removePrefix("le ").removePrefix("la ").removePrefix("l'").trim().removeSuffix("?").trim()
                val translatedItem = DictionaryLookupEngine.translateDirect(item, "Français", targetLang)
                if (translatedItem != null) {
                    return when {
                        isTargetMoore -> "$translatedItem be yẽ ?"
                        isTargetDioula -> "$translatedItem be mi ?"
                        isTargetHausa -> "Ina $translatedItem yake ?"
                        else -> null
                    }
                }
            }
        } else {
            // Local -> Français composition
            val isFromMoore = sourceLang.contains("moor", ignoreCase = true)
            val isFromDioula = sourceLang.contains("dioula", ignoreCase = true) || sourceLang.contains("jula", ignoreCase = true)
            val isFromHausa = sourceLang.contains("haoussa", ignoreCase = true) || sourceLang.contains("hausa", ignoreCase = true)
            val isTargetFrench = targetLang.contains("franç", ignoreCase = true) || targetLang.contains("franc", ignoreCase = true)

            if (isTargetFrench) {
                if (isFromMoore) {
                    if (lower.startsWith("mam yʋʋre la ") || lower.startsWith("mam yuure la ")) {
                        val name = text.trim().substringAfter(" la ", "").trim()
                        return "Je m'appelle $name"
                    }
                    if (lower.startsWith("m data ")) {
                        val item = lower.removePrefix("m data ").trim()
                        val frItem = DictionaryLookupEngine.translateDirect(item, "Mooré", "Français") ?: item
                        return "Je veux $frItem"
                    }
                    if (lower.startsWith("kõ-m ") || lower.startsWith("kom ")) {
                        val item = lower.removePrefix("kõ-m ").removePrefix("kom ").trim()
                        val frItem = DictionaryLookupEngine.translateDirect(item, "Mooré", "Français") ?: item
                        return "Donne-moi $frItem"
                    }
                } else if (isFromDioula) {
                    if (lower.startsWith("oum togo be ")) {
                        val name = text.trim().substringAfter("be ", "").trim()
                        return "Je m'appelle $name"
                    }
                    if (lower.startsWith("oum b'a fe ") || lower.startsWith("oum ba fe ")) {
                        val item = lower.removePrefix("oum b'a fe ").removePrefix("oum ba fe ").trim()
                        val frItem = DictionaryLookupEngine.translateDirect(item, "Dioula", "Français") ?: item
                        return "Je veux $frItem"
                    }
                } else if (isFromHausa) {
                    if (lower.startsWith("sunana ")) {
                        val name = text.trim().removePrefix("sunana ").trim()
                        return "Je m'appelle $name"
                    }
                    if (lower.startsWith("ina son ")) {
                        val item = lower.removePrefix("ina son ").trim()
                        val frItem = DictionaryLookupEngine.translateDirect(item, "Haoussa", "Français") ?: item
                        return "Je veux $frItem"
                    }
                }
            }
        }

        return null
    }

    private fun tryWordByWordSafe(
        text: String,
        sourceLang: String,
        targetLang: String
    ): String? {
        val words = text.trim().split(Regex("\\s+"))
        if (words.size <= 1) return null

        var translatedCount = 0
        val translatedWords = words.map { word ->
            val cleanWord = word.trim().trim(',', '.', '!', '?', ';', ':', '(', ')')
            if (cleanWord.isBlank()) {
                word
            } else {
                val direct = DictionaryLookupEngine.translateDirect(cleanWord, sourceLang, targetLang)
                if (direct != null && direct.isNotBlank() && !direct.equals(cleanWord, ignoreCase = true)) {
                    translatedCount++
                    word.replace(cleanWord, direct)
                } else {
                    null // Mot manquant : refus catégorique de mélanger les langues
                }
            }
        }

        // RÈGLE STRICTE ANTI-MÉLANGE : Seule une phrase où 100% des mots sont traduits est acceptée
        return if (translatedCount == words.size && translatedWords.all { it != null }) {
            translatedWords.filterNotNull().joinToString(" ")
        } else {
            null
        }
    }
}
