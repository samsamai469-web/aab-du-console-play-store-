package com.example.util

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.data.remote.LiveNewsFeedService
import com.example.ui.components.VoiceCallState
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AudioTranscriptionConfig
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.InlineData
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.LiveServerContent
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.liveGenerationConfig
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/**
 * Gestionnaire officiel d'appel vocal temps réel propulsé à 100 % par Google Gemini Live
 * via Firebase AI Logic (LiveSession bidirectionnel Audio-to-Audio + Google Search Grounding).
 * - Modèle principal : gemini-3.8-live (dernier modèle phare vocal bidirectionnel Google)
 * - Replis automatiques : gemini-3.1-flash-live-preview -> gemini-2.5-flash-native-audio-preview-12-2025
 * - Capture microphone PCM 16-bit 16 kHz mono -> session.sendAudioRealtime(...)
 * - Restitution AudioTrack PCM 16-bit 24 kHz mono + calcul RMS temps réel pour l'orbe Saturne
 * - Verrouillé en Français avec l'identité officielle de SAM AI.
 */
@OptIn(PublicPreviewAPI::class)
class GoogleGeminiLiveCallManager(
    private val context: Context,
    private val onTurnCompleted: (userText: String, aiText: String) -> Unit = { _, _ -> },
    private val onFallbackRequired: (reason: String) -> Unit = {}
) {
    companion object {
        private const val TAG = "GeminiLiveCall"
        private const val INPUT_SAMPLE_RATE = 16000
        private const val OUTPUT_SAMPLE_RATE = 24000
        private const val LIVE_MODEL_PRIMARY = "gemini-3.8-live"
        private const val LIVE_MODEL_SECONDARY = "gemini-3.1-flash-live-preview"
        private const val LIVE_MODEL_FALLBACK = "gemini-2.5-flash-native-audio-preview-12-2025"
        private const val LIVE_VOICE = "Charon"

        private const val SAM_AI_LIVE_INSTRUCTIONS = """
Tu es SAM AI, un homme chaleureux, naturel, cultivé et ultra-précis. L'appel téléphonique avec l'utilisateur est DÉJÀ en cours et les salutations ont DÉJÀ été faites.
Nous sommes en octobre 2026 et tu disposes de Google Search en temps réel ainsi que des actualités en direct ci-dessous.
RÈGLES ABSOLUES ET STRICTES (ZÉRO ERREUR) :
1. INTERDICTION TOTALE DE DIRE "BONJOUR", "SALUT" OU "BONSOIR" :
   - Ne commence JAMAIS tes réponses par « Bonjour », « Salut », « Bonsoir », « Allô » ou « Ravi de vous entendre ».
   - Réponds directement dès le premier mot au contenu exact de la demande de l'utilisateur, sans aucune formule de salutation.
   - Ne prononce le mot « Bonjour » que si l'utilisateur te donne l'ordre explicite « Dis bonjour » ou « Répète bonjour ».
2. OBÉISSANCE EXACTE ET LITTÉRALE À CE QUE L'UTILISATEUR DEMANDE DE DIRE OU DE FAIRE :
   - Si l'utilisateur te demande de DIRE, RÉPÉTER, PRONONCER ou LIRE quelque chose de précis (ex : « Dis ... », « Répète ... », « Prononce ... »), prononce EXACTEMENT et UNIQUEMENT les mots demandés par l'utilisateur, mot pour mot, sans ajouter « Bonjour », « D'accord » ni « Bien sûr ».
   - Si l'utilisateur te demande de FAIRE quelque chose (répondre à une question, expliquer, calculer, traduire, rechercher une information d'actualité en direct), réponds avec précision dès la première phrase.
3. VOIX D'HOMME HUMAINE NATURELLE ET 100 % FRANÇAIS :
   - Tu es un homme : utilise toujours les accords masculins ("je suis attentif", "je suis prêt").
   - Parle en français clair, fluide et naturel (sauf si l'utilisateur te demande explicitement de dire ou traduire une phrase dans une autre langue).
4. ACTUALITÉS & IDENTITÉ EXCLUSIVE SAM AI :
   - Utilise Google Search en direct dès qu'une question porte sur l'actualité récente, les résultats sportifs, la politique, la météo ou des faits récents, sans jamais dire que tes connaissances s'arrêtent en 2023, 2024 ou 2025.
   - Tu t'appelles exclusivement Sam AI. Tu ne t'appelles JAMAIS Gemini, Google, Nova, Ash, ChatGPT ni OpenAI.
   - Si l'utilisateur te demande comment tu t'appelles, quel est ton nom ou qui tu es, réponds toujours directement : « Je m'appelle Sam AI. »
   - Si l'utilisateur demande qui t'a créé, réponds que tu as été créé par Abdoul Mohamine Sawadogo, un jeune développeur burkinabè.
"""

        private val HALLUCINATED_TRANSCRIPT_PATTERNS = listOf(
            "burkina faso, ouagadougou, bobo-dioulasso",
            "ouagadougou, bobo-dioulasso, abdoul mohamine",
            "bobo-dioulasso, abdoul mohamine sawadogo",
            "sous-titres réalisés",
            "sous-titrage",
            "amara.org",
            "merci d'avoir regardé",
            "merci d'avoir écouté"
        )
    }

    private val firebaseAi by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI())
    }
    private val liveNewsFeedService = LiveNewsFeedService()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var liveSession: LiveSession? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var recordingJob: Job? = null
    private var receiveJob: Job? = null
    private var playbackJob: Job? = null
    private var playbackEndCheckJob: Job? = null

    private val isActiveCall = AtomicBoolean(false)
    private val isMuted = AtomicBoolean(false)
    private val isSessionReady = AtomicBoolean(false)
    private val isResponseInProgress = AtomicBoolean(false)
    private val acceptingAiAudio = AtomicBoolean(false)
    private val isPlaybackWorkerWriting = AtomicBoolean(false)
    private val isPlaybackManuallyCancelled = AtomicBoolean(false)

    private val pcmPlaybackQueue = ConcurrentLinkedQueue<ByteArray>()

    @Volatile
    private var sessionReadyAtMs: Long = 0L

    @Volatile
    private var playbackFinishTimeMs: Long = 0L

    private val postPlaybackEchoGuardMs = 520L

    private val _callState = MutableStateFlow(VoiceCallState.CONNECTING)
    val callState: StateFlow<VoiceCallState> = _callState.asStateFlow()

    private val _soundLevel = MutableStateFlow(0f)
    val soundLevel: StateFlow<Float> = _soundLevel.asStateFlow()

    private val _userTranscript = MutableStateFlow("")
    val userTranscript: StateFlow<String> = _userTranscript.asStateFlow()

    private val _aiTranscript = MutableStateFlow("")
    val aiTranscript: StateFlow<String> = _aiTranscript.asStateFlow()

    private val currentUserTranscriptBuilder = StringBuilder()
    private val currentAiTranscriptBuilder = StringBuilder()

    private val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var previousAudioMode: Int = AudioManager.MODE_NORMAL

    fun startCall() {
        if (isActiveCall.getAndSet(true)) return
        TtsManager.stopAllActiveTts()
        SpeechRecognitionManager.pauseAllActiveRecognizersForTts()

        isSessionReady.set(false)
        isResponseInProgress.set(false)
        acceptingAiAudio.set(false)
        isPlaybackWorkerWriting.set(false)
        isPlaybackManuallyCancelled.set(false)
        pcmPlaybackQueue.clear()
        sessionReadyAtMs = 0L
        playbackFinishTimeMs = 0L
        currentUserTranscriptBuilder.clear()
        currentAiTranscriptBuilder.clear()

        _callState.value = VoiceCallState.CONNECTING
        _userTranscript.value = ""
        _aiTranscript.value = ""
        _soundLevel.value = 0f
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        try {
            audioManager?.let { am ->
                previousAudioMode = am.mode
                if (am.mode != AudioManager.MODE_NORMAL) {
                    am.mode = AudioManager.MODE_NORMAL
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "AudioManager mode notice: ${e.message}")
        }

        initAudioTrack()
        startPlaybackWorker()

        scope.launch(Dispatchers.IO) {
            try {
                liveNewsFeedService.fetchLiveNews("actualités")
            } catch (_: Throwable) {}

            val liveHeadlines = LiveNewsFeedService.getInstantCachedSummary()
            val connected = connectLiveSession(liveHeadlines)
            if (!connected && isActiveCall.get()) {
                notifyFallback("Session Google Gemini Live indisponible")
            }
        }
    }

    private fun buildLiveSystemInstruction(liveHeadlines: String): String {
        val dateStr = SimpleDateFormat("EEEE d MMMM yyyy", Locale.FRENCH).format(Date())
        return buildString {
            append("Date actuelle : $dateStr (octobre 2026).\n")
            append(SAM_AI_LIVE_INSTRUCTIONS.trim())
            if (liveHeadlines.isNotBlank()) {
                append("\n\nACTUALITÉS EN DIRECT :\n")
                append(liveHeadlines.take(800))
            }
        }
    }

    private suspend fun connectLiveSession(liveHeadlines: String): Boolean {
        val sysContent = content { text(buildLiveSystemInstruction(liveHeadlines)) }
        val liveConfig = liveGenerationConfig {
            responseModality = ResponseModality.AUDIO
            speechConfig = SpeechConfig(voice = Voice(LIVE_VOICE))
            inputAudioTranscription = AudioTranscriptionConfig()
            outputAudioTranscription = AudioTranscriptionConfig()
        }

        val modelsToTry = listOf(LIVE_MODEL_PRIMARY, LIVE_MODEL_SECONDARY, LIVE_MODEL_FALLBACK)
        for (modelName in modelsToTry) {
            if (!isActiveCall.get()) return false
            try {
                Log.d(TAG, "Connexion Google Gemini Live avec $modelName...")
                val liveModel = firebaseAi.liveModel(
                    modelName = modelName,
                    generationConfig = liveConfig,
                    systemInstruction = sysContent,
                    tools = listOf(Tool.googleSearch())
                )
                val session = withTimeout(12_000L) {
                    liveModel.connect()
                }
                if (!isActiveCall.get()) {
                    try { session.close() } catch (_: Throwable) {}
                    return false
                }
                liveSession = session
                sessionReadyAtMs = System.currentTimeMillis()
                isSessionReady.set(true)
                _callState.value = VoiceCallState.USER_SPEAKING
                Log.d(TAG, "Google Gemini Live connecté ($modelName)")

                startReceivingLoop(session)
                startMicrophoneStreaming(session)
                return true
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Throwable) {
                Log.w(TAG, "Échec connexion Live sur $modelName : ${e.message}", e)
            }
        }
        return false
    }

    private fun startReceivingLoop(session: LiveSession) {
        receiveJob?.cancel()
        receiveJob = scope.launch(Dispatchers.IO) {
            try {
                session.receive().collect { message ->
                    if (!isActiveCall.get()) return@collect
                    if (message is LiveServerContent) {
                        handleLiveServerContent(message)
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Throwable) {
                Log.e(TAG, "Flux LiveSession interrompu : ${e.message}", e)
                if (isActiveCall.get()) {
                    notifyFallback("Connexion vocale en direct interrompue")
                }
            }
        }
    }

    private fun handleLiveServerContent(msg: LiveServerContent) {
        if (msg.interrupted) {
            isPlaybackManuallyCancelled.set(true)
            acceptingAiAudio.set(false)
            isResponseInProgress.set(false)
            stopAndFlushPlayback()
            currentAiTranscriptBuilder.clear()
            _callState.value = VoiceCallState.USER_SPEAKING
            return
        }

        val inputChunk = msg.inputTranscription?.text.orEmpty()
        if (inputChunk.isNotEmpty() && !isAiSpeakingOrEchoGuardActive()) {
            currentUserTranscriptBuilder.append(inputChunk)
            val candidateUser = currentUserTranscriptBuilder.toString().trim()
            if (candidateUser.isNotBlank() && !isHallucinatedOrNoiseTranscript(candidateUser)) {
                _userTranscript.value = candidateUser
            }
        }

        val outputChunk = msg.outputTranscription?.text.orEmpty()
        if (outputChunk.isNotEmpty() && !isPlaybackManuallyCancelled.get()) {
            if (!isResponseInProgress.get()) {
                currentAiTranscriptBuilder.clear()
                isResponseInProgress.set(true)
                acceptingAiAudio.set(true)
            }
            currentAiTranscriptBuilder.append(outputChunk)
            _aiTranscript.value = sanitizeSamAiPersona(currentAiTranscriptBuilder.toString())
        }

        val parts = msg.content?.parts.orEmpty()
        for (part in parts) {
            if (part is InlineDataPart) {
                val pcmBytes = part.inlineData
                if (pcmBytes.isNotEmpty() && !isPlaybackManuallyCancelled.get()) {
                    isResponseInProgress.set(true)
                    acceptingAiAudio.set(true)
                    _callState.value = VoiceCallState.SAM_SPEAKING
                    enqueuePcmForPlayback(pcmBytes)
                }
            }
        }

        if (msg.turnComplete) {
            isResponseInProgress.set(false)
            val finalUser = currentUserTranscriptBuilder.toString().trim()
            val finalAiRaw = currentAiTranscriptBuilder.toString().trim()
            val finalAi = if (finalAiRaw.isNotBlank()) sanitizeSamAiPersona(finalAiRaw) else ""

            if (finalAi.isNotBlank()) {
                _aiTranscript.value = finalAi
            }

            if (finalUser.isNotBlank() && finalAi.isNotBlank() && !isHallucinatedOrNoiseTranscript(finalUser)) {
                mainHandler.post {
                    onTurnCompleted(finalUser, finalAi)
                }
            }

            currentUserTranscriptBuilder.clear()
            currentAiTranscriptBuilder.clear()
            isPlaybackManuallyCancelled.set(false)
            scheduleReturnToListeningState()
        }
    }

    private fun isHallucinatedOrNoiseTranscript(raw: String): Boolean {
        val cleaned = raw.trim()
        if (cleaned.length < 2) return true
        val lower = cleaned.lowercase(Locale.ROOT)
        if (HALLUCINATED_TRANSCRIPT_PATTERNS.any { lower.contains(it) }) return true
        return cleaned.count { it.isLetter() } < 2
    }

    private fun isAiSpeakingOrEchoGuardActive(now: Long = System.currentTimeMillis()): Boolean {
        return isResponseInProgress.get() ||
            acceptingAiAudio.get() ||
            isPlaybackWorkerWriting.get() ||
            pcmPlaybackQueue.isNotEmpty() ||
            _callState.value == VoiceCallState.SAM_SPEAKING ||
            _callState.value == VoiceCallState.THINKING ||
            (playbackFinishTimeMs > 0L && now < (playbackFinishTimeMs + postPlaybackEchoGuardMs))
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
                _callState.value = VoiceCallState.USER_SPEAKING
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startMicrophoneStreaming(session: LiveSession) {
        recordingJob?.cancel()
        recordingJob = scope.launch(Dispatchers.IO) {
            val minBuffer = AudioRecord.getMinBufferSize(
                INPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(2560)

            val recorder = listOf(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                MediaRecorder.AudioSource.MIC
            ).firstNotNullOfOrNull { src ->
                try {
                    val rec = AudioRecord(
                        src,
                        INPUT_SAMPLE_RATE,
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
                Log.e(TAG, "Impossible d'initialiser AudioRecord 16kHz")
                notifyFallback("Microphone indisponible en mode PCM")
                return@launch
            }

            audioRecord = recorder
            try {
                if (AcousticEchoCanceler.isAvailable()) {
                    echoCanceler = AcousticEchoCanceler.create(recorder.audioSessionId)?.apply {
                        enabled = true
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "AEC matériel non disponible: ${e.message}")
            }

            try {
                recorder.startRecording()
            } catch (e: Exception) {
                Log.e(TAG, "Erreur startRecording: ${e.message}")
                notifyFallback("Erreur démarrage microphone")
                return@launch
            }

            val frameBuffer = ByteArray(640)
            var initialFramesSkipped = 0
            var wasAiSpeakingOrGuarded = false
            var postAiFlushFramesRemaining = 0
            var ambientFloor = 55.0
            var voiceHoldFrames = 0

            while (isActive && isActiveCall.get()) {
                val readBytes = recorder.read(frameBuffer, 0, frameBuffer.size)
                if (readBytes > 0) {
                    if (initialFramesSkipped < 12) {
                        initialFramesSkipped++
                        _soundLevel.value = 0f
                        continue
                    }

                    if (isMuted.get() || !isSessionReady.get()) {
                        _soundLevel.value = 0f
                        voiceHoldFrames = 0
                        continue
                    }

                    val now = System.currentTimeMillis()
                    if (sessionReadyAtMs > 0L && now - sessionReadyAtMs < 150L) {
                        _soundLevel.value = 0f
                        continue
                    }

                    val isAiBusyOrSpeaking = isAiSpeakingOrEchoGuardActive(now)
                    if (isAiBusyOrSpeaking) {
                        if (!isPlaybackWorkerWriting.get() && pcmPlaybackQueue.isEmpty() && _callState.value != VoiceCallState.SAM_SPEAKING) {
                            _soundLevel.value = 0f
                        }
                        voiceHoldFrames = 0
                        wasAiSpeakingOrGuarded = true
                        continue
                    } else if (wasAiSpeakingOrGuarded) {
                        wasAiSpeakingOrGuarded = false
                        postAiFlushFramesRemaining = 10
                        _soundLevel.value = 0f
                        voiceHoldFrames = 0
                        continue
                    }

                    if (postAiFlushFramesRemaining > 0) {
                        postAiFlushFramesRemaining--
                        _soundLevel.value = 0f
                        continue
                    }

                    var rawSumSquares = 0.0
                    val sampleCount = readBytes / 2
                    val processedBuffer = ByteArray(readBytes)
                    var j = 0
                    while (j + 1 < readBytes) {
                        val low = frameBuffer[j].toInt() and 0xFF
                        val high = frameBuffer[j + 1].toInt()
                        val rawSample = (high shl 8) or low
                        rawSumSquares += (rawSample * rawSample).toDouble()

                        val boosted = (rawSample * 33) / 20
                        val outSample = when {
                            boosted > 24000 -> (24000 + (boosted - 24000) / 3).coerceAtMost(Short.MAX_VALUE.toInt())
                            boosted < -24000 -> (-24000 + (boosted + 24000) / 3).coerceAtLeast(Short.MIN_VALUE.toInt())
                            else -> boosted
                        }
                        processedBuffer[j] = (outSample and 0xFF).toByte()
                        processedBuffer[j + 1] = ((outSample shr 8) and 0xFF).toByte()
                        j += 2
                    }

                    val rawRms = if (sampleCount > 0) sqrt(rawSumSquares / sampleCount) else 0.0
                    if (voiceHoldFrames == 0 && rawRms in 1.0..120.0) {
                        ambientFloor = ((ambientFloor * 0.92) + (rawRms * 0.08)).coerceIn(35.0, 120.0)
                    }
                    val vocalGate = maxOf(70.0, ambientFloor * 1.32).coerceAtMost(155.0)
                    val isVoiceFrame = rawRms >= vocalGate
                    if (isVoiceFrame) {
                        voiceHoldFrames = 18
                    } else if (voiceHoldFrames > 0) {
                        voiceHoldFrames--
                    }

                    val visualGate = maxOf(65.0, ambientFloor * 1.18)
                    val normalizedLevel = if (rawRms >= visualGate || isVoiceFrame || voiceHoldFrames > 0) {
                        val linear = ((rawRms - ambientFloor * 0.88).coerceAtLeast(0.0) / 1400.0).coerceIn(0.0, 1.0)
                        sqrt(linear).toFloat().coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    _soundLevel.value = normalizedLevel

                    try {
                        session.sendAudioRealtime(
                            InlineData(
                                data = processedBuffer,
                                mimeType = "audio/pcm;rate=$INPUT_SAMPLE_RATE"
                            )
                        )
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (e: Throwable) {
                        Log.w(TAG, "Envoi audio Live échoué : ${e.message}")
                    }
                }
            }
        }
    }

    private fun initAudioTrack() {
        try {
            val minBuffer = AudioTrack.getMinBufferSize(
                OUTPUT_SAMPLE_RATE,
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
                        .setSampleRate(OUTPUT_SAMPLE_RATE)
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
        val chunkDurationMs = (pcmBytes.size * 1000L) / (OUTPUT_SAMPLE_RATE * 2)
        playbackFinishTimeMs = maxOf(now, playbackFinishTimeMs) + chunkDurationMs
        pcmPlaybackQueue.offer(pcmBytes)
    }

    private fun startPlaybackWorker() {
        playbackJob?.cancel()
        playbackJob = scope.launch(Dispatchers.IO) {
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
                                playbackFinishTimeMs = maxOf(playbackFinishTimeMs, System.currentTimeMillis() + 320L)

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
        stopAndFlushPlayback()
        currentAiTranscriptBuilder.clear()
        _callState.value = VoiceCallState.USER_SPEAKING
    }

    fun commitUserSpeechNow() {
        val currentText = currentUserTranscriptBuilder.toString().trim()
        if (!isResponseInProgress.get() && currentText.isNotBlank()) {
            _callState.value = VoiceCallState.THINKING
        }
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
            .replace(Regex("(?i)\\bChatGPT\\b"), "Sam AI")
            .replace(Regex("(?i)\\bGemini\\b"), "Sam AI")
            .replace(Regex("(?i)\\bGoogle AI\\b"), "Sam AI")
            .replace(Regex("(?i)\\bOpenAI\\b"), "Sam AI")
            .replace(Regex("(?i)\\bNova\\b"), "Sam AI")
            .trim()

        val userSpoken = _userTranscript.value
        val allowBonjour = Regex(
            "\\b(dis|dites|r[ée]p[èe]te|r[ée]p[ée]tez|prononce|prononcez)\\b[^.?!]*\\b(bonjour|salut|bonsoir)\\b",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(userSpoken)

        if (!allowBonjour) {
            cleaned = cleaned.replace(
                Regex("^(?i)(bonjour|salut|bonsoir)\\s*[!,.?:;\\-]*\\s*"),
                ""
            ).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.FRENCH) else it.toString() }
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
            receiveJob?.cancel()
            recordingJob?.cancel()
            playbackJob?.cancel()
            playbackEndCheckJob?.cancel()
            val sessionToClose = liveSession
            liveSession = null
            if (sessionToClose != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    try { sessionToClose.close() } catch (_: Throwable) {}
                }
            }
            scope.cancel()
        } catch (_: Exception) {}

        try {
            echoCanceler?.release()
            echoCanceler = null
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
                am.mode = previousAudioMode
            }
        } catch (_: Exception) {}
    }
}
