package com.example.ui.screens

import android.net.Uri
import android.view.ViewGroup
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.ui.components.SamLogo
import com.example.ui.theme.SamPrimary
import kotlinx.coroutines.launch

sealed class OnboardingStepData {
    data class ImageStep(
        val title: String,
        val subtitle: String,
        val description: String,
        val badge: String,
        val imageRes: Int
    ) : OnboardingStepData()

    data class VideoStep(
        val title: String,
        val subtitle: String,
        val description: String,
        val badge: String,
        val videoResId: Int
    ) : OnboardingStepData()
}

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    // 3 étapes complètes en plein écran :
    // 1. Image plein écran : Comment traduire -> Bouton Suivant
    // 2. Image plein écran : Prises de traduction vocale et outils -> Bouton Suivant
    // 3. Vidéo plein écran : Démonstration officielle -> Bouton Commencer (ouvre l'application)
    val steps = remember {
        listOf(
            OnboardingStepData.ImageStep(
                title = "Traduisez Facilement",
                subtitle = "Français, Mooré, Dioula & Haoussa",
                description = "Saisissez votre texte ou vos phrases pour obtenir une traduction instantanée et certifiée dans nos langues locales.",
                badge = "Traductions Précises ✨",
                imageRes = R.drawable.app_ui_mockup_1790095915892
            ),
            OnboardingStepData.ImageStep(
                title = "Prises de Traduction & Outils",
                subtitle = "Mode vocal, audio et fonctionnalités",
                description = "Dictez vos phrases avec le micro, écoutez la prononciation burkinabè authentique, accédez aux quiz et explorez notre patrimoine culturel.",
                badge = "Mode Vocal & Audio 🎙️",
                imageRes = R.drawable.bonjour_2
            ),
            OnboardingStepData.VideoStep(
                title = "Vidéo de Présentation",
                subtitle = "Découvrez SAM AI en action",
                description = "Regardez la démonstration complète de l'application et cliquez sur Commencer pour débuter votre expérience.",
                badge = "Démonstration Officielle 🎬",
                videoResId = R.raw.presentation_video
            )
        )
    }

    val pagerState = rememberPagerState(pageCount = { steps.size })
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Pager plein écran (chaque page remplit 100% de l'écran)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            when (val step = steps[pageIndex]) {
                is OnboardingStepData.ImageStep -> {
                    FullScreenImageSlide(step = step)
                }
                is OnboardingStepData.VideoStep -> {
                    FullScreenVideoSlide(
                        step = step,
                        isActive = pagerState.currentPage == pageIndex
                    )
                }
            }
        }

        // Overlay Supérieur : En-tête avec Logo et Bouton Passer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.55f),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SamLogo(size = 30.dp, elevation = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SAM AI",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
            }

            if (pagerState.currentPage < steps.size - 1) {
                Surface(
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    onClick = onFinish,
                    modifier = Modifier.testTag("skip_onboarding_button")
                ) {
                    Text(
                        text = "Passer",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // Overlay Inférieur : Contenu textuel, indicateurs et bouton Suivant / Commencer
        val currentStep = steps[pagerState.currentPage]
        val isLastPage = pagerState.currentPage == steps.size - 1

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.65f),
                            Color.Black.copy(alpha = 0.95f),
                            Color.Black
                        ),
                        startY = 0f
                    )
                )
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Badge
                val badgeText = when (currentStep) {
                    is OnboardingStepData.ImageStep -> currentStep.badge
                    is OnboardingStepData.VideoStep -> currentStep.badge
                }
                Surface(
                    color = SamPrimary.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Titre
                val titleText = when (currentStep) {
                    is OnboardingStepData.ImageStep -> currentStep.title
                    is OnboardingStepData.VideoStep -> currentStep.title
                }
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        fontSize = 24.sp
                    ),
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Sous-titre
                val subtitleText = when (currentStep) {
                    is OnboardingStepData.ImageStep -> currentStep.subtitle
                    is OnboardingStepData.VideoStep -> currentStep.subtitle
                }
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = SamPrimary,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        fontSize = 15.sp
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Description
                val descText = when (currentStep) {
                    is OnboardingStepData.ImageStep -> currentStep.description
                    is OnboardingStepData.VideoStep -> currentStep.description
                }
                Text(
                    text = descText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                        fontSize = 13.5.sp
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Indicateurs (dots)
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    repeat(steps.size) { iteration ->
                        val isSelected = pagerState.currentPage == iteration
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .height(8.dp)
                                .width(if (isSelected) 30.dp else 8.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) SamPrimary else Color.White.copy(alpha = 0.35f))
                        )
                    }
                }

                // Bouton Suivant (étapes 1 & 2) ou Commencer (étape 3 avec la vidéo)
                Button(
                    onClick = {
                        if (!isLastPage) {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        } else {
                            // Ouvre l'application elle-même !
                            onFinish()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("onboarding_action_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SamPrimary,
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    if (!isLastPage) {
                        Text(
                            "Suivant",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Suivant",
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Text(
                            "Commencer",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Commencer",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Image en PLEIN ÉCRAN total (Immersive)
 */
@Composable
private fun FullScreenImageSlide(step: OnboardingStepData.ImageStep) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(step.imageRes)
                .crossfade(600)
                .build(),
            contentDescription = step.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Dégradé sombre pour sublimer le contraste
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.55f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.75f)
                        )
                    )
                )
        )
    }
}

/**
 * Vidéo en PLEIN ÉCRAN total (Immersive)
 */
@Composable
private fun FullScreenVideoSlide(
    step: OnboardingStepData.VideoStep,
    isActive: Boolean
) {
    var isPlaying by remember { mutableStateOf(false) }
    var isPrepared by remember { mutableStateOf(false) }
    var videoViewInstance by remember { mutableStateOf<VideoView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                videoViewInstance?.stopPlayback()
            } catch (_: Throwable) {}
        }
    }

    LaunchedEffect(isActive, videoViewInstance) {
        if (isActive && isPrepared) {
            videoViewInstance?.start()
            isPlaying = true
        } else if (!isActive && isPlaying) {
            videoViewInstance?.pause()
            isPlaying = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Lecteur Vidéo Plein Écran
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                VideoView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    val uri = Uri.parse("android.resource://${ctx.packageName}/${step.videoResId}")
                    setVideoURI(uri)
                    setOnPreparedListener { mp ->
                        isPrepared = true
                        mp.isLooping = true
                        if (isActive) {
                            start()
                            isPlaying = true
                        }
                    }
                    setOnErrorListener { _, _, _ ->
                        true
                    }
                    videoViewInstance = this
                }
            }
        )

        // Dégradé léger en haut et en bas pour garantir la lisibilité des textes
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.45f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.75f)
                        )
                    )
                )
        )

        // Bouton tactile Play / Pause au centre (s'affiche si la vidéo est en pause)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable {
                    if (isPlaying) {
                        videoViewInstance?.pause()
                        isPlaying = false
                    } else {
                        videoViewInstance?.start()
                        isPlaying = true
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (!isPlaying && isPrepared) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.70f),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White.copy(alpha = 0.5f)),
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Lire la vidéo",
                            tint = Color.White,
                            modifier = Modifier.size(42.dp)
                        )
                    }
                }
            }
        }
    }
}
