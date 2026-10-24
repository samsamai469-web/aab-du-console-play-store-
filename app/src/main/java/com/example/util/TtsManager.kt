package com.example.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class TtsManager(context: Context) : TextToSpeech.OnInitListener {
    private val appContext: Context = context.applicationContext
    private var tts: TextToSpeech? = null
    private var isReady = false
    private var currentSpeechRate: Float = 1.0f

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
                        activeOnStartCallback?.invoke()
                    }

                    override fun onDone(utteranceId: String?) {
                        val callback = activeOnDoneCallback
                        activeOnDoneCallback = null
                        callback?.invoke()
                    }

                    override fun onError(utteranceId: String?) {
                        val callback = activeOnErrorCallback
                        activeOnErrorCallback = null
                        callback?.invoke()
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
            tts?.isSpeaking ?: false
        } catch (_: Exception) {
            false
        }
    }

    fun speak(text: String, language: String? = null) {
        speakWithCallback(text = text, language = language, rate = currentSpeechRate, onStart = null, onDone = null, onError = null)
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
        if (!isReady) {
            pendingSpeech = {
                speakWithCallback(text, language, rate, onStart, onDone, onError)
            }
            return
        }
        try {
            activeOnStartCallback = onStart
            activeOnDoneCallback = onDone
            activeOnErrorCallback = onError

            if (language != null) {
                val targetLocale = when (language.lowercase()) {
                    "anglais", "english", "en" -> Locale.ENGLISH
                    "allemand", "german", "deutsch", "de" -> Locale.GERMAN
                    "espagnol", "spanish", "es" -> Locale("es", "ES")
                    "arabe", "arabic", "ar" -> Locale("ar")
                    "haoussa", "hausa", "ha" -> {
                        val hausaLoc = Locale("ha")
                        if (tts?.isLanguageAvailable(hausaLoc) in listOf(TextToSpeech.LANG_AVAILABLE, TextToSpeech.LANG_COUNTRY_AVAILABLE)) {
                            hausaLoc
                        } else {
                            Locale.FRENCH
                        }
                    }
                    "mooré", "moore", "dioula" -> {
                        // Mooré & Dioula: use French phonetic synthesis with adjusted speech rate for optimal African phoneme pronunciation
                        tts?.setSpeechRate((rate * 0.90f).coerceIn(0.5f, 1.5f))
                        Locale.FRENCH
                    }
                    else -> Locale.FRENCH
                }
                if (language.lowercase() !in listOf("mooré", "moore", "dioula")) {
                    tts?.setSpeechRate(rate)
                }
                tts?.language = targetLocale
            } else {
                tts?.setSpeechRate(rate)
            }
            val params = android.os.Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            }
            val utteranceId = "sam_tts_${System.currentTimeMillis()}"
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        } catch (_: Exception) {
            onError?.invoke()
        }
    }

    fun stop() {
        try {
            activeOnDoneCallback = null
            activeOnErrorCallback = null
            activeOnStartCallback = null
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
