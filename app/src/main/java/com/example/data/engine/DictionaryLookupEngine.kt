package com.example.data.engine

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

    fun isFuzzyMatch(a: String, b: String): Boolean {
        val cleanA = stripPunctuation(a).lowercase()
        val cleanB = stripPunctuation(b).lowercase()
        if (cleanA.isEmpty() || cleanB.isEmpty()) return false
        if (cleanA == cleanB) return true
        if (cleanA.contains(cleanB) || cleanB.contains(cleanA)) return true
        return false
    }

    fun translateDirect(input: String, sourceLang: String, targetLang: String): String? {
        val clean = cleanWhitespace(input)
        if (clean.isBlank()) return null
        return null
    }
}
