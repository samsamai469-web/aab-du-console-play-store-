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

    fun launchWhatsApp(context: Context, message: String, phone: String = "+22600000000") {
        try {
            val encodedMsg = URLEncoder.encode(message, "UTF-8")
            val cleanPhone = phone.replace("+", "").replace(" ", "").trim()
            val url = if (cleanPhone.isNotBlank() && cleanPhone != "22600000000") {
                "https://api.whatsapp.com/send?phone=$cleanPhone&text=$encodedMsg"
            } else {
                "https://api.whatsapp.com/send?text=$encodedMsg"
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(Intent.createChooser(shareIntent, "Envoyer via WhatsApp..."))
        }
    }

    fun formatWhatsAppMessage(
        userProfile: UserProfile?,
        submission: SupportSubmission,
        formattedDate: String = "",
        appVersion: String = "1.0"
    ): String {
        val builder = StringBuilder()
        builder.append("📩 *NOUVEAU MESSAGE SUPPORT SAM AI*\n")
        builder.append("----------------------------\n")
        if (formattedDate.isNotBlank()) {
            builder.append("📅 *Date:* $formattedDate\n")
        }
        builder.append("📌 *Type:* ${submission.type.uppercase()}\n")
        builder.append("🏷️ *Catégorie:* ${submission.category}\n")
        builder.append("📝 *Sujet:* ${submission.subject}\n")
        builder.append("💬 *Message:* \n${submission.message}\n")
        builder.append("----------------------------\n")
        if (userProfile != null) {
            builder.append("👤 *Utilisateur:* ${userProfile.name} (${userProfile.email})\n")
        } else if (submission.name.isNotBlank()) {
            builder.append("👤 *Nom:* ${submission.name}\n")
        }
        if (submission.phoneNumber.isNotBlank()) {
            builder.append("📞 *Téléphone:* ${submission.phoneNumber}\n")
        }
        builder.append("📱 *Version App:* $appVersion\n")
        return builder.toString()
    }

    suspend fun submitSupportRequest(
        context: Context,
        userProfile: UserProfile?,
        submission: SupportSubmission,
        appVersionName: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val builder = StringBuilder()
            builder.append("📩 *NOUVEAU MESSAGE SUPPORT SAM AI*\n")
            builder.append("----------------------------\n")
            builder.append("📌 *Type:* ${submission.type.uppercase()}\n")
            builder.append("🏷️ *Catégorie:* ${submission.category}\n")
            builder.append("📝 *Sujet:* ${submission.subject}\n")
            builder.append("💬 *Message:* \n${submission.message}\n")
            builder.append("----------------------------\n")
            if (userProfile != null) {
                builder.append("👤 *Utilisateur:* ${userProfile.displayName} (${userProfile.email ?: submission.email})\n")
            } else if (submission.name.isNotBlank()) {
                builder.append("👤 *Nom:* ${submission.name}\n")
            }
            if (submission.phoneNumber.isNotBlank()) {
                builder.append("📞 *Téléphone:* ${submission.phoneNumber}\n")
            }
            builder.append("📱 *Version App:* $appVersionName\n")
            if (!submission.attachmentName.isNullOrBlank()) {
                builder.append("📎 *Pièce jointe:* ${submission.attachmentName}\n")
            }

            Result.success(builder.toString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
