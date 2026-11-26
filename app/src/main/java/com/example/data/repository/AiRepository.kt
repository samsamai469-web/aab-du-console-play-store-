package com.example.data.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.example.data.local.ApiKeyManager
import com.example.data.local.AuditDao
import com.example.data.local.ChatDao
import com.example.data.local.ConversationEntity
import com.example.data.local.LinguisticDao
import com.example.data.local.MessageEntity
import com.example.data.local.WebSource
import com.example.data.remote.DirectContentInput
import com.example.data.remote.DirectPartInput
import com.example.data.remote.GeminiApiHttpException
import com.example.data.remote.GoogleGeminiDirectClient
import com.example.data.remote.GoogleTranslationService
import com.example.data.remote.MissingGeminiApiKeyException
import com.example.util.GeminiErrorHandler
import com.example.util.ParsedGeminiError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

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
    companion object {
        private const val TAG = "AiRepository"
        const val DEFAULT_GEMINI_MODEL = "gemini-3.8-flash"
        const val REASONING_GEMINI_MODEL = "gemini-4-argon"
        const val LITE_GEMINI_MODEL = "gemini-3.5-flash-lite"
        private const val AI_TIMEOUT_MS = 10_000L

        @Volatile
        private var preferredWorkingChatModel: String? = null
    }

    private fun resolveApiKey(overrideKey: String = ""): String {
        val clean = overrideKey.trim().removeSurrounding("\"").removeSurrounding("'").trim()
        if (clean.isNotBlank() && clean != "MY_GEMINI_API_KEY") {
            return clean
        }
        return apiKeyManager.getActiveGeminiApiKey()
    }

    private fun bitmapToJpegBytes(bitmap: Bitmap): ByteArray {
        val maxDim = 1600
        val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
            val ratio = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height)
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt().coerceAtLeast(1),
                (bitmap.height * ratio).toInt().coerceAtLeast(1),
                true
            )
        } else {
            bitmap
        }
        return ByteArrayOutputStream().use { baos ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 86, baos)
            baos.toByteArray()
        }
    }

    private fun isFatalApiKeyError(parsed: ParsedGeminiError, raw: Throwable): Boolean {
        return parsed.isAuthError ||
            parsed.httpCode == 403 ||
            parsed.httpCode == 401 ||
            raw is MissingGeminiApiKeyException
    }

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

        try {
            val apiKey = resolveApiKey()
            val systemText =
                "Tu es SAM AI, l'assistant intelligent d'élite, cultivé et polyvalent, conçu par Abdoul Mohamine Sawadogo. " +
                    "Réponds directement, intelligemment et sans bavardage superflu. " +
                    "Ne te présente JAMAIS (pas de 'Bonjour', 'Je suis SAM AI') dans une discussion déjà engagée. " +
                    "Si l'utilisateur demande de détailler ou pose une question complémentaire, approfondis directement et de manière riche et structurée."

            val directContents = listOf(
                DirectContentInput(
                    role = "user",
                    parts = listOf(DirectPartInput(text = text))
                )
            )

            var fullResponse = ""
            var lastStreamError: Throwable? = null
            for (streamModel in listOf(LITE_GEMINI_MODEL, DEFAULT_GEMINI_MODEL).distinct()) {
                try {
                    fullResponse = ""
                    withTimeout(45_000L) {
                        GoogleGeminiDirectClient.streamGenerateContent(
                            apiKey = apiKey,
                            model = streamModel,
                            contents = directContents,
                            systemInstruction = systemText
                        ) { chunk ->
                            if (chunk.text.isNotEmpty()) {
                                fullResponse += chunk.text
                                emit(fullResponse)
                            }
                        }
                    }
                    if (fullResponse.isNotBlank()) break
                } catch (ce: CancellationException) {
                    if (ce is TimeoutCancellationException) {
                        lastStreamError = ce
                    } else {
                        throw ce
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "Stream with $streamModel failed: ${e.message}", e)
                    lastStreamError = e
                    val parsed = GeminiErrorHandler.parse(e)
                    if (isFatalApiKeyError(parsed, e)) break
                }
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
            } else if (lastStreamError != null) {
                throw lastStreamError
            }
        } catch (ce: CancellationException) {
            if (ce is TimeoutCancellationException) {
                val timeoutMsg = GeminiErrorHandler.parse(ce).userFriendlyMessage
                chatDao.insertMessage(
                    MessageEntity(
                        conversationId = conversationId,
                        text = timeoutMsg,
                        sender = "ai",
                        timestamp = System.currentTimeMillis()
                    )
                )
                emit(timeoutMsg)
            } else {
                throw ce
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Direct Google Gemini streaming failed", e)
            val errMsg = GeminiErrorHandler.parse(e).userFriendlyMessage
            chatDao.insertMessage(
                MessageEntity(
                    conversationId = conversationId,
                    text = errMsg,
                    sender = "ai",
                    timestamp = System.currentTimeMillis()
                )
            )
            emit(errMsg)
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

        val isHausa = srcLang.contains("haoussa", ignoreCase = true) ||
            tgtLang.contains("haoussa", ignoreCase = true) ||
            srcLang.contains("hausa", ignoreCase = true) ||
            tgtLang.contains("hausa", ignoreCase = true)

        // 0. Pour le Mooré et le Dioula : le Dictionnaire Local & Moteur d'Assemblage est prioritaire
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

        // 0b. Pour les paires standard : Google Translate officiel ultra-rapide (~250ms)
        if (!isMooreOrDioula && !isHausa) {
            try {
                val googleFast = GoogleTranslationService.translate(cleanSrc, srcLang, tgtLang)
                if (!googleFast.isNullOrBlank() && !googleFast.equals(cleanSrc, ignoreCase = true)) {
                    return@withContext googleFast.trim()
                }
            } catch (_: Throwable) {}
        }

        // 1. Moteur IA Google Gemini direct (gemini-3.5-flash-lite -> gemini-3.8-flash)
        val apiKey = resolveApiKey()
        if (apiKey.isNotBlank()) {
            val candidateModels = listOf(LITE_GEMINI_MODEL, DEFAULT_GEMINI_MODEL).distinct()
            val prompt = "Texte à traduire du $srcLang vers le $tgtLang :\n$cleanSrc"
            val contents = listOf(DirectContentInput(role = "user", parts = listOf(DirectPartInput(text = prompt))))
            val translateStartMs = System.currentTimeMillis()
            for (candidateModel in candidateModels) {
                val elapsed = System.currentTimeMillis() - translateStartMs
                val remaining = 8_500L - elapsed
                if (remaining < 1_200L) break
                val callTimeout = minOf(4_500L, remaining)
                val resultDeferred = CompletableDeferred<String?>()
                val isAuthAbort = AtomicBoolean(false)
                val aiJob = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        val response = GoogleGeminiDirectClient.generateContent(
                            apiKey = apiKey,
                            model = candidateModel,
                            contents = contents,
                            systemInstruction = systemInstructionText,
                            temperature = 0.1f,
                            maxOutputTokens = 600
                        )
                        val rawResult = response.text.trim()
                        val cleanResult = extractCleanTranslation(rawResult, tgtLang)
                        if (cleanResult.isNotBlank() &&
                            !cleanResult.equals(cleanSrc, ignoreCase = true) &&
                            !isExplanatoryOrMixed(cleanResult, tgtLang)
                        ) {
                            resultDeferred.complete(cleanResult)
                        } else {
                            resultDeferred.complete(null)
                        }
                    } catch (e: Throwable) {
                        Log.w(TAG, "Direct Google Gemini translation with $candidateModel failed: ${e.message}")
                        if (e is MissingGeminiApiKeyException ||
                            (e is GeminiApiHttpException && (e.httpCode == 401 || e.httpCode == 403))
                        ) {
                            isAuthAbort.set(true)
                        }
                        resultDeferred.complete(null)
                    }
                }
                val fastAnswer = withTimeoutOrNull(callTimeout) {
                    resultDeferred.await()
                }
                if (!fastAnswer.isNullOrBlank()) {
                    return@withContext fastAnswer
                } else {
                    aiJob.cancel()
                    if (isAuthAbort.get()) break
                }
            }
        }

        // 2. Google Translate officiel de repli pour toutes les autres langues supportées
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
        text = text.replace(
            Regex("""^(Traduction|Translation|En Mooré|En Dioula|En Haoussa|En Français|En Anglais|Traduction en [^:]+)\s*:\s*""", RegexOption.IGNORE_CASE),
            ""
        )
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
        try {
            val apiKey = resolveApiKey()
            val jpegBytes = bitmapToJpegBytes(bitmap)
            val prompt =
                "Extrais tout le texte visible dans cette image et traduis-le fidèlement en $tgtLang. " +
                    "Si le texte est en Mooré ou Dioula, donne la traduction précise en Français."
            val contents = listOf(
                DirectContentInput(
                    role = "user",
                    parts = listOf(
                        DirectPartInput(inlineDataBytes = jpegBytes, inlineMimeType = "image/jpeg"),
                        DirectPartInput(text = prompt)
                    )
                )
            )
            val response = withTimeout(AI_TIMEOUT_MS) {
                GoogleGeminiDirectClient.generateContent(
                    apiKey = apiKey,
                    model = DEFAULT_GEMINI_MODEL,
                    contents = contents
                )
            }
            response.text.trim().ifBlank { "Aucun texte détecté." }
        } catch (ce: CancellationException) {
            if (ce is TimeoutCancellationException) {
                "Délai dépassé lors de l'analyse d'image. Veuillez réessayer."
            } else {
                throw ce
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Image translation failed", e)
            GeminiErrorHandler.parse(e).userFriendlyMessage
        }
    }

    suspend fun translateImage(
        apiKey: String,
        base64Data: String,
        mimeType: String,
        sourceLang: String,
        targetLang: String
    ): ImageTranslationResult = withContext(Dispatchers.IO) {
        try {
            val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                ?: return@withContext ImageTranslationResult(
                    extractedText = "",
                    translatedText = "Impossible de décoder l'image sélectionnée."
                )

            val resolvedKey = resolveApiKey(apiKey)
            val jpegBytes = bitmapToJpegBytes(bitmap)
            val prompt =
                "Extrais le texte visible et traduis-le de $sourceLang vers $targetLang. " +
                    "Réponds sous la forme: TEXTE EXTRAIT: [texte]\nTRADUCTION: [traduction]"
            val contents = listOf(
                DirectContentInput(
                    role = "user",
                    parts = listOf(
                        DirectPartInput(inlineDataBytes = jpegBytes, inlineMimeType = "image/jpeg"),
                        DirectPartInput(text = prompt)
                    )
                )
            )
            val response = withTimeout(AI_TIMEOUT_MS) {
                GoogleGeminiDirectClient.generateContent(
                    apiKey = resolvedKey,
                    model = DEFAULT_GEMINI_MODEL,
                    contents = contents
                )
            }
            val full = response.text
            val extracted = full.substringAfter("TEXTE EXTRAIT:").substringBefore("TRADUCTION:").trim()
            val translated = full.substringAfter("TRADUCTION:").trim()
            if (extracted.isNotBlank() && translated.isNotBlank()) {
                ImageTranslationResult(extractedText = extracted, translatedText = translated)
            } else {
                ImageTranslationResult(extractedText = full, translatedText = full)
            }
        } catch (ce: CancellationException) {
            if (ce is TimeoutCancellationException) {
                ImageTranslationResult(
                    extractedText = "",
                    translatedText = "Délai dépassé lors de l'analyse OCR. Veuillez réessayer."
                )
            } else {
                throw ce
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Base64 image translation failed", e)
            ImageTranslationResult(
                extractedText = "",
                translatedText = GeminiErrorHandler.parse(e).userFriendlyMessage
            )
        }
    }

    suspend fun executeGeminiRequest(
        prompt: String,
        systemPrompt: String = ""
    ): String = withContext(Dispatchers.IO) {
        try {
            val apiKey = resolveApiKey()
            val contents = listOf(DirectContentInput(role = "user", parts = listOf(DirectPartInput(text = prompt))))
            val response = withTimeout(AI_TIMEOUT_MS) {
                GoogleGeminiDirectClient.generateContent(
                    apiKey = apiKey,
                    model = DEFAULT_GEMINI_MODEL,
                    contents = contents,
                    systemInstruction = systemPrompt.takeIf { it.isNotBlank() }
                )
            }
            response.text.trim()
        } catch (ce: CancellationException) {
            if (ce is TimeoutCancellationException) {
                "Le serveur d'intelligence artificielle met trop de temps à répondre. Veuillez réessayer."
            } else {
                throw ce
            }
        } catch (e: Throwable) {
            Log.e(TAG, "executeGeminiRequest(prompt) failed", e)
            GeminiErrorHandler.parse(e).userFriendlyMessage
        }
    }

    suspend fun executeGeminiRequest(
        apiKey: String = "",
        messages: List<ChatMessage>,
        systemInstruction: String,
        enableSearch: Boolean,
        temperature: Double,
        maxOutputTokens: Int,
        preferredModel: String = DEFAULT_GEMINI_MODEL
    ): GeminiRequestResult = withContext(Dispatchers.IO) {
        val baseModels = listOfNotNull(
            preferredWorkingChatModel,
            LITE_GEMINI_MODEL,
            DEFAULT_GEMINI_MODEL
        ).distinct()
        val cleanPreferred = preferredModel.trim()
        val allowedNewModels = setOf(DEFAULT_GEMINI_MODEL, REASONING_GEMINI_MODEL, LITE_GEMINI_MODEL)
        val modelsToTry = if (cleanPreferred in allowedNewModels) {
            (listOf(cleanPreferred) + baseModels).distinct()
        } else {
            baseModels
        }

        val nonBlankMessages = messages.filter { it.content.isNotBlank() }
        if (nonBlankMessages.isEmpty()) {
            return@withContext GeminiRequestResult(
                success = false,
                text = "",
                errorMessage = "Le message envoyé est vide."
            )
        }

        val resolvedKey = resolveApiKey(apiKey)
        val directContents = nonBlankMessages.map { msg ->
            val normalizedRole = if (msg.role.equals("user", ignoreCase = true)) "user" else "model"
            DirectContentInput(
                role = normalizedRole,
                parts = listOf(DirectPartInput(text = msg.content))
            )
        }
        val sysText = systemInstruction.takeIf { it.isNotBlank() }

        var googleSearchAttempted = false
        var googleSearchFailed = false
        var lastParsedError: ParsedGeminiError? = null
        val requestStartMs = System.currentTimeMillis()
        val maxTotalBudgetMs = if (maxOutputTokens <= 500) 30_000L else 45_000L

        // 1. Optional Google Search
        if (enableSearch) {
            googleSearchAttempted = true
            val searchModel = modelsToTry.first()
            try {
                val response = withTimeout(minOf(9_000L, maxTotalBudgetMs)) {
                    GoogleGeminiDirectClient.generateContent(
                        apiKey = resolvedKey,
                        model = searchModel,
                        contents = directContents,
                        systemInstruction = sysText,
                        enableGoogleSearch = true,
                        temperature = temperature.toFloat(),
                        maxOutputTokens = maxOutputTokens
                    )
                }
                val text = response.text.trim()
                if (text.isNotBlank()) {
                    preferredWorkingChatModel = searchModel
                    return@withContext GeminiRequestResult(
                        success = true,
                        text = text,
                        sources = response.sources,
                        isSearchError = false
                    )
                }
            } catch (ce: CancellationException) {
                if (ce is TimeoutCancellationException) {
                    lastParsedError = GeminiErrorHandler.parse(ce)
                    Log.w(TAG, "Google Search timeout on $searchModel")
                } else {
                    throw ce
                }
            } catch (e: Throwable) {
                val parsed = GeminiErrorHandler.parse(e)
                lastParsedError = parsed
                Log.e(TAG, "Google Search on $searchModel failed: ${parsed.rawMessage ?: e.message}", e)
                if (isFatalApiKeyError(parsed, e)) {
                    return@withContext GeminiRequestResult(
                        success = false,
                        text = "",
                        isSearchError = true,
                        errorMessage = parsed.userFriendlyMessage
                    )
                }
            }
            googleSearchFailed = true
        }

        // 2. Direct generation within remaining time budget
        for ((idx, targetModel) in modelsToTry.withIndex()) {
            val remainingMs = maxTotalBudgetMs - (System.currentTimeMillis() - requestStartMs)
            if (remainingMs < 2_500L) break
            val attemptTimeoutMs = if (idx == 0) minOf(6_500L, remainingMs) else minOf(6_000L, remainingMs)
            try {
                val response = withTimeout(attemptTimeoutMs) {
                    GoogleGeminiDirectClient.generateContent(
                        apiKey = resolvedKey,
                        model = targetModel,
                        contents = directContents,
                        systemInstruction = sysText,
                        enableGoogleSearch = false,
                        temperature = temperature.toFloat(),
                        maxOutputTokens = maxOutputTokens
                    )
                }
                val text = response.text.trim()
                if (text.isNotBlank()) {
                    preferredWorkingChatModel = targetModel
                    return@withContext GeminiRequestResult(
                        success = true,
                        text = text,
                        sources = emptyList(),
                        isSearchError = googleSearchAttempted && googleSearchFailed
                    )
                }
            } catch (ce: CancellationException) {
                if (ce is TimeoutCancellationException) {
                    lastParsedError = GeminiErrorHandler.parse(ce)
                    Log.w(TAG, "Direct Google Gemini timeout on $targetModel")
                } else {
                    throw ce
                }
            } catch (e: Throwable) {
                val parsed = GeminiErrorHandler.parse(e)
                lastParsedError = parsed
                Log.e(TAG, "Direct Google Gemini on $targetModel failed: ${parsed.rawMessage ?: e.message}", e)
                if (isFatalApiKeyError(parsed, e)) {
                    return@withContext GeminiRequestResult(
                        success = false,
                        text = "",
                        isSearchError = googleSearchAttempted && googleSearchFailed,
                        errorMessage = parsed.userFriendlyMessage
                    )
                }
            }
        }

        // 3. Surface real error transparently when all attempts fail
        GeminiRequestResult(
            success = false,
            text = "",
            isSearchError = googleSearchAttempted && googleSearchFailed,
            errorMessage = lastParsedError?.userFriendlyMessage
                ?: "Le service Sam AI n'a pas pu obtenir de réponse. Veuillez réessayer."
        )
    }

    /**
     * Streaming temps réel via l'API directe Google Gemini (SSE) avec repli non-streaming et remontée d'erreur explicite.
     */
    suspend fun executeGeminiRequestStreaming(
        messages: List<ChatMessage>,
        systemInstruction: String,
        enableSearch: Boolean,
        temperature: Double,
        maxOutputTokens: Int,
        preferredModel: String = LITE_GEMINI_MODEL,
        onPartialText: suspend (String) -> Unit
    ): GeminiRequestResult = withContext(Dispatchers.IO) {
        val baseModels = listOfNotNull(
            preferredWorkingChatModel,
            LITE_GEMINI_MODEL,
            DEFAULT_GEMINI_MODEL
        ).distinct()
        val cleanPreferred = preferredModel.trim()
        val allowedNewModels = setOf(DEFAULT_GEMINI_MODEL, REASONING_GEMINI_MODEL, LITE_GEMINI_MODEL)
        val modelsToTry = if (cleanPreferred in allowedNewModels) {
            (listOf(cleanPreferred) + baseModels).distinct()
        } else {
            baseModels
        }

        val nonBlankMessages = messages.filter { it.content.isNotBlank() }
        if (nonBlankMessages.isEmpty()) {
            return@withContext GeminiRequestResult(
                success = false,
                text = "",
                errorMessage = "Le message envoyé est vide."
            )
        }

        val resolvedKey = resolveApiKey()
        val directContents = nonBlankMessages.map { msg ->
            val normalizedRole = if (msg.role.equals("user", ignoreCase = true)) "user" else "model"
            DirectContentInput(
                role = normalizedRole,
                parts = listOf(DirectPartInput(text = msg.content))
            )
        }
        val sysText = systemInstruction.takeIf { it.isNotBlank() }

        data class StreamAttemptResult(
            val text: String,
            val sources: List<WebSource>,
            val error: Throwable?
        )

        suspend fun streamFromModel(
            targetModel: String,
            useGoogleSearch: Boolean,
            firstChunkTimeoutMs: Long,
            totalStreamTimeoutMs: Long
        ): StreamAttemptResult {
            val acc = StringBuilder()
            val collectedSources = mutableListOf<WebSource>()
            val firstTokenSignal = CompletableDeferred<Boolean>()
            val timedOutBeforeFirstToken = AtomicBoolean(false)
            val streamErrorRef = AtomicReference<Throwable?>(null)

            val collectJob = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    GoogleGeminiDirectClient.streamGenerateContent(
                        apiKey = resolvedKey,
                        model = targetModel,
                        contents = directContents,
                        systemInstruction = sysText,
                        enableGoogleSearch = useGoogleSearch,
                        temperature = temperature.toFloat(),
                        maxOutputTokens = maxOutputTokens
                    ) { chunk ->
                        if (timedOutBeforeFirstToken.get()) {
                            throw CancellationException("FIRST_TOKEN_TIMEOUT")
                        }
                        if (chunk.text.isNotEmpty()) {
                            firstTokenSignal.complete(true)
                            acc.append(chunk.text)
                            onPartialText(acc.toString())
                        }
                        for (src in chunk.sources) {
                            if (collectedSources.none { it.url == src.url }) {
                                collectedSources.add(src)
                            }
                        }
                    }
                    firstTokenSignal.complete(acc.isNotEmpty())
                } catch (ce: CancellationException) {
                    if (ce is TimeoutCancellationException) {
                        streamErrorRef.compareAndSet(null, ce)
                    }
                    firstTokenSignal.complete(false)
                } catch (t: Throwable) {
                    Log.e(TAG, "Stream collection error: ${t.message}", t)
                    streamErrorRef.compareAndSet(null, t)
                    firstTokenSignal.complete(false)
                }
            }

            val gotFirst = withTimeoutOrNull(firstChunkTimeoutMs) {
                firstTokenSignal.await()
            }

            if (gotFirst == null) {
                timedOutBeforeFirstToken.set(true)
                collectJob.cancel(CancellationException("FIRST_TOKEN_TIMEOUT"))
                val timeoutErr = streamErrorRef.get()
                    ?: java.net.SocketTimeoutException("Délai d'attente dépassé avant le premier mot (${firstChunkTimeoutMs}ms)")
                return StreamAttemptResult("", emptyList(), timeoutErr)
            }

            if (gotFirst == false) {
                collectJob.cancel()
                return StreamAttemptResult("", emptyList(), streamErrorRef.get())
            }

            val completedInTime = withTimeoutOrNull(totalStreamTimeoutMs) {
                collectJob.join()
                true
            } ?: false
            if (!completedInTime) {
                collectJob.cancel(CancellationException("STREAM_MAX_DURATION"))
            }

            return StreamAttemptResult(acc.toString().trim(), collectedSources, streamErrorRef.get())
        }

        var googleSearchAttempted = false
        var googleSearchFailed = false
        var lastParsedError: ParsedGeminiError? = null
        val streamStartMs = System.currentTimeMillis()
        val totalRequestBudgetMs = if (maxOutputTokens <= 500) 30_000L else 45_000L

        if (enableSearch) {
            googleSearchAttempted = true
            val searchModel = modelsToTry.first()
            val remainingMs = totalRequestBudgetMs - (System.currentTimeMillis() - streamStartMs)
            if (remainingMs >= 3_000L) {
                try {
                    val streamResult = streamFromModel(
                        targetModel = searchModel,
                        useGoogleSearch = true,
                        firstChunkTimeoutMs = minOf(7_500L, remainingMs),
                        totalStreamTimeoutMs = minOf(25_000L, remainingMs)
                    )
                    if (streamResult.text.isNotBlank()) {
                        preferredWorkingChatModel = searchModel
                        return@withContext GeminiRequestResult(
                            success = true,
                            text = streamResult.text,
                            sources = streamResult.sources,
                            isSearchError = false
                        )
                    }
                    if (streamResult.error != null) {
                        val parsed = GeminiErrorHandler.parse(streamResult.error)
                        lastParsedError = parsed
                        Log.e(TAG, "Streaming Google Search failed on $searchModel: ${parsed.rawMessage}", streamResult.error)
                        if (isFatalApiKeyError(parsed, streamResult.error)) {
                            return@withContext GeminiRequestResult(
                                success = false,
                                text = "",
                                isSearchError = true,
                                errorMessage = parsed.userFriendlyMessage
                            )
                        }
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Throwable) {
                    val parsed = GeminiErrorHandler.parse(e)
                    lastParsedError = parsed
                    Log.e(TAG, "Streaming Google Search setup failed on $searchModel: ${parsed.rawMessage ?: e.message}", e)
                    if (isFatalApiKeyError(parsed, e)) {
                        return@withContext GeminiRequestResult(
                            success = false,
                            text = "",
                            isSearchError = true,
                            errorMessage = parsed.userFriendlyMessage
                        )
                    }
                }
            }
            googleSearchFailed = true
        }

        for (targetModel in modelsToTry) {
            val remainingMs = totalRequestBudgetMs - (System.currentTimeMillis() - streamStartMs)
            if (remainingMs < 2_500L) break
            try {
                val streamResult = streamFromModel(
                    targetModel = targetModel,
                    useGoogleSearch = false,
                    firstChunkTimeoutMs = minOf(5_500L, remainingMs),
                    totalStreamTimeoutMs = minOf(25_000L, remainingMs)
                )
                if (streamResult.text.isNotBlank()) {
                    preferredWorkingChatModel = targetModel
                    return@withContext GeminiRequestResult(
                        success = true,
                        text = streamResult.text,
                        sources = streamResult.sources,
                        isSearchError = googleSearchAttempted && googleSearchFailed
                    )
                }
                val streamErr = streamResult.error
                val isStreamTimeoutOrNetwork = streamErr is java.net.SocketTimeoutException ||
                    streamErr is TimeoutCancellationException ||
                    streamErr is java.net.UnknownHostException ||
                    streamErr is java.net.ConnectException
                if (streamErr != null) {
                    val parsed = GeminiErrorHandler.parse(streamErr)
                    lastParsedError = parsed
                    Log.e(TAG, "Streaming Google Gemini failed on $targetModel: ${parsed.rawMessage}", streamErr)
                    if (isFatalApiKeyError(parsed, streamErr)) {
                        return@withContext GeminiRequestResult(
                            success = false,
                            text = "",
                            isSearchError = googleSearchAttempted && googleSearchFailed,
                            errorMessage = parsed.userFriendlyMessage
                        )
                    }
                }

                // Repli unary uniquement si le flux a échoué pour une raison non-timeout/non-réseau et qu'il reste du budget
                val afterStreamRemainingMs = totalRequestBudgetMs - (System.currentTimeMillis() - streamStartMs)
                if (!isStreamTimeoutOrNetwork && afterStreamRemainingMs >= 3_000L) {
                    val unaryResponse = withTimeoutOrNull(minOf(6_500L, afterStreamRemainingMs)) {
                        GoogleGeminiDirectClient.generateContent(
                            apiKey = resolvedKey,
                            model = targetModel,
                            contents = directContents,
                            systemInstruction = sysText,
                            enableGoogleSearch = false,
                            temperature = temperature.toFloat(),
                            maxOutputTokens = maxOutputTokens
                        )
                    }
                    val unaryText = unaryResponse?.text.orEmpty().trim()
                    if (unaryText.isNotBlank()) {
                        preferredWorkingChatModel = targetModel
                        onPartialText(unaryText)
                        return@withContext GeminiRequestResult(
                            success = true,
                            text = unaryText,
                            sources = unaryResponse?.sources.orEmpty(),
                            isSearchError = googleSearchAttempted && googleSearchFailed
                        )
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Throwable) {
                val parsed = GeminiErrorHandler.parse(e)
                lastParsedError = parsed
                Log.e(TAG, "Direct Google Gemini attempt failed on $targetModel: ${parsed.rawMessage ?: e.message}", e)
                if (isFatalApiKeyError(parsed, e)) {
                    return@withContext GeminiRequestResult(
                        success = false,
                        text = "",
                        isSearchError = googleSearchAttempted && googleSearchFailed,
                        errorMessage = parsed.userFriendlyMessage
                    )
                }
            }
        }

        GeminiRequestResult(
            success = false,
            text = "",
            isSearchError = googleSearchAttempted && googleSearchFailed,
            errorMessage = lastParsedError?.userFriendlyMessage
                ?: "Le service Sam AI n'a pas pu répondre. Veuillez vérifier votre connexion et réessayer."
        )
    }
}
