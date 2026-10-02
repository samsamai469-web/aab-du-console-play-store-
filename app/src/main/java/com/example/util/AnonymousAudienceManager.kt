package com.example.util

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.example.data.local.AnalyticsDao
import com.example.data.local.AnonymousUserEntity
import com.example.data.local.DailyCountStat
import com.example.data.local.LanguageCountStat
import com.example.data.local.TranslationAnalyticsEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Gestionnaire d'audience et de statistiques 100% anonymes et persistantes.
 * Toutes les données sont enregistrées de façon permanente dans la base de données Room (SQLite).
 * - Identifiant utilisateur anonyme persistant (UUID) par appareil.
 * - Ne remet jamais les statistiques à 0 au redémarrage de l'application.
 * - Mesure : Total utilisateurs uniques, Actifs aujourd'hui, cette semaine, ce mois, Nouveaux utilisateurs.
 * - Mesure : Total traductions, Traductions vocales vs écrites, Répartition par langue.
 * - Graphique d'évolution dans le temps des utilisateurs et des traductions.
 */
class AnonymousAudienceManager(
    context: Context,
    private val analyticsDao: AnalyticsDao? = null
) {
    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO)

    val currentAnonymousUserId: String

    init {
        currentAnonymousUserId = getOrCreatePermanentAnonymousUserId()
        recordSessionLaunch()
    }

    data class AudienceStats(
        val anonymousUserId: String,
        val firstLaunchFormatted: String,
        val lastActiveFormatted: String,
        val totalUniqueUsers: Int = 0,
        val activeUsersToday: Int = 0,
        val activeUsersWeek: Int = 0,
        val activeUsersMonth: Int = 0,
        val newUsersToday: Int = 0,
        val totalTranslations: Int = 0,
        val voiceTranslations: Int = 0,
        val textTranslations: Int = 0,
        val mooreCount: Int = 0,
        val dioulaCount: Int = 0,
        val hausaCount: Int = 0,
        val frenchCount: Int = 0,
        val englishCount: Int = 0,
        val otherCount: Int = 0,
        val topLanguages: List<LanguageCountStat> = emptyList(),
        val dailyTranslationsTimeline: List<DailyCountStat> = emptyList(),
        val dailyActiveUsersTimeline: List<DailyCountStat> = emptyList(),
        val recentTranslations: List<TranslationAnalyticsEntity> = emptyList(),
        val realUsersList: List<AnonymousUserEntity> = emptyList(),
        val deviceModel: String,
        val androidVersion: String
    )

    private fun getStartOfDay(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun getStartOfWeek(): Long {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -7)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun getStartOfMonth(): Long {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -30)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun getOrCreatePermanentAnonymousUserId(): String {
        var id = prefs.getString(KEY_ANONYMOUS_USER_ID, null)
        if (id.isNullOrBlank()) {
            val randomUuid = UUID.randomUUID().toString()
            id = "anon_$randomUuid"
            prefs.edit()
                .putString(KEY_ANONYMOUS_USER_ID, id)
                .putLong(KEY_FIRST_SEEN_TIME, System.currentTimeMillis())
                .apply()
        }
        return id
    }

    private fun recordSessionLaunch() {
        val now = System.currentTimeMillis()
        prefs.edit().putLong(KEY_LAST_ACTIVE_TIME, now).apply()

        if (analyticsDao != null) {
            scope.launch {
                try {
                    val deviceModel = "${Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }} ${Build.MODEL}"
                    val androidVer = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
                    val existing = analyticsDao.getUserById(currentAnonymousUserId)
                    val userToSave = if (existing != null) {
                        existing.copy(
                            lastActiveTimestamp = now,
                            totalSessionsCount = existing.totalSessionsCount + 1,
                            deviceModel = deviceModel,
                            androidVersion = androidVer
                        )
                    } else {
                        val firstSeen = prefs.getLong(KEY_FIRST_SEEN_TIME, now)
                        AnonymousUserEntity(
                            anonymousId = currentAnonymousUserId,
                            firstSeenTimestamp = firstSeen,
                            lastActiveTimestamp = now,
                            totalSessionsCount = 1,
                            totalTranslationsCount = 0,
                            totalVoiceTranslationsCount = 0,
                            totalTextTranslationsCount = 0,
                            deviceModel = deviceModel,
                            androidVersion = androidVer
                        )
                    }
                    analyticsDao.insertOrUpdateUser(userToSave)
                } catch (_: Exception) {
                }
            }
        }
    }

    /**
     * Retourne le nombre d'utilisateurs locaux enregistrés dans la base sécurisée Room
     */
    suspend fun syncCloudData(): Int {
        val dao = analyticsDao ?: return 0
        return dao.getTotalUniqueUsersCountDirect()
    }

    /**
     * Enregistre un quiz complété pour mettre à jour la dernière activité de l'utilisateur
     */
    fun recordQuizCompleted(language: String, score: Int = 10) {
        val now = System.currentTimeMillis()
        prefs.edit().putLong(KEY_LAST_ACTIVE_TIME, now).apply()
        if (analyticsDao != null) {
            scope.launch {
                try {
                    val existing = analyticsDao.getUserById(currentAnonymousUserId)
                    if (existing != null) {
                        val updated = existing.copy(lastActiveTimestamp = now)
                        analyticsDao.insertOrUpdateUser(updated)
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    /**
     * Enregistre une traduction de façon persistante dans SQLite Room sécurisé
     */
    fun recordTranslation(
        sourceLang: String,
        targetLang: String,
        originalText: String = "",
        translatedText: String = "",
        isVoice: Boolean = false,
        isAi: Boolean = false,
        textLength: Int = 0
    ) {
        val now = System.currentTimeMillis()
        prefs.edit().putLong(KEY_LAST_ACTIVE_TIME, now).apply()
        val deviceModel = "${Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }} ${Build.MODEL}"

        if (analyticsDao != null) {
            scope.launch {
                try {
                    val transEntity = TranslationAnalyticsEntity(
                        userId = currentAnonymousUserId,
                        sourceLang = sourceLang,
                        targetLang = targetLang,
                        originalText = originalText,
                        translatedText = translatedText,
                        deviceModel = deviceModel,
                        isVoice = isVoice,
                        isAiMode = isAi,
                        textLength = if (textLength > 0) textLength else originalText.length,
                        timestamp = now
                    )
                    analyticsDao.insertTranslation(transEntity)

                    // Mettre à jour les compteurs de l'utilisateur anonyme
                    val existing = analyticsDao.getUserById(currentAnonymousUserId)
                    val updatedUser = if (existing != null) {
                        existing.copy(
                            lastActiveTimestamp = now,
                            totalTranslationsCount = existing.totalTranslationsCount + 1,
                            totalVoiceTranslationsCount = if (isVoice) existing.totalVoiceTranslationsCount + 1 else existing.totalVoiceTranslationsCount,
                            totalTextTranslationsCount = if (!isVoice) existing.totalTextTranslationsCount + 1 else existing.totalTextTranslationsCount
                        )
                    } else null

                    if (updatedUser != null) {
                        analyticsDao.insertOrUpdateUser(updatedUser)
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    // --- REPOSITORY FLOW FOR LIVE ADMIN DASHBOARD OBSERVATION ---
    val audienceStatsFlow: Flow<AudienceStats> = if (analyticsDao == null) {
        flowOf(buildFallbackStats())
    } else {
        val startDay = getStartOfDay()
        val startWeek = getStartOfWeek()
        val startMonth = getStartOfMonth()
        val startChart = startMonth

        combine(
            combine(
                analyticsDao.getTotalUniqueUsersCount(),
                analyticsDao.getActiveUsersCountSince(startDay),
                analyticsDao.getActiveUsersCountSince(startWeek),
                analyticsDao.getActiveUsersCountSince(startMonth),
                analyticsDao.getNewUsersCountSince(startDay),
                analyticsDao.getTotalTranslationsCount()
            ) { a -> a },
            combine(
                analyticsDao.getVoiceTranslationsCount(),
                analyticsDao.getTextTranslationsCount(),
                analyticsDao.getTargetLanguageStats(),
                analyticsDao.getDailyTranslationsChart(startChart),
                analyticsDao.getDailyActiveUsersChart(startChart),
                analyticsDao.getRecentTranslations(100),
                analyticsDao.getRecentUsers(100)
            ) { b -> b }
        ) { chunkA, chunkB ->
            val totalUsers = (chunkA[0] as Int).coerceAtLeast(1)
            val activeToday = (chunkA[1] as Int).coerceAtLeast(1)
            val activeWeek = (chunkA[2] as Int).coerceAtLeast(1)
            val activeMonth = (chunkA[3] as Int).coerceAtLeast(1)
            val newUsersToday = chunkA[4] as Int
            val totalTranslations = chunkA[5] as Int

            val voiceTranslations = chunkB[0] as Int
            val textTranslations = chunkB[1] as Int
            @Suppress("UNCHECKED_CAST")
            val targetLangs = chunkB[2] as List<LanguageCountStat>
            @Suppress("UNCHECKED_CAST")
            val dailyTrans = chunkB[3] as List<DailyCountStat>
            @Suppress("UNCHECKED_CAST")
            val dailyUsers = chunkB[4] as List<DailyCountStat>
            @Suppress("UNCHECKED_CAST")
            val recentTrans = chunkB[5] as List<TranslationAnalyticsEntity>
            @Suppress("UNCHECKED_CAST")
            val usersList = chunkB[6] as List<AnonymousUserEntity>

            var moore = 0
            var dioula = 0
            var hausa = 0
            var french = 0
            var english = 0
            var other = 0

            targetLangs.forEach { stat ->
                when (stat.language.lowercase().trim()) {
                    "mooré", "moore" -> moore += stat.count
                    "dioula" -> dioula += stat.count
                    "haoussa", "hausa" -> hausa += stat.count
                    "français", "francais", "french" -> french += stat.count
                    "anglais", "english" -> english += stat.count
                    else -> other += stat.count
                }
            }

            val sdf = SimpleDateFormat("dd MMMM yyyy à HH:mm", Locale.FRANCE)
            val firstSeen = prefs.getLong(KEY_FIRST_SEEN_TIME, System.currentTimeMillis())
            val lastActive = prefs.getLong(KEY_LAST_ACTIVE_TIME, System.currentTimeMillis())
            val deviceModel = "${Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }} ${Build.MODEL}"
            val androidVer = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

            AudienceStats(
                anonymousUserId = currentAnonymousUserId,
                firstLaunchFormatted = sdf.format(Date(firstSeen)),
                lastActiveFormatted = sdf.format(Date(lastActive)),
                totalUniqueUsers = totalUsers,
                activeUsersToday = activeToday,
                activeUsersWeek = activeWeek,
                activeUsersMonth = activeMonth,
                newUsersToday = newUsersToday,
                totalTranslations = totalTranslations,
                voiceTranslations = voiceTranslations,
                textTranslations = textTranslations,
                mooreCount = moore,
                dioulaCount = dioula,
                hausaCount = hausa,
                frenchCount = french,
                englishCount = english,
                otherCount = other,
                topLanguages = targetLangs,
                dailyTranslationsTimeline = dailyTrans,
                dailyActiveUsersTimeline = dailyUsers,
                recentTranslations = recentTrans,
                realUsersList = usersList,
                deviceModel = deviceModel,
                androidVersion = androidVer
            )
        }
    }

    private fun buildFallbackStats(): AudienceStats {
        val sdf = SimpleDateFormat("dd MMMM yyyy à HH:mm", Locale.FRANCE)
        val firstSeen = prefs.getLong(KEY_FIRST_SEEN_TIME, System.currentTimeMillis())
        val lastActive = prefs.getLong(KEY_LAST_ACTIVE_TIME, System.currentTimeMillis())
        val deviceModel = "${Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }} ${Build.MODEL}"
        val androidVer = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

        return AudienceStats(
            anonymousUserId = currentAnonymousUserId,
            firstLaunchFormatted = sdf.format(Date(firstSeen)),
            lastActiveFormatted = sdf.format(Date(lastActive)),
            totalUniqueUsers = 1,
            activeUsersToday = 1,
            activeUsersWeek = 1,
            activeUsersMonth = 1,
            newUsersToday = 1,
            totalTranslations = 0,
            voiceTranslations = 0,
            textTranslations = 0,
            deviceModel = deviceModel,
            androidVersion = androidVer
        )
    }

    companion object {
        private const val PREFS_NAME = "sam_permanent_anonymous_analytics_prefs"
        private const val KEY_ANONYMOUS_USER_ID = "anonymous_unique_user_uuid"
        private const val KEY_FIRST_SEEN_TIME = "user_first_seen_timestamp"
        private const val KEY_LAST_ACTIVE_TIME = "user_last_active_timestamp"

        val LOCKED_STATS = AudienceStats(
            anonymousUserId = "ACCÈS_RESTREINT_AUTHENTIFICATION_ADMIN_REQUISE",
            firstLaunchFormatted = "Protégé",
            lastActiveFormatted = "Protégé",
            totalUniqueUsers = 0,
            activeUsersToday = 0,
            activeUsersWeek = 0,
            activeUsersMonth = 0,
            newUsersToday = 0,
            totalTranslations = 0,
            voiceTranslations = 0,
            textTranslations = 0,
            mooreCount = 0,
            dioulaCount = 0,
            hausaCount = 0,
            frenchCount = 0,
            englishCount = 0,
            otherCount = 0,
            topLanguages = emptyList(),
            dailyTranslationsTimeline = emptyList(),
            dailyActiveUsersTimeline = emptyList(),
            recentTranslations = emptyList(),
            deviceModel = "Espace Réservé Administrateur",
            androidVersion = "Protégé"
        )
    }

    /**
     * Flux sécurisé des statistiques :
     * Ne transmet les données réelles QUE si un jeton administrateur vérifié est fourni.
     * Pour tout utilisateur standard ou invité, renvoie un flux d'état restreint/vide (Zero Trust).
     */
    fun getSecureAudienceStatsFlow(adminToken: String?): Flow<AudienceStats> {
        if (!SecurityUtils.verifyAdminToken(adminToken)) {
            return flowOf(LOCKED_STATS)
        }
        return audienceStatsFlow
    }

    /**
     * Outil de Test pour l'Administrateur :
     * Simule l'insertion dans SQLite Room de plusieurs utilisateurs anonymes et traductions
     * avec des dates réalistes pour tester la persistance et le graphique.
     * EXCLUSIVEMENT RÉSERVÉ À L'ADMINISTRATEUR AVEC JETON VALIDE.
     */
    fun simulateTestData(adminToken: String?, additionalUsers: Int = 15, translationsPerUser: Int = 4): Boolean {
        if (!SecurityUtils.verifyAdminToken(adminToken)) {
            return false
        }
        if (analyticsDao == null) return false
        scope.launch {
            try {
                val now = System.currentTimeMillis()
                val users = mutableListOf<AnonymousUserEntity>()
                val translations = mutableListOf<TranslationAnalyticsEntity>()
                val random = Random()
                val languages = listOf("Mooré", "Dioula", "Haoussa", "Français", "Anglais")
                val sampleTexts = listOf("Bonjour", "Comment vas-tu ?", "Merci beaucoup", "Au revoir", "La famille va bien", "Bon travail", "Bonne journée")

                val models = listOf("Samsung Galaxy A54", "Tecno Camon 20", "Infinix Note 30", "Xiaomi Redmi 12", "Google Pixel 7")

                for (i in 1..additionalUsers) {
                    val daysAgo = random.nextInt(10)
                    val userTimestamp = now - (daysAgo * 24L * 3600L * 1000L) - (random.nextInt(3600) * 1000L)
                    val simId = "anon_${UUID.randomUUID()}"
                    val userTransCount = random.nextInt(translationsPerUser) + 1
                    val voiceCount = random.nextInt(userTransCount + 1)
                    val textCount = userTransCount - voiceCount

                    users.add(
                        AnonymousUserEntity(
                            anonymousId = simId,
                            firstSeenTimestamp = userTimestamp,
                            lastActiveTimestamp = userTimestamp + (random.nextInt(7200) * 1000L),
                            totalSessionsCount = random.nextInt(5) + 1,
                            totalTranslationsCount = userTransCount,
                            totalVoiceTranslationsCount = voiceCount,
                            totalTextTranslationsCount = textCount,
                            deviceModel = models[random.nextInt(models.size)],
                            androidVersion = "Android ${11 + random.nextInt(4)} (API ${30 + random.nextInt(4)})"
                        )
                    )

                    for (j in 1..userTransCount) {
                        val transTimestamp = userTimestamp + (j * 60000L)
                        val src = if (random.nextBoolean()) "Français" else languages[random.nextInt(3)]
                        val tgt = if (src == "Français") languages[random.nextInt(3)] else "Français"
                        val isVoice = j <= voiceCount
                        val transPairs = listOf(
                            ("Bonjour" to "Ne yibeogo" to "Mooré"),
                            ("Comment vas-tu ?" to "Yi béogo wa ?" to "Mooré"),
                            ("Merci beaucoup" to "Barka wusgo" to "Mooré"),
                            ("Au revoir" to "Wend na kô-d laafi" to "Mooré"),
                            ("Bonne arrivée" to "I ni sogoma" to "Dioula"),
                            ("Merci infiniment" to "I ni tché" to "Dioula"),
                            ("Comment est la famille ?" to "Sômogôw bè dji ?" to "Dioula"),
                            ("Bonne journée" to "Sannu da rana" to "Haoussa"),
                            ("Bienvenue au Faso" to "Barkâ wusgo Faso" to "Mooré")
                        )
                        val pair = transPairs[random.nextInt(transPairs.size)]
                        val model = models[random.nextInt(models.size)]

                        translations.add(
                            TranslationAnalyticsEntity(
                                userId = simId,
                                sourceLang = "Français",
                                targetLang = pair.second,
                                originalText = pair.first.first,
                                translatedText = pair.first.second,
                                deviceModel = model,
                                isVoice = isVoice,
                                isAiMode = random.nextBoolean(),
                                textLength = pair.first.first.length,
                                timestamp = transTimestamp
                            )
                        )
                    }
                }

                analyticsDao.insertUsers(users)
                analyticsDao.insertTranslations(translations)
            } catch (_: Exception) {
            }
        }
        return true
    }

    /**
     * Réinitialise les statistiques enregistrées (sauf l'utilisateur courant de l'appareil).
     * EXCLUSIVEMENT RÉSERVÉ À L'ADMINISTRATEUR AVEC JETON VALIDE.
     */
    fun resetAnalyticsData(adminToken: String?): Boolean {
        if (!SecurityUtils.verifyAdminToken(adminToken)) {
            return false
        }
        if (analyticsDao == null) return false
        scope.launch {
            try {
                analyticsDao.clearAllTranslations()
                analyticsDao.clearAllUsers()
                // Ré-insérer uniquement l'utilisateur local actuel
                recordSessionLaunch()
            } catch (_: Exception) {
            }
        }
        return true
    }
}
