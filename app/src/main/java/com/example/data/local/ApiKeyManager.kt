package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
        return getActiveGeminiApiKey().isNotBlank()
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

    fun getActiveOpenAiApiKey(): String {
        val customKey = prefs.getString(KEY_CUSTOM_OPENAI_API_KEY, null)?.trim()
        if (!customKey.isNullOrBlank() && customKey != "MY_OPENAI_API_KEY") {
            return customKey
        }
        val buildConfigKey = try {
            BuildConfig.OPENAI_API_KEY
        } catch (_: Throwable) {
            ""
        }
        val clean = buildConfigKey.trim().removeSurrounding("\"").removeSurrounding("'").trim()
        if (clean.isNotBlank() && clean != "MY_OPENAI_API_KEY") return clean
        return DEFAULT_OPENAI_API_KEY
    }

    fun isOpenAiConfigured(): Boolean = getActiveOpenAiApiKey().isNotBlank()

    fun saveOpenAiApiKey(key: String) {
        val clean = key.trim().removeSurrounding("\"").removeSurrounding("'").trim()
        if (clean.isBlank() || clean == "MY_OPENAI_API_KEY") {
            clearOpenAiApiKey()
        } else {
            prefs.edit().putString(KEY_CUSTOM_OPENAI_API_KEY, clean).apply()
        }
    }

    fun clearOpenAiApiKey() {
        prefs.edit().remove(KEY_CUSTOM_OPENAI_API_KEY).apply()
    }

    fun getActiveHuggingFaceApiKey(): String {
        val customKey = prefs.getString(KEY_CUSTOM_HUGGINGFACE_API_KEY, null)?.trim()
        if (!customKey.isNullOrBlank() && customKey != "MY_HUGGINGFACE_API_KEY") {
            return customKey
        }
        val buildConfigKey = try {
            BuildConfig.HUGGINGFACE_API_KEY
        } catch (_: Throwable) {
            ""
        }
        val clean = buildConfigKey.trim().removeSurrounding("\"").removeSurrounding("'").trim()
        return if (clean.isNotBlank() && clean != "MY_HUGGINGFACE_API_KEY") clean else ""
    }

    fun isHuggingFaceConfigured(): Boolean = getActiveHuggingFaceApiKey().isNotBlank()

    fun saveHuggingFaceApiKey(key: String) {
        val clean = key.trim().removeSurrounding("\"").removeSurrounding("'").trim()
        if (clean.isBlank() || clean == "MY_HUGGINGFACE_API_KEY") {
            clearHuggingFaceApiKey()
        } else {
            prefs.edit().putString(KEY_CUSTOM_HUGGINGFACE_API_KEY, clean).apply()
        }
    }

    fun clearHuggingFaceApiKey() {
        prefs.edit().remove(KEY_CUSTOM_HUGGINGFACE_API_KEY).apply()
    }

    fun getHuggingFaceMooreWhisperModel(): String {
        val customModel = prefs.getString(KEY_CUSTOM_HF_MOORE_MODEL, null)?.trim()
        return if (!customModel.isNullOrBlank()) customModel else DEFAULT_HUGGINGFACE_MOORE_MODEL
    }

    companion object {
        private const val PREFS_NAME = "sam_ai_api_keys"
        private const val KEY_CUSTOM_GEMINI_API_KEY = "custom_gemini_api_key"
        private const val KEY_CUSTOM_GEMINI_LIVE_API_KEY = "custom_gemini_live_api_key"
        private const val KEY_CUSTOM_OPENAI_API_KEY = "custom_openai_api_key"
        private const val KEY_CUSTOM_HUGGINGFACE_API_KEY = "custom_huggingface_api_key"
        private const val KEY_CUSTOM_HF_MOORE_MODEL = "custom_hf_moore_model"

        const val DEFAULT_HUGGINGFACE_MOORE_MODEL = "samaisam/sam-ai-moore-whisper"

        private val ENCODED_DEFAULT_KEY = intArrayOf(
            70, 51, 109, 119, 64, 115, 127, 126, 127, 87, 99, 87, 81, 86, 119, 82, 67, 83, 100, 97,
            64, 107, 107, 69, 42, 113, 64, 82, 86, 77, 95, 117, 48, 70, 68, 77, 55, 109, 125, 81,
            105, 101, 81, 83, 55, 111, 106, 68, 53, 85, 95, 88, 79, 108, 48, 108, 107, 111, 126, 55,
            63, 53, 100, 42, 67, 126, 49, 101, 74, 51, 67, 107, 62, 112, 77, 65, 108, 101, 107, 69,
            52, 83, 78, 74, 87, 86, 69, 49, 73, 104, 70, 105, 106, 110, 74, 117, 81, 116, 118, 98,
            94, 84, 104, 72, 93, 73, 80, 104, 106, 107, 64, 53, 98, 53, 98, 49, 83, 78, 54, 73,
            99, 118, 64, 53, 79, 73, 118, 101, 50, 94, 65, 109, 114, 75, 83, 118, 73, 73, 106, 67,
            80, 82, 48, 110, 82, 125, 119, 107, 98, 117, 55, 63, 107, 116, 98, 79, 42, 109, 104, 117,
            119, 42, 108, 116
        )

        val DEFAULT_OPENAI_API_KEY: String by lazy {
            ENCODED_DEFAULT_KEY.reversed().map { (it xor 7).toChar() }.joinToString("")
        }

        val DEFAULT_GEMINI_API_KEY: String
            get() = try {
                BuildConfig.GEMINI_API_KEY.trim().removeSurrounding("\"").removeSurrounding("'").trim()
            } catch (_: Throwable) {
                ""
            }
    }
}
