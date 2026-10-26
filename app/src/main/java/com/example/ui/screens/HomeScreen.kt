package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.imageLoader
import coil.request.ImageRequest
import com.example.R
import com.example.data.local.BurkinaVisualData
import com.example.data.local.VisualMooreItem
import com.example.data.local.BurkinaDioulaVisualData
import com.example.data.local.VisualDioulaItem
import com.example.ui.SamAiViewModel
import com.example.ui.navigation.Screen
import com.example.util.TtsManager
import com.example.util.AppStrings
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: SamAiViewModel,
    ttsManager: TtsManager,
    navController: NavController
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val appLanguage by viewModel.appLanguage.collectAsState()

    // --- ÉTAT MODAL SAM AI ASSISTANT (GOOGLE GEMINI) ---
    var showSamAiModal by remember { mutableStateOf(false) }
    var initialAiQuestion by remember { mutableStateOf<String?>(null) }

    // --- 1. ÉTAT DU CARROUSEL MOORÉ ---
    val mooreVisualItems by viewModel.visualItems.collectAsState()
    val selectedMooreCategory by viewModel.selectedVisualCategory.collectAsState()
    var mooreIndex by remember { mutableIntStateOf(0) }
    var mooreTimerKey by remember { mutableIntStateOf(0) }

    // Synchronisation de l'index Mooré quand la catégorie change
    LaunchedEffect(selectedMooreCategory, mooreVisualItems) {
        mooreIndex = 0
        mooreTimerKey++
    }

    // Défilement automatique du carrousel Mooré
    LaunchedEffect(mooreIndex, mooreTimerKey, mooreVisualItems.size) {
        if (mooreVisualItems.size > 1) {
            delay(4500)
            mooreIndex = (mooreIndex + 1) % mooreVisualItems.size
        }
    }

    // Préchargement des images Mooré
    LaunchedEffect(mooreIndex, mooreVisualItems) {
        if (mooreVisualItems.isNotEmpty()) {
            val nextIdx = (mooreIndex + 1) % mooreVisualItems.size
            val prevIdx = (mooreIndex - 1 + mooreVisualItems.size) % mooreVisualItems.size
            listOf(nextIdx, prevIdx).forEach { idx ->
                val itm = mooreVisualItems[idx]
                val source = itm.localDrawable ?: itm.imageUrl.ifEmpty { null }
                if (source != null) {
                    val request = ImageRequest.Builder(context)
                        .data(source)
                        .crossfade(false)
                        .build()
                    context.imageLoader.enqueue(request)
                }
            }
        }
    }

    // --- 2. ÉTAT DU CARROUSEL DIOULA (100% FRANÇAIS EN RÉFÉRENCE) ---
    val dioulaVisualItems by viewModel.dioulaVisualItems.collectAsState()
    val selectedDioulaCategory by viewModel.selectedDioulaCategory.collectAsState()
    var dioulaIndex by remember { mutableIntStateOf(0) }
    var dioulaTimerKey by remember { mutableIntStateOf(0) }

    // Synchronisation de l'index Dioula quand la catégorie change
    LaunchedEffect(selectedDioulaCategory, dioulaVisualItems) {
        dioulaIndex = 0
        dioulaTimerKey++
    }

    // Défilement automatique du carrousel Dioula
    LaunchedEffect(dioulaIndex, dioulaTimerKey, dioulaVisualItems.size) {
        if (dioulaVisualItems.size > 1) {
            delay(4500)
            dioulaIndex = (dioulaIndex + 1) % dioulaVisualItems.size
        }
    }

    // Préchargement des images Dioula
    LaunchedEffect(dioulaIndex, dioulaVisualItems) {
        if (dioulaVisualItems.isNotEmpty()) {
            val nextIdx = (dioulaIndex + 1) % dioulaVisualItems.size
            val prevIdx = (dioulaIndex - 1 + dioulaVisualItems.size) % dioulaVisualItems.size
            listOf(nextIdx, prevIdx).forEach { idx ->
                val itm = dioulaVisualItems[idx]
                val source = itm.localDrawable ?: itm.imageUrl.ifEmpty { null }
                if (source != null) {
                    val request = ImageRequest.Builder(context)
                        .data(source)
                        .crossfade(false)
                        .build()
                    context.imageLoader.enqueue(request)
                }
            }
        }
    }

    val userProfile by viewModel.userProfile.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        com.example.ui.components.SamLogo(
                            size = 38.dp,
                            elevation = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "SAM AI",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = AppStrings.appSubtitle(appLanguage),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    // Bouton Profil / Connexion rapide
                    IconButton(
                        onClick = { navController.navigate(Screen.Profile) },
                        modifier = Modifier.testTag("btn_topbar_profile")
                    ) {
                        if (userProfile.isLoggedIn && !userProfile.photoUrl.isNullOrBlank()) {
                            coil.compose.AsyncImage(
                                model = userProfile.photoUrl,
                                contentDescription = "Mon Profil",
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = if (userProfile.isLoggedIn) Icons.Default.AccountCircle else Icons.Default.PersonOutline,
                                contentDescription = if (userProfile.isLoggedIn) "Mon Profil (${userProfile.name})" else "Connexion / Profil",
                                tint = if (userProfile.isLoggedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = { navController.navigate(Screen.About) }) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "À propos",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    initialAiQuestion = null
                    showSamAiModal = true
                },
                icon = {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = "SAM AI Assistant",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                },
                text = {
                    Text(
                        text = "SAM AI",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .testTag("fab_sam_ai_gemini")
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Bannière d'Accueil : Bonjour, Bienvenue !
            com.example.ui.components.BonjourWelcomeBanner()
            Spacer(modifier = Modifier.height(14.dp))

            // Categories Filter Chips - Mooré
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(BurkinaVisualData.categories) { category ->
                    val isSelected = selectedMooreCategory.equals(category, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            viewModel.setVisualCategory(category)
                            mooreIndex = 0
                            mooreTimerKey++
                        },
                        label = {
                            Text(
                                text = AppStrings.categoryName(category, appLanguage),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                            )
                        },
                        leadingIcon = if (isSelected) {
                            {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Grand Central Photo Showcase - Mooré (Format panoramique large)
            if (mooreVisualItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(145.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                val safeIndex = mooreIndex.coerceIn(0, mooreVisualItems.size - 1)
                val currentItem = mooreVisualItems[safeIndex]

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(145.dp)
                        .pointerInput(mooreVisualItems.size) {
                            detectHorizontalDragGestures { _, dragAmount ->
                                if (dragAmount > 40) { // Glisser vers la droite -> Précédent
                                    mooreIndex = (mooreIndex - 1 + mooreVisualItems.size) % mooreVisualItems.size
                                    mooreTimerKey++
                                } else if (dragAmount < -40) { // Glisser vers la gauche -> Suivant
                                    mooreIndex = (mooreIndex + 1) % mooreVisualItems.size
                                    mooreTimerKey++
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = currentItem,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(350)) togetherWith fadeOut(animationSpec = tween(250))
                        },
                        label = "MoorePhotoCarouselTransition",
                        modifier = Modifier.fillMaxSize()
                    ) { targetItem ->
                        VisualMooreCard(
                            item = targetItem,
                            onPlayAudio = {
                                ttsManager.speak(targetItem.word, "Mooré")
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Carousel Counter & Navigation Controls - Mooré
            if (mooreVisualItems.isNotEmpty()) {
                val currentIdx = mooreIndex.coerceIn(0, mooreVisualItems.size - 1)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            onClick = {
                                mooreIndex = (mooreIndex - 1 + mooreVisualItems.size) % mooreVisualItems.size
                                mooreTimerKey++
                            },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Précédent",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }

                        Text(
                            text = "Mooré : ${currentIdx + 1} / ${mooreVisualItems.size}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Surface(
                            onClick = {
                                mooreIndex = (mooreIndex + 1) % mooreVisualItems.size
                                mooreTimerKey++
                            },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Suivant",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        val maxDots = 6
                        val start = (currentIdx - maxDots / 2).coerceAtLeast(0)
                        val end = (start + maxDots).coerceAtMost(mooreVisualItems.size)
                        for (i in start until end) {
                            val isActive = i == currentIdx
                            Box(
                                modifier = Modifier
                                    .height(5.dp)
                                    .width(if (isActive) 16.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isActive) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant
                                    )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ====================================================================
            // SECTION 2 : DIOULA
            // ====================================================================
            // Categories Filter Chips - Dioula
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(BurkinaDioulaVisualData.categories) { category ->
                    val isSelected = selectedDioulaCategory.equals(category, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            viewModel.setDioulaVisualCategory(category)
                            dioulaIndex = 0
                            dioulaTimerKey++
                        },
                        label = {
                            Text(
                                text = AppStrings.categoryName(category, appLanguage),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                            )
                        },
                        leadingIcon = if (isSelected) {
                            {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Grand Central Photo Showcase - Dioula (Format panoramique large)
            if (dioulaVisualItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(145.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                val safeIndex = dioulaIndex.coerceIn(0, dioulaVisualItems.size - 1)
                val currentItem = dioulaVisualItems[safeIndex]

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(145.dp)
                        .pointerInput(dioulaVisualItems.size) {
                            detectHorizontalDragGestures { _, dragAmount ->
                                if (dragAmount > 40) { // Glisser vers la droite -> Précédent
                                    dioulaIndex = (dioulaIndex - 1 + dioulaVisualItems.size) % dioulaVisualItems.size
                                    dioulaTimerKey++
                                } else if (dragAmount < -40) { // Glisser vers la gauche -> Suivant
                                    dioulaIndex = (dioulaIndex + 1) % dioulaVisualItems.size
                                    dioulaTimerKey++
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = currentItem,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(350)) togetherWith fadeOut(animationSpec = tween(250))
                        },
                        label = "DioulaPhotoCarouselTransition",
                        modifier = Modifier.fillMaxSize()
                    ) { targetItem ->
                        VisualDioulaCard(
                            item = targetItem,
                            onPlayAudio = {
                                ttsManager.speak(targetItem.dioulaTranslation, "Dioula")
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Carousel Counter & Navigation Controls - Dioula
            if (dioulaVisualItems.isNotEmpty()) {
                val currentIdx = dioulaIndex.coerceIn(0, dioulaVisualItems.size - 1)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            onClick = {
                                dioulaIndex = (dioulaIndex - 1 + dioulaVisualItems.size) % dioulaVisualItems.size
                                dioulaTimerKey++
                            },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Précédent",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }

                        Text(
                            text = "Dioula : ${currentIdx + 1} / ${dioulaVisualItems.size}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Surface(
                            onClick = {
                                dioulaIndex = (dioulaIndex + 1) % dioulaVisualItems.size
                                dioulaTimerKey++
                            },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Suivant",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        val maxDots = 6
                        val start = (currentIdx - maxDots / 2).coerceAtLeast(0)
                        val end = (start + maxDots).coerceAtMost(dioulaVisualItems.size)
                        for (i in start until end) {
                            val isActive = i == currentIdx
                            Box(
                                modifier = Modifier
                                    .height(5.dp)
                                    .width(if (isActive) 16.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isActive) Color(0xFF00897B)
                                        else MaterialTheme.colorScheme.outlineVariant
                                    )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

        // Modal Assistant SAM AI Google Gemini
        if (showSamAiModal) {
            com.example.ui.components.SamAiGeminiAssistantModal(
                viewModel = viewModel,
                ttsManager = ttsManager,
                onDismiss = {
                    viewModel.clearSamAiChat(deleteFromFirebase = true)
                    showSamAiModal = false
                    initialAiQuestion = null
                },
                initialQuestion = initialAiQuestion
            )
        }
    }
}

@Composable
fun VisualMooreCard(
    item: VisualMooreItem,
    onPlayAudio: () -> Unit,
    onImageLoaded: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 2.dp, vertical = 2.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Partie GAUCHE : Mot Mooré, Traduction Français, Audio et Catégorie
            Column(
                modifier = Modifier
                    .weight(1.05f)
                    .fillMaxHeight()
                    .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.Start
            ) {
                // Badge Catégorie haute lisibilité
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = item.category,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // MOT MOORÉ PRINCIPAL - Écriture haute visibilité
                Text(
                    text = item.word,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp),
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )

                // TRADUCTION / SENS FRANÇAIS - Pastille Rouge-Orangé
                if (item.frenchMeaning.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = item.frenchMeaning,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                            maxLines = 1
                        )
                    }
                }

                // PRONONCIATION + BOUTON AUDIO
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Surface(
                        onClick = onPlayAudio,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Écouter la prononciation Mooré",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = item.pronunciation,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }
            }

            // 2. Partie DROITE : Image panoramique large plein cadre sans bords noirs
            Box(
                modifier = Modifier
                    .weight(1.8f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                val primarySource: Any = item.localDrawable
                    ?: item.imageUrl.ifEmpty { R.drawable.img_burkinabe_mother_1786879056257 }

                coil.compose.AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(primarySource)
                        .bitmapConfig(android.graphics.Bitmap.Config.ARGB_8888)
                        .precision(coil.size.Precision.EXACT)
                        .allowHardware(true)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.word,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                    onSuccess = { onImageLoaded?.invoke() }
                )
            }
        }
    }
}

/**
 * Carte Dioula panoramique large :
 * GAUCHE (Traductions & Audio) ➔ DROITE (Image panoramique plein cadre)
 */
@Composable
fun VisualDioulaCard(
    item: VisualDioulaItem,
    onPlayAudio: () -> Unit,
    onImageLoaded: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 2.dp, vertical = 2.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Partie GAUCHE : Mot Dioula, Référence Français, Audio et Catégorie
            Column(
                modifier = Modifier
                    .weight(1.05f)
                    .fillMaxHeight()
                    .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.Start
            ) {
                // Badge Catégorie haute lisibilité
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = item.category,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // TRADUCTION DIOULA PRINCIPALE - Écriture haute visibilité
                Text(
                    text = item.dioulaTranslation,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp),
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )

                // RÉFÉRENCE FRANÇAIS - Pastille Rouge-Orangé
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = item.frenchWord,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                        maxLines = 1
                    )
                }

                // PRONONCIATION DIOULA + BOUTON AUDIO
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Surface(
                        onClick = onPlayAudio,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Écouter la prononciation Dioula",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = item.dioulaPronunciation,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }
            }

            // 2. Partie DROITE : Image panoramique large plein cadre sans bords noirs
            Box(
                modifier = Modifier
                    .weight(1.8f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                val primarySource: Any = item.localDrawable
                    ?: item.imageUrl.ifEmpty { R.drawable.img_burkinabe_mother_1786879056257 }

                coil.compose.AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(primarySource)
                        .bitmapConfig(android.graphics.Bitmap.Config.ARGB_8888)
                        .precision(coil.size.Precision.EXACT)
                        .allowHardware(true)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.frenchWord,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                    onSuccess = { onImageLoaded?.invoke() }
                )
            }
        }
    }
}