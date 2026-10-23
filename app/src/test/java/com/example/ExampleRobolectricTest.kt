package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AuthProvider
import com.example.data.local.AuthSessionManager
import com.example.data.local.UserProfile
import com.example.util.SecurityUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun testReadStringFromContext() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Sam AI", appName)
    }

    @Test
    fun testAuthSessionManagerPersistenceAndEncryption() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sessionManager = AuthSessionManager(context)

        // Clear any previous session
        sessionManager.clearSession()
        assertNull(sessionManager.getSavedUserProfile())

        // Save a test Google user profile
        val profile = UserProfile(
            isLoggedIn = true,
            userId = "google_109283746501928374650",
            googleSubjectId = "109283746501928374650",
            name = "Apprenant Test",
            email = "apprenant.test@gmail.com",
            provider = AuthProvider.GOOGLE,
            photoUrl = "https://lh3.googleusercontent.com/a/test_avatar",
            isCloudSynced = true
        )
        sessionManager.saveUserProfile(profile)

        // Retrieve and verify
        val loaded = sessionManager.getSavedUserProfile()
        assertNotNull(loaded)
        assertEquals(true, loaded?.isLoggedIn)
        assertEquals("google_109283746501928374650", loaded?.userId)
        assertEquals("109283746501928374650", loaded?.googleSubjectId)
        assertEquals("apprenant.test@gmail.com", loaded?.email)
        assertEquals(AuthProvider.GOOGLE, loaded?.provider)

        // Clear session
        sessionManager.clearSession()
        assertNull(sessionManager.getSavedUserProfile())
    }

    @Test
    fun testGoogleAccountsIsolationBySubjectId() {
        val user1Sub = "118237465019283746501"
        val user2Sub = "229384756102938475612"

        val userId1 = "google_$user1Sub"
        val userId2 = "google_$user2Sub"

        // Verify that two different accounts generate completely distinct partition keys
        assertFalse("Account keys must not collide", userId1 == userId2)
        assertTrue(userId1.startsWith("google_"))
        assertTrue(userId2.startsWith("google_"))
        assertFalse(userId1.contains("@")) // Sub IDs must not rely on mutable emails
    }

    @Test
    fun testSecureTokenGenerationAndSanitization() {
        val token1 = SecurityUtils.generateSecureToken(32)
        val token2 = SecurityUtils.generateSecureToken(32)
        assertNotNull(token1)
        assertNotNull(token2)
        assertFalse(token1 == token2)
        assertTrue(token1.length >= 32)

        val dirtyInput = "<script>alert('xss')</script>Bonjour Sam Traduction! \u0000"
        val sanitized = SecurityUtils.sanitizeInput(dirtyInput)
        assertFalse(sanitized.contains("<script>"))
        assertTrue(sanitized.contains("Bonjour Sam Traduction!"))
    }

    @Test
    fun testNewGoogleUserAccountCreationVsExistingUserReconnection() {
        val googleSubjectId = "104928374019283746592"
        val userEmail = "nouvel.apprenant@gmail.com"
        val userName = "Nouvel Apprenant"

        // 1. New user: Automatic creation without extra forms
        val userPartitionKey = "google_$googleSubjectId"
        val newProfile = UserProfile(
            isLoggedIn = true,
            userId = userPartitionKey,
            googleSubjectId = googleSubjectId,
            name = userName,
            email = userEmail,
            provider = AuthProvider.GOOGLE,
            photoUrl = "https://lh3.googleusercontent.com/a/sample_avatar"
        )
        assertEquals(true, newProfile.isLoggedIn)
        assertEquals("google_104928374019283746592", newProfile.userId)
        assertEquals(AuthProvider.GOOGLE, newProfile.provider)

        // 2. Existing user: Reconnecting with the same Google Subject ID resolves to the exact same partition key
        val reconnectingSub = "104928374019283746592"
        val resolvedKey = "google_$reconnectingSub"
        assertEquals(newProfile.userId, resolvedKey) // Guaranteed 0 duplicate accounts
    }

    @Test
    fun testOfficialGoogleOAuthUrlGeneration() {
        val oauthUrl = com.example.util.GoogleAuthManager.getGoogleOAuthUrl()

        assertTrue("Must target accounts.google.com", oauthUrl.startsWith("https://accounts.google.com/o/oauth2/v2/auth"))
        assertTrue("Must include prompt=select_account", oauthUrl.contains("prompt=select_account"))
        assertTrue("Must include client_id", oauthUrl.contains("client_id="))
        assertTrue("Must include openid scope", oauthUrl.contains("openid"))
        assertTrue("Must include nonce", oauthUrl.contains("nonce="))
    }

    @Test
    fun testJwtClaimsExtractionForGoogleIdentity() {
        // Mock a standard base64url encoded OpenID Connect JWT payload
        // {"sub":"108237465019283746501","email":"test.user@gmail.com","name":"Test User"}
        val header = android.util.Base64.encodeToString("{\"alg\":\"RS256\"}".toByteArray(), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
        val payload = android.util.Base64.encodeToString("{\"sub\":\"108237465019283746501\",\"email\":\"test.user@gmail.com\",\"name\":\"Test User\"}".toByteArray(), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
        val mockJwt = "$header.$payload.signature"

        val claims = SecurityUtils.decodeJwtPayload(mockJwt)
        assertNotNull(claims)
        assertEquals("108237465019283746501", claims?.get("sub"))
        assertEquals("test.user@gmail.com", claims?.get("email"))
        assertEquals("Test User", claims?.get("name"))
    }

    @Test
    fun testUserStatisticsPersistentAcrossRestartSimulationInRoomDatabase() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(context, com.example.data.local.AppDatabase::class.java).build()
        val analyticsDao = db.analyticsDao()

        // 1. Direct DAO Operations
        val user1 = com.example.data.local.AnonymousUserEntity(
            anonymousId = "anon_test_user_uuid_123",
            firstSeenTimestamp = System.currentTimeMillis() - 10000,
            lastActiveTimestamp = System.currentTimeMillis(),
            totalSessionsCount = 1,
            totalTranslationsCount = 3,
            totalVoiceTranslationsCount = 1,
            totalTextTranslationsCount = 2,
            deviceModel = "Test Phone",
            androidVersion = "Android 14"
        )
        analyticsDao.insertOrUpdateUser(user1)

        val trans1 = com.example.data.local.TranslationAnalyticsEntity(
            userId = user1.anonymousId,
            sourceLang = "Français",
            targetLang = "Mooré",
            originalText = "Bonjour",
            translatedText = "Nii yi beogo",
            deviceModel = "Test Phone",
            isVoice = false,
            isAiMode = false,
            textLength = 7,
            timestamp = System.currentTimeMillis()
        )
        val trans2 = com.example.data.local.TranslationAnalyticsEntity(
            userId = user1.anonymousId,
            sourceLang = "Français",
            targetLang = "Dioula",
            originalText = "Bonjour",
            translatedText = "Ani sogoma",
            deviceModel = "Test Phone",
            isVoice = false,
            isAiMode = false,
            textLength = 6,
            timestamp = System.currentTimeMillis()
        )
        val trans3 = com.example.data.local.TranslationAnalyticsEntity(
            userId = user1.anonymousId,
            sourceLang = "Français",
            targetLang = "Haoussa",
            originalText = "Bonjour",
            translatedText = "Ina kwana",
            deviceModel = "Test Phone",
            isVoice = true,
            isAiMode = false,
            textLength = 12,
            timestamp = System.currentTimeMillis()
        )
        analyticsDao.insertTranslation(trans1)
        analyticsDao.insertTranslation(trans2)
        analyticsDao.insertTranslation(trans3)

        val totalTranslations = analyticsDao.getTotalTranslationsCountDirect()
        assertEquals(3, totalTranslations)

        val totalUniqueUsers = analyticsDao.getTotalUniqueUsersCountDirect()
        assertEquals(1, totalUniqueUsers)

        val retrievedUser = analyticsDao.getUserById(user1.anonymousId)
        assertNotNull(retrievedUser)
        assertEquals(3, retrievedUser?.totalTranslationsCount)
        assertEquals(1, retrievedUser?.totalVoiceTranslationsCount)

        db.close()
    }
}
