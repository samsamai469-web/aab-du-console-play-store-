package com.example.data.repository

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

    suspend fun translate(
        originalText: String,
        sourceLangInput: String,
        targetLangInput: String,
        userId: String = "guest",
        aiFallback: suspend (srcText: String, srcLang: String, tgtLang: String, localContext: String) -> String
    ): TranslationOutput {
        var translated = ""
        var isLocal = false
        val localMatch = linguisticDao?.findBestMatch(originalText.trim(), targetLangInput)
        if (localMatch != null) {
            translated = if (localMatch.word.equals(originalText.trim(), ignoreCase = true)) localMatch.translation else localMatch.word
            isLocal = true
        }
        if (translated.isBlank()) {
            translated = aiFallback(originalText, sourceLangInput, targetLangInput, "")
        }
        if (translated.isBlank()) {
            translated = originalText
        }
        val output = TranslationOutput(
            translatedText = translated,
            sourceLanguage = sourceLangInput,
            targetLanguage = targetLangInput,
            sourceLang = sourceLangInput,
            targetLang = targetLangInput,
            isFromValidatedSource = isLocal,
            sourceBadge = if (isLocal) "Corpus Local" else "IA Gemini"
        )
        saveToHistory(
            originalText = originalText,
            translatedText = translated,
            sourceLang = sourceLangInput,
            targetLang = targetLangInput,
            userId = userId
        )
        return output
    }
}
