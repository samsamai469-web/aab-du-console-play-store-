package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.ui.SamAiViewModel
import com.example.ui.navigation.Screen
import com.example.util.TtsManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    viewModel: SamAiViewModel,
    ttsManager: TtsManager,
    navController: NavController
) {
    val context = LocalContext.current
    val clipboardManager = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    val translations by viewModel.userTranslations.collectAsStateWithLifecycle()
    val favorites = translations.filter { it.isFavorite }.sortedByDescending { it.timestamp }
    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy • HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Favoris", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        if (favorites.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Aucun favori enregistré.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(favorites, key = { it.id }) { item ->
                    TranslationHistoryCard(
                        item = item,
                        dateFormatter = dateFormatter,
                        onSpeak = { text -> ttsManager.speak(text, item.targetLang) },
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
                        onDelete = { viewModel.deleteTranslationItem(item.id) },
                        onToggleFavorite = { viewModel.toggleTranslationFavorite(item.id, !item.isFavorite) }
                    )
                }
            }
        }
    }
}
