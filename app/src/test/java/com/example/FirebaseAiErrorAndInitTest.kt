package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ApiKeyManager
import com.example.data.local.AppDatabase
import com.example.data.remote.FirebaseAuthService
import com.example.data.remote.GeminiApiHttpException
import com.example.data.remote.MissingGeminiApiKeyException
import com.example.data.repository.AiRepository
import com.example.data.repository.ChatMessage
import com.example.util.GeminiErrorHandler
import com.example.util.SearchNeedLevel
import com.example.util.WebSearchClassifier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.SocketTimeoutException
import java.net.UnknownHostException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirebaseAiErrorAndInitTest {

    @Test
    fun explicitFirebaseOptions_matchComAnvilSamaiAppId() {
        val options = FirebaseAuthService.getExplicitFirebaseOptions()
        assertEquals("sam-traduction", options.projectId)
        assertEquals("1:438563104660:android:ef789e28e3246141a45181", options.applicationId)
    }

    @Test
    fun apiKeyManager_savesAndResolvesCustomGeminiApiKey() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = ApiKeyManager(context)
        manager.clearGeminiApiKey()

        manager.saveGeminiApiKey("AIzaTestKey1234567890")
        assertEquals("AIzaTestKey1234567890", manager.getActiveGeminiApiKey())
        assertTrue(manager.isGeminiConfigured())
        assertEquals("AIzaTestKey1234567890", ApiKeyManager.resolveGeminiApiKey(context))

        manager.clearGeminiApiKey()
    }

    @Test
    fun geminiErrorHandler_surfacesDirectGoogleApiErrorsClearly() {
        val missingKeyErr = GeminiErrorHandler.parse(MissingGeminiApiKeyException())
        assertTrue(missingKeyErr.isAuthError)
        assertTrue(missingKeyErr.userFriendlyMessage.contains("GEMINI_API_KEY"))

        val invalidKeyErr = GeminiErrorHandler.parse(
            GeminiApiHttpException(
                httpCode = 403,
                apiStatus = "PERMISSION_DENIED",
                serverMessage = "API key not valid. Please pass a valid API key.",
                rawBody = null
            )
        )
        assertTrue(invalidKeyErr.isAuthError)
        assertEquals(403, invalidKeyErr.httpCode)
        assertTrue(invalidKeyErr.userFriendlyMessage.contains("Clé API Google Gemini"))

        val quotaErr = GeminiErrorHandler.parse(
            GeminiApiHttpException(
                httpCode = 429,
                apiStatus = "RESOURCE_EXHAUSTED",
                serverMessage = "Quota exceeded for quota metric",
                rawBody = null
            )
        )
        assertTrue(quotaErr.isRateLimited)
        assertEquals(429, quotaErr.httpCode)
        assertTrue(quotaErr.userFriendlyMessage.contains("Quota") || quotaErr.userFriendlyMessage.contains("429"))

        val notFoundErr = GeminiErrorHandler.parse(
            GeminiApiHttpException(
                httpCode = 404,
                apiStatus = "NOT_FOUND",
                serverMessage = "models/gemini-test is not found for API version v1beta",
                rawBody = null
            )
        )
        assertEquals(404, notFoundErr.httpCode)
        assertTrue(notFoundErr.userFriendlyMessage.contains("n'est pas disponible"))

        val overloadErr = GeminiErrorHandler.parse(
            GeminiApiHttpException(
                httpCode = 503,
                apiStatus = "UNAVAILABLE",
                serverMessage = "The model is overloaded. Please try again later.",
                rawBody = null
            )
        )
        assertTrue(overloadErr.isTemporaryOverload)
        assertEquals(503, overloadErr.httpCode)

        val timeoutErr = GeminiErrorHandler.parse(SocketTimeoutException("Read timed out"))
        assertTrue(timeoutErr.userFriendlyMessage.contains("temps"))

        val hostErr = GeminiErrorHandler.parse(UnknownHostException("generativelanguage.googleapis.com"))
        assertTrue(
            hostErr.userFriendlyMessage.contains("inaccessible") ||
                hostErr.userFriendlyMessage.contains("réseau")
        )
    }

    @Test
    fun aiRepository_rejectsEmptyMessagesAndUsesLatestModels() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = AiRepository(
            chatDao = db.chatDao(),
            linguisticDao = db.linguisticDao(),
            apiKeyManager = ApiKeyManager(context),
            auditDao = db.auditDao()
        )

        assertEquals("gemini-3.8-flash", AiRepository.DEFAULT_GEMINI_MODEL)
        assertEquals("gemini-4-argon", AiRepository.REASONING_GEMINI_MODEL)
        assertEquals("gemini-3.5-flash-lite", AiRepository.LITE_GEMINI_MODEL)

        val result = repo.executeGeminiRequest(
            messages = listOf(ChatMessage(role = "user", content = "   ")),
            systemInstruction = "Test",
            enableSearch = false,
            temperature = 0.2,
            maxOutputTokens = 100
        )
        assertFalse(result.success)
        assertTrue(result.errorMessage?.contains("vide") == true)

        db.close()
    }

    @Test
    fun webSearchClassifier_treatsBonjourAsGreetingWithoutWebSearch() {
        val decision = WebSearchClassifier.classify("Bonjour")
        assertEquals(SearchNeedLevel.NONE, decision.level)
        assertFalse(decision.isSearchNeeded)
    }
}
