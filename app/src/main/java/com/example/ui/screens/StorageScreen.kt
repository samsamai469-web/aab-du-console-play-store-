package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.ui.SamAiViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageScreen(navController: NavController, viewModel: SamAiViewModel? = null) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val appLanguage by (viewModel?.appLanguage ?: remember { mutableStateOf("fr") }).let {
        if (viewModel != null) viewModel.appLanguage.collectAsStateWithLifecycle() else remember { mutableStateOf("fr") }
    }

    var showClearCacheConfirmDialog by remember { mutableStateOf(false) }
    var showCacheClearedSuccessDialog by remember { mutableStateOf(false) }

    val cacheSizeFormatted by (viewModel?.cacheSizeFormatted ?: remember { mutableStateOf("0 Ko") }).let {
        if (viewModel != null) viewModel.cacheSizeFormatted.collectAsStateWithLifecycle() else remember { mutableStateOf("0 Ko") }
    }
    val isClearingCache by (viewModel?.isClearingCache ?: remember { mutableStateOf(false) }).let {
        if (viewModel != null) viewModel.isClearingCache.collectAsStateWithLifecycle() else remember { mutableStateOf(false) }
    }

    LaunchedEffect(Unit) {
        viewModel?.refreshCacheSize(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (appLanguage == "en") "Storage and Data" else "Stockage et données",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (appLanguage == "en") "Back" else "Retour"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // Carte Aperçu de l'espace
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_storage_overview"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Storage,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = cacheSizeFormatted,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (appLanguage == "en") "Temporary Cache Files" else "Fichiers temporaires en cache",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Explication et sécurité des données
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (appLanguage == "en") "Safe Cache Cleaning" else "Nettoyage sécurisé",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (appLanguage == "en")
                                    "Vider le cache supprime uniquement les images temporaires et données réseau en cache. Votre compte, vos messages, vos favoris et votre historique restent totalement intacts."
                                else
                                    "Vider le cache supprime uniquement les images temporaires et données réseau en cache. Votre compte, vos messages, vos favoris et votre historique restent totalement intacts.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }

            // Bouton d'action Vider le cache
            item {
                Button(
                    onClick = { showClearCacheConfirmDialog = true },
                    enabled = !isClearingCache,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("btn_page_clear_cache"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    if (isClearingCache) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (appLanguage == "en") "Clearing cache..." else "Nettoyage en cours...",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CleaningServices,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (appLanguage == "en") "Clear Cache" else "Vider le cache",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    // Dialogue de confirmation de suppression du cache
    if (showClearCacheConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = if (appLanguage == "en") "Clear Temporary Cache?" else "Vider le cache temporaire ?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (appLanguage == "en")
                        "This will free up $cacheSizeFormatted of temporary files. Your login session, chat history, and personal settings will not be lost."
                    else
                        "Cette action va libérer $cacheSizeFormatted de fichiers temporaires. Votre session de connexion, historique et réglages personnels ne seront pas perdus.",
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearCacheConfirmDialog = false
                        if (viewModel != null) {
                            viewModel.clearCache(
                                context = context,
                                onComplete = {
                                    showCacheClearedSuccessDialog = true
                                }
                            )
                        } else {
                            coroutineScope.launch {
                                com.example.util.CacheManager.clearAppCache(context)
                                showCacheClearedSuccessDialog = true
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(if (appLanguage == "en") "Confirm" else "Confirmer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheConfirmDialog = false }) {
                    Text(if (appLanguage == "en") "Cancel" else "Annuler")
                }
            }
        )
    }

    // Dialogue de confirmation de succès
    if (showCacheClearedSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showCacheClearedSuccessDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF25D366),
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = if (appLanguage == "en") "Cache Cleared!" else "Cache vidé avec succès !",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (appLanguage == "en")
                        "Temporary files have been safely cleaned. Your app is running smoothly."
                    else
                        "Les fichiers temporaires ont été supprimés avec succès. L'application est propre et rapide.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = { showCacheClearedSuccessDialog = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(if (appLanguage == "en") "OK" else "Parfait")
                }
            }
        )
    }
}
