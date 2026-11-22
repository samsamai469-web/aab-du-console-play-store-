package com.example.util

import android.content.Context
import com.example.ui.components.VoiceCallState
import kotlinx.coroutines.flow.StateFlow

/**
 * Compatibilité héritée : OpenAI Realtime est désactivé et redirige désormais 100 % vers
 * GoogleGeminiLiveCallManager propulsé par Firebase AI Logic (gemini-3.1-flash-live-preview).
 */
class OpenAiRealtimeCallManager(
    context: Context,
    onTurnCompleted: (userText: String, aiText: String) -> Unit = { _, _ -> },
    onFallbackRequired: (reason: String) -> Unit = {}
) {
    private val delegate = GoogleGeminiLiveCallManager(
        context = context,
        onTurnCompleted = onTurnCompleted,
        onFallbackRequired = onFallbackRequired
    )

    val callState: StateFlow<VoiceCallState> get() = delegate.callState
    val soundLevel: StateFlow<Float> get() = delegate.soundLevel
    val userTranscript: StateFlow<String> get() = delegate.userTranscript
    val aiTranscript: StateFlow<String> get() = delegate.aiTranscript

    fun startCall() = delegate.startCall()
    fun interruptAiSpeech() = delegate.interruptAiSpeech()
    fun commitUserSpeechNow() = delegate.commitUserSpeechNow()
    fun setMuted(muted: Boolean) = delegate.setMuted(muted)
    fun stopCall() = delegate.stopCall()
}
