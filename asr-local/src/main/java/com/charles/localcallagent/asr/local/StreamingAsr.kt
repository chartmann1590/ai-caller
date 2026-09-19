package com.charles.localcallagent.asr.local

import com.charles.localcallagent.core.model.PcmFrame
import kotlinx.coroutines.flow.Flow

data class AsrResult(
    val text: String,
    val confidence: Float,
    val endpointTimestampMs: Long = System.currentTimeMillis(),
    val detectedSlots: Map<String, String> = emptyMap(),
    val needsClarification: Boolean = false
)

/**
 * PHASE H — Offline Streaming ASR interface.
 * Operates entirely on-device without network access.
 * Consumes raw incoming PCM frames from the SIP transport.
 */
interface StreamingAsr {
    val partialResults: Flow<String>
    val finalResults: Flow<AsrResult>

    suspend fun start(sampleRateHz: Int = 16000)
    suspend fun accept(frame: PcmFrame)
    suspend fun stop()
    suspend fun reset()
}
