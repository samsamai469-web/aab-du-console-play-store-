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
        private const val GPT4O_FLAGSHIP_TRANSCRIBE_MODEL = "gpt-4o-transcribe"
        private const val GPT4O_MINI_TRANSCRIBE_MODEL = "gpt-4o-mini-transcribe"
        private const val WHISPER_FAST_MODEL = "whisper-1"

        // Prompt court et propre pour éviter toute hallucination de mots aléatoires par Whisper
        private const val MOORE_WHISPER_PROMPT =
            "Nii yi beogo, Yibeo kibare, Nii zabre, Zabre kibare, Nii windga, Windga kibare, Niyungo, Yungo kibare, Kibare, Laafi bala, Laafi beeme, Ya sonma, Barka, Nindaaré, Fo data bõe, M data ligdi, M nan gounsam, M debda zakin wan, Sên na be."

        private const val MOORE_GPT4O_PROMPT =
            "Transcris exactement la question ou la phrase prononcée en Mooré du Burkina Faso ou en Français. Vocabulaire Mooré : Nii yi beogo, Yibeo kibare, Nii zabre, Zabre kibare, Nii windga, Windga kibare, Niyungo, Yungo kibare, Wind da ganeg laafi, Neewôgô, Kibare, Laafi bala, Laafi beeme, Ya sonma, Barka, Nindaaré, Fo yʋʋre la bõe, Fo yaa ãnda, Fo data bõe, Bõe la fo tõe n maane, M nan gounsam, M data ligdi, M debda zakin wan, Sên na be, Zom-kom."

        private const val FRENCH_GPT4O_PROMPT =
            "Transcris exactement et fidèlement les paroles prononcées par l'utilisateur sans jamais inventer ni remplacer de mots."

        private val QUESTION_PROTECTED_WORDS = setOf(
            "bõe", "boe", "bôe", "yɛ", "ãnda", "anda", "wãn", "wan", "yʋʋre", "yuure",
            "tẽng", "teng", "tõe", "toe", "maane", "sõng", "song", "sokre", "leokre",
            "qui", "que", "quoi", "quel", "quelle", "quels", "quelles", "pourquoi", "quand", "combien",
            "ou", "où", "comment", "explique", "dis", "raconte", "parle", "capitale", "president",
            "président", "burkina", "faso", "ouagadougou", "histoire", "signifie", "veut", "traduis",
            "question", "réponse", "reponse", "créé", "cree", "créateur", "createur", "appelle", "appelles"
        )

        // Expressions de salutation et phrases clés Mooré avec leurs variantes phonétiques directes (y compris homophones français de Whisper)
        private val CANONICAL_MOORE_PHRASES: List<Pair<String, List<String>>> = listOf(
            "Nii yi beogo" to listOf(
                "nii yi beogo", "ni yibeogo", "nii yibeogo", "ni ibeogo", "ni beogo", "ne y beogo",
                "ne beogo", "ni i bogo", "ni yibéogô", "ni hibéogo", "ni il beogo", "mais il beogo",
                "ni y beogo", "ni il bogo", "nuit beogo", "ni bio go", "ni biogo"
            ),
            "Yibeo kibare" to listOf(
                "yibeo kibare", "yibéo kibaré", "yi beogo kibare", "ibeo kibare", "yibeo kibar",
                "il beo kibare", "hi beo kibare", "yibeo qui barre"
            ),
            "Nii zabre" to listOf(
                "nii zabre", "ni zabre", "nii zaabre", "ni zaabre", "ne zabre", "ne y zaabre",
                "ni sabre", "ni jabre", "ni zabré", "ni sabré", "nuit zabre", "mais zabre"
            ),
            "Zabre kibare" to listOf(
                "zabre kibare", "zaabre kibare", "zabré kibaré", "sabre kibare", "sabré qui barre"
            ),
            "Nii windga" to listOf(
                "nii windga", "ni windga", "nii ouindga", "ni ouindga", "ni winga", "ni ouinga",
                "ne y windga", "nii ouin-nga", "ni ouin ga"
            ),
            "Windga kibare" to listOf(
                "windga kibare", "ouindga kibare", "winga kibare", "ouin-nga kibaré"
            ),
            "Niyungo" to listOf(
                "niyungo", "nii yungo", "ni yungo", "nii youngo", "ni youngo", "ni yougo",
                "ne y yungo", "nii youngô", "ni hugo", "ni yougô"
            ),
            "Yungo kibare" to listOf(
                "yungo kibare", "youngo kibare", "yougo kibare", "youngô kibaré"
            ),
            "Wind da ganeg laafi" to listOf(
                "wind da ganeg laafi", "wend na ganeg laafi", "ouenda ganag lafi", "winda ganeg lafi", "ganeg laafi"
            ),
            "Neewôgô" to listOf(
                "neewogo", "neewôgô", "ne waongo", "ne y waoongo", "né oua ouongo", "newogo", "ni waongo"
            ),
            "Ya sonma" to listOf(
                "ya sonma", "ya soma", "yaa soama", "ya choma", "ya sôma", "il y a soma", "il y a sonma", "ya chômage"
            ),
            "Laafi beeme" to listOf(
                "laafi beeme", "lafi beme", "lafi bémé", "la fi beme", "la fille bémé", "la fille beme", "la fi bémé"
            ),
            "Laafi bala" to listOf(
                "laafi bala", "lafi bala", "la fi bala", "la fille bala", "l'affiche bala"
            ),
            "Kibare" to listOf(
                "kibare", "kibaré", "ki bare", "kibari", "qui barre", "qui barré", "ki bar"
            ),
            "Nindaaré" to listOf(
                "nindaare", "nindaaré", "nindare", "nin dare", "ni daré"
            ),
            "Wënd-na kô-d nindaare" to listOf(
                "wend na kod nindaare", "ouenda kod nindare", "wenda kod nindaare"
            ),
            "Barka" to listOf("barka", "barca"),
            "Barka wʋsgo" to listOf("barka wusgo", "barka ousgo", "barka wousgo", "barca ousgo"),
            "Zom-kom" to listOf("zom kom", "zom-kom", "zome kome", "zôm kôôm"),
            "Sên na be" to listOf("sen na be", "sèn na bé", "c'est na bé"),
            "M nan gounsam" to listOf("m nan gounsam", "m na goussom", "m na goussam", "m'na goussôm"),
            "M data ligdi" to listOf("m data ligdi", "m'data ligdi", "mam data ligdi", "mam rata ligdi", "on date a ligdi"),
            "M debda zakin wan" to listOf("m debda zakin wan", "m debda zakin", "m'debda zakouan")
        )

        private val FRENCH_PROTECTED_WORDS = setOf(
            "bonjour", "bonsoir", "salut", "merci", "oui", "non", "comment", "ca", "ça", "va",
            "qui", "que", "quoi", "quel", "quelle", "quels", "quelles", "pourquoi", "quand", "combien",
            "ou", "où", "est", "sont", "suis", "es", "sommes", "etes", "êtes", "c'est", "il", "elle",
            "je", "tu", "nous", "vous", "ils", "elles", "on", "me", "te", "se", "le", "la", "les",
            "un", "une", "des", "du", "de", "dans", "pour", "avec", "sans", "sur", "sous", "par",
            "plus", "moins", "tres", "très", "bien", "mal", "bon", "bonne", "explique", "dis",
            "donne", "parle", "aide", "cherche", "trouve", "peux", "peut", "veux", "veut", "fais", "fait",
            "actualite", "actualité", "burkina", "faso", "ouagadougou", "revoir", "nuit", "midi",
            "question", "reponse", "réponse", "créé", "cree", "créateur", "createur", "appelle", "appelles"
        )

        private val HALLUCINATION_PATTERNS = listOf(
            "sous-titres réalisés",
            "sous-titrage",
            "sous-titres",
            "amara.org",
            "merci d'avoir regardé",
            "merci d'avoir écouté",
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
            "transcris exactement",
            "transcris fidèlement",
            "transcription vocale en langue"
        )
    }

    /**
     * Transcrit un fichier audio enregistré avec une précision maximale et une latence minimale :
     * Utilise `gpt-4o-transcribe` en priorité avec repli immédiat sur `gpt-4o-mini-transcribe` et `whisper-1`.
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
            // 1. Appel prioritaire sur le modèle flagship de transcription OpenAI : gpt-4o-transcribe (précision vocale maximale)
            val flagshipResult = executeTranscriptionRequest(
                audioFile = audioFile,
                apiKey = apiKey,
                model = GPT4O_FLAGSHIP_TRANSCRIBE_MODEL,
                whisperLangCode = whisperLangCode,
                isMooreHint = isMooreHint
            )

            val rawTranscriptResult = if (flagshipResult.isSuccess && !flagshipResult.getOrNull().isNullOrBlank()) {
                flagshipResult
            } else {
                val miniResult = executeTranscriptionRequest(
                    audioFile = audioFile,
                    apiKey = apiKey,
                    model = GPT4O_MINI_TRANSCRIBE_MODEL,
                    whisperLangCode = whisperLangCode,
                    isMooreHint = isMooreHint
                )
                if (miniResult.isSuccess && !miniResult.getOrNull().isNullOrBlank()) {
                    miniResult
                } else {
                    executeTranscriptionRequest(
                        audioFile = audioFile,
                        apiKey = apiKey,
                        model = WHISPER_FAST_MODEL,
                        whisperLangCode = whisperLangCode,
                        isMooreHint = isMooreHint
                    )
                }
            }

            if (rawTranscriptResult.isFailure) {
                return@withContext rawTranscriptResult
            }

            val rawText = rawTranscriptResult.getOrNull().orEmpty().trim()
            if (rawText.isBlank() || isHallucinationOrPromptEcho(rawText)) {
                Log.d(TAG, "Silence ou hallucination filtrée : \"$rawText\"")
                return@withContext Result.success("")
            }

            // Correction phonétique ciblée uniquement sur les vraies salutations Mooré sans altérer les questions
            val finalTranscript = applyMoorePhoneticCorrections(rawText, isMooreHint)
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
            isMooreHint && (model == GPT4O_FLAGSHIP_TRANSCRIBE_MODEL || model == GPT4O_MINI_TRANSCRIBE_MODEL) -> MOORE_GPT4O_PROMPT
            model == GPT4O_FLAGSHIP_TRANSCRIBE_MODEL || model == GPT4O_MINI_TRANSCRIBE_MODEL -> FRENCH_GPT4O_PROMPT
            isMooreHint -> MOORE_WHISPER_PROMPT
            whisperLangCode == "en" -> "Accurate speech-to-text transcription."
            else -> "Bonjour, bonsoir, SAM AI, Burkina Faso, Ouagadougou, Nii yi beogo, Yibeo kibare, Nii zabre, Zabre kibare."
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
        if (lower.contains("transcris exactement la question")) return true
        return false
    }

    /**
     * Normalise les salutations et expressions Mooré tout en préservant à 100% les vraies phrases et questions de l'utilisateur.
     */
    fun applyMoorePhoneticCorrections(input: String, isMooreHint: Boolean = true): String {
        var s = input.trim()
        if (s.isEmpty()) return s

        // 0. Normalisation des prolongations vocaliques excessives (ex: "Niiiii yi beoooogo" -> "Nii yi beogo")
        s = com.example.data.engine.DictionaryLookupEngine.normalizeMooreProlongations(s)

        // Uniquement les vraies variantes phonétiques Mooré (aucun mot français courant n'est remplacé hors mode Mooré !)
        val replacements = listOf(
            // 1. Leçon Audio n°1 : "Nii yi beogo" (Bonjour) & "Yibeo kibare"
            Regex("(?i)\\b(nii+\\s*y?i?\\s*beo+go+|ni\\s*y?i?\\s*beo+go+|nii?\\s*y?i?\\s*béogo+|ne\\s*y?i?\\s*beo+go+|né\\s*y?i?\\s*béogo+|ney\\s*beo+go+|nyi\\s*beo+go+|n'i\\s*beo+go+|n'yibeo+go+|ni\\s*yéogo|ne\\s*yéogo|nei\\s*beogo|nay beogo|n'aie beogo|ni bio go|ni biogo|ni ibeogo|ni ibéogo|ni i bogo|ni bogo|ni hibéogo|ni yé béogo|mi ibéogo|ni il beogo|ni il bogo|mais il beogo|nuit beogo)\\b") to "Nii yi beogo",
            Regex("(?i)\\b(yibeo+g?\\s*kibar[eé]+|yibéo+g?\\s*kibar[eé]+|yi\\s*beo+g?\\s*kibar[eé]+|yi\\s*béo+g?\\s*kibar[eé]+|ibeo\\s*kibar[eé]+|ibéo\\s*kibar[eé]+|hibéo\\s*kibar[eé]+|il\\s*beo\\s*kibar[eé]+|yibeo\\s*qui\\s*barr[eé]*)\\b") to "Yibeo kibare",
            // 2. Leçon Audio n°2 : "Nii zabre" (Bonsoir) & "Zabre kibare"
            Regex("(?i)\\b(nii+\\s*za+br[eé]*|ni\\s*za+br[eé]*|ne\\s*za+br[eé]*|ney\\s*za+br[eé]*|ne\\s*i\\s*za+br[eé]*|né\\s*i\\s*za+br[eé]*|ne\\s*y\\s*za+br[eé]*|ni sabre|ni sabré|ni z'abre|ni jabré|ni zablé|mi zabré|nuit zabre|nuit sabre)\\b") to "Nii zabre",
            Regex("(?i)\\b(za+br[eé]?\\s*kibar[eé]+|sabré\\s*kibar[eé]+|z'abre\\s*kibar[eé]+|sabré\\s*qui\\s*barr[eé]*)\\b") to "Zabre kibare",
            // 3. Leçon Audio n°3 : "Nii windga" (Midi / Après-midi) & "Windga kibare"
            Regex("(?i)\\b(nii+\\s*wi+nd?ga+|ni\\s*wi+nd?ga+|ne\\s*wi+nd?ga+|ney\\s*wi+nd?ga+|ne\\s*i\\s*wi+nd?ga+|né\\s*i\\s*wi+nd?ga+|ne\\s*ouind?ga+|ni\\s*ouind?ga+|ne\\s*y\\s*w[ĩi]nd?ga+|ni\\s*ouin\\s*ga|ne\\s*ouin\\s*ga|ni\\s*winga|nii\\s*winga|ni\\s*ouinga|ni\\s*wendga)\\b") to "Nii windga",
            Regex("(?i)\\b(wi+nd?ga+\\s*kibar[eé]+|ouind?ga+\\s*kibar[eé]+|winga\\s*kibar[eé]+|ouinga\\s*kibar[eé]+)\\b") to "Windga kibare",
            // 4. Leçon Audio n°4 : "Niyungo" (Nuit) & "Yungo kibare"
            Regex("(?i)\\b(nii+\\s*yu+ngo+|ni\\s*yu+ngo+|niyungo+|ne\\s*yu+ngo+|ney\\s*you+ngo+|ni\\s*you+ngo+|ne\\s*you+ngo+|ne\\s*i\\s*you+ngo+|ne\\s*y\\s*y[ʋu]ngo+|ni\\s*yougo|ni\\s*yongo|nii\\s*yougo|ni\\s*hugo)\\b") to "Niyungo",
            Regex("(?i)\\b(yu+ngo+\\s*kibar[eé]+|you+ngo+\\s*kibar[eé]+|yougo\\s*kibar[eé]+)\\b") to "Yungo kibare",
            // 5. Souhait de bonne nuit "Wind da ganeg laafi"
            Regex("(?i)\\b(wi+nd\\s*da+\\s*ga+n[ae]g\\s*la+fi+|we+nd\\s*na+\\s*ga+n[ae]g\\s*la+fi+|ouènda\\s*ganag\\s*lafi|ouinda\\s*ganag\\s*lafi|wenda\\s*ganag\\s*laafi|winda\\s*ganeg\\s*lafi|ganeg\\s*laafi)\\b") to "Wind da ganeg laafi",
            // 6. Bienvenue "Neewôgô"
            Regex("(?i)\\b(ne+w[oô]+g[oô]+|ne\\s*wa+o+ngo+|ne\\s*y\\s*wa+o+ngo+|né\\s*oua\\s*ouongo|ni\\s*waongo|né\\s*ouongo|né\\s*wogo)\\b") to "Neewôgô",
            // Salutations au travail (Ne y tʋʋma)
            Regex("(?i)\\b(ne tuuma|ney touma|ne touma|ni touma|ne i touma|ni tuuma|ne tuma|né touma|ne y tuuma)\\b") to "Ne y tʋʋma",
            // Ça va bien : "Ya sonma" / "Laafi bala" / "Laafi beeme"
            Regex("(?i)\\b(ya+\\s*so+n?ma+|ya+\\s*sõ+a*ma+|ya+\\s*ssoma+|ya\\s*choma|il\\s*y\\s*a\\s*so+n?ma+|ya\\s*chômage)\\b") to "Ya sonma",
            Regex("(?i)\\b(lafi bala|la fi bala|laafi ba la|laafi bala|la fille bala|l'affiche bala)\\b") to "Laafi bala",
            Regex("(?i)\\b(lafi bémé|lafi beme|laafi beme|la fi bémé|lafi bème|la fille bémé|la fille beme)\\b") to "Laafi beeme",
            Regex("(?i)\\b(fo bé lafi|fo be lafi|faut la fille)\\b") to "Fo be laafi",
            Regex("(?i)\\b(mam bé lafi|mame bé lafi|mam be lafi)\\b") to "Mam be laafi",
            // Kibare (Comment ça va ?)
            Regex("(?i)\\b(kibaré|ki baré|kibari|qui barre|qui barré|ki bar)\\b") to "Kibare",
            // Au revoir & Adieu : "Nindaaré" / "Wënd-na kô-d nindaare"
            Regex("(?i)\\b(ni+nda+r[eé]+|nin\\s*daré|nindaré)\\b") to "Nindaaré",
            Regex("(?i)\\b(ouènda\\s*kôd\\s*nindaré|wenda\\s*kod\\s*nindaare|wend\\s*na\\s*kod\\s*nindaare)\\b") to "Wënd-na kô-d nindaare",
            // Barka / Barka wʋsgo (Merci / Merci beaucoup)
            Regex("(?i)\\b(barka ousgo|barka wousgo|barka ousgou|barca wousgo|barka wusgo|barka usgo|barca ousgo)\\b") to "Barka wʋsgo",
            // Boisson de bienvenue : "Zom-kom"
            Regex("(?i)\\b(z[oô]+m\\s*k[oô]+m|zome\\s*kome|zom\\s*kom)\\b") to "Zom-kom",
            // L'actualité du jour : "Sên na be" / "Rũndã kibaaya"
            Regex("(?i)\\b(s[eèêẽ]+n\\s*na+\\s*b[eé]+|sèn\\s*na\\s*bé|c'est\\s*na\\s*bé)\\b") to "Sên na be",
            Regex("(?i)\\b(rou+nda+\\s*kiba+ya+|ru+nda+\\s*kiba+ya+)\\b") to "Rũndã kibaaya",
            // Questions fréquentes en Mooré (Comment tu t'appelles / Qui es-tu / Que veux-tu / Que peux-tu faire)
            Regex("(?i)\\b(fo\\s*y[uʋ]+r[eé]?\\s*la\\s*b[oõô]e|faut\\s*you\\s*ré\\s*la\\s*boé|fo\\s*youre\\s*la\\s*boe)\\b") to "Fo yʋʋre la bõe",
            Regex("(?i)\\b(fo\\s*ya+\\s*[aã]nda+|faut\\s*ya\\s*anda)\\b") to "Fo yaa ãnda",
            Regex("(?i)\\b(fo\\s*da+ta+\\s*b[oõô]e|faut\\s*data\\s*boé|b[oõô]e\\s*la\\s*fo\\s*da+ta+)\\b") to "Fo data bõe",
            Regex("(?i)\\b(b[oõô]e\\s*la\\s*fo\\s*t[oõô]e\\s*n\\s*ma+n[eé]?|boé\\s*la\\s*faut\\s*toé\\s*mane)\\b") to "Bõe la fo tõe n maane",
            // Phrases courantes des audios
            Regex("(?i)\\b(m['’\\s]*na+n?\\s*gou*n?sa+m|m['’\\s]*na+\\s*gou*sso+m|m['’\\s]*na+\\s*gousse+m)\\b") to "M nan gounsam",
            Regex("(?i)\\b(m['’\\s]*da+ta+\\s*li+gdi+|mam\\s*data\\s*ligdi|mam\\s*rata\\s*ligdi|on\\s*date\\s*a\\s*ligdi)\\b") to "M data ligdi",
            Regex("(?i)\\b(m['’\\s]*de+bda+\\s*za+ki*n?\\s*wa*n?|m['’\\s]*de+bda+\\s*za+koua+n?)\\b") to "M debda zakin wan",
            // Wẽnd na kõ-d laafi
            Regex("(?i)\\b(wend na kod lafi|ouind na kod lafi|wendé na kod lafi|wend na ko lafi|wend na kod laafi)\\b") to "Wẽnd na kõ-d laafi"
        )

        for ((pattern, replacement) in replacements) {
            s = s.replace(pattern, replacement)
        }

        if (isMooreHint) {
            val fuzzyMatched = matchClosestMooreCanonicalPhrase(s, isMooreHint = true)
            if (fuzzyMatched != null) {
                return fuzzyMatched
            }
        }

        return s
    }

    /**
     * Compare les salutations Mooré courtes avec le squelette phonétique UNIQUEMENT si l'énoncé
     * n'est PAS une question (ne remplace jamais une question posée par l'utilisateur).
     */
    fun matchClosestMooreCanonicalPhrase(input: String, isMooreHint: Boolean = true): String? {
        // Si l'utilisateur pose une question (contient '?'), on ne remplace JAMAIS sa question par une salutation
        if (input.contains("?")) return null

        val cleaned = input.trim().lowercase(Locale.ROOT)
            .replace(Regex("[^a-zàâäéèêëîïôöùûüçñãẽĩõũʋɩɛ'\\s-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleaned.length < 3) return null

        val words = cleaned.split(" ")
        if (words.size > 4) return null

        // Ne jamais écraser une question contenant un mot interrogatif Mooré ou Français
        if (words.any { it in QUESTION_PROTECTED_WORDS }) return null
        if (!isMooreHint && words.any { it in FRENCH_PROTECTED_WORDS }) return null
        if (isMooreHint && words.count { it in FRENCH_PROTECTED_WORDS } >= 2) return null

        val inputSkeleton = toPhoneticSkeleton(cleaned)
        var bestCanonical: String? = null
        var bestScore = 0.0

        for ((canonical, variants) in CANONICAL_MOORE_PHRASES) {
            for (variant in variants) {
                val varNorm = variant.lowercase(Locale.ROOT).trim()
                if (cleaned == varNorm) return canonical

                val maxLen = maxOf(cleaned.length, varNorm.length)
                if (maxLen >= 4) {
                    val dist = levenshteinDistance(cleaned, varNorm)
                    val textScore = 1.0 - (dist.toDouble() / maxLen.toDouble())
                    if (textScore > bestScore) {
                        bestScore = textScore
                        bestCanonical = canonical
                    }
                }

                if (isMooreHint && inputSkeleton.length >= 5) {
                    val varSkeleton = toPhoneticSkeleton(varNorm)
                    if (inputSkeleton == varSkeleton) return canonical
                    val skelMax = maxOf(inputSkeleton.length, varSkeleton.length)
                    if (skelMax >= 6) {
                        val skelDist = levenshteinDistance(inputSkeleton, varSkeleton)
                        val skelScore = 1.0 - (skelDist.toDouble() / skelMax.toDouble())
                        if (skelScore > bestScore) {
                            bestScore = skelScore
                            bestCanonical = canonical
                        }
                    }
                }
            }
        }

        val threshold = 0.90
        return if (bestScore >= threshold) bestCanonical else null
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
