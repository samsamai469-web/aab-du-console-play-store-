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
            var lastStrongSpeechTime = lastSoundTime
            val recordingStart = lastSoundTime
            var noiseFloor = 26f
            var noiseCalibrated = false
            var peakSpeechAmp = 0
            var consecutiveOnsetFrames = 0

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
                val normalized = (amp / 1100f).coerceIn(0f, 1f)
                if (amp > maxSpeechAmpInSession) {
                    maxSpeechAmpInSession = amp
                }

                // Calibre le bruit ambiant uniquement sur les niveaux calmes (<= 180) pour qu'une parole
                // qui démarre immédiatement ne fasse jamais exploser le seuil d'entrée
                if (!speechDetected && elapsedSinceStart >= 60L && amp in 1..180) {
                    noiseFloor = if (!noiseCalibrated) {
                        noiseCalibrated = true
                        amp.toFloat().coerceIn(6f, 120f)
                    } else if (amp < noiseFloor) {
                        ((noiseFloor * 0.78f) + (amp * 0.22f)).coerceIn(6f, 160f)
                    } else if (amp < noiseFloor * 1.45f) {
                        ((noiseFloor * 0.90f) + (amp * 0.10f)).coerceIn(6f, 160f)
                    } else {
                        noiseFloor
                    }
                }

                val onsetThreshold = maxOf(18, (noiseFloor * 1.65f).toInt(), (noiseFloor + 12f).toInt()).coerceAtMost(240)
                val activeThreshold = if (speechDetected) {
                    maxOf(
                        14,
                        (noiseFloor * 1.30f).toInt(),
                        (noiseFloor + 8f).toInt(),
                        (peakSpeechAmp * 0.08f).toInt().coerceAtMost(110)
                    )
                } else {
                    onsetThreshold
                }
                val strongSpeechThreshold = if (speechDetected) {
                    maxOf(onsetThreshold, (peakSpeechAmp * 0.14f).toInt().coerceAtMost(180))
                } else {
                    onsetThreshold
                }

                if ((!speechDetected && amp >= onsetThreshold) || (speechDetected && amp >= strongSpeechThreshold)) {
                    consecutiveOnsetFrames++
                    lastStrongSpeechTime = now
                    if (amp > peakSpeechAmp) {
                        peakSpeechAmp = amp
                    }
                    if (!speechDetected && (consecutiveOnsetFrames >= 2 || amp >= maxOf(90, onsetThreshold * 2))) {
                        speechDetected = true
                        hasDetectedSpeechInSession = true
                        speechStartTime = now - 40L
                        lastSoundTime = now
                    } else if (speechDetected) {
                        lastSoundTime = now
                    }
                } else if (speechDetected && amp >= activeThreshold) {
                    consecutiveOnsetFrames = 0
                    if (amp > peakSpeechAmp) {
                        peakSpeechAmp = amp
                    }
                    lastSoundTime = now
                } else {
                    consecutiveOnsetFrames = 0
                    val silenceDuration = now - lastSoundTime
                    val weakTailDuration = now - lastStrongSpeechTime
                    val voicedDuration = if (speechDetected) (lastSoundTime - speechStartTime) else 0L
                    val totalSpeechDuration = if (speechDetected) (now - speechStartTime) else 0L

                    // Réarme si un simple bruit bref (< 40 ms) a déclenché l'écoute puis s'est arrêté
                    if (speechDetected && voicedDuration < 40L && silenceDuration >= 200L) {
                        speechDetected = false
                        hasDetectedSpeechInSession = false
                        peakSpeechAmp = 0
                    }

                    // Si aucune parole n'est détectée après 5s, informe proprement l'utilisateur
                    if (!speechDetected && elapsedSinceStart >= 5000L) {
                        withContext(Dispatchers.Main) {
                            cancelSilenceRecording()
                        }
                        break
                    }

                    // Détection rapide et naturelle de fin de phrase :
                    // 680 ms en Mooré/Dioula (respecte les prolongations & tons), 520 ms en Français/Anglais/etc.
                    // + Garde anti-blocage : si aucun pic vocal franc depuis 950 ms après 220 ms de parole, on conclut immédiatement.
                    val isAfrican = isAfricanLanguage(language)
                    val requiredSilenceMs = if (isAfrican) 680L else 520L
                    val minVoicedMs = if (isAfrican) 45L else 55L
                    val silenceReached = speechDetected && voicedDuration >= minVoicedMs &&
                        (silenceDuration >= requiredSilenceMs || (voicedDuration >= 220L && weakTailDuration >= 950L))
                    if (silenceReached || (speechDetected && totalSpeechDuration >= 9500L)) {
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
        val voiceDetected = (hasDetectedSpeechInSession && maxSpeechAmpInSession >= 12) ||
            (manualStop && hadNonZeroAudio)

        if (!voiceDetected) {
            scope.launch(Dispatchers.IO) {
                audioRecorder.cancel()
            }
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
            val audioFile = withContext(Dispatchers.IO) {
                audioRecorder.stopRecording()
            }
            if (audioFile == null || !audioFile.exists() || audioFile.length() < 3200L) {
                try {
                    audioFile?.delete()
                } catch (_: Exception) {}
                val err = "Aucune parole détectée. Parlez puis réessayez."
                _state.value = State.Error(err)
                _partialText.value = ""
                val stopCb = pendingStopCallback
                pendingStopCallback = null
                activeOnError?.invoke(err)
                if (onFinished != null) onFinished.invoke("") else stopCb?.invoke("")
                return@launch
            }

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
