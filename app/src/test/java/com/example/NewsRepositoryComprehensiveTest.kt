package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.NewsRepository
import org.junit.Assert.assertEquals
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
    fun testNewsCacheInitializationAndRetentionWindow() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        NewsRepository.initContext(context)
        val cached = NewsRepository.getCachedFreshNews()
        assertNotNull(cached)
        assertEquals(24L * 60L * 60L * 1000L, NewsRepository.RETENTION_WINDOW_MS)
    }

    @Test
    fun testExtractVideoInfoForYouTubeAndTextArticles() {
        val ytInfo = NewsRepository.extractVideoInfo(
            content = "<iframe src=\"https://www.youtube.com/embed/dQw4w9WgXcQ\"></iframe>",
            link = "https://www.rtb.bf/jt-20h",
            title = "JT de 20h RTB"
        )
        assertTrue(ytInfo.isVideo)
        assertEquals("dQw4w9WgXcQ", ytInfo.youtubeId)
        assertTrue(ytInfo.videoUrl.contains("dQw4w9WgXcQ"))

        val textInfo = NewsRepository.extractVideoInfo(
            content = "<p>Article d'actualité AIB sans vidéo.</p>",
            link = "https://www.aib.media/article-test",
            title = "Burkina Faso : actualité économique"
        )
        assertFalse(textInfo.isVideo)
        assertEquals("", textInfo.youtubeId)
    }
}
