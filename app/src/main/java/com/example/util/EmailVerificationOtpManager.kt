package com.example.util

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * Gestionnaire sécurisé de vérification email pour SAM AI :
 * - Délégué à Firebase Authentication (sendEmailVerification)
 * - Validation OTP éphémère sécurisée via Firestore & mémoire locale
 * - Aucun identifiant SMTP, mot de passe ni socket en clair côté client Android
 */
object EmailVerificationOtpManager {

    private const val TAG = "EmailOtpManager"
    private const val OTP_COLLECTION = "email_verification_codes"
    private const val OTP_EXPIRATION_MS = 10 * 60 * 1000L // 10 minutes

    private fun getFirestoreSafe(): FirebaseFirestore? {
        return try {
            FirebaseFirestore.getInstance()
        } catch (_: Throwable) {
            null
        }
    }

    private fun getAuthSafe(): FirebaseAuth? {
        return try {
            FirebaseAuth.getInstance()
        } catch (_: Throwable) {
            null
        }
    }

    data class PendingOtp(
        val email: String,
        val code: String,
        val expiresAt: Long,
        val attemptsLeft: Int = 3
    )

    private val localPendingOtps = ConcurrentHashMap<String, PendingOtp>()

    /**
     * Génère et initialise le code de vérification pour l'adresse email.
     * Envoie également l'email de vérification officiel Firebase si l'utilisateur est authentifié.
     */
    suspend fun generateAndSendOtp(context: Context, email: String): Result<String> {
        val cleanEmail = email.trim().lowercase()
        return withContext(Dispatchers.IO) {
            try {
                // 1. Génération d'un code 6 chiffres sécurisé via SecureRandom
                val random = SecureRandom()
                val codeInt = 100000 + random.nextInt(900000)
                val codeStr = codeInt.toString()
                val now = System.currentTimeMillis()
                val expiresAt = now + OTP_EXPIRATION_MS

                // 2. Mémorisation locale sécurisée
                val pending = PendingOtp(cleanEmail, codeStr, expiresAt, attemptsLeft = 3)
                localPendingOtps[cleanEmail] = pending

                // 3. Sauvegarde Firestore pour validation Cloud Function / Extension
                try {
                    val firestore = getFirestoreSafe()
                    if (firestore != null) {
                        val data = mapOf(
                            "email" to cleanEmail,
                            "code" to codeStr,
                            "createdAt" to now,
                            "expiresAt" to expiresAt,
                            "used" to false
                        )
                        val docId = cleanEmail.replace("@", "_at_").replace(".", "_dot_")
                        firestore.collection(OTP_COLLECTION).document(docId).set(data, SetOptions.merge())

                        // Déclenchement via extension Firebase Mail officielle (sans identifiants client)
                        val mailDoc = mapOf(
                            "to" to listOf(cleanEmail),
                            "message" to mapOf(
                                "subject" to "Votre code de confirmation SAM AI : $codeStr",
                                "html" to generateHtmlEmailBody(cleanEmail, codeStr)
                            )
                        )
                        firestore.collection("mail").add(mailDoc)
                    }
                } catch (fe: Throwable) {
                    Log.w(TAG, "Sauvegarde Firestore OTP: ${fe.message}")
                }

                // 4. Délégation de vérification officielle Firebase Auth si un compte existe
                try {
                    val authUser = getAuthSafe()?.currentUser
                    if (authUser != null && authUser.email.equals(cleanEmail, ignoreCase = true)) {
                        authUser.sendEmailVerification()
                        Log.i(TAG, "Email de vérification officiel Firebase expédié à $cleanEmail")
                    }
                } catch (ae: Throwable) {
                    Log.d(TAG, "Firebase sendEmailVerification non déclenché: ${ae.message}")
                }

                Log.d(TAG, "Code de sécurité généré pour $cleanEmail")
                Result.success(codeStr)
            } catch (e: Throwable) {
                Log.e(TAG, "Erreur generateAndSendOtp: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Génère le corps de l'email au format HTML élégant pour le template
     */
    private fun generateHtmlEmailBody(targetEmail: String, code: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <title>Code de validation SAM AI</title>
            </head>
            <body style="margin: 0; padding: 20px; background-color: #f4f6f9; font-family: 'Segoe UI', Arial, sans-serif; color: #1a202c;">
                <table align="center" border="0" cellpadding="0" cellspacing="0" width="100%" style="max-width: 540px; background-color: #ffffff; border-radius: 16px; box-shadow: 0 4px 12px rgba(0,0,0,0.08); overflow: hidden;">
                    <tr>
                        <td style="background: linear-gradient(135deg, #0070FF, #00C6FF); padding: 28px 24px; text-align: center;">
                            <h1 style="color: #ffffff; margin: 0; font-size: 26px; font-weight: 800; letter-spacing: 1px;">SAM AI</h1>
                            <p style="color: rgba(255,255,255,0.9); margin: 6px 0 0 0; font-size: 14px;">Sécurité & Authentification</p>
                        </td>
                    </tr>
                    <tr>
                        <td style="padding: 32px 28px;">
                            <h2 style="color: #1a202c; font-size: 20px; margin-top: 0; font-weight: 700;">Vérification de votre compte</h2>
                            <p style="color: #4a5568; font-size: 15px; line-height: 1.6; margin: 12px 0 24px 0;">
                                Bonjour,<br><br>
                                Voici votre code de sécurité à 6 chiffres pour certifier votre adresse email (<strong>$targetEmail</strong>) sur l'application <strong>SAM AI</strong> :
                            </p>
                            
                            <div style="background-color: #f0f7ff; border: 2px dashed #0070FF; border-radius: 12px; padding: 20px; text-align: center; margin: 24px 0;">
                                <span style="font-size: 36px; font-weight: 800; letter-spacing: 8px; color: #0070FF; font-family: monospace;">$code</span>
                            </div>
                            
                            <p style="color: #718096; font-size: 13px; line-height: 1.5; margin: 20px 0 0 0;">
                                ⏱️ Ce code est valable pendant <strong>10 minutes</strong>.<br>
                                🔒 Ne partagez ce code avec personne. Si vous n'êtes pas à l'origine de cette demande, vous pouvez ignorer cet email en toute sécurité.
                            </p>
                        </td>
                    </tr>
                    <tr>
                        <td style="background-color: #edf2f7; padding: 16px 28px; text-align: center; border-top: 1px solid #e2e8f0;">
                            <p style="color: #a0aec0; font-size: 12px; margin: 0;">
                                © 2026 SAM AI — Authentification Sécurisée Firebase
                            </p>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Valide le code à 6 chiffres saisi par l'utilisateur
     */
    suspend fun verifyOtp(email: String, enteredCode: String): Result<Boolean> {
        val cleanEmail = email.trim().lowercase()
        val cleanCode = enteredCode.trim().replace(" ", "").replace("-", "")

        if (cleanCode.length != 6 || !cleanCode.all { it.isDigit() }) {
            return Result.failure(Exception("Le code doit comporter exactement 6 chiffres."))
        }

        return withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            var storedCode: String? = null
            var expiresAt: Long = 0L

            // 1. Vérification locale sécurisée
            val localPending = localPendingOtps[cleanEmail]
            if (localPending != null) {
                storedCode = localPending.code
                expiresAt = localPending.expiresAt
            }

            // 2. Si non présent en local, vérification Firestore avec timeout court
            if (storedCode == null) {
                try {
                    kotlinx.coroutines.withTimeoutOrNull(2000L) {
                        val firestore = getFirestoreSafe()
                        if (firestore != null) {
                            val docId = cleanEmail.replace("@", "_at_").replace(".", "_dot_")
                            val doc = firestore.collection(OTP_COLLECTION).document(docId).get().await()
                            if (doc.exists()) {
                                val used = doc.getBoolean("used") ?: false
                                if (!used) {
                                    storedCode = doc.getString("code")
                                    expiresAt = doc.getLong("expiresAt") ?: 0L
                                }
                            }
                        }
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "Erreur lecture Firestore OTP: ${e.message}")
                }
            }

            if (storedCode == null) {
                return@withContext Result.failure(Exception("Aucun code actif trouvé pour cette adresse. Veuillez redemander un code."))
            }

            if (now > expiresAt) {
                localPendingOtps.remove(cleanEmail)
                return@withContext Result.failure(Exception("Ce code à 6 chiffres a expiré. Veuillez cliquer sur 'Renvoyer le code'."))
            }

            if (storedCode != cleanCode) {
                if (localPending != null) {
                    val remaining = localPending.attemptsLeft - 1
                    if (remaining <= 0) {
                        localPendingOtps.remove(cleanEmail)
                        return@withContext Result.failure(Exception("Trop de tentatives infructueuses. Le code est annulé, veuillez en demander un nouveau."))
                    } else {
                        localPendingOtps[cleanEmail] = localPending.copy(attemptsLeft = remaining)
                        return@withContext Result.failure(Exception("Code incorrect. Il vous reste $remaining tentative(s)."))
                    }
                }
                return@withContext Result.failure(Exception("Code de vérification incorrect."))
            }

            // Succès : nettoyer le code
            localPendingOtps.remove(cleanEmail)
            try {
                val firestore = getFirestoreSafe()
                if (firestore != null) {
                    val docId = cleanEmail.replace("@", "_at_").replace(".", "_dot_")
                    firestore.collection(OTP_COLLECTION).document(docId).update("used", true)
                }
            } catch (_: Throwable) {}

            Log.d(TAG, "Code de vérification validé avec succès pour $cleanEmail")
            Result.success(true)
        }
    }
}
