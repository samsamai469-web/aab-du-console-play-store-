package com.example.ui.components

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.data.model.SamAiChatMessage
import com.example.ui.SamAiViewModel
import com.example.util.SpeechRecognitionManager
import com.example.util.TtsManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Modal interactif haute fidélité pour discuter directement avec SAM AI via l'API Google Gemini.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SamAiGeminiAssistantModal(
    viewModel: SamAiViewModel,
    ttsManager: TtsManager,
    onDismiss: () -> Unit,
    initialQuestion: String? = null
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val chatMessages by viewModel.samAiChatMessages.collectAsState()
    val isThinking by viewModel.isSamAiGeminiThinking.collectAsState()
    val isHistoryLoading by viewModel.isSamAiHistoryLoading.collectAsState()
    val savedHistory by viewModel.samAiSavedWeeklyHistory.collectAsState()

    var inputText by remember { mutableStateOf(initialQuestion ?: "") }
    var isListeningVoice by remember { mutableStateOf(false) }
    var showMooreVoiceDialog by remember { mutableStateOf(false) }
    var showVoiceCallDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }

    var dialogRootView by remember { mutableStateOf<android.view.View?>(null) }

    val hideKeyboard = {
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
        try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            dialogRootView?.let { v ->
                imm?.hideSoftInputFromWindow(v.windowToken, 0)
                imm?.hideSoftInputFromWindow(v.applicationWindowToken, 0)
            }
            val activity = context as? android.app.Activity
            activity?.window?.decorView?.let { v ->
                imm?.hideSoftInputFromWindow(v.windowToken, 0)
            }
        } catch (_: Exception) {}
    }

    // Speech recognizer pour la dictée vocale
    val speechManager = remember { SpeechRecognitionManager(context) }
    val speechState by speechManager.state.collectAsState()
    val partialTranscript by speechManager.partialText.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isListeningVoice = true
            speechManager.startListening(
                language = "Français",
                onResult = { transcript ->
                    if (transcript.isNotBlank()) {
                        inputText = transcript
                        isListeningVoice = false
                        hideKeyboard()
                        viewModel.askSamAiGemini(userQuestion = transcript)
                        inputText = ""
                    }
                },
                onError = { err ->
                    isListeningVoice = false
                    Toast.makeText(context, "Dictée vocale : $err", Toast.LENGTH_SHORT).show()
                }
            )
        } else {
            Toast.makeText(context, "Permission micro requise pour la dictée vocale", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(partialTranscript, isListeningVoice) {
        if (isListeningVoice && partialTranscript.isNotBlank()) {
            inputText = partialTranscript
        }
    }

    // Défilement fluide et naturel vers le nouveau message sans expulser brutalement l'ancienne conversation
    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            val targetIndex = (chatMessages.size - 1).coerceAtLeast(0)
            listState.animateScrollToItem(targetIndex)
        }
    }

    // Réinitialisation de la session de chat (message de bienvenue seul)
    // Mode éphémère : dès l'ouverture, tout historique antérieur est nettoyé
    LaunchedEffect(Unit) {
        viewModel.clearSamAiChat(deleteFromFirebase = true)
    }

    // Dès que l'utilisateur quitte l'écran de Sam AI (fermeture, retour, bascule),
    // toutes les discussions sont immédiatement et automatiquement supprimées sans rien enregistrer
    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearSamAiChat(deleteFromFirebase = true)
        }
    }

    // Si une question initiale a été passée, on la pose immédiatement
    LaunchedEffect(initialQuestion) {
        if (!initialQuestion.isNullOrBlank()) {
            viewModel.askSamAiGemini(initialQuestion)
            inputText = ""
        }
    }

    androidx.activity.compose.BackHandler(onBack = {
        viewModel.clearSamAiChat(deleteFromFirebase = true)
        onDismiss()
    })

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) {
                viewModel.clearSamAiChat(deleteFromFirebase = true)
                onDismiss()
            }
            .imePadding()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) {
                    // Empêcher la fermeture si on clique à l'intérieur
                }
                .testTag("modal_sam_ai_gemini"),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 10.dp
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // =========================================================================
                // EN-TÊTE SUPÉRIEUR : IDENTITÉ SAM AI & GEMINI
                // =========================================================================
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color(0xFF1E88E5), Color(0xFF00897B), Color(0xFFFFB300))
                                        )
                                    )
                                    .padding(2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surface),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = "SAM AI",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Text(
                                text = "SAM AI",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Bouton Appel vocal direct en continu (style conversation téléphonique)
                            Surface(
                                onClick = {
                                    hideKeyboard()
                                    showVoiceCallDialog = true
                                },
                                shape = CircleShape,
                                color = Color(0xFF009639),
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("btn_top_voice_call")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Phone,
                                        contentDescription = "Appel vocal direct",
                                        tint = Color.White,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }

                            // Bouton réinitialiser / effacer la conversation immédiatement
                            IconButton(
                                onClick = {
                                    viewModel.clearSamAiChat(deleteFromFirebase = true)
                                    Toast.makeText(context, "Discussion effacée", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Nouvelle discussion",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Bouton fermer (efface tout en quittant)
                            IconButton(
                                onClick = {
                                    viewModel.clearSamAiChat(deleteFromFirebase = true)
                                    onDismiss()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Fermer",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                val hasUserAskedQuestion = remember(chatMessages) {
                    chatMessages.any { it.sender == "user" }
                }

                // =========================================================================
                // ZONE CENTRALE (VIDE / ACCUEIL OU CONVERSATION ACTIVE) - STYLE CHATGPT
                // =========================================================================
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {
                            hideKeyboard()
                        }
                ) {
                    if (!hasUserAskedQuestion) {
                        // Espace épuré et propre en attente de la première question
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            // Vide et propre : aucune écriture superflue au centre
                        }
                    } else {
                        // Liste de discussion active
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            contentPadding = PaddingValues(top = 10.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(chatMessages, key = { it.id }) { msg ->
                                val isLatestResponse = !isThinking && msg.sender == "sam_ai" && msg.id == chatMessages.lastOrNull { it.sender == "sam_ai" }?.id
                                ChatBubbleItem(
                                    message = msg,
                                    isLatestResponse = isLatestResponse,
                                    ttsManager = ttsManager,
                                    onCopy = {
                                        val clip = ClipData.newPlainText("SAM AI", msg.text)
                                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                                        Toast.makeText(context, "Texte copié !", Toast.LENGTH_SHORT).show()
                                    },
                                    onDetailRequest = {
                                        if (!isThinking) {
                                            viewModel.askSamAiGemini("Peux-tu détailler et approfondir ta réponse précédente ?")
                                        }
                                    }
                                )
                            }

                            // Indicateur de chargement / réflexion IA
                            if (isThinking) {
                                item {
                                    ThinkingGeminiIndicator()
                                }
                            }
                        }
                    }
                }

                // =========================================================================
                // BARRE DE SAISIE MODERNE STYLE CHATGPT ANCRÉE EN BAS
                // =========================================================================
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    tonalElevation = 4.dp,
                    shape = RoundedCornerShape(28.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        // Pilules d'accès direct au dialogue et à l'appel en direct en Mooré
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                onClick = {
                                    hideKeyboard()
                                    showVoiceCallDialog = true
                                },
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFF009639).copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, Color(0xFF009639).copy(alpha = 0.5f)),
                                modifier = Modifier.testTag("btn_start_voice_call_pill")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Phone,
                                        contentDescription = null,
                                        tint = Color(0xFF009639),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "Appel en direct",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF009639)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("🇧🇫", fontSize = 11.sp)
                                }
                            }

                            Surface(
                                onClick = {
                                    hideKeyboard()
                                    showMooreVoiceDialog = true
                                },
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                                modifier = Modifier.testTag("btn_moore_voice_chat")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("🗣️", fontSize = 12.sp)
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "Dictée Mooré",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }

                        // Statut vocal actif
                        if (isListeningVoice) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (partialTranscript.isNotBlank()) partialTranscript else "Parlez, SAM AI écoute...",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            isListeningVoice = false
                                            speechManager.stopListening { transcript ->
                                                if (transcript.isNotBlank()) {
                                                    inputText = transcript
                                                    hideKeyboard()
                                                    viewModel.askSamAiGemini(transcript)
                                                    inputText = ""
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Stop,
                                            contentDescription = "Arrêter",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Bouton Dictée vocale
                            IconButton(
                                onClick = {
                                    if (isListeningVoice) {
                                        isListeningVoice = false
                                        speechManager.stopListening { transcript ->
                                            if (transcript.isNotBlank()) {
                                                inputText = transcript
                                                hideKeyboard()
                                                viewModel.askSamAiGemini(userQuestion = transcript)
                                                inputText = ""
                                            }
                                        }
                                    } else {
                                        val hasPermission = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED

                                        if (hasPermission) {
                                            isListeningVoice = true
                                            speechManager.startListening(
                                                language = "Français",
                                                onResult = { transcript ->
                                                    if (transcript.isNotBlank()) {
                                                        inputText = transcript
                                                        isListeningVoice = false
                                                        hideKeyboard()
                                                        viewModel.askSamAiGemini(userQuestion = transcript)
                                                        inputText = ""
                                                    }
                                                },
                                                onError = { err ->
                                                    isListeningVoice = false
                                                    Toast.makeText(context, "Micro : $err", Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        } else {
                                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    if (isListeningVoice) Icons.Default.MicOff else Icons.Default.Mic,
                                    contentDescription = "Dictée vocale",
                                    tint = if (isListeningVoice) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // Action d'envoi unifiée
                            val handleSendMessage: () -> Unit = {
                                if (inputText.isNotBlank() && !isThinking) {
                                    val query = inputText.trim()
                                    inputText = ""
                                    hideKeyboard()
                                    viewModel.askSamAiGemini(query)
                                }
                            }

                            // Champ de texte fluide sans bordure encombrante style ChatGPT
                            OutlinedTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                placeholder = {
                                    Text(
                                        "Message SAM AI...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("input_sam_ai_question"),
                                maxLines = 5,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 15.sp
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(
                                    onSend = { handleSendMessage() }
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    cursorColor = MaterialTheme.colorScheme.primary
                                )
                            )

                            // Bouton Envoyer circulaire moderne style ChatGPT
                            val canSend = inputText.isNotBlank() && !isThinking
                            Surface(
                                onClick = { handleSendMessage() },
                                enabled = canSend,
                                shape = CircleShape,
                                color = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("button_send_sam_ai")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Envoyer",
                                        tint = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Dialogue vocal immersif en langue Mooré
        if (showMooreVoiceDialog) {
            MooreVoiceDialog(
                onDismiss = {
                    speechManager.reset()
                    showMooreVoiceDialog = false
                },
                speechManager = speechManager,
                ttsManager = ttsManager,
                onSendQuery = { queryText ->
                    showMooreVoiceDialog = false
                    viewModel.askSamAiGemini(
                        userQuestion = queryText,
                        isMooreVoice = true,
                        onReply = { botReply ->
                            ttsManager.speak(botReply, "mooré")
                        }
                    )
                }
            )
        }

        // Mode Appel Vocal Direct et Continu (Style appel téléphonique où deux personnes causent)
        if (showVoiceCallDialog) {
            SamAiVoiceCallDialog(
                onDismiss = {
                    speechManager.reset()
                    ttsManager.stop()
                    showVoiceCallDialog = false
                },
                viewModel = viewModel,
                speechManager = speechManager,
                ttsManager = ttsManager
            )
        }
    }
}

/**
 * Bulle de message (Utilisateur ou SAM AI)
 */
@Composable
fun ChatBubbleItem(
    message: SamAiChatMessage,
    isLatestResponse: Boolean = false,
    ttsManager: TtsManager,
    onCopy: () -> Unit,
    onDetailRequest: (() -> Unit)? = null
) {
    val isUser = message.sender == "user"
    val timeStr = remember(message.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            // Avatar SAM AI
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E88E5))
                    .padding(2.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 310.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = if (isUser) 18.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 18.dp
                ),
                color = if (isUser) {
                    MaterialTheme.colorScheme.primary
                } else if (message.isError) {
                    MaterialTheme.colorScheme.errorContainer
                } else if (isLatestResponse && message.id != "welcome_msg") {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                border = if (isLatestResponse && message.id != "welcome_msg") {
                    androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF1E88E5).copy(alpha = 0.5f))
                } else null,
                shadowElevation = if (isLatestResponse && message.id != "welcome_msg") 3.dp else 1.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {

                    if (message.language == "mooré") {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f) else Color(0xFF009639).copy(alpha = 0.15f),
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🇧🇫", fontSize = 10.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isUser) "Question en Mooré" else "Réponse en Mooré (Gom-biiga)",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = if (isUser) MaterialTheme.colorScheme.onPrimary else Color(0xFF009639)
                                )
                            }
                        }
                    }

                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            lineHeight = 20.sp,
                            fontSize = 14.sp
                        ),
                        color = if (isUser) {
                            MaterialTheme.colorScheme.onPrimary
                        } else if (message.isError) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Sources en temps réel (Google Search)
                    if (!isUser && message.sources.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.TravelExplore,
                                contentDescription = null,
                                tint = Color(0xFF0D47A1),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Sources Web en temps réel :",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(message.sources) { source ->
                                Surface(
                                    onClick = {
                                        try {
                                            uriHandler.openUri(source.url)
                                        } catch (_: Exception) {}
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Language,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        val displayLabel = if (source.title.isNotBlank()) {
                                            source.title.take(30)
                                        } else {
                                            source.domain.ifBlank { "Lien web" }
                                        }
                                        Text(
                                            text = displayLabel,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                    }

                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = if (isUser) {
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        },
                        textAlign = if (isUser) TextAlign.End else TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Boutons d'action sous les réponses de SAM AI (Audio & Copie)
            if (!isUser && !message.isError) {
                Row(
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isMooreMsg = message.language == "mooré" || message.text.contains("mooré", ignoreCase = true)
                    Surface(
                        onClick = {
                            val targetLang = if (isMooreMsg) "mooré" else "Français"
                            ttsManager.speak(message.text, targetLang)
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isMooreMsg) Color(0xFF009639).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.height(28.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Écouter",
                                tint = if (isMooreMsg) Color(0xFF009639) else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isMooreMsg) "Écouter en Mooré 🇧🇫" else "Écouter",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = if (isMooreMsg) Color(0xFF009639) else MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Bouton Détailler (affiché sous les réponses SAM AI pour approfondir si souhaité)
                    if (onDetailRequest != null && message.id != "welcome_msg") {
                        Surface(
                            onClick = onDetailRequest,
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.height(28.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.ReadMore,
                                    contentDescription = "Détailler",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Détailler",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Surface(
                        onClick = onCopy,
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.height(28.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copier",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Copier",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Boîte de dialogue et explorateur de l'historique des discussions de SAM AI.
 * - Stocké dans Firebase Firestore par utilisateur
 * - Purge et réinitialisation automatique chaque semaine (7 jours)
 */
@Composable
fun SamAiWeeklyHistoryDialog(
    viewModel: SamAiViewModel,
    chatMessages: List<SamAiChatMessage>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSelectQuestion: (String) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var showClearConfirmation by remember { mutableStateOf(false) }

    // On extrait les paires (Question Utilisateur -> Réponse SAM AI)
    val userQuestions = remember(chatMessages) {
        val pairs = mutableListOf<Pair<SamAiChatMessage, SamAiChatMessage?>>()
        for (i in chatMessages.indices) {
            val msg = chatMessages[i]
            if (msg.sender == "user") {
                val nextMsg = chatMessages.getOrNull(i + 1)
                val reply = if (nextMsg?.sender == "sam_ai") nextMsg else null
                pairs.add(Pair(msg, reply))
            }
        }
        pairs.reversed() // Les plus récentes en premier
    }

    val filteredQuestions = remember(userQuestions, searchQuery) {
        if (searchQuery.isBlank()) {
            userQuestions
        } else {
            val q = searchQuery.trim().lowercase()
            userQuestions.filter { (question, answer) ->
                question.text.lowercase().contains(q) || (answer?.text?.lowercase()?.contains(q) == true)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E88E5).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = null,
                                tint = Color(0xFF1E88E5),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Historique SAM AI",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Discussions des 7 derniers jours",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bannière explicative persistance & réinitialisation hebdomadaire
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF1565C0).copy(alpha = 0.10f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E88E5).copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CloudDone,
                            contentDescription = null,
                            tint = Color(0xFF1E88E5),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Sauvegardé sur Firebase. Vos discussions sont conservées pendant 1 semaine et se réinitialisent automatiquement chaque semaine.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Champ de recherche
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Rechercher dans vos questions...", fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Effacer", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Contenu de l'historique
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Chargement depuis Firebase...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (filteredQuestions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                Icons.Default.QuestionAnswer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (searchQuery.isNotBlank()) "Aucun résultat trouvé pour « $searchQuery »" else "Aucune discussion enregistrée cette semaine",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (searchQuery.isNotBlank()) "Essayez un autre mot-clé." else "Posez vos questions à SAM AI. Vos discussions seront automatiquement sauvegardées ici pendant 7 jours !",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredQuestions, key = { it.first.id }) { (question, answer) ->
                            val dateStr = remember(question.timestamp) {
                                val cal = Calendar.getInstance().apply { timeInMillis = question.timestamp }
                                val now = Calendar.getInstance()
                                val isToday = cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                                        cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
                                val isYesterday = cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                                        cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) - 1

                                val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
                                when {
                                    isToday -> "Aujourd'hui à ${timeFormatter.format(Date(question.timestamp))}"
                                    isYesterday -> "Hier à ${timeFormatter.format(Date(question.timestamp))}"
                                    else -> SimpleDateFormat("dd/MM/yyyy 'à' HH:mm", Locale.FRENCH).format(Date(question.timestamp))
                                }
                            }

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp)
                                ) {
                                    // Question Header & Date
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = Color(0xFF1E88E5).copy(alpha = 0.15f),
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = "Q",
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF1E88E5),
                                                        fontSize = 12.sp
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = dateStr,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    // Question Text
                                    Text(
                                        text = question.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    if (answer != null) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
                                        ) {
                                            Text(
                                                text = answer.text.take(200) + if (answer.text.length > 200) "..." else "",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(10.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Action Buttons
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Bouton Copier
                                        IconButton(
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                val textToCopy = "Question: ${question.text}\n\nRéponse SAM AI: ${answer?.text ?: ""}"
                                                clipboard.setPrimaryClip(ClipData.newPlainText("Discussion SAM AI", textToCopy))
                                                Toast.makeText(context, "Discussion copiée !", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.ContentCopy,
                                                contentDescription = "Copier",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(6.dp))

                                        // Bouton Reprendre / Poser à nouveau
                                        FilledTonalButton(
                                            onClick = {
                                                onSelectQuestion(question.text)
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.AutoAwesome,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Reposer", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Actions inférieures : Effacer tout & Fermer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { showClearConfirmation = true },
                        enabled = userQuestions.isNotEmpty()
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = if (userQuestions.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Effacer l'historique",
                            color = if (userQuestions.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                            fontSize = 13.sp
                        )
                    }

                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Fermer")
                    }
                }
            }
        }
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            icon = {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Effacer tout l'historique ?",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(
                    text = "Toutes les discussions et questions enregistrées sur Firebase pour cette semaine seront définitivement supprimées.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearSamAiChat(deleteFromFirebase = true)
                        showClearConfirmation = false
                        Toast.makeText(context, "Historique supprimé de Firebase", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Supprimer", color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text("Annuler")
                }
            }
        )
    }
}

/**
 * Indicateur visuel pendant que Gemini génère la réponse
 */
@Composable
fun ThinkingGeminiIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "thinking_anim")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E88E5).copy(alpha = alphaAnim)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "SAM AI réfléchit à votre question...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Dialogue interactif dédié pour parler vocalement en Mooré avec SAM AI
 * et écouter la réponse audio immédiate de l'IA en Mooré authentique.
 */
@Composable
fun MooreVoiceDialog(
    onDismiss: () -> Unit,
    speechManager: SpeechRecognitionManager,
    ttsManager: TtsManager,
    onSendQuery: (String) -> Unit
) {
    val context = LocalContext.current
    val speechState by speechManager.state.collectAsState()
    val partialTranscript by speechManager.partialText.collectAsState()
    val soundLevel by speechManager.soundLevel.collectAsState()

    var isRecording by remember { mutableStateOf(false) }
    var spokenText by remember { mutableStateOf("") }

    val startRecording = {
        spokenText = ""
        isRecording = true
        speechManager.startListening(
            language = "Mooré",
            onResult = { result ->
                val clean = result.trim()
                isRecording = false
                if (clean.isNotBlank()) {
                    spokenText = clean
                    onSendQuery(clean)
                }
            },
            onError = { err ->
                isRecording = false
                Toast.makeText(context, "Micro : $err", Toast.LENGTH_SHORT).show()
            }
        )
    }

    val stopRecording = {
        isRecording = false
        speechManager.stopListening { finalResult ->
            val clean = finalResult.trim()
            if (clean.isNotBlank()) {
                spokenText = clean
                onSendQuery(clean)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startRecording()
        } else {
            Toast.makeText(context, "Permission micro requise pour parler en Mooré", Toast.LENGTH_SHORT).show()
        }
    }

    // Démarrage automatique de l'écoute à l'ouverture du dialogue
    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            startRecording()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Mise à jour continue du texte détecté en temps réel
    LaunchedEffect(partialTranscript) {
        if (partialTranscript.isNotBlank() && isRecording) {
            spokenText = partialTranscript
        }
    }

    // Animation du halo sonore
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Dialog(
        onDismissRequest = {
            speechManager.reset()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            border = BorderStroke(1.5.dp, Color(0xFF009639).copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Barre de titre
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🇧🇫", fontSize = 22.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "SAM AI en Mooré",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Gom-biiga la leokre ne SAM AI",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF009639)
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            speechManager.reset()
                            onDismiss()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Fermer",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Zone centrale : Halo & Visualiseur sonore du micro
                Box(
                    modifier = Modifier
                        .size(130.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isRecording) {
                        val dynamicHalo = (1f + (soundLevel * 0.8f)).coerceAtLeast(pulseScale)
                        Box(
                            modifier = Modifier
                                .size((96 * dynamicHalo).dp)
                                .clip(CircleShape)
                                .background(Color(0xFF009639).copy(alpha = 0.2f))
                        )
                        Box(
                            modifier = Modifier
                                .size((80 * dynamicHalo * 0.9f).dp)
                                .clip(CircleShape)
                                .background(Color(0xFF009639).copy(alpha = 0.35f))
                        )
                    }

                    Surface(
                        onClick = {
                            if (isRecording) {
                                stopRecording()
                            } else {
                                val hasPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasPermission) {
                                    startRecording()
                                } else {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        },
                        shape = CircleShape,
                        color = if (isRecording) Color(0xFFD32F2F) else Color(0xFF009639),
                        modifier = Modifier
                            .size(72.dp)
                            .testTag("btn_moore_mic_toggle"),
                        shadowElevation = 4.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = if (isRecording) "Arrêter" else "Parler",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Statut de l'écoute
                Text(
                    text = when {
                        isRecording -> "Écoute active... Gom-y, SAM AI wumdame !"
                        speechState is SpeechRecognitionManager.State.Processing -> "Transcription de votre voix..."
                        spokenText.isNotBlank() -> "Parole capturée ! Prêt à envoyer."
                        else -> "Appuyez sur le micro pour parler en Mooré"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isRecording) Color(0xFF009639) else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Zone de transcription affichant ce que l'utilisateur a dit
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 72.dp, max = 130.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = if (spokenText.isNotBlank()) spokenText else "« Dites par exemple : Nii yi beogo (Bonjour) → Réponse : Yibeo kibare »",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                            color = if (spokenText.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Suggestions rapides de questions en Mooré
                Text(
                    text = "Phrases usuelles en Mooré :",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))

                val suggestions = listOf(
                    "Nii yi beogo" to "Bonjour (Salutation)",
                    "Yibeo kibare" to "Bonjour, quelles nouvelles ?",
                    "Bôe la SAM AI tõe n maane ?" to "Que peux-tu faire ?",
                    "Kibare bôe n be rũndã ?" to "Quelles sont les nouvelles ?",
                    "Yel-mam kibare ne Burkina Faso" to "Parle-moi du Burkina Faso"
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(suggestions) { (moorePhrase, frenchLabel) ->
                        Surface(
                            onClick = {
                                spokenText = moorePhrase
                                speechManager.reset()
                                isRecording = false
                                onSendQuery(moorePhrase)
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                Text(
                                    text = moorePhrase,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF009639)
                                )
                                Text(
                                    text = frenchLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Boutons d'action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            speechManager.reset()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Annuler")
                    }

                    Button(
                        onClick = {
                            if (isRecording) {
                                stopRecording()
                            } else {
                                val textToSend = spokenText.trim()
                                if (textToSend.isNotBlank()) {
                                    onSendQuery(textToSend)
                                } else {
                                    Toast.makeText(context, "Veuillez parler ou choisir une phrase", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1.5f)
                            .testTag("btn_moore_send_audio"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF009639)),
                        enabled = spokenText.isNotBlank() || isRecording
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Envoyer & Écouter 🔊",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * État de l'appel vocal direct en temps réel
 */
enum class VoiceCallState {
    CONNECTING,
    SAM_SPEAKING,
    USER_SPEAKING,
    THINKING
}

/**
 * Mode Appel Vocal Direct et Continu (Style appel téléphonique où deux personnes causent).
 * L'utilisateur parle librement, SAM AI répond à voix haute en Mooré,
 * puis le micro se réouvre automatiquement en continu sans devoir cliquer à chaque fois.
 */
@Composable
fun SamAiVoiceCallDialog(
    onDismiss: () -> Unit,
    viewModel: SamAiViewModel,
    speechManager: SpeechRecognitionManager,
    ttsManager: TtsManager
) {
    val context = LocalContext.current
    var callState by remember { mutableStateOf(VoiceCallState.CONNECTING) }
    var isCallActive by remember { mutableStateOf(true) }
    var isMicMuted by remember { mutableStateOf(false) }
    var callDurationSeconds by remember { mutableIntStateOf(0) }

    var lastUserSentence by remember { mutableStateOf("") }
    var lastAiSentence by remember { mutableStateOf("") }

    val soundLevel by speechManager.soundLevel.collectAsState()
    val partialTranscript by speechManager.partialText.collectAsState()

    // Chronomètre d'appel (00:00)
    LaunchedEffect(isCallActive) {
        while (isCallActive) {
            kotlinx.coroutines.delay(1000L)
            callDurationSeconds++
        }
    }

    // Réception en temps réel des mots prononcés par l'utilisateur
    LaunchedEffect(partialTranscript) {
        if (partialTranscript.isNotBlank() && callState == VoiceCallState.USER_SPEAKING) {
            lastUserSentence = partialTranscript
        }
    }

    // Boucle récursive infinie pour que l'appel continue sans limite de tours
    fun listenTurn() {
        if (!isCallActive || isMicMuted) return
        callState = VoiceCallState.USER_SPEAKING
        speechManager.startListening(
            language = "Mooré",
            continuousMode = true,
            onResult = { recognizedText ->
                val clean = recognizedText.trim()
                if (clean.isNotBlank() && isCallActive) {
                    lastUserSentence = clean
                    callState = VoiceCallState.THINKING
                    viewModel.askSamAiGemini(
                        userQuestion = clean,
                        isMooreVoice = true,
                        onReply = { botReply ->
                            if (isCallActive) {
                                lastAiSentence = botReply
                                callState = VoiceCallState.SAM_SPEAKING
                                ttsManager.speakWithCallback(
                                    text = botReply,
                                    language = "mooré",
                                    onStart = {
                                        callState = VoiceCallState.SAM_SPEAKING
                                    },
                                    onDone = {
                                        if (isCallActive && !isMicMuted) {
                                            speechManager.reset()
                                            listenTurn()
                                        }
                                    },
                                    onError = {
                                        if (isCallActive && !isMicMuted) {
                                            speechManager.reset()
                                            listenTurn()
                                        }
                                    }
                                )
                            }
                        }
                    )
                } else if (isCallActive && !isMicMuted) {
                    // Aucune parole captée sur ce créneau : on relance l'écoute immédiatement
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        if (isCallActive && !isMicMuted && callState == VoiceCallState.USER_SPEAKING) {
                            listenTurn()
                        }
                    }, 150L)
                }
            },
            onError = { _ ->
                if (isCallActive && !isMicMuted) {
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        if (isCallActive && !isMicMuted && callState == VoiceCallState.USER_SPEAKING) {
                            listenTurn()
                        }
                    }, 180L)
                }
            }
        )
    }

    val startListeningInCall = rememberUpdatedState { listenTurn() }

    val hangUpCall = {
        isCallActive = false
        speechManager.reset()
        ttsManager.stop()
        onDismiss()
    }

    // Gestion de la permission microphone
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startListeningInCall.value()
        } else {
            Toast.makeText(context, "Permission micro requise pour l'appel", Toast.LENGTH_SHORT).show()
            hangUpCall()
        }
    }

    // Déclenchement automatique de l'écoute dès l'ouverture de l'appel pour que l'utilisateur puisse saluer ("Nii yi beogo") et que SAM réponde ("Yibeo kibare")
    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            startListeningInCall.value()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Nettoyage impératif à la destruction
    DisposableEffect(Unit) {
        onDispose {
            isCallActive = false
            speechManager.reset()
            ttsManager.stop()
        }
    }

    // Animations de la sphère vocale 3D
    val infiniteTransition = rememberInfiniteTransition(label = "callOrbPulse")
    val pulse1 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse1"
    )

    Dialog(
        onDismissRequest = { hangUpCall() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF101216) // Fond sombre immersif style appel téléphonique moderne
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // ==========================================
                // 1. EN-TÊTE DE L'APPEL
                // ==========================================
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF1E222B),
                        border = BorderStroke(1.dp, Color(0xFF009639).copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🇧🇫", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Appel en direct • Mooré (Gom-biiga)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "SAM AI",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    val minutes = callDurationSeconds / 60
                    val seconds = callDurationSeconds % 60
                    Text(
                        text = String.format("%02d:%02d", minutes, seconds),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium
                    )
                }

                // ==========================================
                // 2. SPHÈRE VOCALE ANIMÉE ET STATUT
                // ==========================================
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier.size(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when (callState) {
                            VoiceCallState.SAM_SPEAKING -> {
                                Box(
                                    modifier = Modifier
                                        .size((140 * pulse1).dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF009639).copy(alpha = 0.25f))
                                )
                                Box(
                                    modifier = Modifier
                                        .size((115 * pulse1 * 0.9f).dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFD32F2F).copy(alpha = 0.35f))
                                )
                            }
                            VoiceCallState.USER_SPEAKING -> {
                                val dynamicHalo = (1f + (soundLevel * 0.8f)).coerceAtLeast(pulse1 * 0.9f)
                                Box(
                                    modifier = Modifier
                                        .size((140 * dynamicHalo).dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF009639).copy(alpha = 0.3f))
                                )
                            }
                            VoiceCallState.THINKING -> {
                                Box(
                                    modifier = Modifier
                                        .size((130 * pulse1).dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFFB300).copy(alpha = 0.3f))
                                )
                            }
                            else -> {}
                        }

                        // Noyau central interactif de la sphère (appui pour valider la parole ou couper SAM AI)
                        Surface(
                            onClick = {
                                when (callState) {
                                    VoiceCallState.USER_SPEAKING -> {
                                        speechManager.stopListening()
                                    }
                                    VoiceCallState.SAM_SPEAKING -> {
                                        ttsManager.stop()
                                        speechManager.reset()
                                        startListeningInCall.value()
                                    }
                                    else -> {}
                                }
                            },
                            shape = CircleShape,
                            color = when (callState) {
                                VoiceCallState.SAM_SPEAKING -> Color(0xFF009639)
                                VoiceCallState.USER_SPEAKING -> Color(0xFF1E88E5)
                                VoiceCallState.THINKING -> Color(0xFFFFB300)
                                else -> Color(0xFF263238)
                            },
                            modifier = Modifier.size(92.dp),
                            shadowElevation = 8.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    when (callState) {
                                        VoiceCallState.SAM_SPEAKING -> Icons.AutoMirrored.Filled.VolumeUp
                                        VoiceCallState.USER_SPEAKING -> Icons.Default.Mic
                                        VoiceCallState.THINKING -> Icons.Default.AutoAwesome
                                        else -> Icons.Default.Phone
                                    },
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(44.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Libellé de statut conversationnel
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1E222B)
                    ) {
                        Text(
                            text = when (callState) {
                                VoiceCallState.SAM_SPEAKING -> "🔊 SAM AI vous parle en Mooré..."
                                VoiceCallState.USER_SPEAKING -> "🎙️ À vous de parler, SAM AI écoute..."
                                VoiceCallState.THINKING -> "⏳ SAM AI réfléchit..."
                                VoiceCallState.CONNECTING -> "Connexion de l'appel..."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = when (callState) {
                                VoiceCallState.SAM_SPEAKING -> Color(0xFF009639)
                                VoiceCallState.USER_SPEAKING -> Color(0xFF64B5F6)
                                VoiceCallState.THINKING -> Color(0xFFFFB300)
                                else -> Color.White
                            },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ==========================================
                    // 3. SOUS-TITRES EN DIRECT (Dialogue affiché)
                    // ==========================================
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 90.dp, max = 150.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFF181B22),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.Center
                        ) {
                            if (lastUserSentence.isNotBlank()) {
                                Text(
                                    text = "Vous : « $lastUserSentence »",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF90CAF9),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                            if (lastAiSentence.isNotBlank()) {
                                Text(
                                    text = "SAM AI : « $lastAiSentence »",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            if (lastUserSentence.isBlank() && lastAiSentence.isBlank()) {
                                Text(
                                    text = "Parlez naturellement comme dans un appel téléphonique.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.5f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                // ==========================================
                // 4. COMMANDES DE L'APPEL (En bas)
                // ==========================================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Bouton Couper/Activer le micro (Mute)
                    Surface(
                        onClick = {
                            isMicMuted = !isMicMuted
                            if (isMicMuted) {
                                speechManager.stopListening()
                            } else if (callState == VoiceCallState.USER_SPEAKING) {
                                startListeningInCall.value()
                            }
                        },
                        shape = CircleShape,
                        color = if (isMicMuted) Color(0xFFD32F2F) else Color(0xFF262C38),
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = "Micro",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    // Grand Bouton Rouge de Fin d'appel 🔴
                    Surface(
                        onClick = { hangUpCall() },
                        shape = CircleShape,
                        color = Color(0xFFE53935),
                        modifier = Modifier
                            .size(72.dp)
                            .testTag("btn_hang_up_call"),
                        shadowElevation = 8.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.CallEnd,
                                contentDescription = "Raccrocher",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    // Bouton Interrompre / Reprendre la parole
                    Surface(
                        onClick = {
                            ttsManager.stop()
                            speechManager.reset()
                            startListeningInCall.value()
                        },
                        shape = CircleShape,
                        color = Color(0xFF262C38),
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.RecordVoiceOver,
                                contentDescription = "Parler",
                                tint = Color(0xFF009639),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

