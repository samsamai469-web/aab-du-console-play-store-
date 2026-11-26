package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.data.local.ApiKeyManager
import com.example.util.GeminiErrorHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale

/**
 * Service de reconnaissance vocale et transcription audio haute précision propulsé
 * exclusivement par l'API directe Google Gemini (gemini-3.8-flash / gemini-3.5-flash-lite).
 */
class OpenAiWhisperService(context: Context) {

    private val appContext: Context = context.applicationContext

    companion object {
        private const val TAG = "GoogleSpeechTranscribe"
        private const val PRIMARY_TRANSCRIBE_MODEL = "gemini-3.8-flash"
        private const val FALLBACK_TRANSCRIBE_MODEL = "gemini-3.5-flash-lite"
        private const val FAST_TRANSCRIBE_TIMEOUT_MS = 3_800L
        private const val AFRICAN_TRANSCRIBE_TIMEOUT_MS = 4_800L

        @Volatile
        private var preferredWorkingTranscribeModel: String? = null

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

        val apiKey = ApiKeyManager.resolveGeminiApiKey(appContext)
        if (apiKey.isBlank()) {
            return@withContext Result.failure(MissingGeminiApiKeyException())
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

        val baseModels = listOf(
            FALLBACK_TRANSCRIBE_MODEL to FAST_TRANSCRIBE_TIMEOUT_MS,
            PRIMARY_TRANSCRIBE_MODEL to AFRICAN_TRANSCRIBE_TIMEOUT_MS
        )
        val modelsToTry = preferredWorkingTranscribeModel?.let { pref ->
            baseModels.sortedByDescending { it.first == pref }
        } ?: baseModels

        val requestContents = listOf(
            DirectContentInput(
                role = "user",
                parts = listOf(
                    DirectPartInput(inlineDataBytes = audioBytes, inlineMimeType = mimeType),
                    DirectPartInput(text = instruction)
                )
            )
        )

        var lastError: Throwable? = null
        for ((modelId, timeoutMs) in modelsToTry) {
            val resultDeferred = CompletableDeferred<Result<String>>()
            val transcribeJob = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    val response = GoogleGeminiDirectClient.generateContent(
                        apiKey = apiKey,
                        model = modelId,
                        contents = requestContents,
                        systemInstruction = systemGuideText,
                        temperature = 0.0f,
                        maxOutputTokens = 256
                    )
                    val rawText = response.text
                        .trim()
                        .removeSurrounding("\"")
                        .removeSurrounding("«", "»")
                        .trim()

                    if (rawText.isBlank() || isHallucinationOrPromptEcho(rawText)) {
                        resultDeferred.complete(Result.success(""))
                    } else {
                        val postProcessed = when {
                            isMooreHint -> applyMoorePhoneticCorrections(rawText, isMooreHint = true)
                            isDioulaHint -> applyDioulaPhoneticCorrections(rawText, isDioulaHint = true)
                            else -> rawText
                        }
                        Log.d(TAG, "Transcription Google ($modelId / $targetLangLabel) -> \"$postProcessed\" (raw=\"$rawText\")")
                        resultDeferred.complete(Result.success(postProcessed))
                    }
                } catch (e: Throwable) {
                    resultDeferred.complete(Result.failure(e))
                }
            }

            val outcome = withTimeoutOrNull(timeoutMs) {
                resultDeferred.await()
            }
            if (outcome == null) {
                transcribeJob.cancel(CancellationException("TRANSCRIBE_TIMEOUT"))
                Log.w(TAG, "Timeout transcription audio sur $modelId (${timeoutMs}ms)")
                lastError = java.util.concurrent.TimeoutException("Timeout transcription audio sur $modelId")
                continue
            }

            if (outcome.isSuccess) {
                preferredWorkingTranscribeModel = modelId
                return@withContext outcome
            } else {
                val err = outcome.exceptionOrNull()
                if (err is CancellationException && err !is TimeoutCancellationException) {
                    throw err
                }
                lastError = err
                Log.w(TAG, "Échec transcription audio sur $modelId: ${err?.message}", err)
                if (err is MissingGeminiApiKeyException ||
                    (err is GeminiApiHttpException && (err.httpCode == 401 || err.httpCode == 403))
                ) {
                    break
                }
            }
        }

        val errMsg = when (lastError) {
            null -> "Erreur temporaire de transcription audio."
            is TimeoutCancellationException,
            is java.util.concurrent.TimeoutException -> "Délai de transcription audio dépassé. Veuillez réessayer."
            is MissingGeminiApiKeyException,
            is GeminiApiHttpException -> GeminiErrorHandler.parse(lastError).userFriendlyMessage
            is java.net.UnknownHostException,
            is java.net.ConnectException,
            is java.net.NoRouteToHostException ->
                if (ApiKeyManager.hasActiveInternetConnection()) {
                    "Service vocal Google temporairement inaccessible. Veuillez réessayer."
                } else {
                    "Erreur de connexion : vérifiez votre réseau."
                }
            else -> GeminiErrorHandler.parse(lastError).userFriendlyMessage
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
2. PROLONGATIONS & VOIX DOUCE : Les locuteurs peuvent traîner ou prolonger certaines voyelles ou consonnes (ex : « Baaaarkaaa », « Niiii yiii beogooo », « Laaaafiii », « Wuuusgo », « Kooooom », « Foooo ») ou parler doucement. Restitue toujours le mot ou la phrase mooré naturel exact sans couper la fin de phrase ni inventer un mot français.
3. NE TRANSFORME JAMAIS un mot mooré en mot français qui lui ressemble phonétiquement (exemple interdit : transformer « Barka » en « barca », « Nii yi beogo » en « ni biogo » ou « il est beau », « Sugri » en « souris », « Fo laafi » en « faut la fille », « Kibaré » en « qui barré »).
4. Utilise l'orthographe mooré naturelle (avec ʋ, ɩ, ɛ, ã, ẽ, ĩ, õ, ũ si approprié ou graphie claire).
5. Repères lexicaux mooré fréquents :
   - Salutations : Nii yi beogo, Ne y beogo, Yibeo kibare, Nii windga, Windga kibare, Nii zabre, Zabre kibare, Niyungo, Yungo kibare, Neewôgô, Ne y waoongo, Kibaré, Fo laafi, Yãmb laafi bame wa, Laafi bala, Mam laafi bala, Ya sonma, Laafi beeme, La fõ.
   - Politesse & Bases : Barka, Bark wusgo, Barka ka be ye, Sugri, M kosa sugri, Tõnd kotame, N-ye, Ayo, A zemsame, A yaa sõma, A pa sõma ye, Yaa sɩda, Bilmãse, Nindaaré, Beoogo laafi, Wind da ganeg laafi, Wẽnd na ning barka.
   - Phrases courantes : M, Maam, Fo, Fõ, Tõnd, Yãmb, M debda raagẽ, M debda zakin, M debda tʋʋmde, M debda lekollẽ, M yita zakin, M data ligdi, M ka ligd ye, M tara ligdi, Kõ-m koom, Kõ-m rɩɩbo, Kõ-m ligdi, A yaa ligd a wãna, A yaa toogo, M wʋmame, M pa wʋm ye, M miime, M pa mi ye, M nan gounsam, M nonga foo, Fo yʋʋre la bõe, Mam yʋʋre la, Song maam, Wa ka, Zĩnd ka, Gũ-m, Gom bila bila.
   - Mots : koom (eau), ligdi (argent), rɩɩbo (nourriture), zaka / roogo (maison), raaga (marché), rawa (homme), paga (femme), biiga (enfant), mougne (riz), saabo (tô), buru (pain), nemdo (viande), zĩm (poisson), zom-kom, kênge, wa, rɩ, yũ, gũsi.
6. Renvoie uniquement la transcription exacte, sans guillemets, sans traduction ni commentaire. Si aucune voix humaine n'est audible, renvoie une chaîne vide.
            """.trimIndent()
            isDioulaHint -> """
Tu es un système de reconnaissance vocale (ASR) spécialisé en DIOULA / JULA (Burkina Faso, Côte d'Ivoire, Mali).
RÈGLES STRICTES :
1. Transcris fidèlement ce que la personne prononce en dioula (y compris s'il y a quelques mots empruntés au français).
2. PROLONGATIONS & VOIX DOUCE : Les locuteurs peuvent prolonger les voyelles ou consonnes (ex : « Iii niii sogomaaa », « Iii niii ceee », « Tɔɔɔrɔɔɔ tɛɛɛ », « Waaari », « Jiiii ») ou parler doucement. Restitue toujours le mot ou la phrase dioula naturel exact sans couper la phrase.
3. NE TRANSFORME JAMAIS un mot dioula en mot français qui lui ressemble phonétiquement (exemple interdit : transformer « I ni ce » en « initié » ou « il dit c'est », « I ni sogoma » en « il ni sogoma », « Tɔɔrɔ tɛ » en « trop tard » ou « toro té », « N bɛ taa » en « en bêtat », « I ka kɛnɛ wa » en « il a qu'à... »).
4. Phonologie dioula : c = tch (I ni ce = i ni tché), j = dj (ji = dji, joli = djoli), u = ou (sugu = sougou, dumuni = doumouni), ɛ = è, ɔ = o ouvert, ɲ = gn, ŋ = ng.
5. Repères lexicaux dioula fréquents :
   - Salutations : I ni sogoma, Aw ni sogoma, I ni tilen, I ni wula, I ni su, K'an su diya, I ni ce, I ni ce kosɛbɛ, I ni baara, Aw ni baara, I ka kɛnɛ wa, Aw ka kɛnɛ wa, N ka kɛnɛ, Tɔɔrɔ tɛ, Ile do, I dansɛ, Aw dansɛ, Sabali, Yafa n ma, Hakɛto, K'an bɛn, K'an bɛn sini, K'an bɛn sɔɔni, Awo, Ayi, A bɛnna, A ka ɲi, A man ɲi, Tiɛn don, Tiɛn tɛ.
   - Phrases courantes : N, Ne, I, Ile, A, An, Aw, U, N bɛ taa sugu la, N bɛ taa so, N bɛ taa baara la, N bɛ taa lakɔli la, N taara, N bɛ na, N bɔra so, I bɛ taa min, I bɛ mun kɛ, I bɛ min, Kɔngɔ bɛ n na, Minnɔgɔ bɛ n na, Ji di n ma, Dumuni di n ma, Wari di n ma, Wari bɛ n bolo, Wari tɛ n bolo, A sɔngɔ ye joli ye, A sɔngɔ ka gɛlɛn, Dɔgɔ bɔ a la, N y'a faamu, N m'a faamu, N y'a lɔn, N m'a lɔn, Kuma dɔɔni dɔɔni, Segi a kan, N dɛmɛ, N wele, N b'i fɛ, I tɔgɔ ye di, N tɔgɔ ye, Na yan, I sigi, N makɔnɔ.
   - Mots & Chiffres : ji (eau), wari (argent), dumuni (nourriture), so (maison), sugu (marché), dugu (village/ville), baara (travail), tɛri (ami), fa (père), ba (mère), den (enfant), cɛ (homme), muso (femme), malo (riz), sogo (viande), jɛgɛ (poisson), kelen, fila, saba, naani, duuru, wɔɔrɔ, wolonwula, seegin, kɔnɔntɔn, tan.
6. Renvoie uniquement la transcription exacte, sans guillemets, sans traduction ni commentaire. Si aucune voix humaine n'est audible, renvoie une chaîne vide.
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
