package com.example.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

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

    private var recognizer: SpeechRecognizer? = null
    private var activeOnResult: ((String) -> Unit)? = null
    private var activeOnError: ((String) -> Unit)? = null

    init {
        initRecognizer()
    }

    private fun initRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            try {
                recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(createListener())
                }
            } catch (_: Exception) {
                recognizer = null
            }
        }
    }

    private fun createListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _state.value = State.Listening
        }

        override fun onBeginningOfSpeech() {
            _state.value = State.Listening
        }

        override fun onRmsChanged(rmsdB: Float) {
            _soundLevel.value = (rmsdB.coerceIn(0f, 10f) / 10f)
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            _state.value = State.Processing
        }

        override fun onError(error: Int) {
            val message = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> "Aucune parole détectée."
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Temps d'écoute dépassé."
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Erreur de connexion réseau."
                SpeechRecognizer.ERROR_AUDIO -> "Erreur d'enregistrement audio."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permission microphone requise."
                else -> "Reconnaissance vocale non disponible actuellement."
            }
            _state.value = State.Error(message)
            activeOnError?.invoke(message)
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val spokenText = matches?.firstOrNull() ?: ""
            if (spokenText.isNotBlank()) {
                _state.value = State.Success(spokenText)
                _partialText.value = spokenText
                activeOnResult?.invoke(spokenText)
            } else {
                _state.value = State.Idle
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partial = matches?.firstOrNull()
            if (!partial.isNullOrBlank()) {
                _partialText.value = partial
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun startListening(
        language: String = "fr-FR",
        continuousMode: Boolean = false,
        onResult: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        activeOnResult = onResult
        activeOnError = onError
        _partialText.value = ""

        if (recognizer == null) {
            initRecognizer()
        }
        val targetLangCode = when (language.lowercase().trim()) {
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
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, targetLangCode)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        try {
            _state.value = State.Listening
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            val err = "Erreur micro: ${e.localizedMessage}"
            _state.value = State.Error(err)
            onError(err)
        }
    }

    fun stopListening() {
        try {
            recognizer?.stopListening()
        } catch (_: Exception) {}
    }

    fun reset() {
        stopListening()
        _partialText.value = ""
        _soundLevel.value = 0f
        _state.value = State.Idle
        activeOnResult = null
        activeOnError = null
    }

    fun resetState() {
        reset()
    }

    fun pauseListening() {
        stopListening()
    }

    fun destroy() {
        try {
            reset()
            recognizer?.destroy()
            recognizer = null
        } catch (_: Exception) {}
    }
}
