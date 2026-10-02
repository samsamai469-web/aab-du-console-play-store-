package com.example

import com.example.data.engine.DictionaryLookupEngine
import com.example.data.engine.LanguageDetector
import com.example.data.engine.MultilingualTranslationEngine
import org.junit.Assert.*
import org.junit.Test

/**
 * Automated tests for Sam Traduction's deterministic translation engine.
 * 
 * Verifies:
 * 1. "Bonjour" -> accurate translation across all supported languages (Mooré, Dioula, Haoussa, Allemand, Anglais).
 * 2. Case insensitivity ("Bonjour", "bonjour", "BONJOUR").
 * 3. Extra/unnecessary whitespaces ("   bonjour   ", "  Bonjour  ").
 * 4. Simple typos ("bonjor", "bonjourr", "bjour", "bonjur", "bonjoure", "sanu", "ngode", "ina kwna").
 * 5. Phrases containing "Bonjour" ("Bonjour, comment vas-tu ?", "Bonjour !").
 * 6. Haoussa specific letters (ɓ, ɗ, ƙ, ƴ) and tolerant transliteration matching (kudi -> Kuɗi, daki -> Ɗaki, kofa -> Ƙofa).
 * 7. Haoussa numbers and cross-language number generation.
 * 8. Language detection including Haoussa.
 */
class TranslationEngineTest {

    // =========================================================================
    // 1. "BONJOUR" -> EXACT TRANSLATIONS IN MULTIPLE LANGUAGES (INCL. HAOUSA)
    // =========================================================================

    @Test
    fun testBonjourToMoore() {
        val result = MultilingualTranslationEngine.tryDirectExpressionMatch("Bonjour", "Français", "Mooré")
        assertNotNull(result)
        assertEquals("Nii yi beogo", result)
    }

    @Test
    fun testBonjourToDioula() {
        val result = MultilingualTranslationEngine.tryDirectExpressionMatch("Bonjour", "Français", "Dioula")
        assertNotNull(result)
        assertEquals("Ani sogoma", result)
    }

    @Test
    fun testBonjourToHausa() {
        val result = MultilingualTranslationEngine.tryDirectExpressionMatch("Bonjour", "Français", "Haoussa")
        assertNotNull(result)
        assertEquals("Ina kwana", result)
    }

    @Test
    fun testBonjourToGerman() {
        val result = MultilingualTranslationEngine.tryDirectExpressionMatch("Bonjour", "Français", "Allemand")
        assertNotNull(result)
        assertEquals("Guten Tag", result)
    }

    @Test
    fun testBonjourToEnglish() {
        val result = MultilingualTranslationEngine.tryDirectExpressionMatch("Bonjour", "Français", "Anglais")
        assertNotNull(result)
        assertEquals("Hello", result)
    }

    @Test
    fun testReverseBonjourFromHausa() {
        val result = MultilingualTranslationEngine.tryDirectExpressionMatch("Ina kwana", "Haoussa", "Français")
        assertNotNull(result)
        assertEquals("Bonjour", result)
    }

    @Test
    fun testReverseBonjourFromMoore() {
        val result = MultilingualTranslationEngine.tryDirectExpressionMatch("Nii yi beogo", "Mooré", "Français")
        assertNotNull(result)
        assertEquals("Bonjour", result)
    }

    @Test
    fun testReverseBonjourFromDioula() {
        val result = MultilingualTranslationEngine.tryDirectExpressionMatch("Ani sogoma", "Dioula", "Français")
        assertNotNull(result)
        assertEquals("Bonjour", result)
    }

    @Test
    fun testReverseBonjourFromEnglish() {
        val result = MultilingualTranslationEngine.tryDirectExpressionMatch("Hello", "Anglais", "Français")
        assertNotNull(result)
        assertEquals("Bonjour", result)
    }

    @Test
    fun testDirectCrossMooreToHausa() {
        val result = MultilingualTranslationEngine.tryDirectExpressionMatch("Nii yi beogo", "Mooré", "Haoussa")
        assertNotNull(result)
        assertEquals("Ina kwana", result)
    }

    @Test
    fun testDirectCrossHausaToDioula() {
        val result = MultilingualTranslationEngine.tryDirectExpressionMatch("Ina kwana", "Haoussa", "Dioula")
        assertNotNull(result)
        assertEquals("Ani sogoma", result)
    }

    // =========================================================================
    // 2. CASE SENSITIVITY (UPPERCASE, LOWERCASE, MIXED)
    // =========================================================================

    @Test
    fun testBonjourLowercase() {
        val result = MultilingualTranslationEngine.tryDirectExpressionMatch("bonjour", "Français", "Mooré")
        assertEquals("Nii yi beogo", result)
    }

    @Test
    fun testBonjourUppercase() {
        val result = MultilingualTranslationEngine.tryDirectExpressionMatch("BONJOUR", "Français", "Haoussa")
        assertEquals("Ina kwana", result)
    }

    @Test
    fun testBonjourMixedCase() {
        val result = MultilingualTranslationEngine.tryDirectExpressionMatch("bOnJoUr", "Français", "Dioula")
        assertEquals("Ani sogoma", result)
    }

    // =========================================================================
    // 3. UNNECESSARY & EXTRA WHITESPACES
    // =========================================================================

    @Test
    fun testBonjourWithLeadingAndTrailingSpaces() {
        val result = MultilingualTranslationEngine.tryDirectExpressionMatch("   bonjour   ", "Français", "Haoussa")
        assertEquals("Ina kwana", result)
    }

    @Test
    fun testBonjourWithTabsAndNewlines() {
        val result = MultilingualTranslationEngine.tryDirectExpressionMatch("\t\nBonjour \n\t", "Français", "Allemand")
        assertEquals("Guten Tag", result)
    }

    // =========================================================================
    // 4. SIMPLE TYPOS RECOGNITION (e.g. "bonjor", "bonjourr", "bjour", "ina kwna", "ngode")
    // =========================================================================

    @Test
    fun testBonjourTypo_bonjor() {
        val result = MultilingualTranslationEngine.tryDirectExpressionMatch("bonjor", "Français", "Haoussa")
        assertEquals("Ina kwana", result)
    }

    @Test
    fun testBonjourTypo_bonjourr() {
        val result = MultilingualTranslationEngine.tryDirectExpressionMatch("bonjourr", "Français", "Dioula")
        assertEquals("Ani sogoma", result)
    }

    @Test
    fun testBonjourTypo_bjour() {
        val result = MultilingualTranslationEngine.tryDirectExpressionMatch("bjour", "Français", "Anglais")
        assertEquals("Hello", result)
    }

    @Test
    fun testBonjourTypo_bonjur() {
        val result = MultilingualTranslationEngine.tryDirectExpressionMatch("bonjur", "Français", "Allemand")
        assertEquals("Guten Tag", result)
    }

    @Test
    fun testBonjourTypo_bonjoure() {
        val result = MultilingualTranslationEngine.tryDirectExpressionMatch("bonjoure", "Français", "Mooré")
        assertEquals("Nii yi beogo", result)
    }

    @Test
    fun testHausaTypo_ina_kwna() {
        val result = MultilingualTranslationEngine.tryDirectExpressionMatch("ina kwna", "Haoussa", "Français")
        assertEquals("Bonjour", result)
    }

    @Test
    fun testHausaTypo_ngode() {
        val result = MultilingualTranslationEngine.tryDirectExpressionMatch("ngode", "Haoussa", "Français")
        assertEquals("Merci", result)
    }

    // =========================================================================
    // 5. PHRASES CONTAINING "BONJOUR" & COMPOUND GREETINGS
    // =========================================================================

    @Test
    fun testBonjourWithExclamation_Hausa() {
        val result = MultilingualTranslationEngine.tryDirectExpressionMatch("Bonjour !", "Français", "Haoussa")
        assertNotNull(result)
        assertTrue(result!!.startsWith("Ina kwana"))
    }

    @Test
    fun testBonjourCommentVasTu_Hausa() {
        val result = MultilingualTranslationEngine.tryDirectExpressionMatch("Bonjour, comment vas-tu ?", "Français", "Haoussa")
        assertNotNull(result)
        assertTrue(result!!.startsWith("Ina kwana"))
    }

    @Test
    fun testBonjourCommentVasTu_Moore() {
        val result = MultilingualTranslationEngine.tryDirectExpressionMatch("Bonjour comment vas-tu ?", "Français", "Mooré")
        assertNotNull(result)
        assertEquals("Nii yi beogo, Fo laafi ?", result)
    }

    @Test
    fun testBonjourCommentVasTu_Dioula() {
        val result = MultilingualTranslationEngine.tryDirectExpressionMatch("Bonjour, comment vas-tu ?", "Français", "Dioula")
        assertNotNull(result)
        assertEquals("Ani sogoma, I kakene wa ?", result)
    }

    @Test
    fun testBonjourCommentVasTu_German() {
        val result = MultilingualTranslationEngine.tryDirectExpressionMatch("Bonjour, comment vas-tu ?", "Français", "Allemand")
        assertNotNull(result)
        assertEquals("Guten Tag, Wie geht es dir?", result)
    }

    @Test
    fun testBonjourCommentVasTu_English() {
        val result = MultilingualTranslationEngine.tryDirectExpressionMatch("Bonjour, comment vas-tu ?", "Français", "Anglais")
        assertNotNull(result)
        assertEquals("Hello, How are you?", result)
    }

    // =========================================================================
    // 6. HAOUSA SPECIAL CHARACTERS (ɓ, ɗ, ƙ, ƴ) & TRANSLITERATION
    // =========================================================================

    @Test
    fun testHausaSpecialCharacters_Kudi() {
        val withHook = MultilingualTranslationEngine.tryDirectExpressionMatch("Kuɗi", "Haoussa", "Français")
        val withoutHook = MultilingualTranslationEngine.tryDirectExpressionMatch("kudi", "Haoussa", "Français")
        assertEquals("Argent", withHook)
        assertEquals("Argent", withoutHook)
    }

    @Test
    fun testHausaSpecialCharacters_Daki() {
        val withHook = MultilingualTranslationEngine.tryDirectExpressionMatch("Ɗaki", "Haoussa", "Français")
        val withoutHook = MultilingualTranslationEngine.tryDirectExpressionMatch("daki", "Haoussa", "Français")
        assertEquals("Chambre", withHook)
        assertEquals("Chambre", withoutHook)
    }

    @Test
    fun testHausaSpecialCharacters_Kofa() {
        val withHook = MultilingualTranslationEngine.tryDirectExpressionMatch("Ƙofa", "Haoussa", "Français")
        val withoutHook = MultilingualTranslationEngine.tryDirectExpressionMatch("kofa", "Haoussa", "Français")
        assertEquals("Porte", withHook)
        assertEquals("Porte", withoutHook)
    }

    @Test
    fun testHausaSpecialCharacters_DanUwa_YarUwa() {
        val brother = MultilingualTranslationEngine.tryDirectExpressionMatch("dan uwa", "Haoussa", "Français")
        val sister = MultilingualTranslationEngine.tryDirectExpressionMatch("yar uwa", "Haoussa", "Français")
        assertEquals("Frère", brother)
        assertEquals("Sœur", sister)
    }

    @Test
    fun testFrenchToHausa_WithSpecialCharsOutput() {
        val money = MultilingualTranslationEngine.tryDirectExpressionMatch("Argent", "Français", "Haoussa")
        val room = MultilingualTranslationEngine.tryDirectExpressionMatch("Chambre", "Français", "Haoussa")
        val door = MultilingualTranslationEngine.tryDirectExpressionMatch("Porte", "Français", "Haoussa")
        val brother = MultilingualTranslationEngine.tryDirectExpressionMatch("Frère", "Français", "Haoussa")
        val sister = MultilingualTranslationEngine.tryDirectExpressionMatch("Sœur", "Français", "Haoussa")

        assertEquals("Kuɗi", money)
        assertEquals("Ɗaki", room)
        assertEquals("Ƙofa", door)
        assertEquals("Ɗan'uwa", brother)
        assertEquals("Ƴar'uwa", sister)
    }

    // =========================================================================
    // 7. HAOUSA NUMBERS ENGINE
    // =========================================================================

    @Test
    fun testHausaNumberFormatting() {
        assertEquals("Ɗaya", MultilingualTranslationEngine.formatNumberInTargetLanguage(1L, "Haoussa"))
        assertEquals("Biyu", MultilingualTranslationEngine.formatNumberInTargetLanguage(2L, "Haoussa"))
        assertEquals("Goma", MultilingualTranslationEngine.formatNumberInTargetLanguage(10L, "Haoussa"))
        assertEquals("Goma sha biyu", MultilingualTranslationEngine.formatNumberInTargetLanguage(12L, "Haoussa"))
        assertEquals("Ashirin", MultilingualTranslationEngine.formatNumberInTargetLanguage(20L, "Haoussa"))
        assertEquals("Ashirin da biyar", MultilingualTranslationEngine.formatNumberInTargetLanguage(25L, "Haoussa"))
        assertEquals("Ɗari", MultilingualTranslationEngine.formatNumberInTargetLanguage(100L, "Haoussa"))
        assertEquals("Dubu", MultilingualTranslationEngine.formatNumberInTargetLanguage(1000L, "Haoussa"))
    }

    @Test
    fun testHausaNumberParsing() {
        assertEquals(1L, MultilingualTranslationEngine.parseNumberFromAnyLanguage("daya"))
        assertEquals(1L, MultilingualTranslationEngine.parseNumberFromAnyLanguage("ɗaya"))
        assertEquals(5L, MultilingualTranslationEngine.parseNumberFromAnyLanguage("biyar"))
        assertEquals(15L, MultilingualTranslationEngine.parseNumberFromAnyLanguage("goma sha biyar"))
        assertEquals(25L, MultilingualTranslationEngine.parseNumberFromAnyLanguage("ashirin da biyar"))
        assertEquals(100L, MultilingualTranslationEngine.parseNumberFromAnyLanguage("dari"))
        assertEquals(1000L, MultilingualTranslationEngine.parseNumberFromAnyLanguage("dubu"))
    }

    // =========================================================================
    // 8. LANGUAGE DETECTOR (INCL. HAOUSA)
    // =========================================================================

    @Test
    fun testHausaLanguageDetection() {
        assertEquals("Haoussa", LanguageDetector.detect("ina kwana"))
        assertEquals("Haoussa", LanguageDetector.detect("Nagode sosai"))
        assertEquals("Haoussa", LanguageDetector.detect("Kuɗi"))
        assertEquals("Haoussa", LanguageDetector.detect("Ɗaki mai kyau"))
        assertEquals("Haoussa", LanguageDetector.detect("Ƙofa"))
    }

    // =========================================================================
    // 9. OTHER KEY DICTIONARY WORDS
    // =========================================================================

    @Test
    fun testMerciAndTypoMercii() {
        val merciMoore = MultilingualTranslationEngine.tryDirectExpressionMatch("merci", "Français", "Mooré")
        val typoMoore = MultilingualTranslationEngine.tryDirectExpressionMatch("mercii", "Français", "Mooré")
        val merciHausa = MultilingualTranslationEngine.tryDirectExpressionMatch("merci", "Français", "Haoussa")
        assertEquals("Bark wusgo", merciMoore)
        assertEquals("Bark wusgo", typoMoore)
        assertEquals("Nagode", merciHausa)
    }

    @Test
    fun testAuRevoirAndTypoAurevoir() {
        val exactMoore = MultilingualTranslationEngine.tryDirectExpressionMatch("Au revoir", "Français", "Mooré")
        val typoMoore = MultilingualTranslationEngine.tryDirectExpressionMatch("aurevoir", "Français", "Mooré")
        val exactHausa = MultilingualTranslationEngine.tryDirectExpressionMatch("Au revoir", "Français", "Haoussa")
        assertEquals("Bilmãse", exactMoore)
        assertEquals("Bilmãse", typoMoore)
        assertEquals("Sai an jima", exactHausa)
    }

    @Test
    fun testBonsoirAndTypoBonsoirr() {
        val exactDioula = MultilingualTranslationEngine.tryDirectExpressionMatch("Bonsoir", "Français", "Dioula")
        val typoDioula = MultilingualTranslationEngine.tryDirectExpressionMatch("bonsoirr", "Français", "Dioula")
        val exactHausa = MultilingualTranslationEngine.tryDirectExpressionMatch("Bonsoir", "Français", "Haoussa")
        assertEquals("Ani houla", exactDioula)
        assertEquals("Ani houla", typoDioula)
        assertEquals("Ina wuni", exactHausa)
    }

    @Test
    fun testSilVousPlaitAndTypo() {
        val exact = MultilingualTranslationEngine.tryDirectExpressionMatch("S'il vous plaît", "Français", "Dioula")
        val typo = MultilingualTranslationEngine.tryDirectExpressionMatch("sil vous plait", "Français", "Dioula")
        val exactHausa = MultilingualTranslationEngine.tryDirectExpressionMatch("S'il vous plaît", "Français", "Haoussa")
        assertEquals("Sabali", exact)
        assertEquals("Sabali", typo)
        assertEquals("Don Allah", exactHausa)
    }
}
