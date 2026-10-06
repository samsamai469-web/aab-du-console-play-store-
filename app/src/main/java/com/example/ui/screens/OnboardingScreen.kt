package com.example.ui.screens

import android.media.MediaPlayer
import android.net.Uri
import android.view.ViewGroup
import android.widget.VideoView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.ui.theme.SamPrimary
import com.example.ui.theme.SamPrimaryLight
import kotlinx.coroutines.launch

/**
 * Présentation officielle de l'application SAM Traduction en 3 étapes :
 * 1. Bienvenue sur Sam Traduction (Présentation du projet, patrimoine et langues)
 * 2. Comment fonctionne l'application ? (4 fonctionnalités phares)
 * 3. Vidéo de démonstration en plein écran total (zoomée pour remplir 100% de l'écran) + Bouton Commencer
 *
 * Toutes les couleurs bleues des anciennes maquettes ont été adaptées à la couleur orange/rouge-orangé
 * officielle de l'application (SamPrimary).
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0B0D))
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> WelcomePage(
                    onNext = {
                        scope.launch { pagerState.animateScrollToPage(1) }
                    }
                )
                1 -> HowItWorksPage(
                    onBack = {
                        scope.launch { pagerState.animateScrollToPage(0) }
                    },
                    onNext = {
                        scope.launch { pagerState.animateScrollToPage(2) }
                    }
                )
                2 -> FullScreenVideoPage(
                    isActive = pagerState.currentPage == 2,
                    onBack = {
                        scope.launch { pagerState.animateScrollToPage(1) }
                    },
                    onStart = onFinish
                )
            }
        }
    }
}

/**
 * Indicateur à 3 points au sommet :
 * Le point actif est une barre allongée orange (SamPrimary).
 */
@Composable
private fun OnboardingDotsIndicator(
    currentPage: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val isSelected = currentPage == index
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .height(5.dp)
                    .width(if (isSelected) 28.dp else 7.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) SamPrimary else Color.White.copy(alpha = 0.25f)
                    )
            )
        }
    }
}

// =============================================================================
// PAGE 1 : BIENVENUE SUR SAM TRADUCTION
// =============================================================================
@Composable
private fun WelcomePage(onNext: () -> Unit) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(6.dp))

            // 3 Dots indicator
            OnboardingDotsIndicator(currentPage = 0)

            Spacer(modifier = Modifier.height(20.dp))

            // Titre Principal
            Text(
                text = "Bienvenue sur Sam\nTraduction",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 26.sp,
                    lineHeight = 32.sp
                ),
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Sous-titre
            Text(
                text = "Intelligence Artificielle & Langues du Burkina\nFaso",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                    lineHeight = 18.sp
                ),
                color = SamPrimaryLight,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(22.dp))

            // 1. Carte Descriptif du Projet
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF131720),
                border = BorderStroke(1.dp, Color(0xFF222836))
            ) {
                Text(
                    text = "Votre compagnon intelligent dédié à l'échange, à la communication et à la traduction instantanée, conçu pour valoriser les langues et le patrimoine culturel burkinabè.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        color = Color.White.copy(alpha = 0.90f)
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Carte Images Patrimoine (Famille + Faso Dan Fani)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF131720),
                border = BorderStroke(1.dp, Color(0xFF222836))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Image Famille burkinabè
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(R.drawable.img_burkinabe_family_1786879044801)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Famille burkinabè",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .weight(1f)
                                .height(125.dp)
                                .clip(RoundedCornerShape(14.dp))
                        )

                        // Image Faso Dan Fani tissé traditionnel
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(R.drawable.img_faso_dan_fani_1786953081141)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Faso Dan Fani",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .weight(1f)
                                .height(125.dp)
                                .clip(RoundedCornerShape(14.dp))
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Valorisation du patrimoine et des langues du Faso",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        ),
                        color = SamPrimaryLight,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Pastille Langues disponibles
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF131720),
                border = BorderStroke(1.dp, Color(0xFF222836))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = null,
                        tint = SamPrimary,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Mooré • Dioula • Français • Anglais • Allemand",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.5.sp
                        ),
                        color = Color.White.copy(alpha = 0.95f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Bouton Suivant (Orange / Rouge-orangé de l'app)
        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("btn_welcome_next"),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SamPrimary,
                contentColor = Color.White
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) {
            Text(
                text = "SUIVANT",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    letterSpacing = 0.5.sp
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Suivant",
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// =============================================================================
// PAGE 2 : COMMENT FONCTIONNE L'APPLICATION ?
// =============================================================================
@Composable
private fun HowItWorksPage(
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // En-tête : Flèche retour + 3 Dots centrés
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Retour",
                        tint = Color.White
                    )
                }

                OnboardingDotsIndicator(
                    currentPage = 1,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Titre Principal
            Text(
                text = "Comment fonctionne\nl'application ?",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp,
                    lineHeight = 30.sp
                ),
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Sous-titre
            Text(
                text = "4 fonctionnalités simples pour vous accompagner",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.70f)
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 4 Cartes de fonctionnalités
            FeatureItemCard(
                imageRes = R.drawable.img_burkinabe_market_1786879084962,
                badgeIcon = Icons.Default.Translate,
                title = "Traduire des langues",
                description = "Traduisez rapidement mots, phrases et expressions entre le Français, le Mooré, le Dioula, l'Anglais et l'Allemand."
            )

            Spacer(modifier = Modifier.height(10.dp))

            FeatureItemCard(
                imageRes = R.drawable.img_laafi_bala_friends_1786881102699,
                badgeIcon = Icons.Default.SwapHoriz,
                title = "Langue source & Langue cible",
                description = "Sélectionnez vos langues en un instant et permutez-les en un seul clic selon vos besoins."
            )

            Spacer(modifier = Modifier.height(10.dp))

            FeatureItemCard(
                imageRes = R.drawable.img_burkinabe_child_1786953052232,
                badgeIcon = Icons.Default.Mic,
                title = "Traduction vocale & Prononciation",
                description = "Dictez vos messages au micro et écoutez la lecture audio authentique de vos traductions."
            )

            Spacer(modifier = Modifier.height(10.dp))

            FeatureItemCard(
                imageRes = R.drawable.img_burkinabe_school_1786881851073,
                badgeIcon = Icons.Default.History,
                title = "Historique personnel sécurisé",
                description = "Toutes vos traductions et échanges sont automatiquement sauvegardés et consultables à tout moment."
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Bouton Suivant (Orange / Rouge-orangé de l'app)
        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("btn_how_it_works_next"),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SamPrimary,
                contentColor = Color.White
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) {
            Text(
                text = "SUIVANT",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    letterSpacing = 0.5.sp
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Suivant",
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Carte de fonctionnalité individuelle avec vignette + petit badge icône
 */
@Composable
private fun FeatureItemCard(
    imageRes: Int,
    badgeIcon: ImageVector,
    title: String,
    description: String
) {
    val context = LocalContext.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF131720),
        border = BorderStroke(1.dp, Color(0xFF222836))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Vignette Image avec pastille d'icône en bas à droite
            Box(
                modifier = Modifier.size(62.dp)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imageRes)
                        .crossfade(true)
                        .build(),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                )

                // Badge icône en bas à droite
                Surface(
                    shape = CircleShape,
                    color = SamPrimary,
                    modifier = Modifier
                        .size(22.dp)
                        .align(Alignment.BottomEnd)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = badgeIcon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Titre et Description
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp
                    ),
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = Color.White.copy(alpha = 0.80f)
                    )
                )
            }
        }
    }
}

// =============================================================================
// PAGE 3 : VIDÉO DE PRÉSENTATION EN PLEIN ÉCRAN TOTAL + BOUTON COMMENCER
// =============================================================================
@Composable
private fun FullScreenVideoPage(
    isActive: Boolean,
    onBack: () -> Unit,
    onStart: () -> Unit
) {
    var isPlaying by remember { mutableStateOf(false) }
    var isPrepared by remember { mutableStateOf(false) }
    var videoViewInstance by remember { mutableStateOf<VideoView?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    try {
                        videoViewInstance?.pause()
                        isPlaying = false
                    } catch (_: Throwable) {}
                }
                Lifecycle.Event.ON_RESUME -> {
                    try {
                        if (isPrepared && isActive) {
                            videoViewInstance?.start()
                            isPlaying = true
                        }
                    } catch (_: Throwable) {}
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try {
                videoViewInstance?.stopPlayback()
            } catch (_: Throwable) {}
        }
    }

    LaunchedEffect(isActive, videoViewInstance, isPrepared) {
        if (isActive && isPrepared) {
            try {
                videoViewInstance?.start()
                isPlaying = true
            } catch (_: Throwable) {}
        } else if (!isActive && isPlaying) {
            try {
                videoViewInstance?.pause()
                isPlaying = false
            } catch (_: Throwable) {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. Vidéo en plein écran total absolu (zoomée pour remplir 100% de l'écran sans bandes noires)
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                VideoView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    val uri = Uri.parse("android.resource://${ctx.packageName}/${R.raw.presentation_video}")
                    setVideoURI(uri)
                    setOnPreparedListener { mp ->
                        try {
                            // Remplit et zoome la vidéo sur 100% de l'écran
                            mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                        } catch (_: Throwable) {}
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

        // 2. Zone tactile pour Play / Pause
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
                    border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.6f)),
                    modifier = Modifier.size(76.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Lire la vidéo",
                            tint = Color.White,
                            modifier = Modifier.size(46.dp)
                        )
                    }
                }
            }
        }

        // 3. En-tête discret supérieur : Bouton retour + 3e dot actif
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Retour",
                    tint = Color.White
                )
            }

            OnboardingDotsIndicator(
                currentPage = 2,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )

            // Espaceur pour garder le centrage parfait
            Spacer(modifier = Modifier.size(40.dp))
        }

        // 4. Bouton COMMENCER au bas de l'écran en plein écran (Couleur Orange de l'application)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.50f),
                            Color.Black.copy(alpha = 0.90f)
                        )
                    )
                )
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("btn_onboarding_commencer"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SamPrimary,
                    contentColor = Color.White
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
            ) {
                Text(
                    text = "COMMENCER",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        letterSpacing = 0.5.sp
                    )
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
