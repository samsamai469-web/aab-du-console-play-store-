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

    private val clientConfiguredModel: String by lazy {
        try {
            BuildConfig.OPENAI_MODEL.trim().removeSurrounding("\"").removeSurrounding("'").trim()
        } catch (_: Throwable) {
            ""
        }.ifBlank { "gpt-4o-mini" }
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
            .replace(Regex("(?i)\\bGPT-4o(-mini|-2024-\\d{2}-\\d{2})?\\b"), "SAM AI")
            .replace(Regex("(?i)\\bGPT-4(-turbo)?\\b"), "SAM AI")
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

        // Utilisation prioritaire de gpt-4o-mini pour les échanges vocaux et conversationnels rapides (< 400 ms)
        val effectiveModel = if (isMooreVoice || (preferredModel.isBlank() && cleanQuestion.length <= 220 && newsContext.isBlank())) {
            "gpt-4o-mini"
        } else {
            preferredModel.ifBlank { clientConfiguredModel }
        }

        val directOpenAiResult = tryCallOfficialOpenAiApi(
            messages = messages,
            userQuestion = cleanQuestion,
            model = effectiveModel,
            newsContext = newsContext,
            sources = sources,
            isMooreVoice = isMooreVoice
        )

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

        // 1. SALUTATIONS ET DIALOGUES OBLIGATOIRES (avec réponse exacte enseignée dans l'audio, en 0 ms)
        when {
            // Audio #1 (Matin) : "Nii yi beogo" (= Bonjour) -> Réponse obligatoire : "Yibeo kibare"
            norm in setOf(
                "nii yi beogo", "ni yibeogo", "ni yi beogo", "ne y beogo", "ne yibeogo",
                "ni beogo", "ne beogo", "ney beogo", "n'yibeogo", "nei beogo", "ne yeogo",
                "nii yibeogo", "ni ibeogo", "ni ibéogo", "ni yibeo", "yibeogo", "ni i bogo", "ni bogo"
            ) || norm.startsWith("nii yi beogo") || norm.startsWith("ni yibeogo") ||
                (isMooreVoice && norm in setOf("bonjour", "salut", "bon matin", "hello")) ->
                return "Yibeo kibare"

            // Audio #2 (Soir) : "Nii zabre" (= Bonsoir) -> Réponse obligatoire : "Zabre kibare"
            norm in setOf(
                "nii zabre", "nii zaabre", "ni zabre", "ni zaabre", "ne zabre", "ne zaabre",
                "ne y zabre", "ne y zaabre", "ney zabre", "ney zaabre", "nezaabre", "ni sabre", "ni zabré"
            ) || norm.startsWith("nii zabre") || norm.startsWith("ni zabre") ||
                (isMooreVoice && norm in setOf("bonsoir", "bon soir")) ->
                return "Zabre kibare"

            // Audio #3 (Midi / Après-midi) : "Nii windga" (= Midi / Bon après-midi) -> Réponse obligatoire : "Windga kibare"
            norm in setOf(
                "nii windga", "ni windga", "ne windga", "ne y windga", "ne y wĩndga",
                "niwinga", "ne winga", "ne wĩnga", "nii ouindga", "ni ouindga", "ni winga", "nii winga"
            ) || norm.startsWith("nii windga") || norm.startsWith("ni windga") ||
                (isMooreVoice && norm in setOf("bon apres midi", "bon après-midi", "bon apres-midi", "midi")) ->
                return "Windga kibare"

            // Audio #4 (Nuit) : "Niyungo" / "Nii yungo" (= Bonsoir de nuit) -> Réponse obligatoire : "Yungo kibare"
            norm in setOf(
                "niyungo", "nii yungo", "ni yungo", "ne yungo", "ne y yungo", "ne y yʋngo",
                "n'yungo", "nii youngo", "ni youngo", "ni yougo"
            ) || norm.startsWith("niyungo") || norm.startsWith("nii yungo") ->
                return "Yungo kibare"

            // Audio #5 (Bonne nuit) : "Wind da ganeg laafi"
            norm in setOf(
                "wind da ganeg laafi", "wend na ganeg laafi", "wẽnd na gãneg laafi",
                "gang laafi", "gãng laafi", "ouenda ganag lafi", "winda ganeg lafi"
            ) || norm.contains("ganeg laafi") || (isMooreVoice && norm == "bonne nuit") ->
                return "Amiina ! Wind da ganeg laafi."

            // Audio #6 (Bienvenue) : "Neewôgô" / "Ne waongo"
            norm in setOf("neewôgô", "neewogo", "ne waongo", "ne waogo", "ne y waoongo", "waoongo", "newogo") ||
                (isMooreVoice && norm == "bienvenue") ->
                return "Barka ! Laafi bala ?"

            // Audio #7 (Réponse aux nouvelles) : "Kibare" / "Yibeo kibare" / "Zabre kibare" / "Windga kibare" / "Yungo kibare"
            norm in setOf(
                "kibare", "kibaré", "kibaare", "yibeo kibare", "yibéo kibaré", "zabre kibare", "zaabre kibare",
                "windga kibare", "yungo kibare", "fo laafi", "fo be laafi", "laafi beeme", "lafi beme", "lafi bémé"
            ) || (isMooreVoice && norm in setOf("comment ca va", "comment ça va", "ca va", "ça va", "comment vas tu", "comment allez vous")) ->
                return "Laafi bala ! Ya sonma."

            // Audio #9 (Ça va bien) : "Ya sonma" / "Laafi bala"
            norm in setOf("ya sonma", "ya soma", "yaa sõama", "yaa soama", "laafi bala", "lafi bala", "mam be laafi") ||
                (isMooreVoice && norm in setOf("ca va bien", "ça va bien", "je vais bien", "oui ca va")) ->
                return "Wẽnd na kõ-d laafi !"

            // Audio #10 (Merci) : "Barka" / "Barka wʋsgo"
            norm in setOf("barka", "barka wusgo", "barka wʋsgo", "barka woto", "barka ousgo") ||
                (isMooreVoice && norm in setOf("merci", "merci beaucoup")) ->
                return "Kaseto ka be ye ! Mam pʋsda fo."

            // Audio #11 (Au revoir) : "Nindaaré"
            norm in setOf("nindaare", "nindaaré", "bilmãse", "bilmase", "nindare") ||
                (isMooreVoice && norm in setOf("au revoir", "a bientot", "à bientôt", "bye")) ->
                return "Wënd-na kô-d nindaare !"

            // Audio #12 (Adieu / À la prochaine) : "Wënd-na kô-d nindaare"
            norm in setOf("wënd-na kô-d nindaare", "wend na ko-d nindaare", "wẽnd na kõ-d nindaare", "wenda kod nindaare") ||
                norm.contains("kod nindaare") || norm.contains("kô-d nindaare") ->
                return "Amiina ! Nindaaré."

            // Audio #13 (Je vais dormir) : "M nan gounsam"
            norm in setOf("m nan gounsam", "m na n gʋsame", "m na goussom", "m na goussem", "m'na goussom") ||
                norm.contains("nan gounsam") || norm.contains("na goussom") ||
                (isMooreVoice && norm == "je vais dormir") ->
                return "Wind da ganeg laafi !"

            // Audio #14 (Je vais à la maison) : "M debda zakin wan"
            norm in setOf("m debda zakin wan", "m debda zakin", "m debda zakẽ wã", "m'debda zakouan") ||
                norm.contains("debda zakin") ||
                (isMooreVoice && (norm == "je vais a la maison" || norm == "je vais à la maison")) ->
                return "Wẽnd na taase laafi ! Nindaaré."

            // Audio #15 (Je veux l'argent) : "M data ligdi"
            norm in setOf("m data ligdi", "mam data ligdi", "mam rata ligdi", "m'data ligdi") ||
                norm.contains("data ligdi") || norm.contains("rata ligdi") ||
                (isMooreVoice && (norm == "je veux l'argent" || norm == "je veux de l'argent")) ->
                return "Wẽnd na kõ-f ligdi la laafi !"

            // Audio #16 (L'actualité du jour / Nouvelles) : "Sên na be" / "Rũndã kibaaya"
            norm in setOf("sên na be", "sen na be", "rũndã kibaaya", "runda kibaya", "kibare bôe n be rũndã", "kibare boe n be runda") ->
                return "Rũndã kibaaya yaa sõama Burkina Faso pʋgẽ ! Laafi beeme."

            // Questions conversationnelles fréquentes en Appel Mooré
            norm.contains("fo yʋʋre") || norm.contains("fo yuure") || norm.contains("fo youre") ||
                (isMooreVoice && (norm.contains("comment tu t'appelles") || norm.contains("ton nom") || norm.contains("qui es tu"))) ->
                return "Mam yʋʋre la Same ! Maam la fo zoodo."

            norm.contains("bôe la sam ai") || norm.contains("boe la sam ai") || norm.contains("bõe la fo") || norm.contains("boe la fo") ||
                (isMooreVoice && (norm.contains("que peux tu faire") || norm.contains("que fais tu"))) ->
                return "Maam sõngda fõ ne Mooré la Burkina Faso kibaya !"

            norm.contains("yel-mam kibare ne burkina") || (isMooreVoice && norm.contains("burkina faso")) ->
                return "Burkina Faso yaa tẽng sõama ! Tond nongda Burkina Faso wʋsgo."
        }

        // 2. Si l'utilisateur prononce en mode Appel Mooré un mot du lexique des 50 audios,
        // SAM AI confirme immédiatement (0 ms) le mot en Mooré avec une phrase claire et juste sans erreur
        if (isMooreVoice) {
            val singleWordAck = mapOf(
                "maam" to "N-ye, maam la fo zoodo Same.",
                "fõ" to "N-ye, fõ yaa mam zoodo !",
                "fo" to "N-ye, fõ yaa mam zoodo !",
                "tond" to "N-ye, tond be laafi bala !",
                "tõndo" to "N-ye, tond be laafi bala !",
                "m'ma" to "Wẽnd na kõ m'ma laafi !",
                "ma" to "Wẽnd na kõ m'ma laafi !",
                "m'baaba" to "Wẽnd na kõ m'baaba laafi !",
                "baaba" to "Wẽnd na kõ m'baaba laafi !",
                "nikieman" to "Wẽnd na kõ nikieman laafi !",
                "nikiemam" to "Wẽnd na kõ nikieman laafi !",
                "poug yaanga" to "Wẽnd na kõ poug yaanga laafi !",
                "paga" to "Paga be laafi bala !",
                "rawa" to "Rawa be laafi bala !",
                "biiga" to "Biiga be laafi bala !",
                "poug sada" to "Poug sada be laafi bala !",
                "kakouada" to "Kakouada tʋmda sõama !",
                "tandaogo" to "Tandaogo tʋmda sõama !",
                "raaga" to "Fo debda raaga ? Wẽnd na taase laafi !",
                "weefo" to "N-ye, weefo beeme !",
                "monteur" to "N-ye, monteur beeme !",
                "monbile" to "N-ye, monbile beeme !",
                "pnous" to "N-ye, pnous yaa sõama !",
                "nanwoudre" to "N-ye, nanwoudre yaa sõama !",
                "moncoroni" to "N-ye, moncoroni noomame !",
                "foūgou" to "N-ye, foūgou yaa sõama !",
                "fougou" to "N-ye, foūgou yaa sõama !",
                "son kaam" to "N-ye, son kaam noomame !",
                "bidou" to "N-ye, bidou beeme !",
                "mougne" to "N-ye, mougne noomame !",
                "beinga" to "N-ye, beinga noomame !",
                "touayga" to "N-ye, touayga noomame !",
                "zom-kom" to "Zom-kom noomame wʋsgo !",
                "banga" to "N-ye, banga beeme !",
                "yagre" to "N-ye, yagre beeme !",
                "saame" to "N-ye, saame !",
                "sên ka be" to "N-ye, sên ka be !",
                "sen ka be" to "N-ye, sên ka be !",
                "siifu" to "N-ye, siifu beeme !",
                "wese" to "N-ye, baaga wesdame !",
                "yôsgre" to "N-ye, yôsgre ka sõama ye !",
                "yosgre" to "N-ye, yôsgre ka sõama ye !",
                "yâgme" to "N-ye, yâgme !",
                "yagme" to "N-ye, yâgme !",
                "sake" to "N-ye, maam sakame !",
                "doge" to "Wẽnd na kõ biiga laafi !",
                "pùg roagsá" to "Pùg Roagsá tʋmda sõama !",
                "pug roagsa" to "Pùg Roagsá tʋmda sõama !",
                "maane" to "N-ye, maam maandame !",
                "lagme" to "N-ye, tond lagmame !",
                "yâage" to "N-ye, maam yâagda fõ !",
                "yaage" to "N-ye, maam yâagda fõ !",
                "rodbo" to "N-ye, rodbo !",
                "da" to "Fo data n da bôe raaga ?",
                "ràada" to "N-ye, ràada be raaga !",
                "raada" to "N-ye, ràada be raaga !",
                "raabo" to "N-ye, raabo yaa sõama !",
                "yagle" to "N-ye, yagle !",
                "soge" to "N-ye, soge !",
                "baasè" to "N-ye, tʋʋma baasame !",
                "baase" to "N-ye, tʋʋma baasame !",
                "paoongo" to "N-ye, paoongo yaa sõama !",
                "manesem" to "N-ye, manesem yaa sõama !",
                "paasga" to "N-ye, paasga !",
                "paase" to "N-ye, paase !",
                "kê" to "N-ye, kê zakin !"
            )
            singleWordAck[norm]?.let { return it }
        }

        // 3. Si l'utilisateur demande en français la traduction d'un mot ou d'une expression des 50 audios
        if (!isMooreVoice) {
            val directMooreToFr = com.example.data.engine.DictionaryLookupEngine.translateDirect(deprolonged, "Mooré", "Français")
            if (directMooreToFr != null && deprolonged.split(Regex("\\s+")).size <= 4 &&
                norm !in setOf("bonjour", "bonsoir", "merci", "oui", "non", "midi", "homme", "femme", "enfant", "papa", "maman", "voiture", "moto", "velo", "vélo", "riz", "mais", "maïs", "fer", "habit", "bidon", "pneu")
            ) {
                return "En Mooré, **« $deprolonged »** signifie **« $directMooreToFr »** en français."
            }
        }

        return null
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
                append("Tu es Sam (SAM AI), un véritable ami burkinabè natif de Ouagadougou qui cause en direct au téléphone en langue MOORÉ avec l'utilisateur.\n")
                append("RÈGLES ABSOLUES POUR PARLER COMME DANS L'AUDIO DE RÉFÉRENCE ET RÉPONDRE VITE SANS ERREUR :\n")
                append("0. SALUTATIONS ET RÉPONSES OBLIGATOIRES :\n")
                append("   - 'Nii yi beogo' (Bonjour) -> 'Yibeo kibare'\n")
                append("   - 'Nii zabre' (Bonsoir) -> 'Zabre kibare'\n")
                append("   - 'Nii windga' (Midi / Bon après-midi) -> 'Windga kibare'\n")
                append("   - 'Niyungo' (Bonsoir de nuit) -> 'Yungo kibare'\n")
                append("   - 'Wind da ganeg laafi' (Bonne nuit) -> 'Amiina ! Wind da ganeg laafi.'\n")
                append("   - 'Neewôgô' (Bienvenue) -> 'Barka ! Laafi bala ?'\n")
                append("   - 'Kibare' / 'Laafi beeme' -> 'Laafi bala ! Ya sonma.'\n")
                append("   - 'Nindaaré' (Au revoir) -> 'Wënd-na kô-d nindaare !'\n")
                append("1. COMPRÉHENSION IMMÉDIATE : Comprends le sens exact de la question de l'utilisateur et réponds directement à sa question sans jamais répondre au hasard.\n")
                append("2. VOCABULAIRE CERTIFIÉ : Maam (Moi), Fõ (Toi), Tond (Nous), M'ma (Maman), M'baaba (Papa), Nikieman (Vieux), Poug yaanga (Vieille), Paga (Femme), Rawa (Homme), Biiga (Enfant), Poug sada (Fille), Kakouada (Cultivateur), Tandaogo (Orpailleur), M nan gounsam (Je vais dormir), M data ligdi (Je veux l'argent), M debda zakin wan (Je vais à la maison), Sên na be (Actualité du jour), Raaga (Marché), Monteur (Moto), Monbile (Voiture), Pnous (Pneu), Nanwoudre (Chaussure), Weefo (Vélo), Moncoroni (Spaghetti), Foūgou (Habit), Son kaam (Arachide), Bidou (Bidon), Mougne (Riz), Beinga (Haricot), Touayga (Gombo), Zom-kom (Boisson de bienvenue), Banga (Fer), Yagre (Caillou).\n")
                append("3. ZÉRO FRANÇAIS, ZÉRO ANGLAIS : Réponds en 1 phrase courte et directe en Mooré pur.\n")
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
                append("1. RÉPONSE DIRECTE ET FLUIDE : Réponds immédiatement et directement à la question sans introduction robotique.\n")
                append("2. INFORMATIONS D'ACTUALITÉ : Exploite immédiatement et pleinement les faits, dépêches et articles récents fournis pour répondre aux questions sur le Burkina Faso et le monde.\n")
                append("3. IDENTITÉ EXCLUSIVE SAM AI : Tu es uniquement et exclusivement SAM AI, conçu par Abdoul Mohamine Sawadogo. Ne mentionne JAMAIS OpenAI, ChatGPT, GPT-4 ou GPT.\n")
                append("4. MAÎTRISE CERTIFIÉE DU MOORÉ (50 LEÇONS AUDIO OFFICIELLES) :\n")
                append("   - Salutations et réponses obligatoires : 'Nii yi beogo' (Bonjour) -> réponse 'Yibeo kibare' ; 'Nii zabre' (Bonsoir) -> réponse 'Zabre kibare' ; 'Nii windga' (Midi / Bon après-midi) -> réponse 'Windga kibare' ; 'Niyungo' (Bonsoir de nuit) -> réponse 'Yungo kibare' ; 'Wind da ganeg laafi' (Bonne nuit) ; 'Neewôgô' (Bienvenue) ; 'Ya sonma' (Ça va bien) ; 'Laafi beeme' (Et la santé ?) ; 'Kibaré' (Comment ça va ?) ; 'Nindaaré' (Au revoir) ; 'Wënd-na kô-d nindaare' (Adieu) ; 'Barka' (Merci) ; 'Zom-kom' (Jus de petit mil / Boisson de bienvenue) ; 'Sên na be' (L'actualité du jour).\n")
                append("   - Pronoms, personnes et objets certifiés : Maam (Moi), Fõ (Toi), Tond (Nous), M'ma (Maman/Mère), M'baaba (Papa/Père), Nikieman (Un vieux), Poug yaanga (Une vieille), Paga (Une femme), Rawa (Un homme), Biiga (Un enfant), Poug sada (Une fille), Kakouada (Cultivateur), Tandaogo (Orpailleur), M nan gounsam (Je vais dormir), M data ligdi (Je veux l'argent), M debda zakin wan (Je vais à la maison), Raaga (Marché), Monteur (Moto), Monbile (Voiture), Pnous (Pneu), Nanwoudre (Chaussure), Weefo (Vélo), Moncoroni (Spaghetti), Foūgou (Habit), Son kaam (Arachide), Bidou (Bidon), Mougne (Du riz), Beinga (Haricot), Touayga (Gombo), Banga (Fer), Yagre (Caillou).\n")
                append("   - En mode Mooré, n'utilise jamais de mots français ni anglais.\n")
            }
        }

        try {
            val isReasoningModel = model.startsWith("o1") || model.startsWith("o3")
            val systemRole = if (isReasoningModel) "developer" else "system"

            val historyLimit = if (isMooreVoice) 6 else 14
            val safeHistory = messages.takeLast(historyLimit).map {
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
                    put("max_completion_tokens", 75)
                    put("temperature", 0.2)
                } else {
                    put("max_completion_tokens", 2000)
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
                    return OpenAiChatResult(
                        success = true,
                        text = sanitizeSamAiText(content),
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
