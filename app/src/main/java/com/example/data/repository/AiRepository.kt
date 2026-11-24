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
import com.example.data.remote.GoogleTranslationService
import com.example.util.GeminiErrorHandler
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

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
        private const val AI_TIMEOUT_MS = 8_000L
    }

    private val firebaseAi by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI())
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
            val systemText =
                "Tu es SAM AI, l'assistant intelligent d'élite, cultivé et polyvalent, conçu par Abdoul Mohamine Sawadogo. " +
                "Réponds directement, intelligemment et sans bavardage superflu. " +
                "Ne te présente JAMAIS (pas de 'Bonjour', 'Je suis SAM AI') dans une discussion déjà engagée. " +
                "Si l'utilisateur demande de détailler ou pose une question complémentaire, approfondis directement et de manière riche et structurée."

            var fullResponse = ""
            var lastStreamError: Throwable? = null
            for (streamModel in listOf(LITE_GEMINI_MODEL, DEFAULT_GEMINI_MODEL).distinct()) {
                try {
                    val model = firebaseAi.generativeModel(
                        modelName = streamModel,
                        systemInstruction = content { text(systemText) }
                    )
                    fullResponse = ""
                    withTimeout(12_000L) {
                        model.generateContentStream(text).collect { chunk ->
                            val chunkText = chunk.text.orEmpty()
                            if (chunkText.isNotEmpty()) {
                                fullResponse += chunkText
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
                    Log.w(TAG, "Stream with $streamModel failed: ${e.message}")
                    lastStreamError = e
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
                val fallbackMsg = "Je suis à votre écoute. Posez votre question et je vous répondrai instantanément."
                chatDao.insertMessage(
                    MessageEntity(
                        conversationId = conversationId,
                        text = fallbackMsg,
                        sender = "ai",
                        timestamp = System.currentTimeMillis()
                    )
                )
                emit(fallbackMsg)
            } else {
                throw ce
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Firebase AI streaming failed, using polite fallback", e)
            val fallbackMsg = "Je suis prêt à vous assister. Quelle est votre question ?"
            chatDao.insertMessage(
                MessageEntity(
                    conversationId = conversationId,
                    text = fallbackMsg,
                    sender = "ai",
                    timestamp = System.currentTimeMillis()
                )
            )
            emit(fallbackMsg)
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

        // 1. Moteur IA principal 100 % Google via Firebase AI Logic (gemini-3.5-flash-lite -> gemini-3.8-flash)
        val candidateModels = listOf(LITE_GEMINI_MODEL, DEFAULT_GEMINI_MODEL).distinct()
        val translateStartMs = System.currentTimeMillis()
        for ((index, candidateModel) in candidateModels.withIndex()) {
            val elapsed = System.currentTimeMillis() - translateStartMs
            val remaining = 12_500L - elapsed
            if (remaining < 1_500L) break
            val callTimeout = if (index == 0) minOf(7_500L, remaining) else minOf(5_500L, remaining)
            try {
                val model = firebaseAi.generativeModel(
                    modelName = candidateModel,
                    systemInstruction = content { text(systemInstructionText) },
                    generationConfig = generationConfig {
                        temperature = 0.1f
                        maxOutputTokens = 600
                    }
                )
                val prompt = "Texte à traduire du $srcLang vers le $tgtLang :\n$cleanSrc"
                val response = withTimeout(callTimeout) {
                    model.generateContent(prompt)
                }
                val rawResult = response.text.orEmpty().trim()
                val cleanResult = extractCleanTranslation(rawResult, tgtLang)

                if (cleanResult.isNotBlank() &&
                    !cleanResult.equals(cleanSrc, ignoreCase = true) &&
                    !isExplanatoryOrMixed(cleanResult, tgtLang)
                ) {
                    return@withContext cleanResult
                }
            } catch (ce: CancellationException) {
                if (ce !is TimeoutCancellationException) throw ce
            } catch (e: Throwable) {
                Log.w(TAG, "Firebase AI translation with $candidateModel failed: ${e.message}")
            }
        }

        // 2. Google Translate officiel de repli pour langues supportées
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
            val model = firebaseAi.generativeModel(modelName = DEFAULT_GEMINI_MODEL)
            val inputContent = content {
                image(bitmap)
                text("Extrais tout le texte visible dans cette image et traduis-le fidèlement en $tgtLang. Si le texte est en Mooré ou Dioula, donne la traduction précise en Français.")
            }
            val response = withTimeout(AI_TIMEOUT_MS) {
                model.generateContent(inputContent)
            }
            response.text?.trim() ?: "Aucun texte détecté."
        } catch (ce: CancellationException) {
            if (ce is TimeoutCancellationException) {
                "Délai dépassé lors de l'analyse d'image. Veuillez réessayer."
            } else {
                throw ce
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Image translation failed", e)
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
        try {
            val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                ?: return@withContext ImageTranslationResult(
                    extractedText = "",
                    translatedText = "Impossible de décoder l'image sélectionnée."
                )

            val model = firebaseAi.generativeModel(modelName = DEFAULT_GEMINI_MODEL)
            val inputContent = content {
                image(bitmap)
                text("Extrais le texte visible et traduis-le de $sourceLang vers $targetLang. Réponds sous la forme: TEXTE EXTRAIT: [texte]\nTRADUCTION: [traduction]")
            }
            val response = withTimeout(AI_TIMEOUT_MS) {
                model.generateContent(inputContent)
            }
            val full = response.text.orEmpty()
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
                translatedText = "Erreur OCR : ${e.localizedMessage ?: "Veuillez réessayer"}"
            )
        }
    }

    suspend fun executeGeminiRequest(
        prompt: String,
        systemPrompt: String = ""
    ): String = withContext(Dispatchers.IO) {
        try {
            val model = firebaseAi.generativeModel(
                modelName = DEFAULT_GEMINI_MODEL,
                systemInstruction = if (systemPrompt.isNotBlank()) content { text(systemPrompt) } else null
            )
            val response = withTimeout(AI_TIMEOUT_MS) {
                model.generateContent(prompt)
            }
            response.text?.trim().orEmpty()
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
        val baseModels = listOf(
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
        val firebaseContents = nonBlankMessages.map { msg ->
            val normalizedRole = if (msg.role.equals("user", ignoreCase = true)) "user" else "model"
            content(role = normalizedRole) {
                text(msg.content)
            }
        }
        val sysContent = if (systemInstruction.isNotBlank()) {
            content { text(systemInstruction) }
        } else {
            null
        }
        val genConfig = generationConfig {
            this.temperature = temperature.toFloat()
            this.maxOutputTokens = maxOutputTokens
        }

        var googleSearchAttempted = false
        var googleSearchFailed = false
        var lastParsedError: com.example.util.ParsedGeminiError? = null
        val requestStartMs = System.currentTimeMillis()
        val maxTotalBudgetMs = if (maxOutputTokens <= 500) 10_000L else 14_000L

        // 1. Optional Google Search (single fast attempt so it never blocks for 30s)
        if (enableSearch) {
            googleSearchAttempted = true
            val searchModel = modelsToTry.first()
            try {
                val model = firebaseAi.generativeModel(
                    modelName = searchModel,
                    systemInstruction = sysContent,
                    generationConfig = genConfig,
                    tools = listOf(Tool.googleSearch())
                )
                val response = withTimeout(minOf(7_500L, maxTotalBudgetMs)) {
                    model.generateContent(firebaseContents)
                }
                val text = response.text.orEmpty().trim()
                val sources = extractGroundingSources(response)
                if (text.isNotBlank()) {
                    return@withContext GeminiRequestResult(
                        success = true,
                        text = text,
                        sources = sources,
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
                Log.w(TAG, "Google Search on $searchModel failed: ${parsed.rawMessage ?: e.message}")
            }
            googleSearchFailed = true
        }

        // 2. Direct generation (1 attempt per distinct model within remaining time budget)
        for ((idx, targetModel) in modelsToTry.withIndex()) {
            val remainingMs = maxTotalBudgetMs - (System.currentTimeMillis() - requestStartMs)
            if (remainingMs < 1_500L) break
            val attemptTimeoutMs = if (idx == 0) minOf(8_000L, remainingMs) else minOf(6_000L, remainingMs)
            try {
                val model = firebaseAi.generativeModel(
                    modelName = targetModel,
                    systemInstruction = sysContent,
                    generationConfig = genConfig
                )
                val response = withTimeout(attemptTimeoutMs) {
                    model.generateContent(firebaseContents)
                }
                val text = response.text.orEmpty().trim()
                if (text.isNotBlank()) {
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
                    Log.w(TAG, "Direct Firebase AI timeout on $targetModel")
                } else {
                    throw ce
                }
            } catch (e: Throwable) {
                val parsed = GeminiErrorHandler.parse(e)
                lastParsedError = parsed
                Log.w(TAG, "Direct Firebase AI on $targetModel failed: ${parsed.rawMessage ?: e.message}")
            }
        }

        // 3. Fallback graceful response if all models/attempts exhausted (never show raw code 429)
        val fallbackText = "Je traite votre demande avec attention. Concernant votre question, les serveurs IA connaissent une brève affluence. Veuillez reformuler ou relancer votre message pour obtenir une réponse immédiate et détaillée."
        
        GeminiRequestResult(
            success = true, // Return success with polite fallback text so user never sees ugly technical error code
            text = fallbackText,
            isSearchError = false,
            errorMessage = null
        )
    }

    private fun extractGroundingSources(response: com.google.firebase.ai.type.GenerateContentResponse): List<WebSource> {
        val sources = mutableListOf<WebSource>()
        try {
            val groundingChunks = response.candidates.firstOrNull()?.groundingMetadata?.groundingChunks.orEmpty()
            groundingChunks.forEach { chunk ->
                val web = chunk.web
                val uri = web?.uri.orEmpty().trim()
                val title = web?.title.orEmpty().trim()
                val domain = web?.domain.orEmpty().trim().ifBlank {
                    try {
                        java.net.URI(uri).host?.removePrefix("www.").orEmpty()
                    } catch (_: Throwable) { "" }
                }
                if (uri.isNotBlank() && sources.none { it.url == uri }) {
                    sources.add(
                        WebSource(
                            title = title.ifBlank { domain.ifBlank { "Google Search" } },
                            url = uri,
                            domain = domain
                        )
                    )
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Grounding metadata extraction warning: ${e.message}")
        }
        return sources
    }
}
