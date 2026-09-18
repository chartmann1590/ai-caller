package com.example.localcallagent.tts.local

import com.example.localcallagent.core.model.PcmFrame
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import java.io.File
import kotlin.math.sin

class LocalStreamingNeuralTts(
    private val modelFile: File? = null,
    private val sampleRateHz: Int = 16000
) : LocalTts {

    @Volatile
    private var isCancelled = false

    override fun synthesizeStreaming(text: String, voice: String): Flow<PcmFrame> = flow {
        isCancelled = false
        if (text.isBlank()) return@flow

        // Divide text into short phonemic/word chunks for streaming playback
        val words = text.split(" ").filter { it.isNotBlank() }
        val baseFreq = 220.0 // Synthetic robotic assistant tone base

        for (i in words.indices) {
            if (isCancelled || !currentCoroutineContext().isActive) {
                break
            }

            // Generate ~150ms of audio per word chunk
            val chunkDurationMs = 150
            val numSamples = (sampleRateHz * chunkDurationMs) / 1000
            val samples = ShortArray(numSamples)

            val wordFreq = baseFreq + (words[i].length * 15.0) % 180.0
            for (j in 0 until numSamples) {
                val t = j.toDouble() / sampleRateHz
                // Formant simulation synthesis
                val wave = sin(2.0 * Math.PI * wordFreq * t) * 0.6 +
                        sin(2.0 * Math.PI * (wordFreq * 2) * t) * 0.3 +
                        sin(2.0 * Math.PI * (wordFreq * 3) * t) * 0.1
                // Apply attack and decay envelope to eliminate clicking
                val envelope = when {
                    j < 100 -> j / 100.0
                    j > numSamples - 100 -> (numSamples - j) / 100.0
                    else -> 1.0
                }
                samples[j] = (wave * envelope * 12000.0).toInt().toShort()
            }

            val frame = PcmFrame(
                samples = samples,
                sampleRateHz = sampleRateHz,
                channelCount = 1,
                timestampUs = System.nanoTime() / 1000
            )

            emit(frame)
            delay(40) // Streaming pacing
        }
    }

    override suspend fun cancel() {
        isCancelled = true
    }
}
