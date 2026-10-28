package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.data.local.ApiKeyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Service d'élite de reconnaissance vocale OpenAI Whisper & GPT-4o Audio
 * spécialement optimisé pour le Mooré (Burkina Faso) et le multilingue :
 * 1. Décodage acoustique haute précision (gpt-4o-mini-transcribe + repli whisper-1) avec temperature=0
 * 2. Conditionnement lexical Mooré intensif (salutations, verbes, pronoms, phonétique burkinabè)
 * 3. Filtrage anti-hallucinations de silence
 * 4. Dictionnaire déterministe de correction d'homophones Français -> Mooré
 * 5. Reconstruction phonétique neuronale rapide pour restituer un Mooré authentique
 */
class OpenAiWhisperService(private val context: Context) {

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(35, TimeUnit.SECONDS)
            .build()
    }

    private val fastPhoneticClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(6, TimeUnit.SECONDS)
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
        private const val CHAT_API_URL = "https://api.openai.com/v1/chat/completions"
        private const val PRIMARY_MOORE_MODEL = "gpt-4o-mini-transcribe"
        private const val FALLBACK_WHISPER_MODEL = "whisper-1"

        // Vocabulaire Mooré haute densité pour guider le décodeur acoustique
        private const val MOORE_WHISPER_PROMPT =
            "Ne y beogo, ne y zaabre, ne y wĩndga, ne y yʋngo, ne y tʋʋma, " +
            "laafi bala, laafi beeme, kibare, kibare bõe n be rũndã, " +
            "mam yaa SAM AI, bõe la mam tõe n sõng fo, barka, barka wʋsgo, " +
            "Wẽnd na kõ-d laafi, Wẽnd na sõng-do, Burkina Faso, Ouagadougou, Bobo-Dioulasso, " +
            "wãn-wãn, mam wʋmdame, sɩda la a woto, gomde, sõama, tʋʋma, yãmba, tõndo, " +
            "fo yʋʋr la a bõe, mam yʋʋr la, mam data, mam rata, koom, sagbo, ligdi, " +
            "raaga, zakẽ, biiga, pagba, rao, kambã, bãngre, karẽngo, logtorẽ."

        private val HALLUCINATION_PATTERNS = listOf(
            "sous-titres réalisés",
            "amara.org",
            "merci d'avoir regardé",
            "abonnez-vous",
            "thank you for watching",
            "subtitles by",
            "transcribed by",
            "sottotitoli",
            "mbc 뉴스",
            "시청해주셔서",
            "ご視聴ありがとう",
            "Субтитры",
            "www."
        )
    }

    /**
     * Transcrit un fichier audio enregistré via l'API OpenAI avec pipeline spécial Mooré.
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
            return@withContext Result.failure(
                IllegalStateException("Clé API OpenAI non configurée. Veuillez ajouter votre clé dans les paramètres.")
            )
        }

        val isMooreHint = languageHint?.lowercase(Locale.ROOT)?.contains("moor") == true
        val whisperLangCode = mapToWhisperLanguageCode(languageHint)

        try {
            // Étape 1 : Décodage acoustique (essai gpt-4o-mini-transcribe en Mooré puis repli whisper-1)
            val rawTranscriptResult = if (isMooreHint) {
                val primaryTry = executeTranscriptionRequest(
                    audioFile = audioFile,
                    apiKey = apiKey,
                    model = PRIMARY_MOORE_MODEL,
                    whisperLangCode = null,
                    isMooreHint = true
                )
                if (primaryTry.isSuccess && primaryTry.getOrNull()?.isNotBlank() == true) {
                    primaryTry
                } else {
                    executeTranscriptionRequest(
                        audioFile = audioFile,
                        apiKey = apiKey,
                        model = FALLBACK_WHISPER_MODEL,
                        whisperLangCode = null,
                        isMooreHint = true
                    )
                }
            } else {
                executeTranscriptionRequest(
                    audioFile = audioFile,
                    apiKey = apiKey,
                    model = FALLBACK_WHISPER_MODEL,
                    whisperLangCode = whisperLangCode,
                    isMooreHint = false
                )
            }

            if (rawTranscriptResult.isFailure) {
                return@withContext rawTranscriptResult
            }

            val rawText = rawTranscriptResult.getOrNull().orEmpty().trim()
            if (rawText.isBlank() || isHallucinationOrPromptEcho(rawText)) {
                Log.d(TAG, "Silence ou hallucination filtrée : \"$rawText\"")
                return@withContext Result.success("")
            }

            // Étape 2 : Si mode Mooré, correction phonétique déterministe + reconstruction neuronale Mooré
            val finalTranscript = if (isMooreHint) {
                val deterministicallyFixed = applyMoorePhoneticCorrections(rawText)
                val neuralFixed = reconstructMooreSentenceWithAi(
                    rawAcousticText = rawText,
                    preCorrectedText = deterministicallyFixed,
                    apiKey = apiKey
                )
                Log.d(TAG, "Whisper Mooré -> Brut: \"$rawText\" | Corrigé: \"$neuralFixed\"")
                neuralFixed
            } else {
                rawText
            }

            Result.success(finalTranscript)
        } catch (e: Exception) {
            Log.e(TAG, "Exception lors de l'appel OpenAI Whisper : ${e.message}", e)
            Result.failure(Exception("Erreur de connexion : ${e.localizedMessage ?: "Vérifiez votre réseau"}"))
        } finally {
            try {
                if (audioFile.exists()) {
                    audioFile.delete()
                }
            } catch (_: Exception) {}
        }
    }

    private fun executeTranscriptionRequest(
        audioFile: File,
        apiKey: String,
        model: String,
        whisperLangCode: String?,
        isMooreHint: Boolean
    ): Result<String> {
        val mediaType = when {
            audioFile.name.endsWith(".wav", ignoreCase = true) -> "audio/wav".toMediaType()
            audioFile.name.endsWith(".mp3", ignoreCase = true) -> "audio/mpeg".toMediaType()
            else -> "audio/m4a".toMediaType()
        }

        val contextPrompt = when {
            isMooreHint -> MOORE_WHISPER_PROMPT
            whisperLangCode == "fr" -> "Transcription fidèle en français du Burkina Faso, Ouagadougou, SAM AI."
            whisperLangCode == "en" -> "Accurate speech-to-text transcription."
            else -> "Ne y beogo, laafi bala, anitché, i ni sogoma, Burkina Faso, Mooré, Dioula, Français."
        }

        val multipartBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", model)
            .addFormDataPart("response_format", "json")
            .addFormDataPart("temperature", "0")
            .addFormDataPart("prompt", contextPrompt)
            .addFormDataPart(
                "file",
                audioFile.name,
                audioFile.asRequestBody(mediaType)
            )

        if (!whisperLangCode.isNullOrBlank()) {
            multipartBuilder.addFormDataPart("language", whisperLangCode)
        }

        val request = Request.Builder()
            .url(WHISPER_API_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(multipartBuilder.build())
            .build()

        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                Log.w(TAG, "API Whisper ($model) [Code ${response.code}]: $responseBody")
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
                return Result.failure(Exception(userMessage))
            }

            val jsonEl = json.parseToJsonElement(responseBody).jsonObject
            val transcribedText = jsonEl["text"]?.jsonPrimitive?.content.orEmpty().trim()
            return Result.success(transcribedText)
        }
    }

    /**
     * Filtre les hallucinations classiques de Whisper sur du bruit de fond ou un silence.
     */
    private fun isHallucinationOrPromptEcho(text: String): Boolean {
        val lower = text.trim().lowercase(Locale.ROOT)
        if (lower.length < 2) return true
        if (HALLUCINATION_PATTERNS.any { lower.contains(it) }) return true
        // Si Whisper répète exactement la liste du prompt de conditionnement
        if (lower.contains("ne y beogo, ne y zaabre, ne y wĩndga")) return true
        return false
    }

    /**
     * Dictionnaire déterministe de correction phonétique Mooré :
     * Répare instantanément les homophones français/anglais produits quand Whisper entend du Mooré.
     */
    fun applyMoorePhoneticCorrections(input: String): String {
        var s = input.trim()
        if (s.isEmpty()) return s

        val replacements = listOf(
            // Salutations du matin (Ne y beogo)
            Regex("(?i)\\b(mais il est beau|n'ayez beau|n'est-il beau|nez beau|les beaux|ni beogo|ne beogo|ney beogo|né i béogo|ne i beogo|nyi beogo|n'i beogo)\\b") to "Ne y beogo",
            // Salutations du soir (Ne y zaabre)
            Regex("(?i)\\b(mais il z'abre|ne zaabre|ney zaabre|ni zabre|ni zaabre|ne i zaabre|né i zaabré|ney zabré)\\b") to "Ne y zaabre",
            // Salutations du midi / soleil (Ne y wĩndga)
            Regex("(?i)\\b(ne windga|ney windga|ni windga|ne i windga|né i windga)\\b") to "Ne y wĩndga",
            // Salutations de la nuit (Ne y yʋngo)
            Regex("(?i)\\b(ne yungo|ney youngo|ni youngo|ne youngo|ne i youngo)\\b") to "Ne y yʋngo",
            // Salutations au travail (Ne y tʋʋma)
            Regex("(?i)\\b(ne tuuma|ney touma|ne touma|ni touma|ne i touma)\\b") to "Ne y tʋʋma",
            // Laafi bala (Paix seulement / Ça va bien)
            Regex("(?i)\\b(la fille va là|l'afrique va là|la vie va là|l'habit va là|lafi bala|la fi bala|laafi ba la|la fille bala)\\b") to "Laafi bala",
            // Laafi beeme (Il y a la santé)
            Regex("(?i)\\b(la fille bémé|la vie bémé|lafi bémé|lafi beme|laafi beme|la fi bémé)\\b") to "Laafi beeme",
            // Kibare (Les nouvelles)
            Regex("(?i)\\b(qui barre|qu'il barre|kibaré|ki baré|kibari|qui bar)\\b") to "Kibare",
            // Barka wʋsgo (Merci beaucoup)
            Regex("(?i)\\b(barka ousgo|barka wousgo|barka ousgou|barca wousgo|barque à ousgo|barka wusgo|barka usgo)\\b") to "Barka wʋsgo",
            // Wẽnd na kõ-d laafi (Que Dieu nous donne la santé)
            Regex("(?i)\\b(wend na kod lafi|ouind na kod lafi|wendé na kod lafi)\\b") to "Wẽnd na kõ-d laafi",
            // Mots courants Mooré transcrits phonétiquement
            Regex("(?i)\\b(ouindé|wendé|wende)\\b") to "Wẽnde",
            Regex("(?i)\\b(wan-wan|wan wan|ouan ouan|wane wane)\\b") to "wãn-wãn",
            Regex("(?i)\\b(wousgo|ousgo|ousgou|wusgo)\\b") to "wʋsgo",
            Regex("(?i)\\b(roundan|rounda|ronda|runda)\\b") to "rũndã",
            Regex("(?i)\\b(yamba)\\b") to "yãmba",
            Regex("(?i)\\b(tondo)\\b") to "tõndo",
            Regex("(?i)\\b(touma|tuma)\\b") to "tʋʋma",
            Regex("(?i)\\b(sogma|somma|soama)\\b") to "sõama",
            Regex("(?i)\\b(boé|boué|bwé|boe)\\b") to "bõe"
        )

        for ((pattern, replacement) in replacements) {
            s = s.replace(pattern, replacement)
        }
        return s
    }

    /**
     * Reconstruction phonétique neuronale Mooré :
     * Prend la transcription acoustique brute de Whisper et répare les syllabes transcrites
     * phonétiquement en français vers un Mooré authentique et naturel.
     */
    private fun reconstructMooreSentenceWithAi(
        rawAcousticText: String,
        preCorrectedText: String,
        apiKey: String
    ): String {
        if (preCorrectedText.isBlank()) return preCorrectedText

        return try {
            val systemInstruction = """
                Tu es un expert linguiste et phonéticien natif du Mooré (Burkina Faso).
                Un utilisateur vient de parler au microphone en Mooré (ou en mélangeant Mooré et Français).
                Le moteur acoustique Whisper a transcrit les sons entendus sous forme brute : "$rawAcousticText"
                Après pré-correction lexicale : "$preCorrectedText"
                
                TA MISSION :
                1. Restitue exactement la phrase que l'utilisateur a prononcée en Mooré propre et naturel.
                2. Si certains mots Mooré ont été transcrits avec une orthographe phonétique française (ex: "mame data koom" -> "Mam data koom", "boé la fo toumda" -> "Bõe la fo tʋmda?", "la fille va là" -> "Laafi bala", "qui barre" -> "Kibare"), corrige-les en vrai Mooré.
                3. Si l'utilisateur a réellement dit des mots en français dans sa phrase, conserve ces mots français intacts.
                4. Réponds UNIQUEMENT par la phrase transcrite finale, sans guillemets, sans explication, sans commentaire.
            """.trimIndent()

            val payload = buildJsonObject {
                put("model", "gpt-4o-mini")
                put("temperature", 0.0)
                put("max_tokens", 120)
                put("messages", buildJsonArray {
                    add(buildJsonObject {
                        put("role", "system")
                        put("content", systemInstruction)
                    })
                    add(buildJsonObject {
                        put("role", "user")
                        put("content", preCorrectedText)
                    })
                })
            }

            val request = Request.Builder()
                .url(CHAT_API_URL)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            fastPhoneticClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return preCorrectedText
                val body = response.body?.string().orEmpty()
                val root = json.parseToJsonElement(body).jsonObject
                val choices = root["choices"]?.jsonArray
                val content = choices?.firstOrNull()?.jsonObject
                    ?.get("message")?.jsonObject
                    ?.get("content")?.jsonPrimitive?.content
                    ?.trim()
                    ?.removeSurrounding("\"")
                    ?.trim()

                if (!content.isNullOrBlank()) content else preCorrectedText
            }
        } catch (e: Exception) {
            Log.w(TAG, "Repli sur pré-correction Mooré déterministe: ${e.message}")
            preCorrectedText
        }
    }

    /**
     * Mappage des langues de l'application vers les codes ISO 639-1 supportés par Whisper.
     * Pour les langues nationales sans code ISO Whisper strict (Mooré, Dioula), on retourne null
     * pour laisser Whisper utiliser son décodeur acoustique multilingue guidé par le prompt Mooré.
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
            else -> null
        }
    }
}
