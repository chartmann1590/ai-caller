package com.example.localcallagent.audio.core

import com.example.localcallagent.core.model.PcmFrame
import kotlin.math.sqrt

enum class VadState {
    SILENCE,
    SPEECH_STARTING,
    IN_SPEECH,
    SPEECH_ENDING
}

/**
 * Real-time Voice Activity and Endpoint Detector.
 * Target end-of-speech hangover: 250-400ms.
 * Target barge-in detection: <150ms.
 */
class VadDetector(
    private val energyThresholdRms: Float = 300f,
    private val speechOnsetFrames: Int = 3,      // ~60ms
    private val hangoverSilenceFrames: Int = 15 // ~300ms at 20ms frames
) {
    var state: VadState = VadState.SILENCE
        private set

    private var consecutiveSpeechFrames = 0
    private var consecutiveSilenceFrames = 0

    /**
     * Evaluates a PCM audio frame.
     * Returns true if speech was detected in this frame.
     */
    fun process(frame: PcmFrame): Boolean {
        val rms = calculateRms(frame.samples)
        val isSpeechFrame = rms > energyThresholdRms

        if (isSpeechFrame) {
            consecutiveSpeechFrames++
            consecutiveSilenceFrames = 0

            state = if (consecutiveSpeechFrames >= speechOnsetFrames) {
                VadState.IN_SPEECH
            } else {
                VadState.SPEECH_STARTING
            }
        } else {
            consecutiveSilenceFrames++
            consecutiveSpeechFrames = 0

            state = when {
                state == VadState.IN_SPEECH && consecutiveSilenceFrames < hangoverSilenceFrames -> {
                    VadState.SPEECH_ENDING
                }
                state == VadState.SPEECH_ENDING && consecutiveSilenceFrames >= hangoverSilenceFrames -> {
                    VadState.SILENCE
                }
                else -> VadState.SILENCE
            }
        }

        return state == VadState.IN_SPEECH || state == VadState.SPEECH_ENDING
    }

    /**
     * Fast check for barge-in interruption (<150ms).
     */
    fun isBargeInTriggered(frame: PcmFrame): Boolean {
        val rms = calculateRms(frame.samples)
        // High confidence speech spike
        return rms > (energyThresholdRms * 1.5f)
    }

    fun reset() {
        state = VadState.SILENCE
        consecutiveSpeechFrames = 0
        consecutiveSilenceFrames = 0
    }

    companion object {
        fun calculateRms(samples: ShortArray): Float {
            if (samples.isEmpty()) return 0f
            var sum = 0.0
            for (s in samples) {
                sum += (s.toDouble() * s.toDouble())
            }
            return sqrt(sum / samples.size).toFloat()
        }
    }
}
