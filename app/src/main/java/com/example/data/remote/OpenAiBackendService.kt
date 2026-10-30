package com.example.data.remote

import android.util.Log
import com.example.BuildConfig
import com.example.data.local.WebSource
import com.example.data.repository.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@Serializable
data class OpenAiChatResult(
    val success: Boolean = true,
    val text: String = "",
    val sources: List<WebSource> = emptyList(),
    val model: String = "",
    val searchPerformed: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Service client pour l'intégration officielle et exclusive de l'API dans SAM AI.
 */
class OpenAiBackendService {

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(18, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    companion object {
        const val FLAGSHIP_OPENAI_MODEL = "gpt-4o"
        private val VALID_OPENAI_MODELS = setOf(
            "gpt-4o",
            "gpt-4.1",
            "chatgpt-4o-latest",
            "gpt-4.5-preview",
            "o3",
            "o1",
            "gpt-4-turbo"
        )
    }

    private val clientConfiguredModel: String by lazy {
        val raw = try {
            BuildConfig.OPENAI_MODEL.trim().removeSurrounding("\"").removeSurrounding("'").trim()
        } catch (_: Throwable) {
            ""
        }
        if (raw in VALID_OPENAI_MODELS) raw else FLAGSHIP_OPENAI_MODEL
    }

    private fun getOpenAiApiKey(): String {
        val buildKey = try {
            BuildConfig.OPENAI_API_KEY.trim().removeSurrounding("\"").removeSurrounding("'").trim()
        } catch (_: Throwable) {
            ""
        }
        if (buildKey.isNotBlank() && buildKey != "MY_OPENAI_API_KEY") return buildKey
        return com.example.data.local.ApiKeyManager.DEFAULT_OPENAI_API_KEY
    }

    private fun sanitizeSamAiText(raw: String): String {
        return raw
            .replace(Regex("(?i)\\bChatGPT\\b"), "SAM AI")
            .replace(Regex("(?i)\\bGPT-4(\\.1|\\.5|o)?(-mini|-turbo|-preview|-2024-\\d{2}-\\d{2})?\\b"), "SAM AI")
            .replace(Regex("(?i)\\bOpenAI\\b"), "SAM AI")
            .trim()
    }

    /**
     * Envoie une question ou un historique complet de discussion à SAM AI
     * en injectant si disponible le contexte des dépêches d'actualités en direct.
     */
    suspend fun sendChatMessage(
        messages: List<ChatMessage>,
        userQuestion: String,
        enableWebSearch: Boolean = false,
        preferredModel: String = "",
        newsContext: String = "",
        sources: List<WebSource> = emptyList(),
        isMooreVoice: Boolean = false
    ): OpenAiChatResult = withContext(Dispatchers.IO) {
        val cleanQuestion = userQuestion.trim()
        if (cleanQuestion.isBlank() && messages.isEmpty()) {
            return@withContext OpenAiChatResult(
                success = false,
                errorMessage = "Le message est vide."
            )
        }

        // RÈGLES PRIORITAIRES DÉTERMINISTES (50 Leçons Audio Officielles du Créateur) :
        // Normalisation des prolongations vocales ("Niiiii zabreee", "Maaaaam", "Fooooo") et réponse immédiate sans erreur (0 ms)
        val deterministicReply = resolveDeterministicMooreLessonReply(cleanQuestion, isMooreVoice)
        if (deterministicReply != null) {
            return@withContext OpenAiChatResult(
                success = true,
                text = deterministicReply,
                sources = emptyList(),
                model = "sam-ai-moore-direct",
                searchPerformed = false
            )
        }

        // Utilisation prioritaire du modèle flagship le plus performant d'OpenAI (gpt-4o / gpt-4.1)
        val primaryModel = when {
            preferredModel.isNotBlank() && preferredModel in VALID_OPENAI_MODELS -> preferredModel
            else -> clientConfiguredModel
        }

        var directOpenAiResult = tryCallOfficialOpenAiApi(
            messages = messages,
            userQuestion = cleanQuestion,
            model = primaryModel,
            newsContext = newsContext,
            sources = sources,
            isMooreVoice = isMooreVoice
        )

        if (directOpenAiResult == null || !directOpenAiResult.success) {
            val fallbackModel = if (primaryModel != FLAGSHIP_OPENAI_MODEL) FLAGSHIP_OPENAI_MODEL else "gpt-4o-mini"
            val fallbackResult = tryCallOfficialOpenAiApi(
                messages = messages,
                userQuestion = cleanQuestion,
                model = fallbackModel,
                newsContext = newsContext,
                sources = sources,
                isMooreVoice = isMooreVoice
            )
            if (fallbackResult != null && fallbackResult.success) {
                directOpenAiResult = fallbackResult
            }
        }

        directOpenAiResult ?: OpenAiChatResult(
            success = false,
            text = "",
            model = "",
            errorMessage = "Impossible de joindre le serveur SAM AI. Vérifiez votre connexion réseau."
        )
    }

    /**
     * Répond instantanément (0 ms) et avec 0% d'erreur à toutes les salutations, phrases et mots
     * enseignés dans les 50 audios de référence ainsi qu'aux relances conversationnelles courantes,
     * y compris lorsque l'utilisateur prolonge les voyelles ou les consonnes nasales.
     */
    fun resolveDeterministicMooreLessonReply(rawQuestion: String, isMooreVoice: Boolean): String? {
        val cleaned = rawQuestion
            .replace(Regex("\\[.*?\\]"), " ")
            .trim()
        if (cleaned.isBlank()) return null

        val deprolonged = com.example.data.engine.DictionaryLookupEngine.normalizeMooreProlongations(cleaned)
        val norm = deprolonged.lowercase(java.util.Locale.ROOT)
            .replace("’", "'")
            .replace("‘", "'")
            .replace(Regex("[^a-zàâäéèêëîïôöùûüçñãẽĩõũʋɩɛ'\\s-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        // En mode écrit normal (!isMooreVoice), on ne déclenche jamais de traduction automatique non sollicitée :
        // seules les vraies salutations Mooré exactes ou le mode Appel Vocal En Direct (isMooreVoice) déclenchent la réponse vocale Mooré.
        if (!isMooreVoice && norm !in setOf(
                "nii yi beogo", "ni yibeogo", "ni yi beogo", "ne y beogo", "ne yibeogo",
                "nii zabre", "nii zaabre", "ni zabre", "ni zaabre", "ne zabre", "ne zaabre",
                "nii windga", "ni windga", "ne windga", "ne y windga",
                "niyungo", "nii yungo", "ni yungo", "ne yungo",
                "wind da ganeg laafi", "wend na ganeg laafi", "neewôgô", "neewogo", "nindaaré", "nindaare"
            )
        ) {
            return null
        }

        // 1. SALUTATIONS COMPOSÉES + QUESTIONS (ex: "Nii yi beogo, fo be laafi ?")
        if (norm.startsWith("nii yi beogo") && (norm.contains("laafi") || norm.contains("kibare") || norm.contains("kibaré"))) {
            return "Yibeo kibare ! Laafi bala, ya sonma. Fo data bõe tɩ m sõng-fo ?"
        }
        if (norm.startsWith("nii zabre") && (norm.contains("laafi") || norm.contains("kibare") || norm.contains("kibaré"))) {
            return "Zabre kibare ! Laafi bala, ya sonma. Fo data bõe tɩ m sõng-fo ?"
        }
        if (norm.startsWith("nii windga") && (norm.contains("laafi") || norm.contains("kibare") || norm.contains("kibaré"))) {
            return "Windga kibare ! Laafi bala, ya sonma. Fo data bõe tɩ m sõng-fo ?"
        }
        if ((norm.startsWith("niyungo") || norm.startsWith("nii yungo")) && (norm.contains("laafi") || norm.contains("kibare") || norm.contains("kibaré"))) {
            return "Yungo kibare ! Laafi bala, ya sonma. Fo data bõe tɩ m sõng-fo ?"
        }

        // 2. SALUTATIONS, QUESTIONS ET DIALOGUES EXACTS DES 50 AUDIOS DE RÉFÉRENCE (0% d'erreur)
        when {
            // Audio #1 (Matin) : "Nii yi beogo" (= Bonjour) -> Réponse obligatoire : "Yibeo kibare"
            norm in setOf(
                "nii yi beogo", "ni yibeogo", "ni yi beogo", "ne y beogo", "ne yibeogo",
                "ni beogo", "ne beogo", "ney beogo", "n'yibeogo", "nei beogo", "ne yeogo",
                "nii yibeogo", "ni ibeogo", "ni ibéogo", "ni yibeo", "yibeogo", "ni i bogo", "ni bogo"
            ) -> return "Yibeo kibare"

            // Audio #2 (Soir) : "Nii zabre" (= Bonsoir) -> Réponse obligatoire : "Zabre kibare"
            norm in setOf(
                "nii zabre", "nii zaabre", "ni zabre", "ni zaabre", "ne zabre", "ne zaabre",
                "ne y zabre", "ne y zaabre", "ney zabre", "ney zaabre", "nezaabre", "ni sabre", "ni zabré"
            ) -> return "Zabre kibare"

            // Audio #3 (Midi / Après-midi) : "Nii windga" (= Midi / Bon après-midi) -> Réponse obligatoire : "Windga kibare"
            norm in setOf(
                "nii windga", "ni windga", "ne windga", "ne y windga", "ne y wĩndga",
                "niwinga", "ne winga", "ne wĩnga", "nii ouindga", "ni ouindga", "ni winga", "nii winga"
            ) -> return "Windga kibare"

            // Audio #4 (Nuit) : "Niyungo" / "Nii yungo" (= Bonsoir de nuit) -> Réponse obligatoire : "Yungo kibare"
            norm in setOf(
                "niyungo", "nii yungo", "ni yungo", "ne yungo", "ne y yungo", "ne y yʋngo",
                "n'yungo", "nii youngo", "ni youngo", "ni yougo"
            ) -> return "Yungo kibare"

            // Audio #5 (Bonne nuit) : "Wind da ganeg laafi"
            norm in setOf(
                "wind da ganeg laafi", "wend na ganeg laafi", "wẽnd na gãneg laafi",
                "gang laafi", "gãng laafi", "ouenda ganag lafi", "winda ganeg lafi"
            ) -> return "Amiina ! Wind da ganeg laafi."

            // Audio #6 (Bienvenue) : "Neewôgô" / "Ne waongo"
            norm in setOf("neewôgô", "neewogo", "ne waongo", "ne waogo", "ne y waoongo", "waoongo", "newogo") ->
                return if (isMooreVoice) "Barka ! Laafi bala ? Fo data bõe tɩ m sõng-fo ?" else "Barka ! Laafi bala ?"

            // Audio #7 (Réponse aux nouvelles) : "Kibare" / "Yibeo kibare" / "Zabre kibare" / "Windga kibare" / "Yungo kibare"
            norm in setOf(
                "kibare", "kibaré", "kibaare", "yibeo kibare", "yibéo kibaré", "zabre kibare", "zaabre kibare",
                "windga kibare", "yungo kibare", "fo laafi", "fo be laafi", "laafi beeme", "lafi beme", "lafi bémé"
            ) -> return if (isMooreVoice) "Laafi bala ! Ya sonma. Fo data bõe tɩ m sõng-fo ?" else "Laafi bala ! Ya sonma."

            // Audio #9 (Ça va bien) : "Ya sonma" / "Laafi bala"
            norm in setOf("ya sonma", "ya soma", "yaa sõama", "yaa soama", "laafi bala", "lafi bala", "mam be laafi") ->
                return if (isMooreVoice) "Wẽnd na kõ-d laafi ! Fo data bõe tɩ m sõng-fo ?" else "Wẽnd na kõ-d laafi !"

            // Audio #10 (Merci) : "Barka" / "Barka wʋsgo"
            norm in setOf("barka", "barka wusgo", "barka wʋsgo", "barka woto", "barka ousgo") ->
                return if (isMooreVoice) "Kaseto ka be ye ! Mam pʋsda fo. Fo me data bõe ?" else "Kaseto ka be ye ! Mam pʋsda fo."

            // Audio #11 (Au revoir) : "Nindaaré"
            norm in setOf("nindaare", "nindaaré", "bilmãse", "bilmase", "nindare") ->
                return "Wënd-na kô-d nindaare !"

            // Audio #12 (Adieu / À la prochaine) : "Wënd-na kô-d nindaare"
            norm in setOf("wënd-na kô-d nindaare", "wend na ko-d nindaare", "wẽnd na kõ-d nindaare", "wenda kod nindaare") ->
                return "Amiina ! Nindaaré."

            // Audio #13 (Je vais dormir) : "M nan gounsam"
            norm in setOf("m nan gounsam", "m na n gʋsame", "m na goussom", "m na goussem", "m'na goussom") ->
                return "Wind da ganeg laafi !"

            // Audio #14 (Je vais à la maison) : "M debda zakin wan"
            norm in setOf("m debda zakin wan", "m debda zakin", "m debda zakẽ wã", "m'debda zakouan") ->
                return "Wẽnd na taase laafi ! Nindaaré."

            // Audio #15 (Je veux l'argent) : "M data ligdi"
            norm in setOf("m data ligdi", "mam data ligdi", "mam rata ligdi", "m'data ligdi") ->
                return "Wẽnd na kõ-f ligdi la laafi ! Fo data bõe yɛsa ?"

            // Audio #16 (L'actualité du jour / Nouvelles) : "Sên na be" / "Rũndã kibaaya"
            norm in setOf("sên na be", "sen na be", "rũndã kibaaya", "runda kibaya", "kibare boe n be runda", "kibare bôe n be rũndã") ->
                return "Rũndã kibaaya yaa sõama Burkina Faso pʋgẽ ! Laafi beeme. Fo data bõe ?"

            // Questions courantes en Mooré : Comment tu t'appelles / Qui es-tu / Qui t'a créé / Que peux-tu faire / Parle-moi du Burkina
            norm in setOf("fo yʋʋre la bõe", "fo yuure la boe", "fo yʋʋre la boe", "fo yuure la bõe") ->
                return "Mam yʋʋre la SAM AI ! Abdoul Mohamine Sawadogo n naan maam. Fo data bõe tɩ m sõng-fo ?"

            norm in setOf("fo yaa anda", "fo yaa ãnda", "anda n naan fo", "ãnda n naan fo", "anda n naan-fo", "ãnda n naan-fo") ->
                return "Maam yaa SAM AI, Abdoul Mohamine Sawadogo n naan maam Burkina Faso pʋgẽ. Fo data bõe tɩ m sõng-fo ?"

            norm in setOf("boe la sam ai toe n maane", "bôe la sam ai tõe n maane", "bõe la fo tõe n maane", "boe la fo toe n maane") ->
                return "Maam tõe n leoka fo sokdsã fãa ne Mooré, n sõng-fo ne Burkina Faso kibaya la gom-biisi ! Fo data bõe tɩ m sõng-fo ?"

            norm in setOf("fo data bõe", "fo data boe", "bõe la fo data", "boe la fo data") ->
                return "Maam data n sõng-fo ! Sok-m fo sokrã tɩ m leok-fo sân-sân."

            norm in setOf("yel-mam kibare ne burkina faso", "yel mam kibare ne burkina faso", "burkina faso teng-zugu la ye", "burkina faso tẽng-zugu la yɛ") ->
                return "Burkina Faso yaa nin-tɩrs tẽnga ! A tẽng-zugu yaa Ouagadougou, la Bobo-Dioulasso yaa tẽng-kãsenga. Tond nongda Burkina Faso wʋsgo !"

            // Vocabulaire des 50 leçons audio du créateur (réponses exactes en Mooré lors de l'appel vocal)
            norm in setOf("maam", "mam") -> return "N-ye, maam la fo zoodo SAM AI ! Fo data bõe ?"
            norm in setOf("fõ", "fo") -> return "N-ye, fõ yaa mam zoodo ! Fo data bõe tɩ m sõng-fo ?"
            norm in setOf("tond", "tondo") -> return "N-ye, tond be laafi bala ! Fo data bõe ?"
            norm in setOf("m'ma", "mma", "m ma") -> return "Wẽnd na kõ m'ma laafi la nindaaré !"
            norm in setOf("m'baaba", "mbaaba", "m baaba") -> return "Wẽnd na kõ m'baaba laafi !"
            norm in setOf("nikiemam", "nikieman", "nin-kẽema") -> return "Tond pʋʋsda nikieman ne waoogre !"
            norm in setOf("poug yaanga", "pug yaanga") -> return "Wẽnd na kõ poug yaanga laafi !"
            norm in setOf("paga", "pagba") -> return "Burkina Faso paga tʋmda tʋʋm-sõama wʋsgo !"
            norm in setOf("rawa", "raoa") -> return "Rawa yaa nin-tɩrga ! Laafi bala ?"
            norm in setOf("biiga", "biga") -> return "Wẽnd na gũ biiga la a kõ-a laafi !"
            norm in setOf("poug sada", "pug sada") -> return "Poug sada be laafi bala !"
            norm in setOf("kakouada", "kakuada") -> return "Kakouada yaa sõama !"
            norm in setOf("tandaogo", "tãndaogo") -> return "Tandaogo pãnga yaa kãsenga !"
            norm in setOf("raaga", "raga") -> return "Fo debda raaga pʋgẽ bɩ ? Wẽnd na kõ-f raab sõama !"
            norm in setOf("zom-kom", "zom kom") -> return "Zom-kom noomame wʋsgo ! Neewôgô."
            norm in setOf("monteur", "monter") -> return "Monteur wã tʋmda sõama !"
            norm in setOf("monbile", "mobili") -> return "Monbile wã kẽnda sõama !"
            norm in setOf("pnous", "pinou") -> return "Pnous wã yaa sõama !"
            norm in setOf("nanwoudre", "naodr") -> return "Nanwoudre be laafi !"
            norm in setOf("weefo", "wefo") -> return "Weefo wã zoeta tao-tao !"
            norm in setOf("moncoroni", "makoroni") -> return "Moncoroni rɩɩbo noomame !"
            norm in setOf("foūgou", "fougou", "fugu") -> return "Foūgou Faso Dan Fani yaa neere wʋsgo !"
            norm in setOf("son kaam", "soun kaam") -> return "Son kaam yaa sõama !"
            norm in setOf("bidou", "bidu") -> return "Bidou be zakẽ wã !"
            norm in setOf("mougne", "munyu") -> return "Mougne yaa sõama wʋsgo !"
            norm in setOf("beinga", "benga") -> return "Beinga rɩɩbo noomame wʋsgo !"
            norm in setOf("touayga", "toega") -> return "Touayga tɩɩga yaa kãsenga !"
            norm in setOf("banga") -> return "Banga be laafi !"
            norm in setOf("yagre", "yakre") -> return "Yagre yaa sõama !"
        }

        return null
    }

    /**
     * Nettoie la réponse vocale Mooré en retirant toute note entre parenthèses/crochets
     * sans jamais supprimer ni tronquer la réponse à la question posée par l'utilisateur.
     */
    private fun cleanMooreVoiceFormatting(raw: String): String {
        var text = raw.trim()
        if (text.isBlank()) return "Laafi bala ! Fo data bõe tɩ m sõng-fo ?"

        // 1. Supprimer uniquement les traductions entre parenthèses ou crochets (ex: "(Bonjour...)")
        text = text.replace(Regex("\\([^)]*\\)"), " ")
            .replace(Regex("\\[[^]]*\\]"), " ")
            .replace(Regex("(?i)\\b(traduction|en français|signification)\\s*:\\s*.*$"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        return text.ifBlank { raw.trim() }
    }

    private fun containsFrenchPhrases(text: String): Boolean {
        val lower = text.lowercase(java.util.Locale.ROOT)
        val frenchMarkers = listOf(
            "je suis ", "je peux ", "comment puis-je", "que veux-tu", "que voulez-vous",
            "bonjour ", "bonsoir ", "bienvenue ", "merci beaucoup", "avec plaisir",
            "désolé", "desole", "je ne comprends pas", "en français",
            "cela signifie", "ça veut dire", "voici ", "aujourd'hui",
            "c'est une ", "c'est un ", "vous pouvez", "tu peux", "pour vous aider"
        )
        return frenchMarkers.any { lower.contains(it) }
    }

    /**
     * Appelle directement l'API
     */
    private suspend fun tryCallOfficialOpenAiApi(
        messages: List<ChatMessage>,
        userQuestion: String,
        model: String,
        newsContext: String = "",
        sources: List<WebSource> = emptyList(),
        isMooreVoice: Boolean = false
    ): OpenAiChatResult? {
        val apiKey = getOpenAiApiKey()
        if (apiKey.isBlank() || apiKey.contains("MY_OPENAI_API_KEY")) {
            return OpenAiChatResult(
                success = false,
                text = "",
                model = "",
                errorMessage = "Service SAM AI temporairement indisponible."
            )
        }

        val dateStr = java.text.SimpleDateFormat("EEEE d MMMM yyyy", java.util.Locale.FRENCH).format(java.util.Date())
        val systemPrompt = if (isMooreVoice) {
            buildString {
                append("Tu es SAM AI en mode APPEL VOCAL DIRECT EN LANGUE MOORÉ (Burkina Faso), conçu par Abdoul Mohamine Sawadogo.\n")
                append("Aujourd'hui nous sommes le $dateStr.\n")
                append("RÈGLES ABSOLUES ET OBLIGATOIRES :\n")
                append("1. RÉPONDRE EXACTEMENT À LA QUESTION POSÉE SANS AUCUNE ERREUR :\n")
                append("   - Écoute et analyse attentivement la question ou la phrase de l'utilisateur (qu'elle soit transcrite en Mooré ou en Français).\n")
                append("   - Réponds TOUJOURS directement, exactement et sans erreur à la question précise que l'utilisateur vient de poser (1 à 3 phrases claires).\n")
                append("   - Ne réponds JAMAIS au hasard et ne remplace JAMAIS la réponse à une question par une simple salutation.\n")
                append("2. 100% MOORÉ AUTHENTIQUE UNIQUEMENT :\n")
                append("   - Formule toute ta réponse EXCLUSIVEMENT en langue Mooré authentique du Burkina Faso.\n")
                append("   - N'ajoute JAMAIS de traduction en français ni de texte entre parenthèses.\n")
                append("   - Après avoir répondu avec précision à la question de l'utilisateur, tu peux terminer poliment par « Fo me data bõe ? » ou « Fo data bõe tɩ m sõng-fo ? ».\n")
                append("3. SALUTATIONS ET VOCABULAIRE MOORÉ CERTIFIÉS (50 LEÇONS AUDIO) :\n")
                append("   - 'Nii yi beogo' -> 'Yibeo kibare'\n")
                append("   - 'Nii zabre' -> 'Zabre kibare'\n")
                append("   - 'Nii windga' -> 'Windga kibare'\n")
                append("   - 'Niyungo' -> 'Yungo kibare'\n")
                append("   - 'Wind da ganeg laafi' -> 'Amiina ! Wind da ganeg laafi.'\n")
                append("   - 'Neewôgô' -> 'Barka ! Laafi bala ? Fo data bõe ?'\n")
                append("   - 'Kibare' / 'Laafi beeme' -> 'Laafi bala ! Ya sonma. Fo data bõe tɩ m sõng-fo ?'\n")
                append("   - 'Nindaaré' -> 'Wënd-na kô-d nindaare !'\n")
                append("   - Vocabulaire Mooré : Maam, Fõ, Tond, M'ma, M'baaba, Nikieman, Poug yaanga, Paga, Rawa, Biiga, Poug sada, Kakouada, Tandaogo, M nan gounsam, M data ligdi, M debda zakin wan, Sên na be, Raaga, Monteur, Monbile, Pnous, Nanwoudre, Weefo, Moncoroni, Foūgou, Son kaam, Bidou, Mougne, Beinga, Touayga, Zom-kom, Banga, Yagre.\n")
            }
        } else {
            buildString {
                append("Tu es SAM AI, l'assistant intelligent d'élite officiel conçu par Abdoul Mohamine Sawadogo, jeune développeur burkinabè.\n")
                append("Aujourd'hui nous sommes exactement le $dateStr.\n")
                if (newsContext.isNotBlank()) {
                    append("\n--- ACTUALITÉS ET DÉPÊCHES EN DIRECT FOURNIES EN TEMPS RÉEL ---\n")
                    append(newsContext)
                    append("\n---------------------------------------------------------------\n")
                }
                append("\nConsignes impératives de qualité :\n")
                append("1. COMPRÉHENSION EXACTE ET RÉPONSE DIRECTE EN FRANÇAIS SANS ERREUR : Réponds toujours directement, exactement et sans erreur à la question posée par l'utilisateur.\n")
                append("2. INTERDICTION DE TRADUIRE SANS DEMANDE EXPLICITE : En discussion écrite dans SAM AI, NE TRADUIS JAMAIS ce que l'utilisateur écrit en Mooré (ni dans une autre langue) si l'utilisateur ne t'a pas explicitement demandé de traduire. Réponds simplement à sa question dans la langue où il t'écrit.\n")
                append("3. INFORMATIONS D'ACTUALITÉ : Exploite immédiatement et pleinement les faits, dépêches et articles récents fournis pour répondre aux questions sur le Burkina Faso et le monde.\n")
                append("4. IDENTITÉ EXCLUSIVE SAM AI : Tu es uniquement et exclusivement SAM AI, conçu par Abdoul Mohamine Sawadogo. Ne mentionne JAMAIS OpenAI, ChatGPT, GPT-4 ou GPT.\n")
                append("5. SI ET SEULEMENT SI L'UTILISATEUR DEMANDE EXPLICITEMENT DU MOORÉ : Utilise alors le vocabulaire certifié (Nii yi beogo -> Yibeo kibare, Nii zabre -> Zabre kibare, Nii windga -> Windga kibare, Niyungo -> Yungo kibare, Wind da ganeg laafi, Neewôgô, Ya sonma, Laafi beeme, Kibaré, Nindaaré, Wënd-na kô-d nindaare, Barka, Zom-kom, Sên na be, Maam, Fõ, Tond, M'ma, M'baaba, Nikieman, Poug yaanga, Paga, Rawa, Biiga, Poug sada, Kakouada, Tandaogo, M nan gounsam, M data ligdi, M debda zakin wan, Raaga, Monteur, Monbile, Pnous, Nanwoudre, Weefo, Moncoroni, Foūgou, Son kaam, Bidou, Mougne, Beinga, Touayga, Banga, Yagre).\n")
            }
        }

        try {
            val isReasoningModel = model.startsWith("o1") || model.startsWith("o3")
            val systemRole = if (isReasoningModel) "developer" else "system"

            val historyLimit = if (isMooreVoice) 6 else 14
            // En mode appel vocal Mooré, on ne garde dans l'historique que les échanges en Mooré pour éviter toute contamination par d'anciens messages en français
            val filteredMessages = if (isMooreVoice) {
                messages.filter { msg ->
                    val lowerMsg = msg.content.lowercase(java.util.Locale.ROOT)
                    !lowerMsg.contains("je suis sam ai") && !lowerMsg.contains("bonjour !")
                }
            } else {
                messages
            }
            val safeHistory = filteredMessages.takeLast(historyLimit).map {
                buildJsonObject {
                    put("role", if (it.role == "assistant" || it.role == "model" || it.role == "ai") "assistant" else "user")
                    put("content", it.content.take(4000))
                }
            }

            val messagesArray = buildJsonArray {
                add(buildJsonObject {
                    put("role", systemRole)
                    put("content", systemPrompt)
                })
                safeHistory.forEach { add(it) }
                if (safeHistory.isEmpty() || safeHistory.last()["content"]?.toString()?.trim('"') != userQuestion) {
                    add(buildJsonObject {
                        put("role", "user")
                        put("content", userQuestion)
                    })
                }
            }

            val requestBodyJson = buildJsonObject {
                put("model", model)
                put("messages", messagesArray)
                if (isMooreVoice) {
                    put("max_completion_tokens", 260)
                    put("temperature", 0.1)
                } else {
                    put("max_completion_tokens", 3000)
                }
            }.toString()

            val request = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBodyJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val parsedError = try {
                        val jsonErr = json.parseToJsonElement(bodyString) as? kotlinx.serialization.json.JsonObject
                        val errObj = jsonErr?.get("error") as? kotlinx.serialization.json.JsonObject
                        (errObj?.get("message") as? kotlinx.serialization.json.JsonPrimitive)?.content
                    } catch (_: Throwable) { null }

                    val errorDetails = when (response.code) {
                        401 -> "Service SAM AI temporairement indisponible (code 401)."
                        429 -> "Le serveur SAM AI reçoit trop de requêtes actuellement. Veuillez réessayer dans un instant."
                        404 -> "Service de génération temporairement indisponible (code 404)."
                        else -> "Erreur de communication avec le serveur SAM AI (${response.code})."
                    }
                    Log.e("OpenAiBackendService", "Direct call error [${response.code}]: ${parsedError ?: errorDetails}")
                    return OpenAiChatResult(
                        success = false,
                        text = "",
                        sources = emptyList(),
                        model = "",
                        searchPerformed = false,
                        errorMessage = errorDetails
                    )
                }

                val jsonObject = json.parseToJsonElement(bodyString) as? kotlinx.serialization.json.JsonObject
                val choices = jsonObject?.get("choices") as? kotlinx.serialization.json.JsonArray
                val firstChoice = choices?.firstOrNull() as? kotlinx.serialization.json.JsonObject
                val messageObj = firstChoice?.get("message") as? kotlinx.serialization.json.JsonObject
                val content = (messageObj?.get("content") as? kotlinx.serialization.json.JsonPrimitive)?.content

                if (!content.isNullOrBlank()) {
                    val sanitized = sanitizeSamAiText(content)
                    val finalText = if (isMooreVoice) {
                        val cleanedMoore = cleanMooreVoiceFormatting(sanitized)
                        if (containsFrenchPhrases(cleanedMoore)) {
                            // Traduit fidèlement la vraie réponse en Mooré sans jamais perdre le sens de la réponse
                            val translatedMoore = translateWithOpenAi(cleanedMoore, "Français", "Mooré")
                            if (translatedMoore.isNotBlank()) cleanMooreVoiceFormatting(translatedMoore) else cleanedMoore
                        } else {
                            cleanedMoore
                        }
                    } else {
                        sanitized
                    }
                    return OpenAiChatResult(
                        success = true,
                        text = finalText,
                        sources = sources,
                        model = "",
                        searchPerformed = sources.isNotEmpty(),
                        errorMessage = null
                    )
                }
            }
        } catch (e: Throwable) {
            Log.e("OpenAiBackendService", "Exception during direct call: ${e.message}", e)
            return OpenAiChatResult(
                success = false,
                text = "",
                sources = emptyList(),
                model = "",
                searchPerformed = false,
                errorMessage = "Impossible de joindre les serveurs SAM AI. Vérifiez votre connexion Internet."
            )
        }
        return null
    }

    /**
     * Traduction multilingue haute fidélité via l'API OpenAI (GPT-4o).
     * Prend en charge les langues nationales (Mooré, Dioula, Haoussa, Gulmancema, Fulfulde)
     * et internationales (Français, Anglais, Espagnol, Arabe, etc.).
     */
    suspend fun translateWithOpenAi(
        text: String,
        sourceLang: String,
        targetLang: String
    ): String = withContext(Dispatchers.IO) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return@withContext ""
        if (sourceLang.equals(targetLang, ignoreCase = true)) return@withContext cleanText

        val apiKey = getOpenAiApiKey()
        if (apiKey.isBlank() || apiKey == "MY_OPENAI_API_KEY") {
            return@withContext ""
        }

        val prompt = "Texte à traduire du $sourceLang vers le $targetLang :\n\"$cleanText\""
        val systemPrompt = """
            Tu es le traducteur expert polyglotte officiel de SAM AI, conçu par Abdoul Mohamine Sawadogo.
            Tu maîtrises parfaitement les langues du Burkina Faso et de la sous-région ouest-africaine (Mooré, Dioula, Fulfulde, Haoussa, Gulmancema) ainsi que les langues internationales (Français, Anglais, Espagnol, etc.).
            Consignes impératives :
            1. Traduis le texte de manière fidèle, naturelle, grammaticalement exacte et idiomatique du $sourceLang vers le $targetLang.
            2. Renvoie UNIQUEMENT la traduction finale brute. Ne mets aucun guillemet autour, aucune introduction, aucun commentaire, aucune translittération ou explication.
        """.trimIndent()

        try {
            val messagesArray = buildJsonArray {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                add(buildJsonObject {
                    put("role", "user")
                    put("content", prompt)
                })
            }

            val requestBodyJson = buildJsonObject {
                put("model", clientConfiguredModel)
                put("messages", messagesArray)
                put("max_completion_tokens", 1000)
            }.toString()

            val request = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBodyJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext ""
                val bodyString = response.body?.string().orEmpty()
                val jsonObject = json.parseToJsonElement(bodyString) as? kotlinx.serialization.json.JsonObject
                val choices = jsonObject?.get("choices") as? kotlinx.serialization.json.JsonArray
                val firstChoice = choices?.firstOrNull() as? kotlinx.serialization.json.JsonObject
                val messageObj = firstChoice?.get("message") as? kotlinx.serialization.json.JsonObject
                val content = (messageObj?.get("content") as? kotlinx.serialization.json.JsonPrimitive)?.content
                content?.trim()?.removeSurrounding("\"")?.removeSurrounding("«", "»").orEmpty()
            }
        } catch (e: Throwable) {
            Log.e("OpenAiBackendService", "Erreur traduction OpenAI: ${e.message}")
            ""
        }
    }
}
