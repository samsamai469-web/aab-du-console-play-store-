package com.example.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

// La clé n'est plus dans le corps : Tavily attend l'en-tête "Authorization: Bearer <clé>".
@Serializable
data class TavilySearchRequest(
    val query: String,
    val search_depth: String = "basic",
    val topic: String = "general",
    val max_results: Int = 5,
    val include_answer: Boolean = true
)

@Serializable
data class TavilySearchResultItem(
    val title: String = "",
    val url: String = "",
    val content: String = "",
    val score: Float = 0.0f
)

@Serializable
data class TavilySearchResponse(
    val answer: String? = null,
    val query: String = "",
    val results: List<TavilySearchResultItem> = emptyList()
)

class TavilySearchService {

    /**
     * Dernière erreur de recherche (ex. "HTTP 401 : clé invalide"), ou null si tout va bien.
     * À afficher dans le chat pour comprendre pourquoi la recherche a échoué.
     */
    @Volatile
    var lastError: String? = null
        private set

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    suspend fun search(
        query: String,
        apiKey: String,
        isNews: Boolean = false
    ): TavilySearchResponse? = withContext(Dispatchers.IO) {
        lastError = null

        // Nettoie la clé : espaces, retours à la ligne et guillemets collés par erreur.
        val cleanKey = apiKey.trim().removeSurrounding("\"").removeSurrounding("'").trim()
        val cleanQuery = query.trim()

        if (cleanQuery.isBlank()) return@withContext null
        if (cleanKey.isBlank()) {
            lastError = "Clé Tavily absente"
            return@withContext null
        }
        if (cleanKey.startsWith("tvly-your")) {
            lastError = "Clé Tavily non configurée (valeur d'exemple)"
            return@withContext null
        }

        try {
            val reqPayload = TavilySearchRequest(
                query = cleanQuery,
                search_depth = "basic",
                topic = if (isNews) "news" else "general",
                max_results = 5,
                include_answer = true
            )
            val jsonBody = json.encodeToString(TavilySearchRequest.serializer(), reqPayload)
            val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url("https://api.tavily.com/search")
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer $cleanKey")
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    val meaning = when (response.code) {
                        401, 403 -> "clé Tavily refusée"
                        429 -> "quota Tavily dépassé"
                        432, 433 -> "limite du plan Tavily atteinte"
                        else -> "erreur Tavily"
                    }
                    lastError = "HTTP ${response.code} : $meaning"
                    android.util.Log.w(
                        "TavilySearchService",
                        "Tavily search failed code=${response.code} body=${bodyStr.take(200)}"
                    )
                    return@withContext null
                }

                if (bodyStr.isBlank()) {
                    lastError = "Réponse Tavily vide"
                    return@withContext null
                }
                json.decodeFromString(TavilySearchResponse.serializer(), bodyStr)
            }
        } catch (e: Throwable) {
            lastError = "Connexion à Tavily impossible : ${e.javaClass.simpleName}"
            android.util.Log.e("TavilySearchService", "Error during Tavily search", e)
            null
        }
    }
}
