package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.audiofx.Visualizer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.data.local.ApiKeyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

class TtsManager(context: Context) : TextToSpeech.OnInitListener {
    private val appContext: Context = context.applicationContext
    private var tts: TextToSpeech? = null
    private var mediaPlayer: MediaPlayer? = null
    private var audioVisualizer: Visualizer? = null
    private val audioManager by lazy {
        appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }
    private var audioFocusRequest: AudioFocusRequest? = null
    private var isReady = false
    private var isNeuralPlaying = false
    private var currentSpeechRate: Float = 1.0f

    private val _ttsSoundLevel = MutableStateFlow(0f)
    val ttsSoundLevel: StateFlow<Float> = _ttsSoundLevel.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var speechJob: Job? = null
    private val apiKeyManager by lazy { ApiKeyManager(appContext) }

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6000, TimeUnit.MILLISECONDS)
            .readTimeout(12000, TimeUnit.MILLISECONDS)
            .callTimeout(15000, TimeUnit.MILLISECONDS)
            .build()
    }

    companion object {
        // Voix masculine la plus humaine, chaleureuse et naturelle recommandée par OpenAI pour gpt-4o-mini-tts
        private const val PRIMARY_TTS_MODEL = "gpt-4o-mini-tts"
        private const val UNIFIED_OPENAI_MALE_VOICE = "cedar"
        // Repli haute définition uniquement si gpt-4o-mini-tts est indisponible
        private const val FALLBACK_TTS_MODEL = "tts-1-hd"
        private const val FALLBACK_TTS1_MALE_VOICE = "onyx"
        private const val CACHE_PREFIX = "sam_tts_v5_"

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

    init {
        activeInstances.add(this)
        try {
            tts = TextToSpeech(appContext, this)
        } catch (_: Exception) {
            isReady = false
        }
        // Purge les anciens fichiers audio mis en cache avant la mise à niveau vocale v4
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

    private fun getCachedVoiceFile(spokenText: String, langCode: String, speed: Float): File {
        val dir = File(appContext.cacheDir, "tts_voice_cache").apply { if (!exists()) mkdirs() }
        val speedTag = "%.2f".format(Locale.US, speed)
        val normalized = "${langCode}_${UNIFIED_OPENAI_MALE_VOICE}_${speedTag}_${spokenText.lowercase(Locale.ROOT).trim()}"
        val h1 = normalized.hashCode().toUInt().toString(16)
        val h2 = normalized.reversed().hashCode().toUInt().toString(16)
        return File(dir, "${CACHE_PREFIX}${langCode}_${UNIFIED_OPENAI_MALE_VOICE}_${h1}_$h2.mp3")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            try {
                selectBestVoiceForLocale(Locale.FRENCH)
                tts?.setPitch(0.98f)
                tts?.setSpeechRate(1.0f)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        isGlobalTtsSpeaking = true
                        SpeechRecognitionManager.pauseAllActiveRecognizersForTts()
                        mainHandler.post {
                            activeOnStartCallback?.invoke()
                        }
                    }

                    override fun onAudioAvailable(utteranceId: String?, audio: ByteArray?) {
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
                val pending = pendingSpeech
                pendingSpeech = null
                pending?.invoke()
            } catch (_: Exception) {
                isReady = false
            }
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
        isGlobalTtsSpeaking = true
        SpeechRecognitionManager.pauseAllActiveRecognizersForTts()

        speechJob = scope.launch {
            var apiKey = apiKeyManager.getActiveOpenAiApiKey()
            if (apiKey.isBlank() || apiKey == "MY_OPENAI_API_KEY") {
                apiKey = ApiKeyManager.forceRefreshOpenAiKeyFromServer()
            }
            val success = if (apiKey.isNotBlank() && apiKey != "MY_OPENAI_API_KEY") {
                trySpeakWithNeuralVoice(spokenText, apiKey, langCode, rate)
            } else {
                Log.w("TtsManager", "Clé OpenAI absente, repli vers TTS local Android")
                false
            }
            if (!success) {
                withContext(Dispatchers.Main) {
                    speakWithLocalAndroidTts(spokenText, langCode, rate)
                }
            }
        }
    }

    private fun buildHumanVoiceInstructions(langCode: String): String {
        return when (langCode) {
            "en" -> "Speak in warm, relaxed everyday English, like a real person talking casually face-to-face with a friend. Natural conversational rhythm, subtle breathing, effortless phrasing, and gentle falling inflection."
            "es" -> "Habla en español natural y cercano, como una persona real conversando tranquilamente con un amigo. Voz cálida, ritmo fluido, vocales claras e inflexión humana espontánea."
            "de" -> "Sprich natürliches, entspanntes Alltagsdeutsch wie ein echter Mensch im ruhigen Gespräch mit einem Freund. Warme Stimme, natürliche Betonung und fließender Sprechrhythmus."
            "ar" -> "تحدث بعربية واضحة وطبيعية ودافئة مثل إنسان حقيقي يتحدث بهدوء مع صديق، بنبرة مريحة وإيقاع كلام طبيعي غير متكلف."
            "it" -> "Parla in italiano naturale, caldo e rilassato, come una persona vera che chiacchiera tranquillamente con un amico. Cadenza fluida e intonazione spontanea."
            "pt" -> "Fale em português natural, caloroso e descontraído, como uma pessoa real conversando com um amigo. Ritmo fluido e entonação humana espontânea."
            "ru" -> "Говори живым, тёплым и естественным разговорным русским языком, как реальный человек в спокойной беседе с другом, с естественными паузами и интонацией."
            "zh" -> "用温暖、自然、放松的日常普通话说话，就像真人和朋友面对面轻松聊天一样，语调自然流畅，没有播音腔。"
            "mos" -> "Lis cette transcription phonétique française à voix haute comme un locuteur burkinabè calme et chaleureux qui parle naturellement en mooré dans la vie de tous les jours. Prononce chaque voyelle clairement selon l'orthographe française (ou, é, è, ô, an, on), avec un rythme détendu, humain et fluide."
            "dyu" -> "Lis cette transcription phonétique française à voix haute comme un locuteur ouest-africain calme et chaleureux qui parle naturellement en dioula dans une vraie conversation. Prononce chaque voyelle et consonne clairement selon l'orthographe française (ou, é, è, ô, gn, tch, dj), avec un rythme posé et humain."
            "ha" -> "Lis cette transcription phonétique française à voix haute comme un locuteur ouest-africain calme et chaleureux qui parle naturellement en haoussa dans la vie quotidienne. Prononce clairement chaque voyelle et consonne selon l'orthographe française (ou, é, è, tch, ch, dj), avec une intonation humaine détendue."
            else -> "Parle naturellement comme une vraie personne francophone qui discute calmement avec un ami. Voix masculine posée, chaude et détendue, liaisons naturelles, légères respirations et intonation fluide."
        }
    }

    private fun computeNaturalSpeed(userRate: Float): Float {
        // Conserve 1.0f par défaut pour préserver le timbre organique sans étirement temporel artificiel
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

    private data class NeuralAudioResult(
        val bytes: ByteArray,
        val isPrimaryModel: Boolean
    )

    private suspend fun synthesizeNeuralBytes(
        spokenText: String,
        initialApiKey: String,
        langCode: String,
        speed: Float
    ): NeuralAudioResult? = withContext(Dispatchers.IO) {
        val naturalSpeed = computeNaturalSpeed(speed)
        val instructions = buildHumanVoiceInstructions(langCode)
        val ttsInput = ensureNaturalSentenceEnding(spokenText).take(2500)
        var activeKey = initialApiKey

        val primaryPayload = buildJsonObject {
            put("model", PRIMARY_TTS_MODEL)
            put("input", ttsInput)
            put("voice", UNIFIED_OPENAI_MALE_VOICE)
            put("instructions", instructions)
            put("response_format", "mp3")
            put("speed", naturalSpeed)
        }.toString()

        var response = requestOpenAiAudioBytes(primaryPayload, activeKey, PRIMARY_TTS_MODEL)
        if (response.httpCode == 401 || response.httpCode == 403) {
            val refreshed = ApiKeyManager.forceRefreshOpenAiKeyFromServer()
            if (refreshed.isNotBlank() && refreshed != activeKey) {
                activeKey = refreshed
                response = requestOpenAiAudioBytes(primaryPayload, activeKey, PRIMARY_TTS_MODEL)
            }
        }
        if (response.bytes != null && response.bytes.size >= 200) {
            return@withContext NeuralAudioResult(response.bytes, isPrimaryModel = true)
        }

        // Repli HD (tts-1-hd + onyx) sans polluer le cache principal
        val fallbackPayload = buildJsonObject {
            put("model", FALLBACK_TTS_MODEL)
            put("input", ttsInput)
            put("voice", FALLBACK_TTS1_MALE_VOICE)
            put("response_format", "mp3")
            put("speed", naturalSpeed)
        }.toString()
        val fallbackResp = requestOpenAiAudioBytes(fallbackPayload, activeKey, FALLBACK_TTS_MODEL)
        if (fallbackResp.bytes != null && fallbackResp.bytes.size >= 200) {
            return@withContext NeuralAudioResult(fallbackResp.bytes, isPrimaryModel = false)
        }
        null
    }

    private suspend fun trySpeakWithNeuralVoice(
        spokenText: String,
        apiKey: String,
        langCode: String,
        rate: Float
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val naturalSpeed = computeNaturalSpeed(rate)
            val cachedFile = getCachedVoiceFile(spokenText, langCode, naturalSpeed)
            val audioFileToPlay: File

            if (cachedFile.exists() && cachedFile.length() >= 200) {
                audioFileToPlay = cachedFile
            } else {
                val result = kotlinx.coroutines.withTimeoutOrNull(18000L) {
                    synthesizeNeuralBytes(spokenText, apiKey, langCode, rate)
                } ?: return@withContext false

                if (result.isPrimaryModel) {
                    FileOutputStream(cachedFile).use { it.write(result.bytes) }
                    audioFileToPlay = cachedFile
                } else {
                    val tempFallback = File(appContext.cacheDir, "tts_fallback_tmp.mp3")
                    FileOutputStream(tempFallback).use { it.write(result.bytes) }
                    audioFileToPlay = tempFallback
                }
            }

            withContext(Dispatchers.Main) {
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
                            speakWithLocalAndroidTts(spokenText, langCode, rate)
                            true
                        }
                        prepareAsync()
                    }
                } catch (e: Exception) {
                    Log.w("TtsManager", "MediaPlayer init exception: ${e.message}")
                    isNeuralPlaying = false
                    releaseMediaPlayer()
                    speakWithLocalAndroidTts(spokenText, langCode, rate)
                }
            }
            return@withContext true
        } catch (e: Exception) {
            Log.w("TtsManager", "Exception Neural TTS, repli local: ${e.message}")
            return@withContext false
        }
    }

    private data class OpenAiAudioResponse(
        val bytes: ByteArray? = null,
        val httpCode: Int = 0
    )

    private fun requestOpenAiAudioBytes(
        payloadJson: String,
        apiKey: String,
        modelLabel: String
    ): OpenAiAudioResponse {
        return try {
            val request = Request.Builder()
                .url("https://api.openai.com/v1/audio/speech")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(payloadJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = try { response.body?.string()?.take(200) } catch (_: Exception) { null }
                    Log.w("TtsManager", "OpenAI TTS $modelLabel HTTP ${response.code}: ${errBody ?: response.message}")
                    return OpenAiAudioResponse(null, response.code)
                }
                OpenAiAudioResponse(response.body?.bytes(), response.code)
            }
        } catch (e: Exception) {
            Log.w("TtsManager", "OpenAI TTS $modelLabel network error: ${e.message}")
            OpenAiAudioResponse(null, 0)
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

    private fun selectBestVoiceForLocale(targetLocale: Locale) {
        try {
            tts?.language = targetLocale
            val matchingVoices = tts?.voices?.filter { v ->
                v.locale.language.equals(targetLocale.language, ignoreCase = true) &&
                    !v.features.contains("notInstalled")
            }.orEmpty()
            if (matchingVoices.isEmpty()) return

            // En repli local (souvent hors ligne), privilégier les voix installées de haute qualité sans dépendance réseau bloquante
            val ranked = matchingVoices.sortedWith(
                compareBy<android.speech.tts.Voice> { if (it.isNetworkConnectionRequired) 1 else 0 }
                    .thenByDescending { it.quality }
                    .thenByDescending { v ->
                        val n = v.name.lowercase(Locale.ROOT)
                        if (n.contains("frd") || n.contains("male") || n.contains("iom") || n.contains("thomas") || n.contains("cfb") || n.contains("rjd") || n.contains("gba")) 1 else 0
                    }
            )
            ranked.firstOrNull()?.let { tts?.voice = it }
        } catch (_: Exception) {}
    }

    private fun speakWithLocalAndroidTts(
        spokenText: String,
        langCode: String,
        rate: Float
    ) {
        if (!isReady) {
            pendingSpeech = {
                speakWithLocalAndroidTts(spokenText, langCode, rate)
            }
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
            }
            val utteranceId = "sam_tts_${System.currentTimeMillis()}"
            val result = tts?.speak(ensureNaturalSentenceEnding(spokenText), TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            if (result == TextToSpeech.ERROR) {
                isGlobalTtsSpeaking = false
                abandonPlaybackAudioFocus()
                activeOnErrorCallback?.invoke()
            }
        } catch (_: Exception) {
            isGlobalTtsSpeaking = false
            abandonPlaybackAudioFocus()
            activeOnErrorCallback?.invoke()
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
            if (isNeuralPlaying || isGlobalTtsSpeaking || (tts?.isSpeaking == true)) {
                lastGlobalTtsEndTimeMs = System.currentTimeMillis()
            }
            isGlobalTtsSpeaking = false
            _ttsSoundLevel.value = 0f
            speechJob?.cancel()
            speechJob = null
            isNeuralPlaying = false
            activeOnDoneCallback = null
            activeOnErrorCallback = null
            activeOnStartCallback = null
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
