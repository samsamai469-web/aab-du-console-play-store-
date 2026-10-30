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
     * Démarre la reconnaissance vocale avec affichage instantané mot par mot.
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
        _partialText.value = ""
        _soundLevel.value = 0f
        _state.value = State.Listening

        val isNativeAvailable = try {
            SpeechRecognizer.isRecognitionAvailable(context)
        } catch (_: Exception) {
            false
        }

        // Pour les langues nationales (Mooré, Dioula), on privilégie Whisper
        // Pour les langues standards (Français, Anglais, etc.), Google SpeechRecognizer écrit en temps réel
        if (isNativeAvailable && !isAfricanLanguage(language)) {
            startNativeRecognition(language)
        } else {
            startWhisperRecognition(language)
        }
    }

    private fun startNativeRecognition(language: String) {
        isUsingNativeRecognizer = true
        mainHandler.post {
            try {
                destroyNativeRecognizer()
                var silenceRetryCount = 0
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, getLanguageTag(language))
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1200L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 750L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 550L)
                    putExtra("android.speech.extra.DICTATION_MODE", true)
                }

                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            _state.value = State.Listening
                        }

                        override fun onBeginningOfSpeech() {
                            _state.value = State.Listening
                        }

                        override fun onRmsChanged(rmsdB: Float) {
                            val normalized = (rmsdB.coerceIn(0f, 10f) / 10f)
                            _soundLevel.value = normalized
                        }

                        override fun onBufferReceived(buffer: ByteArray?) {}

                        override fun onEndOfSpeech() {
                            _soundLevel.value = 0f
                        }

                        override fun onError(error: Int) {
                            Log.d(TAG, "SpeechRecognizer notice [code $error]")
                            val currentText = _partialText.value.trim()
                            if (currentText.isNotBlank()) {
                                deliverSuccess(currentText)
                                return
                            }

                            if ((error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) && silenceRetryCount < 1) {
                                silenceRetryCount++
                                Log.d(TAG, "Relance de l'écoute après silence initial")
                                try {
                                    startListening(intent)
                                    return
                                } catch (_: Exception) {}
                            }

                            if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                                val msg = "Aucune parole détectée. Veuillez parler plus fort."
                                _state.value = State.Error(msg)
                                activeOnError?.invoke(msg)
                            } else {
                                // En cas d'erreur de reconnaissance vocale native (service indisponible ou permissions), bascule vers Whisper
                                Log.w(TAG, "Bascule vers enregistreur audio Whisper")
                                startWhisperRecognition(activeLanguage)
                            }
                        }

                        override fun onResults(results: Bundle?) {
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val recognized = matches?.firstOrNull()?.trim().orEmpty()
                            val fallback = _partialText.value.trim()
                            val finalText = if (recognized.isNotBlank()) recognized else fallback

                            if (finalText.isNotBlank()) {
                                deliverSuccess(finalText)
                            } else {
                                val err = "Aucune parole détectée."
                                _state.value = State.Error(err)
                                activeOnError?.invoke(err)
                            }
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val liveWord = matches?.firstOrNull()?.trim().orEmpty()
                            if (liveWord.isNotBlank()) {
                                _partialText.value = liveWord
                            }
                        }

                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }

                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Échec démarrage SpeechRecognizer, bascule vers Whisper: ${e.message}")
                startWhisperRecognition(language)
            }
        }
    }

    private fun startWhisperRecognition(language: String) {
        isUsingNativeRecognizer = false
        _partialText.value = ""
        
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
            var noiseFloor = 520f
            var peakSpeechAmp = 0

            while (isActive && _state.value is State.Listening) {
                val amp = audioRecorder.getMaxAmplitude()
                val normalized = (amp / 14000f).coerceIn(0f, 1f)
                val now = System.currentTimeMillis()
                val elapsedSinceStart = now - recordingStart

                // Calibrage dynamique du bruit ambiant sur les 180 premières ms (6 trames de 30 ms)
                if (!speechDetected && elapsedSinceStart < 180L && amp > 0) {
                    noiseFloor = (noiseFloor * 0.65f) + (amp.coerceAtMost(2400) * 0.35f)
                } else if (!speechDetected && amp in 1..noiseFloor.toInt()) {
                    noiseFloor = (noiseFloor * 0.85f) + (amp * 0.15f)
                }

                val onsetThreshold = maxOf(820, (noiseFloor * 1.75f).toInt()).coerceAtMost(3600)
                // Dès que la voix est captée, le seuil de maintien monte à 42% du pic vocal de l'utilisateur
                // afin que la fin de phrase soit détectée en 360 ms même dans une pièce bruyante
                val activeThreshold = if (speechDetected) {
                    maxOf(onsetThreshold, (peakSpeechAmp * 0.42f).toInt().coerceAtMost(5200))
                } else {
                    onsetThreshold
                }

                if (amp > activeThreshold) {
                    consecutiveVoiceFrames++
                    if (speechDetected || consecutiveVoiceFrames >= 2) {
                        lastSoundTime = now
                        if (amp > peakSpeechAmp) {
                            peakSpeechAmp = amp
                        }
                        if (!speechDetected) {
                            speechDetected = true
                            speechStartTime = now
                            withContext(Dispatchers.Main) {
                                _partialText.value = "🎙️ Voix captée, SAM AI vous écoute..."
                            }
                        }
                    }
                } else {
                    consecutiveVoiceFrames = 0
                    val silenceDuration = now - lastSoundTime
                    val totalSpeechDuration = if (speechDetected) (now - speechStartTime) else 0L

                    // Détection ultra-rapide : dès 360 ms de pause après une parole (ou max 3.2s par phrase)
                    if ((speechDetected && silenceDuration >= 360L && totalSpeechDuration >= 220L) ||
                        (speechDetected && totalSpeechDuration >= 3200L) ||
                        (!speechDetected && elapsedSinceStart >= 3200L)
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

    private fun deliverSuccess(text: String, onFinished: ((String) -> Unit)? = null) {
        val rawClean = text.trim()
        val clean = whisperService.applyMoorePhoneticCorrections(rawClean)
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
        if (_state.value !is State.Listening) {
            val existing = _partialText.value.trim()
            onFinished?.invoke(existing)
            return
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
        _state.value = State.Processing
        val isMoore = isAfricanLanguage(activeLanguage)
        _partialText.value = if (isMoore) {
            "⚡ Réponse immédiate SAM AI..."
        } else {
            "Transcription..."
        }

        val audioFile = audioRecorder.stopRecording()

        transcriptionJob = scope.launch {
            if (audioFile == null || !audioFile.exists() || audioFile.length() < 300) {
                val err = "Aucune parole détectée. Veuillez parler plus près du micro."
                _state.value = State.Error(err)
                _partialText.value = ""
                activeOnError?.invoke(err)
                onFinished?.invoke("")
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
