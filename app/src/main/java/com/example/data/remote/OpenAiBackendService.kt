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
    val model: String = "gpt-4o-mini",
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

    /**
     * Envoie une question ou un historique complet de discussion à OpenAI.
     */
    suspend fun sendChatMessage(
        messages: List<ChatMessage>,
        userQuestion: String,
        enableWebSearch: Boolean = true,
        preferredModel: String = "gpt-4o-mini"
    ): OpenAiChatResult = withContext(Dispatchers.IO) {
        val cleanQuestion = userQuestion.trim()
        if (cleanQuestion.isBlank() && messages.isEmpty()) {
            return@withContext OpenAiChatResult(
                success = false,
                errorMessage = "Le message est vide."
            )
        }

        // 1. TENTATIVE VIA FIREBASE CLOUD FUNCTIONS (Architecture serveur sécurisée recommandée)
        val cloudFunctionsResult = tryCallFirebaseCloudFunction(
            messages = messages,
            question = cleanQuestion,
            enableWebSearch = enableWebSearch,
            model = preferredModel
        )

        if (cloudFunctionsResult != null && cloudFunctionsResult.success && cloudFunctionsResult.text.isNotBlank()) {
            return@withContext cloudFunctionsResult
        }

        // 2. SECOURS DIRECT HAUTE DISPONIBILITÉ VIA GATEWAY OFFICIELLE OPENAI / OPENROUTER
        // Utilisé si la fonction Cloud Firebase n'est pas encore déployée par l'utilisateur
        val directResult = tryCallDirectOpenAiGateway(
            messages = messages,
            userQuestion = cleanQuestion,
            enableWebSearch = enableWebSearch,
            preferredModel = preferredModel
        )

        if (directResult != null && directResult.success && directResult.text.isNotBlank()) {
            return@withContext directResult
        }

        // 3. SECOURS LOCAL INTELLIGENT AUTONOME (Mode hors-ligne ou réseau indisponible)
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
            Tu es SAM AI, l'intelligence artificielle d'élite officielle propulsée par OpenAI et conçue par Abdoul Mohamine Sawadogo, jeune développeur burkinabè.
            Aujourd'hui nous sommes exactement le $dateStr.
            Consignes d'or :
            1. Réponds de manière intelligente, structurée, claire et agréable en français.
            2. Si l'utilisateur demande d'approfondir ou de détailler sa question précédente, développe le sujet en profondeur.
            3. Ne te présente JAMAIS au milieu d'une conversation déjà engagée.
            4. Ne mentionne ton créateur que si l'utilisateur te demande expressément qui t'a créé.
            $webContext
        """.trimIndent()

        // 1. Tenter avec OpenRouter (clé déjà présente et testée avec modèle openai/gpt-4o-mini)
        val openRouterKey = try {
            BuildConfig.OPENROUTER_API_KEY.trim().removeSurrounding("\"").removeSurrounding("'").trim()
        } catch (_: Throwable) { "" }

        if (openRouterKey.isNotBlank() && openRouterKey.startsWith("sk-or-")) {
            val res = callOpenAiCompatibleEndpoint(
                endpointUrl = "https://openrouter.ai/api/v1/chat/completions",
                apiKey = openRouterKey,
                model = "openai/gpt-4o-mini",
                systemPrompt = systemPrompt,
                messages = messages,
                userQuestion = userQuestion
            )
            if (res != null) {
                return OpenAiChatResult(
                    success = true,
                    text = res,
                    sources = webSources,
                    model = "gpt-4o-mini",
                    searchPerformed = webSources.isNotEmpty()
                )
            }
        }

        // 2. Tenter avec OpenAI officiel direct si clé configurée
        val openAiKey = try {
            BuildConfig.OPENAI_API_KEY.trim().removeSurrounding("\"").removeSurrounding("'").trim()
        } catch (_: Throwable) { "" }

        if (openAiKey.isNotBlank() && !openAiKey.equals("MY_OPENAI_API_KEY", ignoreCase = true)) {
            val res = callOpenAiCompatibleEndpoint(
                endpointUrl = "https://api.openai.com/v1/chat/completions",
                apiKey = openAiKey,
                model = preferredModel,
                systemPrompt = systemPrompt,
                messages = messages,
                userQuestion = userQuestion
            )
            if (res != null) {
                return OpenAiChatResult(
                    success = true,
                    text = res,
                    sources = webSources,
                    model = preferredModel,
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

            val messagesArray = buildJsonArray {
                add(buildJsonObject {
                    put("role", "system")
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
                put("temperature", 0.6)
                put("max_tokens", 3000)
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
