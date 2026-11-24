package com.example.data.remote

import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.Locale

/**
 * Service de reconnaissance vocale et transcription audio haute précision propulsé
 * exclusivement par Google Gemini (Firebase AI Logic — gemini-3.8-flash / gemini-3.5-flash-lite).
 * (OpenAI Whisper est entièrement désactivé.)
 */
class OpenAiWhisperService(@Suppress("UNUSED_PARAMETER") context: Context) {

    private val firebaseAi by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI())
    }

    companion object {
        private const val TAG = "GoogleSpeechTranscribe"
        private const val PRIMARY_TRANSCRIBE_MODEL = "gemini-3.8-flash"
        private const val FALLBACK_TRANSCRIBE_MODEL = "gemini-3.5-flash-lite"
        private const val FAST_TRANSCRIBE_TIMEOUT_MS = 6_500L
        private const val AFRICAN_TRANSCRIBE_TIMEOUT_MS = 8_000L

        fun isLikelyMooreUtterance(text: String): Boolean = false

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
            "transcris mot pour mot",
            "transcris exactement",
            "burkina faso, ouagadougou, bobo-dioulasso"
        )
    }

    suspend fun transcribeAudio(
        audioFile: File,
        languageHint: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!audioFile.exists() || audioFile.length() <= 44) {
            return@withContext Result.failure(IllegalArgumentException("Fichier audio vide ou introuvable."))
        }

        val mimeType = when {
            audioFile.name.endsWith(".wav", ignoreCase = true) -> "audio/wav"
            audioFile.name.endsWith(".mp3", ignoreCase = true) -> "audio/mpeg"
            audioFile.name.endsWith(".aac", ignoreCase = true) -> "audio/aac"
            audioFile.name.endsWith(".ogg", ignoreCase = true) -> "audio/ogg"
            else -> "audio/mp4"
        }

        val normalizedHint = languageHint?.trim()?.lowercase(Locale.ROOT).orEmpty()
        val isMooreHint = normalizedHint == "mos" || normalizedHint.contains("moor") || normalizedHint.contains("mòor")
        val isDioulaHint = normalizedHint == "dyu" || normalizedHint.contains("dioula") || normalizedHint.contains("jula")

        val targetLangLabel = when {
            normalizedHint in setOf("en", "anglais", "english") -> "anglais"
            normalizedHint in setOf("es", "espagnol", "spanish") -> "espagnol"
            normalizedHint in setOf("de", "allemand", "german", "deutsch") -> "allemand"
            normalizedHint in setOf("ar", "arabe", "arabic") -> "arabe"
            normalizedHint in setOf("it", "italien", "italian") -> "italien"
            normalizedHint in setOf("pt", "portugais", "portuguese") -> "portugais"
            normalizedHint in setOf("ru", "russe", "russian") -> "russe"
            normalizedHint in setOf("zh", "chinois", "chinese") -> "chinois"
            normalizedHint in setOf("ha", "haoussa", "hausa") -> "haoussa"
            isMooreHint -> "mooré"
            isDioulaHint -> "dioula"
            else -> "français"
        }

        val audioBytes = try {
            audioFile.readBytes()
        } catch (e: Exception) {
            Log.e(TAG, "Impossible de lire le fichier audio", e)
            return@withContext Result.failure(e)
        } finally {
            try {
                if (audioFile.exists()) audioFile.delete()
            } catch (_: Exception) {}
        }

        if (audioBytes.size <= 44) {
            return@withContext Result.failure(IllegalArgumentException("Enregistrement audio trop court."))
        }

        val systemGuideText = buildTranscriptionSystemInstruction(targetLangLabel, isMooreHint, isDioulaHint)
        val instruction = when {
            isMooreHint ->
                "Transcris fidèlement en mooré (orthographe mooré standard ou phonétique fidèle) ce que dit la personne dans cet audio. " +
                    "Renvoie UNIQUEMENT les mots prononcés, sans guillemets ni commentaire."
            isDioulaHint ->
                "Transcris fidèlement en dioula / jula ce que dit la personne dans cet audio. " +
                    "Renvoie UNIQUEMENT les mots prononcés, sans guillemets ni commentaire."
            else ->
                "Écoute attentivement cet enregistrement audio et transcris mot pour mot, fidèlement, ce que dit la personne (langue attendue : $targetLangLabel). " +
                    "Renvoie UNIQUEMENT le texte prononcé, sans guillemets, sans commentaire et sans préambule. " +
                    "S'il n'y a vraiment aucune parole humaine audible, renvoie uniquement une chaîne vide."
        }

        val modelsToTry = if (isMooreHint || isDioulaHint) {
            listOf(
                PRIMARY_TRANSCRIBE_MODEL to AFRICAN_TRANSCRIBE_TIMEOUT_MS,
                FALLBACK_TRANSCRIBE_MODEL to FAST_TRANSCRIBE_TIMEOUT_MS
            )
        } else {
            listOf(
                FALLBACK_TRANSCRIBE_MODEL to FAST_TRANSCRIBE_TIMEOUT_MS,
                PRIMARY_TRANSCRIBE_MODEL to AFRICAN_TRANSCRIBE_TIMEOUT_MS
            )
        }

        var lastError: Throwable? = null
        for ((modelId, timeoutMs) in modelsToTry) {
            try {
                val model = firebaseAi.generativeModel(
                    modelName = modelId,
                    systemInstruction = content { text(systemGuideText) },
                    generationConfig = generationConfig {
                        temperature = 0.0f
                        maxOutputTokens = 256
                    }
                )
                val promptContent = content {
                    part(InlineDataPart(audioBytes, mimeType))
                    text(instruction)
                }
                val response = withTimeout(timeoutMs) {
                    model.generateContent(promptContent)
                }
                val rawText = response.text.orEmpty()
                    .trim()
                    .removeSurrounding("\"")
                    .removeSurrounding("«", "»")
                    .trim()

                if (rawText.isBlank() || isHallucinationOrPromptEcho(rawText)) {
                    return@withContext Result.success("")
                }

                val postProcessed = when {
                    isMooreHint -> applyMoorePhoneticCorrections(rawText, isMooreHint = true)
                    isDioulaHint -> applyDioulaPhoneticCorrections(rawText, isDioulaHint = true)
                    else -> rawText
                }
                Log.d(TAG, "Transcription Google ($modelId / $targetLangLabel) -> \"$postProcessed\" (raw=\"$rawText\")")
                return@withContext Result.success(postProcessed)
            } catch (ce: CancellationException) {
                if (ce is TimeoutCancellationException) {
                    lastError = ce
                    Log.w(TAG, "Timeout transcription audio sur $modelId")
                } else {
                    throw ce
                }
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "Échec transcription audio sur $modelId: ${e.message}", e)
            }
        }

        val errMsg = when (lastError) {
            is TimeoutCancellationException -> "Délai de transcription audio dépassé. Veuillez réessayer."
            is java.net.UnknownHostException,
            is java.net.ConnectException,
            is java.net.NoRouteToHostException ->
                if (com.example.data.local.ApiKeyManager.hasActiveInternetConnection()) {
                    "Service vocal Google temporairement inaccessible. Veuillez réessayer."
                } else {
                    "Erreur de connexion : vérifiez votre réseau."
                }
            else -> "Erreur temporaire de transcription audio."
        }
        Result.failure(Exception(errMsg, lastError))
    }

    private fun buildTranscriptionSystemInstruction(
        targetLangLabel: String,
        isMooreHint: Boolean,
        isDioulaHint: Boolean
    ): String {
        return when {
            isMooreHint -> """
Tu es un système de reconnaissance vocale (ASR) spécialisé en MOORÉ (langue nationale du Burkina Faso).
RÈGLES STRICTES :
1. Transcris fidèlement ce que la personne prononce en mooré (y compris s'il y a quelques mots empruntés au français).
2. NE TRANSFORME JAMAIS un mot mooré en mot français qui lui ressemble phonétiquement (exemple interdit : transformer « Barka » en « barca », « Nii yi beogo » en « ni biogo » ou « il est beau », « Sugri » en « souris », « Fo laafi » en « faut la fille », « Kibaré » en « qui barré »).
3. Utilise l'orthographe mooré naturelle (avec ʋ, ɩ, ɛ, ã, ẽ, ĩ, õ, ũ si approprié ou graphie claire).
4. Repères lexicaux mooré fréquents :
   - Salutations : Nii yi beogo, Ne y beogo, Yibeo kibare, Nii windga, Windga kibare, Nii zabre, Zabre kibare, Niyungo, Yungo kibare, Neewôgô, Ne y waoongo, Kibaré, Fo laafi, Yãmb laafi bame wa, Laafi bala, Mam laafi bala, Ya sonma, Laafi beeme, La fõ.
   - Politesse & Bases : Barka, Bark wusgo, Barka ka be ye, Sugri, M kosa sugri, Tõnd kotame, N-ye, Ayo, A zemsame, A yaa sõma, A pa sõma ye, Yaa sɩda, Bilmãse, Nindaaré, Beoogo laafi, Wind da ganeg laafi, Wẽnd na ning barka.
   - Phrases courantes : M, Maam, Fo, Fõ, Tõnd, Yãmb, M debda raagẽ, M debda zakin, M debda tʋʋmde, M debda lekollẽ, M yita zakin, M data ligdi, M ka ligd ye, M tara ligdi, Kõ-m koom, Kõ-m rɩɩbo, Kõ-m ligdi, A yaa ligd a wãna, A yaa toogo, M wʋmame, M pa wʋm ye, M miime, M pa mi ye, M nan gounsam, M nonga foo, Fo yʋʋre la bõe, Mam yʋʋre la, Song maam, Wa ka, Zĩnd ka, Gũ-m, Gom bila bila.
   - Mots : koom (eau), ligdi (argent), rɩɩbo (nourriture), zaka / roogo (maison), raaga (marché), rawa (homme), paga (femme), biiga (enfant), mougne (riz), saabo (tô), buru (pain), nemdo (viande), zĩm (poisson), zom-kom, kênge, wa, rɩ, yũ, gũsi.
5. Renvoie uniquement la transcription exacte, sans guillemets, sans traduction ni commentaire. Si aucune voix humaine n'est audible, renvoie une chaîne vide.
            """.trimIndent()
            isDioulaHint -> """
Tu es un système de reconnaissance vocale (ASR) spécialisé en DIOULA / JULA (Burkina Faso, Côte d'Ivoire, Mali).
RÈGLES STRICTES :
1. Transcris fidèlement ce que la personne prononce en dioula (y compris s'il y a quelques mots empruntés au français).
2. NE TRANSFORME JAMAIS un mot dioula en mot français qui lui ressemble phonétiquement (exemple interdit : transformer « I ni ce » en « initié » ou « il dit c'est », « I ni sogoma » en « il ni sogoma », « Tɔɔrɔ tɛ » en « trop tard » ou « toro té », « N bɛ taa » en « en bêtat », « I ka kɛnɛ wa » en « il a qu'à... »).
3. Phonologie dioula : c = tch (I ni ce = i ni tché), j = dj (ji = dji, joli = djoli), u = ou (sugu = sougou, dumuni = doumouni), ɛ = è, ɔ = o ouvert, ɲ = gn, ŋ = ng.
4. Repères lexicaux dioula fréquents :
   - Salutations : I ni sogoma, Aw ni sogoma, I ni tilen, I ni wula, I ni su, K'an su diya, I ni ce, I ni ce kosɛbɛ, I ni baara, Aw ni baara, I ka kɛnɛ wa, Aw ka kɛnɛ wa, N ka kɛnɛ, Tɔɔrɔ tɛ, Ile do, I dansɛ, Aw dansɛ, Sabali, Yafa n ma, Hakɛto, K'an bɛn, K'an bɛn sini, K'an bɛn sɔɔni, Awo, Ayi, A bɛnna, A ka ɲi, A man ɲi, Tiɛn don, Tiɛn tɛ.
   - Phrases courantes : N, Ne, I, Ile, A, An, Aw, U, N bɛ taa sugu la, N bɛ taa so, N bɛ taa baara la, N bɛ taa lakɔli la, N taara, N bɛ na, N bɔra so, I bɛ taa min, I bɛ mun kɛ, I bɛ min, Kɔngɔ bɛ n na, Minnɔgɔ bɛ n na, Ji di n ma, Dumuni di n ma, Wari di n ma, Wari bɛ n bolo, Wari tɛ n bolo, A sɔngɔ ye joli ye, A sɔngɔ ka gɛlɛn, Dɔgɔ bɔ a la, N y'a faamu, N m'a faamu, N y'a lɔn, N m'a lɔn, Kuma dɔɔni dɔɔni, Segi a kan, N dɛmɛ, N wele, N b'i fɛ, I tɔgɔ ye di, N tɔgɔ ye, Na yan, I sigi, N makɔnɔ.
   - Mots & Chiffres : ji (eau), wari (argent), dumuni (nourriture), so (maison), sugu (marché), dugu (village/ville), baara (travail), tɛri (ami), fa (père), ba (mère), den (enfant), cɛ (homme), muso (femme), malo (riz), sogo (viande), jɛgɛ (poisson), kelen, fila, saba, naani, duuru, wɔɔrɔ, wolonwula, seegin, kɔnɔntɔn, tan.
5. Renvoie uniquement la transcription exacte, sans guillemets, sans traduction ni commentaire. Si aucune voix humaine n'est audible, renvoie une chaîne vide.
            """.trimIndent()
            else -> """
Tu es un système de reconnaissance vocale (ASR) multilingue de très haute précision (langue attendue : $targetLangLabel).
Transcris mot pour mot et fidèlement ce que dit la personne sans commentaire, sans guillemets et sans préambule. S'il n'y a aucune parole humaine audible, renvoie une chaîne vide.
            """.trimIndent()
        }
    }

    private fun isHallucinationOrPromptEcho(text: String): Boolean {
        val lower = text.trim().lowercase(Locale.ROOT)
        if (lower.length < 2) return true
        if (lower in setOf("[silence]", "[bruit]", "(silence)", "(inaudible)", "[inaudible]", "[musique]", "aucune parole", "silence")) return true
        if (HALLUCINATION_PATTERNS.any { lower.contains(it) }) return true
        if (lower.contains("transcris mot pour mot")) return true
        if (lower.contains("transcris fidèlement")) return true
        if (lower.contains("renvoie uniquement")) return true
        if (lower.contains("chaîne vide")) return true
        return false
    }

    fun applyMoorePhoneticCorrections(input: String, isMooreHint: Boolean = false): String {
        val s = input.trim()
        if (s.isEmpty() || !isMooreHint) return s
        return com.example.data.engine.DictionaryLookupEngine.normalizeMooreProlongations(s)
    }

    fun applyDioulaPhoneticCorrections(input: String, isDioulaHint: Boolean = false): String {
        val s = input.trim()
        if (s.isEmpty() || !isDioulaHint) return s
        return com.example.data.engine.DictionaryLookupEngine.normalizeDioulaAsrTranscript(s)
    }

    @Suppress("UNUSED_PARAMETER")
    fun matchClosestMooreCanonicalPhrase(input: String, isMooreHint: Boolean = true): String? = null
}
