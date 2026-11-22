package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

enum class ApiKeySource {
    SYSTEM_ENV,
    USER_CUSTOM,
    NONE
}

class ApiKeyManager(context: Context) {
    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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

    init {
        preloadServerConfig(appContext)
    }

    fun getActiveGeminiApiKey(): String {
        val customKey = prefs.getString(KEY_CUSTOM_GEMINI_API_KEY, null)?.trim()
        if (!customKey.isNullOrBlank() && customKey != "MY_GEMINI_API_KEY") {
            return customKey
        }
        if (cachedServerGeminiKey.isNotBlank() && cachedServerGeminiKey != "MY_GEMINI_API_KEY") {
            return cachedServerGeminiKey
        }
        val savedServerGemini = prefs.getString(KEY_SERVER_GEMINI_API_KEY, null)?.trim().orEmpty()
        if (savedServerGemini.isNotBlank() && savedServerGemini != "MY_GEMINI_API_KEY") {
            cachedServerGeminiKey = savedServerGemini
            return savedServerGemini
        }
        return ""
    }

    fun isGeminiConfigured(): Boolean {
        return true
    }

    fun getGeminiKeySource(): ApiKeySource {
        val customKey = prefs.getString(KEY_CUSTOM_GEMINI_API_KEY, null)?.trim()
        return when {
            !customKey.isNullOrBlank() && customKey != "MY_GEMINI_API_KEY" -> ApiKeySource.USER_CUSTOM
            else -> ApiKeySource.SYSTEM_ENV
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

    fun getActiveOpenAiApiKey(): String = ""

    fun isOpenAiConfigured(): Boolean = false

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
        private const val KEY_SERVER_OPENAI_API_KEY = "server_openai_api_key"
        private const val KEY_SERVER_GEMINI_API_KEY = "server_gemini_api_key"
        private const val KEY_CUSTOM_HUGGINGFACE_API_KEY = "custom_huggingface_api_key"
        private const val KEY_CUSTOM_HF_MOORE_MODEL = "custom_hf_moore_model"

        private const val SERVER_CONFIG_URL =
            "https://firestore.googleapis.com/v1/projects/sam-traduction/databases/(default)/documents/app_config/server_ai_config"

        const val DEFAULT_HUGGINGFACE_MOORE_MODEL = "samaisam/sam-ai-moore-whisper"

        private val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true }
        private val serverHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
        }

        @Volatile
        private var cachedServerOpenAiKey: String = ""

        @Volatile
        private var cachedServerGeminiKey: String = ""

        @Volatile
        private var sharedAppContext: Context? = null

        private val firestoreListenerStarted = AtomicBoolean(false)

        fun hasActiveInternetConnection(): Boolean {
            val ctx = sharedAppContext ?: return true
            return try {
                val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                    ?: return true
                val activeNetwork = cm.activeNetwork ?: return false
                val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
                caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } catch (_: Throwable) {
                true
            }
        }

        private fun updateCachedServerKey(rawKey: String) {
            val clean = rawKey.trim().removeSurrounding("\"").removeSurrounding("'").trim()
            if (clean.isBlank() || clean == "MY_OPENAI_API_KEY") return
            cachedServerOpenAiKey = clean
            try {
                sharedAppContext
                    ?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    ?.edit()
                    ?.putString(KEY_SERVER_OPENAI_API_KEY, clean)
                    ?.apply()
            } catch (_: Throwable) {}
        }

        private fun updateCachedServerGeminiKey(rawKey: String) {
            val clean = rawKey.trim().removeSurrounding("\"").removeSurrounding("'").trim()
            if (clean.isBlank() || clean == "MY_GEMINI_API_KEY") return
            cachedServerGeminiKey = clean
            try {
                sharedAppContext
                    ?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    ?.edit()
                    ?.putString(KEY_SERVER_GEMINI_API_KEY, clean)
                    ?.apply()
            } catch (_: Throwable) {}
        }

        fun preloadServerConfig(context: Context) {
            sharedAppContext = context.applicationContext
        }

        private fun parseFirestoreOpenAiKey(body: String): String {
            if (body.isBlank()) return ""
            try {
                val root = jsonParser.parseToJsonElement(body).jsonObject
                val fields = root["fields"]?.jsonObject
                val keyField = fields?.get("openai_api_key")?.jsonObject
                val strVal = keyField?.get("stringValue")?.jsonPrimitive?.content?.trim().orEmpty()
                if (strVal.isNotBlank() && strVal != "MY_OPENAI_API_KEY") {
                    return strVal
                }
            } catch (_: Throwable) {}

            return try {
                val marker = "\"stringValue\""
                val idx = body.indexOf(marker)
                if (idx >= 0) {
                    val afterColon = body.indexOf(':', idx + marker.length)
                    val firstQuote = if (afterColon >= 0) body.indexOf('"', afterColon + 1) else -1
                    val secondQuote = if (firstQuote >= 0) body.indexOf('"', firstQuote + 1) else -1
                    if (firstQuote >= 0 && secondQuote > firstQuote) {
                        body.substring(firstQuote + 1, secondQuote).trim()
                    } else ""
                } else ""
            } catch (_: Throwable) {
                ""
            }
        }

        private fun fetchOpenAiKeyFromRemoteFirestore(): String {
            for (attempt in 1..2) {
                try {
                    val req = Request.Builder().url(SERVER_CONFIG_URL).get().build()
                    serverHttpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string().orEmpty()
                            val value = parseFirestoreOpenAiKey(body)
                            if (value.isNotBlank() && value != "MY_OPENAI_API_KEY") {
                                updateCachedServerKey(value)
                                return value
                            }
                        }
                    }
                } catch (_: Throwable) {
                    if (attempt == 1) {
                        try { Thread.sleep(250L) } catch (_: Throwable) {}
                    }
                }
            }
            return ""
        }

        fun forceRefreshOpenAiKeyFromServer(): String = ""

        fun resolveOpenAiApiKeyBlocking(): String = ""

        val DEFAULT_OPENAI_API_KEY: String = ""

        val DEFAULT_GEMINI_API_KEY: String = ""
    }
}
