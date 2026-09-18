package com.example.localcallagent.core.model

import java.util.Arrays

/**
 * Represents raw PCM audio frame.
 * All internal audio processing and transports use this representation.
 */
data class PcmFrame(
    val samples: ShortArray,
    val sampleRateHz: Int,
    val channelCount: Int = 1,
    val timestampUs: Long = System.nanoTime() / 1000
) {
    val durationMs: Float
        get() = (samples.size.toFloat() / (sampleRateHz * channelCount)) * 1000f

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PcmFrame
        if (!samples.contentEquals(other.samples)) return false
        if (sampleRateHz != other.sampleRateHz) return false
        if (channelCount != other.channelCount) return false
        if (timestampUs != other.timestampUs) return false

        return true
    }

    override fun hashCode(): Int {
        var result = samples.contentHashCode()
        result = 31 * result + sampleRateHz
        result = 31 * result + channelCount
        result = 31 * result + timestampUs.hashCode()
        return result
    }

    override fun toString(): String {
        return "PcmFrame(samplesCount=${samples.size}, rate=$sampleRateHz, channels=$channelCount, timeUs=$timestampUs)"
    }
}
