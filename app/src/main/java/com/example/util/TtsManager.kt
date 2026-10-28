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
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(22, TimeUnit.SECONDS)
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

    private suspend fun trySpeakWithNeuralVoice(
        spokenText: String,
        apiKey: String,
        isMoore: Boolean,
        rate: Float
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Débit normal, fluide et dynamique de vraie personne au téléphone
            val speed = (rate * 1.05f).coerceIn(0.98f, 1.40f)

            // 1. Essai prioritaire avec gpt-4o-mini-tts et la voix "coral" (claire, humaine, chaleureuse)
            val primaryPayload = buildJsonObject {
                put("model", "gpt-4o-mini-tts")
                put("input", spokenText.take(2500))
                put("voice", "coral")
                put("response_format", "mp3")
                put("speed", speed)
                if (isMoore) {
                    put(
                        "instructions",
                        "Tu es un vrai Burkinabè natif de Ouagadougou qui parle en langue MOORÉ au téléphone avec un ami. " +
                        "INTERDICTION ABSOLUE DE PARLER EN ANGLAIS OU EN FRANÇAIS : " +
                        "1. Ne prononce JAMAIS un seul mot ni une seule syllabe avec un accent anglais (ne dis jamais 'do' comme 'dou', ni 'fo' comme 'fow', ni 'rounda' comme 'raounda', et n'épelle JAMAIS de lettres en anglais comme 'em-pee'). " +
                        "2. Ne prends JAMAIS un accent français parisien et ne fais aucun son 'euh' muet. " +
                        "3. Prononce chaque voyelle de façon pure, franche et ouest-africaine : 'a' ouvert, 'é' franc, 'è' ouvert, 'ô/o' fermé franc, 'ou' franc, avec une voix 100% humaine, chaleureuse et naturelle."
                    )
                } else {
                    put(
                        "instructions",
                        "Parle exactement comme une vraie personne bienveillante au téléphone : voix claire, chaleureuse, vivante, avec des intonations humaines naturelles et un rythme fluide."
                    )
                }
            }.toString()

            var audioBytes = requestOpenAiAudioBytes(primaryPayload, apiKey)

            // 2. Repli automatique sur tts-1-hd avec la voix "nova" (claire, cristalline et dynamique, sans lourdeur grave)
            if (audioBytes == null) {
                val hdPayload = buildJsonObject {
                    put("model", "tts-1-hd")
                    put("input", spokenText.take(2500))
                    put("voice", "nova")
                    put("response_format", "mp3")
                    put("speed", speed)
                }.toString()
                audioBytes = requestOpenAiAudioBytes(hdPayload, apiKey)
            }

            if (audioBytes == null || audioBytes.size < 200) {
                return@withContext false
            }

            val tempFile = File(appContext.cacheDir, "sam_ai_voice_${System.currentTimeMillis()}.mp3")
            FileOutputStream(tempFile).use { it.write(audioBytes) }

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
                        setDataSource(tempFile.absolutePath)
                        setVolume(1.0f, 1.0f)
                        setOnPreparedListener { mp ->
                            // Amplification équilibrée (+4.5 dB) pour une clarté vocale optimale sans saturation des basses
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
                            tempFile.delete()
                            val cb = activeOnDoneCallback
                            activeOnDoneCallback = null
                            cb?.invoke()
                        }
                        setOnErrorListener { _, _, _ ->
                            isNeuralPlaying = false
                            releaseMediaPlayer()
                            tempFile.delete()
                            val errCb = activeOnErrorCallback
                            activeOnErrorCallback = null
                            errCb?.invoke()
                            true
                        }
                        prepareAsync()
                    }
                } catch (e: Exception) {
                    isNeuralPlaying = false
                    tempFile.delete()
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

        // 1c. Blocage total des groupes de consonnes initiaux ('mp', 'mb', 'nd', 'nk', 'nt', 'ng')
        // qui faisaient épeler "M P" en anglais au synthétiseur vocal (ex: m'pʋsda / mpousda -> mame pousda)
        text = text
            .replace(Regex("(?i)\\bmp\\b"), "mame pa")
            .replace(Regex("(?i)\\bmb\\b"), "mame ba")
            .replace(Regex("(?i)\\bnd\\b"), "na dô")
            .replace(Regex("(?i)\\bnk\\b"), "na kô")
            .replace(Regex("(?i)\\bnt\\b"), "na tô")
            .replace(Regex("(?i)\\bng\\b"), "na gô")
            .replace(Regex("(?i)\\bm['’]?([pbftkszdgvyrl])([aàâeéèêiîoôuùûʋɩɛãẽĩõũ])"), "mame $1$2")
            .replace(Regex("(?i)\\bn['’]?([pbftkszdgvyrl])([aàâeéèêiîoôuùûʋɩɛãẽĩõũ])"), "na $1$2")

        // 2. Dictionnaire phonétique étendu des mots, verbes et expressions du Mooré parlé à Ouagadougou
        // Utilisation de 'ô' et 'î' pour verrouiller la phonologie hors de l'anglais ('do', 'fo', 'to', 'so', 'go', 'round')
        val exactReplacements = listOf(
            Regex("(?i)\\bne\\s+y\\s+beo+go\\b") to "né i béogô",
            Regex("(?i)\\bne\\s+y\\s+zaa+bre\\b") to "né i zabré",
            Regex("(?i)\\bne\\s+y\\s+k[ʋu]+ngo\\b") to "né i koungô",
            Regex("(?i)\\bne\\s+y\\s+y[ʋu]+ngo\\b") to "né i youngô",
            Regex("(?i)\\bne\\s+y\\s+w[ĩi]ndga\\b") to "né i ouindga",
            Regex("(?i)\\bne\\s+y\\s+t[ʋu]+ma\\b") to "né i touma",
            Regex("(?i)\\blaafi\\s+bala\\b") to "lafi bala",
            Regex("(?i)\\blaafi\\s+be+me\\b") to "lafi bémé",
            Regex("(?i)\\blaafi\\b") to "lafi",
            Regex("(?i)\\bkibare\\b") to "kibaré",
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
            Regex("(?i)\\bt[õo]ndo?\\b") to "tondô",
            Regex("(?i)\\bw[ẽe]nde\\b") to "ouèndé",
            Regex("(?i)\\bw[ẽe]nd\\b") to "ouèndé",
            Regex("(?i)\\bnaa+ba\\b") to "naba",
            Regex("(?i)\\bp[ʋu]+sda\\b") to "pousda",
            Regex("(?i)\\bw[ʋu]sgo\\b") to "ousgô",
            Regex("(?i)\\bwousgo\\b") to "ousgô",
            Regex("(?i)\\bousgo\\b") to "ousgô",
            Regex("(?i)\\bs[ʋu]ka\\b") to "ssouka",
            Regex("(?i)\\bs[õo]ama\\b") to "ssoma",
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
            Regex("(?i)\\bmaane\\b") to "mané",
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
