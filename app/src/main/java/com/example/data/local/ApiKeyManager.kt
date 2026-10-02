package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

enum class ApiKeySource {
    SYSTEM_ENV,
    USER_CUSTOM,
    NONE
}

class ApiKeyManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _geminiKeyFlow = MutableStateFlow(getActiveGeminiApiKey())
    val geminiKeyFlow: StateFlow<String> = _geminiKeyFlow.asStateFlow()

    private val _geminiLiveKeyFlow = MutableStateFlow(getActiveGeminiLiveApiKey())
    val geminiLiveKeyFlow: StateFlow<String> = _geminiLiveKeyFlow.asStateFlow()

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    fun getActiveGeminiApiKey(): String {
        val customKey = prefs.getString(KEY_CUSTOM_GEMINI_API_KEY, null)?.trim()
        if (!customKey.isNullOrBlank() && customKey != "MY_GEMINI_API_KEY") {
            return customKey
        }
        val buildConfigKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (_: Throwable) {
            ""
        }
        val clean = buildConfigKey.trim().removeSurrounding("\"").removeSurrounding("'").trim()
        return if (clean.isNotBlank() && clean != "MY_GEMINI_API_KEY") clean else ""
    }

    fun isGeminiConfigured(): Boolean {
        val key = getActiveGeminiApiKey()
        return key.isNotBlank() && key != "MY_GEMINI_API_KEY"
    }

    fun getGeminiKeySource(): ApiKeySource {
        val customKey = prefs.getString(KEY_CUSTOM_GEMINI_API_KEY, null)?.trim()
        val envKey = try {
            BuildConfig.GEMINI_API_KEY.trim().removeSurrounding("\"").removeSurrounding("'").trim()
        } catch (_: Throwable) {
            ""
        }
        return when {
            !customKey.isNullOrBlank() && customKey != "MY_GEMINI_API_KEY" -> ApiKeySource.USER_CUSTOM
            envKey.isNotBlank() && envKey != "MY_GEMINI_API_KEY" -> ApiKeySource.SYSTEM_ENV
            else -> ApiKeySource.NONE
        }
    }

    fun saveGeminiApiKey(key: String) {
        val clean = key.trim().removeSurrounding("\"").removeSurrounding("'").trim()
        if (clean.isBlank() || clean == "MY_GEMINI_API_KEY") {
            clearGeminiApiKey()
        } else {
            prefs.edit().putString(KEY_CUSTOM_GEMINI_API_KEY, clean).apply()
            _geminiKeyFlow.value = clean
        }
    }

    fun clearGeminiApiKey() {
        prefs.edit().remove(KEY_CUSTOM_GEMINI_API_KEY).apply()
        _geminiKeyFlow.value = getActiveGeminiApiKey()
    }

    fun getActiveGeminiLiveApiKey(): String {
        val customLiveKey = prefs.getString(KEY_CUSTOM_GEMINI_LIVE_API_KEY, null)?.trim()
        if (!customLiveKey.isNullOrBlank() && customLiveKey != "MY_GEMINI_API_KEY") {
            return customLiveKey
        }
        return getActiveGeminiApiKey()
    }

    fun getSavedCustomGeminiLiveKey(): String? {
        val key = prefs.getString(KEY_CUSTOM_GEMINI_LIVE_API_KEY, null)?.trim()?.takeIf { it.isNotBlank() }
        return if (key == "MY_GEMINI_API_KEY") null else key
    }

    fun isGeminiLiveDedicatedKeyConfigured(): Boolean {
        return !getSavedCustomGeminiLiveKey().isNullOrBlank()
    }

    fun saveGeminiLiveApiKey(key: String) {
        val clean = key.trim().removeSurrounding("\"").removeSurrounding("'").trim()
        if (clean.isBlank() || clean == "MY_GEMINI_API_KEY") {
            clearGeminiLiveApiKey()
        } else {
            prefs.edit().putString(KEY_CUSTOM_GEMINI_LIVE_API_KEY, clean).apply()
            _geminiLiveKeyFlow.value = clean
        }
    }

    fun clearGeminiLiveApiKey() {
        prefs.edit().remove(KEY_CUSTOM_GEMINI_LIVE_API_KEY).apply()
        _geminiLiveKeyFlow.value = getActiveGeminiLiveApiKey()
    }

    suspend fun testApiKey(key: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val cleanKey = key.trim().removeSurrounding("\"").removeSurrounding("'").trim()
        if (cleanKey.isBlank() || cleanKey == "MY_GEMINI_API_KEY") {
            return@withContext Pair(false, "Veuillez saisir une clé API Gemini valide.")
        }
        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$cleanKey"
            val request = Request.Builder().url(url).get().build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                Pair(true, "Clé API valide et fonctionnelle")
            } else {
                Pair(false, "Clé invalide (Code ${response.code}) : ${response.message}")
            }
        } catch (e: Throwable) {
            Pair(false, "Erreur de connexion : ${e.localizedMessage ?: "Vérifiez votre réseau"}")
        }
    }

    companion object {
        private const val PREFS_NAME = "sam_ai_api_keys"
        private const val KEY_CUSTOM_GEMINI_API_KEY = "custom_gemini_api_key"
        private const val KEY_CUSTOM_GEMINI_LIVE_API_KEY = "custom_gemini_live_api_key"
    }
}
