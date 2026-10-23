package com.example.data.remote

import android.util.Log
import com.example.BuildConfig
import com.example.data.local.WebSource
import com.example.data.repository.ChatMessage
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
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
    val model: String = "gpt-4o",
    val searchPerformed: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Service client pour l'intégration officielle d'OpenAI dans SAM AI.
 * Priorise la fonction serveur Firebase Cloud Functions (`samAiChat`)
 * avec gestion sécurisée des secrets côté serveur et recherche Web en direct.
 */
class OpenAiBackendService(
    private val tavilySearchService: TavilySearchService? = null
) {
    private val functions by lazy {
        try {
            FirebaseFunctions.getInstance()
        } catch (_: Throwable) {
            null
        }
    }

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
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
        }.ifBlank { "gpt-4o" }
    }

    private fun getOpenAiApiKey(): String {
        return try {
            BuildConfig.OPENAI_API_KEY.trim().removeSurrounding("\"").removeSurrounding("'").trim()
        } catch (_: Throwable) {
            ""
        }
    }

    /**
     * Envoie une question ou un historique complet de discussion à OpenAI.
     */
    suspend fun sendChatMessage(
        messages: List<ChatMessage>,
        userQuestion: String,
        enableWebSearch: Boolean = true,
        preferredModel: String = ""
    ): OpenAiChatResult = withContext(Dispatchers.IO) {
        val cleanQuestion = userQuestion.trim()
        if (cleanQuestion.isBlank() && messages.isEmpty()) {
            return@withContext OpenAiChatResult(
                success = false,
                errorMessage = "Le message est vide."
            )
        }

        val effectiveModel = preferredModel.ifBlank { clientConfiguredModel }

        // 1. APPEL DIRECT À L'API OFFICIELLE OPENAI (Option B demandée par l'utilisateur)
        val directOpenAiResult = tryCallOfficialOpenAiApi(
            messages = messages,
            userQuestion = cleanQuestion,
            enableWebSearch = enableWebSearch,
            model = effectiveModel
        )

        if (directOpenAiResult != null) {
            // Si la requête a réussi ou a retourné une erreur API explicite (quota, clé invalide...), on renvoie le résultat directement
            if (directOpenAiResult.success || !directOpenAiResult.errorMessage.isNullOrBlank()) {
                return@withContext directOpenAiResult
            }
        }

        // 2. TENTATIVE VIA FIREBASE CLOUD FUNCTIONS (Architecture serveur si déployée)
        val cloudFunctionsResult = tryCallFirebaseCloudFunction(
            messages = messages,
            question = cleanQuestion,
            enableWebSearch = enableWebSearch,
            model = effectiveModel
        )

        if (cloudFunctionsResult != null && cloudFunctionsResult.success && cloudFunctionsResult.text.isNotBlank()) {
            return@withContext cloudFunctionsResult
        }

        // 3. SECOURS DIRECT HAUTE DISPONIBILITÉ VIA GATEWAY OFFICIELLE OPENROUTER
        val directResult = tryCallDirectOpenAiGateway(
            messages = messages,
            userQuestion = cleanQuestion,
            enableWebSearch = enableWebSearch,
            preferredModel = effectiveModel
        )

        if (directResult != null && directResult.success && directResult.text.isNotBlank()) {
            return@withContext directResult
        }

        // 4. SECOURS LOCAL INTELLIGENT AUTONOME (Mode hors-ligne ou réseau indisponible)
        val fallbackText = com.example.data.engine.AutonomousKnowledgeEngine.answer(cleanQuestion, messages)
        OpenAiChatResult(
            success = true,
            text = fallbackText,
            sources = emptyList(),
            model = "sam-ai-local",
            searchPerformed = false,
            errorMessage = null
        )
    }

    /**
     * Appelle directement l'API officielle OpenAI (https://api.openai.com/v1/chat/completions)
     */
    private suspend fun tryCallOfficialOpenAiApi(
        messages: List<ChatMessage>,
        userQuestion: String,
        enableWebSearch: Boolean,
        model: String
    ): OpenAiChatResult? {
        val apiKey = getOpenAiApiKey()
        if (apiKey.isBlank() || apiKey.contains("MY_OPENAI_API_KEY")) {
            return null
        }

        // Recherche Web en direct si activée et nécessaire
        val webSources = mutableListOf<WebSource>()
        var webContext = ""
        val needsSearch = enableWebSearch && isSearchQuery(userQuestion)

        if (needsSearch && tavilySearchService != null) {
            val tavilyKey = try {
                BuildConfig.TAVILY_API_KEY.trim().removeSurrounding("\"").removeSurrounding("'").trim()
            } catch (_: Throwable) { "" }

            if (tavilyKey.isNotBlank() && !tavilyKey.startsWith("tvly-your")) {
                try {
                    val searchResp = tavilySearchService.search(
                        query = userQuestion,
                        apiKey = tavilyKey,
                        isNews = true
                    )
                    if (searchResp != null && searchResp.results.isNotEmpty()) {
                        searchResp.results.filter { it.url.isNotBlank() }.forEach { r ->
                            val domain = try {
                                java.net.URI(r.url).host?.removePrefix("www.").orEmpty()
                            } catch (_: Throwable) { "" }
                            webSources.add(
                                WebSource(
                                    title = r.title.ifBlank { domain.ifBlank { "Source Web" } },
                                    url = r.url,
                                    domain = domain
                                )
                            )
                        }
                        webContext = buildString {
                            appendLine("\n=== INFORMATIONS VÉRIFIÉES DU WEB EN DIRECT ===")
                            if (!searchResp.answer.isNullOrBlank()) {
                                appendLine("Synthèse : ${searchResp.answer}")
                            }
                            searchResp.results.forEachIndexed { i, r ->
                                appendLine("[Source ${i + 1}] Titre : ${r.title}")
                                appendLine("URL : ${r.url}")
                                appendLine("Extrait : ${r.content}")
                            }
                            appendLine("=== FIN DES INFORMATIONS DU WEB ===")
                            appendLine("Consigne : Base ta réponse avec précision sur ces faits récents vérifiés et cite les sources.")
                        }
                    }
                } catch (e: Throwable) {
                    Log.w("OpenAiBackendService", "Recherche web Tavily échouée: ${e.message}")
                }
            }
        }

        val dateStr = java.text.SimpleDateFormat("EEEE d MMMM yyyy", java.util.Locale.FRENCH).format(java.util.Date())
        val systemPrompt = """
            Tu es SAM AI, l'assistant d'élite officiel propulsé par OpenAI et conçu par Abdoul Mohamine Sawadogo, jeune développeur burkinabè.
            Aujourd'hui nous sommes exactement le $dateStr.
            Consignes impératives de qualité :
            1. RÉPONSE DIRECTE : Réponds immédiatement et directement à la question sans introduction robotique ni formule stéréotypée ("Merci pour votre question", etc.).
            2. APPROFONDISSEMENT UTILE : Si l'utilisateur demande de détailler ou développer, donne des explications concrètes, structurées et complètes.
            3. INCERTITUDE : Signale clairement si un fait récent est incertain ou non confirmé.
            4. Ne mentionne ton créateur que si expressément demandé.
            $webContext
        """.trimIndent()

        try {
            val safeHistory = messages.takeLast(16).map {
                buildJsonObject {
                    put("role", if (it.role == "assistant" || it.role == "model" || it.role == "ai") "assistant" else "user")
                    put("content", it.content.take(4000))
                }
            }

            val isReasoningModel = model.startsWith("o1") || model.startsWith("o3")
            val systemRole = if (isReasoningModel) "developer" else "system"
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
                put("max_completion_tokens", 3000)
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
                        401 -> "Clé API OpenAI non valide (code 401). Vérifiez la clé configurée."
                        429 -> "Quota OpenAI dépassé ou solde insuffisant (code 429). Vérifiez votre solde sur platform.openai.com."
                        404 -> "Modèle '$model' introuvable ou non autorisé sur votre compte OpenAI (code 404)."
                        else -> parsedError ?: "Erreur HTTP ${response.code} renvoyée par OpenAI."
                    }
                    Log.e("OpenAiBackendService", "OpenAI direct call error [${response.code}]: $errorDetails")
                    return OpenAiChatResult(
                        success = false,
                        text = "",
                        sources = emptyList(),
                        model = model,
                        searchPerformed = false,
                        errorMessage = errorDetails
                    )
                }

                val jsonObject = json.parseToJsonElement(bodyString) as? kotlinx.serialization.json.JsonObject
                val choices = jsonObject?.get("choices") as? kotlinx.serialization.json.JsonArray
                val firstChoice = choices?.firstOrNull() as? kotlinx.serialization.json.JsonObject
                val messageObj = firstChoice?.get("message") as? kotlinx.serialization.json.JsonObject
                val content = (messageObj?.get("content") as? kotlinx.serialization.json.JsonPrimitive)?.content
                val actualModel = (jsonObject?.get("model") as? kotlinx.serialization.json.JsonPrimitive)?.content ?: model

                if (!content.isNullOrBlank()) {
                    return OpenAiChatResult(
                        success = true,
                        text = content.trim(),
                        sources = webSources,
                        model = actualModel,
                        searchPerformed = webSources.isNotEmpty(),
                        errorMessage = null
                    )
                }
            }
        } catch (e: Throwable) {
            Log.e("OpenAiBackendService", "Exception during direct OpenAI call: ${e.message}", e)
            return OpenAiChatResult(
                success = false,
                text = "",
                sources = emptyList(),
                model = model,
                searchPerformed = false,
                errorMessage = "Erreur de connexion : ${e.message ?: "Impossible de joindre les serveurs OpenAI."}"
            )
        }
        return null
    }

    /**
     * Appelle la fonction Firebase Cloud Functions "samAiChat"
     */
    private suspend fun tryCallFirebaseCloudFunction(
        messages: List<ChatMessage>,
        question: String,
        enableWebSearch: Boolean,
        model: String
    ): OpenAiChatResult? {
        val f = functions ?: return null
        return try {
            val payload = hashMapOf(
                "question" to question,
                "messages" to messages.takeLast(20).map {
                    hashMapOf(
                        "role" to if (it.role == "assistant" || it.role == "model") "assistant" else "user",
                        "content" to it.content.take(4000)
                    )
                },
                "enableWebSearch" to enableWebSearch,
                "model" to model
            )

            val httpsCallable = f.getHttpsCallable("samAiChat")
            val taskResult = httpsCallable.call(payload).await()
            val dataMap = taskResult.getData() as? Map<*, *> ?: return null

            val success = dataMap["success"] as? Boolean ?: true
            val text = (dataMap["text"] as? String).orEmpty().trim()
            val returnedModel = (dataMap["model"] as? String) ?: model
            val searchPerformed = dataMap["searchPerformed"] as? Boolean ?: false
            val sourcesList = mutableListOf<WebSource>()

            val rawSources = dataMap["sources"] as? List<*>
            rawSources?.forEach { item ->
                if (item is Map<*, *>) {
                    val title = (item["title"] as? String).orEmpty().ifBlank { "Source Web" }
                    val url = (item["url"] as? String).orEmpty()
                    val domain = (item["domain"] as? String).orEmpty()
                    if (url.isNotBlank()) {
                        sourcesList.add(WebSource(title = title, url = url, domain = domain))
                    }
                }
            }

            if (text.isNotBlank()) {
                OpenAiChatResult(
                    success = success,
                    text = text,
                    sources = sourcesList,
                    model = returnedModel,
                    searchPerformed = searchPerformed
                )
            } else {
                null
            }
        } catch (fe: FirebaseFunctionsException) {
            Log.w("OpenAiBackendService", "Firebase Cloud Functions samAiChat code: ${fe.code}, message: ${fe.message}")
            null
        } catch (e: Throwable) {
            Log.w("OpenAiBackendService", "Firebase Cloud Functions call error: ${e.message}")
            null
        }
    }

    /**
     * Passerelle directe vers OpenAI / OpenRouter avec recherche Web en direct
     */
    private suspend fun tryCallDirectOpenAiGateway(
        messages: List<ChatMessage>,
        userQuestion: String,
        enableWebSearch: Boolean,
        preferredModel: String
    ): OpenAiChatResult? {
        // Recherche Web préliminaire si nécessaire pour enrichir le prompt
        val webSources = mutableListOf<WebSource>()
        var webContext = ""
        val needsSearch = enableWebSearch && isSearchQuery(userQuestion)

        if (needsSearch && tavilySearchService != null) {
            val tavilyKey = try {
                BuildConfig.TAVILY_API_KEY.trim().removeSurrounding("\"").removeSurrounding("'").trim()
            } catch (_: Throwable) { "" }

            if (tavilyKey.isNotBlank() && !tavilyKey.startsWith("tvly-your")) {
                try {
                    val searchResp = tavilySearchService.search(
                        query = userQuestion,
                        apiKey = tavilyKey,
                        isNews = true
                    )
                    if (searchResp != null && searchResp.results.isNotEmpty()) {
                        searchResp.results.filter { it.url.isNotBlank() }.forEach { r ->
                            val domain = try {
                                java.net.URI(r.url).host?.removePrefix("www.").orEmpty()
                            } catch (_: Throwable) { "" }
                            webSources.add(
                                WebSource(
                                    title = r.title.ifBlank { domain.ifBlank { "Source Web" } },
                                    url = r.url,
                                    domain = domain
                                )
                            )
                        }
                        webContext = buildString {
                            appendLine("\n=== INFORMATIONS VÉRIFIÉES DU WEB EN DIRECT (TAVILY) ===")
                            if (!searchResp.answer.isNullOrBlank()) {
                                appendLine("Synthèse : ${searchResp.answer}")
                            }
                            searchResp.results.forEachIndexed { i, r ->
                                appendLine("[Source ${i + 1}] Titre : ${r.title}")
                                appendLine("URL : ${r.url}")
                                appendLine("Contenu : ${r.content}")
                            }
                            appendLine("=== FIN DES INFORMATIONS DU WEB ===")
                            appendLine("Consigne : Base ta réponse avec précision sur ces faits récents vérifiés et cite les sources.")
                        }
                    }
                } catch (e: Throwable) {
                    Log.w("OpenAiBackendService", "Recherche web de secours échouée: ${e.message}")
                }
            }
        }

        val dateStr = java.text.SimpleDateFormat("EEEE d MMMM yyyy", java.util.Locale.FRENCH).format(java.util.Date())
        val systemPrompt = """
            Tu es SAM AI, l'assistant d'élite officiel propulsé par OpenAI et conçu par Abdoul Mohamine Sawadogo, jeune développeur burkinabè.
            Aujourd'hui nous sommes exactement le $dateStr.
            Consignes impératives de qualité :
            1. RÉPONSE DIRECTE : Réponds immédiatement et directement à la question sans introduction robotique ni formule stéréotypée ("Merci pour votre question", etc.).
            2. APPROFONDISSEMENT UTILE : Si l'utilisateur demande de détailler ou développer, donne des explications concrètes, structurées et complètes.
            3. INCERTITUDE : Signale clairement si un fait récent est incertain ou non confirmé.
            4. Ne mentionne ton créateur que si expressément demandé.
            $webContext
        """.trimIndent()

        // Secours via passerelle OpenRouter si configurée
        val openRouterKey = try {
            BuildConfig.OPENROUTER_API_KEY.trim().removeSurrounding("\"").removeSurrounding("'").trim()
        } catch (_: Throwable) { "" }

        if (openRouterKey.isNotBlank() && openRouterKey.startsWith("sk-or-")) {
            val res = callOpenAiCompatibleEndpoint(
                endpointUrl = "https://openrouter.ai/api/v1/chat/completions",
                apiKey = openRouterKey,
                model = if (preferredModel.contains("/")) preferredModel else "openai/${preferredModel.ifBlank { "gpt-4o" }}",
                systemPrompt = systemPrompt,
                messages = messages,
                userQuestion = userQuestion
            )
            if (res != null) {
                return OpenAiChatResult(
                    success = true,
                    text = res,
                    sources = webSources,
                    model = preferredModel.ifBlank { "gpt-4o" },
                    searchPerformed = webSources.isNotEmpty()
                )
            }
        }

        return null
    }

    private fun callOpenAiCompatibleEndpoint(
        endpointUrl: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        messages: List<ChatMessage>,
        userQuestion: String
    ): String? {
        try {
            val safeHistory = messages.takeLast(10).map {
                buildJsonObject {
                    put("role", if (it.role == "assistant" || it.role == "model") "assistant" else "user")
                    put("content", it.content.take(2000))
                }
            }

            val isReasoning = model.startsWith("o1") || model.startsWith("o3")
            val systemRole = if (isReasoning) "developer" else "system"
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
                put("max_completion_tokens", 3000)
            }.toString()

            val request = Request.Builder()
                .url(endpointUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBodyJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w("OpenAiBackendService", "OpenAI HTTP error ${response.code}: ${response.message}")
                    return null
                }
                val bodyString = response.body?.string().orEmpty()
                val jsonObject = json.parseToJsonElement(bodyString) as? kotlinx.serialization.json.JsonObject
                val choices = jsonObject?.get("choices") as? kotlinx.serialization.json.JsonArray
                val firstChoice = choices?.firstOrNull() as? kotlinx.serialization.json.JsonObject
                val message = firstChoice?.get("message") as? kotlinx.serialization.json.JsonObject
                val content = message?.get("content")?.toString()?.trim('"')?.replace("\\n", "\n")?.replace("\\\"", "\"")
                if (!content.isNullOrBlank()) {
                    return content
                }
            }
        } catch (e: Throwable) {
            Log.w("OpenAiBackendService", "Error calling $endpointUrl: ${e.message}")
        }
        return null
    }

    private fun isSearchQuery(text: String): Boolean {
        val lower = text.lowercase(java.util.Locale.ROOT)
        val triggers = listOf(
            "score", "match", "résultat", "resultat", "classement", "météo", "meteo",
            "prix", "cours", "aujourd'hui", "ce soir", "hier", "demain", "cette semaine",
            "2026", "2025", "actualité", "actualite", "qui est", "président", "president",
            "gouvernement", "real madrid", "barcelone", "barca", "psg", "football", "can"
        )
        return triggers.any { lower.contains(it) }
    }
}
