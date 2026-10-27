package com.example.util

import android.content.Context
import android.util.Log
import com.example.data.remote.OpenAiWhisperService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Gestionnaire officiel de reconnaissance vocale utilisant l'API OpenAI Whisper.
 * Assure un enregistrement audio exclusif haute fidélité depuis le microphone de l'appareil
 * sans concurrence de ressources matérielles, garantissant une capture vocale optimale sur tout téléphone.
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

    private var activeOnResult: ((String) -> Unit)? = null
    private var activeOnError: ((String) -> Unit)? = null
    private var activeLanguage: String = "Français"

    companion object {
        private const val TAG = "SpeechRecognitionMgr"
    }

    /**
     * Démarre l'enregistrement audio exclusif pour OpenAI Whisper.
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
        _partialText.value = "Écoute en cours... Parlez clairement."
        _soundLevel.value = 0f
        _state.value = State.Listening

        // Démarrage de la capture audio réelle du micro (accès exclusif)
        val audioFile = audioRecorder.startRecording()
        if (audioFile == null) {
            val msg = "Impossible d'accéder au microphone de l'appareil."
            _state.value = State.Error(msg)
            onError(msg)
            return
        }

        // Boucle de mesure de l'amplitude pour l'animation des ondes sonores et détection de présence vocale
        amplitudeJob = scope.launch(Dispatchers.Default) {
            var speechDetected = false
            while (isActive && _state.value is State.Listening) {
                val amp = audioRecorder.getMaxAmplitude()
                val normalized = (amp / 20000f).coerceIn(0f, 1f)
                
                if (amp > 1200 && !speechDetected) {
                    speechDetected = true
                    withContext(Dispatchers.Main) {
                        _partialText.value = "Voix détectée... Transcription en fin d'écoute"
                    }
                }
                
                withContext(Dispatchers.Main) {
                    _soundLevel.value = normalized
                }
                delay(60)
            }
        }
    }

    /**
     * Arrête l'écoute et déclenche la transcription via l'API OpenAI Whisper.
     */
    fun stopListening(onFinished: ((String) -> Unit)? = null) {
        if (_state.value !is State.Listening) {
            val existing = _partialText.value.takeIf { !it.startsWith("Écoute") && !it.startsWith("Voix") }.orEmpty()
            onFinished?.invoke(existing)
            return
        }

        amplitudeJob?.cancel()
        amplitudeJob = null
        _soundLevel.value = 0f
        _state.value = State.Processing
        _partialText.value = "Transcription OpenAI Whisper en cours..."

        // Arrêt de l'enregistrement et récupération du fichier audio
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

    fun destroy() {
        reset()
        scope.cancel()
    }
}
