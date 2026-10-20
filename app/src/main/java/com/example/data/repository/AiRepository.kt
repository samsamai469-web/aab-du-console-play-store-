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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

data class ChatMessage(
    val role: String,
    val content: String
)

data class GeminiRequestResult(
    val success: Boolean = true,
    val text: String = "",
    val sources: List<WebSource> = emptyList()
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
            val fallback = "Je suis à votre écoute pour vous répondre avec précision sur tous vos sujets et sur les langues du Burkina Faso."
            chatDao.insertMessage(
                MessageEntity(
                    conversationId = conversationId,
                    text = fallback,
                    sender = "ai",
                    timestamp = System.currentTimeMillis()
                )
            )
            emit(fallback)
            return@flow
        }
        try {
            val model = GenerativeModel(
                modelName = "gemini-3.6-flash",
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
            val err = "Erreur IA : ${e.localizedMessage ?: "Vérifiez votre connexion internet."}"
            chatDao.insertMessage(
                MessageEntity(
                    conversationId = conversationId,
                    text = err,
                    sender = "ai",
                    timestamp = System.currentTimeMillis()
                )
            )
            emit(err)
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

        // 1. Moteur IA principal via Retrofit REST (Direct, ultra-rapide et fiable)
        if (effectiveKey.isNotBlank() && effectiveKey != "MY_GEMINI_API_KEY") {
            val candidateModels = listOf("gemini-3.6-flash", "gemini-3.5-flash")
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
                modelName = "gemini-3.6-flash",
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
                modelName = "gemini-3.6-flash",
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
                modelName = "gemini-3.6-flash",
                apiKey = apiKey
            )
            val fullPrompt = if (systemPrompt.isNotBlank()) "$systemPrompt\n\n$prompt" else prompt
            val response = model.generateContent(fullPrompt)
            response.text?.trim() ?: ""
        } catch (e: Throwable) {
            "Erreur IA : ${e.localizedMessage ?: "Service indisponible"}"
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
            return@withContext GeminiRequestResult(success = false, text = "")
        }

        val modelsToTry = if (preferredModel.isNotBlank()) {
            listOf(preferredModel, "gemini-3.6-flash", "gemini-3.5-flash").distinct()
        } else {
            listOf("gemini-3.6-flash", "gemini-3.5-flash")
        }

        // 1. Appel direct via Retrofit
        for (targetModel in modelsToTry) {
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
                    )
                )
                val resp = com.example.data.remote.RetrofitClient.geminiService.generateContent(
                    model = targetModel,
                    apiKey = effectiveKey,
                    request = req
                )
                val text = resp.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim() ?: ""
                if (text.isNotBlank()) {
                    return@withContext GeminiRequestResult(success = true, text = text)
                }
            } catch (e: Throwable) {
                android.util.Log.w("AiRepository", "Retrofit Gemini ($targetModel) call failed: ${e.message}")
            }
        }

        // 2. Appel de repli via GenerativeModel SDK
        for (targetModel in listOf("gemini-3.6-flash", "gemini-3.5-flash")) {
            try {
                val model = GenerativeModel(
                    modelName = targetModel,
                    apiKey = effectiveKey
                )
                val historyPrompt = messages.joinToString("\n") { "${it.role}: ${it.content}" }
                val fullPrompt = if (systemInstruction.isNotBlank()) "$systemInstruction\n\n$historyPrompt" else historyPrompt
                val response = model.generateContent(fullPrompt)
                val text = response.text?.trim() ?: ""
                if (text.isNotBlank()) {
                    return@withContext GeminiRequestResult(success = true, text = text)
                }
            } catch (e: Throwable) {
                android.util.Log.e("AiRepository", "GenerativeModel Gemini ($targetModel) failed: ${e.message}")
            }
        }

        GeminiRequestResult(success = false, text = "")
    }
}
