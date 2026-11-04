package com.example.data.remote

import android.net.Uri
import android.util.Log
import com.example.data.local.WebSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern

data class LiveNewsArticle(
    val title: String,
    val link: String,
    val sourceName: String,
    val pubDate: String,
    val snippet: String = ""
)

data class LiveNewsResult(
    val hasNews: Boolean,
    val contextSummary: String,
    val sources: List<WebSource>
)

/**
 * Service en temps réel pour récupérer les vraies dépêches de presse officielles
 * du Burkina Faso (LeFaso.net, Burkina24, Sidwaya, AIB, Google News Burkina)
 * et du monde (Google News Monde, France 24, RFI) avec rotation anti-répétition pour le Chat SAM AI.
 */
class LiveNewsFeedService {

    companion object {
        // Cache mémoire des listes d'articles bruts (pour éviter de re-télécharger si demandé à quelques secondes d'intervalle,
        // tout en sélectionnant à chaque question de NOUVEAUX articles non encore cités dans le chat)
        private val rawArticlesCache = ConcurrentHashMap<String, Pair<Long, List<LiveNewsArticle>>>()
        private val topicRotationIndex = ConcurrentHashMap<String, AtomicInteger>()
        private const val CACHE_TTL_MS = 3 * 60 * 1000L // 3 minutes max pour garantir la fraîcheur

        private val sharedHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(6000, TimeUnit.MILLISECONDS)
                .readTimeout(6500, TimeUnit.MILLISECONDS)
                .callTimeout(8000, TimeUnit.MILLISECONDS)
                .followRedirects(true)
                .build()
        }

        private fun isBurkinaTopicStatic(query: String): Boolean {
            val q = query.lowercase(java.util.Locale.ROOT)
            val burkinaMarkers = listOf(
                "burkina", "faso", "ouaga", "bobo", "koudougou", "banfora", "ouahigouya",
                "kaya", "fada", "dedougou", "tenkodogo", "traore", "ibrahim traore",
                "aib", "rtb", "sidwaya", "lefaso", "burkina24", "etalons", "étalons",
                "sahel", "aes", "ouagadougou", "siao", "fespaco", "burkinabe", "burkinabè"
            )
            return burkinaMarkers.any { q.contains(it) }
        }

        /**
         * Renvoie instantanément un lot varié de dépêches réelles adapté au sujet demandé (Burkina Faso vs Monde),
         * sans jamais mélanger les dépêches mondiales lorsqu'on demande le Burkina Faso.
         */
        fun getInstantCachedSummary(
            query: String = "",
            fallbackBurkinaArticles: List<com.example.data.local.NewsArticle> = emptyList()
        ): String {
            val isBurkina = isBurkinaTopicStatic(query)
            if (isBurkina) {
                val burkinaPool = rawArticlesCache.entries
                    .firstOrNull { it.key.startsWith("burkina_") && it.value.second.isNotEmpty() }
                    ?.value?.second
                    ?: run {
                        val repoArticles = fallbackBurkinaArticles.ifEmpty {
                            com.example.data.repository.NewsRepository.getDefaultBurkinaNews()
                        }
                        repoArticles.map { art ->
                            LiveNewsArticle(
                                title = art.title,
                                link = art.url.ifBlank { "https://www.aib.media" },
                                sourceName = art.source.ifBlank { "AIB / Presse du Faso" },
                                pubDate = art.date,
                                snippet = art.summary
                            )
                        }
                    }
                if (burkinaPool.isEmpty()) return ""
                val cursor = topicRotationIndex.getOrPut("instant_burkina") { AtomicInteger(0) }.getAndIncrement()
                val rotated = rotateArticles(burkinaPool, cursor, count = 6)
                return buildSummaryFromArticles(rotated, scopeLabel = "BURKINA FASO")
            } else {
                val worldPool = rawArticlesCache.entries
                    .firstOrNull { !it.key.startsWith("burkina_") && it.value.second.isNotEmpty() }
                    ?.value?.second
                    ?: return ""
                val cursor = topicRotationIndex.getOrPut("instant_world") { AtomicInteger(0) }.getAndIncrement()
                val rotated = rotateArticles(worldPool, cursor, count = 6)
                return buildSummaryFromArticles(rotated, scopeLabel = "MONDE & INTERNATIONAL")
            }
        }

        private fun rotateArticles(
            pool: List<LiveNewsArticle>,
            rotationStep: Int,
            count: Int
        ): List<LiveNewsArticle> {
            if (pool.size <= count) return pool
            val offset = ((rotationStep * 4) % pool.size).coerceAtLeast(0)
            val rotated = pool.drop(offset) + pool.take(offset)
            return rotated.take(count)
        }

        private fun buildSummaryFromArticles(articles: List<LiveNewsArticle>, scopeLabel: String = "EN DIRECT"): String {
            if (articles.isEmpty()) return ""
            val dateStr = java.text.SimpleDateFormat("EEEE d MMMM yyyy", java.util.Locale.FRENCH).format(java.util.Date())
            return buildString {
                append("=== DÉPÊCHES ET ACTUALITÉS EN DIRECT [$scopeLabel] ($dateStr) ===\n")
                articles.forEachIndexed { idx, art ->
                    append("${idx + 1}. [${art.sourceName}] ${art.title}")
                    if (art.pubDate.isNotBlank()) append(" (${art.pubDate})")
                    if (art.snippet.isNotBlank()) append(" — ${art.snippet}")
                    append("\n")
                }
            }
        }
    }

    private val httpClient get() = sharedHttpClient

    private val itemPattern = Pattern.compile("<item[^>]*>(.*?)</item>", Pattern.DOTALL)
    private val titlePattern = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.DOTALL)
    private val linkPattern = Pattern.compile("<link[^>]*>(.*?)</link>", Pattern.DOTALL)
    private val pubDatePattern = Pattern.compile("<(?:pubDate|dc:date)[^>]*>(.*?)</(?:pubDate|dc:date)>", Pattern.DOTALL)
    private val sourcePattern = Pattern.compile("<source[^>]*>(.*?)</source>", Pattern.DOTALL)
    private val descPattern = Pattern.compile("<description[^>]*>(.*?)</description>", Pattern.DOTALL)

    /**
     * Nettoie la question parlée ou écrite pour extraire uniquement les vrais mots-clés de recherche.
     * IMPORTANT : Ne supprime JAMAIS "monde", "international", "burkina", "faso", "afrique", etc.
     */
    private fun extractCoreKeywords(rawQuery: String): String {
        val stopWords = setOf(
            "quel", "quelle", "quels", "quelles", "est", "sont", "les", "des", "une", "un", "le", "la",
            "du", "de", "dans", "sur", "pour", "avec", "par", "au", "aux", "en", "ce", "cet", "cette",
            "ces", "mon", "ton", "son", "notre", "votre", "leur", "qui", "que", "quoi", "ou", "où",
            "comment", "pourquoi", "quand", "donne", "donnes", "donner", "donnez", "moi", "nous",
            "dis", "dites", "dire", "parle", "parles", "parlez", "parler", "montre", "montres", "montrez", "montrer",
            "raconte", "racontes", "racontez", "raconter", "explique", "expliques", "expliquez", "expliquer",
            "cherche", "cherches", "chercher", "recherche", "recherches", "rechercher", "trouve", "trouves", "trouver",
            "fais", "fait", "faire", "peux", "peut", "pourrais", "pourrait", "tu", "vous", "je",
            "veux", "voudrais", "aimerais", "savoir", "connaitre", "connaître",
            "actualite", "actualites", "actualité", "actualités", "actu", "actus", "nouvelle",
            "nouvelles", "info", "infos", "information", "informations", "recent", "recente", "recents",
            "recentes", "récent", "récente", "récents", "récentes", "dernier", "derniere", "derniers",
            "dernieres", "dernière", "dernières", "aujourd", "hui", "aujourdhui", "jour", "direct",
            "passe", "passent", "passer", "il", "elle", "on", "y", "a", "t", "neuf", "internet", "web",
            "sam", "ai", "stp", "svp", "s'il", "plait", "plaît", "autre", "autres", "encore", "plus",
            "concernant", "sujet", "propos", "actuellement", "moment", "pays", "quelques", "tous", "toutes", "tout", "toute"
        )
        val tokens = rawQuery
            .replace(Regex("['’`\"«»?!.,;:()\\[\\]-]"), " ")
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { token ->
                val lower = token.lowercase(java.util.Locale.ROOT)
                token.length >= 2 && lower !in stopWords
            }
        return tokens.joinToString(" ").trim()
    }

    /**
     * Recherche et agrège en parallèle les actualités les plus récentes en fonction de la question posée,
     * en excluant automatiquement les articles déjà cités dans la conversation (`alreadyMentionedText`).
     */
    suspend fun fetchLiveNews(
        query: String,
        alreadyMentionedText: String = ""
    ): LiveNewsResult = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        val coreKeywords = extractCoreKeywords(cleanQuery)

        val isBurkinaSpecific = isBurkinaTopic(cleanQuery) || isBurkinaTopic(coreKeywords)
        val isWorldSpecific = !isBurkinaSpecific && (isWorldTopic(cleanQuery) || isWorldTopic(coreKeywords))
        val isGeneralNews = !isBurkinaSpecific && !isWorldSpecific && (coreKeywords.isBlank() || isGeneralNewsQuery(cleanQuery))

        val topicBucket = when {
            isBurkinaSpecific -> {
                val extraBurkinaKeywords = coreKeywords
                    .replace(Regex("(?i)\\b(burkina|faso|burkinabe|burkinabè)\\b"), "")
                    .trim()
                if (extraBurkinaKeywords.isNotBlank()) "burkina_$extraBurkinaKeywords" else "burkina_general"
            }
            isWorldSpecific || isGeneralNews -> "world_general"
            else -> "topic_${coreKeywords.lowercase(java.util.Locale.ROOT)}"
        }

        val now = System.currentTimeMillis()
        val cachedPool = rawArticlesCache[topicBucket]?.takeIf { (now - it.first) < CACHE_TTL_MS }?.second

        var allDistinctArticles = if (!cachedPool.isNullOrEmpty()) {
            cachedPool
        } else {
            val fetched = supervisorScope {
                when {
                    isBurkinaSpecific -> {
                        val extraKeywords = coreKeywords
                            .replace(Regex("(?i)\\b(burkina|faso|burkinabe|burkinabè)\\b"), "")
                            .trim()

                        val dGoogleRecent3d = async {
                            withTimeoutOrNull(5500L) {
                                val q = if (extraKeywords.isNotBlank()) {
                                    "Burkina Faso $extraKeywords when:7d"
                                } else {
                                    "\"Burkina Faso\" OR Ouagadougou when:7d"
                                }
                                fetchGoogleNewsRss(q, fallbackSource = "Presse du Faso")
                            } ?: emptyList()
                        }
                        val dGoogleMedia = async {
                            withTimeoutOrNull(5500L) {
                                val q = "(site:lefaso.net OR site:burkina24.com OR site:sidwaya.info OR site:aib.media) when:7d"
                                fetchGoogleNewsRss(q, fallbackSource = "Médias du Faso")
                            } ?: emptyList()
                        }
                        val dGoogle7d = async {
                            withTimeoutOrNull(5500L) {
                                fetchGoogleNewsRss("Burkina Faso actualités when:7d", fallbackSource = "Actualités Faso")
                            } ?: emptyList()
                        }
                        val dLefaso = async {
                            withTimeoutOrNull(5000L) { fetchLefasoFeed() } ?: emptyList()
                        }
                        val dBurkina24 = async {
                            withTimeoutOrNull(5000L) { fetchBurkina24Feed() } ?: emptyList()
                        }

                        val list = mutableListOf<LiveNewsArticle>()
                        list.addAll(dLefaso.await())
                        list.addAll(dBurkina24.await())
                        list.addAll(dGoogleMedia.await())
                        list.addAll(dGoogleRecent3d.await())
                        list.addAll(dGoogle7d.await())
                        list
                    }

                    isWorldSpecific || isGeneralNews -> {
                        val dWorldTopic = async {
                            withTimeoutOrNull(5500L) {
                                fetchGoogleWorldHeadlinesRss()
                            } ?: emptyList()
                        }
                        val dWorldSearch = async {
                            withTimeoutOrNull(5500L) {
                                fetchGoogleNewsRss("actualité internationale OR monde when:3d", fallbackSource = "Presse Internationale")
                            } ?: emptyList()
                        }
                        val dFrance24 = async {
                            withTimeoutOrNull(5000L) { fetchFrance24WorldFeed() } ?: emptyList()
                        }
                        val dRfiWorld = async {
                            withTimeoutOrNull(5000L) { fetchRfiWorldFeed() } ?: emptyList()
                        }

                        val list = mutableListOf<LiveNewsArticle>()
                        list.addAll(dFrance24.await())
                        list.addAll(dRfiWorld.await())
                        list.addAll(dWorldTopic.await())
                        list.addAll(dWorldSearch.await())
                        list
                    }

                    else -> {
                        val effectiveQuery = coreKeywords.ifBlank { cleanQuery }
                        val dRecent = async {
                            withTimeoutOrNull(5500L) {
                                fetchGoogleNewsRss("$effectiveQuery when:7d", fallbackSource = "Actualités en direct")
                            } ?: emptyList()
                        }
                        val dBroad = async {
                            withTimeoutOrNull(5500L) {
                                fetchGoogleNewsRss(effectiveQuery, fallbackSource = "Presse en direct")
                            } ?: emptyList()
                        }
                        val dFrance24 = async {
                            withTimeoutOrNull(4500L) { fetchFrance24WorldFeed() } ?: emptyList()
                        }
                        val list = mutableListOf<LiveNewsArticle>()
                        list.addAll(dRecent.await())
                        list.addAll(dBroad.await())
                        list.addAll(dFrance24.await().take(4))
                        list
                    }
                }
            }

            val deduped = fetched
                .filter { it.title.isNotBlank() && it.link.isNotBlank() }
                .distinctBy { normalizeTitleForDeduplication(it.title) }
                .take(28)

            if (deduped.isNotEmpty()) {
                rawArticlesCache[topicBucket] = Pair(now, deduped)
                if (isBurkinaSpecific && topicBucket != "burkina_general" && !rawArticlesCache.containsKey("burkina_general")) {
                    rawArticlesCache["burkina_general"] = Pair(now, deduped)
                }
            }
            deduped
        }

        // Si la connexion mobile est lente et que l'utilisateur demande le Burkina Faso, utiliser le pool Burkina vérifié de NewsRepository
        if (allDistinctArticles.isEmpty() && isBurkinaSpecific) {
            allDistinctArticles = com.example.data.repository.NewsRepository.getDefaultBurkinaNews().map { art ->
                LiveNewsArticle(
                    title = art.title,
                    link = art.url.ifBlank { "https://www.aib.media" },
                    sourceName = art.source.ifBlank { "AIB / Presse du Faso" },
                    pubDate = art.date,
                    snippet = art.summary
                )
            }
        }

        if (allDistinctArticles.isEmpty()) {
            return@withContext LiveNewsResult(
                hasNews = false,
                contextSummary = "",
                sources = emptyList()
            )
        }

        // Filtrage anti-répétition : exclut les articles dont les mots-clés principaux figurent déjà
        // dans les réponses précédentes de SAM AI au cours de la discussion
        val normalizedHistory = alreadyMentionedText.lowercase(java.util.Locale.ROOT)
        val unseenArticles = if (normalizedHistory.isNotBlank()) {
            allDistinctArticles.filterNot { article ->
                isArticleAlreadyMentioned(article.title, normalizedHistory)
            }
        } else {
            allDistinctArticles
        }

        val candidatePool = if (unseenArticles.size >= 4) unseenArticles else allDistinctArticles
        val step = topicRotationIndex.getOrPut(topicBucket) { AtomicInteger(0) }.getAndIncrement()
        val selectedArticles = rotateArticles(candidatePool, step, count = 8)

        val dateStr = java.text.SimpleDateFormat("EEEE d MMMM yyyy", java.util.Locale.FRENCH).format(java.util.Date())
        val scopeLabel = when {
            isBurkinaSpecific -> "BURKINA FASO"
            isWorldSpecific || isGeneralNews -> "MONDE & INTERNATIONAL"
            else -> coreKeywords.uppercase(java.util.Locale.ROOT)
        }

        val sb = StringBuilder()
        sb.append("=== DÉPÊCHES RÉELLES EN DIRECT [$scopeLabel] ($dateStr) ===\n")
        sb.append("Voici de nouvelles dépêches actuelles vérifiées (ne répète JAMAIS les anciennes phrases déjà dites dans la discussion) :\n\n")

        val webSources = mutableListOf<WebSource>()

        selectedArticles.forEachIndexed { index, art ->
            val domain = extractDomain(art.link).ifBlank { art.sourceName }
            sb.append("${index + 1}. [${art.sourceName.ifBlank { domain }}] : \"${art.title}\"\n")
            if (art.snippet.isNotBlank() && !art.snippet.equals(art.title, ignoreCase = true)) {
                sb.append("   Détails : ${art.snippet}\n")
            }
            if (art.pubDate.isNotBlank()) {
                sb.append("   Publié : ${art.pubDate}\n")
            }
            sb.append("\n")

            webSources.add(
                WebSource(
                    title = art.title,
                    url = art.link,
                    domain = art.sourceName.ifBlank { domain }
                )
            )
        }

        sb.append("CONSIGNE STRICTE : Présente ces nouvelles informations de façon vivante, variée et précise. Si tu as déjà parlé de certains sujets dans tes messages précédents, concentre-toi exclusivement sur les nouveaux faits ci-dessus sans jamais répéter les mêmes phrases.")

        LiveNewsResult(
            hasNews = true,
            contextSummary = sb.toString(),
            sources = webSources
        )
    }

    private fun isArticleAlreadyMentioned(title: String, normalizedHistory: String): Boolean {
        val significantWords = title.lowercase(java.util.Locale.ROOT)
            .replace(Regex("[^a-zàâäéèêëîïôöùûüç0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 5 && it !in setOf("burkina", "ouagadougou", "actualite", "actualites", "monde", "contre", "apres", "entre") }

        if (significantWords.size < 2) return false
        val matchedCount = significantWords.count { word -> normalizedHistory.contains(word) }
        return matchedCount >= (significantWords.size * 0.65).toInt().coerceAtLeast(2)
    }

    private fun fetchGoogleNewsRss(query: String, fallbackSource: String): List<LiveNewsArticle> {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://news.google.com/rss/search?q=$encodedQuery&hl=fr&gl=FR&ceid=FR:fr"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) SAM-AI-News/2.0")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string().orEmpty()
                parseRssXml(body, fallbackSource = fallbackSource, maxItems = 15)
            }
        } catch (e: Throwable) {
            Log.w("LiveNewsFeedService", "Erreur Google News RSS ($query): ${e.message}")
            emptyList()
        }
    }

    private fun fetchGoogleWorldHeadlinesRss(): List<LiveNewsArticle> {
        return try {
            val url = "https://news.google.com/rss/headlines/section/topic/WORLD?hl=fr&gl=FR&ceid=FR:fr"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) SAM-AI-News/2.0")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string().orEmpty()
                parseRssXml(body, fallbackSource = "Actualités Monde", maxItems = 15)
            }
        } catch (e: Throwable) {
            Log.w("LiveNewsFeedService", "Erreur Google World RSS: ${e.message}")
            emptyList()
        }
    }

    private fun fetchLefasoFeed(): List<LiveNewsArticle> {
        return try {
            val req = Request.Builder()
                .url("https://lefaso.net/spip.php?page=backend")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) SAM-AI-Reader/2.0")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val xml = resp.body?.string().orEmpty()
                    parseRssXml(xml, fallbackSource = "LeFaso.net", maxItems = 12)
                } else {
                    emptyList()
                }
            }
        } catch (e: Throwable) {
            Log.w("LiveNewsFeedService", "Lefaso feed error: ${e.message}")
            emptyList()
        }
    }

    private fun fetchBurkina24Feed(): List<LiveNewsArticle> {
        return try {
            val req = Request.Builder()
                .url("https://burkina24.com/feed/")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) SAM-AI-Reader/2.0")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val xml = resp.body?.string().orEmpty()
                    parseRssXml(xml, fallbackSource = "Burkina24", maxItems = 10)
                } else {
                    emptyList()
                }
            }
        } catch (e: Throwable) {
            Log.w("LiveNewsFeedService", "Burkina24 feed error: ${e.message}")
            emptyList()
        }
    }

    private fun fetchFrance24WorldFeed(): List<LiveNewsArticle> {
        return try {
            val req = Request.Builder()
                .url("https://www.france24.com/fr/rss")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) SAM-AI-Reader/2.0")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val xml = resp.body?.string().orEmpty()
                    parseRssXml(xml, fallbackSource = "France 24", maxItems = 10)
                } else {
                    emptyList()
                }
            }
        } catch (e: Throwable) {
            Log.w("LiveNewsFeedService", "France24 feed error: ${e.message}")
            emptyList()
        }
    }

    private fun fetchRfiWorldFeed(): List<LiveNewsArticle> {
        return try {
            val req = Request.Builder()
                .url("https://www.rfi.fr/fr/rss")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) SAM-AI-Reader/2.0")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val xml = resp.body?.string().orEmpty()
                    parseRssXml(xml, fallbackSource = "RFI", maxItems = 10)
                } else {
                    emptyList()
                }
            }
        } catch (e: Throwable) {
            Log.w("LiveNewsFeedService", "RFI feed error: ${e.message}")
            emptyList()
        }
    }

    private fun parseRssXml(xml: String, fallbackSource: String, maxItems: Int = 15): List<LiveNewsArticle> {
        val results = mutableListOf<LiveNewsArticle>()
        val itemMatcher = itemPattern.matcher(xml)

        while (itemMatcher.find()) {
            val itemContent = itemMatcher.group(1) ?: continue

            val title = extractMatch(titlePattern, itemContent)
            val link = extractMatch(linkPattern, itemContent)
            val pubDate = extractMatch(pubDatePattern, itemContent)
            val source = extractMatch(sourcePattern, itemContent).ifBlank { fallbackSource }
            val rawDesc = extractMatch(descPattern, itemContent)

            val cleanTitle = cleanHtmlText(title)
            val cleanSnippet = cleanHtmlText(rawDesc)

            if (cleanTitle.isNotBlank() && link.isNotBlank()) {
                results.add(
                    LiveNewsArticle(
                        title = cleanTitle,
                        link = link.trim(),
                        sourceName = cleanHtmlText(source),
                        pubDate = cleanPubDate(pubDate),
                        snippet = cleanSnippet.take(280)
                    )
                )
            }
            if (results.size >= maxItems) break
        }
        return results
    }

    private fun extractMatch(pattern: Pattern, text: String): String {
        val m = pattern.matcher(text)
        return if (m.find()) m.group(1).orEmpty().trim() else ""
    }

    private fun cleanHtmlText(input: String): String {
        if (input.isBlank()) return ""
        var text = input
            .replace(Regex("<!\\[CDATA\\[(.*?)\\]\\]>", RegexOption.DOT_MATCHES_ALL)) { it.groupValues[1] }
            .replace(Regex("<[^>]+>"), " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")

        text = text.replace(Regex("&#(\\d+);")) { match ->
            try {
                match.groupValues[1].toInt().toChar().toString()
            } catch (_: Throwable) {
                match.value
            }
        }
        text = text.replace(Regex("&#x([0-9a-fA-F]+);")) { match ->
            try {
                match.groupValues[1].toInt(16).toChar().toString()
            } catch (_: Throwable) {
                match.value
            }
        }
        return text.replace(Regex("\\s+"), " ").trim()
    }

    private fun cleanPubDate(pubDate: String): String {
        return pubDate
            .replace(" +0000", "")
            .replace(" GMT", "")
            .replace("T", " ")
            .replace("Z", "")
            .trim()
    }

    private fun normalizeTitleForDeduplication(title: String): String {
        return title.lowercase(java.util.Locale.ROOT)
            .replace(Regex("[^a-z0-9]"), "")
            .take(45)
    }

    private fun extractDomain(url: String): String {
        return try {
            val uri = Uri.parse(url)
            val host = uri.host.orEmpty()
            host.removePrefix("www.")
        } catch (_: Throwable) {
            ""
        }
    }

    private fun isGeneralNewsQuery(query: String): Boolean {
        val q = query.lowercase(java.util.Locale.ROOT).trim()
        val generalMarkers = listOf(
            "actualite", "actualités", "actualites", "actu", "actus",
            "nouvelles", "derniere nouvelle", "dernieres nouvelles",
            "info", "infos", "que se passe-t-il", "quoi de neuf",
            "a la une", "titres du jour"
        )
        return generalMarkers.any { q == it || q.startsWith("$it ") || q.endsWith(" $it") }
    }

    private fun isWorldTopic(query: String): Boolean {
        val q = query.lowercase(java.util.Locale.ROOT)
        val worldMarkers = listOf(
            "monde", "mondial", "mondiale", "mondiales", "international",
            "internationale", "internationales", "etranger", "étranger",
            "planete", "planète", "globe"
        )
        return worldMarkers.any { q.contains(it) }
    }

    private fun isBurkinaTopic(query: String): Boolean {
        val q = query.lowercase(java.util.Locale.ROOT)
        val burkinaMarkers = listOf(
            "burkina", "faso", "ouaga", "bobo", "koudougou", "banfora", "ouahigouya",
            "kaya", "fada", "dedougou", "tenkodogo", "traore", "ibrahim traore",
            "aib", "rtb", "sidwaya", "lefaso", "burkina24", "etalons", "étalons",
            "sahel", "aes", "ouagadougou", "siao", "fespaco", "burkinabe", "burkinabè"
        )
        return burkinaMarkers.any { q.contains(it) }
    }
}
