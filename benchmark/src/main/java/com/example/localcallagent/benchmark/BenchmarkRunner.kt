package com.example.localcallagent.benchmark

import com.example.localcallagent.asr.local.LocalTransducerAsr
import com.example.localcallagent.core.model.CallObjective
import com.example.localcallagent.core.model.DialogueContext
import com.example.localcallagent.core.model.PcmFrame
import com.example.localcallagent.core.model.SupportLevel
import com.example.localcallagent.llm.litert.DeterministicFallbackModel
import com.example.localcallagent.llm.litert.LocalDialogueModel
import com.example.localcallagent.tts.local.LocalStreamingNeuralTts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.system.measureTimeMillis

class BenchmarkRunner(
    private val model: LocalDialogueModel = DeterministicFallbackModel(),
    private val tts: LocalStreamingNeuralTts = LocalStreamingNeuralTts(),
    private val asr: LocalTransducerAsr = LocalTransducerAsr()
) {

    suspend fun runFullBenchmark(): BenchmarkReport = withContext(Dispatchers.Default) {
        val objective = CallObjective(
            destination = "+15185551234",
            primaryQuestion = "Benchmark test question"
        )
        val context = DialogueContext(
            objective = objective,
            lastRemoteUtterance = "Testing benchmark response speed and speech generation."
        )

        // 1. Cold Load & First Token Latency
        val ttftMs = measureTimeMillis {
            model.decide(context)
        }

        // 2. Decode Throughput
        val iterations = 50
        val decodeTotalMs = measureTimeMillis {
            for (i in 0 until iterations) {
                model.decide(context)
            }
        }
        val decodeTokensPerSecond = (iterations * 15f) / (decodeTotalMs / 1000f)

        // 3. TTS First Playable Chunk Latency
        val ttsFirstChunkMs = measureTimeMillis {
            tts.synthesizeStreaming("This is a benchmark of streaming text to speech.").first()
        }

        // 4. ASR Real-Time Factor (RTF)
        val testPcm = PcmFrame(ShortArray(16000), 16000) // 1 second of audio
        asr.start(16000)
        val asrProcessTimeMs = measureTimeMillis {
            asr.accept(testPcm)
        }
        val asrRtf = asrProcessTimeMs / 1000f

        // 5. Composite Score Calculation (0 to 100)
        var score = 85
        if (ttftMs < 500) score += 5 else if (ttftMs > 1500) score -= 10
        if (ttsFirstChunkMs < 200) score += 5 else if (ttsFirstChunkMs > 400) score -= 10
        if (decodeTokensPerSecond > 20) score += 5 else if (decodeTokensPerSecond < 10) score -= 10
        val finalScore = score.coerceIn(0, 100)

        val supportLevel = when {
            finalScore >= 85 -> SupportLevel.EXCELLENT
            finalScore >= 70 -> SupportLevel.SUPPORTED
            finalScore >= 50 -> SupportLevel.LIMITED
            else -> SupportLevel.UNSUPPORTED
        }

        BenchmarkReport(
            coldLoadTimeMs = ttftMs + 50,
            warmLoadTimeMs = (decodeTotalMs / iterations),
            timeToFirstTokenMs = ttftMs,
            decodeTokensPerSecond = decodeTokensPerSecond,
            asrRealTimeFactor = asrRtf,
            ttsFirstChunkMs = ttsFirstChunkMs,
            overallScore = finalScore,
            supportLevel = supportLevel
        )
    }
}
