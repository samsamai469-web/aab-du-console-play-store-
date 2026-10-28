package com.example.ui.screens

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.data.engine.VideoQuizData
import com.example.data.engine.VideoQuizData.CategoryDef
import com.example.data.engine.VideoQuizData.VideoQuizItem
import com.example.data.engine.VideoQuizEngine
import com.example.ui.components.CommentsBottomSheet
import com.example.ui.components.VideoPlayerCard
import com.example.util.TtsManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Plateforme Maître « QUIZ BURKINA » 🇧🇫
 * Duolingo × TikTok × Quizizz × Kahoot
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoQuizScreen(
    ttsManager: TtsManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val quizEngine = remember { VideoQuizEngine(context) }
    val coroutineScope = rememberCoroutineScope()

    // Splash animation d'ouverture de 1.2s
    var isSplashOpening by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(1200)
        isSplashOpening = false
    }

    if (isSplashOpening) {
        QuizBurkinaSplashView()
        return
    }

    // États du moteur
    val userXp by quizEngine.userXp.collectAsState()
    val lives by quizEngine.lives.collectAsState()
    val currentStreak by quizEngine.currentStreak.collectAsState()
    val maxStreak by quizEngine.maxStreak.collectAsState()
    val quizzesPlayed by quizEngine.quizzesPlayed.collectAsState()
    val correctAnswersCount by quizEngine.correctAnswersCount.collectAsState()
    val likedQuizIds by quizEngine.likedQuizIds.collectAsState()
    val bookmarkedQuizIds by quizEngine.bookmarkedQuizIds.collectAsState()
    val quizList by quizEngine.quizItems.collectAsState()
    val newsQuizzes by quizEngine.newsQuizzes.collectAsState()
    val comments by quizEngine.comments.collectAsState()
    val badges by quizEngine.badges.collectAsState()
    val unlockedLevel by quizEngine.unlockedLevel.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Feed Vidéo, 1: Explorer, 2: Défi du Jour, 3: Le Ditanyè, 4: Classement, 5: Profil, 6: Figures
    var currentFeedIndex by remember { mutableIntStateOf(0) }
    var showCommentsSheet by remember { mutableStateOf(false) }
    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }
    var selectedLevelFilter by remember { mutableStateOf<Int?>(null) }
    var showRefillLivesDialog by remember { mutableStateOf(false) }
    var showLevelSuccessDialog by remember { mutableStateOf<Int?>(null) }
    var lockedLevelAlert by remember { mutableStateOf<Int?>(null) }

    // Arrêter immédiatement la synthèse vocale quand on change d'onglet ou qu'on quitte Quiz Burkina
    DisposableEffect(selectedTab) {
        if (selectedTab != 0) {
            ttsManager.stop()
        }
        onDispose {
            ttsManager.stop()
        }
    }

    // Dialog Niveau Verrouillé
    if (lockedLevelAlert != null) {
        val reqLevel = (lockedLevelAlert!! - 1).coerceAtLeast(1)
        AlertDialog(
            onDismissRequest = { lockedLevelAlert = null },
            icon = { Text("🔒", fontSize = 32.sp) },
            title = { Text("Niveau ${lockedLevelAlert} Verrouillé !", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) },
            text = {
                Text(
                    "Pour accéder au Niveau ${lockedLevelAlert}, vous devez d'abord réussir sans aucune faute les 4 questions du Niveau $reqLevel (4/4 sans erreur).",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetLevel = reqLevel
                        lockedLevelAlert = null
                        selectedLevelFilter = targetLevel
                        selectedCategoryFilter = null
                        currentFeedIndex = 0
                        selectedTab = 0
                    }
                ) {
                    Text("Jouer au Niveau $reqLevel")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { lockedLevelAlert = null }) {
                    Text("Compris")
                }
            }
        )
    }

    // Dialog Célébration Victoire Niveau (4/4 réussi & déblocage)
    if (showLevelSuccessDialog != null) {
        val completedLvl = showLevelSuccessDialog!!
        val hasNextLevel = completedLvl < 7
        val nextLvl = completedLvl + 1

        AlertDialog(
            onDismissRequest = { showLevelSuccessDialog = null },
            icon = { Text(if (completedLvl == 7) "👑" else "🏆", fontSize = 42.sp) },
            title = {
                Text(
                    text = if (completedLvl == 7) "MAÎTRE DU FASO 🇧🇫" else "NIVEAU $completedLvl RÉUSSI !",
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    color = Color(0xFF2E7D32)
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Félicitations ! Vous avez réussi les 4 questions sans aucune faute (Score Parfait 4/4).",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFFD54F).copy(alpha = 0.25f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB300))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⭐", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("+500 XP Bonus de Maîtrise", fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                        }
                    }

                    if (hasNextLevel) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF2E7D32).copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E7D32))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🔓", fontSize = 18.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Le Niveau $nextLvl est désormais Débloqué !",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1B5E20)
                                )
                            }
                        }
                    } else {
                        Text(
                            "Vous avez complété l'intégralité des 7 Niveaux de Quiz Burkina ! Vous êtes une fierté pour la nation.",
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE65100)
                        )
                    }
                }
            },
            confirmButton = {
                if (hasNextLevel) {
                    Button(
                        onClick = {
                            val next = nextLvl
                            showLevelSuccessDialog = null
                            selectedLevelFilter = next
                            selectedCategoryFilter = null
                            currentFeedIndex = 0
                            selectedTab = 0
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) {
                        Text("🚀 Lancer Niveau $nextLvl", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = {
                            showLevelSuccessDialog = null
                            selectedTab = 5 // Profil
                        }
                    ) {
                        Text("🏅 Voir Profil & Titres")
                    }
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showLevelSuccessDialog = null
                        selectedTab = 1 // Explorer
                    }
                ) {
                    Text("📋 Tous les Niveaux")
                }
            }
        )
    }

    // Dialog recharger les vies
    if (showRefillLivesDialog) {
        AlertDialog(
            onDismissRequest = { showRefillLivesDialog = false },
            title = { Text("Recharger vos Vies ❤️", fontWeight = FontWeight.Bold) },
            text = { Text("Vous disposez de 5 vies. Chaque réponse incorrecte retire 1 vie. Les vies se régénèrent automatiquement ou peuvent être rechargées maintenant.") },
            confirmButton = {
                Button(
                    onClick = {
                        quizEngine.refillLives()
                        showRefillLivesDialog = false
                    }
                ) {
                    Text("Recharger 5 Vies Gratuitement")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showRefillLivesDialog = false }) {
                    Text("Fermer")
                }
            }
        )
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = {
                        ttsManager.stop()
                        selectedTab = 0
                        selectedLevelFilter = null
                        selectedCategoryFilter = null
                        currentFeedIndex = 0
                    },
                    icon = { Icon(Icons.Default.PlayCircle, contentDescription = "Feed Actu 2026") },
                    label = { Text("Actu 2026", style = MaterialTheme.typography.labelSmall) }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = {
                        ttsManager.stop()
                        selectedTab = 1
                    },
                    icon = { Icon(Icons.Default.Explore, contentDescription = "Explorer") },
                    label = { Text("Explorer", style = MaterialTheme.typography.labelSmall) }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = {
                        ttsManager.stop()
                        selectedTab = 2
                    },
                    icon = { Icon(Icons.Default.Today, contentDescription = "Défi") },
                    label = { Text("Défi 24h", style = MaterialTheme.typography.labelSmall) }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = {
                        ttsManager.stop()
                        selectedTab = 3
                    },
                    icon = { Icon(Icons.Default.MusicVideo, contentDescription = "Le Ditanyè") },
                    label = { Text("Ditanyè", style = MaterialTheme.typography.labelSmall) }
                )
                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = {
                        ttsManager.stop()
                        selectedTab = 4
                    },
                    icon = { Icon(Icons.Default.Leaderboard, contentDescription = "Classement") },
                    label = { Text("Top", style = MaterialTheme.typography.labelSmall) }
                )
                NavigationBarItem(
                    selected = selectedTab == 5,
                    onClick = {
                        ttsManager.stop()
                        selectedTab = 5
                    },
                    icon = { Icon(Icons.Default.AccountCircle, contentDescription = "Profil") },
                    label = { Text("Profil", style = MaterialTheme.typography.labelSmall) }
                )
                NavigationBarItem(
                    selected = selectedTab == 6,
                    onClick = {
                        ttsManager.stop()
                        selectedTab = 6
                    },
                    icon = { Icon(Icons.Default.HistoryEdu, contentDescription = "Personnalités") },
                    label = { Text("Figures", style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                // 1. Fil Vidéo Principal (TikTok style)
                0 -> {
                    val filteredList = when {
                        selectedLevelFilter != null -> quizEngine.getQuizzesForLevel(selectedLevelFilter!!)
                        selectedCategoryFilter != null -> quizEngine.getQuizzesByCategory(selectedCategoryFilter!!)
                        else -> newsQuizzes // 12 Quiz Vidéos inédits et exclusifs sur l'Actualité & Burkina Faso 2026 !
                    }

                    if (filteredList.isNotEmpty()) {
                        val activeQuiz = filteredList[currentFeedIndex.coerceIn(0, filteredList.size - 1)]

                        Box(modifier = Modifier.fillMaxSize()) {
                            VideoPlayerCard(
                                quizItem = activeQuiz,
                                ttsManager = ttsManager,
                                isLiked = likedQuizIds.contains(activeQuiz.id),
                                isBookmarked = bookmarkedQuizIds.contains(activeQuiz.id),
                                lives = lives,
                                currentStreak = currentStreak,
                                levelNumber = selectedLevelFilter,
                                questionIndex = currentFeedIndex + 1,
                                totalQuestionsInLevel = filteredList.size,
                                onLikeToggle = { quizEngine.toggleLike(activeQuiz.id) },
                                onBookmarkToggle = { quizEngine.toggleBookmark(activeQuiz.id) },
                                onAnswerSelected = { _, isCorrect, isFast ->
                                    quizEngine.recordQuizAnswer(activeQuiz, isCorrect, isFast)
                                    if (lives <= 0) {
                                        showRefillLivesDialog = true
                                    }
                                },
                                onNextQuiz = {
                                    if (currentFeedIndex < filteredList.size - 1) {
                                        currentFeedIndex++
                                    } else {
                                        if (selectedLevelFilter != null) {
                                            quizEngine.unlockNextLevel(selectedLevelFilter!!)
                                            showLevelSuccessDialog = selectedLevelFilter
                                        } else {
                                            currentFeedIndex = 0 // Reboucler
                                        }
                                    }
                                },
                                onRestartLevel = {
                                    currentFeedIndex = 0 // Recommence à la question 1
                                },
                                onLevelCompleted = { lvl ->
                                    quizEngine.unlockNextLevel(lvl)
                                    showLevelSuccessDialog = lvl
                                },
                                onBack = onBack,
                                onOpenComments = { showCommentsSheet = true }
                            )

                            // Bannière d'en-tête du flux vidéo
                            val isNewsFeedMode = selectedLevelFilter == null && selectedCategoryFilter == null
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 58.dp),
                                shape = RoundedCornerShape(20.dp),
                                color = if (isNewsFeedMode) Color(0xFF1B5E20).copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.80f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isNewsFeedMode) Color(0xFF81C784).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.3f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isNewsFeedMode) {
                                        Text(
                                            "🇧🇫 Actu & Tendances 2026 (${currentFeedIndex + 1}/${filteredList.size})",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            onClick = { selectedTab = 1 }, // Ouvrir Explorer
                                            shape = CircleShape,
                                            color = Color.White.copy(alpha = 0.22f)
                                        ) {
                                            Text(
                                                "🎯 Niveaux",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFFFFD54F),
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    } else {
                                        val filterTitle = when {
                                            selectedLevelFilter != null -> "🎯 Niveau $selectedLevelFilter (${currentFeedIndex + 1}/${filteredList.size} Vidéos)"
                                            else -> "📚 ${filteredList.firstOrNull()?.categoryTitle ?: "Section"} (${filteredList.size} Quiz)"
                                        }
                                        Text(
                                            filterTitle,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            onClick = {
                                                selectedLevelFilter = null
                                                selectedCategoryFilter = null
                                                currentFeedIndex = 0
                                            },
                                            shape = CircleShape,
                                            color = Color.White.copy(alpha = 0.25f)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Réinitialiser", tint = Color.White, modifier = Modifier.size(12.dp))
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text("Actu 2026", style = MaterialTheme.typography.labelSmall, color = Color.White)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Aucun quiz disponible pour ce filtre.")
                        }
                    }
                }

                // 2. Écran Explorer & Niveaux / Catégories
                1 -> {
                    QuizExplorerView(
                        categories = VideoQuizData.OFFICIAL_CATEGORIES,
                        unlockedLevel = unlockedLevel,
                        onSelectCategory = { catId ->
                            selectedCategoryFilter = catId
                            selectedLevelFilter = null
                            currentFeedIndex = 0
                            selectedTab = 0
                        },
                        onSelectLevel = { level ->
                            selectedLevelFilter = level
                            selectedCategoryFilter = null
                            currentFeedIndex = 0
                            selectedTab = 0
                        },
                        onLockedLevelClick = { lockedLvl ->
                            lockedLevelAlert = lockedLvl
                        },
                        onBack = onBack
                    )
                }

                // 3. Défi du Jour (Double XP)
                2 -> {
                    DailyChallengeView(
                        quizEngine = quizEngine,
                        ttsManager = ttsManager,
                        onBack = onBack
                    )
                }

                // 4. Le Ditanyè Authentique (Hymne National du Burkina Faso)
                3 -> {
                    DitanyeAuthentiqueView(
                        ttsManager = ttsManager,
                        onBack = { selectedTab = 0 }
                    )
                }

                // 5. Classement Mondial
                4 -> {
                    QuizLeaderboardView(userXp = userXp, onBack = onBack)
                }

                // 6. Profil Joueur & Badges
                5 -> {
                    QuizProfileView(
                        userXp = userXp,
                        lives = lives,
                        currentStreak = currentStreak,
                        maxStreak = maxStreak,
                        quizzesPlayed = quizzesPlayed,
                        correctAnswersCount = correctAnswersCount,
                        badges = badges,
                        onRefillLives = { quizEngine.refillLives() },
                        onBack = onBack
                    )
                }

                // 7. Personnalités & Grandes Figures du Faso (20 personnalités & dates clés)
                6 -> {
                    BurkinaPersonalitiesView(
                        ttsManager = ttsManager,
                        onBack = onBack
                    )
                }
            }

            if (showCommentsSheet) {
                CommentsBottomSheet(
                    comments = comments,
                    onAddComment = { text ->
                        quizEngine.addComment(text)
                    },
                    onDismiss = { showCommentsSheet = false }
                )
            }
        }
    }
}

@Composable
fun QuizBurkinaSplashView() {
    val infiniteTransition = rememberInfiniteTransition(label = "splash")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFFB71C1C), Color(0xFFD32F2F), Color(0xFF1B5E20))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.2f),
                modifier = Modifier
                    .size(110.dp)
                    .scale(scale)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("🇧🇫", fontSize = 54.sp)
                }
            }

            Text(
                text = "QUIZ BURKINA",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 2.sp
            )

            Text(
                text = "L'expérience Quiz Vidéo Officielle du Faso",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            CircularProgressIndicator(
                color = Color(0xFFFFD54F),
                strokeWidth = 3.dp,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

data class QuizLevelDef(
    val level: Int,
    val title: String,
    val subtitle: String,
    val icon: String,
    val colorHex: Long,
    val quizCount: Int = 4
)

val OFFICIAL_LEVELS = listOf(
    QuizLevelDef(1, "Niveau 1 : Découverte", "Symboles nationaux, capitale & bases", "🌱", 0xFF2E7D32, 4),
    QuizLevelDef(2, "Niveau 2 : Essentiel", "Patrimoine, langues & coutumes", "🌊", 0xFF0288D1, 4),
    QuizLevelDef(3, "Niveau 3 : Connaisseur", "Monuments, fleuves & gastronomie", "🏛️", 0xFF7B1FA2, 4),
    QuizLevelDef(4, "Niveau 4 : Initié", "Histoire des royaumes & artisanat", "🌾", 0xFFE65100, 4),
    QuizLevelDef(5, "Niveau 5 : Passionné", "FESPACO, ciné & grandes personnalités", "🔥", 0xFFC2185B, 4),
    QuizLevelDef(6, "Niveau 6 : Expert", "Géographie profonde, cascades & parcs", "🛡️", 0xFFD32F2F, 4),
    QuizLevelDef(7, "Niveau 7 : Maître du Faso", "Haute culture, traditions & légendes", "👑", 0xFFFF8F00, 4)
)

@Composable
fun QuizExplorerView(
    categories: List<CategoryDef>,
    unlockedLevel: Int = 1,
    onSelectCategory: (String?) -> Unit,
    onSelectLevel: (Int) -> Unit,
    onLockedLevelClick: (Int) -> Unit = {},
    onBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedExplorerTab by remember { mutableIntStateOf(0) } // 0: 7 Niveaux, 1: 8 Catégories

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
            }
            Text(
                text = "Explorer Quiz Burkina 🇧🇫",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Onglets Niveaux vs Catégories
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                onClick = { selectedExplorerTab = 0 },
                color = if (selectedExplorerTab == 0) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .padding(4.dp)
            ) {
                Text(
                    text = "🎯 7 Niveaux (Progression)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (selectedExplorerTab == 0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            Surface(
                onClick = { selectedExplorerTab = 1 },
                color = if (selectedExplorerTab == 1) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .padding(4.dp)
            ) {
                Text(
                    text = "📚 8 Sections (5 quiz)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (selectedExplorerTab == 1) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(if (selectedExplorerTab == 0) "Rechercher un niveau..." else "Rechercher sport, culture, cuisine...")
            },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Bouton Tout le Fil Mixte
        Surface(
            onClick = { onSelectCategory(null) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🌟", fontSize = 22.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        "Tout le Fil Burkina Faso (40 Vidéos)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Mix complet des 7 niveaux & 8 univers", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (selectedExplorerTab == 0) {
            // Bannière de Progression des Niveaux
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🏆", fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Progression des Niveaux",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Niveau $unlockedLevel / 7 Débloqué",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { unlockedLevel / 7f },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = Color(0xFF2E7D32),
                        trackColor = Color.LightGray.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Trouvez les 4 quiz sans faute d'un niveau pour débloquer le suivant !",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }

            // Liste des 7 Niveaux
            val filteredLevels = OFFICIAL_LEVELS.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                        it.subtitle.contains(searchQuery, ignoreCase = true)
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filteredLevels) { lvl ->
                    val isUnlocked = lvl.level <= unlockedLevel
                    val isCompleted = lvl.level < unlockedLevel
                    val isCurrent = lvl.level == unlockedLevel

                    Surface(
                        onClick = {
                            if (isUnlocked) {
                                onSelectLevel(lvl.level)
                            } else {
                                onLockedLevelClick(lvl.level)
                            }
                        },
                        shape = RoundedCornerShape(18.dp),
                        color = if (isUnlocked) {
                            Color(lvl.colorHex).copy(alpha = 0.12f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        },
                        border = androidx.compose.foundation.BorderStroke(
                            1.5.dp,
                            if (isUnlocked) Color(lvl.colorHex) else Color.Gray.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (isUnlocked) {
                                    Color(lvl.colorHex).copy(alpha = 0.25f)
                                } else {
                                    Color.Gray.copy(alpha = 0.2f)
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(if (isUnlocked) lvl.icon else "🔒", fontSize = 22.sp)
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = lvl.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isUnlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = when {
                                            isCompleted -> Color(0xFF2E7D32)
                                            isCurrent -> Color(lvl.colorHex)
                                            else -> Color.Gray
                                        }
                                    ) {
                                        Text(
                                            text = when {
                                                isCompleted -> "✓ Réussi (4/4)"
                                                isCurrent -> "🎯 En cours"
                                                else -> "🔒 Bloqué"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isUnlocked) {
                                        lvl.subtitle
                                    } else {
                                        "Réussissez d'abord le Niveau ${lvl.level - 1} pour débloquer"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isUnlocked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }

                            if (isUnlocked) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "Lancer",
                                    tint = Color(lvl.colorHex)
                                )
                            } else {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = "Verrouillé",
                                    tint = Color.Gray.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Grille des 8 Catégories (5 quiz par section)
            val filteredCategories = categories.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                        it.description.contains(searchQuery, ignoreCase = true)
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filteredCategories) { cat ->
                    Surface(
                        onClick = { onSelectCategory(cat.id) },
                        shape = RoundedCornerShape(18.dp),
                        color = Color(cat.primaryColorHex).copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(cat.primaryColorHex)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(145.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(cat.icon, fontSize = 28.sp)
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(cat.primaryColorHex)
                                ) {
                                    Text(
                                        "5 quiz",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Column {
                                Text(
                                    text = cat.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = cat.description,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DailyChallengeView(
    quizEngine: VideoQuizEngine,
    ttsManager: TtsManager,
    onBack: () -> Unit
) {
    val dailyQuestions = remember { quizEngine.getDailyChallengeQuestions() }
    val coroutineScope = rememberCoroutineScope()
    var currentQuestionIdx by remember { mutableIntStateOf(0) }
    var sessionScore by remember { mutableIntStateOf(0) }
    var isFinished by remember { mutableStateOf(false) }

    // Feedback state
    var selectedAnswerIndex by remember { mutableStateOf<Int?>(null) }
    var isCorrectAnswer by remember { mutableStateOf<Boolean?>(null) }

    // Reset feedback on question change
    LaunchedEffect(currentQuestionIdx) {
        selectedAnswerIndex = null
        isCorrectAnswer = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFFFD54F).copy(alpha = 0.2f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB300))
            ) {
                Text(
                    text = "⚡ DOUBLE XP ACTIVÉ",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE65100),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (!isFinished && dailyQuestions.isNotEmpty()) {
            val q = dailyQuestions[currentQuestionIdx.coerceIn(0, dailyQuestions.size - 1)]

            Text(
                text = "Défi Quotidien du Faso 🇧🇫",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Question ${currentQuestionIdx + 1} / ${dailyQuestions.size} • Tentative unique",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = q.question,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            q.options.forEachIndexed { optIndex, optText ->
                val isSelected = selectedAnswerIndex == optIndex
                val isCorrectOption = optIndex == q.correctAnswerIndex

                val containerColor = when {
                    selectedAnswerIndex != null && isCorrectOption -> Color(0xFFE8F5E9) // Vert clair
                    selectedAnswerIndex != null && isSelected && !isCorrectOption -> Color(0xFFFFEBEE) // Rouge clair
                    else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                }

                val borderColor = when {
                    selectedAnswerIndex != null && isCorrectOption -> Color(0xFF4CAF50) // Vert
                    selectedAnswerIndex != null && isSelected && !isCorrectOption -> Color(0xFFE53935) // Rouge
                    else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                }

                val textColor = when {
                    selectedAnswerIndex != null && isCorrectOption -> Color(0xFF2E7D32)
                    selectedAnswerIndex != null && isSelected && !isCorrectOption -> Color(0xFFC62828)
                    else -> MaterialTheme.colorScheme.onSurface
                }

                Surface(
                    onClick = {
                        if (selectedAnswerIndex == null) {
                            val isCorrect = (optIndex == q.correctAnswerIndex)
                            selectedAnswerIndex = optIndex
                            isCorrectAnswer = isCorrect
                            quizEngine.recordQuizAnswer(q, isCorrect, true, isDailyChallenge = true)
                            if (isCorrect) sessionScore += 200

                            coroutineScope.launch {
                                delay(1200)
                                if (currentQuestionIdx < dailyQuestions.size - 1) {
                                    currentQuestionIdx++
                                } else {
                                    isFinished = true
                                }
                            }
                        }
                    },
                    enabled = selectedAnswerIndex == null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = containerColor,
                    border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = optText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = textColor,
                            modifier = Modifier.weight(1f)
                        )
                        if (selectedAnswerIndex != null) {
                            if (isCorrectOption) {
                                Text("✅", fontSize = 16.sp)
                            } else if (isSelected) {
                                Text("❌", fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
        } else {
            // Écran de fin du Défi du jour
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("🏆", fontSize = 60.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Défi Quotidien Validé !",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "+$sessionScore XP accumulés aujourd'hui",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(onClick = onBack) {
                    Text("Retour aux Quiz Vidéos")
                }
            }
        }
    }
}

@Composable
fun DitanyeAuthentiqueView(
    ttsManager: TtsManager? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableIntStateOf(0) }
    var totalDuration by remember { mutableIntStateOf(107) }
    var showControls by remember { mutableStateOf(true) }
    var videoViewInstance by remember { mutableStateOf<VideoView?>(null) }

    // Mise à jour continue de la progression
    LaunchedEffect(isPlaying, videoViewInstance) {
        while (true) {
            if (isPlaying && videoViewInstance != null) {
                val pos = videoViewInstance?.currentPosition ?: 0
                val dur = videoViewInstance?.duration ?: 0
                currentPosition = (pos / 1000).coerceAtLeast(0)
                if (dur > 0) totalDuration = (dur / 1000)
            }
            delay(500)
        }
    }

    // Masquage temporisé des contrôles si la vidéo est en cours de lecture
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(4000)
            showControls = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable {
                showControls = !showControls
            }
    ) {
        // Lecteur Vidéo Plein Écran Unique (Remplacement total de la page)
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                VideoView(ctx).apply {
                    val targetUri = Uri.parse("android.resource://${ctx.packageName}/${R.raw.le_ditanye}")
                    setVideoURI(targetUri)
                    setOnPreparedListener { mp ->
                        mp.isLooping = true
                        totalDuration = (mp.duration / 1000).coerceAtLeast(1)
                        start()
                        isPlaying = true
                    }
                    setOnErrorListener { _, _, _ ->
                        true
                    }
                    videoViewInstance = this
                }
            },
            update = { view ->
                videoViewInstance = view
            }
        )

        // En-tête : Bouton Retour
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .padding(start = 16.dp, top = 20.dp)
                    .size(46.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Retour",
                    tint = Color.White
                )
            }
        }

        // Bouton central Play si en pause
        if (!isPlaying) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(76.dp)
                    .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                    .clickable {
                        videoViewInstance?.start()
                        isPlaying = true
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Lire la vidéo",
                    tint = Color(0xFFFCD116),
                    modifier = Modifier.size(52.dp)
                )
            }
        }

        // Contrôles en bas : Slider de timeline, Play/Pause, Replay et Temps
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 20.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = if (totalDuration > 0) currentPosition.toFloat() / totalDuration.toFloat() else 0f,
                        onValueChange = { frac ->
                            val newMs = (frac * totalDuration * 1000).toInt()
                            videoViewInstance?.seekTo(newMs)
                            currentPosition = (newMs / 1000)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFFCD116),
                            activeTrackColor = Color(0xFFEF2B2D),
                            inactiveTrackColor = Color.White.copy(alpha = 0.35f)
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    videoViewInstance?.let { vv ->
                                        if (vv.isPlaying) {
                                            vv.pause()
                                            isPlaying = false
                                        } else {
                                            vv.start()
                                            isPlaying = true
                                        }
                                    }
                                },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled,
                                    contentDescription = if (isPlaying) "Pause" else "Lecture",
                                    tint = Color(0xFFFCD116),
                                    modifier = Modifier.size(38.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    videoViewInstance?.apply {
                                        seekTo(0)
                                        start()
                                        isPlaying = true
                                    }
                                },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    Icons.Default.Replay,
                                    contentDescription = "Recommencer",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Text(
                            text = "${currentPosition / 60}:${(currentPosition % 60).toString().padStart(2, '0')} / ${totalDuration / 60}:${(totalDuration % 60).toString().padStart(2, '0')}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QuizLeaderboardView(
    userXp: Int,
    onBack: () -> Unit
) {
    var selectedPeriod by remember { mutableStateOf("semaine") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
            }
            Text(
                text = "Classement des Champions 🏆",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Onglets de période
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "jour" to "Aujourd'hui",
                "semaine" to "Cette Semaine",
                "general" to "Général"
            ).forEach { (key, label) ->
                val isSelected = selectedPeriod == key
                Button(
                    onClick = { selectedPeriod = key },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Podium Top 3
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            PodiumStep(rank = "2", name = "Mariam B.", xp = "14 200 XP", height = 90.dp, color = Color(0xFF90A4AE))
            PodiumStep(rank = "1", name = "Ousmane O.", xp = "21 500 XP", height = 120.dp, color = Color(0xFFFFD700))
            PodiumStep(rank = "3", name = "Vous", xp = "$userXp XP", height = 75.dp, color = Color(0xFFCD7F32))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Reste du classement
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(
                listOf(
                    Triple(4, "Boureima Traoré", "11 300 XP"),
                    Triple(5, "Fatou Ouédraogo", "9 850 XP"),
                    Triple(6, "Adama Sanou", "8 420 XP"),
                    Triple(7, "Salif Sawadogo", "7 100 XP"),
                    Triple(8, "Awa Zoungrana", "6 900 XP")
                )
            ) { (rank, name, xp) ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("$rank", fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(name, fontWeight = FontWeight.SemiBold)
                        }
                        Text(xp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
fun PodiumStep(rank: String, name: String, xp: String, height: androidx.compose.ui.unit.Dp, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        Text(name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Text(xp, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(4.dp))
        Surface(
            modifier = Modifier
                .width(80.dp)
                .height(height),
            shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
            color = color.copy(alpha = 0.85f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = rank,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun QuizProfileView(
    userXp: Int,
    lives: Int,
    currentStreak: Int,
    maxStreak: Int,
    quizzesPlayed: Int,
    correctAnswersCount: Int,
    badges: List<VideoQuizData.QuizBadge>,
    onRefillLives: () -> Unit,
    onBack: () -> Unit
) {
    val levelTitle = VideoQuizEngine.getLevelTitle(userXp)
    val nextXp = VideoQuizEngine.getNextLevelXp(userXp)
    val progress = VideoQuizEngine.getLevelProgress(userXp)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                }
                Text(
                    text = "Mon Profil Quiz 🇧🇫",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Carte Joueur
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(54.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("🇧🇫", fontSize = 28.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = levelTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "$userXp XP accumulés",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text("Progression vers le niveau supérieur ($userXp / $nextXp XP)", style = MaterialTheme.typography.labelSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape)
                    )
                }
            }
        }

        // Statistiques
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard(label = "Vies ❤️", value = "$lives / 5", modifier = Modifier.weight(1f))
                StatCard(label = "Série 🔥", value = "x$currentStreak (Max $maxStreak)", modifier = Modifier.weight(1f))
                StatCard(label = "Précision 🎯", value = if (quizzesPlayed > 0) "${(correctAnswersCount * 100) / quizzesPlayed}%" else "100%", modifier = Modifier.weight(1f))
            }
        }

        // Badges débloqués
        item {
            Text(
                text = "Badges & Trophées (${badges.count { it.isUnlocked }} / ${badges.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        items(badges) { badge ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = if (badge.isUnlocked) Color(0xFFFFD54F).copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (badge.isUnlocked) Color(0xFFFFB300) else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(badge.icon, fontSize = 28.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(badge.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = badge.rarity,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp
                                )
                            }
                        }
                        Text(badge.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (badge.isUnlocked) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Débloqué", tint = Color(0xFF2E7D32))
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
