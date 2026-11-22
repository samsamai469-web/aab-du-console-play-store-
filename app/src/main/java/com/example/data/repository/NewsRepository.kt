package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.util.Xml
import com.example.BuildConfig
import com.example.data.local.NewsArticle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

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

    // Fenêtre principale de rétention (24h) avec repli étendu (7 jours) par média si un flux n'a rien publié dans les 24h
    const val RETENTION_WINDOW_MS = 24L * 60L * 60L * 1000L
    const val EXTENDED_RETENTION_WINDOW_MS = 7L * 24L * 60L * 60L * 1000L
    private const val FUTURE_CLOCK_SKEW_MS = 2L * 60L * 60L * 1000L
    private const val MAX_DISPLAYED_ARTICLES = 40
    private const val RTB_YOUTUBE_CHANNEL_ID = "UCZl9utbYlPMssMhgrGUqXZA"
    private const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
    private val refreshLock = Any()

    private val ipv4PreferredDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val all = Dns.SYSTEM.lookup(hostname)
            val v4 = all.filterIsInstance<Inet4Address>()
            val v6 = all.filterIsInstance<Inet6Address>()
            return if (v4.isNotEmpty()) v4 + v6 else all
        }
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(ipv4PreferredDns)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .connectTimeout(7, TimeUnit.SECONDS)
            .readTimeout(9, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    @Volatile
    private var inFlightRefresh: CompletableDeferred<List<NewsArticle>>? = null

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
            val freshOnly = applySourceAwareRetention(decoded)
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
        val fresh = applySourceAwareRetention(cachedArticles)
        cachedArticles = fresh
        return fresh
    }

    private fun executeGetText(
        url: String,
        acceptHeader: String = "text/html,application/xhtml+xml,application/xml;q=0.9,application/json;q=0.8,*/*;q=0.7"
    ): String? {
        return try {
            val req = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", BROWSER_USER_AGENT)
                .header("Accept", acceptHeader)
                .header("Accept-Language", "fr-FR,fr;q=0.9,en;q=0.8")
                .header("Cache-Control", "no-cache")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "HTTP ${resp.code} on $url")
                    return@use null
                }
                val text = resp.body?.string()?.removePrefix("\uFEFF")?.trim().orEmpty()
                text.ifBlank { null }
            }
        } catch (e: Exception) {
            Log.w(TAG, "HTTP GET error ($url): ${e.message}")
            null
        }
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
            val body = executeGetText(testUrl, "application/json")
            if (!body.isNullOrBlank()) {
                return@withContext Pair(true, "✅ Connexion Meta établie pour AIB ($cleanAib) et RTB ($cleanRtb).")
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

    // Flux officiels AIB (Agence d'Information du Burkina) — couverture étendue en parallèle (aib.media & aib.bf)
    val AIB_SOURCES = listOf(
        RssSource(
            name = "AIB",
            feedUrls = listOf(
                "https://www.aib.media/feed/",
                "https://aib.media/feed/",
                "https://www.aib.media/?feed=rss2"
            ),
            defaultCategory = "Burkina Faso",
            siteUrl = "https://www.aib.media"
        ),
        RssSource(
            name = "AIB",
            feedUrls = listOf(
                "https://www.aib.bf/feed/",
                "https://aib.bf/feed/",
                "https://www.aib.bf/?feed=rss2"
            ),
            defaultCategory = "Burkina Faso",
            siteUrl = "https://www.aib.bf"
        ),
        RssSource(
            name = "AIB",
            feedUrls = listOf(
                "https://www.aib.media/category/politique/feed/",
                "https://www.aib.bf/category/politique/feed/"
            ),
            defaultCategory = "Burkina Faso",
            siteUrl = "https://www.aib.media"
        ),
        RssSource(
            name = "AIB",
            feedUrls = listOf(
                "https://www.aib.media/category/nation/feed/",
                "https://www.aib.media/category/burkina-faso/feed/",
                "https://www.aib.media/category/a-la-une/feed/",
                "https://www.aib.bf/category/nation/feed/",
                "https://www.aib.bf/category/a-la-une/feed/"
            ),
            defaultCategory = "Burkina Faso",
            siteUrl = "https://www.aib.media"
        ),
        RssSource(
            name = "AIB",
            feedUrls = listOf(
                "https://www.aib.media/category/regions/feed/",
                "https://www.aib.media/category/securite/feed/",
                "https://www.aib.bf/category/regions/feed/"
            ),
            defaultCategory = "Burkina Faso",
            siteUrl = "https://www.aib.media"
        ),
        RssSource(
            name = "AIB",
            feedUrls = listOf(
                "https://www.aib.media/category/societe/feed/",
                "https://www.aib.media/category/sante/feed/",
                "https://www.aib.media/category/education/feed/",
                "https://www.aib.bf/category/societe/feed/"
            ),
            defaultCategory = "Société",
            siteUrl = "https://www.aib.media"
        ),
        RssSource(
            name = "AIB",
            feedUrls = listOf(
                "https://www.aib.media/category/economie/feed/",
                "https://www.aib.bf/category/economie/feed/"
            ),
            defaultCategory = "Économie",
            siteUrl = "https://www.aib.media"
        ),
        RssSource(
            name = "AIB",
            feedUrls = listOf(
                "https://www.aib.media/category/culture/feed/",
                "https://www.aib.bf/category/culture/feed/"
            ),
            defaultCategory = "Culture & Arts",
            siteUrl = "https://www.aib.media"
        ),
        RssSource(
            name = "AIB",
            feedUrls = listOf(
                "https://www.aib.media/category/sport/feed/",
                "https://www.aib.bf/category/sport/feed/"
            ),
            defaultCategory = "Sport",
            siteUrl = "https://www.aib.media"
        ),
        RssSource(
            name = "AIB",
            feedUrls = listOf(
                "https://www.aib.media/category/international/feed/",
                "https://www.aib.bf/category/international/feed/"
            ),
            defaultCategory = "Burkina Faso",
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
     * Équilibre équitablement les actualités AIB et RTB dans la limite de [maxTotal] articles,
     * en garantissant une forte visibilité à AIB face aux vidéos fréquentes de la RTB,
     * et en alternant les sources récentes en tête de liste.
     */
    private fun balanceAibAndRtbArticles(
        articles: List<NewsArticle>,
        maxTotal: Int = MAX_DISPLAYED_ARTICLES
    ): List<NewsArticle> {
        if (articles.isEmpty()) return emptyList()

        val sorted = articles.sortedByDescending { it.timestamp }
        val aibAll = sorted.filter { it.source.contains("AIB", ignoreCase = true) }
        val rtbAll = sorted.filter { it.source.contains("RTB", ignoreCase = true) }

        if (aibAll.isEmpty() || rtbAll.isEmpty()) {
            return sorted.take(maxTotal)
        }

        val halfQuota = maxTotal / 2 // 20 AIB + 20 RTB par défaut

        // Limiter les vidéos YouTube RTB à la moitié du quota RTB tant que des articles écrits existent
        val rtbWritten = rtbAll.filter { !it.isVideo }
        val rtbVideo = rtbAll.filter { it.isVideo }
        val maxRtbVideoPreferred = if (rtbWritten.isNotEmpty()) 8 else halfQuota
        val prioritizedRtb = (rtbWritten + rtbVideo.take(maxRtbVideoPreferred) + rtbVideo.drop(maxRtbVideoPreferred))
            .distinctBy { it.id }

        var aibTarget = halfQuota
        var rtbTarget = halfQuota

        if (aibAll.size < halfQuota) {
            aibTarget = aibAll.size
            rtbTarget = (maxTotal - aibTarget).coerceAtMost(prioritizedRtb.size)
        } else if (prioritizedRtb.size < halfQuota) {
            rtbTarget = prioritizedRtb.size
            aibTarget = (maxTotal - rtbTarget).coerceAtMost(aibAll.size)
        }

        val selectedAib = aibAll.take(aibTarget).sortedByDescending { it.timestamp }
        val selectedRtb = prioritizedRtb.take(rtbTarget).sortedByDescending { it.timestamp }

        // Alternance équilibrée (AIB / RTB) tout en respectant l'ordre chronologique dans chaque source
        val interleaved = mutableListOf<NewsArticle>()
        var aIdx = 0
        var rIdx = 0
        var pickAibNext = (selectedAib.firstOrNull()?.timestamp ?: 0L) >= (selectedRtb.firstOrNull()?.timestamp ?: 0L)
        while (aIdx < selectedAib.size || rIdx < selectedRtb.size) {
            if (pickAibNext && aIdx < selectedAib.size) {
                interleaved.add(selectedAib[aIdx++])
            } else if (!pickAibNext && rIdx < selectedRtb.size) {
                interleaved.add(selectedRtb[rIdx++])
            } else if (aIdx < selectedAib.size) {
                interleaved.add(selectedAib[aIdx++])
            } else if (rIdx < selectedRtb.size) {
                interleaved.add(selectedRtb[rIdx++])
            }
            pickAibNext = !pickAibNext
        }

        return interleaved.take(maxTotal)
    }

    /**
     * Applique une rétention intelligente par média :
     * - Priorité absolue aux actualités des dernières 24h pour chaque source (AIB et RTB).
     * - Si une source (ex: AIB) compte moins de 3 dépêches sur les dernières 24h, conserve ses dépêches les plus récentes jusqu'à 7 jours
     *   afin qu'« AIB Direct » ne soit jamais vide lorsqu'AIB publie avec un léger décalage.
     */
    private fun applySourceAwareRetention(
        articles: List<NewsArticle>,
        now: Long = System.currentTimeMillis()
    ): List<NewsArticle> {
        val cutoffPreferred = now - RETENTION_WINDOW_MS
        val cutoffExtended = now - EXTENDED_RETENTION_WINDOW_MS
        val maxAllowedFuture = now + FUTURE_CLOCK_SKEW_MS

        val validPool = articles
            .filter {
                isAllowedAibOrRtbSource(it.source) &&
                    it.title.isNotBlank() &&
                    it.url.isNotBlank() &&
                    it.timestamp in cutoffExtended..maxAllowedFuture
            }
            .distinctBy { normalizeTitle(it.title) }
            .map { it.copy(date = formatRelativeDate(it.timestamp)) }

        if (validPool.isEmpty()) return emptyList()

        val aibAll = validPool.filter { it.source.contains("AIB", ignoreCase = true) }
            .sortedByDescending { it.timestamp }
        val rtbAll = validPool.filter { it.source.contains("RTB", ignoreCase = true) }
            .sortedByDescending { it.timestamp }

        val aib24h = aibAll.filter { it.timestamp >= cutoffPreferred }
        val rtb24h = rtbAll.filter { it.timestamp >= cutoffPreferred }

        val selectedAib = if (aib24h.size >= 3) {
            aib24h
        } else {
            (aib24h + aibAll).distinctBy { normalizeTitle(it.title) }.take(20)
        }

        val selectedRtb = if (rtb24h.size >= 3) {
            rtb24h
        } else {
            (rtb24h + rtbAll).distinctBy { normalizeTitle(it.title) }.take(20)
        }

        return balanceAibAndRtbArticles(selectedAib + selectedRtb, MAX_DISPLAYED_ARTICLES)
    }

    /**
     * Récupère exclusivement les actualités en direct de l'AIB et de la RTB (24h en priorité, repli 7j si un média est calme).
     * Coalesce les appels concurrents pour éviter les courses.
     */
    suspend fun getLiveNews(forceRefresh: Boolean = false): List<NewsArticle> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()

        val prunedCache = applySourceAwareRetention(cachedArticles, now)
        cachedArticles = prunedCache

        var existingInFlight: CompletableDeferred<List<NewsArticle>>? = null
        var ownedDeferred: CompletableDeferred<List<NewsArticle>>? = null

        synchronized(refreshLock) {
            val active = inFlightRefresh
            if (active != null && active.isActive) {
                existingInFlight = active
            } else if (!forceRefresh && prunedCache.isNotEmpty()) {
                return@withContext prunedCache
            } else {
                val created = CompletableDeferred<List<NewsArticle>>()
                inFlightRefresh = created
                ownedDeferred = created
            }
        }

        existingInFlight?.let { deferred ->
            return@withContext try {
                deferred.await()
            } catch (_: Throwable) {
                getCachedFreshNews()
            }
        }

        val currentDeferred = ownedDeferred!!
        try {
            val finalArticles = executeBalancedNetworkRefresh(now)
            currentDeferred.complete(finalArticles)
            finalArticles
        } catch (t: Throwable) {
            val fallback = getCachedFreshNews()
            currentDeferred.complete(fallback)
            fallback
        } finally {
            synchronized(refreshLock) {
                if (inFlightRefresh === currentDeferred) {
                    inFlightRefresh = null
                }
            }
        }
    }

    private suspend fun executeBalancedNetworkRefresh(now: Long): List<NewsArticle> {
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
                    withTimeoutOrNull(13000L) { fetchFromWpRestApi() } ?: emptyList()
                }
                val rtbYtAtomDeferred = async {
                    withTimeoutOrNull(8000L) { fetchFromRtbYouTubeAtomFeed() } ?: emptyList()
                }
                val ytApiDeferred = async {
                    withTimeoutOrNull(7000L) { fetchFromYouTubeApi() } ?: emptyList()
                }
                val googleAibMirrorDeferred = async {
                    withTimeoutOrNull(9500L) {
                        fetchGoogleMirrorForSource(
                            siteQuery = "(site:aib.media OR site:aib.bf OR \"Agence d'Information du Burkina\")",
                            canonicalSource = "AIB",
                            requiredKeyword = "aib"
                        )
                    } ?: emptyList()
                }
                val bingAibMirrorDeferred = async {
                    withTimeoutOrNull(8500L) {
                        fetchBingNewsMirrorForSource(
                            query = "(site:aib.media OR site:aib.bf OR \"Agence d'Information du Burkina\")",
                            canonicalSource = "AIB",
                            requiredKeyword = "aib"
                        )
                    } ?: emptyList()
                }
                val googleRtbMirrorDeferred = async {
                    withTimeoutOrNull(9500L) {
                        fetchGoogleMirrorForSource(
                            siteQuery = "(site:rtb.bf OR \"Radiodiffusion Télévision du Burkina\")",
                            canonicalSource = "RTB",
                            requiredKeyword = "rtb"
                        )
                    } ?: emptyList()
                }
                val rtbRssDeferredList = RTB_SOURCES.map { source ->
                    async {
                        withTimeoutOrNull(9500L) { fetchFromSource(source) } ?: emptyList()
                    }
                }
                val aibRssDeferredList = AIB_SOURCES.map { source ->
                    async {
                        withTimeoutOrNull(11000L) { fetchFromSource(source) } ?: emptyList()
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

                // Priorité 2 : Miroirs Google News & Bing News strictement limités à AIB et RTB
                val googleAibResults = googleAibMirrorDeferred.await()
                val bingAibResults = bingAibMirrorDeferred.await()
                val googleRtbResults = googleRtbMirrorDeferred.await()

                allFetched.addAll(aibResults)
                allFetched.addAll(aibRssResults)
                allFetched.addAll(googleAibResults)
                allFetched.addAll(bingAibResults)
                allFetched.addAll(rtbResults)
                allFetched.addAll(rtbRssResults)
                allFetched.addAll(rtbYtAtomResults)
                allFetched.addAll(ytApiResults)
                allFetched.addAll(metaResults)
                allFetched.addAll(googleRtbResults)

                if (rtbResults.isNotEmpty() || aibResults.isNotEmpty() ||
                    rtbRssResults.isNotEmpty() || aibRssResults.isNotEmpty() ||
                    rtbYtAtomResults.isNotEmpty() || ytApiResults.isNotEmpty() ||
                    metaResults.isNotEmpty() || googleAibResults.isNotEmpty() ||
                    bingAibResults.isNotEmpty() || googleRtbResults.isNotEmpty()
                ) {
                    anySourceSucceeded = true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching AIB & RTB live feeds", e)
        }

        // Deuxième passe multi-canaux si l'AIB a moins de 3 dépêches ou si aucun flux n'a encore répondu
        val extendedCutoff = now - EXTENDED_RETENTION_WINDOW_MS
        val aibCountSoFar = allFetched.count {
            it.source.contains("AIB", ignoreCase = true) &&
                it.timestamp in extendedCutoff..(now + FUTURE_CLOCK_SKEW_MS)
        }
        if (aibCountSoFar < 3 || allFetched.isEmpty()) {
            try {
                supervisorScope {
                    val aibHtmlFallbackDeferred = async {
                        withTimeoutOrNull(11000L) { fetchAibFromHtmlPages() } ?: emptyList()
                    }
                    val aibLightRetry = async {
                        withTimeoutOrNull(8000L) {
                            fetchWordPressPosts(
                                endpoints = listOf(
                                    "https://aib.media/wp-json/wp/v2/posts?per_page=25",
                                    "https://www.aib.bf/wp-json/wp/v2/posts?per_page=25",
                                    "https://www.aib.media/index.php?rest_route=/wp/v2/posts&per_page=25",
                                    "https://www.aib.bf/index.php?rest_route=/wp/v2/posts&per_page=25"
                                ),
                                sourceName = "AIB",
                                idPrefix = "aib_wp_fast",
                                defaultFallbackSummary = "Dépêche officielle transmise en direct par l'Agence d'Information du Burkina (AIB)."
                            )
                        } ?: emptyList()
                    }
                    val aibMirrorRetry = async {
                        withTimeoutOrNull(7500L) {
                            fetchGoogleMirrorForSource(
                                siteQuery = "(\"Agence d'Information du Burkina\" OR (AIB \"Burkina Faso\") OR \"aib.media\" OR \"aib.bf\")",
                                canonicalSource = "AIB",
                                requiredKeyword = "aib"
                            )
                        } ?: emptyList()
                    }
                    val aibBingRetry = async {
                        withTimeoutOrNull(7000L) {
                            fetchBingNewsMirrorForSource(
                                query = "\"Agence d'Information du Burkina\" OR (AIB \"Burkina Faso\")",
                                canonicalSource = "AIB",
                                requiredKeyword = "aib"
                            )
                        } ?: emptyList()
                    }
                    val rtbRetry: Deferred<List<NewsArticle>>? = if (allFetched.isEmpty()) {
                        async {
                            withTimeoutOrNull(6500L) {
                                fetchGoogleMirrorForSource(
                                    siteQuery = "site:rtb.bf OR \"Radiodiffusion Télévision du Burkina\"",
                                    canonicalSource = "RTB",
                                    requiredKeyword = "rtb"
                                )
                            } ?: emptyList()
                        }
                    } else null

                    val retryAib = aibHtmlFallbackDeferred.await() +
                        aibLightRetry.await() +
                        aibMirrorRetry.await() +
                        aibBingRetry.await()
                    val retryRtb = rtbRetry?.await() ?: emptyList()
                    if (retryAib.isNotEmpty() || retryRtb.isNotEmpty()) {
                        anySourceSucceeded = true
                        allFetched.addAll(retryAib)
                        allFetched.addAll(retryRtb)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Secondary news pass warning: ${e.message}")
            }
        }

        lastFetchHadNetworkSuccess = anySourceSucceeded

        val freshFetched = applySourceAwareRetention(allFetched, now)

        val finalArticles = if (freshFetched.isNotEmpty()) {
            saveCachedArticlesToDisk(freshFetched)
            freshFetched
        } else {
            getCachedFreshNews()
        }

        cachedArticles = finalArticles
        return finalArticles
    }

    /**
     * Flux Atom officiel gratuit de la chaîne YouTube RTB (@rtburkina), sans clé API requise.
     */
    private fun fetchFromRtbYouTubeAtomFeed(): List<NewsArticle> {
        val articles = mutableListOf<NewsArticle>()
        val feedUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=$RTB_YOUTUBE_CHANNEL_ID"
        try {
            val xml = executeGetText(feedUrl, "application/atom+xml, application/xml, text/xml, */*")
                ?: return emptyList()

            val now = System.currentTimeMillis()
            val cutoffRetention = now - EXTENDED_RETENTION_WINDOW_MS

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
        }
        return articles
    }

    /**
     * Miroir Google News RSS strictement filtré sur AIB (aib.media / aib.bf) ou RTB (rtb.bf)
     * afin de garantir les dépêches récentes même si le serveur direct est lent ou bloqué.
     */
    private fun fetchGoogleMirrorForSource(
        siteQuery: String,
        canonicalSource: String,
        requiredKeyword: String
    ): List<NewsArticle> {
        val articles = mutableListOf<NewsArticle>()
        val encoded = URLEncoder.encode("$siteQuery when:7d", "UTF-8")
        val endpoint = "https://news.google.com/rss/search?q=$encoded&hl=fr&gl=BF&ceid=BF:fr"

        try {
            val xml = executeGetText(endpoint, "application/rss+xml, application/xml, text/xml, */*")
                ?: return emptyList()

            val now = System.currentTimeMillis()
            val cutoffRetention = now - EXTENDED_RETENTION_WINDOW_MS
            val isAibTarget = requiredKeyword.equals("aib", ignoreCase = true)
            val wordRegex = Regex("\\b${Regex.escape(requiredKeyword)}\\b", RegexOption.IGNORE_CASE)

            val itemMatcher = Pattern.compile("<item>(.*?)</item>", Pattern.DOTALL).matcher(xml)
            while (itemMatcher.find()) {
                val item = itemMatcher.group(1) ?: continue
                val rawTitle = cleanHtml(extractRegexGroup(item, "<title[^>]*>(.*?)</title>"))
                val link = cleanHtml(extractRegexGroup(item, "<link[^>]*>(.*?)</link>"))
                val pubDate = extractRegexGroup(item, "<pubDate[^>]*>(.*?)</pubDate>").trim()
                val rawDesc = cleanHtml(extractRegexGroup(item, "<description[^>]*>(.*?)</description>"))
                val sourceTag = cleanHtml(extractRegexGroup(item, "<source[^>]*>(.*?)</source>"))
                val sourceUrlAttr = extractRegexGroup(item, "<source[^>]+url=[\"']([^\"']+)[\"']")

                val domainMatches = if (isAibTarget) {
                    sourceUrlAttr.contains("aib.media", ignoreCase = true) ||
                        sourceUrlAttr.contains("aib.bf", ignoreCase = true) ||
                        link.contains("aib.media", ignoreCase = true) ||
                        link.contains("aib.bf", ignoreCase = true) ||
                        rawDesc.contains("aib.media", ignoreCase = true) ||
                        rawDesc.contains("aib.bf", ignoreCase = true)
                } else {
                    sourceUrlAttr.contains("rtb.bf", ignoreCase = true) ||
                        link.contains("rtb.bf", ignoreCase = true)
                }

                val agencyFullName = if (isAibTarget) "Agence d'Information du Burkina" else "Radiodiffusion Télévision du Burkina"
                val sourceMatches = domainMatches ||
                    wordRegex.containsMatchIn(sourceTag) ||
                    sourceTag.contains(agencyFullName, ignoreCase = true) ||
                    wordRegex.containsMatchIn(sourceUrlAttr) ||
                    wordRegex.containsMatchIn(rawTitle) ||
                    rawDesc.contains(agencyFullName, ignoreCase = true)

                if (!sourceMatches || rawTitle.isBlank() || link.isBlank()) continue

                val cleanTitle = rawTitle
                    .replace(Regex("\\s*-\\s*(AIB|Agence d'Information du Burkina|aib\\.media|aib\\.bf|RTB|Radiodiffusion Télévision du Burkina).*$", RegexOption.IGNORE_CASE), "")
                    .trim()
                    .ifBlank { rawTitle }

                val ts = parsePubDateToMillis(pubDate)
                if (ts < cutoffRetention) continue

                val category = inferCategory(cleanTitle, canonicalSource)
                val defaultSummary = if (canonicalSource == "RTB") {
                    "Information officielle publiée par la Radiodiffusion Télévision du Burkina (RTB)."
                } else {
                    "Dépêche officielle publiée par l'Agence d'Information du Burkina (AIB)."
                }
                val cleanSummary = if (rawDesc.length > 35 && !rawDesc.equals(rawTitle, ignoreCase = true)) {
                    if (rawDesc.length > 220) rawDesc.take(217) + "..." else rawDesc
                } else {
                    defaultSummary
                }

                articles.add(
                    NewsArticle(
                        id = "${requiredKeyword}_gn_${link.hashCode()}",
                        title = cleanTitle,
                        summary = cleanSummary,
                        category = category,
                        date = formatRelativeDate(ts),
                        source = canonicalSource,
                        url = link,
                        imageUrl = "",
                        timestamp = ts,
                        fullContent = "$cleanTitle\n\n$cleanSummary"
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Google News mirror ($canonicalSource) error: ${e.message}")
        }
        return articles
    }

    /**
     * Miroir secondaire Bing News RSS pour AIB / RTB lorsque les flux directs sont indisponibles.
     */
    private fun fetchBingNewsMirrorForSource(
        query: String,
        canonicalSource: String,
        requiredKeyword: String
    ): List<NewsArticle> {
        val articles = mutableListOf<NewsArticle>()
        val encoded = URLEncoder.encode(query, "UTF-8")
        val endpoint = "https://www.bing.com/news/search?q=$encoded&format=rss&setlang=fr-fr"

        try {
            val xml = executeGetText(endpoint, "application/rss+xml, application/xml, text/xml, */*")
                ?: return emptyList()

            val now = System.currentTimeMillis()
            val cutoffRetention = now - EXTENDED_RETENTION_WINDOW_MS
            val isAibTarget = requiredKeyword.equals("aib", ignoreCase = true)
            val wordRegex = Regex("\\b${Regex.escape(requiredKeyword)}\\b", RegexOption.IGNORE_CASE)

            val itemMatcher = Pattern.compile("<item>(.*?)</item>", Pattern.DOTALL).matcher(xml)
            while (itemMatcher.find()) {
                val item = itemMatcher.group(1) ?: continue
                val rawTitle = cleanHtml(extractRegexGroup(item, "<title[^>]*>(.*?)</title>"))
                val rawLink = cleanHtml(extractRegexGroup(item, "<link[^>]*>(.*?)</link>"))
                val pubDate = extractRegexGroup(item, "<pubDate[^>]*>(.*?)</pubDate>").trim()
                val rawDesc = cleanHtml(extractRegexGroup(item, "<description[^>]*>(.*?)</description>"))
                val newsSource = cleanHtml(extractRegexGroup(item, "<News:Source[^>]*>(.*?)</News:Source>"))
                val newsImage = sanitizeImageUrl(extractRegexGroup(item, "<News:Image[^>]*>(.*?)</News:Image>"))

                // Bing News encapsule parfois l'URL finale dans ?url=https%3a%2f%2f...
                val decodedLink = try {
                    val urlParam = Regex("[?&]url=([^&]+)").find(rawLink)?.groupValues?.get(1)
                    if (!urlParam.isNullOrBlank()) java.net.URLDecoder.decode(urlParam, "UTF-8") else rawLink
                } catch (_: Exception) {
                    rawLink
                }

                val agencyFullName = if (isAibTarget) "Agence d'Information du Burkina" else "Radiodiffusion Télévision du Burkina"
                val domainMatches = if (isAibTarget) {
                    decodedLink.contains("aib.media", ignoreCase = true) ||
                        decodedLink.contains("aib.bf", ignoreCase = true) ||
                        newsSource.contains("aib", ignoreCase = true)
                } else {
                    decodedLink.contains("rtb.bf", ignoreCase = true) ||
                        newsSource.contains("rtb", ignoreCase = true)
                }

                val matchesSource = domainMatches ||
                    wordRegex.containsMatchIn(newsSource) ||
                    newsSource.contains(agencyFullName, ignoreCase = true) ||
                    wordRegex.containsMatchIn(rawTitle) ||
                    rawDesc.contains(agencyFullName, ignoreCase = true)

                if (!matchesSource || rawTitle.isBlank() || decodedLink.isBlank()) continue

                val ts = parsePubDateToMillis(pubDate)
                if (ts < cutoffRetention) continue

                val cleanTitle = rawTitle
                    .replace(Regex("\\s*-\\s*(AIB|Agence d'Information du Burkina|RTB).*$", RegexOption.IGNORE_CASE), "")
                    .trim()
                    .ifBlank { rawTitle }
                val summary = if (rawDesc.isNotBlank()) {
                    if (rawDesc.length > 220) rawDesc.take(217) + "..." else rawDesc
                } else {
                    "Dépêche officielle publiée par l'Agence d'Information du Burkina (AIB)."
                }

                articles.add(
                    NewsArticle(
                        id = "${requiredKeyword}_bing_${decodedLink.hashCode()}",
                        title = cleanTitle,
                        summary = summary,
                        category = inferCategory(cleanTitle, canonicalSource),
                        date = formatRelativeDate(ts),
                        source = canonicalSource,
                        url = decodedLink,
                        imageUrl = newsImage,
                        timestamp = ts,
                        fullContent = if (rawDesc.isNotBlank()) "$cleanTitle\n\n$rawDesc" else summary
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Bing News mirror ($canonicalSource) error: ${e.message}")
        }
        return articles
    }

    /**
     * Repli HTML direct sur les pages d'accueil d'AIB (aib.media / aib.bf)
     * au cas où wp-json et /feed/ seraient temporairement limités ou bloqués par un pare-feu WordPress.
     */
    private suspend fun fetchAibFromHtmlPages(): List<NewsArticle> {
        val candidatePages = listOf(
            "https://www.aib.media/",
            "https://aib.media/",
            "https://www.aib.bf/",
            "https://aib.bf/",
            "https://www.aib.media/category/a-la-une/",
            "https://www.aib.media/category/burkina-faso/"
        )

        val discovered = linkedMapOf<String, NewsArticle>()
        val now = System.currentTimeMillis()

        for (pageUrl in candidatePages) {
            val html = executeGetText(pageUrl, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                ?: continue
            val parsed = parseAibHtmlListings(html, pageUrl, now)
            for (art in parsed) {
                discovered.putIfAbsent(normalizeTitle(art.title), art)
            }
            if (discovered.size >= 12) break
        }

        if (discovered.isEmpty()) return emptyList()

        // Enrichir les 8 premières dépêches avec leur vraie date, image og:image et paragraphes complets
        val topList = discovered.values.toList()
        val toEnrich = topList.take(8)
        val remainder = topList.drop(8)

        val enrichedTop = supervisorScope {
            toEnrich.mapIndexed { idx, base ->
                async {
                    withTimeoutOrNull(4500L) {
                        enrichAibHtmlArticle(base, now, idx)
                    } ?: base.copy(timestamp = if (base.timestamp > 0L) base.timestamp else (now - idx * 20L * 60_000L))
                }
            }.awaitAll()
        }

        return (enrichedTop + remainder).map { art ->
            val safeTs = if (art.timestamp > 0L) art.timestamp else now
            art.copy(
                timestamp = safeTs,
                date = formatRelativeDate(safeTs)
            )
        }
    }

    private fun parseAibHtmlListings(html: String, baseUrl: String, now: Long): List<NewsArticle> {
        val list = mutableListOf<NewsArticle>()
        val seenUrls = HashSet<String>()
        val baseHostUrl = try {
            val u = URL(baseUrl)
            "${u.protocol}://${u.host}"
        } catch (_: Exception) {
            "https://www.aib.media"
        }

        fun resolveAibUrl(rawHref: String): String {
            val trimmed = rawHref.trim()
            if (trimmed.isBlank() || trimmed.startsWith("#") || trimmed.startsWith("javascript:", true)) return ""
            return when {
                trimmed.startsWith("http://", true) -> "https://" + trimmed.removePrefix("http://").removePrefix("HTTP://")
                trimmed.startsWith("https://", true) -> trimmed
                trimmed.startsWith("//") -> "https:$trimmed"
                trimmed.startsWith("/") -> "$baseHostUrl$trimmed"
                else -> "$baseHostUrl/$trimmed"
            }
        }

        fun isArticleUrl(url: String): Boolean {
            val lower = url.lowercase(Locale.ROOT)
            if (!lower.contains("aib.media") && !lower.contains("aib.bf")) return false
            val excluded = listOf(
                "/category/", "/tag/", "/author/", "/page/", "/wp-content/", "/wp-json/",
                "/wp-admin/", "/feed", "facebook.com", "twitter.com", "youtube.com",
                "/contact", "/a-propos", "/qui-sommes-nous", "/mentions-legales", "/search"
            )
            if (excluded.any { lower.contains(it) }) return false
            val path = try { URL(url).path.trim('/') } catch (_: Exception) { "" }
            return path.length >= 10 && path.contains("-")
        }

        // Stratégie 1 : blocs <article>...</article>
        val articleMatcher = Pattern.compile("<article\\b[^>]*>(.*?)</article>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE).matcher(html)
        var rank = 0
        while (articleMatcher.find() && list.size < 20) {
            val block = articleMatcher.group(1) ?: continue
            val headingLinkMatcher = Pattern.compile(
                "<h[1-5][^>]*>.*?<a[^>]+href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>",
                Pattern.DOTALL or Pattern.CASE_INSENSITIVE
            ).matcher(block)
            val fallbackLinkMatcher = Pattern.compile(
                "<a[^>]+href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>",
                Pattern.DOTALL or Pattern.CASE_INSENSITIVE
            ).matcher(block)

            var articleUrl = ""
            var title = ""
            if (headingLinkMatcher.find()) {
                articleUrl = resolveAibUrl(headingLinkMatcher.group(1).orEmpty())
                title = cleanHtml(headingLinkMatcher.group(2).orEmpty())
            } else {
                while (fallbackLinkMatcher.find()) {
                    val candUrl = resolveAibUrl(fallbackLinkMatcher.group(1).orEmpty())
                    val candTitle = cleanHtml(fallbackLinkMatcher.group(2).orEmpty())
                    if (isArticleUrl(candUrl) && candTitle.length >= 18) {
                        articleUrl = candUrl
                        title = candTitle
                        break
                    }
                }
            }

            if (!isArticleUrl(articleUrl) || title.length < 18 || !seenUrls.add(articleUrl)) continue

            val rawDateAttr = extractRegexGroup(block, "<time[^>]+datetime=[\"']([^\"']+)[\"']")
                .ifBlank { extractRegexGroup(block, "<time[^>]*>(.*?)</time>") }
            val parsedTs = parseFlexibleDateToMillis(rawDateAttr)
                .takeIf { it > 0L }
                ?: extractDateFromUrl(articleUrl)
                ?: (now - rank * 25L * 60_000L)

            val imgUrl = extractBestImageFromHtmlBlock(block, baseHostUrl)
            val excerpt = cleanHtml(extractRegexGroup(block, "<p[^>]*>(.*?)</p>"))
            val summary = if (excerpt.length >= 25) {
                if (excerpt.length > 220) excerpt.take(217) + "..." else excerpt
            } else {
                "Dépêche officielle publiée par l'Agence d'Information du Burkina (AIB)."
            }

            list.add(
                NewsArticle(
                    id = "aib_html_${articleUrl.hashCode()}",
                    title = title,
                    summary = summary,
                    category = inferCategory(title, "AIB"),
                    date = formatRelativeDate(parsedTs),
                    source = "AIB",
                    url = articleUrl,
                    imageUrl = imgUrl,
                    timestamp = parsedTs,
                    fullContent = if (excerpt.isNotBlank()) "$title\n\n$excerpt" else summary
                )
            )
            rank++
        }

        // Stratégie 2 : titres <h1>..<h5> contenant un lien d'article AIB si peu de <article>
        if (list.size < 4) {
            val hMatcher = Pattern.compile(
                "<h[1-5][^>]*>(?:(?!</h[1-5]>).)*?<a[^>]+href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>",
                Pattern.DOTALL or Pattern.CASE_INSENSITIVE
            ).matcher(html)
            while (hMatcher.find() && list.size < 20) {
                val articleUrl = resolveAibUrl(hMatcher.group(1).orEmpty())
                val title = cleanHtml(hMatcher.group(2).orEmpty())
                if (!isArticleUrl(articleUrl) || title.length < 18 || !seenUrls.add(articleUrl)) continue
                val ts = extractDateFromUrl(articleUrl) ?: (now - rank * 25L * 60_000L)
                val summary = "Dépêche officielle publiée par l'Agence d'Information du Burkina (AIB)."
                list.add(
                    NewsArticle(
                        id = "aib_html_${articleUrl.hashCode()}",
                        title = title,
                        summary = summary,
                        category = inferCategory(title, "AIB"),
                        date = formatRelativeDate(ts),
                        source = "AIB",
                        url = articleUrl,
                        imageUrl = "",
                        timestamp = ts,
                        fullContent = "$title\n\n$summary"
                    )
                )
                rank++
            }
        }

        return list
    }

    private fun enrichAibHtmlArticle(base: NewsArticle, now: Long, fallbackIndex: Int): NewsArticle {
        val html = executeGetText(base.url, "text/html,application/xhtml+xml,*/*;q=0.8")
            ?: return base

        val metaPublished = extractRegexGroup(html, "<meta[^>]+property=[\"']article:published_time[\"'][^>]+content=[\"']([^\"']+)[\"']")
            .ifBlank { extractRegexGroup(html, "<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+property=[\"']article:published_time[\"']") }
            .ifBlank { extractRegexGroup(html, "\"datePublished\"\\s*:\\s*\"([^\"]+)\"") }
            .ifBlank { extractRegexGroup(html, "<time[^>]+datetime=[\"']([^\"']+)[\"']") }
        val realTs = parseFlexibleDateToMillis(metaPublished)
            .takeIf { it > 0L }
            ?: base.timestamp.takeIf { it > 0L }
            ?: (now - fallbackIndex * 25L * 60_000L)

        val ogImage = sanitizeImageUrl(
            extractRegexGroup(html, "<meta[^>]+property=[\"']og:image[\"'][^>]+content=[\"']([^\"']+)[\"']")
                .ifBlank { extractRegexGroup(html, "<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+property=[\"']og:image[\"']") }
        )

        val ogDesc = cleanHtml(
            extractRegexGroup(html, "<meta[^>]+property=[\"']og:description[\"'][^>]+content=[\"']([^\"']+)[\"']")
                .ifBlank { extractRegexGroup(html, "<meta[^>]+name=[\"']description[\"'][^>]+content=[\"']([^\"']+)[\"']") }
        )

        // Extraire les paragraphes principaux du corps de l'article
        val bodyBlock = extractRegexGroup(html, "<div[^>]+class=[\"'][^\"']*(?:entry-content|post-content|td-post-content|article-content)[^\"']*[\"'][^>]*>(.*?)</div>\\s*(?:<footer|</article|<div[^>]+class=[\"'][^\"']*sharedaddy)")
            .ifBlank { extractRegexGroup(html, "<article[^>]*>(.*?)</article>") }
        val pMatcher = Pattern.compile("<p[^>]*>(.*?)</p>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
            .matcher(bodyBlock)
        val paragraphs = mutableListOf<String>()
        while (pMatcher.find() && paragraphs.size < 12) {
            val pText = cleanHtml(pMatcher.group(1).orEmpty())
            if (pText.length >= 35 && !pText.contains("aib.media", ignoreCase = true)) {
                paragraphs.add(pText)
            }
        }
        val fullBody = if (paragraphs.isNotEmpty()) paragraphs.joinToString("\n\n") else ogDesc.ifBlank { base.fullContent }
        val finalSummary = when {
            ogDesc.length >= 35 -> if (ogDesc.length > 220) ogDesc.take(217) + "..." else ogDesc
            paragraphs.isNotEmpty() -> if (paragraphs.first().length > 220) paragraphs.first().take(217) + "..." else paragraphs.first()
            else -> base.summary
        }

        return base.copy(
            timestamp = realTs,
            date = formatRelativeDate(realTs),
            imageUrl = ogImage.ifBlank { base.imageUrl },
            summary = finalSummary,
            fullContent = fullBody
        )
    }

    private fun extractBestImageFromHtmlBlock(html: String, baseHostUrl: String): String {
        val attrs = listOf("data-lazy-src", "data-src", "src")
        for (attr in attrs) {
            val m = Pattern.compile("<img[^>]+$attr=[\"']([^\"'>]+)[\"']", Pattern.CASE_INSENSITIVE).matcher(html)
            while (m.find()) {
                val raw = m.group(1).orEmpty().trim()
                val lower = raw.lowercase(Locale.ROOT)
                if (raw.isBlank() || lower.startsWith("data:") || lower.contains("avatar") || lower.contains("logo") || lower.contains("emoji")) continue
                val resolved = when {
                    raw.startsWith("http://", true) || raw.startsWith("https://", true) -> raw
                    raw.startsWith("//") -> "https:$raw"
                    raw.startsWith("/") -> "$baseHostUrl$raw"
                    else -> ""
                }
                if (resolved.isNotBlank()) return sanitizeImageUrl(resolved)
            }
        }
        return ""
    }

    private fun extractDateFromUrl(url: String): Long? {
        val m = Pattern.compile("/(20\\d{2})/(\\d{2})/(\\d{2})/").matcher(url)
        if (m.find()) {
            val y = m.group(1) ?: return null
            val mo = m.group(2) ?: return null
            val d = m.group(3) ?: return null
            return parseIsoDateToMillis("$y-$mo-${d}T12:00:00Z").takeIf { it > 0L }
        }
        return null
    }

    private fun parseFlexibleDateToMillis(rawDate: String): Long {
        if (rawDate.isBlank()) return 0L
        val iso = parseIsoDateToMillis(rawDate)
        if (iso > 0L) return iso
        val cleaned = cleanHtml(rawDate)
            .replace("à", "")
            .replace(Regex("\\s+"), " ")
            .trim()
        val frenchFormats = listOf(
            SimpleDateFormat("d MMMM yyyy", Locale.FRENCH),
            SimpleDateFormat("dd MMMM yyyy", Locale.FRENCH),
            SimpleDateFormat("d MMM yyyy", Locale.FRENCH),
            SimpleDateFormat("dd/MM/yyyy", Locale.FRENCH)
        )
        for (fmt in frenchFormats) {
            try {
                val parsed = fmt.parse(cleaned)
                if (parsed != null) {
                    val now = System.currentTimeMillis()
                    return if (parsed.time > now) now else parsed.time
                }
            } catch (_: Exception) {}
        }
        return 0L
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
        val cutoffRetention = now - EXTENDED_RETENTION_WINDOW_MS

        for ((pageName, sourceLabel) in dynamicTargets) {
            val endpoint = "https://graph.facebook.com/v19.0/$pageName/posts?fields=id,message,created_time,full_picture,permalink_url&limit=15&access_token=$customMetaToken"
            try {
                val jsonText = executeGetText(endpoint, "application/json") ?: continue
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
            } catch (e: Exception) {
                Log.w(TAG, "Meta Graph API check for $pageName: ${e.message}")
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

        try {
            val endpoint = "https://www.googleapis.com/youtube/v3/search?part=snippet&channelId=$RTB_YOUTUBE_CHANNEL_ID&maxResults=10&order=date&type=video&key=$apiKey"
            val jsonText = executeGetText(endpoint, "application/json") ?: return emptyList()
            val items = org.json.JSONObject(jsonText).optJSONArray("items") ?: return emptyList()
            val now = System.currentTimeMillis()
            val cutoffRetention = now - EXTENDED_RETENTION_WINDOW_MS

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
        } catch (e: Exception) {
            Log.w(TAG, "YouTube API fetch failed: ${e.message}")
        }
        return articles
    }

    /**
     * API REST WordPress de la RTB — requêtes optimisées (per_page=25) avec repli léger sans _embed.
     */
    private fun fetchFromRtbRestApi(): List<NewsArticle> {
        val endpoints = listOf(
            "https://www.rtb.bf/wp-json/wp/v2/posts?_embed=wp:featuredmedia,wp:term&per_page=25",
            "https://www.rtb.bf/wp-json/wp/v2/posts?per_page=25",
            "https://www.rtb.bf/index.php?rest_route=/wp/v2/posts&per_page=25",
            "https://rtb.bf/wp-json/wp/v2/posts?per_page=25"
        )
        return fetchWordPressPosts(
            endpoints = endpoints,
            sourceName = "RTB",
            idPrefix = "rtb_wp",
            defaultFallbackSummary = "Reportage et article d'information diffusé par la Radiodiffusion Télévision du Burkina (RTB)."
        )
    }

    /**
     * API REST WordPress de l'AIB — interroge en parallèle aib.media et aib.bf (avec et sans _embed).
     */
    private suspend fun fetchFromWpRestApi(): List<NewsArticle> = supervisorScope {
        val mediaEndpoints = listOf(
            "https://www.aib.media/wp-json/wp/v2/posts?_embed=wp:featuredmedia,wp:term&per_page=20",
            "https://www.aib.media/wp-json/wp/v2/posts?per_page=25",
            "https://aib.media/wp-json/wp/v2/posts?per_page=25",
            "https://www.aib.media/index.php?rest_route=/wp/v2/posts&per_page=25"
        )
        val bfEndpoints = listOf(
            "https://www.aib.bf/wp-json/wp/v2/posts?_embed=wp:featuredmedia,wp:term&per_page=20",
            "https://www.aib.bf/wp-json/wp/v2/posts?per_page=25",
            "https://aib.bf/wp-json/wp/v2/posts?per_page=25",
            "https://www.aib.bf/index.php?rest_route=/wp/v2/posts&per_page=25"
        )
        val aibSummary = "Dépêche officielle transmise en direct par l'Agence d'Information du Burkina (AIB)."
        val mediaJob = async {
            fetchWordPressPosts(mediaEndpoints, "AIB", "aib_wp", aibSummary)
        }
        val bfJob = async {
            fetchWordPressPosts(bfEndpoints, "AIB", "aib_bf_wp", aibSummary)
        }
        (mediaJob.await() + bfJob.await()).distinctBy { normalizeTitle(it.title) }
    }

    private fun fetchWordPressPosts(
        endpoints: List<String>,
        sourceName: String,
        idPrefix: String,
        defaultFallbackSummary: String
    ): List<NewsArticle> {
        val articles = mutableListOf<NewsArticle>()

        for (endpoint in endpoints) {
            try {
                val jsonText = executeGetText(endpoint, "application/json, text/plain, */*") ?: continue
                if (!jsonText.startsWith("[")) continue

                val jsonArray = org.json.JSONArray(jsonText)
                val now = System.currentTimeMillis()
                val cutoffRetention = now - EXTENDED_RETENTION_WINDOW_MS

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

                // Retourner immédiatement dès que des articles valides ont été trouvés ; sinon tester les autres endpoints/domaines
                if (articles.isNotEmpty()) return articles
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching $sourceName WP REST ($endpoint): ${e.message}")
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
        return try {
            val bodyText = executeGetText(
                urlStr,
                "application/rss+xml, application/atom+xml, application/xml, text/xml, */*"
            ) ?: return emptyList()
            if (bodyText.contains("<item", ignoreCase = true) || bodyText.contains("<entry", ignoreCase = true)) {
                parseRssXmlSafe(bodyText, source)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed feed $urlStr (${source.name}): ${e.message}")
            emptyList()
        }
    }

    /**
     * Parseur XML tolérant avec repli par Regex si le XML du serveur contient des balises HTML imbriquées.
     */
    private fun parseRssXmlSafe(xmlText: String, source: RssSource): List<NewsArticle> {
        val list = mutableListOf<NewsArticle>()
        val nowTime = System.currentTimeMillis()
        val cutoffRetention = nowTime - EXTENDED_RETENTION_WINDOW_MS

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
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
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
        val days = diff / (1000 * 60 * 60 * 24)

        return when {
            diff <= 0 -> "À l'instant"
            minutes < 2 -> "À l'instant"
            minutes < 60 -> "Il y a $minutes min"
            hours < 24 -> "Il y a $hours h"
            days < 2 -> "Hier"
            else -> "Il y a $days j"
        }
    }

    private fun normalizeTitle(title: String): String {
        return title.lowercase().trim().replace(Regex("[^a-z0-9]"), "")
    }

    /**
     * Retourne uniquement les vraies actualités AIB & RTB en cache.
     */
    fun getDefaultBurkinaNews(): List<NewsArticle> = getCachedFreshNews()
}
