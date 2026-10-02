package com.example.util

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Système de logs diagnostic en temps réel pour le pipeline vocal de Sam Traduction.
 * Strictement conforme aux règles de sécurité : Ne logge AUCUNE clé API, token ou donnée sensible.
 */
object LiveTranslateLogger {

    private const val SYSTEM_TAG = "SamVoicePipeline"
    private const val MAX_LOG_ENTRIES = 100

    data class LogEntry(
        val timestamp: String,
        val stage: Stage,
        val message: String,
        val isError: Boolean = false
    )

    enum class Stage(val tag: String, val badge: String) {
        INIT("[INIT]", "🚀"),
        WEBSOCKET("[WS]", "🌐"),
        AUDIO_INPUT("[MIC/AUDIO]", "🎙️"),
        TRANSCRIPTION("[TRANSCRIPTION]", "📝"),
        TRANSLATION("[TRANSLATION]", "🌍"),
        AUDIO_OUTPUT("[AUDIO_OUT]", "🔊"),
        STATE("[ETAT]", "⚡"),
        ERROR("[ERREUR]", "⚠️")
    }

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    private val _logsFlow = MutableStateFlow<List<LogEntry>>(emptyList())
    val logsFlow = _logsFlow.asStateFlow()

    private fun addEntry(stage: Stage, rawMessage: String, isError: Boolean = false) {
        val sanitized = sanitize(rawMessage)
        val entry = LogEntry(
            timestamp = timeFormat.format(Date()),
            stage = stage,
            message = sanitized,
            isError = isError
        )

        // Logcat Android
        if (isError) {
            Log.e(SYSTEM_TAG, "${stage.tag} $sanitized")
        } else {
            Log.i(SYSTEM_TAG, "${stage.tag} $sanitized")
        }

        // Buffer mémoire UI
        val current = _logsFlow.value.toMutableList()
        if (current.size >= MAX_LOG_ENTRIES) {
            current.removeAt(0)
        }
        current.add(entry)
        _logsFlow.value = current
    }

    fun logInit(message: String) = addEntry(Stage.INIT, message)

    fun logWebSocket(message: String) = addEntry(Stage.WEBSOCKET, message)

    fun logAudioInput(message: String) = addEntry(Stage.AUDIO_INPUT, message)

    fun logTranscription(isFinal: Boolean, text: String) {
        val status = if (isFinal) "Finale" else "Partielle"
        val cleanPreview = text.take(60).replace("\n", " ")
        addEntry(Stage.TRANSCRIPTION, "$status: \"$cleanPreview\"")
    }

    fun logTranslation(srcLang: String, tgtLang: String, text: String) {
        val cleanPreview = text.take(60).replace("\n", " ")
        addEntry(Stage.TRANSLATION, "($srcLang → $tgtLang): \"$cleanPreview\"")
    }

    fun logAudioOutput(message: String) = addEntry(Stage.AUDIO_OUTPUT, message)

    fun logState(stateName: String) = addEntry(Stage.STATE, "Nouvel état -> $stateName")

    fun logError(message: String) = addEntry(Stage.ERROR, message, isError = true)

    fun clearLogs() {
        _logsFlow.value = emptyList()
    }

    /**
     * Masque strictement toute clé d'API, token ou chaîne d'authentification potentielle.
     */
    private fun sanitize(input: String): String {
        return input
            .replace(Regex("key=[A-Za-z0-9_\\-\\.]+"), "key=***REDACTED***")
            .replace(Regex("AIza[0-9A-Za-z-_]{35}"), "***REDACTED_API_KEY***")
            .replace(Regex("Bearer\\s+[A-Za-z0-9_\\-\\.]+"), "Bearer ***REDACTED***")
    }
}
