package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.data.local.LinguisticKnowledge
import com.example.data.local.SecurityAuditLogEntity
import com.example.data.local.TranslationAnalyticsEntity
import com.example.ui.SamAiViewModel
import com.example.ui.SecurityTestResult
import com.example.ui.navigation.Screen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminMooreScreen(viewModel: SamAiViewModel, navController: NavController) {
    val isAdminAuth by viewModel.isAdminAuthenticated.collectAsState()
    val auditLogs by viewModel.auditLogs.collectAsState(initial = emptyList())
    val securityReport by viewModel.securityReport.collectAsState()

    var currentTab by remember { mutableIntStateOf(0) }
    val knowledgeList by viewModel.allKnowledge.collectAsState(initial = emptyList())
    var searchQuery by remember { mutableStateOf("") }
    var selectedLanguageFilter by remember { mutableStateOf("Toutes") }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingKnowledge by remember { mutableStateOf<LinguisticKnowledge?>(null) }
    var actionMessage by remember { mutableStateOf<String?>(null) }

    // Admin login form states
    var adminKeyInput by remember { mutableStateOf("") }
    var adminMfaInput by remember { mutableStateOf("") }
    var isKeyPasswordVisible by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<String?>(null) }

    val filteredList = knowledgeList.filter {
        val matchLang = selectedLanguageFilter == "Toutes" || it.language.equals(selectedLanguageFilter, ignoreCase = true)
        val matchQuery = it.word.contains(searchQuery, ignoreCase = true) ||
            it.translation.contains(searchQuery, ignoreCase = true) ||
            it.category.contains(searchQuery, ignoreCase = true) ||
            it.variants.contains(searchQuery, ignoreCase = true)
        matchLang && matchQuery
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Console Sécurité & Dictionnaire",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = if (isAdminAuth) "Session Administrateur Active (Persistante & Chiffrée)" else "Accès Restreint — Administrateur Principal Uniquement",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isAdminAuth) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
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
                        }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    if (isAdminAuth) {
                        IconButton(onClick = { viewModel.logoutAdmin() }) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = "Déconnexion Admin",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (!isAdminAuth) {
                // --- ÉCRAN D'AUTHENTIFICATION ADMINISTRATEUR ULTRA-SÉCURISÉ (MFA/2FA) ---
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Security,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }

                            Text(
                                text = "Espace Réservé Administrateur",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Text(
                                text = "L'accès aux statistiques globales de l'application, à la console d'audit et à l'administration du dictionnaire officiel est strictement réservé à l'administrateur principal de Sam Traduction.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            OutlinedTextField(
                                value = adminKeyInput,
                                onValueChange = {
                                    adminKeyInput = it
                                    loginError = null
                                },
                                label = { Text("Mot de passe Administrateur") },
                                placeholder = { Text("Entrez votre clé secrète administrateur") },
                                visualTransformation = if (isKeyPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                leadingIcon = { Icon(Icons.Default.VpnKey, contentDescription = null) },
                                trailingIcon = {
                                    IconButton(onClick = { isKeyPasswordVisible = !isKeyPasswordVisible }) {
                                        Icon(
                                            imageVector = if (isKeyPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (isKeyPasswordVisible) "Masquer le mot de passe" else "Afficher le mot de passe"
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            if (loginError != null) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = loginError ?: "",
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    val (ok, msg) = viewModel.authenticateAdminWith2FA(adminKeyInput.trim(), adminMfaInput.trim())
                                    if (!ok) {
                                        loginError = msg
                                    } else {
                                        loginError = null
                                        adminKeyInput = ""
                                        adminMfaInput = ""
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.VerifiedUser, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Déverrouiller l'Espace Admin")
                            }

                            // Bouton d'accès direct réservé au créateur
                            OutlinedButton(
                                onClick = {
                                    val (ok, msg) = viewModel.unlockAdminAsCreator()
                                    if (!ok) {
                                        loginError = msg
                                    } else {
                                        loginError = null
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(Icons.Default.Stars, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Accès Direct Créateur (samsamai469@gmail.com)")
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Security,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "🔒 Protection stricte : Espace confidentiel et restreint au créateur de Sam Traduction. Les sessions sont chiffrées de bout en bout.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // --- CONSOLE PRINCIPALE D'ADMINISTRATION & SÉCURITÉ ---
                ScrollableTabRow(
                    selectedTabIndex = currentTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    edgePadding = 8.dp
                ) {
                    Tab(
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 },
                        text = { Text("Audience & Stats", maxLines = 1) },
                        icon = { Icon(Icons.Default.QueryStats, contentDescription = null) }
                    )
                    Tab(
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 },
                        text = { Text("Traductions", maxLines = 1) },
                        icon = { Icon(Icons.Default.Translate, contentDescription = null) }
                    )
                    Tab(
                        selected = currentTab == 2,
                        onClick = { currentTab = 2 },
                        text = { Text("Dictionnaire", maxLines = 1) },
                        icon = { Icon(Icons.Default.Book, contentDescription = null) }
                    )
                    Tab(
                        selected = currentTab == 3,
                        onClick = { currentTab = 3 },
                        text = { Text("Audit & Logs", maxLines = 1) },
                        icon = { Icon(Icons.Default.Policy, contentDescription = null) }
                    )
                    Tab(
                        selected = currentTab == 4,
                        onClick = { currentTab = 4 },
                        text = { Text("Sécurité & Tests", maxLines = 1) },
                        icon = { Icon(Icons.Default.Shield, contentDescription = null) }
                    )
                }

                if (actionMessage != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = actionMessage ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            IconButton(onClick = { actionMessage = null }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Fermer", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                when (currentTab) {
                    0 -> {
                        // --- ONGLET 0 : AUDIENCE & STATISTIQUES ANONYMES (EXCLUSIVITÉ CRÉATEUR) ---
                        AudienceStatsTab(viewModel = viewModel, onNavigateToTranslations = { currentTab = 1 })
                    }
                    1 -> {
                        // --- ONGLET 1 : FLUX DIRECT DES TRADUCTIONS DES UTILISATEURS ---
                        RealTranslationsFeedTab(viewModel = viewModel)
                    }
                    2 -> {
                        // --- ONGLET 2 : GESTION DU DICTIONNAIRE OFFICIEL ---
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier.weight(1f),
                                    placeholder = { Text("Rechercher dans le dictionnaire...") },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                FilledTonalButton(
                                    onClick = { showAddDialog = true },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Ajouter")
                                }
                            }

                            // Language Filter Chips
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("Toutes", "Dioula", "Mooré").forEach { lang ->
                                    FilterChip(
                                        selected = selectedLanguageFilter == lang,
                                        onClick = { selectedLanguageFilter = lang },
                                        label = { Text(if (lang == "Toutes") "Toutes les langues" else lang) }
                                    )
                                }
                            }

                            Text(
                                text = "${filteredList.size} entrées officielles",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )

                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(filteredList, key = { it.id }) { knowledge ->
                                    KnowledgeCard(
                                        knowledge = knowledge,
                                        onEdit = { editingKnowledge = knowledge },
                                        onDelete = {
                                            viewModel.deleteKnowledgeSecure(knowledge.id) { success, msg ->
                                                actionMessage = msg
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    3 -> {
                        // --- ONGLET 3 : JOURNAUX D'AUDIT EN DIRECT ---
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Journaux d'Audit & Événements de Sécurité (Zero Trust)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Traçabilité intégrale de toutes les authentifications, mutations du dictionnaire et tentatives d'intrusion.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            if (auditLogs.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Aucun événement d'audit enregistré.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(auditLogs, key = { it.id }) { log ->
                                        AuditLogCard(log = log)
                                    }
                                }
                            }
                        }
                    }
                    4 -> {
                        // --- ONGLET 4 : PENTEST & CONTRÔLE DES DÉFENSES EN PROFONDEUR ---
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            ElevatedCard(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Auto-Contrôle & Test de Pénétration",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Simule des attaques en boîte noire sur les 7 domaines critiques pour certifier la robustesse.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = { viewModel.runSecuritySelfAudit() },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Lancer l'Auto-Audit de Sécurité")
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            val report = securityReport
                            if (report != null) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (report.passedTests == report.totalTests) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(
                                                text = "Statut : ${report.overallStatus}",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (report.passedTests == report.totalTests) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                                            )
                                            Text(
                                                text = "${report.passedTests} / ${report.totalTests} tests validés avec succès",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (report.passedTests == report.totalTests) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }
                                        Icon(
                                            if (report.passedTests == report.totalTests) Icons.Default.CheckCircle else Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = if (report.passedTests == report.totalTests) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(report.results) { test ->
                                        SecurityTestCard(test = test)
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Appuyez sur le bouton ci-dessus pour lancer la batterie de tests d'intrusion et de conformité Zero Trust.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        KnowledgeEditDialog(
            onDismiss = { showAddDialog = false },
            onSave = {
                viewModel.saveKnowledgeSecure(it) { success, msg ->
                    actionMessage = msg
                }
                showAddDialog = false
            }
        )
    }

    if (editingKnowledge != null) {
        KnowledgeEditDialog(
            knowledge = editingKnowledge,
            onDismiss = { editingKnowledge = null },
            onSave = {
                viewModel.saveKnowledgeSecure(it) { success, msg ->
                    actionMessage = msg
                }
                editingKnowledge = null
            }
        )
    }
}

@Composable
fun AuditLogCard(log: SecurityAuditLogEntity) {
    val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.FRENCH).format(Date(log.timestamp))
    val (badgeColor, textColor) = when (log.severity) {
        "CRITICAL" -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        "WARNING" -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeColor
                ) {
                    Text(
                        text = "${log.severity} • ${log.eventType}",
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = log.details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (log.actorUserId.isNotBlank() || log.targetResource.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Acteur: ${log.actorUserId} ${if (log.targetResource.isNotBlank()) "→ Cible: ${log.targetResource}" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun SecurityTestCard(test: SecurityTestResult) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (test.passed) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                if (test.passed) Icons.Default.CheckCircle else Icons.Default.Cancel,
                contentDescription = null,
                tint = if (test.passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = test.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = test.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = test.details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun KnowledgeCard(
    knowledge: LinguisticKnowledge,
    onEdit: (LinguisticKnowledge) -> Unit,
    onDelete: (LinguisticKnowledge) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = knowledge.word,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (knowledge.language.equals("Dioula", ignoreCase = true)) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = knowledge.language,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (knowledge.language.equals("Dioula", ignoreCase = true)) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (knowledge.status == "validated") {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    text = "Validé (Officiel)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "= ${knowledge.translation}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row {
                    IconButton(onClick = { onEdit(knowledge) }) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Modifier",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = { onDelete(knowledge) }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Supprimer",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (knowledge.category.isNotEmpty() || knowledge.variants.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (knowledge.category.isNotEmpty()) {
                        SuggestionChip(
                            onClick = { },
                            label = { Text(knowledge.category, fontSize = 11.sp) },
                            modifier = Modifier.height(28.dp)
                        )
                    }
                    if (knowledge.variants.isNotEmpty()) {
                        Text(
                            text = "Var: ${knowledge.variants}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeEditDialog(
    knowledge: LinguisticKnowledge? = null,
    onDismiss: () -> Unit,
    onSave: (LinguisticKnowledge) -> Unit
) {
    var language by remember { mutableStateOf(knowledge?.language ?: "Dioula") }
    var word by remember { mutableStateOf(knowledge?.word ?: "") }
    var translation by remember { mutableStateOf(knowledge?.translation ?: "") }
    var category by remember { mutableStateOf(knowledge?.category ?: "") }
    var synonyms by remember { mutableStateOf(knowledge?.synonyms ?: "") }
    var variants by remember { mutableStateOf(knowledge?.variants ?: "") }
    var pronunciation by remember { mutableStateOf(knowledge?.pronunciation ?: "") }
    var examples by remember { mutableStateOf(knowledge?.examples ?: "") }
    var status by remember { mutableStateOf(knowledge?.status ?: "validated") }
    var confidenceLevel by remember { mutableIntStateOf(knowledge?.confidenceLevel ?: 100) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (knowledge == null) "Ajouter au Dictionnaire Officiel" else "Modifier l'Entrée Officielle",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text("Langue :", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Dioula", "Mooré").forEach { lang ->
                            FilterChip(
                                selected = language == lang,
                                onClick = { language = lang },
                                label = { Text(lang) }
                            )
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = word,
                        onValueChange = { word = it },
                        label = { Text("Mot / Expression en $language *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                item {
                    OutlinedTextField(
                        value = translation,
                        onValueChange = { translation = it },
                        label = { Text("Traduction en Français *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                item {
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Catégorie (Nombres, Salutations, etc.)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                item {
                    OutlinedTextField(
                        value = variants,
                        onValueChange = { variants = it },
                        label = { Text("Variantes orthographiques (séparées par virgule)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = synonyms,
                        onValueChange = { synonyms = it },
                        label = { Text("Synonymes (séparés par virgule)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = pronunciation,
                        onValueChange = { pronunciation = it },
                        label = { Text("Prononciation phonétique") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = examples,
                        onValueChange = { examples = it },
                        label = { Text("Exemples d'utilisation") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Statut de validation : ", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.width(8.dp))
                        FilterChip(
                            selected = status == "validated",
                            onClick = { status = if (status == "validated") "pending" else "validated" },
                            label = { Text(if (status == "validated") "Validé (Officiel)" else "En attente") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        knowledge?.copy(
                            word = word.trim(),
                            translation = translation.trim(),
                            language = language,
                            category = category.trim(),
                            synonyms = synonyms.trim(),
                            variants = variants.trim(),
                            pronunciation = pronunciation.trim(),
                            examples = examples.trim(),
                            status = status,
                            confidenceLevel = confidenceLevel,
                            dateModified = System.currentTimeMillis()
                        ) ?: LinguisticKnowledge(
                            word = word.trim(),
                            translation = translation.trim(),
                            language = language,
                            category = category.trim(),
                            synonyms = synonyms.trim(),
                            variants = variants.trim(),
                            pronunciation = pronunciation.trim(),
                            examples = examples.trim(),
                            status = status,
                            confidenceLevel = confidenceLevel
                        )
                    )
                },
                enabled = word.isNotBlank() && translation.isNotBlank()
            ) {
                Text("Enregistrer avec Privilège Admin")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}

@Composable
fun AudienceStatsTab(viewModel: SamAiViewModel, onNavigateToTranslations: () -> Unit = {}) {
    val stats by viewModel.audienceStatsFlow.collectAsState(
        initial = com.example.util.AnonymousAudienceManager.AudienceStats(
            anonymousUserId = "anon_local",
            firstLaunchFormatted = "Aujourd'hui",
            lastActiveFormatted = "En cours",
            deviceModel = "Appareil Android",
            androidVersion = "Android 14"
        )
    )
    var showTestTools by remember { mutableStateOf(false) }
    var testFeedback by remember { mutableStateOf<String?>(null) }
    var userSearchQuery by remember { mutableStateOf("") }
    var isSyncingCloud by remember { mutableStateOf(false) }

    // Synchronisation automatique au chargement de l'onglet
    LaunchedEffect(Unit) {
        viewModel.syncAudienceWithCloud()
    }

    val filteredUsers = remember(stats.realUsersList, userSearchQuery) {
        if (userSearchQuery.isBlank()) {
            stats.realUsersList
        } else {
            stats.realUsersList.filter {
                it.anonymousId.contains(userSearchQuery, ignoreCase = true) ||
                it.deviceModel.contains(userSearchQuery, ignoreCase = true) ||
                it.androidVersion.contains(userSearchQuery, ignoreCase = true)
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            // Header Creator Badge with SQLite Room Persistence Confirmation & Cloud Sync
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Synchronisation Cloud & Base Room",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "Admin Sam",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Statistiques Réelles d'Audience et Utilisateurs",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Chaque utilisateur externe qui installe et utilise l'application est synchronisé ici en direct. Cliquez ci-dessous pour actualiser les données de tous les téléphones distants.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            isSyncingCloud = true
                            viewModel.syncAudienceWithCloud { count ->
                                isSyncingCloud = false
                                testFeedback = "Cloud actualisé avec succès ($count appareils réels synchronisés)."
                            }
                        },
                        enabled = !isSyncingCloud,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isSyncingCloud) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Actualisation des données Cloud...")
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("🔄 Actualiser les Téléphones Externes")
                        }
                    }
                }
            }
        }

        if (testFeedback != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = testFeedback ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        IconButton(
                            onClick = { testFeedback = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Fermer", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // Section 1: KPI Utilisateurs
        item {
            Text(
                text = "👥 Statistiques Utilisateurs (Anonymes & Uniques)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AudienceMetricCard(
                    title = "Utilisateurs Totaux",
                    value = "${stats.totalUniqueUsers}",
                    subtitle = "Appareils réels enregistrés",
                    icon = Icons.Default.People,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.primary
                )
                AudienceMetricCard(
                    title = "Actifs Aujourd'hui",
                    value = "${stats.activeUsersToday}",
                    subtitle = "Utilisateurs actifs ces 24h",
                    icon = Icons.Default.CheckCircle,
                    modifier = Modifier.weight(1f),
                    color = Color(0xFF2E7D32)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AudienceMetricCard(
                    title = "Actifs Cette Semaine",
                    value = "${stats.activeUsersWeek}",
                    subtitle = "7 derniers jours",
                    icon = Icons.Default.DateRange,
                    modifier = Modifier.weight(1f),
                    color = Color(0xFF1565C0)
                )
                AudienceMetricCard(
                    title = "Actifs Ce Mois",
                    value = "${stats.activeUsersMonth}",
                    subtitle = "30 derniers jours",
                    icon = Icons.Default.CalendarMonth,
                    modifier = Modifier.weight(1f),
                    color = Color(0xFF6A1B9A)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AudienceMetricCard(
                    title = "Nouveaux Utilisateurs",
                    value = "${stats.newUsersToday}",
                    subtitle = "Première ouverture aujourd'hui",
                    icon = Icons.Default.PersonAdd,
                    modifier = Modifier.weight(1f),
                    color = Color(0xFFE65100)
                )
                AudienceMetricCard(
                    title = "Votre Appareil",
                    value = stats.anonymousUserId.take(12) + "...",
                    subtitle = stats.deviceModel.take(18),
                    icon = Icons.Default.Fingerprint,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        // Section 2: KPI Traductions
        item {
            Text(
                text = "🔤 Statistiques des Traductions",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AudienceMetricCard(
                    title = "Total Traductions",
                    value = "${stats.totalTranslations}",
                    subtitle = "Requêtes traitées",
                    icon = Icons.Default.Translate,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.primary
                )
                AudienceMetricCard(
                    title = "Traductions Vocales",
                    value = "${stats.voiceTranslations}",
                    subtitle = "🎙️ Dictée vocale",
                    icon = Icons.Default.Mic,
                    modifier = Modifier.weight(1f),
                    color = Color(0xFF00838F)
                )
                AudienceMetricCard(
                    title = "Traductions Écrites",
                    value = "${stats.textTranslations}",
                    subtitle = "⌨️ Saisie texte",
                    icon = Icons.Default.Keyboard,
                    modifier = Modifier.weight(1f),
                    color = Color(0xFF455A64)
                )
            }
        }

        // Section Preview des Phrases Traduites en Direct
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Translate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "💬 Phrases Récemment Traduites (${stats.recentTranslations.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        TextButton(onClick = onNavigateToTranslations) {
                            Text("Voir Tout ➔")
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (stats.recentTranslations.isEmpty()) {
                        Text(
                            text = "Aucune phrase enregistrée pour le moment.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    } else {
                        stats.recentTranslations.take(3).forEach { trans ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${trans.deviceModel} • ${trans.sourceLang} ➔ ${trans.targetLang}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = if (trans.isVoice) "🎙️ Vocal" else "⌨️ Écrit",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "« ${trans.originalText.ifBlank { "Texte (${trans.textLength} car.)" }} »",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "= ${trans.translatedText.ifBlank { "Traduction effectuée" }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        FilledTonalButton(
                            onClick = onNavigateToTranslations,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Consulter l'historique complet des phrases traduites")
                        }
                    }
                }
            }
        }

        // Section 3: LISTE DÉTAILLÉE DES VRAIS UTILISATEURS
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Devices,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "📱 Liste des Vrais Utilisateurs (${filteredUsers.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = userSearchQuery,
                        onValueChange = { userSearchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Filtrer par modèle ou ID...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (userSearchQuery.isNotBlank()) {
                                IconButton(onClick = { userSearchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = null)
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (filteredUsers.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Aucun utilisateur correspondant trouvé.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)
                        val now = System.currentTimeMillis()

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            filteredUsers.take(15).forEach { user ->
                                val isOnline = (now - user.lastActiveTimestamp) < (15 * 60 * 1000L) // 15 min
                                val isToday = (now - user.lastActiveTimestamp) < (24 * 3600 * 1000L) // 24h

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(10.dp)
                                                        .background(
                                                            color = when {
                                                                isOnline -> Color(0xFF2E7D32)
                                                                isToday -> Color(0xFF1565C0)
                                                                else -> Color(0xFF757575)
                                                            },
                                                            shape = RoundedCornerShape(5.dp)
                                                        )
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = user.deviceModel.ifBlank { "Appareil Inconnu" },
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = when {
                                                    isOnline -> Color(0xFF2E7D32).copy(alpha = 0.15f)
                                                    isToday -> Color(0xFF1565C0).copy(alpha = 0.15f)
                                                    else -> Color(0xFF757575).copy(alpha = 0.15f)
                                                }
                                            ) {
                                                Text(
                                                    text = when {
                                                        isOnline -> "En ligne"
                                                        isToday -> "Aujourd'hui"
                                                        else -> "Récemment"
                                                    },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = when {
                                                        isOnline -> Color(0xFF2E7D32)
                                                        isToday -> Color(0xFF1565C0)
                                                        else -> Color(0xFF757575)
                                                    },
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "ID: ${user.anonymousId}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline,
                                            fontSize = 11.sp
                                        )
                                        Text(
                                            text = "OS: ${user.androidVersion}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp
                                        )

                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Traductions: ${user.totalTranslationsCount} (🎙️ ${user.totalVoiceTranslationsCount} • ⌨️ ${user.totalTextTranslationsCount})",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "Actif: ${sdf.format(Date(user.lastActiveTimestamp))}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
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

        // Section 4: Graphique d'Évolution Temporelle
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.ShowChart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "📈 Évolution de l'Activité dans le Temps",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val timelineData = stats.dailyTranslationsTimeline
                    if (timelineData.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Effectuez des traductions pour voir le graphique d'évolution quotidien.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        val maxCount = timelineData.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            timelineData.takeLast(7).forEach { dayStat ->
                                val barHeightFraction = (dayStat.count.toFloat() / maxCount.toFloat()).coerceIn(0.15f, 1f)
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "${dayStat.count}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .width(22.dp)
                                            .fillMaxHeight(barHeightFraction * 0.75f)
                                            .background(
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
                                            )
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = dayStat.dayString.takeLast(5),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 5: Répartition des Langues
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🌍 Langues les Plus Utilisées",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val total = stats.totalTranslations.coerceAtLeast(1)
                    LanguageProgressRow(label = "Mooré (Burkina Faso)", count = stats.mooreCount, total = total, color = Color(0xFF2E7D32))
                    LanguageProgressRow(label = "Dioula (Burkina Faso)", count = stats.dioulaCount, total = total, color = Color(0xFFE65100))
                    LanguageProgressRow(label = "Haoussa", count = stats.hausaCount, total = total, color = Color(0xFF1565C0))
                    LanguageProgressRow(label = "Français", count = stats.frenchCount, total = total, color = Color(0xFF6A1B9A))
                    LanguageProgressRow(label = "Anglais", count = stats.englishCount, total = total, color = Color(0xFF00838F))
                    if (stats.otherCount > 0) {
                        LanguageProgressRow(label = "Autres", count = stats.otherCount, total = total, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }

        // Section 6: Informations Techniques & Système
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "📱 Informations Système & Persistance",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    DetailRow(label = "Identifiant Anonyme", value = stats.anonymousUserId)
                    DetailRow(label = "Modèle appareil", value = stats.deviceModel)
                    DetailRow(label = "Version Android", value = stats.androidVersion)
                    DetailRow(label = "Première installation", value = stats.firstLaunchFormatted)
                    DetailRow(label = "Dernière interaction", value = stats.lastActiveFormatted)
                    DetailRow(label = "Moteur de stockage", value = "SQLite Room Database (v12)")
                }
            }
        }

        // Section 7: Outils de Test Administrateur pour la Persistance
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Science,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "🧪 Outils de Test de Persistance",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(onClick = { showTestTools = !showTestTools }) {
                            Icon(
                                imageVector = if (showTestTools) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null
                            )
                        }
                    }

                    if (showTestTools) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Vous pouvez injecter des données de test simulées dans la base SQLite Room pour observer l'agrégation des statistiques et le graphique d'évolution. Les données resteront sauvegardées même si vous quittez et rouvrez l'application.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.simulateAudienceTestData(15, 4)
                                    testFeedback = "✅ 15 utilisateurs et traductions enregistrés dans SQLite Room !"
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Simuler 15 Utilisateurs", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    viewModel.resetAudienceData()
                                    testFeedback = "🔄 Statistiques réinitialisées (utilisateur local conservé)."
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Réinitialiser", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LanguageProgressRow(label: String, count: Int, total: Int, color: Color) {
    val percentage = if (total > 0) (count * 100) / total else 0
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(color, shape = RoundedCornerShape(3.dp))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = label, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                text = "$count ($percentage%)",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { if (total > 0) count.toFloat() / total.toFloat() else 0f },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
fun AudienceMetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    color: Color
) {
    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun LanguageUsageRow(label: String, count: Int, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(color, shape = RoundedCornerShape(3.dp))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = label, style = MaterialTheme.typography.bodySmall)
        }
        Text(
            text = "$count utilisation${if (count > 1) "s" else ""}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun RealTranslationsFeedTab(viewModel: SamAiViewModel) {
    val stats by viewModel.audienceStatsFlow.collectAsState(
        initial = com.example.util.AnonymousAudienceManager.AudienceStats(
            anonymousUserId = "anon_local",
            firstLaunchFormatted = "Aujourd'hui",
            lastActiveFormatted = "En cours",
            deviceModel = "Appareil Android",
            androidVersion = "Android 14"
        )
    )
    var searchQuery by remember { mutableStateOf("") }
    var selectedLangFilter by remember { mutableStateOf("Toutes") }
    var selectedTypeFilter by remember { mutableStateOf("Tous") }
    var isSyncing by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.syncAudienceWithCloud()
    }

    val filteredTranslations = remember(stats.recentTranslations, searchQuery, selectedLangFilter, selectedTypeFilter) {
        stats.recentTranslations.filter { trans ->
            val matchesQuery = if (searchQuery.isBlank()) true else {
                trans.originalText.contains(searchQuery, ignoreCase = true) ||
                trans.translatedText.contains(searchQuery, ignoreCase = true) ||
                trans.deviceModel.contains(searchQuery, ignoreCase = true) ||
                trans.userId.contains(searchQuery, ignoreCase = true) ||
                trans.sourceLang.contains(searchQuery, ignoreCase = true) ||
                trans.targetLang.contains(searchQuery, ignoreCase = true)
            }
            val matchesLang = when (selectedLangFilter) {
                "Toutes" -> true
                else -> trans.sourceLang.contains(selectedLangFilter, ignoreCase = true) ||
                        trans.targetLang.contains(selectedLangFilter, ignoreCase = true)
            }
            val matchesType = when (selectedTypeFilter) {
                "Vocales" -> trans.isVoice
                "Écrites" -> !trans.isVoice
                else -> true
            }
            matchesQuery && matchesLang && matchesType
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            // Header card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Translate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Flux Direct des Phrases Traduites",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "${stats.recentTranslations.size} enregistrements",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Visualisation Complète des Phrases Traduites",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Dès qu'un utilisateur traduit une phrase (vocale ou écrite) sur son téléphone, elle remonte ici instantanément avec le texte original, la traduction générée et l'appareil.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            isSyncing = true
                            viewModel.syncAudienceWithCloud { count ->
                                isSyncing = false
                                feedbackMessage = "Flux synchronisé avec succès ($count utilisateurs et leurs traductions reçus du Cloud)."
                            }
                        },
                        enabled = !isSyncing,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Synchronisation Cloud en cours...")
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("🔄 Actualiser le Flux Cloud")
                        }
                    }
                }
            }
        }

        if (feedbackMessage != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = feedbackMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        IconButton(
                            onClick = { feedbackMessage = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Fermer", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        item {
            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Rechercher dans les phrases ou par appareil...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = null)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
        }

        item {
            // Language & Type filters
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Toutes", "Mooré", "Dioula", "Haoussa", "Français").forEach { lang ->
                        FilterChip(
                            selected = selectedLangFilter == lang,
                            onClick = { selectedLangFilter = lang },
                            label = { Text(lang, fontSize = 11.sp) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Tous" to "Tous types", "Vocales" to "🎙️ Vocales", "Écrites" to "⌨️ Écrites").forEach { (typeKey, label) ->
                        FilterChip(
                            selected = selectedTypeFilter == typeKey,
                            onClick = { selectedTypeFilter = typeKey },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${filteredTranslations.size} traductions affichées",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (stats.recentTranslations.isNotEmpty()) {
                    Text(
                        text = "Triées par date (récentes en premier)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        if (filteredTranslations.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Translate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (searchQuery.isNotBlank() || selectedLangFilter != "Toutes" || selectedTypeFilter != "Tous") {
                                "Aucune traduction ne correspond aux filtres appliqués."
                            } else {
                                "Aucune phrase enregistrée pour le moment."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Toutes les phrases écrites ou prononcées sur n'importe quel téléphone apparaîtront ici.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                viewModel.simulateAudienceTestData(5, 3)
                                feedbackMessage = "Données de test générées avec des phrases réalistes !"
                            }
                        ) {
                            Text("🧪 Générer 5 Traductions de Test")
                        }
                    }
                }
            }
        } else {
            items(filteredTranslations, key = { it.id }) { trans ->
                TranslationEventCard(translation = trans)
            }
        }
    }
}

@Composable
fun TranslationEventCard(translation: TranslationAnalyticsEntity) {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.FRANCE)
    val dateFormatted = sdf.format(Date(translation.timestamp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Device model + Lang direction + Mode badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = translation.deviceModel.ifBlank { "Appareil Utilisateur" },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "${translation.sourceLang} ➔ ${translation.targetLang}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.sp
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (translation.isVoice) Color(0xFF00838F).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = if (translation.isVoice) "🎙️ Vocal" else "⌨️ Écrit",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (translation.isVoice) Color(0xFF00838F) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Body: Original Text & Translated Text
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = "🗣️ ",
                            fontSize = 13.sp
                        )
                        Column {
                            Text(
                                text = "Texte d'origine (${translation.sourceLang}) :",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                fontSize = 10.sp
                            )
                            Text(
                                text = translation.originalText.ifBlank { "(Longueur : ${translation.textLength} caractères)" },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = "✨ ",
                            fontSize = 13.sp
                        )
                        Column {
                            Text(
                                text = "Traduction obtenue (${translation.targetLang}) :",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = translation.translatedText.ifBlank { "Traduction effectuée" },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Footer: User ID + Timestamp + AI badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ID: ${translation.userId.take(14)}...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 10.sp
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (translation.isAiMode) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = "Mode IA",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = dateFormatted,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

