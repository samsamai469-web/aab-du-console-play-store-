package com.example.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.example.data.remote.OpenAiWhisperService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Locale

/**
 * Gestionnaire intelligent de reconnaissance vocale haute performance :
 * - Mode streaming en direct ultra-rapide (Google SpeechRecognizer) : écrit mot par mot en temps réel
 *   pendant que vous parlez ("Ça écrit automatiquement quand tu parles").
 * - Mode haute précision multilingue (Google Gemini via Firebase AI Logic) : activé automatiquement pour les langues nationales
 *   (Mooré, Dioula, etc.) ou en cas d'indisponibilité du service natif.
 */
class SpeechRecognitionManager(private val context: Context) {

    sealed class State {
        object Idle : State()
        object Listening : State()
        object Processing : State()
        data class Success(val text: String) : State()
        data class Error(val message: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val _soundLevel = MutableStateFlow(0f)
    val soundLevel: StateFlow<Float> = _soundLevel.asStateFlow()

    private val audioRecorder = AudioRecorderHelper(context)
    private val whisperService = OpenAiWhisperService(context)

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var amplitudeJob: Job? = null
    private var transcriptionJob: Job? = null

    private var speechRecognizer: SpeechRecognizer? = null
    private var isUsingNativeRecognizer = false

    private var activeOnResult: ((String) -> Unit)? = null
    private var activeOnError: ((String) -> Unit)? = null
    private var pendingStopCallback: ((String) -> Unit)? = null
    private var activeLanguage: String = "Français"
    private var isContinuousSession: Boolean = false
    @Volatile
    private var hasDetectedSpeechInSession: Boolean = false
    @Volatile
    private var maxSpeechAmpInSession: Int = 0

    companion object {
        private const val TAG = "SpeechRecognitionMgr"

        private val activeRecognizers = java.util.Collections.synchronizedSet(
            java.util.Collections.newSetFromMap(java.util.WeakHashMap<SpeechRecognitionManager, Boolean>())
        )

        /**
         * Coupe immédiatement tous les microphones de reconnaissance vocale actifs dès que SAM AI
         * commence à parler à haute voix pour empêcher toute auto-détection de sa propre voix.
         */
        fun pauseAllActiveRecognizersForTts() {
            val snapshot = synchronized(activeRecognizers) { activeRecognizers.toList() }
            snapshot.forEach { recognizer ->
                try {
                    if (recognizer._state.value is State.Listening) {
                        recognizer.reset()
                    }
                } catch (_: Exception) {}
            }
        }
    }

    init {
        activeRecognizers.add(this)
    }

    private fun isAfricanLanguage(lang: String): Boolean {
        val l = lang.trim().lowercase(Locale.ROOT)
        return l == "mos" || l == "dyu" || l == "ha" ||
            l.contains("moor") || l.contains("mòor") ||
            l.contains("dioul") || l.contains("jula") || l.contains("dyul") ||
            l.contains("haoussa") || l.contains("hausa") ||
            l.contains("fulf") || l.contains("gulm") || l.contains("bambara")
    }

    private fun getLanguageTag(lang: String): String {
        return when (lang.trim().lowercase(Locale.ROOT)) {
            "fr", "français", "francais", "french" -> "fr-FR"
            "en", "anglais", "english" -> "en-US"
            "es", "espagnol", "spanish" -> "es-ES"
            "de", "allemand", "german" -> "de-DE"
            "ar", "arabe", "arabic" -> "ar-SA"
            "it", "italien", "italian" -> "it-IT"
            "pt", "portugais", "portuguese" -> "pt-PT"
            "ru", "russe", "russian" -> "ru-RU"
            "zh", "chinois", "chinese" -> "zh-CN"
            "ha", "haoussa", "hausa" -> "ha-NE"
            else -> "fr-FR"
        }
    }

    /**
     * Démarre la reconnaissance vocale haute précision avec capture PCM 16kHz instantanée.
     */
    fun startListening(
        language: String = "fr-FR",
        continuousMode: Boolean = false,
        onResult: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        // En mode continu automatique (appel vocal de secours / conversation live), si SAM AI est
        // en train de parler en haut-parleur ou dans sa garde acoustique, on refuse d'ouvrir le micro
        if (continuousMode && TtsManager.isAnyTtsSpeakingOrCoolingDown(480L)) {
            reset()
            onError("Haut-parleur actif")
            return
        }
        if (!continuousMode) {
            TtsManager.stopAllActiveTts()
        }

        cancelCurrentJobs()
        activeOnResult = onResult
        activeOnError = onError
        activeLanguage = language
        isContinuousSession = continuousMode
        hasDetectedSpeechInSession = false
        maxSpeechAmpInSession = 0
        _partialText.value = ""
        _soundLevel.value = 0f
        _state.value = State.Listening

        // Capture directe PCM 16kHz sans perte + transcription Google Gemini (multilingue dont Mooré, Dioula, Haoussa, Français)
        startWhisperRecognition(language)
    }

    private fun startWhisperRecognition(language: String) {
        isUsingNativeRecognizer = false
        _partialText.value = ""
        hasDetectedSpeechInSession = false
        maxSpeechAmpInSession = 0

        val audioFile = audioRecorder.startRecording()
        if (audioFile == null) {
            val msg = "Impossible d'accéder au microphone de l'appareil."
            _state.value = State.Error(msg)
            activeOnError?.invoke(msg)
            return
        }

        amplitudeJob = scope.launch(Dispatchers.Default) {
            var speechDetected = false
            var speechStartTime = 0L
            var lastSoundTime = System.currentTimeMillis()
            val recordingStart = lastSoundTime
            var noiseFloor = 24f
            var peakSpeechAmp = 0
            var sampleFrameCount = 0

            while (isActive && _state.value is State.Listening) {
                val now = System.currentTimeMillis()
                val elapsedSinceStart = now - recordingStart

                // Protection anti-écho globale : en écoute continue ou après démarrage stabilisé,
                // si SAM AI prend la parole, on coupe pour éviter l'auto-capture
                if (TtsManager.isGlobalTtsSpeaking && (isContinuousSession || elapsedSinceStart > 350L)) {
                    withContext(Dispatchers.Main) {
                        reset()
                    }
                    break
                }

                val amp = audioRecorder.getMaxAmplitude()
                val normalized = (amp / 2600f).coerceIn(0f, 1f)
                if (amp > 0) sampleFrameCount++
                if (amp > maxSpeechAmpInSession) {
                    maxSpeechAmpInSession = amp
                }

                val onsetThreshold = maxOf(26, (noiseFloor * 1.45f).toInt()).coerceAtMost(150)

                // Calibration rapide du bruit ambiant de la pièce hors parole
                if (!speechDetected && amp in 1..140) {
                    noiseFloor = if (sampleFrameCount <= 2 && amp < 110) {
                        amp.toFloat().coerceIn(12f, 95f)
                    } else if (amp < onsetThreshold) {
                        ((noiseFloor * 0.88f) + (amp * 0.12f)).coerceIn(12f, 115f)
                    } else {
                        noiseFloor
                    }
                }

                val activeThreshold = if (speechDetected) {
                    maxOf(20, (noiseFloor * 1.25f).toInt(), (peakSpeechAmp * 0.15f).toInt().coerceAtMost(220))
                } else {
                    onsetThreshold
                }

                if (amp >= activeThreshold) {
                    if (amp > peakSpeechAmp) {
                        peakSpeechAmp = amp
                    }
                    if (!speechDetected) {
                        speechDetected = true
                        hasDetectedSpeechInSession = true
                        speechStartTime = now - 25L
                        lastSoundTime = now
                    } else {
                        lastSoundTime = now
                    }
                } else {
                    val silenceDuration = now - lastSoundTime
                    val voicedDuration = if (speechDetected) (lastSoundTime - speechStartTime) else 0L
                    val totalSpeechDuration = if (speechDetected) (now - speechStartTime) else 0L

                    // Si un simple clic mécanique (< 35 ms) a déclenché speechDetected suivi de silence, on réarme l'écoute
                    if (speechDetected && voicedDuration < 35L && silenceDuration >= 260L) {
                        speechDetected = false
                        peakSpeechAmp = 0
                    }

                    // Si aucune parole n'a été détectée après 12s, on informe l'utilisateur proprement
                    if (!speechDetected && elapsedSinceStart >= 12000L) {
                        withContext(Dispatchers.Main) {
                            cancelSilenceRecording()
                        }
                        break
                    }

                    // Détection naturelle de fin de phrase : laisse plus d'aisance rythmique en Mooré/Dioula
                    // tout en captant les réponses courtes ("Barka", "Awo", "Ayi", "N-ye").
                    val isAfrican = isAfricanLanguage(language)
                    val requiredSilenceMs = if (isAfrican) 1350L else 1150L
                    val minVoicedMs = if (isAfrican) 50L else 80L
                    if ((speechDetected && silenceDuration >= requiredSilenceMs && voicedDuration >= minVoicedMs) ||
                        (speechDetected && totalSpeechDuration >= 20000L)
                    ) {
                        withContext(Dispatchers.Main) {
                            stopWhisperRecognition(onFinished = null, manualStop = false)
                        }
                        break
                    }
                }

                withContext(Dispatchers.Main) {
                    _soundLevel.value = normalized
                }
                delay(20L)
            }
        }
    }

    private fun cancelSilenceRecording() {
        amplitudeJob?.cancel()
        amplitudeJob = null
        audioRecorder.cancel()
        _soundLevel.value = 0f
        _partialText.value = ""
        val err = "Aucune parole détectée."
        _state.value = State.Error(err)
        activeOnError?.invoke(err)
    }

    private fun deliverSuccess(text: String, onFinished: ((String) -> Unit)? = null) {
        val clean = text.trim()
        _state.value = State.Success(clean)
        _partialText.value = clean
        _soundLevel.value = 0f
        val resultCallback = activeOnResult
        val stopCb = pendingStopCallback
        activeOnResult = null
        pendingStopCallback = null
        when {
            onFinished != null -> onFinished.invoke(clean)
            stopCb != null -> stopCb.invoke(clean)
            else -> resultCallback?.invoke(clean)
        }
    }

    /**
     * Arrête l'écoute et finalise la transcription.
     */
    fun stopListening(onFinished: ((String) -> Unit)? = null) {
        when (val currentState = _state.value) {
            is State.Processing -> {
                if (onFinished != null) {
                    pendingStopCallback = onFinished
                    activeOnResult = onFinished
                }
                return
            }
            is State.Success -> {
                onFinished?.invoke(currentState.text.trim())
                return
            }
            !is State.Listening -> {
                onFinished?.invoke("")
                return
            }
            else -> {}
        }

        if (isUsingNativeRecognizer) {
            mainHandler.post {
                try {
                    speechRecognizer?.stopListening()
                } catch (_: Exception) {}
            }
            val current = _partialText.value.trim()
            if (current.isNotBlank()) {
                deliverSuccess(current, onFinished)
            }
        } else {
            stopWhisperRecognition(onFinished, manualStop = true)
        }
    }

    private fun stopWhisperRecognition(onFinished: ((String) -> Unit)?, manualStop: Boolean = true) {
        amplitudeJob?.cancel()
        amplitudeJob = null
        _soundLevel.value = 0f

        val hadNonZeroAudio = audioRecorder.hadNonZeroAudio()
        val voiceDetected = (hasDetectedSpeechInSession && maxSpeechAmpInSession >= 20) ||
            (manualStop && hadNonZeroAudio)
        val audioFile = audioRecorder.stopRecording()

        if (!voiceDetected || audioFile == null || !audioFile.exists() || audioFile.length() < 4000L) {
            try {
                audioFile?.delete()
            } catch (_: Exception) {}
            val err = if (!hadNonZeroAudio) {
                "Aucun signal micro capté. Vérifiez le microphone de l'appareil."
            } else {
                "Aucune parole détectée. Parlez puis réessayez."
            }
            _state.value = State.Error(err)
            _partialText.value = ""
            activeOnError?.invoke(err)
            onFinished?.invoke("")
            return
        }

        _state.value = State.Processing
        if (onFinished != null) {
            pendingStopCallback = onFinished
            activeOnResult = onFinished
        }

        transcriptionJob = scope.launch {
            val result = whisperService.transcribeAudio(audioFile, activeLanguage)
            result.onSuccess { transcribed ->
                val clean = transcribed.trim()
                if (clean.isNotBlank()) {
                    deliverSuccess(clean, onFinished)
                } else {
                    val err = "Aucune parole détectée."
                    _state.value = State.Error(err)
                    _partialText.value = ""
                    val stopCb = pendingStopCallback
                    pendingStopCallback = null
                    activeOnError?.invoke(err)
                    if (onFinished != null) onFinished.invoke("") else stopCb?.invoke("")
                }
            }.onFailure { err ->
                Log.e(TAG, "Erreur transcription audio Google Gemini: ${err.message}")
                val errMsg = when {
                    err.message?.contains("API") == true -> "Service vocal Google momentanément indisponible."
                    err.message?.contains("connexion") == true || err.message?.contains("réseau") == true -> "Vérifiez votre connexion Internet."
                    else -> err.localizedMessage ?: "Erreur de transcription audio."
                }
                _state.value = State.Error(errMsg)
                val stopCb = pendingStopCallback
                pendingStopCallback = null
                activeOnError?.invoke(errMsg)
                if (onFinished != null) onFinished.invoke("") else stopCb?.invoke("")
            }
        }
    }

    fun pauseListening() {
        stopListening()
    }

    fun reset() {
        cancelCurrentJobs()
        destroyNativeRecognizer()
        audioRecorder.cancel()
        _partialText.value = ""
        _soundLevel.value = 0f
        _state.value = State.Idle
        activeOnResult = null
        activeOnError = null
        pendingStopCallback = null
    }

    fun resetState() {
        reset()
    }

    private fun cancelCurrentJobs() {
        amplitudeJob?.cancel()
        amplitudeJob = null
        transcriptionJob?.cancel()
        transcriptionJob = null
    }

    private fun destroyNativeRecognizer() {
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (_: Exception) {}
        }
    }

    fun destroy() {
        reset()
        scope.cancel()
    }
}
