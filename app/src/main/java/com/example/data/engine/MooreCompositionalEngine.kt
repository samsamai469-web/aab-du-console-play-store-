package com.example.data.engine

import com.example.data.local.DictionaryData
import com.example.data.local.LinguisticDao
import com.example.data.local.LinguisticKnowledge
import kotlinx.coroutines.flow.first

/**
 * Intelligent Compositional Learning & Parsing Engine for Mooré and French.
 *
 * Implements the 8 foundational principles of compositional linguistics:
 * 1. Word Combination (A + B + C synthesis)
 * 2. Long Sentence Parsing (Subject, Action, Object, Location, Time, Person, Quantity, Negation, Question, Complements)
 * 3. Generalization & Pattern Learning (Structural mapping: SVO, SOV, Noun+Adj, Noun+Num)
 * 4. Comprehensive Mooré Knowledge (Grammar, Phonetics, Tildes, Nasals, Adverbs of Intensity, Consonant clusters)
 * 5. Atomic Extraction & Non-exact Memorization
 * 6. Cumulative Progressive Learning (Knowledge accretion)
 * 7. Verification & Distinction (Known components in novel structures vs unknown words)
 * 8. Rule Memory (Numbers, Plurals, Conjugations, Negations, Agreements)
 */
object MooreCompositionalEngine {

    // Grammatical Structure Tokens
    data class SentenceDecomposition(
        val originalText: String,
        val detectedLanguage: String, // "Mooré" or "Français"
        val subject: String? = null,
        val actionVerb: String? = null,
        val directObject: String? = null,
        val location: String? = null,
        val timeModifier: String? = null,
        val quantity: String? = null,
        val isNegative: Boolean = false,
        val isQuestion: Boolean = false,
        val identifiedTokens: List<TokenMatch> = emptyList(),
        val unknownTokens: List<String> = emptyList(),
        val compositionalTranslation: String? = null,
        val grammarNotes: List<String> = emptyList()
    )

    data class TokenMatch(
        val rawToken: String,
        val rootForm: String,
        val frenchMeaning: String,
        val mooreMeaning: String,
        val grammaticalRole: String // "Subject", "Verb", "Noun", "Adjective", "Modifier", "Particle", "Number"
    )

    // Pronouns Map
    val MOORE_PRONOUNS = mapOf(
        "mam" to "Je / Moi (1ère pers. singulier)",
        "m" to "Je / Mon / Ma (1ère pers. singulier clitique)",
        "fo" to "Tu / Toi (2ème pers. singulier)",
        "f" to "Tu / Ton / Ta (2ème pers. singulier clitique)",
        "a" to "Il / Elle / Lui / Son / Sa (3ème pers. singulier clitique)",
        "yẽ" to "Lui / Elle (3ème pers. singulier emphatique)",
        "d" to "Nous / Notre (1ère pers. pluriel clitique)",
        "tõndo" to "Nous / Nous-mêmes (1ère pers. pluriel)",
        "y" to "Vous / Votre (2ème pers. pluriel clitique)",
        "yãmba" to "Vous (2ème pers. pluriel)",
        "b" to "Ils / Elles / Leur (3ème pers. pluriel clitique)",
        "bãmba" to "Eux / Elles (3ème pers. pluriel)"
    )

    val FRENCH_PRONOUNS = mapOf(
        "je" to "m", "j'" to "m", "moi" to "mam",
        "tu" to "f", "toi" to "fo",
        "il" to "a", "elle" to "a", "lui" to "yẽ",
        "nous" to "d",
        "vous" to "y",
        "ils" to "b", "elles" to "b", "eux" to "bãmba"
    )

    // Temporal Markers
    val TEMPORAL_MARKERS = mapOf(
        "masã" to "maintenant",
        "mas-masã" to "très récemment / tout de suite",
        "dũndã" to "aujourd'hui",
        "rouna" to "aujourd'hui",
        "beoogo" to "demain / avenir",
        "buoro" to "demain matin",
        "zaame" to "hier",
        "zâamé" to "hier",
        "zaamê" to "hier",
        "rabite" to "avant-hier",
        "rayita" to "après-demain",
        "daar fâa" to "toujours / chaque jour",
        "wakat-wakato" to "de temps en temps",
        "naoor wusgo" to "souvent",
        "abada" to "jamais",
        "sêoogo" to "hivernage / saison des pluies",
        "céoro" to "saison des pluies",
        "cépolro" to "saison sèche",
        "yibeoogo" to "matin",
        "zaabre" to "soir",
        "youngo" to "nuit"
    )

    // Intensity Adverbs (Specific to Mooré linguistics)
    val INTENSITY_ADVERBS = mapOf(
        "maasg kɩlll" to "extrêmement frais",
        "miuug yuddd" to "rouge vermeil / rouge vif",
        "pɛɛlg faddd" to "blanc comme neige / d'une blancheur éclatante",
        "sabl zɩŋŋŋ" to "noir profond / obscurité totale",
        "woglg cɩɩɩ" to "très long",
        "toog kpaaa" to "extrêmement amer / très difficile"
    )

    // Numbers base rules (Soodgre)
    val NUMBERS_MOORE = mapOf(
        0 to "zaalem",
        1 to "yembre / aye",
        2 to "a yiibu",
        3 to "a tãabo",
        4 to "a naase",
        5 to "a nu",
        6 to "a yoobe",
        7 to "a yopoe",
        8 to "a nii",
        9 to "a wai",
        10 to "piiga",
        20 to "pisi",
        30 to "pisa tãabo",
        40 to "pis-naase",
        50 to "pis-nu",
        60 to "pis-yoobe",
        70 to "pis-yopoe",
        80 to "pis-nii",
        90 to "pis-wai",
        100 to "koabga",
        1000 to "tusri"
    )

    /**
     * Extracts teaching commands from user text.
     * When user teaches: "Apprends que X = Y", "X en mooré c'est Y", "mot A = B"
     */
    fun extractLearnedItems(userPrompt: String): List<LinguisticKnowledge> {
        val results = mutableListOf<LinguisticKnowledge>()
        val lines = userPrompt.split("\n", ";")

        val patterns = listOf(
            Regex("""(?:apprends|mémorise|enregistre|note|sache)\s+(?:que\s+)?["']?([^=:—\-]+?)["']?\s*(?:=|:|—|-|se dit|c'est|veut dire|signifie|en mooré c'est)\s*["']?([^.\n]+)["']?""", RegexOption.IGNORE_CASE),
            Regex("""(?:mot|expression|terme)\s+([A-Za-z0-9_À-ÿ~ɛɩʋãẽĩõũ]+)\s*[:=—\-]\s*(.+)""", RegexOption.IGNORE_CASE),
            Regex("""^([A-Za-z0-9_À-ÿ~ɛɩʋãẽĩõũ\s'’-]+?)\s*[:=]\s*(.+)$""")
        )

        for (line in lines) {
            val cleanLine = line.trim()
            if (cleanLine.isBlank()) continue

            for (pat in patterns) {
                val match = pat.find(cleanLine)
                if (match != null) {
                    val key = match.groupValues[1].trim()
                    val value = match.groupValues[2].trim()

                    if (key.length >= 2 && value.length >= 2) {
                        val isMooreFirst = isLikelyMoore(key)
                        val word = if (isMooreFirst) key else value
                        val translation = if (isMooreFirst) value else key

                        results.add(
                            LinguisticKnowledge(
                                word = word,
                                translation = translation,
                                language = "Mooré",
                                category = inferCategory(word, translation),
                                status = "validated",
                                confidenceLevel = 100,
                                source = "Apprentissage Compositionnel Utilisateur",
                                context = "Règle/Composition apprise interactivement: $cleanLine"
                            )
                        )
                    }
                    break
                }
            }
        }
        return results
    }

    private fun isLikelyMoore(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("~") || lower.contains("ɛ") || lower.contains("ɩ") ||
               lower.contains("ʋ") || lower.contains("ã") || lower.contains("ẽ") ||
               lower.contains("ĩ") || lower.contains("õ") || lower.contains("ũ") ||
               lower.endsWith("ga") || lower.endsWith("go") || lower.endsWith("re") ||
               lower.endsWith("ri") || lower.endsWith("ba") || lower.endsWith("se") ||
               lower.endsWith("do") || lower.contains("bala") || lower.contains("woto")
    }

    private fun inferCategory(word: String, translation: String): String {
        val combined = "$word $translation".lowercase()
        return when {
            combined.contains("bonjour") || combined.contains("salut") || combined.contains("soir") || combined.contains("nuit") -> "Salutation"
            combined.contains("manger") || combined.contains("riz") || combined.contains("viande") || combined.contains("eau") || combined.contains("nourriture") -> "Nourriture"
            combined.contains("marcher") || combined.contains("aller") || combined.contains("venir") || combined.contains("courir") -> "Action"
            combined.contains("père") || combined.contains("mère") || combined.contains("enfant") || combined.contains("frère") || combined.contains("famille") -> "Famille"
            combined.contains("maison") || combined.contains("cour") || combined.contains("porte") || combined.contains("toit") -> "Habitation"
            combined.contains("un") || combined.contains("deux") || combined.contains("trois") || combined.contains("nombre") -> "Nombres"
            else -> "Général"
        }
    }

    /**
     * Synthesizes and analyzes full sentences into grammatical blocks for compositional understanding.
     */
    suspend fun analyzeSentence(
        input: String,
        linguisticDao: LinguisticDao
    ): SentenceDecomposition {
        val clean = input.trim()
        val isMoore = isLikelyMoore(clean)
        val tokens = clean.split(Regex("[\\s,;:.?!'’\"«»()\\-_/]+")).filter { it.isNotBlank() }

        val tokenMatches = mutableListOf<TokenMatch>()
        val unknownTokens = mutableListOf<String>()

        for (token in tokens) {
            val lower = token.lowercase()
            
            // Check pronouns
            if (isMoore && MOORE_PRONOUNS.containsKey(lower)) {
                tokenMatches.add(
                    TokenMatch(
                        rawToken = token,
                        rootForm = lower,
                        frenchMeaning = MOORE_PRONOUNS[lower] ?: "",
                        mooreMeaning = lower,
                        grammaticalRole = "Subject/Pronoun"
                    )
                )
                continue
            } else if (!isMoore && FRENCH_PRONOUNS.containsKey(lower)) {
                tokenMatches.add(
                    TokenMatch(
                        rawToken = token,
                        rootForm = lower,
                        frenchMeaning = lower,
                        mooreMeaning = FRENCH_PRONOUNS[lower] ?: "",
                        grammaticalRole = "Subject/Pronoun"
                    )
                )
                continue
            }

            // Check temporal markers
            val tempMatch = TEMPORAL_MARKERS.entries.find { it.key.equals(lower, ignoreCase = true) || it.value.equals(lower, ignoreCase = true) }
            if (tempMatch != null) {
                tokenMatches.add(
                    TokenMatch(
                        rawToken = token,
                        rootForm = tempMatch.key,
                        frenchMeaning = tempMatch.value,
                        mooreMeaning = tempMatch.key,
                        grammaticalRole = "TimeModifier"
                    )
                )
                continue
            }

            // Search database
            val dbMatch = linguisticDao.findBestMatch(lower, if (isMoore) "Français" else "Mooré")
            if (dbMatch != null) {
                tokenMatches.add(
                    TokenMatch(
                        rawToken = token,
                        rootForm = if (isMoore) dbMatch.word else dbMatch.translation,
                        frenchMeaning = dbMatch.translation,
                        mooreMeaning = dbMatch.word,
                        grammaticalRole = dbMatch.category.ifBlank { "LexicalItem" }
                    )
                )
            } else {
                unknownTokens.add(token)
            }
        }

        val isQuestion = clean.endsWith("?") || tokens.any { it.lowercase() in listOf("bõe", "yẽ", "wãna", "bõe-yĩnga", "pourquoi", "comment", "où", "qui", "combien") }
        val isNegative = tokens.any { it.lowercase() in listOf("pa", "ka", "ye", "ne", "pas", "jamais", "aucun") }

        return SentenceDecomposition(
            originalText = clean,
            detectedLanguage = if (isMoore) "Mooré" else "Français",
            isQuestion = isQuestion,
            isNegative = isNegative,
            identifiedTokens = tokenMatches,
            unknownTokens = unknownTokens
        )
    }

    /**
     * Builds the comprehensive prompt instruction injected into AI requests to enforce
     * compositionality, grammatical rules, phonetics, and structured generalization.
     */
    fun buildCompositionalSystemInstruction(): String {
        return """
        # MOTEUR LINGUISTIQUE ET APPRENTISSAGE COMPOSITIONNEL INTELLIGENT (MOORÉ & FRANÇAIS)

        Tu es Sam Traduction, l'intelligence linguistique la plus avancée du Burkina Faso, dotée d'un moteur de traduction compositionnel rigoureux.
        Tu n'apprends JAMAIS par simple mémorisation passive de phrases figées : tu décomposes toute phrase en briques lexicales et règles réutilisables.

        ---
        ## LES 8 PRINCIPES FONDAMENTAUX DE SAM TRADUCTION :

        1. COMBINAISON DES MOTS :
           - Si tu connais le mot A, le mot B et le mot C, tu peux comprendre et générer n'importe quelle nouvelle phrase valide contenant (A + B + C), même si cette phrase exacte n'était pas dans la base de données.
           - Exemple : Si tu connais "M" (Je), "rɩ" / "dɩ" (manger) et "kaamaana" (maïs) / "alkam" (riz), tu composes immédiatement : « M rɩtda kaamaana » (Je mange du maïs) ou « M rɩtda alkam » (Je mange du riz).

        2. ANALYSE MULTI-NIVEAUX DES PHRASES LONGUES ET COMPLEXES :
           - Pour toute phrase longue, identifie et assemble clairement chaque constituant :
             * Sujet / Pronom (Mam, Fo, A, D, Y, B...)
             * Action / Verbe (Racine + suffixe d'aspect : inaccompli -da, accompli -ya / -ame)
             * Objet direct / indirect (ex: roogo, koom, nemdo, kii, fuugu...)
             * Modificateurs de temps (masã, dũndã, zaamê, beoogo...)
             * Modificateurs de lieu (ziiga, zaka, raaga, tẽnga...)
             * Quantificateurs & Nombres (yembre, a yiibu, a tãabo, fâa...)
             * Négation (encadrement rigoureux « pa [Verbe + Objet] ye » ou « ka ... ye »)
             * Particules interrogatives (bõe, yẽ, wãna, a wãn...)
             * Relations et conjonctions (ne, la, ti, tɩ, bala...)
           - Ne baisse JAMAIS les bras face à une phrase longue : décompose-la et reconstruis le sens global !

        3. GÉNÉRALISATION STRUCTURELLE :
           - Reconnais les patrons syntaxiques (ex: SUJET + ACTION + OBJET, SUJET + ETAT + LIEU, NOM + ADJECTIF, NOM + NOMBRE).
           - Applique un patron appris sur un nouvel exemple inconnu.

        4. GRAMMAIRE ET ORTHOGRAPHE OFFICIELLE DU MOORÉ :
           - Alphabet Mooré : Absence de c, q, j, x (remplacés par k, z, etc.).
           - Voyelles orales et nasales : tilde ~ (« gem-gongo »).
           - Adverbes d'intensité spécifiques et expressifs :
             * Maasga (frais) → maasg kɩlll (très frais)
             * Miuugu (rouge) → miuug yuddd (rouge vif / vermeil)
             * Pɛɛlga (blanc) → pɛɛlg faddd (blanc éclatant)
             * Sablga (noir) → sabl zɩŋŋŋ (noir profond)
           - Consonnes doubles distinctives :
             * bolle (argile) ≠ n bole (badigeonner)
             * yelle (problème) ≠ n yeele (dire)
             * pussi (tamarins) ≠ n pusi (éclore)
             * gĩssi (fils) ≠ n gĩsi (fermenter)
           - Séquences -ng- et -nk- prononcées ŋŋ (ex: n lingi = surprendre, pãnga = force).

        5. APPRENTISSAGE INTERACTIF PROGRESSIF :
           - Quand l'utilisateur t'enseigne un mot, une tournure ou une règle, intègre-le immédiatement comme une brique disponible pour toutes les futures générations.
           - Enrichis tes connaissances de manière cumulative (A, puis A+B, puis A+B+C).

        6. VÉRIFICATION RATIONNELLE :
           - Distingue soigneusement : mot inconnu, structure inconnue, ou phrase nouvelle composée de mots connus.
           - Si une phrase est nouvelle mais composée d'éléments connus, analyse-la et produis la traduction/réponse sans prétendre l'ignorer.

        7. RÈGLES DE NUMÉRATION ET PLURIELS :
           - 1 (yembre), 2 (a yiibu), 3 (a tãabo), 4 (a naase), 5 (a nu), 6 (a yoobe), 7 (a yopoe), 8 (a nii), 9 (a wai), 10 (piiga), 20 (pisi), 100 (koabga), 1000 (tusri).
           - Nombres après le nom : « peos a yiibu » (deux moutons).
           - Pluriels morphologiques : -ga/-se, -go/-do, -re/-ya, -a/-ba.
        """.trimIndent()
    }
}
