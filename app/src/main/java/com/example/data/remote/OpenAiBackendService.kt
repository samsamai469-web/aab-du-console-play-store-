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
        const val WEB_SEARCH_OPENAI_MODEL = "gpt-4o-search-preview"
        const val FAST_VOICE_OPENAI_MODEL = "gpt-4o-mini"
        private val VALID_OPENAI_MODELS = setOf(
            "gpt-4o",
            "gpt-4o-search-preview",
            "gpt-4o-mini",
            "gpt-4o-mini-search-preview",
            "gpt-4.1",
            "gpt-4.1-mini",
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

    private fun sanitizeSamAiText(raw: String, userQuestion: String = ""): String {
        var cleaned = raw
            .replace(Regex("(?i)\\bChatGPT\\b"), "SAM AI")
            .replace(Regex("(?i)\\bGPT-4(\\.1|\\.5|o)?(-mini|-turbo|-preview|-search-preview|-2024-\\d{2}-\\d{2})?\\b"), "SAM AI")
            .replace(Regex("(?i)\\bOpenAI\\b"), "SAM AI")
            .trim()

        val qLower = userQuestion.lowercase(java.util.Locale.ROOT).trim()
        val userExplicitlyCommandedGreeting = Regex(
            "(?i)\\b(dis|dire|répète|répéter|prononce|prononcer|écris|écrire)\\b.{0,25}\\b(bonjour|salut|bonsoir)\\b"
        ).containsMatchIn(qLower)
        if (!userExplicitlyCommandedGreeting) {
            cleaned = cleaned.replace(
                Regex("^(?i)(bonjour|salut|bonsoir)(\\s+à\\s+vous|\\s+tout\\s+le\\s+monde)?\\s*[!,.?:;\\-—]*\\s*"),
                ""
            ).replace(
                Regex("^(?i)(comment\\s+puis-je\\s+vous\\s+aider(\\s+aujourd'hui)?\\s*[?.,!]*\\s*)"),
                ""
            ).replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.FRENCH) else it.toString() }
        }
        return cleaned.trim()
    }

    /**
     * Envoie une question ou un historique complet de discussion à SAM AI
     * en activant la recherche Web en direct et le contexte de dépêches fraîches.
     */
    suspend fun sendChatMessage(
        messages: List<ChatMessage>,
        userQuestion: String,
        enableWebSearch: Boolean = false,
        preferredModel: String = "",
        newsContext: String = "",
        sources: List<WebSource> = emptyList(),
        isMooreVoice: Boolean = false,
        isVoiceCall: Boolean = false
    ): OpenAiChatResult = withContext(Dispatchers.IO) {
        val cleanQuestion = userQuestion.trim()
        if (cleanQuestion.isBlank() && messages.isEmpty()) {
            return@withContext OpenAiChatResult(
                success = false,
                errorMessage = "Le message est vide."
            )
        }

        // Si une recherche d'actualité en direct est demandée dans le chat écrit, on tente d'abord
        // le modèle de recherche Web en temps réel (gpt-4o-search-preview), puis repli sur gpt-4o
        val candidateModels = buildList {
            if (enableWebSearch && !isVoiceCall) {
                add(WEB_SEARCH_OPENAI_MODEL)
            }
            if (preferredModel.isNotBlank() && preferredModel in VALID_OPENAI_MODELS) {
                add(preferredModel)
            }
            add(FLAGSHIP_OPENAI_MODEL)
            add(FAST_VOICE_OPENAI_MODEL)
        }.distinct()

        var directOpenAiResult: OpenAiChatResult? = null
        for (candidateModel in candidateModels) {
            val attemptResult = tryCallOfficialOpenAiApi(
                messages = messages,
                userQuestion = cleanQuestion,
                model = candidateModel,
                newsContext = newsContext,
                sources = sources,
                enableWebSearch = enableWebSearch,
                isMooreVoice = false,
                isVoiceCall = isVoiceCall
            )
            if (attemptResult != null && attemptResult.success && attemptResult.text.isNotBlank()) {
                directOpenAiResult = attemptResult
                break
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
     * Mode 100% Français activé : désactive les réponses pré-enregistrées en Mooré pour laisser OpenAI répondre en français naturel.
     */
    fun resolveDeterministicMooreLessonReply(rawQuestion: String, isMooreVoice: Boolean): String? {
        return null
    }

    @Suppress("unused")
    private fun legacyResolveDeterministicMooreLessonReply(rawQuestion: String, isMooreVoice: Boolean): String? {
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
        enableWebSearch: Boolean = false,
        isMooreVoice: Boolean = false,
        isVoiceCall: Boolean = false
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
        val systemPrompt = if (isVoiceCall) {
            buildString {
                append("Tu es SAM AI, un homme chaleureux, naturel et ultra-précis. L'appel téléphonique avec l'utilisateur a DÉJÀ commencé.\n")
                append("Aujourd'hui nous sommes le $dateStr (octobre 2026).\n")
                if (newsContext.isNotBlank()) {
                    append("\n--- ACTUALITÉS ET DÉPÊCHES EN DIRECT FOURNIES EN TEMPS RÉEL ---\n")
                    append(newsContext.take(900))
                    append("\n---------------------------------------------------------------\n")
                }
                append("RÈGLES ABSOLUES D'APPEL TÉLÉPHONIQUE (ZÉRO ERREUR) :\n")
                append("1. INTERDICTION TOTALE DE DIRE 'BONJOUR' À CHAQUE RÉPONSE : Ne commence JAMAIS ta réponse par « Bonjour », « Salut » ou « Bonsoir » (sauf si l'utilisateur vient uniquement de dire « Bonjour » ou a demandé « Dis bonjour »). Entre directement dans la réponse dès le premier mot.\n")
                append("2. OBÉISSANCE EXACTE ET LITTÉRALE (DIRE OU FAIRE EXACTEMENT CE QUI EST DEMANDÉ) :\n")
                append("   - Si l'utilisateur te demande de DIRE, RÉPÉTER, PRONONCER ou LIRE quelque chose (ex : « Dis ... », « Répète ... », « Prononce ... »), réponds UNIQUEMENT et EXACTEMENT par les mots demandés par l'utilisateur, mot pour mot, sans dire « Bonjour », sans dire « D'accord » ou « Bien sûr », et sans erreur.\n")
                append("   - Si l'utilisateur te demande de FAIRE quelque chose ou pose une question, exécute sa demande avec une exactitude absolue (0% d'erreur) et sans jamais répondre à côté.\n")
                append("3. 100% FRANÇAIS ET TON D'HOMME HUMAIN : Parle en français naturel avec les accords masculins. Sois direct et concis (1 à 3 phrases claires, sauf si l'utilisateur demande plus).\n")
                append("4. DISTINCTION STRICTE DE CHAQUE QUESTION : Réponds uniquement au message actuel sans jamais reparler d'une ancienne question.\n")
                append("5. ACTUALITÉS EN DIRECT : Lorsque des dépêches sont fournies ci-dessus, utilise-les directement sans jamais répéter les mêmes phrases. Ne dis JAMAIS que tes connaissances s'arrêtent en 2023, 2024 ou 2025.\n")
                append("6. ZÉRO FORMATAGE ÉCRIT : N'utilise jamais d'astérisques (*), de listes à puces, de titres (#) ou d'émojis.\n")
                append("7. IDENTITÉ : Tu es SAM AI (créé par Abdoul Mohamine Sawadogo si on te demande qui t'a créé). Ne mentionne jamais OpenAI ou ChatGPT.\n")
            }
        } else {
            buildString {
                append("Tu es SAM AI, une intelligence artificielle d'élite, cultivée, brillante, polyvalente et experte, conçue par Abdoul Mohamine Sawadogo, jeune développeur burkinabè.\n")
                append("Aujourd'hui nous sommes exactement le $dateStr (octobre 2026).\n")
                if (newsContext.isNotBlank()) {
                    append("\n--- ACTUALITÉS ET DÉPÊCHES EN DIRECT FOURNIES EN TEMPS RÉEL ---\n")
                    append(newsContext)
                    append("\n---------------------------------------------------------------\n")
                }
                append("\nConsignes impératives d'excellence (ZÉRO ERREUR) :\n")
                append("1. INTERDICTION DE DIRE 'BONJOUR' À CHAQUE MESSAGE :\n")
                append("   - Ne commence JAMAIS tes réponses par « Bonjour », « Salut » ou « Bonsoir » sauf si l'utilisateur vient uniquement de te saluer.\n")
                append("2. OBÉISSANCE EXACTE ET LITTÉRALE À CE QUE L'UTILISATEUR DEMANDE DE DIRE OU DE FAIRE :\n")
                append("   - Si l'utilisateur te demande de DIRE, RÉPÉTER ou ÉCRIRE exactement un texte ou une phrase précise (ex : « Dis ... », « Répète ... »), renvoie EXACTEMENT et UNIQUEMENT ce qu'il a demandé, mot pour mot, sans préambule (« Bien sûr », « Bonjour ») et sans erreur.\n")
                append("   - Si l'utilisateur te donne une consigne précise ou pose une question, exécute exactement ce qu'il demande avec une précision factuelle absolue (0% d'erreur), sans jamais répondre à côté ni t'excuser inutilement.\n")
                append("3. DISTINCTION STRICTE DES QUESTIONS :\n")
                append("   - Si l'utilisateur pose une question au début de son message puis une autre question à la fin : s'il a changé d'avis, réponds à la dernière question ; s'il pose deux questions distinctes, traite chacune clairement sans revenir sur d'anciennes questions déjà résolues.\n")
                append("4. LANGUE FRANÇAISE IMPECCABLE :\n")
                append("   - Réponds toujours en français clair, fluide et naturel (sauf si l'utilisateur demande explicitement une traduction ou citation dans une autre langue).\n")
                append("5. ACTUALITÉS EN TEMPS RÉEL (BURKINA FASO & MONDE) ET INTERDICTION DE SE RÉPÉTER :\n")
                append("   - Lorsque l'utilisateur demande les actualités du Burkina Faso ou du monde, donne des informations réelles, récentes, concrètes et détaillées en t'appuyant sur la recherche Web en direct et les dépêches fournies ci-dessus.\n")
                append("   - Si la question porte sur le Burkina Faso, traite spécifiquement des actualités récentes du Burkina Faso. Si la question porte sur le monde, traite des actualités internationales.\n")
                append("   - INTERDICTION ABSOLUE DE RÉPÉTER LES MÊMES PHRASES : Vérifie tes réponses précédentes dans la conversation. Ne répète JAMAIS les mêmes phrases, les mêmes titres ni le même résumé déjà donné plus haut. À chaque nouvelle question sur l'actualité, présente de NOUVEAUX faits récents, d'autres dépêches et une rédaction entièrement nouvelle.\n")
                append("   - Ne dis JAMAIS que tes connaissances s'arrêtent en 2023, 2024 ou 2025, ni que tu n'as pas accès aux actualités en temps réel.\n")
                append("6. IDENTITÉ EXCLUSIVE SAM AI :\n")
                append("   - Tu es uniquement SAM AI, conçu par Abdoul Mohamine Sawadogo (ne le mentionne que si on te demande qui t'a créé). Ne mentionne JAMAIS OpenAI, ChatGPT, GPT-4 ou Claude.\n")
            }
        }

        try {
            val isReasoningModel = model.startsWith("o1") || model.startsWith("o3")
            val isSearchPreviewModel = model.contains("search-preview")
            val systemRole = if (isReasoningModel) "developer" else "system"

            val safeHistory = if (isVoiceCall) {
                emptyList()
            } else {
                messages.takeLast(12).map {
                    buildJsonObject {
                        put("role", if (it.role == "assistant" || it.role == "model" || it.role == "ai") "assistant" else "user")
                        put("content", it.content.take(4000))
                    }
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
                if (isSearchPreviewModel) {
                    put("max_completion_tokens", 3500)
                    put("web_search_options", buildJsonObject {
                        put("search_context_size", "medium")
                    })
                } else if (isVoiceCall) {
                    put("max_completion_tokens", 300)
                    put("temperature", if (enableWebSearch || newsContext.isNotBlank()) 0.45 else 0.15)
                } else {
                    put("max_completion_tokens", 3500)
                    if (!isReasoningModel) {
                        put("temperature", if (enableWebSearch || newsContext.isNotBlank()) 0.6 else 0.2)
                    }
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
                    Log.w("OpenAiBackendService", "Call status [${response.code}] on $model: ${parsedError ?: errorDetails}")
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

                val combinedSources = sources.toMutableList()
                val annotations = messageObj?.get("annotations") as? kotlinx.serialization.json.JsonArray
                annotations?.forEach { elem ->
                    val annObj = elem as? kotlinx.serialization.json.JsonObject
                    val urlCit = annObj?.get("url_citation") as? kotlinx.serialization.json.JsonObject
                    val url = (urlCit?.get("url") as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty().trim()
                    val title = (urlCit?.get("title") as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty().trim()
                    if (url.isNotBlank() && combinedSources.none { it.url == url }) {
                        val domain = try {
                            java.net.URI(url).host?.removePrefix("www.").orEmpty()
                        } catch (_: Throwable) { "" }
                        combinedSources.add(
                            WebSource(
                                title = title.ifBlank { domain.ifBlank { "Source Web" } },
                                url = url,
                                domain = domain
                            )
                        )
                    }
                }

                if (!content.isNullOrBlank()) {
                    val finalText = sanitizeSamAiText(content, userQuestion)
                    return OpenAiChatResult(
                        success = true,
                        text = finalText,
                        sources = combinedSources,
                        model = "",
                        searchPerformed = combinedSources.isNotEmpty() || isSearchPreviewModel,
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
