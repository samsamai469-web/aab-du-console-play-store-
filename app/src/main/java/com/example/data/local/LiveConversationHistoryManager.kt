package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.LiveConversationTurn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object LiveConversationHistoryManager {
    private const val PREFS_NAME = "live_conversation_history_prefs"
    private const val KEY_TURNS = "saved_live_turns_json"
    private const val KEY_LAST_CLEANUP = "last_weekly_cleanup_timestamp"
    private const val ONE_WEEK_MILLIS = 7L * 24 * 60 * 60 * 1000L // 7 jours

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Récupère tous les tours de conversation en supprimant automatiquement ceux de plus de 7 jours.
     */
    suspend fun getHistory(context: Context): List<LiveConversationTurn> = withContext(Dispatchers.IO) {
        val prefs = getPrefs(context)
        val rawJson = prefs.getString(KEY_TURNS, null) ?: return@withContext emptyList()
        try {
            val list = json.decodeFromString<List<LiveConversationTurn>>(rawJson)
            val now = System.currentTimeMillis()
            val cutoff = now - ONE_WEEK_MILLIS
            // Filtrer les messages de moins de 7 jours (réinitialisation automatique hebdomadaire)
            val validList = list.filter { it.timestamp >= cutoff }
            if (validList.size != list.size) {
                // Sauvegarder la liste nettoyée
                val cleanedJson = json.encodeToString(validList)
                prefs.edit().putString(KEY_TURNS, cleanedJson).apply()
            }
            validList.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Ajoute un nouvel échange dans l'historique et applique le nettoyage hebdomadaire automatique.
     */
    suspend fun saveTurn(context: Context, turn: LiveConversationTurn) = withContext(Dispatchers.IO) {
        val prefs = getPrefs(context)
        val currentList = getHistory(context).toMutableList()
        currentList.removeAll { it.id == turn.id }
        currentList.add(0, turn)
        
        val now = System.currentTimeMillis()
        val cutoff = now - ONE_WEEK_MILLIS
        val validList = currentList.filter { it.timestamp >= cutoff }

        val updatedJson = json.encodeToString(validList)
        prefs.edit()
            .putString(KEY_TURNS, updatedJson)
            .putLong(KEY_LAST_CLEANUP, now)
            .apply()
    }

    /**
     * Supprime un échange spécifique.
     */
    suspend fun deleteTurn(context: Context, turnId: String) = withContext(Dispatchers.IO) {
        val prefs = getPrefs(context)
        val currentList = getHistory(context).toMutableList()
        currentList.removeAll { it.id == turnId }
        val updatedJson = json.encodeToString(currentList)
        prefs.edit().putString(KEY_TURNS, updatedJson).apply()
    }

    /**
     * Efface manuellement tout l'historique.
     */
    suspend fun clearHistory(context: Context) = withContext(Dispatchers.IO) {
        val prefs = getPrefs(context)
        prefs.edit().remove(KEY_TURNS).apply()
    }
}
