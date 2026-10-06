package com.example.data.engine

import com.example.data.cache.SmartTranslationCache
import com.example.data.local.LinguisticDao
import com.example.data.local.LinguisticKnowledge

/**
 * Unified Multilingual Translation Engine for Sam AI.
 * Implements the Invisible French Pivot architecture:
 *
 * Direct Pairs:
 * - Mooré ↔ Français
 * - Dioula ↔ Français
 * - Haoussa ↔ Français
 * - Allemand ↔ Français
 * - Anglais ↔ Français
 * - Arabe ↔ Français
 * - Espagnol ↔ Français
 *
 * Invisible Bridge (French Pivot in Background):
 * - Haoussa → Mooré / Dioula / Allemand / Anglais
 * - Mooré → Haoussa / Allemand / Dioula / Anglais
 * - etc.
 *
 * The user ONLY sees the final result in the target language.
 */
object MultilingualTranslationEngine {

    fun getCacheKey(text: String, src: String, tgt: String): String =
        "${src.trim().lowercase()}_${tgt.trim().lowercase()}_${text.trim().lowercase()}"

    fun getFromCache(text: String, src: String, tgt: String): String? =
        SmartTranslationCache.get(getCacheKey(text, src, tgt))

    fun putInCache(text: String, src: String, tgt: String, translation: String, isValidated: Boolean = false) {
        SmartTranslationCache.put(getCacheKey(text, src, tgt), translation)
    }

    // -------------------------------------------------------------
    // 1. DIRECT MULTI-WAY NUMBER ENGINE
    // -------------------------------------------------------------

    private val MOORE_BASE_UNITS = mapOf(
        0L to "Zaalem",
        1L to "Yembre",
        2L to "A yiibu",
        3L to "A tãabo",
        4L to "A naase",
        5L to "A nu",
        6L to "A yoobe",
        7L to "A yopoe",
        8L to "A nii",
        9L to "A wai",
        10L to "Piiga"
    )

    private val MOORE_BASE_TENS = mapOf(
        20L to "Pisi",
        30L to "Pisa tãabo",
        40L to "Pis-naase",
        50L to "Pis-nu",
        60L to "Pis-yoobe",
        70L to "Pis-yopoe",
        80L to "Pis-nii",
        90L to "Pis-wai"
    )

    private val HAUSA_UNITS = mapOf(
        0L to "Sifiri",
        1L to "Ɗaya",
        2L to "Biyu",
        3L to "Uku",
        4L to "Huɗu",
        5L to "Biyar",
        6L to "Shida",
        7L to "Bakwai",
        8L to "Takwas",
        9L to "Tara",
        10L to "Goma"
    )

    private val HAUSA_TENS = mapOf(
        20L to "Ashirin",
        30L to "Talatin",
        40L to "Arba'in",
        50L to "Hamsin",
        60L to "Sittin",
        70L to "Saba'in",
        80L to "Tamanin",
        90L to "Tasa'in"
    )

    private val ENGLISH_UNITS = mapOf(
        0L to "Zero", 1L to "One", 2L to "Two", 3L to "Three", 4L to "Four",
        5L to "Five", 6L to "Six", 7L to "Seven", 8L to "Eight", 9L to "Nine",
        10L to "Ten", 11L to "Eleven", 12L to "Twelve", 13L to "Thirteen", 14L to "Fourteen",
        15L to "Fifteen", 16L to "Sixteen", 17L to "Seventeen", 18L to "Eighteen", 19L to "Nineteen"
    )

    private val ENGLISH_TENS = mapOf(
        20L to "Twenty", 30L to "Thirty", 40L to "Forty", 50L to "Fifty",
        60L to "Sixty", 70L to "Seventy", 80L to "Eighty", 90L to "Ninety"
    )

    fun numberToMoore(n: Long): String {
        if (n < 0) return "Zaalem"
        if (n in 0..10) return MOORE_BASE_UNITS[n] ?: "Zaalem"
        if (n in 11..19) {
            val unit = n - 10
            val unitStr = (MOORE_BASE_UNITS[unit]?.lowercase() ?: "yembre")
            return "Piig la $unitStr"
        }
        if (n in 20..99 && n % 10 == 0L) {
            return MOORE_BASE_TENS[n] ?: "Pisi"
        }
        if (n in 21..99) {
            val tens = (n / 10) * 10
            val unit = n % 10
            val tensWord = MOORE_BASE_TENS[tens] ?: "Pisi"
            val unitWord = MOORE_BASE_UNITS[unit]?.lowercase() ?: "yembre"
            return "$tensWord la $unitWord"
        }
        if (n == 100L) return "Koabga"
        if (n in 101..999) {
            val hundredCount = n / 100
            val remainder = n % 100
            val prefix = if (hundredCount == 1L) "Koabga" else "Koabg ${MOORE_BASE_UNITS[hundredCount]?.lowercase()}"
            return if (remainder == 0L) prefix else "$prefix la ${numberToMoore(remainder).lowercase()}"
        }
        if (n == 1000L) return "Tusri"
        if (n > 1000L && n % 1000L == 0L) {
            val thousands = n / 1000L
            return "Tusr ${MOORE_BASE_UNITS[thousands]?.lowercase()}"
        }
        return "Tusri la " + numberToMoore(n % 1000).lowercase()
    }

    fun numberToHausa(n: Long): String {
        if (n < 0) return "Sifiri"
        if (n in 0..10) return HAUSA_UNITS[n] ?: "$n"
        if (n in 11..19) {
            val u = n - 10
            val uStr = HAUSA_UNITS[u]?.lowercase() ?: "$u"
            return "Goma sha $uStr"
        }
        if (n in 20..99) {
            val tens = (n / 10) * 10
            val unit = n % 10
            val tStr = HAUSA_TENS[tens] ?: "$tens"
            return if (unit == 0L) tStr else "$tStr da ${HAUSA_UNITS[unit]?.lowercase()}"
        }
        if (n == 100L) return "Ɗari"
        if (n in 101..999) {
            val h = n / 100
            val rem = n % 100
            val hStr = if (h == 1L) "Ɗari" else "Ɗari ${HAUSA_UNITS[h]?.lowercase()}"
            return if (rem == 0L) hStr else "$hStr da ${numberToHausa(rem).lowercase()}"
        }
        if (n == 1000L) return "Dubu"
        if (n > 1000L && n % 1000L == 0L) {
            val th = n / 1000L
            return "Dubu ${HAUSA_UNITS[th]?.lowercase()}"
        }
        return "Dubu da ${numberToHausa(n % 1000).lowercase()}"
    }

    fun numberToEnglish(n: Long): String {
        if (n in 0..19) return ENGLISH_UNITS[n] ?: "$n"
        if (n in 20..99) {
            val ten = (n / 10) * 10
            val unit = n % 10
            val tenStr = ENGLISH_TENS[ten] ?: ""
            return if (unit == 0L) tenStr else "$tenStr-${ENGLISH_UNITS[unit]?.lowercase()}"
        }
        if (n == 100L) return "One hundred"
        if (n in 101..999) {
            val hundred = n / 100
            val rem = n % 100
            val hStr = "${ENGLISH_UNITS[hundred]} hundred"
            return if (rem == 0L) hStr else "$hStr and ${numberToEnglish(rem).lowercase()}"
        }
        if (n == 1000L) return "One thousand"
        return n.toString()
    }

    fun mooreToNumber(input: String): Long? {
        val clean = input.trim().lowercase()
            .replace("yemre", "yembre")
            .replace("pisa taabo", "pisa tãabo")
            .replace("pis naase", "pis-naase")
            .replace("pis nu", "pis-nu")
            .replace("pis yoobe", "pis-yoobe")
            .replace("pis yopoe", "pis-yopoe")
            .replace("pis nii", "pis-nii")
            .replace("pis wai", "pis-wai")

        clean.toLongOrNull()?.let { return it }

        MOORE_BASE_UNITS.entries.find { it.value.equals(clean, ignoreCase = true) }?.let { return it.key }
        MOORE_BASE_TENS.entries.find { it.value.equals(clean, ignoreCase = true) }?.let { return it.key }

        if (clean == "koabga") return 100L
        if (clean == "tusri") return 1000L

        if (clean.contains(" la ")) {
            val parts = clean.split(" la ").map { it.trim() }
            if (parts.size == 2) {
                val first = parts[0]
                val second = parts[1]

                if (first == "piig" || first == "piiga") {
                    val unit = MOORE_BASE_UNITS.entries.find { it.value.equals(second, ignoreCase = true) }?.key
                    if (unit != null) return 10L + unit
                }

                val tensVal = MOORE_BASE_TENS.entries.find { it.value.equals(first, ignoreCase = true) }?.key
                val unitVal = MOORE_BASE_UNITS.entries.find { it.value.equals(second, ignoreCase = true) }?.key
                if (tensVal != null && unitVal != null) {
                    return tensVal + unitVal
                }
            }
        }

        return null
    }

    fun hausaToNumber(input: String): Long? {
        val clean = DictionaryLookupEngine.normalizeHausaSpecialChars(input.trim().lowercase())
        clean.toLongOrNull()?.let { return it }

        HAUSA_UNITS.entries.find { DictionaryLookupEngine.normalizeHausaSpecialChars(it.value).equals(clean, ignoreCase = true) }?.let { return it.key }
        HAUSA_TENS.entries.find { DictionaryLookupEngine.normalizeHausaSpecialChars(it.value).equals(clean, ignoreCase = true) }?.let { return it.key }

        if (clean == "dari" || clean == "ɗari") return 100L
        if (clean == "dubu") return 1000L

        if (clean.startsWith("goma sha ")) {
            val unitPart = clean.removePrefix("goma sha ").trim()
            val unit = HAUSA_UNITS.entries.find { DictionaryLookupEngine.normalizeHausaSpecialChars(it.value).equals(unitPart, ignoreCase = true) }?.key
            if (unit != null) return 10L + unit
        }

        if (clean.contains(" da ")) {
            val parts = clean.split(" da ").map { it.trim() }
            if (parts.size == 2) {
                val tVal = HAUSA_TENS.entries.find { DictionaryLookupEngine.normalizeHausaSpecialChars(it.value).equals(parts[0], ignoreCase = true) }?.key
                val uVal = HAUSA_UNITS.entries.find { DictionaryLookupEngine.normalizeHausaSpecialChars(it.value).equals(parts[1], ignoreCase = true) }?.key
                if (tVal != null && uVal != null) return tVal + uVal
            }
        }

        return null
    }

    fun parseNumberFromAnyLanguage(input: String): Long? {
        val clean = input.trim()

        clean.toLongOrNull()?.let { return it }
        DioulaCompositionalEngine.DioulaNumberEngine.dioulaToNumber(clean)?.let { return it }
        mooreToNumber(clean)?.let { return it }
        hausaToNumber(clean)?.let { return it }
        GermanCompositionalEngine.germanToNumber(clean)?.let { return it }
        DioulaCompositionalEngine.DioulaNumberEngine.parseFrenchOrDigit(clean)?.let { return it }

        // English numbers
        ENGLISH_UNITS.entries.find { it.value.equals(clean, ignoreCase = true) }?.let { return it.key }
        ENGLISH_TENS.entries.find { it.value.equals(clean, ignoreCase = true) }?.let { return it.key }

        return null
    }

    fun formatNumberInTargetLanguage(number: Long, targetLang: String): String {
        return when (targetLang.lowercase()) {
            "dioula" -> DioulaCompositionalEngine.DioulaNumberEngine.numberToDioula(number)
            "mooré", "moore" -> numberToMoore(number)
            "haoussa", "hausa" -> numberToHausa(number)
            "allemand" -> GermanCompositionalEngine.numberToGerman(number)
            "anglais" -> numberToEnglish(number)
            "français", "francais" -> number.toString()
            else -> number.toString()
        }
    }

    // -------------------------------------------------------------
    // 2. VERIFIED EXACT CROSS-EXPRESSIONS DICTIONARY
    // -------------------------------------------------------------
    private val DIRECT_CROSS_EXPRESSIONS = listOf(
        // Bonjour
        mapOf("Français" to "Bonjour", "Dioula" to "Ani sogoma", "Mooré" to "Nii yi beogo", "Haoussa" to "Ina kwana", "Allemand" to "Guten Tag", "Anglais" to "Hello", "Arabe" to "مرحبا", "Espagnol" to "Hola"),
        mapOf("Français" to "Bonjour (matin)", "Dioula" to "Ani sogoma", "Mooré" to "Nii yi beogo", "Haoussa" to "Ina kwana", "Allemand" to "Guten Morgen", "Anglais" to "Good morning", "Arabe" to "صباح الخير", "Espagnol" to "Buenos días"),
        mapOf("Français" to "Bonjour (journée)", "Dioula" to "Ani tilé", "Mooré" to "Nii windga", "Haoussa" to "Ina wuni", "Allemand" to "Guten Tag", "Anglais" to "Hello", "Arabe" to "مرحبا", "Espagnol" to "Hola"),
        // Bonsoir
        mapOf("Français" to "Bonsoir", "Dioula" to "Ani houla", "Mooré" to "Nii zabre", "Haoussa" to "Ina wuni", "Allemand" to "Guten Abend", "Anglais" to "Good evening", "Arabe" to "مساء الخير", "Espagnol" to "Buenas tardes"),
        // Bonne nuit
        mapOf("Français" to "Bonne nuit", "Dioula" to "Ani sou", "Mooré" to "Wind da ganeg laafi", "Haoussa" to "Mu kwana lahiya", "Allemand" to "Gute Nacht", "Anglais" to "Good night", "Arabe" to "تصبح على خير", "Espagnol" to "Buenas noches"),
        // Bonne journée
        mapOf("Français" to "Bonne journée", "Dioula" to "K'an tilé kene", "Mooré" to "Wẽnd na kô-d wĩndg laafi", "Haoussa" to "Barka da yini", "Allemand" to "Schönen Tag noch", "Anglais" to "Have a nice day", "Arabe" to "طاب يومك", "Espagnol" to "Que tenga un buen día"),
        // Merci
        mapOf("Français" to "Merci", "Dioula" to "Anitie", "Mooré" to "Bark wusgo", "Haoussa" to "Nagode", "Allemand" to "Danke", "Anglais" to "Thank you", "Arabe" to "شكراً", "Espagnol" to "Gracias"),
        // Merci beaucoup
        mapOf("Français" to "Merci beaucoup", "Dioula" to "Anitie kossobe", "Mooré" to "Bark wusgo wusgo", "Haoussa" to "Nagode sosai", "Allemand" to "Vielen Dank", "Anglais" to "Thank you very much", "Arabe" to "شكراً جزيلاً", "Espagnol" to "Muchas gracias"),
        // S'il vous plaît / S'il te plaît
        mapOf("Français" to "S'il vous plaît", "Dioula" to "Sabali", "Mooré" to "Tõnd kotame", "Haoussa" to "Don Allah", "Allemand" to "Bitte", "Anglais" to "Please", "Arabe" to "من فضلك", "Espagnol" to "Por favor"),
        mapOf("Français" to "S'il te plaît", "Dioula" to "Sabali", "Mooré" to "M kot-f-laafi", "Haoussa" to "Don Allah", "Allemand" to "Bitte", "Anglais" to "Please", "Arabe" to "من فضلك", "Espagnol" to "Por favor"),
        // Pardon / Excusez-moi
        mapOf("Français" to "Pardon", "Dioula" to "Haketo", "Mooré" to "Sugri", "Haoussa" to "Gafara", "Allemand" to "Entschuldigung", "Anglais" to "Sorry", "Arabe" to "عفواً", "Espagnol" to "Perdón"),
        mapOf("Français" to "Excusez-moi", "Dioula" to "Haketo", "Mooré" to "Kos sugri", "Haoussa" to "Gafara dai", "Allemand" to "Entschuldigen Sie", "Anglais" to "Excuse me", "Arabe" to "معذرة", "Espagnol" to "Disculpe"),
        // Ça va bien / Je vais bien
        mapOf("Français" to "Ça va bien", "Dioula" to "Oum kakene", "Mooré" to "Ya sonma", "Haoussa" to "Lahiya lau", "Allemand" to "Mir geht es gut", "Anglais" to "I am doing well", "Arabe" to "أنا بخير", "Espagnol" to "Estoy bien"),
        mapOf("Français" to "Tout va bien", "Dioula" to "Foyi te", "Mooré" to "Bũmb ka be ye", "Haoussa" to "Babu matsala", "Allemand" to "Alles ist gut", "Anglais" to "Everything is fine", "Arabe" to "كل شيء على ما يرام", "Espagnol" to "Todo está bien"),
        // Comment vas-tu ?
        mapOf("Français" to "Comment vas-tu ?", "Dioula" to "I kakene wa", "Mooré" to "Laafi bame wa", "Haoussa" to "Yaya dai ?", "Allemand" to "Wie geht es dir?", "Anglais" to "How are you?", "Arabe" to "كيف حالك؟", "Espagnol" to "¿Cómo estás?"),
        // Comment allez-vous ?
        mapOf("Français" to "Comment allez-vous ?", "Dioula" to "Aw kakene wa", "Mooré" to "Yãmb laafi bame wa", "Haoussa" to "Yaya kuke ?", "Allemand" to "Wie geht es Ihnen?", "Anglais" to "How are you?", "Arabe" to "كيف حالكم؟", "Espagnol" to "¿Cómo está usted?"),
        // Comment tu t'appelles ?
        mapOf("Français" to "Comment tu t'appelles ?", "Dioula" to "I togo be di ?", "Mooré" to "Fo yʋʋre la bõe ?", "Haoussa" to "Yaya sunanka ?", "Allemand" to "Wie heißt du?", "Anglais" to "What is your name?", "Arabe" to "ما اسمك؟", "Espagnol" to "¿Cómo te llamas?"),
        // Je m'appelle ...
        mapOf("Français" to "Je m'appelle", "Dioula" to "Oum togo be", "Mooré" to "Mam yʋʋre la", "Haoussa" to "Sunana", "Allemand" to "Ich heiße", "Anglais" to "My name is", "Arabe" to "اسمي", "Espagnol" to "Me llamo"),
        // D'où viens-tu ?
        mapOf("Français" to "D'où viens-tu ?", "Dioula" to "I boara mi ?", "Mooré" to "Fo yita yẽ ?", "Haoussa" to "Daga ina kake ?", "Allemand" to "Woher kommst du?", "Anglais" to "Where are you from?", "Arabe" to "من أين أنت؟", "Espagnol" to "¿De dónde eres?"),
        // Où vas-tu ?
        mapOf("Français" to "Où vas-tu ?", "Dioula" to "I bi ta mi ?", "Mooré" to "Fo debda yẽ ?", "Haoussa" to "Ina zaka ?", "Allemand" to "Wohin gehst du?", "Anglais" to "Where are you going?", "Arabe" to "إلى أين أنت ذاهب؟", "Espagnol" to "¿Adónde vas?"),
        // À demain
        mapOf("Français" to "À demain", "Dioula" to "Anbissini", "Mooré" to "Beoogo laafi", "Haoussa" to "Sai gobe", "Allemand" to "Bis morgen", "Anglais" to "See you tomorrow", "Arabe" to "إلى الغد", "Espagnol" to "Hasta mañana"),
        // À bientôt / À plus tard
        mapOf("Français" to "À plus tard", "Dioula" to "Anbedoni", "Mooré" to "Wakat a to", "Haoussa" to "Sai an jima", "Allemand" to "Bis später", "Anglais" to "See you later", "Arabe" to "أراك لاحقاً", "Espagnol" to "Hasta luego"),
        mapOf("Français" to "À bientôt", "Dioula" to "K'an ben sooni", "Mooré" to "Nindaare laafi", "Haoussa" to "Sai an jima", "Allemand" to "Bis bald", "Anglais" to "See you soon", "Arabe" to "إلى اللقاء قريباً", "Espagnol" to "Hasta pronto"),
        // Au revoir
        mapOf("Français" to "Au revoir", "Dioula" to "K'an ben", "Mooré" to "Bilmãse", "Haoussa" to "Sai an jima", "Allemand" to "Auf Wiedersehen", "Anglais" to "Goodbye", "Arabe" to "مع السلامة", "Espagnol" to "Adiós"),
        // Bon voyage
        mapOf("Français" to "Bon voyage", "Dioula" to "Sira diya", "Mooré" to "Sor sõngo", "Haoussa" to "Sauka lafiya", "Allemand" to "Gute Reise", "Anglais" to "Have a safe trip", "Arabe" to "رحلة سعيدة", "Espagnol" to "Buen viaje"),
        // Bienvenue / Bonne arrivée
        mapOf("Français" to "Bienvenue", "Dioula" to "Adanse", "Mooré" to "Ne y waoongo", "Haoussa" to "Barka da zuwa", "Allemand" to "Willkommen", "Anglais" to "Welcome", "Arabe" to "أهلاً وسهلاً", "Espagnol" to "Bienvenido"),
        mapOf("Français" to "Bonne arrivée", "Dioula" to "I dansé", "Mooré" to "Ne y waongo", "Haoussa" to "Barka da zuwa", "Allemand" to "Herzlich willkommen", "Anglais" to "Welcome", "Arabe" to "مرحباً بكم", "Espagnol" to "Bienvenido"),
        // Oui
        mapOf("Français" to "Oui", "Dioula" to "Awo", "Mooré" to "N-ye", "Haoussa" to "I", "Allemand" to "Ja", "Anglais" to "Yes", "Arabe" to "نعم", "Espagnol" to "Sí"),
        // Non
        mapOf("Français" to "Non", "Dioula" to "Ayi", "Mooré" to "Ayo", "Haoussa" to "A'a", "Allemand" to "Nein", "Anglais" to "No", "Arabe" to "لا", "Espagnol" to "No"),
        // Eau
        mapOf("Français" to "Eau", "Dioula" to "Dji", "Mooré" to "Koom", "Haoussa" to "Ruwa", "Allemand" to "Wasser", "Anglais" to "Water", "Arabe" to "ماء", "Espagnol" to "Agua"),
        // Donne-moi de l'eau
        mapOf("Français" to "Donne-moi de l'eau", "Dioula" to "Dji di ma", "Mooré" to "Kõ-m koom", "Haoussa" to "Ba ni ruwa", "Allemand" to "Gib mir Wasser", "Anglais" to "Give me water", "Arabe" to "أعطني ماء", "Espagnol" to "Dame agua"),
        // L'argent / Argent
        mapOf("Français" to "Argent", "Dioula" to "Wari", "Mooré" to "Ligdi", "Haoussa" to "Kuɗi", "Allemand" to "Geld", "Anglais" to "Money", "Arabe" to "مال", "Espagnol" to "Dinero"),
        mapOf("Français" to "L'argent", "Dioula" to "Wari", "Mooré" to "Ligdi", "Haoussa" to "Kuɗin", "Allemand" to "Das Geld", "Anglais" to "The money", "Arabe" to "المال", "Espagnol" to "El dinero"),
        // Combien ça coûte ?
        mapOf("Français" to "Combien ça coûte ?", "Dioula" to "A sɔngɔ ye joli ye ?", "Mooré" to "A yaa ligd a wãna ?", "Haoussa" to "Nawa ne ?", "Allemand" to "Wie viel kostet das?", "Anglais" to "How much does it cost?", "Arabe" to "كم ثمن هذا؟", "Espagnol" to "¿Cuánto cuesta esto?"),
        // C'est cher
        mapOf("Français" to "C'est cher", "Dioula" to "A sɔngɔ ka gɛlɛn", "Mooré" to "A yaa toogo", "Haoussa" to "Yana da tsada", "Allemand" to "Das ist teuer", "Anglais" to "It is expensive", "Arabe" to "هذا غالي", "Espagnol" to "Es caro"),
        // Diminue un peu
        mapOf("Français" to "Diminue un peu", "Dioula" to "Dogo bo a la", "Mooré" to "Bole bila", "Haoussa" to "Rage mani", "Allemand" to "Mach es etwas billiger", "Anglais" to "Lower the price a bit", "Arabe" to "خفض السعر قليلاً", "Espagnol" to "Rebaja un poco"),
        // J'ai faim
        mapOf("Français" to "J'ai faim", "Dioula" to "Congo bi na", "Mooré" to "Koom tara maam", "Haoussa" to "Ina jin yunwa", "Allemand" to "Ich habe Hunger", "Anglais" to "I am hungry", "Arabe" to "أنا جائع", "Espagnol" to "Tengo hambre"),
        // J'ai soif
        mapOf("Français" to "J'ai soif", "Dioula" to "Dji mi nogo bi na", "Mooré" to "Koom nonga maam", "Haoussa" to "Ina jin ƙishirwa", "Allemand" to "Ich habe Durst", "Anglais" to "I am thirsty", "Arabe" to "أنا عطشان", "Espagnol" to "Tengo sed"),
        // Je vais à la maison
        mapOf("Français" to "Je vais à la maison", "Dioula" to "Mi ta so", "Mooré" to "M debda zakin", "Haoussa" to "Ina tafiya gida", "Allemand" to "Ich gehe nach Hause", "Anglais" to "I am going home", "Arabe" to "أنا ذاهب إلى المنزل", "Espagnol" to "Voy a casa"),
        // Je vais au marché
        mapOf("Français" to "Je vais au marché", "Dioula" to "Mi ta dogo la", "Mooré" to "M debda raagẽ", "Haoussa" to "Ina tafiya kasuwa", "Allemand" to "Ich gehe zum Markt", "Anglais" to "I am going to the market", "Arabe" to "أنا ذاهب إلى السوق", "Espagnol" to "Voy al mercado"),
        // Je suis fatigué
        mapOf("Français" to "Je suis fatigué", "Dioula" to "Oum seguena", "Mooré" to "M sãama", "Haoussa" to "Na gaji", "Allemand" to "Ich bin müde", "Anglais" to "I am tired", "Arabe" to "أنا متعب", "Espagnol" to "Estoy cansado"),
        // Je veux dormir
        mapOf("Français" to "Je veux dormir", "Dioula" to "Oum bi ta la", "Mooré" to "M nan gounsam", "Haoussa" to "Ina son barci", "Allemand" to "Ich möchte schlafen", "Anglais" to "I want to sleep", "Arabe" to "أريد أن أنام", "Espagnol" to "Quiero dormir"),
        // Je ne veux pas
        mapOf("Français" to "Je ne veux pas", "Dioula" to "Oum ta fe", "Mooré" to "M pa date ye", "Haoussa" to "Ba na so", "Allemand" to "Ich will nicht", "Anglais" to "I don't want to", "Arabe" to "لا أريد", "Espagnol" to "No quiero"),
        // Je veux
        mapOf("Français" to "Je veux", "Dioula" to "Oum ba fe", "Mooré" to "M data", "Haoussa" to "Ina so", "Allemand" to "Ich will", "Anglais" to "I want", "Arabe" to "أريد", "Espagnol" to "Quiero"),
        // Je ne comprends pas
        mapOf("Français" to "Je ne comprends pas", "Dioula" to "Oum ma famou", "Mooré" to "M pa wʋm ye", "Haoussa" to "Ban gane ba", "Allemand" to "Ich verstehe nicht", "Anglais" to "I don't understand", "Arabe" to "لا أفهم", "Espagnol" to "No entiendo"),
        // Je comprends
        mapOf("Français" to "Je comprends", "Dioula" to "Oum y'a famou", "Mooré" to "M wʋmame", "Haoussa" to "Na gane", "Allemand" to "Ich verstehe", "Anglais" to "I understand", "Arabe" to "أنا أفهم", "Espagnol" to "Entiendo"),
        // Parle lentement
        mapOf("Français" to "Parle lentement", "Dioula" to "Kouma dooni dooni", "Mooré" to "Gom bila bila", "Haoussa" to "Yi magana a hankali", "Allemand" to "Sprich langsam", "Anglais" to "Speak slowly", "Arabe" to "تكلم ببطء", "Espagnol" to "Habla despacio"),
        // Aide-moi
        mapOf("Français" to "Aide-moi", "Dioula" to "Oum deme", "Mooré" to "Song maam", "Haoussa" to "Taimaka mani", "Allemand" to "Hilf mir", "Anglais" to "Help me", "Arabe" to "ساعدني", "Espagnol" to "Ayúdame"),
        // Je t'aime
        mapOf("Français" to "Je t'aime", "Dioula" to "M'bi fe", "Mooré" to "M nonga foo", "Haoussa" to "Ina sonka", "Allemand" to "Ich liebe dich", "Anglais" to "I love you", "Arabe" to "أحبك", "Espagnol" to "Te quiero"),
        // Maison
        mapOf("Français" to "Maison", "Dioula" to "So", "Mooré" to "Zaka", "Haoussa" to "Gida", "Allemand" to "Haus", "Anglais" to "House", "Arabe" to "منزل", "Espagnol" to "Casa"),
        // Femme
        mapOf("Français" to "Femme", "Dioula" to "Mousso", "Mooré" to "Paga", "Haoussa" to "Mace", "Allemand" to "Frau", "Anglais" to "Woman", "Arabe" to "امرأة", "Espagnol" to "Mujer"),
        // Homme
        mapOf("Français" to "Homme", "Dioula" to "Thie", "Mooré" to "Rawa", "Haoussa" to "Namiji", "Allemand" to "Mann", "Anglais" to "Man", "Arabe" to "رجل", "Espagnol" to "Hombre"),
        // Enfant
        mapOf("Français" to "Enfant", "Dioula" to "Dîn", "Mooré" to "Biiga", "Haoussa" to "Yaro", "Allemand" to "Kind", "Anglais" to "Child", "Arabe" to "طفل", "Espagnol" to "Niño"),
        // Dieu est grand
        mapOf("Français" to "Dieu est grand", "Dioula" to "Ala ka bon", "Mooré" to "Wẽnd yaa kãsenga", "Haoussa" to "Allahu akbar", "Allemand" to "Gott ist groß", "Anglais" to "God is great", "Arabe" to "الله أكبر", "Espagnol" to "Dios es grande"),
        // Que Dieu te bénisse
        mapOf("Français" to "Que Dieu te bénisse", "Dioula" to "Ala k'i duba", "Mooré" to "Wẽnd na ning barka", "Haoussa" to "Allah ya yi maka albarka", "Allemand" to "Gott segne dich", "Anglais" to "God bless you", "Arabe" to "بارك الله فيك", "Espagnol" to "Que Dios te bendiga"),
        // S'il plaît à Dieu / Si Dieu le veut
        mapOf("Français" to "Si Dieu le veut", "Dioula" to "N'Ala soun na", "Mooré" to "Wẽnd sã n tũ", "Haoussa" to "In sha Allahu", "Allemand" to "So Gott will", "Anglais" to "God willing", "Arabe" to "إن شاء الله", "Espagnol" to "Si Dios quiere"),
        // Salutations composées
        mapOf("Français" to "Bonjour comment vas-tu ?", "Dioula" to "Ani sogoma, I kakene wa ?", "Mooré" to "Nii yi beogo, Fo laafi ?", "Haoussa" to "Ina kwana, Yaya dai ?", "Allemand" to "Guten Tag, Wie geht es dir?", "Anglais" to "Hello, How are you?"),
        mapOf("Français" to "Bonjour, comment vas-tu ?", "Dioula" to "Ani sogoma, I kakene wa ?", "Mooré" to "Nii yi beogo, Fo laafi ?", "Haoussa" to "Ina kwana, Yaya dai ?", "Allemand" to "Guten Tag, Wie geht es dir?", "Anglais" to "Hello, How are you?"),
        // Termes courants avec caractères spéciaux Haoussa
        mapOf("Français" to "Chambre", "Dioula" to "So", "Mooré" to "Roogo", "Haoussa" to "Ɗaki", "Allemand" to "Zimmer", "Anglais" to "Room"),
        mapOf("Français" to "Porte", "Dioula" to "Da", "Mooré" to "Noore", "Haoussa" to "Ƙofa", "Allemand" to "Tür", "Anglais" to "Door"),
        mapOf("Français" to "Frère", "Dioula" to "Balimake", "Mooré" to "Ba-bi-raoogo", "Haoussa" to "Ɗan'uwa", "Allemand" to "Bruder", "Anglais" to "Brother"),
        mapOf("Français" to "Sœur", "Dioula" to "Balimamuso", "Mooré" to "Ba-bi-põka", "Haoussa" to "Ƴar'uwa", "Allemand" to "Schwester", "Anglais" to "Sister")
    )

    fun tryDirectExpressionMatch(
        input: String,
        sourceLang: String,
        targetLang: String
    ): String? {
        val clean = DictionaryLookupEngine.cleanWhitespace(input).lowercase()
        val stripped = DictionaryLookupEngine.stripPunctuation(clean)
        val normClean = DictionaryLookupEngine.normalizeHausaSpecialChars(stripped).replace("'", "").replace(" ", "")

        // 1. Première passe : Correspondance EXACTE prioritaire
        for (entry in DIRECT_CROSS_EXPRESSIONS) {
            val srcKey = entry.keys.find { it.equals(sourceLang, ignoreCase = true) } ?: continue
            val srcValue = entry[srcKey] ?: continue
            val srcStripped = DictionaryLookupEngine.stripPunctuation(srcValue).lowercase()
            val normSrc = DictionaryLookupEngine.normalizeHausaSpecialChars(srcStripped).replace("'", "").replace(" ", "")

            if (srcValue.equals(clean, ignoreCase = true) || (stripped.isNotBlank() && srcStripped == stripped) || (normClean.length >= 3 && normSrc == normClean)) {
                val tgtKey = entry.keys.find { it.equals(targetLang, ignoreCase = true) }
                if (tgtKey != null) {
                    val tgtVal = entry[tgtKey]
                    if (tgtVal != null) return tgtVal
                }
            }
        }

        // 2. Deuxième passe : Tolérance aux fautes de frappe (Fuzzy match) sur les expressions certifiées
        for (entry in DIRECT_CROSS_EXPRESSIONS) {
            val srcKey = entry.keys.find { it.equals(sourceLang, ignoreCase = true) } ?: continue
            val srcValue = entry[srcKey] ?: continue
            val srcStripped = DictionaryLookupEngine.stripPunctuation(srcValue)

            if (DictionaryLookupEngine.isFuzzyMatch(stripped, srcStripped)) {
                val tgtKey = entry.keys.find { it.equals(targetLang, ignoreCase = true) }
                if (tgtKey != null) {
                    val tgtVal = entry[tgtKey]
                    if (tgtVal != null) return tgtVal
                }
            }
        }

        // 3. Troisième passe : Dictionnaire riche étendu
        val dictMatch = DictionaryLookupEngine.translateDirect(input, sourceLang, targetLang)
        if (dictMatch != null && dictMatch.isNotBlank()) {
            return dictMatch
        }

        return null
    }

    /**
     * Builds AI system instruction ensuring strict adherence to the target language and absolute zero-error translation.
     */
    fun buildSystemInstructionForPair(
        sourceLang: String,
        targetLang: String,
        localContext: String
    ): String {
        return """
        # TRADUCTION DE HAUTE PRÉCISION — SAM TRADUCTION (ZÉRO ERREUR)
        
        Tu es Sam Traduction, le traducteur linguistique officiel certifié d'élite du Burkina Faso et du Sahel.
        
        EXIGENCE CRITIQUE ABSOLUE :
        Tu ne dois JAMAIS faire d'erreur lors des traductions. La fidélité grammaticale, lexicale et contextuelle doit être totale à 100%.
        
        PAIR DE LANGUES :
        - Langue source : $sourceLang
        - Langue cible : $targetLang
        
        RÈGLES D'OR DE LA TRADUCTION :
        1. Tu traduis STRICTEMENT du $sourceLang vers le $targetLang.
        2. La sortie doit être UNIQUEMENT et EXCLUSIVEMENT le texte traduit en $targetLang.
        3. AUCUN mélange de langues toléré.
        4. AUCUN commentaire, explication, note ou formule d'introduction (ne dis jamais "Voici la traduction :" ou "Traduction :").
        5. RÈGLES PAR LANGUE CIBLE :
           - Si cible = Haoussa : Utilise impérativement le **Haoussa courant du Niger** (parlé couramment à Niamey, Maradi, Zinder, Tahoua, Dosso, Tillabéri, Agadez, Diffa). Utilise les expressions courantes nigériennes authentiques du quotidien (ex: 'Yaya dai ?', 'Lahiya lau' / 'Lafiya lau', 'Ina kwana', 'Ina wuni', 'Barka da zuwa', 'Don Allah', 'Nagode sosai' / 'Nagode kwarai', 'Rage mani' / 'Rage kaɗan', 'Ba ni ruwa', 'Ina jin yunwa', 'Ina jin ƙishirwa', 'Ban gane ba', 'Na gane', 'Yi magana a hankali'). Respecte l'orthographe standard avec les consonnes injectives/glottalisées (ɓ, ɗ, ƙ, ƴ), les préfixes d'aspects et les pronoms (Ni/ina, Kai/kake/kana, Ke/kike/kina, Shi/yana, Ita/tana, Mu/muna, Ku/kuna, Su/suna, Ba...ba pour la négation).
           - Si cible = Mooré : Respecte l'orthographe officielle (voyelles nasales ẽ, ĩ, õ, ũ, voyelles ouvertes ɛ, ɩ, ʋ, pronoms mam/m, fo/f, a, tõndo/d, yãmba/y, bãmba/b, négation « pa ... ye »).
           - Si cible = Dioula : Respecte la grammaire officielle du Burkina Faso (pronoms Oum/m/ne, I, A, Aôn/an, Ao/aw, O/olu, aspect bi, bina, ya, négation te, ta fe, connecteurs ni, ka, katugun).
           - Si cible = Allemand : Respecte les majuscules aux substantifs (Namenwörter), la structure des propositions et la précision grammaticale (Akkusativ, Dativ).
           - Si cible = Anglais : Produis un anglais naturel, idiomatique et parfaitement orthographié.
           - Si cible = Français : Produis un français soutenu, fluide et sans faute d'accord ou de syntaxe.
           - Si cible = Arabe : Utilise l'arabe standard moderne (Fusha) avec une orthographe rigoureuse.
           - Si cible = Espagnol : Respecte les accords en genre/nombre et la ponctuation adaptée.
        
        ${if (localContext.isNotBlank()) "LEXIQUE ET CONTEXTE DE RÉFÉRENCE VALIDÉ :\n$localContext\n" else ""}
        6. GESTION DES MOTS INCONNUS OU HORS DICTIONNAIRE :
           - Si la phrase contient des mots qui ne sont pas dans le dictionnaire de base (termes modernes, argot, noms, expressions locales), traduis-les avec une exactitude absolue et insère-les harmonieusement dans la structure grammaticale de $targetLang.
        
        FORMAT DE SORTIE IMPÉRATIF :
        Rends uniquement le texte final traduit, sans guillemets, sans astérisques et sans préfixe.
        """.trimIndent()
    }
}

