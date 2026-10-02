package com.example.data.engine

object GermanCompositionalEngine {
    private val GERMAN_UNITS = mapOf(
        0L to "Null",
        1L to "Eins",
        2L to "Zwei",
        3L to "Drei",
        4L to "Vier",
        5L to "Fünf",
        6L to "Sechs",
        7L to "Sieben",
        8L to "Acht",
        9L to "Neun",
        10L to "Zehn",
        11L to "Elf",
        12L to "Zwölf"
    )

    private val GERMAN_TENS = mapOf(
        20L to "Zwanzig",
        30L to "Dreißig",
        40L to "Vierzig",
        50L to "Fünfzig",
        60L to "Sechzig",
        70L to "Siebzig",
        80L to "Achtzig",
        90L to "Neunzig"
    )

    fun numberToGerman(n: Long): String {
        if (n in 0..12) return GERMAN_UNITS[n] ?: n.toString()
        if (n in 13..19) {
            val u = n - 10
            return (GERMAN_UNITS[u] ?: "").lowercase() + "zehn"
        }
        if (n in 20..99) {
            val t = (n / 10) * 10
            val u = n % 10
            if (u == 0L) return GERMAN_TENS[t] ?: n.toString()
            val uName = if (u == 1L) "ein" else (GERMAN_UNITS[u] ?: "").lowercase()
            return "${uName}und${(GERMAN_TENS[t] ?: "").lowercase()}"
        }
        if (n in 100..999) {
            val h = n / 100
            val rem = n % 100
            val hPrefix = if (h == 1L) "einhundert" else "${(GERMAN_UNITS[h] ?: "").lowercase()}hundert"
            if (rem == 0L) return hPrefix
            return hPrefix + numberToGerman(rem).lowercase()
        }
        if (n == 1000L) return "eintausend"
        return n.toString()
    }

    fun germanToNumber(input: String): Long? {
        val clean = input.trim().lowercase()
        clean.toLongOrNull()?.let { return it }
        GERMAN_UNITS.entries.find { it.value.equals(clean, ignoreCase = true) }?.let { return it.key }
        GERMAN_TENS.entries.find { it.value.equals(clean, ignoreCase = true) }?.let { return it.key }
        if (clean == "einhundert" || clean == "hundert") return 100L
        if (clean == "eintausend" || clean == "tausend") return 1000L
        return null
    }
}
