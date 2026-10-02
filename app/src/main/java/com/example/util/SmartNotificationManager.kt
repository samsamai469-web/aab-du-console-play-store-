package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Vibrator
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.MainActivity
import java.util.concurrent.TimeUnit

object SmartNotificationManager {

    // Stockage persistant au niveau de l'appareil (jamais supprimé lors de la déconnexion d'un compte)
    private const val PREFS_NAME = "sam_notifications_permanent_prefs"
    private const val KEY_NOTIFICATIONS_ENABLED = "smart_notifications_enabled"
    private const val KEY_FREQUENCY_LEVEL = "smart_notification_frequency" // "1", "2", "3", "5", or "low", "normal", "high"
    private const val KEY_SOUND_ENABLED = "notification_sound"
    private const val KEY_VIBRATION_ENABLED = "notification_vibration"
    private const val KEY_LAST_SENT_TIMESTAMP = "smart_notif_last_sent_timestamp"
    private const val KEY_RECENT_SENT_IDS = "smart_notif_recent_sent_ids"
    private const val KEY_LAST_ACTIVE_TIMESTAMP = "user_last_active_timestamp"
    private const val KEY_FIRST_PROMPT_DONE = "smart_notif_first_prompt_shown_v2"

    // Intervalles dynamiques adaptés selon la fréquence choisie par jour (1, 2, 3, ou 5 notifs/jour)
    fun getMinIntervalMillis(context: Context): Long {
        return when (getFrequencyLevel(context)) {
            "1" -> 8 * 60 * 60 * 1000L             // 8 heures (1 notification par jour)
            "2", "low" -> 4 * 60 * 60 * 1000L      // 4 heures (2 notifications par jour)
            "3" -> 2 * 60 * 60 * 1000L             // 2 heures (3 notifications par jour)
            "5", "normal", "medium", "high" -> 75 * 60 * 1000L // 1 heure 15 min (5 notifications par jour)
            else -> 2 * 60 * 60 * 1000L
        }
    }

    fun getDailyTargetCount(context: Context): Int {
        return when (getFrequencyLevel(context)) {
            "1" -> 1
            "2", "low" -> 2
            "3" -> 3
            "5", "normal", "medium", "high" -> 5
            else -> 3
        }
    }

    // Durée d'inactivité minimale (3 minutes) pour ne pas notifier un utilisateur en train d'interagir
    const val MIN_INACTIVITY_BEFORE_NOTIFICATION = 3 * 60 * 1000L

    private const val WORK_TAG = "SamSmartDailyReminder"

    fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Vérifie si les notifications sont accordées au niveau du système d'exploitation Android
     */
    fun areSystemNotificationsEnabled(context: Context): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    /**
     * Ouvre directement la page des réglages système de notifications pour l'application
     */
    fun openSystemNotificationSettings(context: Context) {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
            } catch (ex: Exception) {
                // Log and ignore
            }
        }
    }

    fun isNotificationsEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_NOTIFICATIONS_ENABLED, true)
    }

    fun setNotificationsEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply()
        if (enabled) {
            scheduleSmartNotifications(context)
        } else {
            cancelSmartNotifications(context)
        }
    }

    fun getFrequencyLevel(context: Context): String {
        return getPrefs(context).getString(KEY_FREQUENCY_LEVEL, "normal") ?: "normal"
    }

    fun setFrequencyLevel(context: Context, level: String) {
        getPrefs(context).edit().putString(KEY_FREQUENCY_LEVEL, level).apply()
        if (isNotificationsEnabled(context)) {
            scheduleSmartNotifications(context)
        }
    }

    fun isSoundEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SOUND_ENABLED, true)
    }

    fun setSoundEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply()
    }

    fun isVibrationEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_VIBRATION_ENABLED, true)
    }

    fun setVibrationEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_VIBRATION_ENABLED, enabled).apply()
    }

    /**
     * Règle absolue du cahier des charges :
     * La demande d'autorisation ne doit apparaître qu'une seule et unique fois sur l'appareil.
     * Si l'utilisateur l'a vue (acceptée ou refusée), elle ne réapparaîtra jamais,
     * même après fermeture/réouverture ou déconnexion/reconnexion.
     */
    fun isFirstPromptShown(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_FIRST_PROMPT_DONE, false)
    }

    fun setFirstPromptShown(context: Context, shown: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_FIRST_PROMPT_DONE, shown).apply()
    }

    /**
     * Enregistre l'instant où l'utilisateur a interagi avec l'application.
     * Permet d'éviter d'envoyer des notifications si l'utilisateur est déjà actif.
     */
    fun recordUserActivity(context: Context) {
        getPrefs(context).edit().putLong(KEY_LAST_ACTIVE_TIMESTAMP, System.currentTimeMillis()).apply()
    }

    private fun isUserCurrentlyActive(context: Context): Boolean {
        val lastActive = getPrefs(context).getLong(KEY_LAST_ACTIVE_TIMESTAMP, 0L)
        val elapsed = System.currentTimeMillis() - lastActive
        return elapsed < MIN_INACTIVITY_BEFORE_NOTIFICATION
    }

    /**
     * Récupère l'ensemble des catégories activées par l'utilisateur
     */
    fun getEnabledCategoryKeys(context: Context): Set<String> {
        val prefs = getPrefs(context)
        val set = mutableSetOf<String>()
        if (prefs.getBoolean("cat_greeting", true)) set.add("greeting")
        if (prefs.getBoolean("cat_return", true)) set.add("return")
        if (prefs.getBoolean("cat_motivation", true)) set.add("motivation")
        if (prefs.getBoolean("cat_word_of_day", true)) set.add("word_of_day")
        if (prefs.getBoolean("cat_tips", true)) set.add("tips")
        if (prefs.getBoolean("cat_loyalty", true)) set.add("loyalty")
        if (prefs.getBoolean("cat_news_culture", true)) set.add("news_culture")
        if (prefs.getBoolean("cat_news", true)) set.add("news")
        if (prefs.getBoolean("cat_quiz", true)) set.add("quiz")
        if (prefs.getBoolean("cat_rewards", true)) set.add("rewards")
        if (prefs.getBoolean("cat_announcements", true)) set.add("announcements")
        
        // Rétrocompatibilité avec les anciennes clés si nécessaires
        if (prefs.getBoolean("cat_apprentissage", true)) set.add("motivation")
        if (prefs.getBoolean("cat_traductions", true)) set.add("tips")
        return set
    }

    fun setCategoryEnabled(context: Context, categoryKey: String, enabled: Boolean) {
        getPrefs(context).edit().putBoolean("cat_$categoryKey", enabled).apply()
    }

    /**
     * Récupère la liste des identifiants récemment envoyés (anti-répétition)
     */
    private fun getRecentSentIds(context: Context): MutableSet<String> {
        val serialized = getPrefs(context).getString(KEY_RECENT_SENT_IDS, "") ?: ""
        if (serialized.isEmpty()) return mutableSetOf()
        return serialized.split(",").filter { it.isNotBlank() }.toMutableSet()
    }

    private fun recordSentId(context: Context, messageId: String) {
        val currentIds = getRecentSentIds(context)
        currentIds.add(messageId)
        // Garder maximum 100 identifiants récents pour économiser la mémoire et permettre un cycle naturel
        val trimmed = if (currentIds.size > 100) currentIds.drop(currentIds.size - 100).toSet() else currentIds
        val serialized = trimmed.joinToString(",")
        getPrefs(context).edit()
            .putString(KEY_RECENT_SENT_IDS, serialized)
            .putLong(KEY_LAST_SENT_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    /**
     * Planifie les tâches périodiques WorkManager selon le niveau de fréquence choisi
     */
    fun scheduleSmartNotifications(context: Context) {
        if (!isNotificationsEnabled(context)) {
            cancelSmartNotifications(context)
            return
        }

        val frequency = getFrequencyLevel(context)
        // WorkManager periodic work request :
        // - 1 par jour : intervalle 24h, flex 2h
        // - 2 par jour : intervalle 12h, flex 1h
        // - 3 par jour : intervalle 8h, flex 45m
        // - 5 par jour : intervalle 4h30, flex 30m
        val (intervalHours, flexMinutes) = when (frequency) {
            "1" -> Pair(24L, 120L)
            "2", "low" -> Pair(12L, 60L)
            "3" -> Pair(8L, 45L)
            "5", "high", "normal", "medium" -> Pair(4L, 30L)
            else -> Pair(8L, 45L)
        }

        val periodicWorkRequest = PeriodicWorkRequestBuilder<ReminderWorker>(
            intervalHours, TimeUnit.HOURS,
            flexMinutes, TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_TAG,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicWorkRequest
        )

        // Active également la surveillance automatique en arrière-plan des actualités
        NewsWatcherWorker.schedule(context)
    }

    fun cancelSmartNotifications(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_TAG)
        NewsWatcherWorker.cancel(context)
    }

    /**
     * Exécute le traitement d'une notification périodique intelligente
     */
    fun processScheduledNotification(context: Context): Boolean {
        if (!isNotificationsEnabled(context)) {
            return false
        }

        // 1. Ne jamais envoyer une notification si l'utilisateur est déjà actif dans l'application
        if (isUserCurrentlyActive(context)) {
            return false
        }

        // 2. Vérifier l'intervalle minimum selon la fréquence configurée
        val lastSent = getPrefs(context).getLong(KEY_LAST_SENT_TIMESTAMP, 0L)
        val elapsed = System.currentTimeMillis() - lastSent
        val minInterval = getMinIntervalMillis(context)
        if (elapsed < minInterval && lastSent > 0) {
            return false
        }

        // 3. Sélectionner un message intelligent anti-répétition
        val enabledCategories = getEnabledCategoryKeys(context)
        val recentIds = getRecentSentIds(context)
        val message = SmartNotificationLibrary.selectNextSmartMessage(enabledCategories, recentIds)

        // 4. Émettre la notification
        val posted = postNotification(context, message, isTest = false)
        if (posted) {
            recordSentId(context, message.id)
        }
        return posted
    }

    /**
     * Déclenche une notification d'aperçu immédiate pour que l'utilisateur teste le rendu
     */
    fun triggerPreviewNotification(context: Context): SmartNotificationMessage {
        val enabledCategories = getEnabledCategoryKeys(context)
        val recentIds = getRecentSentIds(context)
        val message = SmartNotificationLibrary.selectNextSmartMessage(enabledCategories, recentIds)
        postNotification(context, message, isTest = true)
        return message
    }

    fun sendTestNotificationNow(context: Context): SmartNotificationMessage {
        return triggerPreviewNotification(context)
    }

    private fun getNotificationBitmap(context: Context): android.graphics.Bitmap? {
        return try {
            val drawable = ContextCompat.getDrawable(context, com.example.R.drawable.sam_ai_logo) ?: return null
            val bitmap = android.graphics.Bitmap.createBitmap(
                192, 192,
                android.graphics.Bitmap.Config.ARGB_8888
            )
            val canvas = android.graphics.Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Crée et affiche la notification native
     */
    private fun postNotification(context: Context, message: SmartNotificationMessage, isTest: Boolean): Boolean {
        val soundEnabled = isSoundEnabled(context)
        val vibrationEnabled = isVibrationEnabled(context)

        val channelId = "sam_smart_reminder_v2" +
                (if (soundEnabled) "_sound" else "_silent") +
                (if (vibrationEnabled) "_vib" else "_novib")

        val channelName = "Rappels intelligents SAM AI"

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = if (soundEnabled) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = "Notifications personnalisées et mots du jour pour SAM AI"
                enableLights(true)
                lightColor = 0xFF0070FF.toInt()
                if (vibrationEnabled) {
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 250, 150, 250)
                } else {
                    enableVibration(false)
                }
                if (soundEnabled) {
                    val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    setSound(defaultSoundUri, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
                } else {
                    setSound(null, null)
                }
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(message.targetUri), context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            if (isTest) 9999 else message.id.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val largeIconBitmap = getNotificationBitmap(context)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(com.example.R.drawable.sam_ai_logo)
            .setColor(0xFF0070FF.toInt())
            .setContentTitle(message.title)
            .setContentText(message.content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.content))
            .setPriority(if (soundEnabled) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (largeIconBitmap != null) {
            builder.setLargeIcon(largeIconBitmap)
        }

        if (vibrationEnabled) {
            builder.setVibrate(longArrayOf(0, 250, 150, 250))
        } else {
            builder.setVibrate(longArrayOf(0L))
        }

        if (!soundEnabled) {
            builder.setSilent(true)
        }

        // Vérification des permissions sous Android 13+ (TIRAMISU)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }

        val notificationId = if (isTest) 7777 else (message.id.hashCode() and 0x7FFFFFFF)
        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        return true
    }

    /**
     * Envoie une notification intelligente ciblée liée à un événement réel de l'application
     * (Quiz complété, tâche IA terminée, alerte actualité Burkina Faso, annonce officielle).
     * Respecte rigoureusement :
     * 1. L'autorisation système et l'interrupteur général
     * 2. La catégorie activée ou désactivée par l'utilisateur
     * 3. L'anti-spam (évite de spammer si l'utilisateur a désactivé la thématique)
     */
    fun sendEventNotification(
        context: Context,
        category: NotificationCategory,
        title: String,
        content: String,
        targetUri: String = "sam-ai://home",
        forceEvenIfActive: Boolean = false
    ): Boolean {
        if (!isNotificationsEnabled(context) || !areSystemNotificationsEnabled(context)) {
            return false
        }
        val enabledCategories = getEnabledCategoryKeys(context)
        if (!enabledCategories.contains(category.key)) {
            return false
        }
        if (!forceEvenIfActive && isUserCurrentlyActive(context)) {
            // Respecte la quiétude de l'utilisateur actif
            return false
        }
        val message = SmartNotificationMessage(
            id = "evt_${System.currentTimeMillis()}",
            category = category,
            timeOfDay = TimeOfDay.ANY,
            title = title,
            content = content,
            targetUri = targetUri
        )
        recordSentId(context, message.id)
        return postNotification(context, message, isTest = false)
    }
}
