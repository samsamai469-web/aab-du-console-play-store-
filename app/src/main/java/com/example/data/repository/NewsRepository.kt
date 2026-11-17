package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.util.Xml
import com.example.BuildConfig
import com.example.data.local.NewsArticle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
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
    private const val PREFS_NAME = "sam_news_cache_v2"
    private const val KEY_CACHED_ARTICLES = "cached_aib_rtb_articles"
    private const val KEY_LAST_FETCH_SUCCESS = "last_fetch_success_ts"

    // Fenêtre stricte de rétention : 24 heures maximum pour éviter l'accumulation d'anciennes actualités
    const val RETENTION_WINDOW_MS = 24L * 60L * 60L * 1000L
    private const val MAX_DISPLAYED_ARTICLES = 30
    private const val RTB_YOUTUBE_CHANNEL_ID = "UCZl9utbYlPMssMhgrGUqXZA"

    private val jsonSerializer = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Volatile
    private var appPrefs: SharedPreferences? = null

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

    @Volatile
    private var cachedArticles: List<NewsArticle> = emptyList()

    @Volatile
    var lastFetchHadNetworkSuccess: Boolean = false
        private set

    fun initContext(context: Context) {
        if (appPrefs != null) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        appPrefs = prefs
        loadCachedArticlesFromDisk()
    }

    private fun loadCachedArticlesFromDisk() {
        val raw = appPrefs?.getString(KEY_CACHED_ARTICLES, null) ?: return
        try {
            val decoded = jsonSerializer.decodeFromString<List<NewsArticle>>(raw)
            val now = System.currentTimeMillis()
            val cutoff = now - RETENTION_WINDOW_MS
            val freshOnly = decoded
                .filter { isAllowedAibOrRtbSource(it.source) && it.timestamp in cutoff..now }
                .sortedByDescending { it.timestamp }
                .take(MAX_DISPLAYED_ARTICLES)
                .map { it.copy(date = formatRelativeDate(it.timestamp)) }
            cachedArticles = freshOnly
            if (freshOnly.size != decoded.size) {
                saveCachedArticlesToDisk(freshOnly)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Resetting unreadable news cache: ${e.message}")
            appPrefs?.edit()?.remove(KEY_CACHED_ARTICLES)?.apply()
        }
    }

    private fun saveCachedArticlesToDisk(articles: List<NewsArticle>) {
        try {
            val encoded = jsonSerializer.encodeToString(articles)
            appPrefs?.edit()
                ?.putString(KEY_CACHED_ARTICLES, encoded)
                ?.putLong(KEY_LAST_FETCH_SUCCESS, System.currentTimeMillis())
                ?.apply()
        } catch (e: Exception) {
            Log.w(TAG, "Could not persist news cache: ${e.message}")
        }
    }

    fun getCachedFreshNews(): List<NewsArticle> {
        val now = System.currentTimeMillis()
        val cutoff = now - RETENTION_WINDOW_MS
        val fresh = cachedArticles
            .filter { isAllowedAibOrRtbSource(it.source) && it.timestamp >= cutoff }
            .sortedByDescending { it.timestamp }
            .take(MAX_DISPLAYED_ARTICLES)
            .map { it.copy(date = formatRelativeDate(it.timestamp)) }
        cachedArticles = fresh
        return fresh
    }

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
    }

    fun updateCustomMetaConfig(token: String, pageOrUrl: String) {
        updateCustomMetaConfig(appId = "", token = token, aibTarget = pageOrUrl, rtbTarget = "RTBOfficiel")
    }

    fun getCustomAppId(): String = customAppId
    fun getCustomMetaToken(): String = customMetaToken
    fun getCustomMetaTarget(): String = customMetaTarget
    fun getCustomAibTarget(): String = customAibTarget
    fun getCustomRtbTarget(): String = customRtbTarget

    suspend fun testMetaConnection(
        appId: String,
        token: String,
        aibTarget: String,
        rtbTarget: String
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val cleanToken = token.trim()
        val cleanAib = if (aibTarget.isNotBlank()) extractPageIdentifier(aibTarget) else "248050315307820"
        val cleanRtb = if (rtbTarget.isNotBlank()) extractPageIdentifier(rtbTarget) else "RTBOfficiel"

        if (cleanToken.isBlank()) {
            return@withContext Pair(false, "Veuillez entrer un Jeton d'accès (Token Facebook).")
        }

        try {
            val testUrl = "https://graph.facebook.com/v19.0/$cleanAib?fields=id,name&access_token=$cleanToken"
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(testUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6000
                    readTimeout = 6000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "SamTraduction/1.0 (Android)")
                }
                if (connection.responseCode in 200..299) {
                    return@withContext Pair(true, "✅ Connexion Meta établie pour AIB ($cleanAib) et RTB ($cleanRtb).")
                }
            } finally {
                connection?.disconnect()
            }
            Pair(false, "Le jeton Meta n'a pas permis d'accéder à la page AIB ($cleanAib).")
        } catch (e: Exception) {
            Pair(false, "Échec du test de connexion : ${e.localizedMessage}")
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

    // Flux officiels AIB (Agence d'Information du Burkina) — chaque rubrique pointe vers sa vraie URL
    val AIB_SOURCES = listOf(
        RssSource(
            name = "AIB",
            feedUrls = listOf(
                "https://www.aib.media/feed/",
                "https://www.aib.media/?feed=rss2"
            ),
            defaultCategory = "Burkina Faso",
            siteUrl = "https://www.aib.media"
        ),
        RssSource(
            name = "AIB",
            feedUrls = listOf("https://www.aib.media/category/societe/feed/"),
            defaultCategory = "Société",
            siteUrl = "https://www.aib.media"
        ),
        RssSource(
            name = "AIB",
            feedUrls = listOf("https://www.aib.media/category/economie/feed/"),
            defaultCategory = "Économie",
            siteUrl = "https://www.aib.media"
        ),
        RssSource(
            name = "AIB",
            feedUrls = listOf("https://www.aib.media/category/culture/feed/"),
            defaultCategory = "Culture & Arts",
            siteUrl = "https://www.aib.media"
        ),
        RssSource(
            name = "AIB",
            feedUrls = listOf("https://www.aib.media/category/sport/feed/"),
            defaultCategory = "Sport",
            siteUrl = "https://www.aib.media"
        )
    )

    // Flux officiels RTB (Radiodiffusion Télévision du Burkina)
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
            name = "RTB",
            feedUrls = listOf(
                "https://www.rtb.bf/category/actualites/feed/",
                "https://rtb.bf/category/actualites/feed/"
            ),
            defaultCategory = "Burkina Faso",
            siteUrl = "https://www.rtb.bf"
        ),
        RssSource(
            name = "RTB",
            feedUrls = listOf("https://www.rtb.bf/category/societe/feed/"),
            defaultCategory = "Société",
            siteUrl = "https://www.rtb.bf"
        ),
        RssSource(
            name = "RTB",
            feedUrls = listOf("https://www.rtb.bf/category/economie/feed/"),
            defaultCategory = "Économie",
            siteUrl = "https://www.rtb.bf"
        ),
        RssSource(
            name = "RTB",
            feedUrls = listOf("https://www.rtb.bf/category/culture/feed/"),
            defaultCategory = "Culture & Arts",
            siteUrl = "https://www.rtb.bf"
        ),
        RssSource(
            name = "RTB",
            feedUrls = listOf("https://www.rtb.bf/category/sport/feed/"),
            defaultCategory = "Sport",
            siteUrl = "https://www.rtb.bf"
        ),
        RssSource(
            name = "RTB",
            feedUrls = listOf(
                "https://www.rtb.bf/category/journaux-televises/feed/",
                "https://www.rtb.bf/category/reportages/feed/"
            ),
            defaultCategory = "RTB Télé & Radio",
            siteUrl = "https://www.rtb.bf"
        )
    )

    private fun isAllowedAibOrRtbSource(source: String): Boolean {
        return source.contains("AIB", ignoreCase = true) ||
            source.contains("RTB", ignoreCase = true)
    }

    /**
     * Récupère exclusivement les actualités en direct de l'AIB et de la RTB publiées
     * au cours des dernières 24 heures. Les articles de plus de 24h sont supprimés automatiquement.
     */
    suspend fun getLiveNews(forceRefresh: Boolean = false): List<NewsArticle> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cutoffRetention = now - RETENTION_WINDOW_MS

        // Purge stricte de toute actualité dépassant 24h dans le cache mémoire
        cachedArticles = cachedArticles
            .filter { isAllowedAibOrRtbSource(it.source) && it.timestamp >= cutoffRetention }
            .map { it.copy(date = formatRelativeDate(it.timestamp)) }

        if (!forceRefresh && cachedArticles.isNotEmpty()) {
            return@withContext cachedArticles.sortedByDescending { it.timestamp }
        }

        val allFetched = mutableListOf<NewsArticle>()
        var anySourceSucceeded = false

        try {
            supervisorScope {
                val metaDeferred = async {
                    withTimeoutOrNull(8000L) { fetchFromMetaGraphApi() } ?: emptyList()
                }
                val rtbApiDeferred = async {
                    withTimeoutOrNull(12000L) { fetchFromRtbRestApi() } ?: emptyList()
                }
                val aibApiDeferred = async {
                    withTimeoutOrNull(12000L) { fetchFromWpRestApi() } ?: emptyList()
                }
                val rtbYtAtomDeferred = async {
                    withTimeoutOrNull(8000L) { fetchFromRtbYouTubeAtomFeed() } ?: emptyList()
                }
                val ytApiDeferred = async {
                    withTimeoutOrNull(7000L) { fetchFromYouTubeApi() } ?: emptyList()
                }
                val googleAibMirrorDeferred = async {
                    withTimeoutOrNull(7000L) { fetchGoogleMirrorForSource("site:aib.media", "AIB", "aib") } ?: emptyList()
                }
                val googleRtbMirrorDeferred = async {
                    withTimeoutOrNull(7000L) { fetchGoogleMirrorForSource("site:rtb.bf", "RTB", "rtb") } ?: emptyList()
                }
                val rtbRssDeferredList = RTB_SOURCES.map { source ->
                    async {
                        withTimeoutOrNull(9000L) { fetchFromSource(source) } ?: emptyList()
                    }
                }
                val aibRssDeferredList = AIB_SOURCES.map { source ->
                    async {
                        withTimeoutOrNull(9000L) { fetchFromSource(source) } ?: emptyList()
                    }
                }

                // Priorité 1 : APIs directes AIB & RTB (articles complets + vraies images)
                val rtbResults = rtbApiDeferred.await()
                val aibResults = aibApiDeferred.await()
                val rtbRssResults = rtbRssDeferredList.awaitAll().flatten()
                val aibRssResults = aibRssDeferredList.awaitAll().flatten()
                val rtbYtAtomResults = rtbYtAtomDeferred.await()
                val ytApiResults = ytApiDeferred.await()
                val metaResults = metaDeferred.await()

                // Priorité 2 : Miroir Google News strictement limité à aib.media et rtb.bf
                val googleAibResults = googleAibMirrorDeferred.await()
                val googleRtbResults = googleRtbMirrorDeferred.await()

                allFetched.addAll(rtbResults)
                allFetched.addAll(aibResults)
                allFetched.addAll(rtbRssResults)
                allFetched.addAll(aibRssResults)
                allFetched.addAll(rtbYtAtomResults)
                allFetched.addAll(ytApiResults)
                allFetched.addAll(metaResults)
                allFetched.addAll(googleAibResults)
                allFetched.addAll(googleRtbResults)

                if (rtbResults.isNotEmpty() || aibResults.isNotEmpty() ||
                    rtbRssResults.isNotEmpty() || aibRssResults.isNotEmpty() ||
                    rtbYtAtomResults.isNotEmpty() || ytApiResults.isNotEmpty() ||
                    metaResults.isNotEmpty() || googleAibResults.isNotEmpty() || googleRtbResults.isNotEmpty()
                ) {
                    anySourceSucceeded = true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching AIB & RTB live feeds", e)
        }

        lastFetchHadNetworkSuccess = anySourceSucceeded

        val freshFetched = allFetched
            .filter {
                isAllowedAibOrRtbSource(it.source) &&
                    it.title.isNotBlank() &&
                    it.url.isNotBlank() &&
                    it.timestamp in cutoffRetention..(now + 60_000L)
            }
            .distinctBy { normalizeTitle(it.title) }
            .sortedByDescending { it.timestamp }
            .take(MAX_DISPLAYED_ARTICLES)
            .map { it.copy(date = formatRelativeDate(it.timestamp)) }

        val finalArticles = if (freshFetched.isNotEmpty()) {
            saveCachedArticlesToDisk(freshFetched)
            freshFetched
        } else {
            // Si hors-ligne ou erreur réseau temporaire, ne conserver que le cache encore valide (< 24h)
            getCachedFreshNews()
        }

        cachedArticles = finalArticles
        finalArticles
    }

    /**
     * Flux Atom officiel gratuit de la chaîne YouTube RTB (@rtburkina), sans clé API requise.
     */
    private fun fetchFromRtbYouTubeAtomFeed(): List<NewsArticle> {
        val articles = mutableListOf<NewsArticle>()
        val feedUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=$RTB_YOUTUBE_CHANNEL_ID"
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(feedUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 7000
                readTimeout = 7000
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) SamAI/2.0")
            }
            if (connection.responseCode !in 200..299) return emptyList()
            val xml = connection.inputStream.bufferedReader().use { it.readText() }
            if (xml.isBlank()) return emptyList()

            val now = System.currentTimeMillis()
            val cutoffRetention = now - RETENTION_WINDOW_MS

            val entryMatcher = Pattern.compile("<entry>(.*?)</entry>", Pattern.DOTALL).matcher(xml)
            while (entryMatcher.find()) {
                val entry = entryMatcher.group(1) ?: continue
                val videoId = extractRegexGroup(entry, "<yt:videoId>([^<]+)</yt:videoId>").trim()
                val title = cleanHtml(extractRegexGroup(entry, "<title[^>]*>(.*?)</title>"))
                val published = extractRegexGroup(entry, "<published>([^<]+)</published>").trim()
                val desc = cleanHtml(extractRegexGroup(entry, "<media:description[^>]*>(.*?)</media:description>"))
                val thumb = extractRegexGroup(entry, "<media:thumbnail[^>]+url=[\"']([^\"']+)[\"']")
                    .ifBlank { if (videoId.isNotBlank()) "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" else "" }

                if (videoId.isBlank() || title.isBlank()) continue

                val ts = parseIsoDateToMillis(published)
                if (ts < cutoffRetention) continue

                val watchUrl = "https://www.youtube.com/watch?v=$videoId"
                val summary = if (desc.isNotBlank()) {
                    if (desc.length > 220) desc.take(217) + "..." else desc
                } else {
                    "Journal télévisé ou reportage officiel diffusé sur la chaîne YouTube de la RTB."
                }

                articles.add(
                    NewsArticle(
                        id = "rtb_yt_$videoId",
                        title = title,
                        summary = summary,
                        category = "RTB Télé & Radio",
                        date = formatRelativeDate(ts),
                        source = "RTB (YouTube)",
                        url = watchUrl,
                        imageUrl = sanitizeImageUrl(thumb),
                        timestamp = ts,
                        isVideo = true,
                        youtubeId = videoId,
                        videoUrl = watchUrl,
                        fullContent = desc.ifBlank { summary }
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "RTB YouTube Atom feed error: ${e.message}")
        } finally {
            connection?.disconnect()
        }
        return articles
    }

    /**
     * Miroir Google News RSS strictement filtré sur AIB (aib.media) ou RTB (rtb.bf)
     * afin de garantir les dépêches des dernières 24h même si le serveur direct est lent.
     */
    private fun fetchGoogleMirrorForSource(
        siteQuery: String,
        canonicalSource: String,
        requiredKeyword: String
    ): List<NewsArticle> {
        val articles = mutableListOf<NewsArticle>()
        val encoded = URLEncoder.encode("$siteQuery when:1d", "UTF-8")
        val endpoint = "https://news.google.com/rss/search?q=$encoded&hl=fr&gl=BF&ceid=BF:fr"
        var connection: HttpURLConnection? = null

        try {
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) SamAI/2.0")
            }
            if (connection.responseCode !in 200..299) return emptyList()
            val xml = connection.inputStream.bufferedReader().use { it.readText() }
            if (xml.isBlank()) return emptyList()

            val now = System.currentTimeMillis()
            val cutoffRetention = now - RETENTION_WINDOW_MS

            val itemMatcher = Pattern.compile("<item>(.*?)</item>", Pattern.DOTALL).matcher(xml)
            while (itemMatcher.find()) {
                val item = itemMatcher.group(1) ?: continue
                val rawTitle = cleanHtml(extractRegexGroup(item, "<title[^>]*>(.*?)</title>"))
                val link = cleanHtml(extractRegexGroup(item, "<link[^>]*>(.*?)</link>"))
                val pubDate = extractRegexGroup(item, "<pubDate[^>]*>(.*?)</pubDate>").trim()
                val sourceTag = extractRegexGroup(item, "<source[^>]*>(.*?)</source>")
                val sourceUrlAttr = extractRegexGroup(item, "<source[^>]+url=[\"']([^\"']+)[\"']")

                // Vérifier strictement que la dépêche provient uniquement d'AIB ou de la RTB
                val sourceMatches = sourceTag.contains(requiredKeyword, ignoreCase = true) ||
                    sourceUrlAttr.contains(requiredKeyword, ignoreCase = true) ||
                    link.contains(requiredKeyword, ignoreCase = true) ||
                    rawTitle.contains(canonicalSource, ignoreCase = true)

                if (!sourceMatches || rawTitle.isBlank() || link.isBlank()) continue

                // Google News ajoute souvent " - AIB" ou " - RTB" à la fin du titre
                val cleanTitle = rawTitle
                    .replace(Regex("\\s*-\\s*(AIB|Agence d'Information du Burkina|RTB|Radiodiffusion Télévision du Burkina).*$", RegexOption.IGNORE_CASE), "")
                    .trim()
                    .ifBlank { rawTitle }

                val ts = parsePubDateToMillis(pubDate)
                if (ts < cutoffRetention) continue

                val category = inferCategory(cleanTitle, canonicalSource)
                val summary = if (canonicalSource == "RTB") {
                    "Information officielle publiée par la Radiodiffusion Télévision du Burkina (RTB)."
                } else {
                    "Dépêche officielle publiée par l'Agence d'Information du Burkina (AIB)."
                }

                articles.add(
                    NewsArticle(
                        id = "${requiredKeyword}_gn_${link.hashCode()}",
                        title = cleanTitle,
                        summary = summary,
                        category = category,
                        date = formatRelativeDate(ts),
                        source = canonicalSource,
                        url = link,
                        imageUrl = "",
                        timestamp = ts,
                        fullContent = "$cleanTitle\n\n$summary"
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Google News mirror ($canonicalSource) error: ${e.message}")
        } finally {
            connection?.disconnect()
        }
        return articles
    }

    /**
     * Interroge l'API Meta (Facebook Graph API) uniquement si un jeton d'accès valide est configuré.
     */
    private fun fetchFromMetaGraphApi(): List<NewsArticle> {
        if (customMetaToken.isBlank()) return emptyList()

        val articles = mutableListOf<NewsArticle>()
        val dynamicTargets = mutableListOf<Pair<String, String>>()

        if (customAibTarget.isNotBlank()) {
            dynamicTargets.add(Pair(customAibTarget, "AIB (Facebook)"))
        }
        if (customRtbTarget.isNotBlank() && customRtbTarget != customAibTarget) {
            dynamicTargets.add(Pair(customRtbTarget, "RTB (Facebook)"))
        }

        val now = System.currentTimeMillis()
        val cutoffRetention = now - RETENTION_WINDOW_MS

        for ((pageName, sourceLabel) in dynamicTargets) {
            val endpoint = "https://graph.facebook.com/v19.0/$pageName/posts?fields=id,message,created_time,full_picture,permalink_url&limit=15&access_token=$customMetaToken"
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                    instanceFollowRedirects = true
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "SamTraduction/1.0 (Android)")
                    setRequestProperty("Accept", "application/json")
                }

                if (connection.responseCode in 200..299) {
                    val jsonText = connection.inputStream.bufferedReader().use { it.readText() }
                    val dataArray = org.json.JSONObject(jsonText).optJSONArray("data") ?: continue

                    for (i in 0 until dataArray.length()) {
                        val item = dataArray.optJSONObject(i) ?: continue
                        val idStr = item.optString("id", "")
                        val message = item.optString("message", "").trim()
                        val createdTime = item.optString("created_time", "")
                        val fullPicture = sanitizeImageUrl(item.optString("full_picture", ""))
                        val permalinkUrl = item.optString("permalink_url", "https://www.facebook.com/$pageName")

                        if (message.isBlank()) continue

                        val parsedTimestamp = parseIsoDateToMillis(createdTime)
                        if (parsedTimestamp < cutoffRetention) continue

                        val lines = message.lines().filter { it.isNotBlank() }
                        val rawTitle = lines.firstOrNull() ?: message
                        val title = cleanHtml(if (rawTitle.length > 110) rawTitle.take(107) + "..." else rawTitle)
                        val summary = cleanHtml(if (message.length > 220) message.take(217) + "..." else message)
                        val isRtb = pageName.contains("RTB", ignoreCase = true)

                        articles.add(
                            NewsArticle(
                                id = "meta_${pageName}_${idStr.ifBlank { parsedTimestamp.toString() }}",
                                title = title,
                                summary = summary,
                                category = if (isRtb) "Burkina Faso" else "AIB Direct",
                                date = formatRelativeDate(parsedTimestamp),
                                source = sourceLabel,
                                url = permalinkUrl,
                                imageUrl = fullPicture,
                                timestamp = parsedTimestamp,
                                fullContent = message
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Meta Graph API check for $pageName: ${e.message}")
            } finally {
                connection?.disconnect()
            }
        }
        return articles
    }

    /**
     * API YouTube Data v3 (optionnelle, si une vraie YOUTUBE_API_KEY est configurée) sur la chaîne officielle RTB.
     */
    private fun fetchFromYouTubeApi(): List<NewsArticle> {
        val articles = mutableListOf<NewsArticle>()
        val apiKey = try {
            BuildConfig.YOUTUBE_API_KEY.trim().removeSurrounding("\"").removeSurrounding("'").trim()
        } catch (_: Throwable) {
            ""
        }
        if (apiKey.isBlank() || apiKey == "MY_YOUTUBE_API_KEY") {
            return emptyList()
        }

        var connection: HttpURLConnection? = null
        try {
            val endpoint = "https://www.googleapis.com/youtube/v3/search?part=snippet&channelId=$RTB_YOUTUBE_CHANNEL_ID&maxResults=10&order=date&type=video&key=$apiKey"
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "SamTraduction/1.0 (Android)")
                setRequestProperty("Accept", "application/json")
            }

            if (connection.responseCode in 200..299) {
                val jsonText = connection.inputStream.bufferedReader().use { it.readText() }
                val items = org.json.JSONObject(jsonText).optJSONArray("items") ?: return emptyList()
                val now = System.currentTimeMillis()
                val cutoffRetention = now - RETENTION_WINDOW_MS

                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val videoId = item.optJSONObject("id")?.optString("videoId", "") ?: ""
                    if (videoId.isBlank()) continue

                    val snippet = item.optJSONObject("snippet") ?: continue
                    val title = cleanHtml(snippet.optString("title", ""))
                    val description = cleanHtml(snippet.optString("description", ""))
                    val publishedAt = snippet.optString("publishedAt", "")
                    val parsedTimestamp = parseIsoDateToMillis(publishedAt)
                    if (parsedTimestamp < cutoffRetention || title.isBlank()) continue

                    val thumbnails = snippet.optJSONObject("thumbnails")
                    val highThumb = thumbnails?.optJSONObject("high")?.optString("url", "")
                        ?: thumbnails?.optJSONObject("medium")?.optString("url", "")
                        ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

                    articles.add(
                        NewsArticle(
                            id = "yt_$videoId",
                            title = title,
                            summary = description.ifBlank { "Édition et reportage vidéo diffusés par la RTB sur YouTube." },
                            category = "RTB Télé & Radio",
                            date = formatRelativeDate(parsedTimestamp),
                            source = "RTB (YouTube)",
                            url = "https://www.youtube.com/watch?v=$videoId",
                            imageUrl = sanitizeImageUrl(highThumb),
                            timestamp = parsedTimestamp,
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
        return articles
    }

    /**
     * API REST WordPress de la RTB — requêtes optimisées (per_page=15) pour éviter les timeouts mobile.
     */
    private fun fetchFromRtbRestApi(): List<NewsArticle> {
        val endpoints = listOf(
            "https://www.rtb.bf/wp-json/wp/v2/posts?_embed=wp:featuredmedia,wp:term&per_page=15",
            "https://www.rtb.bf/index.php?rest_route=/wp/v2/posts&_embed=wp:featuredmedia,wp:term&per_page=15",
            "https://rtb.bf/wp-json/wp/v2/posts?per_page=15"
        )
        return fetchWordPressPosts(
            endpoints = endpoints,
            sourceName = "RTB",
            idPrefix = "rtb_wp",
            defaultFallbackSummary = "Reportage et article d'information diffusé par la Radiodiffusion Télévision du Burkina (RTB)."
        )
    }

    /**
     * API REST WordPress de l'AIB — requêtes optimisées (per_page=15) pour éviter les timeouts mobile.
     */
    private fun fetchFromWpRestApi(): List<NewsArticle> {
        val endpoints = listOf(
            "https://www.aib.media/wp-json/wp/v2/posts?_embed=wp:featuredmedia,wp:term&per_page=15",
            "https://www.aib.media/index.php?rest_route=/wp/v2/posts&_embed=wp:featuredmedia,wp:term&per_page=15",
            "https://aib.media/wp-json/wp/v2/posts?per_page=15"
        )
        return fetchWordPressPosts(
            endpoints = endpoints,
            sourceName = "AIB",
            idPrefix = "aib_wp",
            defaultFallbackSummary = "Dépêche officielle transmise en direct par l'Agence d'Information du Burkina (AIB)."
        )
    }

    private fun fetchWordPressPosts(
        endpoints: List<String>,
        sourceName: String,
        idPrefix: String,
        defaultFallbackSummary: String
    ): List<NewsArticle> {
        val articles = mutableListOf<NewsArticle>()

        for (endpoint in endpoints) {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 9000
                    readTimeout = 10000
                    instanceFollowRedirects = true
                    requestMethod = "GET"
                    setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
                    )
                    setRequestProperty("Accept", "application/json, text/plain, */*")
                    setRequestProperty("Accept-Encoding", "gzip, deflate")
                    setRequestProperty("Cache-Control", "no-cache")
                }

                if (connection.responseCode !in 200..299) continue

                val encoding = connection.contentEncoding
                val rawStream = connection.inputStream
                val inputStream = if ("gzip".equals(encoding, ignoreCase = true)) {
                    GZIPInputStream(rawStream)
                } else {
                    rawStream
                }

                val jsonText = inputStream.bufferedReader().use { it.readText() }.trim()
                if (!jsonText.startsWith("[")) continue

                val jsonArray = org.json.JSONArray(jsonText)
                val now = System.currentTimeMillis()
                val cutoffRetention = now - RETENTION_WINDOW_MS

                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.optJSONObject(i) ?: continue
                    val idNum = item.optInt("id", 0)
                    val dateGmt = item.optString("date_gmt", "").ifBlank { item.optString("date", "") }
                    val parsedTimestamp = parseIsoDateToMillis(dateGmt)
                    if (parsedTimestamp < cutoffRetention) continue

                    val rawTitle = item.optJSONObject("title")?.optString("rendered", "") ?: ""
                    val title = cleanHtml(rawTitle)
                    val link = item.optString("link", "")
                    if (title.isBlank() || link.isBlank()) continue

                    val rawExcerpt = item.optJSONObject("excerpt")?.optString("rendered", "") ?: ""
                    val rawContent = item.optJSONObject("content")?.optString("rendered", "") ?: ""

                    val videoInfo = extractVideoInfo(rawContent, link, title)

                    var summary = cleanHtml(rawExcerpt)
                    if (summary.isBlank()) {
                        summary = cleanHtml(rawContent)
                    }
                    if (summary.length > 220) {
                        summary = summary.take(217) + "..."
                    }

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
                        imageUrl = "https://i.ytimg.com/vi/${videoInfo.youtubeId}/hqdefault.jpg"
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
                    if (sourceName == "RTB" && category == "Burkina Faso" && videoInfo.isVideo) {
                        category = "RTB Télé & Radio"
                    }

                    val cleanFullText = cleanHtmlWithParagraphs(rawContent).ifBlank { summary }

                    articles.add(
                        NewsArticle(
                            id = "${idPrefix}_${idNum}_${link.hashCode()}",
                            title = title,
                            summary = summary.ifBlank { defaultFallbackSummary },
                            category = category,
                            date = formatRelativeDate(parsedTimestamp),
                            source = sourceName,
                            url = link,
                            imageUrl = sanitizeImageUrl(imageUrl),
                            timestamp = parsedTimestamp,
                            videoUrl = videoInfo.videoUrl,
                            youtubeId = videoInfo.youtubeId,
                            isVideo = videoInfo.isVideo,
                            fullContent = cleanFullText
                        )
                    )
                }

                // Si le JSON était valide et a répondu (même si < 24h vide), pas besoin de retenter les autres endpoints du même site
                return articles
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching $sourceName WP REST ($endpoint): ${e.message}")
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
                        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
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

                    val bodyText = inputStream.bufferedReader().use { it.readText() }
                    if (bodyText.contains("<item", ignoreCase = true) || bodyText.contains("<entry", ignoreCase = true)) {
                        return parseRssXmlSafe(bodyText, source)
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
        return emptyList()
    }

    /**
     * Parseur XML tolérant avec repli par Regex si le XML du serveur contient des balises HTML imbriquées.
     */
    private fun parseRssXmlSafe(xmlText: String, source: RssSource): List<NewsArticle> {
        val list = mutableListOf<NewsArticle>()
        val nowTime = System.currentTimeMillis()
        val cutoffRetention = nowTime - RETENTION_WINDOW_MS

        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xmlText))

            var eventType = parser.eventType
            var insideItem = false

            var title = ""
            var link = ""
            var description = ""
            var rawFullContent = ""
            var pubDate = ""
            var category = source.defaultCategory
            var imageUrl = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name?.lowercase() ?: ""

                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (tagName == "item" || tagName == "entry") {
                            insideItem = true
                            title = ""
                            link = ""
                            description = ""
                            rawFullContent = ""
                            pubDate = ""
                            category = source.defaultCategory
                            imageUrl = ""
                        } else if (insideItem) {
                            when {
                                tagName == "title" -> title = cleanHtml(safeReadElementText(parser))
                                tagName == "link" -> {
                                    val rel = parser.getAttributeValue(null, "rel")
                                    val href = parser.getAttributeValue(null, "href")
                                    val textVal = safeReadElementText(parser).trim()
                                    if (!href.isNullOrBlank() && (rel.isNullOrBlank() || rel == "alternate")) {
                                        link = href
                                    } else if (link.isBlank() && textVal.startsWith("http")) {
                                        link = textVal
                                    }
                                }
                                tagName in listOf("description", "summary") -> {
                                    val raw = safeReadElementText(parser)
                                    if (imageUrl.isBlank()) imageUrl = extractImageFromHtml(raw)
                                    val cleaned = cleanHtml(raw)
                                    if (cleaned.isNotBlank()) description = cleaned
                                    if (rawFullContent.isBlank()) rawFullContent = raw
                                }
                                tagName == "content:encoded" || tagName == "content" -> {
                                    val raw = safeReadElementText(parser)
                                    if (imageUrl.isBlank()) imageUrl = extractImageFromHtml(raw)
                                    if (raw.isNotBlank()) rawFullContent = raw
                                    if (description.isBlank()) description = cleanHtml(raw)
                                }
                                tagName in listOf("pubdate", "published", "updated", "date", "dc:date") -> {
                                    if (pubDate.isBlank()) pubDate = safeReadElementText(parser).trim()
                                }
                                tagName == "category" -> {
                                    val cat = cleanHtml(safeReadElementText(parser))
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
                                    if (mediaUrl.isNotBlank() && imageUrl.isBlank()) {
                                        imageUrl = mediaUrl
                                    }
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (tagName == "item" || tagName == "entry") {
                            if (title.isNotBlank() && link.isNotBlank()) {
                                val parsedTimestamp = parsePubDateToMillis(pubDate)
                                if (parsedTimestamp >= cutoffRetention) {
                                    val cleanFull = cleanHtmlWithParagraphs(rawFullContent).ifBlank { description }
                                    val shortSummary = if (description.length > 220) {
                                        description.take(217) + "..."
                                    } else description
                                    val videoInfo = extractVideoInfo(rawFullContent.ifBlank { description }, link, title)
                                    val finalImage = sanitizeImageUrl(
                                        imageUrl.ifBlank {
                                            if (videoInfo.youtubeId.isNotBlank()) "https://i.ytimg.com/vi/${videoInfo.youtubeId}/hqdefault.jpg" else ""
                                        }
                                    )

                                    list.add(
                                        NewsArticle(
                                            id = "${source.name}_${link.hashCode()}",
                                            title = title,
                                            summary = shortSummary.ifBlank { "Consultez la dépêche complète." },
                                            category = category,
                                            date = formatRelativeDate(parsedTimestamp),
                                            source = source.name,
                                            url = link,
                                            imageUrl = finalImage,
                                            timestamp = parsedTimestamp,
                                            videoUrl = videoInfo.videoUrl,
                                            youtubeId = videoInfo.youtubeId,
                                            isVideo = videoInfo.isVideo,
                                            fullContent = cleanFull
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
            if (list.isNotEmpty()) return list
        } catch (e: Exception) {
            Log.w(TAG, "XML parser fallback for ${source.name}: ${e.message}")
        }

        // Repli Regex si le flux XML contient des erreurs de syntaxe
        val itemMatcher = Pattern.compile("<item[^>]*>(.*?)</item>", Pattern.DOTALL).matcher(xmlText)
        while (itemMatcher.find()) {
            val block = itemMatcher.group(1) ?: continue
            val title = cleanHtml(extractRegexGroup(block, "<title[^>]*>(.*?)</title>"))
            val link = cleanHtml(extractRegexGroup(block, "<link[^>]*>(.*?)</link>"))
            val pubDate = extractRegexGroup(block, "<(?:pubDate|dc:date)[^>]*>(.*?)</(?:pubDate|dc:date)>").trim()
            val rawDesc = extractRegexGroup(block, "<description[^>]*>(.*?)</description>")
            val rawContent = extractRegexGroup(block, "<content:encoded[^>]*>(.*?)</content:encoded>").ifBlank { rawDesc }

            if (title.isBlank() || link.isBlank()) continue
            val ts = parsePubDateToMillis(pubDate)
            if (ts < cutoffRetention) continue

            val descClean = cleanHtml(rawDesc)
            val fullClean = cleanHtmlWithParagraphs(rawContent).ifBlank { descClean }
            val shortSummary = if (descClean.length > 220) descClean.take(217) + "..." else descClean
            val img = sanitizeImageUrl(extractImageFromHtml(rawContent))
            val videoInfo = extractVideoInfo(rawContent, link, title)

            list.add(
                NewsArticle(
                    id = "${source.name}_${link.hashCode()}",
                    title = title,
                    summary = shortSummary.ifBlank { "Consultez la dépêche complète." },
                    category = source.defaultCategory,
                    date = formatRelativeDate(ts),
                    source = source.name,
                    url = link,
                    imageUrl = img,
                    timestamp = ts,
                    videoUrl = videoInfo.videoUrl,
                    youtubeId = videoInfo.youtubeId,
                    isVideo = videoInfo.isVideo,
                    fullContent = fullClean
                )
            )
        }
        return list
    }

    /**
     * Lit tout le texte d'une balise XML sans jamais planter même si des sous-balises HTML sont présentes.
     */
    private fun safeReadElementText(parser: XmlPullParser): String {
        return try {
            val sb = StringBuilder()
            var depth = 1
            while (depth > 0) {
                when (parser.next()) {
                    XmlPullParser.TEXT, XmlPullParser.CDSECT -> sb.append(parser.text ?: "")
                    XmlPullParser.START_TAG -> depth++
                    XmlPullParser.END_TAG -> depth--
                    XmlPullParser.END_DOCUMENT -> break
                }
            }
            sb.toString()
        } catch (_: Exception) {
            ""
        }
    }

    private fun extractRegexGroup(input: String, patternStr: String): String {
        val m = Pattern.compile(patternStr, Pattern.DOTALL or Pattern.CASE_INSENSITIVE).matcher(input)
        return if (m.find()) {
            (m.group(1) ?: "")
                .replace(Regex("<!\\[CDATA\\[(.*?)\\]\\]>", RegexOption.DOT_MATCHES_ALL)) { it.groupValues[1] }
                .trim()
        } else {
            ""
        }
    }

    private fun sanitizeImageUrl(url: String): String {
        val cleaned = url.trim()
        if (cleaned.isBlank()) return ""
        return if (cleaned.startsWith("http://", ignoreCase = true)) {
            "https://" + cleaned.removePrefix("http://").removePrefix("HTTP://")
        } else {
            cleaned
        }
    }

    private fun inferCategory(title: String, source: String): String {
        val lower = title.lowercase(Locale.FRENCH)
        return when {
            lower.contains("jt ") || lower.contains("journal télévisé") || lower.contains("13h") || lower.contains("20h") ->
                if (source.contains("RTB", ignoreCase = true)) "RTB Télé & Radio" else "Burkina Faso"
            lower.contains("sport") || lower.contains("étalon") || lower.contains("football") || lower.contains("cyclis") || lower.contains("tour du faso") -> "Sport"
            lower.contains("écono") || lower.contains("budget") || lower.contains("mine") || lower.contains("coton") || lower.contains("franc cfa") || lower.contains("recettes") -> "Économie"
            lower.contains("cultur") || lower.contains("siao") || lower.contains("fespaco") || lower.contains("art") || lower.contains("musique") || lower.contains("ciné") -> "Culture & Arts"
            lower.contains("santé") || lower.contains("école") || lower.contains("université") || lower.contains("social") || lower.contains("citoyen") -> "Société"
            else -> "Burkina Faso"
        }
    }

    private fun cleanHtmlWithParagraphs(html: String): String {
        if (html.isBlank()) return ""
        val withBreaks = html
            .replace(Regex("</p\\s*>", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</li\\s*>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<li[^>]*>", RegexOption.IGNORE_CASE), "• ")
        return decodeHtmlEntities(
            withBreaks
                .replace(Regex("<style[^>]*>[\\s\\S]*?</style>"), "")
                .replace(Regex("<script[^>]*>[\\s\\S]*?</script>"), "")
                .replace(Regex("<[^>]*>"), " ")
        )
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    private fun cleanHtml(html: String): String {
        if (html.isBlank()) return ""
        return decodeHtmlEntities(
            html
                .replace(Regex("<!\\[CDATA\\[(.*?)\\]\\]>", RegexOption.DOT_MATCHES_ALL)) { it.groupValues[1] }
                .replace(Regex("<style[^>]*>[\\s\\S]*?</style>"), "")
                .replace(Regex("<script[^>]*>[\\s\\S]*?</script>"), "")
                .replace(Regex("<[^>]*>"), " ")
        )
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun decodeHtmlEntities(text: String): String {
        var decoded = text
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&#8217;", "'")
            .replace("&#8216;", "'")
            .replace("&#8220;", "«")
            .replace("&#8221;", "»")
            .replace("&#8211;", "-")
            .replace("&#8212;", "—")
            .replace("&laquo;", "«")
            .replace("&raquo;", "»")
            .replace("&rsquo;", "'")
            .replace("&lsquo;", "'")
            .replace("&ldquo;", "«")
            .replace("&rdquo;", "»")
            .replace("&hellip;", "...")
            .replace("&eacute;", "é")
            .replace("&egrave;", "è")
            .replace("&ecirc;", "ê")
            .replace("&agrave;", "à")
            .replace("&acirc;", "â")
            .replace("&ccedil;", "ç")
            .replace("&ocirc;", "ô")
            .replace("&ucirc;", "û")
            .replace("&ugrave;", "ù")
            .replace("&icirc;", "î")
            .replace("&iuml;", "ï")
            .replace("&Eacute;", "É")
            .replace("&Agrave;", "À")
            .replace("&Ccedil;", "Ç")

        decoded = decoded.replace(Regex("&#(\\d+);")) { match ->
            try {
                match.groupValues[1].toInt().toChar().toString()
            } catch (_: Throwable) {
                match.value
            }
        }
        decoded = decoded.replace(Regex("&#x([0-9a-fA-F]+);")) { match ->
            try {
                match.groupValues[1].toInt(16).toChar().toString()
            } catch (_: Throwable) {
                match.value
            }
        }
        return decoded
    }

    private fun extractImageFromHtml(html: String): String {
        val matcher = Pattern.compile("<img[^>]+src=[\"']([^\"'>]+)[\"']").matcher(html)
        if (matcher.find()) {
            val src = matcher.group(1) ?: ""
            if (src.startsWith("http")) return sanitizeImageUrl(src)
        }
        return ""
    }

    private fun formatCategory(cat: String): String {
        val lower = cat.lowercase()
        return when {
            lower.contains("sport") -> "Sport"
            lower.contains("éco") || lower.contains("eco") || lower.contains("finan") -> "Économie"
            lower.contains("cultur") || lower.contains("art") || lower.contains("ciné") || lower.contains("fespaco") || lower.contains("siao") -> "Culture & Arts"
            lower.contains("polit") || lower.contains("gouv") || lower.contains("défense") || lower.contains("secur") || lower.contains("burkina") || lower.contains("actu") -> "Burkina Faso"
            else -> "Société"
        }
    }

    /**
     * Retourne 0L si la date est absente ou illisible (ne jamais faire passer un vieil article pour « À l'instant »).
     */
    private fun parseIsoDateToMillis(dateStr: String): Long {
        if (dateStr.isBlank()) return 0L
        val cleaned = dateStr.trim()
        val formats = listOf(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
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
        if (pubDateStr.isBlank()) return 0L
        val cleaned = pubDateStr.trim()

        val formats = listOf(
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US),
            SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z", Locale.US),
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US),
            SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss z", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
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
                    return if (time > now) now else time
                }
            } catch (_: Exception) {}
        }
        return 0L
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
            if (ytId.isBlank()) {
                val mp4Matcher = Pattern.compile("https?://[^\"'\\s<>]+\\.mp4").matcher(content)
                if (mp4Matcher.find()) {
                    vUrl = mp4Matcher.group() ?: ""
                }
            }
        }

        if (ytId.isBlank() && link.isNotBlank()) {
            val ytLinkMatcher = Pattern.compile("https?://(?:www\\.)?(?:youtube\\.com/watch\\?v=|youtube\\.com/live/|youtu\\.be/)([a-zA-Z0-9_\\-]{11})").matcher(link)
            if (ytLinkMatcher.find()) {
                ytId = ytLinkMatcher.group(1) ?: ""
            }
        }

        if (ytId.isNotBlank()) {
            vUrl = "https://www.youtube.com/watch?v=$ytId"
        }

        val isVideo = ytId.isNotBlank() || vUrl.isNotBlank()

        return VideoInfo(isVideo = isVideo, youtubeId = ytId, videoUrl = vUrl)
    }

    private fun formatRelativeDate(timestamp: Long): String {
        if (timestamp <= 0L) return "Récent"
        val diff = System.currentTimeMillis() - timestamp
        val minutes = diff / (1000 * 60)
        val hours = diff / (1000 * 60 * 60)

        return when {
            diff <= 0 -> "À l'instant"
            minutes < 2 -> "À l'instant"
            minutes < 60 -> "Il y a $minutes min"
            hours < 24 -> "Il y a $hours h"
            else -> "Il y a 24 h"
        }
    }

    private fun normalizeTitle(title: String): String {
        return title.lowercase().trim().replace(Regex("[^a-z0-9]"), "")
    }

    /**
     * Aucun faux article codé en dur : ne retourne que les vraies actualités AIB & RTB en cache (< 24h).
     */
    fun getDefaultBurkinaNews(): List<NewsArticle> = getCachedFreshNews()
}
