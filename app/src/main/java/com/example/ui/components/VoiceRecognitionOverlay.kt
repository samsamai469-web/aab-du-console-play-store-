package com.example.ui.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.util.ConversationCleaner
import com.example.data.engine.VoiceIntentEngine
import com.example.ui.SamAiViewModel
import com.example.util.SpeechRecognitionManager
import kotlinx.coroutines.launch
import kotlin.math.sin

/**
 * Capsule / Barre d'enregistrement vocale flottante haute fidélité (Style Audio Waveform Capsule).
 * Reproduit fidèlement le design avec bouton [X], onde sonore animée (points & barres),
 * bouton d'arrêt [■], et bouton d'envoi bleu [↑].
 */
@Composable
fun VoiceRecordingCapsuleBar(
    soundLevel: Float,
    isListening: Boolean,
    onCancel: () -> Unit,
    onStopOrPause: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .shadow(
                elevation = 12.dp,
                shape = CircleShape,
                spotColor = Color.Black.copy(alpha = 0.25f)
            ),
        shape = CircleShape,
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Bouton Annuler / Fermer [X]
            Surface(
                onClick = onCancel,
                shape = CircleShape,
                color = Color(0xFFF1F3F5),
                modifier = Modifier
                    .size(42.dp)
                    .testTag("btn_voice_cancel")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Annuler l'enregistrement",
                        tint = Color(0xFF1E2022),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 2. Visualiseur dynamique d'onde sonore (Points + Barres qui vibrent avec la voix)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                VoiceWaveformCapsuleVisualizer(
                    soundLevel = soundLevel,
                    isListening = isListening
                )
            }

            // 3. Groupe d'actions à droite : Bouton Arrêt [■] + Bouton Envoyer [↑]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Bouton Arrêt / Pause [■]
                Surface(
                    onClick = onStopOrPause,
                    shape = CircleShape,
                    color = Color(0xFFF1F3F5),
                    modifier = Modifier
                        .size(42.dp)
                        .testTag("btn_voice_stop")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        // Carré noir Stop
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFF1E2022))
                        )
                    }
                }

                // Bouton Envoyer / Valider Bleu [↑]
                Surface(
                    onClick = onSubmit,
                    shape = CircleShape,
                    color = Color(0xFF007AFF), // Bleu iOS / Messagerie vibrant
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("btn_voice_submit")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Envoyer et traduire",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Visualiseur Waveform reproduisant exactement le dégradé de points et barres verticales réactives.
 */
@Composable
fun VoiceWaveformCapsuleVisualizer(
    soundLevel: Float,
    isListening: Boolean,
    modifier: Modifier = Modifier
) {
    val dotCount = 10
    val barCount = 14
    val infiniteTransition = rememberInfiniteTransition(label = "capsule_waveform")

    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f, // 2 * PI
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_phase"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Section gauche : Petits points discrets
        for (i in 0 until dotCount) {
            val alpha = (0.25f + (i.toFloat() / dotCount) * 0.45f).coerceIn(0.2f, 0.75f)
            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .size(3.5.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF6B7280).copy(alpha = alpha))
            )
        }

        Spacer(modifier = Modifier.width(3.dp))

        // Section droite : Barres verticales ondulantes et réactives au niveau sonore réel
        for (i in 0 until barCount) {
            val wave = if (isListening) {
                val waveOffset = (i * 0.45f) + pulsePhase
                val sinValue = (sin(waveOffset.toDouble()).toFloat() + 1f) / 2f
                // Hauteur dynamique selon l'amplitude vocale (soundLevel)
                val dynamicHeight = 6f + (soundLevel * 20f) + (sinValue * 10f)
                dynamicHeight.coerceIn(4f, 28f)
            } else {
                // Barres statiques légères au repos
                val base = 4f + ((i % 4) * 2f)
                base
            }

            val barAlpha = if (isListening) {
                (0.6f + (i.toFloat() / barCount) * 0.4f).coerceIn(0.6f, 1f)
            } else {
                0.4f
            }

            Box(
                modifier = Modifier
                    .padding(horizontal = 1.8.dp)
                    .width(3.2.dp)
                    .height(wave.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Color(0xFF1E2022).copy(alpha = barAlpha))
            )
        }
    }
}

/**
 * Superposition complète de reconnaissance vocale avec affichage en direct et capsule interactive.
 */
@Composable
fun VoiceRecognitionInteractiveOverlay(
    viewModel: SamAiViewModel,
    sourceLanguage: String,
    targetLanguage: String,
    onDismiss: () -> Unit,
    onTextReady: (String, Boolean) -> Unit // Boolean: autoTranslate
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var showPermissionDialog by remember { mutableStateOf(false) }

    fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Impossible d'ouvrir les paramètres : ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
        if (!isGranted) {
            val activity = context as? Activity
            val canShowRationale = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.RECORD_AUDIO)
            } ?: false
            if (!canShowRationale) {
                showPermissionDialog = true
            } else {
                Toast.makeText(context, "Permission microphone requise pour la saisie vocale.", Toast.LENGTH_LONG).show()
                onDismiss()
            }
        }
    }

    val speechManager = remember { SpeechRecognitionManager(context) }
    val speechState by speechManager.state.collectAsState()
    val soundLevel by speechManager.soundLevel.collectAsState()
    val partialText by speechManager.partialText.collectAsState()

    var recognizedText by remember { mutableStateOf("") }
    var isAnalyzingAi by remember { mutableStateOf(false) }

    val startListening = {
        speechManager.startListening(
            language = sourceLanguage,
            onResult = { result ->
                val cleanImmediate = ConversationCleaner.cleanVoiceTranscript(result, sourceLanguage).ifBlank { result.trim() }
                recognizedText = cleanImmediate
                if (cleanImmediate.isNotBlank()) {
                    onTextReady(cleanImmediate, true)
                    onDismiss()
                }
            },
            onError = { _ ->
                // Gestion non-bloquante
            }
        )
    }

    LaunchedEffect(hasAudioPermission) {
        if (hasAudioPermission) {
            startListening()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            speechManager.stopListening()
        }
    }

    val isListening = speechState is SpeechRecognitionManager.State.Listening
    val currentDisplayTranscript = when {
        recognizedText.isNotBlank() -> recognizedText
        partialText.isNotBlank() -> partialText
        isListening -> "Parlez en $sourceLanguage..."
        else -> "Écoute en pause"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Bulle de transcription en direct
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            shadowElevation = 6.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isListening) Color(0xFFE0F2FE) else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = if (isListening) Color(0xFF007AFF) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isListening) "Reconnaissance vocale en direct ($sourceLanguage)" else "Transcription",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isListening) Color(0xFF007AFF) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = currentDisplayTranscript,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (recognizedText.isNotBlank() || partialText.isNotBlank()) FontWeight.SemiBold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // La capsule de contrôle conforme à l'image
        VoiceRecordingCapsuleBar(
            soundLevel = soundLevel,
            isListening = isListening,
            onCancel = {
                speechManager.stopListening()
                onDismiss()
            },
            onStopOrPause = {
                if (isListening) {
                    speechManager.stopListening()
                } else {
                    startListening()
                }
            },
            onSubmit = {
                val textToSend = recognizedText.ifBlank { partialText }
                speechManager.stopListening()
                if (textToSend.isNotBlank()) {
                    onTextReady(textToSend, true)
                }
                onDismiss()
            }
        )

        if (showPermissionDialog) {
            AlertDialog(
                onDismissRequest = {
                    showPermissionDialog = false
                    onDismiss()
                },
                icon = {
                    Icon(
                        Icons.Default.MicOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text("Microphone bloqué", fontWeight = FontWeight.Bold)
                },
                text = {
                    Text(
                        "L'accès au microphone est refusé par Android. Pour utiliser la dictée vocale, veuillez autoriser le micro dans les Paramètres de l'application."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showPermissionDialog = false
                            openAppSettings()
                            onDismiss()
                        }
                    ) {
                        Text("Ouvrir les Paramètres")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showPermissionDialog = false
                            onDismiss()
                        }
                    ) {
                        Text("Annuler")
                    }
                }
            )
        }
    }
}
