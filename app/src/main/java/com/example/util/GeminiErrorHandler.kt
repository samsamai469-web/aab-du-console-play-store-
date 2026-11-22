package com.example.util

import com.example.data.remote.GeminiErrorResponse
import com.google.firebase.ai.type.FirebaseAIException
import com.google.firebase.ai.type.InvalidAPIKeyException
import com.google.firebase.ai.type.PromptBlockedException
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.RequestTimeoutException
import com.google.firebase.ai.type.ResponseStoppedException
import com.google.firebase.ai.type.SerializationException
import com.google.firebase.ai.type.ServerException
import com.google.firebase.ai.type.ServiceDisabledException
import com.google.firebase.ai.type.UnsupportedUserLocationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

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
        val causeChain = generateSequence(throwable) { it.cause }.take(10).toList()

        var httpCode: Int? = null
        var rawBody: String? = null
        var retryAfterSec: Long? = null

        val httpEx = causeChain.filterIsInstance<HttpException>().firstOrNull()
        if (httpEx != null) {
            httpCode = httpEx.code()
            rawBody = try {
                httpEx.response()?.errorBody()?.string()
            } catch (_: Throwable) {
                null
            }
            val retryHeader = httpEx.response()?.headers()?.get("Retry-After")
            retryAfterSec = retryHeader?.toLongOrNull()
        }

        val allMessages = causeChain.mapNotNull { it.message?.takeIf { msg -> msg.isNotBlank() } }
        val fullThrowableMessage = allMessages.joinToString(" | ")
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

        val combinedText = buildString {
            append(throwable::class.java.simpleName)
            append(" ")
            append(fullThrowableMessage)
            append(" ")
            append(serverMessage.orEmpty())
            append(" ")
            append(status.orEmpty())
        }.lowercase()

        val inferredHttpCode = httpCode ?: serverCode ?: inferHttpCodeFromText(combinedText)
        val firebaseEx = causeChain.filterIsInstance<FirebaseAIException>().firstOrNull()

        val isAppCheckError = combinedText.contains("app check") ||
            combinedText.contains("appcheck") ||
            combinedText.contains("firebaseappcheck") ||
            combinedText.contains("attestation") ||
            combinedText.contains("app_check") ||
            (inferredHttpCode == 403 && !combinedText.contains("api_key"))

        val isServiceDisabled = firebaseEx is ServiceDisabledException ||
            combinedText.contains("servicedisabled") ||
            combinedText.contains("has not been used in project") ||
            combinedText.contains("api is disabled")

        val isQuotaOrBilling = firebaseEx is QuotaExceededException ||
            inferredHttpCode == 429 ||
            status.equals("RESOURCE_EXHAUSTED", ignoreCase = true) ||
            combinedText.contains("429") ||
            combinedText.contains("resource_exhausted") ||
            combinedText.contains("quota") ||
            combinedText.contains("rate limit") ||
            combinedText.contains("billing")

        val isTemporaryOverload = inferredHttpCode == 503 ||
            inferredHttpCode == 500 ||
            status.equals("UNAVAILABLE", ignoreCase = true) ||
            combinedText.contains("503") ||
            combinedText.contains("high demand") ||
            combinedText.contains("spikes in demand") ||
            combinedText.contains("temporarily unavailable") ||
            combinedText.contains("overloaded") ||
            combinedText.contains("grpcerror")

        val isModelNotFound = inferredHttpCode == 404 ||
            status.equals("NOT_FOUND", ignoreCase = true) ||
            combinedText.contains("not found for api version") ||
            combinedText.contains("is not supported for generatecontent")

        val isAuthError = firebaseEx is InvalidAPIKeyException ||
            isAppCheckError ||
            ((inferredHttpCode in listOf(401, 403)) && (
                combinedText.contains("api_key") ||
                    combinedText.contains("api key") ||
                    combinedText.contains("unauthenticated") ||
                    combinedText.contains("permission_denied") ||
                    combinedText.contains("permission denied")
                ))

        val isBlockedContent = firebaseEx is PromptBlockedException ||
            firebaseEx is ResponseStoppedException

        val isUnsupportedLocation = firebaseEx is UnsupportedUserLocationException ||
            combinedText.contains("user location is not supported")

        val isTimeoutException = firebaseEx is RequestTimeoutException ||
            causeChain.any { it is SocketTimeoutException || it is kotlinx.coroutines.TimeoutCancellationException } ||
            (inferredHttpCode == null && (combinedText.contains("timeout") || combinedText.contains("timed out")))

        val isConnectivityException = inferredHttpCode == null && !isTimeoutException && (
            causeChain.any { it is UnknownHostException || it is ConnectException || it is NoRouteToHostException } ||
                combinedText.contains("unable to resolve host") ||
                combinedText.contains("failed to connect") ||
                combinedText.contains("network is unreachable") ||
                (causeChain.any { it is IOException } && !com.example.data.local.ApiKeyManager.hasActiveInternetConnection())
            )

        val conciseDetail = (serverMessage ?: throwable.localizedMessage ?: throwable::class.java.simpleName)
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(180)

        val friendlyMessage = when {
            isAppCheckError ->
                "Vérification App Check Firebase refusée (403). Assurez-vous que l'application est lancée avec un jeton App Check valide ou reCAPTCHA configuré. ($conciseDetail)"

            isServiceDisabled ->
                "Le service Firebase AI Logic n'est pas encore activé sur ce projet Firebase. ($conciseDetail)"

            isUnsupportedLocation ->
                "Ce modèle Gemini n'est pas encore disponible dans votre région."

            isBlockedContent ->
                "La requête ou la réponse a été bloquée par le filtre de sécurité du modèle. Reformulez votre message."

            isModelNotFound ->
                "Le modèle Google demandé n'est pas disponible sur ce point d'accès ($conciseDetail)."

            isTemporaryOverload ->
                "Les serveurs Google IA sont momentanément très sollicités (Code ${inferredHttpCode ?: 503}). Veuillez réessayer dans quelques secondes."

            isQuotaOrBilling ->
                "Quota ou limite de requêtes Google IA temporairement atteint (Code ${inferredHttpCode ?: 429}). Patientez un instant ou vérifiez votre quota Firebase."

            isAuthError ->
                "Autorisation Firebase AI refusée (Code ${inferredHttpCode ?: 403}) : $conciseDetail"

            inferredHttpCode != null ->
                "Erreur du service Google IA (Code $inferredHttpCode) : $conciseDetail"

            isTimeoutException ->
                "Le serveur Google IA met plus de temps que prévu à répondre. Veuillez réessayer."

            isConnectivityException ->
                if (com.example.data.local.ApiKeyManager.hasActiveInternetConnection()) {
                    "Le service Google IA est momentanément inaccessible ($conciseDetail). Veuillez réessayer dans un instant."
                } else {
                    "Connexion au service d'IA impossible. Veuillez vérifier votre réseau Internet et réessayer."
                }

            firebaseEx is ServerException || firebaseEx is SerializationException || firebaseEx != null ->
                "Erreur Firebase AI (${firebaseEx::class.java.simpleName}) : $conciseDetail"

            else ->
                "Erreur du service IA (${throwable::class.java.simpleName}) : $conciseDetail"
        }

        return ParsedGeminiError(
            httpCode = httpCode ?: inferredHttpCode,
            serverCode = serverCode,
            status = status,
            rawMessage = serverMessage ?: throwable.localizedMessage ?: throwable::class.java.simpleName,
            retryAfterSeconds = retryAfterSec,
            isTemporaryOverload = isTemporaryOverload,
            isRateLimited = isQuotaOrBilling,
            isAuthError = isAuthError,
            userFriendlyMessage = friendlyMessage
        )
    }

    private fun inferHttpCodeFromText(text: String): Int? {
        val codePatterns = listOf(403, 429, 503, 404, 401, 400, 500)
        for (code in codePatterns) {
            if (Regex("\\b$code\\b").containsMatchIn(text)) {
                return code
            }
        }
        return null
    }

    private fun extractJsonBlock(msg: String): String? {
        val start = msg.indexOf('{')
        val end = msg.lastIndexOf('}')
        return if (start != -1 && end != -1 && end > start) {
            msg.substring(start, end + 1)
        } else null
    }
}
