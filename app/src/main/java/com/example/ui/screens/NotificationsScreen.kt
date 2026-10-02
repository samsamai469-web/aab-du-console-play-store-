package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.example.ui.SamAiViewModel
import com.example.util.NotificationCategory
import com.example.util.SmartNotificationLibrary
import com.example.util.SmartNotificationManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(navController: NavController, viewModel: SamAiViewModel? = null) {
    val context = LocalContext.current

    var isSystemEnabled by remember { mutableStateOf(SmartNotificationManager.areSystemNotificationsEnabled(context)) }
    var isNotificationsActive by remember { mutableStateOf(SmartNotificationManager.isNotificationsEnabled(context)) }
    var currentFrequency by remember { mutableStateOf(SmartNotificationManager.getFrequencyLevel(context)) }

    // Synchronisation système au focus
    LaunchedEffect(Unit) {
        isSystemEnabled = SmartNotificationManager.areSystemNotificationsEnabled(context)
    }

    // Les 4 catégories requises
    val prefs = remember { SmartNotificationManager.getPrefs(context) }
    var notifQuiz by remember { mutableStateOf(prefs.getBoolean("cat_quiz", true)) }
    var notifNews by remember { mutableStateOf(prefs.getBoolean("cat_news", true)) }
    var notifSamAi by remember { mutableStateOf(prefs.getBoolean("cat_tips", true)) }
    var notifTraduction by remember { mutableStateOf(prefs.getBoolean("cat_word_of_day", true)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        isSystemEnabled = isGranted
        if (isGranted) {
            isNotificationsActive = true
            SmartNotificationManager.setNotificationsEnabled(context, true)
            Toast.makeText(context, "Notifications automatiques activées !", Toast.LENGTH_SHORT).show()
        } else {
            isNotificationsActive = false
            SmartNotificationManager.setNotificationsEnabled(context, false)
            Toast.makeText(context, "Veuillez activer les notifications dans les paramètres Android.", Toast.LENGTH_LONG).show()
        }
    }

    fun handleMainToggle(enable: Boolean) {
        if (enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val status = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            if (status != PackageManager.PERMISSION_GRANTED) {
                if (SmartNotificationManager.isFirstPromptShown(context)) {
                    SmartNotificationManager.openSystemNotificationSettings(context)
                    return
                } else {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    return
                }
            }
        }
        isNotificationsActive = enable
        SmartNotificationManager.setNotificationsEnabled(context, enable)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Notifications",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.testTag("btn_back_notifications")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // --- AVERTISSEMENT PERMISSION SYSTÈME ---
            if (!isSystemEnabled) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.NotificationsOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Autorisation requise",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    "Autorisez les notifications système pour recevoir vos alertes même téléphone verrouillé.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        SmartNotificationManager.openSystemNotificationSettings(context)
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.onErrorContainer,
                                    contentColor = MaterialTheme.colorScheme.errorContainer
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Activer", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            // --- 1. INTERRUPTEUR GÉNÉRAL SIMPLE ---
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isNotificationsActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isNotificationsActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isNotificationsActive) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (isNotificationsActive) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                                    contentDescription = null,
                                    tint = if (isNotificationsActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    "Envoi automatique",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (isNotificationsActive) "Actif • Reçoit automatiquement selon vos choix" else "Désactivé • Aucune alerte envoyée",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = isNotificationsActive,
                            onCheckedChange = { handleMainToggle(it) },
                            modifier = Modifier.testTag("switch_notifications_active")
                        )
                    }
                }
            }

            // --- 2. THÉMATIQUES À COCHER ---
            item {
                Text(
                    "Choisissez vos notifications",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "Cochez ce que vous souhaitez recevoir automatiquement :",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        // Option 1 : Quizz
                        NotificationCheckItem(
                            icon = Icons.Default.Psychology,
                            iconColor = Color(0xFFE65100),
                            title = "Quiz & Apprentissage",
                            subtitle = "Défis rapides, questions du jour pour tester vos connaissances",
                            checked = notifQuiz,
                            enabled = isNotificationsActive,
                            onCheckedChange = { checked ->
                                notifQuiz = checked
                                SmartNotificationManager.setCategoryEnabled(context, "quiz", checked)
                            },
                            testTag = "check_notif_quiz"
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // Option 2 : Actualités (Spécial écran verrouillé)
                        NotificationCheckItem(
                            icon = Icons.Default.Feed,
                            iconColor = Color(0xFF2E7D32),
                            title = "Actualités du Faso & Monde",
                            subtitle = "Flash info instantané (AIB, RTB) même quand le téléphone est verrouillé",
                            badge = "En direct",
                            checked = notifNews,
                            enabled = isNotificationsActive,
                            onCheckedChange = { checked ->
                                notifNews = checked
                                SmartNotificationManager.setCategoryEnabled(context, "news", checked)
                            },
                            testTag = "check_notif_news"
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // Option 3 : Sam AI
                        NotificationCheckItem(
                            icon = Icons.Default.SmartToy,
                            iconColor = MaterialTheme.colorScheme.primary,
                            title = "Sam AI",
                            subtitle = "Conseils intelligents, astuces et fonctionnalités utiles",
                            checked = notifSamAi,
                            enabled = isNotificationsActive,
                            onCheckedChange = { checked ->
                                notifSamAi = checked
                                SmartNotificationManager.setCategoryEnabled(context, "tips", checked)
                                SmartNotificationManager.setCategoryEnabled(context, "announcements", checked)
                            },
                            testTag = "check_notif_sam_ai"
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // Option 4 : Traduction
                        NotificationCheckItem(
                            icon = Icons.Default.Translate,
                            iconColor = Color(0xFF6A1B9A),
                            title = "Traduction & Langues locales",
                            subtitle = "Mots du jour en Mooré, Dioula, expressions et proverbes",
                            checked = notifTraduction,
                            enabled = isNotificationsActive,
                            onCheckedChange = { checked ->
                                notifTraduction = checked
                                SmartNotificationManager.setCategoryEnabled(context, "word_of_day", checked)
                                SmartNotificationManager.setCategoryEnabled(context, "news_culture", checked)
                                SmartNotificationManager.setCategoryEnabled(context, "greeting", checked)
                                SmartNotificationManager.setCategoryEnabled(context, "motivation", checked)
                            },
                            testTag = "check_notif_traduction"
                        )
                    }
                }
            }

            // --- 3. CHOIX DE FRÉQUENCE PAR JOUR ---
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Combien de notifications par jour ?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "Sélectionnez le nombre de rappels automatiques souhaité :",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                val frequencies = listOf(
                    Triple("1", "1 / jour", "Une seule fois par jour (tranquille)"),
                    Triple("2", "2 / jour", "Matin et soir (équilibré)"),
                    Triple("3", "3 / jour", "Matin, midi et soir (recommandé)"),
                    Triple("5", "5 / jour", "Rythme actif tout au long de la journée")
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        frequencies.forEachIndexed { index, (key, label, desc) ->
                            val isSelected = currentFrequency == key || (key == "3" && (currentFrequency == "normal" || currentFrequency.isEmpty())) || (key == "2" && currentFrequency == "low") || (key == "5" && currentFrequency == "high")
                            Surface(
                                onClick = {
                                    if (isNotificationsActive) {
                                        currentFrequency = key
                                        SmartNotificationManager.setFrequencyLevel(context, key)
                                        Toast.makeText(context, "Fréquence réglée : $label", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent,
                                border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("freq_option_$key")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            if (isNotificationsActive) {
                                                currentFrequency = key
                                                SmartNotificationManager.setFrequencyLevel(context, key)
                                                Toast.makeText(context, "Fréquence réglée : $label", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        enabled = isNotificationsActive
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = desc,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                            if (index < frequencies.size - 1) {
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                }
            }

            // --- 4. SECTION D'ESSAI & ACTUALITÉ INSTANTANÉE ---
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "Tester l'envoi dès maintenant",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Vérifiez instantanément comment s'affiche la notification sur votre téléphone :",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Bouton Info Faso (Direct & Prioritaire)
                            Button(
                                onClick = {
                                    SmartNotificationManager.sendEventNotification(
                                        context = context,
                                        category = NotificationCategory.NEWS,
                                        title = "🇧🇫 Flash Info Faso : RTB & AIB",
                                        content = "Dernières actualités du Burkina Faso : économie, société et culture en direct.",
                                        targetUri = "sam-ai://news",
                                        forceEvenIfActive = true
                                    )
                                    Toast.makeText(context, "Alerte actualité envoyée sur l'écran !", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF2E7D32),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .testTag("btn_trigger_news_notif")
                            ) {
                                Icon(Icons.Default.Feed, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Alerte Faso", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                            }

                            // Bouton Test Général (Quiz / Traduction / Sam AI)
                            OutlinedButton(
                                onClick = {
                                    SmartNotificationManager.triggerPreviewNotification(context)
                                    Toast.makeText(context, "Notification envoyée !", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .testTag("btn_trigger_preview_notif")
                            ) {
                                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tester rappel", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            // --- 5. ENCADRÉ INFORMATIF (ÉCRAN VERROUILLÉ & SÉRÉNITÉ) ---
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Les actualités s'affichent immédiatement dès leur parution, même téléphone verrouillé. Aucun doublon ne vous sera envoyé.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationCheckItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    badge: String? = null,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (badge != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFE8F5E9)
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Checkbox(
            checked = checked,
            onCheckedChange = { onCheckedChange(it) },
            enabled = enabled,
            modifier = Modifier.testTag(testTag)
        )
    }
}
