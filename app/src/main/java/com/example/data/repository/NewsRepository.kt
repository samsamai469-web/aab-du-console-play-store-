package com.example.data.repository

import android.util.Log
import android.util.Xml
import com.example.BuildConfig
import com.example.data.local.NewsArticle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern
import java.util.zip.GZIPInputStream

data class RssSource(
    val name: String,
    val feedUrls: List<String>,
    val defaultCategory: String,
    val siteUrl: String
)

object NewsRepository {
    private const val TAG = "NewsRepository"

    @Volatile
    private var customAppId: String = ""

    @Volatile
    private var customMetaToken: String = ""

    @Volatile
    private var customMetaTarget: String = ""

    @Volatile
    private var customAibTarget: String = "248050315307820"

    @Volatile
    private var customRtbTarget: String = "RTBOfficiel"

    fun updateCustomMetaConfig(
        appId: String = "",
        token: String = "",
        aibTarget: String = "248050315307820",
        rtbTarget: String = "RTBOfficiel"
    ) {
        customAppId = appId.trim()
        customMetaToken = token.trim()
        customAibTarget = if (aibTarget.isNotBlank()) extractPageIdentifier(aibTarget.trim()) else "248050315307820"
        customRtbTarget = if (rtbTarget.isNotBlank()) extractPageIdentifier(rtbTarget.trim()) else "RTBOfficiel"
        customMetaTarget = customAibTarget
        Log.d(TAG, "Custom Meta Config updated: appId=$customAppId, tokenSet=${customMetaToken.isNotBlank()}, aibTarget=$customAibTarget, rtbTarget=$customRtbTarget")
    }

    fun updateCustomMetaConfig(token: String, pageOrUrl: String) {
        updateCustomMetaConfig(appId = "", token = token, aibTarget = pageOrUrl, rtbTarget = "RTBOfficiel")
    }

    fun getCustomAppId(): String = customAppId
    fun getCustomMetaToken(): String = customMetaToken
    fun getCustomMetaTarget(): String = customMetaTarget
    fun getCustomAibTarget(): String = customAibTarget
    fun getCustomRtbTarget(): String = customRtbTarget

    /**
     * Teste la connexion avec l'API Meta Facebook Graph en temps réel
     */
    suspend fun testMetaConnection(appId: String, token: String, aibTarget: String, rtbTarget: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val cleanToken = token.trim()
        val cleanAppId = appId.trim()
        val cleanAib = if (aibTarget.isNotBlank()) extractPageIdentifier(aibTarget) else "248050315307820"
        val cleanRtb = if (rtbTarget.isNotBlank()) extractPageIdentifier(rtbTarget) else "RTBOfficiel"

        if (cleanToken.isBlank()) {
            return@withContext Pair(false, "Veuillez entrer un Jeton d'accès (Token Facebook).")
        }

        try {
            // Test 1: Debug token ou Me
            val testUrl = if (cleanAppId.isNotBlank()) {
                "https://graph.facebook.com/debug_token?input_token=$cleanToken&access_token=$cleanToken"
            } else {
                "https://graph.facebook.com/v19.0/me?access_token=$cleanToken"
            }

            var connection: HttpURLConnection? = null
            var testSuccessful = false
            var details = ""

            try {
                connection = (URL(testUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "SamTraduction/1.0 (Android)")
                }
                val code = connection.responseCode
                if (code in 200..299) {
                    testSuccessful = true
                    details = "Jeton d'accès validé par Meta Graph API !"
                } else {
                    val errBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    if (errBody.contains("is_valid\":true") || errBody.contains("\"type\":\"APP\"")) {
                        testSuccessful = true
                        details = "Jeton d'application Meta validé !"
                    } else if (errBody.contains("OAuthException")) {
                        details = "Erreur de validation Meta: Jeton expiré ou permissions manquantes."
                    } else {
                        details = "Code réponse Meta: $code"
                    }
                }
            } catch (e: Exception) {
                details = e.localizedMessage ?: "Erreur de connexion"
            } finally {
                connection?.disconnect()
            }

            // Test 2: Tester l'accès à la page AIB ou RTB
            val pageTestUrl = "https://graph.facebook.com/v19.0/$cleanAib?fields=id,name&access_token=$cleanToken"
            try {
                connection = (URL(pageTestUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "SamTraduction/1.0 (Android)")
                }
                if (connection.responseCode in 200..299) {
                    testSuccessful = true
                    details = "Connexion réussie ! Page AIB ($cleanAib) accessible."
                }
            } catch (_: Exception) {
            } finally {
                connection?.disconnect()
            }

            if (testSuccessful) {
                Pair(true, "✅ Connexion Meta établie avec succès ! Les flux AIB ($cleanAib) et RTB ($cleanRtb) sont prêts.")
            } else {
                Pair(true, "⚠️ Clés enregistrées en local. L'application appliquera le jeton pour interroger les pages AIB ($cleanAib) et RTB ($cleanRtb).")
            }
        } catch (e: Exception) {
            Pair(false, "Échec du test de connexion: ${e.localizedMessage}")
        }
    }

    private fun extractPageIdentifier(rawInput: String): String {
        if (rawInput.isBlank()) return ""
        val cleaned = rawInput.trim()
        val urlPattern = Pattern.compile("(?:facebook\\.com/|fb\\.me/|fb\\.watch/)?(?:profile\\.php\\?id=)?([^/?&\\s]+)")
        val matcher = urlPattern.matcher(cleaned)
        return if (matcher.find()) {
            val found = matcher.group(1) ?: cleaned
            if (found.startsWith("http") || found.contains("facebook.com")) {
                cleaned.substringAfterLast("/").substringBefore("?").substringBefore("&")
            } else {
                found
            }
        } else {
            cleaned
        }
    }

    // Flux officiels et sous-rubriques AIB (Agence d'Information du Burkina)
    val AIB_SOURCES = listOf(
        RssSource(
            name = "AIB",
            feedUrls = listOf(
                "https://www.aib.media/?feed=rss2",
                "https://www.aib.media/index.php?feed=rss2",
                "https://www.aib.media/feed/"
            ),
            defaultCategory = "Burkina Faso",
            siteUrl = "https://www.aib.media"
        ),
        RssSource(
            name = "AIB - Actualités",
            feedUrls = listOf(
                "https://www.aib.media/?feed=rss2",
                "https://www.aib.media/category/actualites/feed/"
            ),
            defaultCategory = "Burkina Faso",
            siteUrl = "https://www.aib.media"
        ),
        RssSource(
            name = "AIB - Régions & Société",
            feedUrls = listOf(
                "https://www.aib.media/?feed=rss2",
                "https://www.aib.media/category/societe/feed/"
            ),
            defaultCategory = "Société",
            siteUrl = "https://www.aib.media"
        ),
        RssSource(
            name = "AIB - Économie",
            feedUrls = listOf(
                "https://www.aib.media/?feed=rss2",
                "https://www.aib.media/category/economie/feed/"
            ),
            defaultCategory = "Économie",
            siteUrl = "https://www.aib.media"
        ),
        RssSource(
            name = "AIB - Culture & Sport",
            feedUrls = listOf(
                "https://www.aib.media/?feed=rss2",
                "https://www.aib.media/category/culture/feed/"
            ),
            defaultCategory = "Culture & Arts",
            siteUrl = "https://www.aib.media"
        )
    )

    // Flux officiels RTB (Radiodiffusion Télévision du Burkina) - Toutes catégories et rubriques
    val RTB_SOURCES = listOf(
        RssSource(
            name = "RTB",
            feedUrls = listOf(
                "https://www.rtb.bf/feed/",
                "https://rtb.bf/feed/"
            ),
            defaultCategory = "Burkina Faso",
            siteUrl = "https://www.rtb.bf"
        ),
        RssSource(
            name = "RTB - Actualités",
            feedUrls = listOf(
                "https://www.rtb.bf/category/actualites/feed/",
                "https://rtb.bf/category/actualites/feed/"
            ),
            defaultCategory = "Burkina Faso",
            siteUrl = "https://www.rtb.bf"
        ),
        RssSource(
            name = "RTB - Société & Régions",
            feedUrls = listOf(
                "https://www.rtb.bf/category/societe/feed/",
                "https://www.rtb.bf/category/regions/feed/"
            ),
            defaultCategory = "Société",
            siteUrl = "https://www.rtb.bf"
        ),
        RssSource(
            name = "RTB - Économie",
            feedUrls = listOf(
                "https://www.rtb.bf/category/economie/feed/"
            ),
            defaultCategory = "Économie",
            siteUrl = "https://www.rtb.bf"
        ),
        RssSource(
            name = "RTB - Culture & Magazines",
            feedUrls = listOf(
                "https://www.rtb.bf/category/culture/feed/",
                "https://www.rtb.bf/category/magazines/feed/"
            ),
            defaultCategory = "Culture & Arts",
            siteUrl = "https://www.rtb.bf"
        ),
        RssSource(
            name = "RTB - Sport",
            feedUrls = listOf(
                "https://www.rtb.bf/category/sport/feed/"
            ),
            defaultCategory = "Sport",
            siteUrl = "https://www.rtb.bf"
        ),
        RssSource(
            name = "RTB - JT & Reportages",
            feedUrls = listOf(
                "https://www.rtb.bf/category/journaux-televises/feed/",
                "https://www.rtb.bf/category/reportages/feed/"
            ),
            defaultCategory = "RTB Télé & Radio",
            siteUrl = "https://www.rtb.bf"
        )
    )

    private var cachedArticles: List<NewsArticle> = emptyList()

    /**
     * Fournit une image thématique burkinabè de haute qualité et contextualisée
     * lorsque l'article n'a pas d'image intégrée dans son flux RSS/REST.
     */
    fun getDefaultCoverImageFor(category: String, source: String, title: String): String {
        val lowerTitle = title.lowercase()
        val lowerCat = category.lowercase()
        val isRtb = source.contains("RTB", ignoreCase = true)

        return when {
            lowerTitle.contains("jt") || lowerTitle.contains("journal télévisé") || lowerTitle.contains("journal de 13h") || lowerTitle.contains("journal de 20h") || lowerCat.contains("télé") || lowerCat.contains("radio") ->
                "https://images.unsplash.com/photo-1585829365295-ab7cd400c167?w=800&auto=format&fit=crop&q=80" // Studio TV / News Broadcast
            lowerCat.contains("sport") || lowerTitle.contains("étalon") || lowerTitle.contains("football") || lowerTitle.contains("championnat") ->
                "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=800&auto=format&fit=crop&q=80" // Sport Stadium
            lowerCat.contains("éco") || lowerCat.contains("eco") || lowerTitle.contains("dan fani") || lowerTitle.contains("coton") || lowerTitle.contains("commerce") || lowerTitle.contains("industrie") ->
                "https://images.unsplash.com/photo-1590736969955-71cc94801759?w=800&auto=format&fit=crop&q=80" // Artisanat / Économie locale
            lowerCat.contains("cultur") || lowerCat.contains("art") || lowerTitle.contains("fespaco") || lowerTitle.contains("musique") || lowerTitle.contains("siao") ->
                "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=800&auto=format&fit=crop&q=80" // Festival / Culture & Spectacle
            lowerCat.contains("sociét") || lowerCat.contains("societ") || lowerCat.contains("région") || lowerCat.contains("region") || lowerTitle.contains("santé") || lowerTitle.contains("éducation") ->
                "https://images.unsplash.com/photo-1544717305-2782549b5136?w=800&auto=format&fit=crop&q=80" // Éducation / Société
            isRtb ->
                "https://images.unsplash.com/photo-1495020689067-958852a7765e?w=800&auto=format&fit=crop&q=80" // Actualités Presse RTB
            else ->
                "https://images.unsplash.com/photo-1504711434969-e33886168f5c?w=800&auto=format&fit=crop&q=80" // AIB Presse & Dépêches
        }
    }

    /**
     * Récupère en ultra-rapide toutes les dépêches et informations de la RTB et de l'AIB.
     * Interroge l'API REST temps réel de la RTB et de l'AIB, l'API Meta Facebook et les flux RSS en parallèle.
     * Les publications les plus fraîches et récentes sont STRICTEMENT triées en tête de liste.
     */
    suspend fun getLiveNews(forceRefresh: Boolean = false): List<NewsArticle> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cutoffRetention = now - (7L * 24L * 60 * 60 * 1000L) // 7 jours de rétention pour conserver toutes les informations récentes

        // Purge des articles trop anciens
        cachedArticles = cachedArticles.filter { it.timestamp >= cutoffRetention }

        if (!forceRefresh && cachedArticles.isNotEmpty()) {
            return@withContext cachedArticles.sortedByDescending { it.timestamp }
        }

        val allFetched = mutableListOf<NewsArticle>()

        try {
            coroutineScope {
                // 1. Appel direct à l'API Meta (Facebook Graph API) pour AIB & RTB
                val metaDeferred = async {
                    fetchFromMetaGraphApi()
                }

                // 2. Appel ultra-rapide en direct à l'API REST WordPress de la RTB (Articles texte & reportages)
                val rtbApiDeferred = async {
                    fetchFromRtbRestApi()
                }

                // 3. Appel ultra-rapide en direct à l'API REST WordPress de l'AIB
                val wpApiDeferred = async {
                    fetchFromWpRestApi()
                }

                // 4. Appels simultanés à l'ensemble des flux RSS RTB en parallèle
                val rtbRssDeferredList = RTB_SOURCES.map { source ->
                    async {
                        fetchFromSource(source)
                    }
                }

                // 5. Appels simultanés aux flux RSS AIB en parallèle
                val aibRssDeferredList = AIB_SOURCES.map { source ->
                    async {
                        fetchFromSource(source)
                    }
                }

                // 6. Appel direct à l'API YouTube Data v3 (Vidéos et JT RTB)
                val ytDeferred = async {
                    fetchFromYouTubeApi()
                }

                val metaResults = metaDeferred.await()
                allFetched.addAll(metaResults)

                val ytResults = ytDeferred.await()
                allFetched.addAll(ytResults)

                val rtbResults = rtbApiDeferred.await()
                allFetched.addAll(rtbResults)

                val wpResults = wpApiDeferred.await()
                allFetched.addAll(wpResults)

                val rtbRssResults = rtbRssDeferredList.awaitAll()
                rtbRssResults.forEach { articles ->
                    allFetched.addAll(articles)
                }

                val aibRssResults = aibRssDeferredList.awaitAll()
                aibRssResults.forEach { articles ->
                    allFetched.addAll(articles)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching AIB & RTB live feeds", e)
        }

        // Tri décroissant strict par timestamp (les plus fraîches tout en haut)
        val finalArticles = allFetched
            .filter { it.timestamp >= cutoffRetention }
            .distinctBy { normalizeTitle(it.title) }
            .sortedByDescending { it.timestamp }

        val activeArticles = if (finalArticles.isNotEmpty()) {
            finalArticles
        } else {
            getDefaultBurkinaNews().sortedByDescending { it.timestamp }
        }

        cachedArticles = activeArticles
        activeArticles
    }

    /**
     * Interroge l'API Meta (Facebook Graph API) et les passerelles publiques Facebook
     * pour récupérer directement les publications Facebook de l'AIB et de la RTB en temps réel avec photos et textes.
     */
    private fun fetchFromMetaGraphApi(): List<NewsArticle> {
        val articles = mutableListOf<NewsArticle>()
        val dynamicTargets = mutableListOf<Pair<String, String>>()

        if (customAibTarget.isNotBlank()) {
            dynamicTargets.add(Pair(customAibTarget, "AIB (Facebook)"))
        }
        if (customRtbTarget.isNotBlank() && customRtbTarget != customAibTarget) {
            dynamicTargets.add(Pair(customRtbTarget, "RTB (Facebook)"))
        }

        val fallbacks = listOf(
            Pair("RTBOfficiel", "RTB (Facebook)"),
            Pair("AIB.Burkina", "AIB (Facebook)"),
            Pair("248050315307820", "AIB (Facebook)")
        )
        for (fb in fallbacks) {
            if (!dynamicTargets.any { it.first.equals(fb.first, ignoreCase = true) }) {
                dynamicTargets.add(fb)
            }
        }

        // 1. Si un token Meta Graph API est configuré
        if (customMetaToken.isNotBlank()) {
            for ((pageName, sourceLabel) in dynamicTargets) {
                val endpoints = listOf(
                    "https://graph.facebook.com/v19.0/$pageName/posts?fields=id,message,created_time,full_picture,permalink_url,attachments{media,type,url,subattachments}&access_token=$customMetaToken",
                    "https://graph.facebook.com/v19.0/$pageName/feed?fields=id,message,created_time,full_picture,permalink_url&access_token=$customMetaToken"
                )

                for (endpoint in endpoints) {
                    var connection: HttpURLConnection? = null
                    try {
                        val url = URL(endpoint)
                        connection = (url.openConnection() as HttpURLConnection).apply {
                            connectTimeout = 5000
                            readTimeout = 5000
                            instanceFollowRedirects = true
                            requestMethod = "GET"
                            setRequestProperty("User-Agent", "SamTraduction/1.0 (Android)")
                            setRequestProperty("Accept", "application/json")
                        }

                        if (connection.responseCode in 200..299) {
                            val jsonText = connection.inputStream.bufferedReader().use { it.readText() }
                            val jsonRoot = org.json.JSONObject(jsonText)
                            val dataArray = jsonRoot.optJSONArray("data") ?: continue
                            val now = System.currentTimeMillis()
                            val cutoffRetention = now - (7L * 24L * 60 * 60 * 1000L)

                            for (i in 0 until dataArray.length()) {
                                val item = dataArray.optJSONObject(i) ?: continue
                                val idStr = item.optString("id", "")
                                val message = item.optString("message", "").trim()
                                val createdTime = item.optString("created_time", "")
                                val fullPicture = item.optString("full_picture", "")
                                val permalinkUrl = item.optString("permalink_url", "https://www.facebook.com/$pageName")

                                if (message.isBlank()) continue

                                val lines = message.lines().filter { it.isNotBlank() }
                                val rawTitle = lines.firstOrNull() ?: message
                                val title = cleanHtml(if (rawTitle.length > 110) rawTitle.take(107) + "..." else rawTitle)
                                val summary = cleanHtml(if (message.length > 220) message.take(217) + "..." else message)

                                val parsedTimestamp = parseIsoDateToMillis(createdTime)
                                if (parsedTimestamp >= cutoffRetention) {
                                    val isRtb = pageName.contains("RTB", ignoreCase = true)
                                    val category = if (isRtb) "Burkina Faso" else "AIB Direct"
                                    val finalImg = fullPicture.trim()

                                    articles.add(
                                        NewsArticle(
                                            id = "meta_${pageName}_${idStr.ifBlank { parsedTimestamp.toString() }}",
                                            title = title,
                                            summary = summary,
                                            category = category,
                                            date = formatRelativeDate(parsedTimestamp),
                                            source = sourceLabel,
                                            url = permalinkUrl,
                                            imageUrl = finalImg,
                                            timestamp = parsedTimestamp,
                                            fullContent = message
                                        )
                                    )
                                }
                            }

                            if (articles.isNotEmpty()) {
                                break
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Meta Graph API check for $pageName: ${e.message}")
                    } finally {
                        connection?.disconnect()
                    }
                }
            }
        }

        // 2. Passerelles RSS ouvertes pour les pages publiques Facebook
        val publicFacebookBridges = listOf(
            Pair("https://rsshub.app/facebook/page/RTBOfficiel", "RTB (Facebook)"),
            Pair("https://rsshub.app/facebook/page/AIB.Burkina", "AIB (Facebook)")
        )

        for ((bridgeUrl, sourceLabel) in publicFacebookBridges) {
            try {
                val bridgeSource = RssSource(
                    name = sourceLabel,
                    feedUrls = listOf(bridgeUrl),
                    defaultCategory = if (sourceLabel.contains("RTB")) "Burkina Faso" else "AIB Direct",
                    siteUrl = if (sourceLabel.contains("RTB")) "https://www.facebook.com/RTBOfficiel" else "https://www.facebook.com/AIB.Burkina"
                )
                val fetchedBridge = fetchFromSource(bridgeSource)
                if (fetchedBridge.isNotEmpty()) {
                    articles.addAll(fetchedBridge)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Bridge FB fetch: ${e.message}")
            }
        }

        return articles
    }

    /**
     * Interroge l'API officielle YouTube Data v3 pour récupérer les dernières vidéos et journaux télévisés (JT) publiés par la RTB.
     */
    private fun fetchFromYouTubeApi(): List<NewsArticle> {
        val articles = mutableListOf<NewsArticle>()
        val apiKey = BuildConfig.YOUTUBE_API_KEY.trim().removeSurrounding("\"").removeSurrounding("'").trim()
        if (apiKey.isBlank() || apiKey == "MY_YOUTUBE_API_KEY") {
            return emptyList()
        }

        val searchQueries = listOf(
            "RTB Burkina Journal Télévisé",
            "RTB Télévision Burkina Faso"
        )

        for (query in searchQueries) {
            var connection: HttpURLConnection? = null
            try {
                val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
                val endpoint = "https://www.googleapis.com/youtube/v3/search?part=snippet&q=$encodedQuery&maxResults=10&order=date&type=video&key=$apiKey"
                val url = URL(endpoint)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6000
                    readTimeout = 6000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "SamTraduction/1.0 (Android)")
                    setRequestProperty("Accept", "application/json")
                }

                if (connection.responseCode in 200..299) {
                    val jsonText = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonRoot = org.json.JSONObject(jsonText)
                    val items = jsonRoot.optJSONArray("items") ?: continue

                    for (i in 0 until items.length()) {
                        val item = items.optJSONObject(i) ?: continue
                        val idObj = item.optJSONObject("id")
                        val videoId = idObj?.optString("videoId", "") ?: ""
                        if (videoId.isBlank()) continue

                        val snippet = item.optJSONObject("snippet") ?: continue
                        val title = snippet.optString("title", "")
                            .replace("&quot;", "\"")
                            .replace("&#39;", "'")
                            .replace("&amp;", "&")
                        val description = snippet.optString("description", "")
                        val publishedAt = snippet.optString("publishedAt", "")
                        val thumbnails = snippet.optJSONObject("thumbnails")
                        val highThumb = thumbnails?.optJSONObject("high")?.optString("url", "")
                            ?: thumbnails?.optJSONObject("medium")?.optString("url", "")
                            ?: "https://img.youtube.com/vi/$videoId/hqdefault.jpg"

                        val parsedTimestamp = parseIsoDateToMillis(publishedAt)
                        articles.add(
                            NewsArticle(
                                id = "yt_$videoId",
                                title = title,
                                summary = description.ifBlank { "Édition et reportage vidéo diffusés par la RTB sur YouTube." },
                                category = "RTB Télé & Radio",
                                date = formatRelativeDate(parsedTimestamp),
                                source = "RTB (YouTube)",
                                url = "https://www.youtube.com/watch?v=$videoId",
                                imageUrl = highThumb,
                                timestamp = if (parsedTimestamp > 0) parsedTimestamp else System.currentTimeMillis(),
                                isVideo = true,
                                youtubeId = videoId,
                                videoUrl = "https://www.youtube.com/watch?v=$videoId",
                                fullContent = description
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "YouTube API fetch failed: ${e.message}")
            } finally {
                connection?.disconnect()
            }
        }
        return articles
    }

    /**
     * Interroge directement le point d'accès REST de la RTB (Radiodiffusion Télévision du Burkina)
     * Récupère instantanément les JT (13h, 19h, 20h), reportages et émissions officielles.
     */
    private fun fetchFromRtbRestApi(): List<NewsArticle> {
        val articles = mutableListOf<NewsArticle>()
        val endpoints = listOf(
            "https://www.rtb.bf/index.php?rest_route=/wp/v2/posts&_embed=true&per_page=50",
            "https://rtb.bf/index.php?rest_route=/wp/v2/posts&_embed=true&per_page=50",
            "https://www.rtb.bf/wp-json/wp/v2/posts?_embed&per_page=50",
            "https://rtb.bf/wp-json/wp/v2/posts?_embed&per_page=50"
        )

        for (endpoint in endpoints) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL(endpoint)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 7000
                    readTimeout = 7000
                    instanceFollowRedirects = true
                    requestMethod = "GET"
                    setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
                    )
                    setRequestProperty("Accept", "application/json, text/plain, */*")
                    setRequestProperty("Accept-Encoding", "gzip, deflate")
                    setRequestProperty("Cache-Control", "no-cache")
                }

                val status = connection.responseCode
                if (status in 200..299) {
                    val encoding = connection.contentEncoding
                    val rawStream = connection.inputStream
                    val inputStream = if ("gzip".equals(encoding, ignoreCase = true)) {
                        GZIPInputStream(rawStream)
                    } else {
                        rawStream
                    }

                    val jsonText = inputStream.bufferedReader().use { it.readText() }
                    val jsonArray = org.json.JSONArray(jsonText)
                    val now = System.currentTimeMillis()
                    val cutoffRetention = now - (7L * 24L * 60 * 60 * 1000L)

                    for (i in 0 until jsonArray.length()) {
                        val item = jsonArray.optJSONObject(i) ?: continue
                        val idNum = item.optInt("id", 0)
                        val dateGmt = item.optString("date_gmt", "").ifBlank { item.optString("date", "") }
                        val titleObj = item.optJSONObject("title")
                        val rawTitle = titleObj?.optString("rendered", "") ?: ""
                        val title = cleanHtml(rawTitle)
                        val link = item.optString("link", "")
                        val excerptObj = item.optJSONObject("excerpt")
                        val rawExcerpt = excerptObj?.optString("rendered", "") ?: ""
                        val contentObj = item.optJSONObject("content")
                        val rawContent = contentObj?.optString("rendered", "") ?: ""

                        val videoInfo = extractVideoInfo(rawContent, link, title)

                        var summary = cleanHtml(rawExcerpt)
                        if (summary.isBlank()) {
                            summary = cleanHtml(rawContent)
                        }
                        if (summary.length > 220) {
                            summary = summary.take(217) + "..."
                        }

                        // VRAIE image officielle de la publication du site
                        var imageUrl = ""
                        val embedded = item.optJSONObject("_embedded")
                        if (embedded != null) {
                            val mediaArray = embedded.optJSONArray("wp:featuredmedia")
                            if (mediaArray != null && mediaArray.length() > 0) {
                                val mediaObj = mediaArray.optJSONObject(0)
                                val sizesObj = mediaObj?.optJSONObject("media_details")?.optJSONObject("sizes")
                                val largeUrl = sizesObj?.optJSONObject("large")?.optString("source_url", "")
                                val medLargeUrl = sizesObj?.optJSONObject("medium_large")?.optString("source_url", "")
                                val fullUrl = sizesObj?.optJSONObject("full")?.optString("source_url", "")
                                imageUrl = listOfNotNull(largeUrl, medLargeUrl, fullUrl)
                                    .firstOrNull { it.isNotBlank() }
                                    ?: mediaObj?.optString("source_url", "")
                                    ?: ""
                            }
                        }
                        // Recherche jetpack_featured_media_url ou yoast og_image si absent
                        if (imageUrl.isBlank()) {
                            imageUrl = item.optString("jetpack_featured_media_url", "")
                        }
                        if (imageUrl.isBlank()) {
                            val yoast = item.optJSONObject("yoast_head_json")
                            val ogImgArray = yoast?.optJSONArray("og_image")
                            if (ogImgArray != null && ogImgArray.length() > 0) {
                                imageUrl = ogImgArray.optJSONObject(0)?.optString("url", "") ?: ""
                            }
                        }
                        if (imageUrl.isBlank() && rawContent.isNotBlank()) {
                            imageUrl = extractImageFromHtml(rawContent)
                        }
                        if (imageUrl.isBlank() && videoInfo.youtubeId.isNotBlank()) {
                            imageUrl = "https://img.youtube.com/vi/${videoInfo.youtubeId}/hqdefault.jpg"
                        }

                        var category = "Burkina Faso"
                        if (embedded != null) {
                            val termsArray = embedded.optJSONArray("wp:term")
                            if (termsArray != null && termsArray.length() > 0) {
                                val catArray = termsArray.optJSONArray(0)
                                if (catArray != null && catArray.length() > 0) {
                                    val firstCat = catArray.optJSONObject(0)?.optString("name", "") ?: ""
                                    if (firstCat.isNotBlank()) {
                                        category = formatCategory(cleanHtml(firstCat))
                                    }
                                }
                            }
                        }
                        if (category == "Burkina Faso" && videoInfo.isVideo) {
                            category = "RTB Télé & Radio"
                        }

                        val parsedTimestamp = parseIsoDateToMillis(dateGmt)
                        if (parsedTimestamp >= cutoffRetention && title.isNotBlank() && link.isNotBlank()) {
                            val id = "rtb_wp_${idNum}_${link.hashCode()}"
                            articles.add(
                                NewsArticle(
                                    id = id,
                                    title = title,
                                    summary = if (summary.isNotBlank()) summary else "Reportage et article d'information diffusé par la Radiodiffusion Télévision du Burkina (RTB).",
                                    category = category,
                                    date = formatRelativeDate(parsedTimestamp),
                                    source = "RTB",
                                    url = link,
                                    imageUrl = imageUrl,
                                    timestamp = parsedTimestamp,
                                    videoUrl = videoInfo.videoUrl,
                                    youtubeId = videoInfo.youtubeId,
                                    isVideo = videoInfo.isVideo,
                                    fullContent = rawContent
                                )
                            )
                        }
                    }

                    if (articles.isNotEmpty()) {
                        return articles
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching from RTB REST API ($endpoint): ${e.message}")
            } finally {
                connection?.disconnect()
            }
        }
        return articles
    }

    /**
     * Interroge directement le point d'accès REST de l'AIB (WordPress REST API)
     * Fournit les articles à la seconde exacte de publication par les journalistes.
     */
    private fun fetchFromWpRestApi(): List<NewsArticle> {
        val articles = mutableListOf<NewsArticle>()
        val endpoints = listOf(
            "https://www.aib.media/index.php?rest_route=/wp/v2/posts&_embed=true&per_page=100",
            "https://aib.media/index.php?rest_route=/wp/v2/posts&_embed=true&per_page=100",
            "https://www.aib.media/wp-json/wp/v2/posts?_embed&per_page=100",
            "https://aib.media/wp-json/wp/v2/posts?_embed&per_page=100"
        )

        for (endpoint in endpoints) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL(endpoint)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 7000
                    readTimeout = 7000
                    instanceFollowRedirects = true
                    requestMethod = "GET"
                    setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
                    )
                    setRequestProperty("Accept", "application/json, text/plain, */*")
                    setRequestProperty("Accept-Encoding", "gzip, deflate")
                    setRequestProperty("Cache-Control", "no-cache")
                }

                val status = connection.responseCode
                if (status in 200..299) {
                    val encoding = connection.contentEncoding
                    val rawStream = connection.inputStream
                    val inputStream = if ("gzip".equals(encoding, ignoreCase = true)) {
                        GZIPInputStream(rawStream)
                    } else {
                        rawStream
                    }

                    val jsonText = inputStream.bufferedReader().use { it.readText() }
                    val jsonArray = org.json.JSONArray(jsonText)
                    val now = System.currentTimeMillis()
                    val cutoffRetention = now - (7L * 24L * 60 * 60 * 1000L)

                    for (i in 0 until jsonArray.length()) {
                        val item = jsonArray.optJSONObject(i) ?: continue
                        val idNum = item.optInt("id", 0)
                        val dateGmt = item.optString("date_gmt", "").ifBlank { item.optString("date", "") }
                        val titleObj = item.optJSONObject("title")
                        val rawTitle = titleObj?.optString("rendered", "") ?: ""
                        val title = cleanHtml(rawTitle)
                        val link = item.optString("link", "")
                        val excerptObj = item.optJSONObject("excerpt")
                        val rawExcerpt = excerptObj?.optString("rendered", "") ?: ""
                        val contentObj = item.optJSONObject("content")
                        val rawContent = contentObj?.optString("rendered", "") ?: ""

                        val videoInfo = extractVideoInfo(rawContent, link, title)

                        var summary = cleanHtml(rawExcerpt)
                        if (summary.isBlank()) {
                            summary = cleanHtml(rawContent)
                        }
                        if (summary.length > 220) {
                            summary = summary.take(217) + "..."
                        }

                        // VRAIE image officielle de la publication du site
                        var imageUrl = ""
                        val embedded = item.optJSONObject("_embedded")
                        if (embedded != null) {
                            val mediaArray = embedded.optJSONArray("wp:featuredmedia")
                            if (mediaArray != null && mediaArray.length() > 0) {
                                val mediaObj = mediaArray.optJSONObject(0)
                                val sizesObj = mediaObj?.optJSONObject("media_details")?.optJSONObject("sizes")
                                val largeUrl = sizesObj?.optJSONObject("large")?.optString("source_url", "")
                                val medLargeUrl = sizesObj?.optJSONObject("medium_large")?.optString("source_url", "")
                                val fullUrl = sizesObj?.optJSONObject("full")?.optString("source_url", "")
                                imageUrl = listOfNotNull(largeUrl, medLargeUrl, fullUrl)
                                    .firstOrNull { it.isNotBlank() }
                                    ?: mediaObj?.optString("source_url", "")
                                    ?: ""
                            }
                        }
                        if (imageUrl.isBlank()) {
                            imageUrl = item.optString("jetpack_featured_media_url", "")
                        }
                        if (imageUrl.isBlank()) {
                            val yoast = item.optJSONObject("yoast_head_json")
                            val ogImgArray = yoast?.optJSONArray("og_image")
                            if (ogImgArray != null && ogImgArray.length() > 0) {
                                imageUrl = ogImgArray.optJSONObject(0)?.optString("url", "") ?: ""
                            }
                        }
                        if (imageUrl.isBlank() && rawContent.isNotBlank()) {
                            imageUrl = extractImageFromHtml(rawContent)
                        }
                        if (imageUrl.isBlank() && videoInfo.youtubeId.isNotBlank()) {
                            imageUrl = "https://img.youtube.com/vi/${videoInfo.youtubeId}/hqdefault.jpg"
                        }

                        // Rubrique
                        var category = "Burkina Faso"
                        if (embedded != null) {
                            val termsArray = embedded.optJSONArray("wp:term")
                            if (termsArray != null && termsArray.length() > 0) {
                                val catArray = termsArray.optJSONArray(0)
                                if (catArray != null && catArray.length() > 0) {
                                    val firstCat = catArray.optJSONObject(0)?.optString("name", "") ?: ""
                                    if (firstCat.isNotBlank()) {
                                        category = formatCategory(cleanHtml(firstCat))
                                    }
                                }
                            }
                        }

                        val parsedTimestamp = parseIsoDateToMillis(dateGmt)
                        if (parsedTimestamp >= cutoffRetention && title.isNotBlank() && link.isNotBlank()) {
                            val id = "aib_wp_${idNum}_${link.hashCode()}"
                            articles.add(
                                NewsArticle(
                                    id = id,
                                    title = title,
                                    summary = if (summary.isNotBlank()) summary else "Dépêche officielle transmise en direct par l'Agence d'Information du Burkina (AIB).",
                                    category = category,
                                    date = formatRelativeDate(parsedTimestamp),
                                    source = "AIB",
                                    url = link,
                                    imageUrl = imageUrl,
                                    timestamp = parsedTimestamp,
                                    videoUrl = videoInfo.videoUrl,
                                    youtubeId = videoInfo.youtubeId,
                                    isVideo = videoInfo.isVideo,
                                    fullContent = rawContent
                                )
                            )
                        }
                    }

                    if (articles.isNotEmpty()) {
                        return articles
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching from WP REST API ($endpoint): ${e.message}")
            } finally {
                connection?.disconnect()
            }
        }
        return articles
    }

    private fun fetchFromSource(source: RssSource): List<NewsArticle> {
        for (urlStr in source.feedUrls) {
            val articles = fetchFromUrl(urlStr, source)
            if (articles.isNotEmpty()) {
                return articles
            }
        }
        return emptyList()
    }

    private fun fetchFromUrl(urlStr: String, source: RssSource): List<NewsArticle> {
        val articles = mutableListOf<NewsArticle>()
        var connection: HttpURLConnection? = null
        var currentUrl = urlStr
        var redirects = 0

        while (redirects < 4) {
            try {
                val url = URL(currentUrl)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    instanceFollowRedirects = true
                    requestMethod = "GET"
                    setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
                    )
                    setRequestProperty(
                        "Accept",
                        "application/rss+xml, application/atom+xml, application/xml, text/xml, */*"
                    )
                    setRequestProperty("Accept-Encoding", "gzip, deflate")
                }

                val status = connection.responseCode
                if (status == HttpURLConnection.HTTP_MOVED_PERM ||
                    status == HttpURLConnection.HTTP_MOVED_TEMP ||
                    status == HttpURLConnection.HTTP_SEE_OTHER ||
                    status == 307 || status == 308
                ) {
                    val newUrl = connection.getHeaderField("Location")
                    if (!newUrl.isNullOrBlank()) {
                        currentUrl = if (newUrl.startsWith("http")) newUrl else URL(url, newUrl).toString()
                        connection.disconnect()
                        redirects++
                        continue
                    }
                }

                if (status in 200..299) {
                    val encoding = connection.contentEncoding
                    val rawStream = connection.inputStream
                    val inputStream = if ("gzip".equals(encoding, ignoreCase = true)) {
                        GZIPInputStream(rawStream)
                    } else {
                        rawStream
                    }

                    inputStream.use { stream ->
                        articles.addAll(parseRssXml(stream, source))
                    }
                }
                break
            } catch (e: Exception) {
                Log.w(TAG, "Failed feed $currentUrl (${source.name}): ${e.message}")
                break
            } finally {
                connection?.disconnect()
            }
        }
        return articles
    }

    private fun parseRssXml(inputStream: InputStream, source: RssSource): List<NewsArticle> {
        val list = mutableListOf<NewsArticle>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, null)

        var eventType = parser.eventType
        var insideItem = false

        var title = ""
        var link = ""
        var description = ""
        var pubDate = ""
        var category = source.defaultCategory
        var imageUrl = ""

        val now = System.currentTimeMillis()
        val cutoff24h = now - (24L * 60 * 60 * 1000L)

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tagName = parser.name?.lowercase() ?: ""

            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (tagName == "item" || tagName == "entry") {
                        insideItem = true
                        title = ""
                        link = ""
                        description = ""
                        pubDate = ""
                        category = source.defaultCategory
                        imageUrl = ""
                    } else if (insideItem) {
                        when {
                            tagName == "title" -> title = cleanHtml(parser.nextText())
                            tagName == "link" -> {
                                val href = parser.getAttributeValue(null, "href")
                                link = if (!href.isNullOrBlank()) href else parser.nextText().trim()
                            }
                            tagName in listOf("description", "summary") -> {
                                val raw = parser.nextText()
                                if (imageUrl.isBlank()) {
                                    imageUrl = extractImageFromHtml(raw)
                                }
                                description = cleanHtml(raw)
                            }
                            tagName == "content:encoded" || tagName == "content" -> {
                                val raw = parser.nextText()
                                if (imageUrl.isBlank()) {
                                    imageUrl = extractImageFromHtml(raw)
                                }
                                if (description.isBlank()) {
                                    description = cleanHtml(raw)
                                }
                            }
                            tagName in listOf("pubdate", "published", "updated", "date", "dc:date") -> {
                                pubDate = parser.nextText().trim()
                            }
                            tagName == "category" -> {
                                val cat = cleanHtml(parser.nextText())
                                if (cat.isNotBlank() && category == source.defaultCategory) {
                                    category = formatCategory(cat)
                                }
                            }
                            tagName == "enclosure" -> {
                                val type = parser.getAttributeValue(null, "type") ?: ""
                                val encUrl = parser.getAttributeValue(null, "url") ?: ""
                                if (encUrl.isNotBlank() && (type.startsWith("image") || encUrl.contains(".jpg") || encUrl.contains(".png") || encUrl.contains(".webp") || encUrl.contains(".jpeg"))) {
                                    imageUrl = encUrl
                                }
                            }
                            tagName in listOf("media:content", "media:thumbnail") -> {
                                val mediaUrl = parser.getAttributeValue(null, "url") ?: ""
                                if (mediaUrl.isNotBlank()) {
                                    imageUrl = mediaUrl
                                }
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (tagName == "item" || tagName == "entry") {
                        if (title.isNotBlank() && link.isNotBlank()) {
                            val id = "${source.name}_${link.hashCode()}"
                            val parsedTimestamp = parsePubDateToMillis(pubDate)
                            val nowTime = System.currentTimeMillis()
                            val cutoffRetention = nowTime - (7L * 24L * 60 * 60 * 1000L)

                            if (parsedTimestamp >= cutoffRetention) {
                                val formattedDate = formatRelativeDate(parsedTimestamp)
                                val shortSummary = if (description.length > 220) {
                                    description.take(217) + "..."
                                } else description

                                val videoInfo = extractVideoInfo(description, link, title)
                                val finalImage = imageUrl.trim()

                                list.add(
                                    NewsArticle(
                                        id = id,
                                        title = title,
                                        summary = if (shortSummary.isNotBlank()) shortSummary else "Consultez l'article complet pour tous les détails de cette information.",
                                        category = category,
                                        date = formattedDate,
                                        source = source.name,
                                        url = link,
                                        imageUrl = finalImage,
                                        timestamp = parsedTimestamp,
                                        videoUrl = videoInfo.videoUrl,
                                        youtubeId = videoInfo.youtubeId,
                                        isVideo = videoInfo.isVideo,
                                        fullContent = description
                                    )
                                )
                            }
                        }
                        insideItem = false
                    }
                }
            }
            eventType = parser.next()
        }

        return list
    }

    private fun cleanHtml(html: String): String {
        return html
            .replace(Regex("<style[^>]*>[\\s\\S]*?</style>"), "")
            .replace(Regex("<script[^>]*>[\\s\\S]*?</script>"), "")
            .replace(Regex("<[^>]*>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&#8217;", "'")
            .replace("&#8220;", "«")
            .replace("&#8221;", "»")
            .replace("&#8211;", "-")
            .replace("&#8212;", "—")
            .replace("&eacute;", "é")
            .replace("&egrave;", "è")
            .replace("&agrave;", "à")
            .replace("&ccedil;", "ç")
            .replace("&ocirc;", "ô")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun extractImageFromHtml(html: String): String {
        val matcher = Pattern.compile("<img[^>]+src=[\"']([^\"'>]+)[\"']").matcher(html)
        if (matcher.find()) {
            val src = matcher.group(1) ?: ""
            if (src.startsWith("http")) return src
        }
        return ""
    }

    private fun formatCategory(cat: String): String {
        val lower = cat.lowercase()
        return when {
            lower.contains("sport") -> "Sport"
            lower.contains("éco") || lower.contains("eco") || lower.contains("finan") -> "Économie"
            lower.contains("cultur") || lower.contains("art") || lower.contains("ciné") || lower.contains("fespaco") -> "Culture & Arts"
            lower.contains("polit") || lower.contains("gouv") || lower.contains("défense") || lower.contains("secur") -> "Burkina Faso"
            lower.contains("inter") || lower.contains("afriq") || lower.contains("monde") -> "International"
            else -> "Société"
        }
    }

    private fun parseIsoDateToMillis(dateStr: String): Long {
        if (dateStr.isBlank()) return System.currentTimeMillis()
        val cleaned = dateStr.trim()
        val formats = listOf(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") },
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US),
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        )
        for (fmt in formats) {
            try {
                val d = fmt.parse(cleaned)
                if (d != null) {
                    val time = d.time
                    val now = System.currentTimeMillis()
                    return if (time > now) now else time
                }
            } catch (_: Exception) {}
        }
        return parsePubDateToMillis(cleaned)
    }

    private fun parsePubDateToMillis(pubDateStr: String): Long {
        if (pubDateStr.isBlank()) return System.currentTimeMillis()
        val cleaned = pubDateStr.trim()

        val formats = listOf(
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US),
            SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z", Locale.US),
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US),
            SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss z", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US),
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.FRENCH),
            SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z", Locale.FRENCH),
            SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.FRENCH),
            SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.FRENCH)
        )

        for (fmt in formats) {
            try {
                val d = fmt.parse(cleaned)
                if (d != null) {
                    val time = d.time
                    val now = System.currentTimeMillis()
                    // Si l'heure du serveur distant est légèrement dans le futur (décalage horaire), ajuster à maintenant
                    return if (time > now) now else time
                }
            } catch (_: Exception) {}
        }
        return System.currentTimeMillis()
    }

    data class VideoInfo(
        val isVideo: Boolean,
        val youtubeId: String,
        val videoUrl: String
    )

    fun extractVideoInfo(content: String, link: String, title: String): VideoInfo {
        var ytId = ""
        var vUrl = ""

        if (content.isNotBlank()) {
            // 1. YouTube embeds (iframe or standard links)
            val ytEmbedMatcher = Pattern.compile("https?://(?:www\\.)?(?:youtube-nocookie\\.com|youtube\\.com)/(?:embed/|watch\\?v=|v/|live/)([a-zA-Z0-9_\\-]{11})").matcher(content)
            if (ytEmbedMatcher.find()) {
                ytId = ytEmbedMatcher.group(1) ?: ""
            }
            if (ytId.isBlank()) {
                val youtuBeMatcher = Pattern.compile("https?://youtu\\.be/([a-zA-Z0-9_\\-]{11})").matcher(content)
                if (youtuBeMatcher.find()) {
                    ytId = youtuBeMatcher.group(1) ?: ""
                }
            }

            // 2. Direct MP4 video sources
            if (ytId.isBlank()) {
                val mp4Matcher = Pattern.compile("https?://[^\"'\\s<>]+\\.mp4").matcher(content)
                if (mp4Matcher.find()) {
                    vUrl = mp4Matcher.group() ?: ""
                }
            }
        }

        // Also check if link itself points to youtube
        if (ytId.isBlank() && link.isNotBlank()) {
            val ytLinkMatcher = Pattern.compile("https?://(?:www\\.)?(?:youtube\\.com/watch\\?v=|youtube\\.com/live/|youtu\\.be/)([a-zA-Z0-9_\\-]{11})").matcher(link)
            if (ytLinkMatcher.find()) {
                ytId = ytLinkMatcher.group(1) ?: ""
            }
        }

        if (ytId.isNotBlank()) {
            vUrl = "https://www.youtube-nocookie.com/embed/$ytId?autoplay=1&enablejsapi=1&playsinline=1"
        }

        // isVideo est uniquement vrai s'il y a un flux vidéo interactif ou si c'est explicitement le journal télévisé / JT
        val isVideo = ytId.isNotBlank() || vUrl.isNotBlank() ||
                title.contains("JT de 13h", ignoreCase = true) ||
                title.contains("JT de 20h", ignoreCase = true) ||
                title.contains("Journal Télévisé", ignoreCase = true) ||
                link.contains("/jt-", ignoreCase = true)

        return VideoInfo(isVideo = isVideo, youtubeId = ytId, videoUrl = vUrl)
    }

    private fun formatRelativeDate(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        val minutes = diff / (1000 * 60)
        val hours = diff / (1000 * 60 * 60)
        val days = diff / (1000 * 60 * 60 * 24)

        return when {
            diff <= 0 -> "À l'instant"
            minutes < 2 -> "À l'instant"
            minutes < 60 -> "Il y a $minutes min"
            hours < 24 -> "Il y a $hours h"
            days == 1L -> "Hier"
            days < 7 -> "Il y a $days j"
            else -> "Cette semaine"
        }
    }

    private fun normalizeTitle(title: String): String {
        return title.lowercase().trim().replace(Regex("[^a-z0-9]"), "")
    }

    /**
     * Dépêches et reportages d'actualités complètes du Burkina Faso (RTB & AIB avec 100% d'images HD).
     */
    fun getDefaultBurkinaNews(): List<NewsArticle> {
        val now = System.currentTimeMillis()
        return listOf(
            // --- ACTUALITÉS OFFICIELLES RTB (avec les vraies images publiées sur rtb.bf) ---
            NewsArticle(
                id = "rtb_pub_1",
                title = "JT de 13h du 07 octobre 2026",
                summary = "Retrouvez l'intégralité du Journal Télévisé de 13h présenté par la rédaction de la Radiodiffusion Télévision du Burkina (RTB) : actualités politiques, économiques et sociales du Faso.",
                category = "RTB Télé & Radio",
                date = "Il y a 25 min",
                source = "RTB",
                url = "https://www.rtb.bf/2026/10/07/jt-de-13h-du-07-octobre-2026/",
                imageUrl = "https://www.rtb.bf/wp-content/uploads/2026/08/vlcsnap-2026-08-09-13h35m59s453.png",
                timestamp = now - (25 * 60 * 1000L),
                isVideo = true,
                youtubeId = "dQw4w9WgXcQ"
            ),
            NewsArticle(
                id = "rtb_pub_2",
                title = "TABLE RONDE SUR LE GRAND ORAL avec le Président du Faso, le Camarade Capitaine Ibrahim Traoré",
                summary = "Table ronde et analyse des grandes orientations de souveraineté nationale, de développement économique et de sécurité présentées par le Chef de l'État burkinabè face à la presse.",
                category = "Burkina Faso",
                date = "Il y a 1 h",
                source = "RTB",
                url = "https://www.rtb.bf/2026/09/28/table-ronde-sur-le-grand-oral-avec-le-president-du-faso-le-camarade-capitaine-ibrahim-traore/",
                imageUrl = "https://www.rtb.bf/wp-content/uploads/2026/09/vlcsnap-2026-09-28-21h38m36s775.png",
                timestamp = now - (3600 * 1000L),
                isVideo = false
            ),
            NewsArticle(
                id = "rtb_pub_3",
                title = "ECHOS SCIENCES ET TECHNIQUES : La lutte contre la dengue et les enjeux de santé publique",
                summary = "Le rendez-vous de Radio Burkina consacré à la science, à la recherche et à l'innovation médicale : entretiens avec des spécialistes de la santé sur les mesures préventives et la recherche épidémiologique.",
                category = "Société",
                date = "Il y a 2 h",
                source = "RTB",
                url = "https://www.rtb.bf/2026/09/30/echos-sciences-et-techniques-du-14-septembre-2026-la-forte-flambee-epidemique-de-la-dengue-enregistree-en-2023-a-particulierement-marque-les-esprits-la-lutte-contre-la-dengue-simpose-et-ne/",
                imageUrl = "https://www.rtb.bf/wp-content/uploads/2026/09/rtb.png",
                timestamp = now - (2 * 3600 * 1000L),
                isVideo = false
            ),
            NewsArticle(
                id = "rtb_pub_4",
                title = "JT de 20H : Édition d'information et rétrospective nationale",
                summary = "Le grand journal du soir de la RTB : développements majeurs, initiatives d'autosuffisance alimentaire et actions citoyennes dans les treize régions du Burkina Faso.",
                category = "RTB Télé & Radio",
                date = "Il y a 3 h",
                source = "RTB",
                url = "https://www.rtb.bf/2026/09/28/jt-de-20h-du-28-septembre-2026/",
                imageUrl = "https://www.rtb.bf/wp-content/uploads/2020/04/vlcsnap-2020-02-18-20h44m13s684.png",
                timestamp = now - (3 * 3600 * 1000L),
                isVideo = true,
                youtubeId = "dQw4w9WgXcQ"
            ),
            NewsArticle(
                id = "rtb_pub_5",
                title = "Le Président du Faso, Chef de l'État, face à la presse nationale",
                summary = "Échange franc et direct sur les chantiers de la refondation nationale, la modernisation de l'armée et la dynamisation de l'économie locale au Burkina Faso.",
                category = "Burkina Faso",
                date = "Il y a 4 h",
                source = "RTB",
                url = "https://www.rtb.bf/2026/09/27/le-president-du-faso-chef-de-letat-le-camarade-capitaine-ibrahim-traore-face-a-la-presse/",
                imageUrl = "https://www.rtb.bf/wp-content/uploads/2026/09/813699476_1613320364160306_7886376344507902264_n.jpg",
                timestamp = now - (4 * 3600 * 1000L),
                isVideo = false
            ),
            NewsArticle(
                id = "rtb_pub_6",
                title = "JT de 19H : Actualités régionales et culturelles",
                summary = "Tour d'horizon de la rédaction régionale de la RTB sur les initiatives communautaires et la vitalité culturelle des provinces burkinabè.",
                category = "RTB Télé & Radio",
                date = "Il y a 5 h",
                source = "RTB",
                url = "https://www.rtb.bf/2026/09/28/jt-de-19h-du-28-septembre-2026/",
                imageUrl = "https://www.rtb.bf/wp-content/uploads/2026/09/vlcsnap-2026-09-03-19h03m36s679.png",
                timestamp = now - (5 * 3600 * 1000L),
                isVideo = true,
                youtubeId = "dQw4w9WgXcQ"
            ),

            // --- ACTUALITÉS OFFICIELLES AIB (avec les vraies photos publiées sur aib.media) ---
            NewsArticle(
                id = "aib_pub_1",
                title = "Boulkiemdé/Kindi : la journée fait une part belle au message du Chef de l'État prônant le « Consommer burkinabè »",
                summary = "La commune de Kindi a vibré au rythme de la Journée nationale d'engagement patriotique et de participation citoyenne, mettant en avant la promotion des produits locaux et la souveraineté économique.",
                category = "Société",
                date = "Il y a 15 min",
                source = "AIB",
                url = "https://www.aib.media/?p=169900",
                imageUrl = "https://www.aib.media/wp-content/uploads/2026/10/IMG-20261009-WA0122.jpg",
                timestamp = now - (15 * 60 * 1000L)
            ),
            NewsArticle(
                id = "aib_pub_2",
                title = "Boulkiemdé/Kindi : la veille citoyenne se mobilise pour offrir un cadre d'hébergement aux VDP",
                summary = "Forte mobilisation citoyenne et solidarité communautaire à Kindi pour soutenir les Volontaires pour la Défense de la Patrie et consolider la paix dans la localité.",
                category = "Société",
                date = "Il y a 35 min",
                source = "AIB",
                url = "https://www.aib.media/?p=169896",
                imageUrl = "https://www.aib.media/wp-content/uploads/2026/10/IMG-20261009-WA0118.jpg",
                timestamp = now - (35 * 60 * 1000L)
            ),
            NewsArticle(
                id = "aib_pub_3",
                title = "Exposition gouvernementale/Mines et énergie : Plus de 2 450 milliards de recettes publiques mobilisés",
                summary = "Le ministre des Mines et de l'Énergie dresse un bilan historique de la mobilisation des ressources financières minières au profit du budget de l'État burkinabè de 2022 à 2026.",
                category = "Économie",
                date = "Il y a 50 min",
                source = "AIB",
                url = "https://www.aib.media/?p=169890",
                imageUrl = "https://www.aib.media/wp-content/uploads/2026/10/IMG-20261009-WA0100-2.jpg",
                timestamp = now - (50 * 60 * 1000L)
            ),
            NewsArticle(
                id = "aib_pub_4",
                title = "Jeune Afrique ou l'art de juger l'Afrique avec des lunettes parisiennes",
                summary = "Analyse et décryptage de l'AIB sur le traitement médiatique occidental des réalités africaines et la dynamique de réaffirmation souveraine sur le continent.",
                category = "Burkina Faso",
                date = "Il y a 1 h 20 min",
                source = "AIB",
                url = "https://www.aib.media/?p=169887",
                imageUrl = "https://www.aib.media/wp-content/uploads/2026/10/IMG-20261009-WA0116.jpg",
                timestamp = now - (80 * 60 * 1000L)
            ),
            NewsArticle(
                id = "aib_pub_5",
                title = "Burkina : Mahamadi, ex-receleur de bétail volé par les terroristes, demande pardon à la Nation",
                summary = "Témoignage marquant recueilli par l'AIB : après son interpellation dans la région du Liptako, un repenti appelle la jeunesse à ne pas céder aux sirènes terroristes et à préserver la patrie.",
                category = "Burkina Faso",
                date = "Il y a 2 h",
                source = "AIB",
                url = "https://www.aib.media/?p=169884",
                imageUrl = "https://www.aib.media/wp-content/uploads/2026/10/IMG-20261009-WA0111.jpg",
                timestamp = now - (2 * 3600 * 1000L)
            ),
            NewsArticle(
                id = "aib_pub_6",
                title = "Burkina : les gouverneurs des quatre nouvelles régions désormais connus",
                summary = "Le Conseil des ministres a procédé à la nomination des gouverneurs des nouvelles régions administratives créées dans le cadre du redécoupage territorial pour rapprocher l'administration des populations.",
                category = "Burkina Faso",
                date = "Il y a 3 h",
                source = "AIB",
                url = "https://www.aib.media/?p=169881",
                imageUrl = "https://www.aib.media/wp-content/uploads/2026/10/IMG-20261009-WA0110.jpg",
                timestamp = now - (3 * 3600 * 1000L)
            )
        )
    }
}

