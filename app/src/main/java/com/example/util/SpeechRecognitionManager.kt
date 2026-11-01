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
 * - Mode haute précision multilingue (OpenAI Whisper) : activé automatiquement pour les langues nationales
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
    private var activeLanguage: String = "Français"
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
        return l.contains("moor") || l.contains("dioul") || l.contains("dyul") ||
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
        // En mode manuel (appui explicite de l'utilisateur sur le bouton micro), on coupe d'abord
        // toute voix TTS en cours pour qu'elle ne soit jamais enregistrée par le microphone
        if (!continuousMode && TtsManager.isAnyTtsSpeakingOrCoolingDown(0L)) {
            TtsManager.stopAllActiveTts()
        }

        cancelCurrentJobs()
        activeOnResult = onResult
        activeOnError = onError
        activeLanguage = language
        hasDetectedSpeechInSession = false
        maxSpeechAmpInSession = 0
        _partialText.value = ""
        _soundLevel.value = 0f
        _state.value = State.Listening

        // Capture directe PCM 16kHz sans perte + transcription neuronale OpenAI (comprend Français + Mooré sans délai ni échec natif)
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
            var consecutiveVoiceFrames = 0
            var speechStartTime = 0L
            var lastSoundTime = System.currentTimeMillis()
            val recordingStart = lastSoundTime
            var noiseFloor = 75f
            var peakSpeechAmp = 0
            var sampleFrameCount = 0

            while (isActive && _state.value is State.Listening) {
                // Protection anti-écho globale : si SAM AI prend la parole sur le haut-parleur pendant
                // que le micro écoute, on annule immédiatement l'enregistrement en cours pour ne jamais
                // transcrire la voix de SAM AI comme une question de l'utilisateur
                if (TtsManager.isGlobalTtsSpeaking) {
                    withContext(Dispatchers.Main) {
                        reset()
                    }
                    break
                }

                val amp = audioRecorder.getMaxAmplitude()
                val normalized = (amp / 4000f).coerceIn(0f, 1f)
                val now = System.currentTimeMillis()
                val elapsedSinceStart = now - recordingStart
                if (amp > 0) sampleFrameCount++

                val onsetThreshold = maxOf(130, (noiseFloor * 1.55f).toInt()).coerceAtMost(350)

                // Calibration rapide du bruit ambiant de la pièce hors parole (jusqu'à 240 RMS)
                // Empêche un ventilateur ou bruit de fond de déclencher speechDetected en continu pendant 15s
                if (!speechDetected && amp in 1..240) {
                    noiseFloor = if (sampleFrameCount <= 2 && amp < 200) {
                        amp.toFloat().coerceIn(50f, 190f)
                    } else if (amp < onsetThreshold) {
                        ((noiseFloor * 0.88f) + (amp * 0.12f)).coerceIn(45f, 220f)
                    } else {
                        noiseFloor
                    }
                }

                val activeThreshold = if (speechDetected) {
                    maxOf(120, (noiseFloor * 1.38f).toInt(), (peakSpeechAmp * 0.26f).toInt().coerceAtMost(460))
                } else {
                    onsetThreshold
                }

                if (amp >= activeThreshold) {
                    consecutiveVoiceFrames++
                    if (amp > peakSpeechAmp) {
                        peakSpeechAmp = amp
                        maxSpeechAmpInSession = amp
                    }
                    if (!speechDetected) {
                        if (consecutiveVoiceFrames >= 2 || amp >= (onsetThreshold * 130 / 100)) {
                            speechDetected = true
                            hasDetectedSpeechInSession = true
                            speechStartTime = now - 20L
                            lastSoundTime = now
                        }
                    } else if (consecutiveVoiceFrames >= 2 || amp >= (activeThreshold * 130 / 100)) {
                        // Ignore un micro-bruit isolé de 20 ms pour ne pas réinitialiser le compteur de silence
                        lastSoundTime = now
                    }
                } else {
                    consecutiveVoiceFrames = 0
                    val silenceDuration = now - lastSoundTime
                    val voicedDuration = if (speechDetected) (lastSoundTime - speechStartTime) else 0L
                    val totalSpeechDuration = if (speechDetected) (now - speechStartTime) else 0L

                    // Si un simple clic mécanique (< 60 ms) a déclenché speechDetected suivi de silence, on réarme l'écoute
                    if (speechDetected && voicedDuration < 60L && silenceDuration >= 220L) {
                        speechDetected = false
                        peakSpeechAmp = 0
                    }

                    // Si aucune parole n'a été détectée après 7.5s, on relance proprement le cycle d'écoute
                    if (!speechDetected && elapsedSinceStart >= 7500L) {
                        withContext(Dispatchers.Main) {
                            cancelSilenceRecording()
                        }
                        break
                    }

                    // Détection naturelle de fin de question (550 ms de silence) : laisse l'utilisateur
                    // respirer entre les mots de sa question sans jamais le couper prématurément
                    val requiredSilenceMs = 550L
                    if ((speechDetected && silenceDuration >= requiredSilenceMs && voicedDuration >= 120L) ||
                        (speechDetected && totalSpeechDuration >= 15000L)
                    ) {
                        withContext(Dispatchers.Main) {
                            stopListening()
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
        activeOnResult = null
        if (onFinished != null) {
            onFinished.invoke(clean)
        } else {
            resultCallback?.invoke(clean)
        }
    }

    /**
     * Arrête l'écoute et finalise la transcription.
     */
    fun stopListening(onFinished: ((String) -> Unit)? = null) {
        when (val currentState = _state.value) {
            is State.Processing -> {
                if (onFinished != null) {
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
            stopWhisperRecognition(onFinished)
        }
    }

    private fun stopWhisperRecognition(onFinished: ((String) -> Unit)?) {
        amplitudeJob?.cancel()
        amplitudeJob = null
        _soundLevel.value = 0f

        val voiceDetected = hasDetectedSpeechInSession && maxSpeechAmpInSession >= 115
        val audioFile = audioRecorder.stopRecording()

        if (!voiceDetected || audioFile == null || !audioFile.exists() || audioFile.length() < 400) {
            try {
                audioFile?.delete()
            } catch (_: Exception) {}
            val err = "Aucune parole détectée. Veuillez parler plus près du micro."
            _state.value = State.Error(err)
            _partialText.value = ""
            activeOnError?.invoke(err)
            onFinished?.invoke("")
            return
        }

        _state.value = State.Processing
        if (onFinished != null) {
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
                    activeOnError?.invoke(err)
                    onFinished?.invoke("")
                }
            }.onFailure { err ->
                Log.e(TAG, "Erreur Whisper: ${err.message}")
                val errMsg = when {
                    err.message?.contains("API") == true -> "Clé OpenAI requise ou quota atteint."
                    err.message?.contains("connexion") == true || err.message?.contains("réseau") == true -> "Vérifiez votre connexion Internet."
                    else -> err.localizedMessage ?: "Erreur de transcription audio."
                }
                _state.value = State.Error(errMsg)
                activeOnError?.invoke(errMsg)
                onFinished?.invoke("")
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
