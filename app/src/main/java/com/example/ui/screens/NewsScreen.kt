package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import coil.request.ImageRequest
import com.example.data.local.NewsArticle
import com.example.ui.SamAiViewModel
import com.example.ui.components.YouTubePlayerView
import com.example.ui.components.YouTubeVideoCard
import com.example.ui.components.launchVideoPlayer
import com.example.util.TtsManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsScreen(
    viewModel: SamAiViewModel,
    ttsManager: TtsManager,
    navController: NavController
) {
    val context = LocalContext.current
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val articles by viewModel.filteredNews.collectAsStateWithLifecycle()
    val isLoading by viewModel.isNewsLoading.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedNewsCategory.collectAsStateWithLifecycle()
    val selectedSource by viewModel.selectedNewsSource.collectAsStateWithLifecycle()
    val searchQuery by viewModel.newsSearchQuery.collectAsStateWithLifecycle()

    var currentlySpeakingId by remember { mutableStateOf<String?>(null) }
    var activeInlineVideoId by remember { mutableStateOf<String?>(null) }
    var selectedArticleForRead by remember { mutableStateOf<NewsArticle?>(null) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.loadLiveNews(forceRefresh = true)
    }

    val categories = listOf(
        "Tout",
        "AIB Direct",
        "RTB Actualités",
        "Burkina Faso",
        "Société",
        "Culture & Arts",
        "Économie",
        "Sport",
        "Vidéos / JT"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "refreshRotation")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (appLanguage == "en") "AIB & RTB • Direct News" else "AIB & RTB • Dépêches en direct",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFD32F2F).copy(alpha = 0.15f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(Color(0xFFD32F2F), CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "LIVE BF",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFFD32F2F),
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (appLanguage == "en") "AIB & RTB Feeds • Direct information" else "Dépêches & Articles • En direct 24h/24",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { 
                            viewModel.loadLiveNews(forceRefresh = true)
                            coroutineScope.launch {
                                if (articles.isNotEmpty()) {
                                    listState.animateScrollToItem(0)
                                }
                            }
                        },
                        modifier = Modifier.testTag("btn_refresh_news")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Actualiser",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = if (isLoading) Modifier.rotate(rotationAngle) else Modifier
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 1. Barre de recherche
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setNewsSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("input_search_news"),
                placeholder = {
                    Text(
                        text = if (appLanguage == "en") "Search AIB or RTB news, topics..." else "Rechercher une actualité AIB ou RTB...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Rechercher",
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { viewModel.setNewsSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Effacer")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            // 2. Bannières d'accès directs officiels AIB & RTB
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1877F2).copy(alpha = 0.12f)
                    ),
                    border = BorderStroke(1.dp, Color(0xFF1877F2).copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "AIB Officiel",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1877F2)
                            )
                            Text(
                                text = "Facebook",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                        }
                        IconButton(
                            onClick = {
                                openArticleUrl(context, "https://www.facebook.com/share/1ErsffNdsd/?mibextid=wwXIfr")
                            },
                            modifier = Modifier.size(28.dp).testTag("btn_open_facebook_aib")
                        ) {
                            Icon(
                                Icons.Default.OpenInNew,
                                contentDescription = "Ouvrir AIB",
                                tint = Color(0xFF1877F2),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            openArticleUrl(context, "https://www.youtube.com/@RTBOfficiel")
                        },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFE50914).copy(alpha = 0.12f)
                    ),
                    border = BorderStroke(1.dp, Color(0xFFE50914).copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "RTB YouTube",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE50914)
                            )
                            Text(
                                text = "Chaîne Officielle",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                        }
                        IconButton(
                            onClick = {
                                openArticleUrl(context, "https://www.youtube.com/@RTBOfficiel")
                            },
                            modifier = Modifier.size(28.dp).testTag("btn_open_rtb_portal")
                        ) {
                            Icon(
                                Icons.Default.PlayCircle,
                                contentDescription = "Ouvrir RTB YouTube",
                                tint = Color(0xFFE50914),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // 3. Filtres par Rubrique & Média
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Filtrer :",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                categories.forEach { category ->
                    val isSelected = selectedCategory.equals(category, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setNewsCategory(category) },
                        label = {
                            Text(
                                text = category,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 4. Liste des articles d'actualités
            if (isLoading && articles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Récupération des flux d'actualités en direct...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (articles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Article,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Aucune actualité trouvée",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Vérifiez vos filtres ou actualisez pour charger les flux.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                viewModel.setNewsCategory("Tout")
                                viewModel.setNewsSource("Toutes")
                                viewModel.setNewsSearchQuery("")
                                viewModel.loadLiveNews(forceRefresh = true)
                            }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Réinitialiser & Actualiser")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(articles, key = { it.id }) { article ->
                        val isVideoItem = article.isVideo && (article.youtubeId.isNotBlank() || article.videoUrl.isNotBlank())
                        val isPlayingInline = (activeInlineVideoId == article.id)

                        if (isVideoItem) {
                            YouTubeVideoCard(
                                article = article,
                                isSpeaking = currentlySpeakingId == article.id,
                                onListen = {
                                    if (currentlySpeakingId == article.id) {
                                        ttsManager.stop()
                                        currentlySpeakingId = null
                                    } else {
                                        val textToRead = "${article.title}. ${article.summary}"
                                        ttsManager.speak(textToRead, "fr")
                                        currentlySpeakingId = article.id
                                    }
                                },
                                onShare = {
                                    shareArticle(context, article)
                                }
                            )
                        } else {
                            NewsCard(
                                article = article,
                                isVideoItem = false,
                                isPlayingInline = isPlayingInline,
                                isSpeaking = currentlySpeakingId == article.id,
                                onListen = {
                                    if (currentlySpeakingId == article.id) {
                                        ttsManager.stop()
                                        currentlySpeakingId = null
                                    } else {
                                        val textToRead = "${article.title}. ${article.summary}"
                                        ttsManager.speak(textToRead, "fr")
                                        currentlySpeakingId = article.id
                                    }
                                },
                                onPlayInlineVideo = {
                                    if (activeInlineVideoId == article.id) {
                                        activeInlineVideoId = null
                                    } else {
                                        activeInlineVideoId = article.id
                                    }
                                },
                                onCloseInlineVideo = {
                                    activeInlineVideoId = null
                                },
                                onReadArticle = {
                                    selectedArticleForRead = article
                                },
                                onShare = {
                                    shareArticle(context, article)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal de lecture complète pour dépêches écrites AIB
    selectedArticleForRead?.let { article ->
        InAppArticleReaderDialog(
            article = article,
            onDismiss = { selectedArticleForRead = null },
            onShare = { shareArticle(context, article) }
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NewsCard(
    article: NewsArticle,
    isVideoItem: Boolean,
    isPlayingInline: Boolean,
    isSpeaking: Boolean,
    onListen: () -> Unit,
    onPlayInlineVideo: () -> Unit,
    onCloseInlineVideo: () -> Unit,
    onReadArticle: () -> Unit,
    onShare: () -> Unit
) {
    val sourceColor = when {
        article.source.contains("AIB", ignoreCase = true) -> Color(0xFF1877F2)
        article.source.contains("RTB", ignoreCase = true) -> Color(0xFFE50914)
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { 
                if (isVideoItem) {
                    onPlayInlineVideo()
                } else {
                    onReadArticle()
                }
            }
            .testTag("card_news_${article.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (isPlayingInline) {
                // Lecteur vidéo interactif directement dans la carte (sans changer de page)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .background(Color.Black)
                ) {
                    val context = LocalContext.current
                    val youtubeId = remember(article) {
                        if (article.youtubeId.isNotBlank()) {
                            article.youtubeId
                        } else {
                            val m = java.util.regex.Pattern.compile("(?:youtube\\.com/(?:embed/|watch\\?v=|live/)|youtu\\.be/)([a-zA-Z0-9_\\-]{11})").matcher(article.fullContent + " " + article.url + " " + article.videoUrl)
                            if (m.find()) m.group(1) ?: "" else ""
                        }
                    }

                    val targetVideoUrl = remember(article, youtubeId) {
                        if (youtubeId.isNotBlank()) {
                            "https://www.youtube.com/watch?v=$youtubeId"
                        } else if (article.videoUrl.isNotBlank()) {
                            article.videoUrl
                        } else {
                            article.url
                        }
                    }

                    if (youtubeId.isNotBlank()) {
                        YouTubePlayerView(
                            youtubeId = youtubeId,
                            videoTitle = article.title,
                            thumbnailUrl = article.imageUrl,
                            articleUrl = article.url,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (article.videoUrl.endsWith(".mp4", ignoreCase = true)) {
                        // Vidéo MP4 directe
                        val mp4Html = """
                            <!DOCTYPE html>
                            <html>
                            <head>
                              <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                              <style>
                                * { margin:0; padding:0; box-sizing:border-box; }
                                html, body { width:100%; height:100%; background:#000; overflow:hidden; display:flex; justify-content:center; align-items:center; }
                                video { width:100%; height:100%; object-fit:contain; }
                              </style>
                            </head>
                            <body>
                              <video controls autoplay playsinline src="${article.videoUrl}"></video>
                            </body>
                            </html>
                        """.trimIndent()

                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = true
                                    settings.mediaPlaybackRequiresUserGesture = false
                                    webChromeClient = WebChromeClient()
                                    loadDataWithBaseURL(null, mp4Html, "text/html", "UTF-8", null)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // Fallback visuel élégant YouTube
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (article.imageUrl.isNotBlank()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(article.imageUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = article.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.65f))
                                    )
                            }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Icon(
                                    Icons.Default.PlayCircle,
                                    contentDescription = null,
                                    tint = Color(0xFFE50914),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Vidéo disponible sur YouTube",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        launchVideoPlayer(context, youtubeId, targetVideoUrl)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Regarder sur YouTube")
                                }
                            }
                        }
                    }

                    // Barre de contrôle supérieure (Bouton YouTube + Bouton Fermer)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .align(Alignment.TopEnd),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFE50914).copy(alpha = 0.9f),
                            modifier = Modifier.clickable {
                                launchVideoPlayer(context, youtubeId, targetVideoUrl)
                            }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "YouTube",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.75f)
                        ) {
                            IconButton(
                                onClick = onCloseInlineVideo,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Fermer la vidéo",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            } else if (article.imageUrl.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(article.imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = article.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)),
                                    startY = 50f
                                )
                            )
                    )

                    // Badge de la source
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = sourceColor.copy(alpha = 0.95f),
                        modifier = Modifier
                            .padding(10.dp)
                            .align(Alignment.TopStart)
                    ) {
                        Text(
                            text = article.source,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    // Bouton Play central si c'est une vidéo / JT
                    if (isVideoItem) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(54.dp),
                            shape = CircleShape,
                            color = Color(0xFFD32F2F).copy(alpha = 0.9f),
                            border = BorderStroke(2.dp, Color.White),
                            shadowElevation = 4.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Regarder",
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                    }
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Badge de la source si l'article n'a pas d'image
                        if (article.imageUrl.isBlank() && !isPlayingInline) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = sourceColor.copy(alpha = 0.95f)
                            ) {
                                Text(
                                    text = article.source,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isVideoItem) Color(0xFFD32F2F).copy(alpha = 0.12f) else sourceColor.copy(alpha = 0.12f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                if (isVideoItem) {
                                    Icon(
                                        Icons.Default.Videocam,
                                        contentDescription = null,
                                        tint = Color(0xFFD32F2F),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    text = if (isVideoItem) "Vidéo / JT RTB" else article.category,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isVideoItem) Color(0xFFD32F2F) else sourceColor
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val isVeryRecent = (System.currentTimeMillis() - article.timestamp) < (2L * 60 * 60 * 1000L)
                        if (isVeryRecent) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFD32F2F).copy(alpha = 0.15f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .background(Color(0xFFD32F2F), CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "DIRECT",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFFD32F2F),
                                        fontSize = 8.5.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        }

                        Icon(
                            Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = article.date,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isVeryRecent) FontWeight.Bold else FontWeight.Normal,
                            color = if (isVeryRecent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = article.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = article.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onListen,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isSpeaking) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isSpeaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Icon(
                            imageVector = if (isSpeaking) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (isSpeaking) "Arrêter" else "Écouter",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isSpeaking) "Arrêter" else "Écouter",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onShare,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Partager",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        if (isVideoItem) {
                            if (isPlayingInline) {
                                OutlinedButton(
                                    onClick = onCloseInlineVideo,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = Color(0xFFE50914).copy(alpha = 0.1f)
                                    ),
                                    border = BorderStroke(1.dp, Color(0xFFE50914)),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("btn_close_video_${article.id}")
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = null,
                                        tint = Color(0xFFE50914),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Fermer",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE50914)
                                    )
                                }
                            } else {
                                Button(
                                    onClick = onPlayInlineVideo,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("btn_play_video_${article.id}")
                                ) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Regarder",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        } else {
                            Button(
                                onClick = onReadArticle,
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("btn_read_news_${article.id}")
                            ) {
                                Text(
                                    text = "Lire",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    Icons.Default.Article,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Lecteur de dépêche intégré dans l'application pour les articles écrits
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InAppArticleReaderDialog(
    article: NewsArticle,
    onDismiss: () -> Unit,
    onShare: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.96f)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF1877F2).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "${article.source} • ${article.category}",
                            color = Color(0xFF1877F2),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = article.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = article.date,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (article.imageUrl.isNotBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(article.imageUrl).crossfade(true).build(),
                        contentDescription = article.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = article.summary,
                    style = MaterialTheme.typography.bodyLarge,
                    lineHeight = 24.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onShare) {
                        Icon(Icons.Default.Share, contentDescription = "Partager")
                    }

                    Button(
                        onClick = { openArticleUrl(context, article.url) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Lire sur ${article.source}")
                    }
                }
            }
        }
    }
}

private fun openArticleUrl(context: Context, url: String) {
    if (url.isBlank()) return
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "Impossible d'ouvrir le lien", Toast.LENGTH_SHORT).show()
    }
}

private fun shareArticle(context: Context, article: NewsArticle) {
    try {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(
                Intent.EXTRA_TEXT,
                "📰 *${article.title}*\n\n${article.summary}\n\n🔗 Lire l'article : ${article.url}\n\n_Partagé via SAM AI_"
            )
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Partager l'actualité via")
        context.startActivity(shareIntent)
    } catch (_: Exception) {
        Toast.makeText(context, "Erreur lors du partage", Toast.LENGTH_SHORT).show()
    }
}


