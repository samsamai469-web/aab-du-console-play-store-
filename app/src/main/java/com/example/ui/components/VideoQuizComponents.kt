package com.example.ui.components

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.engine.VideoQuizData
import com.example.data.engine.VideoQuizData.QuizComment
import com.example.data.engine.VideoQuizData.VideoQuizItem
import com.example.util.TtsManager
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Composants d'interface premium pour le système Quiz Vidéo "Quiz Burkina" 🇧🇫
 */

@Composable
fun VideoPlayerCard(
    quizItem: VideoQuizItem,
    ttsManager: TtsManager,
    isLiked: Boolean,
    isBookmarked: Boolean,
    lives: Int,
    currentStreak: Int,
    onLikeToggle: () -> Unit,
    onBookmarkToggle: () -> Unit,
    onAnswerSelected: (Int, Boolean, Boolean) -> Unit,
    onNextQuiz: () -> Unit,
    onBack: () -> Unit,
    onOpenComments: () -> Unit,
    modifier: Modifier = Modifier,
    levelNumber: Int? = null,
    questionIndex: Int = 1,
    totalQuestionsInLevel: Int = 4,
    onRestartLevel: () -> Unit = {},
    onLevelCompleted: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    var secondsElapsed by remember(quizItem.id) { mutableIntStateOf(0) }
    var selectedOptionIndex by remember(quizItem.id) { mutableStateOf<Int?>(null) }
    var hasAnswered by remember(quizItem.id) { mutableStateOf(false) }
    var isCorrectAnswer by remember(quizItem.id) { mutableStateOf(false) }
    var earnedXpAmount by remember(quizItem.id) { mutableIntStateOf(0) }
    var showPedagogicalSheet by remember(quizItem.id) { mutableStateOf(false) }
    var isNarratorPlaying by remember(quizItem.id) { mutableStateOf(true) }

    // Minuteur chronométré pour déclencher les étapes d'apparition
    DisposableEffect(quizItem.id) {
        onDispose {
            ttsManager.stop()
        }
    }

    LaunchedEffect(quizItem.id) {
        secondsElapsed = 0
        selectedOptionIndex = null
        hasAnswered = false
        showPedagogicalSheet = false
        isNarratorPlaying = true

        // Lecture vocale automatique de l'amorce narrative
        ttsManager.speak(quizItem.narratorScript, "fr")

        while (true) {
            delay(1000)
            secondsElapsed++
            if (secondsElapsed >= 3 && isNarratorPlaying) {
                // Lecture audio de la question
                ttsManager.speak(quizItem.question, "fr")
                isNarratorPlaying = false
            }
        }
    }

    // Animation du fond de la vidéo (particules et dégradés dynamiques)
    val infiniteTransition = rememberInfiniteTransition(label = "video_ambient")
    val gradientShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gradient_shift"
    )

    val backgroundColors = remember(quizItem.videoThemeGradient) {
        quizItem.videoThemeGradient.map { Color(it) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = backgroundColors,
                    startY = 0f + (gradientShift * 200),
                    endY = 1200f + (gradientShift * 300)
                )
            )
    ) {
        // Toile de particules d'ambiance et symboles en arrière-plan
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // Cercles lumineux diffus
            drawCircle(
                color = Color.White.copy(alpha = 0.08f),
                radius = canvasWidth * 0.45f,
                center = Offset(canvasWidth * 0.8f, canvasHeight * (0.2f + gradientShift * 0.1f))
            )
            drawCircle(
                color = Color(quizItem.categoryColorHex).copy(alpha = 0.15f),
                radius = canvasWidth * 0.6f,
                center = Offset(canvasWidth * 0.2f, canvasHeight * 0.7f)
            )
        }

        // Overlay central avec symbole culturel et narration animée
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 80.dp, start = 20.dp, end = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Cercle visuel du thème avec animation de pulsation
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 0.95f,
                targetValue = 1.05f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulse"
            )

            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.35f),
                border = androidx.compose.foundation.BorderStroke(2.dp, Color.White.copy(alpha = 0.3f)),
                modifier = Modifier
                    .size(90.dp)
                    .scale(pulseScale)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = quizItem.burkinaSymbol,
                        fontSize = 42.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bulle de Narration
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.5f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Narration audio",
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = quizItem.narratorScript,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // 1. Barre Supérieure (Header)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(42.dp)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = Color.White)
            }

            // Logo Quiz Burkina
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.45f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🇧🇫", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "QUIZ BURKINA",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Statut Streak & Vies
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentStreak > 0) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFE65100).copy(alpha = 0.85f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🔥", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                "$currentStreak",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.Black.copy(alpha = 0.45f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("❤️", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "$lives",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // 3. Zone Inférieure : Question & Options de Réponse
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Badge Catégorie & Difficulté
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(quizItem.categoryColorHex).copy(alpha = 0.9f)
                ) {
                    Text(
                        text = quizItem.categoryTitle,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = "Niveau ${quizItem.level} • ${quizItem.difficulty.replaceFirstChar { it.uppercase() }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // Question (apparaît avec fondu et glissement)
            AnimatedVisibility(
                visible = secondsElapsed >= 2,
                enter = fadeIn(animationSpec = tween(500)) + slideInVertically(initialOffsetY = { 30 })
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = quizItem.question,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(14.dp),
                        lineHeight = 20.sp
                    )
                }
            }

            // Options de réponse A, B, C, D (cascade fluide)
            val letters = listOf("A", "B", "C", "D")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                quizItem.options.forEachIndexed { index, optionText ->
                    val isVisible = secondsElapsed >= 3 || hasAnswered
                    val isSelected = selectedOptionIndex == index
                    val isCorrect = index == quizItem.correctAnswerIndex

                    AnimatedVisibility(
                        visible = isVisible,
                        enter = fadeIn(animationSpec = tween(300, delayMillis = index * 100)) +
                                slideInHorizontally(
                                    initialOffsetX = { if (index % 2 == 0) -40 else 40 },
                                    animationSpec = tween(300, delayMillis = index * 100)
                                )
                    ) {
                        QuizOptionButton(
                            letter = letters.getOrElse(index) { "•" },
                            text = optionText,
                            isSelected = isSelected,
                            isCorrect = isCorrect,
                            hasAnswered = hasAnswered,
                            onClick = {
                                if (!hasAnswered) {
                                    selectedOptionIndex = index
                                    hasAnswered = true
                                    val correct = (index == quizItem.correctAnswerIndex)
                                    isCorrectAnswer = correct
                                    val isFast = secondsElapsed <= 6
                                    onAnswerSelected(index, correct, isFast)
                                    earnedXpAmount = if (correct) (if (isFast) 150 else 100) else 0

                                    // Déclencher fiche pédagogique après un court instant
                                    showPedagogicalSheet = true
                                }
                            }
                        )
                    }
                }
            }

            // Barre de progression du quiz
            LinearProgressIndicator(
                progress = { (secondsElapsed.toFloat() / quizItem.durationSeconds.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape),
                color = Color(0xFFFFD54F),
                trackColor = Color.White.copy(alpha = 0.2f)
            )
        }

        // Effet XP & Célébration si bonne réponse
        if (hasAnswered && isCorrectAnswer) {
            FloatingXpAnimation(
                xp = earnedXpAmount,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // 4. Fiche Pédagogique après réponse
        if (showPedagogicalSheet) {
            PedagogicalSheetOverlay(
                quizItem = quizItem,
                isCorrect = isCorrectAnswer,
                levelNumber = levelNumber,
                questionIndex = questionIndex,
                totalQuestionsInLevel = totalQuestionsInLevel,
                onNext = {
                    showPedagogicalSheet = false
                    onNextQuiz()
                },
                onRestartLevel = {
                    showPedagogicalSheet = false
                    onRestartLevel()
                },
                onLevelCompleted = { lvl ->
                    showPedagogicalSheet = false
                    onLevelCompleted(lvl)
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
fun QuizOptionButton(
    letter: String,
    text: String,
    isSelected: Boolean,
    isCorrect: Boolean,
    hasAnswered: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "button_scale"
    )

    // Couleurs selon l'état
    val backgroundColor = when {
        hasAnswered && isCorrect -> Color(0xFF2E7D32).copy(alpha = 0.9f)
        hasAnswered && isSelected && !isCorrect -> Color(0xFFC62828).copy(alpha = 0.9f)
        isSelected -> Color(0xFF1976D2).copy(alpha = 0.85f)
        else -> Color.Black.copy(alpha = 0.55f)
    }

    val borderColor = when {
        hasAnswered && isCorrect -> Color(0xFF66BB6A)
        hasAnswered && isSelected && !isCorrect -> Color(0xFFEF5350)
        isSelected -> Color(0xFF64B5F6)
        else -> Color.White.copy(alpha = 0.25f)
    }

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(14.dp),
        color = backgroundColor,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.2f),
                modifier = Modifier.size(26.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = letter,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected || (hasAnswered && isCorrect)) FontWeight.Bold else FontWeight.Normal,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )

            if (hasAnswered) {
                if (isCorrect) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Correct",
                        tint = Color(0xFF69F0AE),
                        modifier = Modifier.size(20.dp)
                    )
                } else if (isSelected) {
                    Icon(
                        Icons.Default.Cancel,
                        contentDescription = "Incorrect",
                        tint = Color(0xFFFF8A80),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SideActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.45f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
            modifier = Modifier.size(46.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = tint,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            fontSize = 11.sp
        )
    }
}

@Composable
fun PedagogicalSheetOverlay(
    quizItem: VideoQuizItem,
    isCorrect: Boolean,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    levelNumber: Int? = null,
    questionIndex: Int = 1,
    totalQuestionsInLevel: Int = 4,
    onRestartLevel: () -> Unit = {},
    onLevelCompleted: (Int) -> Unit = {}
) {
    val isLevelMode = levelNumber != null

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF1E1E1E).copy(alpha = 0.98f),
        border = androidx.compose.foundation.BorderStroke(
            1.5.dp,
            if (isCorrect) Color(0xFF2E7D32) else Color(0xFFD32F2F)
        ),
        shadowElevation = 16.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // En-tête Feedback
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isCorrect) "🎉" else "❌",
                        fontSize = 26.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = when {
                                !isCorrect && isLevelMode -> "Échec du Niveau $levelNumber !"
                                isCorrect && isLevelMode && questionIndex == totalQuestionsInLevel -> "🏆 Niveau $levelNumber Complété (4/4) !"
                                isCorrect -> "Excellent ! Tu connais le Faso 🇧🇫"
                                else -> "Tu apprends quelque chose d'important !"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isCorrect) Color(0xFF81C784) else Color(0xFFEF5350)
                        )
                        Text(
                            text = "Bonne réponse : ${quizItem.options.getOrElse(quizItem.correctAnswerIndex) { "" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Si échec en mode niveau, message clair sur la règle de recommencement
            if (!isCorrect && isLevelMode) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFD32F2F).copy(alpha = 0.2f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF5350).copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⚠️", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Pour débloquer le Niveau ${levelNumber + 1}, vous devez trouver les 4 questions sans faute. Vous devez recommencer le niveau.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFFCDD2),
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // Explication détaillée
            Text(
                text = quizItem.explanation,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.9f),
                lineHeight = 18.sp
            )

            // Anecdote culturelle (Le saviez-vous ?)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.08f)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text("💡", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Anecdote : ${quizItem.anecdote}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFFD54F),
                        lineHeight = 15.sp
                    )
                }
            }

            // Source & Niveau
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "📚 Source : ${quizItem.source}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 10.sp
                )
                if (isLevelMode) {
                    Text(
                        text = "Progression : $questionIndex/$totalQuestionsInLevel",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD54F),
                        fontSize = 11.sp
                    )
                }
            }

            // Boutons d'action selon le résultat et le mode
            if (!isCorrect && isLevelMode) {
                // En cas d'erreur dans un niveau : bouton Recommencer le Niveau
                Button(
                    onClick = onRestartLevel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFC62828)
                    )
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Recommencer le Niveau $levelNumber (0/$totalQuestionsInLevel)",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            } else if (isCorrect && isLevelMode && questionIndex >= totalQuestionsInLevel) {
                // 4/4 questions réussies : Valider le niveau !
                Button(
                    onClick = { onLevelCompleted(levelNumber) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2E7D32)
                    )
                ) {
                    Text(
                        text = "🏆 Valider le Niveau $levelNumber (4/4 Sans Faute) ➔",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            } else {
                // Question suivante standard
                Button(
                    onClick = onNext,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCorrect) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                    )
                ) {
                    val nextButtonText = if (isLevelMode) {
                        "Question Suivante (${questionIndex + 1}/$totalQuestionsInLevel) ➔"
                    } else {
                        "Continuer le Quiz Vidéo ➔"
                    }
                    Text(
                        text = nextButtonText,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun FloatingXpAnimation(
    xp: Int,
    modifier: Modifier = Modifier
) {
    var animStarted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        animStarted = true
    }

    val offsetY by animateFloatAsState(
        targetValue = if (animStarted) -120f else 0f,
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label = "xp_offset"
    )

    val alpha by animateFloatAsState(
        targetValue = if (animStarted) 0f else 1f,
        animationSpec = tween(1200, easing = LinearEasing),
        label = "xp_alpha"
    )

    Box(
        modifier = modifier
            .offset(y = offsetY.dp)
            .graphicsLayer { this.alpha = alpha }
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFFFFD700),
            shadowElevation = 12.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⭐", fontSize = 20.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "+$xp XP",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = Color.Black
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsBottomSheet(
    comments: List<QuizComment>,
    onAddComment: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var commentInput by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = "Commentaires (${comments.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .heightIn(max = 350.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(comments) { comment ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(comment.authorAvatar, fontSize = 18.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = comment.authorName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "• ${comment.timeAgo}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = comment.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Champ d'ajout de commentaire
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = commentInput,
                    onValueChange = { commentInput = it },
                    placeholder = { Text("Ajouter un commentaire...") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (commentInput.isNotBlank()) {
                            onAddComment(commentInput)
                            commentInput = ""
                        }
                    }
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Envoyer", tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
