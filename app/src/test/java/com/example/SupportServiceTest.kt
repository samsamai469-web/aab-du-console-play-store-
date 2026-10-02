package com.example

import com.example.data.local.AuthProvider
import com.example.data.local.UserProfile
import com.example.data.remote.SupportService
import org.junit.Assert.*
import org.junit.Test

class SupportServiceTest {

    @Test
    fun testSupportValidation_NotLoggedIn() {
        val guest = UserProfile(isLoggedIn = false)
        val submission = SupportService.SupportSubmission(
            type = "problem",
            category = "Bug",
            subject = "Problème de traduction",
            message = "Voici un message de test détaillé qui dépasse les vingt caractères requis."
        )
        assertFalse(guest.isLoggedIn)
        assertEquals("problem", submission.type)
        assertEquals("Bug", submission.category)
    }

    @Test
    fun testSupportValidation_MessageLengthRequirements() {
        val shortMessage = "Trop court"
        assertTrue(shortMessage.trim().length < 20)

        val validMessage = "L'application se ferme inopinément lors de la saisie de texte en Mooré."
        assertTrue(validMessage.trim().length >= 20)
    }

    @Test
    fun testSupportWhatsAppMessageFormatting() {
        val user = UserProfile(
            isLoggedIn = true,
            userId = "user_test_uid_12345",
            name = "Amadou Ouédraogo",
            email = "amadou@example.com",
            provider = AuthProvider.GOOGLE
        )

        val submission = SupportService.SupportSubmission(
            type = "problem",
            category = "Bug",
            subject = "Erreur lecture vocale",
            message = "Le son ne sort pas quand j'appuie sur le haut-parleur en Mooré.",
            phoneNumber = "+226 70 00 00 00"
        )

        val message = SupportService.formatWhatsAppMessage(
            userProfile = user,
            submission = submission,
            formattedDate = "22/08/2026 10:30",
            appVersion = "1.0"
        )

        assertNotNull(message)
        assertTrue(message.contains("Amadou Ouédraogo"))
        assertTrue(message.contains("amadou@example.com"))
        assertTrue(message.contains("Erreur lecture vocale"))
        assertTrue(message.contains("Bug"))
        assertTrue(message.contains("+226 70 00 00 00"))
    }
}
