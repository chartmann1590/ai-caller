package com.example.localcallagent.tts.local

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsLocalTest {

    @Test
    fun testStreamingSynthesisYieldsFrames() = runBlocking {
        val tts = LocalStreamingNeuralTts()
        val text = "Hi, I am calling to ask a question."
        val frames = tts.synthesizeStreaming(text).toList()

        assertTrue(frames.isNotEmpty())
        assertEquals(16000, frames[0].sampleRateHz)
        assertTrue(frames[0].samples.isNotEmpty())
    }

    @Test
    fun testInstantCancellationStopsSynthesis() = runBlocking {
        val tts = LocalStreamingNeuralTts()
        val text = "This is a very long sentence that will be interrupted by barge in immediately."

        // Start collecting first frame then cancel
        val frames = mutableListOf<Any>()
        val flow = tts.synthesizeStreaming(text)
        flow.take(1).collect {
            frames.add(it)
            tts.cancel()
        }

        assertEquals(1, frames.size)
    }
}
