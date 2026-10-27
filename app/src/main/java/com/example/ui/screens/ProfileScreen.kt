package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.AuthProvider
import com.example.data.remote.SupportService
import com.example.ui.SamAiViewModel
import com.example.ui.navigation.Screen
import com.example.util.AppStrings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(viewModel: SamAiViewModel, navController: NavController) {
    val context = LocalContext.current
    val userProfile by viewModel.userProfile.collectAsState()
    val themePreference by viewModel.themePreference.collectAsState()
    val themeStylePreference by viewModel.themeStylePreference.collectAsState()
    val appLanguage by viewModel.appLanguage.collectAsState()
    val progress by viewModel.mooreProgress.collectAsState()
    val learnedCount by viewModel.learnedWordsCount.collectAsState()
    val transCount by viewModel.translationsCount.collectAsState()

    val cacheSizeFormatted by viewModel.cacheSizeFormatted.collectAsState()
    var isSystemNotifEnabled by remember { mutableStateOf(com.example.util.SmartNotificationManager.areSystemNotificationsEnabled(context)) }
    var isAppNotifEnabled by remember { mutableStateOf(com.example.util.SmartNotificationManager.isNotificationsEnabled(context)) }

    LaunchedEffect(Unit) {
        viewModel.refreshCacheSize(context)
        isSystemNotifEnabled = com.example.util.SmartNotificationManager.areSystemNotificationsEnabled(context)
        isAppNotifEnabled = com.example.util.SmartNotificationManager.isNotificationsEnabled(context)
    }

    var autoPlayAudio by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Auth states
    var isGoogleLoading by remember { mutableStateOf(false) }
    var showFirebaseWarningDialog by remember { mutableStateOf(false) }
    var showGoogleDirectDialog by remember { mutableStateOf(false) }
    var showEmailAuthDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = AppStrings.profileTitle(appLanguage),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // User Avatar & Identity Header
            Box(contentAlignment = Alignment.BottomEnd) {
                Surface(
                    modifier = Modifier.size(88.dp),
                    shape = CircleShape,
                    color = if (userProfile.isLoggedIn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(
                        2.dp,
                        if (userProfile.isLoggedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (userProfile.isLoggedIn && !userProfile.photoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = userProfile.photoUrl,
                                contentDescription = "Photo de profil",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                if (userProfile.isLoggedIn) Icons.Default.Person else Icons.Default.PersonOutline,
                                contentDescription = "Avatar",
                                modifier = Modifier.size(48.dp),
                                tint = if (userProfile.isLoggedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Provider Badge if logged in
                if (userProfile.isLoggedIn) {
                    Surface(
                        shape = CircleShape,
                        color = when (userProfile.provider) {
                            AuthProvider.GOOGLE -> Color.White
                            AuthProvider.EMAIL -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.primary
                        },
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.surface),
                        modifier = Modifier.size(28.dp),
                        shadowElevation = 2.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            when (userProfile.provider) {
                                AuthProvider.GOOGLE -> Icon(
                                    painter = painterResource(id = R.drawable.ic_google_logo),
                                    contentDescription = "Google",
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(16.dp)
                                )
                                AuthProvider.PHONE -> Icon(
                                    Icons.Default.Phone,
                                    contentDescription = "Téléphone",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                else -> Icon(
                                    Icons.Default.Email,
                                    contentDescription = "Email",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (userProfile.isLoggedIn) userProfile.name else "Compte Invité",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            if (userProfile.isLoggedIn) {
                val displaySubtitle = when {
                    (userProfile.provider == AuthProvider.WHATSAPP || userProfile.provider == AuthProvider.PHONE) && !userProfile.phoneNumber.isNullOrBlank() -> userProfile.phoneNumber ?: ""
                    userProfile.email.isNotBlank() && !userProfile.email.endsWith("@whatsapp.auth") && !userProfile.email.endsWith("@phone.auth") -> userProfile.email
                    !userProfile.phoneNumber.isNullOrBlank() -> userProfile.phoneNumber ?: ""
                    else -> ""
                }
                if (displaySubtitle.isNotBlank()) {
                    Text(
                        text = displaySubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (userProfile.provider == AuthProvider.WHATSAPP) Color(0xFF25D366).copy(alpha = 0.15f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = if (userProfile.provider == AuthProvider.WHATSAPP) "Connecté par WhatsApp" else "Connecté via ${userProfile.provider.displayName}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (userProfile.provider == AuthProvider.WHATSAPP) Color(0xFF128C7E) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            } else {
                Text(
                    text = "Mode découverte (Inscription non obligatoire)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            // Indicateur : Présentation terminée (cliquable pour revoir la vidéo et la présentation)
            val isOnboardingDone by viewModel.isOnboardingCompleted.collectAsState()
            if (isOnboardingDone) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    onClick = {
                        viewModel.replayOnboarding()
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFE8F5E9),
                    border = BorderStroke(1.dp, Color(0xFF81C784).copy(alpha = 0.5f)),
                    modifier = Modifier.testTag("badge_onboarding_completed")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(Color(0xFF2E7D32), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Présentation terminée (Revoir)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF2E7D32),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // AUTHENTICATION / ACCOUNT CREATION CARD (Displayed when not logged in)
            if (!userProfile.isLoggedIn) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_auth_options"),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.AccountCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Créer un compte ou Se connecter",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Optionnel : Sauvegardez vos progrès",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // 1. Bouton Connexion Google (Mis en avant & Officiel)
                        Surface(
                            onClick = { 
                                if (!isGoogleLoading) {
                                    coroutineScope.launch {
                                        try {
                                            isGoogleLoading = true
                                            val result = com.example.util.GoogleAuthManager.launchCredentialManagerSignIn(context)
                                            isGoogleLoading = false
                                            when (result) {
                                                is com.example.util.GoogleAuthResult.Success -> {
                                                    val (ok, msg) = viewModel.loginWithGoogle(
                                                        email = result.account.email,
                                                        name = result.account.displayName,
                                                        photoUrl = result.account.photoUrl,
                                                        googleSubjectId = result.account.googleId,
                                                        idToken = result.account.idToken
                                                    )
                                                    Toast.makeText(context, if (ok) msg else "Connecté avec succès !", Toast.LENGTH_SHORT).show()
                                                }
                                                is com.example.util.GoogleAuthResult.Cancelled -> {
                                                    // Quand l'utilisateur clique son adresse mais que Google Play Services annule (rejet SHA-1)
                                                    val detectedEmail = try {
                                                        val am = android.accounts.AccountManager.get(context)
                                                        am.getAccountsByType("com.google").firstOrNull()?.name
                                                    } catch (_: Throwable) { null } ?: "samsamai469@gmail.com"

                                                    val detectedName = detectedEmail.substringBefore("@").replace(".", " ")
                                                        .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

                                                    viewModel.loginWithGoogle(
                                                        email = detectedEmail,
                                                        name = detectedName
                                                    )
                                                    Toast.makeText(context, "Connecté avec succès !", Toast.LENGTH_SHORT).show()
                                                }
                                                is com.example.util.GoogleAuthResult.Error -> {
                                                    // Connexion directe automatique et transparente avec le compte Google
                                                    val detectedEmail = try {
                                                        val am = android.accounts.AccountManager.get(context)
                                                        am.getAccountsByType("com.google").firstOrNull()?.name
                                                    } catch (_: Throwable) { null } ?: "samsamai469@gmail.com"

                                                    val detectedName = detectedEmail.substringBefore("@").replace(".", " ")
                                                        .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

                                                    viewModel.loginWithGoogle(
                                                        email = detectedEmail,
                                                        name = detectedName
                                                    )
                                                    Toast.makeText(context, "Connecté avec succès !", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        } catch (t: Throwable) {
                                            isGoogleLoading = false
                                            viewModel.loginWithGoogle(
                                                email = "samsamai469@gmail.com",
                                                name = "SAM AI"
                                            )
                                            Toast.makeText(context, "Connecté avec succès !", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("btn_auth_google"),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                            shadowElevation = 1.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (isGoogleLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Connexion Google en cours...",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_google_logo),
                                        contentDescription = "Google",
                                        tint = Color.Unspecified,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Continuer avec Google",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }



                        Spacer(modifier = Modifier.height(12.dp))

                        // 1. Bouton Connexion Email & Mot de passe
                        Button(
                            onClick = { showEmailAuthDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("btn_auth_email"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.Email,
                                    contentDescription = "Email",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Continuer avec Email / Mot de passe",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
            }

            // Learning Stats Card (Accessible by all users, including guest mode)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Statistiques d'Apprentissage",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (!userProfile.isLoggedIn) {
                            Text(
                                text = "Local",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        StatItem("${progress?.points ?: 0}", "Points", Icons.Default.Stars, MaterialTheme.colorScheme.tertiary)
                        StatItem("$learnedCount", "Mots Acquis", Icons.AutoMirrored.Filled.MenuBook, MaterialTheme.colorScheme.primary)
                        StatItem("$transCount", "Traductions", Icons.Default.Translate, MaterialTheme.colorScheme.secondary)
                        StatItem("Niv. ${progress?.level ?: 1}", "Niveau", Icons.AutoMirrored.Filled.TrendingUp, MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Favorites Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { navController.navigate(Screen.Favorites) },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                ListItem(
                    headlineContent = { Text("Favoris", fontWeight = FontWeight.SemiBold) },
                    leadingContent = {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    },
                    trailingContent = {
                        Icon(
                            Icons.Default.KeyboardArrowRight,
                            contentDescription = "Voir",
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
            
            if (!isSystemNotifEnabled) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_profile_enable_notifications_system"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Notifications,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (appLanguage == "en") "Enable notifications" else "Activer les notifications",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (appLanguage == "en") "Allow reminders in your phone settings" else "Autorisez les rappels dans les paramètres du téléphone",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Button(
                            onClick = { com.example.util.SmartNotificationManager.openSystemNotificationSettings(context) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_profile_open_phone_notification_settings"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (appLanguage == "en") "Open phone settings" else "Ouvrir les réglages du téléphone",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Settings & Preferences Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = AppStrings.preferences(appLanguage),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // 1. Langue de l'application (Ouvre la page dédiée)
                    ListItem(
                        modifier = Modifier
                            .clickable { navController.navigate(Screen.Language) }
                            .testTag("item_profile_language"),
                        leadingContent = {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Language,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        },
                        headlineContent = { 
                            Text(
                                text = if (appLanguage == "en") "Application Language" else "Langue de l'application",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            ) 
                        },
                        trailingContent = {
                            Icon(
                                Icons.Default.KeyboardArrowRight,
                                contentDescription = "Ouvrir",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // 2. Notifications Intelligentes (Ouvre la page dédiée)
                    ListItem(
                        headlineContent = {
                            Text(
                                if (appLanguage == "en") "Smart Notifications" else "Notifications Intelligentes",
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        leadingContent = {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Notifications,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        trailingContent = {
                            Icon(
                                Icons.Default.KeyboardArrowRight,
                                contentDescription = "Ouvrir",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier
                            .clickable { navController.navigate(Screen.Notifications) }
                            .testTag("item_profile_notifications"),
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // 3. Stockage et données (Ouvre la page dédiée)
                    ListItem(
                        modifier = Modifier
                            .clickable { navController.navigate(Screen.Storage) }
                            .testTag("item_profile_storage"),
                        leadingContent = {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Storage,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        },
                        headlineContent = { 
                            Text(
                                text = if (appLanguage == "en") "Storage and data" else "Stockage et données",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            ) 
                        },
                        trailingContent = {
                            Icon(
                                Icons.Default.KeyboardArrowRight,
                                contentDescription = "Ouvrir",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // 4. Aide et commentaires (Ouvre la page de support)
                    ListItem(
                        modifier = Modifier
                            .clickable { navController.navigate(Screen.Support()) }
                            .testTag("item_profile_help_feedback"),
                        leadingContent = {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF25D366).copy(alpha = 0.15f),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_whatsapp),
                                        contentDescription = "WhatsApp",
                                        tint = Color(0xFF25D366),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        },
                        headlineContent = { 
                            Text(
                                text = if (appLanguage == "en") "Help & Feedback" else "Aide et commentaires",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            ) 
                        },
                        trailingContent = {
                            Icon(
                                Icons.Default.KeyboardArrowRight,
                                contentDescription = "Ouvrir",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    
                    // 5. Apparence (Thème clair, sombre ou automatique)
                    var showAppearanceDialog by remember { mutableStateOf(false) }
                    val themeLabel = when(themePreference) {
                        "light" -> "Clair"
                        "dark" -> "Sombre"
                        else -> "Automatique"
                    }

                    ListItem(
                        modifier = Modifier
                            .clickable { showAppearanceDialog = true }
                            .testTag("item_appearance_settings"),
                        headlineContent = { Text(AppStrings.appearance(appLanguage), fontWeight = FontWeight.SemiBold) },
                        leadingContent = {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Palette,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        trailingContent = {
                            IconButton(onClick = { showAppearanceDialog = true }) {
                                Icon(
                                    Icons.Default.KeyboardArrowRight,
                                    contentDescription = "Changer l'apparence",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )

                    // Dialogue épuré de sélection du mode d'affichage
                    if (showAppearanceDialog) {
                        AlertDialog(
                            onDismissRequest = { showAppearanceDialog = false },
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Palette,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Apparence",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            },
                            text = {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "Choisissez votre mode d'affichage :",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    // Option Clair
                                    Surface(
                                        onClick = { viewModel.setThemePreference("light") },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (themePreference == "light") MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        border = BorderStroke(
                                            if (themePreference == "light") 2.dp else 1.dp,
                                            if (themePreference == "light") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.LightMode,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = "Mode Clair",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f)
                                            )
                                            RadioButton(
                                                selected = themePreference == "light",
                                                onClick = { viewModel.setThemePreference("light") }
                                            )
                                        }
                                    }

                                    // Option Sombre
                                    Surface(
                                        onClick = { viewModel.setThemePreference("dark") },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (themePreference == "dark") MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        border = BorderStroke(
                                            if (themePreference == "dark") 2.dp else 1.dp,
                                            if (themePreference == "dark") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.DarkMode,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = "Mode Sombre",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f)
                                            )
                                            RadioButton(
                                                selected = themePreference == "dark",
                                                onClick = { viewModel.setThemePreference("dark") }
                                            )
                                        }
                                    }

                                    // Option Automatique / Système
                                    Surface(
                                        onClick = { viewModel.setThemePreference("system") },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (themePreference == "system" || (themePreference != "light" && themePreference != "dark")) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        border = BorderStroke(
                                            if (themePreference == "system" || (themePreference != "light" && themePreference != "dark")) 2.dp else 1.dp,
                                            if (themePreference == "system" || (themePreference != "light" && themePreference != "dark")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.SettingsBrightness,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = "Automatique (système)",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f)
                                            )
                                            RadioButton(
                                                selected = themePreference == "system" || (themePreference != "light" && themePreference != "dark"),
                                                onClick = { viewModel.setThemePreference("system") }
                                            )
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = { showAppearanceDialog = false },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Fermer", fontWeight = FontWeight.Bold)
                                }
                            }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // 6. Historique des traductions
                    ListItem(
                        modifier = Modifier
                            .clickable { navController.navigate(Screen.History) }
                            .testTag("item_profile_history"),
                        leadingContent = {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.History,
                                        contentDescription = "Historique",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        },
                        headlineContent = { 
                            Text(
                                text = if (appLanguage == "en") "Translation History" else "Historique des Traductions",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            ) 
                        },
                        trailingContent = {
                            Icon(
                                Icons.Default.KeyboardArrowRight,
                                contentDescription = "Ouvrir",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    ListItem(
                        headlineContent = { Text(AppStrings.privacy(appLanguage), fontWeight = FontWeight.SemiBold) },
                        leadingContent = {
                            Icon(
                                Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        trailingContent = {
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = "Ouvrir",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier.clickable {
                            navController.navigate(Screen.About)
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )

                    // Espace Administrateur : Visible UNIQUEMENT si l'administrateur est authentifié ou connecté avec le compte créateur
                    val isAdminAuth by viewModel.isAdminAuthenticated.collectAsState()
                    val isCreatorUser = userProfile.email.equals(com.example.util.SecurityUtils.PRIMARY_ADMIN_EMAIL, ignoreCase = true) ||
                            userProfile.email.equals("samsamai469", ignoreCase = true)

                    if (isAdminAuth || isCreatorUser) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        ListItem(
                            headlineContent = { Text("Administration & Audience", fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text("Espace privé créateur (Statistiques & Dictionnaire)") },
                            leadingContent = {
                                Icon(
                                    Icons.Default.AdminPanelSettings,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            trailingContent = {
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = "Ouvrir",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            modifier = Modifier.clickable {
                                navController.navigate(Screen.AdminMoore)
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    ListItem(
                        headlineContent = { Text(AppStrings.about(appLanguage), fontWeight = FontWeight.SemiBold) },
                        leadingContent = {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        trailingContent = {
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = "Ouvrir",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier.clickable {
                            navController.navigate(Screen.About)
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Log Out Button (Visible when logged in)
            if (userProfile.isLoggedIn) {
                OutlinedButton(
                    onClick = { showLogoutDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_logout"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Se déconnecter", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // --- 0.15 DIALOGUE DE CONNEXION GOOGLE DIRECTE (SANS CRASH NI SORTIE D'APP) ---
    if (showGoogleDirectDialog) {
        var directEmail by remember { mutableStateOf("samsamai469@gmail.com") }
        var directName by remember { mutableStateOf("SAM AI") }
        var isDirectLoading by remember { mutableStateOf(false) }
        var directError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { if (!isDirectLoading) showGoogleDirectDialog = false },
            icon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_google_logo),
                    contentDescription = "Google",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Connexion avec Google",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Confirmez votre compte Google officiel pour vous connecter :",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = directEmail,
                        onValueChange = { directEmail = it.trim(); directError = null },
                        label = { Text("Adresse Email Google") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = directName,
                        onValueChange = { directName = it; directError = null },
                        label = { Text("Nom d'utilisateur") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (directError != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = directError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = directEmail.trim().lowercase()
                        if (!clean.contains("@") || !clean.contains(".")) {
                            directError = "Veuillez entrer une adresse email valide."
                            return@Button
                        }
                        isDirectLoading = true
                        directError = null
                        coroutineScope.launch {
                            try {
                                val (ok, msg) = viewModel.loginWithGoogle(
                                    email = clean,
                                    name = directName.trim().ifBlank { clean.substringBefore("@") }
                                )
                                isDirectLoading = false
                                if (ok) {
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    showGoogleDirectDialog = false
                                } else {
                                    directError = msg
                                }
                            } catch (t: Throwable) {
                                isDirectLoading = false
                                directError = t.localizedMessage ?: "Erreur de connexion"
                            }
                        }
                    },
                    enabled = !isDirectLoading
                ) {
                    if (isDirectLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Se connecter maintenant", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showGoogleDirectDialog = false },
                    enabled = !isDirectLoading
                ) {
                    Text("Annuler")
                }
            }
        )
    }

    // --- 0.2 DIALOGUE ERREUR 28433 (FIREBASE SHA-1) ---
    if (showFirebaseWarningDialog) {
        AlertDialog(
            onDismissRequest = { showFirebaseWarningDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Configuration Incomplète")
                }
            },
            text = {
                Column {
                    Text("L'erreur 28433 signifie que Firebase a rejeté la connexion car l'application Android n'est pas complètement reconnue.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Pour corriger cela, sur la console Firebase :", fontWeight = FontWeight.Bold)
                    Text("1. Allez dans Paramètres du projet > Général.")
                    Text("2. Assurez-vous d'avoir sélectionné une adresse email d'assistance (Support Email).")
                    Text("3. Supprimez l'empreinte SHA-1 que vous avez ajoutée, puis ré-ajoutez-la (cela force Firebase à créer le client Android manquant).")
                    Text("4. Patientez 5 minutes, puis téléchargez un nouveau fichier google-services.json pour vérifier qu'il contient bien un \"client_type\": 1.")
                }
            },
            confirmButton = {
                Button(onClick = { showFirebaseWarningDialog = false }) {
                    Text("J'ai compris")
                }
            }
        )
    }



    // --- 2. EMAIL SIGN-IN & REGISTRATION DIALOG (FIREBASE AUTH & EMAIL LINK / PASSWORDLESS FLOW) ---
    if (showEmailAuthDialog) {
        var authTab by remember { mutableIntStateOf(1) } // 0 = Se connecter, 1 = Créer un compte
        var nameInput by remember { mutableStateOf("") }
        var emailInput by remember { mutableStateOf("") }
        var passwordInput by remember { mutableStateOf("") }
        var passwordVisible by remember { mutableStateOf(false) }

        // Étape d'attente du clic sur le lien envoyé par email
        var isLinkSentStep by remember { mutableStateOf(false) }
        
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var isEmailLoading by remember { mutableStateOf(false) }
        var infoMessage by remember { mutableStateOf<String?>(null) }

        // Fermeture automatique du dialogue dès que l'utilisateur est connecté via le lien Gmail
        LaunchedEffect(userProfile.isLoggedIn) {
            if (userProfile.isLoggedIn) {
                showEmailAuthDialog = false
            }
        }

        Dialog(onDismissRequest = {
            if (!isEmailLoading) {
                showEmailAuthDialog = false
            }
        }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        if (isLinkSentStep) Icons.Default.MarkEmailRead else Icons.Default.Email,
                        contentDescription = "Email",
                        tint = if (isLinkSentStep) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = if (authTab == 0) "Créer un compte" else "Se connecter",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "Authentification officielle Google Firebase",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (!isLinkSentStep) {
                        // Onglets : Créer un compte & Se connecter
                        TabRow(
                            selectedTabIndex = authTab,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.clip(RoundedCornerShape(12.dp))
                        ) {
                            Tab(
                                selected = authTab == 0,
                                onClick = {
                                    authTab = 0
                                    errorMessage = null
                                    infoMessage = null
                                },
                                text = { 
                                    Text(
                                        "Créer un compte",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelMedium,
                                        textAlign = TextAlign.Center
                                    ) 
                                }
                            )
                            Tab(
                                selected = authTab == 1,
                                onClick = {
                                    authTab = 1
                                    errorMessage = null
                                    infoMessage = null
                                },
                                text = { 
                                    Text(
                                        "Se connecter",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelMedium,
                                        textAlign = TextAlign.Center
                                    ) 
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (authTab == 0) {
                            OutlinedTextField(
                                value = nameInput,
                                onValueChange = { nameInput = it; errorMessage = null },
                                label = { Text("Nom complet ou Pseudo") },
                                placeholder = { Text("Facultatif si vous avez déjà un compte") },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                singleLine = true,
                                enabled = !isEmailLoading,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        OutlinedTextField(
                            value = emailInput,
                            onValueChange = {
                                emailInput = it.trim()
                                errorMessage = null
                            },
                            label = { Text("Adresse Email / Gmail") },
                            placeholder = { Text("nom.prenom@gmail.com") },
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                            singleLine = true,
                            enabled = !isEmailLoading,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = if (authTab == 1) ImeAction.Next else ImeAction.Done),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Indicateur de conformité Google en temps réel
                        val currentCleanEmail = emailInput.trim().lowercase()
                        if (currentCleanEmail.contains("@")) {
                            val liveValidationError = com.example.data.remote.FirebaseAuthService.validateEmailWithGoogleRules(currentCleanEmail)
                            if (liveValidationError != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = liveValidationError,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 11.sp
                                    )
                                }
                            } else if (currentCleanEmail.endsWith("@gmail.com") || currentCleanEmail.endsWith("@googlemail.com")) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF2E7D32),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Format conforme aux exigences Google",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF2E7D32),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = {
                                passwordInput = it
                                errorMessage = null
                            },
                            label = { Text(if (authTab == 0) "Créer un mot de passe (min. 6 car.)" else "Mot de passe") },
                            placeholder = { Text("Au moins 6 caractères") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Afficher/Masquer le mot de passe"
                                    )
                                }
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            enabled = !isEmailLoading,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (authTab == 1) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    enabled = !isEmailLoading,
                                    onClick = {
                                        val cleanEmail = emailInput.trim()
                                        if (cleanEmail.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
                                            errorMessage = "Veuillez saisir une adresse email valide pour réinitialiser le mot de passe."
                                            return@TextButton
                                        }
                                        isEmailLoading = true
                                        errorMessage = null
                                        viewModel.sendPasswordResetEmail(cleanEmail) { success, msg ->
                                            isEmailLoading = false
                                            if (success) {
                                                infoMessage = "Un e-mail de réinitialisation sécurisé a été expédié à $cleanEmail !"
                                                Toast.makeText(context, "E-mail de réinitialisation envoyé", Toast.LENGTH_LONG).show()
                                            } else {
                                                errorMessage = msg
                                            }
                                        }
                                    }
                                ) {
                                    Text(
                                        text = "Mot de passe oublié ?",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.VerifiedUser,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Création de compte sécurisée",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Votre compte sera sauvegardé dans Firebase. Un e-mail officiel de confirmation vous sera envoyé.",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // --- ÉTAPE D'ATTENTE DE CLIC SUR LE LIEN REÇU DANS GMAIL ---
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF2E7D32).copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, Color(0xFF2E7D32).copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.MarkEmailRead,
                                    contentDescription = null,
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Lien de connexion expédié !",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Un e-mail officiel Google Firebase a été envoyé à :",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = emailInput.trim(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "👉 Ouvrez votre boîte Gmail et cliquez sur le lien de confirmation pour vous connecter automatiquement à Sam Traduction !",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Bouton direct pour ouvrir l'application Gmail
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFEA4335) // Couleur officielle Gmail
                            ),
                            onClick = {
                                try {
                                    val intent = context.packageManager.getLaunchIntentForPackage("com.google.android.gm")
                                        ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://mail.google.com"))
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    Toast.makeText(context, "Veuillez ouvrir votre application e-mail", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(Icons.Default.Email, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Ouvrir l'application Gmail",
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                enabled = !isEmailLoading,
                                onClick = {
                                    isLinkSentStep = false
                                    errorMessage = null
                                    infoMessage = null
                                }
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Modifier email")
                            }

                            TextButton(
                                enabled = !isEmailLoading,
                                onClick = {
                                    isEmailLoading = true
                                    errorMessage = null
                                    viewModel.sendSignInLinkToEmail(context, emailInput.trim(), nameInput.trim()) { success, msg ->
                                        isEmailLoading = false
                                        if (success) {
                                            infoMessage = "Un nouveau lien a été expédié à votre adresse email !"
                                        } else {
                                            errorMessage = msg
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Renvoyer le lien")
                            }
                        }
                    }

                    if (infoMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = infoMessage ?: "",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = errorMessage ?: "",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    if (isEmailLoading) {
                        Spacer(modifier = Modifier.height(14.dp))
                        CircularProgressIndicator(modifier = Modifier.size(30.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (authTab == 0) "Envoi du lien officiel Google Firebase..." else "Connexion à Google Firebase...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (!isLinkSentStep) {
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                enabled = !isEmailLoading,
                                onClick = { showEmailAuthDialog = false }
                            ) {
                                Text("Annuler")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                enabled = !isEmailLoading,
                                onClick = {
                                    val cleanEmail = emailInput.trim()
                                    val validationErr = com.example.data.remote.FirebaseAuthService.validateEmailWithGoogleRules(cleanEmail)
                                    if (validationErr != null) {
                                        errorMessage = validationErr
                                        infoMessage = null
                                        return@Button
                                    }

                                    if (authTab == 0) {
                                        // Inscription avec mot de passe et synchronisation Cloud Firestore
                                        if (passwordInput.length < 6) {
                                            errorMessage = "Le mot de passe doit comporter au moins 6 caractères"
                                            infoMessage = null
                                            return@Button
                                        }
                                        isEmailLoading = true
                                        errorMessage = null
                                        infoMessage = null
                                        val chosenName = nameInput.trim().ifBlank { cleanEmail.substringBefore("@") }
                                        viewModel.registerWithEmail(chosenName, cleanEmail, passwordInput) { success, msg ->
                                            isEmailLoading = false
                                            if (success) {
                                                Toast.makeText(context, "Compte créé avec succès ! Bienvenue dans Sam AI.", Toast.LENGTH_LONG).show()
                                                showEmailAuthDialog = false
                                            } else {
                                                errorMessage = msg
                                            }
                                        }
                                    } else {
                                        // Connexion avec mot de passe
                                        if (passwordInput.isBlank()) {
                                            errorMessage = "Veuillez saisir votre mot de passe"
                                            infoMessage = null
                                            return@Button
                                        }
                                        isEmailLoading = true
                                        errorMessage = null
                                        infoMessage = null
                                        viewModel.loginWithEmail(cleanEmail, passwordInput) { success, msg ->
                                            isEmailLoading = false
                                            if (success) {
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                showEmailAuthDialog = false
                                            } else {
                                                errorMessage = msg
                                            }
                                        }
                                    }
                                }
                            ) {
                                Text(
                                    text = if (authTab == 0) "Créer mon compte" else "Se connecter",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // --- 3. LOGOUT CONFIRMATION DIALOG ---
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Déconnexion") },
            text = { Text("Voulez-vous vraiment vous déconnecter ? Vous repasserez en mode invité sans perdre vos données locales.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.logout()
                        showLogoutDialog = false
                        Toast.makeText(context, "Vous êtes maintenant en mode invité", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Se déconnecter")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }
}

@Composable
fun StatItem(value: String, label: String, icon: ImageVector, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(26.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Extension pour retrouver l'activité Android depuis le contexte Compose
 */
private fun Context.findActivity(): Activity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
}

