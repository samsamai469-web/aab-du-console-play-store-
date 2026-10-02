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
import com.example.data.repository.NewsRepository
import java.util.concurrent.TimeUnit

/**
 * Worker en arrière-plan dédié à la surveillance automatique des nouvelles actualités du Burkina Faso (AIB, RTB).
 * Fonctionne même si l'application est fermée ou en veille.
 * Lorsqu'un nouvel article est détecté, une notification système de la catégorie NEWS est émise automatiquement.
 */
class NewsWatcherWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "NewsWatcherWorker"
        private const val NEWS_WORK_TAG = "SamNewsLiveWatcher"
        private const val PREFS_NEWS_WATCHER = "sam_news_watcher_prefs"
        private const val KEY_LAST_NOTIFIED_ARTICLE_ID = "last_notified_news_id"
        private const val KEY_LAST_NOTIFIED_TIMESTAMP = "last_notified_news_timestamp"

        /**
         * Enregistre et active la tâche périodique en arrière-plan.
         * Intervalle : toutes les 30 minutes avec vérification réseau connecté.
         */
        fun schedule(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                // Intervalle minimal autorisé par Android WorkManager : 15 minutes (avec flex 5 min)
                val newsWorkRequest = PeriodicWorkRequestBuilder<NewsWatcherWorker>(
                    15, TimeUnit.MINUTES,
                    5, TimeUnit.MINUTES
                )
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    NEWS_WORK_TAG,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    newsWorkRequest
                )
                Log.d(TAG, "Planification NewsWatcherWorker effectuée avec succès (toutes les 15-30 min)")
            } catch (e: Exception) {
                Log.e(TAG, "Erreur lors de la planification de NewsWatcherWorker", e)
            }
        }

        fun cancel(context: Context) {
            try {
                WorkManager.getInstance(context).cancelUniqueWork(NEWS_WORK_TAG)
            } catch (e: Exception) {
                Log.e(TAG, "Erreur lors de l'annulation de NewsWatcherWorker", e)
            }
        }
    }

    override suspend fun doWork(): Result {
        return try {
            checkAndNotifyNewArticles(context)
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Échec lors de la vérification des actualités en arrière-plan: ${e.message}")
            Result.retry()
        }
    }

    private suspend fun checkAndNotifyNewArticles(context: Context) {
        // 1. Vérifier si les notifications et la catégorie actualités sont autorisées
        if (!SmartNotificationManager.isNotificationsEnabled(context) ||
            !SmartNotificationManager.areSystemNotificationsEnabled(context)
        ) {
            return
        }

        val enabledCategories = SmartNotificationManager.getEnabledCategoryKeys(context)
        if (!enabledCategories.contains(NotificationCategory.NEWS.key)) {
            return
        }

        // 2. Récupérer les articles les plus récents depuis NewsRepository
        val articles: List<com.example.data.local.NewsArticle> = try {
            NewsRepository.getLiveNews(forceRefresh = true)
        } catch (e: Exception) {
            Log.w(TAG, "Impossible de récupérer les articles du Faso: ${e.message}")
            emptyList()
        }

        if (articles.isEmpty()) {
            return
        }

        val latestArticle = articles.firstOrNull() ?: return
        val prefs = context.getSharedPreferences(PREFS_NEWS_WATCHER, Context.MODE_PRIVATE)
        val lastNotifiedId = prefs.getString(KEY_LAST_NOTIFIED_ARTICLE_ID, "") ?: ""
        val lastNotifiedTimestamp = prefs.getLong(KEY_LAST_NOTIFIED_TIMESTAMP, 0L)

        // Si c'est la toute première fois qu'on initialise le worker, on mémorise l'ID sans spammer
        if (lastNotifiedId.isEmpty() && lastNotifiedTimestamp == 0L) {
            prefs.edit()
                .putString(KEY_LAST_NOTIFIED_ARTICLE_ID, latestArticle.id)
                .putLong(KEY_LAST_NOTIFIED_TIMESTAMP, latestArticle.timestamp)
                .apply()
            Log.d(TAG, "Initialisation de l'état du watcher avec l'article: ${latestArticle.title}")
            return
        }

        // 3. Détecter si un article est strictement nouveau
        val isNewArticle = latestArticle.id != lastNotifiedId && latestArticle.timestamp > lastNotifiedTimestamp

        if (isNewArticle) {
            Log.d(TAG, "Nouvelle actualité détectée : ${latestArticle.title} (${latestArticle.source})")

            val title = "🇧🇫 Flash Info Faso : ${latestArticle.source}"
            val content = latestArticle.title

            // Envoi de la notification intelligente pour la catégorie NEWS (immédiate même sur écran verrouillé)
            val sent = SmartNotificationManager.sendEventNotification(
                context = context,
                category = NotificationCategory.NEWS,
                title = title,
                content = content,
                targetUri = "sam-ai://news",
                forceEvenIfActive = true // Arrive immédiatement dès qu'une information tombe
            )

            if (sent) {
                prefs.edit()
                    .putString(KEY_LAST_NOTIFIED_ARTICLE_ID, latestArticle.id)
                    .putLong(KEY_LAST_NOTIFIED_TIMESTAMP, latestArticle.timestamp)
                    .apply()
                Log.d(TAG, "Notification d'actualité envoyée avec succès !")
            }
        }
    }
}
