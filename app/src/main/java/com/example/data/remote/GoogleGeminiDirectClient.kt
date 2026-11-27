package com.example.data.remote

import android.util.Base64
import android.util.Log
import com.example.data.local.WebSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MissingGeminiApiKeyException(
    message: String = "Clé API Google Gemini manquante. Configurez GEMINI_API_KEY dans Secrets (ou GitHub Secrets)."
) : IllegalStateException(message)

class GeminiApiHttpException(
    val httpCode: Int,
    val apiStatus: String?,
    val serverMessage: String?,
    val rawBody: String?
) : IOException(
    buildString {
        append("HTTP $httpCode")
        if (!apiStatus.isNullOrBlank()) append(" [$apiStatus]")
        if (!serverMessage.isNullOrBlank()) append(" : $serverMessage")
    }
)

data class DirectPartInput(
    val text: String? = null,
    val inlineDataBytes: ByteArray? = null,
    val inlineMimeType: String? = null
)

data class DirectContentInput(
    val role: String? = null,
    val parts: List<DirectPartInput>
)

data class DirectSpeechConfig(
    val voiceName: String = "Charon",
    val languageCode: String? = null
)

data class DirectGenerationResult(
    val text: String,
    val audioChunks: List<Pair<String, ByteArray>> = emptyList(),
    val sources: List<WebSource> = emptyList()
)

data class DirectStreamChunk(
    val text: String = "",
    val audioChunks: List<Pair<String, ByteArray>> = emptyList(),
    val sources: List<WebSource> = emptyList()
)

data class DirectLiveServerEvent(
    val setupComplete: Boolean = false,
    val interrupted: Boolean = false,
    val turnComplete: Boolean = false,
    val inputTranscription: String = "",
    val outputTranscription: String = "",
    val audioChunks: List<ByteArray> = emptyList(),
    val error: Throwable? = null
)

class DirectGeminiLiveSession internal constructor(
    private val webSocket: WebSocket,
    private val eventChannel: Channel<DirectLiveServerEvent>
) {
    fun receive(): Flow<DirectLiveServerEvent> = eventChannel.receiveAsFlow()

    fun sendAudioRealtime(pcmBytes: ByteArray, mimeType: String = "audio/pcm;rate=16000"): Boolean {
        if (pcmBytes.isEmpty()) return true
        val payload = buildJsonObject {
            putJsonObject("realtimeInput") {
                putJsonObject("audio") {
                    put("mimeType", mimeType)
                    put("data", Base64.encodeToString(pcmBytes, Base64.NO_WRAP))
                }
            }
        }
        return webSocket.send(payload.toString())
    }

    fun sendAudioStreamEnd(): Boolean {
        val payload = buildJsonObject {
            putJsonObject("realtimeInput") {
                put("audioStreamEnd", true)
            }
        }
        return webSocket.send(payload.toString())
    }

    fun close() {
        try {
            webSocket.close(1000, "Session closed")
        } catch (_: Throwable) {}
        eventChannel.close()
    }
}

object GoogleGeminiDirectClient {
    private const val TAG = "GeminiDirectClient"
    private const val REST_BASE = "https://generativelanguage.googleapis.com/v1beta/models"
    private const val LIVE_WS_BASE =
        "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val dnsCache = ConcurrentHashMap<String, Pair<Long, List<InetAddress>>>()
    private val dnsCacheTtlMs = 60_000L

    private val ipv4PreferredDns = object : okhttp3.Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val now = System.currentTimeMillis()
            val cached = dnsCache[hostname]
            if (cached != null && (now - cached.first) < dnsCacheTtlMs && cached.second.isNotEmpty()) {
                return cached.second
            }
            val all = okhttp3.Dns.SYSTEM.lookup(hostname)
            val v4 = all.filterIsInstance<Inet4Address>()
            val v6 = all.filterIsInstance<Inet6Address>()
            val ordered = if (v4.isNotEmpty()) v4 + v6 else all
            if (ordered.isNotEmpty()) {
                dnsCache[hostname] = now to ordered
            }
            return ordered
        }
    }

    private val restClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(ipv4PreferredDns)
            .connectionPool(ConnectionPool(6, 5, TimeUnit.MINUTES))
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val liveWsClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(ipv4PreferredDns)
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }

    private fun requireValidApiKey(apiKey: String): String {
        val clean = apiKey.trim().removeSurrounding("\"").removeSurrounding("'").trim()
        if (clean.isBlank() || clean == "MY_GEMINI_API_KEY") {
            throw MissingGeminiApiKeyException()
        }
        return clean
    }

    private fun buildRequestJson(
        contents: List<DirectContentInput>,
        systemInstruction: String? = null,
        enableGoogleSearch: Boolean = false,
        temperature: Float? = null,
        maxOutputTokens: Int? = null,
        responseModalities: List<String>? = null,
        speechConfig: DirectSpeechConfig? = null
    ): String {
        val root = buildJsonObject {
            put(
                "contents",
                buildJsonArray {
                    for (c in contents) {
                        add(
                            buildJsonObject {
                                if (!c.role.isNullOrBlank()) {
                                    put("role", c.role)
                                }
                                put(
                                    "parts",
                                    buildJsonArray {
                                        for (p in c.parts) {
                                            add(
                                                buildJsonObject {
                                                    if (!p.text.isNullOrEmpty()) {
                                                        put("text", p.text)
                                                    }
                                                    val bytes = p.inlineDataBytes
                                                    val mime = p.inlineMimeType
                                                    if (bytes != null && bytes.isNotEmpty() && !mime.isNullOrBlank()) {
                                                        putJsonObject("inlineData") {
                                                            put("mimeType", mime)
                                                            put("data", Base64.encodeToString(bytes, Base64.NO_WRAP))
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                    }
                                )
                            }
                        )
                    }
                }
            )

            if (!systemInstruction.isNullOrBlank()) {
                putJsonObject("systemInstruction") {
                    put(
                        "parts",
                        buildJsonArray {
                            add(buildJsonObject { put("text", systemInstruction) })
                        }
                    )
                }
            }

            if (enableGoogleSearch) {
                put(
                    "tools",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                putJsonObject("google_search") {}
                            }
                        )
                    }
                )
            }

            val hasGenConfig = temperature != null ||
                maxOutputTokens != null ||
                !responseModalities.isNullOrEmpty() ||
                speechConfig != null
            if (hasGenConfig) {
                putJsonObject("generationConfig") {
                    if (temperature != null) put("temperature", temperature)
                    if (maxOutputTokens != null) put("maxOutputTokens", maxOutputTokens)
                    if (!responseModalities.isNullOrEmpty()) {
                        put(
                            "responseModalities",
                            buildJsonArray {
                                responseModalities.forEach { add(JsonPrimitive(it)) }
                            }
                        )
                    }
                    if (speechConfig != null) {
                        putJsonObject("speechConfig") {
                            putJsonObject("voiceConfig") {
                                putJsonObject("prebuiltVoiceConfig") {
                                    put("voiceName", speechConfig.voiceName)
                                }
                            }
                            if (!speechConfig.languageCode.isNullOrBlank()) {
                                put("languageCode", speechConfig.languageCode)
                            }
                        }
                    }
                }
            }
        }
        return root.toString()
    }

    private fun parseHttpError(code: Int, bodyStr: String?): GeminiApiHttpException {
        var apiStatus: String? = null
        var serverMessage: String? = null
        if (!bodyStr.isNullOrBlank()) {
            try {
                val obj = jsonParser.parseToJsonElement(bodyStr).jsonObject["error"]?.jsonObject
                apiStatus = obj?.get("status")?.jsonPrimitive?.contentOrNull
                serverMessage = obj?.get("message")?.jsonPrimitive?.contentOrNull
            } catch (_: Throwable) {}
        }
        return GeminiApiHttpException(
            httpCode = code,
            apiStatus = apiStatus,
            serverMessage = serverMessage ?: bodyStr?.take(200),
            rawBody = bodyStr
        )
    }

    private fun parseCandidateChunk(chunkJson: String): DirectStreamChunk {
        if (chunkJson.isBlank()) return DirectStreamChunk()
        val root = jsonParser.parseToJsonElement(chunkJson).jsonObject
        val errObj = root["error"]?.jsonObject
        if (errObj != null) {
            val code = errObj["code"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 500
            val status = errObj["status"]?.jsonPrimitive?.contentOrNull
            val msg = errObj["message"]?.jsonPrimitive?.contentOrNull
            throw GeminiApiHttpException(code, status, msg, chunkJson)
        }

        val candidates = root["candidates"] as? JsonArray ?: return DirectStreamChunk()
        val firstCand = candidates.firstOrNull()?.jsonObject ?: return DirectStreamChunk()

        val textBuilder = StringBuilder()
        val audioList = mutableListOf<Pair<String, ByteArray>>()
        val parts = firstCand["content"]?.jsonObject?.get("parts") as? JsonArray
        if (parts != null) {
            for (el in parts) {
                val partObj = el.jsonObject
                val partText = partObj["text"]?.jsonPrimitive?.contentOrNull
                if (!partText.isNullOrEmpty()) {
                    textBuilder.append(partText)
                }
                val inlineObj = partObj["inlineData"]?.jsonObject
                if (inlineObj != null) {
                    val mime = inlineObj["mimeType"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val b64 = inlineObj["data"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    if (b64.isNotBlank()) {
                        try {
                            val bytes = Base64.decode(b64, Base64.DEFAULT)
                            if (bytes.isNotEmpty()) {
                                audioList.add(mime to bytes)
                            }
                        } catch (_: Throwable) {}
                    }
                }
            }
        }

        val sources = mutableListOf<WebSource>()
        val chunks = firstCand["groundingMetadata"]?.jsonObject?.get("groundingChunks") as? JsonArray
        if (chunks != null) {
            for (el in chunks) {
                val web = el.jsonObject["web"]?.jsonObject ?: continue
                val uri = web["uri"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (uri.isBlank()) continue
                val title = web["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                val domain = web["domain"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty().ifBlank {
                    try {
                        URI(uri).host?.removePrefix("www.").orEmpty()
                    } catch (_: Throwable) {
                        ""
                    }
                }
                if (sources.none { it.url == uri }) {
                    sources.add(
                        WebSource(
                            title = title.ifBlank { domain.ifBlank { "Google Search" } },
                            domain = domain,
                            url = uri
                        )
                    )
                }
            }
        }

        return DirectStreamChunk(
            text = textBuilder.toString(),
            audioChunks = audioList,
            sources = sources
        )
    }

    private suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation {
            try {
                cancel()
            } catch (_: Throwable) {}
        }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (cont.isActive) cont.resume(response) else response.close()
            }
        })
    }

    suspend fun generateContent(
        apiKey: String,
        model: String,
        contents: List<DirectContentInput>,
        systemInstruction: String? = null,
        enableGoogleSearch: Boolean = false,
        temperature: Float? = null,
        maxOutputTokens: Int? = null,
        responseModalities: List<String>? = null,
        speechConfig: DirectSpeechConfig? = null
    ): DirectGenerationResult = withContext(Dispatchers.IO) {
        val validKey = requireValidApiKey(apiKey)
        val payload = buildRequestJson(
            contents = contents,
            systemInstruction = systemInstruction,
            enableGoogleSearch = enableGoogleSearch,
            temperature = temperature,
            maxOutputTokens = maxOutputTokens,
            responseModalities = responseModalities,
            speechConfig = speechConfig
        )
        val request = Request.Builder()
            .url("$REST_BASE/$model:generateContent")
            .header("x-goog-api-key", validKey)
            .header("Content-Type", "application/json")
            .post(payload.toRequestBody(jsonMediaType))
            .build()

        val call = restClient.newCall(request)
        call.awaitResponse().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw parseHttpError(response.code, bodyStr)
            }
            val parsed = parseCandidateChunk(bodyStr)
            DirectGenerationResult(
                text = parsed.text,
                audioChunks = parsed.audioChunks,
                sources = parsed.sources
            )
        }
    }

    suspend fun streamGenerateContent(
        apiKey: String,
        model: String,
        contents: List<DirectContentInput>,
        systemInstruction: String? = null,
        enableGoogleSearch: Boolean = false,
        temperature: Float? = null,
        maxOutputTokens: Int? = null,
        responseModalities: List<String>? = null,
        speechConfig: DirectSpeechConfig? = null,
        onChunk: suspend (DirectStreamChunk) -> Unit
    ) = withContext(Dispatchers.IO) {
        val validKey = requireValidApiKey(apiKey)
        val payload = buildRequestJson(
            contents = contents,
            systemInstruction = systemInstruction,
            enableGoogleSearch = enableGoogleSearch,
            temperature = temperature,
            maxOutputTokens = maxOutputTokens,
            responseModalities = responseModalities,
            speechConfig = speechConfig
        )
        val request = Request.Builder()
            .url("$REST_BASE/$model:streamGenerateContent?alt=sse")
            .header("x-goog-api-key", validKey)
            .header("Accept", "text/event-stream")
            .header("Content-Type", "application/json")
            .post(payload.toRequestBody(jsonMediaType))
            .build()

        val call = restClient.newCall(request)
        val streamJobHandle = kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]?.invokeOnCompletion { cause ->
            if (cause != null) {
                try {
                    call.cancel()
                } catch (_: Throwable) {}
            }
        }
        try {
            call.awaitResponse().use { response ->
                if (!response.isSuccessful) {
                    val errBody = try { response.body?.string() } catch (_: Throwable) { null }
                    throw parseHttpError(response.code, errBody)
                }
                val source = response.body?.source()
                    ?: throw IOException("Réponse streaming vide du serveur Google Gemini.")

                val dataBuffer = StringBuilder()
                while (true) {
                    val line = source.readUtf8Line() ?: break
                when {
                    line.startsWith("data:") -> {
                        val contentPart = line.removePrefix("data:").trimStart()
                        if (contentPart == "[DONE]") continue
                        if (dataBuffer.isNotEmpty()) dataBuffer.append('\n')
                        dataBuffer.append(contentPart)
                    }
                    line.isBlank() -> {
                        if (dataBuffer.isNotEmpty()) {
                            val eventPayload = dataBuffer.toString().trim()
                            dataBuffer.setLength(0)
                            if (eventPayload.isNotEmpty() && eventPayload != "[DONE]") {
                                val parsed = parseCandidateChunk(eventPayload)
                                if (parsed.text.isNotEmpty() || parsed.audioChunks.isNotEmpty() || parsed.sources.isNotEmpty()) {
                                    onChunk(parsed)
                                }
                            }
                        }
                    }
                }
            }
            if (dataBuffer.isNotEmpty()) {
                val eventPayload = dataBuffer.toString().trim()
                if (eventPayload.isNotEmpty() && eventPayload != "[DONE]") {
                    val parsed = parseCandidateChunk(eventPayload)
                    if (parsed.text.isNotEmpty() || parsed.audioChunks.isNotEmpty() || parsed.sources.isNotEmpty()) {
                        onChunk(parsed)
                    }
                }
            }
            }
        } finally {
            streamJobHandle?.dispose()
        }
    }

    suspend fun connectLiveSession(
        apiKey: String,
        model: String,
        systemInstruction: String,
        voiceName: String = "Charon",
        enableGoogleSearch: Boolean = false,
        setupTimeoutMs: Long = 8_000L
    ): DirectGeminiLiveSession = withContext(Dispatchers.IO) {
        val validKey = requireValidApiKey(apiKey)
        val normalizedModel = if (model.startsWith("models/")) model else "models/$model"
        val encodedKey = URLEncoder.encode(validKey, "UTF-8")
        val wsUrl = "$LIVE_WS_BASE?key=$encodedKey"
        val eventChannel = Channel<DirectLiveServerEvent>(Channel.UNLIMITED)
        val setupReady = CompletableDeferred<Boolean>()

        val setupJson = buildJsonObject {
            putJsonObject("setup") {
                put("model", normalizedModel)
                putJsonObject("generationConfig") {
                    put("responseModalities", buildJsonArray { add(JsonPrimitive("AUDIO")) })
                    putJsonObject("speechConfig") {
                        putJsonObject("voiceConfig") {
                            putJsonObject("prebuiltVoiceConfig") {
                                put("voiceName", voiceName)
                            }
                        }
                    }
                }
                if (systemInstruction.isNotBlank()) {
                    putJsonObject("systemInstruction") {
                        put(
                            "parts",
                            buildJsonArray {
                                add(buildJsonObject { put("text", systemInstruction) })
                            }
                        )
                    }
                }
                putJsonObject("inputAudioTranscription") {}
                putJsonObject("outputAudioTranscription") {}
                if (enableGoogleSearch) {
                    put(
                        "tools",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    putJsonObject("googleSearch") {}
                                }
                            )
                        }
                    )
                }
            }
        }.toString()

        val req = Request.Builder()
            .url(wsUrl)
            .header("x-goog-api-key", validKey)
            .build()
        val ws = liveWsClient.newWebSocket(
            req,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    val sent = webSocket.send(setupJson)
                    if (!sent && !setupReady.isCompleted) {
                        setupReady.completeExceptionally(
                            IOException("Échec d'envoi de la configuration Live WebSocket.")
                        )
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleLiveSocketMessage(text, setupReady, eventChannel)
                }

                override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                    handleLiveSocketMessage(bytes.utf8(), setupReady, eventChannel)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    val code = response?.code
                    val err = if (code != null) {
                        val body = try { response.body?.string() } catch (_: Throwable) { null }
                        parseHttpError(code, body ?: t.message)
                    } else {
                        t
                    }
                    Log.w(TAG, "Live WebSocket failure ($normalizedModel): ${err.message}")
                    if (!setupReady.isCompleted) {
                        setupReady.completeExceptionally(err)
                    } else {
                        eventChannel.trySend(DirectLiveServerEvent(error = err))
                        eventChannel.close(err)
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (!setupReady.isCompleted) {
                        setupReady.completeExceptionally(
                            IOException("Connexion Live fermée ($code): $reason")
                        )
                    }
                    eventChannel.close()
                }
            }
        )

        try {
            withTimeout(setupTimeoutMs) {
                setupReady.await()
            }
            DirectGeminiLiveSession(ws, eventChannel)
        } catch (ce: CancellationException) {
            try { ws.cancel() } catch (_: Throwable) {}
            eventChannel.close()
            throw ce
        } catch (t: Throwable) {
            try { ws.cancel() } catch (_: Throwable) {}
            eventChannel.close()
            throw t
        }
    }

    private fun handleLiveSocketMessage(
        rawJson: String,
        setupReady: CompletableDeferred<Boolean>,
        eventChannel: Channel<DirectLiveServerEvent>
    ) {
        if (rawJson.isBlank()) return
        try {
            val root = jsonParser.parseToJsonElement(rawJson).jsonObject
            val errObj = root["error"]?.jsonObject
            if (errObj != null) {
                val code = errObj["code"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 400
                val status = errObj["status"]?.jsonPrimitive?.contentOrNull
                val msg = errObj["message"]?.jsonPrimitive?.contentOrNull ?: rawJson.take(200)
                val err = GeminiApiHttpException(code, status, msg, rawJson)
                Log.w(TAG, "Live WebSocket error frame: ${err.message}")
                if (!setupReady.isCompleted) {
                    setupReady.completeExceptionally(err)
                } else {
                    eventChannel.trySend(DirectLiveServerEvent(error = err))
                    eventChannel.close(err)
                }
                return
            }
            if (root.containsKey("setupComplete")) {
                setupReady.complete(true)
                return
            }

            val serverContent = root["serverContent"]?.jsonObject ?: return
            val interrupted = serverContent["interrupted"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
            val turnComplete = serverContent["turnComplete"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
            val inputTx = serverContent["inputTranscription"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull.orEmpty()
            var outputTx = serverContent["outputTranscription"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull.orEmpty()

            val audioList = mutableListOf<ByteArray>()
            val fallbackTextBuilder = StringBuilder()
            val parts = serverContent["modelTurn"]?.jsonObject?.get("parts") as? JsonArray
            if (parts != null) {
                for (el in parts) {
                    val partObj = el.jsonObject
                    val isThought = partObj["thought"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
                    if (!isThought) {
                        val partText = partObj["text"]?.jsonPrimitive?.contentOrNull
                        if (!partText.isNullOrEmpty()) {
                            fallbackTextBuilder.append(partText)
                        }
                    }
                    val inlineData = partObj["inlineData"]?.jsonObject ?: continue
                    val b64 = inlineData["data"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    if (b64.isNotBlank()) {
                        try {
                            val bytes = Base64.decode(b64, Base64.DEFAULT)
                            if (bytes.isNotEmpty()) audioList.add(bytes)
                        } catch (_: Throwable) {}
                    }
                }
            }
            if (outputTx.isEmpty() && fallbackTextBuilder.isNotEmpty()) {
                outputTx = fallbackTextBuilder.toString()
            }

            if (interrupted || turnComplete || inputTx.isNotEmpty() || outputTx.isNotEmpty() || audioList.isNotEmpty()) {
                eventChannel.trySend(
                    DirectLiveServerEvent(
                        interrupted = interrupted,
                        turnComplete = turnComplete,
                        inputTranscription = inputTx,
                        outputTranscription = outputTx,
                        audioChunks = audioList
                    )
                )
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Erreur parsing message Live WebSocket: ${t.message}")
        }
    }
}
