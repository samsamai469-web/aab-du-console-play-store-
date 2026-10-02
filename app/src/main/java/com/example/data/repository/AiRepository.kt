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

        val apiKey = apiKeyManager.getActiveGeminiApiKey()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            val fallback = "Veuillez configurer une clé API Gemini dans les Paramètres pour activer l'intelligence artificielle en direct."
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
                modelName = "gemini-2.5-flash",
                apiKey = apiKey
            )

            val systemInstruction = "Tu es SAM AI, l'assistant intelligent expert en langues (Mooré, Dioula, Français, Haoussa) et culture du Burkina Faso. Réponds de façon concise, précise, respectueuse et pédagogique."
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
        val apiKey = apiKeyManager.getActiveGeminiApiKey()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext ""
        }
        try {
            val model = GenerativeModel(
                modelName = "gemini-2.5-flash",
                apiKey = apiKey
            )
            val prompt = """
                Tu es le traducteur expert SAM AI spécialisé en langues d'Afrique de l'Ouest (Mooré, Dioula, Français).
                Traduis fidèlement le texte suivant de $srcLang vers $tgtLang.
                Contexte lexical local : $localContext
                Texte à traduire : "$srcText"
                Donne UNIQUEMENT la traduction directe exacte sans commentaires superflus.
            """.trimIndent()

            val response = model.generateContent(prompt)
            response.text?.trim() ?: ""
        } catch (_: Throwable) {
            ""
        }
    }

    suspend fun translateImage(
        bitmap: Bitmap,
        srcLang: String,
        tgtLang: String
    ): String = withContext(Dispatchers.IO) {
        val apiKey = apiKeyManager.getActiveGeminiApiKey()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "Veuillez configurer une clé API Gemini valide dans les Paramètres."
        }
        try {
            val model = GenerativeModel(
                modelName = "gemini-2.5-flash",
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
                translatedText = "Veuillez configurer une clé API Gemini valide dans les Paramètres pour utiliser la traduction d'image."
            )
        }
        try {
            val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)

            val model = GenerativeModel(
                modelName = "gemini-2.5-flash",
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
            return@withContext "Veuillez configurer une clé API Gemini dans les Paramètres pour activer les réponses de l'IA."
        }
        try {
            val model = GenerativeModel(
                modelName = "gemini-2.5-flash",
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
        val key = apiKey.ifBlank { apiKeyManager.getActiveGeminiApiKey() }
        if (key.isBlank() || key == "MY_GEMINI_API_KEY") {
            return@withContext GeminiRequestResult(
                success = false,
                text = "Veuillez configurer une clé API Gemini valide dans les Paramètres pour activer les réponses de l'IA."
            )
        }
        try {
            val model = GenerativeModel(
                modelName = "gemini-2.5-flash",
                apiKey = key
            )
            val historyPrompt = messages.joinToString("\n") { "${it.role}: ${it.content}" }
            val fullPrompt = "$systemInstruction\n\n$historyPrompt"
            val response = model.generateContent(fullPrompt)
            val text = response.text?.trim() ?: ""
            GeminiRequestResult(success = text.isNotBlank(), text = text)
        } catch (e: Throwable) {
            GeminiRequestResult(success = false, text = "Erreur IA : ${e.localizedMessage ?: "Vérifiez votre connexion"}")
        }
    }
}
