package com.example.util

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.remote.AdminBroadcastRepository
import java.util.concurrent.TimeUnit

/**
 * Worker de synchronisation en arrière-plan pour les annonces administrateur.
 * Complète Firebase Cloud Messaging (FCM) : même si l'application est fermée
 * ou si un appareil n'a pas reçu le push FCM instantané, ce worker vérifie
 * périodiquement Firestore (`announcements`) et affiche les nouveaux messages.
 */
class AnnouncementWatcherWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "AnnouncementWatcher"
        private const val WORK_NAME = "SamAdminAnnouncementsWatcher"

        fun schedule(context: Context) {
            try {
                AdminBroadcastRepository.ensureBaselineInitialized(context)
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val request = PeriodicWorkRequestBuilder<AnnouncementWatcherWorker>(
                    15, TimeUnit.MINUTES,
                    5, TimeUnit.MINUTES
                )
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
                Log.d(TAG, "AnnouncementWatcherWorker planifié (toutes les 15 min)")
            } catch (e: Exception) {
                Log.w(TAG, "Erreur planification AnnouncementWatcherWorker: ${e.message}")
            }
        }

        fun cancel(context: Context) {
            try {
                WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
            } catch (_: Exception) {}
        }
    }

    override suspend fun doWork(): Result {
        return try {
            if (!SmartNotificationManager.isNotificationsEnabled(context) ||
                !SmartNotificationManager.areSystemNotificationsEnabled(context)
            ) {
                return Result.success()
            }

            val enabledCategories = SmartNotificationManager.getEnabledCategoryKeys(context)
            if (!enabledCategories.contains(NotificationCategory.ANNOUNCEMENTS.key)) {
                return Result.success()
            }

            val baselineMs = AdminBroadcastRepository.ensureBaselineInitialized(context)
            val result = AdminBroadcastRepository.fetchRecentAnnouncements(context, limit = 5)
            val list = result.getOrNull() ?: return Result.retry()

            for (announcement in list.reversed()) {
                val createdAtMs = announcement.createdAt?.toDate()?.time ?: System.currentTimeMillis()
                if (createdAtMs < baselineMs) continue
                if (AdminBroadcastRepository.isAnnouncementNotified(context, announcement.id)) continue

                val posted = SmartNotificationManager.postAdminBroadcastNotification(
                    context = context,
                    announcementId = announcement.id,
                    title = announcement.title,
                    body = announcement.message,
                    category = announcement.category,
                    targetUri = announcement.targetUri
                )
                if (posted) {
                    AdminBroadcastRepository.markAnnouncementNotified(context, announcement.id)
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Erreur vérification annonces en arrière-plan: ${e.message}")
            Result.retry()
        }
    }
}
