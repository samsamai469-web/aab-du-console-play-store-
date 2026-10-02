package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.ui.SamAiViewModel
import com.example.ui.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(viewModel: SamAiViewModel, navController: NavController) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val appLanguage by viewModel.appLanguage.collectAsState()

    // État d'expansion global pour la section Politique et Confidentialité
    var isPrivacyExpanded by remember { mutableStateOf(true) }

    // États d'expansion individuels pour les 16 sections de la politique
    var expandedSec1 by remember { mutableStateOf(false) }
    var expandedSec2 by remember { mutableStateOf(false) }
    var expandedSec3 by remember { mutableStateOf(false) }
    var expandedSec4 by remember { mutableStateOf(false) }
    var expandedSec5 by remember { mutableStateOf(false) }
    var expandedSec6 by remember { mutableStateOf(false) }
    var expandedSec7 by remember { mutableStateOf(false) }
    var expandedSec8 by remember { mutableStateOf(false) }
    var expandedSec9 by remember { mutableStateOf(false) }
    var expandedSec10 by remember { mutableStateOf(false) }
    var expandedSec11 by remember { mutableStateOf(false) }
    var expandedSec12 by remember { mutableStateOf(false) }
    var expandedSec13 by remember { mutableStateOf(false) }
    var expandedSec14 by remember { mutableStateOf(false) }
    var expandedSec15 by remember { mutableStateOf(false) }
    var expandedSec16 by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (appLanguage == "en") "About & Privacy Policy" else "À propos & Confidentialité",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = if (appLanguage == "en") "Creator, Donations & Data Protection" else "Créateur, Dons & Protection des données",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (!navController.popBackStack()) {
                                navController.navigate(Screen.Home) {
                                    popUpTo(Screen.Home) { inclusive = false }
                                    launchSingleTop = true
                                }
                            }
                        },
                        modifier = Modifier.testTag("btn_back_about")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            navController.navigate(Screen.Home) {
                                popUpTo(Screen.Home) { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.Home,
                            contentDescription = "Accueil",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // =========================================================================
            // 1. CRÉATEUR & PHOTO OFFICIELLE EN HAUT (Abdoulmohamine Sawadogo)
            // =========================================================================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("card_creator_top"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Badge Créateur Officiel
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Verified,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Créateur Officiel",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Photo de Abdoulmohamine Sawadogo en haute définition
                    Surface(
                        modifier = Modifier.size(140.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shadowElevation = 8.dp,
                        border = BorderStroke(3.5.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        SubcomposeAsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(R.drawable.img_20260819_wa0102)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Photo de Abdoulmohamine Sawadogo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.Center,
                            loading = {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(28.dp),
                                        strokeWidth = 2.5.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            error = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "AS",
                                        style = MaterialTheme.typography.headlineLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Nom & Titre
                    Text(
                        text = "Abdoulmohamine Sawadogo",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Créateur & Concepteur de l'application Sam Traduction",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Sam Traduction est développée avec passion pour faciliter la communication quotidienne, préserver et valoriser les langues du Burkina Faso (Mooré, Dioula) et les langues internationales.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 21.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Boutons de contact directs
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Téléphone / WhatsApp
                        Surface(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:+22666387816"))
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    clipboardManager.setText(AnnotatedString("+22666387816"))
                                    Toast.makeText(context, "Numéro copié : +226 66 38 78 16", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.Phone,
                                    contentDescription = "Téléphone",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "+226 66 38 78 16",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        // Email
                        Surface(
                            onClick = {
                                try {
                                    val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                                        data = Uri.parse("mailto:samsamai469@gmail.com")
                                        putExtra(Intent.EXTRA_SUBJECT, "Contact via Sam Traduction")
                                    }
                                    context.startActivity(Intent.createChooser(emailIntent, "Envoyer un e-mail"))
                                } catch (_: Exception) {
                                    clipboardManager.setText(AnnotatedString("samsamai469@gmail.com"))
                                    Toast.makeText(context, "Email copié : samsamai469@gmail.com", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.Email,
                                    contentDescription = "Email",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "samsamai469@...",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // 2. SOUTENIR SAM TRADUCTION EN HAUT (DONATIONS ORANGE MONEY & WAVE)
            // =========================================================================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("card_donation_top"),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                ),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Favorite,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = if (appLanguage == "en") "❤️ Support SAM AI" else "❤️ Soutenir SAM AI (Faire un Don)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (appLanguage == "en") "Contribution & development" else "Contribution au développement & aux langues",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (appLanguage == "en")
                            "If you like this application and find it useful, you can support its development and linguistic preservation with a contribution."
                        else
                            "Si cette application vous plaît et vous est utile, vous pouvez soutenir son développement et la valorisation des langues en faisant un don.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = if (appLanguage == "en") "Contribution methods (Click to copy):" else "Moyens de contribution (Cliquez pour copier) :",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Orange Money
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                clipboardManager.setText(AnnotatedString("66387816"))
                                Toast.makeText(context, "Numéro Orange Money copié : 66 38 78 16", Toast.LENGTH_SHORT).show()
                            }
                            .testTag("btn_copy_orange_money")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFFFF7900),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "OM",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 12.sp,
                                            color = Color.White
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Orange Money",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "66 38 78 16",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            FilledTonalIconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString("66387816"))
                                    Toast.makeText(context, "Numéro Orange Money copié : 66 38 78 16", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Copier le numéro Orange Money",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Wave
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                clipboardManager.setText(AnnotatedString("66387816"))
                                Toast.makeText(context, "Numéro Wave copié : 66 38 78 16", Toast.LENGTH_SHORT).show()
                            }
                            .testTag("btn_copy_wave")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF1EA5FC),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "W",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 14.sp,
                                            color = Color.White
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Wave",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "66 38 78 16",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            FilledTonalIconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString("66387816"))
                                    Toast.makeText(context, "Numéro Wave copié : 66 38 78 16", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Copier le numéro Wave",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (appLanguage == "en")
                                "Every contribution, big or small, helps us improve SAM AI. Thank you for your support! 🙏"
                            else
                                "Chaque contribution, petite ou grande, nous aide à enrichir et perfectionner SAM AI. Merci pour votre générosité ! 🙏",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            // =========================================================================
            // 3. SECTION MAJEURE : POLITIQUE ET CONFIDENTIALITÉ (ULTRA-OPTIMISÉE & COMPLÈTE)
            // =========================================================================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("card_privacy_master_section"),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    // Header de la section cliquable pour déplier/replier
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isPrivacyExpanded = !isPrivacyExpanded },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Security,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Politique & Confidentialité",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Protection stricte, sécurité & zéro compromis",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        IconButton(onClick = { isPrivacyExpanded = !isPrivacyExpanded }) {
                            Icon(
                                if (isPrivacyExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isPrivacyExpanded) "Replier" else "Déplier",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = isPrivacyExpanded,
                        enter = fadeIn(tween(250)) + expandVertically(tween(250)),
                        exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
                    ) {
                        Column(
                            modifier = Modifier.padding(top = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Cette politique détaille exhaustivement l'ensemble des mesures de confidentialité, de sécurité cryptographique et de protection des droits mises en œuvre par Sam Traduction.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 20.sp
                            )

                            // Bannière de garantie fondamentale
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
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
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Vos données personnelles et vos traductions restent strictement sous votre contrôle absolu.",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)

                            // 1. Données collectées
                            PolicyCollapsibleCard(
                                title = "1. Données collectées",
                                icon = Icons.Default.AccountCircle,
                                isExpanded = expandedSec1,
                                onToggle = { expandedSec1 = !expandedSec1 }
                            ) {
                                Text(
                                    text = "Nom, e-mail, photo de profil (si Google), historique des traductions et préférences de langue.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 20.sp
                                )
                            }

                            // 2. Protection des données
                            PolicyCollapsibleCard(
                                title = "2. Protection des données",
                                icon = Icons.Default.Lock,
                                isExpanded = expandedSec2,
                                onToggle = { expandedSec2 = !expandedSec2 }
                            ) {
                                Text(
                                    text = "Toutes les données sont chiffrées en transit (HTTPS/TLS) et les comptes sont isolés : un utilisateur ne peut jamais voir les données d'un autre.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 20.sp
                                )
                            }

                            // 3. Confidentialité des conversations IA
                            PolicyCollapsibleCard(
                                title = "3. Confidentialité des conversations IA",
                                icon = Icons.AutoMirrored.Filled.Chat,
                                isExpanded = expandedSec3,
                                onToggle = { expandedSec3 = !expandedSec3 }
                            ) {
                                Text(
                                    text = "Les questions envoyées à l'IA servent uniquement à produire la réponse et ne sont jamais vendues à des annonceurs.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 20.sp
                                )
                            }

                            // 4. Stockage local & Cloud
                            PolicyCollapsibleCard(
                                title = "4. Stockage local & Cloud",
                                icon = Icons.Default.Storage,
                                isExpanded = expandedSec4,
                                onToggle = { expandedSec4 = !expandedSec4 }
                            ) {
                                Text(
                                    text = "Les traductions récentes peuvent être conservées sur l'appareil (SQLite) et les données du compte sont synchronisées de manière sécurisée.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 20.sp
                                )
                            }

                            // 5. Aucune vente de données
                            PolicyCollapsibleCard(
                                title = "5. Aucune vente de données",
                                icon = Icons.Default.Block,
                                isExpanded = expandedSec5,
                                onToggle = { expandedSec5 = !expandedSec5 }
                            ) {
                                Text(
                                    text = "Sam Traduction ne vend ni ne loue les informations personnelles des utilisateurs.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 20.sp
                                )
                            }

                            // 6. Vos droits
                            PolicyCollapsibleCard(
                                title = "6. Vos droits",
                                icon = Icons.Default.AdminPanelSettings,
                                isExpanded = expandedSec6,
                                onToggle = { expandedSec6 = !expandedSec6 }
                            ) {
                                Text(
                                    text = "L'utilisateur peut consulter, supprimer son historique et demander la suppression définitive de son compte.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 20.sp
                                )
                            }

                            // 7. Notifications
                            PolicyCollapsibleCard(
                                title = "7. Notifications",
                                icon = Icons.Default.Notifications,
                                isExpanded = expandedSec7,
                                onToggle = { expandedSec7 = !expandedSec7 }
                            ) {
                                Text(
                                    text = "Les notifications sont facultatives et peuvent être désactivées à tout moment dans les paramètres.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 20.sp
                                )
                            }

                            // 8. Conditions d'utilisation
                            PolicyCollapsibleCard(
                                title = "8. Conditions d'utilisation",
                                icon = Icons.Default.Gavel,
                                isExpanded = expandedSec8,
                                onToggle = { expandedSec8 = !expandedSec8 }
                            ) {
                                Text(
                                    text = "Interdiction d'utiliser l'application pour des activités illégales, du harcèlement ou la diffusion de contenus nuisibles.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 20.sp
                                )
                            }

                            // 9. Contact & Responsable
                            PolicyCollapsibleCard(
                                title = "9. Contact & Responsable",
                                icon = Icons.Default.Person,
                                isExpanded = expandedSec9,
                                onToggle = { expandedSec9 = !expandedSec9 }
                            ) {
                                Text(
                                    text = "Créateur : Abdoulmohamine Sawadogo • WhatsApp : +226 66 38 78 16 • E-mail : samsamai469@gmail.com",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 20.sp
                                )
                            }

                            // 10. Gestion de l’historique
                            PolicyCollapsibleCard(
                                title = "10. Gestion de l’historique",
                                icon = Icons.Default.ManageHistory,
                                isExpanded = expandedSec10,
                                onToggle = { expandedSec10 = !expandedSec10 }
                            ) {
                                Text(
                                    text = "Consultez, organisez ou supprimez vos traductions et conversations en toute simplicité.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 20.sp
                                )
                            }

                            // 11. Sécurité du compte
                            PolicyCollapsibleCard(
                                title = "11. Sécurité du compte",
                                icon = Icons.Default.Security,
                                isExpanded = expandedSec11,
                                onToggle = { expandedSec11 = !expandedSec11 }
                            ) {
                                Text(
                                    text = "Votre compte est protégé par des mécanismes de sécurité afin de garantir un accès personnel et fiable.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 20.sp
                                )
                            }

                            // 12. Qualité des traductions
                            PolicyCollapsibleCard(
                                title = "12. Qualité des traductions",
                                icon = Icons.Default.Translate,
                                isExpanded = expandedSec12,
                                onToggle = { expandedSec12 = !expandedSec12 }
                            ) {
                                Text(
                                    text = "Chaque traduction est optimisée pour offrir des résultats précis en français, mooré, dioula et d’autres langues.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 20.sp
                                )
                            }

                            // 13. Notifications intelligentes
                            PolicyCollapsibleCard(
                                title = "13. Notifications intelligentes",
                                icon = Icons.Default.NotificationsActive,
                                isExpanded = expandedSec13,
                                onToggle = { expandedSec13 = !expandedSec13 }
                            ) {
                                Text(
                                    text = "Recevez uniquement des rappels utiles et personnalisés. Les notifications restent entièrement facultatives.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 20.sp
                                )
                            }

                            // 14. Autorisations de l’application
                            PolicyCollapsibleCard(
                                title = "14. Autorisations de l’application",
                                icon = Icons.Default.PermDeviceInformation,
                                isExpanded = expandedSec14,
                                onToggle = { expandedSec14 = !expandedSec14 }
                            ) {
                                Text(
                                    text = "Sam Traduction demande uniquement les autorisations nécessaires au bon fonctionnement des fonctionnalités utilisées.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 20.sp
                                )
                            }

                            // 15. Mises à jour & Améliorations
                            PolicyCollapsibleCard(
                                title = "15. Mises à jour & Améliorations",
                                icon = Icons.Default.AutoAwesome,
                                isExpanded = expandedSec15,
                                onToggle = { expandedSec15 = !expandedSec15 }
                            ) {
                                Text(
                                    text = "L’application évolue régulièrement afin d’améliorer les performances, la stabilité et l’expérience utilisateur.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 20.sp
                                )
                            }

                            // 16. Assistance & Contact
                            PolicyCollapsibleCard(
                                title = "16. Assistance & Contact",
                                icon = Icons.Default.SupportAgent,
                                isExpanded = expandedSec16,
                                onToggle = { expandedSec16 = !expandedSec16 }
                            ) {
                                Text(
                                    text = "Une assistance est disponible pour toute question, suggestion ou signalement concernant Sam Traduction.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 20.sp
                                )
                            }

                            // Date de mise à jour
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Update,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Dernière mise à jour :",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = "24 août 2026",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // 4. PRÉSENTATION DE SAM TRADUCTION & FONCTIONNALITÉS
            // =========================================================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        com.example.ui.components.SamLogo(
                            size = 52.dp,
                            elevation = 4.dp
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Sam Traduction",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Traduction & Langues du Burkina Faso",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Sam Traduction est une application conçue pour vous accompagner au quotidien dans l'échange, la communication et l'apprentissage linguistique en valorisant le patrimoine culturel du Burkina Faso.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 22.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Verified,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Mooré • Dioula • Français • Allemand • Anglais",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    FeatureBulletItem(
                        icon = Icons.AutoMirrored.Filled.Chat,
                        title = "Poser des questions & Discuter",
                        description = "Échangez en temps réel avec Sam Traduction sur la culture, les expressions et les subtilités linguistiques."
                    )
                    FeatureBulletItem(
                        icon = Icons.Default.Translate,
                        title = "Traductions précises & instantanées",
                        description = "Traduisez fluidement entre le Français, le Mooré, le Dioula, l'Allemand et l'Anglais."
                    )
                    FeatureBulletItem(
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        title = "Apprentissage interactif & Quiz",
                        description = "Parcours par niveaux, vocabulaire thématique et quiz audio de mémorisation."
                    )
                    FeatureBulletItem(
                        icon = Icons.Default.History,
                        title = "Historique local & Synthèse vocale",
                        description = "Enregistrement local sur votre téléphone et lecture audio avec prononciation naturelle."
                    )
                }
            }

            // =========================================================================
            // 5. LOGO OFFICIEL & BOUTON RETOUR ACCUEIL
            // =========================================================================
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    com.example.ui.components.SamLogo(
                        size = 80.dp,
                        elevation = 6.dp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "SAM AI",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Application officielle • Burkina Faso",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Button(
                onClick = {
                    navController.navigate(Screen.Home) {
                        popUpTo(Screen.Home) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("btn_return_home_about"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    Icons.Default.Home,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Retourner à l'Accueil",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Composant de présentation d'une fonctionnalité avec puce visuelle
 */
@Composable
private fun FeatureBulletItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }
    }
}

/**
 * Carte dépliable pour chaque section thématique de la Politique et Confidentialité
 */
@Composable
private fun PolicyCollapsibleCard(
    title: String,
    icon: ImageVector,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (isExpanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Icon(
                    if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Replier" else "Déplier",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(150))
            ) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(10.dp))
                    content()
                }
            }
        }
    }
}
