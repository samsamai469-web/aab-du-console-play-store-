package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.remote.VoiceIntentResult
import com.example.ui.SamAiViewModel
import com.example.util.SpeechRecognitionManager
import com.example.util.TtsManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfessionalVoiceModal(
    viewModel: SamAiViewModel,
    ttsManager: TtsManager? = null,
    onDismiss: () -> Unit,
    onNavigateToScreen: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val speechManager = remember { SpeechRecognitionManager(context) }

    val sourceLang by viewModel.sourceLanguage.collectAsStateWithLifecycle()
    val targetLang by viewModel.targetLanguage.collectAsStateWithLifecycle()
    val isAiProcessing by viewModel.isVoiceAssistantProcessing.collectAsStateWithLifecycle()
    val detectedIntent by viewModel.voiceAssistantIntent.collectAsStateWithLifecycle()

    var isListening by remember { mutableStateOf(false) }
    var rawLiveTranscript by remember { mutableStateOf("") }
    var soundLevel by remember { mutableFloatStateOf(0f) }
    var editableText by remember { mutableStateOf("") }
    var isEditing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Prêt à écouter votre phrase") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedVoiceLanguage by remember { mutableStateOf(if (sourceLang.isNotBlank()) sourceLang else "Français") }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            errorMessage = null
            startListeningInternal(
                speechManager = speechManager,
                language = selectedVoiceLanguage,
                onUpdate = { text, lvl, status ->
                    rawLiveTranscript = text
                    editableText = text
                    soundLevel = lvl
                    statusMessage = status
                    viewModel.updateVoiceAssistantStream(text, lvl)
                },
                onComplete = { finalText ->
                    isListening = false
                    soundLevel = 0f
                    statusMessage = "Traitement & Ponctuation intelligente..."
                    viewModel.processVoiceTranscriptWithAi(
                        rawTranscript = finalText,
                        currentLanguage = selectedVoiceLanguage,
                        autoApplyToSourceText = true
                    ) { result ->
                        editableText = result.correctedText
                        statusMessage = "Phrase comprise avec succès !"
                    }
                },
                onError = { err ->
                    isListening = false
                    soundLevel = 0f
                    errorMessage = err
                    statusMessage = "En attente"
                }
            )
            isListening = true
        } else {
            errorMessage = "Veuillez autoriser l'accès au microphone pour utiliser la reconnaissance vocale."
        }
    }

    fun triggerStartListening() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            errorMessage = null
            startListeningInternal(
                speechManager = speechManager,
                language = selectedVoiceLanguage,
                onUpdate = { text, lvl, status ->
                    rawLiveTranscript = text
                    editableText = text
                    soundLevel = lvl
                    statusMessage = status
                    viewModel.updateVoiceAssistantStream(text, lvl)
                },
                onComplete = { finalText ->
                    isListening = false
                    soundLevel = 0f
                    statusMessage = "Traitement & Ponctuation intelligente..."
                    viewModel.processVoiceTranscriptWithAi(
                        rawTranscript = finalText,
                        currentLanguage = selectedVoiceLanguage,
                        autoApplyToSourceText = true
                    ) { result ->
                        editableText = result.correctedText
                        statusMessage = "Phrase comprise avec succès !"
                    }
                },
                onError = { err ->
                    isListening = false
                    soundLevel = 0f
                    errorMessage = err
                    statusMessage = "En attente"
                }
            )
            isListening = true
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun stopAndProcess() {
        speechManager.stopListening()
        isListening = false
        soundLevel = 0f
        if (editableText.isNotBlank()) {
            statusMessage = "Traitement & Ponctuation intelligente..."
            viewModel.processVoiceTranscriptWithAi(
                rawTranscript = editableText,
                currentLanguage = selectedVoiceLanguage,
                autoApplyToSourceText = true
            ) { result ->
                editableText = result.correctedText
                statusMessage = "Phrase comprise avec succès !"
            }
        }
    }

    // Démarrage automatique au premier affichage
    LaunchedEffect(Unit) {
        triggerStartListening()
    }

    DisposableEffect(Unit) {
        onDispose {
            speechManager.stopListening()
        }
    }

    // Animation de pulsation du bouton micro
    val infiniteTransition = rememberInfiniteTransition(label = "voice_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.18f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = if (isListening) 0.85f else 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    ModalBottomSheet(
        onDismissRequest = {
            speechManager.stopListening()
            onDismiss()
        },
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: Titre & Choix de la langue de reconnaissance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                if (isListening) Color(0xFF4CAF50)
                                else if (isAiProcessing) Color(0xFFFF9800)
                                else MaterialTheme.colorScheme.primary
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Reconnaissance Vocale IA",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Langue active sous forme de badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.clickable {
                        // Bascule rapide entre Français et Anglais / langues
                        val nextLang = when (selectedVoiceLanguage) {
                            "Français" -> "Anglais"
                            "Anglais" -> "Haoussa"
                            "Haoussa" -> "Mooré"
                            "Mooré" -> "Dioula"
                            else -> "Français"
                        }
                        selectedVoiceLanguage = nextLang
                        if (isListening) {
                            speechManager.stopListening()
                            triggerStartListening()
                        }
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedVoiceLanguage,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Statut dynamique avec indicateur visuel
            Text(
                text = if (errorMessage != null) errorMessage!!
                else if (isListening) "🎙️ Écoute en cours… Parlez naturellement"
                else if (isAiProcessing) "✨ Analyse, ponctuation & correction en cours…"
                else statusMessage,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (errorMessage != null) MaterialTheme.colorScheme.error
                else if (isListening) Color(0xFF2E7D32)
                else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Animation Ondes Sonores (Sound Waves)
            AnimatedSoundWaveBar(
                isListening = isListening,
                soundLevel = soundLevel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Microphone Orb central avec effet halo
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(100.dp)
                    .clickable {
                        if (isListening) {
                            stopAndProcess()
                        } else {
                            triggerStartListening()
                        }
                    }
            ) {
                // Aura extérieure
                if (isListening) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF2196F3).copy(alpha = glowAlpha),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }

                // Bouton principal
                Surface(
                    modifier = Modifier
                        .size(72.dp)
                        .shadow(12.dp, CircleShape),
                    shape = CircleShape,
                    color = if (isListening) Color(0xFF1E88E5)
                    else if (isAiProcessing) Color(0xFFFF9800)
                    else MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isAiProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 3.dp,
                                color = Color.White
                            )
                        } else {
                            Icon(
                                imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicNone,
                                contentDescription = if (isListening) "Arrêter l'écoute" else "Démarrer l'écoute",
                                tint = if (isListening) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Zone de transcription en direct avec possibilité de modification
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .border(
                        1.dp,
                        if (isListening) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(18.dp)
                    ),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isListening) "Transcription en direct :" else "Phrase reconnue :",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Row {
                            if (editableText.isNotBlank()) {
                                IconButton(
                                    onClick = { isEditing = !isEditing },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isEditing) Icons.Default.Check else Icons.Default.Edit,
                                        contentDescription = "Modifier",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        editableText = ""
                                        rawLiveTranscript = ""
                                        speechManager.resetState()
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "Effacer",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (isEditing) {
                        OutlinedTextField(
                            value = editableText,
                            onValueChange = { editableText = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodyLarge,
                            shape = RoundedCornerShape(12.dp)
                        )
                    } else {
                        Text(
                            text = if (editableText.isNotBlank()) editableText
                            else if (isListening) "Écoute de votre voix..."
                            else "Appuyez sur le micro et parlez.",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 16.sp,
                                fontStyle = if (editableText.isBlank()) FontStyle.Italic else FontStyle.Normal
                            ),
                            color = if (editableText.isNotBlank()) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            // Carte d'Intention & Commande Vocale Détectée (NLU)
            AnimatedVisibility(
                visible = detectedIntent != null && detectedIntent?.action != "NONE",
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                detectedIntent?.let { intent ->
                    IntentActionCard(
                        intent = intent,
                        onExecute = {
                            executeAction(
                                context = context,
                                intent = intent,
                                viewModel = viewModel,
                                onNavigateToScreen = onNavigateToScreen,
                                onDismiss = onDismiss
                            )
                        },
                        onCancel = {
                            viewModel.closeVoiceAssistantModal()
                            onDismiss()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Barre de boutons d'action rapide (Annuler / Traduire / Valider)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bouton Annuler
                OutlinedButton(
                    onClick = {
                        speechManager.stopListening()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Annuler")
                }

                // Bouton Insérer & Traduire
                Button(
                    onClick = {
                        speechManager.stopListening()
                        if (editableText.isNotBlank()) {
                            viewModel.setSourceText(editableText)
                            viewModel.translate(
                                source = selectedVoiceLanguage,
                                target = targetLang,
                                text = editableText,
                                isVoice = true
                            )
                        }
                        onDismiss()
                    },
                    enabled = editableText.isNotBlank(),
                    modifier = Modifier.weight(1.3f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Traduire", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Carte visuelle de confirmation et d'exécution d'une commande vocale intelligente (NLU)
 */
@Composable
private fun IntentActionCard(
    intent: VoiceIntentResult,
    onExecute: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (intent.action) {
                        "SEND_MESSAGE" -> Icons.AutoMirrored.Filled.Send
                        "CALL_CONTACT" -> Icons.Default.Phone
                        "SEARCH" -> Icons.Default.Search
                        "OPEN_SCREEN" -> Icons.Default.Apps
                        else -> Icons.Default.Bolt
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (intent.action) {
                        "SEND_MESSAGE" -> "Commande : Envoyer un message"
                        "CALL_CONTACT" -> "Commande : Passer un appel"
                        "SEARCH" -> "Commande : Recherche"
                        "OPEN_SCREEN" -> "Commande : Ouvrir une section"
                        else -> "Action Recommandée"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (!intent.recipient.isNullOrBlank()) {
                Text(
                    text = "Destinataire : ${intent.recipient}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            if (!intent.messageContent.isNullOrBlank()) {
                Text(
                    text = "Message : « ${intent.messageContent} »",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            if (!intent.searchQuery.isNullOrBlank()) {
                Text(
                    text = "Requête : ${intent.searchQuery}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            if (intent.isAmbiguous && !intent.clarificationQuestion.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "ℹ️ ${intent.clarificationQuestion}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onCancel) {
                    Text("Ignorer")
                }
                Spacer(modifier = Modifier.width(6.dp))
                Button(
                    onClick = onExecute,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Exécuter l'action")
                }
            }
        }
    }
}

/**
 * Visualiseur d'ondes sonores (Sound Wave Visualizer) réagissant au niveau de volume
 */
@Composable
private fun AnimatedSoundWaveBar(
    isListening: Boolean,
    soundLevel: Float,
    modifier: Modifier = Modifier
) {
    val barCount = 11
    val infiniteTransition = rememberInfiniteTransition(label = "soundwave")

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until barCount) {
            val animOffset by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 400 + (i * 60) % 300,
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$i"
            )

            val dynamicHeight = if (isListening) {
                val factor = ((soundLevel * 1.8f) + animOffset * 0.4f).coerceIn(0.15f, 1f)
                (factor * 32).dp
            } else {
                4.dp
            }

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(dynamicHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (isListening) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
            )
        }
    }
}

private fun startListeningInternal(
    speechManager: SpeechRecognitionManager,
    language: String,
    onUpdate: (String, Float, String) -> Unit,
    onComplete: (String) -> Unit,
    onError: (String) -> Unit
) {
    speechManager.startListening(
        language = language,
        onResult = { transcript ->
            onComplete(transcript)
        },
        onError = { err ->
            onError(err)
        }
    )
}

private fun executeAction(
    context: Context,
    intent: VoiceIntentResult,
    viewModel: SamAiViewModel,
    onNavigateToScreen: ((String) -> Unit)?,
    onDismiss: () -> Unit
) {
    when (intent.action) {
        "SEND_MESSAGE" -> {
            val msg = intent.messageContent ?: intent.correctedText
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, msg)
            }
            context.startActivity(Intent.createChooser(sendIntent, "Envoyer le message à ${intent.recipient ?: "..."}"))
            viewModel.auditLog("VOICE_ACTION_SEND_MSG", "INFO", "Envoi de message via commande vocale à ${intent.recipient}")
            onDismiss()
        }
        "CALL_CONTACT" -> {
            val callIntent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:")
            }
            try {
                context.startActivity(callIntent)
                viewModel.auditLog("VOICE_ACTION_CALL", "INFO", "Lancement appel pour ${intent.recipient}")
            } catch (_: Exception) {
                Toast.makeText(context, "Application téléphone indisponible", Toast.LENGTH_SHORT).show()
            }
            onDismiss()
        }
        "SEARCH" -> {
            val query = intent.searchQuery ?: intent.correctedText
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}"))
            try {
                context.startActivity(webIntent)
                viewModel.auditLog("VOICE_ACTION_SEARCH", "INFO", "Recherche web vocale: $query")
            } catch (_: Exception) {}
            onDismiss()
        }
        "OPEN_SCREEN" -> {
            val target = intent.screenTarget ?: "settings"
            onNavigateToScreen?.invoke(target)
            onDismiss()
        }
        else -> {
            viewModel.setSourceText(intent.correctedText)
            onDismiss()
        }
    }
}
