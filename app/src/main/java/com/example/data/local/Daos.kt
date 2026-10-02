package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun getMessagesListForConversation(conversationId: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Query("SELECT * FROM conversations WHERE userId = :userId ORDER BY timestamp DESC")
    fun getConversationsForUser(userId: String): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations ORDER BY timestamp DESC")
    fun getConversations(): Flow<List<ConversationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity): Long

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: String): Int

    @Query("DELETE FROM conversations WHERE id = :conversationId")
    suspend fun deleteConversation(conversationId: String): Int

    @Query("DELETE FROM conversations WHERE userId = :userId")
    suspend fun deleteAllConversationsForUser(userId: String): Int

    @Query("DELETE FROM messages WHERE conversationId IN (SELECT id FROM conversations WHERE userId = :userId)")
    suspend fun deleteAllMessagesForUser(userId: String): Int

    @Query("UPDATE conversations SET title = :title WHERE id = :conversationId")
    suspend fun renameConversation(conversationId: String, title: String): Int
}

@Dao
interface LinguisticDao {
    @Query("SELECT * FROM linguistic_knowledge ORDER BY word ASC")
    fun getAllKnowledge(): Flow<List<LinguisticKnowledge>>

    @Query("""
        SELECT * FROM linguistic_knowledge 
        WHERE (LOWER(word) = LOWER(:text) 
           OR LOWER(translation) = LOWER(:text) 
           OR LOWER(variants) LIKE '%' || LOWER(:text) || '%' 
           OR LOWER(synonyms) LIKE '%' || LOWER(:text) || '%')
          AND (LOWER(language) = LOWER(:targetLang) OR :targetLang = 'Français' OR :targetLang = 'Auto-détection' OR :targetLang = '')
        ORDER BY 
          CASE WHEN status = 'validated' THEN 0 ELSE 1 END,
          CASE WHEN LOWER(word) = LOWER(:text) OR LOWER(translation) = LOWER(:text) THEN 0 ELSE 1 END,
          confidenceLevel DESC, 
          id ASC 
        LIMIT 1
    """)
    suspend fun findBestMatch(text: String, targetLang: String): LinguisticKnowledge?

    @Query("""
        SELECT * FROM linguistic_knowledge 
        WHERE status = 'validated' 
          AND (LOWER(word) IN (:words) OR LOWER(translation) IN (:words))
    """)
    suspend fun findMatchesForWords(words: List<String>): List<LinguisticKnowledge>

    @Query("SELECT * FROM linguistic_knowledge WHERE (word LIKE '%' || :query || '%' OR translation LIKE '%' || :query || '%' OR synonyms LIKE '%' || :query || '%' OR variants LIKE '%' || :query || '%') AND language = :language ORDER BY CASE WHEN status = 'validated' THEN 0 ELSE 1 END, dateModified DESC")
    fun searchKnowledgeWithPriority(query: String, language: String): Flow<List<LinguisticKnowledge>>

    @Query("SELECT * FROM linguistic_knowledge WHERE difficulty = :difficulty AND status = 'validated' AND id NOT IN (SELECT wordId FROM learned_words WHERE userId = :userId) ORDER BY RANDOM() LIMIT :limit")
    suspend fun getLessonWords(difficulty: String, userId: String, limit: Int): List<LinguisticKnowledge>

    @Query("SELECT * FROM linguistic_knowledge WHERE id != :excludeId ORDER BY RANDOM() LIMIT :limit")
    suspend fun getRandomWords(limit: Int, excludeId: Long): List<LinguisticKnowledge>

    @Query("SELECT COUNT(*) FROM linguistic_knowledge")
    suspend fun getCount(): Int

    @Query("SELECT * FROM linguistic_knowledge WHERE LOWER(word) = LOWER(:word) AND LOWER(translation) = LOWER(:translation) LIMIT 1")
    suspend fun findExact(word: String, translation: String): LinguisticKnowledge?

    @Query("SELECT * FROM linguistic_knowledge WHERE LOWER(translation) = LOWER(:frenchText) AND LOWER(language) = LOWER(:targetLang) ORDER BY CASE WHEN status = 'validated' THEN 0 ELSE 1 END, confidenceLevel DESC LIMIT 1")
    suspend fun findByFrenchAndLang(frenchText: String, targetLang: String): LinguisticKnowledge?

    @Query("SELECT * FROM linguistic_knowledge WHERE LOWER(word) = LOWER(:wordText) AND LOWER(language) = LOWER(:sourceLang) ORDER BY CASE WHEN status = 'validated' THEN 0 ELSE 1 END, confidenceLevel DESC LIMIT 1")
    suspend fun findByWordAndLang(wordText: String, sourceLang: String): LinguisticKnowledge?

    @Query("SELECT * FROM linguistic_knowledge WHERE LOWER(translation) = LOWER(:frenchText) ORDER BY CASE WHEN status = 'validated' THEN 0 ELSE 1 END, confidenceLevel DESC LIMIT 1")
    suspend fun findByFrenchExact(frenchText: String): LinguisticKnowledge?

    @Query("SELECT * FROM linguistic_knowledge WHERE LOWER(word) = LOWER(:mooreText) AND LOWER(language) = 'mooré' ORDER BY CASE WHEN status = 'validated' THEN 0 ELSE 1 END, confidenceLevel DESC LIMIT 1")
    suspend fun findByMooreExact(mooreText: String): LinguisticKnowledge?

    @Query("DELETE FROM linguistic_knowledge WHERE LOWER(translation) = LOWER(:frenchText)")
    suspend fun deleteByFrench(frenchText: String): Int

    @Query("DELETE FROM linguistic_knowledge WHERE LOWER(word) = LOWER(:mooreText)")
    suspend fun deleteByMoore(mooreText: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKnowledge(knowledge: LinguisticKnowledge): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<LinguisticKnowledge>): List<Long>

    @Query("UPDATE linguistic_knowledge SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String): Int

    @Query("UPDATE linguistic_knowledge SET status = :status, dateModified = :timestamp, history = :history WHERE id = :id")
    suspend fun updateStatusWithHistory(id: Long, status: String, history: String, timestamp: Long = System.currentTimeMillis()): Int

    @Query("DELETE FROM linguistic_knowledge WHERE id = :id")
    suspend fun deleteKnowledge(id: Long): Int
}

@Dao
interface ProgressDao {
    @Query("SELECT * FROM user_progress WHERE userId = :userId AND language = :language")
    fun getProgress(userId: String, language: String): Flow<UserProgress?>

    @Query("SELECT * FROM user_progress WHERE userId = :userId")
    fun getAllProgressForUser(userId: String): Flow<List<UserProgress>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateProgress(progress: UserProgress): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markWordAsLearned(learnedWord: LearnedWord): Long

    @Query("SELECT COUNT(*) FROM learned_words WHERE userId = :userId")
    fun getLearnedWordsCount(userId: String): Flow<Int>

    @Query("DELETE FROM user_progress WHERE userId = :userId")
    suspend fun deleteAllProgressForUser(userId: String): Int

    @Query("DELETE FROM learned_words WHERE userId = :userId")
    suspend fun deleteAllLearnedWordsForUser(userId: String): Int
}

@Dao
interface TranslationDao {
    @Query("SELECT * FROM translations WHERE userId = :userId ORDER BY timestamp DESC")
    fun getTranslationsForUser(userId: String): Flow<List<TranslationEntity>>

    @Query("SELECT * FROM translations ORDER BY timestamp DESC")
    fun getAllTranslations(): Flow<List<TranslationEntity>>

    @Query("""
        SELECT * FROM translations 
        WHERE LOWER(TRIM(originalText)) = LOWER(TRIM(:text)) 
          AND LOWER(sourceLang) = LOWER(:srcLang) 
          AND LOWER(targetLang) = LOWER(:tgtLang) 
          AND isValidatedCorrection = 1 
        ORDER BY timestamp DESC 
        LIMIT 1
    """)
    suspend fun findValidatedCorrection(text: String, srcLang: String, tgtLang: String): TranslationEntity?

    @Query("UPDATE translations SET isValidatedCorrection = :isValidated, correctedText = :correctedText, correctionDate = :date WHERE id = :id")
    suspend fun updateValidation(id: Long, isValidated: Boolean, correctedText: String?, date: Long? = System.currentTimeMillis()): Int

    @Query("UPDATE translations SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean): Int


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranslation(translation: TranslationEntity): Long

    @Query("DELETE FROM translations WHERE id = :id")
    suspend fun deleteTranslation(id: Long): Int

    @Query("DELETE FROM translations WHERE userId = :userId")
    suspend fun deleteAllTranslationsForUser(userId: String): Int

    @Query("DELETE FROM translations")
    suspend fun deleteAllTranslations(): Int

    @Query("SELECT * FROM translations WHERE id = :id AND userId = :userId LIMIT 1")
    suspend fun getTranslationByIdAndUser(id: Long, userId: String): TranslationEntity?

    @Query("DELETE FROM translations WHERE id = :id AND userId = :userId")
    suspend fun deleteTranslationSecure(id: Long, userId: String): Int
}

@Dao
interface AuditDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: SecurityAuditLogEntity): Long

    @Query("SELECT * FROM security_audit_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 100): Flow<List<SecurityAuditLogEntity>>

    @Query("SELECT * FROM security_audit_logs WHERE severity IN ('WARNING', 'CRITICAL') ORDER BY timestamp DESC LIMIT :limit")
    fun getSecurityAlerts(limit: Int = 50): Flow<List<SecurityAuditLogEntity>>

    @Query("SELECT COUNT(*) FROM security_audit_logs WHERE eventType = :eventType AND actorUserId = :userId AND timestamp > :sinceTimestamp")
    suspend fun countEventsForUser(eventType: String, userId: String, sinceTimestamp: Long): Int

    @Query("DELETE FROM security_audit_logs WHERE timestamp < :olderThanTimestamp")
    suspend fun purgeOldLogs(olderThanTimestamp: Long): Int
}

@Dao
interface AnalyticsDao {
    // Unique Users counts
    @Query("SELECT COUNT(DISTINCT anonymousId) FROM anonymous_users")
    fun getTotalUniqueUsersCount(): Flow<Int>

    @Query("SELECT COUNT(DISTINCT anonymousId) FROM anonymous_users")
    suspend fun getTotalUniqueUsersCountDirect(): Int

    @Query("SELECT COUNT(DISTINCT anonymousId) FROM anonymous_users WHERE firstSeenTimestamp >= :sinceTimestamp")
    fun getNewUsersCountSince(sinceTimestamp: Long): Flow<Int>

    @Query("SELECT COUNT(DISTINCT anonymousId) FROM anonymous_users WHERE lastActiveTimestamp >= :sinceTimestamp")
    fun getActiveUsersCountSince(sinceTimestamp: Long): Flow<Int>

    // Translations counts
    @Query("SELECT COUNT(*) FROM translation_analytics")
    fun getTotalTranslationsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM translation_analytics")
    suspend fun getTotalTranslationsCountDirect(): Int

    @Query("SELECT COUNT(*) FROM translation_analytics WHERE isVoice = 1")
    fun getVoiceTranslationsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM translation_analytics WHERE isVoice = 0")
    fun getTextTranslationsCount(): Flow<Int>

    // Top Languages used
    @Query("""
        SELECT targetLang as language, COUNT(*) as count 
        FROM translation_analytics 
        GROUP BY targetLang 
        ORDER BY count DESC
    """)
    fun getTargetLanguageStats(): Flow<List<LanguageCountStat>>

    @Query("""
        SELECT sourceLang as language, COUNT(*) as count 
        FROM translation_analytics 
        WHERE sourceLang != 'Auto-détection'
        GROUP BY sourceLang 
        ORDER BY count DESC
    """)
    fun getSourceLanguageStats(): Flow<List<LanguageCountStat>>

    // Daily Timeline for Chart
    @Query("""
        SELECT strftime('%Y-%m-%d', datetime(timestamp / 1000, 'unixepoch')) as dayString, COUNT(*) as count 
        FROM translation_analytics 
        WHERE timestamp >= :sinceTimestamp 
        GROUP BY dayString 
        ORDER BY dayString ASC
    """)
    fun getDailyTranslationsChart(sinceTimestamp: Long): Flow<List<DailyCountStat>>

    @Query("""
        SELECT strftime('%Y-%m-%d', datetime(lastActiveTimestamp / 1000, 'unixepoch')) as dayString, COUNT(DISTINCT anonymousId) as count 
        FROM anonymous_users 
        WHERE lastActiveTimestamp >= :sinceTimestamp 
        GROUP BY dayString 
        ORDER BY dayString ASC
    """)
    fun getDailyActiveUsersChart(sinceTimestamp: Long): Flow<List<DailyCountStat>>

    // Recent activity log / stream
    @Query("SELECT * FROM translation_analytics ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentTranslations(limit: Int = 200): Flow<List<TranslationAnalyticsEntity>>

    @Query("""
        SELECT * FROM translation_analytics 
        WHERE originalText LIKE '%' || :query || '%' 
           OR translatedText LIKE '%' || :query || '%' 
           OR sourceLang LIKE '%' || :query || '%' 
           OR targetLang LIKE '%' || :query || '%' 
           OR deviceModel LIKE '%' || :query || '%' 
           OR userId LIKE '%' || :query || '%'
        ORDER BY timestamp DESC 
        LIMIT 200
    """)
    fun searchTranslations(query: String): Flow<List<TranslationAnalyticsEntity>>

    @Query("SELECT * FROM anonymous_users ORDER BY lastActiveTimestamp DESC LIMIT :limit")
    fun getRecentUsers(limit: Int = 50): Flow<List<AnonymousUserEntity>>

    // User lookup and mutation
    @Query("SELECT * FROM anonymous_users WHERE anonymousId = :id LIMIT 1")
    suspend fun getUserById(id: String): AnonymousUserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateUser(user: AnonymousUserEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<AnonymousUserEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranslation(translation: TranslationAnalyticsEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranslations(translations: List<TranslationAnalyticsEntity>): List<Long>

    @Query("DELETE FROM anonymous_users")
    suspend fun clearAllUsers(): Int

    @Query("DELETE FROM translation_analytics")
    suspend fun clearAllTranslations(): Int
}

