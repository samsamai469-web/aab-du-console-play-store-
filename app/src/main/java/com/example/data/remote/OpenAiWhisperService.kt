package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.data.local.ApiKeyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Service officiel d'intégration de l'API OpenAI Whisper pour la reconnaissance vocale et la transcription.
 * Endpoint : POST https://api.openai.com/v1/audio/transcriptions
 * Modèle : whisper-1
 */
class OpenAiWhisperService(private val context: Context) {

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .build()
    }

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val apiKeyManager = ApiKeyManager(context)

    companion object {
        private const val TAG = "OpenAiWhisperService"
        private const val WHISPER_API_URL = "https://api.openai.com/v1/audio/transcriptions"
        private const val MODEL_NAME = "whisper-1"
    }

    /**
     * Transcrit un fichier audio enregistré via l'API OpenAI Whisper.
     *
     * @param audioFile Le fichier audio (.m4a ou .wav)
     * @param languageHint La langue présumée (ex: "Français", "Anglais", etc.)
     * @return Result contenant le texte transcrit ou l'erreur
     */
    suspend fun transcribeAudio(
        audioFile: File,
        languageHint: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!audioFile.exists() || audioFile.length() <= 0) {
            return@withContext Result.failure(IllegalArgumentException("Fichier audio vide ou introuvable."))
        }

        val apiKey = apiKeyManager.getActiveOpenAiApiKey()
        if (apiKey.isBlank() || apiKey == "MY_OPENAI_API_KEY") {
            Log.e(TAG, "Clé API OpenAI non configurée")
            return@withContext Result.failure(IllegalStateException("Clé API OpenAI non configurée. Veuillez ajouter votre clé dans les paramètres."))
        }

        val whisperLangCode = mapToWhisperLanguageCode(languageHint)
        val mediaType = when {
            audioFile.name.endsWith(".wav", ignoreCase = true) -> "audio/wav".toMediaType()
            audioFile.name.endsWith(".mp3", ignoreCase = true) -> "audio/mpeg".toMediaType()
            else -> "audio/m4a".toMediaType()
        }

        try {
            val multipartBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model", MODEL_NAME)
                .addFormDataPart("response_format", "json")
                .addFormDataPart(
                    "file",
                    audioFile.name,
                    audioFile.asRequestBody(mediaType)
                )

            // Spécification de la langue si applicable (si langue standard ISO)
            if (!whisperLangCode.isNullOrBlank()) {
                multipartBuilder.addFormDataPart("language", whisperLangCode)
            }

            // Prompt indicatif pour améliorer la reconnaissance du contexte francophone et ouest-africain
            val contextPrompt = when (whisperLangCode) {
                "fr" -> "Transcription fidèle en français ou en langues ouest-africaines."
                "en" -> "Accurate speech-to-text transcription."
                else -> "Transcription audio en français ou langues du Burkina Faso (Mooré, Dioula, Haoussa)."
            }
            multipartBuilder.addFormDataPart("prompt", contextPrompt)

            val request = Request.Builder()
                .url(WHISPER_API_URL)
                .addHeader("Authorization", "Bearer $apiKey")
                .post(multipartBuilder.build())
                .build()

            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    Log.e(TAG, "Erreur API Whisper [Code ${response.code}]: $responseBody")
                    val parsedError = try {
                        val jsonEl = json.parseToJsonElement(responseBody).jsonObject
                        val errObj = jsonEl["error"]?.jsonObject
                        errObj?.get("message")?.jsonPrimitive?.content
                    } catch (_: Exception) {
                        null
                    }

                    val userMessage = when (response.code) {
                        401 -> "Clé API OpenAI invalide ou expirée."
                        429 -> "Limite de requêtes OpenAI atteinte. Réessayez dans un instant."
                        else -> parsedError ?: "Erreur OpenAI Whisper (${response.code})"
                    }
                    return@withContext Result.failure(Exception(userMessage))
                }

                val jsonEl = json.parseToJsonElement(responseBody).jsonObject
                val transcribedText = jsonEl["text"]?.jsonPrimitive?.content.orEmpty().trim()

                Log.d(TAG, "Transcription Whisper réussie : \"$transcribedText\"")
                Result.success(transcribedText)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception lors de l'appel OpenAI Whisper : ${e.message}", e)
            Result.failure(Exception("Erreur de connexion : ${e.localizedMessage ?: "Vérifiez votre réseau"}"))
        } finally {
            // Nettoyage sécurisé du fichier temporaire
            try {
                if (audioFile.exists()) {
                    audioFile.delete()
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Mappage des langues de l'application vers les codes ISO 639-1 supportés par Whisper.
     * Pour les langues nationales sans code ISO Whisper strict (Mooré, Dioula), on retourne null
     * pour laisser Whisper utiliser sa détection automatique multilingue haute fidélité.
     */
    private fun mapToWhisperLanguageCode(lang: String?): String? {
        if (lang.isNullOrBlank()) return null
        return when (lang.trim().lowercase(Locale.ROOT)) {
            "français", "francais", "fr", "french" -> "fr"
            "anglais", "english", "en" -> "en"
            "espagnol", "spanish", "es" -> "es"
            "allemand", "german", "de" -> "de"
            "arabe", "arabic", "ar" -> "ar"
            "italien", "italian", "it" -> "it"
            "portugais", "portuguese", "pt" -> "pt"
            "russe", "russian", "ru" -> "ru"
            "chinois", "chinese", "zh" -> "zh"
            "haoussa", "hausa", "ha" -> "ha"
            // Pour Mooré, Dioula, Gulmancema, Fulfulde : auto-détection optimale de Whisper
            else -> null
        }
    }
}
