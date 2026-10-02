package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.engine.SmartQuizEngine
import com.example.data.local.LinguisticKnowledge
import com.example.data.local.UserProgress
import com.example.ui.SamAiViewModel
import com.example.util.TtsManager
import com.example.util.AppStrings
import com.example.util.SmartNotificationManager
import com.example.util.NotificationCategory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearningScreen(viewModel: SamAiViewModel, ttsManager: TtsManager) {
    val selectedQuizLang by viewModel.selectedQuizLanguage.collectAsState()
    val selectedQuizCat by viewModel.selectedQuizCategory.collectAsState()
    val mooreProgress by viewModel.mooreProgress.collectAsState()
    val dioulaProgress by viewModel.dioulaProgress.collectAsState()
    val hausaProgress by viewModel.hausaProgress.collectAsState()
    val learnedCount by viewModel.learnedWordsCount.collectAsState()
    val appLanguage by viewModel.appLanguage.collectAsState()

    val currentProgress = when (selectedQuizLang) {
        "Dioula" -> dioulaProgress
        "Haoussa" -> hausaProgress
        else -> mooreProgress
    }

    var showLesson by remember { mutableStateOf(false) }
    var showQuiz by remember { mutableStateOf(false) }
    var showVideoQuiz by remember { mutableStateOf(false) }
    var selectedDifficulty by remember { mutableStateOf("beginner") }
    var selectedQuestionCount by remember { mutableIntStateOf(10) }

    val languages = listOf("Mooré", "Dioula", "Haoussa")

    DisposableEffect(showVideoQuiz, showLesson, showQuiz) {
        onDispose {
            ttsManager.stop()
        }
    }

    when {
        showVideoQuiz -> VideoQuizScreen(
            ttsManager = ttsManager,
            onBack = {
                ttsManager.stop()
                showVideoQuiz = false
            }
        )
        showLesson -> LessonView(viewModel, ttsManager, selectedDifficulty, selectedQuizLang) {
            ttsManager.stop()
            showLesson = false
        }
        showQuiz -> SmartQuizView(
            viewModel = viewModel,
            ttsManager = ttsManager,
            difficulty = selectedDifficulty,
            language = selectedQuizLang,
            questionCount = selectedQuestionCount,
            onBack = {
                ttsManager.stop()
                showQuiz = false
            }
        )
        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Hero Banner Quiz Vidéo Burkina
                item {
                    Surface(
                        onClick = { showVideoQuiz = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0xFFD32F2F),
                        shadowElevation = 8.dp
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                                        colors = listOf(Color(0xFFB71C1C), Color(0xFFD32F2F), Color(0xFF1B5E20))
                                    )
                                )
                                .padding(18.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.Black.copy(alpha = 0.35f)
                                    ) {
                                        Text(
                                            text = "🎬 FORMAT VIDÉO VERTICAL • TIKTOK STYLE",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "QUIZ BURKINA 🇧🇫",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Apprends la culture, l'histoire, la cuisine, le sport et les langues en vidéos courtes interactives avec vies ❤️ et classements !",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.9f),
                                        lineHeight = 16.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = Color.White,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.PlayArrow,
                                            contentDescription = "Lancer le Quiz Vidéo",
                                            tint = Color(0xFFD32F2F),
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = if (appLanguage == "en") "Learning & Master Quiz" else "Apprentissage Linguistique & Vocabulaire",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (appLanguage == "en") "Authentic vocabulary, oral comprehension, situational dialogues & live streaks." else "Grand quiz complet : Compréhension orale, dialogues de marché, vrai/faux et révision détaillée.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Sélecteur de Langue d'apprentissage
                item {
                    Text(
                        text = "1. Langue d'apprentissage :",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        languages.forEach { lang ->
                            val isSelected = selectedQuizLang == lang
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.setQuizLanguage(lang) },
                                label = { Text(lang, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                item {
                    ProgressHeader(currentProgress, learnedCount, selectedQuizLang)
                }

                // Configuration du Vrai Quiz
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Quiz,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = "Grand Défi Quiz • $selectedQuizLang",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Audio, QCM, Vrai/Faux & Situations réelles",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Thématiques
                            Text(
                                text = "Thème du Quiz :",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(SmartQuizEngine.CATEGORIES) { cat ->
                                    val isSelected = selectedQuizCat == cat.id || (selectedQuizCat == "all" && cat.id == "all")
                                    SuggestionChip(
                                        onClick = { viewModel.setQuizCategory(cat.id) },
                                        label = { Text("${cat.icon} ${cat.title}") },
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                                        ),
                                        border = SuggestionChipDefaults.suggestionChipBorder(
                                            enabled = true,
                                            borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Nombre de questions / Format
                            Text(
                                text = "Format de la session :",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    5 to "⚡ Express (5)",
                                    10 to "🎯 Défi (10)",
                                    15 to "👑 Marathon (15)"
                                ).forEach { (count, label) ->
                                    val isSelected = selectedQuestionCount == count
                                    OutlinedButton(
                                        onClick = { selectedQuestionCount = count },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
                                            contentColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                        )
                                    ) {
                                        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            // Grand Bouton Lancer le Quiz
                            Button(
                                onClick = {
                                    viewModel.startSmartQuiz(
                                        language = selectedQuizLang,
                                        difficulty = selectedDifficulty,
                                        category = selectedQuizCat,
                                        count = selectedQuestionCount
                                    )
                                    showQuiz = true
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Lancer le Vrai Quiz ($selectedQuestionCount questions)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "Modules de Leçons Thématiques",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LevelCard(
                            title = "Niveau Débutant",
                            sub = "Salutations, politesse, chiffres et vocabulaire essentiel",
                            difficulty = "beginner",
                            icon = Icons.AutoMirrored.Filled.MenuBook,
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ) {
                            selectedDifficulty = "beginner"
                            showLesson = true
                        }

                        LevelCard(
                            title = "Niveau Intermédiaire",
                            sub = "Conversations quotidiennes, marché et famille",
                            difficulty = "intermediate",
                            icon = Icons.Default.School,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        ) {
                            selectedDifficulty = "intermediate"
                            showLesson = true
                        }

                        LevelCard(
                            title = "Niveau Avancé",
                            sub = "Proverbes, expressions régionales et subtilités culturelles",
                            difficulty = "advanced",
                            icon = Icons.Default.Psychology,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            selectedDifficulty = "advanced"
                            showLesson = true
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProgressHeader(progress: UserProgress?, learnedCount: Int, language: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "PROGRESSION EN $language".uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Niveau ${progress?.level ?: 1}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { if (learnedCount > 0) ((learnedCount % 10) / 10f).coerceIn(0f, 1f) else 0.1f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outlineVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$learnedCount mots maîtrisés",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(20.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.padding(4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${progress?.points ?: 0}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = "PTS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
    }
}

@Composable
fun LevelCard(
    title: String,
    sub: String,
    difficulty: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun LessonView(
    viewModel: SamAiViewModel,
    ttsManager: TtsManager,
    difficulty: String,
    language: String,
    onBack: () -> Unit
) {
    // Maintien stable de la liste des mots pour toute la session de leçon sans rechargement intempestif
    var wordsList by remember(difficulty, language) { mutableStateOf<List<LinguisticKnowledge>?>(null) }
    var currentIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(difficulty, language) {
        viewModel.getLessonWords(difficulty).collect { words ->
            if (wordsList == null) {
                wordsList = words
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // En-tête de navigation manuelle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Quitter la leçon", tint = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(modifier = Modifier.weight(1f))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            ) {
                Text(
                    text = if (wordsList != null && wordsList!!.isNotEmpty()) "Mot ${currentIndex + 1} / ${wordsList!!.size} • $language" else "Leçon $language",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (wordsList != null && wordsList!!.isNotEmpty() && currentIndex < wordsList!!.size) {
            val words = wordsList!!
            val word = words[currentIndex]

            // Indicateur d'étapes manuelles interactives
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                words.indices.forEach { index ->
                    val isActive = index == currentIndex
                    val isPassed = index < currentIndex
                    Surface(
                        onClick = { currentIndex = index },
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp),
                        shape = CircleShape,
                        color = when {
                            isActive -> MaterialTheme.colorScheme.primary
                            isPassed -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            else -> MaterialTheme.colorScheme.outlineVariant
                        }
                    ) {}
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Carte principale du mot (contrôlée à 100% par l'utilisateur)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = word.category.ifBlank { "Vocabulaire" },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = word.word,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (word.pronunciation.isNotBlank()) {
                            Text(
                                text = "/ ${word.pronunciation} /",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        IconButton(
                            onClick = { ttsManager.speak(word.word, language) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = "Écouter la prononciation",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "= ${word.translation}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    if (word.examples.isNotBlank()) {
                        Spacer(modifier = Modifier.height(20.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "Exemple d'usage :",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = word.examples,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Barre de contrôles manuels : Précédent & Suivant
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bouton Précédent manuel
                OutlinedButton(
                    onClick = {
                        if (currentIndex > 0) {
                            currentIndex--
                        }
                    },
                    enabled = currentIndex > 0,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Précédent", fontWeight = FontWeight.SemiBold)
                }

                // Bouton Suivant ou Terminer manuel
                Button(
                    onClick = {
                        viewModel.completeWord(word.id, language)
                        if (currentIndex < words.size - 1) {
                            currentIndex++
                        } else {
                            onBack()
                        }
                    },
                    modifier = Modifier
                        .weight(1.3f)
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = if (currentIndex < words.size - 1) "Suivant" else "Terminer",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        if (currentIndex < words.size - 1) Icons.Default.ArrowForward else Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        } else if (wordsList == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (wordsList!!.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Aucun mot disponible pour cette catégorie.", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onBack) { Text("Retour") }
                }
            }
        }
    }
}

data class QuizAnswerRecord(
    val question: SmartQuizEngine.QuizQuestion,
    val selectedOption: String,
    val isCorrect: Boolean
)

@Composable
fun SmartQuizView(
    viewModel: SamAiViewModel,
    ttsManager: TtsManager,
    difficulty: String,
    language: String,
    questionCount: Int = 10,
    onBack: () -> Unit
) {
    val questions by viewModel.smartQuizQuestions.collectAsState()
    var currentIndex by remember { mutableIntStateOf(0) }
    val answerHistory = remember { mutableStateMapOf<Int, QuizAnswerRecord>() }
    var currentStreak by remember { mutableIntStateOf(0) }
    var maxStreak by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var isQuizCompleted by remember { mutableStateOf(false) }
    var showQuitConfirmDialog by remember { mutableStateOf(false) }
    val selectedCategory by viewModel.selectedQuizCategory.collectAsState()

    // Confirmation avant de quitter en pleine partie
    if (showQuitConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showQuitConfirmDialog = false },
            title = { Text("Quitter la session de quiz ?", fontWeight = FontWeight.Bold) },
            text = { Text("Votre score en cours et vos séries seront réinitialisés.") },
            confirmButton = {
                Button(
                    onClick = {
                        showQuitConfirmDialog = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Quitter")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showQuitConfirmDialog = false }) {
                    Text("Continuer le Quiz")
                }
            }
        )
    }

    if (isQuizCompleted && questions.isNotEmpty()) {
        QuizResultsView(
            questions = questions,
            answers = answerHistory,
            finalScore = score,
            maxStreak = maxStreak,
            language = language,
            onReplay = {
                // Recommencer une nouvelle session fraîche
                viewModel.startSmartQuiz(
                    language = language,
                    difficulty = difficulty,
                    category = selectedCategory,
                    count = questionCount
                )
                currentIndex = 0
                answerHistory.clear()
                currentStreak = 0
                maxStreak = 0
                score = 0
                isQuizCompleted = false
            },
            onExit = onBack
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // En-tête de Jeu : Quitter, Série de victoires (Streak), et Score
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (answerHistory.isNotEmpty() && !isQuizCompleted) {
                        showQuitConfirmDialog = true
                    } else {
                        onBack()
                    }
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Quitter le quiz", tint = MaterialTheme.colorScheme.onSurface)
            }

            // Badge Combo / Streak
            if (currentStreak >= 2) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFE65100).copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF6C00))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🔥", fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Série x$currentStreak",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE65100)
                        )
                    }
                }
            }

            // Badge Score
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Stars,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$score pts",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        if (questions.isNotEmpty() && currentIndex < questions.size) {
            val question = questions[currentIndex]
            val recordedAnswer = answerHistory[currentIndex]
            val hasAnswered = recordedAnswer != null

            Spacer(modifier = Modifier.height(10.dp))

            // Progression et Thématique
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = question.contextBadge,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = "Question ${currentIndex + 1} / ${questions.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Barre de progression
            LinearProgressIndicator(
                progress = { ((currentIndex + 1).toFloat() / questions.size.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outlineVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Énoncé de la question
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = if (question.type == SmartQuizEngine.QuestionType.AUDIO_LISTENING) Alignment.CenterHorizontally else Alignment.Start
                ) {
                    Text(
                        text = question.prompt,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = if (question.type == SmartQuizEngine.QuestionType.AUDIO_LISTENING) androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start
                    )

                    // Vue spécifique pour Question Audio
                    if (question.type == SmartQuizEngine.QuestionType.AUDIO_LISTENING) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = { ttsManager.speak(question.audioWord, language) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Écouter",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Réécouter la prononciation",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (question.phonetic.isNotBlank() && question.type != SmartQuizEngine.QuestionType.TRUE_FALSE) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Phonétique : / ${question.phonetic} /",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = { ttsManager.speak(question.audioWord.ifBlank { question.phonetic }, language) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "Écouter",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Options de réponse à choix multiple ou Vrai/Faux
            if (question.type == SmartQuizEngine.QuestionType.TRUE_FALSE) {
                // Layout spécial Vrai / Faux avec deux grands boutons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    listOf("Vrai", "Faux").forEach { option ->
                        val isSelected = recordedAnswer?.selectedOption == option
                        val isAnswerCorrect = option == question.correctAnswer
                        val cardColor = when {
                            hasAnswered && isAnswerCorrect -> Color(0xFF1B5E20).copy(alpha = 0.25f)
                            hasAnswered && isSelected && !recordedAnswer.isCorrect -> MaterialTheme.colorScheme.errorContainer
                            isSelected -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surface
                        }
                        val borderColor = when {
                            hasAnswered && isAnswerCorrect -> Color(0xFF2E7D32)
                            hasAnswered && isSelected && !recordedAnswer.isCorrect -> MaterialTheme.colorScheme.error
                            isSelected -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.outlineVariant
                        }

                        Surface(
                            onClick = {
                                if (!hasAnswered) {
                                    val correct = option == question.correctAnswer
                                    answerHistory[currentIndex] = QuizAnswerRecord(question, option, correct)
                                    if (correct) {
                                        currentStreak++
                                        if (currentStreak > maxStreak) maxStreak = currentStreak
                                        val bonus = if (currentStreak >= 3) 30 else 20
                                        score += bonus
                                        viewModel.completeWord(question.wordId, language)
                                    } else {
                                        currentStreak = 0
                                    }
                                    if (question.audioWord.isNotBlank()) {
                                        ttsManager.speak(question.audioWord, language)
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(70.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = cardColor,
                            border = androidx.compose.foundation.BorderStroke(if (hasAnswered && isAnswerCorrect) 2.dp else 1.dp, borderColor)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (option == "Vrai") Icons.Default.Check else Icons.Default.Close,
                                        contentDescription = null,
                                        tint = if (option == "Vrai") Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = option,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Layout 4 options classiques (A, B, C, D)
                val letters = listOf("A", "B", "C", "D")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    question.options.forEachIndexed { optIndex, option ->
                        val isSelected = recordedAnswer?.selectedOption == option
                        val isAnswerCorrect = option == question.correctAnswer

                        val cardColor = when {
                            hasAnswered && isAnswerCorrect -> Color(0xFF1B5E20).copy(alpha = 0.2f)
                            hasAnswered && isSelected && !recordedAnswer.isCorrect -> MaterialTheme.colorScheme.errorContainer
                            isSelected -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surface
                        }

                        val borderColor = when {
                            hasAnswered && isAnswerCorrect -> Color(0xFF2E7D32)
                            hasAnswered && isSelected && !recordedAnswer.isCorrect -> MaterialTheme.colorScheme.error
                            isSelected -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.outlineVariant
                        }

                        Surface(
                            onClick = {
                                if (!hasAnswered) {
                                    val correct = option == question.correctAnswer
                                    answerHistory[currentIndex] = QuizAnswerRecord(question, option, correct)
                                    if (correct) {
                                        currentStreak++
                                        if (currentStreak > maxStreak) maxStreak = currentStreak
                                        val bonus = if (currentStreak >= 3) 30 else 20
                                        score += bonus
                                        viewModel.completeWord(question.wordId, language)
                                    } else {
                                        currentStreak = 0
                                    }
                                    if (question.type == SmartQuizEngine.QuestionType.FRENCH_TO_TARGET) {
                                        ttsManager.speak(option, language)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = cardColor,
                            border = androidx.compose.foundation.BorderStroke(if (hasAnswered && isAnswerCorrect) 2.dp else 1.dp, borderColor)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = letters.getOrElse(optIndex) { "•" },
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Text(
                                    text = option,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                if (hasAnswered && isAnswerCorrect) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Correct", tint = Color(0xFF2E7D32))
                                } else if (hasAnswered && isSelected && !recordedAnswer.isCorrect) {
                                    Icon(Icons.Default.Cancel, contentDescription = "Incorrect", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }

            // Explication pédagogique immédiate
            if (hasAnswered) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = if (recordedAnswer.isCorrect) Color(0xFF2E7D32).copy(alpha = 0.12f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (recordedAnswer.isCorrect) Icons.Default.CheckCircle else Icons.Default.Info,
                            contentDescription = null,
                            tint = if (recordedAnswer.isCorrect) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = question.explanation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Barre de navigation manuelle : Précédent & Suivant / Terminer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentIndex > 0) {
                    OutlinedButton(
                        onClick = { currentIndex-- },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Précédent", fontWeight = FontWeight.SemiBold)
                    }
                }

                if (hasAnswered) {
                    Button(
                        onClick = {
                            if (currentIndex < questions.size - 1) {
                                currentIndex++
                            } else {
                                isQuizCompleted = true
                            }
                        },
                        modifier = Modifier
                            .weight(if (currentIndex > 0) 1.4f else 1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = if (currentIndex < questions.size - 1) "Question Suivante" else "Voir mon Bilan",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            if (currentIndex < questions.size - 1) Icons.Default.ArrowForward else Icons.Default.EmojiEvents,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        } else if (questions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
fun QuizResultsView(
    questions: List<SmartQuizEngine.QuizQuestion>,
    answers: Map<Int, QuizAnswerRecord>,
    finalScore: Int,
    maxStreak: Int,
    language: String,
    onReplay: () -> Unit,
    onExit: () -> Unit
) {
    val totalCount = questions.size
    val correctCount = answers.values.count { it.isCorrect }
    val accuracyPercent = if (totalCount > 0) (correctCount * 100) / totalCount else 0
    var showReviewList by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(questions) {
        if (totalCount > 0) {
            val title = if (accuracyPercent >= 80) "🏆 Quiz Réussi : Félicitations !" else "🎯 Session Quiz terminée"
            val content = "Vous avez obtenu $correctCount/$totalCount ($accuracyPercent%) en $language ! Score total : $finalScore pts."
            SmartNotificationManager.sendEventNotification(
                context = context,
                category = NotificationCategory.QUIZ,
                title = title,
                content = content,
                targetUri = "sam-ai://learning",
                forceEvenIfActive = true
            )
            if (accuracyPercent >= 80) {
                SmartNotificationManager.sendEventNotification(
                    context = context,
                    category = NotificationCategory.REWARDS,
                    title = "🏅 Nouveau Palier Atteint !",
                    content = "Bravo pour votre maîtrise en $language (+100 XP)",
                    targetUri = "sam-ai://learning",
                    forceEvenIfActive = true
                )
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(10.dp))
            // Célébration visuelle et titre de niveau
            Surface(
                shape = CircleShape,
                color = when {
                    accuracyPercent >= 80 -> Color(0xFFFFD700).copy(alpha = 0.2f)
                    accuracyPercent >= 50 -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.size(90.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = when {
                            accuracyPercent >= 80 -> "🏆"
                            accuracyPercent >= 50 -> "🌟"
                            else -> "💪"
                        },
                        fontSize = 44.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = when {
                    accuracyPercent >= 80 -> "Félicitations ! Maître du $language"
                    accuracyPercent >= 50 -> "Très bon travail !"
                    else -> "Bel effort, continuez !"
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Session terminée • $correctCount réponses correctes sur $totalCount",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Grille de statistiques de performance
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        ResultStatItem(
                            label = "Score Total",
                            value = "$finalScore pts",
                            icon = Icons.Default.Stars,
                            color = MaterialTheme.colorScheme.primary
                        )
                        ResultStatItem(
                            label = "Précision",
                            value = "$accuracyPercent %",
                            icon = Icons.Default.CheckCircle,
                            color = Color(0xFF2E7D32)
                        )
                        ResultStatItem(
                            label = "Série Max",
                            value = "🔥 x$maxStreak",
                            icon = Icons.Default.LocalFireDepartment,
                            color = Color(0xFFE65100)
                        )
                    }
                }
            }
        }

        // Bouton pour afficher la revue détaillée des questions
        item {
            OutlinedButton(
                onClick = { showReviewList = !showReviewList },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    if (showReviewList) Icons.Default.ExpandLess else Icons.Default.Visibility,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (showReviewList) "Masquer la revue détaillée" else "Revoir toutes mes réponses ($totalCount)",
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Liste de revue question par question
        if (showReviewList) {
            items(questions.size) { idx ->
                val q = questions[idx]
                val record = answers[idx]
                val isSuccess = record?.isCorrect == true

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSuccess) Color(0xFF2E7D32).copy(alpha = 0.08f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSuccess) Color(0xFF2E7D32).copy(alpha = 0.5f) else MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Question ${idx + 1} : ${q.prompt}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                contentDescription = null,
                                tint = if (isSuccess) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        if (record != null) {
                            Text(
                                text = "Votre réponse : ${record.selectedOption}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSuccess) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (!isSuccess) {
                            Text(
                                text = "Bonne réponse : ${q.correctAnswer}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = q.explanation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Actions finales : Rejouer ou Quitter
        item {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onExit,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Accueil Quiz", fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = onReplay,
                    modifier = Modifier
                        .weight(1.2f)
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Rejouer ce Thème", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ResultStatItem(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = color.copy(alpha = 0.15f),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
