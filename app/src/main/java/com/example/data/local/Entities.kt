package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "messages")
@Serializable
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: String,
    val text: String,
    val sender: String, // "user" or "ai"
    val timestamp: Long = System.currentTimeMillis(),
    val sourcesJson: String = "" // JSON encoded List<WebSource>
)

@Serializable
data class WebSource(
    val title: String,
    val url: String,
    val domain: String = ""
)

@Entity(tableName = "linguistic_knowledge")
@Serializable
data class LinguisticKnowledge(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val word: String, // Mooré or French word
    val translation: String, // Translation in other language
    val language: String, // "Mooré", "Dioula", etc.
    val synonyms: String = "", // Comma separated
    val variants: String = "", // Comma separated
    val pronunciation: String = "",
    val category: String = "",
    val examples: String = "",
    val context: String = "",
    val source: String = "system",
    val status: String = "pending", // "pending", "validated", "rejected"
    val confidenceLevel: Int = 100, // 0-100
    val difficulty: String = "beginner", // "beginner", "intermediate", "advanced"
    val level: Int = 1,
    val dateAdded: Long = System.currentTimeMillis(),
    val dateModified: Long = System.currentTimeMillis(),
    val history: String = "" // JSON or text history of changes
)

@Entity(tableName = "user_progress", primaryKeys = ["userId", "language"])
@Serializable
data class UserProgress(
    val userId: String = "guest",
    val language: String, // "Mooré", "Dioula"
    val level: Int = 1,
    val totalWordsLearned: Int = 0,
    val points: Int = 0,
    val lastLessonDate: Long = System.currentTimeMillis()
)

@Entity(tableName = "learned_words")
@Serializable
data class LearnedWord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String = "guest",
    val wordId: Long,
    val masteryLevel: Int = 0, // 0 to 5
    val nextReviewDate: Long = System.currentTimeMillis()
)

@Entity(tableName = "translations")
@Serializable
data class TranslationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String = "guest",
    val originalText: String,
    val translatedText: String,
    val sourceLang: String,
    val targetLang: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isValidatedCorrection: Boolean = false,
    val correctedText: String? = null,
    val correctionDate: Long? = null,
    val adminNotes: String? = null,
    val isFavorite: Boolean = false
)

@Entity(tableName = "conversations")
@Serializable
data class ConversationEntity(
    @PrimaryKey val id: String,
    val userId: String = "guest",
    val title: String,
    val lastMessage: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "security_audit_logs")
@Serializable
data class SecurityAuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String, // e.g. "AUTH_SUCCESS", "AUTH_FAILURE", "ADMIN_2FA_VERIFIED", "DICTIONARY_MUTATION", "RATE_LIMIT_EXCEEDED", "PROMPT_INJECTION_BLOCKED", "IDOR_PREVENTED"
    val severity: String = "INFO", // "INFO", "WARNING", "CRITICAL"
    val actorUserId: String = "guest",
    val action: String,
    val targetResource: String = "",
    val details: String = "",
    val clientFingerprint: String = "device-local-sandbox"
)

@Entity(tableName = "anonymous_users")
@Serializable
data class AnonymousUserEntity(
    @PrimaryKey val anonymousId: String, // e.g. "anon_0798e21a-..."
    val firstSeenTimestamp: Long = System.currentTimeMillis(),
    val lastActiveTimestamp: Long = System.currentTimeMillis(),
    val totalSessionsCount: Int = 1,
    val totalTranslationsCount: Int = 0,
    val totalVoiceTranslationsCount: Int = 0,
    val totalTextTranslationsCount: Int = 0,
    val deviceModel: String = "",
    val androidVersion: String = "",
    val isRegisteredUser: Boolean = false,
    val userEmail: String? = null
)

@Entity(tableName = "translation_analytics")
@Serializable
data class TranslationAnalyticsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String = "anonymous", // anonymousId or userHash
    val sourceLang: String,
    val targetLang: String,
    val originalText: String = "",
    val translatedText: String = "",
    val deviceModel: String = "",
    val isVoice: Boolean = false, // true = dictée vocale, false = saisie clavier
    val isAiMode: Boolean = false, // true = mode IA cloud, false = dictionnaire local validé
    val textLength: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class LanguageCountStat(
    val language: String,
    val count: Int
)

@Serializable
data class DailyCountStat(
    val dayString: String,
    val count: Int
)
