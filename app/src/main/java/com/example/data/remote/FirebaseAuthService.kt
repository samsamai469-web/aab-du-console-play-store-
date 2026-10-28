package com.example.data.remote

import android.app.Activity
import android.content.Context
import android.util.Log
import com.example.data.local.AuthProvider
import com.example.data.local.AuthSessionManager
import com.example.data.local.UserProfile
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeUnit

/**
 * Service gérant l'authentification Firebase Auth & la persistance Firestore.
 */
object FirebaseAuthService {

    private const val TAG = "FirebaseAuthService"
    private const val USERS_COLLECTION = "users"
    const val ADMIN_EMAIL = "samsamai469@gmail.com"

    @Volatile
    private var appContext: Context? = null

    fun getExplicitFirebaseOptions(): com.google.firebase.FirebaseOptions {
        return com.google.firebase.FirebaseOptions.Builder()
            .setProjectId("sam-traduction")
            .setApplicationId("1:438563104660:android:adc0f63c861d666ba45181")
            .setApiKey("AIzaSyCM2nqPMvLMYxCjHFlLiVXArBbxv1RtPNk")
            .setStorageBucket("sam-traduction.firebasestorage.app")
            .setGcmSenderId("438563104660")
            .build()
    }

    fun initContext(context: Context) {
        val targetCtx = context.applicationContext ?: context
        appContext = targetCtx
        ensureInitialized(targetCtx)
    }

    fun ensureInitialized(context: Context): Boolean {
        val targetCtx = context.applicationContext ?: context
        appContext = targetCtx
        synchronized(this) {
            try {
                val apps = FirebaseApp.getApps(targetCtx)
                if (apps.isEmpty()) {
                    val defaultApp = try {
                        FirebaseApp.initializeApp(targetCtx)
                    } catch (_: Throwable) {
                        null
                    }
                    if (defaultApp == null || FirebaseApp.getApps(targetCtx).isEmpty()) {
                        FirebaseApp.initializeApp(targetCtx, getExplicitFirebaseOptions())
                    }
                } else {
                    // Vérifier si l'instance [DEFAULT] possède les clés valides
                    try {
                        val currentApp = FirebaseApp.getInstance()
                        if (currentApp.options.apiKey.isNullOrBlank() || currentApp.options.applicationId.isNullOrBlank()) {
                            currentApp.delete()
                            FirebaseApp.initializeApp(targetCtx, getExplicitFirebaseOptions())
                        }
                    } catch (_: Exception) {
                        try {
                            FirebaseApp.initializeApp(targetCtx, getExplicitFirebaseOptions())
                        } catch (_: Exception) {}
                    }
                }
                FirebaseAuth.getInstance()
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Tentative de récupération Firebase: ${e.message}")
                return try {
                    FirebaseApp.initializeApp(targetCtx, getExplicitFirebaseOptions())
                    FirebaseAuth.getInstance()
                    true
                } catch (e2: Exception) {
                    Log.e(TAG, "Échec définitif init Firebase: ${e2.message}", e2)
                    false
                }
            }
        }
    }

    private val auth: FirebaseAuth?
        get() {
            try {
                return FirebaseAuth.getInstance()
            } catch (_: Exception) {}

            appContext?.let { ctx ->
                ensureInitialized(ctx)
                try {
                    return FirebaseAuth.getInstance()
                } catch (e: Exception) {
                    Log.e(TAG, "FirebaseAuth non disponible: ${e.message}")
                }
            }
            return null
        }

    private val firestore: FirebaseFirestore?
        get() {
            try {
                return FirebaseFirestore.getInstance()
            } catch (_: Exception) {}

            appContext?.let { ctx ->
                ensureInitialized(ctx)
                try {
                    return FirebaseFirestore.getInstance()
                } catch (e: Exception) {
                    Log.e(TAG, "FirebaseFirestore non disponible: ${e.message}")
                }
            }
            return null
        }

    val currentFirebaseUser: FirebaseUser?
        get() = auth?.currentUser

    fun isFirebaseReady(context: Context): Boolean {
        return ensureInitialized(context)
    }

    /**
     * Connecte l'utilisateur à Firebase Auth via le Google ID Token
     * et enregistre / met à jour son profil dans Firestore.
     */
    suspend fun signInWithGoogleIdToken(
        idToken: String,
        fallbackEmail: String? = null,
        fallbackName: String? = null,
        fallbackPhotoUrl: String? = null
    ): Result<UserProfile> {
        return try {
            val firebaseAuth = auth
                ?: return Result.failure(Exception("Service Firebase Auth indisponible. Vérifiez la configuration."))

            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            val user = authResult.user
                ?: return Result.failure(Exception("Impossible de récupérer les informations de l'utilisateur Google."))

            val uid = user.uid
            val email = user.email ?: fallbackEmail ?: "utilisateur@gmail.com"
            val displayName = user.displayName ?: fallbackName ?: email.substringBefore("@")
            val photoUrl = user.photoUrl?.toString() ?: fallbackPhotoUrl

            // Enregistrer ou mettre à jour dans Firestore en arrière-plan immédiat (non bloquant)
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                syncUserToFirestore(
                    uid = uid,
                    name = displayName,
                    email = email,
                    photoUrl = photoUrl
                )
            }

            val profile = UserProfile(
                isLoggedIn = true,
                userId = uid,
                email = email,
                name = displayName,
                photoUrl = photoUrl,
                provider = AuthProvider.GOOGLE,
                role = if (email.equals(ADMIN_EMAIL, ignoreCase = true)) "admin" else "user"
            )

            Result.success(profile)
        } catch (e: com.google.firebase.FirebaseNetworkException) {
            Result.failure(Exception("Aucune connexion Internet disponible. Veuillez vérifier votre réseau."))
        } catch (e: com.google.firebase.auth.FirebaseAuthInvalidUserException) {
            Result.failure(Exception("Ce compte Google a été désactivé ou n'existe pas."))
        } catch (e: Exception) {
            Log.e(TAG, "Erreur signInWithGoogleIdToken: ${e.message}", e)
            Result.failure(Exception(e.localizedMessage ?: "Erreur de connexion avec Google."))
        }
    }

    /**
     * Vérifie en temps réel auprès des serveurs Google et du DNS la validité du domaine et des serveurs mail Google
     */
    private suspend fun verifyGoogleMailServerRealtime(domain: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (domain == "gmail.com" || domain == "googlemail.com") {
                    val addresses = java.net.InetAddress.getAllByName("gmail-smtp-in.l.google.com")
                    addresses.isNotEmpty()
                } else {
                    val addresses = java.net.InetAddress.getAllByName(domain)
                    addresses.isNotEmpty()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Échec de résolution DNS du serveur mail $domain: ${e.message}")
                false
            }
        }
    }

    /**
     * Vérification stricte selon les règles officielles de Google pour les adresses Gmail et emails
     * Retourne null si valide, ou le message d'erreur précis si Google refuse l'adresse.
     */
    fun validateEmailWithGoogleRules(email: String): String? {
        val clean = email.trim().lowercase()
        if (clean.isBlank()) {
            return "Veuillez saisir votre adresse email."
        }

        // Vérification de la présence du séparateur @
        val parts = clean.split("@")
        if (parts.size != 2) {
            return "Format invalide : l'adresse doit contenir un seul symbole '@'."
        }

        val username = parts[0]
        val domain = parts[1]

        // Détection des erreurs de frappe courantes sur le nom de domaine Google
        val gmailTypos = listOf(
            "gmai.com", "gamil.com", "gmial.com", "gmaill.com", "gmeil.com",
            "gmail.con", "gmail.co", "gmai.fr", "gamil.fr", "gmail.cm", "googlemail.con",
            "gmaill.fr", "gmaill.com", "gmai.ca", "gmail.be", "gmail.ch"
        )
        if (domain in gmailTypos) {
            return "Google refuse cette adresse : le domaine officiel exact doit être '@gmail.com' (faute de frappe détectée : '$domain')."
        }

        // Vérification du domaine général
        if (!domain.contains(".") || domain.startsWith(".") || domain.endsWith(".")) {
            return "Google refuse cette adresse : le nom de domaine '$domain' est incomplet ou invalide."
        }
        val tld = domain.substringAfterLast(".")
        if (tld.length < 2) {
            return "Google refuse cette adresse : l'extension du domaine (.$tld) est trop courte."
        }

        // Règles strictes spécifiques aux comptes Google Gmail (@gmail.com et @googlemail.com)
        if (domain == "gmail.com" || domain == "googlemail.com") {
            if (username.length < 6) {
                return "Google refuse cette adresse : un compte Gmail doit comporter au moins 6 caractères avant '@gmail.com' (le préfixe '$username' est trop court)."
            }
            if (username.length > 30) {
                return "Google refuse cette adresse : un compte Gmail ne peut pas dépasser 30 caractères avant '@gmail.com'."
            }
            if (username.startsWith(".") || username.endsWith(".")) {
                return "Google refuse cette adresse : un compte Gmail ne peut ni commencer ni se terminer par un point ('.')."
            }
            if (username.contains("..")) {
                return "Google refuse cette adresse : deux points consécutifs ('..') sont strictement interdits par Google."
            }

            // Google n'autorise que les lettres a-z, les chiffres 0-9 et les points simples
            val validGmailPattern = Regex("^[a-z0-9]+(\\.[a-z0-9]+)*$")
            if (!validGmailPattern.matches(username)) {
                return "Google refuse cette adresse : les adresses Gmail n'acceptent que des lettres (a-z), chiffres (0-9) et points. Tout symbole spécial (-, _, +, %, !, #) est refusé par Google."
            }
        } else {
            // Règles standards pour les autres fournisseurs
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(clean).matches()) {
                return "Google refuse cette adresse : le format de l'email est non conforme."
            }
        }

        return null
    }

    /**
     * Inscription par Email et Mot de passe avec Firebase Auth & enregistrement Firestore
     * Google Firebase valide la syntaxe, la sécurité du mot de passe et l'unicité de l'adresse.
     */
    suspend fun registerWithEmail(
        email: String,
        password: String,
        displayName: String = "",
        context: Context? = null
    ): Result<UserProfile> {
        val cleanEmail = email.trim().lowercase()
        val validationError = validateEmailWithGoogleRules(cleanEmail)
        if (validationError != null) {
            return Result.failure(Exception(validationError))
        }

        context?.let { initContext(it) }
        val firebaseAuth = auth ?: run {
            context?.let { ensureInitialized(it) }
            auth
        } ?: return Result.failure(Exception("Service Firebase Auth indisponible."))
        val cleanName = displayName.trim().ifBlank { cleanEmail.substringBefore("@") }

        return withContext(Dispatchers.IO) {
            try {
                val authResult = withTimeout(9000L) {
                    firebaseAuth.createUserWithEmailAndPassword(cleanEmail, password).await()
                }
                val user = authResult.user
                    ?: return@withContext Result.failure(Exception("Échec de la création de l'utilisateur Firebase."))

                val uid = user.uid

                // Traitements asynchrones d'arrière-plan (ne bloquent pas l'interface)
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                            .setDisplayName(cleanName)
                            .build()
                        user.updateProfile(profileUpdates).await()
                    } catch (e: Exception) {
                        Log.w(TAG, "Impossible de définir le nom d'affichage: ${e.message}")
                    }

                    try {
                        firebaseAuth.setLanguageCode("fr")
                        user.sendEmailVerification()
                        Log.d(TAG, "Email de vérification officiel Google envoyé à $cleanEmail")
                    } catch (e: Exception) {
                        Log.w(TAG, "Envoi email de vérification: ${e.message}")
                    }

                    try {
                        withTimeout(5000L) {
                            syncUserToFirestore(
                                uid = uid,
                                name = cleanName,
                                email = cleanEmail,
                                photoUrl = null,
                                phoneNumber = null
                            )
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Sync Firestore en tâche de fond: ${e.message}")
                    }
                }

                val profile = UserProfile(
                    isLoggedIn = true,
                    userId = uid,
                    email = cleanEmail,
                    name = cleanName,
                    role = if (cleanEmail.equals(ADMIN_EMAIL, ignoreCase = true)) "admin" else "user",
                    provider = AuthProvider.EMAIL,
                    isCloudSynced = true
                )

                Log.d(TAG, "Inscription réussie avec Google Firebase: $cleanEmail ($uid)")
                Result.success(profile)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Délai d'inscription dépassé: $cleanEmail")
                Result.failure(Exception("Le serveur Firebase met trop de temps à répondre. Vérifiez votre connexion Internet."))
            } catch (e: Exception) {
                Log.e(TAG, "Erreur registerWithEmail: ${e.message}", e)
                Result.failure(Exception(translateFirebaseError(e)))
            }
        }
    }

    /**
     * Connexion par Email et Mot de passe avec Firebase Auth
     */
    suspend fun loginWithEmail(
        email: String,
        password: String,
        context: Context? = null
    ): Result<UserProfile> {
        val cleanEmail = email.trim().lowercase()
        val validationError = validateEmailWithGoogleRules(cleanEmail)
        if (validationError != null) {
            return Result.failure(Exception(validationError))
        }

        context?.let { initContext(it) }
        val firebaseAuth = auth ?: run {
            context?.let { ensureInitialized(it) }
            auth
        } ?: return Result.failure(Exception("Service Firebase Auth indisponible."))

        return withContext(Dispatchers.IO) {
            try {
                val authResult = withTimeout(9000L) {
                    firebaseAuth.signInWithEmailAndPassword(cleanEmail, password).await()
                }
                val user = authResult.user
                    ?: return@withContext Result.failure(Exception("Échec de l'authentification Firebase."))

                val isSuperAdmin = cleanEmail.equals(ADMIN_EMAIL, ignoreCase = true)
                val uid = user.uid
                val name = user.displayName ?: cleanEmail.substringBefore("@")

                // Synchronisation de la dernière connexion dans Firestore en arrière-plan (non bloquant)
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        withTimeout(5000L) {
                            syncUserToFirestore(
                                uid = uid,
                                name = name,
                                email = cleanEmail,
                                photoUrl = user.photoUrl?.toString(),
                                phoneNumber = user.phoneNumber
                            )
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Sync Firestore dernière connexion en arrière-plan: ${e.message}")
                    }
                }

                val profile = UserProfile(
                    isLoggedIn = true,
                    userId = uid,
                    email = cleanEmail,
                    name = name,
                    photoUrl = user.photoUrl?.toString(),
                    role = if (isSuperAdmin) "admin" else "user",
                    provider = AuthProvider.EMAIL,
                    isCloudSynced = true
                )
                Log.d(TAG, "Connexion Email validée dans Firebase: $cleanEmail ($uid)")
                Result.success(profile)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Délai de connexion dépassé pour: $cleanEmail")
                Result.failure(Exception("Délai de connexion dépassé (9 sec). Vérifiez votre connexion Internet."))
            } catch (e: Exception) {
                Log.e(TAG, "Erreur loginWithEmail: ${e.message}", e)
                Result.failure(Exception(translateFirebaseError(e)))
            }
        }
    }

    /**
     * Renvoie un email de vérification d'adresse Google
     */
    suspend fun resendVerificationEmail(
        email: String,
        password: String,
        context: Context? = null
    ): Result<Unit> {
        context?.let { initContext(it) }
        val firebaseAuth = auth ?: run {
            context?.let { ensureInitialized(it) }
            auth
        } ?: return Result.failure(Exception("Service Firebase Auth indisponible."))
        val cleanEmail = email.trim().lowercase()

        return withContext(Dispatchers.IO) {
            try {
                val authResult = withTimeout(8000L) {
                    firebaseAuth.signInWithEmailAndPassword(cleanEmail, password).await()
                }
                val user = authResult.user
                    ?: return@withContext Result.failure(Exception("Utilisateur introuvable."))
                withTimeout(8000L) {
                    user.sendEmailVerification().await()
                }
                firebaseAuth.signOut()
                Result.success(Unit)
            } catch (e: TimeoutCancellationException) {
                Result.failure(Exception("Délai d'envoi dépassé. Vérifiez votre connexion Internet."))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Envoie un email de réinitialisation de mot de passe via Firebase
     */
    suspend fun sendPasswordResetEmail(
        email: String,
        context: Context? = null
    ): Result<Unit> {
        context?.let { initContext(it) }
        val firebaseAuth = auth ?: run {
            context?.let { ensureInitialized(it) }
            auth
        } ?: return Result.failure(Exception("Service Firebase Auth indisponible."))
        val cleanEmail = email.trim().lowercase()

        return withContext(Dispatchers.IO) {
            try {
                firebaseAuth.setLanguageCode("fr")
                withTimeout(8000L) {
                    firebaseAuth.sendPasswordResetEmail(cleanEmail).await()
                }
                Log.d(TAG, "Email officiel de réinitialisation en français envoyé à $cleanEmail")
                Result.success(Unit)
            } catch (e: TimeoutCancellationException) {
                Result.failure(Exception("Délai d'envoi dépassé. Vérifiez votre connexion Internet."))
            } catch (e: Exception) {
                Log.e(TAG, "Erreur sendPasswordResetEmail: ${e.message}", e)
                Result.failure(Exception(translateFirebaseError(e)))
            }
        }
    }

    /**
     * Traduction conviviale en français des exceptions et codes d'erreur Google Firebase
     */
    fun translateFirebaseError(e: Throwable?): String {
        if (e == null) return "Une erreur inattendue est survenue."
        val msg = e.message ?: ""
        return when {
            e is com.google.firebase.auth.FirebaseAuthWeakPasswordException ||
                    msg.contains("weak-password", ignoreCase = true) ||
                    msg.contains("Password should be at least", ignoreCase = true) ->
                "Le mot de passe doit comporter au moins 6 caractères."

            e is com.google.firebase.auth.FirebaseAuthUserCollisionException ||
                    msg.contains("email-already-in-use", ignoreCase = true) ||
                    msg.contains("already in use", ignoreCase = true) ->
                "Un compte existe déjà avec cette adresse e-mail. Veuillez utiliser l'onglet « Se connecter »."

            e is com.google.firebase.auth.FirebaseAuthInvalidUserException ||
                    msg.contains("user-not-found", ignoreCase = true) ||
                    msg.contains("no user record", ignoreCase = true) ->
                "Aucun compte trouvé avec cet e-mail. Veuillez d'abord créer votre compte dans l'onglet « Créer un compte »."

            e is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException ||
                    msg.contains("wrong-password", ignoreCase = true) ||
                    msg.contains("invalid-credential", ignoreCase = true) ||
                    msg.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true) ->
                "Mot de passe incorrect ou adresse email invalide."

            e is com.google.firebase.FirebaseNetworkException ||
                    msg.contains("network", ignoreCase = true) ||
                    msg.contains("timeout", ignoreCase = true) ->
                "Connexion Internet instable ou indisponible. Veuillez vérifier votre réseau."

            msg.contains("operation is not allowed", ignoreCase = true) ||
                    msg.contains("OPERATION_NOT_ALLOWED", ignoreCase = true) ->
                "L'inscription par e-mail/mot de passe n'est pas encore activée dans la console Firebase (Authentication > Sign-in method > Adresse e-mail/Mot de passe > Activer). Vous pouvez vous connecter immédiatement avec le bouton « Continuer avec Google » !"

            msg.contains("too-many-requests", ignoreCase = true) ||
                    msg.contains("blocked", ignoreCase = true) ->
                "Trop de tentatives infructueuses. Veuillez patienter un instant avant de réessayer."

            else -> e.localizedMessage ?: "Erreur d'authentification. Veuillez réessayer."
        }
    }

    /**
     * Envoie un lien de connexion sans mot de passe par e-mail (Firebase Email Link / Passwordless Sign-In)
     */
    suspend fun sendSignInLinkToEmail(
        context: Context,
        email: String,
        displayName: String? = null
    ): Result<String> {
        val cleanEmail = email.trim().lowercase()
        val validationError = validateEmailWithGoogleRules(cleanEmail)
        if (validationError != null) {
            return Result.failure(Exception(validationError))
        }

        val firebaseAuth = auth
            ?: return Result.failure(Exception("Service Firebase Auth indisponible."))

        return withContext(Dispatchers.IO) {
            try {
                val actionCodeSettings = com.google.firebase.auth.ActionCodeSettings.newBuilder()
                    .setUrl("https://sam-traduction.firebaseapp.com")
                    .setHandleCodeInApp(true)
                    .setAndroidPackageName("com.anvil.samai", true, "12")
                    .build()

                firebaseAuth.setLanguageCode("fr")
                withTimeout(8000L) {
                    firebaseAuth.sendSignInLinkToEmail(cleanEmail, actionCodeSettings).await()
                }

                // Sauvegarde locale de l'email et du nom pour vérification lors de l'ouverture du lien
                val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
                prefs.edit()
                    .putString("saved_email", cleanEmail)
                    .putString("saved_name", displayName?.trim())
                    .apply()

                Log.d(TAG, "Lien de connexion magique Firebase envoyé à $cleanEmail")
                Result.success("Un e-mail de connexion sécurisé a été envoyé à $cleanEmail. Ouvrez votre boîte de réception et cliquez sur le lien pour vous connecter automatiquement !")
            } catch (e: TimeoutCancellationException) {
                Result.failure(Exception("Délai d'envoi dépassé. Vérifiez votre connexion Internet."))
            } catch (e: Exception) {
                Log.e(TAG, "Erreur sendSignInLinkToEmail: ${e.message}", e)
                Result.failure(Exception(translateFirebaseError(e)))
            }
        }
    }

    /**
     * Vérifie si un lien reçu est un lien d'authentification Firebase Email Link
     */
    fun isSignInWithEmailLink(emailLink: String): Boolean {
        return auth?.isSignInWithEmailLink(emailLink) == true
    }

    /**
     * Valide le lien reçu par e-mail et connecte l'utilisateur
     */
    suspend fun signInWithEmailLink(
        context: Context,
        emailLink: String,
        fallbackEmail: String? = null
    ): Result<UserProfile> {
        val firebaseAuth = auth
            ?: return Result.failure(Exception("Service Firebase Auth indisponible."))

        val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        val savedEmail = fallbackEmail?.trim()?.lowercase() ?: prefs.getString("saved_email", null)
        val savedName = prefs.getString("saved_name", null)

        if (savedEmail.isNullOrBlank()) {
            return Result.failure(Exception("Adresse email introuvable pour valider la session. Veuillez réécrire votre adresse email."))
        }

        return withContext(Dispatchers.IO) {
            try {
                val authResult = withTimeout(9000L) {
                    firebaseAuth.signInWithEmailLink(savedEmail, emailLink).await()
                }
                val user = authResult.user
                    ?: return@withContext Result.failure(Exception("Échec de la validation du lien Firebase."))

                val uid = user.uid
                val displayName = user.displayName ?: savedName ?: savedEmail.substringBefore("@")

                if (!savedName.isNullOrBlank() && user.displayName.isNullOrBlank()) {
                    try {
                        val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                            .setDisplayName(savedName)
                            .build()
                        user.updateProfile(profileUpdates).await()
                    } catch (_: Exception) {}
                }

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        withTimeout(5000L) {
                            syncUserToFirestore(
                                uid = uid,
                                name = displayName,
                                email = savedEmail,
                                photoUrl = user.photoUrl?.toString(),
                                phoneNumber = user.phoneNumber
                            )
                        }
                    } catch (_: Exception) {}
                }

                val isSuperAdmin = savedEmail.equals(ADMIN_EMAIL, ignoreCase = true)
                val profile = UserProfile(
                    isLoggedIn = true,
                    userId = uid,
                    email = savedEmail,
                    name = displayName,
                    photoUrl = user.photoUrl?.toString(),
                    role = if (isSuperAdmin) "admin" else "user",
                    provider = AuthProvider.EMAIL,
                    isCloudSynced = true
                )

                Log.d(TAG, "Connexion par Email Link réussie: $savedEmail ($uid)")
                Result.success(profile)
            } catch (e: TimeoutCancellationException) {
                Result.failure(Exception("Délai de validation du lien dépassé. Vérifiez votre connexion."))
            } catch (e: Exception) {
                Log.e(TAG, "Erreur signInWithEmailLink: ${e.message}", e)
                Result.failure(Exception(e.localizedMessage ?: "Erreur lors de la validation du lien de connexion."))
            }
        }
    }

    /**
     * Envoie un code SMS de vérification via Firebase Phone Auth
     */
    fun sendPhoneOtp(
        activity: Activity,
        phoneNumber: String,
        userName: String = "",
        resendToken: PhoneAuthProvider.ForceResendingToken? = null,
        onCodeSent: (verificationId: String, token: PhoneAuthProvider.ForceResendingToken) -> Unit,
        onAutoVerified: (UserProfile) -> Unit,
        onError: (String) -> Unit
    ) {
        val firebaseAuth = auth
        if (firebaseAuth == null) {
            onError("Firebase Auth n'est pas initialisé.")
            return
        }

        // Nettoyage et formatage du numéro au format international E.164
        val cleanDigits = phoneNumber.trim().replace(Regex("[^+\\d]"), "")
        val formattedPhone = when {
            cleanDigits.startsWith("+") -> cleanDigits
            cleanDigits.startsWith("00") -> "+" + cleanDigits.substring(2)
            cleanDigits.startsWith("226") && cleanDigits.length >= 11 -> "+$cleanDigits"
            cleanDigits.length == 8 -> "+226$cleanDigits" // Burkina Faso par défaut si 8 chiffres
            else -> if (cleanDigits.startsWith("+")) cleanDigits else "+$cleanDigits"
        }

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                Log.d(TAG, "onVerificationCompleted: auto-validation réussie")
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val authResult = firebaseAuth.signInWithCredential(credential).await()
                        val user = authResult.user
                        val uid = user?.uid ?: "phone_${System.currentTimeMillis()}"
                        val phone = user?.phoneNumber ?: formattedPhone
                        val finalName = if (userName.isNotBlank()) userName.trim() else "Utilisateur $phone"
                        val email = "$phone@phone.auth"

                        syncUserToFirestore(
                            uid = uid,
                            name = finalName,
                            email = email,
                            photoUrl = null,
                            phoneNumber = phone
                        )

                        val profile = UserProfile(
                            isLoggedIn = true,
                            userId = uid,
                            email = email,
                            name = finalName,
                            phoneNumber = phone,
                            photoUrl = null,
                            provider = AuthProvider.PHONE,
                            role = if (email.equals(ADMIN_EMAIL, ignoreCase = true)) "admin" else "user",
                            isCloudSynced = true
                        )

                        withContext(Dispatchers.Main) {
                            onAutoVerified(profile)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Erreur onVerificationCompleted: ${e.message}", e)
                        withContext(Dispatchers.Main) {
                            onError(e.localizedMessage ?: "Erreur d'auto-connexion")
                        }
                    }
                }
            }

            override fun onVerificationFailed(e: FirebaseException) {
                Log.e(TAG, "onVerificationFailed: ${e.message}", e)
                val userMsg = when {
                    e.message?.contains("quota", ignoreCase = true) == true ->
                        "Quota SMS Firebase atteint pour aujourd'hui. Veuillez vérifier dans la console Firebase ou utiliser le code de validation."
                    e.message?.contains("format", ignoreCase = true) == true || e.message?.contains("invalid", ignoreCase = true) == true ->
                        "Format de numéro de téléphone invalide. Ex: +226 70 00 00 00"
                    e.message?.contains("app-not-authorized", ignoreCase = true) == true ->
                        "L'authentification par téléphone doit être activée dans la console Firebase (Auth > Sign-in method > Téléphone)."
                    else -> e.localizedMessage ?: "Impossible d'envoyer le SMS de vérification."
                }
                onError(userMsg)
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                Log.d(TAG, "onCodeSent: $verificationId")
                onCodeSent(verificationId, token)
            }
        }

        try {
            val builder = PhoneAuthOptions.newBuilder(firebaseAuth)
                .setPhoneNumber(formattedPhone)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(callbacks)

            if (resendToken != null) {
                builder.setForceResendingToken(resendToken)
            }

            PhoneAuthProvider.verifyPhoneNumber(builder.build())
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lancement verifyPhoneNumber: ${e.message}", e)
            onError(e.localizedMessage ?: "Erreur lors de la configuration de l'envoi SMS.")
        }
    }

    /**
     * Valide le code SMS saisi par l'utilisateur et connecte le profil à Firebase Auth
     */
    suspend fun verifyPhoneOtpAndSignIn(
        verificationId: String,
        smsCode: String,
        name: String,
        phoneNumber: String
    ): Result<UserProfile> {
        val firebaseAuth = auth
            ?: return Result.failure(Exception("Service Firebase Auth indisponible."))
        return try {
            val credential = PhoneAuthProvider.getCredential(verificationId, smsCode.trim())
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            val user = authResult.user
                ?: return Result.failure(Exception("Impossible de récupérer l'utilisateur Firebase après validation."))

            val uid = user.uid
            val phone = user.phoneNumber ?: phoneNumber
            val finalName = if (name.isNotBlank()) name else "Utilisateur $phone"
            val email = "$phone@phone.auth"

            // Synchronisation avec Firestore
            syncUserToFirestore(
                uid = uid,
                name = finalName,
                email = email,
                photoUrl = null,
                phoneNumber = phone
            )

            val profile = UserProfile(
                isLoggedIn = true,
                userId = uid,
                email = email,
                name = finalName,
                phoneNumber = phone,
                photoUrl = null,
                provider = AuthProvider.PHONE,
                role = if (email.equals(ADMIN_EMAIL, ignoreCase = true)) "admin" else "user",
                isCloudSynced = true
            )
            Result.success(profile)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur verifyPhoneOtpAndSignIn: ${e.message}", e)
            Result.failure(Exception(e.localizedMessage ?: "Code SMS de vérification incorrect ou expiré."))
        }
    }

    /**
     * Synchronise le profil utilisateur avec Firestore sans créer de doublons :
     * - Si nouvel utilisateur : crée le document avec createdAt & lastLogin
     * - Si existant : met à jour uniquement lastLogin, nom, photoURL et téléphone
     */
    suspend fun syncUserToFirestore(
        uid: String,
        name: String,
        email: String,
        photoUrl: String? = null,
        phoneNumber: String? = null
    ) {
        val db = firestore ?: return
        try {
            val userDocRef = db.collection(USERS_COLLECTION).document(uid)
            val snapshot = userDocRef.get().await()

            val now = System.currentTimeMillis()
            val updates = mutableMapOf<String, Any>(
                "uid" to uid,
                "nom" to name,
                "email" to email,
                "lastLogin" to now,
                "derniere_connexion" to now
            )
            photoUrl?.let {
                updates["photoURL"] = it
            }
            phoneNumber?.let {
                updates["telephone"] = it
            }

            if (!snapshot.exists()) {
                // Nouvel utilisateur
                updates["date_de_creation"] = now
                updates["createdAt"] = now
                updates["role"] = if (email.equals(ADMIN_EMAIL, ignoreCase = true)) "admin" else "user"
                updates["provider"] = if (email.endsWith("@phone.auth")) "phone" else if (photoUrl != null) "google" else "email"
                userDocRef.set(updates).await()
                Log.d(TAG, "Nouvel utilisateur Firestore créé : $email ($uid)")
            } else {
                // Mise à jour de l'utilisateur existant (pas de doublon)
                userDocRef.set(updates, SetOptions.merge()).await()
                Log.d(TAG, "Utilisateur Firestore mis à jour (lastLogin) : $email")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Impossible de synchroniser avec Firestore: ${e.message}")
        }
    }

    /**
     * Vérifie si le compte utilisateur actuel est toujours actif et valide dans Firebase.
     * Si le compte a été supprimé ou désactivé depuis la Console Firebase :
     * currentUser.reload() ou getIdToken(true) lève une FirebaseAuthInvalidUserException.
     * Retourne false si le compte a été désactivé / supprimé ou n'existe plus.
     */
    suspend fun verifyAccountStatusActive(): Boolean {
        val firebaseAuth = auth ?: return true
        val user = firebaseAuth.currentUser ?: return true
        return try {
            user.reload().await()
            // Vérifie en forçant le rafraîchissement du jeton de sécurité
            user.getIdToken(true).await()
            user.reload().await()
            true
        } catch (e: com.google.firebase.auth.FirebaseAuthInvalidUserException) {
            Log.w(TAG, "Compte Firebase révoqué/désactivé/supprimé dans la console: ${e.errorCode} - ${e.message}")
            signOut()
            false
        } catch (e: Exception) {
            val msg = e.message?.lowercase() ?: ""
            if (msg.contains("user-disabled") || msg.contains("user-not-found") || msg.contains("invalid-user-token") || msg.contains("token-expired")) {
                Log.w(TAG, "Compte désactivé ou supprimé détecté: $msg")
                signOut()
                false
            } else {
                // Erreur de connectivité réseau temporaire (ne pas déconnecter)
                true
            }
        }
    }

    /**
     * Supprime ou désactive un compte utilisateur de Firestore par l'administrateur
     */
    suspend fun deleteUserFromFirestore(uid: String): Result<Unit> {
        val db = firestore ?: return Result.failure(Exception("Firestore indisponible"))
        return try {
            db.collection(USERS_COLLECTION).document(uid).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Déconnexion complète de Firebase Auth
     */
    fun signOut() {
        try {
            auth?.signOut()
        } catch (e: Exception) {
            Log.w(TAG, "Erreur signOut Firebase: ${e.message}")
        }
    }
}
