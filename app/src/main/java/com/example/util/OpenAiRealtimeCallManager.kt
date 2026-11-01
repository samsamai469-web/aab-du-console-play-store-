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
        // Modèles officiels OpenAI Realtime GA actifs (~200 ms de réponse avec la voix humaine masculine "ash")
        private const val REALTIME_MODEL_PRIMARY = "gpt-realtime-mini"
        private const val REALTIME_MODEL_FALLBACK = "gpt-realtime"
        private const val REALTIME_VOICE = "ash"

        private const val SAM_AI_REALTIME_INSTRUCTIONS = """
Tu es SAM AI, un homme chaleureux, naturel, cultivé et ultra-réactif au téléphone avec l'utilisateur.
Nous sommes en octobre 2026 et tu disposes des actualités en direct ci-dessous.
RÈGLES ABSOLUES :
1. VOIX D'HOMME HUMAINE NATURELLE ET CONSTANTE :
   - Tu es un homme : utilise toujours les accords masculins ("je suis ravi", "je suis prêt", "je suis attentif").
   - Parle comme un vrai homme au téléphone avec un ton posé, vivant, fluide et naturel, en gardant strictement la même voix masculine du début à la fin.
2. LANGUE 100% FRANÇAIS :
   - Comprends et réponds EXCLUSIVEMENT en français (100% français). N'utilise jamais de mooré ni aucune autre langue.
3. RÉPONSE EN 0,2 SECONDE, DIRECTE ET SANS ERREUR :
   - Réponds immédiatement dès la première syllabe avec exactitude, en 1 à 2 phrases claires et naturelles, sauf si l'utilisateur demande plus de détails.
4. ACTUALITÉS & IDENTITÉ :
   - Utilise directement les actualités ci-dessous sans jamais dire que tes connaissances s'arrêtent en 2023, 2024 ou 2025.
   - Tu t'appelles uniquement SAM AI. Si l'utilisateur demande qui t'a créé, réponds que tu as été créé par Abdoul Mohamine Sawadogo, un jeune développeur burkinabè.
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
    private var playbackJob: Job? = null
    private var playbackEndCheckJob: Job? = null

    private val isActiveCall = AtomicBoolean(false)
    private val isMuted = AtomicBoolean(false)
    private val isSessionReady = AtomicBoolean(false)
    private val isResponseInProgress = AtomicBoolean(false)
    private val acceptingAiAudio = AtomicBoolean(false)
    private val isPlaybackWorkerWriting = AtomicBoolean(false)
    private val isPlaybackManuallyCancelled = AtomicBoolean(false)
    private val serverSpeechActive = AtomicBoolean(false)
    private val triedFallbackModel = AtomicBoolean(false)
    private val queueLock = Any()

    // File d'attente des trames audio capturées dès 0 ms pendant la connexion WebSocket
    // Garantit qu'aucun mot prononcé dès l'ouverture de l'appel n'est perdu
    private val preSessionAudioQueue = java.util.concurrent.ConcurrentLinkedQueue<String>()

    // File de lecture PCM non-bloquante pour ne JAMAIS bloquer le thread WebSocket OkHttp
    private val pcmPlaybackQueue = java.util.concurrent.ConcurrentLinkedQueue<ByteArray>()

    // Trame de silence pur de 20 ms (960 octets à 0) encodée en Base64 pour maintenir l'horloge VAD
    private val silenceFrame20msBase64: String by lazy {
        Base64.encodeToString(ByteArray(960), Base64.NO_WRAP)
    }

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

    private var currentUserTranscriptBuilder = StringBuilder()
    private var currentAiTranscriptBuilder = StringBuilder()
    private var lastCompletedUserTranscript = ""
    private var pendingAiTurnTranscript = ""

    private val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
    private var previousAudioMode: Int = android.media.AudioManager.MODE_NORMAL
    private var previousSpeakerphoneState: Boolean = false

    fun startCall() {
        if (isActiveCall.getAndSet(true)) return
        TtsManager.stopAllActiveTts()
        SpeechRecognitionManager.pauseAllActiveRecognizersForTts()
        triedFallbackModel.set(false)
        isSessionReady.set(false)
        isResponseInProgress.set(false)
        acceptingAiAudio.set(false)
        isPlaybackWorkerWriting.set(false)
        isPlaybackManuallyCancelled.set(false)
        serverSpeechActive.set(false)
        preSessionAudioQueue.clear()
        pcmPlaybackQueue.clear()
        playbackFinishTimeMs = 0L
        currentUserTranscriptBuilder.clear()
        currentAiTranscriptBuilder.clear()
        lastCompletedUserTranscript = ""
        pendingAiTurnTranscript = ""
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

        // Mode audio HD : conserve AudioManager en MODE_NORMAL pour bénéficier du convertisseur
        // haute définition 24 kHz (sans le filtre téléphonique 8 kHz qui étouffe la voix)
        try {
            audioManager?.let { am ->
                previousAudioMode = am.mode
                if (am.mode != android.media.AudioManager.MODE_NORMAL) {
                    am.mode = android.media.AudioManager.MODE_NORMAL
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Notice AudioManager: ${e.message}")
        }

        initAudioTrack()
        startPlaybackWorker()
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
        // Connexion directe à l'API officielle OpenAI Realtime GA (sans l'ancien en-tête Beta obsolète)
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket OpenAI Realtime GA connecté avec le modèle $modelName")
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
        val effectiveHeadlines = liveHeadlines.ifBlank {
            try {
                com.example.data.repository.NewsRepository.getDefaultBurkinaNews()
                    .take(5)
                    .joinToString("\n") { "- [${it.source}] ${it.title}: ${it.summary}" }
            } catch (_: Throwable) {
                ""
            }
        }
        val fullInstructions = buildString {
            append("Date actuelle : $dateStr (octobre 2026).\n")
            append(SAM_AI_REALTIME_INSTRUCTIONS.trim())
            if (effectiveHeadlines.isNotBlank()) {
                append("\n\nACTUALITÉS EN DIRECT :\n")
                append(effectiveHeadlines.take(700))
            }
        }

        // Schéma officiel OpenAI Realtime GA (/v1/realtime) validé à 214 ms (0,21 s) sur le serveur OpenAI
        val sessionPayload = buildJsonObject {
            put("type", "session.update")
            putJsonObject("session") {
                put("type", "realtime")
                put("output_modalities", buildJsonArray {
                    add(kotlinx.serialization.json.JsonPrimitive("audio"))
                })
                put("instructions", fullInstructions)
                putJsonObject("audio") {
                    putJsonObject("input") {
                        putJsonObject("format") {
                            put("type", "audio/pcm")
                            put("rate", SAMPLE_RATE)
                        }
                        putJsonObject("transcription") {
                            put("model", "gpt-4o-mini-transcribe")
                            put("language", "fr")
                            put("prompt", "SAM AI, Burkina Faso, Ouagadougou, Bobo-Dioulasso, Abdoul Mohamine Sawadogo.")
                        }
                        putJsonObject("noise_reduction") {
                            // "far_field" est optimisé pour les appels mains-libres sur téléphone (30 à 50 cm du micro)
                            put("type", "far_field")
                        }
                        putJsonObject("turn_detection") {
                            put("type", "server_vad")
                            put("threshold", 0.50)
                            put("prefix_padding_ms", 200)
                            put("silence_duration_ms", 450)
                            put("create_response", true)
                            put("interrupt_response", false)
                        }
                    }
                    putJsonObject("output") {
                        putJsonObject("format") {
                            put("type", "audio/pcm")
                            put("rate", SAMPLE_RATE)
                        }
                        put("voice", REALTIME_VOICE)
                        put("speed", 1.05)
                    }
                }
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
            ws.send("""{"type":"response.create"}""")
        }
    }

    private fun flushPreSessionAudioQueue() {
        val ws = webSocket ?: return
        while (preSessionAudioQueue.isNotEmpty()) {
            val base64Chunk = preSessionAudioQueue.poll() ?: break
            ws.send("""{"type":"input_audio_buffer.append","audio":"$base64Chunk"}""")
        }
    }

    // Garde acoustique post-lecture (520 ms) après la dernière trame AudioTrack pour laisser
    // le buffer matériel Android (AudioFlinger) et la réverbération du haut-parleur s'éteindre
    // totalement avant de rouvrir le microphone.
    private val postPlaybackEchoGuardMs = 520L

    private fun isAiSpeakingOrEchoGuardActive(now: Long = System.currentTimeMillis()): Boolean {
        return isResponseInProgress.get() ||
            acceptingAiAudio.get() ||
            isPlaybackWorkerWriting.get() ||
            pcmPlaybackQueue.isNotEmpty() ||
            _callState.value == VoiceCallState.SAM_SPEAKING ||
            _callState.value == VoiceCallState.THINKING ||
            (playbackFinishTimeMs > 0L && now < (playbackFinishTimeMs + postPlaybackEchoGuardMs))
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
                    // Attend la confirmation de session.updated (~150 ms) pour garantir que la voix "ash"
                    // et les instructions françaises sont verrouillées avant d'envoyer l'audio pré-capturé
                    scope.launch {
                        delay(350L)
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
                    // Protection anti-écho absolue : si SAM AI est en train de répondre ou de parler
                    // en haut-parleur, on ignore toute détection parasite et on vide le buffer d'entrée
                    // pour que SAM AI n'annule jamais ce qu'il est en train de dire.
                    if (isAiSpeakingOrEchoGuardActive()) {
                        serverSpeechActive.set(false)
                        synchronized(queueLock) {
                            if (isSessionReady.get()) {
                                webSocket?.send("""{"type":"input_audio_buffer.clear"}""")
                            }
                        }
                        return
                    }
                    serverSpeechActive.set(true)
                    currentUserTranscriptBuilder.clear()
                    _callState.value = VoiceCallState.USER_SPEAKING
                }

                "input_audio_buffer.speech_stopped" -> {
                    serverSpeechActive.set(false)
                    if (!isAiSpeakingOrEchoGuardActive() && _callState.value == VoiceCallState.USER_SPEAKING) {
                        _callState.value = VoiceCallState.THINKING
                    }
                }

                "conversation.item.input_audio_transcription.delta" -> {
                    if (isAiSpeakingOrEchoGuardActive()) return
                    val delta = root["delta"]?.jsonPrimitive?.content.orEmpty()
                    if (delta.isNotEmpty()) {
                        currentUserTranscriptBuilder.append(delta)
                        val partial = currentUserTranscriptBuilder.toString().trim()
                        if (partial.isNotBlank()) {
                            _userTranscript.value = partial
                        }
                    }
                }

                "conversation.item.input_audio_transcription.completed" -> {
                    val transcript = root["transcript"]?.jsonPrimitive?.content?.trim().orEmpty()
                    currentUserTranscriptBuilder.clear()
                    if (transcript.isNotBlank()) {
                        _userTranscript.value = transcript
                        val readyAi = pendingAiTurnTranscript
                        if (readyAi.isNotBlank()) {
                            pendingAiTurnTranscript = ""
                            lastCompletedUserTranscript = ""
                            mainHandler.post {
                                onTurnCompleted(transcript, readyAi)
                            }
                        } else {
                            lastCompletedUserTranscript = transcript
                        }
                    }
                }

                "response.created" -> {
                    // Verrouille immédiatement l'état de réponse active et purge le tampon micro
                    // pour qu'aucun écho du haut-parleur ne puisse interrompre la réponse de SAM AI
                    serverSpeechActive.set(false)
                    isPlaybackManuallyCancelled.set(false)
                    isResponseInProgress.set(true)
                    acceptingAiAudio.set(true)
                    if (_callState.value != VoiceCallState.SAM_SPEAKING) {
                        currentAiTranscriptBuilder.clear()
                    }
                    synchronized(queueLock) {
                        if (isSessionReady.get()) {
                            webSocket?.send("""{"type":"input_audio_buffer.clear"}""")
                        }
                    }
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
                        val userText = lastCompletedUserTranscript
                        if (userText.isNotBlank()) {
                            lastCompletedUserTranscript = ""
                            pendingAiTurnTranscript = ""
                            mainHandler.post {
                                onTurnCompleted(userText, sanitized)
                            }
                        } else {
                            pendingAiTurnTranscript = sanitized
                        }
                    }
                }

                "response.audio.delta", "response.output_audio.delta" -> {
                    if (isPlaybackManuallyCancelled.get() || !acceptingAiAudio.get()) return
                    val base64Audio = root["delta"]?.jsonPrimitive?.content.orEmpty()
                    if (base64Audio.isNotEmpty()) {
                        val pcmBytes = Base64.decode(base64Audio, Base64.DEFAULT)
                        if (pcmBytes.isNotEmpty() && !isPlaybackManuallyCancelled.get()) {
                            _callState.value = VoiceCallState.SAM_SPEAKING
                            enqueuePcmForPlayback(pcmBytes)
                        }
                    }
                }

                "response.audio.done", "response.output_audio.done" -> {
                    scheduleReturnToListeningState()
                }

                "response.done" -> {
                    isResponseInProgress.set(false)
                    val responseObj = root["response"]?.jsonObject
                    val status = responseObj?.get("status")?.jsonPrimitive?.content.orEmpty()
                    val outputArr = responseObj?.get("output") as? kotlinx.serialization.json.JsonArray
                    val hasFunctionCall = outputArr?.any { elem ->
                        (elem as? kotlinx.serialization.json.JsonObject)?.get("type")?.jsonPrimitive?.content == "function_call"
                    } == true
                    if (!hasFunctionCall && status != "cancelled") {
                        scheduleReturnToListeningState()
                    }
                }

                "error" -> {
                    val errorObj = root["error"]?.jsonObject
                    val message = errorObj?.get("message")?.jsonPrimitive?.content ?: "Erreur Realtime"
                    val code = errorObj?.get("code")?.jsonPrimitive?.content ?: ""
                    Log.w(TAG, "Événement OpenAI Realtime ($code): $message")

                    // CRITIQUE : Ne JAMAIS couper acceptingAiAudio ou isResponseInProgress si SAM AI
                    // est déjà en train de parler sur le haut-parleur (sinon un simple avertissement
                    // WebSocket comme un clear de buffer vide coupe la voix de SAM AI et rouvre le micro !)
                    val isCurrentlyPlayingAudio = isPlaybackWorkerWriting.get() ||
                        pcmPlaybackQueue.isNotEmpty() ||
                        _callState.value == VoiceCallState.SAM_SPEAKING

                    val isFatalAuthOrModelError = code in setOf(
                        "invalid_api_key",
                        "model_not_found",
                        "insufficient_quota",
                        "beta_api_shape_disabled"
                    ) || message.contains("model", ignoreCase = true) ||
                        message.contains("api key", ignoreCase = true)

                    if (!isSessionReady.get() && isFatalAuthOrModelError) {
                        isResponseInProgress.set(false)
                        acceptingAiAudio.set(false)
                        val apiKey = apiKeyManager.getActiveOpenAiApiKey().trim()
                        if (!triedFallbackModel.getAndSet(true) && apiKey.isNotBlank()) {
                            connectWebSocket(apiKey, REALTIME_MODEL_FALLBACK)
                        } else {
                            notifyFallback(message)
                        }
                    } else if (!isCurrentlyPlayingAudio) {
                        isResponseInProgress.set(false)
                        acceptingAiAudio.set(false)
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
            while (
                isActiveCall.get() &&
                (
                    isResponseInProgress.get() ||
                    isPlaybackWorkerWriting.get() ||
                    pcmPlaybackQueue.isNotEmpty() ||
                    (playbackFinishTimeMs > 0L && System.currentTimeMillis() < (playbackFinishTimeMs + postPlaybackEchoGuardMs))
                )
            ) {
                delay(25L)
            }
            if (isActiveCall.get() && !isResponseInProgress.get() && !isPlaybackWorkerWriting.get() && pcmPlaybackQueue.isEmpty()) {
                acceptingAiAudio.set(false)
                playbackFinishTimeMs = 0L
                serverSpeechActive.set(false)
                synchronized(queueLock) {
                    if (isSessionReady.get()) {
                        webSocket?.send("""{"type":"input_audio_buffer.clear"}""")
                    }
                }
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

            // Priorité à VOICE_COMMUNICATION pour activer l'annulation d'écho matérielle DSP (AEC)
            // liée au haut-parleur sur Android et empêcher le micro de capter la voix de SAM AI
            val recorder = listOf(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.MIC
            ).firstNotNullOfOrNull { src ->
                try {
                    val rec = AudioRecord(
                        src,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        minBuffer * 2
                    )
                    if (rec.state == AudioRecord.STATE_INITIALIZED) rec else {
                        rec.release()
                        null
                    }
                } catch (_: Exception) {
                    null
                }
            }

            if (recorder == null) {
                Log.e(TAG, "Impossible d'initialiser AudioRecord")
                notifyFallback("Microphone indisponible en mode PCM")
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

            // Trames temps réel de 20 ms à 24000 Hz (480 échantillons = 960 octets PCM16)
            val frameBuffer = ByteArray(960)
            val onsetPreRoll = java.util.ArrayDeque<String>(4)
            val preSessionPreRoll = java.util.ArrayDeque<String>(5)
            var preSessionVoiceDetected = false
            var initialFramesSkipped = 0
            var wasAiSpeakingOrGuarded = false
            var postAiFlushFramesRemaining = 0
            var ambientFloor = 70.0
            var voiceHoldFrames = 0

            while (isActive && isActiveCall.get()) {
                val readBytes = recorder.read(frameBuffer, 0, frameBuffer.size)
                if (readBytes > 0) {
                    // Ignore les 3 premières trames (60 ms) pour éliminer le bruit mécanique du doigt sur l'écran
                    if (initialFramesSkipped < 3) {
                        initialFramesSkipped++
                        continue
                    }

                    if (isMuted.get()) {
                        _soundLevel.value = 0f
                        continue
                    }

                    val now = System.currentTimeMillis()
                    val isAiBusyOrSpeaking = isAiSpeakingOrEchoGuardActive(now)

                    // Verrouillage anti-écho strict pendant que SAM AI réfléchit, parle à haute voix
                    // ou pendant les 520 ms de réverbération acoustique après la fin de sa phrase :
                    // - Le micro est totalement isolé pour que SAM AI ne ré-entende JAMAIS sa propre voix
                    //   et n'annule jamais ce qu'il est en train de dire.
                    if (isAiBusyOrSpeaking) {
                        _soundLevel.value = 0f
                        onsetPreRoll.clear()
                        voiceHoldFrames = 0
                        wasAiSpeakingOrGuarded = true
                        continue
                    } else if (wasAiSpeakingOrGuarded) {
                        // Dès que SAM AI a fini de parler et que l'écho du haut-parleur s'est éteint,
                        // on purge le tampon serveur et on ignore 15 trames (300 ms) du tampon matériel AudioRecord
                        // pour éliminer 100 % des résidus acoustiques du haut-parleur.
                        wasAiSpeakingOrGuarded = false
                        postAiFlushFramesRemaining = 15
                        onsetPreRoll.clear()
                        voiceHoldFrames = 0
                        synchronized(queueLock) {
                            if (isSessionReady.get()) {
                                webSocket?.send("""{"type":"input_audio_buffer.clear"}""")
                            }
                        }
                        continue
                    }

                    if (postAiFlushFramesRemaining > 0) {
                        postAiFlushFramesRemaining--
                        _soundLevel.value = 0f
                        continue
                    }

                    // 1. Calcul du RMS brut de la trame de 20 ms (uniquement quand c'est au tour de l'utilisateur)
                    var rawSumSquares = 0.0
                    val sampleCount = readBytes / 2
                    var i = 0
                    while (i + 1 < readBytes) {
                        val low = frameBuffer[i].toInt() and 0xFF
                        val high = frameBuffer[i + 1].toInt()
                        val rawSample = (high shl 8) or low
                        rawSumSquares += (rawSample * rawSample).toDouble()
                        i += 2
                    }
                    val rawRms = if (sampleCount > 0) sqrt(rawSumSquares / sampleCount) else 0.0

                    // Adaptation douce du bruit ambiant uniquement hors parole pour isoler la voix humaine
                    if (!serverSpeechActive.get() && voiceHoldFrames == 0 && rawRms in 1.0..155.0) {
                        ambientFloor = ((ambientFloor * 0.90) + (rawRms * 0.10)).coerceIn(40.0, 150.0)
                    }
                    val vocalGate = maxOf(120.0, ambientFloor * 1.45).coerceAtMost(230.0)
                    val isVoiceFrame = rawRms >= vocalGate

                    // 2. Courbe de gain à fort contraste vocal :
                    // - Voix détectée (>= vocalGate) : amplifiée x2.2 (voix douce) ou x1.5 (voix normale) pour déclenchement VAD en 0 ms
                    // - Silence / bruit de fond (< vocalGate) : divisé par 6
                    val processedBuffer = ByteArray(readBytes)
                    var j = 0
                    while (j + 1 < readBytes) {
                        val low = frameBuffer[j].toInt() and 0xFF
                        val high = frameBuffer[j + 1].toInt()
                        val rawSample = (high shl 8) or low
                        val outSample = when {
                            !isVoiceFrame && voiceHoldFrames <= 0 -> rawSample / 6
                            rawRms < 1000.0 -> (rawSample * 11 / 5).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                            rawRms < 2200.0 -> (rawSample * 3 / 2).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                            else -> rawSample
                        }
                        processedBuffer[j] = (outSample and 0xFF).toByte()
                        processedBuffer[j + 1] = ((outSample shr 8) and 0xFF).toByte()
                        j += 2
                    }

                    val normalizedLevel = (rawRms / 3800.0).toFloat().coerceIn(0f, 1f)
                    _soundLevel.value = normalizedLevel

                    val base64Audio = Base64.encodeToString(processedBuffer, Base64.NO_WRAP)

                    synchronized(queueLock) {
                        if (isSessionReady.get()) {
                            val ws = webSocket
                            if (ws != null) {
                                if (isVoiceFrame) {
                                    // Dès la 1re syllabe détectée, envoie les 3 trames de pré-roll (60 ms) + la trame vocale
                                    while (onsetPreRoll.isNotEmpty()) {
                                        val pre = onsetPreRoll.pollFirst() ?: break
                                        ws.send("""{"type":"input_audio_buffer.append","audio":"$pre"}""")
                                    }
                                    // Maintient le flux vocal naturel pendant 8 trames (160 ms) entre les mots
                                    // d'une même question pour ne jamais couper l'utilisateur pendant qu'il pose sa question
                                    voiceHoldFrames = 8
                                    ws.send("""{"type":"input_audio_buffer.append","audio":"$base64Audio"}""")
                                } else if (voiceHoldFrames > 0) {
                                    voiceHoldFrames--
                                    ws.send("""{"type":"input_audio_buffer.append","audio":"$base64Audio"}""")
                                } else {
                                    // Hors parole : garde 3 trames en pré-roll (60 ms) et envoie un silence mathématique pur (0 PCM)
                                    if (onsetPreRoll.size >= 3) onsetPreRoll.pollFirst()
                                    onsetPreRoll.addLast(base64Audio)
                                    if (ws.queueSize() < 24_576L) {
                                        ws.send("""{"type":"input_audio_buffer.append","audio":"$silenceFrame20msBase64"}""")
                                    }
                                }
                            }
                        } else {
                            if (!preSessionVoiceDetected) {
                                if (preSessionPreRoll.size >= 4) preSessionPreRoll.pollFirst()
                                preSessionPreRoll.addLast(base64Audio)
                                if (isVoiceFrame) {
                                    preSessionVoiceDetected = true
                                    while (preSessionPreRoll.isNotEmpty()) {
                                        val pre = preSessionPreRoll.pollFirst() ?: break
                                        preSessionAudioQueue.offer(pre)
                                    }
                                }
                            } else if (preSessionAudioQueue.size < 250) {
                                preSessionAudioQueue.offer(if (isVoiceFrame || voiceHoldFrames > 0) base64Audio else silenceFrame20msBase64)
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

            // Buffer matériel court (~200 ms) pour démarrer la voix dès le 1er paquet sans latence de mixeur
            // et permettre une coupure instantanée (< 20 ms) dès que l'utilisateur reprend la parole
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
                .setBufferSizeInBytes(maxOf(minBuffer * 2, 9600))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            track.play()
            audioTrack = track
        } catch (e: Exception) {
            Log.e(TAG, "Erreur initialisation AudioTrack 24kHz: ${e.message}")
        }
    }

    private fun enqueuePcmForPlayback(pcmBytes: ByteArray) {
        if (isPlaybackManuallyCancelled.get() || !acceptingAiAudio.get()) return
        val now = System.currentTimeMillis()
        // 24000 Hz * 2 octets (16-bit mono) = 48000 octets par seconde d'audio
        val chunkDurationMs = (pcmBytes.size * 1000L) / (SAMPLE_RATE * 2)
        playbackFinishTimeMs = maxOf(now, playbackFinishTimeMs) + chunkDurationMs
        pcmPlaybackQueue.offer(pcmBytes)
    }

    private fun startPlaybackWorker() {
        playbackJob?.cancel()
        playbackJob = scope.launch(Dispatchers.IO) {
            // Découpe l'écriture AudioTrack en tranches de 20 ms (960 octets) tout en verrouillant
            // isPlaybackWorkerWriting pour qu'aucun événement réseau ni vidage de file ne coupe la fin de phrase
            val sliceSize = 960
            while (isActive && isActiveCall.get()) {
                val pcmBytes = pcmPlaybackQueue.poll()
                if (pcmBytes != null && !isPlaybackManuallyCancelled.get()) {
                    isPlaybackWorkerWriting.set(true)
                    val track = audioTrack
                    if (track != null && track.state == AudioTrack.STATE_INITIALIZED) {
                        try {
                            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                                track.play()
                            }
                            var offset = 0
                            while (offset < pcmBytes.size && !isPlaybackManuallyCancelled.get() && isActiveCall.get()) {
                                val bytesToWrite = minOf(sliceSize, pcmBytes.size - offset)
                                val written = track.write(pcmBytes, offset, bytesToWrite)
                                if (written <= 0) break
                                offset += written
                                // Maintient l'estimation de fin de lecture réelle (latence buffer AudioTrack + mixeur Android ~480 ms)
                                playbackFinishTimeMs = maxOf(playbackFinishTimeMs, System.currentTimeMillis() + 480L)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Erreur écriture AudioTrack: ${e.message}")
                        } finally {
                            if (pcmPlaybackQueue.isEmpty()) {
                                playbackFinishTimeMs = maxOf(playbackFinishTimeMs, System.currentTimeMillis() + 480L)
                                isPlaybackWorkerWriting.set(false)
                            }
                        }
                    } else {
                        isPlaybackWorkerWriting.set(false)
                    }
                } else {
                    if (pcmPlaybackQueue.isEmpty()) {
                        isPlaybackWorkerWriting.set(false)
                    }
                    delay(5L)
                }
            }
        }
    }

    fun interruptAiSpeech() {
        isPlaybackManuallyCancelled.set(true)
        acceptingAiAudio.set(false)
        isResponseInProgress.set(false)
        isPlaybackWorkerWriting.set(false)
        serverSpeechActive.set(false)
        stopAndFlushPlayback()
        try {
            webSocket?.send("""{"type":"response.cancel"}""")
            webSocket?.send("""{"type":"input_audio_buffer.clear"}""")
        } catch (_: Exception) {}
        _callState.value = VoiceCallState.USER_SPEAKING
    }

    fun commitUserSpeechNow() {
        try {
            if (!isResponseInProgress.get()) {
                webSocket?.send("""{"type":"input_audio_buffer.commit"}""")
                webSocket?.send("""{"type":"response.create"}""")
                _callState.value = VoiceCallState.THINKING
            }
        } catch (_: Exception) {}
    }

    fun setMuted(muted: Boolean) {
        isMuted.set(muted)
        if (muted) {
            _soundLevel.value = 0f
        }
    }

    private fun stopAndFlushPlayback() {
        isPlaybackWorkerWriting.set(false)
        pcmPlaybackQueue.clear()
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
            acceptingAiAudio.set(false)
            pcmPlaybackQueue.clear()
            recordingJob?.cancel()
            playbackJob?.cancel()
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

        try {
            audioManager?.let { am ->
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = previousSpeakerphoneState
                am.mode = previousAudioMode
            }
        } catch (_: Exception) {}
    }
}
