package com.example.data.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.example.data.local.ApiKeyManager
import com.example.data.local.AuditDao
import com.example.data.local.ChatDao
import com.example.data.local.ConversationEntity
import com.example.data.local.LinguisticDao
import com.example.data.local.MessageEntity
import com.example.data.local.WebSource
import com.example.data.remote.GoogleTranslationService
import com.example.util.SecurityUtils
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import com.example.util.GeminiErrorHandler

data class ChatMessage(
    val role: String,
    val content: String
)

data class GeminiRequestResult(
    val success: Boolean = true,
    val text: String = "",
    val sources: List<WebSource> = emptyList(),
    val isSearchError: Boolean = false,
    val errorMessage: String? = null
)

data class ImageTranslationResult(
    val extractedText: String = "",
    val translatedText: String = ""
)

class AiRepository(
    private val chatDao: ChatDao,
    private val linguisticDao: LinguisticDao,
    val apiKeyManager: ApiKeyManager,
    private val auditDao: AuditDao? = null
) {
    private val openAiBackendService = com.example.data.remote.OpenAiBackendService()
    fun getConversationsForUser(userId: String): Flow<List<ConversationEntity>> {
        return chatDao.getConversationsForUser(userId)
    }

    fun getMessages(conversationId: String): Flow<List<MessageEntity>> {
        return chatDao.getMessagesForConversation(conversationId)
    }

    suspend fun getMessagesList(conversationId: String): List<MessageEntity> {
        return chatDao.getMessagesListForConversation(conversationId)
    }

    fun sendMessageStreaming(conversationId: String, text: String, userId: String = "guest"): Flow<String> = flow {
        chatDao.insertMessage(
            MessageEntity(
                conversationId = conversationId,
                text = text,
                sender = "user",
                timestamp = System.currentTimeMillis()
            )
        )
        chatDao.insertConversation(
            ConversationEntity(
                id = conversationId,
                userId = userId,
                title = text.take(30),
                lastMessage = text,
                timestamp = System.currentTimeMillis()
            )
        )

        val apiKey = apiKeyManager.getActiveGeminiApiKey().ifBlank {
            try {
                com.example.BuildConfig.GEMINI_API_KEY.trim().removeSurrounding("\"").removeSurrounding("'").trim()
            } catch (_: Throwable) { "" }
        }
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            val errText = "⚠️ Service SAM AI temporairement indisponible."
            chatDao.insertMessage(
                MessageEntity(
                    conversationId = conversationId,
                    text = errText,
                    sender = "ai",
                    timestamp = System.currentTimeMillis()
                )
            )
            emit(errText)
            return@flow
        }
        try {
            val model = GenerativeModel(
                modelName = "gemini-3.5-flash",
                apiKey = apiKey
            )

            val systemInstruction = "Tu es SAM AI, l'assistant intelligent d'élite, cultivé et polyvalent. Réponds directement, intelligemment et sans bavardage superflu. Ne te présente JAMAIS (pas de 'Bonjour', 'Je suis SAM AI') dans une discussion déjà engagée. Si l'utilisateur demande de détailler ou pose une question complémentaire, approfondis directement et de manière riche et structurée."
            val prompt = "$systemInstruction\n\nQuestion de l'utilisateur : $text"

            var fullResponse = ""
            model.generateContentStream(prompt).collect { chunk ->
                val chunkText = chunk.text ?: ""
                fullResponse += chunkText
                emit(fullResponse)
            }

            if (fullResponse.isNotBlank()) {
                chatDao.insertMessage(
                    MessageEntity(
                        conversationId = conversationId,
                        text = fullResponse,
                        sender = "ai",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        } catch (e: Throwable) {
            val errText = "⚠️ Erreur de connexion : ${e.localizedMessage ?: "Vérifiez votre réseau"}"
            chatDao.insertMessage(
                MessageEntity(
                    conversationId = conversationId,
                    text = errText,
                    sender = "ai",
                    timestamp = System.currentTimeMillis()
                )
            )
            emit(errText)
        }
    }.flowOn(Dispatchers.IO)

    suspend fun deleteConversationSecure(conversationId: String, userId: String): Boolean = withContext(Dispatchers.IO) {
        chatDao.deleteMessagesForConversation(conversationId)
        chatDao.deleteConversation(conversationId)
        true
    }

    suspend fun clearConversationsForUser(userId: String) = withContext(Dispatchers.IO) {
        chatDao.deleteAllConversationsForUser(userId)
        chatDao.deleteAllMessagesForUser(userId)
    }

    suspend fun translateDirectly(
        srcText: String,
        srcLang: String,
        tgtLang: String,
        localContext: String = ""
    ): String = withContext(Dispatchers.IO) {
        val cleanSrc = srcText.trim()
        if (cleanSrc.isBlank()) return@withContext ""

        val effectiveKey = apiKeyManager.getActiveGeminiApiKey().ifBlank {
            try {
                com.example.BuildConfig.GEMINI_API_KEY.trim().removeSurrounding("\"").removeSurrounding("'").trim()
            } catch (_: Throwable) { "" }
        }

        val systemInstructionText = """
Tu es un traducteur expert linguistique de très haute fidélité.
Tu dois traduire STRICTEMENT le texte donné du $srcLang vers le $tgtLang.

RÈGLES D'OR ABSOLUES :
1. Rends UNIQUEMENT et EXCLUSIVEMENT la traduction exacte en $tgtLang.
2. N'inclus JAMAIS aucun mot en $srcLang, aucune explication, aucune note, aucun astérisque (*), aucun guillemet.
3. Ne dis JAMAIS "Voici la traduction", "En Mooré", "En Dioula", "Décomposition", etc.
4. Rends une phrase fluide, naturelle et directement utilisable.
${if (localContext.isNotBlank()) "Lexique de référence :\n$localContext" else ""}
        """.trimIndent()

        val isMooreOrDioula = srcLang.contains("moor", ignoreCase = true) ||
            tgtLang.contains("moor", ignoreCase = true) ||
            srcLang.contains("dioula", ignoreCase = true) ||
            tgtLang.contains("dioula", ignoreCase = true) ||
            srcLang.contains("jula", ignoreCase = true) ||
            tgtLang.contains("jula", ignoreCase = true)

        // 0. Pour le Mooré et le Dioula : le Dictionnaire Local & Moteur d'Assemblage prend le relais
        if (isMooreOrDioula) {
            val localAssembled = com.example.data.engine.DictionaryLookupEngine.assembleSentenceFromLocalDictionary(
                input = cleanSrc,
                sourceLang = srcLang,
                targetLang = tgtLang
            )
            if (localAssembled != null && localAssembled.translatedText.isNotBlank() &&
                !localAssembled.translatedText.equals(cleanSrc, ignoreCase = true)
            ) {
                return@withContext localAssembled.translatedText.trim()
            }
        }

        // 1. Pour toutes les autres langues : OpenAI (GPT-4o) prend le relais
        try {
            val openAiTranslation = openAiBackendService.translateWithOpenAi(cleanSrc, srcLang, tgtLang)
            if (openAiTranslation.isNotBlank() && !openAiTranslation.equals(cleanSrc, ignoreCase = true)) {
                return@withContext openAiTranslation.trim()
            }
        } catch (e: Throwable) {
            android.util.Log.w("AiRepository", "OpenAI translation notice: ${e.message}")
        }

        // 1. Moteur IA principal via Retrofit REST (Direct, ultra-rapide et fiable)
        if (effectiveKey.isNotBlank() && effectiveKey != "MY_GEMINI_API_KEY") {
            val candidateModels = listOf("gemini-3.5-flash", "gemini-flash-latest")
            for (candidateModel in candidateModels) {
                try {
                    val req = com.example.data.remote.GenerateContentRequest(
                        contents = listOf(
                            com.example.data.remote.Content(
                                role = "user",
                                parts = listOf(com.example.data.remote.Part(text = "Texte à traduire du $srcLang vers le $tgtLang :\n$cleanSrc"))
                            )
                        ),
                        systemInstruction = com.example.data.remote.Content(
                            parts = listOf(com.example.data.remote.Part(text = systemInstructionText))
                        ),
                        generationConfig = com.example.data.remote.GenerationConfig(
                            temperature = 0.1f,
                            maxOutputTokens = 1000
                        )
                    )
                    val resp = com.example.data.remote.RetrofitClient.geminiService.generateContent(
                        model = candidateModel,
                        apiKey = effectiveKey,
                        request = req
                    )
                    val rawResult = resp.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text.orEmpty().trim()
                    val cleanResult = extractCleanTranslation(rawResult, tgtLang)

                    if (cleanResult.isNotBlank() && !cleanResult.equals(cleanSrc, ignoreCase = true) && !isExplanatoryOrMixed(cleanResult, tgtLang)) {
                        return@withContext cleanResult
                    }
                } catch (e: Throwable) {
                    android.util.Log.w("AiRepository", "Retrofit translation with $candidateModel failed: ${e.message}")
                }
            }

            // 2. Moteur IA de secours via GenerativeModel SDK
            for (candidateModel in candidateModels) {
                try {
                    val model = GenerativeModel(
                        modelName = candidateModel,
                        apiKey = effectiveKey,
                        systemInstruction = content { text(systemInstructionText) }
                    )
                    val prompt = "Texte à traduire du $srcLang vers le $tgtLang : \"$cleanSrc\""
                    val response = model.generateContent(prompt)
                    val rawResult = response.text ?: ""
                    val cleanResult = extractCleanTranslation(rawResult, tgtLang)

                    if (cleanResult.isNotBlank() && !cleanResult.equals(cleanSrc, ignoreCase = true) && !isExplanatoryOrMixed(cleanResult, tgtLang)) {
                        return@withContext cleanResult
                    }
                } catch (_: Throwable) {
                    // Modèle suivant si indisponible
                }
            }
        }

        // 3. Google Translate officiel de repli pour langues supportées
        try {
            val googleResult = GoogleTranslationService.translate(cleanSrc, srcLang, tgtLang)
            if (!googleResult.isNullOrBlank() && !googleResult.equals(cleanSrc, ignoreCase = true)) {
                return@withContext googleResult.trim()
            }
        } catch (_: Throwable) {}

        ""
    }

    private fun extractCleanTranslation(raw: String, targetLang: String): String {
        var text = raw.trim()
        text = text.removeSurrounding("```json", "```").removeSurrounding("```", "```").trim()

        if (text.contains("se traduit par :", ignoreCase = true)) {
            val candidate = text.substringAfter("se traduit par :").trim()
            if (candidate.isNotBlank()) text = candidate
        } else if (text.contains("se traduit par", ignoreCase = true)) {
            val candidate = text.substringAfter("se traduit par").trim()
            if (candidate.isNotBlank()) text = candidate
        }

        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.size > 1) {
            val boldMatch = Regex("""\*\*([^*]+)\*\*""").find(text)?.groupValues?.get(1)?.trim()
            val quoteMatch = Regex("""[«"]([^»"]+)[»"]""").find(text)?.groupValues?.get(1)?.trim()

            text = when {
                !quoteMatch.isNullOrBlank() && !quoteMatch.contains("se traduit", ignoreCase = true) -> quoteMatch
                !boldMatch.isNullOrBlank() && !boldMatch.contains("se traduit", ignoreCase = true) -> boldMatch
                else -> lines.firstOrNull { 
                    !it.contains("se traduit", ignoreCase = true) && 
                    !it.startsWith("Décomposition", ignoreCase = true) &&
                    !it.startsWith("*")
                } ?: lines.first()
            }
        }

        text = text.replace("*", "").replace("`", "")
        text = text.replace(Regex("""^(Traduction|Translation|En Mooré|En Dioula|En Haoussa|En Français|En Anglais|Traduction en [^:]+)\s*:\s*""", RegexOption.IGNORE_CASE), "")
        text = text.removeSurrounding("\"").removeSurrounding("«", "»").trim()
        return text
    }

    private fun isExplanatoryOrMixed(text: String, targetLang: String): Boolean {
        val lower = text.lowercase()
        if (lower.contains("voici la traduction") || lower.contains("décomposition")) {
            return true
        }
        if (targetLang.contains("moor", ignoreCase = true) && lower.contains("signifie en français")) {
            return true
        }
        return false
    }

    suspend fun translateImage(
        bitmap: Bitmap,
        srcLang: String,
        tgtLang: String
    ): String = withContext(Dispatchers.IO) {
        val apiKey = apiKeyManager.getActiveGeminiApiKey()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext ""
        }
        try {
            val model = GenerativeModel(
                modelName = "gemini-3.5-flash",
                apiKey = apiKey
            )
            val inputContent = content {
                image(bitmap)
                text("Extrais tout le texte visible dans cette image et traduis-le fidèlement en $tgtLang. Si le texte est en Mooré ou Dioula, donne la traduction précise en Français.")
            }
            val response = model.generateContent(inputContent)
            response.text?.trim() ?: "Aucun texte détecté."
        } catch (e: Throwable) {
            "Erreur lors de l'analyse d'image : ${e.localizedMessage ?: "Veuillez réessayer"}"
        }
    }

    suspend fun translateImage(
        apiKey: String,
        base64Data: String,
        mimeType: String,
        sourceLang: String,
        targetLang: String
    ): ImageTranslationResult = withContext(Dispatchers.IO) {
        val key = apiKey.ifBlank { apiKeyManager.getActiveGeminiApiKey() }
        if (key.isBlank() || key == "MY_GEMINI_API_KEY") {
            return@withContext ImageTranslationResult(
                extractedText = "",
                translatedText = "Service de vision non disponible."
            )
        }
        try {
            val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)

            val model = GenerativeModel(
                modelName = "gemini-3.5-flash",
                apiKey = key
            )
            val inputContent = content {
                image(bitmap)
                text("Extrais le texte visible et traduis-le de $sourceLang vers $targetLang. Réponds sous la forme: TEXTE EXTRAIT: [texte]\nTRADUCTION: [traduction]")
            }
            val response = model.generateContent(inputContent)
            val full = response.text ?: ""
            val extracted = full.substringAfter("TEXTE EXTRAIT:").substringBefore("TRADUCTION:").trim()
            val translated = full.substringAfter("TRADUCTION:").trim()
            if (extracted.isNotBlank() && translated.isNotBlank()) {
                ImageTranslationResult(extractedText = extracted, translatedText = translated)
            } else {
                ImageTranslationResult(extractedText = full, translatedText = full)
            }
        } catch (e: Throwable) {
            ImageTranslationResult(
                extractedText = "",
                translatedText = "Erreur OCR : ${e.localizedMessage ?: "Veuillez réessayer"}"
            )
        }
    }

    suspend fun executeGeminiRequest(
        prompt: String,
        systemPrompt: String = ""
    ): String = withContext(Dispatchers.IO) {
        val apiKey = apiKeyManager.getActiveGeminiApiKey()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext ""
        }
        try {
            val model = GenerativeModel(
                modelName = "gemini-3.5-flash",
                apiKey = apiKey
            )
            val fullPrompt = if (systemPrompt.isNotBlank()) "$systemPrompt\n\n$prompt" else prompt
            val response = model.generateContent(fullPrompt)
            response.text?.trim() ?: ""
        } catch (e: Throwable) {
            com.example.util.GeminiErrorHandler.parse(e).userFriendlyMessage
        }
    }

    suspend fun executeGeminiRequest(
        apiKey: String,
        messages: List<ChatMessage>,
        systemInstruction: String,
        enableSearch: Boolean,
        temperature: Double,
        maxOutputTokens: Int,
        preferredModel: String
    ): GeminiRequestResult = withContext(Dispatchers.IO) {
        val effectiveKey = apiKey.ifBlank { apiKeyManager.getActiveGeminiApiKey() }
        if (effectiveKey.isBlank() || effectiveKey == "MY_GEMINI_API_KEY") {
            return@withContext GeminiRequestResult(
                success = false,
                text = "",
                errorMessage = "Service IA temporairement indisponible."
            )
        }

        // Modèles officiels supportés et vérifiés
        val baseModels = listOf("gemini-3.5-flash", "gemini-flash-latest", "gemini-3.1-flash-lite-preview", "gemini-3.1-pro-preview")
        val cleanPreferred = preferredModel.trim()
        val modelsToTry = if (cleanPreferred.isNotBlank() && cleanPreferred != "gemini-3.6-flash") {
            (listOf(cleanPreferred) + baseModels).distinct()
        } else {
            baseModels
        }

        var googleSearchAttempted = false
        var googleSearchFailed = false
        var lastParsedError: com.example.util.ParsedGeminiError? = null

        // 1. Si la recherche Google Search intégrée est demandée, tenter l'appel avec le Grounding
        if (enableSearch) {
            googleSearchAttempted = true
            val googleSearchTool = kotlinx.serialization.json.buildJsonObject {
                put("googleSearch", kotlinx.serialization.json.buildJsonObject {})
            }

            for (targetModel in modelsToTry) {
                // Tentatives limitées avec délai progressif pour les erreurs temporaires (503 / 429)
                val maxAttempts = 2
                for (attempt in 1..maxAttempts) {
                    try {
                        val req = com.example.data.remote.GenerateContentRequest(
                            contents = messages.map {
                                com.example.data.remote.Content(
                                    role = it.role,
                                    parts = listOf(com.example.data.remote.Part(text = it.content))
                                )
                            },
                            systemInstruction = if (systemInstruction.isNotBlank()) {
                                com.example.data.remote.Content(
                                    parts = listOf(com.example.data.remote.Part(text = systemInstruction))
                                )
                            } else null,
                            generationConfig = com.example.data.remote.GenerationConfig(
                                temperature = temperature.toFloat(),
                                maxOutputTokens = maxOutputTokens
                            ),
                            tools = listOf(googleSearchTool)
                        )
                        val resp = com.example.data.remote.RetrofitClient.geminiService.generateContent(
                            model = targetModel,
                            apiKey = effectiveKey,
                            request = req
                        )
                        val candidate = resp.candidates.firstOrNull()
                        val text = candidate?.content?.parts?.firstOrNull()?.text?.trim() ?: ""
                        val sources = mutableListOf<WebSource>()
                        candidate?.groundingMetadata?.groundingChunks?.forEach { chunk ->
                            val uri = chunk.web?.uri.orEmpty().trim()
                            val title = chunk.web?.title.orEmpty().trim()
                            if (uri.isNotBlank()) {
                                val domain = try {
                                    java.net.URI(uri).host?.removePrefix("www.").orEmpty()
                                } catch (_: Throwable) { "" }
                                sources.add(
                                    WebSource(
                                        title = title.ifBlank { domain.ifBlank { "Google Search" } },
                                        url = uri,
                                        domain = domain
                                    )
                                )
                            }
                        }

                        if (text.isNotBlank()) {
                            return@withContext GeminiRequestResult(
                                success = true,
                                text = text,
                                sources = sources,
                                isSearchError = false
                            )
                        }
                    } catch (e: Throwable) {
                        val parsed = GeminiErrorHandler.parse(e)
                        lastParsedError = parsed
                        android.util.Log.w("AiRepository", "Google Search attempt $attempt on $targetModel failed: ${parsed.rawMessage}")

                        if (attempt < maxAttempts && (parsed.isTemporaryOverload || parsed.isRateLimited)) {
                            val waitSec = (parsed.retryAfterSeconds ?: 1L).coerceIn(1L, 3L)
                            delay(waitSec * 1000L)
                        } else {
                            // Basculement vers le modèle suivant
                            break
                        }
                    }
                }
            }
            googleSearchFailed = true
        }

        // 2. Appel direct SANS outil (direct text generation)
        // Utilisé si enableSearch == false OU comme solution de secours si Google Search a échoué
        for (targetModel in modelsToTry) {
            val maxAttempts = 2
            for (attempt in 1..maxAttempts) {
                try {
                    val req = com.example.data.remote.GenerateContentRequest(
                        contents = messages.map {
                            com.example.data.remote.Content(
                                role = it.role,
                                parts = listOf(com.example.data.remote.Part(text = it.content))
                            )
                        },
                        systemInstruction = if (systemInstruction.isNotBlank()) {
                            com.example.data.remote.Content(
                                parts = listOf(com.example.data.remote.Part(text = systemInstruction))
                            )
                        } else null,
                        generationConfig = com.example.data.remote.GenerationConfig(
                            temperature = temperature.toFloat(),
                            maxOutputTokens = maxOutputTokens
                        ),
                        tools = null
                    )
                    val resp = com.example.data.remote.RetrofitClient.geminiService.generateContent(
                        model = targetModel,
                        apiKey = effectiveKey,
                        request = req
                    )
                    val candidate = resp.candidates.firstOrNull()
                    val text = candidate?.content?.parts?.firstOrNull()?.text?.trim() ?: ""
                    if (text.isNotBlank()) {
                        return@withContext GeminiRequestResult(
                            success = true,
                            text = text,
                            sources = emptyList(),
                            isSearchError = googleSearchAttempted && googleSearchFailed
                        )
                    }
                } catch (e: Throwable) {
                    val parsed = GeminiErrorHandler.parse(e)
                    lastParsedError = parsed
                    android.util.Log.w("AiRepository", "Direct Gemini attempt $attempt on $targetModel failed: ${parsed.rawMessage}")

                    if (attempt < maxAttempts && (parsed.isTemporaryOverload || parsed.isRateLimited)) {
                        val waitSec = (parsed.retryAfterSeconds ?: 1L).coerceIn(1L, 3L)
                        delay(waitSec * 1000L)
                    } else {
                        // Basculement vers le modèle suivant
                        break
                    }
                }
            }
        }

        val finalErrorMessage = lastParsedError?.userFriendlyMessage
            ?: "Service d'intelligence artificielle momentanément indisponible. Veuillez vérifier votre réseau."

        GeminiRequestResult(
            success = false,
            text = "",
            isSearchError = googleSearchAttempted && googleSearchFailed,
            errorMessage = finalErrorMessage
        )
    }
}
