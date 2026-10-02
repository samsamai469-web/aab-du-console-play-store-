package com.example.data.repository

import com.example.data.local.LearnedWord
import com.example.data.local.LinguisticKnowledge
import com.example.data.local.ProgressDao
import com.example.data.local.UserProgress
import kotlinx.coroutines.flow.Flow

class ProgressRepository(private val progressDao: ProgressDao) {

    fun getProgress(language: String, userId: String = "guest"): Flow<UserProgress?> =
        progressDao.getProgress(userId, language)

    suspend fun updateProgress(progress: UserProgress) = progressDao.updateProgress(progress)

    suspend fun markWordLearned(wordId: Long, points: Int, language: String, currentProgress: UserProgress?, userId: String = "guest") {
        progressDao.markWordAsLearned(LearnedWord(wordId = wordId, userId = userId))
        
        val newTotalWords = (currentProgress?.totalWordsLearned ?: 0) + 1
        val newPoints = (currentProgress?.points ?: 0) + points
        // Level up every 10 words
        val newLevel = (newTotalWords / 10) + 1
        
        val newProgress = currentProgress?.copy(
            userId = userId,
            totalWordsLearned = newTotalWords,
            points = newPoints,
            level = newLevel,
            lastLessonDate = System.currentTimeMillis()
        ) ?: UserProgress(
            userId = userId,
            language = language,
            totalWordsLearned = newTotalWords,
            points = newPoints,
            level = newLevel,
            lastLessonDate = System.currentTimeMillis()
        )
        
        progressDao.updateProgress(newProgress)
    }

    fun getLearnedCount(userId: String = "guest"): Flow<Int> = progressDao.getLearnedWordsCount(userId)

    suspend fun clearProgressForUser(userId: String) {
        progressDao.deleteAllProgressForUser(userId)
        progressDao.deleteAllLearnedWordsForUser(userId)
    }
}

