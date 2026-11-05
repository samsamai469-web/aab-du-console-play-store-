package com.example.ui.screens

import android.accounts.AccountManager
import android.app.Activity
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.ui.SamAiViewModel
import com.example.ui.theme.SamPrimary
import com.example.ui.theme.SamPrimaryLight
import kotlinx.coroutines.launch

/**
 * Présentation officielle de l'application SAM AI en 3 étapes :
 * 1. Bienvenue sur Sam Traduction (Présentation du projet, patrimoine et langues)
 * 2. Comment fonctionne l'application ? (4 fonctionnalités phares)
 * 3. Bienvenue sur Sam AI : Connexion / Création de compte réelle (ou Ignorer pour continuer en mode invité)
 */
@Composable
fun OnboardingScreen(
    viewModel: SamAiViewModel? = null,
    onFinish: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0B0D))
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> WelcomePage(
                    onNext = {
                        scope.launch { pagerState.animateScrollToPage(1) }
                    }
                )
                1 -> HowItWorksPage(
                    onBack = {
                        scope.launch { pagerState.animateScrollToPage(0) }
                    },
                    onNext = {
                        scope.launch { pagerState.animateScrollToPage(2) }
                    }
                )
                2 -> OnboardingAuthPage(
                    viewModel = viewModel,
                    onSkip = onFinish,
                    onAuthSuccess = onFinish
                )
            }
        }
    }
}

/**
 * Indicateur à 3 points au sommet :
 * Le point actif est une barre allongée orange (SamPrimary).
 */
@Composable
private fun OnboardingDotsIndicator(
    currentPage: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val isSelected = currentPage == index
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .height(5.dp)
                    .width(if (isSelected) 28.dp else 7.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) SamPrimary else Color.White.copy(alpha = 0.25f)
                    )
            )
        }
    }
}

// =============================================================================
// PAGE 1 : BIENVENUE SUR SAM TRADUCTION
// =============================================================================
@Composable
private fun WelcomePage(onNext: () -> Unit) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        val isCompact = maxHeight < 740.dp
        val imageHeight = if (isCompact) 102.dp else 120.dp
        val sectionGap = if (isCompact) 10.dp else 14.dp

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    OnboardingDotsIndicator(currentPage = 0)

                    Spacer(modifier = Modifier.height(if (isCompact) 12.dp else 18.dp))

                    // Titre Principal
                    Text(
                        text = "Bienvenue sur Sam\nTraduction",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = if (isCompact) 23.sp else 25.sp,
                            lineHeight = if (isCompact) 28.sp else 31.sp
                        ),
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(if (isCompact) 6.dp else 8.dp))

                    // Sous-titre
                    Text(
                        text = "Intelligence Artificielle & Langues du Burkina\nFaso",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            lineHeight = 17.sp
                        ),
                        color = SamPrimaryLight,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(sectionGap))

                // 1. Carte Descriptif du Projet
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF131720),
                    border = BorderStroke(1.dp, Color(0xFF222836))
                ) {
                    Text(
                        text = "Votre compagnon intelligent dédié à l'échange, à la communication et à la traduction instantanée, conçu pour valoriser les langues et le patrimoine culturel burkinabè.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = if (isCompact) 13.sp else 13.5.sp,
                            lineHeight = if (isCompact) 18.sp else 20.sp,
                            color = Color.White.copy(alpha = 0.90f)
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(
                            horizontal = 16.dp,
                            vertical = if (isCompact) 12.dp else 16.dp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(sectionGap))

                // 2. Carte Images Patrimoine (Famille + Faso Dan Fani)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF131720),
                    border = BorderStroke(1.dp, Color(0xFF222836))
                ) {
                    Column(
                        modifier = Modifier.padding(if (isCompact) 10.dp else 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Image Famille burkinabè
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(R.drawable.img_burkinabe_family_1786879044801)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Famille burkinabè",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(imageHeight)
                                    .clip(RoundedCornerShape(14.dp))
                            )

                            // Image Faso Dan Fani tissé traditionnel
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(R.drawable.img_faso_dan_fani_1786953081141)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Faso Dan Fani",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(imageHeight)
                                    .clip(RoundedCornerShape(14.dp))
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Valorisation du patrimoine et des langues du Faso",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            ),
                            color = SamPrimaryLight,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(sectionGap))

                // 3. Pastille Langues disponibles
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF131720),
                    border = BorderStroke(1.dp, Color(0xFF222836))
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = 12.dp,
                            vertical = if (isCompact) 10.dp else 12.dp
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Translate,
                            contentDescription = null,
                            tint = SamPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Mooré • Dioula • Français • Anglais • Allemand",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp
                            ),
                            color = Color.White.copy(alpha = 0.95f),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            // Bouton Suivant (Orange / Rouge-orangé de l'app)
            Button(
                onClick = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isCompact) 48.dp else 52.dp)
                    .testTag("btn_welcome_next"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SamPrimary,
                    contentColor = Color.White
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Text(
                    text = "SUIVANT",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp,
                        letterSpacing = 0.5.sp
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Suivant",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// =============================================================================
// PAGE 2 : COMMENT FONCTIONNE L'APPLICATION ?
// =============================================================================
@Composable
private fun HowItWorksPage(
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val scrollState = rememberScrollState()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        val isCompact = maxHeight < 740.dp
        val cardGap = if (isCompact) 7.dp else 10.dp

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // En-tête : Flèche retour + 3 Dots centrés
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .size(38.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Retour",
                                tint = Color.White
                            )
                        }

                        OnboardingDotsIndicator(
                            currentPage = 1,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    Spacer(modifier = Modifier.height(if (isCompact) 4.dp else 8.dp))

                    // Titre Principal
                    Text(
                        text = "Comment fonctionne\nl'application ?",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = if (isCompact) 21.sp else 23.sp,
                            lineHeight = if (isCompact) 26.sp else 28.sp
                        ),
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Sous-titre
                    Text(
                        text = "4 fonctionnalités simples pour vous accompagner",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 12.5.sp,
                            color = Color.White.copy(alpha = 0.70f)
                        ),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(if (isCompact) 8.dp else 12.dp))

                // 4 Cartes de fonctionnalités
                FeatureItemCard(
                    imageRes = R.drawable.img_burkinabe_family_1786879044801,
                    badgeIcon = Icons.Default.Translate,
                    title = "Traduire des langues",
                    description = "Traduisez rapidement mots, phrases et expressions entre le Français, le Mooré, le Dioula, l'Anglais et l'Allemand.",
                    isCompact = isCompact
                )

                Spacer(modifier = Modifier.height(cardGap))

                FeatureItemCard(
                    imageRes = R.drawable.img_faso_dan_fani_1786953081141,
                    badgeIcon = Icons.Default.SwapHoriz,
                    title = "Langue source & Langue cible",
                    description = "Sélectionnez vos langues en un instant et permutez-les en un seul clic selon vos besoins.",
                    isCompact = isCompact
                )

                Spacer(modifier = Modifier.height(cardGap))

                FeatureItemCard(
                    imageRes = R.drawable.img_burkinabe_child_1786953052232,
                    badgeIcon = Icons.Default.Mic,
                    title = "Traduction vocale & Prononciation",
                    description = "Dictez vos messages au micro et écoutez la lecture audio authentique de vos traductions.",
                    isCompact = isCompact
                )

                Spacer(modifier = Modifier.height(cardGap))

                FeatureItemCard(
                    imageRes = R.drawable.img_burkinabe_house_1786881867414,
                    badgeIcon = Icons.Default.History,
                    title = "Historique personnel sécurisé",
                    description = "Toutes vos traductions et échanges sont automatiquement sauvegardés et consultables à tout moment.",
                    isCompact = isCompact
                )

                Spacer(modifier = Modifier.height(8.dp))
            }

            // Bouton Suivant (Orange / Rouge-orangé de l'app)
            Button(
                onClick = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isCompact) 48.dp else 52.dp)
                    .testTag("btn_how_it_works_next"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SamPrimary,
                    contentColor = Color.White
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Text(
                    text = "SUIVANT",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp,
                        letterSpacing = 0.5.sp
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Suivant",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Carte de fonctionnalité individuelle avec vignette + petit badge icône
 */
@Composable
private fun FeatureItemCard(
    imageRes: Int,
    badgeIcon: ImageVector,
    title: String,
    description: String,
    isCompact: Boolean = false
) {
    val context = LocalContext.current
    val thumbSize = if (isCompact) 52.dp else 58.dp

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF131720),
        border = BorderStroke(1.dp, Color(0xFF222836))
    ) {
        Row(
            modifier = Modifier.padding(if (isCompact) 9.dp else 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Vignette Image avec pastille d'icône en bas à droite
            Box(
                modifier = Modifier.size(thumbSize)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imageRes)
                        .crossfade(true)
                        .build(),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                )

                // Badge icône en bas à droite
                Surface(
                    shape = CircleShape,
                    color = SamPrimary,
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.BottomEnd)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = badgeIcon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Titre et Description
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = if (isCompact) 13.5.sp else 14.sp
                    ),
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = if (isCompact) 11.5.sp else 12.sp,
                        lineHeight = 15.sp,
                        color = Color.White.copy(alpha = 0.80f)
                    )
                )
            }
        }
    }
}

// =============================================================================
// PAGE 3 : BIENVENUE SUR SAM AI (CONNEXION / INSCRIPTION RÉELLE)
// =============================================================================
@Composable
private fun OnboardingAuthPage(
    viewModel: SamAiViewModel?,
    onSkip: () -> Unit,
    onAuthSuccess: () -> Unit
) {
    val scrollState = rememberScrollState()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF451B13),
                        Color(0xFF28120F),
                        Color(0xFF100C0D),
                        Color(0xFF0A0B0D)
                    )
                )
            )
    ) {
        val isCompact = maxHeight < 760.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 18.dp, vertical = if (isCompact) 6.dp else 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Barre supérieure avec bouton "Ignorer" à droite
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 2.dp)
            ) {
                Text(
                    text = "Ignorer",
                    color = Color(0xFFFF3B1D),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.5.sp
                    ),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSkip() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("btn_onboarding_skip_top")
                )
            }

            // Section réutilisable : Logo + Titre + Carte Se connecter / S'inscrire
            SamAiWelcomeAuthSection(
                viewModel = viewModel,
                onAuthSuccess = onAuthSuccess,
                isCompact = isCompact
            )

            // Pied de page : "Ignorer pour le moment"
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = if (isCompact) 8.dp else 12.dp, bottom = 4.dp)
            ) {
                Text(
                    text = "Ignorer pour le moment",
                    color = Color(0xFFFF3B1D),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSkip() }
                        .padding(horizontal = 12.dp, vertical = 3.dp)
                        .testTag("btn_onboarding_commencer")
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = "Vous pourrez vous connecter plus tard depuis l'onglet Profil.",
                    color = Color(0xFF9EA5B4),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 15.sp
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
    }
}

/**
 * Composant complet "Bienvenue sur Sam AI" avec le logo flamme, le titre, le sous-titre
 * et la carte interactive "Se connecter" / "S'inscrire" (onglet par défaut) + "Mot de passe oublié ?" + "Continuer avec Google".
 * Utilisé à la fois sur la 3e page de présentation et dans l'onglet Profil lorsqu'on a ignoré la connexion.
 */
@Composable
fun SamAiWelcomeAuthSection(
    viewModel: SamAiViewModel?,
    onAuthSuccess: () -> Unit = {},
    isCompact: Boolean = false
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // S'inscrire est l'onglet par défaut (comme sur la maquette)
    var isSignUpTab by remember { mutableStateOf(true) }
    var nameInput by remember { mutableStateOf("") }
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    var isSubmitting by remember { mutableStateOf(false) }
    var isGoogleLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var resetConfirmationMessage by remember { mutableStateOf<String?>(null) }

    // Sélecteur officiel de compte Google du téléphone Android (ne dépend pas du SHA-1 et renvoie la vraie adresse choisie)
    val googleAccountPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { activityResult ->
        isGoogleLoading = false
        if (activityResult.resultCode == Activity.RESULT_OK) {
            val selectedEmail = activityResult.data
                ?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
                ?.trim()
                ?.lowercase()
                .orEmpty()

            val validationErr = com.example.data.remote.FirebaseAuthService.validateEmailWithGoogleRules(selectedEmail)
            if (selectedEmail.isBlank() || validationErr != null) {
                errorMessage = validationErr ?: "Aucune adresse Google valide n'a été sélectionnée."
            } else if (viewModel != null) {
                val derivedName = nameInput.trim().takeIf { it.isNotBlank() }
                    ?: selectedEmail.substringBefore("@")
                        .replace(".", " ")
                        .replace("_", " ")
                        .split(" ")
                        .filter { it.isNotBlank() }
                        .joinToString(" ") { part ->
                            part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                        }
                        .ifBlank { selectedEmail.substringBefore("@") }

                coroutineScope.launch {
                    isGoogleLoading = true
                    val (ok, msg) = viewModel.loginWithGoogle(
                        email = selectedEmail,
                        name = derivedName
                    )
                    isGoogleLoading = false
                    if (ok) {
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        onAuthSuccess()
                    } else {
                        errorMessage = msg
                    }
                }
            }
        }
    }

    val logoOuterSize = if (isCompact) 62.dp else 68.dp
    val logoInnerSize = if (isCompact) 44.dp else 50.dp
    val fieldSpacing = if (isCompact) 8.dp else 10.dp

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Logo circulaire noir avec la flamme Sam AI
        Surface(
            modifier = Modifier.size(logoOuterSize),
            shape = CircleShape,
            color = Color(0xFF050608),
            shadowElevation = 10.dp,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(id = R.drawable.sam_ai_logo),
                    contentDescription = "Logo Sam AI",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(logoInnerSize)
                        .clip(CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.height(if (isCompact) 8.dp else 12.dp))

        // Titre : Bienvenue sur Sam AI
        Text(
            text = "Bienvenue sur Sam AI",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = if (isCompact) 21.sp else 23.sp
            ),
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Sous-titre
        Text(
            text = "Un compte est facultatif : il sert à sauvegarder vos progrès.",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = if (isCompact) 12.5.sp else 13.sp,
                lineHeight = 17.sp
            ),
            color = Color(0xFFD0D5DD),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Spacer(modifier = Modifier.height(if (isCompact) 10.dp else 14.dp))

        // Carte principale d'authentification
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("card_auth_options"),
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFF151821),
            border = BorderStroke(1.dp, Color(0xFF232836)),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = if (isCompact) 12.dp else 15.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Sélecteur d'onglets : "Se connecter" | "S'inscrire"
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF232834)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Onglet "Se connecter"
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(9.dp))
                                .background(
                                    if (!isSignUpTab) Color(0xFFFF3B1D) else Color.Transparent
                                )
                                .clickable {
                                    isSignUpTab = false
                                    errorMessage = null
                                }
                                .testTag("tab_se_connecter"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Se connecter",
                                color = if (!isSignUpTab) Color.White else Color(0xFFD5D9E2),
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (!isSignUpTab) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.5.sp
                                )
                            )
                        }

                        // Onglet "S'inscrire"
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(9.dp))
                                .background(
                                    if (isSignUpTab) Color(0xFFFF3B1D) else Color.Transparent
                                )
                                .clickable {
                                    isSignUpTab = true
                                    errorMessage = null
                                    resetConfirmationMessage = null
                                }
                                .testTag("tab_sinscrire"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "S'inscrire",
                                color = if (isSignUpTab) Color.White else Color(0xFFD5D9E2),
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (isSignUpTab) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.5.sp
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(if (isCompact) 10.dp else 12.dp))

                // Champ Nom (affiché uniquement dans l'onglet S'inscrire)
                if (isSignUpTab) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = {
                            nameInput = it
                            errorMessage = null
                        },
                        placeholder = {
                            Text(
                                text = "Nom (facultatif)",
                                color = Color(0xFF8C93A4),
                                fontSize = 14.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Nom",
                                tint = Color(0xFF4285F4),
                                modifier = Modifier.size(19.dp)
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        shape = RoundedCornerShape(13.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF151821),
                            unfocusedContainerColor = Color(0xFF151821),
                            focusedBorderColor = Color(0xFFFF3B1D),
                            unfocusedBorderColor = Color(0xFF2B3142),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFFFF3B1D)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_auth_name")
                    )

                    Spacer(modifier = Modifier.height(fieldSpacing))
                }

                // Champ Email
                OutlinedTextField(
                    value = emailInput,
                    onValueChange = {
                        emailInput = it
                        errorMessage = null
                        resetConfirmationMessage = null
                    },
                    placeholder = {
                        Text(
                            text = "moussa@exemple.com",
                            color = Color(0xFF8C93A4),
                            fontSize = 14.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Email,
                            contentDescription = "Email",
                            tint = Color(0xFFE2E6EE),
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    shape = RoundedCornerShape(13.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF151821),
                        unfocusedContainerColor = Color(0xFF151821),
                        focusedBorderColor = Color(0xFFFF3B1D),
                        unfocusedBorderColor = Color(0xFF2B3142),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFFFF3B1D)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_auth_email")
                )

                Spacer(modifier = Modifier.height(fieldSpacing))

                // Champ Mot de passe
                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = {
                        passwordInput = it
                        errorMessage = null
                    },
                    placeholder = {
                        Text(
                            text = "••••••••",
                            color = Color(0xFF8C93A4),
                            fontSize = 14.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Mot de passe",
                            tint = Color(0xFFF5B041),
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (passwordVisible) "Masquer le mot de passe" else "Afficher le mot de passe",
                                tint = Color(0xFFC5CBD6),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    shape = RoundedCornerShape(13.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF151821),
                        unfocusedContainerColor = Color(0xFF151821),
                        focusedBorderColor = Color(0xFFFF3B1D),
                        unfocusedBorderColor = Color(0xFF2B3142),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFFFF3B1D)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_auth_password")
                )

                // Lien "Mot de passe oublié ?" et message de confirmation (dans l'onglet Se connecter)
                if (!isSignUpTab) {
                    Spacer(modifier = Modifier.height(6.dp))

                    Box(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Mot de passe oublié ?",
                            color = Color(0xFFFF4526),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.5.sp
                            ),
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    val targetEmail = emailInput.trim()
                                    if (targetEmail.isBlank() || !targetEmail.contains("@")) {
                                        errorMessage = "Veuillez d'abord saisir votre adresse email ci-dessus pour réinitialiser votre mot de passe."
                                        resetConfirmationMessage = null
                                    } else {
                                        errorMessage = null
                                        if (viewModel != null) {
                                            viewModel.sendPasswordResetEmail(
                                                email = targetEmail,
                                                context = context
                                            ) { ok, msg ->
                                                if (ok) {
                                                    resetConfirmationMessage = "Un email contenant les instructions de réinitialisation a été envoyé à $targetEmail."
                                                } else {
                                                    errorMessage = msg
                                                }
                                            }
                                        } else {
                                            resetConfirmationMessage = "Un email contenant les instructions de réinitialisation a été envoyé à $targetEmail."
                                        }
                                    }
                                }
                                .padding(vertical = 2.dp, horizontal = 4.dp)
                                .testTag("btn_forgot_password")
                        )
                    }

                    if (!resetConfirmationMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = resetConfirmationMessage!!,
                            color = Color(0xFFFF4526),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("txt_reset_password_confirmation")
                        )
                    }
                }

                // Affichage d'une erreur éventuelle
                if (!errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = errorMessage!!,
                        color = Color(0xFFFF5252),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(if (isCompact) 10.dp else 12.dp))

                // Bouton principal : "Créer mon compte" ou "Se connecter"
                Button(
                    onClick = {
                        if (isSubmitting) return@Button
                        val cleanEmail = emailInput.trim()
                        val cleanPassword = passwordInput

                        if (cleanEmail.isBlank()) {
                            errorMessage = "Veuillez entrer votre adresse email."
                            return@Button
                        }
                        if (cleanPassword.isBlank()) {
                            errorMessage = "Veuillez entrer votre mot de passe."
                            return@Button
                        }
                        if (isSignUpTab && cleanPassword.length < 6) {
                            errorMessage = "Le mot de passe doit contenir au moins 6 caractères."
                            return@Button
                        }

                        errorMessage = null
                        resetConfirmationMessage = null

                        if (viewModel == null) {
                            onAuthSuccess()
                            return@Button
                        }

                        isSubmitting = true
                        if (isSignUpTab) {
                            viewModel.registerWithEmail(
                                name = nameInput.trim(),
                                email = cleanEmail,
                                password = cleanPassword,
                                context = context
                            ) { ok, msg ->
                                isSubmitting = false
                                if (ok) {
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    onAuthSuccess()
                                } else {
                                    errorMessage = msg
                                }
                            }
                        } else {
                            viewModel.loginWithEmail(
                                email = cleanEmail,
                                password = cleanPassword,
                                context = context
                            ) { ok, msg ->
                                isSubmitting = false
                                if (ok) {
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    onAuthSuccess()
                                } else {
                                    errorMessage = msg
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isCompact) 46.dp else 48.dp)
                        .testTag("btn_auth_email_submit"),
                    shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF3B1D),
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = if (isSignUpTab) "Créer mon compte" else "Se connecter",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(if (isCompact) 10.dp else 12.dp))

                // Séparateur "ou"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        thickness = 1.dp,
                        color = Color(0xFF262C3A)
                    )
                    Text(
                        text = "ou",
                        color = Color(0xFF9EA5B4),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        thickness = 1.dp,
                        color = Color(0xFF262C3A)
                    )
                }

                Spacer(modifier = Modifier.height(if (isCompact) 10.dp else 12.dp))

                // Bouton "Continuer avec Google" (Ouvre le sélecteur officiel des comptes Google du téléphone)
                Surface(
                    onClick = {
                        if (isGoogleLoading) return@Surface
                        if (viewModel == null) return@Surface
                        errorMessage = null
                        resetConfirmationMessage = null
                        try {
                            isGoogleLoading = true
                            val pickerIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                AccountManager.newChooseAccountIntent(
                                    null,
                                    null,
                                    arrayOf("com.google"),
                                    null,
                                    null,
                                    null,
                                    null
                                )
                            } else {
                                @Suppress("DEPRECATION")
                                AccountManager.newChooseAccountIntent(
                                    null,
                                    null,
                                    arrayOf("com.google"),
                                    false,
                                    null,
                                    null,
                                    null,
                                    null
                                )
                            }
                            googleAccountPickerLauncher.launch(pickerIntent)
                        } catch (t: Throwable) {
                            // Repli sur CredentialManager si le sélecteur système n'est pas disponible
                            coroutineScope.launch {
                                try {
                                    val result = com.example.util.GoogleAuthManager.launchCredentialManagerSignIn(context)
                                    isGoogleLoading = false
                                    when (result) {
                                        is com.example.util.GoogleAuthResult.Success -> {
                                            val googleEmail = result.account.email.trim()
                                            val validationErr = com.example.data.remote.FirebaseAuthService.validateEmailWithGoogleRules(googleEmail)
                                            if (googleEmail.isBlank() || validationErr != null) {
                                                errorMessage = validationErr ?: "Aucune adresse email Google valide n'a été reçue."
                                            } else {
                                                val (ok, msg) = viewModel.loginWithGoogle(
                                                    email = googleEmail,
                                                    name = result.account.displayName,
                                                    photoUrl = result.account.photoUrl,
                                                    googleSubjectId = result.account.googleId,
                                                    idToken = result.account.idToken
                                                )
                                                if (ok) {
                                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                    onAuthSuccess()
                                                } else {
                                                    errorMessage = msg
                                                }
                                            }
                                        }
                                        is com.example.util.GoogleAuthResult.Cancelled -> {
                                            errorMessage = null
                                        }
                                        is com.example.util.GoogleAuthResult.Error -> {
                                            errorMessage = "Aucun compte Google détecté sur cet appareil. Veuillez utiliser votre adresse email ci-dessus."
                                        }
                                    }
                                } catch (_: Throwable) {
                                    isGoogleLoading = false
                                    errorMessage = "Aucun compte Google détecté sur cet appareil. Veuillez utiliser votre adresse email ci-dessus."
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isCompact) 44.dp else 46.dp)
                        .testTag("btn_auth_google"),
                    shape = RoundedCornerShape(13.dp),
                    color = Color(0xFF151821),
                    border = BorderStroke(1.dp, Color(0xFF2B3142))
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
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Connexion Google...",
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            )
                        } else {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_google_logo),
                                contentDescription = "Google",
                                tint = Color.Unspecified,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Continuer avec Google",
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

