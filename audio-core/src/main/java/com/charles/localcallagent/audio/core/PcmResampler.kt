package com.charles.localcallagent.audio.core

import com.charles.localcallagent.core.model.PcmFrame

/**
 * Resamples PCM audio between 8kHz (standard SIP/telephony) and 16kHz (ASR/VAD/TTS).
 */
object PcmResampler {

    fun resample8kTo16k(input: ShortArray): ShortArray {
        if (input.isEmpty()) return ShortArray(0)
        val output = ShortArray(input.size * 2)
        for (i in 0 until input.size - 1) {
            val curr = input[i].toInt()
            val next = input[i + 1].toInt()
            output[i * 2] = curr.toShort()
            output[i * 2 + 1] = ((curr + next) / 2).toShort()
        }
        output[output.size - 2] = input[input.size - 1]
        output[output.size - 1] = input[input.size - 1]
        return output
    }

    fun resample16kTo8k(input: ShortArray): ShortArray {
        if (input.isEmpty()) return ShortArray(0)
        val output = ShortArray(input.size / 2)
        for (i in output.indices) {
            // Simple 2:1 decimation with 2-tap averaging to mitigate aliasing
            val s1 = input[i * 2].toInt()
            val s2 = if (i * 2 + 1 < input.size) input[i * 2 + 1].toInt() else s1
            output[i] = ((s1 + s2) / 2).toShort()
        }
        return output
    }

    fun resample(frame: PcmFrame, targetRateHz: Int): PcmFrame {
        if (frame.sampleRateHz == targetRateHz) return frame
        val resampled = when {
            frame.sampleRateHz == 8000 && targetRateHz == 16000 -> resample8kTo16k(frame.samples)
            frame.sampleRateHz == 16000 && targetRateHz == 8000 -> resample16kTo8k(frame.samples)
            else -> {
                // Arbitrary ratio linear interpolation
                resampleArbitrary(frame.samples, frame.sampleRateHz, targetRateHz)
            }
        }
        return PcmFrame(
            samples = resampled,
            sampleRateHz = targetRateHz,
            channelCount = frame.channelCount,
            timestampUs = frame.timestampUs
        )
    }

    private fun resampleArbitrary(input: ShortArray, fromRate: Int, toRate: Int): ShortArray {
        val outLength = (input.size.toLong() * toRate / fromRate).toInt()
        val output = ShortArray(outLength)
        val ratio = fromRate.toDouble() / toRate.toDouble()

        for (i in 0 until outLength) {
            val srcPos = i * ratio
            val srcIndex = srcPos.toInt()
            val frac = srcPos - srcIndex

            val s1 = input[srcIndex.coerceAtMost(input.size - 1)].toDouble()
            val s2 = input[(srcIndex + 1).coerceAtMost(input.size - 1)].toDouble()
            val interpolated = s1 + frac * (s2 - s1)
            output[i] = interpolated.toInt().coerceIn(-32768, 32767).toShort()
        }
        return output
    }
}
