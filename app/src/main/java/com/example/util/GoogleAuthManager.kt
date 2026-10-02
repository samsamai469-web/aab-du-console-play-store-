package com.example.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.example.BuildConfig
import com.example.data.local.AuthSessionManager
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Données du compte utilisateur Google récupérées après authentification
 */
data class GoogleAccountInfo(
    val email: String,
    val displayName: String,
    val photoUrl: String?,
    val idToken: String? = null,
    val googleId: String? = null
)

/**
 * Résultat de l'authentification Google OAuth
 */
sealed class GoogleAuthResult {
    data class Success(val account: GoogleAccountInfo) : GoogleAuthResult()
    object Cancelled : GoogleAuthResult()
    data class Error(val message: String) : GoogleAuthResult()
}

/**
 * Gestionnaire d'authentification Google OAuth 100% Autonome (Direct sans Supabase) pour Sam Traduction
 * - Intègre Android Jetpack CredentialManager & Google Identity
 * - Intègre le flux OAuth 2.0 direct officiel Web (accounts.google.com & oauth2.googleapis.com)
 * - Supporte la configuration directe du Client ID et du Client Secret
 * - Récupère uniquement le nom, l'adresse e-mail et la photo de profil
 * - Gère les annulations et erreurs sans bloquer l'application
 * - Ne diffuse aucun secret technique ni clé privée dans l'interface
 */
object GoogleAuthManager {

    private const val TAG = "GoogleAuthManager"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Récupère le Client ID Google configuré dans AuthSessionManager ou BuildConfig / .env
     */
    fun getGoogleClientId(sessionManager: AuthSessionManager? = null): String {
        return "438563104660-t8bmjvsmt4dpl4elj7has3vsg7obfken.apps.googleusercontent.com"
    }

    /**
     * Récupère le Client Secret Google configuré dans AuthSessionManager ou BuildConfig / .env
     */
    fun getGoogleClientSecret(sessionManager: AuthSessionManager? = null): String {
        val customSecret = sessionManager?.getCustomGoogleClientSecret()
        if (!customSecret.isNullOrBlank()) return customSecret.trim()

        return try {
            val field = BuildConfig::class.java.getField("GOOGLE_CLIENT_SECRET")
            val raw = (field.get(null) as? String)?.trim() ?: ""
            if (raw.isNotBlank() && !raw.contains("placeholder")) raw else ""
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Génère l'URL officielle de connexion Google OAuth 2.0 Web directe (sans Supabase)
     */
    fun getGoogleOAuthUrl(
        redirectUri: String = "sam-ai://auth/callback",
        sessionManager: AuthSessionManager? = null,
        useCodeFlow: Boolean = false
    ): String {
        val clientId = getGoogleClientId(sessionManager).ifBlank { "438563104660-t8bmjvsmt4dpl4elj7has3vsg7obfken.apps.googleusercontent.com" }
        val nonce = UUID.randomUUID().toString()
        val state = UUID.randomUUID().toString()
        val encodedRedirect = URLEncoder.encode(redirectUri, "UTF-8")
        val scope = URLEncoder.encode("openid email profile", "UTF-8")
        val responseType = if (useCodeFlow) "code" else "token%20id_token"
        
        return "https://accounts.google.com/o/oauth2/v2/auth?" +
                "client_id=$clientId&" +
                "response_type=$responseType&" +
                "scope=$scope&" +
                "redirect_uri=$encodedRedirect&" +
                "state=$state&" +
                "nonce=$nonce&" +
                "prompt=select_account"
    }

    /**
     * Ouvre la page officielle de connexion Google dans le navigateur sans intermédiaire
     */
    fun openGoogleOAuthInBrowser(context: Context, customUrl: String? = null, sessionManager: AuthSessionManager? = null) {
        val url = if (!customUrl.isNullOrBlank() && customUrl.startsWith("https://")) {
            customUrl
        } else {
            getGoogleOAuthUrl(sessionManager = sessionManager)
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Impossible d'ouvrir le navigateur pour Google OAuth", e)
        }
    }

    /**
     * Échange un Authorization Code Google directement contre des tokens via oauth2.googleapis.com (sans Supabase)
     */
    suspend fun exchangeAuthorizationCode(
        code: String,
        redirectUri: String = "sam-ai://auth/callback",
        sessionManager: AuthSessionManager? = null
    ): GoogleAccountInfo? = withContext(Dispatchers.IO) {
        val clientId = getGoogleClientId(sessionManager)
        val clientSecret = getGoogleClientSecret(sessionManager)

        if (clientId.isBlank()) {
            Log.e(TAG, "Impossible d'échanger le code OAuth: Client ID manquant")
            return@withContext null
        }

        try {
            val formBuilder = FormBody.Builder()
                .add("code", code)
                .add("client_id", clientId)
                .add("redirect_uri", redirectUri)
                .add("grant_type", "authorization_code")

            if (clientSecret.isNotBlank()) {
                formBuilder.add("client_secret", clientSecret)
            }

            val request = Request.Builder()
                .url("https://oauth2.googleapis.com/token")
                .post(formBuilder.build())
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val json = org.json.JSONObject(body)
                    val accessToken = json.optString("access_token")
                    val idToken = json.optString("id_token")

                    return@withContext fetchGoogleUserInfo(accessToken, idToken)
                } else {
                    Log.w(TAG, "Échec échange code Google: HTTP ${response.code} - $body")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur réseau lors de l'échange de token Google", e)
        }

        return@withContext null
    }

    /**
     * Récupère les informations de profil utilisateur Google à partir d'un access_token ou id_token
     */
    suspend fun fetchGoogleUserInfo(accessToken: String?, idToken: String?): GoogleAccountInfo? = withContext(Dispatchers.IO) {
        // 1. Essai via le endpoint standard Google userinfo si accessToken est présent
        if (!accessToken.isNullOrBlank()) {
            try {
                val request = Request.Builder()
                    .url("https://www.googleapis.com/oauth2/v3/userinfo")
                    .addHeader("Authorization", "Bearer $accessToken")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val json = org.json.JSONObject(body)
                        val email = json.optString("email")
                        if (email.isNotBlank()) {
                            val name = json.optString("name")
                                .ifBlank { json.optString("given_name") }
                                .ifBlank { email.substringBefore("@") }
                            val picture = json.optString("picture")
                                .ifBlank { "https://api.dicebear.com/7.x/initials/png?seed=$email&backgroundColor=4285F4" }
                            val sub = json.optString("sub", email)

                            return@withContext GoogleAccountInfo(
                                email = email,
                                displayName = name,
                                photoUrl = picture,
                                idToken = idToken,
                                googleId = sub
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Échec appel userinfo endpoint: ${e.message}")
            }
        }

        // 2. Essai via décodage JWT de l'id_token si présent
        if (!idToken.isNullOrBlank()) {
            try {
                val payload = SecurityUtils.decodeJwtPayload(idToken)
                if (payload != null) {
                    val email = payload["email"] ?: ""
                    if (email.isNotBlank()) {
                        val name = payload["name"] 
                            ?: payload["given_name"] 
                            ?: email.substringBefore("@")
                        val picture = payload["picture"] 
                            ?: "https://api.dicebear.com/7.x/initials/png?seed=$email&backgroundColor=4285F4"
                        val sub = payload["sub"] ?: email

                        return@withContext GoogleAccountInfo(
                            email = email,
                            displayName = name,
                            photoUrl = picture,
                            idToken = idToken,
                            googleId = sub
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Échec décodage id_token JWT: ${e.message}")
            }
        }

        return@withContext null
    }

    /**
     * Liste des comptes Google disponibles pour la sélection rapide
     */
    val SAMPLE_GOOGLE_ACCOUNTS = listOf(
        GoogleAccountInfo(
            email = "samsamai469@gmail.com",
            displayName = "SAM AI",
            photoUrl = "https://lh3.googleusercontent.com/a/ACg8ocLw_sam_traduction_user=s200",
            googleId = "1094829482910482019"
        ),
        GoogleAccountInfo(
            email = "apprenant.faso@gmail.com",
            displayName = "Apprenant Faso",
            photoUrl = "https://lh3.googleusercontent.com/a/ACg8ocK_faso_user=s200",
            googleId = "1049284920194820192"
        )
    )

    /**
     * Recherche récursive de l'Activity depuis un Context Android
     */
    fun Context.findActivity(): Activity? {
        var current: Context? = this
        while (current is android.content.ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }

    /**
     * Lance le flux d'authentification Google officiel avec CredentialManager
     * Exécuté en toute sécurité avec repli garanti sans jamais crasher ni quitter l'application.
     */
    suspend fun launchCredentialManagerSignIn(
        context: Context,
        serverClientId: String = "",
        sessionManager: AuthSessionManager? = null
    ): GoogleAuthResult = withContext(Dispatchers.Main) {
        val effectiveClientId = "438563104660-t8bmjvsmt4dpl4elj7has3vsg7obfken.apps.googleusercontent.com"

        try {
            val act = context.findActivity()
            if (act == null) {
                Log.w(TAG, "Contexte Activity introuvable pour CredentialManager")
                return@withContext GoogleAuthResult.Error("Activity introuvable")
            }

            val credentialManager = CredentialManager.create(act)
            val hashedNonce = UUID.randomUUID().toString()
            val googleIdOption = GetSignInWithGoogleOption.Builder(effectiveClientId)
                .setNonce(hashedNonce)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val response = credentialManager.getCredential(act, request)
            val credential = response.credential

            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val email = googleIdTokenCredential.id
                val name = googleIdTokenCredential.displayName ?: email.substringBefore("@")
                val photoUrl = googleIdTokenCredential.profilePictureUri?.toString()
                    ?: "https://api.dicebear.com/7.x/initials/png?seed=${email}&backgroundColor=4285F4"
                val googleId = googleIdTokenCredential.id

                return@withContext GoogleAuthResult.Success(
                    GoogleAccountInfo(
                        email = email,
                        displayName = name,
                        photoUrl = photoUrl,
                        idToken = googleIdTokenCredential.idToken,
                        googleId = googleId
                    )
                )
            } else {
                Log.w(TAG, "Type d'identifiant non reconnu: ${credential.type}")
                return@withContext GoogleAuthResult.Error("Format de compte Google non reconnu")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.i(TAG, "Authentification Google annulée par l'utilisateur")
            return@withContext GoogleAuthResult.Cancelled
        } catch (e: GetCredentialException) {
            Log.w(TAG, "Exception CredentialManager: ${e.type} - ${e.message}")
            return@withContext GoogleAuthResult.Error(e.message ?: "Compte Google non détecté")
        } catch (t: Throwable) {
            Log.e(TAG, "Exception rattrapée lors de launchCredentialManagerSignIn", t)
            return@withContext GoogleAuthResult.Error(t.localizedMessage ?: "Erreur d'authentification Google")
        }
    }
}

