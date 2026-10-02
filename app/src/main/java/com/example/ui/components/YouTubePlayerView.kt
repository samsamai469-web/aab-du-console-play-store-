package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * Lanceur vidéo YouTube direct et propre :
 * - Ouvre l'application YouTube officielle ou le navigateur web YouTube
 * - Permet un visionnage immédiat sur YouTube
 */
fun launchVideoPlayer(context: Context, youtubeId: String, fallbackUrl: String = "") {
    val cleanId = youtubeId.trim().let { id ->
        if (id.contains("v=")) id.substringAfter("v=").substringBefore("&")
        else if (id.contains("youtu.be/")) id.substringAfter("youtu.be/").substringBefore("?")
        else id
    }

    var launched = false

    // 1. Lancement direct via Intent YouTube officiel
    if (cleanId.isNotBlank()) {
        try {
            val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$cleanId")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                setPackage("com.google.android.youtube")
            }
            context.startActivity(appIntent)
            launched = true
        } catch (_: Exception) {
            launched = false
        }

        // 2. Intent générique vidéo YouTube
        if (!launched) {
            try {
                val genericIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$cleanId")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(genericIntent)
                launched = true
            } catch (_: Exception) {
                launched = false
            }
        }
    }

    // 3. Fallback Web YouTube via Custom Tabs ou navigateur
    if (!launched) {
        val targetUrl = when {
            cleanId.isNotBlank() -> "https://www.youtube.com/watch?v=$cleanId"
            fallbackUrl.isNotBlank() -> fallbackUrl
            else -> "https://www.youtube.com"
        }

        try {
            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
            customTabsIntent.intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            customTabsIntent.launchUrl(context, Uri.parse(targetUrl))
            launched = true
        } catch (_: Exception) {
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(browserIntent)
                launched = true
            } catch (_: Exception) {
                Toast.makeText(context, "Impossible d'ouvrir YouTube", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/**
 * Composant de lecteur vidéo YouTube 100% natif Compose.
 * Affiche la miniature officielle et lance directement la vidéo sur YouTube.
 */
@Composable
fun YouTubePlayerView(
    youtubeId: String,
    modifier: Modifier = Modifier,
    videoTitle: String? = null,
    thumbnailUrl: String? = null,
    articleUrl: String = "",
    onClose: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val cleanId = remember(youtubeId) {
        youtubeId.trim().let { id ->
            if (id.contains("v=")) id.substringAfter("v=").substringBefore("&")
            else if (id.contains("youtu.be/")) id.substringAfter("youtu.be/").substringBefore("?")
            else id
        }
    }

    val resolvedThumbnail = remember(cleanId, thumbnailUrl) {
        if (!thumbnailUrl.isNullOrBlank()) {
            thumbnailUrl
        } else if (cleanId.isNotBlank()) {
            "https://img.youtube.com/vi/$cleanId/hqdefault.jpg"
        } else {
            null
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "play_pulse")
    val pulseScale = infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F11))
            .clickable { launchVideoPlayer(context, cleanId, articleUrl) }
    ) {
        // Image de couverture en fond
        if (!resolvedThumbnail.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(resolvedThumbnail)
                    .crossfade(true)
                    .build(),
                contentDescription = videoTitle ?: "Aperçu YouTube",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Dégradé sombre cinématique
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.40f),
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            )
        }

        // Contenu central de lecture
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Bouton Play YouTube central stylisé
            Surface(
                shape = CircleShape,
                color = Color(0xFFE50914),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .size(56.dp)
                    .scale(pulseScale.value)
                    .border(2.dp, Color.White.copy(alpha = 0.9f), CircleShape)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Lancer sur YouTube",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (!videoTitle.isNullOrBlank()) {
                Text(
                    text = videoTitle,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Bouton unique et direct : Regarder sur YouTube
            Button(
                onClick = { launchVideoPlayer(context, cleanId, articleUrl) },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE50914),
                    contentColor = Color.White
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(42.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Regarder sur YouTube",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

