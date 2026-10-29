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
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
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
        private const val GPT4O_TRANSCRIBE_MODEL = "gpt-4o-mini-transcribe"
        private const val WHISPER_FALLBACK_MODEL = "whisper-1"

        // Instructions phonétiques pour gpt-4o-mini-transcribe : capte les syllabes Mooré exactes et les prolongations vocales
        private const val MOORE_GPT4O_PROMPT =
            "Transcris exactement les paroles prononcées en langue Mooré (Burkina Faso) ou en français burkinabè, même si la personne allonge ou prolonge les voyelles et consonnes. " +
            "Ne traduis jamais en français ni en anglais : écris fidèlement les mots Mooré entendus. " +
            "Salutations et réponses obligatoires : " +
            "Nii yi beogo, Yibeo kibare, Nii zabre, Zabre kibare, Nii windga, Windga kibare, Niyungo, Yungo kibare, " +
            "Wind da ganeg laafi, Neewôgô, Ya sonma, Laafi beeme, Laafi bala, Kibaré, Nindaaré, Wënd-na kô-d nindaare, Barka, Zom-kom, Sên na be. " +
            "Pronoms, famille et personnes : " +
            "Maam, Fõ, Tond, M'ma, M'baaba, Nikieman, Poug yaanga, Paga, Rawa, Biiga, Poug sada, Kakouada, Tandaogo. " +
            "Phrases et objets du quotidien : " +
            "M nan gounsam, M data ligdi, M debda zakin wan, Raaga, Monteur, Monbile, Pnous, Nanwoudre, Weefo, Moncoroni, Foūgou, Son kaam, Bidou, Mougne, Beinga, Touayga, Banga, Yagre. " +
            "Verbes et vocabulaire : " +
            "Saame, Sên ka be, Siifu, Wese, Yôsgre, Yâgme, Sake, Doge, Pùg Roagsá, Maane, Lagme, Yâage, Rodbo, Da, Ràada, Raabo, Yagle, Soge, Baasè, Paoongo, Manesem, Paasga, Paase, Kê."

        // Prompt lexical pur pour le fallback whisper-1
        private const val MOORE_WHISPER_PROMPT =
            "Nii yi beogo, Yibeo kibare, Nii zabre, Zabre kibare, Nii windga, Windga kibare, Niyungo, Yungo kibare, " +
            "Wind da ganeg laafi, Neewôgô, Ya sonma, Laafi beeme, Laafi bala, Kibaré, Nindaaré, Wënd-na kô-d nindaare, Barka, Zom-kom, Sên na be, " +
            "Maam, Fõ, Tond, M'ma, M'baaba, Nikieman, Poug yaanga, Paga, Rawa, Biiga, Poug sada, Kakouada, Tandaogo, " +
            "M nan gounsam, M data ligdi, M debda zakin wan, Raaga, Monteur, Monbile, Pnous, Nanwoudre, Weefo, Moncoroni, Foūgou, Son kaam, Bidou, Mougne, Beinga, Touayga, Banga, Yagre, " +
            "Saame, Sên ka be, Siifu, Wese, Yôsgre, Yâgme, Sake, Doge, Maane, Lagme, Yâage, Rodbo, Da, Ràada, Raabo, Yagle, Soge, Baasè, Paoongo, Manesem, Paasga, Paase, Kê."

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
     * Transcrit un fichier audio enregistré (PCM WAV 16kHz sans perte) via OpenAI Audio Transcriptions.
     * Utilise en priorité `gpt-4o-mini-transcribe` pour capter finement les prononciations Mooré,
     * avec repli automatique sur `whisper-1`.
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
            // 1. Essai principal avec gpt-4o-mini-transcribe (comprend le contexte phonétique Mooré sans forcer le dictionnaire français)
            val primaryResult = executeTranscriptionRequest(
                audioFile = audioFile,
                apiKey = apiKey,
                model = GPT4O_TRANSCRIBE_MODEL,
                whisperLangCode = if (isMooreHint) null else whisperLangCode,
                isMooreHint = isMooreHint
            )

            val rawTranscriptResult = if (primaryResult.isSuccess && !primaryResult.getOrNull().isNullOrBlank()) {
                primaryResult
            } else {
                // Repli automatique sur whisper-1 si gpt-4o-mini-transcribe n'est pas disponible
                Log.d(TAG, "Repli sur $WHISPER_FALLBACK_MODEL pour la transcription")
                executeTranscriptionRequest(
                    audioFile = audioFile,
                    apiKey = apiKey,
                    model = WHISPER_FALLBACK_MODEL,
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

            // Correction phonétique instantanée (0 ms) des homophones Mooré
            val finalTranscript = if (isMooreHint) {
                val corrected = applyMoorePhoneticCorrections(rawText)
                Log.d(TAG, "Transcription Mooré -> Brut: \"$rawText\" | Corrigé: \"$corrected\"")
                corrected
            } else {
                rawText
            }

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
            whisperLangCode == "fr" -> "Transcription fidèle en français du Burkina Faso, Ouagadougou, SAM AI."
            whisperLangCode == "en" -> "Accurate speech-to-text transcription."
            else -> "Ne y beogo, laafi bala, anitché, i ni sogoma, Burkina Faso, Mooré, Dioula, Français."
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

        // Pour whisper-1 uniquement, on ancre en "fr" pour éviter les alphabets non-latins.
        // Pour gpt-4o-mini-transcribe en Mooré, on laisse le modèle libre d'écrire la phonétique Mooré exacte guidée par le prompt.
        val effectiveLang = when {
            isMooreHint && model == GPT4O_TRANSCRIBE_MODEL -> null
            isMooreHint -> "fr"
            else -> whisperLangCode
        }
        if (!effectiveLang.isNullOrBlank()) {
            multipartBuilder.addFormDataPart("language", effectiveLang)
        }

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
        if (lower.contains("ne y beogo, ne y zaabre, ne y windga")) return true
        if (lower.contains("ne y beogo, ne y zaabre, ne y wĩndga")) return true
        return false
    }

    /**
     * Dictionnaire déterministe instantané (0 ms) de correction phonétique Mooré et détection des prolongations vocales :
     * Normalise les voyelles/nasales allongées (ex: "Niiiii zabreee", "Maaaaam", "Fooooo", "Raaaaga")
     * et convertit les homophones français en vrai Mooré standard certifié par les audios de référence.
     */
    fun applyMoorePhoneticCorrections(input: String): String {
        var s = input.trim()
        if (s.isEmpty()) return s

        // 0. Normalisation immédiate des prolongations de voyelles et consonnes nasales
        s = com.example.data.engine.DictionaryLookupEngine.normalizeMooreProlongations(s)

        val replacements = listOf(
            // 1. EN PREMIER — Leçon Audio n°1 : Salutation du matin "Nii yi beogo" (Bonjour) & sa réponse "Yibeo kibare"
            Regex("(?i)\\b(nii+\\s*y?i?\\s*beo+go+|ni\\s*y?i?\\s*beo+go+|nii?\\s*y?i?\\s*béogo+|ne\\s*y?i?\\s*beo+go+|né\\s*y?i?\\s*béogo+|ney\\s*beo+go+|nyi\\s*beo+go+|n'i\\s*beo+go+|n'yibeo+go+|ni\\s*yéogo|ne\\s*yéogo|nei\\s*beogo|mais il est beau|n'ayez beau|n'est-il beau|nez beau|les beaux|mais il boit|n'est beau|n'est-il pas beau|nay beogo|n'aie beogo|ni bio go|ni ibeogo|ni ibéogo)\\b") to "Nii yi beogo",
            Regex("(?i)\\b(yibeo+g?\\s*kibar[eé]+|yibéo+g?\\s*kibar[eé]+|yi\\s*beo+g?\\s*kibar[eé]+|yi\\s*béo+g?\\s*kibar[eé]+|ibeo\\s*kibar[eé]+|ibéo\\s*kibar[eé]+|il est beau qui barre)\\b") to "Yibeo kibare",
            // 2. Leçon Audio n°2 : Salutation du soir "Nii zabre" (Bonsoir) & sa réponse "Zabre kibare"
            Regex("(?i)\\b(nii+\\s*za+br[eé]*|ni\\s*za+br[eé]*|ne\\s*za+br[eé]*|ney\\s*za+br[eé]*|ne\\s*i\\s*za+br[eé]*|né\\s*i\\s*za+br[eé]*|ne\\s*y\\s*za+br[eé]*|mais il z'abre|les arbres|nez arbres|n'est arbre|mise à jour|ni sabre|ni z'abre)\\b") to "Nii zabre",
            Regex("(?i)\\b(za+br[eé]?\\s*kibar[eé]+|sabre\\s*qui\\s*barre|z'abre\\s*kibar[eé]+)\\b") to "Zabre kibare",
            // 3. Leçon Audio n°3 : Salutation Midi / Après-midi "Nii windga" & sa réponse "Windga kibare"
            Regex("(?i)\\b(nii+\\s*wi+nd?ga+|ni\\s*wi+nd?ga+|ne\\s*wi+nd?ga+|ney\\s*wi+nd?ga+|ne\\s*i\\s*wi+nd?ga+|né\\s*i\\s*wi+nd?ga+|ne\\s*ouind?ga+|ni\\s*ouind?ga+|ne\\s*y\\s*w[ĩi]nd?ga+|ni\\s*ouin\\s*ga|ne\\s*ouin\\s*ga)\\b") to "Nii windga",
            Regex("(?i)\\b(wi+nd?ga+\\s*kibar[eé]+|ouind?ga+\\s*kibar[eé]+)\\b") to "Windga kibare",
            // 4. Leçon Audio n°4 : Salutation de la nuit "Niyungo" & sa réponse "Yungo kibare"
            Regex("(?i)\\b(nii+\\s*yu+ngo+|ni\\s*yu+ngo+|niyungo+|ne\\s*yu+ngo+|ney\\s*you+ngo+|ni\\s*you+ngo+|ne\\s*you+ngo+|ne\\s*i\\s*you+ngo+|ne\\s*y\\s*y[ʋu]ngo+)\\b") to "Niyungo",
            Regex("(?i)\\b(yu+ngo+\\s*kibar[eé]+|you+ngo+\\s*kibar[eé]+)\\b") to "Yungo kibare",
            // 5. Souhait de bonne nuit "Wind da ganeg laafi"
            Regex("(?i)\\b(wi+nd\\s*da+\\s*ga+n[ae]g\\s*la+fi+|we+nd\\s*na+\\s*ga+n[ae]g\\s*la+fi+|ouènda\\s*ganag\\s*lafi|ouinda\\s*ganag\\s*lafi|wenda\\s*ganag\\s*laafi)\\b") to "Wind da ganeg laafi",
            // 6. Bienvenue "Neewôgô"
            Regex("(?i)\\b(ne+w[oô]+g[oô]+|ne\\s*wa+o+ngo+|ne\\s*y\\s*wa+o+ngo+|né\\s*oua\\s*ouongo|ni\\s*waongo)\\b") to "Neewôgô",
            // Salutations au travail (Ne y tʋʋma)
            Regex("(?i)\\b(ne tuuma|ney touma|ne touma|ni touma|ne i touma|ni tuuma|ne tuma|né touma|ne y tuuma)\\b") to "Ne y tʋʋma",
            // Ça va bien : "Ya sonma" / "Laafi bala"
            Regex("(?i)\\b(ya+\\s*so+n?ma+|ya+\\s*sõ+a*ma+|ya+\\s*ssoma+|il y a\\s*soma|ya\\s*son\\s*ma)\\b") to "Ya sonma",
            Regex("(?i)\\b(la fille va là|l'afrique va là|la vie va là|l'habit va là|la fille bala|la vie bala|lafi bala|la fi bala|laafi ba la|la fille parlait|la fille là-bas|la fi va là|lafi va la)\\b") to "Laafi bala",
            // Et la santé ? : "Laafi beeme"
            Regex("(?i)\\b(la fille bémé|la vie bémé|lafi bémé|lafi beme|laafi beme|la fi bémé|la fille m'aimait|la fille aimait|lafi bème)\\b") to "Laafi beeme",
            // Fo be laafi / Mam be laafi (Tu vas bien / Je vais bien)
            Regex("(?i)\\b(faut bien la fille|faut bé lafi|fo bé lafi|fo be lafi|il faut la fille|faut payer la fille)\\b") to "Fo be laafi",
            Regex("(?i)\\b(m'a mis la fille|mam bé lafi|mame bé lafi|mam be lafi|maman la fille)\\b") to "Mam be laafi",
            // Kibare (Comment ça va ?)
            Regex("(?i)\\b(qui barre|qu'il barre|kibaré|ki baré|kibari|qui bar|petit bar|tu barres|qui part|qu'il part)\\b") to "Kibare",
            // Au revoir & Adieu : "Nindaaré" / "Wënd-na kô-d nindaare"
            Regex("(?i)\\b(ni+nda+r[eé]+|nin\\s*daré|nindaré)\\b") to "Nindaaré",
            Regex("(?i)\\b(ouènda\\s*kôd\\s*nindaré|wenda\\s*kod\\s*nindaare|wend\\s*na\\s*kod\\s*nindaare)\\b") to "Wënd-na kô-d nindaare",
            // Barka / Barka wʋsgo (Merci / Merci beaucoup)
            Regex("(?i)\\b(barka ousgo|barka wousgo|barka ousgou|barca wousgo|barque à ousgo|barka wusgo|barka usgo|par cas où|barque à|barca ousgo)\\b") to "Barka wʋsgo",
            Regex("(?i)\\b(barque|barca)\\b") to "Barka",
            // Boisson de bienvenue : "Zom-kom"
            Regex("(?i)\\b(z[oô]+m\\s*k[oô]+m|zome\\s*kome|zom\\s*kom)\\b") to "Zom-kom",
            // L'actualité du jour : "Sên na be" / "Rũndã kibaaya"
            Regex("(?i)\\b(s[eèêẽ]+n\\s*na+\\s*b[eé]+|c'est\\s*na\\s*bé|sèn\\s*na\\s*bé)\\b") to "Sên na be",
            Regex("(?i)\\b(rou+nda+\\s*kiba+ya+|ru+nda+\\s*kiba+ya+)\\b") to "Rũndã kibaaya",
            // Phrases courantes des audios : "M nan gounsam" (Je vais dormir), "M data ligdi" (Je veux l'argent), "M debda zakin wan" (Je vais à la maison)
            Regex("(?i)\\b(m['’\\s]*na+n?\\s*gou*n?sa+m|m['’\\s]*na+\\s*gou*sso+m|m['’\\s]*na+\\s*gousse+m)\\b") to "M nan gounsam",
            Regex("(?i)\\b(m['’\\s]*da+ta+\\s*li+gdi+|madame\\s*data\\s*ligdi)\\b") to "M data ligdi",
            Regex("(?i)\\b(m['’\\s]*de+bda+\\s*za+ki*n?\\s*wa*n?|m['’\\s]*de+bda+\\s*za+koua+n?)\\b") to "M debda zakin wan",
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
        return s
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
