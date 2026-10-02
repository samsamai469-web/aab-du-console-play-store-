package com.example.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        MessageEntity::class,
        LinguisticKnowledge::class,
        UserProgress::class,
        LearnedWord::class,
        TranslationEntity::class,
        ConversationEntity::class,
        SecurityAuditLogEntity::class,
        AnonymousUserEntity::class,
        TranslationAnalyticsEntity::class
    ],
    version = 15,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun linguisticDao(): LinguisticDao
    abstract fun progressDao(): ProgressDao
    abstract fun translationDao(): TranslationDao
    abstract fun auditDao(): AuditDao
    abstract fun analyticsDao(): AnalyticsDao
}
