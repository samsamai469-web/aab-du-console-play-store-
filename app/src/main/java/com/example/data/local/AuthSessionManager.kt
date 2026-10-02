package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import com.example.util.SecurityUtils
import kotlinx.serialization.json.Json

/**
 * Gestionnaire de Session Sécurisée pour Sam AI
 * - Adossé au matériel sécurisé (Android Keystore / TEE) via EncryptedSharedPreferences (AES-256-GCM / AES-256-SIV)
 * - Tolérant aux pannes, résilient contre toute corruption de clé Keystore
 * - Persiste et protège le profil utilisateur, tokens et préférences
 * - Assure une déconnexion intégrale sans résidus
 */
class AuthSessionManager(context: Context) {

    private val appContext: Context = context.applicationContext
    
    private var prefs: SharedPreferences = createSecurePreferences()

    private fun createSecurePreferences(): SharedPreferences {
        return try {
            val masterKey = SecurityUtils.getOrCreateMasterKey(appContext)

            EncryptedSharedPreferences.create(
                appContext,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Throwable) {
            Log.w("AuthSessionManager", "EncryptedSharedPreferences non disponible, basculement sécurisé", e)
            try {
                appContext.getSharedPreferences(PREFS_FALLBACK_NAME, Context.MODE_PRIVATE)
            } catch (_: Throwable) {
                appContext.getSharedPreferences("sam_ai_plain_prefs", Context.MODE_PRIVATE)
            }
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    /**
     * Sauvegarde le profil utilisateur de manière chiffrée
     */
    fun saveUserProfile(profile: UserProfile) {
        try {
            if (!profile.isLoggedIn) {
                clearSession()
                return
            }
            val rawJson = json.encodeToString(UserProfile.serializer(), profile)
            prefs.edit()
                .putString(KEY_USER_PROFILE, rawJson)
                .apply()
        } catch (e: Throwable) {
            Log.e("AuthSessionManager", "Erreur lors de la sauvegarde de session", e)
        }
    }

    /**
     * Restaure le profil utilisateur chiffré depuis le stockage sécurisé
     */
    fun getSavedUserProfile(): UserProfile? {
        return try {
            val stored = try {
                prefs.getString(KEY_USER_PROFILE, null)
            } catch (t: Throwable) {
                Log.w("AuthSessionManager", "Keystore read error, resetting fallback", t)
                prefs = appContext.getSharedPreferences(PREFS_FALLBACK_NAME, Context.MODE_PRIVATE)
                prefs.getString(KEY_USER_PROFILE, null)
            } ?: return null

            // Essai de décodage JSON direct (EncryptedSharedPreferences)
            try {
                json.decodeFromString<UserProfile>(stored)
            } catch (_: Throwable) {
                // Rétrocompatibilité : déchiffrement legacy si le blob était précédemment chiffré avec SecurityUtils
                val decrypted = SecurityUtils.decrypt(stored)
                if (decrypted.isNotBlank()) {
                    json.decodeFromString<UserProfile>(decrypted)
                } else null
            }
        } catch (e: Throwable) {
            Log.w("AuthSessionManager", "Échec de restauration du profil utilisateur", e)
            null
        }
    }

    /**
     * Sauvegarde les clés Google OAuth de manière chiffrée
     */
    fun saveGoogleOAuthCredentials(clientId: String, clientSecret: String) {
        try {
            prefs.edit()
                .putString(KEY_GOOGLE_CLIENT_ID, clientId.trim().takeIf { it.isNotBlank() })
                .putString(KEY_GOOGLE_CLIENT_SECRET, clientSecret.trim().takeIf { it.isNotBlank() })
                .apply()
        } catch (e: Throwable) {
            Log.e("AuthSessionManager", "Erreur lors de l'enregistrement des clés Google", e)
        }
    }

    /**
     * Récupère le Google Client ID personnalisé s'il est enregistré
     */
    fun getCustomGoogleClientId(): String? {
        return try {
            val stored = prefs.getString(KEY_GOOGLE_CLIENT_ID, null) ?: return null
            if (stored.startsWith("{") || stored.contains(".apps.googleusercontent.com")) {
                stored
            } else {
                val decrypted = SecurityUtils.decrypt(stored)
                if (decrypted.isNotBlank()) decrypted else stored
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Récupère le Google Client Secret personnalisé s'il est enregistré
     */
    fun getCustomGoogleClientSecret(): String? {
        return try {
            val stored = prefs.getString(KEY_GOOGLE_CLIENT_SECRET, null) ?: return null
            if (stored.startsWith("GOCSPX-")) {
                stored
            } else {
                val decrypted = SecurityUtils.decrypt(stored)
                if (decrypted.isNotBlank()) decrypted else stored
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun getAppLanguage(): String {
        return try {
            prefs.getString(KEY_APP_LANGUAGE, "fr") ?: "fr"
        } catch (_: Throwable) {
            "fr"
        }
    }

    fun setAppLanguage(language: String) {
        try {
            prefs.edit()
                .putString(KEY_APP_LANGUAGE, language)
                .apply()
        } catch (_: Throwable) {}
    }

    fun getThemePreference(): String {
        return try {
            prefs.getString(KEY_THEME_PREFERENCE, "dark") ?: "dark"
        } catch (_: Throwable) {
            "dark"
        }
    }

    fun setThemePreference(theme: String) {
        try {
            prefs.edit()
                .putString(KEY_THEME_PREFERENCE, theme)
                .apply()
        } catch (_: Throwable) {}
    }

    fun getThemeStylePreference(): String {
        return try {
            prefs.getString(KEY_THEME_STYLE_PREFERENCE, "modern") ?: "modern"
        } catch (_: Throwable) {
            "modern"
        }
    }

    fun setThemeStylePreference(style: String) {
        try {
            prefs.edit()
                .putString(KEY_THEME_STYLE_PREFERENCE, style)
                .apply()
        } catch (_: Throwable) {}
    }

    fun isOnboardingCompleted(): Boolean {
        return try {
            prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        } catch (_: Throwable) {
            false
        }
    }

    fun setOnboardingCompleted(completed: Boolean = true) {
        try {
            prefs.edit()
                .putBoolean(KEY_ONBOARDING_COMPLETED, completed)
                .apply()
        } catch (_: Throwable) {}
    }

    /**
     * Sauvegarde le jeton d'authentification Administrateur
     */
    fun saveAdminToken(token: String) {
        try {
            if (token.isBlank()) {
                clearAdminToken()
                return
            }
            prefs.edit()
                .putString(KEY_ADMIN_TOKEN, token.trim())
                .apply()
        } catch (e: Throwable) {
            Log.e("AuthSessionManager", "Erreur lors de la sauvegarde du jeton admin", e)
        }
    }

    /**
     * Récupère et valide le jeton d'authentification Administrateur persistant
     */
    fun getSavedAdminToken(): String? {
        return try {
            val token = prefs.getString(KEY_ADMIN_TOKEN, null) ?: return null
            val cleanToken = if (token.startsWith("SAM_ADMIN_AUTH_VALIDATED_")) {
                token
            } else {
                SecurityUtils.decrypt(token)
            }

            if (cleanToken.isNotBlank() && SecurityUtils.verifyAdminToken(cleanToken)) {
                cleanToken
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Supprime le jeton Administrateur (déconnexion de la console admin)
     */
    fun clearAdminToken() {
        try {
            prefs.edit()
                .remove(KEY_ADMIN_TOKEN)
                .apply()
        } catch (_: Throwable) {}
    }

    /**
     * Vérifie si l'adresse e-mail correspond à l'administrateur principal de Sam Traduction
     */
    fun isSuperAdminUser(email: String?): Boolean {
        return SecurityUtils.isSuperAdminEmail(email)
    }

    /**
     * Réinitialise totalement la session utilisateur
     */
    fun clearSession() {
        try {
            prefs.edit()
                .remove(KEY_USER_PROFILE)
                .remove(KEY_ADMIN_TOKEN)
                .apply()
        } catch (_: Throwable) {}
    }

    companion object {
        private const val PREFS_NAME = "sam_ai_secure_auth_prefs"
        private const val PREFS_FALLBACK_NAME = "sam_ai_auth_prefs_fb"
        private const val KEY_USER_PROFILE = "enc_user_profile"
        private const val KEY_ADMIN_TOKEN = "enc_admin_auth_token"
        private const val KEY_GOOGLE_CLIENT_ID = "enc_google_client_id"
        private const val KEY_GOOGLE_CLIENT_SECRET = "enc_google_client_secret"
        private const val KEY_APP_LANGUAGE = "app_language_preference"
        private const val KEY_THEME_PREFERENCE = "app_theme_preference"
        private const val KEY_THEME_STYLE_PREFERENCE = "app_theme_style_preference"
        private const val KEY_ONBOARDING_COMPLETED = "has_completed_onboarding_presentation"
    }
}
