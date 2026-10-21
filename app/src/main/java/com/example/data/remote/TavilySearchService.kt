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

@Serializable
data class TavilySearchRequest(
    val api_key: String,
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
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
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
        val cleanKey = apiKey.trim()
        val cleanQuery = query.trim()
        if (cleanKey.isBlank() || cleanKey.startsWith("tvly-your-tavily") || cleanQuery.isBlank()) {
            return@withContext null
        }

        try {
            val reqPayload = TavilySearchRequest(
                api_key = cleanKey,
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
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    android.util.Log.w("TavilySearchService", "Tavily search failed code=${response.code}")
                    return@withContext null
                }
                val bodyStr = response.body?.string().orEmpty()
                if (bodyStr.isBlank()) return@withContext null
                json.decodeFromString(TavilySearchResponse.serializer(), bodyStr)
            }
        } catch (e: Throwable) {
            android.util.Log.e("TavilySearchService", "Error during Tavily search", e)
            null
        }
    }
}
