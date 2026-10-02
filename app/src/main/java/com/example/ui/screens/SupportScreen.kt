package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.BuildConfig
import com.example.R
import com.example.data.remote.SupportService
import com.example.ui.SamAiViewModel
import com.example.util.GoogleAuthManager
import com.example.util.GoogleAuthResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen(
    viewModel: SamAiViewModel,
    navController: NavController,
    initialType: String = "problem" // "problem" ou "help"
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()

    // Couleur WhatsApp officielle
    val whatsAppGreen = Color(0xFF25D366)

    // Form state
    var selectedType by remember { mutableStateOf(if (initialType == "help") "help" else "problem") }
    var selectedCategory by remember {
        mutableStateOf(if (initialType == "help") "Suggestions" else "Bug")
    }
    var subject by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }

    // Attachment state
    var attachmentUri by remember { mutableStateOf<Uri?>(null) }
    var attachmentBase64 by remember { mutableStateOf<String?>(null) }
    var attachmentName by remember { mutableStateOf<String?>(null) }
    var attachmentMimeType by remember { mutableStateOf<String?>(null) }
    var attachmentSizeBytes by remember { mutableStateOf<Long?>(null) }

    // Submission UI state
    var isSubmitting by remember { mutableStateOf(false) }
    var submittingStatusText by remember { mutableStateOf("Enregistrement en cours…") }
    var submissionSuccess by remember { mutableStateOf(false) }
    var lastFormattedWhatsAppMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isGoogleLoading by remember { mutableStateOf(false) }

    val categories = remember(selectedType) {
        listOf("Bug", "Traduction", "Compte", "Paiement", "Suggestions", "Autre")
    }

    // Image Picker Launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val (base64, mimeType, size) = SupportService.processAttachment(context, uri)
                if (size > SupportService.MAX_ATTACHMENT_SIZE_BYTES) {
                    Toast.makeText(
                        context,
                        if (appLanguage == "en") "File exceeds 5MB limit" else "L'image dépasse la limite de 5 Mo",
                        Toast.LENGTH_LONG
                    ).show()
                } else if (base64 != null) {
                    attachmentUri = uri
                    attachmentBase64 = base64
                    attachmentMimeType = mimeType
                    attachmentSizeBytes = size
                    attachmentName = "capture_${System.currentTimeMillis()}.${if (mimeType?.contains("png") == true) "png" else "jpg"}"
                    Toast.makeText(
                        context,
                        if (appLanguage == "en") "Image attached successfully" else "Image attachée avec succès",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (selectedType == "problem")
                                (if (appLanguage == "en") "Report an Issue" else "Signaler un problème")
                            else
                                (if (appLanguage == "en") "Request Help" else "Demander de l'aide"),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (userProfile.isLoggedIn) userProfile.name else (if (appLanguage == "en") "Guest session" else "Session Invité"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (appLanguage == "en") "Back" else "Retour"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- BANNIÈRE D'AUTHENTIFICATION SI NON CONNECTÉ ---
            if (!userProfile.isLoggedIn) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("card_support_auth_required"),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                        ),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (appLanguage == "en") "Authentication Required" else "Authentification obligatoire",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (appLanguage == "en")
                                    "To submit a report or ask for help, you must sign in with Google so our support team can identify and assist you."
                                else
                                    "Pour signaler un problème ou demander de l'aide, vous devez vous connecter avec Google afin que notre équipe puisse vous identifier et vous assister.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                lineHeight = 20.sp
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = {
                                    if (!isGoogleLoading) {
                                        isGoogleLoading = true
                                        coroutineScope.launch {
                                            when (val authRes = GoogleAuthManager.launchCredentialManagerSignIn(context)) {
                                                is GoogleAuthResult.Success -> {
                                                    val (ok, msg) = viewModel.loginWithGoogle(
                                                        email = authRes.account.email,
                                                        name = authRes.account.displayName,
                                                        photoUrl = authRes.account.photoUrl,
                                                        googleSubjectId = authRes.account.googleId,
                                                        idToken = authRes.account.idToken
                                                    )
                                                    if (ok) {
                                                        Toast.makeText(
                                                            context,
                                                            if (appLanguage == "en") "Connected as ${authRes.account.displayName}" else "Connecté en tant que ${authRes.account.displayName}",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    } else {
                                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                                is GoogleAuthResult.Error -> {
                                                    Toast.makeText(context, authRes.message, Toast.LENGTH_LONG).show()
                                                }
                                                GoogleAuthResult.Cancelled -> {}
                                            }
                                            isGoogleLoading = false
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("btn_support_login_google"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                if (isGoogleLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.AccountCircle,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (appLanguage == "en") "Sign In with Google" else "Se connecter avec Google",
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (submissionSuccess) {
                // --- CARTE SUCCÈS & REDIRECTION WHATSAPP ---
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                            .testTag("card_support_success"),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(2.dp, whatsAppGreen),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(26.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = whatsAppGreen.copy(alpha = 0.15f),
                                modifier = Modifier.size(80.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_whatsapp),
                                        contentDescription = "WhatsApp",
                                        tint = whatsAppGreen,
                                        modifier = Modifier.size(46.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            Text(
                                text = if (appLanguage == "en") "Redirection to WhatsApp…" else "Redirection vers WhatsApp…",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = if (appLanguage == "en")
                                    "Your request has been securely recorded. WhatsApp has opened with your formatted message ready to send to Sam AI Support."
                                else
                                    "Votre demande a été enregistrée avec succès. L'application WhatsApp s'ouvre avec votre message pré-rempli pour l'équipe Sam AI.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                lineHeight = 22.sp
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            // Bouton Relancer WhatsApp
                            Button(
                                onClick = {
                                    lastFormattedWhatsAppMessage?.let { msg ->
                                        SupportService.launchWhatsApp(context, msg)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = whatsAppGreen)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_whatsapp),
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (appLanguage == "en") "Open WhatsApp Again" else "Ouvrir WhatsApp à nouveau",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Bouton Retour Paramètres
                            OutlinedButton(
                                onClick = { navController.popBackStack() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(if (appLanguage == "en") "Back to Settings" else "Retour aux Paramètres", fontWeight = FontWeight.SemiBold)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Bouton Nouveau message
                            TextButton(
                                onClick = {
                                    submissionSuccess = false
                                    subject = ""
                                    message = ""
                                    attachmentUri = null
                                    attachmentBase64 = null
                                    attachmentName = null
                                    attachmentSizeBytes = null
                                    errorMessage = null
                                }
                            ) {
                                Text(if (appLanguage == "en") "Submit Another Request" else "Écrire un autre message")
                            }
                        }
                    }
                }
            } else {
                // --- 2 CARTES DE SÉLECTION DU TYPE ---
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Carte 1: Signaler un problème
                        val isProblemSelected = selectedType == "problem"
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    selectedType = "problem"
                                    if (selectedCategory == "Suggestions") selectedCategory = "Bug"
                                }
                                .testTag("card_type_problem"),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isProblemSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(
                                if (isProblemSelected) 2.dp else 1.dp,
                                if (isProblemSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = if (isProblemSelected) 4.dp else 1.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(text = "🐞", fontSize = 32.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (appLanguage == "en") "Report Problem" else "Signaler un problème",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isProblemSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (appLanguage == "en") "Bug, crash, translation issue" else "Dysfonctionnement, bug, crash",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 14.sp
                                )
                            }
                        }

                        // Carte 2: Demander de l'aide
                        val isHelpSelected = selectedType == "help"
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    selectedType = "help"
                                    if (selectedCategory == "Bug") selectedCategory = "Suggestions"
                                }
                                .testTag("card_type_help"),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isHelpSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(
                                if (isHelpSelected) 2.dp else 1.dp,
                                if (isHelpSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = if (isHelpSelected) 4.dp else 1.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(text = "💬", fontSize = 32.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (appLanguage == "en") "Request Help" else "Demander de l'aide",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isHelpSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (appLanguage == "en") "Guidance, questions, feedback" else "Assistance, question, conseil",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }

                // --- FORMULAIRE DÉTAILLÉ ---
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("card_support_form"),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            // En-tête formulaire
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.EditNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (selectedType == "problem")
                                        (if (appLanguage == "en") "Bug Report Form" else "Formulaire de signalement")
                                    else
                                        (if (appLanguage == "en") "Help Request Form" else "Formulaire de demande d'aide"),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // 1. Catégorie (Chips)
                            Text(
                                text = if (appLanguage == "en") "Category *" else "Catégorie *",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(categories) { cat ->
                                    val isCatSelected = selectedCategory == cat
                                    FilterChip(
                                        selected = isCatSelected,
                                        onClick = { selectedCategory = cat },
                                        label = { Text(cat, fontSize = 13.sp) },
                                        leadingIcon = if (isCatSelected) {
                                            {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        } else null,
                                        shape = RoundedCornerShape(10.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // 2. Sujet
                            Text(
                                text = if (appLanguage == "en") "Subject *" else "Sujet *",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = subject,
                                onValueChange = { subject = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_support_subject"),
                                placeholder = {
                                    Text(
                                        if (selectedType == "problem")
                                            (if (appLanguage == "en") "e.g. Translation audio issue" else "ex : Erreur de synthèse vocale en Mooré")
                                        else
                                            (if (appLanguage == "en") "e.g. Question on Sam AI features" else "ex : Question sur les fonctionnalités de Sam AI"),
                                        fontSize = 14.sp
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // 3. Message / Description détaillée
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (appLanguage == "en") "Detailed Message *" else "Message détaillé *",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${message.length} / 20 min",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (message.length >= 20) Color(0xFF22C55E) else MaterialTheme.colorScheme.error
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = message,
                                onValueChange = { message = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                                    .testTag("input_support_message"),
                                placeholder = {
                                    Text(
                                        if (appLanguage == "en")
                                            "Describe the situation clearly (minimum 20 characters)..."
                                        else
                                            "Expliquez en détail votre situation (minimum 20 caractères)...",
                                        fontSize = 14.sp,
                                        lineHeight = 19.sp
                                    )
                                },
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // 4. Numéro de téléphone optionnel
                            Text(
                                text = if (appLanguage == "en") "Your Contact Number (Optional)" else "Votre Numéro de contact (Optionnel)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = phoneNumber,
                                onValueChange = { phoneNumber = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_support_phone"),
                                placeholder = { Text(if (appLanguage == "en") "e.g. +226 70 00 00 00" else "ex : +226 70 00 00 00", fontSize = 14.sp) },
                                singleLine = true,
                                leadingIcon = {
                                    Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(20.dp))
                                },
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            // 5. Pièce jointe / Capture d'écran
                            Text(
                                text = if (appLanguage == "en") "Screenshot (Max 5MB)" else "Capture d'écran (Max 5 Mo)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            if (attachmentUri == null) {
                                OutlinedButton(
                                    onClick = { imagePickerLauncher.launch("image/*") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("btn_support_pick_image"),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                ) {
                                    Icon(
                                        Icons.Default.AddPhotoAlternate,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if (appLanguage == "en") "Add a Screenshot" else "Ajouter une capture d'écran")
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(
                                            model = attachmentUri,
                                            contentDescription = "Capture jointe",
                                            modifier = Modifier
                                                .size(54.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = attachmentName ?: "image_jointe.jpg",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1
                                            )
                                            val sizeKb = (attachmentSizeBytes ?: 0L) / 1024
                                            Text(
                                                text = if (sizeKb > 1024) "${String.format(Locale.getDefault(), "%.1f", sizeKb / 1024f)} Mo" else "$sizeKb Ko",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                attachmentUri = null
                                                attachmentBase64 = null
                                                attachmentName = null
                                                attachmentMimeType = null
                                                attachmentSizeBytes = null
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Supprimer pièce jointe",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }

                            // --- MESSAGE D'ERREUR GLOBAL ---
                            if (errorMessage != null) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.ErrorOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = errorMessage ?: "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(22.dp))

                            // --- BOUTON VERT PRINCIPAL : ENVOYER SUR WHATSAPP ---
                            Button(
                                onClick = {
                                    if (!isSubmitting) {
                                        errorMessage = null
                                        coroutineScope.launch {
                                            isSubmitting = true
                                            submittingStatusText = if (appLanguage == "en") "Recording request…" else "Enregistrement en cours…"

                                            val submission = SupportService.SupportSubmission(
                                                type = selectedType,
                                                category = selectedCategory,
                                                subject = subject,
                                                message = message,
                                                phoneNumber = phoneNumber,
                                                attachmentUri = attachmentUri,
                                                attachmentBase64 = attachmentBase64,
                                                attachmentName = attachmentName,
                                                attachmentMimeType = attachmentMimeType
                                            )

                                            val result = SupportService.submitSupportRequest(
                                                context = context,
                                                userProfile = userProfile,
                                                submission = submission,
                                                appVersionName = BuildConfig.VERSION_NAME
                                            )

                                            if (result.isSuccess) {
                                                val formattedMsg = result.getOrNull() ?: ""
                                                lastFormattedWhatsAppMessage = formattedMsg

                                                submittingStatusText = if (appLanguage == "en") "Redirecting to WhatsApp…" else "Redirection vers WhatsApp…"
                                                delay(450)

                                                SupportService.launchWhatsApp(context, formattedMsg)

                                                isSubmitting = false
                                                submissionSuccess = true
                                                Toast.makeText(
                                                    context,
                                                    if (appLanguage == "en") "Redirection to WhatsApp…" else "Redirection vers WhatsApp…",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            } else {
                                                isSubmitting = false
                                                errorMessage = result.exceptionOrNull()?.message
                                                    ?: (if (appLanguage == "en") "Unable to process request. Please try again." else "Impossible de traiter votre demande. Réessayez.")
                                            }
                                        }
                                    }
                                },
                                enabled = !isSubmitting,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("btn_submit_support_whatsapp"),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = whatsAppGreen,
                                    contentColor = Color.White,
                                    disabledContainerColor = whatsAppGreen.copy(alpha = 0.6f),
                                    disabledContentColor = Color.White.copy(alpha = 0.8f)
                                ),
                                elevation = ButtonDefaults.buttonElevation(
                                    defaultElevation = 3.dp,
                                    pressedElevation = 6.dp
                                )
                            ) {
                                if (isSubmitting) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(22.dp),
                                            color = Color.White,
                                            strokeWidth = 2.5.dp
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = submittingStatusText,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                    }
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_whatsapp),
                                            contentDescription = "WhatsApp",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = if (appLanguage == "en") "Send via WhatsApp" else "Envoyer sur WhatsApp",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}
