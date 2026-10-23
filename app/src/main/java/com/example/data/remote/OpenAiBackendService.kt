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
    val model: String = "gpt-4o",
    val searchPerformed: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Service client pour l'intégration officielle et exclusive de l'API OpenAI dans SAM AI.
 * Aucun service tiers (Groq, OpenRouter) ni moteur local de substitution.
 */
class OpenAiBackendService {

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
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
        val buildKey = try {
            BuildConfig.OPENAI_API_KEY.trim().removeSurrounding("\"").removeSurrounding("'").trim()
        } catch (_: Throwable) {
            ""
        }
        if (buildKey.isNotBlank() && buildKey != "MY_OPENAI_API_KEY") return buildKey
        return com.example.data.local.ApiKeyManager.DEFAULT_OPENAI_API_KEY
    }

    /**
     * Envoie une question ou un historique complet de discussion exclusivement à l'API OpenAI
     * en injectant si disponible le contexte des dépêches d'actualités en direct.
     */
    suspend fun sendChatMessage(
        messages: List<ChatMessage>,
        userQuestion: String,
        enableWebSearch: Boolean = false,
        preferredModel: String = "",
        newsContext: String = "",
        sources: List<WebSource> = emptyList()
    ): OpenAiChatResult = withContext(Dispatchers.IO) {
        val cleanQuestion = userQuestion.trim()
        if (cleanQuestion.isBlank() && messages.isEmpty()) {
            return@withContext OpenAiChatResult(
                success = false,
                errorMessage = "Le message est vide."
            )
        }

        val effectiveModel = preferredModel.ifBlank { clientConfiguredModel }

        val directOpenAiResult = tryCallOfficialOpenAiApi(
            messages = messages,
            userQuestion = cleanQuestion,
            model = effectiveModel,
            newsContext = newsContext,
            sources = sources
        )

        directOpenAiResult ?: OpenAiChatResult(
            success = false,
            text = "",
            model = effectiveModel,
            errorMessage = "Impossible de joindre l'API OpenAI. Vérifiez votre clé API et votre réseau."
        )
    }

    /**
     * Appelle directement l'API officielle OpenAI (https://api.openai.com/v1/chat/completions)
     */
    private suspend fun tryCallOfficialOpenAiApi(
        messages: List<ChatMessage>,
        userQuestion: String,
        model: String,
        newsContext: String = "",
        sources: List<WebSource> = emptyList()
    ): OpenAiChatResult? {
        val apiKey = getOpenAiApiKey()
        if (apiKey.isBlank() || apiKey.contains("MY_OPENAI_API_KEY")) {
            return OpenAiChatResult(
                success = false,
                text = "",
                model = model,
                errorMessage = "Clé API OpenAI non renseignée. Veuillez configurer OPENAI_API_KEY."
            )
        }

        val dateStr = java.text.SimpleDateFormat("EEEE d MMMM yyyy", java.util.Locale.FRENCH).format(java.util.Date())
        val systemPrompt = buildString {
            append("Tu es SAM AI, l'assistant d'élite officiel propulsé par OpenAI et conçu par Abdoul Mohamine Sawadogo, jeune développeur burkinabè.\n")
            append("Aujourd'hui nous sommes exactement le $dateStr.\n")
            if (newsContext.isNotBlank()) {
                append("\n--- ACTUALITÉS ET DÉPÊCHES EN DIRECT FOURNIES EN TEMPS RÉEL ---\n")
                append(newsContext)
                append("\n---------------------------------------------------------------\n")
            }
            append("\nConsignes impératives de qualité :\n")
            append("1. RÉPONSE DIRECTE ET FLUIDE : Réponds immédiatement et directement à la question sans introduction robotique ni formule stéréotypée (\"Merci pour votre question\", etc.).\n")
            append("2. INFORMATIONS D'ACTUALITÉ : Exploite immédiatement et pleinement les faits, dépêches et articles récents fournis pour répondre aux questions sur le Burkina Faso et le monde. Présente directement les nouvelles saillantes de manière claire et structurée. Ne dis JAMAIS que tu n'as pas accès à internet ou aux informations en direct, et ne commence JAMAIS par dire qu'il n'y a pas de dépêche : entre directement dans le vif du sujet avec les faits et actualités fournis.\n")
            append("3. APPROFONDISSEMENT UTILE : Si l'utilisateur demande de détailler ou développer, donne des explications concrètes, structurées et complètes.\n")
            append("4. Ne mentionne ton créateur que si expressément demandé.\n")
        }

        try {
            val isReasoningModel = model.startsWith("o1") || model.startsWith("o3")
            val systemRole = if (isReasoningModel) "developer" else "system"

            val safeHistory = messages.takeLast(16).map {
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
                        401 -> "Clé API OpenAI non valide (code 401). Vérifiez votre clé sur platform.openai.com."
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
                        sources = sources,
                        model = actualModel,
                        searchPerformed = sources.isNotEmpty(),
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
