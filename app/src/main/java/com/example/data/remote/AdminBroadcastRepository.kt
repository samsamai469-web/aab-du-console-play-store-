package com.example.data.remote

import android.content.Context
import android.util.Log
import androidx.annotation.Keep
import com.example.util.SecurityUtils
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@Keep
@IgnoreExtraProperties
data class AdminAnnouncement(
    @DocumentId val id: String = "",
    val title: String = "",
    val message: String = "",
    val category: String = "info",
    val targetUri: String = "sam-ai://notifications",
    val authorEmail: String = "",
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "title" to title,
            "message" to message,
            "category" to category,
            "targetUri" to targetUri,
            "authorEmail" to authorEmail,
            "active" to active,
            "createdAt" to (createdAt ?: FieldValue.serverTimestamp())
        )
        return map
    }
}

/**
 * Gère la diffusion des messages administrateur vers tous les utilisateurs :
 * 1. Stockage sécurisé dans Cloud Firestore (`announcements`)
 * 2. Abonnement automatique des appareils au topic FCM `all_users` (push instantané même appli fermée)
 * 3. Synchronisation de secours en arrière-plan & boîte de réception dans l'application
 */
object AdminBroadcastRepository {

    private const val TAG = "AdminBroadcastRepo"
    const val COLLECTION_ANNOUNCEMENTS = "announcements"
    const val TOPIC_ALL_USERS = "all_users"

    private const val PREFS_BROADCAST = "sam_admin_broadcast_prefs"
    private const val KEY_NOTIFIED_IDS = "notified_announcement_ids"
    private const val KEY_SEEN_IN_APP_IDS = "seen_in_app_announcement_ids"
    private const val KEY_FCM_SUBSCRIBED = "fcm_all_users_subscribed"
    private const val KEY_INSTALL_BASELINE_MS = "announcement_watcher_baseline_ms"

    private fun getPrefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_BROADCAST, Context.MODE_PRIVATE)

    private fun getFirestoreSafe(context: Context? = null): FirebaseFirestore? {
        if (context != null) {
            FirebaseAuthService.initContext(context)
        }
        return try {
            FirebaseFirestore.getInstance()
        } catch (e: Throwable) {
            Log.w(TAG, "Firestore indisponible: ${e.message}")
            null
        }
    }

    /**
     * Abonne cet appareil au canal FCM global `all_users` pour recevoir les messages admin
     * instantanément même lorsque l'application est complètement fermée.
     */
    fun subscribeToBroadcastTopic(context: Context) {
        try {
            FirebaseAuthService.initContext(context)
            ensureBaselineInitialized(context)
            FirebaseMessaging.getInstance()
                .subscribeToTopic(TOPIC_ALL_USERS)
                .addOnSuccessListener {
                    getPrefs(context).edit().putBoolean(KEY_FCM_SUBSCRIBED, true).apply()
                    Log.d(TAG, "Appareil abonné au topic FCM '$TOPIC_ALL_USERS'")
                }
                .addOnFailureListener { err ->
                    Log.w(TAG, "Abonnement topic FCM différé: ${err.message}")
                }
        } catch (t: Throwable) {
            Log.w(TAG, "Impossible d'initialiser l'abonnement FCM: ${t.message}")
        }
    }

    fun isFcmTopicSubscribed(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_FCM_SUBSCRIBED, false)
    }

    fun ensureBaselineInitialized(context: Context): Long {
        val prefs = getPrefs(context)
        val existing = prefs.getLong(KEY_INSTALL_BASELINE_MS, 0L)
        if (existing > 0L) return existing
        // Mémorise l'heure de première initialisation moins 2 minutes pour ne pas spammer d'anciennes annonces
        val baseline = System.currentTimeMillis() - 120_000L
        prefs.edit().putLong(KEY_INSTALL_BASELINE_MS, baseline).apply()
        return baseline
    }

    fun isFirebaseAdminSignedIn(): Boolean {
        return try {
            val user = FirebaseAuth.getInstance().currentUser
            val email = user?.email?.trim()?.lowercase().orEmpty()
            user != null && SecurityUtils.isSuperAdminEmail(email)
        } catch (_: Throwable) {
            false
        }
    }

    fun getCurrentFirebaseEmail(): String? {
        return try {
            FirebaseAuth.getInstance().currentUser?.email
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Publie un nouveau message administrateur dans Firestore (`announcements`).
     * Le déclencheur Cloud Function (`onAnnouncementCreated`) envoie ensuite
     * la notification push FCM au topic `all_users` même si l'application est fermée.
     */
    suspend fun publishAnnouncement(
        context: Context,
        title: String,
        message: String,
        category: String = "info",
        targetUri: String = "sam-ai://notifications"
    ): Result<AdminAnnouncement> = withContext(Dispatchers.IO) {
        val cleanTitle = title.trim()
        val cleanMessage = message.trim()
        if (cleanTitle.length < 3) {
            return@withContext Result.failure(IllegalArgumentException("Le titre doit contenir au moins 3 caractères."))
        }
        if (cleanTitle.length > 80) {
            return@withContext Result.failure(IllegalArgumentException("Le titre ne doit pas dépasser 80 caractères."))
        }
        if (cleanMessage.length < 5) {
            return@withContext Result.failure(IllegalArgumentException("Le message doit contenir au moins 5 caractères."))
        }
        if (cleanMessage.length > 500) {
            return@withContext Result.failure(IllegalArgumentException("Le message ne doit pas dépasser 500 caractères."))
        }

        val allowedCategory = when (category.lowercase().trim()) {
            "important", "urgent" -> "important"
            "update", "mise_a_jour" -> "update"
            else -> "info"
        }

        val allowedUri = if (targetUri.startsWith("sam-ai://")) targetUri else "sam-ai://notifications"

        val firestore = getFirestoreSafe(context)
            ?: return@withContext Result.failure(IllegalStateException("Service cloud Firebase indisponible sur cet appareil."))

        val currentUser = try {
            FirebaseAuth.getInstance().currentUser
        } catch (_: Throwable) {
            null
        }

        if (currentUser == null || !SecurityUtils.isSuperAdminEmail(currentUser.email)) {
            return@withContext Result.failure(
                IllegalStateException(
                    "Connectez-vous d'abord dans Profil avec le compte Google créateur (${SecurityUtils.PRIMARY_ADMIN_EMAIL}) pour autoriser l'envoi cloud."
                )
            )
        }

        try {
            val payload = mapOf(
                "title" to cleanTitle,
                "message" to cleanMessage,
                "category" to allowedCategory,
                "targetUri" to allowedUri,
                "authorEmail" to (currentUser.email ?: SecurityUtils.PRIMARY_ADMIN_EMAIL),
                "active" to true,
                "createdAt" to FieldValue.serverTimestamp()
            )

            val docRef = withTimeoutOrNull(12_000L) {
                firestore.collection(COLLECTION_ANNOUNCEMENTS).add(payload).await()
            } ?: return@withContext Result.failure(
                IllegalStateException("Délai dépassé lors de l'envoi. Vérifiez votre connexion Internet.")
            )

            // Éviter que l'admin ne reçoive sa propre annonce en double via le worker
            markAnnouncementNotified(context, docRef.id)
            markAnnouncementSeenInApp(context, docRef.id)

            val created = AdminAnnouncement(
                id = docRef.id,
                title = cleanTitle,
                message = cleanMessage,
                category = allowedCategory,
                targetUri = allowedUri,
                authorEmail = currentUser.email ?: SecurityUtils.PRIMARY_ADMIN_EMAIL,
                active = true,
                createdAt = Timestamp.now()
            )
            Result.success(created)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur publication annonce: ${e.message}", e)
            val msg = e.message.orEmpty()
            val friendly = when {
                msg.contains("PERMISSION_DENIED", ignoreCase = true) ->
                    "Accès refusé par les règles Firebase : vérifiez que vous êtes connecté avec Google (${SecurityUtils.PRIMARY_ADMIN_EMAIL}) et que les règles Firestore sont déployées."
                msg.contains("UNAVAILABLE", ignoreCase = true) || msg.contains("network", ignoreCase = true) ->
                    "Réseau indisponible. Vérifiez votre connexion Internet puis réessayez."
                else -> e.localizedMessage ?: "Échec de l'envoi du message."
            }
            Result.failure(Exception(friendly, e))
        }
    }

    /**
     * Récupère les dernières annonces publiées par l'administrateur.
     */
    suspend fun fetchRecentAnnouncements(
        context: Context,
        limit: Long = 20
    ): Result<List<AdminAnnouncement>> = withContext(Dispatchers.IO) {
        val firestore = getFirestoreSafe(context)
            ?: return@withContext Result.failure(IllegalStateException("Firestore indisponible."))

        try {
            val snapshot = withTimeoutOrNull(10_000L) {
                firestore.collection(COLLECTION_ANNOUNCEMENTS)
                    .whereEqualTo("active", true)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(limit)
                    .get()
                    .await()
            }

            if (snapshot != null) {
                val items = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(AdminAnnouncement::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        ?.copy(id = doc.id)
                }
                return@withContext Result.success(items)
            }

            return@withContext Result.failure(IllegalStateException("Délai d'attente dépassé."))
        } catch (e: Exception) {
            // Repli sans orderBy si l'index composite Firestore n'est pas encore créé
            try {
                val fallbackSnap = withTimeoutOrNull(8_000L) {
                    firestore.collection(COLLECTION_ANNOUNCEMENTS)
                        .whereEqualTo("active", true)
                        .limit(limit)
                        .get()
                        .await()
                }
                if (fallbackSnap != null) {
                    val items = fallbackSnap.documents.mapNotNull { doc ->
                        doc.toObject(AdminAnnouncement::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            ?.copy(id = doc.id)
                    }.sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                    return@withContext Result.success(items)
                }
            } catch (_: Exception) {}

            Log.w(TAG, "Erreur lecture annonces: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun deleteAnnouncement(context: Context, announcementId: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (announcementId.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Identifiant d'annonce invalide."))
        }
        val firestore = getFirestoreSafe(context)
            ?: return@withContext Result.failure(IllegalStateException("Firestore indisponible."))

        try {
            withTimeoutOrNull(10_000L) {
                firestore.collection(COLLECTION_ANNOUNCEMENTS)
                    .document(announcementId)
                    .delete()
                    .await()
            } ?: return@withContext Result.failure(IllegalStateException("Délai dépassé lors de la suppression."))
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur suppression annonce: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun isAnnouncementNotified(context: Context, id: String): Boolean {
        if (id.isBlank()) return false
        val set = getPrefs(context).getStringSet(KEY_NOTIFIED_IDS, emptySet()) ?: emptySet()
        return id in set
    }

    fun markAnnouncementNotified(context: Context, id: String) {
        if (id.isBlank()) return
        val prefs = getPrefs(context)
        val current = prefs.getStringSet(KEY_NOTIFIED_IDS, emptySet())?.toMutableList() ?: mutableListOf()
        if (id !in current) {
            current.add(id)
            if (current.size > 60) {
                current.subList(0, current.size - 60).clear()
            }
            prefs.edit().putStringSet(KEY_NOTIFIED_IDS, current.toSet()).apply()
        }
    }

    fun isAnnouncementSeenInApp(context: Context, id: String): Boolean {
        if (id.isBlank()) return true
        val set = getPrefs(context).getStringSet(KEY_SEEN_IN_APP_IDS, emptySet()) ?: emptySet()
        return id in set
    }

    fun markAnnouncementSeenInApp(context: Context, id: String) {
        if (id.isBlank()) return
        val prefs = getPrefs(context)
        val current = prefs.getStringSet(KEY_SEEN_IN_APP_IDS, emptySet())?.toMutableList() ?: mutableListOf()
        if (id !in current) {
            current.add(id)
            if (current.size > 60) {
                current.subList(0, current.size - 60).clear()
            }
            prefs.edit().putStringSet(KEY_SEEN_IN_APP_IDS, current.toSet()).apply()
        }
    }
}
