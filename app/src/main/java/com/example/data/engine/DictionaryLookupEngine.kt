package com.example.data.engine

import com.example.data.local.DictionaryData
import com.example.data.local.DioulaKnowledgeData

object DictionaryLookupEngine {

    fun normalizeHausaSpecialChars(input: String): String {
        return input.trim()
            .replace("ɓ", "b")
            .replace("ɗ", "d")
            .replace("ƙ", "k")
            .replace("ƴ", "y")
            .replace("Ɓ", "B")
            .replace("Ɗ", "D")
            .replace("Ƙ", "K")
            .replace("Ƴ", "Y")
    }

    fun cleanWhitespace(input: String): String {
        return input.trim().replace(Regex("\\s+"), " ")
    }

    fun stripPunctuation(input: String): String {
        return input.filter { it.isLetterOrDigit() || it.isWhitespace() }.trim()
    }

    fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
            }
        }
        return dp[s1.length][s2.length]
    }

    fun isFuzzyMatch(a: String, b: String): Boolean {
        val cleanA = normalizeHausaSpecialChars(stripPunctuation(a).lowercase())
        val cleanB = normalizeHausaSpecialChars(stripPunctuation(b).lowercase())
        if (cleanA.isEmpty() || cleanB.isEmpty()) return false
        if (cleanA == cleanB) return true
        if (cleanA.replace(" ", "").replace("_", "") == cleanB.replace(" ", "").replace("_", "")) return true
        if (cleanA.length >= 4 && cleanB.length >= 4 && levenshteinDistance(cleanA, cleanB) <= 2) return true
        if (cleanA.contains(cleanB) || cleanB.contains(cleanA)) {
            val ratio = minOf(cleanA.length, cleanB.length).toDouble() / maxOf(cleanA.length, cleanB.length)
            if (ratio >= 0.75) return true
        }
        return false
    }

    // Lazy initialization of dictionary maps for ultra-fast O(1) lookups
    private val frenchToMooreMap by lazy {
        val map = mutableMapOf<String, String>()
        DictionaryData.FULL_DICTIONARY.forEach { entry ->
            val frKey = stripPunctuation(entry.french).lowercase().trim()
            if (frKey.isNotEmpty() && !map.containsKey(frKey)) {
                map[frKey] = entry.moore
            }
            if (entry.synonyms.isNotEmpty()) {
                entry.synonyms.split(",", ";").forEach { syn ->
                    val synKey = stripPunctuation(syn).lowercase().trim()
                    if (synKey.isNotEmpty() && !map.containsKey(synKey)) {
                        map[synKey] = entry.moore
                    }
                }
            }
        }
        map
    }

    private val mooreToFrenchMap by lazy {
        val map = mutableMapOf<String, String>()
        DictionaryData.FULL_DICTIONARY.forEach { entry ->
            val mrKey = stripPunctuation(entry.moore).lowercase().trim()
            if (mrKey.isNotEmpty() && !map.containsKey(mrKey)) {
                map[mrKey] = entry.french
            }
            if (entry.variants.isNotEmpty()) {
                entry.variants.split(",", ";").forEach { v ->
                    val vKey = stripPunctuation(v).lowercase().trim()
                    if (vKey.isNotEmpty() && !map.containsKey(vKey)) {
                        map[vKey] = entry.french
                    }
                }
            }
        }
        map
    }

    private val frenchToDioulaMap by lazy {
        val map = mutableMapOf<String, String>()
        try {
            DioulaKnowledgeData.seedDioulaDatabase().forEach { item ->
                val frKey = stripPunctuation(item.translation).lowercase().trim()
                if (frKey.isNotEmpty() && !map.containsKey(frKey)) {
                    map[frKey] = item.word
                }
            }
        } catch (_: Throwable) {}
        map
    }

    private val dioulaToFrenchMap by lazy {
        val map = mutableMapOf<String, String>()
        try {
            DioulaKnowledgeData.seedDioulaDatabase().forEach { item ->
                val diKey = stripPunctuation(item.word).lowercase().trim()
                if (diKey.isNotEmpty() && !map.containsKey(diKey)) {
                    map[diKey] = item.translation
                }
                if (!item.variants.isNullOrEmpty()) {
                    item.variants.split(",", ";").forEach { v ->
                        val vKey = stripPunctuation(v).lowercase().trim()
                        if (vKey.isNotEmpty() && !map.containsKey(vKey)) {
                            map[vKey] = item.translation
                        }
                    }
                }
            }
        } catch (_: Throwable) {}
        map
    }

    /**
     * Traduction directe dans le dictionnaire complet hors-ligne
     */
    fun translateDirect(input: String, sourceLang: String, targetLang: String): String? {
        val clean = cleanWhitespace(input)
        if (clean.isBlank()) return null
        val stripped = stripPunctuation(clean).lowercase()

        val src = sourceLang.lowercase().trim()
        val tgt = targetLang.lowercase().trim()

        // 1. Chiffres purs (ex: "10", "42")
        val numberVal = clean.toLongOrNull()
        if (numberVal != null) {
            return MultilingualTranslationEngine.formatNumberInTargetLanguage(numberVal, targetLang)
        }

        // 2. Français -> Mooré
        if (src.contains("franç") || src.contains("franc")) {
            if (tgt.contains("moor") || tgt.contains("mòoré")) {
                frenchToMooreMap[stripped]?.let { return it }
                DictionaryData.FULL_DICTIONARY.firstOrNull {
                    it.french.equals(clean, ignoreCase = true) || isFuzzyMatch(it.french, clean)
                }?.let { return it.moore }
            } else if (tgt.contains("dioula") || tgt.contains("jula")) {
                frenchToDioulaMap[stripped]?.let { return it }
                DioulaKnowledgeData.seedDioulaDatabase().firstOrNull {
                    it.translation.equals(clean, ignoreCase = true) || isFuzzyMatch(it.translation, clean)
                }?.let { return it.word }
            }
        }

        // 3. Mooré -> Français
        if (src.contains("moor") || src.contains("mòoré")) {
            if (tgt.contains("franç") || tgt.contains("franc")) {
                mooreToFrenchMap[stripped]?.let { return it }
                DictionaryData.FULL_DICTIONARY.firstOrNull {
                    it.moore.equals(clean, ignoreCase = true) || isFuzzyMatch(it.moore, clean)
                }?.let { return it.french }
            }
        }

        // 4. Dioula -> Français
        if (src.contains("dioula") || src.contains("jula")) {
            if (tgt.contains("franç") || tgt.contains("franc")) {
                dioulaToFrenchMap[stripped]?.let { return it }
                DioulaKnowledgeData.seedDioulaDatabase().firstOrNull {
                    it.word.equals(clean, ignoreCase = true) || isFuzzyMatch(it.word, clean)
                }?.let { return it.translation }
            }
        }

        // 5. Traduction mot-à-mot pour phrases composées
        return translateWordByWord(clean, sourceLang, targetLang)
    }

    fun translateWordByWord(sentence: String, sourceLang: String, targetLang: String): String? {
        val tokens = sentence.split(Regex("\\s+"))
        if (tokens.size < 2) return null

        var translatedAtLeastOne = false
        val resultTokens = tokens.map { token ->
            val stripped = stripPunctuation(token).lowercase()
            if (stripped.length <= 1) {
                token
            } else {
                val direct = translateDirect(token, sourceLang, targetLang)
                if (direct != null && !direct.equals(token, ignoreCase = true)) {
                    translatedAtLeastOne = true
                    direct
                } else {
                    token
                }
            }
        }

        return if (translatedAtLeastOne) {
            resultTokens.joinToString(" ")
        } else {
            null
        }
    }
}
