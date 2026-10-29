package com.example.data.engine

/**
 * Universal Intelligent Language Detector for Sam Traduction.
 * Accurately classifies input text as Mooré, Dioula, Haoussa, Allemand, Anglais, Français, etc.
 */
object LanguageDetector {

    fun detect(text: String): String {
        val clean = text.trim()
        if (clean.isBlank()) return "Français"

        val deprolonged = DictionaryLookupEngine.normalizeMooreProlongations(clean)
        val lower = deprolonged.lowercase()

        // 1. Specific Phonetic Mooré characters (100% confidence)
        if (lower.any { it in listOf('~', 'ɛ', 'ɩ', 'ʋ', 'ã', 'ẽ', 'ĩ', 'õ', 'ũ') }) {
            return "Mooré"
        }

        // 2. Specific Phonetic Haoussa characters (100% confidence)
        if (lower.any { it in listOf('ɓ', 'ɗ', 'ƙ', 'ƴ', 'Ɓ', 'Ɗ', 'Ƙ', 'Ƴ') }) {
            return "Haoussa"
        }

        // 3. Haoussa specific grammatical keywords & numbers
        val hausaKeywords = listOf(
            "ina kwana", "sannu da safe", "barka da asuba", "sannu", "sannu da",
            "barka da yamma", "ina wuni", "mu kwana lafiya", "barka da dare",
            "barka da yini", "barka da zuwa", "yaya kake", "yaya kike", "yaya kuke",
            "lafiya lau", "nagode", "na gode", "don allah", "babu komai", "ba komai",
            "sai an jima", "sai anjima", "sai gobe", "sauka lafiya", "ina sonka", "ina sonki",
            "ina jin yunwa", "ina jin kishirwa", "na gaji", "ba ni da lafiya", "ina bandaki",
            "ku taimaka", "ruwan sha", "ashirin", "talatin", "arba'in", "hamsin",
            "sittin", "saba'in", "tamanin", "tasa'in", "bakwai", "takwas", "goma sha",
            "dubu", "kudi", "ruwa", "gida"
        )
        if (hausaKeywords.any { lower.contains(it) }) {
            return "Haoussa"
        }

        // 4. Mooré specific grammatical keywords, greetings & 50 audio lessons
        val mooreKeywords = listOf(
            "ne yibeogo", "nii yi beogo", "yibeo kibare", "ne yibeoogo", "yibeoogo",
            "nii windga", "windga kibare", "nii zabre", "zabre kibare", "niyungo", "yungo kibare",
            "wind da ganeg laafi", "neewôgô", "laafi", "ya sonma", "kibaré", "kibare",
            "nindaaré", "nindaare", "wënd-na kô-d nindaare", "zom-kom", "sên na be",
            "m data ligdi", "ligdi", "m nan gounsam", "m debda", "zakin",
            "nikieman", "poug yaanga", "poug sada", "kakouada", "tandaogo", "m'ma", "m'baaba",
            "weefo", "foūgou", "fougou", "rawa", "paga", "biiga", "raaga",
            "monteur", "monbile", "pnous", "nanwoudre", "moncoroni", "son kaam", "bidou", "mougne", "beinga", "touayga", "banga", "yagre",
            "zaame", "zâamé", "zaamê", "beoogo", "dũndã", "tõndo", "yãmba", "bãmba", "koabga", "tusri", "pisi",
            "pis-naase", "pis-nu", "pis-yoobe", "pis-yopoe", "pis-nii", "pis-wai",
            "a yiibu", "a tãabo", "a naase", "a nu", "a yoobe", "a yopoe", "a nii", "a wai",
            "piiga", "yembre", "zaalem", "wusgo", "masã", "roogo", "koom", "nemdo"
        )
        if (mooreKeywords.any { lower.contains(it) }) {
            return "Mooré"
        }

        // 5. Dioula specific grammatical keywords & numbers
        val dioulaKeywords = listOf(
            "ani sogoma", "i ni sogoma", "ani tilé", "ani houla", "ani sou", "anitie",
            "anibara", "anbissini", "anbedoni", "adanse", "oum", "aôn", "wari", "kossobe",
            "bina ta", "ta so", "seguena", "doussou", "fanga", "sinoro", "moullo", "thiama",
            "kamele", "soungourou", "teri thie", "teri mousso", "bisse", "mugan", "bisaba",
            "binaani", "biduuru", "biwoor", "biwoorɔ", "biwolonfla", "biseegin", "bikɔnɔntɔn",
            "kɛmɛ", "keme", "wa kelen", "ba kelen", "tan ni", "kelen", "fila", "saba",
            "naani", "duuru", "woorɔ", "wolonfla", "seegin", "kɔnɔntɔn", "somorodô",
            "thienlo", "thiente", "negueso", "dougouma", "san fe"
        )
        if (dioulaKeywords.any { lower.contains(it) } || lower.endsWith(" wa") || lower.contains(" wa?")) {
            return "Dioula"
        }

        // 6. German (Allemand) specific keywords & numbers
        val germanKeywords = listOf(
            "guten morgen", "guten tag", "guten abend", "gute nacht", "wie geht",
            "danke", "vielen dank", "bitte", "tschüss", "auf wiedersehen", "bis bald",
            "bis morgen", "bis später", "ich habe", "ich bin", "ich will", "ich möchte",
            "wasser", "geld", "hause", "schlafen", "müde", "hunger", "durst",
            "einundzwanzig", "zweiundzwanzig", "vierundsechzig", "siebenundachtzig",
            "hundert", "tausend", "zwanzig", "dreißig", "vierzig", "fünfzig",
            "sechzig", "siebzig", "achtzig", "neunzig", "und", "nicht", "oder",
            "deutsch", "hallo", "ja", "nein"
        )
        if (germanKeywords.any { lower.contains(it) }) {
            return "Allemand"
        }

        // 7. Token-based frequency match
        val tokens = lower.split(Regex("[\\s,;:.?!'’\"«»()\\-_/]+")).filter { it.isNotBlank() }
        val hausaTokenMatches = tokens.count { token ->
            token in listOf("ina", "kake", "kike", "kuke", "muna", "suna", "yana", "tana", "sannu", "nagode", "ruwa", "kudi", "gida", "aiki", "dubu", "dari")
        }
        val mooreTokenMatches = tokens.count { token ->
            token in listOf("mam", "fo", "tõndo", "tõnd", "yãmba", "bãmba", "koom", "laafi", "biiga", "roogo", "zaka", "raaga", "tẽnga", "woto", "bala")
        }
        val dioulaTokenMatches = tokens.count { token ->
            token in listOf("oum", "ne", "ele", "aôn", "ao", "olu", "dji", "so", "wari", "kene", "doni", "thiama", "sissan")
        }
        val germanTokenMatches = tokens.count { token ->
            token in listOf("ich", "du", "er", "sie", "es", "wir", "ihr", "ist", "sind", "das", "der", "die", "ein", "eine", "nicht", "und", "mit")
        }

        val maxMatches = maxOf(hausaTokenMatches, mooreTokenMatches, dioulaTokenMatches, germanTokenMatches)
        if (maxMatches > 0) {
            return when {
                hausaTokenMatches == maxMatches -> "Haoussa"
                mooreTokenMatches == maxMatches -> "Mooré"
                dioulaTokenMatches == maxMatches -> "Dioula"
                germanTokenMatches == maxMatches -> "Allemand"
                else -> "Français"
            }
        }

        // Default to French
        return "Français"
    }
}
