package com.example

import com.example.util.ConversationCleaner
import com.example.util.TtsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TtsAndErrorSanitizationTest {

    @Test
    fun ttsManager_stripsVoiceAndPhoneticsMetaDirections() {
        val cases = listOf(
            "Voix masculine claire et phonétiques : Bonjour, comment vas-tu ?" to "Bonjour, comment vas-tu ?",
            "[voix masculine claire] Nii yi beogo" to "Nii yi beogo",
            "(voix masculine, naturelle) Merci beaucoup." to "Merci beaucoup.",
            "Phonétiques : barka wousgô" to "barka wousgô",
            "Lis cette phonétique en mooré : lafi bala" to "lafi bala",
            "Voix d'homme claire, phonétiques : Je m'appelle Sam AI." to "Je m'appelle Sam AI."
        )

        for ((input, expected) in cases) {
            val cleaned = TtsManager.cleanSpeechText(input)
            assertFalse("Meta-text leaked for input '$input': '$cleaned'", cleaned.lowercase().contains("voix masculine"))
            assertFalse("Phonetic label leaked for input '$input': '$cleaned'", cleaned.lowercase().contains("phonétique"))
            assertEquals(expected, cleaned)
        }
    }

    @Test
    fun conversationCleaner_cleansTranscriptsAndTranslationsWithoutLeakingMetaText() {
        val cleanedTranslation = ConversationCleaner.polishTranslatedText("« Nii yi beogo »")
        assertEquals("Nii yi beogo", cleanedTranslation)

        val cleanedTranscript = ConversationCleaner.cleanVoiceTranscript("  bonjour comment   ça va  ", "Français")
        assertEquals("Bonjour comment ça va", cleanedTranscript)
    }
}
