package com.example

import com.example.data.repository.ChatMessage
import com.example.util.SearchNeedLevel
import com.example.util.WebSearchClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSearchClassifierTest {

    @Test
    fun testSimpleQuestion_NoSearchNeeded() {
        val decision = WebSearchClassifier.classify("Qu'est-ce qu'un ordinateur ?")
        assertEquals(SearchNeedLevel.NONE, decision.level)
        assertFalse(decision.isSearchNeeded)
    }

    @Test
    fun testTranslation_NoSearchNeeded() {
        val decision = WebSearchClassifier.classify("Traduis bonjour en mooré.")
        assertEquals(SearchNeedLevel.NONE, decision.level)
        assertFalse(decision.isSearchNeeded)
    }

    @Test
    fun testSportsMatch_SearchNeededWithStrictTemporal() {
        val decision = WebSearchClassifier.classify("Qui a remporté le match d'hier ?")
        assertEquals(SearchNeedLevel.TEMPORAL_LIVE, decision.level)
        assertTrue(decision.isSearchNeeded)
        assertTrue(decision.isStrictTemporal)
        assertTrue(decision.isNewsTopic)
    }

    @Test
    fun testLivePrice_SearchNeededWithStrictTemporal() {
        val decision = WebSearchClassifier.classify("Quel est le prix actuel de cet appareil ?")
        assertEquals(SearchNeedLevel.TEMPORAL_LIVE, decision.level)
        assertTrue(decision.isSearchNeeded)
        assertTrue(decision.isStrictTemporal)
    }

    @Test
    fun testRecentNews_SearchNeededWithNewsTopic() {
        val decision = WebSearchClassifier.classify("Quelles sont les dernières nouvelles du Burkina Faso ?")
        assertEquals(SearchNeedLevel.TEMPORAL_LIVE, decision.level)
        assertTrue(decision.isSearchNeeded)
        assertTrue(decision.isNewsTopic)
    }

    @Test
    fun testFactCheckCreation_SearchNeededNonStrictTemporal() {
        val decision = WebSearchClassifier.classify("Qui a créé Orange Money ?")
        assertEquals(SearchNeedLevel.FACT_CHECK, decision.level)
        assertTrue(decision.isSearchNeeded)
        assertFalse(decision.isStrictTemporal) // Cas 3 : Gemini peut répondre si le web échoue
    }

    @Test
    fun testCodingQuestion_NoSearchNeeded() {
        val decision = WebSearchClassifier.classify("Écris une fonction en Python pour inverser une chaîne")
        assertEquals(SearchNeedLevel.NONE, decision.level)
        assertFalse(decision.isSearchNeeded)
    }

    @Test
    fun testMathQuestion_NoSearchNeeded() {
        val decision = WebSearchClassifier.classify("Calcule 125 * 34")
        assertEquals(SearchNeedLevel.NONE, decision.level)
        assertFalse(decision.isSearchNeeded)
    }

    @Test
    fun testGreeting_NoSearchNeeded() {
        val decision = WebSearchClassifier.classify("Bonjour !")
        assertEquals(SearchNeedLevel.NONE, decision.level)
        assertFalse(decision.isSearchNeeded)
    }

    @Test
    fun testFollowUpToSearch_InheritsSearchNeed() {
        val history = listOf(
            ChatMessage(role = "user", content = "Quel est le score du match du PSG ?"),
            ChatMessage(role = "model", content = "Le PSG a gagné 2-0 hier soir selon https://lequipe.fr")
        )
        val decision = WebSearchClassifier.classify("Et pour le Real ?", history)
        assertTrue(decision.isSearchNeeded)
    }
}
