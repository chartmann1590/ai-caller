package com.example.localcallagent.tts.local

import com.example.localcallagent.core.model.PcmFrame
import kotlinx.coroutines.flow.Flow

/**
 * PHASE I — Streaming Local Neural TTS Interface.
 * Generates incremental audio chunks without waiting for the full sentence.
 * Must support rapid cancellation for barge-in (<150ms).
 */
interface LocalTts {
    fun synthesizeStreaming(text: String, voice: String = "assistant"): Flow<PcmFrame>
    suspend fun cancel()
}
