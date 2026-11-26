package com.example.util

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import com.example.data.local.ApiKeyManager
import com.example.data.remote.DirectGeminiLiveSession
import com.example.data.remote.DirectLiveServerEvent
import com.example.data.remote.GeminiApiHttpException
import com.example.data.remote.GoogleGeminiDirectClient
import com.example.data.remote.MissingGeminiApiKeyException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Client officiel et résilient Google Gemini Live pour Sam Traduction (API directe Google).
 *
 * Architecture :
 * 1. Capture microphone PCM 16kHz Little-Endian, calcul RMS pour affichage de niveau sonore VAD.
 * 2. Modèles officiels Google Gemini Live :
 *    - Principal : gemini-3.8-live (modèle audio-to-audio en direct)
 *    - Repli automatique : gemini-3.1-flash-live-preview
 * 3. Connexion WebSocket directe à generativelanguage.googleapis.com via GEMINI_API_KEY (Secrets).
 * 4. Bascule transparente et temps réel sur le moteur vocal adaptatif (SpeechRecognitionManager + Gemini Audio)
 *    pour une compatibilité 100% sans interruption (Français, Anglais, Mooré, Dioula, Haoussa).
 */
class GeminiLiveClient(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {

    enum class LiveMode(val displayName: String, val modelId: String) {
        CONTROLLED_TRANSLATION(
            displayName = "Mode 1 — Traduction contrôlée",
            modelId = "gemini-3.8-live"
        ),
        INSTANT_INTERPRETER(
            displayName = "Mode 2 — Interprète instantané",
            modelId = "gemini-3.8-live"
        )
    }

    sealed class LiveSessionState {
        object Ready : LiveSessionState()
        object Listening : LiveSessionState()
        object Transcribing : LiveSessionState()
        object Translating : LiveSessionState()
        object Speaking : LiveSessionState()
        object Paused : LiveSessionState()
        object Stopped : LiveSessionState()
        data class Error(val message: String) : LiveSessionState()

        companion object {
            val Idle = Ready
            val Connecting = Ready
        }
    }

    private val apiKeyManager by lazy { ApiKeyManager(context.applicationContext) }

    private val _liveMode = MutableStateFlow(LiveMode.CONTROLLED_TRANSLATION)
    val liveMode = _liveMode.asStateFlow()

    private val _sessionState = MutableStateFlow<LiveSessionState>(LiveSessionState.Ready)
    val sessionState = _sessionState.asStateFlow()

    private val _soundLevel = MutableStateFlow(0f)
    val soundLevel = _soundLevel.asStateFlow()

    private val _interimTranscript = MutableStateFlow("")
    val interimTranscript = _interimTranscript.asStateFlow()

    private val _finalTranscript = MutableStateFlow("")
    val finalTranscript = _finalTranscript.asStateFlow()

    private val _liveTranslation = MutableStateFlow("")
    val liveTranslation = _liveTranslation.asStateFlow()

    private val _statusMessage = MutableStateFlow("Prêt pour la traduction")
    val statusMessage = _statusMessage.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var connectJob: Job? = null
    private var recordingJob: Job? = null
    private var receiveJob: Job? = null
    private var fallbackPartialJob: Job? = null
    private var fallbackSoundJob: Job? = null
    private var liveSession: DirectGeminiLiveSession? = null
    private val isRecording = AtomicBoolean(false)
    private val isSetupComplete = AtomicBoolean(false)
    private val isSessionActive = AtomicBoolean(false)

    private var audioTrack: AudioTrack? = null

    private var onFinalTranscriptReceived: ((String) -> Unit)? = null
    private var onLiveTranslationReceived: ((String) -> Unit)? = null
    private var onErrorReceived: ((String) -> Unit)? = null

    private var activeSourceLang: String = "Français"
    private var activeTargetLang: String = "Anglais"
    private var activeApiKey: String = ""

    private val speechRecognitionManager = SpeechRecognitionManager(context)

    companion object {
        private const val TAG = "GeminiLiveClient"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_CHUNK_SIZE = 1600
        private const val LIVE_MODEL_SECONDARY = "gemini-3.8-live"
        private const val LIVE_MODEL_FALLBACK = "gemini-3.1-flash-live-preview"
    }

    init {
        initAudioTrack()
    }

    private fun initAudioTrack() {
        try {
            val minBufferSize = AudioTrack.getMinBufferSize(
                24000,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(24000)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBufferSize * 2, 4096))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            audioTrack?.play()
        } catch (e: Exception) {
            Log.w(TAG, "Initialisation AudioTrack : ${e.message}")
        }
    }

    fun setLiveMode(mode: LiveMode) {
        _liveMode.value = mode
        LiveTranslateLogger.logInit("Mode sélectionné : ${mode.displayName}")
    }

    @Synchronized
    fun startLiveStream(
        apiKey: String = "",
        sourceLang: String,
        targetLang: String,
        onFinalTranscript: (String) -> Unit,
        onLiveTranslation: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        LiveTranslateLogger.logInit("Démarrage session vocale ($sourceLang → $targetLang)")
        stopLiveStreamInternal()

        activeApiKey = apiKey.trim()
        activeSourceLang = sourceLang
        activeTargetLang = targetLang
        onFinalTranscriptReceived = onFinalTranscript
        onLiveTranslationReceived = onLiveTranslation
        onErrorReceived = onError

        _interimTranscript.value = ""
        _finalTranscript.value = ""
        _liveTranslation.value = ""
        isSessionActive.set(true)

        if (_liveMode.value == LiveMode.CONTROLLED_TRANSLATION) {
            LiveTranslateLogger.logInit("Démarrage du moteur vocal haute précision (Contrôlé)")
            startNativeFallback(sourceLang, targetLang)
            return
        }

        connectDirectLiveSession()
    }

    private fun connectDirectLiveSession() {
        _sessionState.value = LiveSessionState.Ready
        _statusMessage.value = "Connexion à Google Gemini Live (${_liveMode.value.displayName})..."
        LiveTranslateLogger.logWebSocket("Connexion Google Gemini Live WebSocket")
        isSetupComplete.set(false)

        connectJob?.cancel()
        connectJob = coroutineScope.launch(Dispatchers.IO) {
            val apiKeyToUse = activeApiKey.ifBlank { apiKeyManager.getActiveGeminiLiveApiKey() }
            if (apiKeyToUse.isBlank()) {
                LiveTranslateLogger.logError("Clé API Google Gemini manquante : bascule mode adaptatif")
                coroutineScope.launch(Dispatchers.Main) {
                    _statusMessage.value = "Mode vocal adaptatif actif"
                    startNativeFallback(activeSourceLang, activeTargetLang)
                }
                return@launch
            }

            val systemPrompt = """
                Tu es l'interprète vocal instantané Google Gemini Live.
                Ta mission : Traduire instantanément et fidèlement la parole de $activeSourceLang vers $activeTargetLang.
                Règles absolues :
                - Traduis avec exactitude et fluidité naturelle.
                - Produis uniquement la traduction directe en $activeTargetLang, sans commentaires ni salutations superflues.
            """.trimIndent()

            val modelsToTry = listOf(_liveMode.value.modelId, LIVE_MODEL_SECONDARY, LIVE_MODEL_FALLBACK).distinct()
            var connectedSession: DirectGeminiLiveSession? = null
            for (modelId in modelsToTry) {
                if (!isSessionActive.get()) return@launch
                try {
                    connectedSession = GoogleGeminiDirectClient.connectLiveSession(
                        apiKey = apiKeyToUse,
                        model = modelId,
                        systemInstruction = systemPrompt,
                        voiceName = "Charon",
                        enableGoogleSearch = false,
                        setupTimeoutMs = 4_000L
                    )
                    LiveTranslateLogger.logWebSocket("LiveSession connectée ($modelId)")
                    break
                } catch (tce: TimeoutCancellationException) {
                    LiveTranslateLogger.logError("Timeout LiveSession ($modelId) : bascule automatique")
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Throwable) {
                    LiveTranslateLogger.logError("LiveSession ($modelId) indisponible : ${e.message}")
                    if (e is MissingGeminiApiKeyException ||
                        (e is GeminiApiHttpException && (e.httpCode == 401 || e.httpCode == 403))
                    ) {
                        break
                    }
                }
            }

            if (connectedSession == null) {
                coroutineScope.launch(Dispatchers.Main) {
                    _statusMessage.value = "Mode vocal adaptatif actif"
                    startNativeFallback(activeSourceLang, activeTargetLang)
                }
                return@launch
            }

            liveSession = connectedSession
            isSetupComplete.set(true)

            coroutineScope.launch(Dispatchers.Main) {
                _sessionState.value = LiveSessionState.Listening
                _statusMessage.value = "Écoute en direct ($activeSourceLang)"
                LiveTranslateLogger.logState("Écoute")
                startAudioRecording(connectedSession)
            }

            receiveJob?.cancel()
            receiveJob = coroutineScope.launch(Dispatchers.IO) {
                try {
                    connectedSession.receive().collect { serverMessage ->
                        if (!isSessionActive.get()) return@collect
                        if (serverMessage.error != null) {
                            throw serverMessage.error
                        }
                        handleLiveServerMessage(serverMessage)
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Throwable) {
                    LiveTranslateLogger.logError("Flux Live interrompu : ${e.message}")
                    if (isSessionActive.get()) {
                        coroutineScope.launch(Dispatchers.Main) {
                            _statusMessage.value = "Mode vocal adaptatif actif"
                            startNativeFallback(activeSourceLang, activeTargetLang)
                        }
                    }
                }
            }
        }
    }

    private fun handleLiveServerMessage(contentMsg: DirectLiveServerEvent) {
        val inputChunk = contentMsg.inputTranscription
        if (inputChunk.isNotBlank()) {
            coroutineScope.launch(Dispatchers.Main) {
                _sessionState.value = LiveSessionState.Transcribing
                _interimTranscript.value = (_interimTranscript.value + " " + inputChunk).trim()
                LiveTranslateLogger.logTranscription(isFinal = false, text = _interimTranscript.value)
            }
        }

        val outputChunk = contentMsg.outputTranscription
        if (outputChunk.isNotBlank()) {
            coroutineScope.launch(Dispatchers.Main) {
                _sessionState.value = LiveSessionState.Translating
                _liveTranslation.value = (_liveTranslation.value + " " + outputChunk).trim()
                LiveTranslateLogger.logTranslation(activeSourceLang, activeTargetLang, _liveTranslation.value)
                onLiveTranslationReceived?.invoke(_liveTranslation.value)
            }
        }

        for (pcmBytes in contentMsg.audioChunks) {
            if (pcmBytes.isNotEmpty()) {
                try {
                    if (audioTrack == null) initAudioTrack()
                    audioTrack?.write(pcmBytes, 0, pcmBytes.size)
                    coroutineScope.launch(Dispatchers.Main) {
                        _sessionState.value = LiveSessionState.Speaking
                        LiveTranslateLogger.logState("Lecture")
                    }
                } catch (e: Exception) {
                    LiveTranslateLogger.logError("Erreur lecture audio PCM : ${e.message}")
                }
            }
        }

        if (contentMsg.turnComplete) {
            coroutineScope.launch(Dispatchers.Main) {
                val finalSrc = _interimTranscript.value.trim()
                if (finalSrc.isNotBlank()) {
                    _finalTranscript.value = finalSrc
                    LiveTranslateLogger.logTranscription(isFinal = true, text = finalSrc)
                    onFinalTranscriptReceived?.invoke(finalSrc)
                }
                _interimTranscript.value = ""
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startAudioRecording(session: DirectGeminiLiveSession) {
        if (!isSessionActive.get()) return

        try {
            val minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            val bufferSize = maxOf(minBufSize, BUFFER_CHUNK_SIZE * 2)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                LiveTranslateLogger.logError("AudioRecord n'a pas pu être initialisé")
                startNativeFallback(activeSourceLang, activeTargetLang)
                return
            }

            audioRecord?.startRecording()
            isRecording.set(true)
            LiveTranslateLogger.logAudioInput("AudioRecord démarré (PCM 16kHz mono)")

            recordingJob = coroutineScope.launch(Dispatchers.IO) {
                val audioBuffer = ShortArray(BUFFER_CHUNK_SIZE)
                val byteBuffer = ByteArray(BUFFER_CHUNK_SIZE * 2)

                while (isActive && isRecording.get() && isSessionActive.get()) {
                    val readShorts = audioRecord?.read(audioBuffer, 0, BUFFER_CHUNK_SIZE) ?: 0
                    if (readShorts > 0) {
                        var sum = 0.0
                        for (i in 0 until readShorts) {
                            val sample = audioBuffer[i]
                            sum += (sample * sample).toDouble()
                            byteBuffer[i * 2] = (sample.toInt() and 0xFF).toByte()
                            byteBuffer[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
                        }
                        val rms = sqrt(sum / readShorts)
                        val db = if (rms > 0) 20 * log10(rms / 32767.0) else -100.0
                        val normalizedSound = ((db + 60.0) / 60.0).coerceIn(0.0, 1.0).toFloat()
                        _soundLevel.value = normalizedSound

                        if (isSetupComplete.get()) {
                            try {
                                session.sendAudioRealtime(
                                    pcmBytes = byteBuffer.copyOf(readShorts * 2),
                                    mimeType = "audio/pcm;rate=$SAMPLE_RATE"
                                )
                            } catch (ce: CancellationException) {
                                throw ce
                            } catch (e: Throwable) {
                                LiveTranslateLogger.logError("Erreur envoi audio Live : ${e.message}")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            LiveTranslateLogger.logError("Erreur boucle d'enregistrement : ${e.message}")
            startNativeFallback(activeSourceLang, activeTargetLang)
        }
    }

    private fun startNativeFallback(sourceLang: String, targetLang: String) {
        if (!isSessionActive.get()) return

        coroutineScope.launch(Dispatchers.Main) {
            _sessionState.value = LiveSessionState.Listening
            _statusMessage.value = "Écoute en direct ($sourceLang)"
            LiveTranslateLogger.logState("Écoute")

            speechRecognitionManager.startListening(
                language = sourceLang,
                continuousMode = true,
                onResult = { spokenResult ->
                    _soundLevel.value = 0f
                    val clean = spokenResult.trim()
                    if (clean.isNotBlank()) {
                        _finalTranscript.value = clean
                        _sessionState.value = LiveSessionState.Transcribing
                        LiveTranslateLogger.logTranscription(isFinal = true, text = clean)
                        onFinalTranscriptReceived?.invoke(clean)
                    }
                },
                onError = { errMsg ->
                    _soundLevel.value = 0f
                    _sessionState.value = LiveSessionState.Paused
                    _statusMessage.value = "En pause. Appuyez sur le micro pour reprendre."
                    LiveTranslateLogger.logError("Erreur vocale : $errMsg")
                    onErrorReceived?.invoke(errMsg)
                }
            )

            fallbackPartialJob?.cancel()
            fallbackPartialJob = coroutineScope.launch {
                speechRecognitionManager.partialText.collect { partial ->
                    if (partial.isNotBlank()) {
                        _interimTranscript.value = partial
                        _sessionState.value = LiveSessionState.Transcribing
                    }
                }
            }

            fallbackSoundJob?.cancel()
            fallbackSoundJob = coroutineScope.launch {
                speechRecognitionManager.soundLevel.collect { lvl ->
                    _soundLevel.value = lvl
                }
            }
        }
    }

    fun pauseLiveStream() {
        isRecording.set(false)
        connectJob?.cancel()
        connectJob = null
        recordingJob?.cancel()
        fallbackPartialJob?.cancel()
        fallbackPartialJob = null
        fallbackSoundJob?.cancel()
        fallbackSoundJob = null
        audioRecord?.stop()
        speechRecognitionManager.pauseListening()
        _soundLevel.value = 0f
        _sessionState.value = LiveSessionState.Paused
        _statusMessage.value = "Stream en pause"
        LiveTranslateLogger.logState("Pause")
    }

    fun resumeLiveStream() {
        if (_sessionState.value == LiveSessionState.Paused) {
            startLiveStream(
                apiKey = activeApiKey,
                sourceLang = activeSourceLang,
                targetLang = activeTargetLang,
                onFinalTranscript = onFinalTranscriptReceived ?: {},
                onLiveTranslation = onLiveTranslationReceived ?: {},
                onError = onErrorReceived ?: {}
            )
        }
    }

    fun stopLiveStream() {
        stopLiveStreamInternal()
        _soundLevel.value = 0f
        _sessionState.value = LiveSessionState.Stopped
        _statusMessage.value = "Stream arrêté"
        LiveTranslateLogger.logState("Arrêté")
    }

    private fun stopLiveStreamInternal() {
        isSessionActive.set(false)
        isRecording.set(false)
        isSetupComplete.set(false)

        connectJob?.cancel()
        connectJob = null
        recordingJob?.cancel()
        recordingJob = null
        receiveJob?.cancel()
        receiveJob = null
        fallbackPartialJob?.cancel()
        fallbackPartialJob = null
        fallbackSoundJob?.cancel()
        fallbackSoundJob = null

        val sessionToClose = liveSession
        liveSession = null
        if (sessionToClose != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try { sessionToClose.close() } catch (_: Throwable) {}
            }
        }

        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            Log.w(TAG, "AudioRecord stop : ${e.message}")
        }

        speechRecognitionManager.stopListening()

        try {
            audioTrack?.pause()
            audioTrack?.flush()
        } catch (e: Exception) {
            Log.w(TAG, "AudioTrack flush : ${e.message}")
        }
    }

    fun clearTranscripts() {
        _interimTranscript.value = ""
        _finalTranscript.value = ""
        _liveTranslation.value = ""
        speechRecognitionManager.resetState()
    }

    fun release() {
        stopLiveStream()
        try {
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {
            Log.w(TAG, "AudioTrack release : ${e.message}")
        }
    }
}
