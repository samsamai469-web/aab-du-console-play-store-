package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.theme.SamTertiaryLight
import com.example.data.local.LiveConversationHistoryManager
import com.example.data.model.ConversationLiveState
import com.example.data.model.LiveConversationTurn
import com.example.ui.SamAiViewModel
import com.example.util.ConversationCleaner
import com.example.util.LiveTranslateLogger
import com.example.util.SpeechRecognitionManager
import com.example.util.TtsManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Page « Discussion directe » (Conversation bilingue en direct entre 2 interlocuteurs).
 * Conforme pixel-par-pixel à la maquette de référence avec zones indépendantes pour chaque utilisateur.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveConversationScreen(
    viewModel: SamAiViewModel,
    ttsManager: TtsManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val clipboardManager = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }

    // Liste des langues supportées avec drapeau et code court 2 lettres
    val availableLanguages = remember {
        listOf(
            Triple("Français", "🇫🇷", "FR"),
            Triple("Anglais", "🇬🇧", "EN"),
            Triple("Haoussa", "🇳🇪", "HA"),
            Triple("Mooré", "🇧🇫", "MO"),
            Triple("Dioula", "🇨🇮", "DI"),
            Triple("Allemand", "🇩🇪", "DE"),
            Triple("Arabe", "🇸🇦", "AR"),
            Triple("Espagnol", "🇪🇸", "ES"),
            Triple("Italien", "🇮🇹", "IT"),
            Triple("Russe", "🇷🇺", "RU"),
            Triple("Chinois", "🇨🇳", "ZH"),
            Triple("Portugais", "🇵🇹", "PT")
        )
    }

    fun getLangFlag(langName: String): String {
        return availableLanguages.find { it.first.equals(langName, ignoreCase = true) }?.second ?: "🌍"
    }

    fun getLangCode(langName: String): String {
        return availableLanguages.find { it.first.equals(langName, ignoreCase = true) }?.third ?: "TXT"
    }

    val liveGreen = Color(0xFF16A34A) // Vert vif actif pour la reconnaissance vocale
    val liveGreenLight = Color(0xFFDCFCE7) // Vert clair pour fond actif

    // Utilisateur 1 (Abdoul)
    var user1Name by remember { mutableStateOf("Abdoul") }
    var user1Lang by remember { mutableStateOf("Français") }
    var user1Transcript by remember { mutableStateOf("") }
    var user1SentText by remember { mutableStateOf("") }
    var user1ReceivedText by remember { mutableStateOf("") }
    var isUser1Listening by remember { mutableStateOf(false) }
    var user1Seconds by remember { mutableIntStateOf(0) }
    var user1Status by remember { mutableStateOf("Prêt") }

    // Utilisateur 2 (Aminata - Mooré)
    var user2Name by remember { mutableStateOf("Aminata") }
    var user2Lang by remember { mutableStateOf("Mooré") }
    var user2Transcript by remember { mutableStateOf("") }
    var user2SentText by remember { mutableStateOf("") }
    var user2ReceivedText by remember { mutableStateOf("") }
    var isUser2Listening by remember { mutableStateOf(false) }
    var user2Seconds by remember { mutableIntStateOf(0) }
    var user2Status by remember { mutableStateOf("Prêt") }

    // États globaux
    var speechSpeedRate by remember { mutableFloatStateOf(1.0f) }
    var typedMessage by remember { mutableStateOf("") }
    var typedSenderIsUser1 by remember { mutableStateOf(true) } // true = Abdoul -> Aminata, false = Aminata -> Abdoul
    var lastSpeaker by remember { mutableIntStateOf(1) } // 1 = Abdoul (User 1), 2 = Aminata (User 2)
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var turnsList by remember { mutableStateOf<List<LiveConversationTurn>>(emptyList()) }

    // Menus et modales
    var showTopMenu by remember { mutableStateOf(false) }
    var showUser1LangPicker by remember { mutableStateOf(false) }
    var showUser2LangPicker by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var showDebugConsole by remember { mutableStateOf(false) }
    var showPermissionSettingsDialog by remember { mutableStateOf(false) }
    var isMicPermissionPermanentlyDenied by remember { mutableStateOf(false) }

    // Animation de pulsation subtile et professionnelle quand la parole est active
    val infinitePulseTransition = rememberInfiniteTransition(label = "pulseTransition")
    val pulseScaleUser1 by infinitePulseTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale1"
    )
    val pulseScaleUser2 by infinitePulseTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale2"
    )

    // Reconnaissance vocale indépendante pour les deux utilisateurs
    val user1SpeechManager = remember { SpeechRecognitionManager(context) }
    val user2SpeechManager = remember { SpeechRecognitionManager(context) }

    val user1Partial by user1SpeechManager.partialText.collectAsStateWithLifecycle()
    val user2Partial by user2SpeechManager.partialText.collectAsStateWithLifecycle()
    val user1SoundLevel by user1SpeechManager.soundLevel.collectAsStateWithLifecycle()
    val user2SoundLevel by user2SpeechManager.soundLevel.collectAsStateWithLifecycle()

    val debugLogs by LiveTranslateLogger.logsFlow.collectAsStateWithLifecycle()

    // Vérification permission micro
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Gestion de la permission runtime
    var pendingMicTargetUser by remember { mutableIntStateOf(1) } // 1 = Abdoul, 2 = Aminata

    fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Impossible d'ouvrir les paramètres : ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                hasMicPermission = granted
                if (granted) {
                    isMicPermissionPermanentlyDenied = false
                    showPermissionSettingsDialog = false
                    errorMessage = null
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            ttsManager.stop()
            user1SpeechManager.resetState()
            user2SpeechManager.resetState()
        }
    }

    // Chargement de l'historique hebdomadaire avec auto-réinitialisation (7 jours)
    LaunchedEffect(Unit) {
        turnsList = LiveConversationHistoryManager.getHistory(context)
    }

    // Mise à jour de la transcription partielle en direct
    LaunchedEffect(user1Partial) {
        if (isUser1Listening && user1Partial.isNotBlank()) {
            user1Transcript = user1Partial
        }
    }

    LaunchedEffect(user2Partial) {
        if (isUser2Listening && user2Partial.isNotBlank()) {
            user2Transcript = user2Partial
        }
    }

    // Chronomètres indépendants
    LaunchedEffect(isUser1Listening) {
        if (isUser1Listening) {
            user1Seconds = 0
            while (isUser1Listening) {
                delay(1000)
                if (isUser1Listening) {
                    user1Seconds++
                }
            }
        }
    }

    LaunchedEffect(isUser2Listening) {
        if (isUser2Listening) {
            user2Seconds = 0
            while (isUser2Listening) {
                delay(1000)
                if (isUser2Listening) {
                    user2Seconds++
                }
            }
        }
    }

    // Réinitialisation complète et instantanée de la conversation
    fun resetConversation() {
        user1SpeechManager.stopListening()
        user2SpeechManager.stopListening()
        ttsManager.stop()
        isUser1Listening = false
        isUser2Listening = false
        user1Transcript = ""
        user2Transcript = ""
        user1SentText = ""
        user1ReceivedText = ""
        user2SentText = ""
        user2ReceivedText = ""
        user1Status = "Prêt"
        user2Status = "Prêt"
        errorMessage = null
        Toast.makeText(context, "Conversation réinitialisée", Toast.LENGTH_SHORT).show()
    }

    // Traduction et traitement de la parole d'Abdoul (User 1 -> User 2)
    fun processUser1SpokenSpeech(textSpoken: String) {
        val clean = ConversationCleaner.cleanVoiceTranscript(textSpoken).ifBlank { textSpoken.trim() }
        if (clean.isBlank()) {
            isUser1Listening = false
            user1Status = "Prêt"
            return
        }

        lastSpeaker = 1
        user1SentText = clean
        user1Transcript = clean
        isUser1Listening = false
        user1Status = "Traduction..."
        user2Status = "Réception..."

        coroutineScope.launch {
            try {
                LiveTranslateLogger.logTranslation(user1Lang, user2Lang, "User 1: \"$clean\"")
                val rawTranslated = viewModel.translateDirectlyForLiveConversation(
                    text = clean,
                    sourceLang = user1Lang,
                    targetLang = user2Lang
                )
                val translated = ConversationCleaner.polishTranslatedText(rawTranslated).ifBlank { rawTranslated.trim() }

                if (translated.isNotBlank()) {
                    user2ReceivedText = translated
                    user2Transcript = translated
                    user1Status = "Prêt"
                    user2Status = "Lecture audio..."

                    val turn = LiveConversationTurn(
                        sourceLang = user1Lang,
                        targetLang = user2Lang,
                        originalText = clean,
                        translatedText = translated,
                        timestamp = System.currentTimeMillis()
                    )
                    LiveConversationHistoryManager.saveTurn(context, turn)
                    turnsList = LiveConversationHistoryManager.getHistory(context)
                    viewModel.saveLiveConversationTurn(turn)

                    // Lecture vocale automatique à haute voix pour le destinataire
                    ttsManager.speakWithCallback(
                        text = translated,
                        language = user2Lang,
                        rate = speechSpeedRate,
                        onStart = { user2Status = "Lecture audio..." },
                        onDone = { user2Status = "Prêt" },
                        onError = { user2Status = "Prêt" }
                    )
                } else {
                    user2ReceivedText = "Traduction indisponible"
                    user2Transcript = "Traduction indisponible"
                    user1Status = "Prêt"
                    user2Status = "Prêt"
                }
            } catch (e: Exception) {
                user1Status = "Erreur"
                user2Status = "Erreur"
                errorMessage = "Erreur: ${e.message}"
            }
        }
    }

    // Traduction et traitement de la parole d'Aminata (User 2 -> User 1)
    fun processUser2SpokenSpeech(textSpoken: String) {
        val clean = ConversationCleaner.cleanVoiceTranscript(textSpoken).ifBlank { textSpoken.trim() }
        if (clean.isBlank()) {
            isUser2Listening = false
            user2Status = "Prêt"
            return
        }

        lastSpeaker = 2
        user2SentText = clean
        user2Transcript = clean
        isUser2Listening = false
        user2Status = "Traduction..."
        user1Status = "Réception..."

        coroutineScope.launch {
            try {
                LiveTranslateLogger.logTranslation(user2Lang, user1Lang, "User 2: \"$clean\"")
                val rawTranslated = viewModel.translateDirectlyForLiveConversation(
                    text = clean,
                    sourceLang = user2Lang,
                    targetLang = user1Lang
                )
                val translated = ConversationCleaner.polishTranslatedText(rawTranslated).ifBlank { rawTranslated.trim() }

                if (translated.isNotBlank()) {
                    user1ReceivedText = translated
                    user1Transcript = translated
                    user2Status = "Prêt"
                    user1Status = "Lecture audio..."

                    val turn = LiveConversationTurn(
                        sourceLang = user2Lang,
                        targetLang = user1Lang,
                        originalText = clean,
                        translatedText = translated,
                        timestamp = System.currentTimeMillis()
                    )
                    LiveConversationHistoryManager.saveTurn(context, turn)
                    turnsList = LiveConversationHistoryManager.getHistory(context)
                    viewModel.saveLiveConversationTurn(turn)

                    // Lecture vocale automatique à haute voix pour le destinataire
                    ttsManager.speakWithCallback(
                        text = translated,
                        language = user1Lang,
                        rate = speechSpeedRate,
                        onStart = { user1Status = "Lecture audio..." },
                        onDone = { user1Status = "Prêt" },
                        onError = { user1Status = "Prêt" }
                    )
                } else {
                    user1ReceivedText = "Traduction indisponible"
                    user1Transcript = "Traduction indisponible"
                    user1Status = "Prêt"
                    user2Status = "Prêt"
                }
            } catch (e: Exception) {
                user1Status = "Erreur"
                user2Status = "Erreur"
                errorMessage = "Erreur: ${e.message}"
            }
        }
    }

    // Démarrage de la reconnaissance vocale pour User 1 (Abdoul)
    fun startUser1Listening() {
        // Arrête toute écoute d'Aminata
        user2SpeechManager.stopListening()
        isUser2Listening = false
        user2Status = "Prêt"
        ttsManager.stop()

        isUser1Listening = true
        user1Status = "Écoute en cours..."
        errorMessage = null

        user1SpeechManager.startListening(
            language = user1Lang,
            continuousMode = false,
            onResult = { result ->
                if (result.isNotBlank()) {
                    user1Transcript = result
                    isUser1Listening = false
                    user1SpeechManager.stopListening()
                    processUser1SpokenSpeech(result)
                }
            },
            onError = { err ->
                isUser1Listening = false
                user1Status = "Prêt"
            }
        )
    }

    // Démarrage de la reconnaissance vocale pour User 2 (Aminata)
    fun startUser2Listening() {
        // Arrête toute écoute d'Abdoul
        user1SpeechManager.stopListening()
        isUser1Listening = false
        user1Status = "Prêt"
        ttsManager.stop()

        isUser2Listening = true
        user2Status = "Écoute en cours..."
        errorMessage = null

        user2SpeechManager.startListening(
            language = user2Lang,
            continuousMode = false,
            onResult = { result ->
                if (result.isNotBlank()) {
                    user2Transcript = result
                    isUser2Listening = false
                    user2SpeechManager.stopListening()
                    processUser2SpokenSpeech(result)
                }
            },
            onError = { err ->
                isUser2Listening = false
                user2Status = "Prêt"
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasMicPermission = isGranted
        if (isGranted) {
            errorMessage = null
            isMicPermissionPermanentlyDenied = false
            showPermissionSettingsDialog = false
            if (pendingMicTargetUser == 1) {
                user1Transcript = ""
                startUser1Listening()
            } else {
                user2Transcript = ""
                startUser2Listening()
            }
        } else {
            val activity = context as? Activity
            val canShowRationale = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.RECORD_AUDIO)
            } ?: false

            if (!canShowRationale) {
                isMicPermissionPermanentlyDenied = true
                showPermissionSettingsDialog = true
            } else {
                errorMessage = "L'accès au microphone est requis pour parler."
            }
        }
    }

    fun onUser1MicClicked() {
        if (isUser1Listening) {
            val spoken = user1Transcript.trim().ifBlank { user1SpeechManager.partialText.value.trim() }
            user1SpeechManager.stopListening()
            isUser1Listening = false
            user1Status = "Prêt"

            if (spoken.isNotBlank()) {
                processUser1SpokenSpeech(spoken)
            }
        } else {
            if (hasMicPermission) {
                user1Transcript = ""
                startUser1Listening()
            } else {
                pendingMicTargetUser = 1
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    fun onUser2MicClicked() {
        if (isUser2Listening) {
            val spoken = user2Transcript.trim().ifBlank { user2SpeechManager.partialText.value.trim() }
            user2SpeechManager.stopListening()
            isUser2Listening = false
            user2Status = "Prêt"

            if (spoken.isNotBlank()) {
                processUser2SpokenSpeech(spoken)
            }
        } else {
            if (hasMicPermission) {
                user2Transcript = ""
                startUser2Listening()
            } else {
                pendingMicTargetUser = 2
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    // Inversion des langues (⇄)
    fun onSwapLanguages() {
        val tempLang = user1Lang
        user1Lang = user2Lang
        user2Lang = tempLang

        val tempTranscript = user1Transcript
        user1Transcript = user2Transcript
        user2Transcript = tempTranscript

        user1SpeechManager.stopListening()
        user2SpeechManager.stopListening()
        isUser1Listening = false
        isUser2Listening = false
        user1Status = "Prêt"
        user2Status = "Prêt"

        Toast.makeText(context, "Langues inversées : $user1Lang ⇄ $user2Lang", Toast.LENGTH_SHORT).show()
    }

    // Envoi du message tapé dans la barre de saisie inférieure
    fun onSendTypedMessage() {
        val raw = typedMessage.trim()
        if (raw.isBlank()) return
        val text = ConversationCleaner.cleanVoiceTranscript(raw).ifBlank { raw }

        typedMessage = ""
        keyboardController?.hide()

        if (typedSenderIsUser1) {
            // Abdoul (User 1) -> Aminata (User 2)
            lastSpeaker = 1
            user1SentText = text
            user1Transcript = text
            user1Status = "Prêt"
            user2Status = "Traduction..."

            coroutineScope.launch {
                try {
                    LiveTranslateLogger.logTranslation(user1Lang, user2Lang, "Texte Abdoul: \"$text\"")
                    val rawTranslated = viewModel.translateDirectlyForLiveConversation(
                        text = text,
                        sourceLang = user1Lang,
                        targetLang = user2Lang
                    )
                    val translated = ConversationCleaner.polishTranslatedText(rawTranslated)
                    if (translated.isNotBlank()) {
                        user2ReceivedText = translated
                        user2Transcript = translated
                        user2Status = "Lecture audio..."

                        val turn = LiveConversationTurn(
                            sourceLang = user1Lang,
                            targetLang = user2Lang,
                            originalText = text,
                            translatedText = translated,
                            timestamp = System.currentTimeMillis()
                        )
                        turnsList = listOf(turn) + turnsList
                        viewModel.saveLiveConversationTurn(turn)

                        ttsManager.speakWithCallback(
                            text = translated,
                            language = user2Lang,
                            rate = speechSpeedRate,
                            onStart = { user2Status = "Lecture audio..." },
                            onDone = { user2Status = "Prêt" },
                            onError = { user2Status = "Prêt" }
                        )
                    } else {
                        user2ReceivedText = "Traduction indisponible"
                        user2Transcript = "Traduction indisponible"
                        user2Status = "Prêt"
                    }
                } catch (e: Exception) {
                    user2Status = "Erreur"
                    errorMessage = e.message
                }
            }
        } else {
            // Aminata (User 2) -> Abdoul (User 1)
            lastSpeaker = 2
            user2SentText = text
            user2Transcript = text
            user2Status = "Prêt"
            user1Status = "Traduction..."

            coroutineScope.launch {
                try {
                    LiveTranslateLogger.logTranslation(user2Lang, user1Lang, "Texte Aminata: \"$text\"")
                    val rawTranslated = viewModel.translateDirectlyForLiveConversation(
                        text = text,
                        sourceLang = user2Lang,
                        targetLang = user1Lang
                    )
                    val translated = ConversationCleaner.polishTranslatedText(rawTranslated)
                    if (translated.isNotBlank()) {
                        user1ReceivedText = translated
                        user1Transcript = translated
                        user1Status = "Lecture audio..."

                        val turn = LiveConversationTurn(
                            sourceLang = user2Lang,
                            targetLang = user1Lang,
                            originalText = text,
                            translatedText = translated,
                            timestamp = System.currentTimeMillis()
                        )
                        turnsList = listOf(turn) + turnsList
                        viewModel.saveLiveConversationTurn(turn)

                        ttsManager.speakWithCallback(
                            text = translated,
                            language = user1Lang,
                            rate = speechSpeedRate,
                            onStart = { user1Status = "Lecture audio..." },
                            onDone = { user1Status = "Prêt" },
                            onError = { user1Status = "Prêt" }
                        )
                    } else {
                        user1ReceivedText = "Traduction indisponible"
                        user1Transcript = "Traduction indisponible"
                        user1Status = "Prêt"
                    }
                } catch (e: Exception) {
                    user1Status = "Erreur"
                    errorMessage = e.message
                }
            }
        }
    }

    fun formatTimer(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format(java.util.Locale.US, "%02d:%02d", mins, secs)
    }

    // Palette de style adaptée au thème de l'application (Light & Dark)
    val colorScheme = MaterialTheme.colorScheme
    val isDark = isSystemInDarkTheme()

    val surfaceCardColor = colorScheme.surface
    val surfaceVariantColor = colorScheme.surfaceVariant
    val borderColor = colorScheme.outlineVariant
    val primaryColor = colorScheme.primary
    val onSurfaceColor = colorScheme.onSurface
    val textSecondaryColor = colorScheme.onSurfaceVariant
    val badgeBgColor = colorScheme.primaryContainer
    val badgeTextColor = colorScheme.onPrimaryContainer

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Discussion en direct",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp
                        ),
                        color = onSurfaceColor
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            user1SpeechManager.stopListening()
                            user2SpeechManager.stopListening()
                            ttsManager.stop()
                            onDismiss()
                        },
                        modifier = Modifier.testTag("btn_back_discussion_directe")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour",
                            tint = onSurfaceColor
                        )
                    }
                },
                actions = {
                    // Bouton Effacer / Remise à zéro direct (Recommencer une conversation propre)
                    IconButton(
                        onClick = { resetConversation() },
                        modifier = Modifier.testTag("btn_clear_live_conversation")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Effacer la conversation",
                            tint = onSurfaceColor
                        )
                    }

                    // Bouton Historique (avec auto-réinitialisation 7 jours)
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                turnsList = LiveConversationHistoryManager.getHistory(context)
                                showHistorySheet = true
                            }
                        },
                        modifier = Modifier.testTag("btn_history_live")
                    ) {
                        BadgedBox(
                            badge = {
                                if (turnsList.isNotEmpty()) {
                                    Badge(
                                        containerColor = primaryColor,
                                        contentColor = colorScheme.onPrimary
                                    ) {
                                        Text(turnsList.size.toString())
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "Historique en direct",
                                tint = onSurfaceColor
                            )
                        }
                    }

                    // Petits points en haut (Menu 3 points / Options)
                    Box {
                        IconButton(
                            onClick = { showTopMenu = true },
                            modifier = Modifier.testTag("btn_top_options_menu")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Plus d'options",
                                tint = onSurfaceColor
                            )
                        }

                        DropdownMenu(
                            expanded = showTopMenu,
                            onDismissRequest = { showTopMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Historique hebdomadaire (${turnsList.size})") },
                                onClick = {
                                    showTopMenu = false
                                    coroutineScope.launch {
                                        turnsList = LiveConversationHistoryManager.getHistory(context)
                                        showHistorySheet = true
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.History, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Effacer et réinitialiser l'écran") },
                                onClick = {
                                    resetConversation()
                                    showTopMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Vitesse audio : ${speechSpeedRate}x") },
                                onClick = {
                                    speechSpeedRate = when (speechSpeedRate) {
                                        1.0f -> 1.25f
                                        1.25f -> 0.85f
                                        else -> 1.0f
                                    }
                                    Toast.makeText(context, "Vitesse: ${speechSpeedRate}x", Toast.LENGTH_SHORT).show()
                                    showTopMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Speed, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Journal diagnostic en direct") },
                                onClick = {
                                    showTopMenu = false
                                    showDebugConsole = !showDebugConsole
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Terminal, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Paramètres de l'application") },
                                onClick = {
                                    showTopMenu = false
                                    openAppSettings()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Settings, contentDescription = null)
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(colorScheme.background)
        ) {
            // ==========================================
            // 1. TOP CONTROLS (Langue 1 : ex: FR)
            // ==========================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Drapeaux et code ISO en dessous
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showUser1LangPicker = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("btn_select_lang_top")
                ) {
                    Text(
                        text = getLangFlag(user1Lang),
                        fontSize = 24.sp
                    )
                    Text(
                        text = getLangCode(user1Lang),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        ),
                        color = onSurfaceColor
                    )
                }

                Spacer(modifier = Modifier.width(20.dp))

                // Bouton Micro Style Pill / Rounded Rectangle
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isUser1Listening) liveGreenLight else (if (isDark) colorScheme.surfaceVariant else Color(0xFFF1F3F5)),
                    border = BorderStroke(
                        width = if (isUser1Listening) 2.dp else 1.dp,
                        color = if (isUser1Listening) liveGreen else borderColor
                    ),
                    modifier = Modifier
                        .size(width = 56.dp, height = 46.dp)
                        .scale(if (isUser1Listening) pulseScaleUser1 else 1.0f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onUser1MicClicked() }
                        .testTag("btn_mic_top")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Microphone $user1Lang",
                            tint = if (isUser1Listening) liveGreen else onSurfaceColor,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Flèche Chevron vers le bas
                IconButton(
                    onClick = { showUser1LangPicker = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Changer la langue",
                        tint = onSurfaceColor.copy(alpha = 0.8f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            HorizontalDivider(
                color = borderColor.copy(alpha = 0.5f),
                thickness = 0.8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            // ==========================================
            // 2. ZONE TRANSCRIPTION UTILISATEUR 1 (Abdoul)
            // ==========================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$user1Name • $user1Lang",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        color = onSurfaceColor
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isUser1Listening) {
                            SoundWaveIndicator(
                                isActive = true,
                                soundLevel = user1SoundLevel,
                                color = liveGreen
                            )
                        }
                        val roleLabel = when {
                            isUser1Listening -> "● Écoute en cours..."
                            user1Status.startsWith("Traduction") -> "⏳ Traduction..."
                            lastSpeaker == 1 && (user1SentText.isNotBlank() || user1Transcript.isNotBlank()) -> "🗣️ Message envoyé"
                            lastSpeaker == 2 && (user1ReceivedText.isNotBlank() || user1Transcript.isNotBlank()) -> "✨ Traduction reçue"
                            else -> "Prêt"
                        }
                        val roleColor = when {
                            isUser1Listening -> liveGreen
                            user1Status.startsWith("Traduction") -> primaryColor
                            lastSpeaker == 2 && user1ReceivedText.isNotBlank() -> primaryColor
                            else -> textSecondaryColor
                        }
                        Text(
                            text = roleLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            ),
                            color = roleColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (isUser1Listening) {
                    // Boîte active pendant que l'utilisateur 1 parle
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = liveGreenLight,
                        border = BorderStroke(1.dp, liveGreen.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = liveGreen,
                                    modifier = Modifier.size(width = 30.dp, height = 20.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = getLangCode(user1Lang),
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                            color = Color.White
                                        )
                                    }
                                }
                                Text(
                                    text = "Écoute en cours... Parlez maintenant",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = liveGreen
                                )
                            }
                            val liveText = user1Partial.ifBlank { user1Transcript }
                            if (liveText.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = liveText,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
                                    color = Color(0xFF0F172A)
                                )
                            }
                        }
                    }
                } else {
                    val hasMessages = user1SentText.isNotBlank() || user1ReceivedText.isNotBlank() || user1Transcript.isNotBlank()

                    if (!hasMessages) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = "Appuyez sur le micro ci-dessus pour parler en $user1Lang...",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                color = textSecondaryColor.copy(alpha = 0.7f)
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 1. Message envoyé par Abdoul
                            val sent = user1SentText.ifBlank { if (lastSpeaker == 1) user1Transcript else "" }
                            if (sent.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isDark) surfaceVariantColor.copy(alpha = 0.5f) else Color(0xFFF8FAFC),
                                    border = BorderStroke(0.8.dp, borderColor.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
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
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = if (isDark) primaryColor.copy(alpha = 0.25f) else primaryColor.copy(alpha = 0.12f),
                                                    modifier = Modifier.size(width = 28.dp, height = 20.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = getLangCode(user1Lang),
                                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                                            color = primaryColor
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = "Message original",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
                                                    color = textSecondaryColor
                                                )
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                IconButton(
                                                    onClick = {
                                                        ttsManager.speakWithCallback(
                                                            text = sent,
                                                            language = user1Lang,
                                                            rate = speechSpeedRate
                                                        )
                                                    },
                                                    modifier = Modifier.size(26.dp)
                                                ) {
                                                    Icon(
                                                        Icons.AutoMirrored.Filled.VolumeUp,
                                                        contentDescription = "Écouter original",
                                                        tint = primaryColor,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                                IconButton(
                                                    onClick = {
                                                        clipboardManager.setPrimaryClip(ClipData.newPlainText("Live", sent))
                                                        Toast.makeText(context, "Copié !", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.size(26.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.ContentCopy,
                                                        contentDescription = "Copier",
                                                        tint = textSecondaryColor,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = sent,
                                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 21.sp),
                                            color = onSurfaceColor
                                        )
                                    }
                                }
                            }

                            // 2. Traduction reçue pour Abdoul (provenant d'Aminata)
                            val received = user1ReceivedText.ifBlank { if (lastSpeaker == 2) user1Transcript else "" }
                            if (received.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isDark) primaryColor.copy(alpha = 0.18f) else primaryColor.copy(alpha = 0.10f),
                                    border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
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
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = primaryColor,
                                                    modifier = Modifier.size(width = 28.dp, height = 20.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = getLangCode(user1Lang),
                                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                                            color = colorScheme.onPrimary
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = "✨ Traduction de $user2Name",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                                                    color = primaryColor
                                                )
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                IconButton(
                                                    onClick = {
                                                        ttsManager.speakWithCallback(
                                                            text = received,
                                                            language = user1Lang,
                                                            rate = speechSpeedRate
                                                        )
                                                    },
                                                    modifier = Modifier.size(26.dp)
                                                ) {
                                                    Icon(
                                                        Icons.AutoMirrored.Filled.VolumeUp,
                                                        contentDescription = "Écouter traduction",
                                                        tint = primaryColor,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                                IconButton(
                                                    onClick = {
                                                        clipboardManager.setPrimaryClip(ClipData.newPlainText("Live", received))
                                                        Toast.makeText(context, "Copié !", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.size(26.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.ContentCopy,
                                                        contentDescription = "Copier",
                                                        tint = textSecondaryColor,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = received,
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Medium,
                                                lineHeight = 21.sp
                                            ),
                                            color = onSurfaceColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(
                color = borderColor.copy(alpha = 0.5f),
                thickness = 0.8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            // ==========================================
            // 3. ZONE TRANSCRIPTION UTILISATEUR 2 (Aminata)
            // ==========================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$user2Name • $user2Lang",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        color = onSurfaceColor
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isUser2Listening) {
                            SoundWaveIndicator(
                                isActive = true,
                                soundLevel = user2SoundLevel,
                                color = liveGreen
                            )
                        }
                        val roleLabel = when {
                            isUser2Listening -> "● Écoute en cours..."
                            user2Status.startsWith("Traduction") -> "⏳ Traduction..."
                            lastSpeaker == 2 && (user2SentText.isNotBlank() || user2Transcript.isNotBlank()) -> "🗣️ Message envoyé"
                            lastSpeaker == 1 && (user2ReceivedText.isNotBlank() || user2Transcript.isNotBlank()) -> "✨ Traduction reçue"
                            else -> "Prêt"
                        }
                        val roleColor = when {
                            isUser2Listening -> liveGreen
                            user2Status.startsWith("Traduction") -> primaryColor
                            lastSpeaker == 1 && user2ReceivedText.isNotBlank() -> primaryColor
                            else -> textSecondaryColor
                        }
                        Text(
                            text = roleLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            ),
                            color = roleColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (isUser2Listening) {
                    // Boîte active pendant que l'utilisateur 2 parle
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = liveGreenLight,
                        border = BorderStroke(1.dp, liveGreen.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = liveGreen,
                                    modifier = Modifier.size(width = 30.dp, height = 20.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = getLangCode(user2Lang),
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                            color = Color.White
                                        )
                                    }
                                }
                                Text(
                                    text = "Écoute en cours... Parlez maintenant",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = liveGreen
                                )
                            }
                            val liveText = user2Partial.ifBlank { user2Transcript }
                            if (liveText.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = liveText,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
                                    color = Color(0xFF0F172A)
                                )
                            }
                        }
                    }
                } else {
                    val hasMessages = user2SentText.isNotBlank() || user2ReceivedText.isNotBlank() || user2Transcript.isNotBlank()

                    if (!hasMessages) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = "Appuyez sur le micro ci-dessous pour parler en $user2Lang...",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                color = textSecondaryColor.copy(alpha = 0.7f)
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 1. Message envoyé par Aminata
                            val sent = user2SentText.ifBlank { if (lastSpeaker == 2) user2Transcript else "" }
                            if (sent.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isDark) surfaceVariantColor.copy(alpha = 0.5f) else Color(0xFFF8FAFC),
                                    border = BorderStroke(0.8.dp, borderColor.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
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
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = if (isDark) primaryColor.copy(alpha = 0.25f) else primaryColor.copy(alpha = 0.12f),
                                                    modifier = Modifier.size(width = 28.dp, height = 20.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = getLangCode(user2Lang),
                                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                                            color = primaryColor
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = "Message original",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
                                                    color = textSecondaryColor
                                                )
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                IconButton(
                                                    onClick = {
                                                        ttsManager.speakWithCallback(
                                                            text = sent,
                                                            language = user2Lang,
                                                            rate = speechSpeedRate
                                                        )
                                                    },
                                                    modifier = Modifier.size(26.dp)
                                                ) {
                                                    Icon(
                                                        Icons.AutoMirrored.Filled.VolumeUp,
                                                        contentDescription = "Écouter original",
                                                        tint = primaryColor,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                                IconButton(
                                                    onClick = {
                                                        clipboardManager.setPrimaryClip(ClipData.newPlainText("Live", sent))
                                                        Toast.makeText(context, "Copié !", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.size(26.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.ContentCopy,
                                                        contentDescription = "Copier",
                                                        tint = textSecondaryColor,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = sent,
                                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 21.sp),
                                            color = onSurfaceColor
                                        )
                                    }
                                }
                            }

                            // 2. Traduction reçue pour Aminata (provenant d'Abdoul)
                            val received = user2ReceivedText.ifBlank { if (lastSpeaker == 1) user2Transcript else "" }
                            if (received.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isDark) primaryColor.copy(alpha = 0.18f) else primaryColor.copy(alpha = 0.10f),
                                    border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
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
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = primaryColor,
                                                    modifier = Modifier.size(width = 28.dp, height = 20.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = getLangCode(user2Lang),
                                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                                            color = colorScheme.onPrimary
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = "✨ Traduction d'$user1Name",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                                                    color = primaryColor
                                                )
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                IconButton(
                                                    onClick = {
                                                        ttsManager.speakWithCallback(
                                                            text = received,
                                                            language = user2Lang,
                                                            rate = speechSpeedRate
                                                        )
                                                    },
                                                    modifier = Modifier.size(26.dp)
                                                ) {
                                                    Icon(
                                                        Icons.AutoMirrored.Filled.VolumeUp,
                                                        contentDescription = "Écouter traduction",
                                                        tint = primaryColor,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                                IconButton(
                                                    onClick = {
                                                        clipboardManager.setPrimaryClip(ClipData.newPlainText("Live", received))
                                                        Toast.makeText(context, "Copié !", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.size(26.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.ContentCopy,
                                                        contentDescription = "Copier",
                                                        tint = textSecondaryColor,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = received,
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Medium,
                                                lineHeight = 21.sp
                                            ),
                                            color = onSurfaceColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Message d'erreur éventuel
            if (errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            Text(
                                text = errorMessage ?: "",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        IconButton(
                            onClick = { errorMessage = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Fermer",
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // ==========================================
            // 4. BAS DE PAGE : STREAM IS LIVE & CONTROLES DU BAS
            // ==========================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 72.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Divider avec texte au milieu "En direct" et bouton rapide d'effacement
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = borderColor.copy(alpha = 0.5f),
                        thickness = 0.8.dp
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 10.dp)
                    ) {
                        Text(
                            text = "En direct",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Normal,
                                fontSize = 15.sp
                            ),
                            color = onSurfaceColor
                        )

                        if (user1Transcript.isNotBlank() || user2Transcript.isNotBlank() || user1SentText.isNotBlank() || user2SentText.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = colorScheme.surfaceVariant.copy(alpha = 0.8f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { resetConversation() }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "Effacer la discussion",
                                        tint = textSecondaryColor,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "Effacer",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = textSecondaryColor
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = borderColor.copy(alpha = 0.5f),
                        thickness = 0.8.dp
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Contrôles du bas (Langue 2 : ex: EN)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Drapeaux et code ISO en dessous
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showUser2LangPicker = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("btn_select_lang_bottom")
                    ) {
                        Text(
                            text = getLangFlag(user2Lang),
                            fontSize = 24.sp
                        )
                        Text(
                            text = getLangCode(user2Lang),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            ),
                            color = onSurfaceColor
                        )
                    }

                    Spacer(modifier = Modifier.width(20.dp))

                    // Bouton Micro Style Pill / Rounded Rectangle
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isUser2Listening) liveGreenLight else (if (isDark) colorScheme.surfaceVariant else Color(0xFFF1F3F5)),
                        border = BorderStroke(
                            width = if (isUser2Listening) 2.dp else 1.dp,
                            color = if (isUser2Listening) liveGreen else borderColor
                        ),
                        modifier = Modifier
                            .size(width = 56.dp, height = 46.dp)
                            .scale(if (isUser2Listening) pulseScaleUser2 else 1.0f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onUser2MicClicked() }
                            .testTag("btn_mic_bottom")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Microphone $user2Lang",
                                tint = if (isUser2Listening) liveGreen else onSurfaceColor,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    // Flèche Chevron vers le bas
                    IconButton(
                        onClick = { showUser2LangPicker = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Changer la langue",
                            tint = onSurfaceColor.copy(alpha = 0.8f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }

    // ==========================================
    // MODALES & FEUILLES INFÉRIEURES
    // ==========================================

    // Modal Sélecteur Langue Abdoul
    if (showUser1LangPicker) {
        AlertDialog(
            onDismissRequest = { showUser1LangPicker = false },
            title = { Text("Langue de $user1Name") },
            text = {
                LazyColumn {
                    items(availableLanguages) { (langName, flag, _) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    user1Lang = langName
                                    showUser1LangPicker = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(flag, fontSize = 22.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = langName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (user1Lang == langName) FontWeight.Bold else FontWeight.Normal
                            )
                            if (user1Lang == langName) {
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(Icons.Default.Check, contentDescription = null, tint = primaryColor)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showUser1LangPicker = false }) {
                    Text("Fermer")
                }
            }
        )
    }

    // Modal Sélecteur Langue Aminata
    if (showUser2LangPicker) {
        AlertDialog(
            onDismissRequest = { showUser2LangPicker = false },
            title = { Text("Langue de $user2Name") },
            text = {
                LazyColumn {
                    items(availableLanguages) { (langName, flag, _) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    user2Lang = langName
                                    showUser2LangPicker = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(flag, fontSize = 22.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = langName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (user2Lang == langName) FontWeight.Bold else FontWeight.Normal
                            )
                            if (user2Lang == langName) {
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(Icons.Default.Check, contentDescription = null, tint = primaryColor)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showUser2LangPicker = false }) {
                    Text("Fermer")
                }
            }
        )
    }

    // Modal Historique des échanges
    if (showHistorySheet) {
        ModalBottomSheet(onDismissRequest = { showHistorySheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Historique de la discussion",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (turnsList.isNotEmpty()) {
                        TextButton(onClick = { turnsList = emptyList() }) {
                            Text("Effacer")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (turnsList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Aucun échange enregistré pour le moment.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = textSecondaryColor
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(turnsList) { turn ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = surfaceVariantColor)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${turn.sourceLang} → ${turn.targetLang}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = primaryColor
                                        )
                                        Row {
                                            IconButton(
                                                onClick = {
                                                    ttsManager.speakWithCallback(
                                                        text = turn.translatedText,
                                                        language = turn.targetLang,
                                                        rate = speechSpeedRate
                                                    )
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.VolumeUp,
                                                    contentDescription = "Écouter",
                                                    modifier = Modifier.size(16.dp),
                                                    tint = primaryColor
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    clipboardManager.setPrimaryClip(ClipData.newPlainText("Traduction", turn.translatedText))
                                                    Toast.makeText(context, "Copié !", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.ContentCopy,
                                                    contentDescription = "Copier",
                                                    modifier = Modifier.size(16.dp),
                                                    tint = textSecondaryColor
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = "Original : ${turn.originalText}", style = MaterialTheme.typography.bodySmall, color = onSurfaceColor)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Traduction : ${turn.translatedText}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = primaryColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Journal Diagnostic
    if (showDebugConsole) {
        AlertDialog(
            onDismissRequest = { showDebugConsole = false },
            title = { Text("Journal Diagnostic Temps Réel") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (debugLogs.isEmpty()) {
                        Text("Aucun événement dans le journal.", color = Color.Gray)
                    } else {
                        debugLogs.takeLast(25).forEach { log ->
                            Text(
                                text = "[${log.timestamp}] ${log.stage.tag}: ${log.message}",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = if (log.isError) Color.Red else Color.DarkGray,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDebugConsole = false }) {
                    Text("Fermer")
                }
            }
        )
    }

    // Modal Erreur Permission Microphone
    if (showPermissionSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionSettingsDialog = false },
            icon = { Icon(Icons.Default.MicOff, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Microphone bloqué par Android") },
            text = {
                Text("Pour permettre à chaque interlocuteur de parler et traduire sa voix, activez l'autorisation Microphone dans les paramètres de l'application.")
            },
            confirmButton = {
                Button(onClick = {
                    showPermissionSettingsDialog = false
                    openAppSettings()
                }) {
                    Text("Ouvrir les Paramètres")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionSettingsDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }
}

/**
 * Barres verticales ondulantes fluides qui réagissent visuellement en temps réel au niveau sonore RMS.
 */
@Composable
private fun SoundWaveIndicator(
    isActive: Boolean,
    soundLevel: Float,
    color: Color
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveTransition")
    val animFactor1 by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave1"
    )
    val animFactor2 by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(340, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave2"
    )
    val animFactor3 by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(480, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave3"
    )

    Row(
        modifier = Modifier
            .height(28.dp)
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val bar1Height = if (isActive) (8f + (soundLevel * 18f * animFactor1)).coerceIn(6f, 22f).dp else 8.dp
        val bar2Height = if (isActive) (12f + (soundLevel * 24f * animFactor2)).coerceIn(8f, 26f).dp else 14.dp
        val bar3Height = if (isActive) (10f + (soundLevel * 22f * animFactor3)).coerceIn(7f, 24f).dp else 11.dp
        val bar4Height = if (isActive) (7f + (soundLevel * 16f * animFactor1)).coerceIn(6f, 18f).dp else 7.dp

        Box(
            modifier = Modifier
                .width(3.dp)
                .height(bar1Height)
                .clip(RoundedCornerShape(2.dp))
                .background(if (isActive) color else color.copy(alpha = 0.35f))
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(bar2Height)
                .clip(RoundedCornerShape(2.dp))
                .background(if (isActive) color else color.copy(alpha = 0.35f))
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(bar3Height)
                .clip(RoundedCornerShape(2.dp))
                .background(if (isActive) color else color.copy(alpha = 0.35f))
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(bar4Height)
                .clip(RoundedCornerShape(2.dp))
                .background(if (isActive) color else color.copy(alpha = 0.35f))
        )
    }
}
