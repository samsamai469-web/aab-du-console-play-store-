package com.example

import com.example.data.local.NewsArticle
import com.example.data.repository.NewsRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NewsRepositoryComprehensiveTest {

    @Test
    fun testDefaultNewsArticlesContainsBothRtbAndAibSources() {
        val articles = NewsRepository.getDefaultBurkinaNews()
        assertNotNull(articles)
        assertTrue("Articles list must not be empty", articles.isNotEmpty())

        val rtbArticles = articles.filter { it.source.contains("RTB", ignoreCase = true) }
        val aibArticles = articles.filter { it.source.contains("AIB", ignoreCase = true) }

        assertTrue("Must contain RTB articles", rtbArticles.isNotEmpty())
        assertTrue("Must contain AIB articles", aibArticles.isNotEmpty())
    }

    @Test
    fun testAllDefaultNewsArticlesHaveValidData() {
        val articles = NewsRepository.getDefaultBurkinaNews()
        for (article in articles) {
            assertTrue("Title must not be blank", article.title.isNotBlank())
            assertTrue("Summary must not be blank", article.summary.isNotBlank())
            assertTrue("Source must not be blank", article.source.isNotBlank())
            assertTrue("Category must not be blank", article.category.isNotBlank())
            assertTrue("Date string must not be blank", article.date.isNotBlank())
            assertTrue("Image URL must start with http or https", article.imageUrl.startsWith("http"))
            assertTrue("Timestamp must be recent", article.timestamp > 0)
        }
    }

    @Test
    fun testVideoArticlesSeparatedFromTextArticles() {
        val articles = NewsRepository.getDefaultBurkinaNews()
        val videoArticles = articles.filter { it.isVideo }
        val textArticles = articles.filter { !it.isVideo }

        assertTrue("Must have text articles", textArticles.isNotEmpty())
        assertTrue("Must have at least one video item (e.g. JT)", videoArticles.isNotEmpty())

        for (item in textArticles) {
            assertFalse("Text articles must not be flagged as video", item.isVideo)
        }
    }

    @Test
    fun testFallbackCoverImageThemed() {
        val rtbCover = NewsRepository.getDefaultCoverImageFor("Burkina Faso", "RTB", "Actualité")
        val aibCover = NewsRepository.getDefaultCoverImageFor("Économie", "AIB", "Coton Faso Dan Fani")
        val sportsCover = NewsRepository.getDefaultCoverImageFor("Sport", "RTB", "Les Étalons au tournoi")
        val cultureCover = NewsRepository.getDefaultCoverImageFor("Culture & Arts", "AIB", "FESPACO")

        assertTrue("RTB cover must be valid URL", rtbCover.startsWith("https://images.unsplash.com"))
        assertTrue("AIB cover must be valid URL", aibCover.startsWith("https://images.unsplash.com"))
        assertTrue("Sports cover must be valid URL", sportsCover.startsWith("https://images.unsplash.com"))
        assertTrue("Culture cover must be valid URL", cultureCover.startsWith("https://images.unsplash.com"))
    }
}
