package com.example.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
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
 * Gestionnaire unifié de reconnaissance vocale utilisant officiellement l'API OpenAI Whisper.
 * Assure un enregistrement audio direct depuis le microphone (compatible émulateur et smartphones réels)
 * et envoie l'audio à Whisper pour une transcription haute précision.
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

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var amplitudeJob: Job? = null
    private var transcriptionJob: Job? = null

    // Support natif optionnel pour aperçu des mots en direct
    private var nativeRecognizer: SpeechRecognizer? = null
    private var activeOnResult: ((String) -> Unit)? = null
    private var activeOnError: ((String) -> Unit)? = null
    private var activeLanguage: String = "Français"

    companion object {
        private const val TAG = "SpeechRecognitionMgr"
    }

    init {
        try {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                nativeRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(createNativeListener())
                }
            }
        } catch (_: Exception) {
            nativeRecognizer = null
        }
    }

    private fun createNativeListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {
            // Utilisé comme source secondaire de volume si actif
            if (amplitudeJob == null || !amplitudeJob!!.isActive) {
                _soundLevel.value = (rmsdB.coerceIn(0f, 10f) / 10f)
            }
        }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onError(error: Int) {
            // Note : on n'interrompt pas la session ici car OpenAI Whisper est le moteur principal
            Log.d(TAG, "Native recognizer notice [code $error] - Whisper reste le moteur actif")
        }
        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val spoken = matches?.firstOrNull().orEmpty()
            if (spoken.isNotBlank() && _partialText.value.isBlank()) {
                _partialText.value = spoken
            }
        }
        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partial = matches?.firstOrNull().orEmpty()
            if (partial.isNotBlank()) {
                _partialText.value = partial
            }
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    /**
     * Démarre l'enregistrement audio microphone pour OpenAI Whisper.
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

        // Démarrage de la capture audio réelle du micro
        val audioFile = audioRecorder.startRecording()
        if (audioFile == null) {
            val msg = "Impossible d'accéder au microphone de l'appareil."
            _state.value = State.Error(msg)
            onError(msg)
            return
        }

        // Boucle de mesure de l'amplitude pour l'animation des ondes sonores
        amplitudeJob = scope.launch(Dispatchers.Default) {
            while (isActive && _state.value is State.Listening) {
                val amp = audioRecorder.getMaxAmplitude()
                val normalized = (amp / 25000f).coerceIn(0f, 1f)
                withContext(Dispatchers.Main) {
                    _soundLevel.value = normalized
                }
                delay(70)
            }
        }

        // Lancement en parallèle de l'écouteur natif s'il existe pour affichage direct de sous-titres
        startNativeRecognizerIfPossible(language)
    }

    private fun startNativeRecognizerIfPossible(language: String) {
        try {
            val targetLangCode = when (language.lowercase(Locale.ROOT).trim()) {
                "fr", "français", "francais" -> "fr-FR"
                "en", "anglais", "english" -> "en-US"
                "de", "allemand", "german" -> "de-DE"
                "ar", "arabe", "arabic" -> "ar-SA"
                "es", "espagnol", "spanish" -> "es-ES"
                "ha", "haoussa", "hausa" -> "ha-NE"
                else -> "fr-FR"
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, targetLangCode)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            nativeRecognizer?.startListening(intent)
        } catch (_: Exception) {}
    }

    /**
     * Arrête l'écoute et déclenche la transcription via l'API OpenAI Whisper.
     */
    fun stopListening(onFinished: ((String) -> Unit)? = null) {
        if (_state.value !is State.Listening) {
            onFinished?.invoke(_partialText.value)
            return
        }

        amplitudeJob?.cancel()
        amplitudeJob = null
        _soundLevel.value = 0f
        _state.value = State.Processing
        _partialText.value = "Transcription OpenAI Whisper..."

        try {
            nativeRecognizer?.stopListening()
        } catch (_: Exception) {}

        // Arrêt de l'enregistrement et récupération du fichier audio
        val audioFile = audioRecorder.stopRecording()

        transcriptionJob = scope.launch {
            if (audioFile == null || !audioFile.exists() || audioFile.length() < 120) {
                // Fichier trop court ou vide
                val fallbackText = _partialText.value.takeIf {
                    it.isNotBlank() && !it.startsWith("Transcription")
                }.orEmpty()

                if (fallbackText.isNotBlank()) {
                    _state.value = State.Success(fallbackText)
                    activeOnResult?.invoke(fallbackText)
                    onFinished?.invoke(fallbackText)
                } else {
                    val err = "Aucune parole détectée."
                    _state.value = State.Error(err)
                    activeOnError?.invoke(err)
                    onFinished?.invoke("")
                }
                return@launch
            }

            // Appel officiel OpenAI Whisper API
            val result = whisperService.transcribeAudio(audioFile, activeLanguage)
            result.onSuccess { transcribed ->
                val clean = transcribed.trim()
                if (clean.isNotBlank()) {
                    _state.value = State.Success(clean)
                    _partialText.value = clean
                    activeOnResult?.invoke(clean)
                    onFinished?.invoke(clean)
                } else {
                    val fallback = _partialText.value.takeIf {
                        it.isNotBlank() && !it.startsWith("Transcription")
                    }.orEmpty()

                    if (fallback.isNotBlank()) {
                        _state.value = State.Success(fallback)
                        _partialText.value = fallback
                        activeOnResult?.invoke(fallback)
                        onFinished?.invoke(fallback)
                    } else {
                        val err = "Aucune parole détectée."
                        _state.value = State.Error(err)
                        activeOnError?.invoke(err)
                        onFinished?.invoke("")
                    }
                }
            }.onFailure { err ->
                Log.e(TAG, "Erreur Whisper: ${err.message}")
                val fallback = _partialText.value.takeIf {
                    it.isNotBlank() && !it.startsWith("Transcription")
                }.orEmpty()

                if (fallback.isNotBlank()) {
                    _state.value = State.Success(fallback)
                    activeOnResult?.invoke(fallback)
                    onFinished?.invoke(fallback)
                } else {
                    val errMsg = err.localizedMessage ?: "Erreur de reconnaissance vocale"
                    _state.value = State.Error(errMsg)
                    activeOnError?.invoke(errMsg)
                    onFinished?.invoke("")
                }
            }
        }
    }

    fun pauseListening() {
        stopListening()
    }

    fun reset() {
        cancelCurrentJobs()
        audioRecorder.cancel()
        try {
            nativeRecognizer?.cancel()
        } catch (_: Exception) {}
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

    fun destroy() {
        reset()
        scope.cancel()
        try {
            nativeRecognizer?.destroy()
            nativeRecognizer = null
        } catch (_: Exception) {}
    }
}
