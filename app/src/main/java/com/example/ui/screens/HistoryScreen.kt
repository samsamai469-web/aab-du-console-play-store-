package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.data.local.ConversationEntity
import com.example.data.local.MessageEntity
import com.example.data.local.TranslationEntity
import com.example.ui.SamAiViewModel
import com.example.util.AppStrings
import com.example.ui.navigation.Screen
import com.example.util.TtsManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class HistoryItem {
    abstract val timestamp: Long

    data class Translation(val entity: TranslationEntity) : HistoryItem() {
        override val timestamp: Long = entity.timestamp
    }

    data class Conversation(val entity: ConversationEntity) : HistoryItem() {
        override val timestamp: Long = entity.timestamp
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: SamAiViewModel,
    ttsManager: TtsManager,
    navController: NavController
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }

    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val conversations by viewModel.userConversations.collectAsStateWithLifecycle()
    val translations by viewModel.userTranslations.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterIndex by remember { mutableIntStateOf(0) } // 0: Tout, 1: Conversations, 2: Traductions, 3: Favoris
    var showClearAllDialog by remember { mutableStateOf(false) }
    var isGoogleLoading by remember { mutableStateOf(false) }

    // Detail modal for a conversation
    var selectedConversationForDetail by remember { mutableStateOf<ConversationEntity?>(null) }
    var conversationMessages by remember { mutableStateOf<List<MessageEntity>>(emptyList()) }
    var isLoadingMessages by remember { mutableStateOf(false) }

    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy • HH:mm", Locale.getDefault()) }

    val favTranslationsCount = remember(translations) { translations.count { it.isFavorite } }

    // Combine and sort history items
    val allHistoryItems: List<HistoryItem> = remember(conversations, translations, selectedFilterIndex, searchQuery) {
        val transItems = translations.map { HistoryItem.Translation(it) }
        val convItems = conversations.map { HistoryItem.Conversation(it) }

        val pool: List<HistoryItem> = when (selectedFilterIndex) {
            1 -> convItems
            2 -> transItems
            3 -> transItems.filter { it.entity.isFavorite }
            else -> transItems + convItems
        }

        val filtered = if (searchQuery.isBlank()) {
            pool
        } else {
            val q = searchQuery.trim().lowercase()
            pool.filter { item ->
                when (item) {
                    is HistoryItem.Translation -> {
                        item.entity.originalText.lowercase().contains(q) ||
                                item.entity.translatedText.lowercase().contains(q) ||
                                (item.entity.correctedText?.lowercase()?.contains(q) == true) ||
                                item.entity.sourceLang.lowercase().contains(q) ||
                                item.entity.targetLang.lowercase().contains(q)
                    }
                    is HistoryItem.Conversation -> {
                        item.entity.title.lowercase().contains(q) ||
                                item.entity.lastMessage.lowercase().contains(q)
                    }
                }
            }
        }

        filtered.sortedByDescending { it.timestamp }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (!navController.popBackStack()) {
                                navController.navigate(Screen.Home)
                            }
                        },
                        modifier = Modifier.testTag("btn_history_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = if (appLanguage == "en") "History & Saved" else "Historique & Favoris",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = if (appLanguage == "en") "Local on-device history" else "Historique local sur cet appareil",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    if (conversations.isNotEmpty() || translations.isNotEmpty()) {
                        IconButton(
                            onClick = { showClearAllDialog = true },
                            modifier = Modifier.testTag("btn_clear_all_history")
                        ) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = if (appLanguage == "en") "Clear all history" else "Vider tout l'historique",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .testTag("input_history_search"),
                placeholder = {
                    Text(if (appLanguage == "en") "Search in history or favorites..." else "Rechercher dans l'historique ou favoris...")
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Rechercher",
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Effacer recherche",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )

            // Filter Chips (Tout, Conversations, Traductions, Favoris ⭐)
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filters = listOf(
                    AppStrings.filterAll(translations.size + conversations.size, appLanguage),
                    AppStrings.filterConversations(conversations.size, appLanguage),
                    AppStrings.filterTranslations(translations.size, appLanguage),
                    AppStrings.filterFavorites(favTranslationsCount, appLanguage)
                )
                items(filters.size) { idx ->
                    val isSelected = selectedFilterIndex == idx
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilterIndex = idx },
                        label = {
                            Text(
                                text = filters[idx],
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
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
                        shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.testTag("filter_chip_$idx")
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Main Content List
            if (allHistoryItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.size(80.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    if (selectedFilterIndex == 3) Icons.Default.Star else Icons.Default.History,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = if (selectedFilterIndex == 3) Color(0xFFFFB300) else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) {
                                if (appLanguage == "en") "No results found" else "Aucun résultat trouvé"
                            } else if (selectedFilterIndex == 3) {
                                if (appLanguage == "en") "No favorite phrases yet" else "Aucune phrase favorite"
                            } else {
                                if (appLanguage == "en") "History is empty" else "Historique vide"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) {
                                if (appLanguage == "en") "Try another keyword or clear search." else "Essayez un autre mot-clé ou effacez la recherche."
                            } else if (selectedFilterIndex == 3) {
                                if (appLanguage == "en") "Tap the Star ⭐ icon on any translation to save it to your favorites." else "Touchez l'icône Étoile ⭐ sur une traduction pour l'ajouter à vos favoris."
                            } else {
                                if (appLanguage == "en") "Your conversations and translations will automatically appear here." else "Vos conversations et traductions sont enregistrées automatiquement ici dès que vous les effectuez."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(allHistoryItems, key = { item ->
                        when (item) {
                            is HistoryItem.Translation -> "trans_${item.entity.id}"
                            is HistoryItem.Conversation -> "conv_${item.entity.id}"
                        }
                    }) { item ->
                        when (item) {
                            is HistoryItem.Translation -> {
                                TranslationHistoryCard(
                                    item = item.entity,
                                    dateFormatter = dateFormatter,
                                    onSpeak = { text -> ttsManager.speak(text, item.entity.targetLang) },
                                    onCopy = { text ->
                                        val clip = ClipData.newPlainText("traduction", text)
                                        clipboardManager.setPrimaryClip(clip)
                                        Toast.makeText(context, "Copié dans le presse-papiers", Toast.LENGTH_SHORT).show()
                                    },
                                    onShare = { text ->
                                        val sendIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_TEXT, text)
                                            type = "text/plain"
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, "Partager la traduction"))
                                    },
                                    onOpenInTranslation = { orig, tgtLang ->
                                        navController.navigate(Screen.Translation(initialText = orig, initialTargetLang = tgtLang))
                                    },
                                    onDelete = { viewModel.deleteTranslationItem(item.entity.id) },
                                    onToggleFavorite = { viewModel.toggleTranslationFavorite(item.entity.id, !item.entity.isFavorite) }
                                )
                            }
                            is HistoryItem.Conversation -> {
                                ConversationHistoryCard(
                                    item = item.entity,
                                    dateFormatter = dateFormatter,
                                    onOpenDetail = { conv ->
                                        selectedConversationForDetail = conv
                                        isLoadingMessages = true
                                        coroutineScope.launch {
                                            conversationMessages = viewModel.getConversationMessagesList(conv.id)
                                            isLoadingMessages = false
                                        }
                                    },
                                    onCopySummary = { text ->
                                        val clip = ClipData.newPlainText("conversation", text)
                                        clipboardManager.setPrimaryClip(clip)
                                        Toast.makeText(context, "Résumé copié", Toast.LENGTH_SHORT).show()
                                    },
                                    onDelete = { viewModel.deleteConversation(item.entity.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Confirmation dialog for Clear All History
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Vider tout l'historique ?") },
            text = {
                Text("Cette action supprimera définitivement toutes vos anciennes conversations et traductions pour ce compte.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllHistory()
                        showClearAllDialog = false
                        Toast.makeText(context, "Historique vidé", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Tout effacer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Conversation Transcript Detail BottomSheet / Dialog
    selectedConversationForDetail?.let { conv ->
        ModalBottomSheet(
            onDismissRequest = { selectedConversationForDetail = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = conv.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = dateFormatter.format(Date(conv.timestamp)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { selectedConversationForDetail = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                if (isLoadingMessages) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (conversationMessages.isEmpty()) {
                    Text(
                        text = "Aucun message enregistré dans cette conversation.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        conversationMessages.forEach { msg ->
                            val isUser = msg.sender == "user"
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = if (isUser) "Vous" else "Sam Traduction",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                                        )
                                        IconButton(
                                            onClick = {
                                                val clip = ClipData.newPlainText("message", msg.text)
                                                clipboardManager.setPrimaryClip(clip)
                                                Toast.makeText(context, "Texte copié", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.ContentCopy,
                                                contentDescription = "Copier",
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = msg.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TranslationHistoryCard(
    item: TranslationEntity,
    dateFormatter: SimpleDateFormat,
    onSpeak: (String) -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onOpenInTranslation: (String, String) -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val dateStr = remember(item.timestamp) { dateFormatter.format(Date(item.timestamp)) }
    val effectiveResult = item.correctedText ?: item.translatedText

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Pair badge + Date + Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Translate,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${item.sourceLang} → ${item.targetLang}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    if (item.isValidatedCorrection) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = "✓ Validé",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Source Text
            Text(
                text = item.originalText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Translated Text
            Text(
                text = effectiveResult,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Actions Row: Speak, Copy, Share, Retranslate, Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { onSpeak(effectiveResult) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Écouter",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = { onCopy(effectiveResult) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copier",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = { onShare(effectiveResult) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Partager",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            if (item.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Favoris",
                            tint = if (item.isFavorite) Color(0xFFFFB300) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = { onOpenInTranslation(item.originalText, item.targetLang) },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Retraduire", fontSize = 12.sp)
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Supprimer",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConversationHistoryCard(
    item: ConversationEntity,
    dateFormatter: SimpleDateFormat,
    onOpenDetail: (ConversationEntity) -> Unit,
    onCopySummary: (String) -> Unit,
    onDelete: () -> Unit
) {
    val dateStr = remember(item.timestamp) { dateFormatter.format(Date(item.timestamp)) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenDetail(item) },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Chat badge + Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Chat,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Conversation Sam Traduction",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Conversation Title / Question
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Last message snippet
            if (item.lastMessage.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.lastMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onCopySummary(item.lastMessage.ifBlank { item.title }) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copier",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { onOpenDetail(item) },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Voir les échanges", fontSize = 12.sp)
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Supprimer",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
