package com.example.data.remote

import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.Locale

/**
 * Service de reconnaissance vocale et transcription audio haute précision propulsé
 * exclusivement par Google Gemini (Firebase AI Logic — gemini-3.8-flash / gemini-3.5-flash-lite).
 * (OpenAI Whisper est entièrement désactivé.)
 */
class OpenAiWhisperService(@Suppress("UNUSED_PARAMETER") context: Context) {

    private val firebaseAi by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI())
    }

    companion object {
        private const val TAG = "GoogleSpeechTranscribe"
        private const val PRIMARY_TRANSCRIBE_MODEL = "gemini-3.8-flash"
        private const val FALLBACK_TRANSCRIBE_MODEL = "gemini-3.5-flash-lite"
        private const val TRANSCRIBE_TIMEOUT_MS = 35_000L

        fun isLikelyMooreUtterance(text: String): Boolean = false

        private val HALLUCINATION_PATTERNS = listOf(
            "sous-titres réalisés",
            "sous-titrage",
            "sous-titres",
            "amara.org",
            "merci d'avoir regardé",
            "merci d'avoir écouté",
            "abonnez-vous",
            "thank you for watching",
            "subtitles by",
            "transcribed by",
            "sottotitoli",
            "mbc 뉴스",
            "시청해주셔서",
            "ご視聴ありがとう",
            "Субтитры",
            "www.",
            "transcris mot pour mot",
            "transcris exactement",
            "burkina faso, ouagadougou, bobo-dioulasso"
        )
    }

    suspend fun transcribeAudio(
        audioFile: File,
        languageHint: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!audioFile.exists() || audioFile.length() <= 44) {
            return@withContext Result.failure(IllegalArgumentException("Fichier audio vide ou introuvable."))
        }

        val mimeType = when {
            audioFile.name.endsWith(".wav", ignoreCase = true) -> "audio/wav"
            audioFile.name.endsWith(".mp3", ignoreCase = true) -> "audio/mpeg"
            audioFile.name.endsWith(".aac", ignoreCase = true) -> "audio/aac"
            audioFile.name.endsWith(".ogg", ignoreCase = true) -> "audio/ogg"
            else -> "audio/mp4"
        }

        val targetLangLabel = when (languageHint?.trim()?.lowercase(Locale.ROOT)) {
            "en", "anglais", "english" -> "anglais"
            "es", "espagnol", "spanish" -> "espagnol"
            "de", "allemand", "german" -> "allemand"
            "ar", "arabe", "arabic" -> "arabe"
            "ha", "haoussa", "hausa" -> "haoussa"
            else -> "français"
        }

        val audioBytes = try {
            audioFile.readBytes()
        } catch (e: Exception) {
            Log.e(TAG, "Impossible de lire le fichier audio", e)
            return@withContext Result.failure(e)
        } finally {
            try {
                if (audioFile.exists()) audioFile.delete()
            } catch (_: Exception) {}
        }

        if (audioBytes.size <= 44) {
            return@withContext Result.failure(IllegalArgumentException("Enregistrement audio trop court."))
        }

        val instruction =
            "Transcris mot pour mot et fidèlement en $targetLangLabel ce que dit la personne dans cet audio. " +
            "Renvoie UNIQUEMENT le texte prononcé, sans guillemets, sans commentaire et sans préambule. " +
            "S'il n'y a aucune parole audible ou uniquement du bruit, renvoie une chaîne vide."

        var lastError: Throwable? = null
        for (modelId in listOf(PRIMARY_TRANSCRIBE_MODEL, FALLBACK_TRANSCRIBE_MODEL)) {
            try {
                val model = firebaseAi.generativeModel(
                    modelName = modelId,
                    generationConfig = generationConfig {
                        temperature = 0.0f
                        maxOutputTokens = 512
                    }
                )
                val promptContent = content {
                    part(InlineDataPart(audioBytes, mimeType))
                    text(instruction)
                }
                val response = withTimeout(TRANSCRIBE_TIMEOUT_MS) {
                    model.generateContent(promptContent)
                }
                val rawText = response.text.orEmpty()
                    .trim()
                    .removeSurrounding("\"")
                    .removeSurrounding("«", "»")
                    .trim()

                if (rawText.isBlank() || isHallucinationOrPromptEcho(rawText)) {
                    return@withContext Result.success("")
                }
                Log.d(TAG, "Transcription Google ($modelId) -> \"$rawText\"")
                return@withContext Result.success(rawText)
            } catch (ce: CancellationException) {
                if (ce is TimeoutCancellationException) {
                    lastError = ce
                    Log.w(TAG, "Timeout transcription audio sur $modelId")
                } else {
                    throw ce
                }
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "Échec transcription audio sur $modelId: ${e.message}", e)
            }
        }

        val errMsg = when (lastError) {
            is TimeoutCancellationException -> "Délai de transcription audio dépassé. Veuillez réessayer."
            is java.net.UnknownHostException,
            is java.net.ConnectException,
            is java.net.NoRouteToHostException ->
                if (com.example.data.local.ApiKeyManager.hasActiveInternetConnection()) {
                    "Service vocal Google temporairement inaccessible. Veuillez réessayer."
                } else {
                    "Erreur de connexion : vérifiez votre réseau."
                }
            else -> "Erreur temporaire de transcription audio."
        }
        Result.failure(Exception(errMsg, lastError))
    }

    private fun isHallucinationOrPromptEcho(text: String): Boolean {
        val lower = text.trim().lowercase(Locale.ROOT)
        if (lower.length < 2) return true
        if (HALLUCINATION_PATTERNS.any { lower.contains(it) }) return true
        if (lower.contains("transcris mot pour mot")) return true
        if (lower.contains("renvoie uniquement le texte")) return true
        return false
    }

    fun applyMoorePhoneticCorrections(input: String, isMooreHint: Boolean = false): String {
        val s = input.trim()
        if (s.isEmpty() || !isMooreHint) return s
        return com.example.data.engine.DictionaryLookupEngine.normalizeMooreProlongations(s)
    }

    @Suppress("UNUSED_PARAMETER")
    fun matchClosestMooreCanonicalPhrase(input: String, isMooreHint: Boolean = true): String? = null
}
