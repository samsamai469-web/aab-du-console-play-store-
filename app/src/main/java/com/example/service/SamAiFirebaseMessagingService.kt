package com.example.service

import android.util.Log
import com.example.data.remote.AdminBroadcastRepository
import com.example.util.SmartNotificationManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Service Firebase Cloud Messaging (FCM).
 * Reçoit les messages envoyés depuis la console Admin et affiche instantanément
 * une notification système sur le téléphone de chaque utilisateur, même lorsque
 * l'application Sam AI est complètement fermée.
 */
class SamAiFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "SamAiFcmService"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Nouveau jeton FCM généré, ré-abonnement au topic global")
        AdminBroadcastRepository.subscribeToBroadcastTopic(applicationContext)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        try {
            val data = remoteMessage.data
            val notif = remoteMessage.notification

            val title = data["title"]?.takeIf { it.isNotBlank() }
                ?: notif?.title?.takeIf { it.isNotBlank() }
                ?: "Annonce Sam AI"

            val body = data["body"]?.takeIf { it.isNotBlank() }
                ?: data["message"]?.takeIf { it.isNotBlank() }
                ?: notif?.body?.takeIf { it.isNotBlank() }
                ?: return

            val category = data["category"] ?: "info"
            val targetUri = data["targetUri"]?.takeIf { it.startsWith("sam-ai://") }
                ?: "sam-ai://notifications"
            val announcementId = data["announcementId"]?.takeIf { it.isNotBlank() }
                ?: remoteMessage.messageId
                ?: "fcm_${System.currentTimeMillis()}"

            // Éviter un éventuel doublon avec le worker de secours
            AdminBroadcastRepository.markAnnouncementNotified(applicationContext, announcementId)

            SmartNotificationManager.postAdminBroadcastNotification(
                context = applicationContext,
                announcementId = announcementId,
                title = title,
                body = body,
                category = category,
                targetUri = targetUri
            )
            Log.d(TAG, "Notification push admin affichée: $title")
        } catch (t: Throwable) {
            Log.e(TAG, "Erreur lors de la réception du message FCM: ${t.message}", t)
        }
    }
}
