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
     * Démarre la reconnaissance vocale haute précision avec capture PCM 16kHz instantanée (0 ms de délai).
     */
    fun startListening(
        language: String = "fr-FR",
        continuousMode: Boolean = false,
        onResult: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
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
            var noiseFloor = 300f
            var peakSpeechAmp = 0

            while (isActive && _state.value is State.Listening) {
                val amp = audioRecorder.getMaxAmplitude()
                val normalized = (amp / 14000f).coerceIn(0f, 1f)
                val now = System.currentTimeMillis()
                val elapsedSinceStart = now - recordingStart

                // Calibrage dynamique du bruit ambiant sur les 150 premières ms
                if (!speechDetected && elapsedSinceStart < 150L && amp > 0) {
                    noiseFloor = (noiseFloor * 0.65f) + (amp.coerceAtMost(1800) * 0.35f)
                } else if (!speechDetected && amp in 1..noiseFloor.toInt()) {
                    noiseFloor = (noiseFloor * 0.85f) + (amp * 0.15f)
                }

                val onsetThreshold = maxOf(380, (noiseFloor * 1.50f).toInt()).coerceAtMost(2200)
                // Seuil de maintien souple (18% du pic, plafonné à 1400) pour ne jamais couper une question au milieu d'un mot
                val activeThreshold = if (speechDetected) {
                    maxOf(280, (noiseFloor * 1.25f).toInt(), (peakSpeechAmp * 0.18f).toInt().coerceAtMost(1400))
                } else {
                    onsetThreshold
                }

                if (amp > activeThreshold) {
                    consecutiveVoiceFrames++
                    if (speechDetected || consecutiveVoiceFrames >= 2) {
                        lastSoundTime = now
                        if (amp > peakSpeechAmp) {
                            peakSpeechAmp = amp
                            maxSpeechAmpInSession = amp
                        }
                        if (!speechDetected) {
                            speechDetected = true
                            hasDetectedSpeechInSession = true
                            speechStartTime = now
                        }
                    }
                } else {
                    consecutiveVoiceFrames = 0
                    val silenceDuration = now - lastSoundTime
                    val totalSpeechDuration = if (speechDetected) (now - speechStartTime) else 0L

                    // Si aucune parole n'a été détectée après 4.0s, on annule proprement SANS envoyer le silence à Whisper (évite toute parole au hasard)
                    if (!speechDetected && elapsedSinceStart >= 4000L) {
                        withContext(Dispatchers.Main) {
                            cancelSilenceRecording()
                        }
                        break
                    }

                    // Pause naturelle (~0.9s de silence après la voix) pour laisser l'utilisateur finir toute sa question avant que SAM AI ne lui réponde
                    if ((speechDetected && silenceDuration >= 900L && totalSpeechDuration >= 250L) ||
                        (speechDetected && totalSpeechDuration >= 12000L)
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
                delay(25L)
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
        val rawClean = text.trim()
        val isMoore = isAfricanLanguage(activeLanguage)
        val clean = whisperService.applyMoorePhoneticCorrections(rawClean, isMooreHint = isMoore)
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

        val voiceDetected = hasDetectedSpeechInSession && maxSpeechAmpInSession >= 320
        val audioFile = audioRecorder.stopRecording()

        if (!voiceDetected || audioFile == null || !audioFile.exists() || audioFile.length() < 600) {
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
