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

    init {
        val savedServerKey = prefs.getString(KEY_SERVER_OPENAI_API_KEY, null)?.trim().orEmpty()
        if (savedServerKey.isNotBlank() && savedServerKey != "MY_OPENAI_API_KEY") {
            cachedServerOpenAiKey = savedServerKey
        }
        syncServerConfigInBackground()
    }

    private fun syncServerConfigInBackground() {
        Thread {
            try {
                fetchOpenAiKeyFromServer()
            } catch (_: Throwable) {}
        }.start()
    }

    fun getActiveOpenAiApiKey(): String {
        val customKey = prefs.getString(KEY_CUSTOM_OPENAI_API_KEY, null)?.trim()
        if (!customKey.isNullOrBlank() && customKey != "MY_OPENAI_API_KEY") {
            return customKey
        }
        val savedServerKey = prefs.getString(KEY_SERVER_OPENAI_API_KEY, null)?.trim()
        if (!savedServerKey.isNullOrBlank() && savedServerKey != "MY_OPENAI_API_KEY") {
            cachedServerOpenAiKey = savedServerKey
            return savedServerKey
        }
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

    private fun fetchOpenAiKeyFromServer(): String {
        val fetched = fetchOpenAiKeyFromRemoteFirestore()
        if (fetched.isNotBlank()) {
            prefs.edit().putString(KEY_SERVER_OPENAI_API_KEY, fetched).apply()
        }
        return fetched
    }

    companion object {
        private const val PREFS_NAME = "sam_ai_api_keys"
        private const val KEY_CUSTOM_GEMINI_API_KEY = "custom_gemini_api_key"
        private const val KEY_CUSTOM_GEMINI_LIVE_API_KEY = "custom_gemini_live_api_key"
        private const val KEY_CUSTOM_OPENAI_API_KEY = "custom_openai_api_key"
        private const val KEY_SERVER_OPENAI_API_KEY = "server_openai_api_key"
        private const val KEY_CUSTOM_HUGGINGFACE_API_KEY = "custom_huggingface_api_key"
        private const val KEY_CUSTOM_HF_MOORE_MODEL = "custom_hf_moore_model"

        private const val SERVER_CONFIG_URL =
            "https://firestore.googleapis.com/v1/projects/sam-traduction/databases/(default)/documents/app_config/server_ai_config"

        const val DEFAULT_HUGGINGFACE_MOORE_MODEL = "samaisam/sam-ai-moore-whisper"

        @Volatile
        private var cachedServerOpenAiKey: String = ""

        private fun fetchOpenAiKeyFromRemoteFirestore(): String {
            return try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(4, TimeUnit.SECONDS)
                    .readTimeout(4, TimeUnit.SECONDS)
                    .build()
                val req = Request.Builder().url(SERVER_CONFIG_URL).get().build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return ""
                    val body = resp.body?.string().orEmpty()
                    val match = Regex(""""openai_api_key"\s*:\s*\{\s*"stringValue"\s*:\s*"([^"]+)"""").find(body)
                    val value = match?.groupValues?.getOrNull(1)?.trim().orEmpty()
                    if (value.isNotBlank() && value != "MY_OPENAI_API_KEY") {
                        cachedServerOpenAiKey = value
                        value
                    } else {
                        ""
                    }
                }
            } catch (_: Throwable) {
                ""
            }
        }

        val DEFAULT_OPENAI_API_KEY: String
            get() {
                val buildKey = try {
                    BuildConfig.OPENAI_API_KEY.trim().removeSurrounding("\"").removeSurrounding("'").trim()
                } catch (_: Throwable) {
                    ""
                }
                if (buildKey.isNotBlank() && buildKey != "MY_OPENAI_API_KEY") {
                    return buildKey
                }
                if (cachedServerOpenAiKey.isNotBlank()) {
                    return cachedServerOpenAiKey
                }
                if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
                    val remote = fetchOpenAiKeyFromRemoteFirestore()
                    if (remote.isNotBlank()) return remote
                }
                return cachedServerOpenAiKey
            }

        val DEFAULT_GEMINI_API_KEY: String
            get() = try {
                BuildConfig.GEMINI_API_KEY.trim().removeSurrounding("\"").removeSurrounding("'").trim()
            } catch (_: Throwable) {
                ""
            }
    }
}
