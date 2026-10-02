package com.example.data.remote

import com.example.data.model.SamAiChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SamAiFirebaseHistoryService {
    private val inMemoryChatHistory = mutableMapOf<String, MutableList<SamAiChatMessage>>()

    suspend fun loadWeeklyChatHistory(userId: String): List<SamAiChatMessage> = withContext(Dispatchers.IO) {
        val list = inMemoryChatHistory[userId] ?: emptyList()
        val sevenDaysAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)
        list.filter { it.timestamp >= sevenDaysAgo }
    }

    suspend fun saveChatMessage(userId: String, message: SamAiChatMessage) = withContext(Dispatchers.IO) {
        val list = inMemoryChatHistory.getOrPut(userId) { mutableListOf() }
        list.add(message)
    }

    suspend fun saveMessage(userId: String, message: SamAiChatMessage) {
        saveChatMessage(userId, message)
    }

    suspend fun clearUserChatHistory(userId: String) = withContext(Dispatchers.IO) {
        inMemoryChatHistory.remove(userId)
    }
}
