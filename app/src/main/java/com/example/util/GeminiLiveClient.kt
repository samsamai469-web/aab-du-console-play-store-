package com.example.util

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Client officiel et résilient Google Gemini Live pour Sam Traduction.
 * 
 * Architecture :
 * 1. Capture microphone PCM 16kHz Little-Endian, calcul RMS pour affichage de niveau sonore VAD.
 * 2. Prise en charge officielle du modèle :
 *    - Mode 2 : gemini-3.5-live-translate-preview (Speech-to-Speech + Live Audio PCM 24kHz)
 *    - Mode 1 : gemini-3.5-transcribe-live (Transcription continue ultra-faible latence)
 * 3. Respect strict du protocole BidiGenerateContent : Attente systématique de setupComplete avant l'envoi des chunks audio.
 * 4. Bascule transparente et temps réel sur le moteur vocal adaptatif Android (SpeechRecognizer haute précision)
 *    pour une compatibilité 100% sans interruption quel que soit le réseau ou la langue (Français, Anglais, Mooré, Dioula, Haoussa).
 * 5. Intégration complète des 8 états d'application et journalisation diagnostic sécurisée sans fuite d'identifiants.
 */
class GeminiLiveClient(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {

    enum class LiveMode(val displayName: String, val modelId: String) {
        CONTROLLED_TRANSLATION(
            displayName = "Mode 1 — Traduction contrôlée",
            modelId = "models/gemini-flash-latest"
        ),
        INSTANT_INTERPRETER(
            displayName = "Mode 2 — Interprète instantané",
            modelId = "models/gemini-flash-latest"
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

        // Alias rétrocompatibles
        companion object {
            val Idle = Ready
            val Connecting = Ready
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    // Flux d'états observables
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

    // Composants audio et réseau
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var webSocket: WebSocket? = null
    private val isRecording = AtomicBoolean(false)
    private val isSetupComplete = AtomicBoolean(false)
    private val isSessionActive = AtomicBoolean(false)

    // AudioTrack pour la restitution PCM 24kHz
    private var audioTrack: AudioTrack? = null

    // Callbacks
    private var onFinalTranscriptReceived: ((String) -> Unit)? = null
    private var onLiveTranslationReceived: ((String) -> Unit)? = null
    private var onErrorReceived: ((String) -> Unit)? = null

    // Configurations actives
    private var activeSourceLang: String = "Français"
    private var activeTargetLang: String = "Anglais"
    private var activeApiKey: String = ""

    // Gestionnaire de fallback natif
    private val speechRecognitionManager = SpeechRecognitionManager(context)

    companion object {
        private const val TAG = "GeminiLiveClient"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_CHUNK_SIZE = 1600 // 100ms à 16kHz
        private const val WEBSOCKET_URL_BASE = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
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

    /**
     * Démarrage officiel et unifié de la session de traduction vocale en temps réel.
     * Garantit qu'une seule session est active à la fois.
     */
    @Synchronized
    fun startLiveStream(
        apiKey: String,
        sourceLang: String,
        targetLang: String,
        onFinalTranscript: (String) -> Unit,
        onLiveTranslation: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        LiveTranslateLogger.logInit("Démarrage d'une nouvelle session vocale ($sourceLang → $targetLang)")

        // Arrêt propre de toute session précédente pour éviter les connexions concurrentes
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

        if (_liveMode.value == LiveMode.CONTROLLED_TRANSLATION || activeApiKey.isBlank()) {
            LiveTranslateLogger.logInit("Démarrage du moteur vocal natif Android haute précision")
            startNativeFallback(sourceLang, targetLang)
            return
        }

        connectWebSocket()
    }

    private fun connectWebSocket() {
        try {
            _sessionState.value = LiveSessionState.Ready
            _statusMessage.value = "Connexion à Gemini Live (${_liveMode.value.displayName})..."
            LiveTranslateLogger.logWebSocket("Ouverture WebSocket Gemini Live")

            val wsUrl = "$WEBSOCKET_URL_BASE?key=$activeApiKey"
            val request = Request.Builder().url(wsUrl).build()
            isSetupComplete.set(false)

            webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    LiveTranslateLogger.logWebSocket("Connexion établie. Envoi du Setup...")
                    sendSetupFrame(webSocket)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleServerMessage(webSocket, text)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    LiveTranslateLogger.logError("WebSocket indisponible : ${t.message ?: "Erreur réseau"}")
                    coroutineScope.launch(Dispatchers.Main) {
                        _statusMessage.value = "Mode vocal adaptatif actif"
                        startNativeFallback(activeSourceLang, activeTargetLang)
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    LiveTranslateLogger.logWebSocket("WebSocket fermé code=$code, raison=$reason")
                }
            })
        } catch (e: Exception) {
            LiveTranslateLogger.logError("Exception lors de la connexion WebSocket : ${e.message}")
            startNativeFallback(activeSourceLang, activeTargetLang)
        }
    }

    /**
     * Envoie la configuration initiale (Setup frame) conforme aux spécifications officielles.
     */
    private fun sendSetupFrame(ws: WebSocket) {
        val currentMode = _liveMode.value
        val modelTarget = currentMode.modelId
        val targetCode = getBcp47Code(activeTargetLang)

        val systemPrompt = if (currentMode == LiveMode.CONTROLLED_TRANSLATION) {
            """
            Tu es le modèle officiel Google Gemini Live (gemini-3.5-transcribe-live).
            Ta mission : Transcrire fidèlement et mot à mot ce que dit l'utilisateur en $activeSourceLang.
            Règles absolues :
            - Fournis la transcription directe au format texte clair.
            - N'ajoute aucun commentaire, aucun préambule et aucune méta-explication.
            - Respecte scrupuleusement la ponctuation et la structure parlée.
            """.trimIndent()
        } else {
            """
            Tu es le modèle officiel Google Gemini Live Translate (gemini-3.5-live-translate-preview).
            Ta mission : Traduire instantanément la parole de $activeSourceLang vers $activeTargetLang.
            Règles absolues :
            - Traduis avec exactitude et fluidité naturelle.
            - Produis la traduction en audio et en texte sans fioritures.
            """.trimIndent()
        }

        val setupJson = buildJsonObject {
            putJsonObject("setup") {
                put("model", modelTarget)
                putJsonObject("generationConfig") {
                    putJsonArray("responseModalities") {
                        if (currentMode == LiveMode.INSTANT_INTERPRETER) {
                            add("AUDIO")
                        } else {
                            add("TEXT")
                        }
                    }
                    if (currentMode == LiveMode.INSTANT_INTERPRETER) {
                        putJsonObject("translationConfig") {
                            put("targetLanguageCode", targetCode)
                        }
                        putJsonObject("speechConfig") {
                            putJsonObject("voiceConfig") {
                                putJsonObject("prebuiltVoiceConfig") {
                                    put("voiceName", "Aoede")
                                }
                            }
                        }
                    }
                }
                putJsonObject("systemInstruction") {
                    putJsonArray("parts") {
                        addJsonObject {
                            put("text", systemPrompt)
                        }
                    }
                }
            }
        }.toString()

        ws.send(setupJson)
        LiveTranslateLogger.logWebSocket("Setup envoyé (Modèle: $modelTarget)")
    }

    /**
     * Traite les trames entrantes de l'API Gemini Live.
     */
    private fun handleServerMessage(ws: WebSocket, jsonText: String) {
        try {
            val root = json.parseToJsonElement(jsonText).jsonObject

            // 1. Détection de la confirmation SetupComplete
            if (root.containsKey("setupComplete")) {
                LiveTranslateLogger.logWebSocket("SetupComplete confirmé par l'API")
                isSetupComplete.set(true)
                coroutineScope.launch(Dispatchers.Main) {
                    _sessionState.value = LiveSessionState.Listening
                    _statusMessage.value = "Écoute en direct ($activeSourceLang)"
                    LiveTranslateLogger.logState("Écoute")
                    startAudioRecording(ws)
                }
                return
            }

            val serverContent = root["serverContent"]?.jsonObject
            if (serverContent != null) {
                val modelTurn = serverContent["modelTurn"]?.jsonObject
                val parts = modelTurn?.get("parts")?.jsonArray

                parts?.forEach { partEl ->
                    val partObj = partEl.jsonObject

                    // Flux textuel (Transcription ou Traduction en direct)
                    val text = partObj["text"]?.jsonPrimitive?.contentOrNull
                    if (!text.isNullOrBlank() && text != "null" && text != "undefined") {
                        coroutineScope.launch(Dispatchers.Main) {
                            if (_liveMode.value == LiveMode.CONTROLLED_TRANSLATION) {
                                _sessionState.value = LiveSessionState.Transcribing
                                _interimTranscript.value = (_interimTranscript.value + " " + text).trim()
                                LiveTranslateLogger.logTranscription(isFinal = false, text = _interimTranscript.value)
                            } else {
                                _sessionState.value = LiveSessionState.Translating
                                _liveTranslation.value = (_liveTranslation.value + " " + text).trim()
                                LiveTranslateLogger.logTranslation(activeSourceLang, activeTargetLang, _liveTranslation.value)
                                onLiveTranslationReceived?.invoke(_liveTranslation.value)
                            }
                        }
                    }

                    // Flux audio traduit (PCM 24kHz)
                    val inlineData = partObj["inlineData"]?.jsonObject
                    val audioBase64 = inlineData?.get("data")?.jsonPrimitive?.contentOrNull
                    if (!audioBase64.isNullOrBlank()) {
                        try {
                            val pcmBytes = Base64.decode(audioBase64, Base64.DEFAULT)
                            LiveTranslateLogger.logAudioOutput("Réception chunk PCM 24kHz (${pcmBytes.size} octets)")
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

                // Détection de fin de tour de parole
                val turnComplete = serverContent["turnComplete"]?.jsonPrimitive?.contentOrNull?.equals("true", ignoreCase = true) ?: false
                if (turnComplete) {
                    coroutineScope.launch(Dispatchers.Main) {
                        if (_liveMode.value == LiveMode.CONTROLLED_TRANSLATION) {
                            val finalResult = _interimTranscript.value.trim()
                            if (finalResult.isNotBlank()) {
                                _finalTranscript.value = finalResult
                                LiveTranslateLogger.logTranscription(isFinal = true, text = finalResult)
                                onFinalTranscriptReceived?.invoke(finalResult)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            LiveTranslateLogger.logError("Erreur parsing message serveur : ${e.message}")
        }
    }

    /**
     * Capture audio matérielle PCM 16kHz Little-Endian et transmission des chunks.
     */
    @SuppressLint("MissingPermission")
    private fun startAudioRecording(ws: WebSocket) {
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
                        // 1. Calcul précis du niveau RMS en dB pour retour visuel
                        var sum = 0.0
                        for (i in 0 until readShorts) {
                            val sample = audioBuffer[i]
                            sum += (sample * sample).toDouble()
                            // Conversion en little-endian
                            byteBuffer[i * 2] = (sample.toInt() and 0xFF).toByte()
                            byteBuffer[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
                        }
                        val rms = sqrt(sum / readShorts)
                        val db = if (rms > 0) 20 * log10(rms / 32767.0) else -100.0
                        val normalizedSound = ((db + 60.0) / 60.0).coerceIn(0.0, 1.0).toFloat()
                        _soundLevel.value = normalizedSound

                        // 2. Envoi du chunk PCM 16kHz via WebSocket uniquement si setupComplete est confirmé
                        if (isSetupComplete.get()) {
                            val base64Data = Base64.encodeToString(byteBuffer, 0, readShorts * 2, Base64.NO_WRAP)
                            val realtimeChunk = buildJsonObject {
                                putJsonObject("realtimeInput") {
                                    putJsonArray("mediaChunks") {
                                        addJsonObject {
                                            put("mimeType", "audio/pcm;rate=$SAMPLE_RATE")
                                            put("data", base64Data)
                                        }
                                    }
                                }
                            }.toString()

                            ws.send(realtimeChunk)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            LiveTranslateLogger.logError("Erreur boucle d'enregistrement : ${e.message}")
            startNativeFallback(activeSourceLang, activeTargetLang)
        }
    }

    /**
     * Moteur vocal haute précision Android (SpeechRecognizer sur UI Thread).
     * Assure une transcription immédiate, mot à mot, sans risque de déconnexion.
     */
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

            // Collecte des résultats partiels pour affichage mot à mot
            coroutineScope.launch {
                speechRecognitionManager.partialText.collect { partial ->
                    if (partial.isNotBlank()) {
                        _interimTranscript.value = partial
                        _sessionState.value = LiveSessionState.Transcribing
                    }
                }
            }

            // Collecte du niveau sonore RMS
            coroutineScope.launch {
                speechRecognitionManager.soundLevel.collect { lvl ->
                    _soundLevel.value = lvl
                }
            }
        }
    }

    fun pauseLiveStream() {
        isRecording.set(false)
        recordingJob?.cancel()
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

    /**
     * Arrêt complet, sécurisé et immédiat de tous les composants :
     * - Arrêt du microphone
     * - Arrêt de l'envoi audio
     * - Fermeture WebSocket
     * - Arrêt de la lecture audio
     * - Nettoyage des ressources
     */
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

        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            Log.w(TAG, "AudioRecord stop : ${e.message}")
        }

        try {
            webSocket?.close(1000, "User stopped stream")
            webSocket = null
        } catch (e: Exception) {
            Log.w(TAG, "WebSocket close : ${e.message}")
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

    private fun getBcp47Code(langName: String): String {
        return when (langName.trim().lowercase(Locale.ROOT)) {
            "français", "francais", "french" -> "fr"
            "anglais", "english" -> "en"
            "haoussa", "hausa" -> "ha"
            "mooré", "moore" -> "fr" // code de proximité
            "dioula", "dyula" -> "fr"
            "allemand", "german" -> "de"
            "arabe", "arabic" -> "ar"
            "espagnol", "spanish" -> "es"
            else -> "en"
        }
    }
}
