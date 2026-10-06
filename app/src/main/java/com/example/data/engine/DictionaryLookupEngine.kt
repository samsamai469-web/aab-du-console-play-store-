package com.example.data.engine

import com.example.data.local.DictionaryData
import com.example.data.local.DioulaKnowledgeData
import com.example.data.local.HausaKnowledgeData

data class UnifiedDictionaryEntry(
    val id: String,
    val word: String, // Mot en langue locale
    val french: String, // Traduction en français
    val language: String, // "Mooré", "Dioula", "Haoussa"
    val category: String, // "Salutation", "Politesse", "Commerce", etc.
    val pronunciation: String = "",
    val variants: String = ""
)

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

    fun isCloseMatch(a: String, b: String): Boolean {
        val cleanA = normalizeHausaSpecialChars(stripPunctuation(a).lowercase())
        val cleanB = normalizeHausaSpecialChars(stripPunctuation(b).lowercase())
        if (cleanA.isEmpty() || cleanB.isEmpty()) return false
        if (cleanA == cleanB) return true
        if ((cleanA == "bjour" && cleanB == "bonjour") || (cleanA == "bonjour" && cleanB == "bjour")) return true
        val dist = levenshteinDistance(cleanA, cleanB)
        if (cleanA.length >= 4 && cleanB.length >= 4 && dist <= 1) return true
        if (minOf(cleanA.length, cleanB.length) >= 5 && maxOf(cleanA.length, cleanB.length) >= 6 && dist <= 2) return true
        return false
    }

    fun isFuzzyMatch(a: String, b: String): Boolean = isCloseMatch(a, b)

    // --- DICTIONNAIRES EN MÉMOIRE POUR ACCÈS O(1) RAPIDE ET SÛR ---

    private val frenchToMooreMap by lazy {
        val map = mutableMapOf<String, String>()
        
        // 1. Entrées de référence officielles prioritaires (mises à jour par l'utilisateur)
        DictionaryData.AUTHORITATIVE_REFERENCE_ENTRIES.forEach { entry ->
            val frKey = stripPunctuation(entry.french).lowercase().trim()
            if (frKey.isNotEmpty()) {
                map[frKey] = entry.moore
            }
            if (entry.synonyms.isNotEmpty()) {
                entry.synonyms.split(",", ";").forEach { syn ->
                    val synKey = stripPunctuation(syn).lowercase().trim()
                    if (synKey.isNotEmpty()) map[synKey] = entry.moore
                }
            }
            if (entry.variants.isNotEmpty()) {
                entry.variants.split(",", ";").forEach { v ->
                    val vKey = stripPunctuation(v).lowercase().trim()
                    if (vKey.isNotEmpty()) map[vKey] = entry.moore
                }
            }
        }

        // 2. Expressions conversationnelles courantes
        val common = mapOf(
            "salut" to "Nii yi beogo",
            "bonjour" to "Nii yi beogo",
            "bonsoir" to "Nii zabre",
            "bonne nuit" to "Wind da ganeg laafi",
            "ca va" to "Laafi bala",
            "ça va" to "Laafi bala",
            "comment vas tu" to "Fo laafi ?",
            "comment vas-tu" to "Fo laafi ?",
            "comment tu vas" to "Fo laafi ?",
            "comment allez vous" to "Yãmb laafi bame wa ?",
            "comment allez-vous" to "Yãmb laafi bame wa ?",
            "merci" to "Barka",
            "merci beaucoup" to "Bark wusgo wusgo",
            "de rien" to "Barka ka be ye",
            "sil te plait" to "Tõnd kotame",
            "s'il te plaît" to "Tõnd kotame",
            "sil vous plait" to "Tõnd kotame",
            "s'il vous plaît" to "Tõnd kotame",
            "pardon" to "Sugri",
            "oui" to "N-ye",
            "non" to "Ayo",
            "daccord" to "A zemsame",
            "d'accord" to "A zemsame",
            "je vais bien" to "Mam laafi bala",
            "tout va bien" to "Bũmb ka be ye",
            "au revoir" to "Bilmãse",
            "a demain" to "Beoogo laafi",
            "à demain" to "Beoogo laafi",
            "a bientot" to "Nindaare laafi",
            "à bientôt" to "Nindaare laafi",
            "a plus tard" to "Wakat a to",
            "à plus tard" to "Wakat a to",
            "bienvenue" to "Ne y waoongo",
            "bonne arrivee" to "Ne y waoongo",
            "bonne arrivée" to "Ne y waoongo",
            "je taime" to "M nonga foo",
            "je t'aime" to "M nonga foo",
            "je mappelle" to "Mam yʋʋre la",
            "je m'appelle" to "Mam yʋʋre la",
            "comment tu tappelles" to "Fo yʋʋre la bõe ?",
            "comment tu t'appelles" to "Fo yʋʋre la bõe ?",
            "eau" to "Koom",
            "leau" to "Koom",
            "l'eau" to "Koom",
            "argent" to "Ligdi",
            "largent" to "Ligdi",
            "l'argent" to "Ligdi",
            "maison" to "Zaka",
            "marche" to "Raaga",
            "marché" to "Raaga",
            "nourriture" to "Rɩɩbo",
            "homme" to "Rawa",
            "femme" to "Paga",
            "enfant" to "Biiga",
            "riz" to "Mougne",
            "mais" to "Kanmane",
            "maïs" to "Kanmane",
            "voiture" to "Monbile",
            "moto" to "Monteur",
            "velo" to "Weefo",
            "vélo" to "Weefo",
            "chaussure" to "Nanwoudre",
            "chaussures" to "Nanwoudre",
            "habit" to "Foūgou",
            "je veux largent" to "M data ligdi",
            "je veux l'argent" to "M data ligdi",
            "je vais dormir" to "M nan gounsam",
            "je vais a la maison" to "M debda zakin wan",
            "je vais à la maison" to "M debda zakin wan",
            "donne moi de leau" to "Kõ-m koom",
            "donne-moi de l'eau" to "Kõ-m koom",
            "donne moi de largent" to "Kõ-m ligdi",
            "donne-moi de l'argent" to "Kõ-m ligdi"
        )
        common.forEach { (k, v) ->
            if (!map.containsKey(k)) map[k] = v
        }

        // 3. Grand dictionnaire général
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

        // 1. Entrées de référence officielles
        DictionaryData.AUTHORITATIVE_REFERENCE_ENTRIES.forEach { entry ->
            val mrKey = stripPunctuation(entry.moore).lowercase().trim()
            if (mrKey.isNotEmpty()) {
                map[mrKey] = entry.french
            }
            if (entry.variants.isNotEmpty()) {
                entry.variants.split(",", ";").forEach { v ->
                    val vKey = stripPunctuation(v).lowercase().trim()
                    if (vKey.isNotEmpty()) map[vKey] = entry.french
                }
            }
        }

        // 2. Expressions conversationnelles courantes
        val common = mapOf(
            "nii yi beogo" to "Bonjour",
            "ne y beogo" to "Bonjour",
            "nei beogo" to "Bonjour",
            "n'yibeogo" to "Bonjour",
            "nii zabre" to "Bonsoir",
            "ne zaabre" to "Bonsoir",
            "wind da ganeg laafi" to "Bonne nuit",
            "barka" to "Merci",
            "bark wusgo wusgo" to "Merci beaucoup",
            "ya sonma" to "Ça va bien",
            "laafi" to "Ça va bien",
            "laafi bala" to "Ça va bien",
            "kibare" to "Comment ça va ?",
            "kibaare" to "Comment ça va ?",
            "fo laafi" to "Comment vas-tu ?",
            "fo laafi bame wa" to "Comment vas-tu ?",
            "neewôgô" to "Bienvenue",
            "ne y waoongo" to "Bienvenue",
            "m data ligdi" to "Je veux l'argent",
            "m debda zakin" to "Je vais à la maison",
            "m debda zakin wan" to "Je vais à la maison",
            "m nan gounsam" to "Je vais dormir",
            "koom" to "Eau",
            "ligdi" to "Argent",
            "mougne" to "Riz",
            "kanmane" to "Maïs",
            "zaka" to "Maison",
            "roogo" to "Maison",
            "raaga" to "Marché",
            "rawa" to "Homme",
            "paga" to "Femme",
            "biiga" to "Enfant",
            "m nonga foo" to "Je t'aime",
            "sugri" to "Pardon",
            "a zemsame" to "D'accord",
            "bilmãse" to "Au revoir",
            "beoogo laafi" to "À demain",
            "wakat a to" to "À plus tard",
            "nindaare" to "À bientôt",
            "nindaare laafi" to "À bientôt"
        )
        common.forEach { (k, v) ->
            if (!map.containsKey(k)) map[k] = v
        }

        // 3. Grand dictionnaire
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
            }
        } catch (_: Throwable) {}
        map
    }

    private val frenchToHausaMap by lazy {
        val map = mutableMapOf<String, String>()
        HausaKnowledgeData.FULL_HAUSA_DICTIONARY.forEach { entry ->
            val frKey = stripPunctuation(entry.french).lowercase().trim()
            if (frKey.isNotEmpty() && !map.containsKey(frKey)) {
                map[frKey] = entry.hausa
            }
        }
        map
    }

    private val hausaToFrenchMap by lazy {
        val map = mutableMapOf<String, String>()
        HausaKnowledgeData.FULL_HAUSA_DICTIONARY.forEach { entry ->
            val haKey = stripPunctuation(entry.hausa).lowercase().trim()
            val normKey = normalizeHausaSpecialChars(haKey)
            if (haKey.isNotEmpty() && !map.containsKey(haKey)) {
                map[haKey] = entry.french
            }
            if (normKey.isNotEmpty() && !map.containsKey(normKey)) {
                map[normKey] = entry.french
            }
        }
        map
    }

    /**
     * Traduction directe dans le dictionnaire certifié.
     * Règle absolue : Ne retourne une traduction QUE SI elle existe exactement ou sous forme d'expression reconnue.
     * Ne fait JAMAIS de substitution mot-à-mot hasardeuse qui mélangerait les langues.
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
                    it.french.equals(clean, ignoreCase = true) || isCloseMatch(it.french, clean)
                }?.let { return it.moore }
            } else if (tgt.contains("dioula") || tgt.contains("jula")) {
                frenchToDioulaMap[stripped]?.let { return it }
                DioulaKnowledgeData.seedDioulaDatabase().firstOrNull {
                    it.translation.equals(clean, ignoreCase = true) || isCloseMatch(it.translation, clean)
                }?.let { return it.word }
            } else if (tgt.contains("haoussa") || tgt.contains("hausa")) {
                frenchToHausaMap[stripped]?.let { return it }
                HausaKnowledgeData.FULL_HAUSA_DICTIONARY.firstOrNull {
                    it.french.equals(clean, ignoreCase = true) || isCloseMatch(it.french, clean)
                }?.let { return it.hausa }
            }
        }

        // 3. Mooré -> Français
        if (src.contains("moor") || src.contains("mòoré")) {
            if (tgt.contains("franç") || tgt.contains("franc")) {
                mooreToFrenchMap[stripped]?.let { return it }
                DictionaryData.FULL_DICTIONARY.firstOrNull {
                    it.moore.equals(clean, ignoreCase = true) || isCloseMatch(it.moore, clean)
                }?.let { return it.french }
            }
        }

        // 4. Dioula -> Français
        if (src.contains("dioula") || src.contains("jula")) {
            if (tgt.contains("franç") || tgt.contains("franc")) {
                dioulaToFrenchMap[stripped]?.let { return it }
                DioulaKnowledgeData.seedDioulaDatabase().firstOrNull {
                    it.word.equals(clean, ignoreCase = true) || isCloseMatch(it.word, clean)
                }?.let { return it.translation }
            }
        }

        // 5. Haoussa -> Français
        if (src.contains("haoussa") || src.contains("hausa")) {
            if (tgt.contains("franç") || tgt.contains("franc")) {
                hausaToFrenchMap[stripped]?.let { return it }
                hausaToFrenchMap[normalizeHausaSpecialChars(stripped)]?.let { return it }
                HausaKnowledgeData.FULL_HAUSA_DICTIONARY.firstOrNull {
                    it.hausa.equals(clean, ignoreCase = true) || isCloseMatch(it.hausa, clean)
                }?.let { return it.french }
            }
        }

        return null
    }

    /**
     * Fournit l'intégralité du lexique unifié pour l'onglet Dictionnaire (2500+ entrées).
     * Accessible instantanément sans chargement de base de données.
     */
    val unifiedDictionaryList: List<UnifiedDictionaryEntry> by lazy {
        val list = mutableListOf<UnifiedDictionaryEntry>()
        val seenPairs = mutableSetOf<String>()

        // 1. Entrées de référence officielles prioritaires Mooré (les mots demandés par l'utilisateur)
        DictionaryData.AUTHORITATIVE_REFERENCE_ENTRIES.forEachIndexed { index, entry ->
            val key = "${entry.moore.lowercase().trim()}_${entry.french.lowercase().trim()}"
            if (seenPairs.add(key)) {
                list.add(
                    UnifiedDictionaryEntry(
                        id = "mr_ref_$index",
                        word = entry.moore,
                        french = entry.french,
                        language = "Mooré",
                        category = entry.category.ifBlank { "Salutations et expressions" },
                        pronunciation = entry.pronunciation.ifBlank { entry.moore },
                        variants = entry.variants
                    )
                )
            }
        }

        // 2. Entrées Mooré complètes (1900+)
        DictionaryData.FULL_DICTIONARY.forEachIndexed { index, entry ->
            val key = "${entry.moore.lowercase().trim()}_${entry.french.lowercase().trim()}"
            if (seenPairs.add(key)) {
                list.add(
                    UnifiedDictionaryEntry(
                        id = "mr_$index",
                        word = entry.moore,
                        french = entry.french,
                        language = "Mooré",
                        category = entry.category.ifBlank { "Général" },
                        pronunciation = entry.pronunciation.ifBlank { entry.moore },
                        variants = entry.variants
                    )
                )
            }
        }

        // 3. Entrées Dioula (300+)
        try {
            DioulaKnowledgeData.seedDioulaDatabase().forEachIndexed { index, item ->
                list.add(
                    UnifiedDictionaryEntry(
                        id = "dyu_$index",
                        word = item.word,
                        french = item.translation,
                        language = "Dioula",
                        category = item.category.ifBlank { "Général" },
                        pronunciation = item.pronunciation ?: item.word,
                        variants = item.variants ?: ""
                    )
                )
            }
        } catch (_: Throwable) {}

        // 4. Entrées Haoussa (100+)
        HausaKnowledgeData.FULL_HAUSA_DICTIONARY.forEachIndexed { index, item ->
            list.add(
                UnifiedDictionaryEntry(
                    id = "ha_$index",
                    word = item.hausa,
                    french = item.french,
                    language = "Haoussa",
                    category = item.category,
                    pronunciation = item.pronunciation,
                    variants = item.variants
                )
            )
        }

        list
    }
}
