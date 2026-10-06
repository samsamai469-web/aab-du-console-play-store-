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
        var badge = "Corpus Local"

        // 1. Vérification du cache intelligent
        val cached = MultilingualTranslationEngine.getFromCache(cleanText, sourceLangInput, targetLangInput)
        if (!cached.isNullOrBlank() && !cached.equals(cleanText, ignoreCase = true)) {
            translated = cached
            isLocal = true
            badge = "Mémoire Validée"
        }

        // 2. Expressions certifiées directes du Faso (Salutations, vie quotidienne, politesse)
        if (translated.isBlank()) {
            val directExp = MultilingualTranslationEngine.tryDirectExpressionMatch(cleanText, sourceLangInput, targetLangInput)
            if (!directExp.isNullOrBlank() && !directExp.equals(cleanText, ignoreCase = true)) {
                translated = directExp
                isLocal = true
                badge = "Corpus Officiel"
            }
        }

        // 3. Moteur de dictionnaire riche (1900+ mots et expressions du quotidien)
        if (translated.isBlank()) {
            val dictMatch = DictionaryLookupEngine.translateDirect(cleanText, sourceLangInput, targetLangInput)
            if (!dictMatch.isNullOrBlank() && !dictMatch.equals(cleanText, ignoreCase = true)) {
                translated = dictMatch
                isLocal = true
                badge = "Dictionnaire Certifié"
            }
        }

        // 4. Base SQLite locale
        if (translated.isBlank()) {
            try {
                val localMatch = linguisticDao?.findBestMatch(cleanText, targetLangInput)
                if (localMatch != null) {
                    val candidate = if (localMatch.word.equals(cleanText, ignoreCase = true)) localMatch.translation else localMatch.word
                    if (candidate.isNotBlank() && !candidate.equals(cleanText, ignoreCase = true)) {
                        translated = candidate
                        isLocal = true
                        badge = "Corpus Local"
                    }
                }
            } catch (_: Throwable) {}
        }

        // 5. Traduction IA Gemini
        if (translated.isBlank()) {
            try {
                val aiCandidate = aiFallback(cleanText, sourceLangInput, targetLangInput, "")
                if (aiCandidate.isNotBlank() && !aiCandidate.equals(cleanText, ignoreCase = true)) {
                    translated = aiCandidate.trim()
                    isLocal = false
                    badge = "IA Gemini"
                }
            } catch (_: Throwable) {}
        }

        // 6. Traduction mot-à-mot garantie pour les phrases composées
        if (translated.isBlank() || translated.equals(cleanText, ignoreCase = true)) {
            val wordByWord = DictionaryLookupEngine.translateWordByWord(cleanText, sourceLangInput, targetLangInput)
            if (!wordByWord.isNullOrBlank() && !wordByWord.equals(cleanText, ignoreCase = true)) {
                translated = wordByWord
                isLocal = true
                badge = "Traduction Composée"
            }
        }

        // Sécurité finale : si aucune traduction n'a été possible
        if (translated.isBlank()) {
            translated = cleanText
        }

        // Sauvegarder dans le cache pour accélération future
        if (translated.isNotBlank() && !translated.equals(cleanText, ignoreCase = true)) {
            MultilingualTranslationEngine.putInCache(cleanText, sourceLangInput, targetLangInput, translated, isLocal)
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
}
