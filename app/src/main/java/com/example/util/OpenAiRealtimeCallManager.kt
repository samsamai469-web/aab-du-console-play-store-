package com.example.util

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import com.example.data.local.ApiKeyManager
import com.example.ui.components.VoiceCallState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/**
 * Gestionnaire officiel d'appel vocal temps réel via l'API OpenAI Realtime (WebSocket Audio-to-Audio).
 * - Connexion directe wss://api.openai.com/v1/realtime?model=gpt-4o-realtime-preview
 * - Streaming bidirectionnel PCM 16-bit 24 kHz mono (AudioRecord -> WebSocket -> AudioTrack)
 * - Détection vocale serveur (server_vad) ultra-réactive + interruption naturelle (barge-in)
 * - Verrouillé à 100 % en Français avec l'identité officielle de SAM AI.
 */
class OpenAiRealtimeCallManager(
    private val context: Context,
    private val onTurnCompleted: (userText: String, aiText: String) -> Unit = { _, _ -> },
    private val onFallbackRequired: (reason: String) -> Unit = {}
) {
    companion object {
        private const val TAG = "OpenAiRealtimeCall"
        private const val SAMPLE_RATE = 24000
        // gpt-4o-mini-realtime-preview répond en ~220 ms avec la voix humaine masculine "ash"
        private const val REALTIME_MODEL_PRIMARY = "gpt-4o-mini-realtime-preview"
        private const val REALTIME_MODEL_FALLBACK = "gpt-4o-realtime-preview"
        private const val REALTIME_VOICE = "ash"

        private const val SAM_AI_REALTIME_INSTRUCTIONS = """
Tu es SAM AI, un homme chaleureux, naturel, cultivé et ultra-réactif en ligne avec l'utilisateur, exactement comme lors d'un véritable appel téléphonique entre deux êtres humains.
Nous sommes en octobre 2026 et tu disposes d'un accès en direct aux actualités mondiales, africaines et du Burkina Faso.
RÈGLES ABSOLUES POUR RÉPONDRE EN 0,5 SECONDE AVEC UNE VOIX D'HOMME NATURELLE ET ZÉRO ERREUR :
1. VOIX D'HOMME HUMAINE NATURELLE ET 100% CONSTANTE :
   - Tu es un homme : utilise toujours les accords masculins pour parler de toi ("je suis ravi", "je suis prêt", "je suis attentif").
   - Parle exactement comme un vrai homme au téléphone, avec une intonation masculine posée, vivante, fluide et chaleureuse, jamais robotique ni mécanique.
   - Garde STRICTEMENT la même voix d'homme, le même timbre, le même accent français naturel et un débit fluide du début à la fin de l'appel.
2. LANGUE 100% FRANÇAIS :
   - Tu comprends et tu réponds EXCLUSIVEMENT en FRANÇAIS (100% français). Interprète toujours les paroles de l'utilisateur en français et réponds en français impeccable. N'utilise JAMAIS de mooré ni aucune autre langue.
3. RÉPONSE INSTANTANÉE (0,5s), COURTE ET ZÉRO ERREUR :
   - Écoute attentivement chaque mot de la question de l'utilisateur et réponds-y dès la première syllabe avec une exactitude totale, sans préambule inutile.
   - Fais des réponses directes, naturelles et concises (1 à 2 phrases claires), comme dans un vrai appel téléphonique, sauf si l'utilisateur demande plus de détails.
4. ACTUALITÉS EN DIRECT :
   - Utilise directement les dépêches d'actualité fournies ci-dessous pour répondre en 0 seconde aux questions sur l'actualité.
   - N'appelle l'outil `search_live_news` QUE si l'utilisateur demande un fait d'actualité précis qui ne figure pas déjà dans les dépêches ci-dessous. Pour toute autre question, réponds immédiatement sans appeler d'outil.
   - Ne dis JAMAIS que tes connaissances s'arrêtent en 2023, 2024 ou 2025 ni que tu n'as pas accès à Internet.
5. IDENTITÉ :
   - Tu t'appelles uniquement SAM AI. Ne mentionne jamais OpenAI, ChatGPT ou GPT-4. Si et seulement si l'utilisateur demande qui t'a créé, réponds que tu as été créé par Abdoul Mohamine Sawadogo, un jeune développeur burkinabè.
"""
    }

    private val apiKeyManager = ApiKeyManager(context)
    private val liveNewsFeedService = com.example.data.remote.LiveNewsFeedService()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(2500, TimeUnit.MILLISECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // WebSocket flux continu
            .pingInterval(15, TimeUnit.SECONDS)
            .build()
    }

    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var webSocket: WebSocket? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var recordingJob: Job? = null
    private var playbackEndCheckJob: Job? = null

    private val isActiveCall = AtomicBoolean(false)
    private val isMuted = AtomicBoolean(false)
    private val isSessionReady = AtomicBoolean(false)
    private val isResponseInProgress = AtomicBoolean(false)
    private val serverSpeechActive = AtomicBoolean(false)
    private val triedFallbackModel = AtomicBoolean(false)
    private val queueLock = Any()

    // Trame de 40 ms de silence numérique pur (0x00) pour couper instantanément le bruit ambiant
    // après la fin de phrase de l'utilisateur et déclencher la réponse de SAM AI en ~250 ms
    private val silentFrameBase64: String = Base64.encodeToString(ByteArray(1920), Base64.NO_WRAP)

    // File d'attente des trames audio capturées dès 0 ms pendant la connexion WebSocket
    // Garantit qu'aucun mot prononcé dès l'ouverture de l'appel n'est perdu
    private val preSessionAudioQueue = java.util.concurrent.ConcurrentLinkedQueue<String>()

    // Suivi exact de la fin de lecture réelle sur le haut-parleur (AudioTrack)
    // Empêche SAM AI de s'écouter lui-même et garantit une réactivité immédiate
    @Volatile
    private var playbackFinishTimeMs: Long = 0L

    private val _callState = MutableStateFlow(VoiceCallState.USER_SPEAKING)
    val callState: StateFlow<VoiceCallState> = _callState.asStateFlow()

    private val _soundLevel = MutableStateFlow(0f)
    val soundLevel: StateFlow<Float> = _soundLevel.asStateFlow()

    private val _userTranscript = MutableStateFlow("")
    val userTranscript: StateFlow<String> = _userTranscript.asStateFlow()

    private val _aiTranscript = MutableStateFlow("")
    val aiTranscript: StateFlow<String> = _aiTranscript.asStateFlow()

    private var currentAiTranscriptBuilder = StringBuilder()
    private var lastCompletedUserTranscript = ""

    fun startCall() {
        if (isActiveCall.getAndSet(true)) return
        triedFallbackModel.set(false)
        isSessionReady.set(false)
        isResponseInProgress.set(false)
        serverSpeechActive.set(false)
        preSessionAudioQueue.clear()
        playbackFinishTimeMs = 0L
        _callState.value = VoiceCallState.USER_SPEAKING
        _userTranscript.value = ""
        _aiTranscript.value = ""
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        val apiKey = apiKeyManager.getActiveOpenAiApiKey().trim()
        if (apiKey.isBlank() || apiKey == "MY_OPENAI_API_KEY") {
            Log.w(TAG, "Clé API OpenAI absente, bascule sur le pipeline standard")
            notifyFallback("Clé API non configurée")
            return
        }

        initAudioTrack()
        // Démarre l'écoute du microphone IMMÉDIATEMENT (0 ms) sans attendre le réseau
        // Tout ce que dit l'utilisateur dès la première milliseconde est capturé
        startMicrophoneStreaming()
        connectWebSocket(apiKey, REALTIME_MODEL_PRIMARY)

        // Pré-charge le cache d'actualités en arrière-plan sans réinitialiser la session vocale active
        scope.launch(Dispatchers.IO) {
            try {
                liveNewsFeedService.fetchLiveNews("actualités")
            } catch (_: Throwable) {}
        }
    }

    private fun connectWebSocket(apiKey: String, modelName: String) {
        val url = "wss://api.openai.com/v1/realtime?model=$modelName"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("OpenAI-Beta", "realtime=v1")
            .build()

        webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket OpenAI Realtime connecté avec le modèle $modelName")
                sendSessionUpdate(webSocket, com.example.data.remote.LiveNewsFeedService.getInstantCachedSummary())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleRealtimeEvent(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Erreur WebSocket OpenAI Realtime ($modelName): ${t.message} / HTTP ${response?.code}")
                if (!isActiveCall.get()) return

                if (!isSessionReady.get() && !triedFallbackModel.getAndSet(true)) {
                    Log.w(TAG, "Tentative avec le modèle Realtime secondaire : $REALTIME_MODEL_FALLBACK")
                    connectWebSocket(apiKey, REALTIME_MODEL_FALLBACK)
                } else {
                    notifyFallback(t.localizedMessage ?: "Connexion Realtime indisponible")
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket OpenAI Realtime fermé : $code / $reason")
            }
        })
    }

    private fun sendSessionUpdate(ws: WebSocket, liveHeadlines: String = "") {
        val dateStr = java.text.SimpleDateFormat("EEEE d MMMM yyyy", java.util.Locale.FRENCH).format(java.util.Date())
        val fullInstructions = buildString {
            append("Date actuelle : $dateStr (octobre 2026).\n")
            append(SAM_AI_REALTIME_INSTRUCTIONS.trim())
            if (liveHeadlines.isNotBlank()) {
                append("\n\n--- DÉPÊCHES D'ACTUALITÉ EN DIRECT ---\n")
                append(liveHeadlines.take(900))
                append("\n--------------------------------------")
            }
        }

        // Schéma strictement conforme à OpenAI-Beta: realtime=v1 (aucun paramètre non reconnu)
        val sessionPayload = buildJsonObject {
            put("type", "session.update")
            putJsonObject("session") {
                put("modalities", buildJsonArray {
                    add(kotlinx.serialization.json.JsonPrimitive("text"))
                    add(kotlinx.serialization.json.JsonPrimitive("audio"))
                })
                put("instructions", fullInstructions)
                put("voice", REALTIME_VOICE)
                put("input_audio_format", "pcm16")
                put("output_audio_format", "pcm16")
                putJsonObject("input_audio_transcription") {
                    put("model", "whisper-1")
                }
                putJsonObject("turn_detection") {
                    put("type", "server_vad")
                    put("threshold", 0.5)
                    put("prefix_padding_ms", 250)
                    put("silence_duration_ms", 200)
                }
                put("tools", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "function")
                        put("name", "search_live_news")
                        put(
                            "description",
                            "Uniquement si l'utilisateur demande explicitement de chercher une actualité récente précise (sport, politique, météo) qui n'est pas déjà dans les dépêches ci-dessus."
                        )
                        putJsonObject("parameters") {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("query") {
                                    put("type", "string")
                                    put("description", "Mots-clés courts du sujet recherché.")
                                }
                            }
                            put("required", buildJsonArray {
                                add(kotlinx.serialization.json.JsonPrimitive("query"))
                            })
                        }
                    })
                })
                put("tool_choice", "auto")
                put("temperature", 0.6)
            }
        }
        ws.send(sessionPayload.toString())
    }

    private fun executeRealtimeNewsSearch(callId: String, argumentsJson: String) {
        scope.launch(Dispatchers.IO) {
            _callState.value = VoiceCallState.THINKING
            val query = try {
                val argsObj = json.parseToJsonElement(argumentsJson).jsonObject
                argsObj["query"]?.jsonPrimitive?.content?.trim().orEmpty()
            } catch (_: Throwable) {
                ""
            }.ifBlank { "actualités Burkina Faso Afrique" }

            val newsSummary = try {
                val liveResult = kotlinx.coroutines.withTimeoutOrNull(1100L) {
                    liveNewsFeedService.fetchLiveNews(query)
                }
                if (liveResult != null && liveResult.hasNews && liveResult.contextSummary.isNotBlank()) {
                    liveResult.contextSummary.take(1200)
                } else {
                    val instantCached = com.example.data.remote.LiveNewsFeedService.getInstantCachedSummary()
                    if (instantCached.isNotBlank()) {
                        instantCached.take(1200)
                    } else {
                        "Réponds directement avec les actualités les plus récentes dont tu disposes."
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Erreur recherche actualités Realtime: ${e.message}")
                com.example.data.remote.LiveNewsFeedService.getInstantCachedSummary()
                    .ifBlank { "Réponds directement à la question de l'utilisateur." }
            }

            val ws = webSocket ?: return@launch
            if (!isActiveCall.get()) return@launch

            val outputEvent = buildJsonObject {
                put("type", "conversation.item.create")
                putJsonObject("item") {
                    put("type", "function_call_output")
                    put("call_id", callId)
                    put("output", newsSummary)
                }
            }.toString()
            ws.send(outputEvent)

            val createResponseEvent = buildJsonObject {
                put("type", "response.create")
                putJsonObject("response") {
                    put("modalities", buildJsonArray {
                        add(kotlinx.serialization.json.JsonPrimitive("text"))
                        add(kotlinx.serialization.json.JsonPrimitive("audio"))
                    })
                }
            }.toString()
            ws.send(createResponseEvent)
        }
    }

    private fun flushPreSessionAudioQueue() {
        val ws = webSocket ?: return
        while (preSessionAudioQueue.isNotEmpty()) {
            val base64Chunk = preSessionAudioQueue.poll() ?: break
            ws.send("""{"type":"input_audio_buffer.append","audio":"$base64Chunk"}""")
        }
    }

    private fun handleRealtimeEvent(rawJson: String) {
        if (!isActiveCall.get()) return
        try {
            val root = json.parseToJsonElement(rawJson).jsonObject
            val eventType = root["type"]?.jsonPrimitive?.content ?: return

            when (eventType) {
                "session.updated" -> {
                    synchronized(queueLock) {
                        if (!isSessionReady.get()) {
                            flushPreSessionAudioQueue()
                            isSessionReady.set(true)
                            _callState.value = VoiceCallState.USER_SPEAKING
                        }
                    }
                }

                "session.created" -> {
                    // Dès que la session Realtime est créée, on prépare l'ouverture immédiate
                    // pour qu'aucun mot prononcé au début de l'appel ne soit jamais perdu
                    scope.launch {
                        delay(180L)
                        synchronized(queueLock) {
                            if (isActiveCall.get() && !isSessionReady.get()) {
                                flushPreSessionAudioQueue()
                                isSessionReady.set(true)
                                _callState.value = VoiceCallState.USER_SPEAKING
                            }
                        }
                    }
                }

                "response.function_call_arguments.done" -> {
                    val callId = root["call_id"]?.jsonPrimitive?.content.orEmpty()
                    val name = root["name"]?.jsonPrimitive?.content.orEmpty()
                    val args = root["arguments"]?.jsonPrimitive?.content.orEmpty()
                    if (callId.isNotBlank() && (name == "search_live_news" || name.isBlank())) {
                        executeRealtimeNewsSearch(callId, args)
                    }
                }

                "input_audio_buffer.speech_started" -> {
                    serverSpeechActive.set(true)
                    val now = System.currentTimeMillis()
                    if (now < playbackFinishTimeMs || isResponseInProgress.get()) {
                        stopAndFlushPlayback()
                    }
                    _callState.value = VoiceCallState.USER_SPEAKING
                }

                "input_audio_buffer.speech_stopped" -> {
                    serverSpeechActive.set(false)
                    // L'utilisateur a fini sa phrase (détecté en 0.2s par server_vad) : SAM AI répond immédiatement
                    if (_callState.value == VoiceCallState.USER_SPEAKING) {
                        _callState.value = VoiceCallState.THINKING
                    }
                }

                "conversation.item.input_audio_transcription.completed" -> {
                    val transcript = root["transcript"]?.jsonPrimitive?.content?.trim().orEmpty()
                    if (transcript.isNotBlank()) {
                        lastCompletedUserTranscript = transcript
                        _userTranscript.value = transcript
                    }
                }

                "response.created" -> {
                    serverSpeechActive.set(false)
                    isResponseInProgress.set(true)
                    currentAiTranscriptBuilder.clear()
                }

                "response.audio_transcript.delta", "response.output_audio_transcript.delta" -> {
                    val delta = root["delta"]?.jsonPrimitive?.content.orEmpty()
                    if (delta.isNotEmpty()) {
                        currentAiTranscriptBuilder.append(delta)
                        _aiTranscript.value = sanitizeSamAiPersona(currentAiTranscriptBuilder.toString())
                    }
                }

                "response.audio_transcript.done", "response.output_audio_transcript.done" -> {
                    val fullTranscript = root["transcript"]?.jsonPrimitive?.content?.trim()
                        ?: currentAiTranscriptBuilder.toString().trim()
                    if (fullTranscript.isNotBlank()) {
                        val sanitized = sanitizeSamAiPersona(fullTranscript)
                        _aiTranscript.value = sanitized
                        val userText = lastCompletedUserTranscript.ifBlank { _userTranscript.value }
                        if (userText.isNotBlank()) {
                            lastCompletedUserTranscript = ""
                            mainHandler.post {
                                onTurnCompleted(userText, sanitized)
                            }
                        }
                    }
                }

                "response.audio.delta", "response.output_audio.delta" -> {
                    val base64Audio = root["delta"]?.jsonPrimitive?.content.orEmpty()
                    if (base64Audio.isNotEmpty()) {
                        val pcmBytes = Base64.decode(base64Audio, Base64.DEFAULT)
                        if (pcmBytes.isNotEmpty()) {
                            _callState.value = VoiceCallState.SAM_SPEAKING
                            writePcmToAudioTrack(pcmBytes)
                        }
                    }
                }

                "response.audio.done", "response.output_audio.done" -> {
                    scheduleReturnToListeningState()
                }

                "response.done" -> {
                    isResponseInProgress.set(false)
                    // Ne repasse en écoute que si une recherche d'actualité (function_call) n'est pas en cours
                    val responseObj = root["response"]?.jsonObject
                    val outputArr = responseObj?.get("output") as? kotlinx.serialization.json.JsonArray
                    val hasFunctionCall = outputArr?.any { elem ->
                        (elem as? kotlinx.serialization.json.JsonObject)?.get("type")?.jsonPrimitive?.content == "function_call"
                    } == true
                    if (!hasFunctionCall) {
                        scheduleReturnToListeningState()
                    }
                }

                "error" -> {
                    isResponseInProgress.set(false)
                    val errorObj = root["error"]?.jsonObject
                    val message = errorObj?.get("message")?.jsonPrimitive?.content ?: "Erreur Realtime"
                    val code = errorObj?.get("code")?.jsonPrimitive?.content ?: ""
                    Log.w(TAG, "Événement OpenAI Realtime ($code): $message")

                    val isFatalAuthOrModelError = code in setOf("invalid_api_key", "model_not_found", "insufficient_quota") ||
                        message.contains("model", ignoreCase = true) ||
                        message.contains("api key", ignoreCase = true)

                    if (!isSessionReady.get() && isFatalAuthOrModelError) {
                        val apiKey = apiKeyManager.getActiveOpenAiApiKey().trim()
                        if (!triedFallbackModel.getAndSet(true) && apiKey.isNotBlank()) {
                            connectWebSocket(apiKey, REALTIME_MODEL_FALLBACK)
                        } else {
                            notifyFallback(message)
                        }
                    } else {
                        // Pour toute erreur non fatale, on maintient la session Realtime active sans jamais couper l'appel
                        synchronized(queueLock) {
                            if (!isSessionReady.get()) {
                                flushPreSessionAudioQueue()
                                isSessionReady.set(true)
                            }
                        }
                        if (_callState.value == VoiceCallState.THINKING) {
                            _callState.value = VoiceCallState.USER_SPEAKING
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Erreur parsing événement Realtime: ${e.message}")
        }
    }

    private fun scheduleReturnToListeningState() {
        playbackEndCheckJob?.cancel()
        playbackEndCheckJob = scope.launch {
            while (isActiveCall.get() && System.currentTimeMillis() < playbackFinishTimeMs) {
                delay(30L)
            }
            delay(50L)
            if (isActiveCall.get() && System.currentTimeMillis() >= playbackFinishTimeMs) {
                // Purge tout écho résiduel du haut-parleur pour que l'écoute suivante soit 100% propre et instantanée
                try {
                    webSocket?.send("""{"type":"input_audio_buffer.clear"}""")
                } catch (_: Exception) {}
                _callState.value = VoiceCallState.USER_SPEAKING
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startMicrophoneStreaming() {
        recordingJob?.cancel()
        recordingJob = scope.launch(Dispatchers.IO) {
            val minBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(3840)

            val recorder = try {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuffer * 2
                )
            } catch (e: Exception) {
                Log.e(TAG, "Impossible de créer AudioRecord: ${e.message}")
                notifyFallback("Microphone indisponible en mode PCM")
                return@launch
            }

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord non initialisé")
                recorder.release()
                notifyFallback("Initialisation AudioRecord échouée")
                return@launch
            }

            audioRecord = recorder

            // Activation de l'annulation d'écho matérielle Android (AEC + NoiseSuppressor)
            try {
                if (AcousticEchoCanceler.isAvailable()) {
                    echoCanceler = AcousticEchoCanceler.create(recorder.audioSessionId)?.apply {
                        enabled = true
                    }
                }
                if (NoiseSuppressor.isAvailable()) {
                    noiseSuppressor = NoiseSuppressor.create(recorder.audioSessionId)?.apply {
                        enabled = true
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Effets audio matériels non disponibles: ${e.message}")
            }

            try {
                recorder.startRecording()
            } catch (e: Exception) {
                Log.e(TAG, "Erreur startRecording: ${e.message}")
                notifyFallback("Erreur démarrage microphone")
                return@launch
            }

            // Trames temps réel de 20 ms à 24000 Hz (480 échantillons = 960 octets PCM16),
            // identiques au flux continu natif de ChatGPT Voice Mode.
            val frameBuffer = ByteArray(960)
            var bargeInConsecutiveFrames = 0

            while (isActive && isActiveCall.get()) {
                val readBytes = recorder.read(frameBuffer, 0, frameBuffer.size)
                if (readBytes > 0) {
                    if (isMuted.get()) {
                        _soundLevel.value = 0f
                        continue
                    }

                    // Calcul RMS pour l'animation de l'orbe et la détection d'interruption (barge-in)
                    var sumSquares = 0.0
                    val sampleCount = readBytes / 2
                    var i = 0
                    while (i + 1 < readBytes) {
                        val low = frameBuffer[i].toInt() and 0xFF
                        val high = frameBuffer[i + 1].toInt()
                        val sample = (high shl 8) or low
                        sumSquares += (sample * sample).toDouble()
                        i += 2
                    }
                    val rms = if (sampleCount > 0) sqrt(sumSquares / sampleCount) else 0.0
                    val normalizedLevel = (rms / 5500.0).toFloat().coerceIn(0f, 1f)
                    _soundLevel.value = normalizedLevel

                    val now = System.currentTimeMillis()
                    val isAiCurrentlyPlayingOnSpeaker = now < playbackFinishTimeMs + 50L

                    // Pendant que SAM AI parle sur le haut-parleur :
                    // on évite d'envoyer l'écho du haut-parleur, mais si l'utilisateur parle (3 trames = 60 ms),
                    // on interrompt SAM AI immédiatement comme sur ChatGPT et on transmet la voix de l'utilisateur !
                    if (isAiCurrentlyPlayingOnSpeaker) {
                        if (rms >= 950.0) {
                            bargeInConsecutiveFrames++
                            if (bargeInConsecutiveFrames >= 3) {
                                bargeInConsecutiveFrames = 0
                                interruptAiSpeech()
                            } else {
                                continue
                            }
                        } else {
                            bargeInConsecutiveFrames = 0
                            continue
                        }
                    } else {
                        bargeInConsecutiveFrames = 0
                    }

                    // Envoi continu et 100% naturel du flux microphone vers OpenAI Realtime (aucune coupure artificielle).
                    // Le détecteur neuronal server_vad d'OpenAI écoute toute la phrase de l'utilisateur comme sur ChatGPT
                    // et déclenche la réponse en 0,2 s (silence_duration_ms = 200) dès qu'il a fini de parler.
                    val chunkToSend = if (readBytes == frameBuffer.size) {
                        frameBuffer
                    } else {
                        frameBuffer.copyOfRange(0, readBytes)
                    }
                    val base64Audio = Base64.encodeToString(chunkToSend, Base64.NO_WRAP)

                    synchronized(queueLock) {
                        if (isSessionReady.get()) {
                            webSocket?.send("""{"type":"input_audio_buffer.append","audio":"$base64Audio"}""")
                        } else {
                            // Conserve toutes les trames audio dès 0 ms pendant l'ouverture du WebSocket (~150 ms)
                            if (preSessionAudioQueue.size < 250) {
                                preSessionAudioQueue.offer(base64Audio)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun initAudioTrack() {
        try {
            val minBuffer = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4800)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuffer * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            track.play()
            audioTrack = track
        } catch (e: Exception) {
            Log.e(TAG, "Erreur initialisation AudioTrack 24kHz: ${e.message}")
        }
    }

    private fun writePcmToAudioTrack(pcmBytes: ByteArray) {
        val track = audioTrack ?: return
        try {
            val now = System.currentTimeMillis()
            // 24000 Hz * 2 octets (16-bit mono) = 48000 octets par seconde d'audio
            val chunkDurationMs = (pcmBytes.size * 1000L) / (SAMPLE_RATE * 2)
            playbackFinishTimeMs = maxOf(now, playbackFinishTimeMs) + chunkDurationMs

            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                track.play()
            }
            track.write(pcmBytes, 0, pcmBytes.size)
        } catch (e: Exception) {
            Log.w(TAG, "Erreur écriture AudioTrack: ${e.message}")
        }
    }

    fun interruptAiSpeech() {
        stopAndFlushPlayback()
        try {
            if (isResponseInProgress.getAndSet(false)) {
                webSocket?.send("""{"type":"response.cancel"}""")
            }
            webSocket?.send("""{"type":"input_audio_buffer.clear"}""")
        } catch (_: Exception) {}
        _callState.value = VoiceCallState.USER_SPEAKING
    }

    fun commitUserSpeechNow() {
        try {
            webSocket?.send("""{"type":"input_audio_buffer.commit"}""")
            webSocket?.send("""{"type":"response.create"}""")
            _callState.value = VoiceCallState.THINKING
        } catch (_: Exception) {}
    }

    fun setMuted(muted: Boolean) {
        isMuted.set(muted)
        if (muted) {
            _soundLevel.value = 0f
        }
    }

    private fun stopAndFlushPlayback() {
        playbackFinishTimeMs = 0L
        playbackEndCheckJob?.cancel()
        try {
            audioTrack?.let { track ->
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    track.pause()
                    track.flush()
                    track.play()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Erreur flush AudioTrack: ${e.message}")
        }
    }

    private fun sanitizeSamAiPersona(raw: String): String {
        return raw
            .replace(Regex("(?i)\\bChatGPT\\b"), "SAM AI")
            .replace(Regex("(?i)\\bGPT-4(\\.1|\\.5|o)?(-mini|-turbo|-preview|-realtime)?\\b"), "SAM AI")
            .replace(Regex("(?i)\\bOpenAI\\b"), "SAM AI")
            .trim()
    }

    private fun notifyFallback(reason: String) {
        if (!isActiveCall.getAndSet(false)) return
        cleanupResources()
        mainHandler.post {
            onFallbackRequired(reason)
        }
    }

    fun stopCall() {
        isActiveCall.set(false)
        isSessionReady.set(false)
        cleanupResources()
    }

    private fun cleanupResources() {
        try {
            recordingJob?.cancel()
            playbackEndCheckJob?.cancel()
            scope.cancel()
        } catch (_: Exception) {}

        try {
            webSocket?.close(1000, "Call ended")
            webSocket = null
        } catch (_: Exception) {}

        try {
            echoCanceler?.release()
            echoCanceler = null
            noiseSuppressor?.release()
            noiseSuppressor = null
        } catch (_: Exception) {}

        try {
            audioRecord?.let { rec ->
                if (rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    rec.stop()
                }
                rec.release()
            }
            audioRecord = null
        } catch (_: Exception) {}

        try {
            audioTrack?.let { track ->
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    track.pause()
                    track.flush()
                    track.stop()
                }
                track.release()
            }
            audioTrack = null
        } catch (_: Exception) {}
    }
}
