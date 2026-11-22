package com.example.data.remote

import com.example.data.local.WebSource
import com.example.data.repository.ChatMessage
import kotlinx.serialization.Serializable

@Serializable
data class OpenAiChatResult(
    val success: Boolean = true,
    val text: String = "",
    val sources: List<WebSource> = emptyList(),
    val model: String = "",
    val searchPerformed: Boolean = false,
    val errorMessage: String? = null,
    val isNetworkError: Boolean = false,
    val httpStatusCode: Int? = null
)

/**
 * Service hérité OpenAI — DÉSACTIVÉ.
 * Toutes les requêtes IA, de traduction, d'actualités et de recherche Web sont désormais
 * propulsées exclusivement par Google Gemini via Firebase AI Logic.
 */
class OpenAiBackendService {

    companion object {
        const val FLAGSHIP_OPENAI_MODEL = "disabled"
        const val WEB_SEARCH_OPENAI_MODEL = "disabled"
        const val FAST_VOICE_OPENAI_MODEL = "disabled"
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun sendChatMessage(
        messages: List<ChatMessage>,
        userQuestion: String,
        enableWebSearch: Boolean = false,
        preferredModel: String = "",
        newsContext: String = "",
        sources: List<WebSource> = emptyList(),
        isMooreVoice: Boolean = false,
        isVoiceCall: Boolean = false
    ): OpenAiChatResult {
        return OpenAiChatResult(
            success = false,
            text = "",
            model = "",
            errorMessage = "OpenAI est désactivé. SAM AI utilise exclusivement les modèles Google Gemini.",
            isNetworkError = false
        )
    }

    @Suppress("UNUSED_PARAMETER")
    fun resolveDeterministicMooreLessonReply(rawQuestion: String, isMooreVoice: Boolean): String? {
        return null
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun translateWithOpenAi(
        text: String,
        sourceLang: String,
        targetLang: String
    ): String {
        return ""
    }
}
