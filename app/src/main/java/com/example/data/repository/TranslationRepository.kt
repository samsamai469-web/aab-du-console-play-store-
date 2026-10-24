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

    private fun isMooreOrDioulaLang(lang: String): Boolean {
        val l = lang.lowercase().trim()
        return l.contains("moor") || l.contains("mòoré") || l.contains("dioula") || l.contains("jula")
    }

    private fun isFrenchLang(lang: String): Boolean {
        val l = lang.lowercase().trim()
        return l.contains("franç") || l.contains("franc")
    }

    /**
     * Architecture de routage hybride :
     * 1. Pour le MOORÉ et le DIOULA : Le Dictionnaire Local & Moteur d'Assemblage Compositionnel prend le relais
     *    (découpe les phrases comme "Je suis allé à l'école" en briques du dictionnaire local : "Je" + "suis allé" + "à l'école"
     *    et les rassemble proprement sans erreurs d'IA).
     * 2. Pour TOUTES LES AUTRES LANGUES (Anglais, Arabe, Espagnol, Allemand, Haoussa, etc.) :
     *    Le moteur OpenAI prend le relais directement.
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
                sourceBadge = "Dictionnaire Local"
            )
        }

        val effectiveSourceLang = if (sourceLangInput.trim().startsWith("Auto", ignoreCase = true) || sourceLangInput.isBlank()) {
            com.example.data.engine.LanguageDetector.detect(cleanText)
        } else {
            sourceLangInput.trim()
        }
        val effectiveTargetLang = targetLangInput.trim().ifBlank { "Mooré" }

        // Si les deux langues sont strictement identiques
        if (effectiveSourceLang.equals(effectiveTargetLang, ignoreCase = true)) {
            return TranslationOutput(
                translatedText = cleanText,
                sourceLanguage = effectiveSourceLang,
                targetLanguage = effectiveTargetLang,
                sourceLang = effectiveSourceLang,
                targetLang = effectiveTargetLang,
                isFromValidatedSource = true,
                sourceBadge = "Identique"
            )
        }

        val srcIsMooreOrDioula = isMooreOrDioulaLang(effectiveSourceLang)
        val tgtIsMooreOrDioula = isMooreOrDioulaLang(effectiveTargetLang)
        val srcIsFrench = isFrenchLang(effectiveSourceLang)
        val tgtIsFrench = isFrenchLang(effectiveTargetLang)

        var translated = ""
        var isLocal = false
        var badge = "OpenAI Officiel ⚡"
        var breakdown = emptyList<String>()

        // =========================================================================
        // CAS 1 : MOORÉ & DIOULA — LE DICTIONNAIRE LOCAL PREND LE RELAIS EN PRIORITÉ
        // =========================================================================
        if (srcIsMooreOrDioula || tgtIsMooreOrDioula) {
            val localLangLabel = when {
                effectiveTargetLang.contains("moor", ignoreCase = true) || effectiveSourceLang.contains("moor", ignoreCase = true) -> "Mooré"
                else -> "Dioula"
            }

            // A. Paire directe Français <-> Mooré/Dioula ou Mooré <-> Dioula
            if ((srcIsFrench && tgtIsMooreOrDioula) || (srcIsMooreOrDioula && tgtIsFrench) || (srcIsMooreOrDioula && tgtIsMooreOrDioula)) {
                val smartComp = trySmartCompositional(cleanText, effectiveSourceLang, effectiveTargetLang)
                val localAssembly = DictionaryLookupEngine.assembleSentenceFromLocalDictionary(
                    input = cleanText,
                    sourceLang = effectiveSourceLang,
                    targetLang = effectiveTargetLang
                )

                if (localAssembly != null && localAssembly.translatedText.isNotBlank() &&
                    !localAssembly.translatedText.equals(cleanText, ignoreCase = true)
                ) {
                    translated = localAssembly.translatedText.trim()
                    breakdown = localAssembly.breakdown
                    badge = "📖 Dictionnaire Local ($localLangLabel)"
                    isLocal = true
                    MultilingualTranslationEngine.putInCache(cleanText, effectiveSourceLang, effectiveTargetLang, translated, true)
                } else if (!smartComp.isNullOrBlank() && !smartComp.equals(cleanText, ignoreCase = true)) {
                    translated = smartComp.trim()
                    badge = "📖 Dictionnaire Local ($localLangLabel)"
                    isLocal = true
                    MultilingualTranslationEngine.putInCache(cleanText, effectiveSourceLang, effectiveTargetLang, translated, true)
                }
            }
            // B. Paire Autre Langue (ex: Anglais, Arabe, Haoussa, Espagnol) -> Mooré ou Dioula
            // OpenAI traduit Autre Langue -> Français, puis le Dictionnaire Local Mooré/Dioula traduit mot à mot jusqu'à la fin de la phrase
            else if (!srcIsFrench && tgtIsMooreOrDioula) {
                try {
                    val rawPivot = aiFallback(cleanText, effectiveSourceLang, "Français", "").trim()
                    val frenchPivot = rawPivot
                        .replace(Regex("""(?i)^(traduction|translation)\s*:\s*"""), "")
                        .removeSurrounding("\"")
                        .removeSurrounding("«", "»")
                        .trim()
                    if (frenchPivot.isNotBlank()) {
                        val smartComp = trySmartCompositional(frenchPivot, "Français", effectiveTargetLang)
                        val localAssembly = DictionaryLookupEngine.assembleSentenceFromLocalDictionary(
                            input = frenchPivot,
                            sourceLang = "Français",
                            targetLang = effectiveTargetLang
                        )
                        if (localAssembly != null && localAssembly.translatedText.isNotBlank()) {
                            translated = localAssembly.translatedText.trim()
                            breakdown = localAssembly.breakdown
                            badge = "📖 Dictionnaire Local ($localLangLabel)"
                            isLocal = true
                            MultilingualTranslationEngine.putInCache(cleanText, effectiveSourceLang, effectiveTargetLang, translated, true)
                        } else if (!smartComp.isNullOrBlank()) {
                            translated = smartComp.trim()
                            badge = "📖 Dictionnaire Local ($localLangLabel)"
                            isLocal = true
                            MultilingualTranslationEngine.putInCache(cleanText, effectiveSourceLang, effectiveTargetLang, translated, true)
                        }
                    }
                } catch (_: Throwable) {}
            }
            // C. Paire Mooré ou Dioula -> Autre Langue (ex: Anglais, Arabe, Haoussa, Espagnol)
            // Le Dictionnaire Local Mooré/Dioula traduit mot à mot vers le Français, puis OpenAI traduit vers la langue cible sans erreur
            else if (srcIsMooreOrDioula && !tgtIsFrench) {
                val localToFrench = DictionaryLookupEngine.assembleSentenceFromLocalDictionary(
                    input = cleanText,
                    sourceLang = effectiveSourceLang,
                    targetLang = "Français"
                )
                val frenchFromLocal = localToFrench?.translatedText?.takeIf { it.isNotBlank() }
                    ?: trySmartCompositional(cleanText, effectiveSourceLang, "Français")
                    ?: DictionaryLookupEngine.translateDirect(cleanText, effectiveSourceLang, "Français")
                if (!frenchFromLocal.isNullOrBlank()) {
                    try {
                        val openAiFinal = aiFallback(frenchFromLocal, "Français", effectiveTargetLang, "").trim()
                        if (openAiFinal.isNotBlank()) {
                            translated = openAiFinal
                            breakdown = localToFrench?.breakdown.orEmpty()
                            badge = "📖 Dictionnaire Local ($localLangLabel) + OpenAI"
                            isLocal = true
                            MultilingualTranslationEngine.putInCache(cleanText, effectiveSourceLang, effectiveTargetLang, translated, true)
                        }
                    } catch (_: Throwable) {}
                }
            }
        }

        // =========================================================================
        // CAS 2 : AUTRES LANGUES (Français, Anglais, Arabe, Haoussa, etc.) -> OPENAI
        // =========================================================================
        if (translated.isBlank() || translated.equals(cleanText, ignoreCase = true)) {
            try {
                val onlineResult = aiFallback(cleanText, effectiveSourceLang, effectiveTargetLang, "")
                if (onlineResult.isNotBlank() && !onlineResult.equals(cleanText, ignoreCase = true)) {
                    translated = onlineResult.trim()
                    badge = "OpenAI Officiel ⚡"
                    isLocal = false
                    MultilingualTranslationEngine.putInCache(cleanText, effectiveSourceLang, effectiveTargetLang, translated, false)
                }
            } catch (_: Throwable) {}
        }

        // Repli local ultime si hors-ligne pour les autres langues
        if (translated.isBlank() || translated.equals(cleanText, ignoreCase = true)) {
            val compositional = trySmartCompositional(cleanText, sourceLangInput, targetLangInput)
            val dictionaryDirect = if (compositional.isNullOrBlank()) {
                DictionaryLookupEngine.translateDirect(cleanText, sourceLangInput, targetLangInput)
            } else null
            val wordByWord = if (compositional.isNullOrBlank() && dictionaryDirect.isNullOrBlank()) {
                tryWordByWordSafe(cleanText, sourceLangInput, targetLangInput)
            } else null

            val localResult = compositional ?: dictionaryDirect ?: wordByWord
            if (!localResult.isNullOrBlank() && !localResult.equals(cleanText, ignoreCase = true)) {
                translated = localResult.trim()
                badge = "📖 Dictionnaire Local"
                isLocal = true
            } else {
                translated = cleanText
                badge = "Source"
                isLocal = false
            }
        }

        val output = TranslationOutput(
            translatedText = translated,
            sourceLanguage = sourceLangInput,
            targetLanguage = targetLangInput,
            sourceLang = sourceLangInput,
            targetLang = targetLangInput,
            isFromValidatedSource = isLocal,
            sourceBadge = badge,
            literalBreakdown = breakdown
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
        val lower = text.trim().lowercase().replace("’", "'")
        val isFromFrench = sourceLang.contains("franç", ignoreCase = true) || sourceLang.contains("franc", ignoreCase = true)
        val isTargetMoore = targetLang.contains("moor", ignoreCase = true)
        val isTargetDioula = targetLang.contains("dioula", ignoreCase = true) || targetLang.contains("jula", ignoreCase = true)
        val isTargetHausa = targetLang.contains("haoussa", ignoreCase = true) || targetLang.contains("hausa", ignoreCase = true)

        if (isFromFrench) {
            // 1. Salutations composées avec proches (ex: "bonjour mon frère", "merci mon ami")
            if (lower.startsWith("bonjour ") || lower.startsWith("salut ")) {
                val suffix = lower.removePrefix("bonjour ").removePrefix("salut ").trim()
                val (mrSuffix, dyuSuffix, haSuffix) = when (suffix) {
                    "mon frère", "mon frere" -> Triple("m ba-bi-raoogo", "n balimakɛ", "ɗan'uwa")
                    "ma sœur", "ma soeur" -> Triple("m ba-bi-põka", "n balimamuso", "ƴar'uwa")
                    "mon ami" -> Triple("m zoodo", "n teri", "abokina")
                    "mes amis" -> Triple("m zoodba", "n teriw", "abokaina")
                    "papa", "mon père", "mon pere" -> Triple("m baa", "n fa", "babana")
                    "maman", "ma mère", "ma mere" -> Triple("m ma", "n ba", "mamana")
                    "mon fils", "mon enfant" -> Triple("m biiga", "n den", "dana")
                    "ma femme" -> Triple("m paga", "n muso", "matata")
                    "mon mari" -> Triple("m sapa", "n cɛ", "miji na")
                    "tout le monde" -> Triple("neda fâa", "mɔgɔ bɛɛ", "kowa da kowa")
                    else -> Triple(null, null, null)
                }
                if (mrSuffix != null) {
                    return when {
                        isTargetMoore -> "Nii yi beogo $mrSuffix"
                        isTargetDioula -> "I ni sogoma $dyuSuffix"
                        isTargetHausa -> "Ina kwana $haSuffix"
                        else -> null
                    }
                }
            }

            if (lower.startsWith("bonsoir ")) {
                val suffix = lower.removePrefix("bonsoir ").trim()
                val (mrSuffix, dyuSuffix, haSuffix) = when (suffix) {
                    "mon frère", "mon frere" -> Triple("m ba-bi-raoogo", "n balimakɛ", "ɗan'uwa")
                    "ma sœur", "ma soeur" -> Triple("m ba-bi-põka", "n balimamuso", "ƴar'uwa")
                    "mon ami" -> Triple("m zoodo", "n teri", "abokina")
                    "papa", "mon père", "mon pere" -> Triple("m baa", "n fa", "babana")
                    "maman", "ma mère", "ma mere" -> Triple("m ma", "n ba", "mamana")
                    "tout le monde" -> Triple("neda fâa", "mɔgɔ bɛɛ", "kowa da kowa")
                    else -> Triple(null, null, null)
                }
                if (mrSuffix != null) {
                    return when {
                        isTargetMoore -> "Nii zabre $mrSuffix"
                        isTargetDioula -> "I ni wula $dyuSuffix"
                        isTargetHausa -> "Ina wuni $haSuffix"
                        else -> null
                    }
                }
            }

            if (lower.startsWith("merci beaucoup ") || lower.startsWith("merci ")) {
                val isMuch = lower.startsWith("merci beaucoup ")
                val suffix = if (isMuch) lower.removePrefix("merci beaucoup ").trim() else lower.removePrefix("merci ").trim()
                val (mrPrefix, dyuPrefix, haPrefix) = if (isMuch) {
                    Triple("Bark wusgo wusgo", "I ni ce kosɛbɛ", "Nagode sosai")
                } else {
                    Triple("Barka", "I ni ce", "Nagode")
                }
                val (mrSuffix, dyuSuffix, haSuffix) = when (suffix) {
                    "mon frère", "mon frere" -> Triple("m ba-bi-raoogo", "n balimakɛ", "ɗan'uwa")
                    "ma sœur", "ma soeur" -> Triple("m ba-bi-põka", "n balimamuso", "ƴar'uwa")
                    "mon ami" -> Triple("m zoodo", "n teri", "abokina")
                    "papa" -> Triple("m baa", "n fa", "babana")
                    "maman" -> Triple("m ma", "n ba", "mamana")
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
                        isTargetDioula -> "N tɔgɔ ye $name"
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
                        isTargetDioula -> "$translatedItem di n ma"
                        isTargetHausa -> "Ba ni $translatedItem"
                        else -> null
                    }
                }
            }

            // 4. Modèles "Où est X ?" / "Où se trouve X ?"
            if (lower.startsWith("où est ") || lower.startsWith("ou est ") || lower.startsWith("où se trouve ") || lower.startsWith("ou se trouve ")) {
                val item = lower.removePrefix("où est ").removePrefix("ou est ").removePrefix("où se trouve ").removePrefix("ou se trouve ").removePrefix("le ").removePrefix("la ").removePrefix("l'").trim().removeSuffix("?").trim()
                val translatedItem = DictionaryLookupEngine.translateDirect(item, "Français", targetLang)
                if (translatedItem != null) {
                    return when {
                        isTargetMoore -> "$translatedItem be yẽ ?"
                        isTargetDioula -> "$translatedItem bɛ min ?"
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
                    if (lower.startsWith("oum togo be ") || lower.startsWith("n tɔgɔ ye ") || lower.startsWith("n togo ye ")) {
                        val name = text.trim().substringAfterLast(" ", "").trim()
                        return "Je m'appelle $name"
                    }
                    if (lower.startsWith("oum b'a fe ") || lower.startsWith("oum ba fe ") || lower.startsWith("n b'a fɛ ")) {
                        val item = lower.removePrefix("oum b'a fe ").removePrefix("oum ba fe ").removePrefix("n b'a fɛ ").trim()
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
                    null
                }
            }
        }

        return if (translatedCount == words.size && translatedWords.all { it != null }) {
            translatedWords.filterNotNull().joinToString(" ")
        } else {
            null
        }
    }
}
