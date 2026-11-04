package com.example.data.remote

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import com.example.data.local.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

object SupportService {
    const val MAX_ATTACHMENT_SIZE_BYTES = 5 * 1024 * 1024L // 5 MB
    const val OFFICIAL_SUPPORT_WHATSAPP_NUMBER = "+22669434630"

    data class SupportSubmission(
        val type: String = "problem",
        val category: String = "general",
        val subject: String = "",
        val message: String = "",
        val phoneNumber: String = "",
        val name: String = "",
        val email: String = "",
        val attachmentUri: Uri? = null,
        val attachmentBase64: String? = null,
        val attachmentName: String? = null,
        val attachmentMimeType: String? = null
    )

    fun processAttachment(context: Context, uri: Uri): Triple<String?, String?, Long> {
        return try {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
            val inputStream = contentResolver.openInputStream(uri) ?: return Triple(null, null, 0L)
            val bytes = inputStream.readBytes()
            inputStream.close()
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            Triple(base64, mimeType, bytes.size.toLong())
        } catch (_: Exception) {
            Triple(null, null, 0L)
        }
    }

    /**
     * Ouvre directement la conversation WhatsApp avec le numéro officiel +22669434630
     * et pré-remplit toutes les informations saisies par l'utilisateur sans qu'il ait
     * besoin de chercher ou d'enregistrer le numéro.
     */
    fun launchWhatsApp(
        context: Context,
        message: String,
        phone: String = OFFICIAL_SUPPORT_WHATSAPP_NUMBER
    ) {
        val cleanPhone = phone.replace(Regex("[^0-9]"), "").ifBlank { "22669434630" }
        val encodedMsg = try {
            URLEncoder.encode(message, "UTF-8")
        } catch (_: Exception) {
            Uri.encode(message)
        }

        // 1. Tentative d'ouverture directe dans l'application native WhatsApp / WhatsApp Business
        // via le schéma whatsapp://send?phone=22669434630&text=...
        val nativeUri = Uri.parse("whatsapp://send?phone=$cleanPhone&text=$encodedMsg")
        val pm = context.packageManager
        val whatsAppPackages = listOf("com.whatsapp", "com.whatsapp.w4b")

        for (pkg in whatsAppPackages) {
            try {
                val directIntent = Intent(Intent.ACTION_VIEW, nativeUri).apply {
                    setPackage(pkg)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                if (directIntent.resolveActivity(pm) != null) {
                    context.startActivity(directIntent)
                    return
                }
            } catch (_: Exception) {
                // Continue vers le prochain package ou fallback
            }
        }

        // 2. Tentative avec le schéma whatsapp:// sans restriction de package
        try {
            val schemeIntent = Intent(Intent.ACTION_VIEW, nativeUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(schemeIntent)
            return
        } catch (_: Exception) {
            // Fallback universel wa.me / api.whatsapp.com ciblant toujours le +22669434630
        }

        // 3. Fallback universel https://wa.me/22669434630?text=...
        try {
            val webUrl = "https://wa.me/$cleanPhone?text=$encodedMsg"
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(webIntent)
        } catch (_: Exception) {
            val apiUrl = "https://api.whatsapp.com/send?phone=$cleanPhone&text=$encodedMsg"
            val apiIntent = Intent(Intent.ACTION_VIEW, Uri.parse(apiUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                context.startActivity(apiIntent)
            } catch (_: Exception) {}
        }
    }

    fun formatWhatsAppMessage(
        userProfile: UserProfile?,
        submission: SupportSubmission,
        formattedDate: String = "",
        appVersion: String = "1.0"
    ): String {
        val typeLabel = if (submission.type.equals("help", ignoreCase = true)) {
            "DEMANDE D'AIDE / ASSISTANCE"
        } else {
            "SIGNALEMENT DE BUG / PROBLÈME"
        }

        val resolvedName = when {
            submission.name.isNotBlank() -> submission.name.trim()
            userProfile != null && userProfile.isLoggedIn && userProfile.name.isNotBlank() -> userProfile.name.trim()
            userProfile != null && userProfile.isLoggedIn && userProfile.displayName.isNotBlank() -> userProfile.displayName.trim()
            else -> "Utilisateur Sam Traduction"
        }

        val resolvedEmail = when {
            submission.email.isNotBlank() -> submission.email.trim()
            userProfile != null && !userProfile.email.isNullOrBlank() -> userProfile.email.trim()
            else -> ""
        }

        val builder = StringBuilder()
        builder.append("📩 *NOUVEAU MESSAGE SUPPORT SAM AI*\n")
        builder.append("----------------------------\n")
        if (formattedDate.isNotBlank()) {
            builder.append("📅 *Date :* $formattedDate\n")
        }
        builder.append("📌 *Type :* $typeLabel\n")
        builder.append("🏷️ *Catégorie :* ${submission.category}\n")
        if (submission.subject.isNotBlank()) {
            builder.append("📝 *Sujet :* ${submission.subject.trim()}\n")
        }
        builder.append("💬 *Message :*\n${submission.message.trim()}\n")
        builder.append("----------------------------\n")
        if (resolvedEmail.isNotBlank()) {
            builder.append("👤 *Nom :* $resolvedName ($resolvedEmail)\n")
        } else {
            builder.append("👤 *Nom :* $resolvedName\n")
        }
        if (submission.phoneNumber.isNotBlank()) {
            builder.append("📞 *Contact :* ${submission.phoneNumber.trim()}\n")
        }
        if (!submission.attachmentName.isNullOrBlank()) {
            builder.append("📎 *Capture d'écran :* ${submission.attachmentName} (à joindre dans la discussion)\n")
        }
        builder.append("📱 *Version App :* $appVersion")
        return builder.toString()
    }

    suspend fun submitSupportRequest(
        context: Context,
        userProfile: UserProfile?,
        submission: SupportSubmission,
        appVersionName: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (submission.subject.isBlank() && submission.message.isBlank()) {
                return@withContext Result.failure(
                    IllegalArgumentException("Veuillez renseigner un sujet ou décrire votre problème / demande d'aide.")
                )
            }
            if (submission.message.trim().length < 5) {
                return@withContext Result.failure(
                    IllegalArgumentException("Veuillez écrire un message explicatif (au moins 5 caractères) avant d'envoyer.")
                )
            }

            val dateStr = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.FRENCH)
                .format(java.util.Date())

            val formatted = formatWhatsAppMessage(
                userProfile = userProfile,
                submission = submission.copy(
                    subject = submission.subject.ifBlank {
                        if (submission.type == "help") "Besoin d'aide" else "Signalement de bug"
                    }
                ),
                formattedDate = dateStr,
                appVersion = appVersionName
            )

            Result.success(formatted)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
