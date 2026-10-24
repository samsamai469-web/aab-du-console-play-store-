package com.example.data.engine

import com.example.data.local.DictionaryData
import com.example.data.local.DioulaKnowledgeData
import com.example.data.local.HausaKnowledgeData
import java.text.Normalizer

data class UnifiedDictionaryEntry(
    val id: String,
    val word: String, // Mot en langue locale
    val french: String, // Traduction en français
    val language: String, // "Mooré", "Dioula", "Haoussa"
    val category: String, // "Salutation", "Politesse", "Commerce", etc.
    val pronunciation: String = "",
    val variants: String = ""
)

data class LocalSentenceAssembly(
    val translatedText: String,
    val breakdown: List<String>,
    val matchedCount: Int,
    val totalSegments: Int
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

    fun normalizeAccents(input: String): String {
        val temp = Normalizer.normalize(input.trim().lowercase(), Normalizer.Form.NFD)
        return temp.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace("ɛ", "e")
            .replace("ɩ", "i")
            .replace("ʋ", "u")
            .replace("ɔ", "o")
            .replace("ɲ", "ny")
            .replace("ŋ", "ng")
            .replace("’", "'")
    }

    fun cleanWhitespace(input: String): String {
        return input.trim().replace("’", "'").replace(Regex("\\s+"), " ")
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

    private fun registerFrenchKey(map: MutableMap<String, String>, rawKey: String, targetValue: String, overwrite: Boolean = false) {
        val cleanedNoParen = rawKey.replace(Regex("\\([^)]*\\)"), " ").trim()
        val parts = cleanedNoParen.split(",", "/", ";").map { it.trim() }.filter { it.isNotEmpty() }
        for (part in parts + listOf(cleanedNoParen, rawKey)) {
            val k = stripPunctuation(part).lowercase().replace(Regex("\\s+"), " ").trim()
            if (k.isNotEmpty() && (overwrite || !map.containsKey(k))) {
                map[k] = targetValue
            }
            val normK = normalizeAccents(k)
            if (normK.isNotEmpty() && (overwrite || !map.containsKey(normK))) {
                map[normK] = targetValue
            }
            // Enregistrer également la forme avec apostrophe éclatée ("j'ai" -> "je ai", "l'école" -> "l ecole", "d'argent" -> "de argent")
            val expandedPart = part.lowercase().replace("’", "'")
                .replace(Regex("\\bj'"), "je ")
                .replace(Regex("\\bn'"), "ne ")
                .replace(Regex("\\bm'"), "me ")
                .replace(Regex("\\bt'"), "te ")
                .replace(Regex("\\bs'"), "se ")
                .replace(Regex("\\bqu'"), "que ")
                .replace(Regex("\\bl'"), "l ")
                .replace(Regex("\\bd'"), "de ")
            val expK = stripPunctuation(expandedPart).lowercase().replace(Regex("\\s+"), " ").trim()
            if (expK.isNotEmpty() && (overwrite || !map.containsKey(expK))) {
                map[expK] = targetValue
            }
            val normExpK = normalizeAccents(expK)
            if (normExpK.isNotEmpty() && (overwrite || !map.containsKey(normExpK))) {
                map[normExpK] = targetValue
            }
        }
    }

    // --- DICTIONNAIRES EN MÉMOIRE POUR ACCÈS O(1) RAPIDE ET SÛR ---

    private val frenchToMooreMap by lazy {
        val map = mutableMapOf<String, String>()

        // 1. Entrées de référence officielles prioritaires
        DictionaryData.AUTHORITATIVE_REFERENCE_ENTRIES.forEach { entry ->
            registerFrenchKey(map, entry.french, entry.moore, overwrite = true)
            if (entry.synonyms.isNotEmpty()) {
                entry.synonyms.split(",", ";").forEach { syn ->
                    registerFrenchKey(map, syn, entry.moore, overwrite = true)
                }
            }
        }

        // 2. Expressions conversationnelles et vocabulaire de base certifié
        val common = mapOf(
            "salut" to "Nii yi beogo",
            "bonjour" to "Nii yi beogo",
            "bonjour mon frere" to "Nii yi beogo m ba-bi-raoogo",
            "bonjour mon frère" to "Nii yi beogo m ba-bi-raoogo",
            "bonjour ma soeur" to "Nii yi beogo m ba-bi-põka",
            "bonjour ma sœur" to "Nii yi beogo m ba-bi-põka",
            "bonjour mon ami" to "Nii yi beogo m zoodo",
            "bonjour papa" to "Nii yi beogo m baa",
            "bonjour maman" to "Nii yi beogo m ma",
            "bonsoir" to "Nii zabre",
            "bonsoir mon frere" to "Nii zabre m ba-bi-raoogo",
            "bonsoir mon frère" to "Nii zabre m ba-bi-raoogo",
            "bonsoir ma soeur" to "Nii zabre m ba-bi-põka",
            "bonsoir ma sœur" to "Nii zabre m ba-bi-põka",
            "bonsoir mon ami" to "Nii zabre m zoodo",
            "bonne nuit" to "Wind da ganeg laafi",
            "ca va" to "Laafi bala",
            "ça va" to "Laafi bala",
            "tu vas bien" to "Fo laafi ?",
            "comment vas tu" to "Fo laafi ?",
            "comment vas-tu" to "Fo laafi ?",
            "comment tu vas" to "Fo laafi ?",
            "comment ca va" to "Fo laafi ?",
            "comment ça va" to "Fo laafi ?",
            "comment va la famille" to "Zak ramba laafi ?",
            "les enfants vont bien" to "Kamb laafi ?",
            "comment allez vous" to "Yãmb laafi bame wa ?",
            "comment allez-vous" to "Yãmb laafi bame wa ?",
            "et toi" to "La fõ ?",
            "merci" to "Barka",
            "merci beaucoup" to "Bark wusgo wusgo",
            "de rien" to "Barka ka be ye",
            "sil te plait" to "Tõnd kotame",
            "s'il te plaît" to "Tõnd kotame",
            "sil vous plait" to "Tõnd kotame",
            "s'il vous plaît" to "Tõnd kotame",
            "pardon" to "Sugri",
            "excuse moi" to "Sugri",
            "excuse-moi" to "Sugri",
            "je suis desole" to "M kosa sugri",
            "je suis désolé" to "M kosa sugri",
            "oui" to "N-ye",
            "non" to "Ayo",
            "daccord" to "A zemsame",
            "d'accord" to "A zemsame",
            "c'est bon" to "A yaa sõma",
            "cest bon" to "A yaa sõma",
            "ce n'est pas bon" to "A pa sõma ye",
            "ce nest pas bon" to "A pa sõma ye",
            "c'est vrai" to "Yaa sɩda",
            "cest vrai" to "Yaa sɩda",
            "ce n'est pas vrai" to "Pa sɩda ye",
            "ce nest pas vrai" to "Pa sɩda ye",
            "je vais bien" to "Mam laafi bala",
            "tout va bien" to "Bũmb ka be ye",
            "au revoir" to "Bilmãse",
            "a demain" to "Beoogo laafi",
            "à demain" to "Beoogo laafi",
            "a bientot" to "Nindaare laafi",
            "à bientôt" to "Nindaare laafi",
            "a plus tard" to "Wakat a to",
            "à plus tard" to "Wakat a to",
            "a tout a lheure" to "Wakat bila",
            "à tout à l'heure" to "Wakat bila",
            "bonne arrivee" to "Ne y waoongo",
            "bonne arrivée" to "Ne y waoongo",
            "bienvenue" to "Ne y waoongo",
            "bonne journee" to "Dahare sõngo",
            "bonne journée" to "Dahare sõngo",
            "bonne soiree" to "Zaabr sõngo",
            "bonne soirée" to "Zaabr sõngo",
            "bon voyage" to "Sor sõngo",
            "que dieu te benisse" to "Wẽnd na ning barka",
            "que dieu te bénisse" to "Wẽnd na ning barka",
            "si dieu le veut" to "Wẽnd sã n tũ",
            "inchallah" to "Wẽnd sã n tũ",
            "dieu merci" to "Barka wẽnde",
            "prends soin de toi" to "Gũus f menga",
            "qui est la" to "Anan be ka ?",
            "qui est là" to "Anan be ka ?",
            "c'est moi" to "Yaa maam",
            "cest moi" to "Yaa maam",
            "entre" to "Kẽ-y ka",
            "entrez" to "Kẽ-y ka",
            "viens ici" to "Wa ka",
            "assieds toi" to "Zĩnd ka",
            "assieds-toi" to "Zĩnd ka",
            "attends moi" to "Gũ-m",
            "attends-moi" to "Gũ-m",
            "je tattends" to "M gũuda foo",
            "je t'attends" to "M gũuda foo",
            "je viens" to "M watame",
            "je suis en route" to "M be sore",
            "je suis arrive" to "M taame",
            "je suis arrivé" to "M taame",
            "je pars" to "M loogda",
            "tu fais quoi" to "Fo maanda bõe ?",
            "que fais-tu" to "Fo maanda bõe ?",
            "que fais tu" to "Fo maanda bõe ?",
            "tu es ou" to "Fo be yẽ ?",
            "tu es où" to "Fo be yẽ ?",
            "ou es tu" to "Fo be yẽ ?",
            "où es-tu" to "Fo be yẽ ?",
            "ou vas tu" to "Fo debda yẽ ?",
            "où vas-tu" to "Fo debda yẽ ?",
            "tu vas ou" to "Fo debda yẽ ?",
            "tu vas où" to "Fo debda yẽ ?",
            "tu viens dou" to "Fo yita yẽ ?",
            "tu viens d'où" to "Fo yita yẽ ?",
            "d'où viens-tu" to "Fo yita yẽ ?",
            "je viens de la maison" to "M yita zakin",
            "je vais au marche" to "M debda raagẽ",
            "je vais au marché" to "M debda raagẽ",
            "je vais au travail" to "M debda tʋʋmde",
            "je vais a la maison" to "M debda zakin",
            "je vais à la maison" to "M debda zakin",
            "je me repose" to "M vʋʋsda",
            "jai faim" to "Koom tara maam",
            "j'ai faim" to "Koom tara maam",
            "jai soif" to "Koom nonga maam",
            "j'ai soif" to "Koom nonga maam",
            "viens manger" to "Wa rɩ",
            "donne moi a manger" to "Kõ-m rɩɩbo",
            "donne-moi à manger" to "Kõ-m rɩɩbo",
            "donne moi de leau" to "Kõ-m koom",
            "donne-moi de l'eau" to "Kõ-m koom",
            "donne moi de largent" to "Kõ-m ligdi",
            "donne-moi de l'argent" to "Kõ-m ligdi",
            "donne moi ca" to "Kõ-m dẽ",
            "donne-moi ça" to "Kõ-m dẽ",
            "prends ca" to "Rɩk dẽ",
            "prends ça" to "Rɩk dẽ",
            "je veux largent" to "M data ligdi",
            "je veux l'argent" to "M data ligdi",
            "je nai pas dargent" to "M ka ligd ye",
            "je n'ai pas d'argent" to "M ka ligd ye",
            "jai de largent" to "M tara ligdi",
            "j'ai de l'argent" to "M tara ligdi",
            "combien ca coute" to "A yaa ligd a wãna ?",
            "combien ça coûte" to "A yaa ligd a wãna ?",
            "cest combien" to "A yaa ligd a wãna ?",
            "c'est combien" to "A yaa ligd a wãna ?",
            "cest cher" to "A yaa toogo",
            "c'est cher" to "A yaa toogo",
            "diminue un peu" to "Bole bila",
            "il fait chaud" to "Tʋʋlem yaa wusgo",
            "il fait froid" to "Waood yaa wusgo",
            "je veux dormir" to "M nan gounsam",
            "je vais dormir" to "M nan gounsam",
            "tu dors" to "Fo gũusame ?",
            "je ne dors pas" to "M pa gũus ye",
            "je comprends" to "M wʋmame",
            "je ne comprends pas" to "M pa wʋm ye",
            "je sais" to "M miime",
            "je ne sais pas" to "M pa mi ye",
            "parle doucement" to "Gom bila bila",
            "parle lentement" to "Gom bila bila",
            "repete" to "Lebg gom",
            "répète" to "Lebg gom",
            "aide moi" to "Song maam",
            "aide-moi" to "Song maam",
            "appelle moi" to "Bole maam",
            "appelle-moi" to "Bole maam",
            "je tappelle" to "M na bool-f",
            "je t'appelle" to "M na bool-f",
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
            "ecole" to "Karen-doogo",
            "école" to "Karen-doogo",
            "aller" to "Kênge",
            "manger" to "Rɩ",
            "boire" to "Yũ",
            "dormir" to "Gũsi",
            "travailler" to "Tʋm",
            "travail" to "Tʋʋmde",
            "venir" to "Wa",
            "revenir" to "Le wa",
            "rentrer" to "Kʋɩ",
            "retourner" to "Leb",
            "partir" to "Looge",
            "sortir" to "Yi",
            "entrer" to "Kẽ",
            "arriver" to "Ta",
            "rester" to "Pa",
            "asseoir" to "Zĩnd",
            "s'asseoir" to "Zĩnd",
            "lever" to "Yik",
            "se lever" to "Yik",
            "parler" to "Gom",
            "dire" to "Yeele",
            "voir" to "Yã",
            "regarder" to "Gese",
            "écouter" to "Kelge",
            "entendre" to "Wʋm",
            "donner" to "Kõ",
            "prendre" to "Rɩk",
            "chercher" to "Bao",
            "trouver" to "Paam",
            "acheter" to "Da",
            "vendre" to "Koɛɛse",
            "étudier" to "Zãms",
            "apprendre" to "Zãms",
            "lire" to "Karem",
            "écrire" to "Gʋls",
            "préparer" to "Segl",
            "cuisiner" to "Segl",
            "laver" to "Pek",
            "marcher" to "Kẽn",
            "courir" to "Zoe",
            "aider" to "Songe",
            "aimer" to "Nonge",
            "vouloir" to "Da",
            "pouvoir" to "Tõog",
            "devoir" to "Segd",
            "savoir" to "Mi",
            "connaître" to "Bãng",
            "comprendre" to "Wʋm",
            "attendre" to "Gũ",
            "appeler" to "Boole",
            "demander" to "Sok",
            "répondre" to "Leok",
            "payer" to "Yao",
            "finir" to "Baas",
            "terminer" to "Baas",
            "commencer" to "Sɩng",
            "ouvrir" to "Pak",
            "fermer" to "Pag",
            "cultiver" to "Ko",
            "habiter" to "Be",
            "vivre" to "Vɩɩmde",
            "être" to "Yaa",
            "avoir" to "Tara",
            "fatigué" to "Wãbgome",
            "malade" to "Bãada",
            "content" to "Sũ-noogo",
            "heureux" to "Sũ-noogo",
            "aujourd'hui" to "Dũndã",
            "aujourdhui" to "Dũndã",
            "demain" to "Beoogo",
            "hier" to "Zaamẽ",
            "maintenant" to "Masã",
            "matin" to "Yibeoogo",
            "midi" to "Wĩndga",
            "soir" to "Zaabre",
            "nuit" to "Yungo",
            "toujours" to "Daar fãa",
            "ici" to "Ka",
            "là-bas" to "Be",
            "où" to "Yẽ",
            "quoi" to "Bõe",
            "qui" to "Ãnda",
            "quand" to "Wakat bõe",
            "pourquoi" to "Bõe yĩnga",
            "comment" to "A wãn",
            "combien" to "A wãna",
            "grand" to "Kãsenga",
            "petit" to "Bila",
            "bon" to "Sõma",
            "bien" to "Sõma",
            "beaucoup" to "Wusgo",
            "un peu" to "Bilfu",
            "père" to "Baa",
            "papa" to "Baa",
            "mère" to "Ma",
            "maman" to "Ma",
            "frère" to "Ba-bi-raoogo",
            "sœur" to "Ba-bi-põka",
            "ami" to "Zoa",
            "élève" to "Karen-biiga",
            "étudiant" to "Karen-biiga",
            "enseignant" to "Karen-saamba",
            "maître" to "Karen-saamba",
            "professeur" to "Karen-saamba",
            "livre" to "Sebre",
            "cahier" to "Sebre",
            "stylo" to "Gʋls-teoogo",
            "hôpital" to "Logtor yiri",
            "dispensaire" to "Logtor yiri",
            "mosquée" to "Misri",
            "église" to "Wẽnd-doogo",
            "village" to "Tẽn-bila",
            "ville" to "Galen-tẽnga",
            "champ" to "Pʋʋgo",
            "route" to "Sore",
            "chemin" to "Sore",
            "boutique" to "Bitiki",
            "magasin" to "Bitiki",
            "pain" to "Buru",
            "viande" to "Nemdo",
            "poisson" to "Zĩm",
            "lait" to "Bĩisim",
            "sauce" to "Zeefo",
            "tô" to "Saabo"
        )
        common.forEach { (k, v) ->
            registerFrenchKey(map, k, v, overwrite = false)
        }

        // 3. Grand dictionnaire général (avec indexation de chaque sous-terme séparé par virgule/slash)
        DictionaryData.FULL_DICTIONARY.forEach { entry ->
            registerFrenchKey(map, entry.french, entry.moore, overwrite = false)
            if (entry.synonyms.isNotEmpty()) {
                entry.synonyms.split(",", ";").forEach { syn ->
                    registerFrenchKey(map, syn, entry.moore, overwrite = false)
                }
            }
        }
        map
    }

    private val mooreToFrenchMap by lazy {
        val map = mutableMapOf<String, String>()

        // 1. Entrées de référence officielles
        DictionaryData.AUTHORITATIVE_REFERENCE_ENTRIES.forEach { entry ->
            registerFrenchKey(map, entry.moore, entry.french, overwrite = true)
            if (entry.variants.isNotEmpty()) {
                entry.variants.split(",", ";").forEach { v ->
                    registerFrenchKey(map, v, entry.french, overwrite = true)
                }
            }
        }

        // 2. Expressions conversationnelles courantes
        val common = mapOf(
            "nii yi beogo" to "Bonjour",
            "ne y beogo" to "Bonjour",
            "nei beogo" to "Bonjour",
            "n'yibeogo" to "Bonjour",
            "nii yi beogo m zoodo" to "Bonjour mon ami",
            "nii yi beogo m ba-bi-raoogo" to "Bonjour mon frère",
            "nii yi beogo m ba-bi-põka" to "Bonjour ma sœur",
            "nii yi beogo m baa" to "Bonjour papa",
            "nii yi beogo m ma" to "Bonjour maman",
            "nii zabre" to "Bonsoir",
            "ne zaabre" to "Bonsoir",
            "nii zabre m zoodo" to "Bonsoir mon ami",
            "nii zabre m ba-bi-raoogo" to "Bonsoir mon frère",
            "nii zabre m ba-bi-põka" to "Bonsoir ma sœur",
            "wind da ganeg laafi" to "Bonne nuit",
            "barka" to "Merci",
            "bark wusgo wusgo" to "Merci beaucoup",
            "barka ka be ye" to "De rien",
            "ya sonma" to "Ça va bien",
            "laafi" to "Ça va bien",
            "laafi bala" to "Ça va bien",
            "mam laafi bala" to "Je vais bien",
            "kibare" to "Comment ça va ?",
            "kibaare" to "Comment ça va ?",
            "fo laafi" to "Comment vas-tu ?",
            "fo laafi bame wa" to "Comment vas-tu ?",
            "zak ramba laafi" to "La famille va bien ?",
            "kamb laafi" to "Les enfants vont bien ?",
            "la fõ" to "Et toi ?",
            "tõnd kotame" to "S'il vous plaît",
            "sugri" to "Pardon",
            "m kosa sugri" to "Je suis désolé",
            "n-ye" to "Oui",
            "ayo" to "Non",
            "a zemsame" to "D'accord",
            "a yaa sõma" to "C'est bon",
            "a pa sõma ye" to "Ce n'est pas bon",
            "yaa sɩda" to "C'est vrai",
            "pa sɩda ye" to "Ce n'est pas vrai",
            "bũmb ka be ye" to "Tout va bien",
            "neewôgô" to "Bienvenue",
            "ne y waoongo" to "Bienvenue",
            "dahare sõngo" to "Bonne journée",
            "zaabr sõngo" to "Bonne soirée",
            "sor sõngo" to "Bon voyage",
            "wẽnd na ning barka" to "Que Dieu te bénisse",
            "wẽnd sã n tũ" to "Si Dieu le veut",
            "barka wẽnde" to "Dieu merci",
            "gũus f menga" to "Prends soin de toi",
            "anan be ka" to "Qui est là ?",
            "yaa maam" to "C'est moi",
            "kẽ-y ka" to "Entrez",
            "wa ka" to "Viens ici",
            "zĩnd ka" to "Assieds-toi",
            "gũ-m" to "Attends-moi",
            "m gũuda foo" to "Je t'attends",
            "m watame" to "Je viens",
            "m be sore" to "Je suis en route",
            "m taame" to "Je suis arrivé",
            "m loogda" to "Je pars",
            "fo maanda bõe" to "Tu fais quoi ?",
            "fo be yẽ" to "Tu es où ?",
            "fo yita yẽ" to "D'où viens-tu ?",
            "m yita zakin" to "Je viens de la maison",
            "fo debda yẽ" to "Où vas-tu ?",
            "m debda raagẽ" to "Je vais au marché",
            "m debda tʋʋmde" to "Je vais au travail",
            "m debda zakin" to "Je vais à la maison",
            "m debda zakin wan" to "Je vais à la maison",
            "m kẽngame lekollẽ" to "Je suis allé à l'école",
            "m kẽngame karen-doogo" to "Je suis allé à l'école",
            "m kênge karen-doogo" to "Je suis allé à l'école",
            "m debda karen-doogo" to "Je vais à l'école",
            "m debda lekollẽ" to "Je vais à l'école",
            "m vʋʋsda" to "Je me repose",
            "koom tara maam" to "J'ai faim",
            "koom nonga maam" to "J'ai soif",
            "wa rɩ" to "Viens manger",
            "kõ-m rɩɩbo" to "Donne-moi à manger",
            "kõ-m koom" to "Donne-moi de l'eau",
            "kõ-m ligdi" to "Donne-moi de l'argent",
            "kõ-m dẽ" to "Donne-moi ça",
            "rɩk dẽ" to "Prends ça",
            "m data ligdi" to "Je veux l'argent",
            "m ka ligd ye" to "Je n'ai pas d'argent",
            "m tara ligdi" to "J'ai de l'argent",
            "a yaa ligd a wãna" to "Combien ça coûte ?",
            "a yaa toogo" to "C'est cher",
            "bole bila" to "Diminue un peu",
            "tʋʋlem yaa wusgo" to "Il fait chaud",
            "waood yaa wusgo" to "Il fait froid",
            "m nan gounsam" to "Je vais dormir",
            "fo gũusame" to "Tu dors ?",
            "m pa gũus ye" to "Je ne dors pas",
            "m wʋmame" to "Je comprends",
            "m pa wʋm ye" to "Je ne comprends pas",
            "m miime" to "Je sais",
            "m pa mi ye" to "Je ne sais pas",
            "gom bila bila" to "Parle doucement",
            "lebg gom" to "Répète",
            "song maam" to "Aide-moi",
            "bole maam" to "Appelle-moi",
            "m na bool-f" to "Je t'appelle",
            "m nonga foo" to "Je t'aime",
            "mam yʋʋre la" to "Je m'appelle",
            "fo yʋʋre la bõe" to "Comment tu t'appelles ?",
            "bilmãse" to "Au revoir",
            "beoogo laafi" to "À demain",
            "wakat a to" to "À plus tard",
            "wakat bila" to "À tout à l'heure",
            "nindaare" to "À bientôt",
            "nindaare laafi" to "À bientôt",
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
            "karen-doogo" to "École",
            "lekolle" to "École",
            "lekollẽ" to "à l'école",
            "kẽngame" to "allé",
            "kênge" to "aller",
            "kenge" to "aller"
        )
        common.forEach { (k, v) ->
            registerFrenchKey(map, k, v, overwrite = false)
        }

        // 3. Grand dictionnaire
        DictionaryData.FULL_DICTIONARY.forEach { entry ->
            val cleanFrench = entry.french.substringBefore("(").substringBefore(",").trim()
            registerFrenchKey(map, entry.moore, cleanFrench, overwrite = false)
            if (entry.variants.isNotEmpty()) {
                entry.variants.split(",", ";").forEach { v ->
                    registerFrenchKey(map, v, cleanFrench, overwrite = false)
                }
            }
        }
        map
    }

    private val frenchToDioulaMap by lazy {
        val map = mutableMapOf<String, String>()
        val commonDioula = mapOf(
            "salut" to "I ni sogoma",
            "bonjour" to "I ni sogoma",
            "bonjour mon frere" to "I ni sogoma n balimakɛ",
            "bonjour mon frère" to "I ni sogoma n balimakɛ",
            "bonjour ma soeur" to "I ni sogoma n balimamuso",
            "bonjour ma sœur" to "I ni sogoma n balimamuso",
            "bonjour mon ami" to "I ni sogoma n teri",
            "bonjour papa" to "I ni sogoma n fa",
            "bonjour maman" to "I ni sogoma n ba",
            "bonsoir" to "I ni wula",
            "bonsoir mon frere" to "I ni wula n balimakɛ",
            "bonsoir mon frère" to "I ni wula n balimakɛ",
            "bonsoir ma soeur" to "I ni wula n balimamuso",
            "bonsoir ma sœur" to "I ni wula n balimamuso",
            "bonsoir mon ami" to "I ni wula n teri",
            "bonne nuit" to "K'an su diya",
            "ca va" to "Tɔɔrɔ tɛ",
            "ça va" to "Tɔɔrɔ tɛ",
            "tu vas bien" to "I ka kɛnɛ wa ?",
            "comment vas tu" to "I ka kɛnɛ wa ?",
            "comment vas-tu" to "I ka kɛnɛ wa ?",
            "comment tu vas" to "I ka kɛnɛ wa ?",
            "comment ca va" to "I ka kɛnɛ wa ?",
            "comment ça va" to "I ka kɛnɛ wa ?",
            "comment va la famille" to "Somɔgɔw ka kɛnɛ wa ?",
            "les enfants vont bien" to "Denw ka kɛnɛ wa ?",
            "comment allez vous" to "Aw ka kɛnɛ wa ?",
            "comment allez-vous" to "Aw ka kɛnɛ wa ?",
            "et toi" to "Ile do ?",
            "merci" to "I ni ce",
            "merci beaucoup" to "I ni ce kosɛbɛ",
            "de rien" to "Foyi tɛ",
            "sil te plait" to "Sabali",
            "s'il te plaît" to "Sabali",
            "sil vous plait" to "Sabali",
            "s'il vous plaît" to "Sabali",
            "pardon" to "Yafa n ma",
            "excuse moi" to "Yafa n ma",
            "excuse-moi" to "Yafa n ma",
            "je suis desole" to "Hakɛto",
            "je suis désolé" to "Hakɛto",
            "oui" to "Awo",
            "non" to "Ayi",
            "daccord" to "A bɛnna",
            "d'accord" to "A bɛnna",
            "c'est bon" to "A ka ɲi",
            "cest bon" to "A ka ɲi",
            "ce n'est pas bon" to "A man ɲi",
            "ce nest pas bon" to "A man ɲi",
            "c'est vrai" to "Tiɛn don",
            "cest vrai" to "Tiɛn don",
            "ce n'est pas vrai" to "Tiɛn tɛ",
            "ce nest pas vrai" to "Tiɛn tɛ",
            "je vais bien" to "N ka kɛnɛ",
            "tout va bien" to "Tɔɔrɔ tɛ",
            "au revoir" to "K'an bɛn",
            "a demain" to "K'an bɛn sini",
            "à demain" to "K'an bɛn sini",
            "a bientot" to "K'an bɛn sɔɔni",
            "à bientôt" to "K'an bɛn sɔɔni",
            "a plus tard" to "An bɛ dɔɔni",
            "à plus tard" to "An bɛ dɔɔni",
            "bonne arrivee" to "I dansɛ",
            "bonne arrivée" to "I dansɛ",
            "bienvenue" to "I dansɛ",
            "bonne journee" to "K'an tilen diya",
            "bonne journée" to "K'an tilen diya",
            "bonne soiree" to "K'an wula diya",
            "bonne soirée" to "K'an wula diya",
            "bon voyage" to "Ala ka sira diya",
            "que dieu te benisse" to "Ala k'i duba",
            "que dieu te bénisse" to "Ala k'i duba",
            "si dieu le veut" to "Ni Ala sɔnna",
            "inchallah" to "Ni Ala sɔnna",
            "dieu merci" to "Ala barika",
            "qui est la" to "Jɔn bɛ yan ?",
            "qui est là" to "Jɔn bɛ yan ?",
            "c'est moi" to "Ne don",
            "cest moi" to "Ne don",
            "entre" to "Don yan",
            "entrez" to "Aw ye don",
            "viens ici" to "Na yan",
            "assieds toi" to "I sigi",
            "assieds-toi" to "I sigi",
            "attends moi" to "N makɔnɔ",
            "attends-moi" to "N makɔnɔ",
            "je tattends" to "N b'i makɔnɔ",
            "je t'attends" to "N b'i makɔnɔ",
            "je viens" to "N bɛ na",
            "je suis en route" to "N bɛ sira la",
            "je suis arrive" to "N sera",
            "je suis arrivé" to "N sera",
            "je pars" to "N bɛ taa",
            "tu fais quoi" to "I bɛ mun kɛ ?",
            "que fais-tu" to "I bɛ mun kɛ ?",
            "que fais tu" to "I bɛ mun kɛ ?",
            "tu es ou" to "I bɛ min ?",
            "tu es où" to "I bɛ min ?",
            "ou es tu" to "I bɛ min ?",
            "où es-tu" to "I bɛ min ?",
            "ou vas tu" to "I bɛ taa min ?",
            "où vas-tu" to "I bɛ taa min ?",
            "tu vas ou" to "I bɛ taa min ?",
            "tu vas où" to "I bɛ taa min ?",
            "tu viens dou" to "I bɔra min ?",
            "tu viens d'où" to "I bɔra min ?",
            "d'où viens-tu" to "I bɔra min ?",
            "je viens de la maison" to "N bɔra so",
            "je vais au marche" to "N bɛ taa sugu la",
            "je vais au marché" to "N bɛ taa sugu la",
            "je vais au travail" to "N bɛ taa baara la",
            "je vais a la maison" to "N bɛ taa so",
            "je vais à la maison" to "N bɛ taa so",
            "je vais a lecole" to "N bɛ taa lakɔli la",
            "je vais à l'école" to "N bɛ taa lakɔli la",
            "je suis alle a lecole" to "N taara lakɔli la",
            "je suis allé à l'école" to "N taara lakɔli la",
            "je me repose" to "N bɛ n lafiya",
            "jai faim" to "Kɔngɔ bɛ n na",
            "j'ai faim" to "Kɔngɔ bɛ n na",
            "jai soif" to "Minnɔgɔ bɛ n na",
            "j'ai soif" to "Minnɔgɔ bɛ n na",
            "viens manger" to "Na dumuni kɛ",
            "donne moi a manger" to "Dumuni di n ma",
            "donne-moi à manger" to "Dumuni di n ma",
            "donne moi de leau" to "Ji di n ma",
            "donne-moi de l'eau" to "Ji di n ma",
            "donne moi de largent" to "Wari di n ma",
            "donne-moi de l'argent" to "Wari di n ma",
            "je veux largent" to "N bɛ wari fɛ",
            "je veux l'argent" to "N bɛ wari fɛ",
            "je nai pas dargent" to "Wari tɛ n bolo",
            "je n'ai pas d'argent" to "Wari tɛ n bolo",
            "jai de largent" to "Wari bɛ n bolo",
            "j'ai de l'argent" to "Wari bɛ n bolo",
            "combien ca coute" to "A sɔngɔ ye joli ye ?",
            "combien ça coûte" to "A sɔngɔ ye joli ye ?",
            "cest combien" to "A sɔngɔ ye joli ye ?",
            "c'est combien" to "A sɔngɔ ye joli ye ?",
            "cest cher" to "A sɔngɔ ka gɛlɛn",
            "c'est cher" to "A sɔngɔ ka gɛlɛn",
            "diminue un peu" to "Dɔgɔ bɔ a la",
            "il fait chaud" to "Funteni bɛ",
            "il fait froid" to "Suma bɛ",
            "je veux dormir" to "N b'a fɛ ka sinɔgɔ",
            "je vais dormir" to "N bɛna sinɔgɔ",
            "je comprends" to "N y'a faamu",
            "je ne comprends pas" to "N m'a faamu",
            "je sais" to "N y'a lɔn",
            "je ne sais pas" to "N m'a lɔn",
            "parle doucement" to "Kuma dɔɔni dɔɔni",
            "parle lentement" to "Kuma dɔɔni dɔɔni",
            "repete" to "Segi a kan",
            "répète" to "Segi a kan",
            "aide moi" to "N dɛmɛ",
            "aide-moi" to "N dɛmɛ",
            "appelle moi" to "N wele",
            "appelle-moi" to "N wele",
            "je taime" to "N b'i fɛ",
            "je t'aime" to "N b'i fɛ",
            "je mappelle" to "N tɔgɔ ye",
            "je m'appelle" to "N tɔgɔ ye",
            "comment tu tappelles" to "I tɔgɔ ye di ?",
            "comment tu t'appelles" to "I tɔgɔ ye di ?"
        )
        commonDioula.forEach { (k, v) ->
            registerFrenchKey(map, k, v, overwrite = true)
        }
        try {
            DioulaKnowledgeData.seedDioulaDatabase().forEach { item ->
                registerFrenchKey(map, item.translation, item.word, overwrite = false)
                if (item.synonyms.isNotEmpty()) {
                    item.synonyms.split(",", ";").forEach { syn ->
                        registerFrenchKey(map, syn, item.word, overwrite = false)
                    }
                }
            }
        } catch (_: Throwable) {}
        map
    }

    private val dioulaToFrenchMap by lazy {
        val map = mutableMapOf<String, String>()
        val commonDioulaRev = mapOf(
            "n taara lakɔli la" to "Je suis allé à l'école",
            "n taara lakoli la" to "Je suis allé à l'école",
            "n bɛ taa lakɔli la" to "Je vais à l'école",
            "n bɛ taa sugu la" to "Je vais au marché",
            "n bɛ taa so" to "Je vais à la maison",
            "n bɛ taa baara la" to "Je vais au travail",
            "kɔngɔ bɛ n na" to "J'ai faim",
            "minnɔgɔ bɛ n na" to "J'ai soif",
            "wari tɛ n bolo" to "Je n'ai pas d'argent",
            "wari bɛ n bolo" to "J'ai de l'argent",
            "ji di n ma" to "Donne-moi de l'eau",
            "dumuni di n ma" to "Donne-moi à manger",
            "n y'a faamu" to "Je comprends",
            "n m'a faamu" to "Je ne comprends pas",
            "n y'a lɔn" to "Je sais",
            "n m'a lɔn" to "Je ne sais pas",
            "i tɔgɔ ye di" to "Comment tu t'appelles ?",
            "n tɔgɔ ye" to "Je m'appelle"
        )
        commonDioulaRev.forEach { (k, v) ->
            registerFrenchKey(map, k, v, overwrite = true)
        }
        try {
            DioulaKnowledgeData.seedDioulaDatabase().forEach { item ->
                val cleanFr = item.translation.substringBefore("(").substringBefore("/").trim()
                registerFrenchKey(map, item.word, cleanFr, overwrite = false)
                if (item.variants.isNotEmpty()) {
                    item.variants.split(",", ";").forEach { v ->
                        registerFrenchKey(map, v, cleanFr, overwrite = false)
                    }
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
     */
    fun translateDirect(input: String, sourceLang: String, targetLang: String): String? {
        val clean = cleanWhitespace(input)
        if (clean.isBlank()) return null
        val stripped = stripPunctuation(clean).lowercase()
        val normStripped = normalizeAccents(stripped)

        val src = sourceLang.lowercase().trim()
        val tgt = targetLang.lowercase().trim()

        // 1. Chiffres purs (ex: "10", "42")
        val numberVal = clean.toLongOrNull()
        if (numberVal != null) {
            return MultilingualTranslationEngine.formatNumberInTargetLanguage(numberVal, targetLang)
        }

        // 2. Français -> Mooré / Dioula / Haoussa
        if (src.contains("franç") || src.contains("franc")) {
            if (tgt.contains("moor") || tgt.contains("mòoré")) {
                frenchToMooreMap[stripped]?.let { return it }
                frenchToMooreMap[normStripped]?.let { return it }
                DictionaryData.FULL_DICTIONARY.firstOrNull {
                    it.french.equals(clean, ignoreCase = true) || isCloseMatch(it.french, clean)
                }?.let { return it.moore }
            } else if (tgt.contains("dioula") || tgt.contains("jula")) {
                frenchToDioulaMap[stripped]?.let { return it }
                frenchToDioulaMap[normStripped]?.let { return it }
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
                mooreToFrenchMap[normStripped]?.let { return it }
                DictionaryData.FULL_DICTIONARY.firstOrNull {
                    it.moore.equals(clean, ignoreCase = true) || isCloseMatch(it.moore, clean)
                }?.let { return it.french }
            }
        }

        // 4. Dioula -> Français
        if (src.contains("dioula") || src.contains("jula")) {
            if (tgt.contains("franç") || tgt.contains("franc")) {
                dioulaToFrenchMap[stripped]?.let { return it }
                dioulaToFrenchMap[normStripped]?.let { return it }
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

    // =========================================================================
    // MOTEUR D'ASSEMBLAGE COMPOSITIONNEL PAR DICTIONNAIRE LOCAL (MOORÉ & DIOULA)
    // =========================================================================

    private val IRREGULAR_PARTICIPLE_TO_INFINITIVE = mapOf(
        "allé" to "aller", "allée" to "aller", "allés" to "aller", "allées" to "aller", "alle" to "aller", "allee" to "aller", "alles" to "aller", "allees" to "aller",
        "venu" to "venir", "venue" to "venir", "venus" to "venir", "venues" to "venir",
        "revenu" to "revenir", "revenue" to "revenir", "devenu" to "devenir",
        "parti" to "partir", "partie" to "partir", "partis" to "partir", "parties" to "partir",
        "sorti" to "sortir", "sortie" to "sortir", "sortis" to "sortir", "sorties" to "sortir",
        "dormi" to "dormir", "couru" to "courir", "bu" to "boire", "bue" to "boire",
        "vu" to "voir", "vue" to "voir", "vus" to "voir",
        "lu" to "lire", "lue" to "lire",
        "pris" to "prendre", "prise" to "prendre",
        "appris" to "apprendre", "compris" to "comprendre",
        "mis" to "mettre", "mise" to "mettre",
        "dit" to "dire", "dite" to "dire",
        "écrit" to "écrire", "ecrit" to "écrire",
        "fait" to "faire", "faite" to "faire",
        "ouvert" to "ouvrir", "offerte" to "offrir", "offert" to "offrir",
        "mort" to "mourir", "né" to "naître", "ne" to "naître",
        "reçu" to "recevoir", "recu" to "recevoir",
        "voulu" to "vouloir", "pu" to "pouvoir", "su" to "savoir", "connu" to "connaître",
        "attendu" to "attendre", "entendu" to "entendre", "vendu" to "vendre", "perdu" to "perdre",
        "répondu" to "répondre", "repondu" to "répondre", "descendu" to "descendre",
        "assis" to "s'asseoir", "assise" to "s'asseoir",
        "fini" to "finir", "choisi" to "choisir", "guéri" to "guérir", "rempli" to "remplir",
        "été" to "être", "ete" to "être", "eu" to "avoir"
    )

    private val CONJUGATED_VERB_TO_INFINITIVE = mapOf(
        // Aller
        "vais" to "aller", "vas" to "aller", "va" to "aller", "allons" to "aller", "allez" to "aller", "vont" to "aller",
        "allais" to "aller", "allait" to "aller", "irai" to "aller", "iras" to "aller", "ira" to "aller", "irons" to "aller", "irez" to "aller", "iront" to "aller",
        // Venir / Revenir
        "viens" to "venir", "vient" to "venir", "venons" to "venir", "venez" to "venir", "viennent" to "venir",
        "venais" to "venir", "venait" to "venir", "viendrai" to "venir", "viendras" to "venir", "viendra" to "venir",
        "reviens" to "revenir", "revient" to "revenir", "revenons" to "revenir", "revenez" to "revenir",
        // Partir / Sortir / Entrer
        "pars" to "partir", "part" to "partir", "partons" to "partir", "partez" to "partir", "partent" to "partir",
        "sors" to "sortir", "sort" to "sortir", "sortons" to "sortir", "sortez" to "sortir", "sortent" to "sortir",
        "entre" to "entrer", "entres" to "entrer", "entrons" to "entrer", "entrez" to "entrer", "entrent" to "entrer",
        // Manger / Boire / Dormir
        "mange" to "manger", "manges" to "manger", "mangeons" to "manger", "mangez" to "manger", "mangent" to "manger",
        "mangerai" to "manger", "mangeras" to "manger", "mangera" to "manger",
        "bois" to "boire", "boit" to "boire", "buvons" to "boire", "buvez" to "boire", "boivent" to "boire",
        "dors" to "dormir", "dort" to "dormir", "dormons" to "dormir", "dormez" to "dormir", "dorment" to "dormir",
        // Travailler / Étudier / Apprendre / Lire / Écrire
        "travaille" to "travailler", "travailles" to "travailler", "travaillons" to "travailler", "travaillez" to "travailler", "travaillent" to "travailler",
        "étudie" to "étudier", "etudie" to "étudier", "étudies" to "étudier", "étudions" to "étudier", "étudient" to "étudier",
        "apprends" to "apprendre", "apprend" to "apprendre", "apprenons" to "apprendre", "apprenez" to "apprendre", "apprennent" to "apprendre",
        "lis" to "lire", "lit" to "lire", "lisons" to "lire", "lisez" to "lire", "lisent" to "lire",
        "écris" to "écrire", "ecris" to "écrire", "écrit" to "écrire", "ecrit" to "écrire", "écrivons" to "écrire",
        // Parler / Dire / Comprendre / Savoir / Connaître
        "parle" to "parler", "parles" to "parler", "parlons" to "parler", "parlez" to "parler", "parlent" to "parler",
        "dis" to "dire", "dit" to "dire", "disons" to "dire", "dites" to "dire", "disent" to "dire",
        "comprends" to "comprendre", "comprend" to "comprendre", "comprenons" to "comprendre", "comprenez" to "comprendre",
        "sais" to "savoir", "sait" to "savoir", "savons" to "savoir", "savez" to "savoir", "savent" to "savoir",
        "connais" to "connaître", "connait" to "connaître", "connaît" to "connaître",
        // Voir / Regarder / Écouter / Entendre
        "vois" to "voir", "voit" to "voir", "voyons" to "voir", "voyez" to "voir", "voient" to "voir", "verrai" to "voir", "verra" to "voir",
        "regarde" to "regarder", "regardes" to "regarder", "regardons" to "regarder", "regardez" to "regarder", "regardent" to "regarder",
        "écoute" to "écouter", "ecoute" to "écouter", "écoutes" to "écouter", "écoutons" to "écouter",
        "entends" to "entendre", "entend" to "entendre", "entendons" to "entendre",
        // Acheter / Vendre / Payer / Donner / Prendre / Chercher / Trouver / Aider
        "achète" to "acheter", "achete" to "acheter", "achètes" to "acheter", "achetons" to "acheter", "achètent" to "acheter",
        "vends" to "vendre", "vend" to "vendre", "vendons" to "vendre", "vendez" to "vendre", "vendent" to "vendre",
        "paie" to "payer", "paye" to "payer", "payons" to "payer", "payez" to "payer",
        "donne" to "donner", "donnes" to "donner", "donnons" to "donner", "donnez" to "donner", "donnent" to "donner",
        "prends" to "prendre", "prend" to "prendre", "prenons" to "prendre", "prenez" to "prendre", "prennent" to "prendre",
        "cherche" to "chercher", "cherches" to "chercher", "cherchons" to "chercher", "cherchez" to "chercher", "cherchent" to "chercher",
        "trouve" to "trouver", "trouves" to "trouver", "trouvons" to "trouver", "trouvez" to "trouver", "trouvent" to "trouver",
        "aide" to "aider", "aides" to "aider", "aidons" to "aider", "aidez" to "aider", "aident" to "aider",
        // Vouloir / Aimer / Pouvoir / Devoir / Faire
        "veux" to "vouloir", "veut" to "vouloir", "voulons" to "vouloir", "voulez" to "vouloir", "veulent" to "vouloir", "voudrais" to "vouloir", "voudrait" to "vouloir",
        "aime" to "aimer", "aimes" to "aimer", "aimons" to "aimer", "aimez" to "aimer", "aiment" to "aimer",
        "peux" to "pouvoir", "peut" to "pouvoir", "pouvons" to "pouvoir", "pouvez" to "pouvoir", "peuvent" to "pouvoir", "pourrai" to "pouvoir",
        "dois" to "devoir", "doit" to "devoir", "devons" to "devoir", "devez" to "devoir", "doivent" to "devoir",
        "fais" to "faire", "fait" to "faire", "faisons" to "faire", "faites" to "faire", "font" to "faire", "ferai" to "faire", "fera" to "faire",
        // Préparer / Laver / Marcher / Courir / Appeler / Attendre / Demander / Répondre / Finir / Commencer
        "prépare" to "préparer", "prepare" to "préparer", "prépares" to "préparer", "préparons" to "préparer",
        "cuisine" to "préparer", "lave" to "laver", "laves" to "laver", "lavons" to "laver",
        "marche" to "marcher", "marches" to "marcher", "marchons" to "marcher", "marchent" to "marcher",
        "cours" to "courir", "court" to "courir", "courons" to "courir", "courent" to "courir",
        "appelle" to "appeler", "appelles" to "appeler", "appelons" to "appeler", "appellent" to "appeler",
        "attends" to "attendre", "attend" to "attendre", "attendons" to "attendre", "attendent" to "attendre",
        "demande" to "demander", "demandes" to "demander", "demandons" to "demander", "demandent" to "demander",
        "réponds" to "répondre", "reponds" to "répondre", "répond" to "répondre", "repond" to "répondre",
        "finis" to "finir", "finit" to "finir", "finissons" to "finir", "termine" to "terminer",
        "commence" to "commencer", "commences" to "commencer", "commençons" to "commencer",
        "habite" to "habiter", "habites" to "habiter", "habitons" to "habiter",
        "arrive" to "arriver", "arrives" to "arriver", "arrivons" to "arriver", "arrivent" to "arriver",
        "rentre" to "rentrer", "rentres" to "rentrer", "rentrons" to "rentrer", "rentrent" to "rentrer",
        "ouvre" to "ouvrir", "ouvres" to "ouvrir", "ferme" to "fermer", "fermes" to "fermer"
    )

    private val ETRE_FORMS = setOf(
        "suis", "es", "est", "sommes", "êtes", "etes", "sont",
        "étais", "etais", "était", "etait", "étions", "étaient",
        "serai", "seras", "sera", "serons", "serez", "seront"
    )

    private val AVOIR_FORMS = setOf(
        "ai", "as", "a", "avons", "avez", "ont",
        "avais", "avait", "avions", "avaient",
        "aurai", "auras", "aura", "aurons", "aurez", "auront"
    )

    private val AUXILIARIES_PAST = ETRE_FORMS + AVOIR_FORMS

    private val FUTURE_AUXILIARIES = setOf(
        "vais", "vas", "va", "allons", "allez", "vont"
    )

    private val MODAL_VERBS = setOf(
        "veux", "veut", "voulons", "voulez", "veulent", "voudrais", "voudrait",
        "peux", "peut", "pouvons", "pouvez", "peuvent",
        "dois", "doit", "devons", "devez", "doivent"
    )

    private val FRENCH_PRONOUN_MAP = mapOf(
        "je" to Pair("M", "N"),
        "moi" to Pair("Maam", "Ne"),
        "tu" to Pair("Fo", "I"),
        "toi" to Pair("Fõ", "Ile"),
        "il" to Pair("A", "A"),
        "elle" to Pair("A", "A"),
        "on" to Pair("Tõnd", "An"),
        "nous" to Pair("Tõnd", "An"),
        "vous" to Pair("Yãmb", "Aw"),
        "ils" to Pair("B", "U"),
        "elles" to Pair("B", "U"),
        "eux" to Pair("Bãmba", "Olu")
    )

    private val FRENCH_POSSESSIVE_MAP = mapOf(
        "mon" to Pair("m", "n"),
        "ma" to Pair("m", "n"),
        "mes" to Pair("m", "n"),
        "ton" to Pair("fo", "i"),
        "ta" to Pair("fo", "i"),
        "tes" to Pair("fo", "i"),
        "son" to Pair("a", "a"),
        "sa" to Pair("a", "a"),
        "ses" to Pair("a", "a"),
        "notre" to Pair("tõnd", "an"),
        "nos" to Pair("tõnd", "an"),
        "votre" to Pair("yãmb", "aw"),
        "vos" to Pair("yãmb", "aw"),
        "leur" to Pair("b", "u"),
        "leurs" to Pair("b", "u")
    )

    private val FRENCH_CONNECTORS_MAP = mapOf(
        "et" to Pair("la", "ani"),
        "avec" to Pair("ne", "ni"),
        "pour" to Pair("n", "ka"),
        "parce" to Pair("tɩ", "katuguni"),
        "car" to Pair("tɩ", "katuguni"),
        "mais" to Pair("la", "nka"),
        "ou" to Pair("bɩ", "walima"),
        "si" to Pair("sã n", "ni"),
        "dans" to Pair("pʋgẽ", "kɔnɔ"),
        "sur" to Pair("zugu", "kan"),
        "chez" to Pair("yiri", "fɛ"),
        "sans" to Pair("sẽn ka", "kɔ")
    )

    // Adjectifs d'état courants qui ne doivent pas être pris pour des participes passés de verbes d'action
    private val STATE_ADJECTIVES = setOf(
        "fatigué", "fatigue", "fatiguée", "fatigues",
        "malade", "malades", "content", "contente", "heureux", "heureuse",
        "prêt", "pret", "prête", "désolé", "desole", "désolée",
        "occupé", "occupe", "fâché", "fache", "marié", "marie"
    )

    /**
     * Ramène un participe passé français à son infinitif du dictionnaire local.
     */
    fun participleToInfinitive(word: String): String? {
        val clean = word.lowercase().trim()
        if (clean in STATE_ADJECTIVES) return null

        IRREGULAR_PARTICIPLE_TO_INFINITIVE[clean]?.let { return it }
        IRREGULAR_PARTICIPLE_TO_INFINITIVE[normalizeAccents(clean)]?.let { return it }

        // Règle morphologique générale : -é, -ée, -és, -ées -> -er
        val stemE = when {
            clean.endsWith("ées") && clean.length > 4 -> clean.dropLast(3) + "er"
            clean.endsWith("és") && clean.length > 3 -> clean.dropLast(2) + "er"
            clean.endsWith("ée") && clean.length > 3 -> clean.dropLast(2) + "er"
            clean.endsWith("é") && clean.length > 2 -> clean.dropLast(1) + "er"
            else -> null
        }
        if (stemE != null && (lookupWordInLocalDict(stemE, "Mooré") != null || lookupWordInLocalDict(stemE, "Dioula") != null)) {
            return stemE
        }

        // Règle morphologique : -i, -ie, -is, -ies -> -ir
        val stemI = when {
            clean.endsWith("ies") && clean.length > 4 -> clean.dropLast(3) + "ir"
            clean.endsWith("is") && clean.length > 3 -> clean.dropLast(2) + "ir"
            clean.endsWith("ie") && clean.length > 3 -> clean.dropLast(2) + "ir"
            clean.endsWith("i") && clean.length > 2 -> clean.dropLast(1) + "ir"
            else -> null
        }
        if (stemI != null && (lookupWordInLocalDict(stemI, "Mooré") != null || lookupWordInLocalDict(stemI, "Dioula") != null)) {
            return stemI
        }

        // Règle morphologique : -du, -due, -dus, -dues -> -dre
        val stemU = when {
            clean.endsWith("dues") && clean.length > 5 -> clean.dropLast(4) + "dre"
            clean.endsWith("dus") && clean.length > 4 -> clean.dropLast(3) + "dre"
            clean.endsWith("due") && clean.length > 4 -> clean.dropLast(3) + "dre"
            clean.endsWith("du") && clean.length > 3 -> clean.dropLast(2) + "dre"
            else -> null
        }
        if (stemU != null && (lookupWordInLocalDict(stemU, "Mooré") != null || lookupWordInLocalDict(stemU, "Dioula") != null)) {
            return stemU
        }

        return null
    }

    /**
     * Ramène un verbe conjugué français à son infinitif du dictionnaire local.
     */
    fun conjugatedToInfinitive(word: String): String? {
        val clean = word.lowercase().trim()
        CONJUGATED_VERB_TO_INFINITIVE[clean]?.let { return it }
        CONJUGATED_VERB_TO_INFINITIVE[normalizeAccents(clean)]?.let { return it }

        // Terminaisons régulières (présent, imparfait, futur) des 1er et 2e groupes
        val candidates = mutableListOf<String>()
        if (clean.endsWith("eront") && clean.length > 6) candidates.add(clean.dropLast(5) + "er")
        if (clean.endsWith("erons") && clean.length > 6) candidates.add(clean.dropLast(5) + "er")
        if (clean.endsWith("erez") && clean.length > 5) candidates.add(clean.dropLast(4) + "er")
        if (clean.endsWith("erai") && clean.length > 5) candidates.add(clean.dropLast(4) + "er")
        if (clean.endsWith("eras") && clean.length > 5) candidates.add(clean.dropLast(4) + "er")
        if (clean.endsWith("era") && clean.length > 4) candidates.add(clean.dropLast(3) + "er")
        if (clean.endsWith("aient") && clean.length > 6) candidates.add(clean.dropLast(5) + "er")
        if (clean.endsWith("ais") && clean.length > 4) candidates.add(clean.dropLast(3) + "er")
        if (clean.endsWith("ait") && clean.length > 4) candidates.add(clean.dropLast(3) + "er")
        if (clean.endsWith("ent") && clean.length > 4) candidates.add(clean.dropLast(3) + "er")
        if (clean.endsWith("ons") && clean.length > 4) candidates.add(clean.dropLast(3) + "er")
        if (clean.endsWith("ez") && clean.length > 3) candidates.add(clean.dropLast(2) + "er")
        if (clean.endsWith("es") && clean.length > 3) candidates.add(clean.dropLast(2) + "er")
        if (clean.endsWith("e") && clean.length > 3) candidates.add(clean.dropLast(1) + "er")

        for (cand in candidates) {
            if (lookupWordInLocalDict(cand, "Mooré") != null || lookupWordInLocalDict(cand, "Dioula") != null) {
                return cand
            }
        }
        return null
    }

    /**
     * Recherche une racine française dans le dictionnaire local Mooré ou Dioula (avec gestion du singulier/pluriel).
     */
    private val IRREGULAR_FEMININE_TO_MASCULINE = mapOf(
        "bonne" to "bon", "bonnes" to "bon",
        "belle" to "beau", "belles" to "beau",
        "nouvelle" to "nouveau", "nouvelles" to "nouveau",
        "vieille" to "vieux", "vieilles" to "vieux",
        "blanche" to "blanc", "blanches" to "blanc",
        "fraîche" to "frais", "fraiche" to "frais", "fraîches" to "frais",
        "longue" to "long", "longues" to "long",
        "grosse" to "gros", "grosses" to "gros",
        "gentille" to "gentil", "gentilles" to "gentil",
        "heureuse" to "heureux", "heureuses" to "heureux",
        "malheureuse" to "malheureux",
        "première" to "premier", "premiere" to "premier",
        "dernière" to "dernier", "derniere" to "dernier",
        "chère" to "cher", "chere" to "cher", "chères" to "cher",
        "entière" to "entier", "entiere" to "entier",
        "légère" to "léger", "legere" to "léger",
        "sèche" to "sec", "seche" to "sec",
        "douce" to "doux", "douces" to "doux"
    )

    /**
     * Recherche une racine française dans le dictionnaire local Mooré ou Dioula (avec gestion du singulier/pluriel/féminin).
     */
    fun lookupWordInLocalDict(frenchWord: String, targetLang: String): String? {
        val isMoore = targetLang.contains("moor", ignoreCase = true)
        val map = if (isMoore) frenchToMooreMap else frenchToDioulaMap

        val clean = stripPunctuation(frenchWord).lowercase().trim()
        val norm = normalizeAccents(clean)
        if (clean.isEmpty()) return null

        map[clean]?.let { return it }
        map[norm]?.let { return it }

        IRREGULAR_FEMININE_TO_MASCULINE[clean]?.let { masc ->
            map[masc]?.let { return it }
            map[normalizeAccents(masc)]?.let { return it }
        }

        // Gestion automatique du pluriel français (-s, -x, -aux -> -al)
        if (clean.endsWith("aux") && clean.length > 4) {
            val sing = clean.dropLast(3) + "al"
            map[sing]?.let { return it }
            map[normalizeAccents(sing)]?.let { return it }
        }
        if ((clean.endsWith("s") || clean.endsWith("x")) && clean.length > 3) {
            val sing = clean.dropLast(1)
            map[sing]?.let { return it }
            map[normalizeAccents(sing)]?.let { return it }
            IRREGULAR_FEMININE_TO_MASCULINE[sing]?.let { masc ->
                map[masc]?.let { return it }
                map[normalizeAccents(masc)]?.let { return it }
            }
        }
        if (clean.endsWith("es") && clean.length > 4) {
            val sing = clean.dropLast(2)
            map[sing]?.let { return it }
            map[normalizeAccents(sing)]?.let { return it }
        }
        if (clean.endsWith("e") && clean.length > 4) {
            val masc = clean.dropLast(1)
            map[masc]?.let { return it }
            map[normalizeAccents(masc)]?.let { return it }
        }

        return null
    }

    /**
     * Formate un verbe du dictionnaire local au passé (accompli) ou au présent selon la grammaire Mooré ou Dioula.
     */
    private fun inflectLocalVerb(infinitiveFr: String, dictRoot: String, targetLang: String, tense: String): String {
        val isMoore = targetLang.contains("moor", ignoreCase = true)
        val cleanInf = normalizeAccents(infinitiveFr)
        val rootLower = dictRoot.lowercase().trim()

        if (isMoore) {
            return when (tense) {
                "PAST" -> when (cleanInf) {
                    "aller" -> "kẽngame"
                    "venir" -> "waame"
                    "partir" -> "loogame"
                    "sortir" -> "yiime"
                    "entrer" -> "kẽeme"
                    "arriver" -> "taame"
                    "manger" -> "rɩɩme"
                    "boire" -> "yũume"
                    "dormir" -> "gũsame"
                    "travailler" -> "tʋmame"
                    "etudier", "apprendre" -> "zãmsame"
                    "lire" -> "karmame"
                    "ecrire" -> "gʋlsame"
                    "parler", "dire" -> "gomame"
                    "comprendre", "entendre" -> "wʋmame"
                    "savoir" -> "miime"
                    "voir" -> "yãame"
                    "acheter" -> "daame"
                    "vendre" -> "koɛɛsame"
                    "payer" -> "yaoome"
                    "donner" -> "kõome"
                    "prendre" -> "rɩkame"
                    "chercher" -> "baoome"
                    "trouver" -> "paamame"
                    "aider" -> "songame"
                    "preparer" -> "seglame"
                    "laver" -> "pekame"
                    "marcher" -> "kẽname"
                    "courir" -> "zoeeame"
                    "appeler" -> "boolame"
                    "attendre" -> "gũume"
                    "finir", "terminer" -> "baasame"
                    "commencer" -> "sɩngame"
                    "faire" -> "maaname"
                    else -> if (rootLower.endsWith("e")) rootLower.dropLast(1) + "ame" else "${rootLower}ame"
                }
                "PRESENT" -> when (cleanInf) {
                    "aller" -> "debda"
                    "venir" -> "watame"
                    "revenir" -> "lebgda"
                    "rentrer", "retourner" -> "kʋɩta"
                    "partir" -> "loogda"
                    "sortir" -> "yita"
                    "entrer" -> "kẽeda"
                    "arriver" -> "taada"
                    "manger" -> "rɩtda"
                    "boire" -> "yũuda"
                    "dormir" -> "gũusda"
                    "travailler" -> "tʋmda"
                    "etudier", "apprendre" -> "zãmsda"
                    "lire" -> "karmda"
                    "ecrire" -> "gʋlsda"
                    "parler", "dire" -> "gomda"
                    "vouloir" -> "data"
                    "aimer" -> "nonga"
                    "savoir" -> "miime"
                    "connaitre" -> "bãngda"
                    "comprendre", "entendre" -> "wʋmda"
                    "ecouter" -> "kelgda"
                    "voir" -> "yãtda"
                    "regarder" -> "gesda"
                    "acheter" -> "daada"
                    "vendre" -> "koɛɛsda"
                    "payer" -> "yaooda"
                    "donner" -> "kõtda"
                    "prendre" -> "rɩkda"
                    "chercher" -> "baooda"
                    "trouver" -> "paamda"
                    "aider" -> "songda"
                    "preparer", "cuisiner" -> "seglda"
                    "laver" -> "pekda"
                    "marcher" -> "kẽnda"
                    "courir" -> "zooda"
                    "appeler" -> "boolda"
                    "attendre" -> "gũuda"
                    "demander" -> "sokda"
                    "repondre" -> "leokda"
                    "finir", "terminer" -> "baasda"
                    "commencer" -> "sɩngda"
                    "ouvrir" -> "pakda"
                    "fermer" -> "pagda"
                    "cultiver" -> "kooda"
                    "habiter" -> "be"
                    "faire" -> "maanda"
                    else -> dictRoot.lowercase()
                }
                "FUTURE" -> "na n ${dictRoot.lowercase()}"
                else -> dictRoot.lowercase()
            }
        } else {
            // Dioula
            return when (tense) {
                "PAST" -> when (cleanInf) {
                    "aller" -> "taara"
                    "venir" -> "nana"
                    "partir" -> "taara"
                    "sortir" -> "bɔra"
                    "entrer" -> "donna"
                    "arriver" -> "sera"
                    "retourner", "rentrer", "revenir" -> "kɔsegira"
                    "manger" -> "dumuna"
                    "boire" -> "minna"
                    "dormir" -> "sinɔgɔra"
                    "travailler" -> "ye baara kɛ"
                    "etudier", "apprendre", "lire" -> "kalanna"
                    "ecrire" -> "sɛbɛnna"
                    "parler" -> "kumana"
                    "dire" -> "fɔra"
                    "comprendre" -> "faamuna"
                    "voir" -> "yera"
                    "acheter" -> "sanna"
                    "vendre" -> "feerela"
                    "payer" -> "sarala"
                    "donner" -> "dila"
                    "prendre" -> "tara"
                    "chercher" -> "ɲinina"
                    "trouver" -> "sɔrɔla"
                    "aider" -> "dɛmɛna"
                    "preparer" -> "tobila"
                    "laver" -> "kora"
                    "marcher" -> "taamara"
                    "courir" -> "bolila"
                    "appeler" -> "welela"
                    "finir", "terminer" -> "banna"
                    "commencer" -> "daminɛna"
                    "faire" -> "kɛra"
                    else -> if (rootLower.endsWith("a") || rootLower.endsWith("i") || rootLower.endsWith("u") || rootLower.endsWith("ɛ") || rootLower.endsWith("ɔ") || rootLower.endsWith("o") || rootLower.endsWith("e")) "${rootLower}ra" else "$rootLower kɛra"
                }
                "PRESENT" -> when (cleanInf) {
                    "aller" -> "bɛ taa"
                    "vouloir" -> "b'a fɛ"
                    "aimer" -> "bɛ kanu"
                    "savoir" -> "b'a lɔn"
                    "comprendre" -> "b'a faamu"
                    "pouvoir" -> "bɛ se"
                    else -> "bɛ ${dictRoot.lowercase()}"
                }
                "FUTURE" -> "bɛna ${dictRoot.lowercase()}"
                else -> dictRoot.lowercase()
            }
        }
    }

    /**
     * Formate un nom de lieu du dictionnaire local avec son locatif naturel en Mooré ou Dioula.
     */
    private fun formatLocativePlace(placeFr: String, dictPlace: String, targetLang: String): String {
        val isMoore = targetLang.contains("moor", ignoreCase = true)
        val normPlace = normalizeAccents(placeFr)
        if (isMoore) {
            return when (normPlace) {
                "ecole" -> "lekollẽ"
                "marche" -> "raagẽ"
                "maison" -> "zakin"
                "travail", "bureau" -> "tʋʋmde"
                "hopital", "dispensaire" -> "logtor yiri"
                "champ" -> "pʋʋgẽ"
                "village" -> "tẽn-bila"
                "ville" -> "galen-tẽnga"
                "mosquee" -> "misrẽ"
                "eglise" -> "wẽnd-doogẽ"
                else -> dictPlace.lowercase()
            }
        } else {
            return when (normPlace) {
                "ecole" -> "lakɔli la"
                "marche" -> "sugu la"
                "maison" -> "so"
                "travail", "bureau" -> "baara la"
                "hopital", "dispensaire" -> "dɔkɔtɔrɔso la"
                "champ" -> "foro la"
                "village" -> "dugu la"
                "ville" -> "duguba la"
                "mosquee" -> "misiri la"
                "eglise" -> "eglizi la"
                "boutique", "magasin" -> "bitiki la"
                "gare" -> "gari la"
                "banque" -> "banki la"
                else -> "${dictPlace.lowercase()} la"
            }
        }
    }

    /**
     * Assemble une phrase complète en Mooré ou Dioula (ou vers le Français)
     * en la découpant en briques grammaticales et mots du dictionnaire local.
     */
    fun assembleSentenceFromLocalDictionary(
        input: String,
        sourceLang: String,
        targetLang: String
    ): LocalSentenceAssembly? {
        val cleanInput = cleanWhitespace(input)
        if (cleanInput.isBlank()) return null

        val srcLower = sourceLang.lowercase().trim()
        val tgtLower = targetLang.lowercase().trim()

        val isSrcFrench = srcLower.contains("franç") || srcLower.contains("franc")
        val isSrcMoore = srcLower.contains("moor")
        val isSrcDioula = srcLower.contains("dioula") || srcLower.contains("jula")

        val isTgtFrench = tgtLower.contains("franç") || tgtLower.contains("franc")
        val isTgtMoore = tgtLower.contains("moor")
        val isTgtDioula = tgtLower.contains("dioula") || tgtLower.contains("jula")

        // 0. Si la phrase entière est déjà une expression exacte dans le dictionnaire local
        val directExact = MultilingualTranslationEngine.tryDirectExpressionMatch(cleanInput, sourceLang, targetLang)
            ?: translateDirect(cleanInput, sourceLang, targetLang)
        if (!directExact.isNullOrBlank()) {
            return LocalSentenceAssembly(
                translatedText = directExact,
                breakdown = listOf("$cleanInput ➔ $directExact"),
                matchedCount = 1,
                totalSegments = 1
            )
        }

        // Pivot local Mooré <-> Dioula via le Français
        if ((isSrcMoore && isTgtDioula) || (isSrcDioula && isTgtMoore)) {
            val toFr = assembleSentenceFromLocalDictionary(cleanInput, sourceLang, "Français")
            if (toFr != null && toFr.translatedText.isNotBlank()) {
                val toTarget = assembleSentenceFromLocalDictionary(toFr.translatedText, "Français", targetLang)
                if (toTarget != null && toTarget.translatedText.isNotBlank()) {
                    return toTarget
                }
            }
            return null
        }

        // 1. FRANÇAIS -> MOORÉ ou FRANÇAIS -> DIOULA
        if (isSrcFrench && (isTgtMoore || isTgtDioula)) {
            return assembleFrenchToMooreOrDioula(cleanInput, if (isTgtMoore) "Mooré" else "Dioula")
        }

        // 2. MOORÉ -> FRANÇAIS ou DIOULA -> FRANÇAIS
        if ((isSrcMoore || isSrcDioula) && isTgtFrench) {
            return assembleMooreOrDioulaToFrench(cleanInput, if (isSrcMoore) "Mooré" else "Dioula")
        }

        return null
    }

    private fun assembleFrenchToMooreOrDioula(
        frenchText: String,
        targetLang: String // "Mooré" ou "Dioula"
    ): LocalSentenceAssembly? {
        val isMoore = targetLang == "Mooré"
        var isQuestion = frenchText.trim().endsWith("?")

        // Normalisation des tournures interrogatives françaises et apostrophes
        val preNormalized = frenchText.trim()
            .replace("’", "'")
            .replace(Regex("(?i)\\bqu'est-ce que\\b"), "quoi")
            .replace(Regex("(?i)\\bqu'est ce que\\b"), "quoi")
            .replace(Regex("(?i)\\bqu'est-ce qui\\b"), "quoi")
            .replace(Regex("(?i)\\bqui est-ce qui\\b"), "qui")
            .replace(Regex("(?i)\\boù est-ce que\\b"), "où")
            .replace(Regex("(?i)\\bou est-ce que\\b"), "où")
            .replace(Regex("(?i)\\bquand est-ce que\\b"), "quand")
            .replace(Regex("(?i)\\bcomment est-ce que\\b"), "comment")
            .replace(Regex("(?i)\\bpourquoi est-ce que\\b"), "pourquoi")

        if (preNormalized.contains(Regex("(?i)\\best-ce que\\b|\\best ce que\\b"))) {
            isQuestion = true
        }

        val preExpanded = preNormalized
            .replace(Regex("(?i)\\best-ce que\\b"), " ")
            .replace(Regex("(?i)\\best ce que\\b"), " ")
            .replace(Regex("(?i)\\best-ce qu'"), " ")
            .replace(Regex("(?i)\\bc'est\\b"), "c'est")
            .replace(Regex("(?i)\\bj'"), "je ")
            .replace(Regex("(?i)\\bn'"), "ne ")
            .replace(Regex("(?i)\\bm'"), "me ")
            .replace(Regex("(?i)\\bt'"), "te ")
            .replace(Regex("(?i)\\bs'"), "se ")
            .replace(Regex("(?i)\\bqu'"), "que ")
            .replace(Regex("(?i)\\bl'"), "l' ")
            .replace(Regex("(?i)\\bd'"), "de ")

        val rawTokens = preExpanded
            .replace(Regex("[,;:.?!\"«»()]+"), " ")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        if (rawTokens.isEmpty()) return null

        val assembledParts = mutableListOf<String>()
        val breakdownList = mutableListOf<String>()
        var matchedSegments = 0
        var totalMeaningfulSegments = 0

        var i = 0
        while (i < rawTokens.size) {
            val tokLower = rawTokens[i].lowercase()
            val tokNorm = normalizeAccents(tokLower)

            // --- A. Expressions multi-mots exactes dans le dictionnaire local (fenêtre de 5 à 2 mots) ---
            var multiMatched = false
            val maxWindow = minOf(5, rawTokens.size - i)
            for (len in maxWindow downTo 2) {
                val phraseTokens = rawTokens.subList(i, i + len)
                val phraseRaw = phraseTokens.joinToString(" ").replace("l' ", "l'")
                val phraseDirect = translateDirect(phraseRaw, "Français", targetLang)
                if (phraseDirect != null && phraseDirect.isNotBlank()) {
                    assembledParts.add(phraseDirect)
                    breakdownList.add("$phraseRaw ➔ $phraseDirect")
                    matchedSegments++
                    totalMeaningfulSegments++
                    i += len
                    multiMatched = true
                    break
                }
            }
            if (multiMatched) continue

            // --- A2. Tournures "c'est", "ce n'est pas", "il y a", "il n'y a pas", "il faut" ---
            if (tokLower == "c'est" || tokLower == "cest") {
                val nextTok = if (i + 1 < rawTokens.size) rawTokens[i + 1].lowercase() else ""
                if (nextTok == "pas") {
                    val negCest = if (isMoore) "pa" else "tɛ"
                    assembledParts.add(negCest)
                    breakdownList.add("c'est pas ➔ $negCest")
                    matchedSegments++
                    totalMeaningfulSegments++
                    i += 2
                    continue
                } else {
                    val cestWord = if (isMoore) "yaa" else "ye"
                    assembledParts.add(cestWord)
                    breakdownList.add("c'est ➔ $cestWord")
                    matchedSegments++
                    totalMeaningfulSegments++
                    i++
                    continue
                }
            }
            if (tokLower == "il" && i + 2 < rawTokens.size && rawTokens[i + 1].lowercase() == "y" && rawTokens[i + 2].lowercase() == "a") {
                val ilYaWord = if (isMoore) "beeme" else "bɛ yen"
                assembledParts.add(ilYaWord)
                breakdownList.add("il y a ➔ $ilYaWord")
                matchedSegments++
                totalMeaningfulSegments++
                i += 3
                continue
            }
            if (tokLower == "il" && i + 1 < rawTokens.size && rawTokens[i + 1].lowercase() == "faut") {
                val ilFautWord = if (isMoore) "segdame n" else "ka kan ka"
                assembledParts.add(ilFautWord)
                breakdownList.add("il faut ➔ $ilFautWord")
                matchedSegments++
                totalMeaningfulSegments++
                i += 2
                continue
            }

            // --- B. Constructions verbales composées : PASSÉ COMPOSÉ (ex: "suis allé", "ai mangé", "est venu") ---
            if (tokLower in AUXILIARIES_PAST && i + 1 < rawTokens.size) {
                val nextTok = rawTokens[i + 1].lowercase()
                // Gestion de la négation "ne suis pas allé" ou adverbe intercalé
                if ((nextTok == "pas" || nextTok == "jamais" || nextTok == "deja" || nextTok == "déjà" || nextTok == "bien") && i + 2 < rawTokens.size) {
                    val partTok = rawTokens[i + 2]
                    val inf = participleToInfinitive(partTok)
                    if (inf != null) {
                        val dictVerb = lookupWordInLocalDict(inf, targetLang)
                        if (dictVerb != null) {
                            val isNeg = nextTok == "pas" || nextTok == "jamais"
                            val conjugated = if (isNeg) {
                                if (isMoore) "pa ${dictVerb.lowercase()} ye" else "ma ${dictVerb.lowercase()}"
                            } else {
                                inflectLocalVerb(inf, dictVerb, targetLang, "PAST")
                            }
                            val chunkLabel = "${rawTokens[i]} ${rawTokens[i + 1]} $partTok"
                            assembledParts.add(conjugated)
                            breakdownList.add("$chunkLabel (${inf.replaceFirstChar { it.uppercase() }} : $dictVerb) ➔ $conjugated")
                            matchedSegments++
                            totalMeaningfulSegments++
                            i += 3
                            continue
                        }
                    }
                } else {
                    val partTok = rawTokens[i + 1]
                    val inf = participleToInfinitive(partTok)
                    if (inf != null) {
                        val dictVerb = lookupWordInLocalDict(inf, targetLang)
                        if (dictVerb != null) {
                            val conjugated = inflectLocalVerb(inf, dictVerb, targetLang, "PAST")
                            val chunkLabel = "${rawTokens[i]} $partTok"
                            assembledParts.add(conjugated)
                            breakdownList.add("$chunkLabel (${inf.replaceFirstChar { it.uppercase() }} : $dictVerb) ➔ $conjugated")
                            matchedSegments++
                            totalMeaningfulSegments++
                            i += 2
                            continue
                        }
                    }
                }
            }

            // --- B2. Verbe "ÊTRE" employé seul ("suis", "es", "est", "sommes", "êtes", "sont") ---
            if (tokLower in ETRE_FORMS) {
                val nextTok = if (i + 1 < rawTokens.size) rawTokens[i + 1].lowercase() else ""
                val nextNorm = normalizeAccents(nextTok)
                if (nextTok == "pas" || nextTok == "jamais") {
                    val negEtre = if (isMoore) "pa" else "tɛ"
                    assembledParts.add(negEtre)
                    breakdownList.add("${rawTokens[i]} pas ➔ $negEtre")
                    matchedSegments++
                    totalMeaningfulSegments++
                    i += 2
                    continue
                }
                if (nextLowerOrNormInState(nextTok) && i + 1 < rawTokens.size) {
                    val stateDict = lookupWordInLocalDict(rawTokens[i + 1], targetLang)
                    if (stateDict != null) {
                        val chunkLabel = "${rawTokens[i]} ${rawTokens[i + 1]}"
                        assembledParts.add(stateDict.lowercase())
                        breakdownList.add("$chunkLabel ➔ ${stateDict.lowercase()}")
                        matchedSegments++
                        totalMeaningfulSegments++
                        i += 2
                        continue
                    }
                }
                val isLocativeContext = nextNorm in setOf("a", "au", "aux", "en", "dans", "chez", "ici", "la", "la-bas", "ou")
                val etreWord = if (isLocativeContext) {
                    if (isMoore) "be" else "bɛ"
                } else {
                    if (isMoore) "yaa" else "ye"
                }
                assembledParts.add(etreWord)
                breakdownList.add("${rawTokens[i]} (Être) ➔ $etreWord")
                matchedSegments++
                totalMeaningfulSegments++
                i++
                continue
            }

            // --- B3. Verbe "AVOIR" employé seul ("ai", "as", "a", "avons", "avez", "ont") ---
            val prevTok = if (i > 0) rawTokens[i - 1].lowercase() else ""
            val isVerbAvoir = (tokLower in (AVOIR_FORMS - "a")) ||
                (rawTokens[i] == "a" && prevTok in setOf("il", "elle", "on", "qui", "mon", "ma", "ton", "ta", "son", "sa", "homme", "femme", "enfant", "père", "mère", "frère", "sœur", "ami"))
            if (isVerbAvoir) {
                val nextTok = if (i + 1 < rawTokens.size) rawTokens[i + 1].lowercase() else ""
                if (nextTok == "pas" || nextTok == "plus" || nextTok == "jamais") {
                    val negAvoir = if (isMoore) "ka tar" else "tɛ sɔrɔ"
                    assembledParts.add(negAvoir)
                    breakdownList.add("${rawTokens[i]} $nextTok ➔ $negAvoir")
                    matchedSegments++
                    totalMeaningfulSegments++
                    i += 2
                    continue
                }
                val avoirWord = if (isMoore) "tara" else "sɔrɔ"
                assembledParts.add(avoirWord)
                breakdownList.add("${rawTokens[i]} (Avoir) ➔ $avoirWord")
                matchedSegments++
                totalMeaningfulSegments++
                i++
                continue
            }

            // --- C. Constructions verbales : FUTUR PROCHE (ex: "vais aller", "va manger", "allons travailler") ---
            if (tokLower in FUTURE_AUXILIARIES && i + 1 < rawTokens.size) {
                val nextTok = rawTokens[i + 1].lowercase()
                val dictVerb = lookupWordInLocalDict(nextTok, targetLang)
                if (dictVerb != null && (nextTok.endsWith("er") || nextTok.endsWith("ir") || nextTok.endsWith("re"))) {
                    val futForm = inflectLocalVerb(nextTok, dictVerb, targetLang, "FUTURE")
                    val chunkLabel = "${rawTokens[i]} ${rawTokens[i + 1]}"
                    assembledParts.add(futForm)
                    breakdownList.add("$chunkLabel (${nextTok.replaceFirstChar { it.uppercase() }} : $dictVerb) ➔ $futForm")
                    matchedSegments++
                    totalMeaningfulSegments++
                    i += 2
                    continue
                }
            }

            // --- C2. Verbe modal + Infinitif (ex: "veux manger", "veut aller", "peux venir", "ne veux pas manger") ---
            if (tokLower in MODAL_VERBS && i + 1 < rawTokens.size) {
                val modalInf = conjugatedToInfinitive(tokLower) ?: "vouloir"
                val nextTok = rawTokens[i + 1].lowercase()
                if ((nextTok == "pas" || nextTok == "plus") && i + 2 < rawTokens.size) {
                    val verbAfterNeg = rawTokens[i + 2].lowercase()
                    val dictVerb = lookupWordInLocalDict(verbAfterNeg, targetLang)
                    if (dictVerb != null && (verbAfterNeg.endsWith("er") || verbAfterNeg.endsWith("ir") || verbAfterNeg.endsWith("re"))) {
                        val negModal = when (modalInf) {
                            "pouvoir" -> if (isMoore) "pa tõog n ${dictVerb.lowercase()} ye" else "tɛ se ka ${dictVerb.lowercase()}"
                            "devoir" -> if (isMoore) "pa segd n ${dictVerb.lowercase()} ye" else "man kan ka ${dictVerb.lowercase()}"
                            else -> if (isMoore) "pa data n ${dictVerb.lowercase()} ye" else "t'a fɛ ka ${dictVerb.lowercase()}"
                        }
                        assembledParts.add(negModal)
                        breakdownList.add("${rawTokens[i]} $nextTok $verbAfterNeg ➔ $negModal")
                        matchedSegments++
                        totalMeaningfulSegments++
                        i += 3
                        continue
                    }
                }
                val dictVerb = lookupWordInLocalDict(nextTok, targetLang)
                if (dictVerb != null && (nextTok.endsWith("er") || nextTok.endsWith("ir") || nextTok.endsWith("re"))) {
                    val modalPrefix = when (modalInf) {
                        "pouvoir" -> if (isMoore) "tõog n" else "bɛ se ka"
                        "devoir" -> if (isMoore) "segd n" else "ka kan ka"
                        else -> if (isMoore) "data n" else "b'a fɛ ka"
                    }
                    val combined = "$modalPrefix ${dictVerb.lowercase()}"
                    val chunkLabel = "${rawTokens[i]} ${rawTokens[i + 1]}"
                    assembledParts.add(combined)
                    breakdownList.add("$chunkLabel ($modalInf + $nextTok : $dictVerb) ➔ $combined")
                    matchedSegments++
                    totalMeaningfulSegments++
                    i += 2
                    continue
                }
            }

            // --- D. Compléments de lieu avec préposition : "à l'école", "au marché", "à la maison", "au travail", "dans la ..." ---
            if (tokNorm in setOf("a", "au", "aux", "en", "dans", "vers") && i + 1 < rawTokens.size) {
                // Cas 1 : "à l' école" / "à la maison" / "dans la maison" / "dans le marché" (3 tokens: prép + article + nom)
                if (i + 2 < rawTokens.size && rawTokens[i + 1].lowercase() in setOf("l'", "la", "le", "les", "un", "une")) {
                    val nounTok = rawTokens[i + 2]
                    val dictPlace = lookupWordInLocalDict(nounTok, targetLang)
                    if (dictPlace != null) {
                        val locForm = formatLocativePlace(nounTok, dictPlace, targetLang)
                        val chunkLabel = "${rawTokens[i]} ${rawTokens[i + 1]}$nounTok".replace("l' ", "l'")
                        assembledParts.add(locForm)
                        breakdownList.add("$chunkLabel (${nounTok.replaceFirstChar { it.uppercase() }} : $dictPlace) ➔ $locForm")
                        matchedSegments++
                        totalMeaningfulSegments++
                        i += 3
                        continue
                    }
                }
                // Cas 2 : "au marché" / "au travail" / "en ville" (2 tokens: prép + nom)
                val nounTok = rawTokens[i + 1]
                val dictPlace = lookupWordInLocalDict(nounTok, targetLang)
                if (dictPlace != null) {
                    val locForm = formatLocativePlace(nounTok, dictPlace, targetLang)
                    val chunkLabel = "${rawTokens[i]} $nounTok"
                    assembledParts.add(locForm)
                    breakdownList.add("$chunkLabel (${nounTok.replaceFirstChar { it.uppercase() }} : $dictPlace) ➔ $locForm")
                    matchedSegments++
                    totalMeaningfulSegments++
                    i += 2
                    continue
                }
            }

            // --- E. Articles partitifs ou définis + Nom ("du riz", "de l'eau", "la maison", "l'école", "les enfants") ---
            if (tokLower in setOf("du", "des", "le", "la", "les", "l'", "un", "une") && i + 1 < rawTokens.size) {
                val nounTok = rawTokens[i + 1]
                val dictNoun = lookupWordInLocalDict(nounTok, targetLang)
                if (dictNoun != null) {
                    val outNoun = if (tokLower == "un" || tokLower == "une") {
                        if (isMoore) "${dictNoun.lowercase()} a yembre" else "${dictNoun.lowercase()} kelen"
                    } else {
                        dictNoun.lowercase()
                    }
                    val chunkLabel = "${rawTokens[i]} $nounTok".replace("l' ", "l'")
                    assembledParts.add(outNoun)
                    breakdownList.add("$chunkLabel (${nounTok.replaceFirstChar { it.uppercase() }} : $dictNoun) ➔ $outNoun")
                    matchedSegments++
                    totalMeaningfulSegments++
                    i += 2
                    continue
                }
            }
            if (tokLower == "de" && i + 2 < rawTokens.size && rawTokens[i + 1].lowercase() in setOf("l'", "la", "le")) {
                val nounTok = rawTokens[i + 2]
                val dictNoun = lookupWordInLocalDict(nounTok, targetLang)
                if (dictNoun != null) {
                    val outNoun = dictNoun.lowercase()
                    val chunkLabel = "de ${rawTokens[i + 1]}$nounTok".replace("l' ", "l'")
                    assembledParts.add(outNoun)
                    breakdownList.add("$chunkLabel (${nounTok.replaceFirstChar { it.uppercase() }} : $dictNoun) ➔ $outNoun")
                    matchedSegments++
                    totalMeaningfulSegments++
                    i += 3
                    continue
                }
            }

            // --- F. Adjectifs possessifs + Nom ("mon frère", "ma sœur", "mon ami", "ton père") ---
            FRENCH_POSSESSIVE_MAP[tokLower]?.let { (mrPoss, dyuPoss) ->
                val poss = if (isMoore) mrPoss else dyuPoss
                if (i + 1 < rawTokens.size) {
                    val nounTok = rawTokens[i + 1]
                    val dictNoun = lookupWordInLocalDict(nounTok, targetLang)
                    if (dictNoun != null) {
                        val combined = "$poss ${dictNoun.lowercase()}"
                        val chunkLabel = "${rawTokens[i]} $nounTok"
                        assembledParts.add(combined)
                        breakdownList.add("$chunkLabel ($poss + $dictNoun) ➔ $combined")
                        matchedSegments++
                        totalMeaningfulSegments++
                        i += 2
                        continue
                    }
                }
                assembledParts.add(poss)
                breakdownList.add("${rawTokens[i]} ➔ $poss")
                matchedSegments++
                totalMeaningfulSegments++
                i++
                continue
            }

            // --- F2. Adjectifs démonstratifs ("ce matin", "ce soir", "cette maison") ---
            if (tokLower in setOf("ce", "cet", "cette", "ces") && i + 1 < rawTokens.size) {
                val nounTok = rawTokens[i + 1]
                val dictNoun = lookupWordInLocalDict(nounTok, targetLang)
                if (dictNoun != null) {
                    val combined = if (isMoore) "${dictNoun.lowercase()} wã" else "nin ${dictNoun.lowercase()}"
                    assembledParts.add(combined)
                    breakdownList.add("${rawTokens[i]} $nounTok ➔ $combined")
                    matchedSegments++
                    totalMeaningfulSegments++
                    i += 2
                    continue
                }
                i++
                continue
            }

            // --- G. Pronoms sujets ("je", "tu", "il", "elle", "nous", "vous", "ils", "elles") ---
            FRENCH_PRONOUN_MAP[tokLower]?.let { (mrPron, dyuPron) ->
                val pron = if (isMoore) mrPron else dyuPron
                assembledParts.add(pron)
                breakdownList.add("${rawTokens[i]} ➔ $pron")
                matchedSegments++
                totalMeaningfulSegments++
                i++
                continue
            }

            // --- G2. Pronoms réfléchis et compléments ("me", "te", "se", "lui", "leur") ---
            if (tokLower in setOf("me", "te", "se", "lui", "leur")) {
                val prev = if (i > 0) rawTokens[i - 1].lowercase() else ""
                val isReflexive = (tokLower == "me" && prev == "je") ||
                    (tokLower == "te" && prev == "tu") ||
                    (tokLower == "se" && prev in setOf("il", "elle", "on", "ils", "elles"))
                if (!isReflexive) {
                    val objPron = when (tokLower) {
                        "me" -> if (isMoore) "maam" else "n"
                        "te" -> if (isMoore) "foo" else "i"
                        "lui" -> if (isMoore) "a" else "a"
                        "leur" -> if (isMoore) "bãmba" else "u"
                        else -> ""
                    }
                    if (objPron.isNotEmpty()) {
                        assembledParts.add(objPron)
                        matchedSegments++
                        totalMeaningfulSegments++
                    }
                }
                i++
                continue
            }

            // --- H. Connecteurs & prépositions ("et", "avec", "pour", "mais", "ou", "dans") ---
            FRENCH_CONNECTORS_MAP[tokLower]?.let { (mrConn, dyuConn) ->
                val conn = if (isMoore) mrConn else dyuConn
                assembledParts.add(conn)
                breakdownList.add("${rawTokens[i]} ➔ $conn")
                matchedSegments++
                totalMeaningfulSegments++
                i++
                continue
            }

            // --- I. Ignorer les particules grammaticales muettes isolées ("ne", "pas", "que", "l'", "le", "la", "les", "de", "du", "des", "à") ---
            if (tokLower in setOf("ne", "pas", "que", "l'", "le", "la", "les", "de", "du", "des", "a", "à", "au", "aux", "un", "une", "ce", "cet", "cette", "ces", "y", "en")) {
                i++
                continue
            }

            // --- J. Nombres (chiffres ou mots : "2", "deux", "10", "vingt") ---
            val numVal = MultilingualTranslationEngine.parseNumberFromAnyLanguage(tokLower)
            if (numVal != null) {
                val numTranslated = MultilingualTranslationEngine.formatNumberInTargetLanguage(numVal, targetLang)
                assembledParts.add(numTranslated.lowercase())
                breakdownList.add("${rawTokens[i]} ➔ $numTranslated")
                matchedSegments++
                totalMeaningfulSegments++
                i++
                continue
            }

            // --- K. Verbes conjugués au présent / futur / imparfait (avec prise en charge de la négation "<verbe> pas") ---
            val infFromConj = conjugatedToInfinitive(tokLower)
            if (infFromConj != null) {
                val dictVerb = lookupWordInLocalDict(infFromConj, targetLang)
                if (dictVerb != null) {
                    val nextTok = if (i + 1 < rawTokens.size) rawTokens[i + 1].lowercase() else ""
                    if (nextTok == "pas" || nextTok == "plus" || nextTok == "jamais") {
                        val negVerbForm = if (isMoore) {
                            "pa ${dictVerb.lowercase()} ye"
                        } else {
                            if (normalizeAccents(infFromConj) == "vouloir") "t'a fɛ" else "tɛ ${dictVerb.lowercase()}"
                        }
                        assembledParts.add(negVerbForm)
                        breakdownList.add("${rawTokens[i]} $nextTok (${infFromConj.replaceFirstChar { it.uppercase() }} : $dictVerb) ➔ $negVerbForm")
                        matchedSegments++
                        totalMeaningfulSegments++
                        i += 2
                        continue
                    }
                    val isFutureConj = tokLower.endsWith("rai") || tokLower.endsWith("ras") || tokLower.endsWith("ra") || tokLower.endsWith("rons") || tokLower.endsWith("rez") || tokLower.endsWith("ront")
                    val verbTense = if (isFutureConj) "FUTURE" else "PRESENT"
                    val presForm = inflectLocalVerb(infFromConj, dictVerb, targetLang, verbTense)
                    assembledParts.add(presForm)
                    breakdownList.add("${rawTokens[i]} (${infFromConj.replaceFirstChar { it.uppercase() }} : $dictVerb) ➔ $presForm")
                    matchedSegments++
                    totalMeaningfulSegments++
                    i++
                    continue
                }
            }

            // --- L. Participe passé isolé (ex: "allé", "mangé") ---
            val infFromPart = participleToInfinitive(tokLower)
            if (infFromPart != null && lookupWordInLocalDict(tokLower, targetLang) == null) {
                val dictVerb = lookupWordInLocalDict(infFromPart, targetLang)
                if (dictVerb != null) {
                    val pastForm = inflectLocalVerb(infFromPart, dictVerb, targetLang, "PAST")
                    assembledParts.add(pastForm)
                    breakdownList.add("${rawTokens[i]} (${infFromPart.replaceFirstChar { it.uppercase() }} : $dictVerb) ➔ $pastForm")
                    matchedSegments++
                    totalMeaningfulSegments++
                    i++
                    continue
                }
            }

            // --- M. Recherche directe du mot (ou singulier/masculin) dans le dictionnaire local ---
            val directWord = lookupWordInLocalDict(tokLower, targetLang)
            if (directWord != null) {
                val formatted = if (assembledParts.isEmpty()) directWord else directWord.lowercase()
                assembledParts.add(formatted)
                breakdownList.add("${rawTokens[i]} ➔ $directWord")
                matchedSegments++
                totalMeaningfulSegments++
                i++
                continue
            }

            // --- N. Mot non trouvé (ex: prénom ou nom propre) ---
            totalMeaningfulSegments++
            assembledParts.add(rawTokens[i])
            i++
        }

        if (matchedSegments == 0 || assembledParts.isEmpty()) return null

        var finalSentence = assembledParts.joinToString(" ").replace(Regex("\\s+"), " ").trim()
        if (finalSentence.isNotEmpty()) {
            finalSentence = finalSentence.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
        if (isQuestion && !finalSentence.endsWith("?")) {
            finalSentence = if (!isMoore && !finalSentence.endsWith(" wa")) "$finalSentence wa ?" else "$finalSentence ?"
        }

        return LocalSentenceAssembly(
            translatedText = finalSentence,
            breakdown = breakdownList,
            matchedCount = matchedSegments,
            totalSegments = maxOf(1, totalMeaningfulSegments)
        )
    }

    private fun nextLowerOrNormInState(word: String): Boolean {
        val clean = word.lowercase().trim()
        return clean in STATE_ADJECTIVES || normalizeAccents(clean) in STATE_ADJECTIVES
    }

    /**
     * Assemble une phrase du Mooré ou du Dioula vers le Français à l'aide du dictionnaire local.
     */
    private fun assembleMooreOrDioulaToFrench(
        localText: String,
        sourceLang: String // "Mooré" ou "Dioula"
    ): LocalSentenceAssembly? {
        val isMoore = sourceLang == "Mooré"
        val isQuestion = localText.trim().endsWith("?") || localText.trim().lowercase().endsWith(" wa")

        val rawTokens = localText.trim()
            .replace("’", "'")
            .replace(Regex("[,;:.?!\"«»()]+"), " ")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        if (rawTokens.isEmpty()) return null

        val frParts = mutableListOf<String>()
        val breakdownList = mutableListOf<String>()
        var matchedSegments = 0
        var totalSegments = 0

        val moorePronounsToFr = mapOf(
            "m" to "Je", "mam" to "Je", "maam" to "Moi",
            "fo" to "Tu", "f" to "Tu", "fõ" to "Toi",
            "a" to "Il", "yẽ" to "Lui",
            "d" to "Nous", "tõnd" to "Nous", "tõndo" to "Nous", "tond" to "Nous",
            "y" to "Vous", "yãmb" to "Vous", "yãmba" to "Vous",
            "b" to "Ils", "bãmba" to "Ils"
        )

        val dioulaPronounsToFr = mapOf(
            "n" to "Je", "ne" to "Je", "oum" to "Je", "m" to "Je", "mi" to "Je",
            "i" to "Tu", "ile" to "Toi", "ele" to "Toi",
            "a" to "Il", "ale" to "Lui",
            "an" to "Nous", "anw" to "Nous", "aôn" to "Nous", "aon" to "Nous",
            "aw" to "Vous", "ao" to "Vous",
            "u" to "Ils", "o" to "Ils", "olu" to "Ils"
        )

        var i = 0
        while (i < rawTokens.size) {
            val tok = rawTokens[i]
            val lower = tok.lowercase()
            val norm = normalizeAccents(lower)

            // 1. Fenêtre multi-mots (ex: "lakɔli la", "sugu la", "baara kɛ", "bɛ taa")
            var multiMatched = false
            val maxWin = minOf(4, rawTokens.size - i)
            for (len in maxWin downTo 2) {
                val sub = rawTokens.subList(i, i + len).joinToString(" ")

                // Locatifs Dioula : "<lieu> la" / "<lieu> kɔnɔ"
                if (!isMoore && len == 2 && rawTokens[i + 1].lowercase() in setOf("la", "na", "kɔnɔ", "kono")) {
                    val placeWord = rawTokens[i]
                    val placeFr = translateDirect(placeWord, "Dioula", "Français")
                    if (placeFr != null) {
                        val cleanPlace = placeFr.lowercase()
                        val prepFr = when {
                            cleanPlace.startsWith("é") || cleanPlace.startsWith("e") || cleanPlace.startsWith("h") -> "à l'$cleanPlace"
                            cleanPlace in setOf("marché", "travail", "village", "champ") -> "au $cleanPlace"
                            else -> "à la $cleanPlace"
                        }
                        frParts.add(prepFr)
                        breakdownList.add("$sub ($placeWord = $placeFr) ➔ $prepFr")
                        matchedSegments++
                        totalSegments++
                        i += 2
                        multiMatched = true
                        break
                    }
                }

                // Aspect présent/futur Dioula : "bɛ <verbe>" / "bɛna <verbe>"
                if (!isMoore && len == 2 && rawTokens[i].lowercase() in setOf("bɛ", "be", "bi", "bɛna", "bina")) {
                    val verbWord = rawTokens[i + 1]
                    val verbFr = translateDirect(verbWord, "Dioula", "Français")
                    if (verbFr != null) {
                        val isFut = rawTokens[i].lowercase().startsWith("bɛn") || rawTokens[i].lowercase().startsWith("bin")
                        val outFr = if (isFut) "vais ${verbFr.lowercase()}" else verbFr.lowercase()
                        frParts.add(outFr)
                        breakdownList.add("$sub ➔ $outFr")
                        matchedSegments++
                        totalSegments++
                        i += 2
                        multiMatched = true
                        break
                    }
                }

                val directMulti = translateDirect(sub, sourceLang, "Français")
                if (directMulti != null) {
                    frParts.add(directMulti.lowercase())
                    breakdownList.add("$sub ➔ $directMulti")
                    matchedSegments++
                    totalSegments++
                    i += len
                    multiMatched = true
                    break
                }
            }
            if (multiMatched) continue

            // 2. Pronoms
            val pronMap = if (isMoore) moorePronounsToFr else dioulaPronounsToFr
            if (pronMap.containsKey(lower)) {
                val pFr = pronMap[lower]!!
                frParts.add(pFr)
                breakdownList.add("$tok ➔ $pFr")
                matchedSegments++
                totalSegments++
                i++
                continue
            }

            // 3. Verbes conjugués au passé ou locatifs Mooré / Dioula
            if (isMoore) {
                if (norm == "kengame" || lower == "kẽngame" || norm == "kenge" || lower == "kênge") {
                    val frVerb = if (frParts.firstOrNull()?.lowercase() == "je") "suis allé" else "est allé"
                    frParts.add(frVerb)
                    breakdownList.add("$tok (Kênge = Aller) ➔ $frVerb")
                    matchedSegments++
                    totalSegments++
                    i++
                    continue
                }
                if (norm == "lekolle" || lower == "lekollẽ" || norm == "karen-doogo") {
                    frParts.add("à l'école")
                    breakdownList.add("$tok (École) ➔ à l'école")
                    matchedSegments++
                    totalSegments++
                    i++
                    continue
                }
                if (norm == "raage" || lower == "raagẽ" || norm == "raaga") {
                    frParts.add("au marché")
                    breakdownList.add("$tok (Raaga = Marché) ➔ au marché")
                    matchedSegments++
                    totalSegments++
                    i++
                    continue
                }
                if (norm == "zakin" || norm == "zaka") {
                    frParts.add("à la maison")
                    breakdownList.add("$tok (Zaka = Maison) ➔ à la maison")
                    matchedSegments++
                    totalSegments++
                    i++
                    continue
                }
                if (lower.endsWith("ame") && lower.length > 4) {
                    val stem = lower.removeSuffix("ame")
                    val verbFr = translateDirect(stem, "Mooré", "Français")
                        ?: translateDirect("${stem}e", "Mooré", "Français")
                    if (verbFr != null) {
                        frParts.add("a ${verbFr.lowercase()}")
                        breakdownList.add("$tok ($stem = $verbFr) ➔ a ${verbFr.lowercase()}")
                        matchedSegments++
                        totalSegments++
                        i++
                        continue
                    }
                }
            } else {
                if (norm == "taara" || norm == "tara" || norm == "taa") {
                    val frVerb = if (frParts.firstOrNull()?.lowercase() == "je") "suis allé" else "est allé"
                    frParts.add(frVerb)
                    breakdownList.add("$tok (Taa = Aller) ➔ $frVerb")
                    matchedSegments++
                    totalSegments++
                    i++
                    continue
                }
                if ((lower.endsWith("ra") || lower.endsWith("la") || lower.endsWith("na")) && lower.length > 3) {
                    val stem = lower.dropLast(2)
                    val verbFr = translateDirect(stem, "Dioula", "Français")
                    if (verbFr != null) {
                        frParts.add(verbFr.lowercase())
                        breakdownList.add("$tok ($stem = $verbFr) ➔ ${verbFr.lowercase()}")
                        matchedSegments++
                        totalSegments++
                        i++
                        continue
                    }
                }
            }

            // 4. Recherche directe du mot dans le dictionnaire local
            val direct = translateDirect(tok, sourceLang, "Français")
            if (direct != null) {
                frParts.add(direct.lowercase())
                breakdownList.add("$tok ➔ $direct")
                matchedSegments++
                totalSegments++
                i++
                continue
            }

            totalSegments++
            frParts.add(tok)
            i++
        }

        if (matchedSegments == 0 || frParts.isEmpty()) return null

        var finalFr = frParts.joinToString(" ").replace(Regex("\\s+"), " ").trim()
        if (finalFr.isNotEmpty()) {
            finalFr = finalFr.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
        if (isQuestion && !finalFr.endsWith("?")) {
            finalFr = "$finalFr ?"
        }

        return LocalSentenceAssembly(
            translatedText = finalFr,
            breakdown = breakdownList,
            matchedCount = matchedSegments,
            totalSegments = maxOf(1, totalSegments)
        )
    }

    /**
     * Fournit l'intégralité du lexique unifié pour l'onglet Dictionnaire (2500+ entrées).
     * Accessible instantanément sans chargement de base de données.
     */
    val unifiedDictionaryList: List<UnifiedDictionaryEntry> by lazy {
        val list = mutableListOf<UnifiedDictionaryEntry>()
        val seenPairs = mutableSetOf<String>()

        // 1. Entrées de référence officielles prioritaires Mooré
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

        // 3. Entrées Dioula (160+)
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
