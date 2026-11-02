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
        // Modèles officiels OpenAI Realtime GA actifs (priorité à gpt-realtime haute précision + voix humaine masculine "ash")
        private const val REALTIME_MODEL_PRIMARY = "gpt-realtime"
        private const val REALTIME_MODEL_FALLBACK = "gpt-realtime-mini"
        private const val REALTIME_VOICE = "ash"

        private const val SAM_AI_REALTIME_INSTRUCTIONS = """
Tu es SAM AI, un homme chaleureux, naturel, cultivé et ultra-précis. L'appel téléphonique avec l'utilisateur a DÉJÀ commencé et les salutations ont DÉJÀ été faites.
Nous sommes en octobre 2026 et tu disposes des actualités en direct ci-dessous.
RÈGLES ABSOLUES ET STRICTES (ZÉRO ERREUR) :
1. INTERDICTION TOTALE DE DIRE "BONJOUR", "SALUT" OU "BONSOIR" :
   - Les salutations d'ouverture sont déjà terminées : ne commence JAMAIS tes réponses par « Bonjour », « Salut », « Bonsoir » ou « Ravie/Ravi de vous entendre ».
   - Réponds directement dès le premier mot au contenu exact de la demande de l'utilisateur, sans aucune formule de salutation.
   - Ne dis le mot « Bonjour » que si l'utilisateur te donne l'ordre explicite « Dis bonjour » ou s'il a prononcé uniquement le seul mot « Bonjour » sans aucune autre question.
2. OBÉISSANCE EXACTE ET LITTÉRALE À CE QUE L'UTILISATEUR DEMANDE DE DIRE OU DE FAIRE (ZÉRO ERREUR) :
   - Écoute très attentivement la prononciation exacte et chaque mot prononcé par l'utilisateur.
   - Si l'utilisateur te demande de DIRE, RÉPÉTER, PRONONCER ou LIRE quelque chose de précis (ex : « Dis ... », « Répète ... », « Prononce exactement ... »), prononce EXACTEMENT et UNIQUEMENT les mots demandés par l'utilisateur, mot pour mot, sans ajouter « Bonjour », sans ajouter « D'accord » ou « Bien sûr », et sans aucune erreur.
   - Si l'utilisateur te demande de FAIRE quelque chose (répondre à une question, calculer, traduire, expliquer, citer), fais exactement ce qu'il a demandé avec une exactitude absolue (0% d'erreur) et sans jamais dévier du sujet.
3. VOIX D'HOMME HUMAINE NATURELLE ET 100% FRANÇAIS :
   - Tu es un homme : utilise toujours les accords masculins ("je suis attentif", "je suis prêt").
   - Parle et réponds en français clair et naturel (sauf si l'utilisateur te demande explicitement de dire ou traduire une phrase dans une autre langue).
4. DISTINCTION STRICTE DE CHAQUE QUESTION :
   - Ne reviens JAMAIS sur une ancienne question déjà répondue.
   - Si l'utilisateur pose une question au début puis une autre à la fin, ou se corrige en cours de phrase, réponds avec précision à sa demande finale.
5. ACTUALITÉS & IDENTITÉ :
   - Utilise directement les actualités ci-dessous sans jamais dire que tes connaissances s'arrêtent en 2023, 2024 ou 2025.
   - Tu t'appelles uniquement SAM AI. Si l'utilisateur demande qui t'a créé, réponds que tu as été créé par Abdoul Mohamine Sawadogo, un jeune développeur burkinabè.
"""

        private val HALLUCINATED_TRANSCRIPT_PATTERNS = listOf(
            "burkina faso, ouagadougou, bobo-dioulasso",
            "ouagadougou, bobo-dioulasso, abdoul mohamine",
            "sam ai, burkina faso",
            "bobo-dioulasso, abdoul mohamine sawadogo",
            "sous-titres réalisés",
            "sous-titrage",
            "amara.org",
            "merci d'avoir regardé",
            "merci d'avoir écouté",
            "transcris mot pour mot",
            "transcris exactement"
        )
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

    // Liste des identifiants d'items de conversation (user + assistant) créés sur le serveur Realtime
    // Purgés après chaque réponse pour que SAM AI ne reparle JAMAIS d'une ancienne question
    private val activeConversationItemIds = java.util.concurrent.ConcurrentLinkedQueue<String>()

    // Compteur de trames vocales humaines réelles détectées dans le tour courant
    @Volatile
    private var currentTurnVoiceFrames: Int = 0

    // Timestamp de disponibilité effective de la session pour ignorer le bruit d'ouverture
    @Volatile
    private var sessionReadyAtMs: Long = 0L

    // File d'attente des trames audio capturées pendant la connexion WebSocket
    private val preSessionAudioQueue = java.util.concurrent.ConcurrentLinkedQueue<String>()

    // File de lecture PCM non-bloquante pour ne JAMAIS bloquer le thread WebSocket OkHttp
    private val pcmPlaybackQueue = java.util.concurrent.ConcurrentLinkedQueue<ByteArray>()

    // Trame de silence pur de 20 ms (960 octets à 0) encodée en Base64 pour fermer proprement un tour VAD
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
        activeConversationItemIds.clear()
        currentTurnVoiceFrames = 0
        sessionReadyAtMs = 0L
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

        // Schéma officiel OpenAI Realtime GA (/v1/realtime) avec gpt-4o-transcribe haute fidélité
        // et réduction de bruit near_field pour capter chaque syllabe et consonne prononcée sans déformation.
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
                            put("model", "gpt-4o-transcribe")
                            put("language", "fr")
                        }
                        putJsonObject("noise_reduction") {
                            // "near_field" préserve toutes les consonnes douces (s, f, p, t, k, c) et la prononciation exacte de l'utilisateur
                            put("type", "near_field")
                        }
                        putJsonObject("turn_detection") {
                            put("type", "server_vad")
                            put("threshold", 0.50)
                            put("prefix_padding_ms", 300)
                            // 820 ms laisse l'utilisateur articuler chaque mot ou donner une consigne complète sans jamais être coupé
                            put("silence_duration_ms", 820)
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
                        put("speed", 1.03)
                    }
                }
            }
        }
        ws.send(sessionPayload.toString())
    }

    private fun isHallucinatedOrNoiseTranscript(raw: String): Boolean {
        val cleaned = raw.trim()
        if (cleaned.length < 2) return true
        val lower = cleaned.lowercase(java.util.Locale.ROOT)
        if (HALLUCINATED_TRANSCRIPT_PATTERNS.any { lower.contains(it) }) return true
        val letterCount = cleaned.count { it.isLetter() }
        if (letterCount < 2) return true
        return false
    }

    private fun purgePreviousConversationItems() {
        val ws = webSocket ?: return
        while (activeConversationItemIds.isNotEmpty()) {
            val itemId = activeConversationItemIds.poll() ?: break
            if (itemId.isNotBlank()) {
                ws.send("""{"type":"conversation.item.delete","item_id":"$itemId"}""")
            }
        }
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
        // Ne jamais envoyer les bruits de clic d'ouverture captés avant l'initialisation de la session
        preSessionAudioQueue.clear()
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
                            preSessionAudioQueue.clear()
                            sessionReadyAtMs = System.currentTimeMillis()
                            isSessionReady.set(true)
                            _callState.value = VoiceCallState.USER_SPEAKING
                        }
                    }
                }

                "session.created" -> {
                    // Attend la confirmation de session.updated (~150 ms) pour garantir que la voix "ash"
                    // et les instructions françaises sont verrouillées avant d'activer l'écoute
                    scope.launch {
                        delay(350L)
                        synchronized(queueLock) {
                            if (isActiveCall.get() && !isSessionReady.get()) {
                                preSessionAudioQueue.clear()
                                sessionReadyAtMs = System.currentTimeMillis()
                                isSessionReady.set(true)
                                _callState.value = VoiceCallState.USER_SPEAKING
                            }
                        }
                    }
                }

                "conversation.item.created", "conversation.item.added" -> {
                    val itemObj = root["item"]?.jsonObject
                    val itemId = itemObj?.get("id")?.jsonPrimitive?.content.orEmpty()
                    if (itemId.isNotBlank() && !activeConversationItemIds.contains(itemId)) {
                        activeConversationItemIds.offer(itemId)
                    }
                }

                "response.output_item.added" -> {
                    val itemObj = root["item"]?.jsonObject
                    val itemId = itemObj?.get("id")?.jsonPrimitive?.content.orEmpty()
                    if (itemId.isNotBlank() && !activeConversationItemIds.contains(itemId)) {
                        activeConversationItemIds.offer(itemId)
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
                    currentAiTranscriptBuilder.clear()
                    lastCompletedUserTranscript = ""
                    pendingAiTurnTranscript = ""
                    _userTranscript.value = ""
                    _aiTranscript.value = ""
                    _callState.value = VoiceCallState.USER_SPEAKING
                }

                "input_audio_buffer.speech_stopped" -> {
                    serverSpeechActive.set(false)
                    // Si un simple bruit parasite court (< 4 trames vocales = 80 ms) a déclenché server_vad,
                    // on vide immédiatement le buffer pour éviter toute fausse réponse à l'ouverture
                    if (currentTurnVoiceFrames < 4) {
                        synchronized(queueLock) {
                            if (isSessionReady.get()) {
                                webSocket?.send("""{"type":"input_audio_buffer.clear"}""")
                            }
                        }
                        return
                    }
                    if (!isAiSpeakingOrEchoGuardActive() && _callState.value == VoiceCallState.USER_SPEAKING) {
                        _callState.value = VoiceCallState.THINKING
                    }
                }

                "conversation.item.input_audio_transcription.delta" -> {
                    if (isAiSpeakingOrEchoGuardActive() && _callState.value == VoiceCallState.SAM_SPEAKING) return
                    val delta = root["delta"]?.jsonPrimitive?.content.orEmpty()
                    if (delta.isNotEmpty()) {
                        currentUserTranscriptBuilder.append(delta)
                        val partial = currentUserTranscriptBuilder.toString().trim()
                        if (partial.isNotBlank() && !isHallucinatedOrNoiseTranscript(partial)) {
                            _userTranscript.value = partial
                        }
                    }
                }

                "conversation.item.input_audio_transcription.completed" -> {
                    val transcript = root["transcript"]?.jsonPrimitive?.content?.trim().orEmpty()
                    currentUserTranscriptBuilder.clear()
                    if (transcript.isBlank() || isHallucinatedOrNoiseTranscript(transcript)) {
                        Log.d(TAG, "Transcription bruyante ou hallucinée ignorée : \"$transcript\"")
                        _userTranscript.value = ""
                        _aiTranscript.value = ""
                        lastCompletedUserTranscript = ""
                        pendingAiTurnTranscript = ""
                        isResponseInProgress.set(false)
                        acceptingAiAudio.set(false)
                        stopAndFlushPlayback()
                        synchronized(queueLock) {
                            if (isSessionReady.get()) {
                                webSocket?.send("""{"type":"response.cancel"}""")
                                webSocket?.send("""{"type":"input_audio_buffer.clear"}""")
                                purgePreviousConversationItems()
                            }
                        }
                        _callState.value = VoiceCallState.USER_SPEAKING
                        return
                    }
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

                "response.created" -> {
                    // Si aucune vraie parole humaine n'a été captée (ex: bruit d'ouverture d'appel),
                    // on annule immédiatement cette réponse parasite avant qu'elle ne produise du son
                    if (currentTurnVoiceFrames < 4) {
                        currentTurnVoiceFrames = 0
                        isResponseInProgress.set(false)
                        acceptingAiAudio.set(false)
                        stopAndFlushPlayback()
                        _userTranscript.value = ""
                        _aiTranscript.value = ""
                        synchronized(queueLock) {
                            if (isSessionReady.get()) {
                                webSocket?.send("""{"type":"response.cancel"}""")
                                webSocket?.send("""{"type":"input_audio_buffer.clear"}""")
                                purgePreviousConversationItems()
                            }
                        }
                        _callState.value = VoiceCallState.USER_SPEAKING
                        return
                    }

                    // Verrouille immédiatement l'état de réponse active et purge le tampon micro
                    // pour qu'aucun écho du haut-parleur ne puisse interrompre la réponse de SAM AI
                    currentTurnVoiceFrames = 0
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
                    if (isPlaybackManuallyCancelled.get() || !acceptingAiAudio.get()) return
                    val delta = root["delta"]?.jsonPrimitive?.content.orEmpty()
                    if (delta.isNotEmpty()) {
                        currentAiTranscriptBuilder.append(delta)
                        _aiTranscript.value = sanitizeSamAiPersona(currentAiTranscriptBuilder.toString())
                    }
                }

                "response.audio_transcript.done", "response.output_audio_transcript.done" -> {
                    if (isPlaybackManuallyCancelled.get()) return
                    val fullTranscript = root["transcript"]?.jsonPrimitive?.content?.trim()
                        ?: currentAiTranscriptBuilder.toString().trim()
                    if (fullTranscript.isNotBlank()) {
                        val sanitized = sanitizeSamAiPersona(fullTranscript)
                        _aiTranscript.value = sanitized
                        val userText = lastCompletedUserTranscript
                        if (userText.isNotBlank() && !isHallucinatedOrNoiseTranscript(userText)) {
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
                    outputArr?.forEach { elem ->
                        val itemId = (elem as? kotlinx.serialization.json.JsonObject)?.get("id")?.jsonPrimitive?.content.orEmpty()
                        if (itemId.isNotBlank() && !activeConversationItemIds.contains(itemId)) {
                            activeConversationItemIds.offer(itemId)
                        }
                    }
                    val hasFunctionCall = outputArr?.any { elem ->
                        (elem as? kotlinx.serialization.json.JsonObject)?.get("type")?.jsonPrimitive?.content == "function_call"
                    } == true
                    if (!hasFunctionCall) {
                        // Purge systématiquement les items du tour terminé (question + réponse) sur le serveur Realtime
                        // pour que chaque nouvelle question soit traitée de façon 100% distincte sans jamais reparler de l'ancienne !
                        synchronized(queueLock) {
                            if (isSessionReady.get()) {
                                purgePreviousConversationItems()
                            }
                        }
                        if (status != "cancelled") {
                            scheduleReturnToListeningState()
                        }
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
                                preSessionAudioQueue.clear()
                                sessionReadyAtMs = System.currentTimeMillis()
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
                currentTurnVoiceFrames = 0
                synchronized(queueLock) {
                    if (isSessionReady.get()) {
                        webSocket?.send("""{"type":"input_audio_buffer.clear"}""")
                        purgePreviousConversationItems()
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

            // Priorité à VOICE_RECOGNITION pour une courbe de réponse vocale linéaire haute fidélité
            // qui préserve 100 % des consonnes et de la prononciation exacte de l'utilisateur
            val recorder = listOf(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
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

            // Activation de l'annulation d'écho matérielle Android (AEC uniquement ; pas de NoiseSuppressor
            // matériel doublonné pour ne jamais étouffer les consonnes douces de l'utilisateur)
            try {
                if (AcousticEchoCanceler.isAvailable()) {
                    echoCanceler = AcousticEchoCanceler.create(recorder.audioSessionId)?.apply {
                        enabled = true
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Effet AEC matériel non disponible: ${e.message}")
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
            val onsetPreRoll = java.util.ArrayDeque<String>(12)
            var initialFramesSkipped = 0
            var wasAiSpeakingOrGuarded = false
            var postAiFlushFramesRemaining = 0
            var ambientFloor = 65.0
            var voiceHoldFrames = 0
            var consecutiveVoiceFrames = 0
            var silenceTailFramesRemaining = 0

            while (isActive && isActiveCall.get()) {
                val readBytes = recorder.read(frameBuffer, 0, frameBuffer.size)
                if (readBytes > 0) {
                    // Ignore les 16 premières trames (320 ms) à l'ouverture de l'appel pour éliminer
                    // totalement le bruit du doigt sur l'écran et le clic d'activation audio Android
                    if (initialFramesSkipped < 16) {
                        initialFramesSkipped++
                        _soundLevel.value = 0f
                        continue
                    }

                    if (isMuted.get() || !isSessionReady.get()) {
                        _soundLevel.value = 0f
                        onsetPreRoll.clear()
                        consecutiveVoiceFrames = 0
                        voiceHoldFrames = 0
                        silenceTailFramesRemaining = 0
                        continue
                    }

                    val now = System.currentTimeMillis()
                    // Ignore les 200 premières millisecondes après session.updated pour stabiliser le flux
                    if (sessionReadyAtMs > 0L && now - sessionReadyAtMs < 200L) {
                        _soundLevel.value = 0f
                        continue
                    }

                    val isAiBusyOrSpeaking = isAiSpeakingOrEchoGuardActive(now)

                    // Verrouillage anti-écho strict pendant que SAM AI réfléchit, parle à haute voix
                    // ou pendant les 520 ms de réverbération acoustique après la fin de sa phrase :
                    // - Le micro est totalement isolé pour que SAM AI ne ré-entende JAMAIS sa propre voix
                    //   et n'annule jamais ce qu'il est en train de dire.
                    if (isAiBusyOrSpeaking) {
                        if (!isPlaybackWorkerWriting.get() && pcmPlaybackQueue.isEmpty() && _callState.value != VoiceCallState.SAM_SPEAKING) {
                            _soundLevel.value = 0f
                        }
                        onsetPreRoll.clear()
                        consecutiveVoiceFrames = 0
                        voiceHoldFrames = 0
                        silenceTailFramesRemaining = 0
                        wasAiSpeakingOrGuarded = true
                        continue
                    } else if (wasAiSpeakingOrGuarded) {
                        // Dès que SAM AI a fini de parler et que l'écho du haut-parleur s'est éteint,
                        // on purge le tampon serveur et on ignore 14 trames (280 ms) du tampon matériel AudioRecord
                        // pour éliminer 100 % des résidus acoustiques du haut-parleur.
                        wasAiSpeakingOrGuarded = false
                        postAiFlushFramesRemaining = 14
                        _soundLevel.value = 0f
                        onsetPreRoll.clear()
                        consecutiveVoiceFrames = 0
                        voiceHoldFrames = 0
                        silenceTailFramesRemaining = 0
                        currentTurnVoiceFrames = 0
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
                    if (!serverSpeechActive.get() && voiceHoldFrames == 0 && rawRms in 1.0..135.0) {
                        ambientFloor = ((ambientFloor * 0.92) + (rawRms * 0.08)).coerceIn(40.0, 135.0)
                    }
                    val vocalGate = maxOf(95.0, ambientFloor * 1.38).coerceAtMost(190.0)
                    val isVoiceFrame = rawRms >= vocalGate

                    // 2. Pré-amplification vocale 100 % LINÉAIRE et CONTINUE (x1.35 avec limiteur doux) :
                    // Ne hache JAMAIS les consonnes douces (s, f, p, t, k, ch, d) ni les fins de mots
                    // pour que le modèle comprenne parfaitement la prononciation exacte de la personne.
                    val processedBuffer = ByteArray(readBytes)
                    var j = 0
                    while (j + 1 < readBytes) {
                        val low = frameBuffer[j].toInt() and 0xFF
                        val high = frameBuffer[j + 1].toInt()
                        val rawSample = (high shl 8) or low
                        val outSample = ((rawSample * 27) / 20).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        processedBuffer[j] = (outSample and 0xFF).toByte()
                        processedBuffer[j + 1] = ((outSample shr 8) and 0xFF).toByte()
                        j += 2
                    }

                    // Niveau audio temps réel de la voix utilisateur (réaction instantanée dès le seuil vocal)
                    val visualGate = maxOf(75.0, ambientFloor * 1.20)
                    val normalizedLevel = if (rawRms >= visualGate || isVoiceFrame || voiceHoldFrames > 0) {
                        val linear = ((rawRms - ambientFloor * 0.90).coerceAtLeast(0.0) / 1450.0).coerceIn(0.0, 1.0)
                        sqrt(linear).toFloat().coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    _soundLevel.value = normalizedLevel

                    val base64Audio = Base64.encodeToString(processedBuffer, Base64.NO_WRAP)

                    synchronized(queueLock) {
                        val ws = webSocket
                        if (ws != null && isSessionReady.get()) {
                            if (isVoiceFrame) {
                                consecutiveVoiceFrames++
                                currentTurnVoiceFrames++
                                // Dès la 1re trame vocale confirmée, envoie le pré-roll complet de 240 ms (12 trames)
                                // afin qu'aucune première syllabe ou consonne d'attaque ne soit jamais perdue
                                while (onsetPreRoll.isNotEmpty()) {
                                    val pre = onsetPreRoll.pollFirst() ?: break
                                    ws.send("""{"type":"input_audio_buffer.append","audio":"$pre"}""")
                                }
                                // Maintient le pont vocal linéaire pendant 25 trames (500 ms) entre les mots et syllabes
                                voiceHoldFrames = 25
                                // Prévoit 45 trames (900 ms) de queue de silence après la fin de la parole pour fermer server_vad
                                silenceTailFramesRemaining = 45
                                ws.send("""{"type":"input_audio_buffer.append","audio":"$base64Audio"}""")
                            } else {
                                consecutiveVoiceFrames = 0
                                if (voiceHoldFrames > 0) {
                                    voiceHoldFrames--
                                    ws.send("""{"type":"input_audio_buffer.append","audio":"$base64Audio"}""")
                                } else if (silenceTailFramesRemaining > 0 || serverSpeechActive.get()) {
                                    if (silenceTailFramesRemaining > 0) silenceTailFramesRemaining--
                                    if (ws.queueSize() < 24_576L) {
                                        ws.send("""{"type":"input_audio_buffer.append","audio":"$silenceFrame20msBase64"}""")
                                    }
                                } else {
                                    // Silence total avant prise de parole : conserve 12 trames (240 ms) de pré-roll linéaire
                                    if (onsetPreRoll.size >= 12) onsetPreRoll.pollFirst()
                                    onsetPreRoll.addLast(base64Audio)
                                }
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
                                val writeStartMs = System.currentTimeMillis()
                                val written = track.write(pcmBytes, offset, bytesToWrite)
                                if (written <= 0) break

                                // Analyse RMS réelle de la tranche audio PCM16 de SAM AI (20 ms)
                                var aiSumSquares = 0.0
                                val aiSamples = written / 2
                                var k = offset
                                val endIdx = offset + written
                                while (k + 1 < endIdx) {
                                    val low = pcmBytes[k].toInt() and 0xFF
                                    val high = pcmBytes[k + 1].toInt()
                                    val sample = (high shl 8) or low
                                    aiSumSquares += (sample * sample).toDouble()
                                    k += 2
                                }
                                val aiRms = if (aiSamples > 0) sqrt(aiSumSquares / aiSamples) else 0.0
                                val aiNormalizedLevel = if (aiRms >= 45.0) {
                                    val linear = ((aiRms - 45.0) / 1650.0).coerceIn(0.0, 1.0)
                                    sqrt(linear).toFloat().coerceIn(0f, 1f)
                                } else {
                                    0f
                                }
                                _soundLevel.value = aiNormalizedLevel

                                offset += written
                                // Maintient l'estimation de fin de lecture réelle (latence buffer AudioTrack + mixeur Android ~320 ms)
                                playbackFinishTimeMs = maxOf(playbackFinishTimeMs, System.currentTimeMillis() + 320L)

                                // Cadence l'analyse RMS à la vitesse réelle de lecture audio (20 ms par tranche de 960 octets)
                                // pour que la rotation et la luminosité de Saturne suivent chaque syllabe en temps réel
                                val writeElapsed = System.currentTimeMillis() - writeStartMs
                                if (writeElapsed < 16L) {
                                    delay((16L - writeElapsed).coerceIn(4L, 16L))
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Erreur écriture AudioTrack: ${e.message}")
                        } finally {
                            if (pcmPlaybackQueue.isEmpty()) {
                                playbackFinishTimeMs = maxOf(playbackFinishTimeMs, System.currentTimeMillis() + 260L)
                                isPlaybackWorkerWriting.set(false)
                            }
                        }
                    } else {
                        isPlaybackWorkerWriting.set(false)
                        _soundLevel.value = 0f
                    }
                } else {
                    if (pcmPlaybackQueue.isEmpty()) {
                        isPlaybackWorkerWriting.set(false)
                        if (_callState.value != VoiceCallState.USER_SPEAKING) {
                            val now = System.currentTimeMillis()
                            if (now < playbackFinishTimeMs && !isPlaybackManuallyCancelled.get() && _soundLevel.value > 0.01f) {
                                // Décroissance progressive pendant les derniers millisecondes du buffer matériel AudioTrack
                                val decayed = _soundLevel.value * 0.84f
                                _soundLevel.value = if (decayed < 0.02f) 0f else decayed
                                delay(16L)
                                continue
                            } else {
                                _soundLevel.value = 0f
                            }
                        }
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
        currentTurnVoiceFrames = 0
        stopAndFlushPlayback()
        try {
            synchronized(queueLock) {
                webSocket?.send("""{"type":"response.cancel"}""")
                webSocket?.send("""{"type":"input_audio_buffer.clear"}""")
                purgePreviousConversationItems()
            }
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
        var cleaned = raw
            .replace(Regex("(?i)\\bChatGPT\\b"), "SAM AI")
            .replace(Regex("(?i)\\bGPT-4(\\.1|\\.5|o)?(-mini|-turbo|-preview|-realtime)?\\b"), "SAM AI")
            .replace(Regex("(?i)\\bOpenAI\\b"), "SAM AI")
            .trim()

        val userSpokenLower = (_userTranscript.value.ifBlank { lastCompletedUserTranscript }).lowercase(java.util.Locale.ROOT)
        val userSaidGreetingOrAskedToSayBonjour = userSpokenLower.contains("bonjour") ||
            userSpokenLower.contains("salut") ||
            userSpokenLower.contains("bonsoir")
        if (!userSaidGreetingOrAskedToSayBonjour) {
            cleaned = cleaned.replace(
                Regex("^(?i)(bonjour|salut|bonsoir)\\s*[!,.?:;\\-]*\\s+"),
                ""
            ).replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.FRENCH) else it.toString() }
        }
        return cleaned.trim()
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
