package com.example.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Service officiel de traduction automatique via le moteur Google Translate.
 * Prend en charge les langues du Sahel (Mooré, Dioula, Haoussa) ainsi que les langues internationales
 * (Français, Anglais, Allemand, Espagnol, Arabe, etc.).
 */
object GoogleTranslationService {

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .build()
    }

    fun getGoogleLangCode(lang: String): String {
        val l = lang.lowercase().trim()
        return when {
            l.contains("moor") -> "mos"
            l.contains("dioula") || l.contains("jula") -> "dyu"
            l.contains("haoussa") || l.contains("hausa") -> "ha"
            l.contains("franç") || l.contains("franc") -> "fr"
            l.contains("angl") || l.contains("engl") -> "en"
            l.contains("allem") || l.contains("germ") -> "de"
            l.contains("arab") -> "ar"
            l.contains("espagn") || l.contains("span") -> "es"
            l.contains("ital") -> "it"
            l.contains("russ") -> "ru"
            l.contains("chin") -> "zh-CN"
            l.contains("port") -> "pt"
            else -> "auto"
        }
    }

    suspend fun translate(
        text: String,
        sourceLang: String,
        targetLang: String
    ): String? = withContext(Dispatchers.IO) {
        val clean = text.trim()
        if (clean.isBlank()) return@withContext null

        val sl = getGoogleLangCode(sourceLang)
        val tl = getGoogleLangCode(targetLang)
        if (sl == tl && sl != "auto") return@withContext clean

        try {
            val encodedQuery = URLEncoder.encode(clean, "UTF-8")
            val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$sl&tl=$tl&dt=t&q=$encodedQuery"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:120.0) Gecko/120.0 Firefox/120.0")
                .header("Accept", "*/*")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                if (!body.startsWith("[")) return@withContext null

                val json = JSONArray(body)
                val sentencesArray = json.optJSONArray(0) ?: return@withContext null
                val sb = StringBuilder()
                for (i in 0 until sentencesArray.length()) {
                    val part = sentencesArray.optJSONArray(i)
                    if (part != null && part.length() > 0) {
                        val piece = part.optString(0, "")
                        if (piece.isNotBlank() && piece != "null") {
                            sb.append(piece)
                        }
                    }
                }
                val result = sb.toString().trim()
                if (result.isNotBlank() && !result.equals(clean, ignoreCase = true)) {
                    result
                } else {
                    null
                }
            }
        } catch (_: Throwable) {
            null
        }
    }
}
