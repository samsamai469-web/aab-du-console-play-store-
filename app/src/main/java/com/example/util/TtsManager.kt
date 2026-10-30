package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
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

class TtsManager(context: Context) : TextToSpeech.OnInitListener {
    private val appContext: Context = context.applicationContext
    private var tts: TextToSpeech? = null
    private var mediaPlayer: MediaPlayer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var isReady = false
    private var isNeuralPlaying = false
    private var currentSpeechRate: Float = 1.0f

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var speechJob: Job? = null
    private val apiKeyManager by lazy { ApiKeyManager(appContext) }

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(2200, TimeUnit.MILLISECONDS)
            .readTimeout(2500, TimeUnit.MILLISECONDS)
            .callTimeout(2800, TimeUnit.MILLISECONDS)
            .build()
    }

    private var activeOnDoneCallback: (() -> Unit)? = null
    private var activeOnErrorCallback: (() -> Unit)? = null
    private var activeOnStartCallback: (() -> Unit)? = null
    private var pendingSpeech: (() -> Unit)? = null

    init {
        try {
            tts = TextToSpeech(appContext, this)
        } catch (_: Exception) {
            isReady = false
        }
        prewarmEssentialMooreAudioCache()
    }

    private fun getCachedVoiceFile(spokenText: String, isMoore: Boolean): File {
        val dir = File(appContext.cacheDir, "tts_voice_cache").apply { if (!exists()) mkdirs() }
        val key = "${isMoore}_${spokenText.lowercase(Locale.ROOT).trim()}".hashCode().toUInt().toString(16)
        return File(dir, "sam_tts_v4_$key.mp3")
    }

    private fun prewarmEssentialMooreAudioCache() {
        scope.launch(Dispatchers.IO) {
            try {
                val apiKey = apiKeyManager.getActiveOpenAiApiKey()
                if (apiKey.isBlank() || apiKey == "MY_OPENAI_API_KEY") return@launch
                val essentialReplies = listOf(
                    "Yibeo kibare",
                    "Zabre kibare",
                    "Windga kibare",
                    "Yungo kibare",
                    "Laafi bala ! Ya sonma.",
                    "Wẽnd na kõ-d laafi !",
                    "Barka ! Laafi bala ?",
                    "Wënd-na kô-d nindaare !",
                    "Amiina ! Wind da ganeg laafi.",
                    "Kaseto ka be ye ! Mam pʋsda fo.",
                    "Amiina ! Nindaaré.",
                    "Wind da ganeg laafi !",
                    "Wẽnd na taase laafi ! Nindaaré.",
                    "Wẽnd na kõ-f ligdi la laafi !",
                    "Rũndã kibaaya yaa sõama Burkina Faso pʋgẽ ! Laafi beeme.",
                    "Mam yʋʋre la Same ! Maam la fo zoodo.",
                    "Maam sõngda fõ ne Mooré la Burkina Faso kibaya !",
                    "Burkina Faso yaa tẽng sõama ! Tond nongda Burkina Faso wʋsgo.",
                    "N-ye, maam la fo zoodo Same.",
                    "N-ye, fõ yaa mam zoodo !",
                    "N-ye, tond be laafi bala !",
                    "Wẽnd na kõ m'ma laafi !",
                    "Wẽnd na kõ m'baaba laafi !",
                    "Zom-kom noomame wʋsgo !"
                )
                for (batch in essentialReplies.chunked(6)) {
                    kotlinx.coroutines.coroutineScope {
                        batch.map { phrase ->
                            launch(Dispatchers.IO) {
                                try {
                                    val spoken = toMooreSpokenPhonetics(phrase)
                                    val cached = getCachedVoiceFile(spoken, true)
                                    if (!cached.exists() || cached.length() < 200) {
                                        val bytes = synthesizeNeuralBytes(spoken, apiKey, true, 1.08f)
                                        if (bytes != null && bytes.size >= 200) {
                                            FileOutputStream(cached).use { it.write(bytes) }
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            try {
                tts?.language = Locale.FRENCH
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        mainHandler.post {
                            activeOnStartCallback?.invoke()
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        val callback = activeOnDoneCallback
                        activeOnDoneCallback = null
                        mainHandler.post {
                            callback?.invoke()
                        }
                    }

                    override fun onError(utteranceId: String?) {
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

        val isMoore = language?.lowercase(Locale.ROOT)?.let {
            it.contains("moor") || it.contains("dioul")
        } ?: false

        // Conversion phonétique avancée : élimine les symboles/accents/lettres isolées ("vi", "i grec", "tilde")
        // et applique la phonétique authentique du Mooré parlé au Burkina Faso
        val spokenText = if (isMoore) {
            toMooreSpokenPhonetics(text)
        } else {
            cleanGeneralTextForSpeech(text)
        }

        if (spokenText.isBlank()) {
            onError?.invoke()
            return
        }

        activeOnStartCallback = onStart
        activeOnDoneCallback = onDone
        activeOnErrorCallback = onError

        val apiKey = apiKeyManager.getActiveOpenAiApiKey()
        if (apiKey.isNotBlank() && apiKey != "MY_OPENAI_API_KEY") {
            speechJob = scope.launch {
                val success = trySpeakWithNeuralVoice(spokenText, apiKey, isMoore, rate)
                if (!success) {
                    withContext(Dispatchers.Main) {
                        speakWithLocalAndroidTts(spokenText, language, rate)
                    }
                }
            }
        } else {
            speakWithLocalAndroidTts(spokenText, language, rate)
        }
    }

    private suspend fun synthesizeNeuralBytes(
        spokenText: String,
        apiKey: String,
        isMoore: Boolean,
        speed: Float
    ): ByteArray? = withContext(Dispatchers.IO) {
        // Priorité absolue : Modèle temps réel ultra-rapide `tts-1` (~350 ms) lisant directement
        // notre transcription phonétique ouest-africaine (`toMooreSpokenPhonetics`) sans délai LLM
        val fastPayload = buildJsonObject {
            put("model", "tts-1")
            put("input", spokenText.take(2500))
            put("voice", "nova")
            put("response_format", "mp3")
            put("speed", speed)
        }.toString()

        var audioBytes = requestOpenAiAudioBytes(fastPayload, apiKey)

        if (audioBytes == null) {
            val fallbackPayload = buildJsonObject {
                put("model", "gpt-4o-mini-tts")
                put("input", spokenText.take(2500))
                put("voice", "coral")
                put("response_format", "mp3")
                put("speed", speed)
                if (isMoore) {
                    put(
                        "instructions",
                        "Locuteur burkinabè natif parlant en langue Mooré. Prononciation ouest-africaine franche, zéro anglais, zéro français."
                    )
                }
            }.toString()
            audioBytes = requestOpenAiAudioBytes(fallbackPayload, apiKey)
        }
        audioBytes
    }

    private suspend fun trySpeakWithNeuralVoice(
        spokenText: String,
        apiKey: String,
        isMoore: Boolean,
        rate: Float
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val speed = (rate * 1.08f).coerceIn(1.0f, 1.45f)
            val cachedFile = getCachedVoiceFile(spokenText, isMoore)

            if (!cachedFile.exists() || cachedFile.length() < 200) {
                // Délai strict de 1.35s max : si le réseau est lent, on bascule immédiatement sur la voix locale (0 ms)
                // tout en téléchargeant l'audio neuronal en arrière-plan pour la prochaine fois !
                val audioBytes = kotlinx.coroutines.withTimeoutOrNull(1350L) {
                    synthesizeNeuralBytes(spokenText, apiKey, isMoore, speed)
                }
                if (audioBytes == null || audioBytes.size < 200) {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val bgBytes = synthesizeNeuralBytes(spokenText, apiKey, isMoore, speed)
                            if (bgBytes != null && bgBytes.size >= 200) {
                                FileOutputStream(cachedFile).use { it.write(bgBytes) }
                            }
                        } catch (_: Exception) {}
                    }
                    return@withContext false
                }
                FileOutputStream(cachedFile).use { it.write(audioBytes) }
            }

            withContext(Dispatchers.Main) {
                try {
                    releaseMediaPlayer()
                    isNeuralPlaying = true
                    mediaPlayer = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        setDataSource(cachedFile.absolutePath)
                        setVolume(1.0f, 1.0f)
                        setOnPreparedListener { mp ->
                            try {
                                loudnessEnhancer = LoudnessEnhancer(mp.audioSessionId).apply {
                                    setTargetGain(450)
                                    enabled = true
                                }
                            } catch (_: Exception) {}
                            activeOnStartCallback?.invoke()
                            mp.start()
                        }
                        setOnCompletionListener {
                            isNeuralPlaying = false
                            releaseMediaPlayer()
                            val cb = activeOnDoneCallback
                            activeOnDoneCallback = null
                            cb?.invoke()
                        }
                        setOnErrorListener { _, _, _ ->
                            isNeuralPlaying = false
                            releaseMediaPlayer()
                            cachedFile.delete()
                            val errCb = activeOnErrorCallback
                            activeOnErrorCallback = null
                            errCb?.invoke()
                            true
                        }
                        prepareAsync()
                    }
                } catch (e: Exception) {
                    isNeuralPlaying = false
                    speakWithLocalAndroidTts(spokenText, if (isMoore) "mooré" else "Français", rate)
                }
            }
            return@withContext true
        } catch (e: Exception) {
            Log.w("TtsManager", "Exception Neural TTS, repli local: ${e.message}")
            return@withContext false
        }
    }

    private fun requestOpenAiAudioBytes(payloadJson: String, apiKey: String): ByteArray? {
        return try {
            val request = Request.Builder()
                .url("https://api.openai.com/v1/audio/speech")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(payloadJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.bytes()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun speakWithLocalAndroidTts(
        spokenText: String,
        language: String?,
        rate: Float
    ) {
        if (!isReady) {
            pendingSpeech = {
                speakWithLocalAndroidTts(spokenText, language, rate)
            }
            return
        }
        try {
            if (language != null) {
                val targetLocale = when (language.lowercase(Locale.ROOT)) {
                    "anglais", "english", "en" -> Locale.ENGLISH
                    "allemand", "german", "deutsch", "de" -> Locale.GERMAN
                    "espagnol", "spanish", "es" -> Locale("es", "ES")
                    "arabe", "arabic", "ar" -> Locale("ar")
                    "haoussa", "hausa", "ha" -> {
                        val hausaLoc = Locale("ha")
                        if (tts?.isLanguageAvailable(hausaLoc) in listOf(
                                TextToSpeech.LANG_AVAILABLE,
                                TextToSpeech.LANG_COUNTRY_AVAILABLE
                            )
                        ) {
                            hausaLoc
                        } else {
                            Locale.FRENCH
                        }
                    }
                    "mooré", "moore", "dioula" -> {
                        tts?.setSpeechRate((rate * 1.06f).coerceIn(0.85f, 1.6f))
                        tts?.setPitch(1.08f)
                        Locale.FRENCH
                    }
                    else -> Locale.FRENCH
                }
                if (language.lowercase(Locale.ROOT) !in listOf("mooré", "moore", "dioula")) {
                    tts?.setSpeechRate((rate * 1.05f).coerceIn(0.85f, 1.6f))
                    tts?.setPitch(1.08f)
                }
                tts?.language = targetLocale
            } else {
                tts?.setSpeechRate((rate * 1.05f).coerceIn(0.85f, 1.6f))
                tts?.setPitch(1.08f)
            }

            val params = android.os.Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            }
            val utteranceId = "sam_tts_${System.currentTimeMillis()}"
            tts?.speak(spokenText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        } catch (_: Exception) {
            activeOnErrorCallback?.invoke()
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
            Regex("(?i)\\bmerci\\s+beaucoup\\b") to "barka ousgô",
            Regex("(?i)\\b(bonjour|salut|hello|hi)\\b") to "né i béogô",
            Regex("(?i)\\bbonsoir\\b") to "né i zabré",
            Regex("(?i)\\b(merci|thanks|thank\\s+you)\\b") to "barka",
            Regex("(?i)\\b(d'accord|bien\\s+sûr|oui|yes|okay|ok)\\b") to "n-yé",
            Regex("(?i)\\b(non|no)\\b") to "ayô",
            Regex("(?i)\\b(parce\\s+que|car|because)\\b") to "bala",
            Regex("(?i)\\b(mais|but)\\b") to "la",
            Regex("(?i)\\b(donc|alors)\\b") to "bî",
            Regex("(?i)\\b(aujourd'hui|today)\\b") to "roun-da",
            Regex("(?i)\\b(demain|tomorrow)\\b") to "béogô",
            Regex("(?i)\\b(hier|yesterday)\\b") to "zamè",
            Regex("(?i)\\b(maintenant|now)\\b") to "marsa"
        )
        for ((pattern, replacement) in frenchAndEnglishLeaks) {
            text = text.replace(pattern, replacement)
        }

        // 1c. Blocage des groupes de consonnes isolés ('mp', 'mb', 'nd', 'nk', 'nt', 'ng')
        // tout en préservant M'ma, M'baaba, M nan gounsam, M data ligdi, M debda zakin wan
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
        // Utilisation de 'ô' et 'î' pour verrouiller la phonologie hors de l'anglais ('do', 'fo', 'to', 'so', 'go', 'round')
        val exactReplacements = listOf(
            // A. Salutations & Réponses Obligatoires (Audios 1 à 13)
            Regex("(?i)\\by[ĩi]?beo+g?o?\\s+kibar[eé]\\b") to "yibéo kibaré",
            Regex("(?i)\\byi\\s+beo+g?o?\\s+kibar[eé]\\b") to "yibéo kibaré",
            Regex("(?i)\\by[ĩi]?beo+g?\\b") to "yibéo",
            Regex("(?i)\\bnii?\\s+y?i?\\s*beo+go\\b") to "nii yibéogô",
            Regex("(?i)\\bne\\s+y?i?\\s*beo+go\\b") to "nii yibéogô",
            Regex("(?i)\\bza+br[eé]?\\s+kibar[eé]\\b") to "zaabré kibaré",
            Regex("(?i)\\bnii?\\s+za+br[eé]?\\b") to "nii zaabré",
            Regex("(?i)\\bne\\s+y?\\s*za+br[eé]?\\b") to "nii zaabré",
            Regex("(?i)\\bw[ĩi]nd?ga\\s+kibar[eé]\\b") to "ouin-nga kibaré",
            Regex("(?i)\\bnii?\\s+w[ĩi]nd?ga\\b") to "nii ouin-nga",
            Regex("(?i)\\bne\\s+y?\\s*w[ĩi]nd?ga\\b") to "nii ouin-nga",
            Regex("(?i)\\by[ʋu]ngo\\s+kibar[eé]\\b") to "youn-gô kibaré",
            Regex("(?i)\\bnii?\\s*y[ʋu]ngo\\b") to "nii youn-gô",
            Regex("(?i)\\bne\\s+y?\\s*y[ʋu]+ngo\\b") to "nii youn-gô",
            Regex("(?i)\\bne\\s+y\\s+k[ʋu]+ngo\\b") to "né i koungô",
            Regex("(?i)\\bne\\s+y\\s+t[ʋu]+ma\\b") to "né i touma",
            Regex("(?i)\\bwind\\s+da\\s+ganeg\\s+laafi\\b") to "ouènda ganag lafi",
            Regex("(?i)\\bw[ẽe]nd\\s+na\\s+g[ãa]neg\\s+laafi\\b") to "ouènda ganag lafi",
            Regex("(?i)\\bneew[ôo]g[ôo]\\b") to "né oua-ouon-gô",
            Regex("(?i)\\bne\\s+y?\\s*wao+ngo\\b") to "né oua-ouon-gô",
            Regex("(?i)\\bya+\\s+so+n?ma\\b") to "ya sôma",
            Regex("(?i)\\bya+\\s+s[õo]ama\\b") to "ya sôma",
            Regex("(?i)\\blaafi\\s+bala\\b") to "lafi bala",
            Regex("(?i)\\blaafi\\s+be+me\\b") to "lafi béémé",
            Regex("(?i)\\blaafi\\b") to "lafi",
            Regex("(?i)\\bkibar[eé]\\b") to "kibaré",
            Regex("(?i)\\bw[ëẽe]nd[-\\s]*na\\s+k[ôõo][-\\s]*d\\s+ninda+r[eé]\\b") to "ouènda kôd nin-daaré",
            Regex("(?i)\\bninda+r[eé]\\b") to "nin-daaré",
            Regex("(?i)\\bzom[-\\s]*ko+m\\b") to "zôm-kôôm",
            Regex("(?i)\\bs[êẽe]n\\s+na\\s+be\\b") to "sèn na bé",
            Regex("(?i)\\br[ũu]nd[ãa]\\s+kiba+ya\\b") to "roun-da kibaya",

            // B. Pronoms, Famille, Personnes & Phrases des Audios (Audios 14 à 29)
            Regex("(?i)\\bm\\s+nan\\s+gounsam\\b") to "m'na goussôm",
            Regex("(?i)\\bm\\s+na\\s+n\\s+g[ʋu]same\\b") to "m'na goussôm",
            Regex("(?i)\\bm\\s+data\\s+ligdi\\b") to "m'data lig-di",
            Regex("(?i)\\bm\\s+debda\\s+zakin\\s+wan\\b") to "m'débda za-kouan",
            Regex("(?i)\\bm\\s+debda\\s+zak[ẽe]\\s+w[ãa]\\b") to "m'débda za-kouan",
            Regex("(?i)\\bm\\s+debda\\s+zakin\\b") to "m'débda za-kouan",
            Regex("(?i)\\bm['’\\s]*ba+ba\\b") to "m'baaba",
            Regex("(?i)\\bm['’\\s]*ma\\b") to "m'ma",
            Regex("(?i)\\bnin[-\\s]*k[ẽêe]+ma\\b") to "nin-kééma",
            Regex("(?i)\\bnikieman\\b") to "nin-kééma",
            Regex("(?i)\\bpoug\\s+ya+nga\\b") to "pou-gnan-ga",
            Regex("(?i)\\bp[ʋu]g[-\\s]*y[ãa]+nga\\b") to "pou-gnan-ga",
            Regex("(?i)\\bpoug\\s+sada\\b") to "pouk-sada",
            Regex("(?i)\\bp[ʋu]g[-\\s]*sadga\\b") to "pouk-sada",
            Regex("(?i)\\bp[ùʋu]g[-\\s]*roags[áa]\\b") to "poug-roak-sa",
            Regex("(?i)\\bkakouada\\b") to "ka-koua-da",
            Regex("(?i)\\bka[-\\s]*koaadga\\b") to "ka-koua-da",
            Regex("(?i)\\btandaogo\\b") to "tan-da-ougô",
            Regex("(?i)\\brawa\\b") to "ra-oua",
            Regex("(?i)\\braoa\\b") to "ra-oua",
            Regex("(?i)\\bpaga\\b") to "paga",
            Regex("(?i)\\bbi+ga\\b") to "bii-ga",
            Regex("(?i)\\bmaa+m\\b") to "maam",
            Regex("(?i)\\bf[õoô]+\\b") to "fô",
            Regex("(?i)\\btond\\b") to "tôn-dô",
            Regex("(?i)\\bt[õo]ndo?\\b") to "tôn-dô",

            // C. Objets, Transports, Aliments & Verbes des Audios (Audios 30 à 50)
            Regex("(?i)\\bra+ga\\b") to "raa-ga",
            Regex("(?i)\\bmonteur\\b") to "mon-tèèr",
            Regex("(?i)\\bm[õo]nte+re\\b") to "mon-tèèr",
            Regex("(?i)\\bmonbile\\b") to "mô-bil",
            Regex("(?i)\\bm[õo]billi\\b") to "mô-bil",
            Regex("(?i)\\bpnous\\b") to "pii-nou",
            Regex("(?i)\\bp[ĩi]nu\\b") to "pii-nou",
            Regex("(?i)\\bnanwoudre\\b") to "na-ou-dr",
            Regex("(?i)\\bnaoodre\\b") to "na-ou-dr",
            Regex("(?i)\\bwe+fo\\b") to "ouéé-fô",
            Regex("(?i)\\bmoncoroni\\b") to "mon-kô-rô-ni",
            Regex("(?i)\\bfo[ūu]gou\\b") to "fouu-gou",
            Regex("(?i)\\bfu+gu\\b") to "fouu-gou",
            Regex("(?i)\\bson\\s+ka+m\\b") to "son-kaam",
            Regex("(?i)\\bbidou\\b") to "bii-dou",
            Regex("(?i)\\bb[ĩi]du\\b") to "bii-dou",
            Regex("(?i)\\bmougne\\b") to "moui",
            Regex("(?i)\\bmui\\b") to "moui",
            Regex("(?i)\\bbeinga\\b") to "bén-ga",
            Regex("(?i)\\bb[ẽe]nga\\b") to "bén-ga",
            Regex("(?i)\\btouayga\\b") to "touay-ga",
            Regex("(?i)\\bb[ãa]?nga\\b") to "ban-ga",
            Regex("(?i)\\bya+gre\\b") to "yaa-gré",
            Regex("(?i)\\bsa+me\\b") to "saa-mé",
            Regex("(?i)\\bs[êẽe]n\\s+ka\\s+be\\b") to "sèn ka bé",
            Regex("(?i)\\bs[ĩi]+fu\\b") to "sii-fou",
            Regex("(?i)\\bwesbo\\b") to "oués-bô",
            Regex("(?i)\\bwese\\b") to "oués-bô",
            Regex("(?i)\\by[ôõo]sgre\\b") to "yôs-gré",
            Regex("(?i)\\by[âãa]gme\\b") to "nian-gmé",
            Regex("(?i)\\bsake\\b") to "sa-ké",
            Regex("(?i)\\bdoge\\b") to "rô-gué",
            Regex("(?i)\\broge\\b") to "rô-gué",
            Regex("(?i)\\bmaa?ne\\b") to "maa-né",
            Regex("(?i)\\blagme\\b") to "lag-mé",
            Regex("(?i)\\by[âãa]+ge\\b") to "niaa-gué",
            Regex("(?i)\\br[ôõo]dbo\\b") to "rôd-bô",
            Regex("(?i)\\br[àa]+da\\b") to "raa-da",
            Regex("(?i)\\bra+bo\\b") to "raa-bô",
            Regex("(?i)\\byagle\\b") to "ya-gal",
            Regex("(?i)\\bsoge\\b") to "sô-gué",
            Regex("(?i)\\bba+s[èe]\\b") to "baa-sé",
            Regex("(?i)\\bpao+ngo\\b") to "pa-ouon-gô",
            Regex("(?i)\\bmanesem\\b") to "man-sôm",
            Regex("(?i)\\bpa+sga\\b") to "paas-ga",
            Regex("(?i)\\bpa+se\\b") to "paa-sé",
            Regex("(?i)\\bk[êẽe]\\b") to "kiè",

            // D. Autres verbes et mots usuels du Mooré
            Regex("(?i)\\bw[ũuʋ]mdame\\b") to "oumdamé",
            Regex("(?i)\\bwoumdam[eé]\\b") to "oumdamé",
            Regex("(?i)\\bw[ũuʋ]m\\b") to "oume",
            Regex("(?i)\\byeelame\\b") to "yélamé",
            Regex("(?i)\\bwaame\\b") to "ouamé",
            Regex("(?i)\\bk[ẽe]ngame\\b") to "kèngamé",
            Regex("(?i)\\bmaaname\\b") to "manamé",
            Regex("(?i)\\bnongame\\b") to "nongamé",
            Regex("(?i)\\bs[õo]ngdame\\b") to "songodamé",
            Regex("(?i)\\bt[ʋu]mdame\\b") to "toumdamé",
            Regex("(?i)\\bmiime\\b") to "mimé",
            Regex("(?i)\\bbeeme\\b") to "bémé",
            Regex("(?i)\\bgom-biiga\\b") to "gome bîga",
            Regex("(?i)\\bgom-y\\b") to "gomé",
            Regex("(?i)\\bgomyi\\b") to "gomé",
            Regex("(?i)\\bkelg-y\\b") to "kèlgui",
            Regex("(?i)\\bges-y\\b") to "guèssé",
            Regex("(?i)\\bz[ĩi]nd-y\\b") to "zindî",
            Regex("(?i)\\bwa-y\\b") to "oua-i",
            Regex("(?i)\\bb[ôõ]e\\b") to "boé",
            Regex("(?i)\\bt[ôõ]e\\b") to "toé",
            Regex("(?i)\\bs[ôõ]e\\b") to "ssoé",
            Regex("(?i)\\bw[ôõ]e\\b") to "ouoé",
            Regex("(?i)\\bw[ãa]n-w[ãa]n\\b") to "ouan-ouan",
            Regex("(?i)\\bwan\\s+wan\\b") to "ouan-ouan",
            Regex("(?i)\\bw[ãa]na\\b") to "ouana",
            Regex("(?i)\\bwoto\\b") to "ouotô",
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
            Regex("(?i)\\br[ũu]nd[ãa]\\b") to "roun-da",
            Regex("(?i)\\broundan?\\b") to "roun-da",
            Regex("(?i)\\by[ãa]mba?\\b") to "yamba",
            Regex("(?i)\\bb[ãa]mba?\\b") to "bamba",
            Regex("(?i)\\bw[ẽe]nde\\b") to "ouèndé",
            Regex("(?i)\\bw[ẽe]nd\\b") to "ouèndé",
            Regex("(?i)\\bnaa+ba\\b") to "naba",
            Regex("(?i)\\bp[ʋu]+sda\\b") to "pousda",
            Regex("(?i)\\bw[ʋu]sgo\\b") to "ousgô",
            Regex("(?i)\\bwousgo\\b") to "ousgô",
            Regex("(?i)\\bousgo\\b") to "ousgô",
            Regex("(?i)\\bs[ʋu]ka\\b") to "ssouka",
            Regex("(?i)\\bs[õo]ama\\b") to "sôma",
            Regex("(?i)\\bs[ɩi]da\\b") to "ssida",
            Regex("(?i)\\bt[ʋu]+ma\\b") to "touma",
            Regex("(?i)\\by[ʋu]+re\\b") to "youré",
            Regex("(?i)\\by[ʋu]+r\\b") to "youré",
            Regex("(?i)\\bf[ʋu]+go\\b") to "fougô",
            Regex("(?i)\\bk[ʋu]+ngo\\b") to "koungô",
            Regex("(?i)\\bb[ʋu]+du\\b") to "boudou",
            Regex("(?i)\\bd[ʋu]+niya\\b") to "douniya",
            Regex("(?i)\\bb[ɩi]\\b") to "bî",
            Regex("(?i)\\bn[ɩi]+bo\\b") to "nibô",
            Regex("(?i)\\bn[ɩi]n-buiidu\\b") to "nine bouidou",
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
            Regex("(?i)\\bburkina\\s+faso\\b") to "bourkina fassô",
            Regex("(?i)\\bsam\\s+ai\\b") to "Same",
            Regex("(?i)\\bsam\\s+a\\s+i\\b") to "Same",
            Regex("(?i)\\bsam\\b") to "Same"
        )

        for ((pattern, replacement) in exactReplacements) {
            text = text.replace(pattern, replacement)
        }

        // 3. Transformation des suffixes grammaticaux avec tiret (-y, -f, -d, -m, -a, -b)
        text = text
            .replace(Regex("(?i)([a-zɩʋɛãẽĩõũéèêôâû])-y\\b"), "$1é")
            .replace(Regex("(?i)([a-zɩʋɛãẽĩõũéèêôâû])-f\\b"), "$1 fô")
            .replace(Regex("(?i)([a-zɩʋɛãẽĩõũéèêôâû])-d\\b"), "$1 dô")
            .replace(Regex("(?i)([a-zɩʋɛãẽĩõũéèêôâû])-m\\b"), "$1 ma")
            .replace(Regex("(?i)([a-zɩʋɛãẽĩõũéèêôâû])-a\\b"), "$1 a")
            .replace(Regex("(?i)([a-zɩʋɛãẽĩõũéèêôâû])-b\\b"), "$1 ba")
            .replace("-", " ")

        // 4. Pronoms et mots courts en Mooré verrouillés avec accents ('fô', 'dô', 'kô', 'tô', 'ssô', 'mame')
        // pour empêcher à 100% la lecture anglaise ('do' -> 'dou', 'to' -> 'tou', 'fo' -> 'fow', 'so' -> 'sow')
        text = text
            .replace(Regex("(?i)\\by\\b"), "i")
            .replace(Regex("(?i)\\bm\\b"), "mame")
            .replace(Regex("(?i)\\bn\\b"), "na")
            .replace(Regex("(?i)\\bf\\b"), "fô")
            .replace(Regex("(?i)\\bd\\b"), "dô")
            .replace(Regex("(?i)\\bb\\b"), "ba")
            .replace(Regex("(?i)\\bfo\\b"), "fô")
            .replace(Regex("(?i)\\bdo\\b"), "dô")
            .replace(Regex("(?i)\\bto\\b"), "tô")
            .replace(Regex("(?i)\\bko\\b"), "kô")
            .replace(Regex("(?i)\\bso\\b"), "ssô")
            .replace(Regex("(?i)\\bno\\b"), "nô")
            .replace(Regex("(?i)\\bgo\\b"), "gô")
            .replace(Regex("(?i)\\bbo\\b"), "bô")

        // 5. Diphtongues et lettres spéciales de l'alphabet Mooré (ʋ, ɩ, ɛ) et voyelles nasales (~)
        text = text
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

        // 6. Règles phonétiques et prosodiques humaines du Mooré :
        // - Remplacement de 'w' devant voyelle par 'ou' pour empêcher le TTS de basculer vers un accent anglais
        // - Suppression du son français 'euh' muet : tout 'e' non accentué en syllabe ouverte devient 'é' ou 'è'
        // - Tout 'o' final devient 'ô' fermé franc
        text = text
            .replace(Regex("(?i)eoo+"), "éo")
            .replace(Regex("(?i)eo"), "éo")
            .replace(Regex("(?i)aa+"), "a")
            .replace(Regex("(?i)oo+"), "o")
            .replace(Regex("(?i)ii+"), "i")
            .replace(Regex("(?i)uu+"), "u")
            .replace(Regex("(?<![oO])u"), "ou")
            .replace(Regex("(?<![oO])U"), "Ou")
            .replace(Regex("(?i)\\bw(ou)"), "$1")
            .replace(Regex("(?i)w([aàâeéèêiîoô])"), "ou$1")
            .replace(Regex("(?i)g([eéèi])"), "gu$1")
            .replace(Regex("(?i)([aeiouéèôî])s([aeiouéèôî])"), "$1ss$2")
            .replace(Regex("(?i)e([bcdfghjklmnpqrstvwxz]{2})"), "è$1")
            .replace(Regex("(?i)\\b(n|b|t|d|k|l|r|s|z|p|y)e\\b"), "$1é")
            .replace(Regex("(?i)([bcdfghjklnpqrstvwxz])e\\b"), "$1é")
            .replace(Regex("(?i)o\\b"), "ô")

        return text.replace(Regex("\\s+"), " ").trim()
    }

    private fun cleanGeneralTextForSpeech(rawText: String): String {
        return rawText
            .replace(Regex("[*#_~`^\\[\\]{}<>|\\\\«»\"“”•]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun releaseMediaPlayer() {
        try {
            loudnessEnhancer?.enabled = false
            loudnessEnhancer?.release()
        } catch (_: Exception) {}
        loudnessEnhancer = null
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {}
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
    }

    fun stop() {
        try {
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
