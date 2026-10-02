package com.example.data.local

object DioulaKnowledgeData {
    val BASE_UNITS = mapOf(
        0 to "Fu",
        1 to "Kelen",
        2 to "Fila",
        3 to "Saba",
        4 to "Naani",
        5 to "Duuru",
        6 to "Wɔɔrɔ",
        7 to "Wolonwula",
        8 to "Seegin",
        9 to "Kɔnɔntɔn"
    )

    val BASE_TENS = mapOf(
        10 to "Tan",
        20 to "Mugan",
        30 to "Bi saba",
        40 to "Bi naani",
        50 to "Bi duuru",
        60 to "Bi wɔɔrɔ",
        70 to "Bi wolonwula",
        80 to "Bi seegin",
        90 to "Bi kɔnɔntɔn"
    )

    const val HUNDRED_ROOT = "Kɛmɛ"
    const val THOUSAND_ROOT = "Ba"

    fun seedDioulaDatabase(): List<LinguisticKnowledge> {
        return listOf(
            LinguisticKnowledge(
                word = "I ni sogoma",
                translation = "Bonjour (le matin)",
                language = "Dioula",
                variants = "Inisogoma, Anisogoma",
                synonyms = "Kelen",
                category = "Salutations",
                status = "validated",
                confidenceLevel = 100,
                difficulty = "beginner",
                level = 1,
                pronunciation = "I ni so-go-ma",
                source = "Corpus Officiel Dioula Sam AI"
            ),
            LinguisticKnowledge(
                word = "I ni tilen",
                translation = "Bon après-midi",
                language = "Dioula",
                variants = "Initilen",
                synonyms = "",
                category = "Salutations",
                status = "validated",
                confidenceLevel = 100,
                difficulty = "beginner",
                level = 1,
                pronunciation = "I ni ti-len",
                source = "Corpus Officiel Dioula Sam AI"
            ),
            LinguisticKnowledge(
                word = "I ni su",
                translation = "Bonsoir / Bonne nuit",
                language = "Dioula",
                variants = "Inisu",
                synonyms = "",
                category = "Salutations",
                status = "validated",
                confidenceLevel = 100,
                difficulty = "beginner",
                level = 1,
                pronunciation = "I ni su",
                source = "Corpus Officiel Dioula Sam AI"
            ),
            LinguisticKnowledge(
                word = "I ni ce",
                translation = "Merci / Bravo pour le travail",
                language = "Dioula",
                variants = "I ni tche, Anitche",
                synonyms = "Barika",
                category = "Salutations",
                status = "validated",
                confidenceLevel = 100,
                difficulty = "beginner",
                level = 1,
                pronunciation = "I ni tché",
                source = "Corpus Officiel Dioula Sam AI"
            ),
            LinguisticKnowledge(
                word = "I ka kene wa ?",
                translation = "Comment vas-tu ? (Es-tu en bonne santé ?)",
                language = "Dioula",
                variants = "Ikakene",
                synonyms = "Somogow be dji ?",
                category = "Salutations",
                status = "validated",
                confidenceLevel = 100,
                difficulty = "beginner",
                level = 1,
                pronunciation = "I ka ké-né wa",
                source = "Corpus Officiel Dioula Sam AI"
            ),
            LinguisticKnowledge(
                word = "Toro t'i la",
                translation = "Ça va bien (pas de problème)",
                language = "Dioula",
                variants = "Torotila",
                synonyms = "Kene don",
                category = "Salutations",
                status = "validated",
                confidenceLevel = 100,
                difficulty = "beginner",
                level = 1,
                pronunciation = "To-ro ti la",
                source = "Corpus Officiel Dioula Sam AI"
            ),
            LinguisticKnowledge(
                word = "Ji",
                translation = "Eau",
                language = "Dioula",
                variants = "Dji",
                synonyms = "",
                category = "Nourriture",
                status = "validated",
                confidenceLevel = 100,
                difficulty = "beginner",
                level = 1,
                pronunciation = "Dji",
                source = "Corpus Officiel Dioula Sam AI"
            ),
            LinguisticKnowledge(
                word = "Dumuni",
                translation = "Nourriture / Repas",
                language = "Dioula",
                variants = "Domuni",
                synonyms = "",
                category = "Nourriture",
                status = "validated",
                confidenceLevel = 100,
                difficulty = "beginner",
                level = 1,
                pronunciation = "Dou-mou-ni",
                source = "Corpus Officiel Dioula Sam AI"
            ),
            LinguisticKnowledge(
                word = "Malo",
                translation = "Riz",
                language = "Dioula",
                variants = "",
                synonyms = "",
                category = "Nourriture",
                status = "validated",
                confidenceLevel = 100,
                difficulty = "beginner",
                level = 1,
                pronunciation = "Ma-lo",
                source = "Corpus Officiel Dioula Sam AI"
            ),
            LinguisticKnowledge(
                word = "So",
                translation = "Maison",
                language = "Dioula",
                variants = "",
                synonyms = "Lu",
                category = "Famille",
                status = "validated",
                confidenceLevel = 100,
                difficulty = "beginner",
                level = 1,
                pronunciation = "So",
                source = "Corpus Officiel Dioula Sam AI"
            ),
            LinguisticKnowledge(
                word = "Ba",
                translation = "Mère",
                language = "Dioula",
                variants = "Bamo",
                synonyms = "",
                category = "Famille",
                status = "validated",
                confidenceLevel = 100,
                difficulty = "beginner",
                level = 1,
                pronunciation = "Ba",
                source = "Corpus Officiel Dioula Sam AI"
            ),
            LinguisticKnowledge(
                word = "Fa",
                translation = "Père",
                language = "Dioula",
                variants = "Faba",
                synonyms = "",
                category = "Famille",
                status = "validated",
                confidenceLevel = 100,
                difficulty = "beginner",
                level = 1,
                pronunciation = "Fa",
                source = "Corpus Officiel Dioula Sam AI"
            ),
            LinguisticKnowledge(
                word = "Den",
                translation = "Enfant",
                language = "Dioula",
                variants = "Denmisɛn",
                synonyms = "",
                category = "Famille",
                status = "validated",
                confidenceLevel = 100,
                difficulty = "beginner",
                level = 1,
                pronunciation = "Den",
                source = "Corpus Officiel Dioula Sam AI"
            ),
            LinguisticKnowledge(
                word = "Sugu",
                translation = "Marché",
                language = "Dioula",
                variants = "Sougou",
                synonyms = "",
                category = "Commerce",
                status = "validated",
                confidenceLevel = 100,
                difficulty = "beginner",
                level = 1,
                pronunciation = "Sou-gou",
                source = "Corpus Officiel Dioula Sam AI"
            ),
            LinguisticKnowledge(
                word = "Wari",
                translation = "Argent",
                language = "Dioula",
                variants = "",
                synonyms = "",
                category = "Commerce",
                status = "validated",
                confidenceLevel = 100,
                difficulty = "beginner",
                level = 1,
                pronunciation = "Wa-ri",
                source = "Corpus Officiel Dioula Sam AI"
            ),
            LinguisticKnowledge(
                word = "Joli don ?",
                translation = "C'est combien ?",
                language = "Dioula",
                variants = "Djelidon",
                synonyms = "",
                category = "Commerce",
                status = "validated",
                confidenceLevel = 100,
                difficulty = "beginner",
                level = 1,
                pronunciation = "Djo-li don",
                source = "Corpus Officiel Dioula Sam AI"
            )
        )
    }
}
