package com.example

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.example.ui.screens.OnboardingScreen
import com.example.ui.theme.MyApplicationTheme

import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.util.SmartNotificationManager
import com.example.data.repository.AiRepository
import com.example.data.repository.LinguisticRepository
import com.example.data.repository.ProgressRepository
import com.example.data.repository.TranslationRepository
import com.example.ui.MainScreen
import com.example.ui.SamAiViewModel
import com.example.ui.SamAiViewModelFactory
import com.example.util.TtsManager

class MainActivity : ComponentActivity() {

  private val ttsManager: TtsManager by lazy {
    TtsManager(applicationContext)
  }

  private val viewModel: SamAiViewModel by viewModels {
    try {
      val app = applicationContext as? SamAiApplication ?: (application as SamAiApplication)
      val chatDao = app.database.chatDao()
      val linguisticDao = app.database.linguisticDao()
      val progressDao = app.database.progressDao()
      val translationDao = app.database.translationDao()
      val auditDao = app.database.auditDao()
      val apiKeyManager = com.example.data.local.ApiKeyManager(app)
      val authSessionManager = com.example.data.local.AuthSessionManager(app)
      val analyticsDao = app.database.analyticsDao()
      val audienceManager = com.example.util.AnonymousAudienceManager(app, analyticsDao)
      
      // Synchronisation sans écriture redondante sur le stockage chiffré
      try {
          val prefs = getSharedPreferences("SamAiPrefs", Context.MODE_PRIVATE)
          val hasSeenClassic = prefs.getBoolean("has_seen_onboarding", false)
          val hasSeenSession = authSessionManager.isOnboardingCompleted()
          if (hasSeenClassic && !hasSeenSession) {
              authSessionManager.setOnboardingCompleted(true)
          } else if (!hasSeenClassic && hasSeenSession) {
              prefs.edit().putBoolean("has_seen_onboarding", true).apply()
          }
      } catch (_: Throwable) {}
      
      SamAiViewModelFactory(
        AiRepository(chatDao, linguisticDao, apiKeyManager, auditDao),
        LinguisticRepository(linguisticDao, auditDao),
        ProgressRepository(progressDao),
        TranslationRepository(translationDao, linguisticDao, auditDao),
        auditDao,
        authSessionManager,
        audienceManager
      )
    } catch (e: Throwable) {
      Log.e("MainActivity", "Erreur initialisation dépendances normales, utilisation du repli sécurisé", e)
      val fallbackDb = try {
        androidx.room.Room.inMemoryDatabaseBuilder(applicationContext, com.example.data.local.AppDatabase::class.java)
          .allowMainThreadQueries()
          .build()
      } catch (_: Throwable) {
        val app = applicationContext as SamAiApplication
        app.database
      }
      val apiKeyManager = com.example.data.local.ApiKeyManager(this)
      val authSessionManager = com.example.data.local.AuthSessionManager(this)
      val audienceManager = com.example.util.AnonymousAudienceManager(this, fallbackDb.analyticsDao())

      SamAiViewModelFactory(
        AiRepository(fallbackDb.chatDao(), fallbackDb.linguisticDao(), apiKeyManager, fallbackDb.auditDao()),
        LinguisticRepository(fallbackDb.linguisticDao(), fallbackDb.auditDao()),
        ProgressRepository(fallbackDb.progressDao()),
        TranslationRepository(fallbackDb.translationDao(), fallbackDb.linguisticDao(), fallbackDb.auditDao()),
        fallbackDb.auditDao(),
        authSessionManager,
        audienceManager
      )
    }
  }

  private val notificationPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    try {
      SmartNotificationManager.setFirstPromptShown(this, true)
      SmartNotificationManager.setNotificationsEnabled(this, isGranted)
    } catch (_: Throwable) {}
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Initialisation immédiate du contexte Firebase Auth
    try {
      com.example.data.remote.FirebaseAuthService.initContext(this)
    } catch (_: Throwable) {}

    // 1. Initialisation système EdgeToEdge sécurisée
    try {
      enableEdgeToEdge()
    } catch (_: Throwable) {}
    
    // 2. Gestion sécurisée des permissions système
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (!SmartNotificationManager.isFirstPromptShown(this)) {
          val currentPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
          if (currentPermission != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
          } else {
            SmartNotificationManager.setFirstPromptShown(this, true)
          }
        }
      } else {
        if (!SmartNotificationManager.isFirstPromptShown(this)) {
          SmartNotificationManager.setFirstPromptShown(this, true)
        }
      }
    } catch (_: Throwable) {}

    // 3. Gestion sécurisée des notifications et services en tâche de fond
    try {
      if (SmartNotificationManager.isNotificationsEnabled(this)) {
        SmartNotificationManager.scheduleSmartNotifications(this)
      }
    } catch (_: Throwable) {}

    // 4. Pare-feu d'interface utilisateur : setContent est TOUJOURS appelé
    var uiLoadedSuccessfully = false
    try {
      setContent {
        val themePref by viewModel.themePreference.collectAsState()
        val themeStylePref by viewModel.themeStylePreference.collectAsState()
        val isDarkTheme = when (themePref) {
            "light" -> false
            "dark" -> true
            else -> androidx.compose.foundation.isSystemInDarkTheme()
        }
        MyApplicationTheme(darkTheme = isDarkTheme, themeStyle = themeStylePref) {
          val hasCompletedOnboarding by viewModel.isOnboardingCompleted.collectAsState()
          
          if (!hasCompletedOnboarding) {
              OnboardingScreen(
                  onFinish = {
                      try {
                        val prefs = getSharedPreferences("SamAiPrefs", Context.MODE_PRIVATE)
                        prefs.edit().putBoolean("has_seen_onboarding", true).apply()
                      } catch (_: Throwable) {}
                      viewModel.completeOnboarding()
                  }
              )
          } else {
              MainScreen(viewModel, ttsManager)
          }
        }
      }
      uiLoadedSuccessfully = true
    } catch (t: Throwable) {
      Log.e("MainActivity", "Erreur affichage UI principale rattrapée par le pare-feu", t)
    }

    // 5. Repli garanti si le rendu standard échoue pour éviter le crash forcé
    if (!uiLoadedSuccessfully) {
      try {
        setContent {
          MaterialTheme {
            Surface(
              modifier = Modifier.fillMaxSize(),
              color = MaterialTheme.colorScheme.background
            ) {
              Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center
              ) {
                Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                  Text(
                    text = "Sam AI",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                  )
                  Text(
                    text = "L'application s'est initialisée en mode de sécurité.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                  )
                  Button(
                    onClick = {
                      recreate()
                    }
                  ) {
                    Text("Recharger l'application")
                  }
                }
              }
            }
          }
        }
      } catch (_: Throwable) {}
    }

    // 6. Gestion des intents externes (OAuth / Liens magiques)
    try {
      handleIntent(intent)
    } catch (_: Throwable) {}
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    try {
      handleIntent(intent)
    } catch (_: Throwable) {}
  }

  private fun handleIntent(intent: Intent?) {
      if (intent == null) return
      try {
          val data: Uri? = intent.data
          if (data != null) {
              val uriString = data.toString()

              // 1. Détection automatique du lien de connexion sans mot de passe Firebase (Email Link)
              if (com.example.data.remote.FirebaseAuthService.isSignInWithEmailLink(uriString) ||
                  (uriString.contains("apiKey=") && uriString.contains("oobCode=")) ||
                  uriString.contains("sam-traduction.firebaseapp.com") ||
                  uriString.contains("sam-traduction.web.app")
              ) {
                  viewModel.handleEmailLinkAuth(this, uriString) { success, msg ->
                      try {
                          if (success) {
                              Toast.makeText(this, msg ?: "Connexion réussie !", Toast.LENGTH_SHORT).show()
                          } else if (msg != null) {
                              Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                          }
                      } catch (_: Throwable) {}
                  }
                  return
              }

              // 2. Détection du retour OAuth Google
              if (uriString.contains("/auth/callback") ||
                  uriString.contains("access_token=") ||
                  uriString.contains("code=")
              ) {
                  viewModel.handleOAuthRedirect(uriString) { success, errorMsg ->
                      try {
                          if (success) {
                              Toast.makeText(this, "Connexion Google réussie !", Toast.LENGTH_SHORT).show()
                          } else if (errorMsg != null) {
                              Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
                          }
                      } catch (_: Throwable) {}
                  }
              }
          }
      } catch (e: Throwable) {
          Log.e("MainActivity", "Erreur lors du traitement de l'intent", e)
      }
  }

  override fun onResume() {
      super.onResume()
      try {
          SmartNotificationManager.recordUserActivity(this)
      } catch (_: Throwable) {}
  }

  override fun onDestroy() {
      super.onDestroy()
      try {
          ttsManager.shutdown()
      } catch (_: Throwable) {}
  }
}
