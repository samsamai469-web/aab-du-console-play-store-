package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.audiofx.Visualizer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.sqrt

class TtsManager(context: Context) : TextToSpeech.OnInitListener {
    private val appContext: Context = context.applicationContext
    private var tts: TextToSpeech? = null
    private var mediaPlayer: MediaPlayer? = null
    @Volatile
    private var activeAudioTrack: AudioTrack? = null
    private var audioVisualizer: Visualizer? = null
    private val audioManager by lazy {
        appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }
    private var audioFocusRequest: AudioFocusRequest? = null
    private var isReady = false
    @Volatile
    private var isNeuralPlaying = false
    private var currentSpeechRate: Float = 1.0f

    private val _ttsSoundLevel = MutableStateFlow(0f)
    val ttsSoundLevel: StateFlow<Float> = _ttsSoundLevel.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var speechJob: Job? = null
    private val activeSpeechGeneration = AtomicLong(0L)

    private val firebaseAi by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI())
    }

    companion object {
        // Modèles Google Gemini TTS officiels supportés par Firebase AI Logic (voix masculine naturelle "Charon")
        private const val PRIMARY_TTS_MODEL = "gemini-3.1-flash-tts-preview"
        private const val UNIFIED_GOOGLE_MALE_VOICE = "Charon"
        private const val CACHE_PREFIX = "sam_tts_google_v7_"
        private const val FIRST_CHUNK_TIMEOUT_MS = 700L
        private const val INTER_CHUNK_STALL_TIMEOUT_MS = 3_500L
        private const val NEURAL_COOLDOWN_MS = 180_000L
        private const val MAX_CONSECUTIVE_NEURAL_FAILURES = 1

        @Volatile
        private var preferredWorkingTtsModel: String? = null

        @Volatile
        private var consecutiveNeuralFailures: Int = 0

        @Volatile
        private var lastNeuralFailureTimeMs: Long = 0L

        private val activeInstances = java.util.Collections.synchronizedSet(
            java.util.Collections.newSetFromMap(java.util.WeakHashMap<TtsManager, Boolean>())
        )

        @Volatile
        var isGlobalTtsSpeaking: Boolean = false
            private set

        @Volatile
        var lastGlobalTtsEndTimeMs: Long = 0L
            private set

        fun isAnyTtsSpeakingOrCoolingDown(guardMs: Long = 480L): Boolean {
            if (isGlobalTtsSpeaking) return true
            val anyInstanceSpeaking = synchronized(activeInstances) {
                activeInstances.any { it.isSpeaking() }
            }
            if (anyInstanceSpeaking) return true
            val elapsed = System.currentTimeMillis() - lastGlobalTtsEndTimeMs
            return lastGlobalTtsEndTimeMs > 0L && elapsed in 0..guardMs
        }

        fun stopAllActiveTts() {
            isGlobalTtsSpeaking = false
            val snapshot = synchronized(activeInstances) { activeInstances.toList() }
            snapshot.forEach { instance ->
                try {
                    instance.stop()
                } catch (_: Exception) {}
            }
        }
    }

    private var activeOnDoneCallback: (() -> Unit)? = null
    private var activeOnErrorCallback: (() -> Unit)? = null
    private var activeOnStartCallback: (() -> Unit)? = null
    private var pendingSpeech: (() -> Unit)? = null
    private var pendingInitWatchdog: Runnable? = null
    @Volatile
    private var activeUtteranceId: String? = null
    private val cachedVoicesByLang = java.util.concurrent.ConcurrentHashMap<String, android.speech.tts.Voice>()

    init {
        activeInstances.add(this)
        try {
            tts = TextToSpeech(appContext, this)
        } catch (_: Exception) {
            isReady = false
        }
        // Purge les anciens fichiers audio mis en cache avant la migration 100 % Google Gemini TTS
        scope.launch {
            try {
                val dir = File(appContext.cacheDir, "tts_voice_cache")
                if (dir.exists()) {
                    dir.listFiles()?.forEach { file ->
                        if (file.isFile && !file.name.startsWith(CACHE_PREFIX)) {
                            file.delete()
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun normalizeLanguageCode(language: String?): String {
        val l = language?.lowercase(Locale.ROOT)?.trim().orEmpty()
        return when {
            l.isBlank() || l.contains("franç") || l.contains("franc") || l == "fr" -> "fr"
            l.contains("moor") || l.contains("mòor") || l == "mos" -> "mos"
            l.contains("dioula") || l.contains("jula") || l == "dyu" -> "dyu"
            l.contains("haoussa") || l.contains("hausa") || l == "ha" -> "ha"
            l.contains("angl") || l.contains("engl") || l == "en" -> "en"
            l.contains("allem") || l.contains("germ") || l.contains("deutsch") || l == "de" -> "de"
            l.contains("espagn") || l.contains("span") || l == "es" -> "es"
            l.contains("arab") || l == "ar" -> "ar"
            l.contains("ital") || l == "it" -> "it"
            l.contains("port") || l == "pt" -> "pt"
            l.contains("russ") || l == "ru" -> "ru"
            l.contains("chin") || l == "zh" -> "zh"
            else -> "fr"
        }
    }

    private fun toBcp47LanguageCode(langCode: String): String {
        return when (langCode) {
            "en" -> "en-US"
            "de" -> "de-DE"
            "es" -> "es-ES"
            "ar" -> "ar-XA"
            "it" -> "it-IT"
            "pt" -> "pt-BR"
            "ru" -> "ru-RU"
            "zh" -> "cmn-CN"
            // Le Mooré, Dioula et Haoussa passent par la transcription phonétique française
            "mos", "dyu", "ha", "fr" -> "fr-FR"
            else -> "fr-FR"
        }
    }

    private fun getCachedVoiceFile(spokenText: String, langCode: String, speed: Float): File {
        val dir = File(appContext.cacheDir, "tts_voice_cache").apply { if (!exists()) mkdirs() }
        val speedTag = "%.2f".format(Locale.US, speed)
        val normalized = "${langCode}_${UNIFIED_GOOGLE_MALE_VOICE}_${speedTag}_${spokenText.lowercase(Locale.ROOT).trim()}"
        val h1 = normalized.hashCode().toUInt().toString(16)
        val h2 = normalized.reversed().hashCode().toUInt().toString(16)
        return File(dir, "${CACHE_PREFIX}${langCode}_${UNIFIED_GOOGLE_MALE_VOICE}_${h1}_$h2.wav")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            try {
                tts?.language = Locale.FRENCH
                tts?.setPitch(0.98f)
                tts?.setSpeechRate(1.0f)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        if (utteranceId != null && activeUtteranceId != null && utteranceId != activeUtteranceId) return
                        isGlobalTtsSpeaking = true
                        SpeechRecognitionManager.pauseAllActiveRecognizersForTts()
                        mainHandler.post {
                            if (utteranceId == null || activeUtteranceId == null || utteranceId == activeUtteranceId) {
                                activeOnStartCallback?.invoke()
                            }
                        }
                    }

                    override fun onAudioAvailable(utteranceId: String?, audio: ByteArray?) {
                        if (utteranceId != null && activeUtteranceId != null && utteranceId != activeUtteranceId) return
                        if (audio != null && audio.size >= 4) {
                            var sumSq = 0.0
                            val samples = audio.size / 2
                            var idx = 0
                            while (idx + 1 < audio.size) {
                                val low = audio[idx].toInt() and 0xFF
                                val high = audio[idx + 1].toInt()
                                val sample = (high shl 8) or low
                                sumSq += (sample * sample).toDouble()
                                idx += 2
                            }
                            val rms = if (samples > 0) sqrt(sumSq / samples) else 0.0
                            val linear = ((rms - 45.0).coerceAtLeast(0.0) / 1650.0).coerceIn(0.0, 1.0)
                            _ttsSoundLevel.value = sqrt(linear).toFloat().coerceIn(0f, 1f)
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        if (utteranceId != null && activeUtteranceId != null && utteranceId != activeUtteranceId) return
                        activeUtteranceId = null
                        isGlobalTtsSpeaking = false
                        _ttsSoundLevel.value = 0f
                        lastGlobalTtsEndTimeMs = System.currentTimeMillis()
                        val callback = activeOnDoneCallback
                        activeOnDoneCallback = null
                        mainHandler.post {
                            callback?.invoke()
                        }
                    }

                    override fun onError(utteranceId: String?) {
                        if (utteranceId != null && activeUtteranceId != null && utteranceId != activeUtteranceId) return
                        activeUtteranceId = null
                        isGlobalTtsSpeaking = false
                        _ttsSoundLevel.value = 0f
                        lastGlobalTtsEndTimeMs = System.currentTimeMillis()
                        val callback = activeOnErrorCallback
                        activeOnErrorCallback = null
                        mainHandler.post {
                            callback?.invoke()
                        }
                    }
                })
                isReady = true
                pendingInitWatchdog?.let { mainHandler.removeCallbacks(it) }
                pendingInitWatchdog = null
                scope.launch(Dispatchers.IO) {
                    warmUpVoiceCache()
                }
                val pending = pendingSpeech
                pendingSpeech = null
                pending?.invoke()
            } catch (_: Exception) {
                isReady = false
                isGlobalTtsSpeaking = false
                pendingSpeech = null
                pendingInitWatchdog?.let { mainHandler.removeCallbacks(it) }
                pendingInitWatchdog = null
            }
        } else {
            isReady = false
            isGlobalTtsSpeaking = false
            pendingSpeech = null
            val callback = activeOnErrorCallback
            activeOnErrorCallback = null
            mainHandler.post { callback?.invoke() }
        }
    }

    fun setSpeechRate(rate: Float) {
        currentSpeechRate = rate.coerceIn(0.5f, 2.0f)
        try {
            tts?.setSpeechRate(currentSpeechRate)
        } catch (_: Exception) {}
    }

    fun isSpeaking(): Boolean {
        return try {
            isNeuralPlaying || (tts?.isSpeaking ?: false)
        } catch (_: Exception) {
            false
        }
    }

    fun speak(text: String, language: String? = null) {
        speakWithCallback(
            text = text,
            language = language,
            rate = currentSpeechRate,
            onStart = null,
            onDone = null,
            onError = null
        )
    }

    fun speakWithCallback(
        text: String,
        language: String? = null,
        rate: Float = currentSpeechRate,
        onStart: (() -> Unit)? = null,
        onDone: (() -> Unit)? = null,
        onError: (() -> Unit)? = null
    ) {
        if (text.isBlank()) {
            onError?.invoke()
            return
        }

        stop()

        val langCode = normalizeLanguageCode(language)
        val spokenText = prepareSpokenText(text, langCode)

        if (spokenText.isBlank()) {
            onError?.invoke()
            return
        }

        activeOnStartCallback = onStart
        activeOnDoneCallback = onDone
        activeOnErrorCallback = onError

        // Verrouille immédiatement l'état global TTS et coupe tout microphone de reconnaissance actif
        // pour que SAM AI ne ré-entende jamais sa propre voix en haut-parleur
        val requestGeneration = activeSpeechGeneration.incrementAndGet()
        isGlobalTtsSpeaking = true
        SpeechRecognitionManager.pauseAllActiveRecognizersForTts()

        speechJob = scope.launch {
            val success = trySpeakWithNeuralVoice(spokenText, langCode, rate, requestGeneration)
            if (!success && isActive && requestGeneration == activeSpeechGeneration.get() && isGlobalTtsSpeaking) {
                withContext(Dispatchers.Main) {
                    if (requestGeneration == activeSpeechGeneration.get() && isGlobalTtsSpeaking) {
                        speakWithLocalAndroidTts(spokenText, langCode, rate)
                    }
                }
            }
        }
    }

    private fun buildHumanVoiceDirectorPrompt(langCode: String, ttsInput: String): String {
        val styleDirection = when (langCode) {
            "en" -> "Warm natural male English voice:"
            "es" -> "Voz masculina cálida y natural:"
            "de" -> "Warme, natürliche männliche Stimme:"
            "ar" -> "بصوت رجالي دافئ وطبيعي:"
            "it" -> "Voce maschile calda e naturale:"
            "pt" -> "Voz masculina calorosa e natural:"
            "ru" -> "Тёплым, естественным мужским голосом:"
            "zh" -> "用温暖自然的男性声音朗读："
            "mos" -> "Lis cette phonétique mooré d'une voix masculine calme et naturelle :"
            "dyu" -> "Lis cette phonétique dioula d'une voix masculine calme et naturelle :"
            "ha" -> "Lis cette phonétique haoussa d'une voix masculine calme et naturelle :"
            else -> "Voix masculine calme, chaleureuse et naturelle :"
        }
        return "$styleDirection $ttsInput"
    }

    private fun computeNaturalSpeed(userRate: Float): Float {
        return userRate.coerceIn(0.65f, 1.5f)
    }

    private fun ensureNaturalSentenceEnding(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ""
        val last = trimmed.last()
        return if (last == '.' || last == '!' || last == '?' || last == '…' || last == '؟') {
            trimmed
        } else {
            "$trimmed."
        }
    }

    private fun hasRiffWavHeader(bytes: ByteArray): Boolean {
        return bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() &&
            bytes[9] == 'A'.code.toByte() &&
            bytes[10] == 'V'.code.toByte() &&
            bytes[11] == 'E'.code.toByte()
    }

    private fun wrapPcm24kMono16BitAsWav(pcmBytes: ByteArray, sampleRate: Int = 24000): ByteArray {
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * (bitsPerSample / 8)
        val blockAlign = channels * (bitsPerSample / 8)
        val totalDataLen = pcmBytes.size
        val totalAudioLen = totalDataLen + 36

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(totalAudioLen)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16) // PCM subchunk size
            putShort(1.toShort()) // AudioFormat = 1 (PCM)
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort(blockAlign.toShort())
            putShort(bitsPerSample.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(totalDataLen)
        }.array()

        val out = ByteArray(header.size + pcmBytes.size)
        System.arraycopy(header, 0, out, 0, header.size)
        System.arraycopy(pcmBytes, 0, out, header.size, pcmBytes.size)
        return out
    }

    private fun parseSampleRateFromMimeType(mimeType: String): Int {
        val match = Regex("rate=(\\d+)").find(mimeType)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(8000, 48000) ?: 24000
    }

    private fun isStreamablePcmOrWavMimeType(mimeType: String): Boolean {
        val lower = mimeType.lowercase(Locale.ROOT)
        return lower.isBlank() ||
            lower.contains("pcm") ||
            lower.contains("l16") ||
            lower.contains("wav")
    }

    private fun createStreamingAudioTrack(sampleRate: Int, speed: Float): AudioTrack? {
        return try {
            val minBuffer = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)
            val bufferSize = (minBuffer * 2).coerceAtLeast(8192)
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            val track = AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            if (track.state != AudioTrack.STATE_INITIALIZED) {
                try { track.release() } catch (_: Exception) {}
                return null
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && kotlin.math.abs(speed - 1.0f) > 0.03f) {
                try {
                    track.playbackParams = track.playbackParams.setSpeed(speed)
                } catch (_: Exception) {}
            }
            track
        } catch (e: Exception) {
            Log.w("TtsManager", "Impossible de créer AudioTrack streaming: ${e.message}")
            null
        }
    }

    private fun updatePcmSoundLevel(pcmBytes: ByteArray, offset: Int, length: Int) {
        if (length < 2) return
        var sumSq = 0.0
        val samples = length / 2
        var i = offset
        val limit = offset + length
        while (i + 1 < limit) {
            val low = pcmBytes[i].toInt() and 0xFF
            val high = pcmBytes[i + 1].toInt()
            val sample = (high shl 8) or low
            sumSq += (sample * sample).toDouble()
            i += 2
        }
        val rms = if (samples > 0) sqrt(sumSq / samples) else 0.0
        val level = if (rms >= 45.0) {
            val linear = ((rms - 45.0) / 1650.0).coerceIn(0.0, 1.0)
            sqrt(linear).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }
        _ttsSoundLevel.value = level
    }

    private fun releaseStreamAudioTrack(track: AudioTrack?) {
        if (track == null) return
        if (activeAudioTrack === track) {
            activeAudioTrack = null
        }
        try {
            if (track.state == AudioTrack.STATE_INITIALIZED) {
                track.pause()
                track.flush()
                track.stop()
            }
        } catch (_: Exception) {}
        try {
            track.release()
        } catch (_: Exception) {}
    }

    @OptIn(PublicPreviewAPI::class)
    private suspend fun streamAndPlayNeuralVoice(
        spokenText: String,
        langCode: String,
        naturalSpeed: Float,
        cachedFile: File,
        requestGeneration: Long,
        abortedByTimeout: AtomicBoolean,
        firstAudioSignal: CompletableDeferred<Boolean>
    ): Boolean = withContext(Dispatchers.IO) {
        val ttsInput = ensureNaturalSentenceEnding(spokenText).take(2200)
        val prompt = buildHumanVoiceDirectorPrompt(langCode, ttsInput)
        val bcp47 = toBcp47LanguageCode(langCode)

        val ttsConfig = generationConfig {
            responseModalities = listOf(ResponseModality.AUDIO)
            speechConfig = SpeechConfig(
                voice = Voice(UNIFIED_GOOGLE_MALE_VOICE),
                languageCode = bcp47
            )
        }

        val candidateModels = listOf(PRIMARY_TTS_MODEL)

        fun isRequestStillValid(): Boolean {
            return !abortedByTimeout.get() &&
                requestGeneration == activeSpeechGeneration.get() &&
                isGlobalTtsSpeaking
        }

        for (modelName in candidateModels) {
            if (!isActive || !isRequestStillValid()) {
                firstAudioSignal.complete(false)
                return@withContext false
            }
            var localTrack: AudioTrack? = null
            var receivedFirstAudio = false
            var startedStreamingTrack = false
            var compressedFallbackStream: ByteArrayOutputStream? = null
            val fullCacheStream = ByteArrayOutputStream()
            var detectedMimeType = "audio/pcm;rate=24000"
            var detectedSampleRate = 24000
            var totalPcmBytesWritten = 0L
            var pendingOddByte: Byte? = null
            var lastChunkReceivedMs = System.currentTimeMillis()

            try {
                val model = firebaseAi.generativeModel(
                    modelName = modelName,
                    generationConfig = ttsConfig
                )

                coroutineScope {
                    val streamJob = launch {
                        model.generateContentStream(prompt).collect { chunk ->
                            if (!isActive || !isRequestStillValid()) {
                                throw CancellationException("TTS stopped or timed out")
                            }
                            val parts = chunk.candidates.firstOrNull()?.content?.parts.orEmpty()
                            for (part in parts) {
                                if (part is InlineDataPart && part.inlineData.isNotEmpty()) {
                                    lastChunkReceivedMs = System.currentTimeMillis()
                                    if (!receivedFirstAudio) {
                                        receivedFirstAudio = true
                                        if (part.mimeType.isNotBlank()) {
                                            detectedMimeType = part.mimeType
                                        }
                                        detectedSampleRate = parseSampleRateFromMimeType(detectedMimeType)
                                    }

                                    val rawBytes = part.inlineData
                                    fullCacheStream.write(rawBytes)

                                    if (!startedStreamingTrack && compressedFallbackStream == null) {
                                        val hasWavHeader = hasRiffWavHeader(rawBytes) && rawBytes.size >= 44
                                        if (hasWavHeader) {
                                            val wavSampleRate = ByteBuffer.wrap(rawBytes, 24, 4)
                                                .order(ByteOrder.LITTLE_ENDIAN).int
                                            if (wavSampleRate in 8000..48000) {
                                                detectedSampleRate = wavSampleRate
                                            }
                                        }
                                        if (isStreamablePcmOrWavMimeType(detectedMimeType)) {
                                            val canPlay = withContext(Dispatchers.Main) {
                                                if (!isRequestStillValid()) {
                                                    false
                                                } else {
                                                    releaseMediaPlayer()
                                                    requestPlaybackAudioFocus()
                                                    isNeuralPlaying = true
                                                    SpeechRecognitionManager.pauseAllActiveRecognizersForTts()
                                                    true
                                                }
                                            }
                                            if (!canPlay) {
                                                throw CancellationException("TTS stopped before AudioTrack start")
                                            }
                                            val track = createStreamingAudioTrack(detectedSampleRate, naturalSpeed)
                                            if (track != null) {
                                                if (!isRequestStillValid()) {
                                                    releaseStreamAudioTrack(track)
                                                    throw CancellationException("TTS superseded before track play")
                                                }
                                                localTrack = track
                                                activeAudioTrack = track
                                                track.play()
                                                startedStreamingTrack = true
                                                firstAudioSignal.complete(true)
                                                mainHandler.post {
                                                    if (isRequestStillValid()) {
                                                        activeOnStartCallback?.invoke()
                                                    }
                                                }
                                            } else {
                                                compressedFallbackStream = ByteArrayOutputStream()
                                            }
                                        } else {
                                            compressedFallbackStream = ByteArrayOutputStream()
                                        }
                                    }

                                    if (startedStreamingTrack && localTrack != null) {
                                        val track = localTrack!!
                                        val startOffset = if (totalPcmBytesWritten == 0L && hasRiffWavHeader(rawBytes) && rawBytes.size >= 44) 44 else 0
                                        val payloadLen = rawBytes.size - startOffset
                                        if (payloadLen > 0) {
                                            val alignedBytes: ByteArray
                                            val prevOdd = pendingOddByte
                                            if (prevOdd != null) {
                                                val combined = ByteArray(1 + payloadLen)
                                                combined[0] = prevOdd
                                                System.arraycopy(rawBytes, startOffset, combined, 1, payloadLen)
                                                pendingOddByte = null
                                                alignedBytes = combined
                                            } else {
                                                alignedBytes = if (startOffset == 0) rawBytes else rawBytes.copyOfRange(startOffset, rawBytes.size)
                                            }

                                            val evenLen = alignedBytes.size and 0x7FFFFFFE
                                            if (alignedBytes.size % 2 != 0) {
                                                pendingOddByte = alignedBytes[alignedBytes.size - 1]
                                            }

                                            if (evenLen > 0) {
                                                var writtenOffset = 0
                                                while (writtenOffset < evenLen && isActive && isNeuralPlaying && isRequestStillValid()) {
                                                    val slice = minOf(2048, evenLen - writtenOffset)
                                                    val written = track.write(alignedBytes, writtenOffset, slice)
                                                    if (written <= 0) break
                                                    updatePcmSoundLevel(alignedBytes, writtenOffset, written)
                                                    totalPcmBytesWritten += written
                                                    writtenOffset += written
                                                }
                                            }
                                        }
                                    } else {
                                        compressedFallbackStream?.write(rawBytes)
                                    }
                                }
                            }
                        }
                    }

                    val stallWatchdogJob = launch {
                        while (isActive) {
                            delay(200L)
                            if (!isRequestStillValid()) {
                                streamJob.cancel(CancellationException("TTS_ABORTED"))
                                break
                            }
                            if (receivedFirstAudio && (System.currentTimeMillis() - lastChunkReceivedMs) > INTER_CHUNK_STALL_TIMEOUT_MS) {
                                Log.w("TtsManager", "Stall inter-chunk sur $modelName")
                                streamJob.cancel(CancellationException("INTER_CHUNK_STALL"))
                                break
                            }
                        }
                    }

                    streamJob.join()
                    stallWatchdogJob.cancel()
                }

                if (startedStreamingTrack && totalPcmBytesWritten > 0L && localTrack != null && isRequestStillValid()) {
                    preferredWorkingTtsModel = modelName
                    val allBytes = fullCacheStream.toByteArray()
                    val wavBytes = if (hasRiffWavHeader(allBytes)) {
                        allBytes
                    } else {
                        wrapPcm24kMono16BitAsWav(allBytes, detectedSampleRate)
                    }
                    // Sauvegarde en cache en arrière-plan pour lecture instantanée future
                    scope.launch(Dispatchers.IO) {
                        try {
                            FileOutputStream(cachedFile).use { it.write(wavBytes) }
                        } catch (_: Exception) {}
                    }

                    // Attente précise de la fin de lecture dans le buffer matériel AudioTrack
                    val track = localTrack!!
                    val totalFrames = totalPcmBytesWritten / 2L
                    val maxWaitMs = ((totalFrames * 1000L) / (detectedSampleRate * naturalSpeed)).toLong() + 1200L
                    val waitStart = System.currentTimeMillis()
                    while (isActive && isNeuralPlaying && isRequestStillValid()) {
                        val playedFrames = try { track.playbackHeadPosition.toLong() } catch (_: Exception) { totalFrames }
                        if (playedFrames >= totalFrames || (System.currentTimeMillis() - waitStart) > maxWaitMs) {
                            break
                        }
                        delay(35L)
                    }

                    if (isNeuralPlaying && isRequestStillValid()) {
                        isNeuralPlaying = false
                        isGlobalTtsSpeaking = false
                        _ttsSoundLevel.value = 0f
                        lastGlobalTtsEndTimeMs = System.currentTimeMillis()
                        val cb = activeOnDoneCallback
                        activeOnDoneCallback = null
                        mainHandler.post { cb?.invoke() }
                    }
                    firstAudioSignal.complete(true)
                    return@withContext true
                }

                // Cas rare où l'audio reçu était compressé (non-PCM)
                val compressedBytes = compressedFallbackStream?.toByteArray()
                if (compressedBytes != null && compressedBytes.size >= 200 && isRequestStillValid()) {
                    preferredWorkingTtsModel = modelName
                    val playableBytes = if (hasRiffWavHeader(compressedBytes)) {
                        compressedBytes
                    } else {
                        wrapPcm24kMono16BitAsWav(compressedBytes, detectedSampleRate)
                    }
                    FileOutputStream(cachedFile).use { it.write(playableBytes) }
                    firstAudioSignal.complete(true)
                    return@withContext playAudioFileWithMediaPlayer(cachedFile, cachedFile, naturalSpeed, spokenText, langCode, naturalSpeed)
                }
            } catch (ce: CancellationException) {
                firstAudioSignal.complete(false)
                throw ce
            } catch (e: Throwable) {
                Log.w("TtsManager", "Google Gemini TTS $modelName indisponible: ${e.message}")
            } finally {
                withContext(NonCancellable) {
                    releaseStreamAudioTrack(localTrack)
                    if (!abortedByTimeout.get() && requestGeneration == activeSpeechGeneration.get()) {
                        if (startedStreamingTrack) {
                            abandonPlaybackAudioFocus()
                            if (isNeuralPlaying || isGlobalTtsSpeaking) {
                                isNeuralPlaying = false
                                isGlobalTtsSpeaking = false
                                _ttsSoundLevel.value = 0f
                                lastGlobalTtsEndTimeMs = System.currentTimeMillis()
                            }
                        } else {
                            isNeuralPlaying = false
                            _ttsSoundLevel.value = 0f
                        }
                    }
                }
            }

            if (startedStreamingTrack) {
                firstAudioSignal.complete(true)
                return@withContext true
            }
        }
        firstAudioSignal.complete(false)
        false
    }

    private suspend fun playAudioFileWithMediaPlayer(
        audioFileToPlay: File,
        cachedFile: File,
        naturalSpeed: Float,
        spokenText: String,
        langCode: String,
        rawRate: Float
    ): Boolean = withContext(Dispatchers.Main) {
        try {
            releaseMediaPlayer()
            requestPlaybackAudioFocus()
            isNeuralPlaying = true
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(audioFileToPlay.absolutePath)
                setVolume(1.0f, 1.0f)
                setOnPreparedListener { mp ->
                    isGlobalTtsSpeaking = true
                    SpeechRecognitionManager.pauseAllActiveRecognizersForTts()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && kotlin.math.abs(naturalSpeed - 1.0f) > 0.03f) {
                        try {
                            mp.playbackParams = mp.playbackParams.setSpeed(naturalSpeed)
                        } catch (_: Exception) {}
                    }
                    try {
                        audioVisualizer = Visualizer(mp.audioSessionId).apply {
                            captureSize = Visualizer.getCaptureSizeRange()[0]
                            setDataCaptureListener(
                                object : Visualizer.OnDataCaptureListener {
                                    override fun onWaveFormDataCapture(
                                        visualizer: Visualizer?,
                                        waveform: ByteArray?,
                                        samplingRate: Int
                                    ) {
                                        if (waveform != null && waveform.isNotEmpty() && isNeuralPlaying) {
                                            var sumSq = 0.0
                                            for (b in waveform) {
                                                val centered = (b.toInt() and 0xFF) - 128
                                                sumSq += (centered * centered).toDouble()
                                            }
                                            val rms = sqrt(sumSq / waveform.size)
                                            val level = if (rms >= 2.8) {
                                                val linear = ((rms - 2.8) / 28.0).coerceIn(0.0, 1.0)
                                                sqrt(linear).toFloat().coerceIn(0f, 1f)
                                            } else {
                                                0f
                                            }
                                            _ttsSoundLevel.value = level
                                        }
                                    }

                                    override fun onFftDataCapture(
                                        visualizer: Visualizer?,
                                        fft: ByteArray?,
                                        samplingRate: Int
                                    ) {}
                                },
                                Visualizer.getMaxCaptureRate(),
                                true,
                                false
                            )
                            enabled = true
                        }
                    } catch (_: Exception) {}
                    activeOnStartCallback?.invoke()
                    mp.start()
                }
                setOnCompletionListener {
                    isNeuralPlaying = false
                    isGlobalTtsSpeaking = false
                    _ttsSoundLevel.value = 0f
                    lastGlobalTtsEndTimeMs = System.currentTimeMillis()
                    releaseMediaPlayer()
                    val cb = activeOnDoneCallback
                    activeOnDoneCallback = null
                    cb?.invoke()
                }
                setOnErrorListener { _, what, extra ->
                    Log.w("TtsManager", "MediaPlayer error ($what/$extra), bascule TTS local")
                    isNeuralPlaying = false
                    releaseMediaPlayer()
                    if (audioFileToPlay == cachedFile) {
                        cachedFile.delete()
                    }
                    speakWithLocalAndroidTts(spokenText, langCode, rawRate)
                    true
                }
                prepareAsync()
            }
            true
        } catch (e: Exception) {
            Log.w("TtsManager", "MediaPlayer init exception: ${e.message}")
            isNeuralPlaying = false
            releaseMediaPlayer()
            speakWithLocalAndroidTts(spokenText, langCode, rawRate)
            true
        }
    }

    private suspend fun trySpeakWithNeuralVoice(
        spokenText: String,
        langCode: String,
        rate: Float,
        requestGeneration: Long
    ): Boolean = withContext(Dispatchers.IO) {
        val abortedByTimeout = AtomicBoolean(false)
        val firstAudioSignal = CompletableDeferred<Boolean>()
        var neuralJob: Job? = null
        try {
            val naturalSpeed = computeNaturalSpeed(rate)
            val cachedFile = getCachedVoiceFile(spokenText, langCode, naturalSpeed)

            if (cachedFile.exists() && cachedFile.length() >= 200) {
                return@withContext playAudioFileWithMediaPlayer(
                    audioFileToPlay = cachedFile,
                    cachedFile = cachedFile,
                    naturalSpeed = naturalSpeed,
                    spokenText = spokenText,
                    langCode = langCode,
                    rawRate = rate
                )
            }

            if (!com.example.data.local.ApiKeyManager.hasActiveInternetConnection()) {
                return@withContext false
            }

            val inCooldown = consecutiveNeuralFailures >= MAX_CONSECUTIVE_NEURAL_FAILURES &&
                (System.currentTimeMillis() - lastNeuralFailureTimeMs) < NEURAL_COOLDOWN_MS
            if (inCooldown) {
                return@withContext false
            }

            // Course non-bloquante : si aucun octet audio n'arrive sous FIRST_CHUNK_TIMEOUT_MS (700 ms),
            // on détache immédiatement le job sans attendre neuralJob.join() (qui pouvait bloquer 30-60s dans Firebase/AppCheck)
            // et on bascule instantanément sur le moteur vocal local Android.
            neuralJob = scope.launch(Dispatchers.IO) {
                try {
                    streamAndPlayNeuralVoice(
                        spokenText = spokenText,
                        langCode = langCode,
                        naturalSpeed = naturalSpeed,
                        cachedFile = cachedFile,
                        requestGeneration = requestGeneration,
                        abortedByTimeout = abortedByTimeout,
                        firstAudioSignal = firstAudioSignal
                    )
                } catch (ce: CancellationException) {
                    firstAudioSignal.complete(false)
                    throw ce
                } catch (t: Throwable) {
                    Log.w("TtsManager", "Exception Google Gemini TTS, repli local: ${t.message}")
                    firstAudioSignal.complete(false)
                } finally {
                    firstAudioSignal.complete(false)
                }
            }

            val gotFirstAudio = withTimeoutOrNull(FIRST_CHUNK_TIMEOUT_MS) {
                firstAudioSignal.await()
            } == true

            if (!gotFirstAudio || abortedByTimeout.get() || requestGeneration != activeSpeechGeneration.get()) {
                abortedByTimeout.set(true)
                neuralJob.cancel(CancellationException("FIRST_CHUNK_TIMEOUT"))
                consecutiveNeuralFailures++
                lastNeuralFailureTimeMs = System.currentTimeMillis()
                Log.w("TtsManager", "Bascule instantanée vers TTS local (${FIRST_CHUNK_TIMEOUT_MS}ms écoulés)")
                return@withContext false
            }

            // L'audio streaming joue déjà activement : on attend la fin propre de lecture
            neuralJob.join()
            consecutiveNeuralFailures = 0
            lastNeuralFailureTimeMs = 0L
            return@withContext true
        } catch (ce: CancellationException) {
            abortedByTimeout.set(true)
            neuralJob?.cancel(ce)
            throw ce
        } catch (e: Exception) {
            abortedByTimeout.set(true)
            neuralJob?.cancel(CancellationException("TTS_EXCEPTION"))
            consecutiveNeuralFailures++
            lastNeuralFailureTimeMs = System.currentTimeMillis()
            Log.w("TtsManager", "Exception Google Gemini TTS, repli local: ${e.message}")
            return@withContext false
        }
    }

    private fun requestPlaybackAudioFocus() {
        try {
            val am = audioManager ?: return
            if (am.mode == AudioManager.MODE_IN_COMMUNICATION) {
                am.mode = AudioManager.MODE_NORMAL
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(attrs)
                    .build()
                audioFocusRequest = req
                am.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            }
        } catch (_: Exception) {}
    }

    private fun abandonPlaybackAudioFocus() {
        try {
            val am = audioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
            }
        } catch (_: Exception) {}
    }

    private fun warmUpVoiceCache() {
        try {
            val voices = tts?.voices ?: return
            val targetLangs = listOf("fr", "en", "de", "es", "ar", "it", "pt", "ru", "zh")
            for (lang in targetLangs) {
                if (cachedVoicesByLang.containsKey(lang)) continue
                val best = voices.asSequence()
                    .filter { v ->
                        v.locale.language.equals(lang, ignoreCase = true) &&
                            !v.isNetworkConnectionRequired &&
                            !v.features.contains("notInstalled")
                    }
                    .sortedWith(
                        compareByDescending<android.speech.tts.Voice> { it.quality }
                            .thenByDescending { v ->
                                val n = v.name.lowercase(Locale.ROOT)
                                if (n.contains("frd") || n.contains("male") || n.contains("iom") || n.contains("thomas") || n.contains("cfb") || n.contains("rjd") || n.contains("gba")) 1 else 0
                            }
                    )
                    .firstOrNull()
                if (best != null) {
                    cachedVoicesByLang[lang] = best
                }
            }
        } catch (_: Exception) {}
    }

    private fun selectBestVoiceForLocale(targetLocale: Locale) {
        try {
            tts?.language = targetLocale
            val langKey = targetLocale.language.lowercase(Locale.ROOT)
            val cached = cachedVoicesByLang[langKey]
            if (cached != null) {
                tts?.voice = cached
                return
            }
            val matchingOfflineVoices = tts?.voices?.filter { v ->
                v.locale.language.equals(targetLocale.language, ignoreCase = true) &&
                    !v.isNetworkConnectionRequired &&
                    !v.features.contains("notInstalled")
            }.orEmpty()
            if (matchingOfflineVoices.isEmpty()) return

            // Sélectionne exclusivement des voix locales hors-ligne pour éviter toute attente réseau de 30s du moteur Google TTS
            val ranked = matchingOfflineVoices.sortedWith(
                compareByDescending<android.speech.tts.Voice> { it.quality }
                    .thenByDescending { v ->
                        val n = v.name.lowercase(Locale.ROOT)
                        if (n.contains("frd") || n.contains("male") || n.contains("iom") || n.contains("thomas") || n.contains("cfb") || n.contains("rjd") || n.contains("gba")) 1 else 0
                    }
            )
            ranked.firstOrNull()?.let {
                cachedVoicesByLang[langKey] = it
                tts?.voice = it
            }
        } catch (_: Exception) {}
    }

    private fun speakWithLocalAndroidTts(
        spokenText: String,
        langCode: String,
        rate: Float
    ) {
        if (!isReady) {
            if (tts == null) {
                isGlobalTtsSpeaking = false
                _ttsSoundLevel.value = 0f
                abandonPlaybackAudioFocus()
                val errCb = activeOnErrorCallback
                activeOnErrorCallback = null
                activeOnDoneCallback = null
                errCb?.invoke()
                return
            }
            isGlobalTtsSpeaking = true
            _ttsSoundLevel.value = 0f
            pendingSpeech = {
                speakWithLocalAndroidTts(spokenText, langCode, rate)
            }
            pendingInitWatchdog?.let { mainHandler.removeCallbacks(it) }
            val watchdog = Runnable {
                if (!isReady && pendingSpeech != null) {
                    pendingSpeech = null
                    isGlobalTtsSpeaking = false
                    _ttsSoundLevel.value = 0f
                    abandonPlaybackAudioFocus()
                    val errCb = activeOnErrorCallback
                    activeOnErrorCallback = null
                    activeOnDoneCallback = null
                    errCb?.invoke()
                }
            }
            pendingInitWatchdog = watchdog
            mainHandler.postDelayed(watchdog, 1_200L)
            return
        }
        try {
            requestPlaybackAudioFocus()
            val targetLocale = when (langCode) {
                "en" -> Locale.ENGLISH
                "de" -> Locale.GERMAN
                "es" -> Locale.forLanguageTag("es-ES")
                "ar" -> Locale.forLanguageTag("ar")
                "it" -> Locale.ITALIAN
                "pt" -> Locale.forLanguageTag("pt-PT")
                "ru" -> Locale.forLanguageTag("ru")
                "zh" -> Locale.SIMPLIFIED_CHINESE
                // Pour le Mooré, le Dioula et le Haoussa, le texte est déjà converti en phonétique syllabique française
                "mos", "dyu", "ha", "fr" -> Locale.FRENCH
                else -> Locale.FRENCH
            }

            selectBestVoiceForLocale(targetLocale)

            val naturalSpeed = computeNaturalSpeed(rate)
            tts?.setSpeechRate(naturalSpeed)
            tts?.setPitch(0.98f)

            val params = android.os.Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                putString(TextToSpeech.Engine.KEY_FEATURE_NETWORK_TIMEOUT_MS, "500")
                putString(TextToSpeech.Engine.KEY_FEATURE_NETWORK_RETRIES_COUNT, "0")
            }
            val utteranceId = "sam_tts_${System.nanoTime()}"
            activeUtteranceId = utteranceId
            val result = tts?.speak(ensureNaturalSentenceEnding(spokenText), TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            if (result == TextToSpeech.ERROR) {
                isGlobalTtsSpeaking = false
                abandonPlaybackAudioFocus()
                val errCb = activeOnErrorCallback
                activeOnErrorCallback = null
                activeOnDoneCallback = null
                errCb?.invoke()
            }
        } catch (_: Exception) {
            isGlobalTtsSpeaking = false
            abandonPlaybackAudioFocus()
            val errCb = activeOnErrorCallback
            activeOnErrorCallback = null
            activeOnDoneCallback = null
            errCb?.invoke()
        }
    }

    private fun prepareSpokenText(rawText: String, langCode: String): String {
        val cleaned = cleanGeneralTextForSpeech(rawText)
        if (cleaned.isBlank()) return ""
        return when (langCode) {
            "mos" -> toMooreSpokenPhonetics(cleaned)
            "dyu" -> toDioulaSpokenPhonetics(cleaned)
            "ha" -> toHausaSpokenPhonetics(cleaned)
            else -> cleaned
        }
    }

    /**
     * Moteur Phonétique & Prosodique Mooré Haute Fidélité.
     * Transforme l'orthographe écrite du Mooré en syllabes phonétiques pures
     * pour que la voix parle exactement comme un locuteur burkinabè naturel :
     * - Supprime les traductions entre parenthèses pour ne lire que le Mooré
     * - Élimine tous les symboles, astérisques, tirets et tildes (~)
     * - Remplace les pronoms d'une lettre (y -> i, m -> mam, f -> fo, d -> do, b -> ba)
     * - Convertit ʋ -> ou (jamais "vi"), ɩ -> i, ɛ -> è
     * - Applique la prononciation authentique des verbes en -ame/-dame/-lame -> -amé/-damé/-lamé
     */
    fun toMooreSpokenPhonetics(rawText: String): String {
        var text = rawText

        // 0. Si le texte contient des traductions explicatives entre parenthèses, les retirer de la lecture audio
        // pour que SAM AI parle 100% en Mooré fluide sans casser le rythme
        text = text.replace(Regex("\\([^)]*\\)"), " ")

        // 1. Nettoyage complet du markdown, des guillemets et symboles parasites (sans transformer les virgules en points)
        text = text
            .replace(Regex("[*#_~`^\\[\\]{}<>|\\\\«»\"“”•–—]"), " ")
            .replace(Regex("[:;]"), ", ")

        // 1b. Remplacement automatique de tout mot français ou anglais accidentel par du vrai Mooré parlé
        val frenchAndEnglishLeaks = listOf(
            Regex("(?i)\\bcomment\\s+ça\\s+va\\b") to "lafi bala",
            Regex("(?i)\\bça\\s+va\\s+bien\\b") to "lafi bala",
            Regex("(?i)\\bmerci\\s+beaucoup\\b") to "barka wousgô",
            Regex("(?i)\\b(bonjour|salut|hello|hi)\\b") to "né i béogô",
            Regex("(?i)\\bbonsoir\\b") to "né i zabré",
            Regex("(?i)\\b(merci|thanks|thank\\s+you)\\b") to "barka",
            Regex("(?i)\\b(d'accord|bien\\s+sûr|oui|yes|okay|ok)\\b") to "nyé",
            Regex("(?i)\\b(non|no)\\b") to "ayô",
            Regex("(?i)\\b(parce\\s+que|car|because)\\b") to "bala",
            Regex("(?i)\\b(mais|but)\\b") to "la",
            Regex("(?i)\\b(donc|alors)\\b") to "bî",
            Regex("(?i)\\b(aujourd'hui|today)\\b") to "rounda",
            Regex("(?i)\\b(demain|tomorrow)\\b") to "béogô",
            Regex("(?i)\\b(hier|yesterday)\\b") to "zamè",
            Regex("(?i)\\b(maintenant|now)\\b") to "marsa"
        )
        for ((pattern, replacement) in frenchAndEnglishLeaks) {
            text = text.replace(pattern, replacement)
        }

        // 1c. Blocage des groupes de consonnes isolés ('mp', 'mb', 'nd', 'nk', 'nt', 'ng')
        text = text
            .replace(Regex("(?i)\\bmp\\b"), "mame pa")
            .replace(Regex("(?i)\\bmb\\b"), "mame ba")
            .replace(Regex("(?i)\\bnd\\b"), "na dô")
            .replace(Regex("(?i)\\bnk\\b"), "na kô")
            .replace(Regex("(?i)\\bnt\\b"), "na tô")
            .replace(Regex("(?i)\\bng\\b"), "na gô")
            .replace(Regex("(?i)\\bm['’]?(?!ma\\b|ba+ba\\b|na\\b|data\\b|d[eé]bda\\b)([pbftkszdgvyrl])([aàâeéèêiîoôuùûʋɩɛãẽĩõũ])"), "mame $1$2")
            .replace(Regex("(?i)\\bn['’]?(?!ye\\b)([pbftkszdgvyrl])([aàâeéèêiîoôuùûʋɩɛãẽĩõũ])"), "na $1$2")

        // 2. Dictionnaire phonétique étendu des 50 audios de référence et expressions du Mooré parlé
        val exactReplacements = listOf(
            // A. Salutations & Réponses Obligatoires
            Regex("(?i)\\by[ĩi]?beo+g?o?\\s+kibar[eé]\\b") to "yibéo kibaré",
            Regex("(?i)\\byi\\s+beo+g?o?\\s+kibar[eé]\\b") to "yibéo kibaré",
            Regex("(?i)\\by[ĩi]?beo+g?\\b") to "yibéo",
            Regex("(?i)\\bnii?\\s+y?i?\\s*beo+go\\b") to "ni yibéogô",
            Regex("(?i)\\bne\\s+y?i?\\s*beo+go\\b") to "ni yibéogô",
            Regex("(?i)\\bza+br[eé]?\\s+kibar[eé]\\b") to "zaabré kibaré",
            Regex("(?i)\\bnii?\\s+za+br[eé]?\\b") to "ni zaabré",
            Regex("(?i)\\bne\\s+y?\\s*za+br[eé]?\\b") to "ni zaabré",
            Regex("(?i)\\bw[ĩi]nd?ga\\s+kibar[eé]\\b") to "winn-dga kibaré",
            Regex("(?i)\\bnii?\\s+w[ĩi]nd?ga\\b") to "ni winn-dga",
            Regex("(?i)\\bne\\s+y?\\s*w[ĩi]nd?ga\\b") to "ni winn-dga",
            Regex("(?i)\\bw[ĩi]ndg\\b") to "winn-dg",
            Regex("(?i)\\by[ʋu]ngo\\s+kibar[eé]\\b") to "youngô kibaré",
            Regex("(?i)\\bnii?\\s*y[ʋu]ngo\\b") to "ni youngô",
            Regex("(?i)\\bne\\s+y?\\s*y[ʋu]+ngo\\b") to "ni youngô",
            Regex("(?i)\\bne\\s+y\\s+k[ʋu]+ngo\\b") to "né i koungô",
            Regex("(?i)\\bne\\s+y\\s+t[ʋu]+ma\\b") to "né i touma",
            Regex("(?i)\\bwind\\s+da\\s+ganeg\\s+laafi\\b") to "wènda ganèg lafi",
            Regex("(?i)\\bw[ẽe]nd\\s+na\\s+g[ãa]neg\\s+laafi\\b") to "wènda ganèg lafi",
            Regex("(?i)\\bneew[ôo]g[ôo]\\b") to "né wôgô",
            Regex("(?i)\\bne\\s+y?\\s*wao+ngo\\b") to "né waongô",
            Regex("(?i)\\bya+\\s+so+n?ma\\b") to "ya sôma",
            Regex("(?i)\\bya+\\s+s[õo]ama\\b") to "ya sôma",
            Regex("(?i)\\blaafi\\s+bala\\b") to "lafi bala",
            Regex("(?i)\\blaafi\\s+be+me\\b") to "lafi bémé",
            Regex("(?i)\\blaafi\\b") to "lafi",
            Regex("(?i)\\bkibar[eé]\\b") to "kibaré",
            Regex("(?i)\\bw[ëẽe]nd[-\\s]*na\\s+k[ôõo][-\\s]*d\\s+ninda+r[eé]\\b") to "wènda kôd nindaaré",
            Regex("(?i)\\bninda+r[eé]\\b") to "nindaaré",
            Regex("(?i)\\bzom[-\\s]*ko+m\\b") to "zôm kôm",
            Regex("(?i)\\bs[êẽe]n\\s+na\\s+be\\b") to "sèn na bé",
            Regex("(?i)\\br[ũu]nd[ãa]\\s+kiba+ya\\b") to "rounda kibaya",

            // B. Pronoms, Famille, Personnes & Phrases
            Regex("(?i)\\bm\\s+nan\\s+gounsam\\b") to "m'na goussôm",
            Regex("(?i)\\bm\\s+na\\s+n\\s+g[ʋu]same\\b") to "m'na goussôm",
            Regex("(?i)\\bm\\s+data\\s+ligdi\\b") to "m'data ligdi",
            Regex("(?i)\\bm\\s+debda\\s+zakin\\s+wan\\b") to "m'débda zakouan",
            Regex("(?i)\\bm\\s+debda\\s+zak[ẽe]\\s+w[ãa]\\b") to "m'débda zakouan",
            Regex("(?i)\\bm\\s+debda\\s+zakin\\b") to "m'débda zakouan",
            Regex("(?i)\\bm['’\\s]*ba+ba\\b") to "m'baaba",
            Regex("(?i)\\bm['’\\s]*ma\\b") to "m'ma",
            Regex("(?i)\\bnin[-\\s]*k[ẽêe]+ma\\b") to "ninkéma",
            Regex("(?i)\\bnikieman\\b") to "ninkéma",
            Regex("(?i)\\bpoug\\s+y[ãa]+nga\\b") to "poug yanga",
            Regex("(?i)\\bp[ʋu]g[-\\s]*y[ãa]+nga\\b") to "poug yanga",
            Regex("(?i)\\bpoug\\s+sada\\b") to "pouksada",
            Regex("(?i)\\bp[ʋu]g[-\\s]*sadga\\b") to "pouksada",
            Regex("(?i)\\bp[ùʋu]g[-\\s]*roags[áa]\\b") to "pougroaksa",
            Regex("(?i)\\bkakouada\\b") to "kakouada",
            Regex("(?i)\\bka[-\\s]*koaadga\\b") to "kakouada",
            Regex("(?i)\\btandaogo\\b") to "tandaogô",
            Regex("(?i)\\brawa\\b") to "raoua",
            Regex("(?i)\\braoa\\b") to "raoua",
            Regex("(?i)\\bpaga\\b") to "paga",
            Regex("(?i)\\bbi+ga\\b") to "biga",
            Regex("(?i)\\bmaa+m\\b") to "maam",
            Regex("(?i)\\bf[õoô]+\\b") to "fô",
            Regex("(?i)\\btond\\b") to "tôndô",
            Regex("(?i)\\bt[õo]ndo?\\b") to "tôndô",

            // C. Objets, Transports, Aliments & Verbes
            Regex("(?i)\\bra+ga\\b") to "raga",
            Regex("(?i)\\bmonteur\\b") to "montèèr",
            Regex("(?i)\\bm[õo]nte+re\\b") to "montèèr",
            Regex("(?i)\\bmonbile\\b") to "môbil",
            Regex("(?i)\\bm[õo]billi\\b") to "môbil",
            Regex("(?i)\\bpnous\\b") to "pinou",
            Regex("(?i)\\bp[ĩi]nu\\b") to "pinou",
            Regex("(?i)\\bnanwoudre\\b") to "nanwoudré",
            Regex("(?i)\\bnaoodre\\b") to "naoudré",
            Regex("(?i)\\bwe+fo\\b") to "wéfô",
            Regex("(?i)\\bmoncoroni\\b") to "monkôrôni",
            Regex("(?i)\\bfo[ūu]gou\\b") to "fougou",
            Regex("(?i)\\bfu+gu\\b") to "fougou",
            Regex("(?i)\\bson\\s+ka+m\\b") to "sonkaam",
            Regex("(?i)\\bbidou\\b") to "bidou",
            Regex("(?i)\\bb[ĩi]du\\b") to "bidou",
            Regex("(?i)\\bmougne\\b") to "mouyi",
            Regex("(?i)\\bmui\\b") to "mouyi",
            Regex("(?i)\\bbeinga\\b") to "bénga",
            Regex("(?i)\\bb[ẽe]nga\\b") to "bénga",
            Regex("(?i)\\btouayga\\b") to "touayga",
            Regex("(?i)\\bb[ãa]?nga\\b") to "banga",
            Regex("(?i)\\bya+gre\\b") to "yagré",
            Regex("(?i)\\bsa+me\\b") to "samé",
            Regex("(?i)\\bs[êẽe]n\\s+ka\\s+be\\b") to "sèn ka bé",
            Regex("(?i)\\bs[ĩi]+fu\\b") to "sifou",
            Regex("(?i)\\bwesbo\\b") to "wésbô",
            Regex("(?i)\\bwese\\b") to "wéssé",
            Regex("(?i)\\by[ôõo]sgre\\b") to "yôsgré",
            Regex("(?i)\\by[âãa]gme\\b") to "yagmé",
            Regex("(?i)\\bsake\\b") to "saké",
            Regex("(?i)\\bdoge\\b") to "rôgué",
            Regex("(?i)\\broge\\b") to "rôgué",
            Regex("(?i)\\bmaa?ne\\b") to "mané",
            Regex("(?i)\\blagme\\b") to "lagmé",
            Regex("(?i)\\by[âãa]+ge\\b") to "yagué",
            Regex("(?i)\\br[ôõo]dbo\\b") to "rôdbô",
            Regex("(?i)\\br[àa]+da\\b") to "rada",
            Regex("(?i)\\bra+bo\\b") to "rabô",
            Regex("(?i)\\byagle\\b") to "yaglé",
            Regex("(?i)\\bsoge\\b") to "sôgué",
            Regex("(?i)\\bba+s[èe]\\b") to "basé",
            Regex("(?i)\\bpao+ngo\\b") to "paongô",
            Regex("(?i)\\bmanesem\\b") to "mansèm",
            Regex("(?i)\\bpa+sga\\b") to "pasga",
            Regex("(?i)\\bpa+se\\b") to "pasé",
            Regex("(?i)\\bk[êẽe]\\b") to "kè",

            // D. Autres verbes et mots usuels du Mooré
            Regex("(?i)\\bw[ũuʋ]mdame\\b") to "woumdamé",
            Regex("(?i)\\bwoumdam[eé]\\b") to "woumdamé",
            Regex("(?i)\\bw[ũuʋ]m\\b") to "woum",
            Regex("(?i)\\byeelame\\b") to "yélamé",
            Regex("(?i)\\bwaame\\b") to "ouamé",
            Regex("(?i)\\bk[ẽe]ngame\\b") to "kèngamé",
            Regex("(?i)\\bmaaname\\b") to "manamé",
            Regex("(?i)\\bnongame\\b") to "nongamé",
            Regex("(?i)\\bs[õo]ngdame\\b") to "songdamé",
            Regex("(?i)\\bt[ʋu]mdame\\b") to "toumdamé",
            Regex("(?i)\\bmiime\\b") to "mimé",
            Regex("(?i)\\bbeeme\\b") to "bémé",
            Regex("(?i)\\bgom-biiga\\b") to "gom biga",
            Regex("(?i)\\bgom-y\\b") to "gomé",
            Regex("(?i)\\bgomyi\\b") to "gomé",
            Regex("(?i)\\bkelg-y\\b") to "kèlgui",
            Regex("(?i)\\bges-y\\b") to "guèssé",
            Regex("(?i)\\bz[ĩi]nd-y\\b") to "zindî",
            Regex("(?i)\\bk[ẽe]-y\\b") to "kèy",
            Regex("(?i)\\bwa-y\\b") to "ouaï",
            Regex("(?i)\\bn['’-]?y[eéẽ]\\b") to "nyé",
            Regex("(?i)\\bk[ôo]+m\\b") to "kôm",
            Regex("(?i)\\bb[ôõ]e\\b") to "boé",
            Regex("(?i)\\bt[ôõ]e\\b") to "toé",
            Regex("(?i)\\bs[ôõ]e\\b") to "soé",
            Regex("(?i)\\bw[ôõ]e\\b") to "woé",
            Regex("(?i)\\bw[ãa]n-w[ãa]n\\b") to "wan wan",
            Regex("(?i)\\bwan\\s+wan\\b") to "wan wan",
            Regex("(?i)\\bw[ãa]na\\b") to "wana",
            Regex("(?i)\\bwoto\\b") to "wotô",
            Regex("(?i)\\bmas-mas[ãa]\\b") to "mass massa",
            Regex("(?i)\\bmars[ãa]\\b") to "marsa",
            Regex("(?i)\\by[ĩi]nga\\b") to "yinga",
            Regex("(?i)\\bs[õo]ng-f\\b") to "songô fô",
            Regex("(?i)\\bs[õo]ng-m\\b") to "songô ma",
            Regex("(?i)\\bs[õo]ng-d\\b") to "songô dô",
            Regex("(?i)\\bs[õo]ng\\s+fo\\b") to "songô fô",
            Regex("(?i)\\bs[õo]ngo\\b") to "songô",
            Regex("(?i)\\bs[õo]ng\\b") to "songô",
            Regex("(?i)\\bnong-f\\b") to "nongô fô",
            Regex("(?i)\\bnong-m\\b") to "nongô ma",
            Regex("(?i)\\bnong\\b") to "nongô",
            Regex("(?i)\\bk[ôõ]-f\\b") to "kô fô",
            Regex("(?i)\\bk[ôõ]-m\\b") to "kô ma",
            Regex("(?i)\\bk[ôõ]-d\\b") to "kô dô",
            Regex("(?i)\\br[ũu]nd[ãa]\\b") to "rounda",
            Regex("(?i)\\broundan?\\b") to "rounda",
            Regex("(?i)\\by[ãa]mba\\b") to "yamba",
            Regex("(?i)\\by[ãa]mb\\b") to "yamb",
            Regex("(?i)\\bb[ãa]mba?\\b") to "bamba",
            Regex("(?i)\\bw[ẽe]nde\\b") to "wèndé",
            Regex("(?i)\\bw[ẽe]nd\\b") to "wènd",
            Regex("(?i)\\bnaa+ba\\b") to "naba",
            Regex("(?i)\\bp[ʋu]+sda\\b") to "pousda",
            Regex("(?i)\\bw[ʋu]sgo\\b") to "wousgô",
            Regex("(?i)\\bwousgo\\b") to "wousgô",
            Regex("(?i)\\bousgo\\b") to "wousgô",
            Regex("(?i)\\bs[ʋu]ka\\b") to "souka",
            Regex("(?i)\\bs[õo]ama\\b") to "sôma",
            Regex("(?i)\\bs[ɩi]da\\b") to "sida",
            Regex("(?i)\\bt[ʋu]+ma\\b") to "touma",
            Regex("(?i)\\by[ʋu]+re\\b") to "youré",
            Regex("(?i)\\by[ʋu]+r\\b") to "youré",
            Regex("(?i)\\bf[ʋu]+go\\b") to "fougô",
            Regex("(?i)\\bk[ʋu]+ngo\\b") to "koungô",
            Regex("(?i)\\bb[ʋu]+du\\b") to "boudou",
            Regex("(?i)\\bd[ʋu]+niya\\b") to "douniya",
            Regex("(?i)\\bd[ûũu]nd[âãa]\\b") to "dounda",
            Regex("(?i)\\bb[ɩi]\\b") to "bî",
            Regex("(?i)\\bn[ɩi]+bo\\b") to "nibô",
            Regex("(?i)\\bn[ɩi]n-buiidu\\b") to "nin bouidou",
            Regex("(?i)\\bk[ẽe]ng\\b") to "kèngué",
            Regex("(?i)\\bb[ãa]ngre\\b") to "bangré",
            Regex("(?i)\\bb[ãa]ng\\b") to "bangué",
            Regex("(?i)\\bbeoog[ẽe]\\b") to "béoguè",
            Regex("(?i)\\bbeo+go\\b") to "béogô",
            Regex("(?i)\\bzaam[ẽe]\\b") to "zamè",
            Regex("(?i)\\btaoo+re\\b") to "taoré",
            Regex("(?i)\\bpore\\b") to "poré",
            Regex("(?i)\\byelle\\b") to "yèllé",
            Regex("(?i)\\byel-b[ʋu]n\\b") to "yèl boune",
            Regex("(?i)\\bmoaaga\\b") to "moaga",
            Regex("(?i)\\bmooose\\b") to "mossé",
            Regex("(?i)\\basoe\\b") to "assoé",
            Regex("(?i)\\bkaseto\\b") to "kassétô",
            Regex("(?i)\\bnasaara\\b") to "nassara",
            Regex("(?i)\\bleokre\\b") to "léokré",
            Regex("(?i)\\bgomde\\b") to "gomdé",
            Regex("(?i)\\bmam\\b") to "mame",
            Regex("(?i)\\bburkina\\s+faso\\b") to "Burkina Faso",
            Regex("(?i)\\bsam\\s+ai\\b") to "Sam",
            Regex("(?i)\\bsam\\s+a\\s+i\\b") to "Sam",
            Regex("(?i)\\bsam\\b") to "Sam"
        )

        for ((pattern, replacement) in exactReplacements) {
            text = text.replace(pattern, replacement)
        }

        // 3. Transformation des suffixes grammaticaux avec tiret (-y, -f, -d, -m, -a, -b),
        // puis séparation des mots composés à tirets (protège "winn-dga" contre les règles suivantes)
        text = text
            .replace(Regex("(?i)([a-zɩʋɛãẽĩõũéèêôâû])-y\\b"), "$1y")
            .replace(Regex("(?i)([a-zɩʋɛãẽĩõũéèêôâû])-f\\b"), "$1 fô")
            .replace(Regex("(?i)([a-zɩʋɛãẽĩõũéèêôâû])-d\\b"), "$1 dô")
            .replace(Regex("(?i)([a-zɩʋɛãẽĩõũéèêôâû])-m\\b"), "$1 ma")
            .replace(Regex("(?i)([a-zɩʋɛãẽĩõũéèêôâû])-a\\b"), "$1 a")
            .replace(Regex("(?i)([a-zɩʋɛãẽĩõũéèêôâû])-b\\b"), "$1 ba")
            .replace("winn-dga", "WNNDGA_TOKEN")
            .replace("winn-dg", "WNNDG_TOKEN")
            .replace("-", " ")

        // 4. Pronoms et mots courts en Mooré verrouillés avec accents ('fô', 'dô', 'kô', 'tô', 'sô', 'mame')
        // sans altérer les clitiques avec apostrophe (m'baaba, m'ma, m'na, m'data, m'débda)
        text = text
            .replace(Regex("(?i)(?<![a-zɩʋɛãẽĩõũ'’])y(?![a-zɩʋɛãẽĩõũ'’])"), "i")
            .replace(Regex("(?i)(?<![a-zɩʋɛãẽĩõũ'’])m(?![a-zɩʋɛãẽĩõũ'’])"), "mame")
            .replace(Regex("(?i)(?<![a-zɩʋɛãẽĩõũ'’])n(?![a-zɩʋɛãẽĩõũ'’])"), "na")
            .replace(Regex("(?i)\\bf\\b"), "fô")
            .replace(Regex("(?i)\\bd\\b"), "dô")
            .replace(Regex("(?i)\\bb\\b"), "ba")
            .replace(Regex("(?i)\\bfo\\b"), "fô")
            .replace(Regex("(?i)\\bdo\\b"), "dô")
            .replace(Regex("(?i)\\bto\\b"), "tô")
            .replace(Regex("(?i)\\bko\\b"), "kô")
            .replace(Regex("(?i)\\bso\\b"), "sô")
            .replace(Regex("(?i)\\bno\\b"), "nô")
            .replace(Regex("(?i)\\bgo\\b"), "gô")
            .replace(Regex("(?i)\\bbo\\b"), "bô")

        // 5. Diphtongues, accents et lettres spéciales de l'alphabet Mooré (ʋ, ɩ, ɛ, ë, ò, ù, û, voyelles nasales ~)
        text = text
            .replace("ë", "é").replace("Ë", "É")
            .replace("ò", "o").replace("Ò", "O")
            .replace("ù", "ou").replace("Ù", "Ou")
            .replace("ú", "ou").replace("Ú", "Ou")
            .replace("û", "ou").replace("Û", "Ou")
            .replace("á", "a").replace("Á", "A")
            .replace("í", "i").replace("Í", "I")
            .replace("îi", "î").replace("ÎI", "Î")
            .replace("êe", "è").replace("ÊE", "È")
            .replace("ên", "èn").replace("Ên", "Èn")
            .replace("êm", "èm").replace("Êm", "Èm")
            .replace("õe", "oé").replace("ÕE", "Oé")
            .replace("ôe", "oé").replace("ÔE", "Oé")
            .replace("ãe", "aé").replace("ÃE", "Aé")
            .replace("aoo", "ao").replace("AOO", "Ao")
            .replace("ʋʋ", "ou").replace("ƲƲ", "Ou")
            .replace("ʋ", "ou").replace("Ʋ", "Ou")
            .replace("ɩɩ", "i").replace("ƖƖ", "I")
            .replace("ɩ", "i").replace("Ɩ", "I")
            .replace("ɛɛ", "è").replace("ƐƐ", "È")
            .replace("ɛ", "è").replace("Ɛ", "È")
            .replace("âa", "a").replace("ÂA", "A")
            .replace("ãa", "an").replace("ÃA", "An")
            .replace("ã", "an").replace("Ã", "An")
            .replace("õo", "on").replace("ÕO", "On")
            .replace("õ", "on").replace("Õ", "On")
            .replace("ẽe", "è").replace("ẼE", "È")
            .replace("ẽ", "è").replace("Ẽ", "È")
            .replace("ũu", "ou").replace("ŨU", "Ou")
            .replace("ũ", "oun").replace("Ũ", "Oun")
            .replace("ĩi", "i").replace("ĨI", "I")
            .replace("ĩ", "in").replace("Ĩ", "In")

        // 6. Règles phonétiques et prosodiques du Mooré :
        // - Préserve "Burkina Faso" et les mots déjà verrouillés ("winn-dga", "né wôgô", "mame")
        // - Convertit 'ee+' en 'é' (évite la lecture anglaise /i:/ de "deege", "teedo")
        // - Empêche la lecture française schwa "eu" sur les 'e' ouverts non finaux (neda -> néda)
        // - Prononce systématiquement les verbes en -ame/-dame/-ome/-me (-amé/-damé/-omé)
        text = text
            .replace(Regex("(?i)eoo+"), "éo")
            .replace(Regex("(?i)eo"), "éo")
            .replace(Regex("(?i)ee+"), "é")
            .replace(Regex("(?i)aa+"), "a")
            .replace(Regex("(?i)oo+"), "o")
            .replace(Regex("(?i)ii+"), "i")
            .replace(Regex("(?i)uu+"), "u")
            .replace(Regex("(?<![oOéÉ])u"), "ou")
            .replace(Regex("(?<![oOéÉ])U"), "Ou")
            .replace(Regex("(?i)w([aàâeéèêiî])"), "ou$1")
            .replace(Regex("(?i)([bcdfghjklmnpqrstvwxz])en\\b"), "$1èn")
            .replace(Regex("(?i)em\\b"), "ème")
            .replace(Regex("(?i)em(?=[bcdfghjklpqrstvwxz])"), "èm")
            .replace(Regex("(?i)e([bcdfghjklmnpqrstvwxz]{2})"), "è$1")
            .replace(Regex("e(?=[bcdfghjklmnpqrstvwxz][aàâeéèêiîoôuùûy])"), "é")
            .replace(Regex("E(?=[bcdfghjklmnpqrstvwxz][aàâeéèêiîoôuùûy])"), "É")
            .replace(Regex("(?i)g([eéèi])"), "gu$1")
            .replace(Regex("(?i)([aeiouéèôîï])s([aeiouéèôîï])"), "$1ss$2")
            .replace(Regex("(?i)\\b(n|b|t|d|k|l|r|s|z|p|y)e\\b"), "$1é")
            .replace(Regex("(?i)([bcdfghjklnpqrstvwxz])e\\b"), "$1é")
            .replace(Regex("(?i)(?<!\\bma)([aàâeéèêiîoôuùûy])me\\b"), "$1mé")
            .replace(Regex("(?i)o\\b"), "ô")
            .replace(Regex("(?i)\\bbourkina\\s+fass?[oô]\\b"), "Burkina Faso")
            .replace(Regex("(?i)\\bburkina\\s+fass?[oô]\\b"), "Burkina Faso")
            .replace("WNNDGA_TOKEN", "winn-dga")
            .replace("WNNDG_TOKEN", "winn-dg")

        return text.replace(Regex("\\s+"), " ").trim()
    }

    /**
     * Moteur Phonétique Dioula : convertit l'orthographe Dioula (ɛ, ɔ, ɲ, ŋ, c='tch', j='dj', u='ou', N='oum/oun', e final='é')
     * en lecture naturelle fluide sans accent anglais ni syllabes avalées.
     */
    fun toDioulaSpokenPhonetics(rawText: String): String {
        var text = rawText
            .replace(Regex("\\([^)]*\\)"), " ")
            .replace(Regex("[*#_~`^\\[\\]{}<>|\\\\«»\"“”•–—]"), " ")
            .replace(Regex("[:;]"), ", ")

        val exactDioula = listOf(
            Regex("(?i)\\bi\\s+ni\\s+s[oɔ]g[oɔ]ma\\b") to "i ni sogoma",
            Regex("(?i)\\bi\\s+ni\\s+tilen\\b") to "i ni tilén",
            Regex("(?i)\\bi\\s+ni\\s+wula\\b") to "i ni woula",
            Regex("(?i)\\bi\\s+ni\\s+su\\b") to "i ni sou",
            Regex("(?i)\\bi\\s+ni\\s+ce\\b") to "i ni tché",
            Regex("(?i)\\ba\\s+ni\\s+ce\\b") to "a ni tché",
            Regex("(?i)\\baw\\s+ni\\s+ce\\b") to "aô ni tché",
            Regex("(?i)\\banitie\\b") to "a ni tché",
            Regex("(?i)\\bkossobe\\b") to "kossèbè",
            Regex("(?i)\\bkakene\\b") to "ka kènè",
            Regex("(?i)\\bthie\\b") to "tchè",
            Regex("(?i)\\bseguena\\b") to "séguéna",
            Regex("(?i)\\bkos[ɛe]b[ɛe]\\b") to "kossèbè",
            Regex("(?i)\\bk[ɛe]n[ɛe]\\b") to "kènè",
            Regex("(?i)\\bt[ɔo]{2,}r[ɔo]\\b") to "toro",
            Regex("(?i)\\bw[ɔo]{2,}r[ɔo]\\b") to "woro",
            Regex("(?i)\\bs[ɔo]{2,}ni\\b") to "soni",
            Regex("(?i)\\bs[ɔo]nna\\b") to "sonna",
            Regex("(?i)\\bk[ɔo]n[ɔo]nt[ɔo]n\\b") to "kononton",
            Regex("(?i)\\bwolonwula\\b") to "wolonwoula",
            Regex("(?i)\\bseegin\\b") to "séguin",
            Regex("(?i)\\bduuru\\b") to "dourou",
            Regex("(?i)\\bkelen\\b") to "kélèn",
            Regex("(?i)\\bk[ɛe]m[ɛe]\\b") to "kèmè",
            Regex("(?i)\\bhak[ɛe]to\\b") to "hakètô",
            Regex("(?i)\\bi\\s+dans[ɛe]\\b") to "i dansè",
            Regex("(?i)\\baw\\s+dans[ɛe]\\b") to "aô dansè",
            Regex("(?i)\\baw\\b") to "aô",
            Regex("(?i)\\bwa\\b") to "oua"
        )
        for ((pattern, replacement) in exactDioula) {
            text = text.replace(pattern, replacement)
        }

        // Pronom 1re personne "N" isolé (jamais lu comme la lettre "ènne", sans toucher à n'...)
        text = text
            .replace(Regex("(?i)(?<![a-zɛɔɲŋ'’])n\\s+(?=[bpmf])"), "oum ")
            .replace(Regex("(?i)(?<![a-zɛɔɲŋ'’])n(?![a-zɛɔɲŋ'’])"), "oun")

        // Ferme uniquement les vrais 'o' finaux avant d'ouvrir 'ɔ' -> 'o'
        text = text
            .replace(Regex("(?i)o\\b"), "ô")
            .replace("ɔɔ", "o").replace("ƆƆ", "O")
            .replace("ɔ", "o").replace("Ɔ", "O")
            .replace("ɛɛ", "è").replace("ƐƐ", "È")
            .replace("ɛ", "è").replace("Ɛ", "È")
            .replace("ɲ", "gn").replace("Ɲ", "Gn")
            .replace("ŋ", "ng").replace("Ŋ", "Ng")
            .replace(Regex("(?i)c([eéèiaou])"), "tch$1")
            .replace(Regex("(?i)j([eéèiaou])"), "dj$1")
            .replace(Regex("(?i)aa+"), "a")
            .replace(Regex("(?i)ee+"), "é")
            .replace(Regex("(?<![oO])u"), "ou")
            .replace(Regex("(?<![oO])U"), "Ou")
            .replace(Regex("(?i)w([aàâeéèêiî])"), "ou$1")
            .replace(Regex("(?i)([bcdfghjklmnpqrstvwxz])en\\b"), "$1èn")
            .replace(Regex("(?i)em\\b"), "ème")
            .replace(Regex("e(?=[bcdfghjklmnpqrstvwxz][aàâeéèêiîoôuùûy])"), "é")
            .replace(Regex("E(?=[bcdfghjklmnpqrstvwxz][aàâeéèêiîoôuùûy])"), "É")
            .replace(Regex("(?i)g([eéèi])"), "gu$1")
            .replace(Regex("(?i)([aeiouéèôî])s([aeiouéèôî])"), "$1ss$2")
            .replace(Regex("(?i)([ei])w\\b"), "$1ou")
            .replace(Regex("(?i)([bcdfghjklmnpqrstvwxz])e\\b"), "$1é")

        return text.replace(Regex("\\s+"), " ").trim()
    }

    /**
     * Moteur Phonétique Haoussa : convertit l'orthographe Haoussa (ɓ, ɗ, ƙ, ƴ, c='tch', sh='ch', j='dj', u='ou', ai='aï', au='aou')
     * en lecture orale fluide et fidèle.
     */
    fun toHausaSpokenPhonetics(rawText: String): String {
        var text = rawText
            .replace(Regex("\\([^)]*\\)"), " ")
            .replace(Regex("[*#_~`^\\[\\]{}<>|\\\\«»\"“”•–—]"), " ")
            .replace(Regex("[:;]"), ", ")

        val exactHausa = listOf(
            Regex("(?i)\\bina\\s+kwana\\b") to "ina kouana",
            Regex("(?i)\\bina\\s+wuni\\b") to "ina wouni",
            Regex("(?i)\\byaya\\s+dai\\b") to "yaya daï",
            Regex("(?i)\\blafiya\\s+lau\\b") to "lafiya laou",
            Regex("(?i)\\bnagode\\s+sosai\\b") to "nagodé sossaï",
            Regex("(?i)\\bnagode\\b") to "nagodé",
            Regex("(?i)\\bsai\\s+an\\s+jima\\b") to "saï an djima",
            Regex("(?i)\\bsai\\s+gobe\\b") to "saï gobé",
            Regex("(?i)\\bsauka\\s+lafiya\\b") to "saouka lafiya",
            Regex("(?i)\\bgafara\\s+dai\\b") to "gafara daï",
            Regex("(?i)\\bdon\\s+allah\\b") to "donne allah",
            Regex("(?i)\\bjin\\b") to "djine",
            Regex("(?i)\\bbabu\\s+matsala\\b") to "babou matssala",
            Regex("(?i)\\bnawa\\s+ne\\b") to "nawa né"
        )
        for ((pattern, replacement) in exactHausa) {
            text = text.replace(pattern, replacement)
        }

        text = text
            .replace("ɓ", "b").replace("Ɓ", "B")
            .replace("ɗ", "d").replace("Ɗ", "D")
            .replace("ƙ", "k").replace("Ƙ", "K")
            .replace("ƴ", "y").replace("Ƴ", "Y")
            .replace(Regex("(?i)sh"), "ch")
            .replace(Regex("(?i)c(?=[eéèiaou])"), "tch")
            .replace(Regex("(?i)j(?=[eéèiaou])"), "dj")
            .replace(Regex("(?i)kw([aeio])"), "kou$1")
            .replace(Regex("(?i)gw([aeio])"), "gou$1")
            .replace(Regex("(?i)ai"), "aï")
            .replace(Regex("(?i)au"), "aou")
            .replace(Regex("(?<![oOaA])u"), "ou")
            .replace(Regex("(?<![oOaA])U"), "Ou")
            .replace(Regex("(?i)([bcdfghjklmnpqrstvwxz])en\\b"), "$1èn")
            .replace(Regex("(?i)em\\b"), "ème")
            .replace(Regex("e(?=[bcdfghjklmnpqrstvwxz][aàâeéèêiîoôuùûy])"), "é")
            .replace(Regex("E(?=[bcdfghjklmnpqrstvwxz][aàâeéèêiîoôuùûy])"), "É")
            .replace(Regex("(?i)g([eéèi])"), "gu$1")
            .replace(Regex("(?i)([aeiouéèôîï])s([aeiouéèôîï])"), "$1ss$2")
            .replace(Regex("(?i)([bcdfghjklmnpqrstvwxz])e\\b"), "$1é")
            .replace(Regex("(?i)o\\b"), "ô")

        return text.replace(Regex("\\s+"), " ").trim()
    }

    private fun cleanGeneralTextForSpeech(rawText: String): String {
        return rawText
            .replace(Regex("(?i)\\bChatGPT\\b"), "Sam AI")
            .replace(Regex("(?i)\\bOpenAI\\b"), "Sam AI")
            .replace(Regex("(?i)\\b(je\\s+m'appelle|je\\s+suis|mon\\s+nom\\s+est|il\\s+s'appelle)\\s+Nova\\b"), "$1 Sam AI")
            .replace(Regex("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+"), " ")
            .replace(Regex("[\\u2600-\\u27BF]"), " ")
            .replace(Regex("[*#_~`^\\[\\]{}<>|\\\\«»\"“”•]"), " ")
            .replace(Regex("([^.!?…,;:])\\s*\\n+\\s*"), "$1, ")
            .replace(Regex("\\s*/\\s*"), ", ")
            .replace("...", ", ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun releaseMediaPlayer() {
        try {
            audioVisualizer?.enabled = false
            audioVisualizer?.release()
        } catch (_: Exception) {}
        audioVisualizer = null
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {}
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        abandonPlaybackAudioFocus()
    }

    fun stop() {
        try {
            activeSpeechGeneration.incrementAndGet()
            activeUtteranceId = null
            if (isNeuralPlaying || isGlobalTtsSpeaking || (tts?.isSpeaking == true)) {
                lastGlobalTtsEndTimeMs = System.currentTimeMillis()
            }
            isGlobalTtsSpeaking = false
            _ttsSoundLevel.value = 0f
            isNeuralPlaying = false
            pendingSpeech = null
            speechJob?.cancel()
            speechJob = null
            activeOnDoneCallback = null
            activeOnErrorCallback = null
            activeOnStartCallback = null
            try {
                activeAudioTrack?.let { t ->
                    if (t.state == AudioTrack.STATE_INITIALIZED) {
                        t.pause()
                        t.flush()
                    }
                }
            } catch (_: Exception) {}
            releaseMediaPlayer()
            tts?.stop()
        } catch (_: Exception) {}
    }

    fun shutdown() {
        try {
            stop()
            tts?.shutdown()
            tts = null
            isReady = false
        } catch (_: Exception) {}
    }
}
