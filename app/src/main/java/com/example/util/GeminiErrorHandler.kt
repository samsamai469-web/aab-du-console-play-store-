package com.example.util

import com.example.data.remote.GeminiErrorResponse
import kotlinx.serialization.json.Json
import retrofit2.HttpException

data class ParsedGeminiError(
    val httpCode: Int? = null,
    val serverCode: Int? = null,
    val status: String? = null,
    val rawMessage: String? = null,
    val retryAfterSeconds: Long? = null,
    val isTemporaryOverload: Boolean = false,
    val isRateLimited: Boolean = false,
    val isAuthError: Boolean = false,
    val userFriendlyMessage: String
)

object GeminiErrorHandler {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    fun parse(throwable: Throwable): ParsedGeminiError {
        var httpCode: Int? = null
        var rawBody: String? = null
        var retryAfterSec: Long? = null

        if (throwable is HttpException) {
            httpCode = throwable.code()
            rawBody = try {
                throwable.response()?.errorBody()?.string()
            } catch (_: Throwable) {
                null
            }
            val retryHeader = throwable.response()?.headers()?.get("Retry-After")
            retryAfterSec = retryHeader?.toLongOrNull()
        }

        val fullThrowableMessage = throwable.message.orEmpty()
        val errorJsonToParse = rawBody ?: extractJsonBlock(fullThrowableMessage)

        var serverCode: Int? = null
        var status: String? = null
        var serverMessage: String? = null

        if (!errorJsonToParse.isNullOrBlank()) {
            try {
                val parsed = json.decodeFromString<GeminiErrorResponse>(errorJsonToParse)
                serverCode = parsed.error?.code
                status = parsed.error?.status
                serverMessage = parsed.error?.message
            } catch (_: Throwable) {
                // Ignore decoding issues
            }
        }

        val effectiveCode = httpCode ?: serverCode
        val combinedText = buildString {
            append(fullThrowableMessage)
            append(" ")
            append(serverMessage.orEmpty())
            append(" ")
            append(status.orEmpty())
        }.lowercase()

        val isTemporaryOverload = effectiveCode == 503 ||
                status.equals("UNAVAILABLE", ignoreCase = true) ||
                combinedText.contains("503") ||
                combinedText.contains("high demand") ||
                combinedText.contains("spikes in demand") ||
                combinedText.contains("temporarily unavailable") ||
                combinedText.contains("grpcerror") ||
                combinedText.contains("missingfieldexception")

        val isRateLimited = effectiveCode == 429 ||
                status.equals("RESOURCE_EXHAUSTED", ignoreCase = true) ||
                combinedText.contains("429") ||
                combinedText.contains("resource_exhausted") ||
                combinedText.contains("quota exceeded") ||
                combinedText.contains("rate limit")

        val isAuthError = (effectiveCode in listOf(400, 401, 403)) && (
                combinedText.contains("api_key") ||
                        combinedText.contains("api key") ||
                        combinedText.contains("unauthenticated") ||
                        combinedText.contains("permission_denied") ||
                        combinedText.contains("invalid api key")
                )

        val friendlyMessage = when {
            isTemporaryOverload ->
                "Le modèle d'intelligence artificielle est actuellement très sollicité (erreur 503). Les pics de charge sont généralement temporaires. Veuillez patienter quelques instants et réessayer votre question."

            isRateLimited ->
                "Limite de requêtes temporairement atteinte (erreur 429). Veuillez patienter quelques secondes avant de relancer votre demande."

            isAuthError ->
                "Clé API Gemini invalide ou non autorisée. Veuillez vérifier votre clé API dans les Paramètres de l'application."

            effectiveCode != null ->
                "Service d'intelligence artificielle momentanément indisponible (Code $effectiveCode). Veuillez vérifier votre connexion Internet et réessayer."

            else ->
                "Connexion au service d'IA impossible. Veuillez vérifier votre réseau Internet et réessayer."
        }

        return ParsedGeminiError(
            httpCode = httpCode,
            serverCode = serverCode,
            status = status,
            rawMessage = serverMessage ?: throwable.localizedMessage,
            retryAfterSeconds = retryAfterSec,
            isTemporaryOverload = isTemporaryOverload,
            isRateLimited = isRateLimited,
            isAuthError = isAuthError,
            userFriendlyMessage = friendlyMessage
        )
    }

    private fun extractJsonBlock(msg: String): String? {
        val start = msg.indexOf('{')
        val end = msg.lastIndexOf('}')
        return if (start != -1 && end != -1 && end > start) {
            msg.substring(start, end + 1)
        } else null
    }
}
