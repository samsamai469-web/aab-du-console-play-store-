package com.example

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ApiKeyManager
import com.example.data.local.AppDatabase
import com.example.data.remote.FirebaseAuthService
import com.example.data.repository.AiRepository
import com.example.data.repository.ChatMessage
import com.example.util.GeminiErrorHandler
import com.example.util.SearchNeedLevel
import com.example.util.WebSearchClassifier
import com.example.util.configureAppCheck
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.RequestTimeoutException
import com.google.firebase.ai.type.ServerException
import com.google.firebase.ai.type.ServiceDisabledException
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

    private fun newServerException(message: String): ServerException {
        val ctor = ServerException::class.java.getDeclaredConstructor(String::class.java, Throwable::class.java)
        ctor.isAccessible = true
        return ctor.newInstance(message, null)
    }

    private fun newQuotaExceededException(message: String): QuotaExceededException {
        val ctor = QuotaExceededException::class.java.getDeclaredConstructor(String::class.java, Throwable::class.java)
        ctor.isAccessible = true
        return ctor.newInstance(message, null)
    }

    private fun newServiceDisabledException(message: String): ServiceDisabledException {
        val ctor = ServiceDisabledException::class.java.getDeclaredConstructor(String::class.java, Throwable::class.java)
        ctor.isAccessible = true
        return ctor.newInstance(message, null)
    }

    private fun newRequestTimeoutException(message: String, cause: Throwable?): RequestTimeoutException {
        val ctor = RequestTimeoutException::class.java.getDeclaredConstructor(
            String::class.java,
            Throwable::class.java,
            List::class.java
        )
        ctor.isAccessible = true
        return ctor.newInstance(message, cause, emptyList<Content>())
    }

    @Test
    fun configureAppCheck_persistsDebugTokenSynchronouslyBeforeProviderFactory() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val expectedToken = "test-debug-token-1234-5678"
        val intent = Intent().apply {
            putExtra("FIREBASE_APPCHECK_DEBUG_TOKEN", expectedToken)
        }

        configureAppCheck(context, intent)

        val persistenceKey = FirebaseApp.getInstance().persistenceKey
        val prefs = context.getSharedPreferences(
            "com.google.firebase.appcheck.debug.store.$persistenceKey",
            Context.MODE_PRIVATE
        )
        assertEquals(
            expectedToken,
            prefs.getString("com.google.firebase.appcheck.debug.DEBUG_SECRET", null)
        )
    }

    @Test
    fun explicitFirebaseOptions_matchComAnvilSamaiAppId() {
        val options = FirebaseAuthService.getExplicitFirebaseOptions()
        assertEquals("sam-traduction", options.projectId)
        assertEquals("1:438563104660:android:ef789e28e3246141a45181", options.applicationId)
    }

    @Test
    fun geminiErrorHandler_surfacesSpecificFirebaseAiErrorsClearly() {
        val appCheckErr = GeminiErrorHandler.parse(
            newServerException("403 FirebaseAppCheck token is invalid: App attestation failed")
        )
        assertTrue(appCheckErr.isAuthError)
        assertTrue(appCheckErr.userFriendlyMessage.contains("App Check"))

        val quotaErr = GeminiErrorHandler.parse(
            newQuotaExceededException("Quota exceeded for quota metric")
        )
        assertTrue(quotaErr.isRateLimited)
        assertTrue(quotaErr.userFriendlyMessage.contains("Quota") || quotaErr.userFriendlyMessage.contains("429"))

        val disabledErr = GeminiErrorHandler.parse(
            newServiceDisabledException("Firebase AI Logic API has not been used in project sam-traduction")
        )
        assertTrue(disabledErr.userFriendlyMessage.contains("Firebase AI Logic"))

        val notFoundErr = GeminiErrorHandler.parse(
            newServerException("404 models/gemini-test is not found for API version v1beta")
        )
        assertEquals(404, notFoundErr.httpCode)
        assertTrue(notFoundErr.userFriendlyMessage.contains("n'est pas disponible"))

        val overloadErr = GeminiErrorHandler.parse(
            newServerException("503 The model is overloaded. Please try again later.")
        )
        assertTrue(overloadErr.isTemporaryOverload)

        val timeoutErr = GeminiErrorHandler.parse(
            newRequestTimeoutException("Request timed out", SocketTimeoutException("Read timed out"))
        )
        assertTrue(timeoutErr.userFriendlyMessage.contains("temps"))

        val genericServerErr = GeminiErrorHandler.parse(
            newServerException("Unexpected server payload rejection")
        )
        assertTrue(genericServerErr.userFriendlyMessage.contains("Unexpected server payload rejection"))
        assertFalse(genericServerErr.userFriendlyMessage.contains("erreur temporaire"))

        val hostErr = GeminiErrorHandler.parse(UnknownHostException("firebasevertexai.googleapis.com"))
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
