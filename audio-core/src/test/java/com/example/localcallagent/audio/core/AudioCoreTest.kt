package com.example.localcallagent.audio.core

import com.example.localcallagent.core.model.PcmFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioCoreTest {

    @Test
    fun testPcmResampling8kTo16kAndBack() {
        val original8k = ShortArray(80) { (it * 100).toShort() }
        val resampled16k = PcmResampler.resample8kTo16k(original8k)
        assertEquals(160, resampled16k.size)

        val backTo8k = PcmResampler.resample16kTo8k(resampled16k)
        assertEquals(80, backTo8k.size)
    }

    @Test
    fun testVadDetectorSpeechAndSilence() {
        val vad = VadDetector(energyThresholdRms = 200f, speechOnsetFrames = 2, hangoverSilenceFrames = 3)

        // Silence frame
        val silenceFrame = PcmFrame(ShortArray(160) { 10 }, 8000)
        assertFalse(vad.process(silenceFrame))
        assertEquals(VadState.SILENCE, vad.state)

        // Loud speech frames
        val loudFrame = PcmFrame(ShortArray(160) { 2000 }, 8000)
        vad.process(loudFrame) // 1st frame: onset
        assertEquals(VadState.SPEECH_STARTING, vad.state)

        val isSpeech = vad.process(loudFrame) // 2nd frame: in speech
        assertTrue(isSpeech)
        assertEquals(VadState.IN_SPEECH, vad.state)

        // Interruption / barge-in test
        assertTrue(vad.isBargeInTriggered(loudFrame))
    }

    @Test
    fun testBoundedAudioQueueDropsOldestWhenFull() {
        val queue = BoundedAudioQueue(maxCapacity = 3)
        val f1 = PcmFrame(ShortArray(10), 8000, timestampUs = 1)
        val f2 = PcmFrame(ShortArray(10), 8000, timestampUs = 2)
        val f3 = PcmFrame(ShortArray(10), 8000, timestampUs = 3)
        val f4 = PcmFrame(ShortArray(10), 8000, timestampUs = 4)

        assertTrue(queue.enqueue(f1))
        assertTrue(queue.enqueue(f2))
        assertTrue(queue.enqueue(f3))
        // 4th frame exceeds capacity: f1 is dropped
        assertFalse(queue.enqueue(f4))

        assertEquals(3, queue.size)
        assertEquals(1L, queue.droppedFramesCount)
        assertEquals(f2, queue.dequeue())
    }

    @Test
    fun testAudioRouterTakeOver() {
        val router = AudioRouter()
        assertEquals(CallAudioMode.BOT_CALLING, router.audioMode.value)

        router.takeOver()
        assertEquals(CallAudioMode.HUMAN_TAKEOVER, router.audioMode.value)

        router.resumeBot()
        assertEquals(CallAudioMode.BOT_CALLING, router.audioMode.value)
    }
}
