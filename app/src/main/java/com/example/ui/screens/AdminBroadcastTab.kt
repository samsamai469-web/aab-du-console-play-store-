package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.remote.AdminAnnouncement
import com.example.data.remote.AdminBroadcastRepository
import com.example.ui.SamAiViewModel
import com.example.util.SecurityUtils
import com.example.util.SmartNotificationManager
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun AdminBroadcastTab(
    viewModel: SamAiViewModel,
    onNavigateToProfile: () -> Unit
) {
    val context = LocalContext.current
    val announcements by viewModel.adminAnnouncements.collectAsState()
    val isPublishing by viewModel.isPublishingAnnouncement.collectAsState()
    val isLoading by viewModel.isLoadingAnnouncements.collectAsState()

    var titleInput by remember { mutableStateOf("") }
    var messageInput by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("info") }
    var selectedTargetUri by remember { mutableStateOf("sam-ai://notifications") }
    var statusFeedback by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var pendingDelete by remember { mutableStateOf<AdminAnnouncement?>(null) }

    val isFirebaseAdmin = remember(announcements, isPublishing) {
        AdminBroadcastRepository.isFirebaseAdminSignedIn()
    }
    val currentFirebaseEmail = remember(announcements, isPublishing) {
        AdminBroadcastRepository.getCurrentFirebaseEmail()
    }
    val isFcmSubscribed = remember {
        AdminBroadcastRepository.isFcmTopicSubscribed(context)
    }

    val notificationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            SmartNotificationManager.postAdminBroadcastNotification(
                context = context,
                announcementId = "test_${System.currentTimeMillis()}",
                title = titleInput.ifBlank { "Annonce Sam AI" },
                body = messageInput.ifBlank { "Ceci est un aperçu de votre message envoyé à tous les utilisateurs." },
                category = selectedCategory,
                targetUri = selectedTargetUri,
                bypassUserOptOutForTest = true
            )
            statusFeedback = true to "Notification d'aperçu affichée sur cet appareil."
        } else {
            statusFeedback = false to "Activez l'autorisation de notifications Android pour voir l'aperçu."
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshAnnouncements(context)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Statut de connexion Cloud Admin & FCM
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isFirebaseAdmin) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    }
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (isFirebaseAdmin) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isFirebaseAdmin) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isFirebaseAdmin) {
                                    "Diffusion Push Cloud prête (${SecurityUtils.PRIMARY_ADMIN_EMAIL})"
                                } else {
                                    "Session Google Créateur requise pour envoyer"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isFirebaseAdmin) {
                                    val topicTxt = if (isFcmSubscribed) "Topic FCM 'all_users' actif" else "Abonnement FCM prêt"
                                    "$topicTxt • Livraison même appli fermée + synchronisation arrière-plan"
                                } else {
                                    val currentTxt = currentFirebaseEmail?.let { "Connecté avec : $it" } ?: "Aucun compte Google connecté dans Firebase"
                                    "$currentTxt. Connectez-vous dans Profil avec ${SecurityUtils.PRIMARY_ADMIN_EMAIL}."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (!isFirebaseAdmin) {
                        OutlinedButton(
                            onClick = onNavigateToProfile,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Aller dans Profil pour connecter Google")
                        }
                    }
                }
            }
        }

        // 2. Formulaire d'envoi de message global
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Campaign,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Envoyer un message à tous les utilisateurs",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Le message s'affiche en notification système sur le téléphone de chaque utilisateur, même si Sam AI est fermé.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "Type de message",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "info" to "📢 Info",
                            "important" to "🚨 Important",
                            "update" to "🆕 Mise à jour"
                        ).forEach { (key, label) ->
                            FilterChip(
                                selected = selectedCategory == key,
                                onClick = { selectedCategory = key },
                                label = { Text(label) }
                            )
                        }
                    }

                    Text(
                        text = "Écran ouvert au clic sur la notification",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "sam-ai://notifications" to "Messages",
                            "sam-ai://home" to "Accueil",
                            "sam-ai://news" to "Actualités",
                            "sam-ai://learning" to "Cours"
                        ).forEach { (uri, label) ->
                            FilterChip(
                                selected = selectedTargetUri == uri,
                                onClick = { selectedTargetUri = uri },
                                label = { Text(label) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { if (it.length <= 80) titleInput = it },
                        label = { Text("Titre de la notification *") },
                        placeholder = { Text("Ex : Nouvelle mise à jour Sam AI disponible !") },
                        supportingText = { Text("${titleInput.length}/80 caractères") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("broadcast_title_input")
                    )

                    OutlinedTextField(
                        value = messageInput,
                        onValueChange = { if (it.length <= 500) messageInput = it },
                        label = { Text("Contenu du message *") },
                        placeholder = { Text("Écrivez le message que tous les utilisateurs recevront...") },
                        supportingText = { Text("${messageInput.length}/500 caractères") },
                        minLines = 3,
                        maxLines = 6,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("broadcast_message_input")
                    )

                    if (statusFeedback != null) {
                        val (isSuccess, msg) = statusFeedback!!
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSuccess) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.errorContainer
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSuccess) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { statusFeedback = null },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Fermer le message")
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    SmartNotificationManager.postAdminBroadcastNotification(
                                        context = context,
                                        announcementId = "test_${System.currentTimeMillis()}",
                                        title = titleInput.ifBlank { "Annonce Sam AI" },
                                        body = messageInput.ifBlank { "Aperçu de votre notification push pour tous les utilisateurs." },
                                        category = selectedCategory,
                                        targetUri = selectedTargetUri,
                                        bypassUserOptOutForTest = true
                                    )
                                    statusFeedback = true to "Notification d'aperçu affichée sur votre appareil."
                                }
                            },
                            enabled = !isPublishing,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(0.9f)
                                .height(50.dp)
                                .testTag("btn_test_admin_broadcast")
                        ) {
                            Icon(
                                Icons.Default.NotificationsActive,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Aperçu", maxLines = 1)
                        }

                        Button(
                            onClick = {
                                statusFeedback = null
                                viewModel.publishAdminAnnouncement(
                                    context = context,
                                    title = titleInput,
                                    message = messageInput,
                                    category = selectedCategory,
                                    targetUri = selectedTargetUri
                                ) { ok, msg ->
                                    statusFeedback = ok to msg
                                    if (ok) {
                                        titleInput = ""
                                        messageInput = ""
                                    }
                                }
                            },
                            enabled = !isPublishing && titleInput.trim().length >= 3 && messageInput.trim().length >= 5,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1.4f)
                                .height(50.dp)
                                .testTag("btn_send_admin_broadcast")
                        ) {
                            if (isPublishing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Envoi...")
                            } else {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Envoyer à tous", maxLines = 1)
                            }
                        }
                    }
                }
            }
        }

        // 3. Historique des messages diffusés
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Messages envoyés (${announcements.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                TextButton(
                    onClick = { viewModel.refreshAnnouncements(context) },
                    enabled = !isLoading
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Actualiser",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Actualiser")
                }
            }
        }

        if (isLoading && announcements.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (announcements.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Aucun message envoyé pour le moment. Rédigez une annonce ci-dessus pour notifier tous les utilisateurs.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(announcements, key = { it.id }) { ann ->
                val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)
                val dateStr = ann.createdAt?.toDate()?.let { sdf.format(it) } ?: "À l'instant"
                val badge = when (ann.category.lowercase()) {
                    "important", "urgent" -> "🚨 Important"
                    "update", "mise_a_jour" -> "🆕 Mise à jour"
                    else -> "📢 Info"
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = badge,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = dateStr,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                IconButton(
                                    onClick = { pendingDelete = ann },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Supprimer le message",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = ann.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = ann.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                        Text(
                            text = "Cible : ${ann.targetUri}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }

    val targetDelete = pendingDelete
    if (targetDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Supprimer ce message ?") },
            text = { Text("Le message « ${targetDelete.title} » sera retiré de l'historique Cloud des utilisateurs.") },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDelete = null
                        viewModel.deleteAdminAnnouncement(context, targetDelete.id) { ok, msg ->
                            statusFeedback = ok to msg
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Supprimer")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Annuler")
                }
            }
        )
    }
}
