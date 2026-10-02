package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.LinguisticKnowledge
import com.example.ui.SamAiViewModel
import androidx.compose.animation.*
import com.example.ui.components.VoiceRecognitionInteractiveOverlay
import com.example.ui.components.VoiceRecordingCapsuleBar
import com.example.util.TtsManager
import com.example.util.AppStrings
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslationScreen(
    viewModel: SamAiViewModel,
    ttsManager: TtsManager,
    initialText: String? = null,
    initialTargetLang: String? = null
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    val sourceText by viewModel.sourceText.collectAsStateWithLifecycle()
    val sourceLang by viewModel.sourceLanguage.collectAsStateWithLifecycle()
    val targetLang by viewModel.targetLanguage.collectAsStateWithLifecycle()
    val translatedText by viewModel.translatedText.collectAsStateWithLifecycle()
    val lastOutput by viewModel.lastTranslationOutput.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val allKnowledge by viewModel.allKnowledge.collectAsStateWithLifecycle()
    val userTranslations by viewModel.userTranslations.collectAsStateWithLifecycle()
    val isVoiceModalOpen by viewModel.isVoiceAssistantModalOpen.collectAsStateWithLifecycle()

    val isImageTranslating by viewModel.isImageTranslating.collectAsStateWithLifecycle()
    val selectedImageUri by viewModel.selectedImageUri.collectAsStateWithLifecycle()
    val imageErrorMessage by viewModel.imageErrorMessage.collectAsStateWithLifecycle()

    var showGalleryPermissionDialog by remember { mutableStateOf(false) }

    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.translateImageFromUri(
                context = context,
                uri = uri,
                sourceLang = sourceLang,
                targetLang = targetLang
            )
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var showVoiceModal by remember { mutableStateOf(false) }
    var showLiveConversationScreen by remember { mutableStateOf(false) }
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle() // 0: Traduction, 1: Dictionnaire & Lexique

    LaunchedEffect(initialText, initialTargetLang) {
        if (initialText != null && initialText.isNotBlank()) {
            viewModel.setSourceText(initialText)
        }
        if (initialTargetLang != null && initialTargetLang.isNotBlank()) {
            viewModel.setTargetLanguage(initialTargetLang)
        }
    }

    val sourceLanguages = listOf("Français", "Haoussa", "Mooré", "Dioula", "Allemand", "Anglais", "Arabe", "Espagnol")
    val targetLanguages = listOf("Haoussa", "Mooré", "Dioula", "Anglais", "Allemand", "Français", "Arabe", "Espagnol")

    val hausaSpecialChars = listOf("ɓ", "ɗ", "ƙ", "ƴ", "Ɓ", "Ɗ", "Ƙ", "Ƴ")
    val mooreSpecialChars = listOf("ẽ", "ĩ", "õ", "ʋ", "ɛ", "ɩ", "ũ")
    val dioulaSpecialChars = listOf("ɛ", "ɔ", "ɲ", "ŋ")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (appLanguage == "en") "Multilingual Translation" else "Traduction Multilingue",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Haoussa (Niger) • Mooré • Dioula • Français • Anglais",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    Surface(
                        onClick = { showLiveConversationScreen = true },
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .testTag("btn_open_live_conversation")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Conversation en direct",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "🎙️ Conversation",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
            // Tab switch
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Traduction Directe", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Dictionnaire & Lexique", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            if (selectedTab == 0) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Language Selection Row
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Source Language Dropdown
                                LanguageDropdown(
                                    label = "Source",
                                    selectedLanguage = sourceLang,
                                    languages = sourceLanguages,
                                    onLanguageSelected = { viewModel.setSourceLanguage(it) },
                                    modifier = Modifier.weight(1f)
                                )

                                // Swap button
                                IconButton(
                                    onClick = { viewModel.swapLanguages() },
                                    modifier = Modifier
                                        .padding(horizontal = 4.dp)
                                        .size(40.dp)
                                ) {
                                    Icon(
                                        Icons.Default.SwapHoriz,
                                        contentDescription = "Inverser les langues",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                // Target Language Dropdown
                                LanguageDropdown(
                                    label = "Cible",
                                    selectedLanguage = targetLang,
                                    languages = targetLanguages,
                                    onLanguageSelected = { viewModel.setTargetLanguage(it) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Source Text Input Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Texte à traduire ($sourceLang)",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    if (sourceText.isNotEmpty()) {
                                        IconButton(
                                            onClick = { viewModel.setSourceText("") }
                                        ) {
                                            Icon(Icons.Default.Clear, contentDescription = "Effacer", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Aperçu de l'image sélectionnée depuis la galerie
                                if (selectedImageUri != null || isImageTranslating) {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                if (selectedImageUri != null) {
                                                    AsyncImage(
                                                        model = selectedImageUri,
                                                        contentDescription = "Photo sélectionnée",
                                                        modifier = Modifier
                                                            .size(46.dp)
                                                            .clip(RoundedCornerShape(8.dp)),
                                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                    )
                                                }

                                                Column {
                                                    Text(
                                                        text = if (isImageTranslating) "Analyse & Traduction de la photo..." else "Photo sélectionnée pour traduction",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                                    )
                                                    Text(
                                                        text = if (isImageTranslating) "Gemini extrait le texte et le traduit..." else "Texte extrait prêt à être modifié ou traduit",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
                                                    )
                                                }
                                            }

                                            if (isImageTranslating) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(20.dp),
                                                    strokeWidth = 2.dp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            } else {
                                                IconButton(
                                                    onClick = { viewModel.clearSelectedImage() },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Close,
                                                        contentDescription = "Retirer la photo",
                                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Message d'erreur de traitement d'image si nécessaire
                                if (imageErrorMessage != null) {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.errorContainer
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = imageErrorMessage ?: "",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(
                                                onClick = { viewModel.clearSelectedImage() },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "Fermer l'erreur",
                                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Zone de saisie principale
                                OutlinedTextField(
                                    value = sourceText,
                                    onValueChange = { viewModel.setSourceText(it) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 110.dp)
                                        .testTag("input_source_text"),
                                    placeholder = {
                                        Text(
                                            "Tapez votre phrase à traduire (ex: Bonjour, comment vas-tu ?)...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                        )
                                    },
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                        imeAction = androidx.compose.ui.text.input.ImeAction.Done
                                    ),
                                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                        onDone = {
                                            if (sourceText.isNotBlank() && !isGenerating && !isImageTranslating) {
                                                keyboardController?.hide()
                                                viewModel.translate(sourceLang, targetLang, sourceText)
                                            }
                                        }
                                    ),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color.Transparent,
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp)
                                )

                                // Quick Special Character Bar for Haoussa
                                if (sourceLang == "Haoussa") {
                                    Text(
                                        text = "Caractères Haoussa (ɓ, ɗ, ƙ, ƴ) :",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                                    )
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(hausaSpecialChars) { char ->
                                            SuggestionChip(
                                                onClick = { viewModel.setSourceText(sourceText + char) },
                                                label = { Text(char, fontWeight = FontWeight.Bold) },
                                                modifier = Modifier.height(32.dp)
                                            )
                                        }
                                    }
                                }

                                // Quick Special Character Bar for Mooré
                                if (sourceLang == "Mooré") {
                                    Text(
                                        text = "Caractères Mooré :",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                                    )
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(mooreSpecialChars) { char ->
                                            SuggestionChip(
                                                onClick = { viewModel.setSourceText(sourceText + char) },
                                                label = { Text(char, fontWeight = FontWeight.Bold) },
                                                modifier = Modifier.height(32.dp)
                                            )
                                        }
                                    }
                                }

                                // Quick Special Character Bar for Dioula
                                if (sourceLang == "Dioula") {
                                    Text(
                                        text = "Caractères Dioula :",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                                    )
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(dioulaSpecialChars) { char ->
                                            SuggestionChip(
                                                onClick = { viewModel.setSourceText(sourceText + char) },
                                                label = { Text(char, fontWeight = FontWeight.Bold) },
                                                modifier = Modifier.height(32.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Actions : Bouton Prononciation TTS (gauche) + Bouton Reconnaissance Vocale Pro & Traduire (droite)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Bouton Prononciation TTS
                                    IconButton(
                                        onClick = { ttsManager.speak(sourceText, sourceLang) },
                                        enabled = sourceText.isNotBlank()
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.VolumeUp,
                                            contentDescription = "Écouter le texte source",
                                            tint = if (sourceText.isNotBlank()) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                        )
                                    }

                                    // Groupe d'actions en bas à droite : Bouton Galerie (+) + Bouton Micro Pro + Bouton Traduire
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Bouton Plus (+) pour ouvrir la galerie photo et traduire
                                        FilledTonalIconButton(
                                            onClick = {
                                                showGalleryPermissionDialog = true
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                            ),
                                            modifier = Modifier
                                                .size(40.dp)
                                                .testTag("btn_gallery_image_picker")
                                        ) {
                                            Icon(
                                                Icons.Default.Add,
                                                contentDescription = if (appLanguage == "en") "Select photo from gallery to translate" else "Sélectionner une photo dans la galerie pour traduire",
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }

                                        // Bouton Reconnaissance Vocale Pro
                                        FilledTonalIconButton(
                                            onClick = {
                                                viewModel.openVoiceAssistantModal()
                                                showVoiceModal = true
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                contentColor = MaterialTheme.colorScheme.primary
                                            ),
                                            modifier = Modifier
                                                .size(40.dp)
                                                .testTag("btn_pro_voice_input")
                                        ) {
                                            Icon(
                                                Icons.Default.Mic,
                                                contentDescription = "Reconnaissance vocale intelligente",
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        // Bouton Traduire
                                        Button(
                                            onClick = {
                                                keyboardController?.hide()
                                                focusManager.clearFocus()
                                                viewModel.translate(sourceLang, targetLang, sourceText)
                                            },
                                            enabled = sourceText.isNotBlank() && !isGenerating && !isImageTranslating,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.height(40.dp)
                                        ) {
                                            if (isGenerating || isImageTranslating) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(18.dp),
                                                    strokeWidth = 2.dp,
                                                    color = MaterialTheme.colorScheme.onPrimary
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Traduction...")
                                            } else {
                                                Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(if (appLanguage == "en") "Translate" else "Traduire", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Result Card (Traduction Finale)
                    if (translatedText.isNotEmpty()) {
                        val badgeText = lastOutput?.sourceBadge ?: "✦ Traduction Directe"
                        val effectiveSrc = lastOutput?.sourceLang ?: sourceLang
                        val effectiveTgt = lastOutput?.targetLang ?: targetLang

                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "$effectiveSrc → $effectiveTgt",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            // Source badge
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            ) {
                                                Text(
                                                    text = badgeText,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Row {
                                            IconButton(
                                                onClick = {
                                                    val clip = ClipData.newPlainText("Traduction", translatedText)
                                                    clipboardManager.setPrimaryClip(clip)
                                                    Toast.makeText(context, "Copié dans le presse-papier !", Toast.LENGTH_SHORT).show()
                                                }
                                            ) {
                                                Icon(
                                                    Icons.Default.ContentCopy,
                                                    contentDescription = "Copier",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    val sendIntent = Intent().apply {
                                                        action = Intent.ACTION_SEND
                                                        putExtra(Intent.EXTRA_TEXT, translatedText)
                                                        type = "text/plain"
                                                    }
                                                    context.startActivity(Intent.createChooser(sendIntent, "Partager la traduction"))
                                                }
                                            ) {
                                                Icon(
                                                    Icons.Default.Share,
                                                    contentDescription = "Partager",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Text(
                                        text = translatedText,
                                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Spacer(modifier = Modifier.height(14.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            IconButton(
                                                onClick = { ttsManager.speak(translatedText, effectiveTgt) },
                                                modifier = Modifier.testTag("btn_audio_translation")
                                            ) {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.VolumeUp,
                                                    contentDescription = "Écouter la traduction",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }

                                            val isFav = userTranslations.any {
                                                it.originalText.trim().equals(sourceText.trim(), ignoreCase = true) &&
                                                it.translatedText.trim().equals(translatedText.trim(), ignoreCase = true) &&
                                                it.isFavorite
                                            }

                                            Surface(
                                                onClick = {
                                                    viewModel.toggleCurrentTranslationFavorite(sourceText, translatedText, effectiveSrc, effectiveTgt)
                                                    val feedback = if (!isFav) AppStrings.addedToFavorites(appLanguage) else AppStrings.removedFromFavorites(appLanguage)
                                                    Toast.makeText(context, feedback, Toast.LENGTH_SHORT).show()
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isFav) Color(0xFFFFB300).copy(alpha = 0.18f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                                border = androidx.compose.foundation.BorderStroke(
                                                    1.dp,
                                                    if (isFav) Color(0xFFFFB300).copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                                ),
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .testTag("btn_favorite_translation")
                                            ) {
                                                Box(
                                                    contentAlignment = Alignment.Center,
                                                    modifier = Modifier.fillMaxSize()
                                                ) {
                                                    Icon(
                                                        imageVector = if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                                                        contentDescription = if (isFav) "Favori (Actif)" else "Ajouter aux favoris",
                                                        tint = if (isFav) Color(0xFFFFB300) else MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Text(
                                            text = "Vérifié & Garanti",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // --- SUGGESTIONS DE PHRASES RAPIDES ---
                    item {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                            Text(
                                text = if (appLanguage == "en") "💡 Quick Suggestions" else "💡 Phrases rapides & Exemples",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            val quickPhrases = listOf(
                                "Bonjour, comment vas-tu ?",
                                "Merci beaucoup",
                                "Au revoir et à bientôt",
                                "Bonne journée",
                                "Combien ça coûte ?",
                                "Où est le marché ?",
                                "Je vais bien, merci",
                                "Bienvenue au Burkina Faso"
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(quickPhrases) { phrase ->
                                    SuggestionChip(
                                        onClick = {
                                            viewModel.setSourceText(phrase)
                                            viewModel.translate(sourceLang, targetLang, phrase)
                                        },
                                        label = {
                                            Text(
                                                text = phrase,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium
                                            )
                                        },
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        border = SuggestionChipDefaults.suggestionChipBorder(
                                            enabled = true,
                                            borderColor = MaterialTheme.colorScheme.outlineVariant
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // --- HISTORIQUE DES TRADUCTIONS RÉCENTES ---
                    if (userTranslations.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (appLanguage == "en") "📜 Recent Translations" else "📜 Traductions récentes",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "${userTranslations.size} élément${if (userTranslations.size > 1) "s" else ""}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        items(userTranslations.take(15)) { item ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp)),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                                onClick = {
                                    viewModel.setSourceLanguage(item.sourceLang)
                                    viewModel.setTargetLanguage(item.targetLang)
                                    viewModel.setSourceText(item.originalText)
                                    viewModel.translate(item.sourceLang, item.targetLang, item.originalText)
                                }
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        ) {
                                            Text(
                                                text = "${item.sourceLang} → ${item.targetLang}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = { ttsManager.speak(item.translatedText, item.targetLang) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.VolumeUp,
                                                    contentDescription = "Écouter",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    val clip = ClipData.newPlainText("Traduction", item.translatedText)
                                                    clipboardManager.setPrimaryClip(clip)
                                                    Toast.makeText(context, "Copié !", Toast.LENGTH_SHORT).show()
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
                                            IconButton(
                                                onClick = {
                                                    viewModel.toggleCurrentTranslationFavorite(
                                                        item.originalText,
                                                        item.translatedText,
                                                        item.sourceLang,
                                                        item.targetLang
                                                    )
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (item.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                                    contentDescription = "Favori",
                                                    tint = if (item.isFavorite) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = item.originalText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = item.translatedText,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Lexicon / Dictionary Tab
                DictionaryView(allKnowledge = allKnowledge, ttsManager = ttsManager)
            }
        }

        // Overlay interactif de reconnaissance vocale avec Capsule Waveform (Style exact demandé)
        AnimatedVisibility(
            visible = showVoiceModal,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
        ) {
            VoiceRecognitionInteractiveOverlay(
                viewModel = viewModel,
                sourceLanguage = sourceLang,
                targetLanguage = targetLang,
                onDismiss = { showVoiceModal = false },
                onTextReady = { text, autoTranslate ->
                    viewModel.setSourceText(text)
                    if (autoTranslate) {
                        viewModel.translate(sourceLang, targetLang, text)
                    }
                }
            )
        }
    }
}

    if (showLiveConversationScreen) {
        Dialog(
            onDismissRequest = { showLiveConversationScreen = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            LiveConversationScreen(
                viewModel = viewModel,
                ttsManager = ttsManager,
                onDismiss = { showLiveConversationScreen = false }
            )
        }
    }

    // Boîte de dialogue demandant explicitement l'autorisation de l'utilisateur avant d'ouvrir la galerie
    if (showGalleryPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showGalleryPermissionDialog = false },
            icon = {
                Icon(
                    Icons.Default.PhotoLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = if (appLanguage == "en") "Gallery Access Permission" else "Autorisation d'accès à la galerie",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (appLanguage == "en")
                        "Sam Translation needs your permission to open your photo gallery so you can choose an image, photo, document or sign to extract its text and translate it into $targetLang.\n\nDo you authorize opening your gallery?"
                    else
                        "Sam Traduction a besoin de votre autorisation pour ouvrir votre galerie de photos afin de sélectionner une image, une photo, une affiche ou un document pour en extraire le texte et le traduire en $targetLang.\n\nAutorisez-vous l'accès à votre galerie ?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showGalleryPermissionDialog = false
                        galleryPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("btn_confirm_gallery_permission")
                ) {
                    Text(if (appLanguage == "en") "Authorize & Open" else "Autoriser et ouvrir")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showGalleryPermissionDialog = false },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("btn_cancel_gallery_permission")
                ) {
                    Text(if (appLanguage == "en") "Cancel" else "Annuler")
                }
            }
        )
    }
}

@Composable
fun LanguageDropdown(
    label: String,
    selectedLanguage: String,
    languages: List<String>,
    onLanguageSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = selectedLanguage,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            languages.forEach { language ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = language,
                            fontWeight = if (language == selectedLanguage) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        onLanguageSelected(language)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun DictionaryView(
    allKnowledge: List<LinguisticKnowledge>,
    ttsManager: TtsManager
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Tous") }

    val categories = listOf("Tous", "Salutation", "Politesse", "Commerce", "Santé", "Action", "Quotidien", "Culture")

    val filteredKnowledge = remember(searchQuery, selectedCategory, allKnowledge) {
        allKnowledge.filter { entry ->
            val matchesQuery = searchQuery.isBlank() ||
                    entry.word.contains(searchQuery, ignoreCase = true) ||
                    entry.translation.contains(searchQuery, ignoreCase = true) ||
                    entry.variants.contains(searchQuery, ignoreCase = true)
            val matchesCategory = selectedCategory == "Tous" || entry.category.contains(selectedCategory, ignoreCase = true)
            matchesQuery && matchesCategory
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Rechercher un mot (ex: Bonjour, Laafi, Wari, Ina kwana)...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                { IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Clear, contentDescription = "Effacer") } }
            } else null,
            shape = RoundedCornerShape(16.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(categories) { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = category },
                    label = { Text(category) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "${filteredKnowledge.size} entrées trouvées",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filteredKnowledge) { item ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = item.word,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = item.language,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = item.translation,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (item.pronunciation.isNotBlank()) {
                                Text(
                                    text = "/ ${item.pronunciation} /",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(onClick = { ttsManager.speak(item.word, item.language) }) {
                            Icon(
                                Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Écouter",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}


