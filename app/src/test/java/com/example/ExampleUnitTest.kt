package com.example

import com.example.data.local.AuthProvider
import com.example.data.local.UserProfile
import com.example.util.GoogleAuthManager
import com.example.util.SecurityUtils
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testGoogleAccountsListNotEmpty() {
        assertTrue(GoogleAuthManager.SAMPLE_GOOGLE_ACCOUNTS.isNotEmpty())
        val defaultAccount = GoogleAuthManager.SAMPLE_GOOGLE_ACCOUNTS.first()
        assertTrue(defaultAccount.email.contains("@gmail.com"))
        assertNotNull(defaultAccount.displayName)
    }

    @Test
    fun testGoogleUserProfileCreation() {
        val email = "samsamai469@gmail.com"
        val name = "Sam Traduction"
        val subId = SecurityUtils.hashPassword(email).take(16)
        val profile = UserProfile(
            isLoggedIn = true,
            userId = "google_$subId",
            googleSubjectId = subId,
            name = name,
            email = email,
            provider = AuthProvider.GOOGLE,
            photoUrl = "https://lh3.googleusercontent.com/a/test=s200"
        )

        assertTrue(profile.isLoggedIn)
        assertEquals(AuthProvider.GOOGLE, profile.provider)
        assertEquals("Google", profile.provider.displayName)
        assertEquals(email, profile.email)
        assertEquals(name, profile.name)
    }
}
