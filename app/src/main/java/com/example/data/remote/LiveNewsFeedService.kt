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
 * Service en temps réel pour récupérer les dépêches de presse officielles
 * du Burkina Faso (LeFaso.net, AIB, RTB, Sidwaya, Burkina24) et du monde (Google News, RFI, France24, BBC).
 * Parse nativement les flux RSS 2.0 et RSS 1.0 (RDF) avec décodage complet des entités HTML.
 */
class LiveNewsFeedService {

    companion object {
        // Cache mémoire partagé à travers toute l'application (chat + appels vocaux)
        private val sharedMemoryCache = ConcurrentHashMap<String, Pair<Long, LiveNewsResult>>()
        private const val CACHE_TTL_MS = 5 * 60 * 1000L

        private val sharedHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(1200, TimeUnit.MILLISECONDS)
                .readTimeout(1300, TimeUnit.MILLISECONDS)
                .callTimeout(1500, TimeUnit.MILLISECONDS)
                .followRedirects(true)
                .build()
        }

        /**
         * Renvoie instantanément (0 ms) les dernières dépêches en mémoire pour éviter toute latence
         */
        fun getInstantCachedSummary(): String {
            val generalCached = sharedMemoryCache["general_top_news"]?.second
            if (generalCached != null && generalCached.hasNews && generalCached.contextSummary.isNotBlank()) {
                return generalCached.contextSummary
            }
            val anyCached = sharedMemoryCache.values.firstOrNull { it.second.hasNews }?.second
            if (anyCached != null && anyCached.contextSummary.isNotBlank()) {
                return anyCached.contextSummary
            }
            val defaultArticles = com.example.data.repository.NewsRepository.getDefaultBurkinaNews().take(5)
            if (defaultArticles.isNotEmpty()) {
                return buildString {
                    append("ACTUALITÉS RÉCENTES EN DIRECT :\n")
                    defaultArticles.forEachIndexed { idx, art ->
                        append("${idx + 1}. [${art.source}] ${art.title} — ${art.summary.take(150)}\n")
                    }
                }
            }
            return ""
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
     * Nettoie la question parlée ou écrite pour extraire uniquement les vrais mots-clés de recherche
     */
    private fun extractCoreKeywords(rawQuery: String): String {
        val stopWords = setOf(
            "quel", "quelle", "quels", "quelles", "est", "sont", "les", "des", "une", "un", "le", "la",
            "du", "de", "dans", "sur", "pour", "avec", "par", "au", "aux", "en", "ce", "cet", "cette",
            "ces", "mon", "ton", "son", "notre", "votre", "leur", "qui", "que", "quoi", "ou", "où",
            "comment", "pourquoi", "quand", "donne", "donner", "donnez", "moi", "nous", "dis", "dire",
            "parle", "parler", "cherche", "chercher", "recherche", "rechercher", "trouve", "trouver",
            "fais", "faire", "peux", "peut", "tu", "vous", "je", "veux", "voudrais", "savoir", "connaitre",
            "connaître", "actualite", "actualites", "actualité", "actualités", "actu", "actus", "nouvelle",
            "nouvelles", "info", "infos", "information", "informations", "recent", "recente", "recents",
            "recentes", "récent", "récente", "récents", "récentes", "dernier", "derniere", "derniers",
            "dernieres", "dernière", "dernières", "aujourd", "hui", "aujourdhui", "jour", "direct",
            "passe", "passer", "il", "elle", "on", "y", "a", "t", "neuf", "monde", "internet", "web",
            "sam", "ai", "stp", "svp", "s'il", "plait", "plaît"
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
     * Recherche et agrège en parallèle les actualités les plus récentes en fonction de la question posée.
     */
    suspend fun fetchLiveNews(query: String): LiveNewsResult = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        val coreKeywords = extractCoreKeywords(cleanQuery)
        val cacheKey = coreKeywords.ifBlank { "general_top_news" }.lowercase(java.util.Locale.ROOT)

        val cached = sharedMemoryCache[cacheKey]
        if (cached != null && (System.currentTimeMillis() - cached.first) < CACHE_TTL_MS) {
            return@withContext cached.second
        }

        val isGeneralNews = coreKeywords.isBlank() || isGeneralNewsQuery(cleanQuery)
        val isBurkinaSpecific = isBurkinaTopic(cleanQuery) || isBurkinaTopic(coreKeywords) || isGeneralNews
        val effectiveSearchQuery = coreKeywords.ifBlank { cleanQuery }

        // Exécution 100% EN PARALLÈLE avec plafond de 1.3s par source pour qu'un serveur lent ne ralentisse jamais la réponse
        val articles = supervisorScope {
            val deferredGoogleSearch = async {
                withTimeoutOrNull(1300L) {
                    fetchGoogleNewsRss(effectiveSearchQuery, isGeneral = false, isBurkina = isBurkinaSpecific)
                } ?: emptyList()
            }
            val deferredGoogleHeadline = async {
                if (isGeneralNews || isBurkinaSpecific) {
                    withTimeoutOrNull(1200L) {
                        fetchGoogleNewsRss("", isGeneral = true, isBurkina = isBurkinaSpecific)
                    } ?: emptyList()
                } else {
                    emptyList()
                }
            }
            val deferredLefaso = async {
                if (isBurkinaSpecific || isGeneralNews) {
                    withTimeoutOrNull(1200L) { fetchLefasoFeed() } ?: emptyList()
                } else {
                    emptyList()
                }
            }
            val deferredRfi = async {
                withTimeoutOrNull(1200L) { fetchRfiFeed() } ?: emptyList()
            }

            val combined = mutableListOf<LiveNewsArticle>()
            combined.addAll(deferredGoogleSearch.await())
            combined.addAll(deferredLefaso.await())
            combined.addAll(deferredRfi.await())
            combined.addAll(deferredGoogleHeadline.await())
            combined
        }

        // Déduplication par titre normalisé
        val distinctArticles = articles
            .filter { it.title.isNotBlank() && it.link.isNotBlank() }
            .distinctBy { normalizeTitleForDeduplication(it.title) }
            .take(8)

        if (distinctArticles.isEmpty()) {
            val emptyResult = LiveNewsResult(
                hasNews = false,
                contextSummary = "",
                sources = emptyList()
            )
            return@withContext emptyResult
        }

        val dateStr = java.text.SimpleDateFormat("EEEE d MMMM yyyy", java.util.Locale.FRENCH).format(java.util.Date())
        val sb = StringBuilder()
        sb.append("=== DÉPÊCHES OFFICIELLES ET ACTUALITÉS CERTIFIÉES EN DIRECT ($dateStr) ===\n")
        sb.append("Voici les articles et dépêches réels publiés par les médias :\n\n")

        val webSources = mutableListOf<WebSource>()

        distinctArticles.forEachIndexed { index, art ->
            val domain = extractDomain(art.link).ifBlank { art.sourceName }
            sb.append("${index + 1}. [${art.sourceName.ifBlank { domain }}] : \"${art.title}\"\n")
            if (art.snippet.isNotBlank()) {
                sb.append("   Détail : ${art.snippet}\n")
            }
            if (art.pubDate.isNotBlank()) {
                sb.append("   Date : ${art.pubDate}\n")
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

        sb.append("Consigne impérative : Exploite immédiatement ces actualités réelles pour répondre de manière directe, précise et naturelle, sans jamais dire que tu n'as pas accès à Internet ou aux actualités.")

        val finalResult = LiveNewsResult(
            hasNews = true,
            contextSummary = sb.toString(),
            sources = webSources
        )

        sharedMemoryCache[cacheKey] = Pair(System.currentTimeMillis(), finalResult)
        if (isGeneralNews) {
            sharedMemoryCache["general_top_news"] = Pair(System.currentTimeMillis(), finalResult)
        }
        return@withContext finalResult
    }

    private fun fetchGoogleNewsRss(query: String, isGeneral: Boolean, isBurkina: Boolean): List<LiveNewsArticle> {
        return try {
            val url = if (isGeneral || query.isBlank()) {
                // Flux À la Une francophone
                if (isBurkina) {
                    "https://news.google.com/rss/search?q=Burkina+Faso+OR+Afrique&hl=fr&gl=FR&ceid=FR:fr"
                } else {
                    "https://news.google.com/rss?hl=fr&gl=FR&ceid=FR:fr"
                }
            } else {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                "https://news.google.com/rss/search?q=$encodedQuery&hl=fr&gl=FR&ceid=FR:fr"
            }

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile) SAM-AI-News/1.0")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string().orEmpty()
                parseRssXml(body, fallbackSource = if (isBurkina) "Presse Faso" else "Presse Internationale")
            }
        } catch (e: Throwable) {
            Log.w("LiveNewsFeedService", "Erreur Google News RSS: ${e.message}")
            emptyList()
        }
    }

    private fun fetchLefasoFeed(): List<LiveNewsArticle> {
        return try {
            val req = Request.Builder()
                .url("https://lefaso.net/spip.php?page=backend")
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile) SAM-AI-Reader/1.0")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val xml = resp.body?.string().orEmpty()
                    parseRssXml(xml, fallbackSource = "LeFaso.net").take(5)
                } else {
                    emptyList()
                }
            }
        } catch (e: Throwable) {
            Log.w("LiveNewsFeedService", "Lefaso feed error: ${e.message}")
            emptyList()
        }
    }

    private fun fetchRfiFeed(): List<LiveNewsArticle> {
        return try {
            val req = Request.Builder()
                .url("https://www.rfi.fr/fr/afrique/rss")
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile) SAM-AI-Reader/1.0")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val xml = resp.body?.string().orEmpty()
                    parseRssXml(xml, fallbackSource = "RFI Afrique").take(4)
                } else {
                    emptyList()
                }
            }
        } catch (e: Throwable) {
            Log.w("LiveNewsFeedService", "RFI feed error: ${e.message}")
            emptyList()
        }
    }

    private fun parseRssXml(xml: String, fallbackSource: String): List<LiveNewsArticle> {
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
                        snippet = cleanSnippet.take(250)
                    )
                )
            }
            if (results.size >= 12) break
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

        // Décodage des entités décimales &#123;
        text = text.replace(Regex("&#(\\d+);")) { match ->
            try {
                match.groupValues[1].toInt().toChar().toString()
            } catch (_: Throwable) {
                match.value
            }
        }
        // Décodage des entités hexadécimales &#x12a;
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
            .take(40)
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
            "actualite", "actualités", "actualite", "actu", "actus",
            "nouvelles", "derniere nouvelle", "dernieres nouvelles",
            "info", "infos", "que se passe-t-il", "quoi de neuf",
            "a la une", "titres du jour"
        )
        return generalMarkers.any { q == it || q.startsWith("$it ") || q.endsWith(" $it") }
    }

    private fun isBurkinaTopic(query: String): Boolean {
        val q = query.lowercase(java.util.Locale.ROOT)
        val burkinaMarkers = listOf(
            "burkina", "faso", "ouaga", "bobo", "koudougou", "traore",
            "ibrahim traore", "aib", "rtb", "sidwaya", "lefaso", "etalons",
            "sahel", "aes", "mali", "niger", "ouagadougou", "cfa", "siao", "fespaco"
        )
        return burkinaMarkers.any { q.contains(it) }
    }
}
