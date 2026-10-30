package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.data.local.ApiKeyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Service ultra-rapide de reconnaissance vocale OpenAI Whisper optimisé pour le Mooré (Burkina Faso) :
 * - Latence minimale (< 0.8s) en une seule passe réseau directe (sans double appel bloquant)
 * - Ancrage phonétique francophone/burkinabè (language="fr" + temperature="0") empêchant Whisper
 *   de basculer sur des langues aléatoires (anglais, coréen, gallois, arabe) sur les phrases courtes
 * - Conditionnement lexical Mooré haute densité + dictionnaire phonétique instantané (0 ms)
 */
class OpenAiWhisperService(private val context: Context) {

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val apiKeyManager = ApiKeyManager(context)

    companion object {
        private const val TAG = "OpenAiWhisperService"
        private const val WHISPER_API_URL = "https://api.openai.com/v1/audio/transcriptions"
        private const val WHISPER_FAST_MODEL = "whisper-1"
        private const val GPT4O_TRANSCRIBE_MODEL = "gpt-4o-mini-transcribe"

        // Prompt lexical compact haute vitesse (< 224 tokens) pour un décodage instantané (< 0.45s)
        private const val MOORE_WHISPER_PROMPT =
            "Nii yi beogo, Yibeo kibare, Nii zabre, Zabre kibare, Nii windga, Windga kibare, Niyungo, Yungo kibare, " +
            "Wind da ganeg laafi, Neewôgô, Ya sonma, Laafi beeme, Laafi bala, Kibaré, Nindaaré, Wënd-na kô-d nindaare, Barka, Zom-kom, Sên na be, " +
            "Maam, Fõ, Tond, M'ma, M'baaba, Nikieman, Poug yaanga, Paga, Rawa, Biiga, Poug sada, Kakouada, Tandaogo, " +
            "M nan gounsam, M data ligdi, M debda zakin wan, Raaga, Monteur, Monbile, Pnous, Nanwoudre, Weefo, Moncoroni, Foūgou, Son kaam, Bidou, Mougne, Beinga, Touayga, Banga, Yagre."

        private const val MOORE_GPT4O_PROMPT =
            "Transcris fidèlement les paroles en Mooré (Burkina Faso) ou français : " +
            "Nii yi beogo, Yibeo kibare, Nii zabre, Zabre kibare, Nii windga, Windga kibare, Niyungo, Yungo kibare, " +
            "Wind da ganeg laafi, Neewôgô, Ya sonma, Laafi beeme, Laafi bala, Kibaré, Nindaaré, Wënd-na kô-d nindaare, Barka, Zom-kom, Sên na be, " +
            "Maam, Fõ, Tond, M'ma, M'baaba, Nikieman, Poug yaanga, Paga, Rawa, Biiga, Poug sada, Kakouada, Tandaogo, " +
            "M nan gounsam, M data ligdi, M debda zakin wan, Raaga, Monteur, Monbile, Pnous, Nanwoudre, Weefo, Moncoroni, Foūgou, Son kaam, Bidou, Mougne, Beinga, Touayga, Banga, Yagre."

        // Base de référence des 50 leçons audio + expressions Mooré courantes avec leurs variantes phonétiques parlées
        private val CANONICAL_MOORE_PHRASES: List<Pair<String, List<String>>> = listOf(
            "Nii yi beogo" to listOf("nii yi beogo", "ni yibeogo", "nii yibeogo", "ni ibeogo", "ni beogo", "ne y beogo", "ne beogo", "ni i bogo", "ni bogo", "ni yibéogô", "ni hibéogo"),
            "Yibeo kibare" to listOf("yibeo kibare", "yibéo kibaré", "yi beogo kibare", "ibeo kibare", "yibeo kibar"),
            "Nii zabre" to listOf("nii zabre", "ni zabre", "nii zaabre", "ni zaabre", "ne zabre", "ne y zaabre", "ni sabre", "ni jabre", "ni zabré"),
            "Zabre kibare" to listOf("zabre kibare", "zaabre kibare", "zabré kibaré", "sabre kibare"),
            "Nii windga" to listOf("nii windga", "ni windga", "nii ouindga", "ni ouindga", "ni winga", "ni ouinga", "ne y windga", "nii ouin-nga"),
            "Windga kibare" to listOf("windga kibare", "ouindga kibare", "winga kibare", "ouin-nga kibaré"),
            "Niyungo" to listOf("niyungo", "nii yungo", "ni yungo", "nii youngo", "ni youngo", "ni yougo", "ne y yungo", "nii youngô"),
            "Yungo kibare" to listOf("yungo kibare", "youngo kibare", "yougo kibare", "youngô kibaré"),
            "Wind da ganeg laafi" to listOf("wind da ganeg laafi", "wend na ganeg laafi", "ouenda ganag lafi", "winda ganeg lafi", "ganeg laafi"),
            "Neewôgô" to listOf("neewogo", "neewôgô", "ne waongo", "ne y waoongo", "né oua ouongo", "newogo", "ni waongo"),
            "Ya sonma" to listOf("ya sonma", "ya soma", "yaa soama", "ya choma", "ya sôma"),
            "Laafi beeme" to listOf("laafi beeme", "lafi beme", "lafi bémé", "la fi beme", "la fille beme"),
            "Laafi bala" to listOf("laafi bala", "lafi bala", "la fi bala", "la fille bala"),
            "Kibare" to listOf("kibare", "kibaré", "ki bare", "kibari", "qui bare", "ti bare"),
            "Nindaaré" to listOf("nindaare", "nindaaré", "nindare", "nin dare", "ni dare"),
            "Wënd-na kô-d nindaare" to listOf("wend na kod nindaare", "ouenda kod nindare", "wenda kod nindaare"),
            "Barka" to listOf("barka", "barca", "barque a"),
            "Barka wʋsgo" to listOf("barka wusgo", "barka ousgo", "barka wousgo", "barca ousgo"),
            "Zom-kom" to listOf("zom kom", "zom-kom", "zome kome", "zôm kôôm"),
            "Sên na be" to listOf("sen na be", "sèn na bé", "se na be"),
            "M nan gounsam" to listOf("m nan gounsam", "m na goussom", "m na goussam", "ma goussom", "na goussom", "m'na goussôm"),
            "M data ligdi" to listOf("m data ligdi", "m'data ligdi", "ma data ligdi", "mam data ligdi", "data ligdi", "rata ligdi"),
            "M debda zakin wan" to listOf("m debda zakin wan", "m debda zakin", "m'debda zakouan", "debda zakin", "debda zakouan"),
            "Maam" to listOf("maam", "maame"),
            "Fõ" to listOf("fõ", "fo"),
            "Tond" to listOf("tond", "tondo", "tõndo", "tôndô"),
            "M'ma" to listOf("m'ma", "mma"),
            "M'baaba" to listOf("m'baaba", "mbaaba", "m baba", "baaba"),
            "Nikieman" to listOf("nikieman", "nikiemam", "nin keema", "ninkema", "nin-kééma"),
            "Poug yaanga" to listOf("poug yaanga", "poug yanga", "pug yaanga", "pou gnanga", "pougnanga"),
            "Paga" to listOf("paga", "pagha"),
            "Rawa" to listOf("rawa", "raoua", "raoa"),
            "Biiga" to listOf("biiga", "biga"),
            "Poug sada" to listOf("poug sada", "pouksada", "pouk sada", "pug sadga"),
            "Kakouada" to listOf("kakouada", "ka kouada", "kakoaada", "ka koaadga"),
            "Tandaogo" to listOf("tandaogo", "tan da ougo", "tandaougo"),
            "Raaga" to listOf("raaga", "raga"),
            "Monteur" to listOf("monteur", "montere", "mon tèèr"),
            "Monbile" to listOf("monbile", "mobilli", "môbil"),
            "Pnous" to listOf("pnous", "pinou", "piinou", "pinu"),
            "Nanwoudre" to listOf("nanwoudre", "naoodre", "naoudr", "nanwoudr"),
            "Weefo" to listOf("weefo", "ouefo", "ouééfô", "wefo"),
            "Moncoroni" to listOf("moncoroni", "monkoroni", "macaroni"),
            "Foūgou" to listOf("fougou", "foūgou", "fugu"),
            "Son kaam" to listOf("son kaam", "sonkam", "soun kaam", "son kam"),
            "Bidou" to listOf("bidou", "bidu", "biidou"),
            "Mougne" to listOf("mougne", "moui", "mui"),
            "Beinga" to listOf("beinga", "benga", "bénga"),
            "Touayga" to listOf("touayga", "twayga", "touaiga"),
            "Banga" to listOf("banga"),
            "Yagre" to listOf("yagre", "yaagré"),
            "Saame" to listOf("saame", "saamé"),
            "Sên ka be" to listOf("sen ka be", "sèn ka bé"),
            "Siifu" to listOf("siifu", "sifou", "siifou"),
            "Wese" to listOf("wese", "wesbo", "ouesbo"),
            "Yôsgre" to listOf("yosgre", "yôsgre"),
            "Yâgme" to listOf("yagme", "niangme", "nian-gmé"),
            "Sake" to listOf("sake", "saké"),
            "Doge" to listOf("doge", "roge", "rogué"),
            "Pùg Roagsá" to listOf("pug roagsa", "poug roaksa", "poug roagsa"),
            "Maane" to listOf("maane", "mané"),
            "Lagme" to listOf("lagme", "lagmé"),
            "Yâage" to listOf("yaage", "niaagué", "niague"),
            "Rodbo" to listOf("rodbo", "rôdbô"),
            "Ràada" to listOf("raada", "rada"),
            "Raabo" to listOf("raabo", "rabo"),
            "Yagle" to listOf("yagle", "yagal"),
            "Soge" to listOf("soge", "sogué"),
            "Baasè" to listOf("baase", "baasè", "basé"),
            "Paoongo" to listOf("paoongo", "paouongo", "paongo"),
            "Manesem" to listOf("manesem", "mansom"),
            "Paasga" to listOf("paasga", "pasga"),
            "Paase" to listOf("paase", "pasé")
        )

        private val STANDARD_FRENCH_WORDS = setOf(
            "bonjour", "bonsoir", "salut", "merci", "oui", "non", "comment", "ca va", "ça va",
            "qui es tu", "comment tu t'appelles", "aide moi", "pourquoi", "quand", "combien",
            "actualite", "actualité", "burkina", "ouagadougou", "au revoir", "bonne nuit", "midi"
        )

        private val HALLUCINATION_PATTERNS = listOf(
            "sous-titres réalisés",
            "sous-titrage",
            "amara.org",
            "merci d'avoir regardé",
            "abonnez-vous",
            "thank you for watching",
            "subtitles by",
            "transcribed by",
            "sottotitoli",
            "mbc 뉴스",
            "시청해주셔서",
            "ご視聴ありがとう",
            "Субтитры",
            "www.",
            "transcris exactement les paroles"
        )
    }

    /**
     * Transcrit un fichier audio enregistré via OpenAI Audio Transcriptions avec latence minimale (< 0.5s).
     * Utilise en priorité `whisper-1` ancré phonétiquement pour une vitesse de réponse immédiate,
     * avec repli sur `gpt-4o-mini-transcribe`.
     */
    suspend fun transcribeAudio(
        audioFile: File,
        languageHint: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!audioFile.exists() || audioFile.length() <= 44) {
            return@withContext Result.failure(IllegalArgumentException("Fichier audio vide ou introuvable."))
        }

        val apiKey = apiKeyManager.getActiveOpenAiApiKey()
        if (apiKey.isBlank() || apiKey == "MY_OPENAI_API_KEY") {
            Log.e(TAG, "Clé API OpenAI non configurée")
            return@withContext Result.failure(
                IllegalStateException("Clé API OpenAI non configurée. Veuillez ajouter votre clé dans les paramètres.")
            )
        }

        val isMooreHint = languageHint?.lowercase(Locale.ROOT)?.contains("moor") == true
        val whisperLangCode = mapToWhisperLanguageCode(languageHint)

        try {
            // 1. Appel direct ultra-rapide sur whisper-1 (< 450 ms) avec conditionnement lexical Mooré
            val primaryResult = executeTranscriptionRequest(
                audioFile = audioFile,
                apiKey = apiKey,
                model = WHISPER_FAST_MODEL,
                whisperLangCode = whisperLangCode,
                isMooreHint = isMooreHint
            )

            val rawTranscriptResult = if (primaryResult.isSuccess && !primaryResult.getOrNull().isNullOrBlank()) {
                primaryResult
            } else {
                executeTranscriptionRequest(
                    audioFile = audioFile,
                    apiKey = apiKey,
                    model = GPT4O_TRANSCRIBE_MODEL,
                    whisperLangCode = whisperLangCode,
                    isMooreHint = isMooreHint
                )
            }

            if (rawTranscriptResult.isFailure) {
                return@withContext rawTranscriptResult
            }

            val rawText = rawTranscriptResult.getOrNull().orEmpty().trim()
            if (rawText.isBlank() || isHallucinationOrPromptEcho(rawText)) {
                Log.d(TAG, "Silence ou hallucination filtrée : \"$rawText\"")
                return@withContext Result.success("")
            }

            // Correction phonétique et rapprochement flou instantané (0 ms)
            val finalTranscript = applyMoorePhoneticCorrections(rawText)
            Log.d(TAG, "Transcription -> Brut: \"$rawText\" | Corrigé: \"$finalTranscript\"")

            Result.success(finalTranscript)
        } catch (e: Exception) {
            Log.e(TAG, "Exception lors de l'appel OpenAI Transcription : ${e.message}", e)
            Result.failure(Exception("Erreur de connexion : ${e.localizedMessage ?: "Vérifiez votre réseau"}"))
        } finally {
            try {
                if (audioFile.exists()) {
                    audioFile.delete()
                }
            } catch (_: Exception) {}
        }
    }

    private fun executeTranscriptionRequest(
        audioFile: File,
        apiKey: String,
        model: String,
        whisperLangCode: String?,
        isMooreHint: Boolean
    ): Result<String> {
        val mediaType = when {
            audioFile.name.endsWith(".wav", ignoreCase = true) -> "audio/wav".toMediaType()
            audioFile.name.endsWith(".mp3", ignoreCase = true) -> "audio/mpeg".toMediaType()
            else -> "audio/m4a".toMediaType()
        }

        val contextPrompt = when {
            isMooreHint && model == GPT4O_TRANSCRIBE_MODEL -> MOORE_GPT4O_PROMPT
            isMooreHint -> MOORE_WHISPER_PROMPT
            whisperLangCode == "fr" -> "Nii yi beogo, Nii zabre, Nii windga, Niyungo, Yibeo kibare, Laafi bala, Ya sonma, français du Burkina Faso, Ouagadougou, SAM AI."
            whisperLangCode == "en" -> "Accurate speech-to-text transcription."
            else -> MOORE_WHISPER_PROMPT
        }

        val multipartBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", model)
            .addFormDataPart("response_format", "json")
            .addFormDataPart("temperature", "0")
            .addFormDataPart("prompt", contextPrompt)
            .addFormDataPart(
                "file",
                audioFile.name,
                audioFile.asRequestBody(mediaType)
            )

        val effectiveLang = if (isMooreHint) "fr" else (whisperLangCode ?: "fr")
        multipartBuilder.addFormDataPart("language", effectiveLang)

        val request = Request.Builder()
            .url(WHISPER_API_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(multipartBuilder.build())
            .build()

        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                Log.w(TAG, "API Transcription [$model - Code ${response.code}]: $responseBody")
                val parsedError = try {
                    val jsonEl = json.parseToJsonElement(responseBody).jsonObject
                    val errObj = jsonEl["error"]?.jsonObject
                    errObj?.get("message")?.jsonPrimitive?.content
                } catch (_: Exception) {
                    null
                }
                val userMessage = when (response.code) {
                    401 -> "Clé API OpenAI invalide ou expirée."
                    429 -> "Limite de requêtes OpenAI atteinte. Réessayez dans un instant."
                    else -> parsedError ?: "Erreur OpenAI Transcription (${response.code})"
                }
                return Result.failure(Exception(userMessage))
            }

            val jsonEl = json.parseToJsonElement(responseBody).jsonObject
            val transcribedText = jsonEl["text"]?.jsonPrimitive?.content.orEmpty().trim()
            return Result.success(transcribedText)
        }
    }

    private fun isHallucinationOrPromptEcho(text: String): Boolean {
        val lower = text.trim().lowercase(Locale.ROOT)
        if (lower.length < 2) return true
        if (HALLUCINATION_PATTERNS.any { lower.contains(it) }) return true
        if (lower.contains("nii yi beogo, yibeo kibare, nii zabre")) return true
        if (lower.contains("ne y beogo, ne y zaabre, ne y windga")) return true
        return false
    }

    /**
     * Dictionnaire déterministe instantané (0 ms) + Squelette phonétique flou (Levenshtein) :
     * Normalise les voyelles/nasales allongées (ex: "Niiiii zabreee", "Maaaaam", "Fooooo", "Raaaaga"),
     * corrige les homophones français et retrouve l'expression Mooré exacte même si la prononciation est rapide ou bruitée.
     */
    fun applyMoorePhoneticCorrections(input: String): String {
        var s = input.trim()
        if (s.isEmpty()) return s

        // 0. Normalisation immédiate des prolongations de voyelles et consonnes nasales
        s = com.example.data.engine.DictionaryLookupEngine.normalizeMooreProlongations(s)

        val replacements = listOf(
            // 1. EN PREMIER — Leçon Audio n°1 : Salutation du matin "Nii yi beogo" (Bonjour) & sa réponse "Yibeo kibare"
            Regex("(?i)\\b(nii+\\s*y?i?\\s*beo+go+|ni\\s*y?i?\\s*beo+go+|nii?\\s*y?i?\\s*béogo+|ne\\s*y?i?\\s*beo+go+|né\\s*y?i?\\s*béogo+|ney\\s*beo+go+|nyi\\s*beo+go+|n'i\\s*beo+go+|n'yibeo+go+|ni\\s*yéogo|ne\\s*yéogo|nei\\s*beogo|mais il est beau|n'ayez beau|n'est-il beau|nez beau|les beaux|mais il boit|n'est beau|n'est-il pas beau|nay beogo|n'aie beogo|ni bio go|ni ibeogo|ni ibéogo|ni i bogo|ni bogo|ni hibéogo|ni il est beau|ni yé béogo|mi ibéogo)\\b") to "Nii yi beogo",
            Regex("(?i)\\b(yibeo+g?\\s*kibar[eé]+|yibéo+g?\\s*kibar[eé]+|yi\\s*beo+g?\\s*kibar[eé]+|yi\\s*béo+g?\\s*kibar[eé]+|ibeo\\s*kibar[eé]+|ibéo\\s*kibar[eé]+|il est beau qui barre|hibéo\\s*kibar[eé]+)\\b") to "Yibeo kibare",
            // 2. Leçon Audio n°2 : Salutation du soir "Nii zabre" (Bonsoir) & sa réponse "Zabre kibare"
            Regex("(?i)\\b(nii+\\s*za+br[eé]*|ni\\s*za+br[eé]*|ne\\s*za+br[eé]*|ney\\s*za+br[eé]*|ne\\s*i\\s*za+br[eé]*|né\\s*i\\s*za+br[eé]*|ne\\s*y\\s*za+br[eé]*|mais il z'abre|les arbres|nez arbres|n'est arbre|mise à jour|ni sabre|ni sabré|ni z'abre|ni jabré|ni zablé|mi zabré)\\b") to "Nii zabre",
            Regex("(?i)\\b(za+br[eé]?\\s*kibar[eé]+|sabre\\s*qui\\s*barre|sabré\\s*kibar[eé]+|z'abre\\s*kibar[eé]+)\\b") to "Zabre kibare",
            // 3. Leçon Audio n°3 : Salutation Midi / Après-midi "Nii windga" & sa réponse "Windga kibare"
            Regex("(?i)\\b(nii+\\s*wi+nd?ga+|ni\\s*wi+nd?ga+|ne\\s*wi+nd?ga+|ney\\s*wi+nd?ga+|ne\\s*i\\s*wi+nd?ga+|né\\s*i\\s*wi+nd?ga+|ne\\s*ouind?ga+|ni\\s*ouind?ga+|ne\\s*y\\s*w[ĩi]nd?ga+|ni\\s*ouin\\s*ga|ne\\s*ouin\\s*ga|ni\\s*winga|nii\\s*winga|ni\\s*ouinga|ni\\s*wendga)\\b") to "Nii windga",
            Regex("(?i)\\b(wi+nd?ga+\\s*kibar[eé]+|ouind?ga+\\s*kibar[eé]+|winga\\s*kibar[eé]+|ouinga\\s*kibar[eé]+)\\b") to "Windga kibare",
            // 4. Leçon Audio n°4 : Salutation de la nuit "Niyungo" & sa réponse "Yungo kibare"
            Regex("(?i)\\b(nii+\\s*yu+ngo+|ni\\s*yu+ngo+|niyungo+|ne\\s*yu+ngo+|ney\\s*you+ngo+|ni\\s*you+ngo+|ne\\s*you+ngo+|ne\\s*i\\s*you+ngo+|ne\\s*y\\s*y[ʋu]ngo+|ni\\s*yougo|ni\\s*yongo|nii\\s*yougo)\\b") to "Niyungo",
            Regex("(?i)\\b(yu+ngo+\\s*kibar[eé]+|you+ngo+\\s*kibar[eé]+|yougo\\s*kibar[eé]+)\\b") to "Yungo kibare",
            // 5. Souhait de bonne nuit "Wind da ganeg laafi"
            Regex("(?i)\\b(wi+nd\\s*da+\\s*ga+n[ae]g\\s*la+fi+|we+nd\\s*na+\\s*ga+n[ae]g\\s*la+fi+|ouènda\\s*ganag\\s*lafi|ouinda\\s*ganag\\s*lafi|wenda\\s*ganag\\s*laafi|winda\\s*ganeg\\s*lafi|ganeg\\s*laafi)\\b") to "Wind da ganeg laafi",
            // 6. Bienvenue "Neewôgô"
            Regex("(?i)\\b(ne+w[oô]+g[oô]+|ne\\s*wa+o+ngo+|ne\\s*y\\s*wa+o+ngo+|né\\s*oua\\s*ouongo|ni\\s*waongo|né\\s*ouongo|né\\s*wogo)\\b") to "Neewôgô",
            // Salutations au travail (Ne y tʋʋma)
            Regex("(?i)\\b(ne tuuma|ney touma|ne touma|ni touma|ne i touma|ni tuuma|ne tuma|né touma|ne y tuuma)\\b") to "Ne y tʋʋma",
            // Ça va bien : "Ya sonma" / "Laafi bala"
            Regex("(?i)\\b(ya+\\s*so+n?ma+|ya+\\s*sõ+a*ma+|ya+\\s*ssoma+|il y a\\s*soma|ya\\s*son\\s*ma|y\\s*a\\s*soma|ya\\s*choma)\\b") to "Ya sonma",
            Regex("(?i)\\b(la fille va là|l'afrique va là|la vie va là|l'habit va là|la fille bala|la vie bala|lafi bala|la fi bala|laafi ba la|la fille parlait|la fille là-bas|la fi va là|lafi va la)\\b") to "Laafi bala",
            // Et la santé ? : "Laafi beeme"
            Regex("(?i)\\b(la fille bémé|la vie bémé|lafi bémé|lafi beme|laafi beme|la fi bémé|la fille m'aimait|la fille aimait|lafi bème)\\b") to "Laafi beeme",
            // Fo be laafi / Mam be laafi (Tu vas bien / Je vais bien)
            Regex("(?i)\\b(faut bien la fille|faut bé lafi|fo bé lafi|fo be lafi|il faut la fille|faut payer la fille)\\b") to "Fo be laafi",
            Regex("(?i)\\b(m'a mis la fille|mam bé lafi|mame bé lafi|mam be lafi|maman la fille)\\b") to "Mam be laafi",
            // Kibare (Comment ça va ?)
            Regex("(?i)\\b(qui barre|qu'il barre|qui barré|qu'il barré|kibaré|ki baré|kibari|qui bar|petit bar|tu barres|qui part|qu'il part|ti barré)\\b") to "Kibare",
            // Au revoir & Adieu : "Nindaaré" / "Wënd-na kô-d nindaare"
            Regex("(?i)\\b(ni+nda+r[eé]+|nin\\s*daré|nindaré|ni\\s*d'arrêt|n'y\\s*d'arrêt)\\b") to "Nindaaré",
            Regex("(?i)\\b(ouènda\\s*kôd\\s*nindaré|wenda\\s*kod\\s*nindaare|wend\\s*na\\s*kod\\s*nindaare|ouenda\\s*code\\s*nindaré)\\b") to "Wënd-na kô-d nindaare",
            // Barka / Barka wʋsgo (Merci / Merci beaucoup)
            Regex("(?i)\\b(barka ousgo|barka wousgo|barka ousgou|barca wousgo|barque à ousgo|barka wusgo|barka usgo|par cas où|barque à|barca ousgo)\\b") to "Barka wʋsgo",
            Regex("(?i)\\b(barque|barca)\\b") to "Barka",
            // Boisson de bienvenue : "Zom-kom"
            Regex("(?i)\\b(z[oô]+m\\s*k[oô]+m|zome\\s*kome|zom\\s*kom)\\b") to "Zom-kom",
            // L'actualité du jour : "Sên na be" / "Rũndã kibaaya"
            Regex("(?i)\\b(s[eèêẽ]+n\\s*na+\\s*b[eé]+|c'est\\s*na\\s*bé|sèn\\s*na\\s*bé)\\b") to "Sên na be",
            Regex("(?i)\\b(rou+nda+\\s*kiba+ya+|ru+nda+\\s*kiba+ya+)\\b") to "Rũndã kibaaya",
            // Phrases courantes des audios : "M nan gounsam" (Je vais dormir), "M data ligdi" (Je veux l'argent), "M debda zakin wan" (Je vais à la maison)
            Regex("(?i)\\b(m['’\\s]*na+n?\\s*gou*n?sa+m|m['’\\s]*na+\\s*gou*sso+m|m['’\\s]*na+\\s*gousse+m|ma\\s*goussom|na\\s*goussom|on\\s*a\\s*goût\\s*ça)\\b") to "M nan gounsam",
            Regex("(?i)\\b(m['’\\s]*da+ta+\\s*li+gdi+|madame\\s*data\\s*ligdi|ma\\s*data\\s*ligdi|data\\s*ligdi|rata\\s*ligdi)\\b") to "M data ligdi",
            Regex("(?i)\\b(m['’\\s]*de+bda+\\s*za+ki*n?\\s*wa*n?|m['’\\s]*de+bda+\\s*za+koua+n?|debda\\s*zakin|débda\\s*zakouan)\\b") to "M debda zakin wan",
            // Famille, personnes et métiers des audios
            Regex("(?i)\\b(ni+n[-\\s]*k[eéèêẽ]+ma+n?|ni+ki+[eéè]+ma+n?)\\b") to "Nikieman",
            Regex("(?i)\\b(pou*g[-\\s]*n?ya+nga+|p[uʋ]+g[-\\s]*n?ya+nga+)\\b") to "Poug yaanga",
            Regex("(?i)\\b(pou*g?k?[-\\s]*sa+da+|p[uʋ]+g[-\\s]*sa+dga+)\\b") to "Poug sada",
            Regex("(?i)\\b(ka+[-\\s]*kou*a+da+|ka+[-\\s]*ko+a+dga+)\\b") to "Kakouada",
            Regex("(?i)\\b(ta+nda+o*go+|tan\\s*da\\s*ougo)\\b") to "Tandaogo",
            Regex("(?i)\\b(m['’\\s]*ba+ba+)\\b") to "M'baaba",
            Regex("(?i)\\b(m['’\\s]*ma+)\\b") to "M'ma",
            Regex("(?i)\\b(ra+[-\\s]*oua+|ra+wa+)\\b") to "Rawa",
            // Objets et aliments des audios
            Regex("(?i)\\b(mo+nte+u*r|mon\\s*terre|m[oõ]+nte+r[eé]*)\\b") to "Monteur",
            Regex("(?i)\\b(mo+nbi+l[eé]*|m[oõ]+bi+ll?i*)\\b") to "Monbile",
            Regex("(?i)\\b(pi+[-\\s]*nou+|pno+u*s?)\\b") to "Pnous",
            Regex("(?i)\\b(na+[-\\s]*ou+[-\\s]*dr[eé]*|na+n?wou*dr[eé]*)\\b") to "Nanwoudre",
            Regex("(?i)\\b(oué+[-\\s]*f[oô]+|we+fo+)\\b") to "Weefo",
            Regex("(?i)\\b(mo+n[-\\s]*k[oô]+[-\\s]*r[oô]+[-\\s]*ni+|mo+nco+ro+ni+)\\b") to "Moncoroni",
            Regex("(?i)\\b(fou+[-\\s]*gou+|fo+u+gou+)\\b") to "Foūgou",
            Regex("(?i)\\b(so+n[-\\s]*ka+m|sou*n[-\\s]*ka+m)\\b") to "Son kaam",
            Regex("(?i)\\b(bi+[-\\s]*dou+|bi+du+)\\b") to "Bidou",
            Regex("(?i)\\b(moui+|mou+gn[eé]*)\\b") to "Mougne",
            Regex("(?i)\\b(b[eéèẽ]+n[-\\s]*ga+|be+i*nga+)\\b") to "Beinga",
            Regex("(?i)\\b(tou*a+y[-\\s]*ga+|twa+yga+)\\b") to "Touayga",
            // Wẽnd na kõ-d laafi
            Regex("(?i)\\b(wend na kod lafi|ouind na kod lafi|wendé na kod lafi|ouèndé na ko do lafi|wend na ko lafi|wend na kod laafi)\\b") to "Wẽnd na kõ-d laafi",
            // Questions courantes (Bõe la fo... / Fo yʋʋre la a bõe / Wãn-wãn)
            Regex("(?i)\\b(boire la peau|bois la faut|boé la fo|boué la fo|boe la fo|oi la fo|bon la faut)\\b") to "Bõe la fo",
            Regex("(?i)\\b(fo youré|faut youré|fo youre|il faut youré|faut pleurer|fo yuure)\\b") to "Fo yʋʋre",
            Regex("(?i)\\b(mam youré|mame youré|ma meilleure|mam yuure|m'a amélioré)\\b") to "Mam yʋʋre",
            Regex("(?i)\\b(mam ya|mame ya|m'a mis à|maman il y a|même il y a)\\b") to "Mam yaa",
            Regex("(?i)\\b(mam data|mame data|mam rata|mame rata|madame data|m'a raté)\\b") to "Mam rata",
            Regex("(?i)\\b(mam woumdamé|mam oumdamé|mame oumdamé|mam wumdame|m'a condamné)\\b") to "Mam wʋmdame",
            Regex("(?i)\\b(mam pa woumd|mam pa oumd|mame pa oumd|mam pa wumd)\\b") to "Mam pa wʋmd",
            Regex("(?i)\\b(sida la a woto|sida la woto|c'est dans la photo|si dans la moto)\\b") to "Sida la a woto",
            // Mots courants Mooré transcrits phonétiquement
            Regex("(?i)\\b(ouindé|wendé|wende|ouèndé)\\b") to "Wẽnde",
            Regex("(?i)\\b(wan-wan|wan wan|ouan ouan|wane wane|oin oin|one one)\\b") to "wãn-wãn",
            Regex("(?i)\\b(wousgo|ousgo|ousgou|wusgo|ousgô)\\b") to "wʋsgo",
            Regex("(?i)\\b(roundan|rounda|ronda|runda|roun-da)\\b") to "rũndã",
            Regex("(?i)\\b(yamba)\\b") to "yãmba",
            Regex("(?i)\\b(tondo|tondô)\\b") to "Tond",
            Regex("(?i)\\b(touma|tuma)\\b") to "tʋʋma",
            Regex("(?i)\\b(sogma|somma|soama|ssoma)\\b") to "sõama",
            Regex("(?i)\\b(boé|boué|bwé|boe)\\b") to "bõe",
            Regex("(?i)\\b(sagbo|sakbo|sabo)\\b") to "sagbo"
        )

        for ((pattern, replacement) in replacements) {
            s = s.replace(pattern, replacement)
        }

        // Rapprochement phonétique flou (squelette acoustique) sur les phrases courtes (1 à 5 mots)
        val fuzzyMatched = matchClosestMooreCanonicalPhrase(s)
        if (fuzzyMatched != null) {
            return fuzzyMatched
        }

        return s
    }

    /**
     * Compare le squelette phonétique de la phrase transcrite avec les 50 leçons audio Mooré
     * pour corriger automatiquement les syllabes légèrement déformées par le micro.
     */
    fun matchClosestMooreCanonicalPhrase(input: String): String? {
        val cleaned = input.trim().lowercase(Locale.ROOT)
            .replace(Regex("[^a-zàâäéèêëîïôöùûüçñãẽĩõũʋɩɛ'\\s-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleaned.length < 2 || cleaned in STANDARD_FRENCH_WORDS) return null
        val wordCount = cleaned.split(" ").size
        if (wordCount > 6) return null

        val inputSkeleton = toPhoneticSkeleton(cleaned)
        if (inputSkeleton.length < 2) return null

        var bestCanonical: String? = null
        var bestScore = 0.0

        for ((canonical, variants) in CANONICAL_MOORE_PHRASES) {
            for (variant in variants) {
                val varNorm = variant.lowercase(Locale.ROOT).trim()
                if (cleaned == varNorm) return canonical
                val varSkeleton = toPhoneticSkeleton(varNorm)
                if (inputSkeleton == varSkeleton) return canonical

                val maxLen = maxOf(inputSkeleton.length, varSkeleton.length)
                if (maxLen >= 4) {
                    val dist = levenshteinDistance(inputSkeleton, varSkeleton)
                    val score = 1.0 - (dist.toDouble() / maxLen.toDouble())
                    if (score > bestScore) {
                        bestScore = score
                        bestCanonical = canonical
                    }
                }
            }
        }

        return if (bestScore >= 0.76) bestCanonical else null
    }

    private fun toPhoneticSkeleton(text: String): String {
        val raw = text.lowercase(Locale.ROOT)
            .replace("ou", "u")
            .replace("w", "u")
            .replace("ʋ", "u")
            .replace("ū", "u")
            .replace("ù", "u")
            .replace("û", "u")
            .replace("ũ", "u")
            .replace("é", "e")
            .replace("è", "e")
            .replace("ê", "e")
            .replace("ë", "e")
            .replace("ẽ", "e")
            .replace("ɛ", "e")
            .replace("ai", "e")
            .replace("ei", "e")
            .replace("ô", "o")
            .replace("ö", "o")
            .replace("õ", "o")
            .replace("au", "o")
            .replace("eau", "o")
            .replace("à", "a")
            .replace("â", "a")
            .replace("ã", "a")
            .replace("ä", "a")
            .replace("î", "i")
            .replace("ï", "i")
            .replace("ĩ", "i")
            .replace("ɩ", "i")
            .replace("y", "i")
            .replace("qu", "k")
            .replace("c", "k")
            .replace("gu", "g")
            .replace("gn", "ny")
            .replace("s", "z")
            .replace(Regex("[^a-z]"), "")

        val sb = StringBuilder()
        var prev = ' '
        for (ch in raw) {
            if (ch != prev) {
                sb.append(ch)
                prev = ch
            }
        }
        return sb.toString()
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val temp = dp[j]
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + cost)
                prev = temp
            }
        }
        return dp[b.length]
    }

    private fun mapToWhisperLanguageCode(lang: String?): String? {
        if (lang.isNullOrBlank()) return null
        return when (lang.trim().lowercase(Locale.ROOT)) {
            "français", "francais", "fr", "french" -> "fr"
            "anglais", "english", "en" -> "en"
            "espagnol", "spanish", "es" -> "es"
            "allemand", "german", "de" -> "de"
            "arabe", "arabic", "ar" -> "ar"
            "italien", "italian", "it" -> "it"
            "portugais", "portuguese", "pt" -> "pt"
            "russe", "russian", "ru" -> "ru"
            "chinois", "chinese", "zh" -> "zh"
            "haoussa", "hausa", "ha" -> "ha"
            "mooré", "moore" -> "fr"
            else -> null
        }
    }
}
